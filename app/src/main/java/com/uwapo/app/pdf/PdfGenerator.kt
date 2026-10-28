package com.uwapo.app.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.uwapo.app.data.MemberEntity
import com.uwapo.app.data.PaymentEntity
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {

    private fun money(amount: Long): String =
        "TZS " + NumberFormat.getIntegerInstance(Locale("en", "TZ")).format(amount)

    fun generateReceiptPdf(
        context: Context,
        payment: PaymentEntity,
        memberName: String,
        memberNo: String
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 standard (72 dpi)
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // Primary Header Box
        paint.color = Color.parseColor("#17365D")
        canvas.drawRect(0f, 0f, 595f, 110f, paint)

        // App Title
        paint.color = Color.WHITE
        paint.textSize = 26f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("UWAPO MEMBERSHIP CLUB", 40f, 50f, paint)

        paint.textSize = 14f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Official Payment Receipt / Risiti ya Malipo", 40f, 80f, paint)

        // Receipt Details Section
        paint.color = Color.BLACK
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("RECEIPT NO: ${payment.receiptNo}", 40f, 150f, paint)

        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(payment.paidAt))
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Date / Tarehe: $dateStr", 350f, 150f, paint)

        // Divider Line
        paint.color = Color.LTGRAY
        paint.strokeWidth = 2f
        canvas.drawLine(40f, 170f, 555f, 170f, paint)

        // Member Information Box
        paint.color = Color.parseColor("#F2F4F7")
        canvas.drawRoundRect(40f, 190f, 555f, 270f, 12f, 12f, paint)

        paint.color = Color.parseColor("#17365D")
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("MEMBER DETAILS / TAARIFA ZA MWANACHAMA", 55f, 215f, paint)

        paint.color = Color.BLACK
        paint.textSize = 14f
        canvas.drawText("Name / Jina: $memberName", 55f, 242f, paint)
        canvas.drawText("Member No / Namba: $memberNo", 350f, 242f, paint)

        // Contribution Information Table
        paint.color = Color.parseColor("#17365D")
        canvas.drawRect(40f, 300f, 555f, 330f, paint)

        paint.color = Color.WHITE
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("DESCRIPTION / MAELEZO", 55f, 320f, paint)
        canvas.drawText("PERIOD / MWEZI", 300f, 320f, paint)
        canvas.drawText("AMOUNT / KIASI", 440f, 320f, paint)

        paint.color = Color.BLACK
        paint.textSize = 13f
        paint.typeface = Typeface.DEFAULT
        val periodStr = "${payment.periodMonth.toString().padStart(2, '0')}/${payment.periodYear}"
        canvas.drawText("Monthly Contribution / Mchango wa Mwezi", 55f, 360f, paint)
        canvas.drawText(periodStr, 300f, 360f, paint)
        canvas.drawText(money(payment.amountTzs), 440f, 360f, paint)

        // Divider
        paint.color = Color.LTGRAY
        canvas.drawLine(40f, 390f, 555f, 390f, paint)

        // Summary Section
        paint.color = Color.BLACK
        paint.textSize = 13f
        canvas.drawText("Payment Method / Njia ya Malipo: ${payment.method.replace('_', ' ').uppercase()}", 40f, 420f, paint)
        canvas.drawText("Status / Hali: ${payment.syncStatus.uppercase()}", 40f, 445f, paint)

        paint.color = Color.parseColor("#17365D")
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOTAL PAID: ${money(payment.amountTzs)}", 300f, 435f, paint)

        // Footer Box
        paint.color = Color.parseColor("#E8EEF5")
        canvas.drawRect(40f, 500f, 555f, 560f, paint)

        paint.color = Color.parseColor("#17365D")
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Thank you for your prompt contribution! / Ahsante kwa mchango wako!", 65f, 535f, paint)

        document.finishPage(page)

        val file = File(context.cacheDir, "receipt_${payment.receiptNo}.pdf")
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
        return file
    }

    fun generateStatementPdf(
        context: Context,
        member: MemberEntity,
        payments: List<PaymentEntity>
    ): File {
        val document = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true }
        val orderedPayments = payments.sortedBy { it.paidAt }
        val totalPaid = orderedPayments.filter { it.syncStatus != "reversed" }.sumOf { it.amountTzs }
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvasRef: android.graphics.Canvas? = null
        fun canvas(): android.graphics.Canvas = checkNotNull(canvasRef)
        var y = 0f

        fun drawTableHeader() {
            paint.color = Color.parseColor("#17365D")
            canvas().drawRect(40f, y, 555f, y + 24f, paint)
            paint.color = Color.WHITE
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas().drawText("DATE", 48f, y + 16f, paint)
            canvas().drawText("PERIOD", 130f, y + 16f, paint)
            canvas().drawText("METHOD", 205f, y + 16f, paint)
            canvas().drawText("RECEIPT", 300f, y + 16f, paint)
            canvas().drawText("STATUS", 410f, y + 16f, paint)
            canvas().drawText("AMOUNT TZS", 475f, y + 16f, paint)
            y += 24f
            paint.color = Color.BLACK
            paint.textSize = 9f
            paint.typeface = Typeface.DEFAULT
        }


        fun startPage() {
            if (pageNumber > 0) page?.let { document.finishPage(it) }
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            canvasRef = page!!.canvas
            paint.color = Color.parseColor("#17365D")
            canvas().drawRect(0f, 0f, 595f, 105f, paint)
            paint.color = Color.WHITE
            paint.textSize = 21f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas().drawText("UWAPO - MEMBER STATEMENT", 40f, 42f, paint)
            paint.textSize = 12f
            paint.typeface = Typeface.DEFAULT
            canvas().drawText("Taarifa ya Michango ya Mwanachama", 40f, 68f, paint)
            paint.textSize = 10f
            canvas().drawText("Generated: ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())}", 40f, 88f, paint)
            paint.color = Color.LTGRAY
            canvas().drawLine(40f, 802f, 555f, 802f, paint)
            paint.color = Color.GRAY
            paint.textSize = 9f
            canvas().drawText("UWAPO • Local records • Amounts in TZS", 40f, 820f, paint)
            canvas().drawText("Page $pageNumber", 505f, 820f, paint)
            y = 125f
            paint.color = Color.BLACK
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas().drawText("${member.fullName} (${member.memberNo})", 40f, y, paint)
            y += 18f
            paint.textSize = 10f
            paint.typeface = Typeface.DEFAULT
            canvas().drawText("Phone: ${member.phone.ifBlank { "N/A" }}    Status: ${member.status.uppercase()}", 40f, y, paint)
            y += 18f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas().drawText("Total recorded (excluding reversals): ${money(totalPaid)}", 40f, y, paint)
            y += 24f
            drawTableHeader()
        }


        try {
            startPage()
            if (orderedPayments.isEmpty()) {
                paint.textSize = 11f
                canvas().drawText("No payment records for this member.", 48f, y + 18f, paint)
            } else {
                orderedPayments.forEach { payment ->
                    if (y + 20f > 785f) {
                        startPage()
                    }
                    val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(payment.paidAt))
                    val period = "${payment.periodMonth.toString().padStart(2, '0')}/${payment.periodYear}"
                    canvas().drawText(date, 48f, y + 13f, paint)
                    canvas().drawText(period, 130f, y + 13f, paint)
                    canvas().drawText(payment.method.replace('_', ' ').take(13), 205f, y + 13f, paint)
                    canvas().drawText(payment.receiptNo.take(17), 300f, y + 13f, paint)
                    canvas().drawText(payment.syncStatus.uppercase().take(10), 410f, y + 13f, paint)
                    canvas().drawText(money(payment.amountTzs).take(16), 475f, y + 13f, paint)
                    paint.color = Color.parseColor("#D8DEE8")
                    canvas().drawLine(40f, y + 18f, 555f, y + 18f, paint)
                    paint.color = Color.BLACK
                    y += 20f
                }
            }
            document.finishPage(page)
            val safeMember = member.memberNo.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val file = File(context.cacheDir, "statement_${safeMember}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { out -> document.writeTo(out) }
            return file
        } finally {
            document.close()
        }
    }

    fun generateReportPdf(
        context: Context,
        reportTitle: String,
        monthTotal: Long,
        yearlyTotal: Long,
        expensesTotal: Long,
        memberCount: Int,
        defaulters: List<MemberEntity>,
        batchBreakdown: Map<String, Long> = emptyMap(),
        periodLabel: String = "Current reporting period",
        periodCollectionsTzs: Long = yearlyTotal
    ): File {
        val document = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true }
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvasRef: android.graphics.Canvas? = null
        fun canvas(): android.graphics.Canvas = checkNotNull(canvasRef)
        var y = 0f

        fun startPage() {
            if (pageNumber > 0) page?.let { document.finishPage(it) }
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            canvasRef = page!!.canvas
            paint.color = Color.parseColor("#17365D")
            canvas().drawRect(0f, 0f, 595f, 105f, paint)
            paint.color = Color.WHITE
            paint.textSize = 20f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas().drawText("UWAPO MEMBERSHIP CLUB", 40f, 37f, paint)
            paint.textSize = 14f
            canvas().drawText(reportTitle.take(62), 40f, 60f, paint)
            paint.textSize = 10f
            paint.typeface = Typeface.DEFAULT
            val generated = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
            canvas().drawText("Period: $periodLabel", 40f, 80f, paint)
            canvas().drawText("Generated: $generated", 40f, 95f, paint)
            paint.color = Color.LTGRAY
            canvas().drawLine(40f, 802f, 555f, 802f, paint)
            paint.color = Color.GRAY
            paint.textSize = 9f
            canvas().drawText("UWAPO • Local records report • Amounts in TZS", 40f, 820f, paint)
            canvas().drawText("Page $pageNumber", 505f, 820f, paint)
            y = 128f
        }

        fun sectionHeader(title: String) {
            if (y + 32f > 785f) startPage()
            paint.color = Color.parseColor("#17365D")
            canvas().drawRoundRect(40f, y, 555f, y + 26f, 4f, 4f, paint)
            paint.color = Color.WHITE
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas().drawText(title.take(78), 52f, y + 17f, paint)
            paint.typeface = Typeface.DEFAULT
            y += 34f
        }

        fun tableRow(left: String, right: String, rowHeight: Float = 22f) {
            if (y + rowHeight > 785f) startPage()
            paint.color = Color.BLACK
            paint.textSize = 11f
            paint.typeface = Typeface.DEFAULT
            canvas().drawText(left.take(62), 52f, y + 14f, paint)
            canvas().drawText(right.take(26), 365f, y + 14f, paint)
            paint.color = Color.parseColor("#D8DEE8")
            canvas().drawLine(48f, y + rowHeight, 547f, y + rowHeight, paint)
            y += rowHeight
        }

        try {
            startPage()

        sectionHeader("EXECUTIVE SUMMARY / MUHTASARI")
        tableRow("Reporting period", periodLabel)
        tableRow("Registered members", memberCount.toString())
        tableRow("Collections for selected period", money(periodCollectionsTzs))
        tableRow("Collections recorded year-to-date", money(yearlyTotal))
        tableRow("Approved / paid expenses for period", money(expensesTotal))
        tableRow("Net movement (opening balance excluded)", money(periodCollectionsTzs - expensesTotal))
        y += 12f

        sectionHeader("BATCH COLLECTIONS / MICHANGO KWA BATCH")
        if (batchBreakdown.isEmpty()) tableRow("No batch collection records for this report", "—")
        else batchBreakdown.toSortedMap().forEach { (batchLabel, collected) -> tableRow("Batch $batchLabel", money(collected)) }
        y += 12f

        sectionHeader("CURRENT-MONTH ARREARS / WANACHAMA AMBAO HAWAJALIPA")
        tableRow("Members outstanding for current month", defaulters.size.toString())
        if (defaulters.isEmpty()) {
            tableRow("All active members are recorded as paid this month", "—")
        } else {
            defaulters.forEachIndexed { index, member ->
                if (y + 26f > 785f) {
                    startPage()
                    sectionHeader("CURRENT-MONTH ARREARS / CONTINUED")
                }
                paint.color = Color.BLACK
                paint.textSize = 10f
                canvas().drawText("${index + 1}. ${member.fullName} (${member.memberNo})".take(64), 52f, y + 12f, paint)
                canvas().drawText(member.phone.ifBlank { "No phone recorded" }.take(30), 330f, y + 12f, paint)
                paint.color = Color.parseColor("#D8DEE8")
                canvas().drawLine(48f, y + 20f, 547f, y + 20f, paint)
                y += 22f
            }
        }
        y += 18f
        if (y + 70f > 785f) startPage()
        sectionHeader("COMMITTEE REVIEW / UTHIBITISHO")
        paint.color = Color.BLACK
        paint.textSize = 10f
        canvas().drawText("Prepared by: ______________________________", 50f, y + 12f, paint)
        canvas().drawText("Reviewed by: ______________________________", 310f, y + 12f, paint)
        y += 30f
        canvas().drawText("Approved by: ______________________________", 50f, y + 12f, paint)
        canvas().drawText("Date: ____________________", 365f, y + 12f, paint)

        document.finishPage(page)
        val safeName = reportTitle.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40).ifBlank { "report" }
        val file = File(context.cacheDir, "uwapo_${safeName}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out -> document.writeTo(out) }
        return file
        } finally {
            document.close()
        }
    }

    fun sharePdf(context: Context, file: File, chooserTitle: String = "Share PDF") {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun viewPdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open PDF"))
    }
}
