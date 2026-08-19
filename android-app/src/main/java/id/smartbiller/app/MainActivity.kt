package id.smartbiller.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import id.smartbiller.app.ui.theme.SmartBillerTheme
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmartBillerTheme { SmartBillerApp() } }
    }
}

@Composable
private fun SmartBillerApp() {
    var splash by rememberSaveable { mutableStateOf(true) }
    val vm = rememberSaveable(saver = MainViewModel.Saver) { MainViewModel() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(900)
        splash = false
        vm.restoreSession()
    }
    when {
        splash -> SplashScreen()
        vm.user == null -> LoginScreen(vm)
        else -> DashboardShell(vm)
    }
}

private enum class AppTab { HOME, CUSTOMERS, BILLINGS, PAY, PROFILE }

private class MainViewModel {
    var user by mutableStateOf<id.smartbiller.app.data.User?>(null)
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var customers = mutableStateOf(emptyList<id.smartbiller.app.data.Customer>()).value
    var billings = mutableStateOf(emptyList<id.smartbiller.app.data.Billing>()).value
    var inquiry by mutableStateOf<id.smartbiller.app.data.InquiryResponse?>(null)
    var summary by mutableStateOf<id.smartbiller.app.data.BillingSummary?>(null)
    var lastSync by mutableStateOf("Belum sinkron")

    fun restoreSession() {
        user = id.smartbiller.app.data.User("demo", "admin@example.com", "Admin Demo", "ADMIN")
        loadDemoData()
    }

    fun login(username: String, password: String) {
        loading = true
        error = null
        if ((username == "admin" || username == "admin@example.com") && password == "change-me-now") {
            user = id.smartbiller.app.data.User("demo-admin", "admin@example.com", "Admin Demo", "ADMIN")
            loadDemoData()
        } else {
            error = "Username atau password tidak valid. Gunakan admin / change-me-now."
        }
        loading = false
    }

    fun logout() { user = null; inquiry = null }
    fun refresh() { loadDemoData() }
    fun search(query: String) { if (query.isBlank()) return; customers = customers.filter { it.customerNo.contains(query, true) || (it.name ?: "").contains(query, true) || (it.address ?: "").contains(query, true) } }
    fun checkInquiry(customerNo: String) { val customer = customers.firstOrNull { it.customerNo == customerNo } ?: return; val billing = billings.firstOrNull { it.customer.customerNo == customerNo } ?: return; inquiry = id.smartbiller.app.data.InquiryResponse(customer, id.smartbiller.app.data.InquiryBilling(billing.period, billing.total, billing.total, 2500.0), id.smartbiller.app.data.Inquiry("INQ-$customerNo", "READY")) }
    private fun loadDemoData() { customers = listOf(id.smartbiller.app.data.Customer("1", "535111194993", "Pelanggan Demo 01", "123456789", "Sumedang", id.smartbiller.app.data.ULP("ULP Sumedang"), -6.8587, 107.9236), id.smartbiller.app.data.Customer("2", "535111194994", "Pelanggan Demo 02", "123456790", "Tanjungsari", id.smartbiller.app.data.ULP("ULP Sumedang"), -6.952, 107.826)); billings = customers.mapIndexed { i, c -> id.smartbiller.app.data.Billing("B$i", "2026-08", "PREVENTIF", if (i == 0) "PAID" else "UNPAID", 175000.0 + i * 25000, "2026-08-20", c) }; summary = id.smartbiller.app.data.BillingSummary("2026-08", "PREVENTIF", customers.size, 0, 0); lastSync = "Sinkronisasi demo berhasil" }

    companion object { val Saver = androidx.compose.runtime.saveable.Saver<MainViewModel, Boolean>(save = { true }, restore = { MainViewModel() }) }
}

@Composable private fun AppBackground(content: @Composable () -> Unit) { Box(Modifier.fillMaxSize().background(Color(0xFF071A33))) { content() } }
@Composable private fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) { Column(modifier.background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp)).padding(18.dp), content = content) }

@Composable private fun SplashScreen() { AppBackground { Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Box(Modifier.size(110.dp).background(Color.White.copy(alpha = 0.12f), MaterialTheme.shapes.extraLarge), contentAlignment = Alignment.Center) { Icon(Icons.Default.Bolt, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(58.dp)) }; Spacer(Modifier.height(18.dp)); Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("PLN Electricity Services", color = Color.White.copy(alpha = 0.72f)); Spacer(Modifier.height(18.dp)); Text("Monitoring • Edukasi • Pelayanan", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall) } } }
@Composable private fun LoginScreen(vm: MainViewModel) { var username by rememberSaveable { mutableStateOf("admin") }; var password by rememberSaveable { mutableStateOf("change-me-now") }; var visible by rememberSaveable { mutableStateOf(false) }; AppBackground { Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { GlassCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Bolt, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(48.dp)); Spacer(Modifier.width(12.dp)); Column { Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("PLN Electricity Services", color = Color.White.copy(alpha = 0.75f)) } }; Spacer(Modifier.height(18.dp)); Text("Selamat Datang 👋", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)); OutlinedTextField(username, { username = it }, label = { Text("Username / Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(10.dp)); OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Sembunyikan" else "Lihat") } }); vm.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Color(0xFFFFB4AB)) }; Spacer(Modifier.height(14.dp)); Button(onClick = { vm.login(username, password) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) { Text(if (vm.loading) "Memeriksa..." else "Masuk") }; Spacer(Modifier.height(8.dp)); Text("Demo: admin / change-me-now", color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall) } } } }

@Composable private fun DashboardShell(vm: MainViewModel) { var tab by rememberSaveable { mutableIntStateOf(0) }; val items = listOf(AppTab.HOME to ("Beranda" to Icons.Default.Dashboard), AppTab.CUSTOMERS to ("Pelanggan" to Icons.Default.People), AppTab.BILLINGS to ("Tagihan" to Icons.Default.ReceiptLong), AppTab.PAY to ("Bayar" to Icons.Default.Bolt), AppTab.PROFILE to ("Profil" to Icons.Default.Person)); Scaffold(containerColor = Color.Transparent, bottomBar = { NavigationBar { items.forEachIndexed { index, item -> NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(item.second.second, item.second.first) }, label = { Text(item.second.first) }) } } }) { padding -> Column(Modifier.fillMaxSize().padding(padding)) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("${vm.user?.name ?: "Petugas"} • ${vm.user?.role ?: "BILLER"}", color = Color.White.copy(alpha = 0.72f)) }; IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Refresh", tint = Color.White) } }; when (items[tab].first) { AppTab.HOME -> HomeScreen(vm); AppTab.CUSTOMERS -> CustomerScreen(vm); AppTab.BILLINGS -> BillingScreen(vm); AppTab.PAY -> InquiryScreen(vm); AppTab.PROFILE -> ProfileScreen(vm) } } } }
@Composable private fun HomeScreen(vm: MainViewModel) { LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) { item { GlassCard(Modifier.fillMaxWidth()) { Text("Halo, ${vm.user?.name ?: "Petugas"}", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("${vm.user?.role ?: "BILLER"} • ULP Sumedang", color = Color.White.copy(alpha = 0.72f)); Spacer(Modifier.height(8.dp)); Text("Periode ${vm.summary?.period ?: "2026-08"}", color = Color.White.copy(alpha = 0.7f)); Text(vm.summary?.category ?: "PREVENTIF", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold); Text(vm.lastSync, color = Color.White.copy(alpha = 0.58f), style = MaterialTheme.typography.bodySmall) } }; item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Pelanggan", (vm.summary?.preventif ?: 0).toString()); MetricCard("Bayar", vm.billings.count { it.status == "PAID" }.toString()); MetricCard("Belum", vm.billings.count { it.status != "PAID" }.toString()) } }; item { GlassCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Map, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(32.dp)); Spacer(Modifier.width(10.dp)); Column { Text("Peta Pelanggan", color = Color.White, fontWeight = FontWeight.Bold); Text("Lokasi pelanggan tersedia melalui Google Maps", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall) } } } } } }
@Composable private fun MetricCard(title: String, value: String) { GlassCard(Modifier.fillMaxWidth()) { Text(title, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(6.dp)); Text(value, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) } }
@Composable private fun CustomerScreen(vm: MainViewModel) { var query by rememberSaveable { mutableStateOf("") }; LaunchedEffect(query) { vm.search(query) }; LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) { item { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Cari ID / nama / alamat") }) }; items(vm.customers) { customer -> GlassCard(Modifier.fillMaxWidth()) { Text(customer.name ?: "Pelanggan", color = Color.White, fontWeight = FontWeight.Bold); Text("ID ${customer.customerNo}", color = Color.White.copy(alpha = 0.7f)); Text(customer.address ?: "Alamat belum tersedia", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(6.dp)); val context = LocalContext.current; TextButton(onClick = { val lat = customer.latitude; val lng = customer.longitude; if (lat != null && lng != null) { val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng")); context.startActivity(intent) } }) { Text("Buka lokasi") } } } } }
@Composable private fun BillingScreen(vm: MainViewModel) { LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) { items(vm.billings) { billing -> GlassCard(Modifier.fillMaxWidth()) { Text(billing.customer.name ?: "Pelanggan", color = Color.White, fontWeight = FontWeight.Bold); Text("${billing.period} • ${billing.category}", color = Color.White.copy(alpha = 0.7f)); Text(formatRupiah(billing.total), color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold); Text(billing.status, color = Color.White.copy(alpha = 0.65f)) } } } }
@Composable private fun InquiryScreen(vm: MainViewModel) { var customerNo by rememberSaveable { mutableStateOf("535111194993") }; LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) { item { GlassCard(Modifier.fillMaxWidth()) { Text("Inquiry Tagihan", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); OutlinedTextField(customerNo, { customerNo = it }, label = { Text("ID Pelanggan") }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(10.dp)); Button(onClick = { vm.checkInquiry(customerNo) }, modifier = Modifier.fillMaxWidth()) { Text("Inquiry") } } }; vm.inquiry?.let { result -> item { GlassCard(Modifier.fillMaxWidth()) { Text(result.customer.name ?: "Pelanggan", color = Color.White, fontWeight = FontWeight.Bold); Text("Periode ${result.billing.periode}", color = Color.White.copy(alpha = 0.7f)); Text(formatRupiah(result.billing.amount), color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold); Text("Reference ${result.inquiry.id}", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall) } } } } }
@Composable private fun ProfileScreen(vm: MainViewModel) { LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) { item { GlassCard(Modifier.fillMaxWidth()) { Text(vm.user?.name ?: "Petugas", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(vm.user?.email ?: "", color = Color.White.copy(alpha = 0.7f)); Text(vm.user?.role ?: "BILLER", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold) } }; item { Button(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("Keluar") } } } }
private fun formatRupiah(value: Double): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply { maximumFractionDigits = 0; minimumFractionDigits = 0 }.format(value)
