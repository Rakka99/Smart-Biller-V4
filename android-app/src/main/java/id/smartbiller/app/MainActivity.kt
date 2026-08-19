package id.smartbiller.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.smartbiller.app.data.*
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

class MainViewModel(
    private val store: SessionStore,
    private val api: SmartBillerApi,
    private val holder: SessionTokenHolder,
) : ViewModel() {
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

    init {
        viewModelScope.launch {
            token = store.token.first()
            holder.token = token
            user = store.user.first()
            if (token != null) refresh()
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val response = api.login(mapOf("email" to email, "password" to password))
                store.save(response)
                token = response.token
                holder.token = response.token
                user = response.user
                refresh()
            } catch (_: Exception) {
                val valid = setOf("admin", "supervisor", "biller", "admin@example.com", "supervisor@example.com", "biller@example.com")
                if (email.trim().lowercase() in valid && password == "change-me-now") {
                    val role = when (email.trim().lowercase()) {
                        "supervisor", "supervisor@example.com" -> "SUPERVISOR"
                        "biller", "biller@example.com" -> "BILLER"
                        else -> "ADMIN"
                    }
                    val mail = if (email.contains("@")) email else "$email@example.com"
                    val localUser = User("demo-$role", mail, when (role) { "ADMIN" -> "Administrator Demo"; "SUPERVISOR" -> "Supervisor Demo"; else -> "Biller Demo" }, role)
                    val response = LoginResponse("review-$role-local", localUser)
                    store.save(response)
                    token = response.token
                    holder.token = response.token
                    user = localUser
                    loadDemoData()
                } else {
                    error = "Login gagal. Gunakan akun demo: admin / change-me-now"
                }
            } finally { loading = false }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                summary = api.summary()
                billings = api.billing().items
                leaders = api.leaderboard().rows
                apiOnline = true
                lastSync = "Tersinkronisasi dari Supabase"
            } catch (_: Exception) {
                apiOnline = false
                loadDemoData()
            } finally { loading = false }
        }
    }

    private fun loadDemoData() {
        val now = "2026-08"
        summary = BillingSummary(now, "PREVENTIF", demoCustomers.size, 1, 1)
        billings = demoCustomers.mapIndexed { index, c ->
            Billing(
                "demo-bill-$index", now,
                if (index == 2) "LEWAT_TEMPO" else if (index == 1) "SUDAH_BAYAR" else "PREVENTIF",
                if (index == 1) "PAID" else "UNPAID", c.bill,
                "2026-08-20T23:59:59.000Z",
                Customer(c.no, c.no, c.name, "3200${c.no.takeLast(6)}", c.address, ULP("ULP Sumedang"), c.lat, c.lng),
            )
        }
        customers = demoCustomers.map { c -> Customer(c.no, c.no, c.name, "3200${c.no.takeLast(6)}", c.address, ULP("ULP Sumedang"), c.lat, c.lng) }
        leaders = listOf(LeaderRow("biller@example.com", "Biller Demo", "ULP Sumedang", "Jawa Barat", demoCustomers.size))
        lastSync = "Mode Review • data demo"
    }

    fun search(query: String) {
        viewModelScope.launch {
            try { customers = api.search(query).items }
            catch (_: Exception) {
                customers = demoCustomers.filter { query.isBlank() || listOf(it.no, it.name, it.address).any { value -> value.contains(query, true) } }
                    .map { Customer(it.no, it.no, it.name, "3200${it.no.takeLast(6)}", it.address, ULP("ULP Sumedang"), it.lat, it.lng) }
            }
        }
    }

    fun checkInquiry(customerNo: String) {
        viewModelScope.launch {
            loading = true
            error = null
            try { inquiry = api.inquiry(InquiryRequest(customerNo)) }
            catch (_: Exception) {
                val c = demoCustomers.firstOrNull { it.no == customerNo }
                if (c != null) inquiry = InquiryResponse(
                    Customer(c.no, c.no, c.name, "320012345678", c.address, ULP("ULP Sumedang"), c.lat, c.lng),
                    InquiryBilling("AGU 2026", c.bill, c.bill, 2500.0), Inquiry("INQ-DEMO", "SUCCESS")
                ) else error = "ID Pelanggan demo tidak ditemukan."
            }
            finally { loading = false }
        }
    }

    fun logout() {
        viewModelScope.launch {
            store.clear(); token = null; holder.token = null; user = null; summary = null
            billings = emptyList(); customers = emptyList(); leaders = emptyList(); inquiry = null; error = null; apiOnline = false
        }
    }
}

private enum class AppScreen { HOME, CUSTOMERS, BILLINGS, PAY, REPORTS, MAP, PROFILE, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val store = remember { SessionStore(applicationContext) }
            val holder = remember { SessionTokenHolder() }
            val vm = remember { MainViewModel(store, ApiProvider().create { holder.token }, holder) }
            SmartBillerApp(vm)
        }
    }
}

@Composable
private fun SmartBillerApp(vm: MainViewModel) {
    var splash by rememberSaveable { mutableStateOf(true) }
    var darkTheme by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1200); splash = false }
    SmartBillerTheme(darkTheme = darkTheme) {
        if (splash) SplashScreen() else if (vm.token == null) LoginScreen(vm) else DashboardShell(vm, darkTheme, { darkTheme = it })
    }
}

@Composable
private fun SplashScreen() {
    AppBackground {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(110.dp).background(Color.White.copy(.12f), MaterialTheme.shapes.extraLarge)) {
                Icon(Icons.Default.Bolt, null, tint = Color(0xFFFFD54F), modifier = Modifier.fillMaxSize().padding(22.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("PLN Electricity Services", color = Color.White.copy(.72f))
            Spacer(Modifier.height(24.dp))
            Text("Monitoring • Edukasi • Pelayanan", color = Color.White.copy(.65f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LoginScreen(vm: MainViewModel) {
    var email by rememberSaveable { mutableStateOf("admin") }
    var password by rememberSaveable { mutableStateOf("change-me-now") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    AppBackground {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            GlassCard(Modifier.fillMaxWidth().widthIn(max = 460.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("PLN Electricity Services", color = Color.White.copy(.75f))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Selamat Datang 👋", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Masuk untuk melanjutkan", color = Color.White.copy(.72f))
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Username / Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Sembunyikan" else "Lihat") } })
                vm.error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Color(0xFFFFB4AB)) }
                Spacer(Modifier.height(14.dp))
                Button(onClick = { vm.login(email, password) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) { Text(if (vm.loading) "Memeriksa..." else "Masuk") }
                Spacer(Modifier.height(10.dp))
                Text("Demo: admin / change-me-now", color = Color.White.copy(.65f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DashboardShell(vm: MainViewModel, darkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit) {
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    val nav = listOf(AppScreen.HOME to ("Beranda" to Icons.Default.Dashboard), AppScreen.CUSTOMERS to ("Pelanggan" to Icons.Default.People), AppScreen.BILLINGS to ("Tagihan" to Icons.Default.ReceiptLong), AppScreen.PAY to ("Bayar" to Icons.Default.Bolt), AppScreen.PROFILE to ("Profil" to Icons.Default.Person))
    Scaffold(containerColor = Color.Transparent, bottomBar = {
        NavigationBar {
            nav.forEach { (target, item) -> NavigationBarItem(selected = screen == target, onClick = { screen = target }, icon = { Icon(item.second, item.first) }, label = { Text(item.first) }) }
        }
    }) { padding ->
        AppBackground {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${vm.user?.name ?: "Petugas"} • ${vm.user?.role ?: "BILLER"}", color = Color.White.copy(.72f))
                    }
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Refresh", tint = Color.White) }
                }
                when (screen) {
                    AppScreen.HOME -> Home(vm, { screen = it })
                    AppScreen.CUSTOMERS -> Customers(vm) { selectedCustomer = it }
                    AppScreen.BILLINGS -> BillingList(vm)
                    AppScreen.PAY -> PayScreen(vm)
                    AppScreen.REPORTS -> Reports(vm)
                    AppScreen.MAP -> MapScreen(vm)
                    AppScreen.PROFILE -> Profile(vm, darkTheme, onDarkThemeChange) { screen = AppScreen.SETTINGS }
                    AppScreen.SETTINGS -> SettingsScreen(vm, darkTheme, onDarkThemeChange) { screen = AppScreen.PROFILE }
                }
            }
        }
    }
    selectedCustomer?.let { c -> CustomerDetailDialog(c, { selectedCustomer = null }, { openGoogleMaps(c) }) }
}

@Composable
private fun Home(vm: MainViewModel, onShortcut: (AppScreen) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Halo, ${vm.user?.name ?: "Petugas"}", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${vm.user?.role ?: "BILLER"} • ULP Sumedang", color = Color.White.copy(.72f))
                }
                Icon(Icons.Default.Person, "Profil", tint = Color.White, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text("Periode ${vm.summary?.period ?: "2026-08"}", color = Color.White.copy(.72f))
            Text(vm.summary?.category ?: "PREVENTIF", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
            Text(vm.lastSync, color = Color.White.copy(.56f), style = MaterialTheme.typography.bodySmall)
        }}
        item { LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.height(190.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Metric("Total Pelanggan", vm.summary?.preventif ?: 0, Icons.Default.People) }
            item { Metric("Sudah Bayar", vm.billings.count { it.status == "PAID" }, Icons.Default.CheckCircle) }
            item { Metric("Belum Bayar", vm.billings.count { it.status == "UNPAID" }, Icons.Default.ReceiptLong) }
            item { Metric("Lewat Tempo", vm.billings.count { it.category == "LEWAT_TEMPO" }, Icons.Default.Warning) }
        }}
        item { GlassCard(Modifier.fillMaxWidth()) {
            Text("Menu Cepat", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            QuickAction("Pelanggan", "Cari & detail pelanggan", Icons.Default.People) { onShortcut(AppScreen.CUSTOMERS) }
            QuickAction("Tagihan", "Monitoring preventive/korektif", Icons.Default.ReceiptLong) { onShortcut(AppScreen.BILLINGS) }
            QuickAction("Peta", "Lokasi pelanggan & navigasi", Icons.Default.Map) { onShortcut(AppScreen.MAP) }
            QuickAction("Laporan", "Harian, mingguan, bulanan", Icons.Default.Assessment) { onShortcut(AppScreen.REPORTS) }
        }}
        item { GlassCard(Modifier.fillMaxWidth()) {
            Text("Aktivitas Hari Ini", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ActivityRow("Booking Dibuat", "08:30 – 16:00", vm.billings.size)
            ActivityRow("Pembacaan Meter", "08:30 – 16:30", vm.customers.size)
            ActivityRow("Edukasi Pelanggan", "Sepanjang hari", vm.summary?.preventif ?: 0)
        }}
    }
}

@Composable
private fun QuickAction(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Color.White.copy(.62f), style = MaterialTheme.typography.bodySmall) }
            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(.7f))
        }
    }
}

@Composable
private fun ActivityRow(title: String, time: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF7BD8A1), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(time, color = Color.White.copy(.58f), style = MaterialTheme.typography.bodySmall) }
        Text(count.toString(), color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Metric(label: String, value: Int, icon: ImageVector) {
    GlassCard(Modifier.fillMaxSize()) { Icon(icon, label, tint = Color(0xFFFFD54F)); Spacer(Modifier.height(7.dp)); Text(value.toString(), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(label, color = Color.White.copy(.7f), style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun Customers(vm: MainViewModel, onSelect: (Customer) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val source = if (query.isBlank() && vm.customers.isEmpty()) demoCustomers.map { Customer(it.no, it.no, it.name, "3200${it.no.takeLast(6)}", it.address, ULP("ULP Sumedang"), it.lat, it.lng) } else vm.customers
    val visible = when (filter) { 1 -> source.filter { c -> vm.billings.any { it.customer.customerNo == c.customerNo && it.status == "UNPAID" } }; 2 -> source.filter { c -> vm.billings.any { it.customer.customerNo == c.customerNo && it.category == "LEWAT_TEMPO" } }; else -> source }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(query, { query = it }, label = { Text("Cari ID pelanggan / nama / alamat") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Semua", "Belum Bayar", "Lewat Tempo").forEachIndexed { i, label -> FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) }) }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.search(query) }, modifier = Modifier.fillMaxWidth()) { Text("Cari Pelanggan") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(visible) { c -> CustomerCard(c) { onSelect(c) } }
        }
    }
}

@Composable
private fun CustomerCard(c: Customer, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(c.name ?: "Tanpa nama", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(c.customerNo, color = Color.White.copy(.72f))
                    Text(c.address ?: "Alamat belum tersedia", color = Color.White.copy(.62f), style = MaterialTheme.typography.bodySmall)
                    Text("${c.ulp?.name ?: "ULP Sumedang"} • ${c.meterNo ?: "Meter —"}", color = Color(0xFF9BD7FF), style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(.7f))
            }
        }
    }
}

@Composable
private fun CustomerDetailDialog(c: Customer, onDismiss: () -> Unit, onMap: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Detail Pelanggan") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.name ?: "Tanpa nama", fontWeight = FontWeight.Bold)
            Text(c.customerNo)
            Text(c.address ?: "Alamat belum tersedia")
            Text("${c.ulp?.name ?: "ULP Sumedang"} • ${c.meterNo ?: "Meter —"}")
            Text("Status layanan: Pascabayar")
            Text("Daya: 2200 VA")
        }
    }, confirmButton = { TextButton(onClick = onMap) { Text("Lihat di Peta") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } })
}

@Composable
private fun BillingList(vm: MainViewModel) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val itemsToShow = when (filter) { 1 -> vm.billings.filter { it.status == "UNPAID" }; 2 -> vm.billings.filter { it.category == "LEWAT_TEMPO" }; else -> vm.billings }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Semua", "Belum Bayar", "Lewat Tempo").forEachIndexed { i, label -> FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) }) }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(itemsToShow) { b -> GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(b.customer.name ?: b.customer.customerNo, color = Color.White, fontWeight = FontWeight.Bold); Text("${b.customer.customerNo} • ${b.period}", color = Color.White.copy(.65f), style = MaterialTheme.typography.bodySmall) }
                    Text(formatRupiah(b.total), color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text("${b.category} • Jatuh tempo ${b.dueDate.take(10)}", color = Color.White.copy(.7f))
                Text(if (b.status == "PAID") "Sudah Bayar" else if (b.category == "LEWAT_TEMPO") "Lewat Tempo" else "Belum Bayar", color = if (b.status == "PAID") Color(0xFF7BD8A1) else Color(0xFFFFB4AB), fontWeight = FontWeight.SemiBold)
            }}
        }
    }
}

@Composable
private fun PayScreen(vm: MainViewModel) {
    var no by rememberSaveable { mutableStateOf("") }
    var paid by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Cek Tagihan PLN", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp)); OutlinedTextField(no, { no = it }, label = { Text("ID Pelanggan") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp)); Button(onClick = { vm.checkInquiry(no); paid = false }, modifier = Modifier.fillMaxWidth(), enabled = no.length >= 6) { Text("Inquiry") }
        }
        vm.inquiry?.let { r ->
            Spacer(Modifier.height(12.dp)); GlassCard(Modifier.fillMaxWidth()) {
                Text(r.customer.name ?: "Pelanggan", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(r.customer.customerNo, color = Color.White.copy(.7f)); Spacer(Modifier.height(10.dp))
                Text("AGU 2026 • Administrasi Rp 2.500", color = Color.White.copy(.7f))
                Text(formatRupiah(r.billing.amount), color = Color(0xFFFFD54F), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp)); Text("Metode Pembayaran", color = Color.White.copy(.7f));
                Button(onClick = { paid = true }, modifier = Modifier.fillMaxWidth()) { Text("Pembayaran Simulasi") }
                if (paid) { Spacer(Modifier.height(8.dp)); Text("Pembayaran demo berhasil • Ref DEMO-${r.inquiry.id.takeLast(8)}", color = Color(0xFF7BD8A1), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun Reports(vm: MainViewModel) {
    val total = vm.billings.size.coerceAtLeast(1)
    val paid = vm.billings.count { it.status == "PAID" }
    val unpaid = vm.billings.count { it.status == "UNPAID" }
    val overdue = vm.billings.count { it.category == "LEWAT_TEMPO" }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Laporan Harian", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("18 Agu 2026", color = Color.White.copy(.7f)) }
            Spacer(Modifier.height(12.dp))
            Text("Grafik Pembayaran", color = Color.White.copy(.75f))
            Spacer(Modifier.height(10.dp))
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val values = listOf(paid.toFloat() / total, unpaid.toFloat() / total, overdue.toFloat() / total)
                val labels = listOf("Bayar", "Belum", "Tempo")
                val barWidth = size.width / 7f
                values.forEachIndexed { i, v ->
                    val x = barWidth * (i * 2 + 1)
                    drawRect(Color(0xFF3FA7FF), androidx.compose.ui.geometry.Offset(x, size.height * (1f - v.coerceIn(.08f, 1f))), androidx.compose.ui.geometry.Size(barWidth, size.height * v.coerceIn(.08f, 1f)))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { Text("Bayar $paid", color = Color.White); Text("Belum $unpaid", color = Color.White); Text("Tempo $overdue", color = Color.White) }
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Ringkasan", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ReportRow("Pelanggan", vm.summary?.preventif ?: 0); ReportRow("Sudah Bayar", paid); ReportRow("Belum Bayar", unpaid); ReportRow("Lewat Tempo", overdue)
            Spacer(Modifier.height(8.dp)); Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Export PDF (Review)") }
        }
    }
}

@Composable
private fun ReportRow(label: String, value: Int) { Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Color.White.copy(.76f)); Text(value.toString(), color = Color.White, fontWeight = FontWeight.Bold) } }

@Composable
private fun MapScreen(vm: MainViewModel) {
    val points = vm.customers.ifEmpty { demoCustomers.map { Customer(it.no, it.no, it.name, "3200${it.no.takeLast(6)}", it.address, ULP("ULP Sumedang"), it.lat, it.lng) } }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Peta Pelanggan", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Mode review • titik lokasi demo", color = Color.White.copy(.65f), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(320.dp).background(Color(0xFFB6D7B1), MaterialTheme.shapes.large)) {
            Canvas(Modifier.fillMaxSize()) {
                for (i in 1..6) drawLine(Color.White.copy(.5f), androidx.compose.ui.geometry.Offset(0f, size.height * i / 7f), androidx.compose.ui.geometry.Offset(size.width, size.height * i / 7f), 2f)
                for (i in 1..5) drawLine(Color.White.copy(.5f), androidx.compose.ui.geometry.Offset(size.width * i / 6f, 0f), androidx.compose.ui.geometry.Offset(size.width * i / 6f, size.height), 2f)
            }
            points.forEachIndexed { i, c ->
                val x = 16f + (i * 22f) % 82f
                val y = 18f + (i * 19f) % 70f
                Icon(Icons.Default.LocationOn, null, tint = if (i % 2 == 0) Color(0xFFE53935) else Color(0xFF1E88E5), modifier = Modifier.fillMaxWidth(.01f).offset(x.dp, y.dp))
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(12.dp)) { Button(onClick = { points.firstOrNull()?.let { openGoogleMaps(it) } }, modifier = Modifier.fillMaxWidth()) { Text("Buka Google Maps") } }
        }
        Spacer(Modifier.height(10.dp))
        GlassCard(Modifier.fillMaxWidth()) { Text("Pelanggan Terdekat", color = Color.White, fontWeight = FontWeight.Bold); points.take(4).forEach { c -> Text("${c.name} • ${c.customerNo}", color = Color.White.copy(.78f), modifier = Modifier.padding(top = 7.dp)) } }
    }
}

@Composable
private fun Profile(vm: MainViewModel, darkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(54.dp)); Spacer(Modifier.width(12.dp)); Column { Text(vm.user?.name ?: "Petugas", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(vm.user?.email ?: "", color = Color.White.copy(.7f)); Text(vm.user?.role ?: "BILLER", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold) } }
        }
        ProfileAction("Pengaturan", "Tema, notifikasi, sinkronisasi & mode offline", Icons.Default.Settings, onSettings)
        ProfileAction("Sinkronisasi Data", vm.lastSync, Icons.Default.Sync) { vm.refresh() }
        ProfileAction("Laporan & Ranking", "Lihat performa biller", Icons.Default.Assessment) { }
        GlassCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.DarkMode, null, tint = Color.White); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Dark Mode", color = Color.White, fontWeight = FontWeight.SemiBold); Text("Material 3 theme", color = Color.White.copy(.62f), style = MaterialTheme.typography.bodySmall) }; Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange) } }
        Button(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) { Text("Keluar") }
    }
}

@Composable
private fun ProfileAction(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { GlassCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color.White); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Color.White.copy(.62f), style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(.7f)) } } }
}

@Composable
private fun SettingsScreen(vm: MainViewModel, darkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit, onBack: () -> Unit) {
    var notifications by rememberSaveable { mutableStateOf(true) }
    var offlineMode by rememberSaveable { mutableStateOf(true) }
    var autoSync by rememberSaveable { mutableStateOf(true) }
    var security by rememberSaveable { mutableStateOf(true) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Kembali", tint = Color.White) }; Text("Pengaturan", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) } }
        item { SettingSwitch("Notifikasi", "Pengingat pembayaran & aktivitas", Icons.Default.Notifications, notifications) { notifications = it } }
        item { SettingSwitch("Dark Mode", "Material 3 + glassmorphism", Icons.Default.DarkMode, darkTheme, onDarkThemeChange) }
        item { SettingSwitch("Mode Offline", "Tetap tampilkan database demo saat API offline", Icons.Default.CloudSync, offlineMode) { offlineMode = it } }
        item { SettingSwitch("Sinkronisasi Otomatis", "Perbarui data master secara berkala", Icons.Default.Sync, autoSync) { autoSync = it } }
        item { SettingSwitch("Keamanan Sesi", "Simpan sesi login pada DataStore", Icons.Default.Security, security) { security = it } }
        item { GlassCard(Modifier.fillMaxWidth()) { Text("Database", color = Color.White, fontWeight = FontWeight.Bold); Text("Master pelanggan demo berasal dari database review Supabase yang disiapkan dari data Excel.", color = Color.White.copy(.7f)); Spacer(Modifier.height(10.dp)); Text("Status API: ${if (vm.apiOnline) "Online" else "Offline / Review"}", color = Color.White.copy(.65f)); Button(onClick = { vm.refresh() }, modifier = Modifier.fillMaxWidth()) { Text("Sinkronkan Sekarang") } } }
        item { GlassCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, tint = Color.White); Spacer(Modifier.width(10.dp)); Column { Text("Smart Biller v2.0.6", color = Color.White, fontWeight = FontWeight.Bold); Text("Kotlin + Jetpack Compose + Material 3", color = Color.White.copy(.65f)); Text("PLN Electricity Services • Review Build", color = Color.White.copy(.55f), style = MaterialTheme.typography.bodySmall) } } } }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color.White); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Color.White.copy(.62f), style = MaterialTheme.typography.bodySmall) }; Switch(checked = checked, onCheckedChange = onChange) } }
}

private fun openGoogleMaps(customer: Customer) {
    val lat = customer.latitude ?: return
    val lng = customer.longitude ?: return
    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(customer.name ?: customer.customerNo)})")
    AppContextHolder.context?.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
}

private object AppContextHolder {
    var context: android.content.Context? = null
}

private fun formatRupiah(value: Double): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply { maximumFractionDigits = 0; minimumFractionDigits = 0 }.format(value)
