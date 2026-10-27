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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.app_subtitle), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f))
                    }
                },
                actions = {
                    Text(
                        text = stringResource(R.string.local_only),
                        modifier = Modifier.padding(end = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
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
            Tab.HOME -> DashboardScreen(Modifier.padding(padding), activeCount, monthTotal, lifetimeTotal, payments.size, currentLanguage, defaulters.size)
            Tab.MEMBERS -> MembersScreen(
                Modifier.padding(padding), members,
                onImport = {
                    importLauncher.launch(
                        arrayOf(
                            "text/*",
                            "text/csv",
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/xlsx",
                            "application/octet-stream"
                        )
                    )
                },
                onSelectMember = { selectedMemberForProfile = it }
            )
            Tab.PAYMENTS -> PaymentsScreen(
                Modifier.padding(padding), payments, members,
                onReverse = { paymentToReverse = it },
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
                }
            )
            Tab.REPORTS -> ReportsScreen(
                Modifier.padding(padding), monthTotal, yearlyTotal, expensesTotal, members.size, activeCount, payments.size, defaulters,
                onExport = { exportLauncher.launch("uwapo-register-${System.currentTimeMillis()}.csv") }
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
            }
        )
    }

    if (showAddMember) {
        AddMemberDialog(
            onDismiss = { showAddMember = false },
            onSave = { number, name, phone ->
                scope.launch {
                    try {
                        dao.insertMember(MemberEntity(UUID.randomUUID().toString(), number.trim(), name.trim(), phone.trim(), syncStatus = "local"))
                        showAddMember = false
                    } catch (_: Exception) { /* UI remains open */ }
                }
            }
        )
    }
    if (showPayment) {
        RecordPaymentDialog(
            members = members,
            onDismiss = { showPayment = false },
            onSave = { member, amount, year, month, method ->
                scope.launch {
                    try {
                        val receipt = "LOCAL-${UUID.randomUUID().toString().take(8).uppercase()}"
                        dao.insertPayment(PaymentEntity(UUID.randomUUID().toString(), member.id, amount, year, month, method = method, receiptNo = receipt, requestId = UUID.randomUUID().toString(), syncStatus = "local"))
                        showPayment = false
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
private fun DashboardScreen(modifier: Modifier, activeCount: Int, monthTotal: Long, lifetimeTotal: Long, paymentCount: Int, language: String, defaultersCount: Int) {
    val locale = remember(language) { Locale(language) }
    val month = remember(locale) { SimpleDateFormat("MMMM yyyy", locale).format(Date()) }
    val context = LocalContext.current

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MembersScreen(
    modifier: Modifier,
    members: List<MemberEntity>,
    onImport: () -> Unit,
    onSelectMember: (MemberEntity) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("all") }

    val filtered = remember(members, search, selectedStatusFilter) {
        members.filter { m ->
            (selectedStatusFilter == "all" || m.status.equals(selectedStatusFilter, true)) &&
            (m.fullName.contains(search, true) || m.memberNo.contains(search, true) || m.phone.contains(search, true))
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.members_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.count_registered, members.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentsScreen(
    modifier: Modifier,
    payments: List<PaymentEntity>,
    members: List<MemberEntity>,
    onReverse: (PaymentEntity) -> Unit,
    onSelectPayment: (PaymentEntity) -> Unit
) {
    val memberMap = remember(members) { members.associateBy { it.id } }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.payments_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.count_payment_records, payments.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (payments.isEmpty()) {
            EmptyState(stringResource(R.string.no_payments_yet), stringResource(R.string.record_first_payment))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(payments, key = { it.id }) { payment ->
                    Card(modifier = Modifier.clickable { onSelectPayment(payment) }) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(memberMap[payment.memberId]?.fullName ?: stringResource(R.string.unknown_member), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text(money(payment.amountTzs), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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

@Composable
private fun ReportsScreen(
    modifier: Modifier,
    monthTotal: Long,
    yearlyTotal: Long,
    expensesTotal: Long,
    memberCount: Int,
    activeCount: Int,
    paymentCount: Int,
    defaulters: List<MemberEntity>,
    onExport: () -> Unit
) {
    val context = LocalContext.current
    val monthStr = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.reports_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("CSV Export", style = MaterialTheme.typography.labelSmall)
            }

            Button(
                onClick = {
                    val file = PdfGenerator.generateReportPdf(
                        context,
                        "Monthly Report - $monthStr",
                        monthTotal,
                        yearlyTotal,
                        expensesTotal,
                        memberCount,
                        defaulters
                    )
                    PdfGenerator.viewPdf(context, file)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("PDF Report", style = MaterialTheme.typography.labelSmall)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                MetricCard(stringResource(R.string.current_month_recorded), money(monthTotal), Modifier.fillMaxWidth())
            }
            item {
                MetricCard(stringResource(R.string.yearly_summary, money(yearlyTotal)), money(yearlyTotal), Modifier.fillMaxWidth())
            }
            item {
                MetricCard(stringResource(R.string.contributions_vs_expenses), "Collections: ${money(yearlyTotal)}\nExpenses: ${money(expensesTotal)}\nNet: ${money(yearlyTotal - expensesTotal)}", Modifier.fillMaxWidth())
            }
            item {
                MetricCard(stringResource(R.string.expected_month_formula), money(activeCount.toLong() * 10000L), Modifier.fillMaxWidth())
            }
            item {
                MetricCard(stringResource(R.string.outstanding_estimate), money((activeCount.toLong() * 10000L - monthTotal).coerceAtLeast(0L)), Modifier.fillMaxWidth())
            }

            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.defaulters_list), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        if (defaulters.isEmpty()) {
                            Text(stringResource(R.string.no_defaulters), color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text(stringResource(R.string.defaulters_count, defaulters.size), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            defaulters.take(10).forEach { m ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${m.fullName} (${m.memberNo})", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    if (m.phone.isNotBlank()) {
                                        IconButton(onClick = {
                                            ReminderManager.sendSmsReminder(context, m.phone, m.fullName, monthStr)
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
    onApprove: (ExpenseEntity) -> Unit
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
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
private fun AddMemberDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val requiredMsg = stringResource(R.string.error_member_fields_required)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_register_member)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(number, { number = it; error = "" }, label = { Text(stringResource(R.string.label_member_number)) }, singleLine = true)
                OutlinedTextField(name, { name = it; error = "" }, label = { Text(stringResource(R.string.label_full_name)) }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.label_phone_optional)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { if (number.isBlank() || name.isBlank()) error = requiredMsg else onSave(number, name, phone) }) { Text(stringResource(R.string.btn_save_member)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordPaymentDialog(
    members: List<MemberEntity>,
    onDismiss: () -> Unit,
    onSave: (MemberEntity, Long, Int, Int, String) -> Unit
) {
    var selectedMemberId by remember { mutableStateOf(members.firstOrNull()?.id.orEmpty()) }
    var amount by remember { mutableStateOf("10000") }
    var year by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) }
    var month by remember { mutableStateOf((Calendar.getInstance().get(Calendar.MONTH) + 1).toString()) }
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
