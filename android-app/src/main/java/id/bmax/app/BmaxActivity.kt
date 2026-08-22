package id.bmax.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.smartbiller.app.data.CustomerSeed
import id.smartbiller.app.ui.theme.SmartBillerTheme

private val BmaxBlue = Color(0xFF0A4B8F)
private val BmaxDeep = Color(0xFF04172D)
private val BmaxCyan = Color(0xFF28C8FF)
private val BmaxYellow = Color(0xFFFFD21F)
private val BmaxGreen = Color(0xFF55E27B)
private val BmaxRed = Color(0xFFFF6B6B)
private val Glass = Color.White.copy(alpha = 0.11f)

private enum class Role { ADMIN, SUPERVISOR, BILLER }
private enum class Tab { DASHBOARD, CUSTOMER, BILLING, MAP, PROFILE }

class BmaxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmartBillerTheme { BmaxApp() } }
    }
}

@Composable
private fun BmaxApp() {
    var role by rememberSaveable { mutableStateOf(Role.BILLER) }
    var tab by rememberSaveable { mutableStateOf(Tab.DASHBOARD) }
    var query by rememberSaveable { mutableStateOf("") }

    val visibleCustomers = CustomerSeed.all.filter { customer ->
        query.isBlank() ||
            customer.customerNo.contains(query, ignoreCase = true) ||
            (customer.name ?: "").contains(query, ignoreCase = true) ||
            customer.rbm.contains(query, ignoreCase = true)
    }

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = BmaxDeep.copy(alpha = .96f)) {
                    listOf(
                        Tab.DASHBOARD to "Dashboard",
                        Tab.CUSTOMER to "Customer",
                        Tab.BILLING to "Billing",
                        Tab.MAP to "Mapping",
                        Tab.PROFILE to "Profile",
                    ).forEach { (destination, label) ->
                        NavigationBarItem(
                            selected = tab == destination,
                            onClick = { tab = destination },
                            icon = { Icon(navIcon(destination), label) },
                            label = { Text(label) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                BmaxHeader(role)
                when (tab) {
                    Tab.DASHBOARD -> DashboardPage(role) { role = it }
                    Tab.CUSTOMER -> CustomerPage(query, { query = it }, visibleCustomers)
                    Tab.BILLING -> BillingPage(role)
                    Tab.MAP -> MappingPage(visibleCustomers.size)
                    Tab.PROFILE -> ProfilePage(role)
                }
            }
        }
    }
}

@Composable
private fun GlassBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BmaxBlue, BmaxDeep, Color(0xFF020A14)),
                ),
            ),
    ) { content() }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Glass),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

@Composable
private fun BmaxHeader(role: Role) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Bolt, null, tint = BmaxYellow, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Bmax", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("Electricity Payment Monitoring • ${role.name}", color = Color.White.copy(alpha = .62f))
        }
        AssistChip(onClick = {}, label = { Text("ULP Sumedang") })
    }
}

@Composable
private fun DashboardPage(currentRole: Role, onRoleChange: (Role) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Monitoring Operasional Biller PLN", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Wilayah → ULP → SPV → Biller → RBM A-E → Pelanggan → Tagihan", color = Color.White.copy(alpha = .65f))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("Pelanggan", CustomerSeed.masterRecordCount.toString(), BmaxCyan)
                    Metric("Lunas", "86", BmaxGreen)
                    Metric("Belum", "32", BmaxYellow)
                    Metric("Lewat", "10", BmaxRed)
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Role Preview", color = Color.White, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Role.values().forEach { role ->
                        AssistChip(
                            onClick = { onRoleChange(role) },
                            label = { Text(role.name) },
                        )
                    }
                }
                Text("Dashboard aktif: ${currentRole.name}", color = BmaxCyan, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Klasifikasi Tagihan", color = Color.White, fontWeight = FontWeight.Bold)
                Classification("Preventif", "Tagihan 1–20", 28, BmaxCyan)
                Classification("Korektif", "Tagihan 21–akhir bulan, belum lunas", 14, BmaxRed)
                Classification("Irisan", "Periode sebelumnya masih outstanding", 9, BmaxYellow)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("RBM Biller", color = Color.White, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("A", "B", "C", "D", "E").forEach { AssistChip(onClick = {}, label = { Text("RBM $it") }) }
                }
                Text("Identitas RBM menggunakan biller_id + rbm_code", color = Color.White.copy(alpha = .62f), modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, accent: Color) {
    GlassCard(Modifier.width(130.dp)) {
        Text(label, color = Color.White.copy(alpha = .62f))
        Text(value, color = accent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun Classification(title: String, subtitle: String, value: Int, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall)
        }
        Text(value.toString(), color = accent, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CustomerPage(query: String, onQueryChange: (String) -> Unit, customers: List<id.smartbiller.app.data.Customer>) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Cari IDPEL / Nama / RBM") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
        )
        Spacer(Modifier.height(10.dp))
        Text("${customers.size} data review • master 1.328 record", color = Color.White.copy(alpha = .60f))
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(customers) { customer ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(customer.name ?: "Pelanggan", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(customer.customerNo, color = Color.White.copy(alpha = .62f))
                            Text("${customer.tarif ?: "-"} • ${customer.daya ?: 0} VA • RBM ${customer.rbm}", color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = {}) { Text("Inquiry") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BillingPage(role: Role) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Billing Monitoring", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Role scope: ${role.name}", color = Color.White.copy(alpha = .60f))
                Spacer(Modifier.height(10.dp))
                listOf(
                    "AGU26 • PREVENTIF • UNPAID" to "Rp 412.800",
                    "AGU26 • KOREKTIF • OVERDUE" to "Rp 389.500",
                    "JUL26 • IRISAN • UNPAID" to "Rp 256.400",
                ).forEach { (label, total) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = Color.White, modifier = Modifier.weight(1f))
                        Text(total, color = BmaxYellow, fontWeight = FontWeight.Bold)
                    }
                }
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Inquiry") }
            }
        }
    }
}

@Composable
private fun MappingPage(customerCount: Int) {
    GlassCard(Modifier.fillMaxSize().padding(16.dp)) {
        Icon(Icons.Default.Map, null, tint = BmaxCyan, modifier = Modifier.size(42.dp))
        Text("Google Maps", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("$customerCount pelanggan hasil filter siap dipetakan.", color = Color.White.copy(alpha = .65f))
        Spacer(Modifier.height(10.dp))
        Text("Filter: Region • ULP • Biller • RBM", color = Color.White.copy(alpha = .60f))
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Buka Peta Pelanggan") }
    }
}

@Composable
private fun ProfilePage(role: Role) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Person, null, tint = BmaxCyan, modifier = Modifier.size(48.dp))
                Text("Profil Pengguna", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Rahmat K • $role • ULP Sumedang", color = Color.White.copy(alpha = .65f))
            }
        }
        item { ProfileAction("Wilayah / ULP", Icons.Default.AccountTree) }
        item { ProfileAction("RBM A-E", Icons.Default.Groups) }
        item { ProfileAction("Transaction & Invoice", Icons.Default.ReceiptLong) }
        item { ProfileAction("Payment & Check Status", Icons.Default.Payment) }
    }
}

@Composable
private fun ProfileAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = BmaxYellow)
            Spacer(Modifier.width(12.dp))
            Text(title, color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = {}) { Text("Buka") }
        }
    }
}

private fun navIcon(tab: Tab) = when (tab) {
    Tab.DASHBOARD -> Icons.Default.Dashboard
    Tab.CUSTOMER -> Icons.Default.Groups
    Tab.BILLING -> Icons.Default.ReceiptLong
    Tab.MAP -> Icons.Default.Map
    Tab.PROFILE -> Icons.Default.Person
}
