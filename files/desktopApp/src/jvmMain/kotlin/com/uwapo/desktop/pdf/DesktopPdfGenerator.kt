package com.uwapo.desktop.pdf

import com.uwapo.desktop.data.MemberEntity
import com.uwapo.desktop.data.PaymentEntity
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.awt.Color
import java.awt.Desktop
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DesktopPdfGenerator {

    private fun money(amount: Long): String =
        "TZS " + NumberFormat.getIntegerInstance(Locale("en", "TZ")).format(amount)

    data class TermMemberRow(
        val memberNo: String,
        val fullName: String,
        val monthlyAmounts: List<Long>,
        val totalAmount: Long
    )

    fun generateTermReportPdf(
        termTitle: String,
        year: Int,
        monthLabels: List<String>,
        memberRows: List<TermMemberRow>,
        grandTotalMonthly: List<Long>,
        grandTotalTerm: Long
    ): File {
        val document = PDDocument()
        val fontBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        val fontRegular = PDType1Font(Standard14Fonts.FontName.HELVETICA)

        val landscapeRect = PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) // 842 x 595
        val pageWidth = landscapeRect.width
        val pageHeight = landscapeRect.height

        val marginLeft = 30f
        val marginRight = 812f
        val marginBottom = 50f

        var pageIndex = 1
        var page = PDPage(landscapeRect)
        document.addPage(page)
        var cs = PDPageContentStream(document, page)

        fun drawHeaderBanner(c: PDPageContentStream, pIndex: Int) {
            // Header box Navy #17365D
            c.setNonStrokingColor(Color(23, 54, 93))
            c.addRect(0f, pageHeight - 75f, pageWidth, 75f)
            c.fill()

            c.setNonStrokingColor(Color.WHITE)

            c.beginText()
            c.setFont(fontBold, 20f)
            c.newLineAtOffset(marginLeft, pageHeight - 36f)
            c.showText("UWAPO MEMBERSHIP CLUB")
            c.endText()

            c.beginText()
            c.setFont(fontRegular, 11f)
            c.newLineAtOffset(marginLeft, pageHeight - 56f)
            c.showText("MEMBER CONTRIBUTIONS REPORT — $termTitle $year (A4 Landscape)")
            c.endText()

            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
            c.beginText()
            c.setFont(fontRegular, 9f)
            c.newLineAtOffset(600f, pageHeight - 56f)
            c.showText("Generated: $dateStr · Page $pIndex")
            c.endText()
        }

        fun drawTableHeader(c: PDPageContentStream, topY: Float) {
            c.setNonStrokingColor(Color(23, 54, 93))
            c.addRect(marginLeft, topY - 22f, marginRight - marginLeft, 22f)
            c.fill()

            c.setNonStrokingColor(Color.WHITE)
            c.setFont(fontBold, 9.5f)

            c.beginText()
            c.newLineAtOffset(35f, topY - 15f)
            c.showText("NO.")
            c.endText()

            c.beginText()
            c.newLineAtOffset(70f, topY - 15f)
            c.showText("FULL NAME & MEMBER NO")
            c.endText()

            val monthColX = listOf(275f, 340f, 405f, 470f, 535f, 600f)
            monthLabels.take(6).forEachIndexed { idx, label ->
                if (idx < monthColX.size) {
                    c.beginText()
                    c.newLineAtOffset(monthColX[idx], topY - 15f)
                    c.showText(label.uppercase(Locale.ROOT))
                    c.endText()
                }
            }

            c.beginText()
            c.newLineAtOffset(670f, topY - 15f)
            c.showText("TOTAL (TZS)")
            c.endText()
        }

        fun drawFooter(c: PDPageContentStream, pIndex: Int) {
            c.setStrokingColor(Color.LIGHT_GRAY)
            c.setLineWidth(1f)
            c.moveTo(marginLeft, 40f)
            c.lineTo(marginRight, 40f)
            c.stroke()

            c.setNonStrokingColor(Color.DARK_GRAY)
            c.setFont(fontRegular, 9f)

            c.beginText()
            c.newLineAtOffset(marginLeft, 25f)
            c.showText("UWAPO Management System · Official Term Contribution Report")
            c.endText()

            c.beginText()
            c.newLineAtOffset(750f, 25f)
            c.showText("Page $pIndex")
            c.endText()
        }

        drawHeaderBanner(cs, pageIndex)

        var y = pageHeight - 95f
        drawTableHeader(cs, y)
        y -= 22f

        val monthColX = listOf(275f, 340f, 405f, 470f, 535f, 600f)
        val numFormat = NumberFormat.getIntegerInstance(Locale("en", "TZ"))

        memberRows.forEachIndexed { idx, row ->
            if (y < marginBottom + 30f) {
                drawFooter(cs, pageIndex)
                cs.close()

                pageIndex++
                page = PDPage(landscapeRect)
                document.addPage(page)
                cs = PDPageContentStream(document, page)

                drawHeaderBanner(cs, pageIndex)
                y = pageHeight - 95f
                drawTableHeader(cs, y)
                y -= 22f
            }

            y -= 18f

            if (idx % 2 == 1) {
                cs.setNonStrokingColor(Color(249, 250, 252))
                cs.addRect(marginLeft, y - 4f, marginRight - marginLeft, 18f)
                cs.fill()
            }

            cs.setNonStrokingColor(Color.BLACK)

            // Index
            cs.beginText()
            cs.setFont(fontRegular, 9f)
            cs.newLineAtOffset(35f, y)
            cs.showText("${idx + 1}")
            cs.endText()

            // Name
            val nameStr = row.fullName
            val truncatedName = if (nameStr.length > 40) nameStr.take(38) + ".." else nameStr
            cs.beginText()
            cs.setFont(fontRegular, 9f)
            cs.newLineAtOffset(70f, y)
            cs.showText(truncatedName)
            cs.endText()

            // Months 1..6
            row.monthlyAmounts.take(6).forEachIndexed { mIdx, amt ->
                if (mIdx < monthColX.size) {
                    val txt = if (amt <= 0) "-" else numFormat.format(amt)
                    cs.beginText()
                    cs.setFont(fontRegular, 9f)
                    cs.newLineAtOffset(monthColX[mIdx], y)
                    cs.showText(txt)
                    cs.endText()
                }
            }

            // Total
            val totalTxt = if (row.totalAmount <= 0) "0" else numFormat.format(row.totalAmount)
            cs.beginText()
            cs.setFont(fontBold, 9f)
            cs.newLineAtOffset(670f, y)
            cs.showText(totalTxt)
            cs.endText()

            cs.setStrokingColor(Color.LIGHT_GRAY)
            cs.setLineWidth(0.5f)
            cs.moveTo(marginLeft, y - 4f)
            cs.lineTo(marginRight, y - 4f)
            cs.stroke()
        }

        // Grand Total Row
        y -= 24f
        if (y < marginBottom + 30f) {
            drawFooter(cs, pageIndex)
            cs.close()

            pageIndex++
            page = PDPage(landscapeRect)
            document.addPage(page)
            cs = PDPageContentStream(document, page)

            drawHeaderBanner(cs, pageIndex)
            y = pageHeight - 110f
        }

        cs.setNonStrokingColor(Color(232, 238, 245))
        cs.addRect(marginLeft, y - 4f, marginRight - marginLeft, 22f)
        cs.fill()

        cs.setNonStrokingColor(Color(23, 54, 93))

        cs.beginText()
        cs.setFont(fontBold, 10f)
        cs.newLineAtOffset(70f, y + 3f)
        cs.showText("GRAND TOTAL (TZS)")
        cs.endText()

        grandTotalMonthly.take(6).forEachIndexed { mIdx, sumAmt ->
            if (mIdx < monthColX.size) {
                val txt = if (sumAmt <= 0) "0" else numFormat.format(sumAmt)
                cs.beginText()
                cs.setFont(fontBold, 9.5f)
                cs.newLineAtOffset(monthColX[mIdx], y + 3f)
                cs.showText(txt)
                cs.endText()
            }
        }

        cs.beginText()
        cs.setFont(fontBold, 10f)
        cs.newLineAtOffset(670f, y + 3f)
        cs.showText(money(grandTotalTerm))
        cs.endText()

        drawFooter(cs, pageIndex)
        cs.close()

        val tempDir = File(System.getProperty("java.io.tmpdir") ?: ".", "uwapo_reports")
        if (!tempDir.exists()) tempDir.mkdirs()
        val outFile = File(tempDir, "term_report_${year}_${termTitle.take(6).lowercase().replace(' ', '_')}_${System.currentTimeMillis()}.pdf")
        document.save(outFile)
        document.close()
        return outFile
    }

    fun generateStatementPdf(member: MemberEntity, payments: List<PaymentEntity>): File {
        val document = PDDocument()
        val fontBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        val fontRegular = PDType1Font(Standard14Fonts.FontName.HELVETICA)

        val page = PDPage(PDRectangle.A4)
        document.addPage(page)
        val cs = PDPageContentStream(document, page)

        val pageWidth = PDRectangle.A4.width
        val pageHeight = PDRectangle.A4.height

        // Header
        cs.setNonStrokingColor(Color(23, 54, 93))
        cs.addRect(0f, pageHeight - 80f, pageWidth, 80f)
        cs.fill()

        cs.setNonStrokingColor(Color.WHITE)
        cs.beginText()
        cs.setFont(fontBold, 18f)
        cs.newLineAtOffset(40f, pageHeight - 40f)
        cs.showText("UWAPO - MEMBER STATEMENT")
        cs.endText()

        cs.beginText()
        cs.setFont(fontRegular, 11f)
        cs.newLineAtOffset(40f, pageHeight - 60f)
        cs.showText("Official Member Contribution Statement")
        cs.endText()

        // Member Info Box
        cs.setNonStrokingColor(Color.BLACK)
        cs.beginText()
        cs.setFont(fontBold, 13f)
        cs.newLineAtOffset(40f, pageHeight - 110f)
        cs.showText("${member.fullName} (${member.memberNo})")
        cs.endText()

        val totalPaid = payments.filter { it.syncStatus != "reversed" }.sumOf { it.amountTzs }
        cs.beginText()
        cs.setFont(fontRegular, 11f)
        cs.newLineAtOffset(40f, pageHeight - 130f)
        cs.showText("Phone: ${member.phone.ifBlank { "N/A" }} · Status: ${member.status.uppercase()} · Total Recorded: ${money(totalPaid)}")
        cs.endText()

        // Table Header
        var y = pageHeight - 160f
        cs.setNonStrokingColor(Color(23, 54, 93))
        cs.addRect(40f, y - 20f, pageWidth - 80f, 20f)
        cs.fill()

        cs.setNonStrokingColor(Color.WHITE)
        cs.setFont(fontBold, 10f)

        cs.beginText()
        cs.newLineAtOffset(48f, y - 14f)
        cs.showText("DATE")
        cs.endText()

        cs.beginText()
        cs.newLineAtOffset(160f, y - 14f)
        cs.showText("PERIOD")
        cs.endText()

        cs.beginText()
        cs.newLineAtOffset(250f, y - 14f)
        cs.showText("METHOD")
        cs.endText()

        cs.beginText()
        cs.newLineAtOffset(340f, y - 14f)
        cs.showText("RECEIPT")
        cs.endText()

        cs.beginText()
        cs.newLineAtOffset(450f, y - 14f)
        cs.showText("AMOUNT")
        cs.endText()

        y -= 20f

        cs.setNonStrokingColor(Color.BLACK)
        cs.setFont(fontRegular, 9.5f)

        payments.forEach { p ->
            y -= 18f
            if (y < 60f) return@forEach

            val dStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(p.paidAt))
            val pStr = "${p.periodMonth.toString().padStart(2, '0')}/${p.periodYear}"

            cs.beginText()
            cs.newLineAtOffset(48f, y)
            cs.showText(dStr)
            cs.endText()

            cs.beginText()
            cs.newLineAtOffset(160f, y)
            cs.showText(pStr)
            cs.endText()

            cs.beginText()
            cs.newLineAtOffset(250f, y)
            cs.showText(p.method.uppercase())
            cs.endText()

            cs.beginText()
            cs.newLineAtOffset(340f, y)
            cs.showText(p.receiptNo)
            cs.endText()

            cs.beginText()
            cs.newLineAtOffset(450f, y)
            cs.showText(money(p.amountTzs))
            cs.endText()

            cs.setStrokingColor(Color.LIGHT_GRAY)
            cs.setLineWidth(0.5f)
            cs.moveTo(40f, y - 4f)
            cs.lineTo(pageWidth - 40f, y - 4f)
            cs.stroke()
        }

        cs.close()

        val tempDir = File(System.getProperty("java.io.tmpdir") ?: ".", "uwapo_reports")
        if (!tempDir.exists()) tempDir.mkdirs()
        val outFile = File(tempDir, "statement_${member.memberNo}_${System.currentTimeMillis()}.pdf")
        document.save(outFile)
        document.close()
        return outFile
    }

    fun viewPdf(file: File) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
