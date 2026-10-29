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

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_LEFT = 40f
    private const val MARGIN_RIGHT = 555f
    private const val MARGIN_BOTTOM = 750f

    fun generateReceiptPdf(
        context: Context,
        payment: PaymentEntity,
        memberName: String,
        memberNo: String
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply { isAntiAlias = true }

        // Primary Header Box
        paint.color = Color.parseColor("#17365D")
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 110f, paint)

        // App Title
        paint.color = Color.WHITE
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("UWAPO MEMBERSHIP CLUB", MARGIN_LEFT, 50f, paint)

        paint.textSize = 13f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Official Payment Receipt / Risiti ya Malipo", MARGIN_LEFT, 80f, paint)

        // Receipt Details Section
        paint.color = Color.BLACK
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("RECEIPT NO: ${payment.receiptNo}", MARGIN_LEFT, 145f, paint)

        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(payment.paidAt))
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Date / Tarehe: $dateStr", 350f, 145f, paint)

        // Divider Line
        paint.color = Color.LTGRAY
        paint.strokeWidth = 1.5f
        canvas.drawLine(MARGIN_LEFT, 165f, MARGIN_RIGHT, 165f, paint)

        // Member Information Box
        paint.color = Color.parseColor("#F2F4F7")
        canvas.drawRoundRect(MARGIN_LEFT, 185f, MARGIN_RIGHT, 265f, 10f, 10f, paint)

        paint.color = Color.parseColor("#17365D")
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("MEMBER DETAILS / TAARIFA ZA MWANACHAMA", 55f, 210f, paint)

        paint.color = Color.BLACK
        paint.textSize = 13f
        canvas.drawText("Name / Jina: $memberName", 55f, 238f, paint)
        canvas.drawText("Member No / Namba: $memberNo", 350f, 238f, paint)

        // Contribution Information Table
        paint.color = Color.parseColor("#17365D")
        canvas.drawRect(MARGIN_LEFT, 290f, MARGIN_RIGHT, 320f, paint)

        paint.color = Color.WHITE
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("DESCRIPTION / MAELEZO", 55f, 310f, paint)
        canvas.drawText("PERIOD / MWEZI", 300f, 310f, paint)
        canvas.drawText("AMOUNT / KIASI", 440f, 310f, paint)

        paint.color = Color.BLACK
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        val periodStr = "${payment.periodMonth.toString().padStart(2, '0')}/${payment.periodYear}"
        canvas.drawText("Monthly Contribution / Mchango wa Mwezi", 55f, 350f, paint)
        canvas.drawText(periodStr, 300f, 350f, paint)
        canvas.drawText(money(payment.amountTzs), 440f, 350f, paint)

        // Divider
        paint.color = Color.LTGRAY
        canvas.drawLine(MARGIN_LEFT, 380f, MARGIN_RIGHT, 380f, paint)

        // Summary Section
        paint.color = Color.BLACK
        paint.textSize = 12f
        canvas.drawText("Payment Method / Njia: ${payment.method.replace('_', ' ').uppercase()}", MARGIN_LEFT, 410f, paint)
        val statusText = if (payment.syncStatus == "reversed") "REVERSED" else "CONFIRMED"
        canvas.drawText("Status / Hali: $statusText", MARGIN_LEFT, 435f, paint)

        paint.color = Color.parseColor("#17365D")
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOTAL PAID: ${money(payment.amountTzs)}", 300f, 425f, paint)

        // Footer Box
        paint.color = Color.parseColor("#E8EEF5")
        canvas.drawRect(MARGIN_LEFT, 490f, MARGIN_RIGHT, 550f, paint)

        paint.color = Color.parseColor("#17365D")
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Thank you for your contribution! / Ahsante kwa mchango wako!", 65f, 525f, paint)

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
        val pages = mutableListOf<PdfDocument.Page>()
        val document = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true }
        val orderedPayments = payments.sortedBy { it.paidAt }
        val totalPaid = orderedPayments.filter { it.syncStatus != "reversed" }.sumOf { it.amountTzs }
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvasRef: android.graphics.Canvas? = null
        fun canvas(): android.graphics.Canvas = checkNotNull(canvasRef)
        var y = 0f

<<<<<<< HEAD
        var pageIndex = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create()
        var page = document.startPage(pageInfo)
        pages.add(page)
        var canvas = page.canvas

        fun drawHeaderBanner(c: android.graphics.Canvas, pIndex: Int) {
            paint.color = Color.parseColor("#17365D")
            c.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 95f, paint)

            paint.color = Color.WHITE
            paint.textSize = 20f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            c.drawText("UWAPO - MEMBER STATEMENT", MARGIN_LEFT, 45f, paint)

            paint.textSize = 12f
            paint.typeface = Typeface.DEFAULT
            c.drawText("Taarifa ya Michango ya Mwanachama · Page $pIndex", MARGIN_LEFT, 72f, paint)
        }

        fun drawTableHeader(c: android.graphics.Canvas, topY: Float) {
            paint.color = Color.parseColor("#17365D")
            c.drawRect(MARGIN_LEFT, topY, MARGIN_RIGHT, topY + 24f, paint)

            paint.color = Color.WHITE
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            c.drawText("DATE / TAREHE", 48f, topY + 16f, paint)
            c.drawText("PERIOD", 160f, topY + 16f, paint)
            c.drawText("METHOD", 250f, topY + 16f, paint)
            c.drawText("RECEIPT", 340f, topY + 16f, paint)
            c.drawText("STATUS", 435f, topY + 16f, paint)
            c.drawText("AMOUNT", 495f, topY + 16f, paint)
        }

        fun drawFooter(c: android.graphics.Canvas, pIndex: Int) {
            paint.color = Color.LTGRAY
            paint.strokeWidth = 1f
            c.drawLine(MARGIN_LEFT, 800f, MARGIN_RIGHT, 800f, paint)

            paint.color = Color.DKGRAY
            paint.textSize = 9f
            paint.typeface = Typeface.DEFAULT
            c.drawText("UWAPO Management System · Official Member Statement", MARGIN_LEFT, 815f, paint)
            c.drawText("Page $pIndex", 500f, 815f, paint)
        }

        drawHeaderBanner(canvas, pageIndex)

        // Member Info Box on Page 1
        paint.color = Color.BLACK
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("${member.fullName} (${member.memberNo})", MARGIN_LEFT, 125f, paint)

        paint.textSize = 11f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Phone / Simu: ${member.phone.ifBlank { "N/A" }}", MARGIN_LEFT, 145f, paint)
        canvas.drawText("Status: ${member.status.uppercase()}", 350f, 145f, paint)

        val totalPaid = payments.filter { it.syncStatus != "reversed" }.sumOf { it.amountTzs }
        val generatedDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Total Recorded: ${money(totalPaid)}", MARGIN_LEFT, 165f, paint)
        canvas.drawText("Date: $generatedDate", 350f, 165f, paint)

        var y = 190f
        drawTableHeader(canvas, y)
        y += 24f

        paint.color = Color.BLACK
        paint.textSize = 10f
        paint.typeface = Typeface.DEFAULT

        payments.forEach { payment ->
            if (y > MARGIN_BOTTOM) {
                drawFooter(canvas, pageIndex)
                document.finishPage(page)

                pageIndex++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create()
                page = document.startPage(pageInfo)
                pages.add(page)
                canvas = page.canvas

                drawHeaderBanner(canvas, pageIndex)
                y = 115f
                drawTableHeader(canvas, y)
                y += 24f
            }

            val dStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(payment.paidAt))
            val pStr = "${payment.periodMonth.toString().padStart(2, '0')}/${payment.periodYear}"
            val isReversed = payment.syncStatus == "reversed"

            y += 18f
            paint.color = if (isReversed) Color.GRAY else Color.BLACK
            canvas.drawText(dStr, 48f, y, paint)
            canvas.drawText(pStr, 160f, y, paint)
            canvas.drawText(payment.method.take(10).uppercase(), 250f, y, paint)
            canvas.drawText(payment.receiptNo.take(12), 340f, y, paint)

            if (isReversed) {
                paint.color = Color.RED
                canvas.drawText("REVERSED", 435f, y, paint)
            } else {
                paint.color = Color.parseColor("#006400")
                canvas.drawText("PAID", 435f, y, paint)
            }

            paint.color = if (isReversed) Color.GRAY else Color.BLACK
            canvas.drawText(money(payment.amountTzs), 495f, y, paint)

            paint.color = Color.LTGRAY
            canvas.drawLine(MARGIN_LEFT, y + 4f, MARGIN_RIGHT, y + 4f, paint)
        }

        drawFooter(canvas, pageIndex)
        document.finishPage(page)

        val file = File(context.cacheDir, "statement_${member.memberNo}.pdf")
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
        return file
=======
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
>>>>>>> fef975d8d515490084c5c154ca0583aa3ec0c026
    }

    fun generateReportPdf(
        context: Context,
        reportType: String, // "Monthly" or "Annual"
        periodTitle: String, // e.g. "January 2025" or "Year 2025"
        collectionsTotal: Long,
        expensesTotal: Long,
        netMovement: Long,
        expectedContributions: Long,
        estimatedArrears: Long,
        memberCount: Int,
<<<<<<< HEAD
        activeCount: Int,
        breakdownMap: Map<String, Long>,
        breakdownTitle: String,
        arrearsMembers: List<MemberEntity>
=======
        defaulters: List<MemberEntity>,
        batchBreakdown: Map<String, Long> = emptyMap(),
        periodLabel: String = "Current reporting period",
        periodCollectionsTzs: Long = yearlyTotal
>>>>>>> fef975d8d515490084c5c154ca0583aa3ec0c026
    ): File {
        val document = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true }
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvasRef: android.graphics.Canvas? = null
        fun canvas(): android.graphics.Canvas = checkNotNull(canvasRef)
        var y = 0f

<<<<<<< HEAD
        var pageIndex = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        fun drawHeaderBanner(c: android.graphics.Canvas, pIndex: Int) {
            paint.color = Color.parseColor("#17365D")
            c.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 100f, paint)
=======
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
>>>>>>> fef975d8d515490084c5c154ca0583aa3ec0c026

        fun sectionHeader(title: String) {
            if (y + 32f > 785f) startPage()
            paint.color = Color.parseColor("#17365D")
            canvas().drawRoundRect(40f, y, 555f, y + 26f, 4f, 4f, paint)
            paint.color = Color.WHITE
            paint.textSize = 21f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
<<<<<<< HEAD
            c.drawText("UWAPO MEMBERSHIP CLUB", MARGIN_LEFT, 42f, paint)

            paint.textSize = 12f
            paint.typeface = Typeface.DEFAULT
            c.drawText("$reportType Financial & Membership Report — $periodTitle", MARGIN_LEFT, 66f, paint)

            val generatedDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
            paint.textSize = 9f
            c.drawText("Generated / Tarehe: $generatedDate · Page $pIndex", MARGIN_LEFT, 86f, paint)
        }

        fun drawFooter(c: android.graphics.Canvas, pIndex: Int) {
            paint.color = Color.LTGRAY
            paint.strokeWidth = 1f
            c.drawLine(MARGIN_LEFT, 800f, MARGIN_RIGHT, 800f, paint)

            paint.color = Color.DKGRAY
            paint.textSize = 9f
            paint.typeface = Typeface.DEFAULT
            c.drawText("UWAPO Management System · Official Financial Document", MARGIN_LEFT, 815f, paint)
            c.drawText("Page $pIndex", 500f, 815f, paint)
        }

        drawHeaderBanner(canvas, pageIndex)

        // Financial Summary Box on Page 1
        paint.color = Color.parseColor("#F2F4F7")
        canvas.drawRoundRect(MARGIN_LEFT, 115f, MARGIN_RIGHT, 235f, 10f, 10f, paint)

        paint.color = Color.parseColor("#17365D")
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("FINANCIAL SUMMARY / MUHTASARI WA FEDHA ($periodTitle)", 55f, 138f, paint)

        paint.color = Color.BLACK
        paint.textSize = 11f
        paint.typeface = Typeface.DEFAULT

        canvas.drawText("Registered Members: $memberCount (Active: $activeCount)", 55f, 160f, paint)
        canvas.drawText("Collections Recorded: ${money(collectionsTotal)}", 55f, 180f, paint)
        canvas.drawText("Approved Expenses: ${money(expensesTotal)}", 55f, 200f, paint)

        canvas.drawText("Expected Contributions*: ${money(expectedContributions)}", 310f, 160f, paint)
        canvas.drawText("Est. Outstanding Arrears*: ${money(estimatedArrears)}", 310f, 180f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = if (netMovement >= 0) Color.parseColor("#006400") else Color.RED
        canvas.drawText("Net Movement: ${money(netMovement)}", 310f, 200f, paint)

        paint.color = Color.DKGRAY
        paint.textSize = 8.5f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("* Calculated estimates based on active membership rules.", 55f, 225f, paint)

        var y = 255f

        // Collection Breakdown Table
        if (breakdownMap.isNotEmpty()) {
            paint.color = Color.parseColor("#17365D")
            canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 22f, paint)

            paint.color = Color.WHITE
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(breakdownTitle.uppercase(), 55f, y + 15f, paint)
            canvas.drawText("COLLECTED AMOUNT", 380f, y + 15f, paint)

            y += 22f
            paint.color = Color.BLACK
            paint.typeface = Typeface.DEFAULT

            breakdownMap.forEach { (label, amount) ->
                if (y > MARGIN_BOTTOM) {
                    drawFooter(canvas, pageIndex)
                    document.finishPage(page)

                    pageIndex++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas

                    drawHeaderBanner(canvas, pageIndex)
                    y = 115f
                    paint.color = Color.parseColor("#17365D")
                    canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 22f, paint)
                    paint.color = Color.WHITE
                    paint.textSize = 10f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText(breakdownTitle.uppercase(), 55f, y + 15f, paint)
                    canvas.drawText("COLLECTED AMOUNT", 380f, y + 15f, paint)
                    y += 22f
                }

                y += 18f
                paint.color = Color.BLACK
                paint.textSize = 10f
                paint.typeface = Typeface.DEFAULT
                canvas.drawText(label, 55f, y, paint)
                canvas.drawText(money(amount), 380f, y, paint)

                paint.color = Color.LTGRAY
                canvas.drawLine(MARGIN_LEFT, y + 4f, MARGIN_RIGHT, y + 4f, paint)
            }
            y += 15f
        }

        // Members in Arrears Section
        if (y + 40f > MARGIN_BOTTOM) {
            drawFooter(canvas, pageIndex)
            document.finishPage(page)

            pageIndex++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas

            drawHeaderBanner(canvas, pageIndex)
            y = 115f
        }

        paint.color = Color.parseColor("#17365D")
        canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 22f, paint)

        paint.color = Color.WHITE
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("MEMBERS IN ARREARS / PENDING (${arrearsMembers.size})", 50f, y + 15f, paint)

        y += 22f
        paint.color = Color.BLACK
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 9.5f

        if (arrearsMembers.isEmpty()) {
            y += 18f
            canvas.drawText("All active members have fulfilled contributions for this period!", 50f, y, paint)
        } else {
            arrearsMembers.forEachIndexed { idx, m ->
                if (y > MARGIN_BOTTOM) {
                    drawFooter(canvas, pageIndex)
                    document.finishPage(page)

                    pageIndex++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas

                    drawHeaderBanner(canvas, pageIndex)
                    y = 115f

                    paint.color = Color.parseColor("#17365D")
                    canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 22f, paint)
                    paint.color = Color.WHITE
                    paint.textSize = 10f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText("MEMBERS IN ARREARS / PENDING (CONTINUED)", 50f, y + 15f, paint)
                    y += 22f
                }

                y += 18f
                paint.color = Color.BLACK
                paint.textSize = 9.5f
                paint.typeface = Typeface.DEFAULT
                canvas.drawText("${idx + 1}. ${m.fullName} (${m.memberNo})", 50f, y, paint)
                canvas.drawText("Phone: ${m.phone.ifBlank { "N/A" }}", 350f, y, paint)

                paint.color = Color.LTGRAY
                canvas.drawLine(MARGIN_LEFT, y + 4f, MARGIN_RIGHT, y + 4f, paint)
            }
        }

        // Committee Sign-off Block on Last Page
        y += 30f
        if (y + 90f > MARGIN_BOTTOM) {
            drawFooter(canvas, pageIndex)
            document.finishPage(page)
=======
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
>>>>>>> fef975d8d515490084c5c154ca0583aa3ec0c026

            pageIndex++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas

            drawHeaderBanner(canvas, pageIndex)
            y = 130f
        }

        paint.color = Color.parseColor("#F2F4F7")
        canvas.drawRoundRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 90f, 8f, 8f, paint)

        paint.color = Color.parseColor("#17365D")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("COMMITTEE APPROVAL / UTHIBITISHO WA KAMATI", 55f, y + 20f, paint)

        paint.color = Color.BLACK
        paint.textSize = 9f
        paint.typeface = Typeface.DEFAULT

        canvas.drawText("Prepared by: _______________________", 55f, y + 45f, paint)
        canvas.drawText("Date: ______________", 230f, y + 45f, paint)

        canvas.drawText("Reviewed by: _______________________", 320f, y + 45f, paint)
        canvas.drawText("Date: ______________", 485f, y + 45f, paint)

        canvas.drawText("Approved by: _______________________", 55f, y + 70f, paint)
        canvas.drawText("Date: ______________", 230f, y + 70f, paint)

        drawFooter(canvas, pageIndex)
        document.finishPage(page)
<<<<<<< HEAD

        val file = File(context.cacheDir, "report_${reportType.lowercase()}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
        return file
    }

    data class TermMemberContributionRow(
        val memberNo: String,
        val fullName: String,
        val monthlyAmounts: List<Long>,
        val totalAmount: Long
    )

    fun generateTermReportPdf(
        context: Context,
        termTitle: String,
        year: Int,
        monthLabels: List<String>,
        memberRows: List<TermMemberContributionRow>,
        grandTotalMonthly: List<Long>,
        grandTotalTerm: Long
    ): File {
        val document = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true }

        val pageWidth = 842
        val pageHeight = 595
        val marginLeft = 30f
        val marginRight = 812f
        val marginBottom = 520f

        var pageIndex = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        fun drawHeaderBanner(c: android.graphics.Canvas, pIndex: Int) {
            paint.color = Color.parseColor("#17365D")
            c.drawRect(0f, 0f, pageWidth.toFloat(), 75f, paint)

            paint.color = Color.WHITE
            paint.textSize = 20f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            c.drawText("UWAPO MEMBERSHIP CLUB", marginLeft, 36f, paint)

            paint.textSize = 12f
            paint.typeface = Typeface.DEFAULT
            c.drawText("MEMBER CONTRIBUTIONS REPORT — $termTitle $year (A4 Landscape)", marginLeft, 58f, paint)

            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
            paint.textSize = 9f
            c.drawText("Generated: $dateStr · Page $pIndex", 600f, 58f, paint)
        }

        fun drawTableHeader(c: android.graphics.Canvas, topY: Float) {
            paint.color = Color.parseColor("#17365D")
            c.drawRect(marginLeft, topY, marginRight, topY + 24f, paint)

            paint.color = Color.WHITE
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            c.drawText("NO.", 35f, topY + 16f, paint)
            c.drawText("FULL NAME & MEMBER NO", 70f, topY + 16f, paint)

            val monthColX = listOf(275f, 340f, 405f, 470f, 535f, 600f)
            monthLabels.take(6).forEachIndexed { idx, label ->
                if (idx < monthColX.size) {
                    c.drawText(label.uppercase(Locale.ROOT), monthColX[idx], topY + 16f, paint)
                }
            }

            c.drawText("TOTAL (TZS)", 670f, topY + 16f, paint)
        }

        fun drawFooter(c: android.graphics.Canvas, pIndex: Int) {
            paint.color = Color.LTGRAY
            paint.strokeWidth = 1f
            c.drawLine(marginLeft, 540f, marginRight, 540f, paint)

            paint.color = Color.DKGRAY
            paint.textSize = 9f
            paint.typeface = Typeface.DEFAULT
            c.drawText("UWAPO Management System · Official Term Contribution Report", marginLeft, 555f, paint)
            c.drawText("Page $pIndex", 750f, 555f, paint)
        }

        drawHeaderBanner(canvas, pageIndex)

        var y = 90f
        drawTableHeader(canvas, y)
        y += 24f

        val monthColX = listOf(275f, 340f, 405f, 470f, 535f, 600f)
        val numFormat = NumberFormat.getIntegerInstance(Locale("en", "TZ"))

        memberRows.forEachIndexed { idx, row ->
            if (y > marginBottom) {
                drawFooter(canvas, pageIndex)
                document.finishPage(page)

                pageIndex++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas

                drawHeaderBanner(canvas, pageIndex)
                y = 90f
                drawTableHeader(canvas, y)
                y += 24f
            }

            y += 18f

            if (idx % 2 == 1) {
                paint.color = Color.parseColor("#F9FAFC")
                canvas.drawRect(marginLeft, y - 14f, marginRight, y + 4f, paint)
            }

            paint.color = Color.BLACK
            paint.textSize = 9f
            paint.typeface = Typeface.DEFAULT

            canvas.drawText("${idx + 1}", 35f, y, paint)

            val displayName = "${row.fullName} (${row.memberNo})"
            val truncatedName = if (displayName.length > 32) displayName.take(30) + ".." else displayName
            canvas.drawText(truncatedName, 70f, y, paint)

            row.monthlyAmounts.take(6).forEachIndexed { mIdx, amt ->
                if (mIdx < monthColX.size) {
                    val txt = if (amt <= 0) "-" else numFormat.format(amt)
                    canvas.drawText(txt, monthColX[mIdx], y, paint)
                }
            }

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val totalTxt = if (row.totalAmount <= 0) "0" else numFormat.format(row.totalAmount)
            canvas.drawText(totalTxt, 670f, y, paint)

            paint.color = Color.LTGRAY
            paint.strokeWidth = 0.5f
            canvas.drawLine(marginLeft, y + 4f, marginRight, y + 4f, paint)
        }

        y += 22f
        if (y > marginBottom) {
            drawFooter(canvas, pageIndex)
            document.finishPage(page)

            pageIndex++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas

            drawHeaderBanner(canvas, pageIndex)
            y = 110f
        }

        paint.color = Color.parseColor("#E8EEF5")
        canvas.drawRect(marginLeft, y - 14f, marginRight, y + 8f, paint)

        paint.color = Color.parseColor("#17365D")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("GRAND TOTAL (TZS)", 70f, y, paint)

        grandTotalMonthly.take(6).forEachIndexed { mIdx, sumAmt ->
            if (mIdx < monthColX.size) {
                val txt = if (sumAmt <= 0) "0" else numFormat.format(sumAmt)
                canvas.drawText(txt, monthColX[mIdx], y, paint)
            }
        }

        val grandTotalTxt = money(grandTotalTerm)
        canvas.drawText(grandTotalTxt, 670f, y, paint)

        drawFooter(canvas, pageIndex)
        document.finishPage(page)

        val file = File(context.cacheDir, "term_report_${year}_${termTitle.take(6).lowercase().replace(' ', '_')}_${System.currentTimeMillis()}.pdf")
=======
        val safeName = reportTitle.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40).ifBlank { "report" }
        val file = File(context.cacheDir, "uwapo_${safeName}_${System.currentTimeMillis()}.pdf")
>>>>>>> fef975d8d515490084c5c154ca0583aa3ec0c026
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
