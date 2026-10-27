package com.uwapo.app.data

import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

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
        val rows = parse(input.bufferedReader(Charsets.UTF_8).readText())
        if (rows.isEmpty()) return ImportResult(0, 0, 0)
        val header = rows.first().map { it.trim().removePrefix("\uFEFF").lowercase(Locale.ROOT) }
        fun index(vararg names: String): Int = header.indexOfFirst { it in names }
        val iNo = index("member_no", "member number", "membership number")
        val iName = index("full_name", "name", "member name")
        if (iNo < 0 || iName < 0) throw IllegalArgumentException("CSV must contain member_no and full_name columns.")
        val iPhone = index("phone", "phone_number")
        val iJoin = index("join_date", "joined_at")
        val iStatus = index("status")
        val iYear = index("period_year", "year")
        val iMonth = index("period_month", "month")
        val iAmount = index("amount_paid_tzs", "amount", "paid")
        val iPaidAt = index("paid_at", "payment_date")
        val iMethod = index("payment_method", "method")
        val iReceipt = index("receipt_reference", "receipt_no", "receipt")
        var membersAdded = 0
        var paymentsAdded = 0
        var skipped = 0
        rows.drop(1).forEach { row ->
            fun value(i: Int) = if (i >= 0 && i < row.size) row[i].trim() else ""
            val memberNo = value(iNo)
            val fullName = value(iName)
            if (memberNo.isBlank() || fullName.isBlank()) { skipped++; return@forEach }
            val joinDate = parseDate(value(iJoin)) ?: LocalDate.now()
            val memberId = UUID.nameUUIDFromBytes("UWAPO-MEMBER:$memberNo".toByteArray(Charsets.UTF_8)).toString()
            val member = MemberEntity(memberId, memberNo, fullName, value(iPhone), joinDate.atStartOfDay(ZoneId.of("Africa/Dar_es_Salaam")).toInstant().toEpochMilli(), value(iStatus).ifBlank { "active" }.lowercase(), "pending")
            val inserted = dao.insertMemberIgnore(member)
            if (inserted != -1L) membersAdded++
            val actualMember = dao.getMemberByNumber(memberNo) ?: run { skipped++; return@forEach }
            val amount = value(iAmount).replace(",", "").toLongOrNull() ?: 0L
            val year = value(iYear).toIntOrNull()
            val month = value(iMonth).toIntOrNull()
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

    private fun parseDate(value: String): LocalDate? = try {
        if (value.isBlank()) null else LocalDate.parse(value.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: Exception) { null }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private fun parse(text: String): List<List<String>> {
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
}
