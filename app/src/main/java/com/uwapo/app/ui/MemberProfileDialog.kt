package com.uwapo.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.uwapo.app.reminders.ReminderManager
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberProfileDialog(
    member: MemberEntity,
    payments: List<PaymentEntity>,
    onDismiss: () -> Unit,
    onUpdateProfile: (String, String, String) -> Unit,
    onDeleteMember: (MemberEntity) -> Unit = {},
    onDeletePayment: (PaymentEntity) -> Unit = {}
) {
    var isEditing by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf(member.phone) }
    var status by remember { mutableStateOf(member.status) }
    var notes by remember { mutableStateOf(member.notes) }
    var statusExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val memberPayments = remember(payments, member.id) {
        payments.filter { it.memberId == member.id }
    }

    val joinDateStr = remember(member.joinedAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(member.joinedAt))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(member.fullName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text("${member.memberNo} · ${stringResource(R.string.joined_date, joinDateStr)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showDeleteConfirmation = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete_member), tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = { isEditing = !isEditing }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_profile))
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isEditing) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(stringResource(R.string.label_phone_optional)) },
                        singleLine = true
                    )

                    ExposedDropdownMenuBox(
                        expanded = statusExpanded,
                        onExpandedChange = { statusExpanded = !statusExpanded }
                    ) {
                        OutlinedTextField(
                            value = status.replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.label_status)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = statusExpanded,
                            onDismissRequest = { statusExpanded = false }
                        ) {
                            listOf("active", "inactive", "suspended").forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        status = option
                                        statusExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.label_notes)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            onUpdateProfile(phone.trim(), status, notes.trim())
                            isEditing = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.btn_save_changes))
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(stringResource(R.string.label_status), style = MaterialTheme.typography.labelSmall)
                                Text(member.status.uppercase(), fontWeight = FontWeight.Bold)
                            }
                        }
                        Card(Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Text("Total Payments", style = MaterialTheme.typography.labelSmall)
                                Text("${memberPayments.size} records", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (member.notes.isNotBlank()) {
                        Text("${stringResource(R.string.label_notes)}: ${member.notes}", style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (member.phone.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    val monthStr = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
                                    ReminderManager.sendSmsReminder(context, member.phone, member.fullName, monthStr)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Sms, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("SMS", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Button(
                            onClick = {
                                val file = PdfGenerator.generateStatementPdf(context, member, memberPayments)
                                PdfGenerator.viewPdf(context, file)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("PDF", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Text(stringResource(R.string.payment_history), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (memberPayments.isEmpty()) {
                    Text(stringResource(R.string.no_payments_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    var paymentToDelete by remember { mutableStateOf<PaymentEntity?>(null) }

                    LazyColumn(
                        Modifier.heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(memberPayments, key = { it.id }) { p ->
                            Card {
                                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${p.periodMonth.toString().padStart(2, '0')}/${p.periodYear} · ${p.receiptNo}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(p.paidAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("TZS ${NumberFormat.getIntegerInstance(Locale("en", "TZ")).format(p.amountTzs)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    IconButton(onClick = { paymentToDelete = p }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete_payment), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    paymentToDelete?.let { p ->
                        val pAmtStr = "TZS " + NumberFormat.getIntegerInstance(Locale("en", "TZ")).format(p.amountTzs)
                        AlertDialog(
                            onDismissRequest = { paymentToDelete = null },
                            title = { Text(stringResource(R.string.dialog_delete_payment_title)) },
                            text = { Text(stringResource(R.string.dialog_delete_payment_msg, pAmtStr, p.receiptNo)) },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val target = p
                                        paymentToDelete = null
                                        onDeletePayment(target)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.btn_delete_payment))
                                }
                            },
                            dismissButton = {
                                OutlinedButton(onClick = { paymentToDelete = null }) {
                                    Text(stringResource(R.string.btn_cancel))
                                }
                            }
                        )
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

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.dialog_delete_member_title)) },
            text = { Text(stringResource(R.string.dialog_delete_member_msg, member.fullName)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteMember(member)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete_member))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}
