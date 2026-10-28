package com.uwapo.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "UWAPO - Financial Management Desktop",
        state = WindowState(size = DpSize(1200.dp, 800.dp))
    ) {
        UWAPODesktopTheme {
            UWAPODesktopMainScreen()
        }
    }
}

enum class DesktopNavTarget(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Home),
    MEMBERS("Members", Icons.Default.People),
    PAYMENTS("Payments", Icons.Default.Payments),
    EXPENSES("Expenses", Icons.Default.ReceiptLong),
    REPORTS("Reports", Icons.Default.Assessment),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun UWAPODesktopTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF006C4C),
        onPrimary = androidx.compose.ui.graphics.Color.White,
        primaryContainer = androidx.compose.ui.graphics.Color(0xFF89F8C7),
        onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF002114),
        secondary = androidx.compose.ui.graphics.Color(0xFF4D6356),
        background = androidx.compose.ui.graphics.Color(0xFF191C1A),
        surface = androidx.compose.ui.graphics.Color(0xFF202421),
        onSurface = androidx.compose.ui.graphics.Color(0xFFE1E3DF)
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun UWAPODesktopMainScreen() {
    var selectedScreen by remember { mutableStateOf(DesktopNavTarget.DASHBOARD) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Navigation Rail / Sidebar
        NavigationRail(
            modifier = Modifier.fillMaxHeight().width(220.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            header = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "UWAPO",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primaryContainer
                    )
                    Text(
                        text = "Desktop Edition",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            DesktopNavTarget.entries.forEach { target ->
                NavigationRailItem(
                    selected = selectedScreen == target,
                    onClick = { selectedScreen = target },
                    icon = { Icon(target.icon, contentDescription = target.title) },
                    label = { Text(target.title) },
                    alwaysShowLabel = true,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                )
            }
        }

        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // Content Area
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)
        ) {
            when (selectedScreen) {
                DesktopNavTarget.DASHBOARD -> DesktopDashboardView()
                DesktopNavTarget.MEMBERS -> DesktopMembersView()
                DesktopNavTarget.PAYMENTS -> DesktopPaymentsView()
                DesktopNavTarget.EXPENSES -> DesktopExpensesView()
                DesktopNavTarget.REPORTS -> DesktopReportsView()
                DesktopNavTarget.SETTINGS -> DesktopSettingsView()
            }
        }
    }
}

@Composable
fun DesktopDashboardView() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Financial Dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCard("Total Collected", "TZS 12,450,000", Icons.Default.Payments, Modifier.weight(1f))
            MetricCard("Active Members", "48 Members", Icons.Default.People, Modifier.weight(1f))
            MetricCard("Approved Expenses", "TZS 3,200,000", Icons.Default.ReceiptLong, Modifier.weight(1f))
            MetricCard("Net Balance", "TZS 9,250,000", Icons.Default.AccountBalance, Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Recent System Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(
                        "Member payment recorded: TZS 50,000 (Member #012)",
                        "New expense submitted: Stationeries (TZS 15,000)",
                        "Monthly contribution statement generated for October 2025",
                        "Database local backup completed successfully"
                    )) { activity ->
                        ListItem(
                            headlineContent = { Text(activity) },
                            leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primaryContainer)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DesktopMembersView() {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Member Management", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Button(onClick = {}) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Member")
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Member records and directory will be displayed here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DesktopPaymentsView() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Contribution Payments", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Payment ledger and recording interface.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DesktopExpensesView() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Expense Management", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Expenses and approvals manager.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DesktopReportsView() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Reports & Export", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("PDF and CSV export generation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DesktopSettingsView() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Desktop Application Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Settings & Configuration", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
