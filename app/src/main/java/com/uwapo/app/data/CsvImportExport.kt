package com.uwapo.app.data

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object CsvImportExport {
    data class ImportResult(val membersAdded: Int, val paymentsAdded: Int, val rowsSkipped: Int)

    fun export(members: List<MemberEntity>, payments: List<PaymentEntity>): String {
        val header = listOf("member_no", "full_name", "phone", "join_date", "status", "period_year", "period_month", "amount_paid_tzs", "paid_at", "payment_method", "receipt_reference", "source_note")
        val memberMap = members.associateBy { it.id }
        val lines = mutableListOf(header.joinToString(",") { csv(it) })
        payments.forEach { payment ->
            val member = memberMap[payment.memberId] ?: return@forEach
            val paidDate = java.time.Instant.ofEpochMilli(payment.paidAt).atZone(ZoneId.of("Africa/Dar_es_Salaam")).toLocalDate().toString()
            val joinedDate = java.time.Instant.ofEpochMilli(member.joinedAt).atZone(ZoneId.of("Africa/Dar_es_Salaam")).toLocalDate().toString()
            lines += listOf(member.memberNo, member.fullName, member.phone, joinedDate, member.status,
                payment.periodYear.toString(), payment.periodMonth.toString(), payment.amountTzs.toString(), paidDate,
                payment.method, payment.receiptNo, "Exported from UWAPO app (${payment.syncStatus})").joinToString(",") { csv(it) }
        }
        val paidMemberIds = payments.map { it.memberId }.toSet()
        members.filter { it.id !in paidMemberIds }.forEach { member ->
            val joinedDate = java.time.Instant.ofEpochMilli(member.joinedAt).atZone(ZoneId.of("Africa/Dar_es_Salaam")).toLocalDate().toString()
            lines += listOf(member.memberNo, member.fullName, member.phone, joinedDate, member.status, "", "", "", "", "", "", "No payment row").joinToString(",") { csv(it) }
        }
        return lines.joinToString("\r\n") + "\r\n"
    }

    suspend fun import(input: InputStream, dao: UWAPODao): ImportResult {
        val rows = parseInput(input)
        if (rows.isEmpty()) return ImportResult(0, 0, 0)
        val header = rows.first().map { it.trim().removePrefix("\uFEFF").lowercase(Locale.ROOT) }
        fun index(vararg names: String): Int = header.indexOfFirst { it in names }
        val iNo = index("member_no", "member number", "membership number", "member_id", "member id", "no", "no.")
        val iName = index("full_name", "name", "member name", "full name")
        if (iNo < 0 || iName < 0) throw IllegalArgumentException("File must contain member_no and full_name columns.")
        val iPhone = index("phone", "phone_number", "phone number", "mobile")
        val iJoin = index("join_date", "joined_at", "join date", "date joined")
        val iStatus = index("status")
        val iYear = index("period_year", "year")
        val iMonth = index("period_month", "month")
        val iAmount = index("amount_paid_tzs", "amount", "paid", "amount paid", "amount_paid")
        val iPaidAt = index("paid_at", "payment_date", "paid date", "date")
        val iMethod = index("payment_method", "method", "payment method")
        val iReceipt = index("receipt_reference", "receipt_no", "receipt", "receipt no")
        var membersAdded = 0
        var paymentsAdded = 0
        var skipped = 0
        rows.drop(1).forEach { row ->
            fun value(i: Int) = if (i >= 0 && i < row.size) row[i].trim() else ""
            val memberNo = cleanStr(value(iNo))
            val fullName = value(iName)
            if (memberNo.isBlank() || fullName.isBlank()) { skipped++; return@forEach }
            val joinDate = parseDate(value(iJoin)) ?: LocalDate.now()
            val memberId = UUID.nameUUIDFromBytes("UWAPO-MEMBER:$memberNo".toByteArray(Charsets.UTF_8)).toString()
            val member = MemberEntity(memberId, memberNo, fullName, cleanStr(value(iPhone)), joinDate.atStartOfDay(ZoneId.of("Africa/Dar_es_Salaam")).toInstant().toEpochMilli(), value(iStatus).ifBlank { "active" }.lowercase(), "pending")
            val inserted = dao.insertMemberIgnore(member)
            if (inserted != -1L) membersAdded++
            val actualMember = dao.getMemberByNumber(memberNo) ?: run { skipped++; return@forEach }
            val amount = value(iAmount).replace(",", "").replace("TZS", "", ignoreCase = true).trim().let { it.toDoubleOrNull()?.toLong() ?: it.toLongOrNull() } ?: 0L
            val year = cleanStr(value(iYear)).toIntOrNull()
            val month = cleanStr(value(iMonth)).toIntOrNull()
            if (amount > 0 && year != null && year in 2000..2100 && month != null && month in 1..12) {
                val paidDate = parseDate(value(iPaidAt)) ?: LocalDate.of(year, month, 1)
                val method = value(iMethod).ifBlank { "cash" }.lowercase().replace(' ', '_')
                val receipt = value(iReceipt).ifBlank { "IMPORT-${memberNo}-${year}-${month.toString().padStart(2,'0')}" }
                val key = "$memberNo|$year|$month|$amount|$paidDate|$receipt"
                val paymentId = UUID.nameUUIDFromBytes("UWAPO-PAYMENT:$key".toByteArray(Charsets.UTF_8)).toString()
                val payment = PaymentEntity(paymentId, actualMember.id, amount, year, month,
                    paidDate.atStartOfDay(ZoneId.of("Africa/Dar_es_Salaam")).toInstant().toEpochMilli(),
                    if (method in listOf("cash", "bank", "mobile_money", "other")) method else "other",
                    receipt, paymentId, "pending")
                if (dao.insertPaymentIgnore(payment) != -1L) paymentsAdded++
            }
        }
        return ImportResult(membersAdded, paymentsAdded, skipped)
    }

    private fun cleanStr(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.endsWith(".0") && trimmed.substringBefore(".0").all { it.isDigit() }) {
            trimmed.substringBefore(".0")
        } else trimmed
    }

    private fun parseDate(value: String): LocalDate? = try {
        val trimmed = value.trim()
        if (trimmed.isBlank()) null
        else {
            val num = trimmed.toDoubleOrNull()
            if (num != null && num > 30000 && num < 100000) {
                val days = num.toLong()
                val offset = if (days > 60) 0L else 1L
                LocalDate.of(1899, 12, 30).plusDays(days - offset)
            } else {
                val s = trimmed.take(10)
                try { LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE) }
                catch (_: Exception) {
                    try { LocalDate.parse(s, DateTimeFormatter.ofPattern("dd/MM/yyyy")) }
                    catch (_: Exception) {
                        try { LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy/MM/dd")) }
                        catch (_: Exception) {
                            try { LocalDate.parse(s, DateTimeFormatter.ofPattern("dd-MM-yyyy")) }
                            catch (_: Exception) { null }
                        }
                    }
                }
            }
        }
    } catch (_: Exception) { null }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private fun parseInput(input: InputStream): List<List<String>> {
        val bytes = input.readBytes()
        val isZip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
        return if (isZip) {
            parseXlsx(bytes)
        } else {
            parseCsv(ByteArrayInputStream(bytes).bufferedReader(Charsets.UTF_8).readText())
        }
    }

    private fun parseCsv(text: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { row.add(cell.toString()); cell.setLength(0) }
                (c == '\n' || c == '\r') && !quoted -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(cell.toString()); cell.setLength(0)
                    if (row.any { it.isNotBlank() }) result.add(row)
                    row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); if (row.any { it.isNotBlank() }) result.add(row) }
        if (quoted) throw IllegalArgumentException("CSV has an unclosed quoted field.")
        return result
    }

    private fun parseXlsx(bytes: ByteArray): List<List<String>> {
        var sharedStrings = emptyList<String>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.lowercase(Locale.ROOT).endsWith("xl/sharedstrings.xml")) {
                        val entryBytes = zip.readBytes()
                        sharedStrings = parseSharedStrings(ByteArrayInputStream(entryBytes))
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        var rows = emptyList<List<String>>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase(Locale.ROOT)
                    if (name.contains("xl/worksheets/sheet") && name.endsWith(".xml")) {
                        val entryBytes = zip.readBytes()
                        rows = parseSheet(ByteArrayInputStream(entryBytes), sharedStrings)
                        if (rows.isNotEmpty()) break
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return rows
    }

    private fun parseSharedStrings(input: InputStream): List<String> {
        val strings = mutableListOf<String>()
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = false
        val doc = dbf.newDocumentBuilder().parse(input)
        val siList = doc.getElementsByTagName("si")
        for (i in 0 until siList.length) {
            val si = siList.item(i) as Element
            val tList = si.getElementsByTagName("t")
            val sb = StringBuilder()
            for (j in 0 until tList.length) {
                sb.append(tList.item(j).textContent)
            }
            strings.add(sb.toString())
        }
        return strings
    }

    private fun parseSheet(input: InputStream, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = false
        val doc = dbf.newDocumentBuilder().parse(input)
        val rowList = doc.getElementsByTagName("row")
        for (i in 0 until rowList.length) {
            val rowElem = rowList.item(i) as Element
            val cList = rowElem.getElementsByTagName("c")
            val currentRow = mutableMapOf<Int, String>()
            var autoCol = 0
            for (j in 0 until cList.length) {
                val cElem = cList.item(j) as Element
                val ref = cElem.getAttribute("r")
                val type = cElem.getAttribute("t")
                val vNodes = cElem.getElementsByTagName("v")
                val tNodes = cElem.getElementsByTagName("t")
                val rawVal = if (vNodes.length > 0) vNodes.item(0).textContent else if (tNodes.length > 0) tNodes.item(0).textContent else ""
                val cellVal = when (type) {
                    "s" -> {
                        val idx = rawVal.toIntOrNull()
                        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else rawVal
                    }
                    "b" -> if (rawVal == "1") "TRUE" else "FALSE"
                    else -> rawVal
                }
                val colIndex = getColIndexFromRef(ref).let { if (it >= 0) it else autoCol }
                autoCol = colIndex + 1
                currentRow[colIndex] = cellVal
            }
            if (currentRow.isNotEmpty()) {
                val maxCol = currentRow.keys.maxOrNull() ?: -1
                if (maxCol >= 0) {
                    val list = List(maxCol + 1) { col -> currentRow[col] ?: "" }
                    if (list.any { it.isNotBlank() }) {
                        rows.add(list)
                    }
                }
            }
        }
        return rows
    }

    private fun getColIndexFromRef(ref: String): Int {
        if (ref.isBlank()) return -1
        var colStr = ""
        for (ch in ref) {
            if (ch.isLetter()) colStr += ch else break
        }
        if (colStr.isBlank()) return -1
        var index = 0
        for (ch in colStr.uppercase(Locale.ROOT)) {
            index = index * 26 + (ch - 'A' + 1)
        }
        return index - 1
    }
}
