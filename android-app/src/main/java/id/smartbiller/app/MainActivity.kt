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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.smartbiller.app.data.ApiProvider
import id.smartbiller.app.data.Billing
import id.smartbiller.app.data.BillingSummary
import id.smartbiller.app.data.Customer
import id.smartbiller.app.data.InquiryBilling
import id.smartbiller.app.data.InquiryRequest
import id.smartbiller.app.data.InquiryResponse
import id.smartbiller.app.data.Inquiry
import id.smartbiller.app.data.LeaderRow
import id.smartbiller.app.data.LoginResponse
import id.smartbiller.app.data.SessionStore
import id.smartbiller.app.data.SmartBillerApi
import id.smartbiller.app.data.ULP
import id.smartbiller.app.data.User
import id.smartbiller.app.ui.AppBackground
import id.smartbiller.app.ui.GlassCard
import id.smartbiller.app.ui.theme.SmartBillerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class SessionTokenHolder { var token: String? = null }

private data class DemoCustomer(val no: String, val name: String, val address: String, val bill: Double, val lat: Double, val lng: Double)

private val demoCustomers = listOf(
    DemoCustomer("535111194993", "H. Asep Seepudin", "Jl. Raya Situraja, No. 123, Desa Mekarsari, Sumedang", 412800.0, -6.8554, 107.9236),
    DemoCustomer("535113329697", "Ibu Rina Lestari", "Jl. Nasional Sumedang", 256400.0, -6.8583, 107.9211),
    DemoCustomer("535111333398", "Dede Ahmad", "Dusun Sukamaju, Sumedang", 389500.0, -6.8612, 107.9262),
    DemoCustomer("535111988644", "Siti Nurhaliza", "Jl. Tanjungsari, Sumedang", 218900.0, -6.8671, 107.9183),
)

class MainViewModel(private val store: SessionStore, private val api: SmartBillerApi, private val holder: SessionTokenHolder) : ViewModel() {
    var token by mutableStateOf<String?>(null); private set
    var user by mutableStateOf<User?>(null); private set
    var summary by mutableStateOf<BillingSummary?>(null); private set
    var billings by mutableStateOf<List<Billing>>(emptyList()); private set
    var customers by mutableStateOf<List<Customer>>(emptyList()); private set
    var leaders by mutableStateOf<List<LeaderRow>>(emptyList()); private set
    var inquiry by mutableStateOf<InquiryResponse?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var apiOnline by mutableStateOf(false); private set
    var lastSync by mutableStateOf("Belum tersinkronisasi"); private set

    init { viewModelScope.launch { token = store.token.first(); holder.token = token; user = store.user.first(); if (token != null) refresh() } }

    fun login(email: String, password: String) { viewModelScope.launch { loading = true; error = null; try { val response = api.login(mapOf("email" to email, "password" to password)); store.save(response); token = response.token; holder.token = response.token; user = response.user; refresh() } catch (_: Exception) { val normalized = email.trim().lowercase(); val valid = normalized in setOf("admin", "supervisor", "biller", "admin@example.com", "supervisor@example.com", "biller@example.com"); if (valid && password == "change-me-now") { val role = when (normalized) { "supervisor", "supervisor@example.com" -> "SUPERVISOR"; "biller", "biller@example.com" -> "BILLER"; else -> "ADMIN" }; val mail = if (normalized.contains('@')) normalized else "$normalized@example.com"; val localUser = User("demo-$role", mail, if (role == "ADMIN") "Administrator Demo" else if (role == "SUPERVISOR") "Supervisor Demo" else "Biller Demo", role); val response = LoginResponse("review-$role-local", localUser); store.save(response); token = response.token; holder.token = response.token; user = localUser; loadDemoData() } else error = "Login gagal. Gunakan akun demo: admin / change-me-now" } finally { loading = false } } }
    fun refresh() { viewModelScope.launch { loading = true; error = null; try { summary = api.summary(); billings = api.billing().items; leaders = api.leaderboard().rows; apiOnline = true; lastSync = "Tersinkronisasi dari Supabase" } catch (_: Exception) { apiOnline = false; loadDemoData() } finally { loading = false } } }

    private fun loadDemoData() {
        val period = "2026-08"
        summary = BillingSummary(period, "PREVENTIF", demoCustomers.size, 1, 1)
        billings = demoCustomers.mapIndexed { index, customer -> Billing("demo-bill-$index", period, when { index == 2 -> "LEWAT_TEMPO"; index == 1 -> "SUDAH_BAYAR"; else -> "PREVENTIF" }, if (index == 1) "PAID" else "UNPAID", customer.bill, "2026-08-20T23:59:59.000Z", Customer(customer.no, customer.no, customer.name, "3200${customer.no.takeLast(6)}", customer.address, ULP("ULP Sumedang"), customer.lat, customer.lng)) }
        customers = demoCustomers.map { customer -> Customer(customer.no, customer.no, customer.name, "3200${customer.no.takeLast(6)}", customer.address, ULP("ULP Sumedang"), customer.lat, customer.lng) }
        leaders = listOf(LeaderRow("biller@example.com", "Biller Demo", "ULP Sumedang", "Jawa Barat", demoCustomers.size))
        lastSync = "Mode Review • data demo"
    }

    fun search(query: String) { viewModelScope.launch { try { customers = api.search(query).items } catch (_: Exception) { customers = demoCustomers.filter { query.isBlank() || listOf(it.no, it.name, it.address).any { value -> value.contains(query, true) } }.map { customer -> Customer(customer.no, customer.no, customer.name, "3200${customer.no.takeLast(6)}", customer.address, ULP("ULP Sumedang"), customer.lat, customer.lng) } } } }
    fun checkInquiry(customerNo: String) { viewModelScope.launch { loading = true; error = null; try { inquiry = api.inquiry(InquiryRequest(customerNo)) } catch (_: Exception) { val customer = demoCustomers.firstOrNull { it.no == customerNo }; if (customer != null) inquiry = InquiryResponse(Customer(customer.no, customer.no, customer.name, "320012345678", customer.address, ULP("ULP Sumedang"), customer.lat, customer.lng), InquiryBilling("AGU 2026", customer.bill, customer.bill, 2500.0), Inquiry("INQ-DEMO", "SUCCESS")) else error = "ID Pelanggan demo tidak ditemukan." } finally { loading = false } } }
    fun logout() { viewModelScope.launch { store.clear(); token = null; holder.token = null; user = null; summary = null; billings = emptyList(); customers = emptyList(); leaders = emptyList(); inquiry = null; error = null; apiOnline = false } }
}

enum class AppTab { HOME, CUSTOMERS, BILLINGS, PAY, PROFILE }

class MainActivity : ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { val store = remember { SessionStore(applicationContext) }; val holder = remember { SessionTokenHolder() }; val api = remember { ApiProvider().create { holder.token } }; val vm = remember { MainViewModel(store, api, holder) }; SmartBillerTheme { SmartBillerApp(vm) } } } }

@Composable private fun SmartBillerApp(vm: MainViewModel) { var splashVisible by rememberSaveable { mutableStateOf(true) }; LaunchedEffect(Unit) { delay(1200); splashVisible = false }; if (splashVisible) SplashScreen() else if (vm.token == null) LoginScreen(vm) else DashboardShell(vm) }

@Composable private fun SplashScreen() { AppBackground { Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Box(Modifier.size(110.dp).background(Color.White.copy(alpha = 0.12f), MaterialTheme.shapes.extraLarge), contentAlignment = Alignment.Center) { Icon(Icons.Default.Bolt, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(58.dp)) }; Spacer(Modifier.height(18.dp)); Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("PLN Electricity Services", color = Color.White.copy(alpha = 0.72f)); Spacer(Modifier.height(18.dp)); Text("Monitoring • Edukasi • Pelayanan", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall) } } }

@Composable private fun LoginScreen(vm: MainViewModel) { var username by rememberSaveable { mutableStateOf("admin") }; var password by rememberSaveable { mutableStateOf("change-me-now") }; var visible by rememberSaveable { mutableStateOf(false) }; AppBackground { Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { GlassCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Bolt, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(48.dp)); Spacer(Modifier.width(12.dp)); Column { Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("PLN Electricity Services", color = Color.White.copy(alpha = 0.75f)) } }; Spacer(Modifier.height(18.dp)); Text("Selamat Datang 👋", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Masuk untuk melanjutkan", color = Color.White.copy(alpha = 0.7f)); Spacer(Modifier.height(16.dp)); OutlinedTextField(username, { username = it }, label = { Text("Username / Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(10.dp)); OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Sembunyikan" else "Lihat") } }); vm.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Color(0xFFFFB4AB)) }; Spacer(Modifier.height(14.dp)); Button(onClick = { vm.login(username, password) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) { Text(if (vm.loading) "Memeriksa..." else "Masuk") }; Spacer(Modifier.height(8.dp)); Text("Demo: admin / change-me-now", color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall) } } } }

@Composable private fun DashboardShell(vm: MainViewModel) { var tab by rememberSaveable { mutableIntStateOf(0) }; val items = listOf(AppTab.HOME to ("Beranda" to Icons.Default.Dashboard), AppTab.CUSTOMERS to ("Pelanggan" to Icons.Default.People), AppTab.BILLINGS to ("Tagihan" to Icons.Default.ReceiptLong), AppTab.PAY to ("Bayar" to Icons.Default.Bolt), AppTab.PROFILE to ("Profil" to Icons.Default.Person)); Scaffold(containerColor = Color.Transparent, bottomBar = { NavigationBar { items.forEachIndexed { index, item -> NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(item.second.second, item.second.first) }, label = { Text(item.second.first) }) } } }) { padding -> Column(Modifier.fillMaxSize().padding(padding)) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("${vm.user?.name ?: "Petugas"} • ${vm.user?.role ?: "BILLER"}", color = Color.White.copy(alpha = 0.72f)) }; IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Refresh", tint = Color.White) } }; when (items[tab].first) { AppTab.HOME -> HomeScreen(vm); AppTab.CUSTOMERS -> CustomerScreen(vm); AppTab.BILLINGS -> BillingScreen(vm); AppTab.PAY -> InquiryScreen(vm); AppTab.PROFILE -> ProfileScreen(vm) } } } }

@Composable private fun HomeScreen(vm: MainViewModel) { LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) { item { GlassCard(Modifier.fillMaxWidth()) { Text("Halo, ${vm.user?.name ?: "Petugas"}", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("${vm.user?.role ?: "BILLER"} • ULP Sumedang", color = Color.White.copy(alpha = 0.72f)); Spacer(Modifier.height(8.dp)); Text("Periode ${vm.summary?.period ?: "2026-08"}", color = Color.White.copy(alpha = 0.7f)); Text(vm.summary?.category ?: "PREVENTIF", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold); Text(vm.lastSync, color = Color.White.copy(alpha = 0.58f), style = MaterialTheme.typography.bodySmall) } }; item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Pelanggan", (vm.summary?.preventif ?: 0).toString()); MetricCard("Bayar", vm.billings.count { it.status == "PAID" }.toString()); MetricCard("Belum", vm.billings.count { it.status != "PAID" }.toString()) } }; item { GlassCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Map, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(32.dp)); Spacer(Modifier.width(10.dp)); Column { Text("Peta Pelanggan", color = Color.White, fontWeight = FontWeight.Bold); Text("Lokasi pelanggan tersedia melalui Google Maps", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall) } } } } } }

@Composable private fun MetricCard(title: String, value: String) { GlassCard(Modifier.fillMaxWidth()) { Text(title, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(6.dp)); Text(value, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) } }

@Composable private fun CustomerScreen(vm: MainViewModel) { var query by rememberSaveable { mutableStateOf("") }; LaunchedEffect(query) { vm.search(query) }; LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) { item { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Cari ID / nama / alamat") }, leadingIcon = { Icon(Icons.Default.Search, null) }) }; items(vm.customers) { customer -> GlassCard(Modifier.fillMaxWidth()) { Text(customer.name ?: "Pelanggan", color = Color.White, fontWeight = FontWeight.Bold); Text("ID ${customer.customerNo}", color = Color.White.copy(alpha = 0.7f)); Text(customer.address ?: "Alamat belum tersedia", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(6.dp)); TextButton(onClick = { val lat = customer.latitude; val lng = customer.longitude; if (lat != null && lng != null) openGoogleMaps(lat, lng) }) { Text("Buka lokasi") } } } } }

@Composable private fun BillingScreen(vm: MainViewModel) { LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) { items(vm.billings) { billing -> GlassCard(Modifier.fillMaxWidth()) { Text(billing.customer.name ?: "Pelanggan", color = Color.White, fontWeight = FontWeight.Bold); Text("${billing.period} • ${billing.category}", color = Color.White.copy(alpha = 0.7f)); Text(formatRupiah(billing.total), color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold); Text(billing.status, color = Color.White.copy(alpha = 0.65f)) } } } }

@Composable private fun InquiryScreen(vm: MainViewModel) { var customerNo by rememberSaveable { mutableStateOf("535111194993") }; LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) { item { GlassCard(Modifier.fillMaxWidth()) { Text("Inquiry Tagihan", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); OutlinedTextField(customerNo, { customerNo = it }, label = { Text("ID Pelanggan") }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(10.dp)); Button(onClick = { vm.checkInquiry(customerNo) }, modifier = Modifier.fillMaxWidth()) { Text("Inquiry") } } }; vm.inquiry?.let { result -> item { GlassCard(Modifier.fillMaxWidth()) { Text(result.customer.name ?: "Pelanggan", color = Color.White, fontWeight = FontWeight.Bold); Text("Periode ${result.billing.periode}", color = Color.White.copy(alpha = 0.7f)); Text(formatRupiah(result.billing.amount), color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold); Text("Reference ${result.inquiry.id}", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall) } } } } }

@Composable private fun ProfileScreen(vm: MainViewModel) { LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) { item { GlassCard(Modifier.fillMaxWidth()) { Text(vm.user?.name ?: "Petugas", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(vm.user?.email ?: "", color = Color.White.copy(alpha = 0.7f)); Text(vm.user?.role ?: "BILLER", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold) } }; item { Button(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("Keluar") } } } }

private fun formatRupiah(value: Double): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply { maximumFractionDigits = 0; minimumFractionDigits = 0 }.format(value)

private fun openGoogleMaps(lat: Double, lng: Double) { val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng")); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); try { AppContextHolder.context?.startActivity(intent) } catch (_: Exception) { } }
