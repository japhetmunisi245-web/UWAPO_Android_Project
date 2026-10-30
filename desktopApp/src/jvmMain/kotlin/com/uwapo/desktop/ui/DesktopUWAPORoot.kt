package com.uwapo.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uwapo.desktop.data.DesktopCsvImportExport
import com.uwapo.desktop.data.DesktopDatabase
import com.uwapo.desktop.data.DuplicateMemberException
import com.uwapo.desktop.data.ExpenseEntity
import com.uwapo.desktop.data.MemberEntity
import com.uwapo.desktop.data.PaymentEntity
import com.uwapo.desktop.pdf.DesktopPdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class DesktopTab(val labelEn: String, val labelSw: String, val icon: ImageVector) {
    HOME("Home", "Nyumbani", Icons.Default.Home),
    MEMBERS("Members", "Wanachama", Icons.Default.People),
    FINANCE("Finance", "Fedha", Icons.Default.ReceiptLong),
    REPORTS("Reports", "Ripoti", Icons.Default.Assessment)
}

enum class ContributionPeriod(val titleEn: String, val titleSw: String, val monthRange: IntRange) {
    TERM_1("Term 1: January – June", "Term 1: Januari – Juni", 1..6),
    TERM_2("Term 2: July – December", "Term 2: Julai – Desemba", 7..12);

    fun getYear(batchYear: Int): Int = batchYear
}

private fun money(amount: Long): String =
    "TZS " + NumberFormat.getIntegerInstance(Locale("en", "TZ")).format(amount)

private fun openFileDialog(title: String): File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
    dialog.isVisible = true
    return if (dialog.file != null) File(dialog.directory, dialog.file) else null
}

private fun saveFileDialog(title: String, defaultName: String): File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.SAVE)
    dialog.file = defaultName
    dialog.isVisible = true
    return if (dialog.file != null) File(dialog.directory, dialog.file) else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopUWAPORoot(
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {}
) {
    val db = remember { DesktopDatabase() }

    val members by db.members.collectAsState()
    val payments by db.payments.collectAsState()
    val expenses by db.expenses.collectAsState()
    val batches by db.batches.collectAsState()

    var currentLanguage by rememberSaveable { mutableStateOf("en") }
    var selectedTab by remember { mutableStateOf(DesktopTab.HOME) }

    var showAddMember by remember { mutableStateOf(false) }
    var showPayment by remember { mutableStateOf(false) }
    var showExpense by remember { mutableStateOf(false) }
    var showMaintenanceDialog by remember { mutableStateOf(false) }

    var initialMemberForPayment by remember { mutableStateOf<MemberEntity?>(null) }
    var initialYearForPayment by remember { mutableStateOf<Int?>(null) }
    var initialMonthForPayment by remember { mutableStateOf<Int?>(null) }

    var selectedMemberForProfile by remember { mutableStateOf<MemberEntity?>(null) }
    var memberToDelete by remember { mutableStateOf<MemberEntity?>(null) }
    var paymentToDelete by remember { mutableStateOf<PaymentEntity?>(null) }
    var paymentToReverse by remember { mutableStateOf<PaymentEntity?>(null) }
    var pendingDuplicate by remember { mutableStateOf<Pair<PaymentEntity, Long>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val cal = remember { Calendar.getInstance() }
    val currentYear = cal.get(Calendar.YEAR)
    val currentMonth = cal.get(Calendar.MONTH) + 1

    val activeMembers = remember(members) { members.filter { it.status == "active" } }
    val activeCount = activeMembers.size

    val currentPaidMemberIds = remember(payments, currentYear, currentMonth) {
        payments.filter { it.periodYear == currentYear && it.periodMonth == currentMonth && !it.syncStatus.startsWith("reversed") }
            .map { it.memberId }.toSet()
    }
    val defaulters = remember(activeMembers, currentPaidMemberIds) {
        activeMembers.filter { it.id !in currentPaidMemberIds }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("UWAPO", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (currentLanguage == "sw") "Usimamizi wa Wanachama Na Fedha · Toleo la Kompyuta"
                            else "Membership & Financial Management · Desktop Edition",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (isDarkTheme) Icons.Default.Brightness7 else Icons.Default.Brightness4,
                            contentDescription = if (currentLanguage == "sw") "Badili Mandhari" else "Toggle Theme",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { showMaintenanceDialog = true }) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = if (currentLanguage == "sw") "Urekebishaji wa Data" else "Data Maintenance",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    OutlinedButton(
                        onClick = { currentLanguage = if (currentLanguage == "sw") "en" else "sw" },
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(if (currentLanguage == "sw") "EN" else "SW", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        },
        floatingActionButton = {
            when (selectedTab) {
                DesktopTab.MEMBERS -> FloatingActionButton(onClick = { showAddMember = true }) { Icon(Icons.Default.Add, contentDescription = "Add Member") }
                DesktopTab.FINANCE -> FloatingActionButton(onClick = { showExpense = true }) { Icon(Icons.Default.Add, contentDescription = "Record Expense") }
                else -> Unit
            }
        }
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            // Sidebar Navigation Rail
            NavigationRail(
                modifier = Modifier.fillMaxHeight().width(200.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Spacer(Modifier.height(16.dp))
                DesktopTab.entries.forEach { tab ->
                    val label = if (currentLanguage == "sw") tab.labelSw else tab.labelEn
                    NavigationRailItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = label) },
                        label = { Text(label, fontWeight = FontWeight.SemiBold) },
                        alwaysShowLabel = true,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                }
            }

            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Main Content Area
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                when (selectedTab) {
                    DesktopTab.HOME -> DesktopDashboardView(members, payments, expenses, activeCount, defaulters, currentLanguage)
                    DesktopTab.MEMBERS -> DesktopMembersView(
                        members = members,
                        payments = payments,
                        batches = batches,
                        db = db,
                        language = currentLanguage,
                        onSelectMember = { selectedMemberForProfile = it },
                        onRecordPaymentForMember = { member, yr, mo ->
                            initialMemberForPayment = member
                            initialYearForPayment = yr
                            initialMonthForPayment = mo
                            showPayment = true
                        },
                        onShowAddMember = { showAddMember = true },
                        onRequestDeleteMember = { memberToDelete = it }
                    )
                    DesktopTab.FINANCE -> DesktopFinanceView(expenses, db, currentLanguage)
                    DesktopTab.REPORTS -> DesktopReportsView(members, payments, expenses, currentLanguage)
                }
            }
        }
    }

    // Dialogs
    if (showAddMember) {
        DesktopAddMemberDialog(
            batches = batches,
            language = currentLanguage,
            onDismiss = { showAddMember = false },
            onSave = { no, name, phone, batchYear ->
                val cleanNo = no.trim()
                val id = UUID.nameUUIDFromBytes("UWAPO-MEMBER:$cleanNo".toByteArray(Charsets.UTF_8)).toString()
                try {
                    db.insertMember(MemberEntity(id, cleanNo, name.trim(), phone.trim(), batchYear = batchYear))
                    showAddMember = false
                    null
                } catch (e: DuplicateMemberException) {
                    e.message
                } catch (e: Exception) {
                    "Could not save the member: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        )
    }

    if (showPayment) {
        DesktopRecordPaymentDialog(
            members = members,
            initialMember = initialMemberForPayment,
            initialYear = initialYearForPayment,
            initialMonth = initialMonthForPayment,
            language = currentLanguage,
            onDismiss = {
                showPayment = false
                initialMemberForPayment = null
                initialYearForPayment = null
                initialMonthForPayment = null
            },
            onSave = { memberId, yr, mo, amount, method ->
                val receipt = "REC-${System.currentTimeMillis().toString().takeLast(6)}"
                val payment = PaymentEntity(UUID.randomUUID().toString(), memberId, amount, yr, mo, System.currentTimeMillis(), method, receipt)
                val existing = payments.filter { it.memberId == memberId && it.periodYear == yr && it.periodMonth == mo && !it.syncStatus.startsWith("reversed") }
                if (existing.isNotEmpty()) {
                    pendingDuplicate = payment to existing.sumOf { it.amountTzs }
                } else {
                    try {
                        db.insertPayment(payment)
                        showPayment = false
                        initialMemberForPayment = null
                        initialYearForPayment = null
                        initialMonthForPayment = null
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "The payment could not be saved."
                    }
                }
            }
        )
    }

    selectedMemberForProfile?.let { m ->
        val memberPayments = payments.filter { it.memberId == m.id }
        DesktopMemberProfileDialog(
            member = m,
            payments = memberPayments,
            language = currentLanguage,
            onDismiss = { selectedMemberForProfile = null },
            onUpdateProfile = { phone, status, notes ->
                db.updateMemberProfile(m.id, phone, status, notes)
                selectedMemberForProfile = null
            },
            onDeleteMember = {
                memberToDelete = m
                selectedMemberForProfile = null
            },
            onDeletePayment = { p -> paymentToDelete = p },
            onReversePayment = { p -> paymentToReverse = p }
        )
    }

    paymentToDelete?.let { p ->
        ConfirmDeleteDialog(
            title = if (currentLanguage == "sw") "Futa Malipo?" else "Delete Payment?",
            message = if (currentLanguage == "sw") "Unataka kufuta malipo ya ${money(p.amountTzs)} (Risiti #${p.receiptNo})?"
                      else "Permanently delete payment of ${money(p.amountTzs)} (Receipt #${p.receiptNo})?",
            onConfirm = {
                paymentToDelete = null
                db.deletePayment(p.id)
            },
            onDismiss = { paymentToDelete = null }
        )
    }

    paymentToReverse?.let { p ->
        DesktopReversePaymentDialog(
            payment = p,
            language = currentLanguage,
            onDismiss = { paymentToReverse = null },
            onConfirm = { reason ->
                paymentToReverse = null
                db.reversePayment(p.id, reason)
            }
        )
    }

    pendingDuplicate?.let { (payment, existingTotal) ->
        val who = members.find { it.id == payment.memberId }?.fullName ?: "This member"
        AlertDialog(
            onDismissRequest = { pendingDuplicate = null },
            title = { Text(if (currentLanguage == "sw") "Malipo Yamesharekodiwa" else "Payment already recorded") },
            text = {
                Text("$who already has ${money(existingTotal)} recorded for ${payment.periodMonth}/${payment.periodYear}. Record another ${money(payment.amountTzs)} for the same month?")
            },
            confirmButton = {
                Button(onClick = {
                    pendingDuplicate = null
                    try {
                        db.insertPayment(payment)
                        showPayment = false
                        initialMemberForPayment = null
                        initialYearForPayment = null
                        initialMonthForPayment = null
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "The payment could not be saved."
                    }
                }) { Text("Record anyway") }
            },
            dismissButton = { OutlinedButton(onClick = { pendingDuplicate = null }) { Text("Cancel") } }
        )
    }

    memberToDelete?.let { m ->
        val memberPayments = payments.filter { it.memberId == m.id }
        ConfirmDeleteDialog(
            title = if (currentLanguage == "sw") "Mfutie ${m.fullName}?" else "Delete ${m.fullName}?",
            message = if (currentLanguage == "sw") "Hatua hii itamfuta kabisa ${m.fullName} (Namba ${m.memberNo}) na kumbukumbu ${memberPayments.size} za malipo (${money(memberPayments.sumOf { it.amountTzs })})."
                      else "This permanently deletes ${m.fullName} (No. ${m.memberNo}) and ${memberPayments.size} payment record(s) totalling ${money(memberPayments.sumOf { it.amountTzs })}. A backup copy of all data is saved automatically first (${db.backupFolderPath}).",
            onConfirm = {
                memberToDelete = null
                try {
                    db.deleteMember(m.id)
                    if (selectedMemberForProfile?.id == m.id) selectedMemberForProfile = null
                } catch (e: Exception) {
                    errorMessage = "Could not delete the member: ${e.message ?: e.javaClass.simpleName}"
                }
            },
            onDismiss = { memberToDelete = null }
        )
    }

    if (showMaintenanceDialog) {
        DesktopMaintenanceDialog(
            db = db,
            language = currentLanguage,
            onDismiss = { showMaintenanceDialog = false }
        )
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Something went wrong") },
            text = { Text(msg) },
            confirmButton = { Button(onClick = { errorMessage = null }) { Text("OK") } }
        )
    }

    if (showExpense) {
        DesktopRecordExpenseDialog(
            language = currentLanguage,
            onDismiss = { showExpense = false },
            onSave = { cat, desc, amount ->
                val eId = UUID.randomUUID().toString()
                db.insertExpense(ExpenseEntity(eId, cat, desc, amount))
                showExpense = false
            }
        )
    }
}

@Composable
private fun DesktopDashboardView(
    members: List<MemberEntity>,
    payments: List<PaymentEntity>,
    expenses: List<ExpenseEntity>,
    activeCount: Int,
    defaulters: List<MemberEntity>,
    language: String
) {
    val totalCollected = remember(payments) { payments.filter { !it.syncStatus.startsWith("reversed") }.sumOf { it.amountTzs } }
    val totalExpenses = remember(expenses) { expenses.filter { it.status == "paid" || it.status == "approved" }.sumOf { it.amountTzs } }
    val netBalance = totalCollected - totalExpenses

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            if (language == "sw") "Muhtasari wa Mfumo" else "Dashboard Overview",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(if (language == "sw") "Wanachama Hai" else "Active Members", "$activeCount", Icons.Default.People, Modifier.weight(1f))
            MetricCard(if (language == "sw") "Jumla ya Makusanyo" else "Total Collected", money(totalCollected), Icons.Default.AccountBalanceWallet, Modifier.weight(1f))
            MetricCard(if (language == "sw") "Jumla ya Matumizi" else "Total Expenses", money(totalExpenses), Icons.Default.MoneyOff, Modifier.weight(1f))
            MetricCard(if (language == "sw") "Salio la Akiba" else "Net Cash Balance", money(netBalance), Icons.Default.Savings, Modifier.weight(1f))
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (language == "sw") "Wanachama Wasiolipa Mwezi Huu (${defaulters.size})" else "Unpaid Active Members This Month (${defaulters.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                HorizontalDivider()
                if (defaulters.isEmpty()) {
                    Text(if (language == "sw") "Wanachama wote hai wamelipa mwezi huu!" else "All active members have paid for this month!", color = MaterialTheme.colorScheme.primary)
                } else {
                    LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(defaulters, key = { it.id }) { m ->
                            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${m.fullName} (${m.memberNo})", fontWeight = FontWeight.SemiBold)
                                Text(m.phone.ifBlank { "No Phone" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DesktopMembersView(
    members: List<MemberEntity>,
    payments: List<PaymentEntity>,
    batches: List<Int>,
    db: DesktopDatabase,
    language: String,
    onSelectMember: (MemberEntity) -> Unit,
    onRecordPaymentForMember: (MemberEntity, Int, Int) -> Unit,
    onShowAddMember: () -> Unit,
    onRequestDeleteMember: (MemberEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    var importBusy by remember { mutableStateOf(false) }
    var pendingPlan by remember { mutableStateOf<DesktopCsvImportExport.ImportPlan?>(null) }
    var pendingFileName by remember { mutableStateOf("") }
    var importMessage by remember { mutableStateOf<String?>(null) }

    var mainTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Batches & Terms, 1 = All Members
    var selectedBatchYear by rememberSaveable { mutableIntStateOf(batches.firstOrNull() ?: 2025) }
    var selectedPeriod by remember { mutableStateOf<ContributionPeriod?>(null) }

    var showAddBatchDialog by remember { mutableStateOf(false) }
    var batchToDelete by remember { mutableStateOf<Int?>(null) }

    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TabRow(selectedTabIndex = mainTab, modifier = Modifier.fillMaxWidth()) {
            Tab(selected = mainTab == 0, onClick = { mainTab = 0 }, text = { Text(if (language == "sw") "Bachi & Terms" else "Batches & Terms", fontWeight = FontWeight.Bold) })
            Tab(selected = mainTab == 1, onClick = { mainTab = 1 }, text = { Text(if (language == "sw") "Orodha ya Wanachama Wote" else "All Members Directory", fontWeight = FontWeight.Bold) })
        }

        if (mainTab == 0) {
            if (selectedPeriod == null) {
                // Batch Year & Term Selection
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (language == "sw") "Chagua Mwaka wa Bachi na Kipindi" else "Select Batch Year & Contribution Period",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(onClick = { showAddBatchDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (language == "sw") "Ongeza Bachi" else "Add Batch Year")
                    }
                }

                LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    items(batches, key = { it }) { yr ->
                        InputChip(
                            selected = selectedBatchYear == yr,
                            onClick = { selectedBatchYear = yr },
                            label = { Text("Batch $yr", fontWeight = FontWeight.Bold) },
                            trailingIcon = {
                                IconButton(onClick = { batchToDelete = yr }, modifier = Modifier.size(18.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Batch", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ContributionPeriod.entries.forEach { period ->
                        val termPayments = payments.filter { !it.syncStatus.startsWith("reversed") && it.periodYear == selectedBatchYear && it.periodMonth in period.monthRange }
                        val collected = termPayments.sumOf { it.amountTzs }
                        val periodTitle = if (language == "sw") period.titleSw else period.titleEn

                        Card(
                            modifier = Modifier.weight(1f).clickable { selectedPeriod = period },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(periodTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Batch Year $selectedBatchYear", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                HorizontalDivider()
                                Text("${if (language == "sw") "Makusanyo" else "Total Collected"}: ${money(collected)}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // Period Details View
                val period = selectedPeriod!!
                val termPayments = payments.filter { !it.syncStatus.startsWith("reversed") && it.periodYear == selectedBatchYear && it.periodMonth in period.monthRange }
                val periodTitle = if (language == "sw") period.titleSw else period.titleEn

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedPeriod = null }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                    Spacer(Modifier.width(8.dp))
                    Text("Batch $selectedBatchYear — $periodTitle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    OutlinedButton(enabled = !importBusy, onClick = {
                        val file = openFileDialog("Import Batch File (.xls / .xlsx / .csv)")
                        if (file != null) {
                            importBusy = true
                            scope.launch {
                                try {
                                    val plan = withContext(Dispatchers.IO) { DesktopCsvImportExport.planFile(file, db, selectedBatchYear) }
                                    pendingFileName = file.name
                                    pendingPlan = plan
                                } catch (e: Exception) {
                                    importMessage = "Could not read the file: ${e.message ?: e.javaClass.simpleName}"
                                } finally {
                                    importBusy = false
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (language == "sw") "Ingiza Faili la Bachi" else "Import Batch File")
                    }
                }

                if (importBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(if (language == "sw") "Tafuta mwanachama..." else "Search member by name, number, phone...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val batchMembers = members.filter { (it.batchYear == selectedBatchYear || selectedBatchYear == 2025) && it.status == "active" && mMatchesSearch(it, search) }
                    items(batchMembers, key = { it.id }) { m ->
                        val mPayments = termPayments.filter { it.memberId == m.id }
                        val paid = mPayments.sumOf { it.amountTzs }
                        val balance = (60000L - paid).coerceAtLeast(0L)

                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${m.fullName} (${m.memberNo})", fontWeight = FontWeight.Bold)
                                    Text("${if (language == "sw") "Amelipa" else "Paid"}: ${money(paid)} · ${if (language == "sw") "Baki" else "Balance"}: ${money(balance)}", style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedButton(onClick = { onRecordPaymentForMember(m, selectedBatchYear, period.monthRange.first) }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (language == "sw") "Rekodi Malipo" else "Record Payment")
                                }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = { onSelectMember(m) }) { Text(if (language == "sw") "Wasifu" else "Profile") }
                            }
                        }
                    }
                }
            }
        } else {
            // All Members Directory
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${if (language == "sw") "Orodha ya Wanachama Wote" else "All Members Directory"} (${members.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = onShowAddMember) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (language == "sw") "Ongeza Mwanachama" else "Add Member")
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text(if (language == "sw") "Tafuta jina, namba au simu..." else "Search name, number, or phone...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )

            val filtered = members.filter { m ->
                (statusFilter == "all" || m.status.equals(statusFilter, true)) && mMatchesSearch(m, search)
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { m ->
                    Card(Modifier.fillMaxWidth().clickable { onSelectMember(m) }) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(m.fullName.take(1).uppercase(), Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.fullName, fontWeight = FontWeight.Bold)
                                Text("${m.memberNo} · Batch ${m.batchYear} · ${m.phone.ifBlank { "No Phone" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(m.status.uppercase(), style = MaterialTheme.typography.labelSmall)
                            IconButton(onClick = { onRequestDeleteMember(m) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddBatchDialog) {
        DesktopAddBatchDialog(
            language = language,
            onDismiss = { showAddBatchDialog = false },
            onAdd = { newYear ->
                db.insertBatch(newYear)
                selectedBatchYear = newYear
                showAddBatchDialog = false
            }
        )
    }

    batchToDelete?.let { yr ->
        ConfirmDeleteDialog(
            title = if (language == "sw") "Futa Bachi $yr Kabisa?" else "Delete batch $yr completely?",
            message = if (language == "sw") "Hatua hii itafuta kabisa Bachi $yr, wanachama wote wa Bachi $yr, na malipo yote yaliyorekodiwa ya mwaka $yr. Hatua hii hairejelewi."
                      else "This will completely delete batch $yr, removing all members assigned to batch $yr and all payment records dated in $yr. This action cannot be undone.",
            onConfirm = {
                batchToDelete = null
                db.deleteBatch(yr)
            },
            onDismiss = { batchToDelete = null }
        )
    }

    pendingPlan?.let { plan ->
        ImportPreviewDialog(
            fileName = pendingFileName,
            batchYear = selectedBatchYear,
            plan = plan,
            onConfirm = {
                pendingPlan = null
                importBusy = true
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { DesktopCsvImportExport.commit(plan, db) }
                        importMessage = "Import complete: ${result.membersAdded} member(s) and ${result.paymentsAdded} payment(s) added. " +
                            "A backup of your previous data was saved to ${db.backupFolderPath}."
                    } catch (e: Exception) {
                        importMessage = "Import failed and nothing was saved: ${e.message ?: e.javaClass.simpleName}"
                    } finally {
                        importBusy = false
                    }
                }
            },
            onDismiss = { pendingPlan = null }
        )
    }

    importMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { importMessage = null },
            title = { Text("Import") },
            text = { Text(msg) },
            confirmButton = { Button(onClick = { importMessage = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun DesktopAddBatchDialog(language: String, onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    var yrStr by remember { mutableStateOf("2026") }
    var err by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (language == "sw") "Ongeza Mwaka wa Bachi" else "Add Custom Batch Year") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(yrStr, { yrStr = it; err = "" }, label = { Text(if (language == "sw") "Mwaka" else "Batch Year") }, singleLine = true)
                if (err.isNotBlank()) Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                val y = yrStr.trim().toIntOrNull()
                if (y == null || y !in 2000..2100) err = "Enter a valid year (2000-2100)"
                else onAdd(y)
            }) { Text(if (language == "sw") "Hifadhi" else "Add Batch") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(if (language == "sw") "Ghairi" else "Cancel") } }
    )
}

@Composable
private fun ConfirmDeleteDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ImportPreviewDialog(
    fileName: String,
    batchYear: Int,
    plan: DesktopCsvImportExport.ImportPlan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var acknowledged by remember { mutableStateOf(false) }
    val needsReview = !plan.reconciles || plan.warnings.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import preview · $fileName → Batch $batchYear") },
        text = {
            Column(
                Modifier.widthIn(max = 640.dp).heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("New members to add: ${plan.newMembers.size}", fontWeight = FontWeight.Bold)
                Text("Payments in the file: ${plan.payments.size} (${money(plan.computedTotal)})")
                Text("Already in the database (will be skipped): ${plan.paymentsAlreadyInDb}")
                Text("New payments to add: ${plan.newPayments}", fontWeight = FontWeight.Bold)

                HorizontalDivider()

                val sheetTotal = plan.sheetTotal
                when {
                    sheetTotal == null -> Text(
                        "No total found in the file to check against.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    plan.reconciles -> Text(
                        "✓ Matches the file's own total (${money(sheetTotal)}).",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    else -> Text(
                        "⚠ The file says ${money(sheetTotal)} but this import adds up to ${money(plan.computedTotal)} " +
                            "(difference ${money(kotlin.math.abs(sheetTotal - plan.computedTotal))}).",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (plan.notes.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Name matches and renumbering (${plan.notes.size})", fontWeight = FontWeight.Bold)
                    plan.notes.take(12).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    if (plan.notes.size > 12) Text("…and ${plan.notes.size - 12} more", style = MaterialTheme.typography.bodySmall)
                }

                if (plan.warnings.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Please review", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    plan.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }

                if (needsReview) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                        Text("I have reviewed the points above and want to import anyway.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = !plan.isEmpty && (!needsReview || acknowledged), onClick = onConfirm) {
                Text(if (needsReview) "Import anyway" else "Import")
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun mMatchesSearch(m: MemberEntity, search: String): Boolean {
    if (search.isBlank()) return true
    return m.fullName.contains(search, true) || m.memberNo.contains(search, true) || m.phone.contains(search, true)
}

@Composable
private fun DesktopFinanceView(expenses: List<ExpenseEntity>, db: DesktopDatabase, language: String) {
    var expenseToDelete by remember { mutableStateOf<ExpenseEntity?>(null) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (language == "sw") "Matumizi ya Klabu" else "Club Expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(expenses, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.category, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(money(e.amountTzs), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { expenseToDelete = e }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text(e.description)
                        Text("Status: ${e.status.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (e.status == "pending_approval") {
                            TextButton(onClick = { db.updateExpenseStatus(e.id, "paid") }) { Text("Approve & Post") }
                        }
                    }
                }
            }
        }
    }

    expenseToDelete?.let { e ->
        ConfirmDeleteDialog(
            title = if (language == "sw") "Futa Matumizi Haya?" else "Delete this expense?",
            message = "${e.category} · ${money(e.amountTzs)} will be permanently deleted.",
            onConfirm = {
                expenseToDelete = null
                db.deleteExpense(e.id)
            },
            onDismiss = { expenseToDelete = null }
        )
    }
}

@Composable
private fun DesktopReportsView(
    members: List<MemberEntity>,
    payments: List<PaymentEntity>,
    expenses: List<ExpenseEntity>,
    language: String
) {
    var selectedReportType by rememberSaveable { mutableIntStateOf(2) } // Default = 2 (Term Report)
    var selectedYear by rememberSaveable { mutableIntStateOf(2025) }
    var selectedTerm by rememberSaveable { mutableIntStateOf(0) } // 0 = Term 1, 1 = Term 2

    val termTitle = if (selectedTerm == 0) "Term 1 (Jan - Jun)" else "Term 2 (Jul - Dec)"
    val termMonthRange = if (selectedTerm == 0) 1..6 else 7..12
    val termMonthLabels = if (selectedTerm == 0) listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun") else listOf("Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    val validPayments = remember(payments) { payments.filter { !it.syncStatus.startsWith("reversed") } }

    val termPayments = remember(validPayments, selectedYear, selectedTerm) {
        validPayments.filter { it.periodYear == selectedYear && it.periodMonth in termMonthRange }
    }

    val activeMembers = remember(members) { members.filter { it.status == "active" } }
    val activeCount = activeMembers.size

    val expectedTermRevenue = activeCount.toLong() * 60000L
    val collectedTermRevenue = termPayments.sumOf { it.amountTzs }
    val estimatedArrears = (expectedTermRevenue - collectedTermRevenue).coerceAtLeast(0L)

    val sortedMembers = remember(members) { members.sortedBy { it.fullName.lowercase(Locale.ROOT) } }

    val termMemberRows = remember(sortedMembers, termPayments, termMonthRange) {
        sortedMembers.map { m ->
            val mPayments = termPayments.filter { it.memberId == m.id }
            val monthlyAmounts = termMonthRange.map { mNum -> mPayments.filter { it.periodMonth == mNum }.sumOf { it.amountTzs } }
            val total = monthlyAmounts.sum()
            DesktopPdfGenerator.TermMemberRow(m.memberNo, m.fullName, monthlyAmounts, total)
        }
    }

    val grandTotalMonthly = remember(termMemberRows) {
        (0..5).map { mIdx -> termMemberRows.sumOf { it.monthlyAmounts.getOrElse(mIdx) { 0L } } }
    }

    val grandTotalTerm = remember(termMemberRows) { termMemberRows.sumOf { it.totalAmount } }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (language == "sw") "Ripoti za Fedha & Michango" else "Financial & Contribution Reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        TabRow(selectedTabIndex = selectedReportType) {
            Tab(selected = selectedReportType == 0, onClick = { selectedReportType = 0 }, text = { Text(if (language == "sw") "Ripoti ya Mwezi" else "Monthly Report", fontWeight = FontWeight.Bold) })
            Tab(selected = selectedReportType == 1, onClick = { selectedReportType = 1 }, text = { Text(if (language == "sw") "Ripoti ya Mwaka" else "Annual Report", fontWeight = FontWeight.Bold) })
            Tab(selected = selectedReportType == 2, onClick = { selectedReportType = 2 }, text = { Text(if (language == "sw") "Ripoti ya Muhula" else "Term Report", fontWeight = FontWeight.Bold) })
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (language == "sw") "Mwaka: " else "Year: ", fontWeight = FontWeight.Bold)
                listOf(2023, 2024, 2025, 2026, 2027).forEach { yr ->
                    FilterChip(selected = selectedYear == yr, onClick = { selectedYear = yr }, label = { Text(yr.toString()) })
                    Spacer(Modifier.width(4.dp))
                }
            }

            if (selectedReportType == 2) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (language == "sw") "Muhula: " else "Term: ", fontWeight = FontWeight.Bold)
                    FilterChip(selected = selectedTerm == 0, onClick = { selectedTerm = 0 }, label = { Text("Term 1 (Jan - Jun)") })
                    Spacer(Modifier.width(4.dp))
                    FilterChip(selected = selectedTerm == 1, onClick = { selectedTerm = 1 }, label = { Text("Term 2 (Jul - Dec)") })
                }
            }
        }

        // Summary Revenue & Arrears Cards
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(if (language == "sw") "Makadirio ya Mapato" else "Expected Revenue", money(expectedTermRevenue), Icons.Default.TrendingUp, Modifier.weight(1f))
            MetricCard(if (language == "sw") "Makusanyo ya Muhula" else "Term Collected", money(collectedTermRevenue), Icons.Default.AccountBalance, Modifier.weight(1f))
            MetricCard(if (language == "sw") "Arrears / Madeni" else "Estimated Arrears", money(estimatedArrears), Icons.Default.Warning, Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val file = saveFileDialog("Save CSV Export", "uwapo-export-${System.currentTimeMillis()}.csv")
                    if (file != null) {
                        val csvContent = DesktopCsvImportExport.export(members, payments)
                        file.writeText(csvContent, Charsets.UTF_8)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Export CSV")
            }

            Button(
                onClick = {
                    val file = DesktopPdfGenerator.generateTermReportPdf(
                        termTitle = termTitle,
                        year = selectedYear,
                        monthLabels = termMonthLabels,
                        memberRows = termMemberRows,
                        grandTotalMonthly = grandTotalMonthly,
                        grandTotalTerm = grandTotalTerm
                    )
                    DesktopPdfGenerator.viewPdf(file)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Generate A4 Landscape PDF")
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Member Contributions Grid ($termTitle)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("${sortedMembers.size} Members", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        HorizontalDivider()

                        termMemberRows.forEach { row ->
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${row.fullName} (${row.memberNo})", fontWeight = FontWeight.Bold)
                                        Text(money(row.totalAmount), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        termMonthLabels.forEachIndexed { mIdx, label ->
                                            val amt = row.monthlyAmounts.getOrElse(mIdx) { 0L }
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(if (amt > 0) money(amt).replace("TZS ", "") else "-", style = MaterialTheme.typography.labelSmall, fontWeight = if (amt > 0) FontWeight.Bold else FontWeight.Normal)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        // Grand Total Summary Box
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("GRAND TOTAL", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text(money(grandTotalTerm), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    termMonthLabels.forEachIndexed { mIdx, label ->
                                        val mSum = grandTotalMonthly.getOrElse(mIdx) { 0L }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Text(if (mSum > 0) money(mSum).replace("TZS ", "") else "0", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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

// Dialog Composable Definitions
@Composable
private fun DesktopAddMemberDialog(
    batches: List<Int>,
    language: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int) -> String?
) {
    var no by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedBatch by remember { mutableStateOf(batches.firstOrNull() ?: 2025) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (language == "sw") "Sajili Mwanachama Mpya" else "Register New Member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(no, { no = it; error = "" }, label = { Text(if (language == "sw") "Namba ya Uwanachama" else "Membership Number") }, singleLine = true)
                OutlinedTextField(name, { name = it; error = "" }, label = { Text(if (language == "sw") "Jina Buche" else "Full Name") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text(if (language == "sw") "Namba ya Simu" else "Phone Number") }, singleLine = true)
                Text(if (language == "sw") "Chagua Bachi:" else "Select Batch Year:", fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(batches) { b ->
                        FilterChip(selected = selectedBatch == b, onClick = { selectedBatch = b }, label = { Text("Batch $b") })
                    }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (no.isBlank() || name.isBlank()) error = "Membership number and full name are required."
                else onSave(no, name, phone, selectedBatch)?.let { error = it }
            }) { Text(if (language == "sw") "Hifadhi" else "Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(if (language == "sw") "Ghairi" else "Cancel") } }
    )
}

@Composable
private fun DesktopRecordPaymentDialog(
    members: List<MemberEntity>,
    initialMember: MemberEntity?,
    initialYear: Int?,
    initialMonth: Int?,
    language: String,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Long, String) -> Unit
) {
    var selectedMemberId by remember { mutableStateOf(initialMember?.id ?: members.firstOrNull()?.id ?: "") }
    var yrStr by remember { mutableStateOf((initialYear ?: 2025).toString()) }
    var moStr by remember { mutableStateOf((initialMonth ?: 1).toString()) }
    var amtStr by remember { mutableStateOf("10000") }
    var method by remember { mutableStateOf("cash") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (language == "sw") "Rekodi Mchango wa Mwanachama" else "Record Member Contribution") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (language == "sw") "Chagua Mwanachama:" else "Select Member:", fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(members) { m ->
                        FilterChip(selected = selectedMemberId == m.id, onClick = { selectedMemberId = m.id }, label = { Text(m.fullName) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(yrStr, { yrStr = it; error = "" }, label = { Text(if (language == "sw") "Mwaka" else "Year") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(moStr, { moStr = it; error = "" }, label = { Text(if (language == "sw") "Mwezi (1-12)" else "Month (1-12)") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(amtStr, { amtStr = it; error = "" }, label = { Text(if (language == "sw") "Kiasi (TZS)" else "Amount (TZS)") })
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                val yr = yrStr.trim().toIntOrNull()
                val mo = moStr.trim().toIntOrNull()
                val amt = amtStr.trim().replace(",", "").toLongOrNull()
                when {
                    selectedMemberId.isBlank() -> error = "Select a member."
                    yr == null || yr !in 2000..2100 -> error = "Enter a valid year (2000–2100)."
                    mo == null || mo !in 1..12 -> error = "Month must be a number from 1 to 12."
                    amt == null || amt <= 0L -> error = "Enter an amount greater than 0."
                    else -> onSave(selectedMemberId, yr, mo, amt, method)
                }
            }) { Text(if (language == "sw") "Hifadhi Malipo" else "Save Payment") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(if (language == "sw") "Ghairi" else "Cancel") } }
    )
}

@Composable
private fun DesktopRecordExpenseDialog(language: String, onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit) {
    var cat by remember { mutableStateOf("Stationery") }
    var desc by remember { mutableStateOf("") }
    var amtStr by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (language == "sw") "Rekodi Matumizi ya Klabu" else "Record Club Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(cat, { cat = it }, label = { Text(if (language == "sw") "Kipengele" else "Category") })
                OutlinedTextField(desc, { desc = it }, label = { Text(if (language == "sw") "Maelezo" else "Description") })
                OutlinedTextField(amtStr, { amtStr = it }, label = { Text(if (language == "sw") "Kiasi (TZS)" else "Amount (TZS)") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amtStr.toLongOrNull() ?: 0L
                if (cat.isNotBlank() && amt > 0) onSave(cat, desc, amt)
            }) { Text(if (language == "sw") "Hifadhi" else "Save Expense") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(if (language == "sw") "Ghairi" else "Cancel") } }
    )
}

@Composable
private fun DesktopMemberProfileDialog(
    member: MemberEntity,
    payments: List<PaymentEntity>,
    language: String,
    onDismiss: () -> Unit,
    onUpdateProfile: (String, String, String) -> Unit,
    onDeleteMember: () -> Unit,
    onDeletePayment: (PaymentEntity) -> Unit,
    onReversePayment: (PaymentEntity) -> Unit
) {
    var phone by remember { mutableStateOf(member.phone) }
    var status by remember { mutableStateOf(member.status) }
    var notes by remember { mutableStateOf(member.notes) }

    val validPayments = remember(payments) { payments.filter { !it.syncStatus.startsWith("reversed") } }
    val totalPaid = validPayments.sumOf { it.amountTzs }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${member.fullName} (${member.memberNo})") },
        text = {
            Column(Modifier.widthIn(max = 600.dp).heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${if (language == "sw") "Jumla ya Malipo" else "Total Paid"}: ${money(totalPaid)} (${validPayments.size} ${if (language == "sw") "rekodi" else "records"})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Batch Year: ${member.batchYear}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(phone, { phone = it }, label = { Text(if (language == "sw") "Simu" else "Phone Number") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(notes, { notes = it }, label = { Text(if (language == "sw") "Maelezo" else "Notes") }, modifier = Modifier.weight(1f))
                }

                Text(if (language == "sw") "Historia ya Malipo" else "Payment History", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                if (payments.isEmpty()) {
                    Text(if (language == "sw") "Hakuna malipo bado." else "No payments recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(payments, key = { it.id }) { p ->
                            val isReversed = p.syncStatus.startsWith("reversed")
                            val paidDateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(p.paidAt))

                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (isReversed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant)) {
                                Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${p.periodMonth.toString().padStart(2, '0')}/${p.periodYear} · Receipt #${p.receiptNo}", fontWeight = FontWeight.SemiBold)
                                        Text("$paidDateStr · Method: ${p.method.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (isReversed) {
                                            Text("STATUS: REVERSED (${p.syncStatus})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(money(p.amountTzs), fontWeight = FontWeight.Bold, color = if (isReversed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    if (!isReversed) {
                                        IconButton(onClick = { onReversePayment(p) }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Undo, contentDescription = "Reverse", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    IconButton(onClick = { onDeletePayment(p) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val file = DesktopPdfGenerator.generateStatementPdf(member, payments)
                    DesktopPdfGenerator.viewPdf(file)
                }) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Statement PDF")
                }
                Button(onClick = { onUpdateProfile(phone, status, notes) }) { Text(if (language == "sw") "Hifadhi Wasifu" else "Save Profile") }
            }
        },
        dismissButton = {
            Row {
                Button(onClick = onDeleteMember, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(if (language == "sw") "Futa Mwanachama" else "Delete Member") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onDismiss) { Text(if (language == "sw") "Funga" else "Close") }
            }
        }
    )
}

@Composable
private fun DesktopReversePaymentDialog(
    payment: PaymentEntity,
    language: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (language == "sw") "Geuza Malipo (Risiti #${payment.receiptNo})" else "Reverse Payment (Receipt #${payment.receiptNo})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (language == "sw") "Kiasi: ${money(payment.amountTzs)}" else "Amount: ${money(payment.amountTzs)}", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    reason,
                    { reason = it },
                    label = { Text(if (language == "sw") "Sababu ya kugeuza malipo" else "Reason for reversal (e.g. wrong entry)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(reason) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text(if (language == "sw") "Thibitisha Kugeuza" else "Confirm Reversal")
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(if (language == "sw") "Ghairi" else "Cancel") } }
    )
}

@Composable
private fun DesktopMaintenanceDialog(
    db: DesktopDatabase,
    language: String,
    onDismiss: () -> Unit
) {
    var statusMsg by remember { mutableStateOf("") }
    val backups = remember(statusMsg) { db.getBackupFiles() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (language == "sw") "Urekebishaji wa Data na Hifadhi" else "Data Maintenance & Backups") },
        text = {
            Column(Modifier.widthIn(max = 600.dp).heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (language == "sw") "Takwimu za Kanuni:" else "Database Statistics:", fontWeight = FontWeight.Bold)
                        Text("• ${if (language == "sw") "Ukubwa wa Faili:" else "DB File Size:"} ${db.getDbFileSize() / 1024} KB")
                        Text("• ${if (language == "sw") "Faili la Hifadhi:" else "Backup Folder:"} ${db.backupFolderPath}")
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val f = db.backup("manual")
                            statusMsg = if (f != null) "Backup created: ${f.name}" else "Backup skipped or failed."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (language == "sw") "Hifadhi Sasa" else "Instant Backup")
                    }

                    OutlinedButton(
                        onClick = {
                            val ok = db.vacuumDatabase()
                            statusMsg = if (ok) "Database VACUUM completed." else "VACUUM failed."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("VACUUM DB")
                    }

                    OutlinedButton(
                        onClick = {
                            val res = db.checkIntegrity()
                            statusMsg = "Integrity check: $res"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Integrity Check")
                    }
                }

                if (statusMsg.isNotBlank()) {
                    Text(statusMsg, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }

                HorizontalDivider()

                Text(if (language == "sw") "Kumbukumbu za Hifadhi zilizopita (${backups.size})" else "Available Backup Snapshots (${backups.size})", fontWeight = FontWeight.Bold)

                if (backups.isEmpty()) {
                    Text(if (language == "sw") "Hakuna faili za hifadhi bado." else "No backup snapshots found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    backups.take(10).forEach { file ->
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(file.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                    Text("$dateStr · ${file.length() / 1024} KB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedButton(onClick = {
                                    val ok = db.restoreBackup(file)
                                    statusMsg = if (ok) "Database restored from ${file.name}!" else "Failed to restore backup."
                                }) {
                                    Text(if (language == "sw") "Rejesha" else "Restore")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(if (language == "sw") "Funga" else "Close") }
        }
    )
}
