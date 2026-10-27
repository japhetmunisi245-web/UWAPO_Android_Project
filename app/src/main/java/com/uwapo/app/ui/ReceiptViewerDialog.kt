package com.uwapo.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uwapo.app.R
import com.uwapo.app.data.MemberEntity
import com.uwapo.app.data.PaymentEntity
import com.uwapo.app.pdf.PdfGenerator
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceiptViewerDialog(
    payment: PaymentEntity,
    member: MemberEntity?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val memberName = member?.fullName ?: stringResource(R.string.unknown_member)
    val memberNo = member?.memberNo ?: ""

    val moneyStr = "TZS " + NumberFormat.getIntegerInstance(Locale("en", "TZ")).format(payment.amountTzs)
    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(payment.paidAt))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.generate_pdf_receipt), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("RECEIPT: ${payment.receiptNo}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Member: $memberName ($memberNo)")
                        Text("Amount: $moneyStr", fontWeight = FontWeight.Bold)
                        Text("Period: ${payment.periodMonth.toString().padStart(2, '0')}/${payment.periodYear}")
                        Text("Date: $dateStr", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val pdfFile = PdfGenerator.generateReceiptPdf(context, payment, memberName, memberNo)
                            PdfGenerator.viewPdf(context, pdfFile)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.view_pdf), style = MaterialTheme.typography.labelSmall)
                    }

                    Button(
                        onClick = {
                            val pdfFile = PdfGenerator.generateReceiptPdf(context, payment, memberName, memberNo)
                            PdfGenerator.sharePdf(context, pdfFile, "Share Receipt PDF")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.share_receipt), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
