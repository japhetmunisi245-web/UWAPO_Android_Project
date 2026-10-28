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
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply { isAntiAlias = true }

        // Header
        paint.color = Color.parseColor("#17365D")
        canvas.drawRect(0f, 0f, 595f, 100f, paint)

        paint.color = Color.WHITE
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("UWAPO - MEMBER STATEMENT", 40f, 50f, paint)

        paint.textSize = 13f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Taarifa ya Michango ya Mwanachama", 40f, 78f, paint)

        // Member Header Info
        paint.color = Color.BLACK
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("${member.fullName} (${member.memberNo})", 40f, 130f, paint)

        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Phone / Simu: ${member.phone.ifBlank { "N/A" }}", 40f, 150f, paint)
        canvas.drawText("Status / Hali: ${member.status.uppercase()}", 350f, 150f, paint)

        val totalPaid = payments.filter { it.syncStatus != "reversed" }.sumOf { it.amountTzs }
        canvas.drawText("Total Recorded: ${money(totalPaid)}", 40f, 170f, paint)

        // Table Header
        paint.color = Color.parseColor("#17365D")
        canvas.drawRect(40f, 190f, 555f, 215f, paint)

        paint.color = Color.WHITE
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("DATE / TAREHE", 50f, 207f, paint)
        canvas.drawText("PERIOD", 180f, 207f, paint)
        canvas.drawText("METHOD", 270f, 207f, paint)
        canvas.drawText("RECEIPT", 370f, 207f, paint)
        canvas.drawText("AMOUNT", 480f, 207f, paint)

        paint.color = Color.BLACK
        paint.typeface = Typeface.DEFAULT
        var y = 235f

        payments.take(25).forEach { payment ->
            val dStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(payment.paidAt))
            val pStr = "${payment.periodMonth.toString().padStart(2, '0')}/${payment.periodYear}"
            canvas.drawText(dStr, 50f, y, paint)
            canvas.drawText(pStr, 180f, y, paint)
            canvas.drawText(payment.method.take(10), 270f, y, paint)
            canvas.drawText(payment.receiptNo.take(12), 370f, y, paint)
            canvas.drawText(money(payment.amountTzs), 480f, y, paint)
            y += 20f
        }

        document.finishPage(page)
        val file = File(context.cacheDir, "statement_${member.memberNo}.pdf")
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
        return file
    }

    fun generateReportPdf(
        context: Context,
        reportTitle: String,
        monthTotal: Long,
        yearlyTotal: Long,
        expensesTotal: Long,
        memberCount: Int,
        defaulters: List<MemberEntity>,
        batchBreakdown: Map<String, Long> = emptyMap()
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply { isAntiAlias = true }

        // Header Banner
        paint.color = Color.parseColor("#17365D")
        canvas.drawRect(0f, 0f, 595f, 100f, paint)

        paint.color = Color.WHITE
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("UWAPO MEMBERSHIP CLUB", 40f, 45f, paint)

        paint.textSize = 14f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Financial & Membership Report / Ripoti ya Fedha", 40f, 68f, paint)

        val generatedDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        paint.textSize = 10f
        canvas.drawText("Generated / Tarehe: $generatedDate", 40f, 88f, paint)

        // Summary Card Section
        paint.color = Color.parseColor("#F2F4F7")
        canvas.drawRoundRect(40f, 115f, 555f, 220f, 10f, 10f, paint)

        paint.color = Color.parseColor("#17365D")
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("FINANCIAL SUMMARY / MUHTASARI WA FEDHA", 55f, 138f, paint)

        paint.color = Color.BLACK
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Total Registered Members: $memberCount", 55f, 162f, paint)
        canvas.drawText("Current Month Collected: ${money(monthTotal)}", 55f, 182f, paint)
        canvas.drawText("Yearly Total Collections: ${money(yearlyTotal)}", 55f, 202f, paint)

        canvas.drawText("Approved Expenses: ${money(expensesTotal)}", 320f, 162f, paint)
        val net = yearlyTotal - expensesTotal
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = if (net >= 0) Color.parseColor("#006400") else Color.RED
        canvas.drawText("Net Reserve Balance: ${money(net)}", 320f, 182f, paint)

        // Batch Breakdown Section Table
        var y = 240f
        if (batchBreakdown.isNotEmpty()) {
            paint.color = Color.parseColor("#17365D")
            canvas.drawRect(40f, y, 555f, y + 25f, paint)

            paint.color = Color.WHITE
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("BATCH YEAR", 55f, y + 17f, paint)
            canvas.drawText("TOTAL COLLECTED", 350f, y + 17f, paint)

            y += 25f
            paint.color = Color.BLACK
            paint.typeface = Typeface.DEFAULT
            batchBreakdown.forEach { (batchLabel, collected) ->
                y += 20f
                canvas.drawText("Batch $batchLabel", 55f, y, paint)
                canvas.drawText(money(collected), 350f, y, paint)
                paint.color = Color.LTGRAY
                canvas.drawLine(40f, y + 5f, 555f, y + 5f, paint)
                paint.color = Color.BLACK
            }
            y += 15f
        }

        // Defaulters List Section
        y += 10f
        paint.color = Color.parseColor("#17365D")
        canvas.drawRect(40f, y, 555f, y + 25f, paint)

        paint.color = Color.WHITE
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("DEFAULTERS LIST / WENYE MADENI MWEZI HUU (${defaulters.size})", 50f, y + 17f, paint)

        y += 25f
        paint.color = Color.BLACK
        paint.typeface = Typeface.DEFAULT

        if (defaulters.isEmpty()) {
            y += 20f
            canvas.drawText("All active members have paid for this month!", 50f, y, paint)
        } else {
            defaulters.take(15).forEachIndexed { idx, m ->
                y += 20f
                canvas.drawText("${idx + 1}. ${m.fullName} (${m.memberNo}) - ${m.phone.ifBlank { "No Phone" }}", 50f, y, paint)
            }
        }

        // Footer
        paint.color = Color.LTGRAY
        canvas.drawLine(40f, 800f, 555f, 800f, paint)
        paint.color = Color.GRAY
        paint.textSize = 10f
        canvas.drawText("UWAPO Management System - Official Financial Document", 150f, 815f, paint)

        document.finishPage(page)
        val file = File(context.cacheDir, "report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
        return file
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
