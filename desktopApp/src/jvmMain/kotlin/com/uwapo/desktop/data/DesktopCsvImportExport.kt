package com.uwapo.desktop.data

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object DesktopCsvImportExport {
    data class ImportResult(val membersAdded: Int, val paymentsAdded: Int, val rowsSkipped: Int)

    private val MONTH_MAP = mapOf(
        "january" to 1, "jan" to 1, "januari" to 1,
        "february" to 2, "feb" to 2, "februari" to 2,
        "march" to 3, "mar" to 3, "machi" to 3,
        "april" to 4, "apr" to 4, "aprili" to 4,
        "may" to 5, "mei" to 5,
        "june" to 6, "jun" to 6, "juni" to 6,
        "july" to 7, "jul" to 7, "julai" to 7,
        "august" to 8, "aug" to 8, "agosti" to 8,
        "september" to 9, "sept" to 9, "sep" to 9, "septemba" to 9,
        "october" to 10, "oct" to 10, "oktoba" to 10,
        "november" to 11, "nov" to 11, "novemba" to 11,
        "december" to 12, "dec" to 12, "desemba" to 12
    )

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
                payment.method, payment.receiptNo, "Exported from UWAPO Desktop (${payment.syncStatus})").joinToString(",") { csv(it) }
        }
        val paidMemberIds = payments.map { it.memberId }.toSet()
        members.filter { it.id !in paidMemberIds }.forEach { member ->
            val joinedDate = java.time.Instant.ofEpochMilli(member.joinedAt).atZone(ZoneId.of("Africa/Dar_es_Salaam")).toLocalDate().toString()
            lines += listOf(member.memberNo, member.fullName, member.phone, joinedDate, member.status, "", "", "", "", "", "", "No payment row").joinToString(",") { csv(it) }
        }
        return lines.joinToString("\r\n") + "\r\n"
    }

    fun importFile(file: File, db: DesktopDatabase, targetStartYear: Int? = null): ImportResult {
        return file.inputStream().use { importStream(it, db, targetStartYear) }
    }

    fun importStream(input: InputStream, db: DesktopDatabase, targetStartYear: Int? = null): ImportResult {
        val sheets = parseInputSheets(input)
        if (sheets.isEmpty() || sheets.all { it.isEmpty() }) return ImportResult(0, 0, 0)

        val isMatrix = sheets.any { sheetHasMatrixHeader(it) }
        return if (isMatrix) {
            importMatrixLedger(sheets, db, targetStartYear)
        } else {
            importStandardRows(sheets.flatten(), db, targetStartYear)
        }
    }

    private fun sheetHasMatrixHeader(sheet: List<List<String>>): Boolean {
        for (row in sheet.take(50)) {
            var monthCount = 0
            for (cell in row) {
                if (MONTH_MAP.containsKey(cell.trim().lowercase(Locale.ROOT))) {
                    monthCount++
                }
            }
            if (monthCount >= 2) return true
        }
        return false
    }

    private fun normalizeMemberName(name: String): String {
        return name.replace(".", " ")
            .replace("-", " ")
            .replace("_", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .lowercase(Locale.ROOT)
    }

    private fun editDistance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    /** Same person written slightly differently (typos like MARUA/MARUWA, or a middle name shortened to an initial). */
    private fun namesLikelySame(a: String, b: String): Boolean {
        if (a.length >= 8 && b.length >= 8 && editDistance(a, b) <= 2) return true
        val ta = a.split(" ").filter { it.isNotBlank() }
        val tb = b.split(" ").filter { it.isNotBlank() }
        if (ta.size < 2 || tb.size < 2 || ta.size == tb.size && ta == tb) return ta == tb
        if (ta.first() != tb.first() || ta.last() != tb.last()) return false
        val midA = ta.drop(1).dropLast(1)
        val midB = tb.drop(1).dropLast(1)
        val (short, long) = if (midA.size <= midB.size) midA to midB else midB to midA
        return short.all { sm -> long.any { lm -> lm.startsWith(sm) || sm.startsWith(lm) } }
    }

    private fun findOrRegisterMember(
        existingMembers: MutableList<MemberEntity>,
        num: String,
        fullName: String,
        year: Int,
        phone: String = "",
        status: String = "active",
        db: DesktopDatabase
    ): Pair<MemberEntity, Boolean> {
        val normName = normalizeMemberName(fullName)
        val matchedByName = if (normName.isNotBlank()) {
            existingMembers.find { normalizeMemberName(it.fullName) == normName }
                ?: existingMembers.find { namesLikelySame(normalizeMemberName(it.fullName), normName) }
        } else null

        // Member numbers differ between sheets, so only use them when the row has no name.
        val matchedByNum = if (matchedByName == null && normName.isBlank() && num.isNotBlank()) {
            existingMembers.find { it.memberNo == num }
        } else null

        val matched = matchedByName ?: matchedByNum
        if (matched != null) {
            return Pair(matched, false)
        }

        var memberNo = num.ifBlank { fullName.replace(" ", "_").take(15) }
        if (existingMembers.any { it.memberNo == memberNo }) {
            val maxNo = existingMembers.mapNotNull { it.memberNo.toIntOrNull() }.maxOrNull() ?: 0
            memberNo = (maxNo + 1).toString()
        }
        val memberId = UUID.nameUUIDFromBytes("UWAPO-MEMBER:$memberNo".toByteArray(Charsets.UTF_8)).toString()
        val joinDate = LocalDate.of(year, 7, 1)
        val newMember = MemberEntity(
            id = memberId,
            memberNo = memberNo,
            fullName = fullName.ifBlank { memberNo },
            phone = phone,
            joinedAt = joinDate.atStartOfDay(ZoneId.of("Africa/Dar_es_Salaam")).toInstant().toEpochMilli(),
            status = status,
            syncStatus = "pending"
        )

        db.insertMember(newMember)
        existingMembers.add(newMember)
        return Pair(newMember, true)
    }

    private fun importMatrixLedger(sheets: List<List<List<String>>>, db: DesktopDatabase, targetStartYear: Int? = null): ImportResult {
        val year = targetStartYear ?: detectYear(sheets)
        db.refreshMembers()
        val existingMembers = db.members.value.toMutableList()
        var membersAdded = 0
        var paymentsAdded = 0
        val skipped = 0

        for ((sheetIdx, sheet) in sheets.withIndex()) {
            val detectedCols = mutableMapOf<Int, Int>()
            for (row in sheet.take(20)) {
                for ((cIdx, cell) in row.withIndex()) {
                    val m = MONTH_MAP[cell.trim().lowercase(Locale.ROOT)]
                    if (m != null && cIdx !in detectedCols) {
                        detectedCols[cIdx] = m
                    }
                }
            }

            // Only month-header columns are payments; the JUMLA (total) column must never be read as a month.
            val monthCols = detectedCols.toMutableMap()
            if (monthCols.isEmpty()) {
                val fallback = if (sheetIdx == 0) mapOf(1 to 1, 2 to 2, 3 to 3, 4 to 4, 5 to 5, 6 to 6)
                else mapOf(1 to 7, 2 to 8, 3 to 9, 4 to 10, 5 to 11, 6 to 12)
                monthCols.putAll(fallback)
            }

            if (monthCols.isEmpty()) continue

            var currentNum = ""
            var currentName = ""
            val blockMonthPayments = mutableMapOf<Int, Long>()

            fun commitBlock() {
                val name = currentName.ifBlank { currentNum }
                if (name.isNotBlank() && blockMonthPayments.isNotEmpty()) {
                    val (actualMember, isNew) = findOrRegisterMember(existingMembers, currentNum, name, year, db = db)
                    if (isNew) membersAdded++
                    val memberNo = actualMember.memberNo

                    for ((monthNum, amount) in blockMonthPayments) {
                        if (amount > 0) {
                            val paidDate = LocalDate.of(year, monthNum, 1)
                            val receipt = "IMPORT-$memberNo-$year-${monthNum.toString().padStart(2, '0')}"
                            val key = "$memberNo|$year|$monthNum|$amount|$paidDate|$receipt"
                            val paymentId = UUID.nameUUIDFromBytes("UWAPO-PAYMENT:$key".toByteArray(Charsets.UTF_8)).toString()
                            val payment = PaymentEntity(
                                paymentId, actualMember.id, amount, year, monthNum,
                                paidDate.atStartOfDay(ZoneId.of("Africa/Dar_es_Salaam")).toInstant().toEpochMilli(),
                                "cash", receipt, paymentId, "pending"
                            )
                            db.insertPayment(payment)
                            paymentsAdded++
                        }
                    }
                }
                currentNum = ""
                currentName = ""
                blockMonthPayments.clear()
            }

            for (row in sheet) {
                val rawCell = row.getOrNull(0)?.trim() ?: ""
                val rawUpper = rawCell.uppercase(Locale.ROOT)

                if (rawUpper in listOf("TOTAL", "JUMLA", "JUNE", "JINA", "ADA", "JUMLA ADA", "JULY", "AUG", "SEPT", "OCT", "NOV", "DEC")) {
                    if (rawUpper in listOf("TOTAL", "JUMLA", "JUMLA ADA")) {
                        commitBlock()
                    }
                    continue
                }

                val (num, name) = parseMemberCell(rawCell)
                val isNumRow = rawCell.matches(Regex("""^\d+\.?$"""))
                val isNameRow = rawCell.isNotBlank() && !isNumRow && rawCell.length > 1

                if (isNumRow || isNameRow) {
                    if ((isNumRow && currentNum.isNotBlank() && currentNum != num) ||
                        (isNameRow && currentName.isNotBlank() && currentName != name)) {
                        commitBlock()
                    }
                    if (num.isNotBlank()) currentNum = num
                    if (name.isNotBlank()) currentName = name
                }

                for ((cIdx, monthNum) in monthCols) {
                    val amtStr = row.getOrNull(cIdx)?.replace(",", "")?.trim() ?: ""
                    val amount = amtStr.toDoubleOrNull()?.toLong() ?: 0L
                    if (amount > 0) {
                        val prev = blockMonthPayments[monthNum] ?: 0L
                        blockMonthPayments[monthNum] = maxOf(prev, amount)
                    }
                }
            }

            commitBlock()
        }
        return ImportResult(membersAdded, paymentsAdded, skipped)
    }

    private fun importStandardRows(rows: List<List<String>>, db: DesktopDatabase, targetStartYear: Int? = null): ImportResult {
        if (rows.isEmpty()) return ImportResult(0, 0, 0)
        val header = rows.first().map { it.trim().removePrefix("\uFEFF").lowercase(Locale.ROOT) }
        fun index(vararg names: String): Int = header.indexOfFirst { it in names }
        val iNo = index("member_no", "member number", "membership number", "member_id", "member id", "no", "no.")
        var iName = index("full_name", "name", "member name", "full name", "jina")
        if (iName < 0 && iNo >= 0) iName = iNo
        if (iNo < 0 && iName < 0) throw IllegalArgumentException("File must contain member_no or full_name columns.")

        val iPhone = index("phone", "phone_number", "phone number", "mobile")
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

        db.refreshMembers()
        val existingMembers = db.members.value.toMutableList()
        rows.drop(1).forEach { row ->
            fun value(i: Int) = if (i >= 0 && i < row.size) row[i].trim() else ""
            val rawName = value(iName)
            val rawNo = value(iNo)
            val (numFromCell, nameFromCell) = parseMemberCell(if (rawName.isNotBlank()) rawName else rawNo)
            val memberNo = cleanStr(if (rawNo.isNotBlank()) rawNo else numFromCell)
            val fullName = if (rawName.isNotBlank() && iName != iNo) rawName else nameFromCell

            if (memberNo.isBlank() && fullName.isBlank()) { skipped++; return@forEach }
            val defaultJoinYear = targetStartYear ?: LocalDate.now().year
            val (actualMember, isNew) = findOrRegisterMember(
                existingMembers = existingMembers,
                num = memberNo,
                fullName = fullName.ifBlank { memberNo },
                year = defaultJoinYear,
                phone = cleanStr(value(iPhone)),
                status = value(iStatus).ifBlank { "active" }.lowercase(),
                db = db
            )
            if (isNew) membersAdded++

            val actualNo = actualMember.memberNo
            val amount = value(iAmount).replace(",", "").replace("TZS", "", ignoreCase = true).trim().let { it.toDoubleOrNull()?.toLong() ?: it.toLongOrNull() } ?: 0L
            val year = cleanStr(value(iYear)).toIntOrNull() ?: targetStartYear
            val month = cleanStr(value(iMonth)).toIntOrNull()
            if (amount > 0 && year != null && year in 2000..2100 && month != null && month in 1..12) {
                val paidDate = parseDate(value(iPaidAt)) ?: LocalDate.of(year, month, 1)
                val method = value(iMethod).ifBlank { "cash" }.lowercase().replace(' ', '_')
                val receipt = value(iReceipt).ifBlank { "IMPORT-$actualNo-$year-${month.toString().padStart(2,'0')}" }
                val key = "$actualNo|$year|$month|$amount|$paidDate|$receipt"
                val paymentId = UUID.nameUUIDFromBytes("UWAPO-PAYMENT:$key".toByteArray(Charsets.UTF_8)).toString()
                val payment = PaymentEntity(paymentId, actualMember.id, amount, year, month,
                    paidDate.atStartOfDay(ZoneId.of("Africa/Dar_es_Salaam")).toInstant().toEpochMilli(),
                    if (method in listOf("cash", "bank", "mobile_money", "other")) method else "other",
                    receipt, paymentId, "pending")
                db.insertPayment(payment)
                paymentsAdded++
            }
        }
        return ImportResult(membersAdded, paymentsAdded, skipped)
    }

    private fun detectYear(sheets: List<List<List<String>>>): Int {
        val yearRegex = Regex("""\b(20\d\d)\b""")
        for (sheet in sheets) {
            for (row in sheet.take(5)) {
                for (cell in row) {
                    val m = yearRegex.find(cell)
                    if (m != null) {
                        val y = m.groupValues[1].toIntOrNull()
                        if (y != null && y in 2000..2100) return y
                    }
                }
            }
        }
        return LocalDate.now().year
    }

    private fun parseMemberCell(raw: String): Pair<String, String> {
        val clean = raw.replace("\n", " ").replace("\r", " ").trim()
        if (clean.isBlank()) return Pair("", "")
        val upper = clean.uppercase(Locale.ROOT)
        if (upper == "TOTAL" || upper == "JUMLA" || upper == "JINA" || upper == "ADA") return Pair("", "")

        var i = 0
        while (i < clean.length && clean[i].isDigit()) {
            i++
        }
        if (i > 0) {
            val num = clean.substring(0, i)
            var rest = clean.substring(i)
            while (rest.startsWith(".") || rest.startsWith(" ") || rest.startsWith("-")) {
                rest = rest.substring(1).trim()
            }
            if (rest.isNotBlank()) {
                return Pair(num, rest)
            }
        }
        return Pair("", clean)
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

    private fun parseInputSheets(input: InputStream): List<List<List<String>>> {
        val bytes = input.readBytes()
        val isZip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
        return if (isZip) {
            parseXlsxSheets(bytes)
        } else {
            listOf(parseCsv(ByteArrayInputStream(bytes).bufferedReader(Charsets.UTF_8).readText()))
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

    private fun parseXlsxSheets(bytes: ByteArray): List<List<List<String>>> {
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

        val sheets = mutableListOf<List<List<String>>>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase(Locale.ROOT)
                    if (name.contains("xl/worksheets/sheet") && name.endsWith(".xml")) {
                        val entryBytes = zip.readBytes()
                        val rows = parseSheet(ByteArrayInputStream(entryBytes), sharedStrings)
                        if (rows.isNotEmpty()) {
                            sheets.add(rows)
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sheets
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
