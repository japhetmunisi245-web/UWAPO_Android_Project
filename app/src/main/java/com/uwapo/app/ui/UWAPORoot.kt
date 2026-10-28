package com.uwapo.app.ui

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwapo.app.R
import com.uwapo.app.data.CsvImportExport
import com.uwapo.app.data.ExpenseEntity
import com.uwapo.app.data.MemberEntity
import com.uwapo.app.data.PaymentEntity
import com.uwapo.app.data.UWAPODao
import com.uwapo.app.pdf.PdfGenerator
import com.uwapo.app.reminders.ReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private enum class Tab(val labelRes: Int) {
    HOME(R.string.tab_home),
    MEMBERS(R.string.tab_members),
    PAYMENTS(R.string.tab_payments),
    FINANCE(R.string.tab_finance),
    REPORTS(R.string.tab_reports)
}

private fun money(amount: Long): String = "TZS " + NumberFormat.getIntegerInstance(Locale("en", "TZ")).format(amount)

private fun setAppLocale(context: Context, language: String) {
    val locale = Locale(language)
    Locale.setDefault(locale)
    val resources = context.resources
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    @Suppress("DEPRECATION")
    resources.updateConfiguration(config, resources.displayMetrics)
}

@Composable
private fun methodLabel(method: String): String = when (method) {
    "cash" -> stringResource(R.string.method_cash)
    "bank" -> stringResource(R.string.method_bank)
    "mobile_money" -> stringResource(R.string.method_mobile_money)
    else -> stringResource(R.string.method_other)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UWAPORoot(dao: UWAPODao) {
    val context = LocalContext.current
    var currentLanguage by rememberSaveable {
        mutableStateOf(if (Locale.getDefault().language == "sw") "sw" else "en")
    }

    SideEffect {
        setAppLocale(context, currentLanguage)
    }

    val members by dao.observeMembers().collectAsState(initial = emptyList())
    val payments by dao.observePayments().collectAsState(initial = emptyList())
    val expenses by dao.observeExpenses().collectAsState(initial = emptyList())
    val cal = remember { Calendar.getInstance() }
    val currentYear = cal.get(Calendar.YEAR)
    val currentMonth = cal.get(Calendar.MONTH) + 1

    val monthTotal by dao.observeMonthlyTotal(currentYear, currentMonth).collectAsState(initial = 0L)
    val yearlyTotal by dao.observeYearlyTotal(currentYear).collectAsState(initial = 0L)
    val activeCount by dao.observeActiveMemberCount().collectAsState(initial = 0)
    val lifetimeTotal by dao.observeLifetimeTotal().collectAsState(initial = 0L)
    val expensesTotal by dao.observePaidExpensesTotal().collectAsState(initial = 0L)

    var selectedTab by remember { mutableStateOf(Tab.HOME) }
    var showAddMember by remember { mutableStateOf(false) }
    var showPayment by remember { mutableStateOf(false) }
    var showExpense by remember { mutableStateOf(false) }
    var paymentToReverse by remember { mutableStateOf<PaymentEntity?>(null) }

    var initialMemberForPayment by remember { mutableStateOf<MemberEntity?>(null) }
    var initialYearForPayment by remember { mutableStateOf<Int?>(null) }
    var initialMonthForPayment by remember { mutableStateOf<Int?>(null) }

    var batchToImport by remember { mutableStateOf<BatchYear?>(null) }
    var activeBatchForAddMember by remember { mutableStateOf<BatchYear?>(null) }

    var selectedMemberForProfile by remember { mutableStateOf<MemberEntity?>(null) }
    var selectedPaymentForReceipt by remember { mutableStateOf<PaymentEntity?>(null) }
    var selectedExpenseForDetail by remember { mutableStateOf<ExpenseEntity?>(null) }

    val scope = rememberCoroutineScope()

    val currentPaidMemberIds = remember(payments, currentYear, currentMonth) {
        payments.filter { it.periodYear == currentYear && it.periodMonth == currentMonth && it.syncStatus != "reversed" }
            .map { it.memberId }.toSet()
    }
    val defaulters = remember(members, currentPaidMemberIds) {
        members.filter { it.status == "active" && it.id !in currentPaidMemberIds }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { CsvImportExport.import(it, dao) }
                        ?: throw IllegalStateException("Could not open the selected file.")
                }
                val msg = context.getString(R.string.toast_import_complete, result.membersAdded, result.paymentsAdded, result.rowsSkipped)
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                val msg = context.getString(R.string.toast_import_failed, e.message ?: "")
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val batchImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetBatch = batchToImport
        if (uri != null && targetBatch != null) scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        CsvImportExport.import(stream, dao, targetStartYear = targetBatch.year)
                    } ?: throw IllegalStateException("Could not open the selected file.")
                }
                val msg = context.getString(
                    R.string.toast_batch_import_complete,
                    result.membersAdded,
                    targetBatch.displayLabel,
                    result.paymentsAdded,
                    result.rowsSkipped
                )
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                val msg = context.getString(R.string.toast_import_failed, e.message ?: "")
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } finally {
                batchToImport = null
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            try {
                val csv = CsvImportExport.export(members, payments)
                withContext(Dispatchers.IO) { (context.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Could not create export file.")).bufferedWriter(Charsets.UTF_8).use { it.write(csv) } }
                Toast.makeText(context, context.getString(R.string.toast_csv_saved), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.toast_export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    var paymentToDelete by remember { mutableStateOf<PaymentEntity?>(null) }
    var expenseToDelete by remember { mutableStateOf<ExpenseEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = { currentLanguage = if (currentLanguage == "sw") "en" else "sw" },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (currentLanguage == "sw") "EN" else "SW",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    val icon = when (tab) {
                        Tab.HOME -> Icons.Default.Home
                        Tab.MEMBERS -> Icons.Default.People
                        Tab.PAYMENTS -> Icons.Default.Payments
                        Tab.FINANCE -> Icons.Default.ReceiptLong
                        Tab.REPORTS -> Icons.Default.Assessment
                    }
                    val label = stringResource(tab.labelRes)
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        },
        floatingActionButton = {
            when (selectedTab) {
                Tab.MEMBERS -> FloatingActionButton(onClick = { showAddMember = true }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_member)) }
                Tab.PAYMENTS -> FloatingActionButton(onClick = { showPayment = true }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.record_payment)) }
                Tab.FINANCE -> FloatingActionButton(onClick = { showExpense = true }) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.record_expense)) }
                else -> Unit
            }
        }
    ) { padding ->
        when (selectedTab) {
            Tab.HOME -> DashboardScreen(Modifier.padding(padding), activeCount, monthTotal, lifetimeTotal, expensesTotal, payments.size, currentLanguage, defaulters.size)
            Tab.MEMBERS -> MembersScreen(
                modifier = Modifier.padding(padding),
                members = members,
                payments = payments,
                onImport = {
                    importLauncher.launch(
                        arrayOf(
                            "text/*",
                            "text/csv",
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/xls",
                            "application/xlsx",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                },
                onImportBatchForYear = { batch ->
                    batchToImport = batch
                    batchImportLauncher.launch(
                        arrayOf(
                            "text/*",
                            "text/csv",
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/xls",
                            "application/xlsx",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                },
                onAddMemberForBatch = { batch ->
                    activeBatchForAddMember = batch
                    showAddMember = true
                },
                onSelectMember = { selectedMemberForProfile = it },
                onDeleteMember = { m ->
                    scope.launch {
                        dao.deleteMember(m.id)
                        if (selectedMemberForProfile?.id == m.id) {
                            selectedMemberForProfile = null
                        }
                        Toast.makeText(context, context.getString(R.string.toast_member_deleted), Toast.LENGTH_SHORT).show()
                    }
                },
                onRecordPaymentForMember = { member, year, month ->
                    initialMemberForPayment = member
                    initialYearForPayment = year
                    initialMonthForPayment = month
                    showPayment = true
                }
            )
            Tab.PAYMENTS -> PaymentsScreen(
                Modifier.padding(padding), payments, members,
                onReverse = { paymentToReverse = it },
                onDeletePayment = { paymentToDelete = it },
                onSelectPayment = { selectedPaymentForReceipt = it }
            )
            Tab.FINANCE -> FinanceScreen(
                Modifier.padding(padding), expenses,
                onSelectExpense = { selectedExpenseForDetail = it },
                onApprove = { expense ->
                    scope.launch {
                        dao.setExpenseLocalStatus(expense.id, "paid")
                        Toast.makeText(context, context.getString(R.string.toast_expense_paid), Toast.LENGTH_LONG).show()
                    }
                },
                onDeleteExpense = { expenseToDelete = it }
            )
            Tab.REPORTS -> ReportsScreen(
                modifier = Modifier.padding(padding),
                members = members,
                payments = payments,
                expenses = expenses,
                onExport = { exportLauncher.launch("uwapo-report-${System.currentTimeMillis()}.csv") }
            )
        }
    }

    selectedMemberForProfile?.let { member ->
        MemberProfileDialog(
            member = member,
            payments = payments,
            onDismiss = { selectedMemberForProfile = null },
            onUpdateProfile = { phone, status, notes ->
                scope.launch {
                    dao.updateMemberProfile(member.id, phone, status, notes)
                    selectedMemberForProfile = null
                }
            },
            onDeleteMember = { m ->
                scope.launch {
                    dao.deleteMember(m.id)
                    selectedMemberForProfile = null
                    Toast.makeText(context, context.getString(R.string.toast_member_deleted), Toast.LENGTH_SHORT).show()
                }
            },
            onDeletePayment = { p ->
                paymentToDelete = p
            }
        )
    }

    selectedPaymentForReceipt?.let { payment ->
        val member = members.firstOrNull { it.id == payment.memberId }
        ReceiptViewerDialog(
            payment = payment,
            member = member,
            onDismiss = { selectedPaymentForReceipt = null }
        )
    }

    selectedExpenseForDetail?.let { expense ->
        ExpenseDetailDialog(
            expense = expense,
            onDismiss = { selectedExpenseForDetail = null },
            onUpdateStatus = { newStatus ->
                scope.launch {
                    dao.setExpenseLocalStatus(expense.id, newStatus)
                    selectedExpenseForDetail = null
                }
            },
            onAttachReceiptPhoto = { imagePath ->
                scope.launch {
                    dao.setExpenseReceiptImage(expense.id, imagePath)
                }
            },
            onDeleteExpense = { e ->
                expenseToDelete = e
                selectedExpenseForDetail = null
            }
        )
    }

    paymentToDelete?.let { payment ->
        val pAmtStr = money(payment.amountTzs)
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_payment_title)) },
            text = { Text(stringResource(R.string.dialog_delete_payment_msg, pAmtStr, payment.receiptNo)) },
            confirmButton = {
                Button(
                    onClick = {
                        val target = payment
                        paymentToDelete = null
                        scope.launch {
                            dao.deletePayment(target.id)
                            Toast.makeText(context, context.getString(R.string.toast_payment_deleted), Toast.LENGTH_SHORT).show()
                        }
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

    expenseToDelete?.let { expense ->
        val eAmtStr = money(expense.amountTzs)
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_expense_title)) },
            text = { Text(stringResource(R.string.dialog_delete_expense_msg, expense.category, eAmtStr)) },
            confirmButton = {
                Button(
                    onClick = {
                        val target = expense
                        expenseToDelete = null
                        scope.launch {
                            dao.deleteExpense(target.id)
                            Toast.makeText(context, context.getString(R.string.toast_expense_deleted), Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete_expense))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { expenseToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showAddMember) {
        AddMemberDialog(
            initialBatchYear = activeBatchForAddMember,
            onDismiss = {
                showAddMember = false
                activeBatchForAddMember = null
            },
            onSave = { number, name, phone, joinedAtTimestamp ->
                scope.launch {
                    try {
                        dao.insertMember(
                            MemberEntity(
                                UUID.randomUUID().toString(),
                                number.trim(),
                                name.trim(),
                                phone.trim(),
                                joinedAt = joinedAtTimestamp,
                                syncStatus = "local"
                            )
                        )
                        showAddMember = false
                        activeBatchForAddMember = null
                    } catch (_: Exception) { /* UI remains open */ }
                }
            }
        )
    }
    if (showPayment) {
        RecordPaymentDialog(
            members = members,
            initialMember = initialMemberForPayment,
            initialYear = initialYearForPayment,
            initialMonth = initialMonthForPayment,
            onDismiss = {
                showPayment = false
                initialMemberForPayment = null
                initialYearForPayment = null
                initialMonthForPayment = null
            },
            onSave = { member, amount, year, month, method ->
                scope.launch {
                    try {
                        val receipt = "LOCAL-${UUID.randomUUID().toString().take(8).uppercase()}"
                        dao.insertPayment(PaymentEntity(UUID.randomUUID().toString(), member.id, amount, year, month, method = method, receiptNo = receipt, requestId = UUID.randomUUID().toString(), syncStatus = "local"))
                        showPayment = false
                        initialMemberForPayment = null
                        initialYearForPayment = null
                        initialMonthForPayment = null
                        Toast.makeText(context, context.getString(R.string.toast_payment_saved), Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.toast_payment_save_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
    if (showExpense) {
        AddExpenseDialog(onDismiss = { showExpense = false }, onSave = { category, description, amount ->
            scope.launch {
                try {
                    dao.insertExpense(ExpenseEntity(UUID.randomUUID().toString(), category, description, amount))
                    showExpense = false
                    Toast.makeText(context, context.getString(R.string.toast_expense_saved), Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.toast_expense_save_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        })
    }
    paymentToReverse?.let { payment ->
        ReversePaymentDialog(payment = payment, onDismiss = { paymentToReverse = null }, onConfirm = { reason ->
            scope.launch {
                try {
                    if (reason.trim().length < 5) throw IllegalArgumentException(context.getString(R.string.error_reversal_reason_min_length))
                    dao.markPaymentReversed(payment.id)
                    paymentToReverse = null
                    Toast.makeText(context, context.getString(R.string.toast_payment_reversed), Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.toast_reversal_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        })
    }
}

@Composable
private fun DashboardScreen(
    modifier: Modifier,
    activeCount: Int,
    monthTotal: Long,
    lifetimeTotal: Long,
    expensesTotal: Long,
    paymentCount: Int,
    language: String,
    defaultersCount: Int
) {
    val locale = remember(language) { Locale(language) }
    val month = remember(locale) { SimpleDateFormat("MMMM yyyy", locale).format(Date()) }
    val context = LocalContext.current
    val netBalance = lifetimeTotal - expensesTotal

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.financial_overview, month), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(stringResource(R.string.active_members), activeCount.toString(), Modifier.weight(1f))
            MetricCard(stringResource(R.string.payments_recorded), paymentCount.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(stringResource(R.string.collected_this_month), money(monthTotal), Modifier.weight(1f))
            MetricCard(stringResource(R.string.all_time_recorded), money(lifetimeTotal), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(stringResource(R.string.total_expenses), money(expensesTotal), Modifier.weight(1f))
            MetricCard(stringResource(R.string.net_balance), money(netBalance), Modifier.weight(1f))
        }
        val expected = activeCount.toLong() * 10000L
        val outstanding = (expected - monthTotal).coerceAtLeast(0L)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(stringResource(R.string.expected_this_month), money(expected), Modifier.weight(1f))
            MetricCard(stringResource(R.string.outstanding_estimate), money(outstanding), Modifier.weight(1f))
        }

        if (defaultersCount > 0) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.defaulters_list), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.defaulters_count, defaultersCount), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = {
                        ReminderManager.sendDueDateNotification(context, defaultersCount)
                        Toast.makeText(context, context.getString(R.string.reminder_sent), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.send_due_notification))
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.contribution_target), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
                Text(stringResource(R.string.target_amount_description), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.target_default_note), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

data class BatchYear(
    val year: Int
) {
    val displayLabel: String get() = "$year"
    val periodLabel: String get() = "January $year – December $year"
}

enum class ContributionPeriod(
    val titleRes: Int,
    val monthRange: IntRange
) {
    TERM_1(R.string.term_1_title, 1..6),
    TERM_2(R.string.term_2_title, 7..12);

    fun getYear(batchYear: BatchYear): Int {
        return batchYear.year
    }
}

private sealed interface MembersNavStep {
    object SelectBatch : MembersNavStep
    data class SelectPeriod(val batchYear: BatchYear) : MembersNavStep
    data class PeriodDetails(val batchYear: BatchYear, val period: ContributionPeriod) : MembersNavStep
}

@Composable
private fun AddBatchYearDialog(
    onDismiss: () -> Unit,
    onAdd: (BatchYear) -> Unit
) {
    var yearText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_batch_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { yearText = it; errorText = "" },
                    label = { Text(stringResource(R.string.batch_year_input_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (errorText.isNotBlank()) {
                    Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val y = yearText.toIntOrNull()
                    if (y == null || y < 2000 || y > 2100) {
                        errorText = "Enter a valid year (e.g. 2025)."
                    } else {
                        onAdd(BatchYear(y))
                    }
                }
            ) {
                Text(stringResource(R.string.btn_add_batch))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

private fun getBatchEndTimestamp(batchYear: BatchYear): Long {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Africa/Dar_es_Salaam"))
    cal.set(batchYear.year, java.util.Calendar.DECEMBER, 31, 23, 59, 59)
    return cal.timeInMillis
}

private fun getBatchStartTimestamp(batchYear: BatchYear): Long {
    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Africa/Dar_es_Salaam"))
    cal.set(batchYear.year, java.util.Calendar.JANUARY, 1, 0, 0, 0)
    return cal.timeInMillis
}

@Composable
private fun MembersScreen(
    modifier: Modifier,
    members: List<MemberEntity>,
    payments: List<PaymentEntity>,
    onImport: () -> Unit,
    onImportBatchForYear: (BatchYear) -> Unit,
    onAddMemberForBatch: (BatchYear) -> Unit,
    onSelectMember: (MemberEntity) -> Unit,
    onDeleteMember: (MemberEntity) -> Unit,
    onRecordPaymentForMember: (MemberEntity, Int, Int) -> Unit
) {
    var selectedMainTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Batches & Terms, 1 = All Members
    var navStep by remember { mutableStateOf<MembersNavStep>(MembersNavStep.SelectBatch) }
    var showAddBatchDialog by remember { mutableStateOf(false) }

    val customBatchYears = remember { mutableStateListOf<BatchYear>() }

    val defaultBatchYears = remember(payments, customBatchYears.size) {
        val base = mutableListOf(
            BatchYear(2023),
            BatchYear(2024),
            BatchYear(2025),
            BatchYear(2026),
            BatchYear(2027)
        )
        payments.map { it.periodYear }.distinct().forEach { yr ->
            if (base.none { it.year == yr }) {
                base.add(BatchYear(yr))
            }
        }
        base.addAll(customBatchYears)
        base.distinctBy { it.year }.sortedByDescending { it.year }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (selectedMainTab == 1 || navStep is MembersNavStep.SelectBatch) {
            TabRow(selectedTabIndex = selectedMainTab, modifier = Modifier.fillMaxWidth()) {
                Tab(
                    selected = selectedMainTab == 0,
                    onClick = { selectedMainTab = 0 },
                    text = { Text(stringResource(R.string.batches_tab), fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) }
                )
                Tab(
                    selected = selectedMainTab == 1,
                    onClick = { selectedMainTab = 1 },
                    text = { Text(stringResource(R.string.all_members_tab), fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.People, contentDescription = null) }
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (selectedMainTab == 1) {
            AllMembersDirectoryView(
                members = members,
                onImport = onImport,
                onSelectMember = onSelectMember,
                onDeleteMember = onDeleteMember
            )
        } else {
            when (val step = navStep) {
                is MembersNavStep.SelectBatch -> {
                    Step1SelectBatchView(
                        batchYears = defaultBatchYears,
                        members = members,
                        payments = payments,
                        onSelectBatch = { batch ->
                            navStep = MembersNavStep.SelectPeriod(batch)
                        },
                        onAddCustomBatch = { showAddBatchDialog = true }
                    )
                }
                is MembersNavStep.SelectPeriod -> {
                    Step2SelectPeriodView(
                        batchYear = step.batchYear,
                        members = members,
                        payments = payments,
                        onBack = { navStep = MembersNavStep.SelectBatch },
                        onSelectPeriod = { period ->
                            navStep = MembersNavStep.PeriodDetails(step.batchYear, period)
                        }
                    )
                }
                is MembersNavStep.PeriodDetails -> {
                    Step3PeriodDetailsView(
                        batchYear = step.batchYear,
                        period = step.period,
                        members = members,
                        payments = payments,
                        onBack = { navStep = MembersNavStep.SelectPeriod(step.batchYear) },
                        onSelectMember = onSelectMember,
                        onRecordPaymentForMember = onRecordPaymentForMember,
                        onImportBatchForYear = onImportBatchForYear,
                        onAddMemberForBatch = onAddMemberForBatch
                    )
                }
            }
        }
    }

    if (showAddBatchDialog) {
        AddBatchYearDialog(
            onDismiss = { showAddBatchDialog = false },
            onAdd = { newBatch ->
                if (customBatchYears.none { it.year == newBatch.year }) {
                    customBatchYears.add(newBatch)
                }
                showAddBatchDialog = false
            }
        )
    }
}

@Composable
private fun Step1SelectBatchView(
    batchYears: List<BatchYear>,
    members: List<MemberEntity>,
    payments: List<PaymentEntity>,
    onSelectBatch: (BatchYear) -> Unit,
    onAddCustomBatch: () -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.select_batch_year),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Annual batches for member contributions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onAddCustomBatch) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_custom_batch), tint = MaterialTheme.colorScheme.primary)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(batchYears, key = { it.displayLabel }) { batch ->
                val batchPayments = payments.filter {
                    it.syncStatus != "reversed" && it.periodYear == batch.year
                }
                val totalCollected = batchPayments.sumOf { it.amountTzs }
                val batchActiveMembersCount = members.count { it.status == "active" }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectBatch(batch) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.batch_year_label, batch.displayLabel),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                batch.periodLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Collected: ${money(totalCollected)} · $batchActiveMembersCount members",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onAddCustomBatch,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_custom_batch))
                }
            }
        }
    }
}

@Composable
private fun Step2SelectPeriodView(
    batchYear: BatchYear,
    members: List<MemberEntity>,
    payments: List<PaymentEntity>,
    onBack: () -> Unit,
    onSelectPeriod: (ContributionPeriod) -> Unit
) {
    val activeMembers = remember(members) {
        members.filter { it.status == "active" }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.back_to_batches))
            }
        }

        Text(
            stringResource(R.string.batch_year_label, batchYear.displayLabel),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.select_contribution_period),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ContributionPeriod.entries.toTypedArray(), key = { it.name }) { period ->
                val targetYear = period.getYear(batchYear)
                val termPayments = payments.filter {
                    it.syncStatus != "reversed" &&
                    it.periodYear == targetYear &&
                    it.periodMonth in period.monthRange
                }
                val totalCollected = termPayments.sumOf { it.amountTzs }

                val paidCount = activeMembers.count { m ->
                    val memberSum = termPayments.filter { it.memberId == m.id }.sumOf { it.amountTzs }
                    memberSum >= 60000L
                }
                val partialCount = activeMembers.count { m ->
                    val memberSum = termPayments.filter { it.memberId == m.id }.sumOf { it.amountTzs }
                    memberSum in 1L..59999L
                }
                val pendingCount = activeMembers.count { m ->
                    val memberSum = termPayments.filter { it.memberId == m.id }.sumOf { it.amountTzs }
                    memberSum == 0L
                }

                val periodSubtitle = when (period) {
                    ContributionPeriod.TERM_1 -> stringResource(R.string.term_1_subtitle, targetYear)
                    ContributionPeriod.TERM_2 -> stringResource(R.string.term_2_subtitle, targetYear)
                }

                val monthsDetailText = when (period) {
                    ContributionPeriod.TERM_1 -> stringResource(R.string.term_months_jan_jun)
                    ContributionPeriod.TERM_2 -> stringResource(R.string.term_months_jul_dec)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPeriod(period) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(period.titleRes),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }

                        Text(periodSubtitle, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text(monthsDetailText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.total_collected_period, money(totalCollected)), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Req: TZS 60,000", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Text(
                            stringResource(R.string.members_summary_counts, paidCount, partialCount, pendingCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Step3PeriodDetailsView(
    batchYear: BatchYear,
    period: ContributionPeriod,
    members: List<MemberEntity>,
    payments: List<PaymentEntity>,
    onBack: () -> Unit,
    onSelectMember: (MemberEntity) -> Unit,
    onRecordPaymentForMember: (MemberEntity, Int, Int) -> Unit,
    onImportBatchForYear: (BatchYear) -> Unit,
    onAddMemberForBatch: (BatchYear) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("all") }
    var expandedMemberId by remember { mutableStateOf<String?>(null) }

    val eligibleMembers = remember(members) {
        members.filter { it.status == "active" }
    }

    val targetYear = remember(batchYear, period) { period.getYear(batchYear) }
    val requiredPerTerm = 60000L

    val termPayments = remember(payments, targetYear, period) {
        payments.filter {
            it.syncStatus != "reversed" &&
            it.periodYear == targetYear &&
            it.periodMonth in period.monthRange
        }
    }

    val totalCollected = remember(termPayments) { termPayments.sumOf { it.amountTzs } }

    val memberStats = remember(eligibleMembers, termPayments, search, selectedStatusFilter) {
        eligibleMembers.map { m ->
            val mPayments = termPayments.filter { it.memberId == m.id }
            val paid = mPayments.sumOf { it.amountTzs }
            val balance = (requiredPerTerm - paid).coerceAtLeast(0L)
            val statusKey = when {
                paid >= requiredPerTerm -> "paid"
                paid > 0L -> "partial"
                else -> "pending"
            }
            m to Triple(paid, balance, statusKey to mPayments)
        }.filter { (m, triple) ->
            val (_, _, pair) = triple
            val (statusKey, _) = pair
            val matchesSearch = m.fullName.contains(search, true) || m.memberNo.contains(search, true) || m.phone.contains(search, true)
            val matchesFilter = selectedStatusFilter == "all" || statusKey.equals(selectedStatusFilter, true)
            matchesSearch && matchesFilter
        }
    }

    val paidCount = remember(eligibleMembers, termPayments) {
        eligibleMembers.count { m -> termPayments.filter { it.memberId == m.id }.sumOf { it.amountTzs } >= requiredPerTerm }
    }
    val partialCount = remember(eligibleMembers, termPayments) {
        eligibleMembers.count { m -> termPayments.filter { it.memberId == m.id }.sumOf { it.amountTzs } in 1L..59999L }
    }
    val pendingCount = remember(eligibleMembers, termPayments) {
        eligibleMembers.count { m -> termPayments.filter { it.memberId == m.id }.sumOf { it.amountTzs } == 0L }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "${batchYear.displayLabel} - ${stringResource(period.titleRes)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onImportBatchForYear(batchYear) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.FileDownload, contentDescription = "Import (.xls)", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { onAddMemberForBatch(batchYear) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Add Member", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total: ${money(totalCollected)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text(
                    "$paidCount Paid · $partialCount Partial · $pendingCount Pending",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text(stringResource(R.string.search_members_hint), style = MaterialTheme.typography.bodySmall) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("all", "paid", "partial", "pending").forEach { statusKey ->
                val label = when (statusKey) {
                    "paid" -> stringResource(R.string.status_paid)
                    "partial" -> stringResource(R.string.status_partial)
                    "pending" -> stringResource(R.string.status_pending)
                    else -> "All"
                }
                FilterChip(
                    selected = selectedStatusFilter == statusKey,
                    onClick = { selectedStatusFilter = statusKey },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        if (memberStats.isEmpty()) {
            EmptyState(stringResource(R.string.no_matching_members), stringResource(R.string.try_another_search))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(memberStats, key = { it.first.id }) { (member, data) ->
                    val (paid, balance, statusPair) = data
                    val (statusKey, mPayments) = statusPair
                    val isExpanded = expandedMemberId == member.id

                    val (statusLabel, statusBg, statusFg) = when (statusKey) {
                        "paid" -> Triple(stringResource(R.string.status_paid), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        "partial" -> Triple(stringResource(R.string.status_partial), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                        else -> Triple(stringResource(R.string.status_pending), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    }

                    Card(shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(10.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(member.fullName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("${member.memberNo}${if (member.phone.isNotBlank()) " · ${member.phone}" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(shape = RoundedCornerShape(6.dp), color = statusBg) {
                                    Text(statusLabel, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = statusFg)
                                }
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(stringResource(R.string.required_amount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("TZS 60,000", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                }
                                Column {
                                    Text(stringResource(R.string.amount_paid), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(money(paid), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Column {
                                    Text(stringResource(R.string.balance), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(money(balance), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            HorizontalDivider()

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedMemberId = if (isExpanded) null else member.id },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${stringResource(R.string.payment_history)} (${mPayments.size})",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (isExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (mPayments.isEmpty()) {
                                        Text(
                                            stringResource(R.string.no_payments_period),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        mPayments.forEach { p ->
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Row(
                                                    Modifier.padding(6.dp).fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text("${p.periodMonth.toString().padStart(2, '0')}/${p.periodYear} · ${p.receiptNo}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                                        Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(p.paidAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    Text(money(p.amountTzs), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }

                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = { onRecordPaymentForMember(member, targetYear, period.monthRange.first) },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(2.dp))
                                            Text(stringResource(R.string.record_payment), style = MaterialTheme.typography.labelSmall)
                                        }
                                        OutlinedButton(
                                            onClick = { onSelectMember(member) },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(stringResource(R.string.member_profile), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllMembersDirectoryView(
    members: List<MemberEntity>,
    onImport: () -> Unit,
    onSelectMember: (MemberEntity) -> Unit,
    onDeleteMember: (MemberEntity) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("all") }
    var memberToDelete by remember { mutableStateOf<MemberEntity?>(null) }

    val filtered = remember(members, search, selectedStatusFilter) {
        members.filter { m ->
            (selectedStatusFilter == "all" || m.status.equals(selectedStatusFilter, true)) &&
            (m.fullName.contains(search, true) || m.memberNo.contains(search, true) || m.phone.contains(search, true))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(stringResource(R.string.members_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.count_registered, members.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(search, { search = it }, label = { Text(stringResource(R.string.search_members_hint)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all", "active", "inactive", "suspended").forEach { s ->
                FilterChip(
                    selected = selectedStatusFilter == s,
                    onClick = { selectedStatusFilter = s },
                    label = { Text(s.replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.import_csv))
        }
        Spacer(Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            EmptyState(
                stringResource(R.string.no_matching_members),
                if (members.isEmpty()) stringResource(R.string.register_first_member) else stringResource(R.string.try_another_search)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { member ->
                    Card(modifier = Modifier.clickable { onSelectMember(member) }) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(member.fullName.take(1).uppercase(), Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(member.fullName, fontWeight = FontWeight.SemiBold)
                                Text("${member.memberNo}${if (member.phone.isNotBlank()) " · ${member.phone}" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(member.status.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
                            IconButton(onClick = { memberToDelete = member }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete_member), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    memberToDelete?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_member_title)) },
            text = { Text(stringResource(R.string.dialog_delete_member_msg, member.fullName)) },
            confirmButton = {
                Button(
                    onClick = {
                        val target = member
                        memberToDelete = null
                        onDeleteMember(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete_member))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { memberToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun PaymentsScreen(
    modifier: Modifier,
    payments: List<PaymentEntity>,
    members: List<MemberEntity>,
    onReverse: (PaymentEntity) -> Unit,
    onDeletePayment: (PaymentEntity) -> Unit,
    onSelectPayment: (PaymentEntity) -> Unit
) {
    val memberMap = remember(members) { members.associateBy { it.id } }
    var selectedBatchFilter by remember { mutableStateOf("all") }

    val availableBatches = remember(payments) {
        val years = payments.map { p -> p.periodYear.toString() }.distinct().sortedDescending()
        listOf("all") + years
    }

    val groupedPayments = remember(payments, selectedBatchFilter) {
        payments.filter { p ->
            if (selectedBatchFilter == "all") true
            else p.periodYear.toString() == selectedBatchFilter
        }.groupBy { p -> p.periodYear.toString() }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.payments_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.count_payment_records, payments.size), color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Batch filter chips
        if (availableBatches.size > 1) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.filter_by_batch) + ":", style = MaterialTheme.typography.labelSmall)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(availableBatches) { bKey ->
                        val label = if (bKey == "all") stringResource(R.string.all_batches) else "Batch $bKey"
                        FilterChip(
                            selected = selectedBatchFilter == bKey,
                            onClick = { selectedBatchFilter = bKey },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        if (payments.isEmpty() || groupedPayments.isEmpty()) {
            EmptyState(
                stringResource(R.string.no_payments_yet),
                if (selectedBatchFilter == "all") stringResource(R.string.record_first_payment)
                else stringResource(R.string.no_payments_for_batch, selectedBatchFilter)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                groupedPayments.forEach { (batchLabel, batchPayments) ->
                    item(key = "header_$batchLabel") {
                        val batchTotal = batchPayments.filter { it.syncStatus != "reversed" }.sumOf { it.amountTzs }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.batch_year_label, batchLabel),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "${money(batchTotal)} (${batchPayments.size})",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    items(batchPayments, key = { it.id }) { payment ->
                        Card(modifier = Modifier.clickable { onSelectPayment(payment) }) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(memberMap[payment.memberId]?.fullName ?: stringResource(R.string.unknown_member), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    Text(money(payment.amountTzs), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    IconButton(onClick = { onDeletePayment(payment) }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete_payment), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Text("${payment.periodMonth.toString().padStart(2, '0')}/${payment.periodYear} · ${methodLabel(payment.method)}", style = MaterialTheme.typography.bodySmall)
                                val syncText = if (payment.syncStatus == "reversed") stringResource(R.string.status_reversed) else stringResource(R.string.status_stored_device)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.receipt_info, payment.receiptNo, syncText), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = if (payment.syncStatus == "reversed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Receipt PDF", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                                if (payment.syncStatus != "reversed") {
                                    TextButton(onClick = { onReverse(payment) }) { Text(stringResource(R.string.reverse_payment)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsScreen(
    modifier: Modifier,
    members: List<MemberEntity>,
    payments: List<PaymentEntity>,
    expenses: List<ExpenseEntity>,
    onExport: () -> Unit
) {
    val context = LocalContext.current
    var selectedReportType by rememberSaveable { mutableIntStateOf(0) } // 0 = Monthly, 1 = Annual

    val currentCal = remember { Calendar.getInstance() }
    var selectedYear by rememberSaveable { mutableIntStateOf(currentCal.get(Calendar.YEAR)) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(currentCal.get(Calendar.MONTH) + 1) }

    val monthNames = remember {
        listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    }

    val activeMembers = remember(members) { members.filter { it.status == "active" } }
    val activeCount = activeMembers.size

    val availableYears = remember(payments, expenses) {
        val y1 = payments.map { it.periodYear }
        val y2 = expenses.map {
            val c = Calendar.getInstance()
            c.timeInMillis = it.expenseDate
            c.get(Calendar.YEAR)
        }
        (y1 + y2 + currentCal.get(Calendar.YEAR)).distinct().sortedDescending()
    }

    // --- MONTHLY DATA ---
    val monthlyPayments = remember(payments, selectedYear, selectedMonth) {
        payments.filter { it.syncStatus != "reversed" && it.periodYear == selectedYear && it.periodMonth == selectedMonth }
    }
    val monthlyCollections = remember(monthlyPayments) { monthlyPayments.sumOf { it.amountTzs } }

    val monthlyApprovedExpenses = remember(expenses, selectedYear, selectedMonth) {
        expenses.filter { exp ->
            (exp.status == "paid" || exp.status == "approved") && run {
                val c = Calendar.getInstance()
                c.timeInMillis = exp.expenseDate
                c.get(Calendar.YEAR) == selectedYear && (c.get(Calendar.MONTH) + 1) == selectedMonth
            }
        }
    }
    val monthlyExpensesTotal = remember(monthlyApprovedExpenses) { monthlyApprovedExpenses.sumOf { it.amountTzs } }
    val monthlyNetMovement = monthlyCollections - monthlyExpensesTotal

    val expectedMonthly = activeCount.toLong() * 10000L
    val estimatedMonthlyArrears = (expectedMonthly - monthlyCollections).coerceAtLeast(0L)

    val monthlyPaidMemberIds = remember(monthlyPayments) {
        monthlyPayments.groupBy { it.memberId }.filter { it.value.sumOf { p -> p.amountTzs } >= 10000L }.keys
    }
    val monthlyArrearsMembers = remember(activeMembers, monthlyPaidMemberIds) {
        activeMembers.filter { it.id !in monthlyPaidMemberIds }
    }

    val monthlyBatchBreakdown = remember(monthlyPayments) {
        monthlyPayments.groupBy { p -> p.periodYear.toString() }.mapValues { entry -> entry.value.sumOf { it.amountTzs } }
    }

    // --- ANNUAL DATA ---
    val annualPayments = remember(payments, selectedYear) {
        payments.filter { it.syncStatus != "reversed" && it.periodYear == selectedYear }
    }
    val annualCollections = remember(annualPayments) { annualPayments.sumOf { it.amountTzs } }

    val annualApprovedExpenses = remember(expenses, selectedYear) {
        expenses.filter { exp ->
            (exp.status == "paid" || exp.status == "approved") && run {
                val c = Calendar.getInstance()
                c.timeInMillis = exp.expenseDate
                c.get(Calendar.YEAR) == selectedYear
            }
        }
    }
    val annualExpensesTotal = remember(annualApprovedExpenses) { annualApprovedExpenses.sumOf { it.amountTzs } }
    val annualNetMovement = annualCollections - annualExpensesTotal

    val expectedAnnual = activeCount.toLong() * 120000L
    val estimatedAnnualShortfall = (expectedAnnual - annualCollections).coerceAtLeast(0L)

    val annualMonthlyBreakdown = remember(annualPayments) {
        (1..12).associate { m ->
            monthNames[m - 1] to annualPayments.filter { p -> p.periodMonth == m }.sumOf { it.amountTzs }
        }
    }

    val annualBatchBreakdown = remember(annualPayments) {
        annualPayments.groupBy { p -> p.periodYear.toString() }.mapValues { entry -> entry.value.sumOf { it.amountTzs } }
    }

    val annualArrearsMembers = remember(activeMembers, annualPayments) {
        activeMembers.filter { m ->
            annualPayments.filter { p -> p.memberId == m.id }.sumOf { it.amountTzs } < 120000L
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.reports_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // View Selector Tabs (Monthly vs Annual)
        TabRow(selectedTabIndex = selectedReportType) {
            Tab(
                selected = selectedReportType == 0,
                onClick = { selectedReportType = 0 },
                text = { Text(stringResource(R.string.monthly_report_tab), fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedReportType == 1,
                onClick = { selectedReportType = 1 },
                text = { Text(stringResource(R.string.annual_report_tab), fontWeight = FontWeight.Bold) }
            )
        }

        // Controls row (Year & Month Selector)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var yearExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = yearExpanded,
                onExpandedChange = { yearExpanded = !yearExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = "Year: $selectedYear",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                ExposedDropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                    availableYears.forEach { yr ->
                        DropdownMenuItem(
                            text = { Text(yr.toString()) },
                            onClick = { selectedYear = yr; yearExpanded = false }
                        )
                    }
                }
            }

            if (selectedReportType == 0) {
                var monthExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = monthExpanded,
                    onExpandedChange = { monthExpanded = !monthExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = monthNames[selectedMonth - 1],
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    ExposedDropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                        (1..12).forEach { m ->
                            DropdownMenuItem(
                                text = { Text(monthNames[m - 1]) },
                                onClick = { selectedMonth = m; monthExpanded = false }
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.export_csv), style = MaterialTheme.typography.labelSmall)
            }

            Button(
                onClick = {
                    val periodTitle = if (selectedReportType == 0) "${monthNames[selectedMonth - 1]} $selectedYear" else "Year $selectedYear"
                    val file = if (selectedReportType == 0) {
                        PdfGenerator.generateReportPdf(
                            context = context,
                            reportType = "Monthly",
                            periodTitle = periodTitle,
                            collectionsTotal = monthlyCollections,
                            expensesTotal = monthlyExpensesTotal,
                            netMovement = monthlyNetMovement,
                            expectedContributions = expectedMonthly,
                            estimatedArrears = estimatedMonthlyArrears,
                            memberCount = members.size,
                            activeCount = activeCount,
                            breakdownMap = monthlyBatchBreakdown,
                            breakdownTitle = "Batch Collection Breakdown",
                            arrearsMembers = monthlyArrearsMembers
                        )
                    } else {
                        PdfGenerator.generateReportPdf(
                            context = context,
                            reportType = "Annual",
                            periodTitle = periodTitle,
                            collectionsTotal = annualCollections,
                            expensesTotal = annualExpensesTotal,
                            netMovement = annualNetMovement,
                            expectedContributions = expectedAnnual,
                            estimatedArrears = estimatedAnnualShortfall,
                            memberCount = members.size,
                            activeCount = activeCount,
                            breakdownMap = annualMonthlyBreakdown,
                            breakdownTitle = "Monthly Collection Breakdown",
                            arrearsMembers = annualArrearsMembers
                        )
                    }
                    PdfGenerator.viewPdf(context, file)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.generate_pdf_report), style = MaterialTheme.typography.labelSmall)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (selectedReportType == 0) {
                item {
                    MetricCard(stringResource(R.string.monthly_net_movement), money(monthlyNetMovement), Modifier.fillMaxWidth())
                }
                item {
                    MetricCard(stringResource(R.string.monthly_collections), money(monthlyCollections), Modifier.fillMaxWidth())
                }
                item {
                    MetricCard(stringResource(R.string.total_expenses), money(monthlyExpensesTotal), Modifier.fillMaxWidth())
                }
                item {
                    MetricCard(stringResource(R.string.expected_monthly), money(expectedMonthly), Modifier.fillMaxWidth())
                }
                item {
                    MetricCard(stringResource(R.string.outstanding_estimate), money(estimatedMonthlyArrears), Modifier.fillMaxWidth())
                }

                if (monthlyBatchBreakdown.isNotEmpty()) {
                    item {
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.batch_collections_summary), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                monthlyBatchBreakdown.forEach { (batchLabel, collected) ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Batch $batchLabel", fontWeight = FontWeight.SemiBold)
                                        Text(money(collected), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.defaulters_list), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            if (monthlyArrearsMembers.isEmpty()) {
                                Text(stringResource(R.string.no_defaulters), color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text(stringResource(R.string.defaulters_count, monthlyArrearsMembers.size), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                monthlyArrearsMembers.forEach { m ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${m.fullName} (${m.memberNo})", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        if (m.phone.isNotBlank()) {
                                            IconButton(onClick = {
                                                ReminderManager.sendSmsReminder(context, m.phone, m.fullName, "${monthNames[selectedMonth - 1]} $selectedYear")
                                            }) {
                                                Icon(Icons.Default.Sms, contentDescription = "SMS", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    MetricCard(stringResource(R.string.annual_net_movement), money(annualNetMovement), Modifier.fillMaxWidth())
                }
                item {
                    MetricCard(stringResource(R.string.annual_collections), money(annualCollections), Modifier.fillMaxWidth())
                }
                item {
                    MetricCard(stringResource(R.string.total_expenses), money(annualExpensesTotal), Modifier.fillMaxWidth())
                }
                item {
                    MetricCard(stringResource(R.string.expected_annual), money(expectedAnnual), Modifier.fillMaxWidth())
                }
                item {
                    MetricCard(stringResource(R.string.estimated_shortfall), money(annualExpensesTotal), Modifier.fillMaxWidth())
                }

                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.monthly_breakdown), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            annualMonthlyBreakdown.forEach { (mName, collected) ->
                                if (collected > 0L) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(mName, fontWeight = FontWeight.SemiBold)
                                        Text(money(collected), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Annual Arrears Summary (${annualArrearsMembers.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            if (annualArrearsMembers.isEmpty()) {
                                Text("All active members have fulfilled annual contributions!", color = MaterialTheme.colorScheme.primary)
                            } else {
                                annualArrearsMembers.forEach { m ->
                                    val mPaid = annualPayments.filter { p -> p.memberId == m.id }.sumOf { it.amountTzs }
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${m.fullName} (${m.memberNo})", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                        Text("Paid: ${money(mPaid)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Box(Modifier.fillMaxWidth().padding(top = 42.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FinanceScreen(
    modifier: Modifier,
    expenses: List<ExpenseEntity>,
    onSelectExpense: (ExpenseEntity) -> Unit,
    onApprove: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.club_finance_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.finance_approval_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (expenses.isEmpty()) {
            EmptyState(stringResource(R.string.no_expenses_recorded), stringResource(R.string.enter_first_expense))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(expenses, key = { it.id }) { expense ->
                    Card(modifier = Modifier.clickable { onSelectExpense(expense) }) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(expense.category, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text(money(expense.amountTzs), fontWeight = FontWeight.Bold)
                                IconButton(onClick = { onDeleteExpense(expense) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete_expense), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Text(expense.description)
                            Text("${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(expense.expenseDate))} · ${expense.status.replace('_', ' ').uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (expense.status == "pending_approval") {
                                TextButton(onClick = { onApprove(expense) }) { Text(stringResource(R.string.approve_and_post)) }
                            }
                        }
                    }
                }
            }
        }
    }
}





@Composable
private fun AddExpenseDialog(onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit) {
    var category by remember { mutableStateOf("Operations") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_record_expense)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(category, { category = it }, label = { Text(stringResource(R.string.label_category)) }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.label_description)) })
                OutlinedTextField(amount, { amount = it; error = "" }, label = { Text(stringResource(R.string.label_amount_tzs)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Text(stringResource(R.string.expense_pending_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(onClick = { val parsed = amount.toLongOrNull(); if (category.isBlank() || description.isBlank()) error = "Enter a category and description." else if (parsed == null || parsed <= 0) error = "Enter a valid amount." else onSave(category.trim(), description.trim(), parsed) }) { Text(stringResource(R.string.btn_save_expense)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun ReversePaymentDialog(payment: PaymentEntity, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val minReasonLengthMsg = stringResource(R.string.error_reversal_reason_min_length)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_reverse_payment_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${money(payment.amountTzs)} · receipt ${payment.receiptNo}")
                Text(stringResource(R.string.reverse_payment_note))
                OutlinedTextField(reason, { reason = it; error = "" }, label = { Text(stringResource(R.string.label_reversal_reason)) })
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(onClick = { if (reason.trim().length < 5) error = minReasonLengthMsg else onConfirm(reason.trim()) }) { Text(stringResource(R.string.reverse_payment)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun AddMemberDialog(
    initialBatchYear: BatchYear? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Long) -> Unit
) {
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val requiredMsg = stringResource(R.string.error_member_fields_required)

    val joinTimestamp = remember(initialBatchYear) {
        if (initialBatchYear != null) getBatchStartTimestamp(initialBatchYear) else System.currentTimeMillis()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_register_member)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialBatchYear != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "Registering for Batch ${initialBatchYear.displayLabel}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                OutlinedTextField(number, { number = it; error = "" }, label = { Text(stringResource(R.string.label_member_number)) }, singleLine = true)
                OutlinedTextField(name, { name = it; error = "" }, label = { Text(stringResource(R.string.label_full_name)) }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.label_phone_optional)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { if (number.isBlank() || name.isBlank()) error = requiredMsg else onSave(number, name, phone, joinTimestamp) }) { Text(stringResource(R.string.btn_save_member)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordPaymentDialog(
    members: List<MemberEntity>,
    initialMember: MemberEntity? = null,
    initialYear: Int? = null,
    initialMonth: Int? = null,
    onDismiss: () -> Unit,
    onSave: (MemberEntity, Long, Int, Int, String) -> Unit
) {
    var selectedMemberId by remember(initialMember) { mutableStateOf(initialMember?.id ?: members.firstOrNull()?.id.orEmpty()) }
    var amount by remember { mutableStateOf("10000") }
    var year by remember(initialYear) { mutableStateOf((initialYear ?: Calendar.getInstance().get(Calendar.YEAR)).toString()) }
    var month by remember(initialMonth) { mutableStateOf((initialMonth ?: (Calendar.getInstance().get(Calendar.MONTH) + 1)).toString()) }
    var method by remember { mutableStateOf("cash") }
    var error by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val selected = members.firstOrNull { it.id == selectedMemberId }

    val errSelectMember = stringResource(R.string.error_select_member)
    val errPositiveAmount = stringResource(R.string.error_valid_positive_amount)
    val errValidMonth = stringResource(R.string.error_valid_month)
    val errValidYear = stringResource(R.string.error_valid_year)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_record_contribution)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (members.isEmpty()) Text(stringResource(R.string.msg_register_member_first))
                else {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = selected?.let { "${it.memberNo} · ${it.fullName}" } ?: stringResource(R.string.select_member),
                            onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.label_member)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            members.forEach { member ->
                                DropdownMenuItem(text = { Text("${member.memberNo} · ${member.fullName}") }, onClick = { selectedMemberId = member.id; expanded = false })
                            }
                        }
                    }
                }
                OutlinedTextField(amount, { amount = it; error = "" }, label = { Text(stringResource(R.string.label_amount_tzs)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(month, { month = it; error = "" }, label = { Text(stringResource(R.string.label_month)) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(year, { year = it; error = "" }, label = { Text(stringResource(R.string.label_year)) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                var methodExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = methodExpanded, onExpandedChange = { methodExpanded = !methodExpanded }) {
                    OutlinedTextField(methodLabel(method), {}, readOnly = true, label = { Text(stringResource(R.string.label_payment_method)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                        listOf("cash", "bank", "mobile_money", "other").forEach { option ->
                            DropdownMenuItem(text = { Text(methodLabel(option)) }, onClick = { method = option; methodExpanded = false })
                        }
                    }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toLongOrNull(); val m = month.toIntOrNull(); val y = year.toIntOrNull()
                when {
                    selected == null -> error = errSelectMember
                    amt == null || amt <= 0 -> error = errPositiveAmount
                    m == null || m !in 1..12 -> error = errValidMonth
                    y == null || y !in 2000..2100 -> error = errValidYear
                    else -> onSave(selected, amt, y, m, method)
                }
            }) { Text(stringResource(R.string.btn_save_payment)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}
