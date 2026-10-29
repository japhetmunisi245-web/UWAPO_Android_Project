package com.uwapo.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwapo.desktop.data.DesktopDatabase
import com.uwapo.desktop.data.ExpenseEntity
import com.uwapo.desktop.data.MemberEntity
import com.uwapo.desktop.data.PaymentEntity
import com.uwapo.desktop.pdf.DesktopPdfGenerator
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

private fun openFileDialog(title: String): java.io.File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
    dialog.isVisible = true
    return if (dialog.file != null) java.io.File(dialog.directory, dialog.file) else null
}

private fun saveFileDialog(title: String, defaultName: String): java.io.File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.SAVE)
    dialog.file = defaultName
    dialog.isVisible = true
    return if (dialog.file != null) java.io.File(dialog.directory, dialog.file) else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopUWAPORoot() {
    val db = remember { DesktopDatabase() }

    val members by db.members.collectAsState()
    val payments by db.payments.collectAsState()
    val expenses by db.expenses.collectAsState()

    var currentLanguage by rememberSaveable { mutableStateOf("en") }
    var selectedTab by remember { mutableStateOf(DesktopTab.HOME) }

    var showAddMember by remember { mutableStateOf(false) }
    var showPayment by remember { mutableStateOf(false) }
    var showExpense by remember { mutableStateOf(false) }

    var initialMemberForPayment by remember { mutableStateOf<MemberEntity?>(null) }
    var initialYearForPayment by remember { mutableStateOf<Int?>(null) }
    var initialMonthForPayment by remember { mutableStateOf<Int?>(null) }

    var selectedMemberForProfile by remember { mutableStateOf<MemberEntity?>(null) }

    val cal = remember { Calendar.getInstance() }
    val currentYear = cal.get(Calendar.YEAR)
    val currentMonth = cal.get(Calendar.MONTH) + 1

    val activeMembers = remember(members) { members.filter { it.status == "active" } }
    val activeCount = activeMembers.size

    val currentPaidMemberIds = remember(payments, currentYear, currentMonth) {
        payments.filter { it.periodYear == currentYear && it.periodMonth == currentMonth && it.syncStatus != "reversed" }
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
                        Text("Membership Management · Desktop Edition", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                },
                actions = {
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
                        db = db,
                        onSelectMember = { selectedMemberForProfile = it },
                        onRecordPaymentForMember = { member, yr, mo ->
                            initialMemberForPayment = member
                            initialYearForPayment = yr
                            initialMonthForPayment = mo
                            showPayment = true
                        },
                        onShowAddMember = { showAddMember = true }
                    )
                    DesktopTab.FINANCE -> DesktopFinanceView(expenses, db)
                    DesktopTab.REPORTS -> DesktopReportsView(members, payments, expenses, currentLanguage)
                }
            }
        }
    }

    // Dialogs
    if (showAddMember) {
        DesktopAddMemberDialog(
            onDismiss = { showAddMember = false },
            onSave = { no, name, phone ->
                val id = UUID.nameUUIDFromBytes("UWAPO-MEMBER:$no".toByteArray(Charsets.UTF_8)).toString()
                db.insertMember(MemberEntity(id, no, name, phone))
                showAddMember = false
            }
        )
    }

    if (showPayment) {
        DesktopRecordPaymentDialog(
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
            onSave = { memberId, yr, mo, amount, method ->
                val member = members.find { it.id == memberId }
                val receipt = "REC-${System.currentTimeMillis().toString().takeLast(6)}"
                val pId = UUID.randomUUID().toString()
                db.insertPayment(PaymentEntity(pId, memberId, amount, yr, mo, System.currentTimeMillis(), method, receipt))
                showPayment = false
                initialMemberForPayment = null
                initialYearForPayment = null
                initialMonthForPayment = null
            }
        )
    }

    if (showExpense) {
        DesktopRecordExpenseDialog(
            onDismiss = { showExpense = false },
            onSave = { cat, desc, amount ->
                val eId = UUID.randomUUID().toString()
                db.insertExpense(ExpenseEntity(eId, cat, desc, amount))
                showExpense = false
            }
        )
    }

    selectedMemberForProfile?.let { m ->
        DesktopMemberProfileDialog(
            member = m,
            payments = payments.filter { it.memberId == m.id },
            onDismiss = { selectedMemberForProfile = null },
            onUpdateProfile = { phone, status, notes ->
                db.updateMemberProfile(m.id, phone, status, notes)
                selectedMemberForProfile = null
            },
            onDeleteMember = {
                db.deleteMember(m.id)
                selectedMemberForProfile = null
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
    val cal = Calendar.getInstance()
    val curYear = cal.get(Calendar.YEAR)
    val curMonth = cal.get(Calendar.MONTH) + 1

    val monthTotal = payments.filter { it.syncStatus != "reversed" && it.periodYear == curYear && it.periodMonth == curMonth }.sumOf { it.amountTzs }
    val lifetimeTotal = payments.filter { it.syncStatus != "reversed" }.sumOf { it.amountTzs }
    val expensesTotal = expenses.filter { it.status == "paid" || it.status == "approved" }.sumOf { it.amountTzs }
    val expectedMonth = activeCount.toLong() * 10000L
    val outstandingEst = (expectedMonth - monthTotal).coerceAtLeast(0L)

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Financial Overview · $curYear", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Active Members", "$activeCount Members", Icons.Default.People, Modifier.weight(1f))
            MetricCard("Collected This Month", money(monthTotal), Icons.Default.Payments, Modifier.weight(1f))
            MetricCard("Lifetime Collections", money(lifetimeTotal), Icons.Default.AccountBalance, Modifier.weight(1f))
            MetricCard("Approved Expenses", money(expensesTotal), Icons.Default.ReceiptLong, Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Expected This Month", money(expectedMonth), Icons.Default.Assessment, Modifier.weight(1f))
            MetricCard("Outstanding Estimate", money(outstandingEst), Icons.Default.Warning, Modifier.weight(1f))
        }

        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Members Pending Contribution This Month (${defaulters.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (defaulters.isEmpty()) {
                    Text("All active members have contributed for this month!", color = MaterialTheme.colorScheme.primary)
                } else {
                    defaulters.take(8).forEach { m ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${m.fullName} (${m.memberNo})", fontWeight = FontWeight.SemiBold)
                            Text("Phone: ${m.phone.ifBlank { "N/A" }}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, contentDescription = title, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DesktopMembersView(
    members: List<MemberEntity>,
    payments: List<PaymentEntity>,
    db: DesktopDatabase,
    onSelectMember: (MemberEntity) -> Unit,
    onRecordPaymentForMember: (MemberEntity, Int, Int) -> Unit,
    onShowAddMember: () -> Unit
) {
    var mainTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Batches & Terms, 1 = All Members
    var selectedBatchYear by rememberSaveable { mutableIntStateOf(2025) }
    var selectedPeriod by remember { mutableStateOf<ContributionPeriod?>(null) }

    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }

    val batchYears = remember(payments) {
        (listOf(2023, 2024, 2025, 2026, 2027) + payments.map { it.periodYear }).distinct().sortedDescending()
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TabRow(selectedTabIndex = mainTab, modifier = Modifier.fillMaxWidth()) {
            Tab(selected = mainTab == 0, onClick = { mainTab = 0 }, text = { Text("Batches & Terms", fontWeight = FontWeight.Bold) })
            Tab(selected = mainTab == 1, onClick = { mainTab = 1 }, text = { Text("All Members Directory", fontWeight = FontWeight.Bold) })
        }

        if (mainTab == 0) {
            if (selectedPeriod == null) {
                // Batch Year & Term Selection
                Text("Select Batch Year & Contribution Period", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    batchYears.forEach { yr ->
                        FilterChip(
                            selected = selectedBatchYear == yr,
                            onClick = { selectedBatchYear = yr },
                            label = { Text("Batch $yr", fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ContributionPeriod.entries.forEach { period ->
                        val termPayments = payments.filter { it.syncStatus != "reversed" && it.periodYear == selectedBatchYear && it.periodMonth in period.monthRange }
                        val collected = termPayments.sumOf { it.amountTzs }

                        Card(
                            modifier = Modifier.weight(1f).clickable { selectedPeriod = period },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(period.titleEn, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Batch Year $selectedBatchYear", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                HorizontalDivider()
                                Text("Total Collected: ${money(collected)}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // Period Details View
                val period = selectedPeriod!!
                val termPayments = payments.filter { it.syncStatus != "reversed" && it.periodYear == selectedBatchYear && it.periodMonth in period.monthRange }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedPeriod = null }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                    Spacer(Modifier.width(8.dp))
                    Text("Batch $selectedBatchYear — ${period.titleEn}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        val file = openFileDialog("Import Batch File (.xls / .xlsx / .csv)")
                        if (file != null) {
                            com.uwapo.desktop.data.DesktopCsvImportExport.importFile(file, db, selectedBatchYear)
                        }
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Import Batch File")
                    }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search member by name, number, phone...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val activeM = members.filter { it.status == "active" && (mMatchesSearch(it, search)) }
                    items(activeM, key = { it.id }) { m ->
                        val mPayments = termPayments.filter { it.memberId == m.id }
                        val paid = mPayments.sumOf { it.amountTzs }
                        val balance = (60000L - paid).coerceAtLeast(0L)

                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${m.fullName} (${m.memberNo})", fontWeight = FontWeight.Bold)
                                    Text("Paid: ${money(paid)} · Balance: ${money(balance)}", style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedButton(onClick = { onRecordPaymentForMember(m, selectedBatchYear, period.monthRange.first) }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Record Payment")
                                }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = { onSelectMember(m) }) { Text("Profile") }
                            }
                        }
                    }
                }
            }
        } else {
            // All Members Directory
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("All Members Directory (${members.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = onShowAddMember) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Member")
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search name, number, or phone...") },
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
                                Text("${m.memberNo} · ${m.phone.ifBlank { "No Phone" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(m.status.uppercase(), style = MaterialTheme.typography.labelSmall)
                            IconButton(onClick = { db.deleteMember(m.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun mMatchesSearch(m: MemberEntity, search: String): Boolean {
    if (search.isBlank()) return true
    return m.fullName.contains(search, true) || m.memberNo.contains(search, true) || m.phone.contains(search, true)
}

@Composable
private fun DesktopFinanceView(expenses: List<ExpenseEntity>, db: DesktopDatabase) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Club Expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(expenses, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.category, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(money(e.amountTzs), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { db.deleteExpense(e.id) }) {
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

    val termPayments = remember(payments, selectedYear, selectedTerm) {
        payments.filter { it.syncStatus != "reversed" && it.periodYear == selectedYear && it.periodMonth in termMonthRange }
    }

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
        Text("Financial & Contribution Reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        TabRow(selectedTabIndex = selectedReportType) {
            Tab(selected = selectedReportType == 0, onClick = { selectedReportType = 0 }, text = { Text("Monthly Report", fontWeight = FontWeight.Bold) })
            Tab(selected = selectedReportType == 1, onClick = { selectedReportType = 1 }, text = { Text("Annual Report", fontWeight = FontWeight.Bold) })
            Tab(selected = selectedReportType == 2, onClick = { selectedReportType = 2 }, text = { Text("Term Report", fontWeight = FontWeight.Bold) })
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Year: ", fontWeight = FontWeight.Bold)
                listOf(2023, 2024, 2025, 2026, 2027).forEach { yr ->
                    FilterChip(selected = selectedYear == yr, onClick = { selectedYear = yr }, label = { Text(yr.toString()) })
                    Spacer(Modifier.width(4.dp))
                }
            }

            if (selectedReportType == 2) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Term: ", fontWeight = FontWeight.Bold)
                    FilterChip(selected = selectedTerm == 0, onClick = { selectedTerm = 0 }, label = { Text("Term 1 (Jan - Jun)") })
                    Spacer(Modifier.width(4.dp))
                    FilterChip(selected = selectedTerm == 1, onClick = { selectedTerm = 1 }, label = { Text("Term 2 (Jul - Dec)") })
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val file = saveFileDialog("Save CSV Export", "uwapo-export-${System.currentTimeMillis()}.csv")
                    if (file != null) {
                        val csvContent = com.uwapo.desktop.data.DesktopCsvImportExport.export(members, payments)
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
                MetricCard("Term Contributions Grand Total ($termTitle $selectedYear)", money(grandTotalTerm), Icons.Default.AccountBalance, Modifier.fillMaxWidth())
            }

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
private fun DesktopAddMemberDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var no by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register New Member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(no, { no = it }, label = { Text("Membership Number") }, singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text("Full Name") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone Number") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { if (no.isNotBlank() && name.isNotBlank()) onSave(no, name, phone) }) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DesktopRecordPaymentDialog(
    members: List<MemberEntity>,
    initialMember: MemberEntity?,
    initialYear: Int?,
    initialMonth: Int?,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Long, String) -> Unit
) {
    var selectedMemberId by remember { mutableStateOf(initialMember?.id ?: members.firstOrNull()?.id ?: "") }
    var yrStr by remember { mutableStateOf((initialYear ?: 2025).toString()) }
    var moStr by remember { mutableStateOf((initialMonth ?: 1).toString()) }
    var amtStr by remember { mutableStateOf("10000") }
    var method by remember { mutableStateOf("cash") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Member Contribution") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select Member:", fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(members) { m ->
                        FilterChip(selected = selectedMemberId == m.id, onClick = { selectedMemberId = m.id }, label = { Text(m.fullName) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(yrStr, { yrStr = it }, label = { Text("Year") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(moStr, { moStr = it }, label = { Text("Month (1-12)") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(amtStr, { amtStr = it }, label = { Text("Amount (TZS)") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val yr = yrStr.toIntOrNull() ?: 2025
                val mo = moStr.toIntOrNull() ?: 1
                val amt = amtStr.toLongOrNull() ?: 10000L
                if (selectedMemberId.isNotBlank()) onSave(selectedMemberId, yr, mo, amt, method)
            }) { Text("Save Payment") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DesktopRecordExpenseDialog(onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit) {
    var cat by remember { mutableStateOf("Stationery") }
    var desc by remember { mutableStateOf("") }
    var amtStr by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Club Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(cat, { cat = it }, label = { Text("Category") })
                OutlinedTextField(desc, { desc = it }, label = { Text("Description") })
                OutlinedTextField(amtStr, { amtStr = it }, label = { Text("Amount (TZS)") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amtStr.toLongOrNull() ?: 0L
                if (cat.isNotBlank() && amt > 0) onSave(cat, desc, amt)
            }) { Text("Save Expense") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DesktopMemberProfileDialog(
    member: MemberEntity,
    payments: List<PaymentEntity>,
    onDismiss: () -> Unit,
    onUpdateProfile: (String, String, String) -> Unit,
    onDeleteMember: () -> Unit
) {
    var phone by remember { mutableStateOf(member.phone) }
    var status by remember { mutableStateOf(member.status) }
    var notes by remember { mutableStateOf(member.notes) }

    val totalPaid = payments.filter { it.syncStatus != "reversed" }.sumOf { it.amountTzs }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${member.fullName} (${member.memberNo})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Total Paid: ${money(totalPaid)} (${payments.size} records)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone Number") })
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
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
                Button(onClick = { onUpdateProfile(phone, status, notes) }) { Text("Save Profile") }
            }
        },
        dismissButton = {
            Row {
                Button(onClick = onDeleteMember, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
