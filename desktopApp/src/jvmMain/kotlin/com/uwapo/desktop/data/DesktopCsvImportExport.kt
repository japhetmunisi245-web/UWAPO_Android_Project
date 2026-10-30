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

    /**
     * What an import WOULD do. Building a plan reads the file and the database but writes nothing,
     * so the user can review it (and reconcile it against the sheet's own totals) before committing.
     */
    data class ImportPlan(
        val newMembers: List<MemberEntity>,
        val payments: List<PaymentEntity>,
        val paymentsAlreadyInDb: Int,
        val notes: List<String>,
        val warnings: List<String>,
        val sheetTotal: Long?,
        val rowsSkipped: Int
    ) {
        val computedTotal: Long get() = payments.sumOf { it.amountTzs }
        val newPayments: Int get() = payments.size - paymentsAlreadyInDb
        /** True when there is no sheet total to compare against, or when it equals what the import computes. */
        val reconciles: Boolean get() = sheetTotal == null || sheetTotal == computedTotal
        val isEmpty: Boolean get() = newMembers.isEmpty() && payments.isEmpty()
    }

    private const val LARGE_AMOUNT = 20_000L

    private fun emptyPlan(message: String) = ImportPlan(emptyList(), emptyList(), 0, emptyList(), listOf(message), null, 0)

    fun planFile(file: File, db: DesktopDatabase, targetStartYear: Int? = null): ImportPlan {
        return file.inputStream().use { planStream(it, db, targetStartYear) }
    }

    fun planStream(input: InputStream, db: DesktopDatabase, targetStartYear: Int? = null): ImportPlan {
        val sheets = parseInputSheets(input)
        if (sheets.isEmpty() || sheets.all { it.isEmpty() }) return emptyPlan("The file has no readable rows.")

        val isMatrix = sheets.any { sheetHasMatrixHeader(it) }
        return if (isMatrix) {
            planMatrixLedger(sheets, db, targetStartYear)
        } else {
            planStandardRows(sheets.flatten(), db, targetStartYear)
        }
    }

    /** Saves a reviewed plan: one backup, one transaction, one screen refresh. */
    fun commit(plan: ImportPlan, db: DesktopDatabase): ImportResult {
        val (members, payments) = db.importBatch(plan.newMembers, plan.payments)
        return ImportResult(members, payments, plan.rowsSkipped)
    }

    fun importFile(file: File, db: DesktopDatabase, targetStartYear: Int? = null): ImportResult =
        commit(planFile(file, db, targetStartYear), db)

    fun importStream(input: InputStream, db: DesktopDatabase, targetStartYear: Int? = null): ImportResult =
        commit(planStream(input, db, targetStartYear), db)

    private fun tzs(amount: Long): String = "TZS " + java.text.NumberFormat.getIntegerInstance(Locale.US).format(amount)

    private fun parseAmount(cell: String?): Long =
        cell?.replace(",", "")?.replace("TZS", "", ignoreCase = true)?.trim()?.toDoubleOrNull()?.toLong() ?: 0L

    private fun monthShort(month: Int): String =
        java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)

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

    private fun editSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val row = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var diagonal = row[0]
            row[0] = i
            for (j in 1..b.length) {
                val above = row[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                row[j] = minOf(row[j] + 1, row[j - 1] + 1, diagonal + cost)
                diagonal = above
            }
        }
        return 1.0 - row[b.length].toDouble() / maxOf(a.length, b.length)
    }

    /** True when two normalized names are the same person written slightly differently (typo, initial vs full middle name). */
    private fun namesMatch(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        if (editSimilarity(a, b) >= 0.88) return true
        val ta = a.split(" ")
        val tb = b.split(" ")
        if (ta.size < 2 || tb.size < 2) return false
        if (ta.first() != tb.first() || ta.last() != tb.last()) return false
        val ma = ta.drop(1).dropLast(1)
        val mb = tb.drop(1).dropLast(1)
        if (ma.isEmpty() || mb.isEmpty()) return true
        return ma.size == mb.size && ma.zip(mb).all { (x, y) ->
            x == y || (x.length == 1 && y.startsWith(x)) || (y.length == 1 && x.startsWith(y))
        }
    }

    private fun nextFreeMemberNo(existingMembers: List<MemberEntity>): String {
        val used = existingMembers.map { it.memberNo }.toSet()
        var candidate = (existingMembers.mapNotNull { it.memberNo.toIntOrNull() }.maxOrNull() ?: 0) + 1
        while (candidate.toString() in used) candidate++
        return candidate.toString()
    }

    /**
     * [numberIsStable] = true for CSV/standard files where member_no is a real ID.
     * Ledger sheets number rows by position and renumber between sheets, so there the
     * number must never be used to decide who a row belongs to.
     * Nothing is written here: new members are only collected into [newMembers].
     */
    private fun findOrRegisterMember(
        existingMembers: MutableList<MemberEntity>,
        newMembers: MutableList<MemberEntity>,
        notes: MutableList<String>,
        num: String,
        fullName: String,
        year: Int,
        phone: String = "",
        status: String = "active",
        numberIsStable: Boolean = true
    ): Pair<MemberEntity, Boolean> {
        val normName = normalizeMemberName(fullName)
        val matchedByName = if (normName.isNotBlank()) {
            existingMembers.find { normalizeMemberName(it.fullName) == normName }
                ?: existingMembers.find { namesMatch(normalizeMemberName(it.fullName), normName) }
        } else null

        val matchedByNum = if (matchedByName == null && numberIsStable && num.isNotBlank()) {
            existingMembers.find { it.memberNo == num }
        } else null

        val matched = matchedByName ?: matchedByNum
        if (matched != null) {
            if (matchedByName != null && normalizeMemberName(matched.fullName) != normName) {
                notes.add("\"${fullName.trim()}\" treated as the same person as \"${matched.fullName.trim()}\" (No. ${matched.memberNo})")
            }
            return Pair(matched, false)
        }

        var memberNo = num.ifBlank { fullName.replace(" ", "_").take(15) }
        if (!numberIsStable && existingMembers.any { it.memberNo == memberNo }) {
            val reassigned = nextFreeMemberNo(existingMembers)
            notes.add("New member \"${fullName.trim()}\": number $memberNo already belongs to someone else, assigned No. $reassigned")
            memberNo = reassigned
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

        existingMembers.add(newMember)
        newMembers.add(newMember)
        return Pair(newMember, true)
    }

    private fun buildWarnings(
        warnings: MutableList<String>,
        largeAmounts: List<String>,
        rowMismatches: List<String>
    ) {
        if (rowMismatches.isNotEmpty()) {
            warnings.add(
                "${rowMismatches.size} row(s) where the sheet's own row total does not match its months: " +
                    rowMismatches.take(5).joinToString("; ") + if (rowMismatches.size > 5) "; ..." else ""
            )
        }
        if (largeAmounts.isNotEmpty()) {
            warnings.add(
                "${largeAmounts.size} payment(s) above ${tzs(LARGE_AMOUNT)} for a single month (usual is TZS 10,000): " +
                    largeAmounts.take(5).joinToString("; ") + if (largeAmounts.size > 5) "; ..." else ""
            )
        }
    }

    private fun planMatrixLedger(sheets: List<List<List<String>>>, db: DesktopDatabase, targetStartYear: Int? = null): ImportPlan {
        val year = targetStartYear ?: detectYear(sheets)
        db.refreshMembers()
        val existingMembers = db.members.value.toMutableList()
        val newMembers = mutableListOf<MemberEntity>()
        val notes = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val payments = LinkedHashMap<String, PaymentEntity>()
        val largeAmounts = mutableListOf<String>()
        val rowMismatches = mutableListOf<String>()
        var sheetTotalSum = 0L
        var sheetsWithTotal = 0
        var sheetsRead = 0

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

            // Columns headed JUMLA / TOTAL hold row sums, never a month's payment.
            val totalCols = mutableSetOf<Int>()
            for (row in sheet.take(20)) {
                for ((cIdx, cell) in row.withIndex()) {
                    if (cIdx > 0 && cell.trim().uppercase(Locale.ROOT) in listOf("JUMLA", "JUMLA ADA", "TOTAL")) totalCols.add(cIdx)
                }
            }

            val monthCols = detectedCols.filterKeys { it !in totalCols }.toMutableMap()
            // Positional fallbacks only fill months whose header was NOT found, and never a total column.
            val fallbackMap = when (sheetIdx) {
                0 -> mapOf(3 to 1, 4 to 2, 5 to 3, 6 to 4, 7 to 5, 2 to 6)
                1 -> mapOf(2 to 7, 3 to 8, 4 to 9, 5 to 10, 6 to 11, 7 to 12)
                else -> emptyMap()
            }
            for ((c, m) in fallbackMap) {
                if (c !in monthCols && c !in totalCols && m !in monthCols.values) monthCols[c] = m
            }

            if (monthCols.isEmpty()) continue
            sheetsRead++
            var foundTotalRow = false

            var currentNum = ""
            var currentName = ""
            val blockMonthPayments = mutableMapOf<Int, Long>()

            fun commitBlock() {
                val name = currentName.ifBlank { currentNum }
                // A named member with no payments still must exist - they are exactly who shows up in arrears.
                if (name.isNotBlank() && (blockMonthPayments.isNotEmpty() || currentName.isNotBlank())) {
                    val (actualMember, _) = findOrRegisterMember(
                        existingMembers, newMembers, notes, currentNum, name, year, numberIsStable = false
                    )
                    val memberNo = actualMember.memberNo

                    for ((monthNum, amount) in blockMonthPayments) {
                        if (amount > 0) {
                            val paidDate = LocalDate.of(year, monthNum, 1)
                            val receipt = "IMPORT-$memberNo-$year-${monthNum.toString().padStart(2, '0')}"
                            val key = "$memberNo|$year|$monthNum|$amount|$paidDate|$receipt"
                            val paymentId = UUID.nameUUIDFromBytes("UWAPO-PAYMENT:$key".toByteArray(Charsets.UTF_8)).toString()
                            payments[paymentId] = PaymentEntity(
                                paymentId, actualMember.id, amount, year, monthNum,
                                paidDate.atStartOfDay(ZoneId.of("Africa/Dar_es_Salaam")).toInstant().toEpochMilli(),
                                "cash", receipt, paymentId, "pending"
                            )
                            if (amount > LARGE_AMOUNT) {
                                largeAmounts.add("${actualMember.fullName.trim()} ${monthShort(monthNum)} ${tzs(amount)}")
                            }
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
                        // The sheet's own grand total: its total column, or else the month cells, of this row.
                        val reported = totalCols.maxOfOrNull { parseAmount(row.getOrNull(it)) }?.takeIf { it > 0 }
                            ?: monthCols.keys.sumOf { parseAmount(row.getOrNull(it)) }
                        if (reported > 0) {
                            sheetTotalSum += reported
                            foundTotalRow = true
                        }
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

                var rowMonthSum = 0L
                for ((cIdx, monthNum) in monthCols) {
                    val amount = parseAmount(row.getOrNull(cIdx))
                    rowMonthSum += amount
                    if (amount > 0) {
                        val prev = blockMonthPayments[monthNum] ?: 0L
                        blockMonthPayments[monthNum] = maxOf(prev, amount)
                    }
                }

                // Single-row members: the sheet's own JUMLA for the row should equal the sum of its months.
                if (isNameRow && num.isNotBlank() && totalCols.isNotEmpty()) {
                    val reportedRow = totalCols.maxOf { parseAmount(row.getOrNull(it)) }
                    if (reportedRow != rowMonthSum && (reportedRow > 0 || rowMonthSum > 0)) {
                        rowMismatches.add("${name.trim()} (sheet ${tzs(reportedRow)}, months ${tzs(rowMonthSum)})")
                    }
                }
            }

            commitBlock()
            if (foundTotalRow) sheetsWithTotal++
        }

        buildWarnings(warnings, largeAmounts, rowMismatches)
        val existingIds = db.existingPaymentIds()
        val sheetTotal = if (sheetsRead > 0 && sheetsWithTotal == sheetsRead) sheetTotalSum else null
        return ImportPlan(
            newMembers = newMembers,
            payments = payments.values.toList(),
            paymentsAlreadyInDb = payments.keys.count { it in existingIds },
            notes = notes.distinct(),
            warnings = warnings,
            sheetTotal = sheetTotal,
            rowsSkipped = 0
        )
    }

    private fun planStandardRows(rows: List<List<String>>, db: DesktopDatabase, targetStartYear: Int? = null): ImportPlan {
        if (rows.isEmpty()) return emptyPlan("The file has no readable rows.")
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

        var skipped = 0
        db.refreshMembers()
        val existingMembers = db.members.value.toMutableList()
        val newMembers = mutableListOf<MemberEntity>()
        val notes = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val payments = LinkedHashMap<String, PaymentEntity>()
        val largeAmounts = mutableListOf<String>()

        rows.drop(1).forEach { row ->
            fun value(i: Int) = if (i >= 0 && i < row.size) row[i].trim() else ""
            val rawName = value(iName)
            val rawNo = value(iNo)
            val (numFromCell, nameFromCell) = parseMemberCell(if (rawName.isNotBlank()) rawName else rawNo)
            val memberNo = cleanStr(if (rawNo.isNotBlank()) rawNo else numFromCell)
            val fullName = if (rawName.isNotBlank() && iName != iNo) rawName else nameFromCell

            if (memberNo.isBlank() && fullName.isBlank()) { skipped++; return@forEach }
            val defaultJoinYear = targetStartYear ?: LocalDate.now().year
            val (actualMember, _) = findOrRegisterMember(
                existingMembers = existingMembers,
                newMembers = newMembers,
                notes = notes,
                num = memberNo,
                fullName = fullName.ifBlank { memberNo },
                year = defaultJoinYear,
                phone = cleanStr(value(iPhone)),
                status = value(iStatus).ifBlank { "active" }.lowercase()
            )

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
                payments[paymentId] = PaymentEntity(
                    paymentId, actualMember.id, amount, year, month,
                    paidDate.atStartOfDay(ZoneId.of("Africa/Dar_es_Salaam")).toInstant().toEpochMilli(),
                    if (method in listOf("cash", "bank", "mobile_money", "other")) method else "other",
                    receipt, paymentId, "pending"
                )
                if (amount > LARGE_AMOUNT) largeAmounts.add("${actualMember.fullName.trim()} ${monthShort(month)} ${tzs(amount)}")
            } else if (amount > 0) {
                skipped++
            }
        }

        buildWarnings(warnings, largeAmounts, emptyList())
        if (skipped > 0) warnings.add("$skipped row(s) were skipped (blank, or amount without a valid year/month).")
        val existingIds = db.existingPaymentIds()
        return ImportPlan(
            newMembers = newMembers,
            payments = payments.values.toList(),
            paymentsAlreadyInDb = payments.keys.count { it in existingIds },
            notes = notes.distinct(),
            warnings = warnings,
            sheetTotal = null,
            rowsSkipped = skipped
        )
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

        val numbered = mutableListOf<Pair<Int, List<List<String>>>>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase(Locale.ROOT)
                    if (name.contains("xl/worksheets/sheet") && name.endsWith(".xml")) {
                        val entryBytes = zip.readBytes()
                        val rows = parseSheet(ByteArrayInputStream(entryBytes), sharedStrings)
                        if (rows.isNotEmpty()) {
                            val sheetNumber = Regex("""sheet(\d+)\.xml$""").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
                            numbered.add(sheetNumber to rows)
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return numbered.sortedBy { it.first }.map { it.second }
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
