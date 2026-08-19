package id.smartbiller.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.smartbiller.app.data.*
import id.smartbiller.app.ui.AppBackground
import id.smartbiller.app.ui.GlassCard
import id.smartbiller.app.ui.theme.SmartBillerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class SessionTokenHolder { var token: String? = null }

private data class DemoCustomer(
    val no: String,
    val name: String,
    val address: String,
    val bill: Double,
)

private val demoCustomers = listOf(
    DemoCustomer("535111194993", "H. Asep Seepudin", "Jl. Raya Situraja, No. 123, Desa Mekarsari, Sumedang", 412800.0),
    DemoCustomer("535113329697", "Ibu Rina Lestari", "Jl. Nasional Sumedang", 256400.0),
    DemoCustomer("535111333398", "Dede Ahmad", "Dusun Sukamaju, Sumedang", 389500.0),
    DemoCustomer("535111988644", "Siti Nurhaliza", "Jl. Tanjungsari, Sumedang", 218900.0),
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
            } finally {
                loading = false
            }
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
            } finally {
                loading = false
            }
        }
    }

    private fun loadDemoData() {
        val now = "2026-08"
        summary = BillingSummary(now, "PREVENTIF", demoCustomers.size, 1, 1)
        billings = demoCustomers.mapIndexed { index, c ->
            Billing("demo-bill-$index", now, if (index == 2) "LEWAT_TEMPO" else "PREVENTIF", "UNPAID", c.bill, "2026-08-20T23:59:59.000Z", Customer(c.no, c.no, c.name, "3200${c.no.takeLast(6)}", c.address, null, null, null))
        }
        customers = demoCustomers.map { c -> Customer(c.no, c.no, c.name, "3200${c.no.takeLast(6)}", c.address, null, null, null) }
        leaders = listOf(LeaderRow("biller@example.com", "Biller Demo", "ULP Sumedang", "Jawa Barat", demoCustomers.size))
        lastSync = "Mode Review • data demo"
    }

    fun search(query: String) {
        viewModelScope.launch {
            try {
                customers = api.search(query).items
            } catch (_: Exception) {
                customers = demoCustomers.filter { query.isBlank() || listOf(it.no, it.name, it.address).any { value -> value.contains(query, true) } }
                    .map { Customer(it.no, it.no, it.name, "3200${it.no.takeLast(6)}", it.address, null, null, null) }
            }
        }
    }

    fun checkInquiry(customerNo: String) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                inquiry = api.inquiry(InquiryRequest(customerNo))
            } catch (_: Exception) {
                val c = demoCustomers.firstOrNull { it.no == customerNo }
                if (c != null) {
                    inquiry = InquiryResponse(
                        Customer(c.no, c.no, c.name, "320012345678", c.address, null, null, null),
                        InquiryBilling("AGU 2026", c.bill, c.bill, 2500.0),
                        Inquiry("INQ-DEMO", "SUCCESS"),
                    )
                } else error = "ID Pelanggan demo tidak ditemukan."
            } finally {
                loading = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            store.clear()
            token = null
            holder.token = null
            user = null
            summary = null
            billings = emptyList()
            customers = emptyList()
            leaders = emptyList()
            inquiry = null
            error = null
            apiOnline = false
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val store = remember { SessionStore(applicationContext) }
            val holder = remember { SessionTokenHolder() }
            val vm = remember { MainViewModel(store, ApiProvider().create { holder.token }, holder) }
            SmartBillerTheme { SmartBillerApp(vm) }
        }
    }
}

@Composable
private fun SmartBillerApp(vm: MainViewModel) {
    if (vm.token == null) LoginScreen(vm) else DashboardShell(vm)
}

@Composable
private fun LoginScreen(vm: MainViewModel) {
    var email by rememberSaveable { mutableStateOf("admin") }
    var password by rememberSaveable { mutableStateOf("change-me-now") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    AppBackground {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlassCard(Modifier.fillMaxWidth().widthIn(max = 460.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(44.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("PLN Electricity Services", color = Color.White.copy(alpha = .75f))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Selamat Datang 👋", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Masuk untuk melanjutkan", color = Color.White.copy(alpha = .72f))
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(email, { email = it }, label = { Text("Username / Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Sembunyikan" else "Lihat") } },
                    )
                    vm.error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Color(0xFFFFB4AB)) }
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { vm.login(email, password) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (vm.loading) "Memeriksa..." else "Masuk")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Demo: admin / change-me-now", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DashboardShell(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val nav = listOf(
        "Beranda" to Icons.Default.Dashboard,
        "Pelanggan" to Icons.Default.People,
        "Tagihan" to Icons.Default.ReceiptLong,
        "Bayar" to Icons.Default.Bolt,
        "Profil" to Icons.Default.Person,
    )
    Scaffold(containerColor = Color.Transparent, bottomBar = {
        NavigationBar {
            nav.forEachIndexed { index, (label, icon) -> NavigationBarItem(index == tab, { tab = index }, icon = { Icon(icon, label) }, label = { Text(label) }) }
        }
    }) { padding ->
        AppBackground {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${vm.user?.name ?: "Petugas"} • ${vm.user?.role ?: "BILLER"}", color = Color.White.copy(alpha = .72f))
                    }
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Refresh", tint = Color.White) }
                }
                when (tab) { 0 -> Home(vm); 1 -> Customers(vm); 2 -> BillingList(vm); 3 -> PayScreen(vm); else -> Profile(vm) }
            }
        }
    }
}

@Composable
private fun Home(vm: MainViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { GlassCard(Modifier.fillMaxWidth()) {
            Text("Periode berjalan", color = Color.White.copy(alpha = .75f))
            Text(vm.summary?.period ?: "—", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(vm.summary?.category ?: "Review", color = Color(0xFFFFD54F), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(vm.lastSync, color = Color.White.copy(alpha = .6f), style = MaterialTheme.typography.bodySmall)
        }}
        item { LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.height(190.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Metric("Preventif", vm.summary?.preventif ?: 0, Icons.Default.Schedule) }
            item { Metric("Korektif", vm.summary?.korektif ?: 0, Icons.Default.Warning) }
            item { Metric("Irisan", vm.summary?.irisan ?: 0, Icons.Default.Layers) }
        }}
        item { GlassCard(Modifier.fillMaxWidth()) {
            Text("Prioritas Tagihan", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            vm.billings.take(5).forEach { b ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(b.customer.name ?: b.customer.customerNo, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(b.customer.customerNo, color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(formatRupiah(b.total), color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                }
            }
        }}
    }
}

@Composable
private fun Metric(label: String, value: Int, icon: ImageVector) {
    GlassCard(Modifier.fillMaxSize()) {
        Icon(icon, label, tint = Color(0xFFFFD54F))
        Spacer(Modifier.height(8.dp))
        Text(value.toString(), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = .7f))
    }
}

@Composable
private fun Customers(vm: MainViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(query, { query = it }, label = { Text("Cari ID pelanggan / nama / alamat") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.search(query) }, modifier = Modifier.fillMaxWidth()) { Text("Cari Pelanggan") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(vm.customers) { c -> GlassCard(Modifier.fillMaxWidth()) {
                Text(c.name ?: "Tanpa nama", color = Color.White, fontWeight = FontWeight.Bold)
                Text(c.customerNo, color = Color.White.copy(alpha = .72f))
                Text(c.address ?: "Alamat belum tersedia", color = Color.White.copy(alpha = .62f))
                Text("${c.ulp?.name ?: "ULP —"} • ${c.meterNo ?: "Meter —"}", color = Color(0xFF9BD7FF), style = MaterialTheme.typography.bodySmall)
            }}
        }
    }
}

@Composable
private fun BillingList(vm: MainViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(vm.billings) { b -> GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(b.customer.name ?: b.customer.customerNo, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${b.customer.customerNo} • ${b.period}", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                }
                Text(formatRupiah(b.total), color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text("${b.category} • Jatuh tempo ${b.dueDate.take(10)}", color = Color.White.copy(alpha = .7f))
        }}
    }
}

@Composable
private fun PayScreen(vm: MainViewModel) {
    var no by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Cek Tagihan PLN", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(no, { no = it }, label = { Text("ID Pelanggan") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.checkInquiry(no) }, modifier = Modifier.fillMaxWidth(), enabled = no.length >= 6) { Text("Inquiry Demo") }
        }
        vm.inquiry?.let { r ->
            Spacer(Modifier.height(12.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Text(r.customer.name ?: "Pelanggan", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(r.customer.customerNo, color = Color.White.copy(alpha = .7f))
                Spacer(Modifier.height(10.dp))
                Text("Total Tagihan", color = Color.White.copy(alpha = .7f))
                Text(formatRupiah(r.billing.amount), color = Color(0xFFFFD54F), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Pembayaran Demo") }
            }
        }
    }
}

@Composable
private fun Profile(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(vm.user?.name ?: "Petugas", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(vm.user?.email ?: "", color = Color.White.copy(alpha = .7f))
            Text(vm.user?.role ?: "BILLER", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Mode Review", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Database pelanggan berasal dari master Excel yang disinkronkan ke Supabase review.", color = Color.White.copy(alpha = .7f))
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Status API", color = Color.White, fontWeight = FontWeight.Bold)
            Text(if (vm.apiOnline) "Online" else "Offline • menggunakan data demo", color = Color.White.copy(alpha = .7f))
        }
        Button(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) { Text("Keluar") }
    }
}

private fun formatRupiah(value: Double): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
    maximumFractionDigits = 0
    minimumFractionDigits = 0
}.format(value)
