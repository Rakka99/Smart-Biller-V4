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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
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

private data class DemoCustomer(
    val no: String,
    val name: String,
    val address: String,
    val bill: Double,
    val lat: Double,
    val lng: Double,
)

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
    var token by mutableStateOf<String?>(null)
        private set
    var user by mutableStateOf<User?>(null)
        private set
    var summary by mutableStateOf<BillingSummary?>(null)
        private set
    var billings by mutableStateOf<List<Billing>>(emptyList())
        private set
    var customers by mutableStateOf<List<Customer>>(emptyList())
        private set
    var leaders by mutableStateOf<List<LeaderRow>>(emptyList())
        private set
    var inquiry by mutableStateOf<InquiryResponse?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var apiOnline by mutableStateOf(false)
        private set
    var lastSync by mutableStateOf("Belum tersinkronisasi")
        private set

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
                val normalized = email.trim().lowercase()
                val valid = normalized in setOf(
                    "admin", "supervisor", "biller",
                    "admin@example.com", "supervisor@example.com", "biller@example.com",
                )
                if (valid && password == "change-me-now") {
                    val role = when (normalized) {
                        "supervisor", "supervisor@example.com" -> "SUPERVISOR"
                        "biller", "biller@example.com" -> "BILLER"
                        else -> "ADMIN"
                    }
                    val mail = if (normalized.contains('@')) normalized else "$normalized@example.com"
                    val localUser = User(
                        "demo-$role",
                        mail,
                        when (role) {
                            "ADMIN" -> "Administrator Demo"
                            "SUPERVISOR" -> "Supervisor Demo"
                            else -> "Biller Demo"
                        },
                        role,
                    )
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
        val period = "2026-08"
        summary = BillingSummary(period, "PREVENTIF", demoCustomers.size, 1, 1)
        billings = demoCustomers.mapIndexed { index, customer ->
            Billing(
                "demo-bill-$index",
                period,
                when {
                    index == 2 -> "LEWAT_TEMPO"
                    index == 1 -> "SUDAH_BAYAR"
                    else -> "PREVENTIF"
                },
                if (index == 1) "PAID" else "UNPAID",
                customer.bill,
                "2026-08-20T23:59:59.000Z",
                Customer(
                    customer.no,
                    customer.no,
                    customer.name,
                    "3200${customer.no.takeLast(6)}",
                    customer.address,
                    ULP("ULP Sumedang"),
                    customer.lat,
                    customer.lng,
                ),
            )
        }
        customers = demoCustomers.map { customer ->
            Customer(
                customer.no,
                customer.no,
                customer.name,
                "3200${customer.no.takeLast(6)}",
                customer.address,
                ULP("ULP Sumedang"),
                customer.lat,
                customer.lng,
            )
        }
        leaders = listOf(LeaderRow("biller@example.com", "Biller Demo", "ULP Sumedang", "Jawa Barat", demoCustomers.size))
        lastSync = "Mode Review • data demo"
    }

    fun search(query: String) {
        viewModelScope.launch {
            try {
                customers = api.search(query).items
            } catch (_: Exception) {
                customers = demoCustomers
                    .filter { query.isBlank() || listOf(it.no, it.name, it.address).any { value -> value.contains(query, true) } }
                    .map { customer ->
                        Customer(
                            customer.no,
                            customer.no,
                            customer.name,
                            "3200${customer.no.takeLast(6)}",
                            customer.address,
                            ULP("ULP Sumedang"),
                            customer.lat,
                            customer.lng,
                        )
                    }
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
                val customer = demoCustomers.firstOrNull { it.no == customerNo }
                if (customer != null) {
                    inquiry = InquiryResponse(
                        Customer(
                            customer.no,
                            customer.no,
                            customer.name,
                            "320012345678",
                            customer.address,
                            ULP("ULP Sumedang"),
                            customer.lat,
                            customer.lng,
                        ),
                        InquiryBilling("AGU 2026", customer.bill, customer.bill, 2500.0),
                        Inquiry("INQ-DEMO", "SUCCESS"),
                    )
                } else {
                    error = "ID Pelanggan demo tidak ditemukan."
                }
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

enum class AppTab { HOME, CUSTOMERS, BILLINGS, PAY, PROFILE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val store = remember { SessionStore(applicationContext) }
            val holder = remember { SessionTokenHolder() }
            val api = remember { ApiProvider().create { holder.token } }
            val vm = remember { MainViewModel(store, api, holder) }
            SmartBillerTheme { SmartBillerApp(vm) }
        }
    }
}

@Composable
private fun SmartBillerApp(vm: MainViewModel) {
    var splashVisible by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1200)
        splashVisible = false
    }
    if (splashVisible) {
        SplashScreen()
    } else if (vm.token == null) {
        LoginScreen(vm)
    } else {
        DashboardShell(vm)
    }
}

@Composable
private fun SplashScreen() {
    AppBackground {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(Color.White.copy(alpha = 0.12f), MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Bolt, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(58.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("PLN Electricity Services", color = Color.White.copy(alpha = 0.72f))
            Spacer(Modifier.height(18.dp))
            Text("Monitoring • Edukasi • Pelayanan", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LoginScreen(vm: MainViewModel) {
    var username by rememberSaveable { mutableStateOf("admin") }
    var password by rememberSaveable { mutableStateOf("change-me-now") }
    var visible by rememberSaveable { mutableStateOf(false) }
    AppBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("PLN Electricity Services", color = Color.White.copy(alpha = 0.75f))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("Selamat Datang 👋", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Masuk untuk melanjutkan", color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(username, { username = it }, label = { Text("Username / Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Sembunyikan" else "Lihat") } },
                )
                vm.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color(0xFFFFB4AB))
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = { vm.login(username, password) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.loading) "Memeriksa..." else "Masuk")
                }
                Spacer(Modifier.height(8.dp))
                Text("Demo: admin / change-me-now", color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DashboardShell(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf(
        AppTab.HOME to ("Beranda" to Icons.Default.Dashboard),
        AppTab.CUSTOMERS to ("Pelanggan" to Icons.Default.People),
        AppTab.BILLINGS to ("Tagihan" to Icons.Default.ReceiptLong),
        AppTab.PAY to ("Bayar" to Icons.Default.Bolt),
        AppTab.PROFILE to ("Profil" to Icons.Default.Person),
    )

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.second.second, item.second.first) },
                        label = { Text(item.second.first) },
                    )
                }
            }
        },
    ) { padding ->
        AppBackground {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${vm.user?.name ?: "Petugas"} • ${vm.user?.role ?: "BILLER"}", color = Color.White.copy(alpha = 0.72f))
                    }
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Refresh", tint = Color.White) }
                }
                when (items[tab].first) {
                    AppTab.HOME -> HomeScreen(vm)
                    AppTab.CUSTOMERS -> CustomerScreen(vm)
                    AppTab.BILLINGS -> BillingScreen(vm)
                    AppTab.PAY -> PayScreen(vm)
                    AppTab.PROFILE -> ProfileScreen(vm)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(vm: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Halo, ${vm.user?.name ?: "Petugas"}", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${vm.user?.role ?: "BILLER"} • ULP Sumedang", color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(8.dp))
                Text("Periode ${vm.summary?.period ?: "2026-08"}", color = Color.White.copy(alpha = 0.7f))
                Text(vm.summary?.category ?: "PREVENTIF", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                Text(vm.lastSync, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
            }
        }
        item { MetricRow(vm) }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Prioritas Tagihan", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                vm.billings.take(5).forEach { bill ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(bill.customer.name ?: bill.customer.customerNo, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(bill.customer.customerNo, color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(formatRupiah(bill.total), color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(vm: MainViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Metric("Pelanggan", vm.customers.size, Icons.Default.People, Modifier.weight(1f))
        Metric("Bayar", vm.billings.count { it.status == "PAID" }, Icons.Default.CheckCircle, Modifier.weight(1f))
        Metric("Tunggak", vm.billings.count { it.status == "UNPAID" }, Icons.Default.ReceiptLong, Modifier.weight(1f))
    }
}

@Composable
private fun Metric(label: String, value: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    GlassCard(modifier) {
        Icon(icon, label, tint = Color(0xFFFFD54F), modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(value.toString(), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CustomerScreen(vm: MainViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("ID pelanggan / nama / alamat") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.search(query) }, modifier = Modifier.fillMaxWidth()) { Text("Cari Pelanggan") }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(vm.customers) { customer ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(customer.name ?: "Tanpa nama", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(customer.customerNo, color = Color.White.copy(alpha = 0.7f))
                    Text(customer.address ?: "Alamat belum tersedia", color = Color.White.copy(alpha = 0.62f))
                    Text("${customer.ulp?.name ?: "ULP Sumedang"} • ${customer.meterNo ?: "Meter —"}", color = Color(0xFF9BD7FF), style = MaterialTheme.typography.bodySmall)
                    val lat = customer.latitude
                    val lng = customer.longitude
                    if (lat != null && lng != null) {
                        TextButton(onClick = { openGoogleMaps(lat, lng) }) { Text("Buka Peta") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BillingScreen(vm: MainViewModel) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        items(vm.billings) { bill ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(bill.customer.name ?: bill.customer.customerNo, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${bill.customer.customerNo} • ${bill.period}", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(formatRupiah(bill.total), color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(5.dp))
                Text("${bill.category} • ${bill.status} • jatuh tempo ${bill.dueDate.take(10)}", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun PayScreen(vm: MainViewModel) {
    var customerNo by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Inquiry Tagihan", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(customerNo, { customerNo = it }, label = { Text("ID Pelanggan") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.checkInquiry(customerNo) }, enabled = customerNo.length >= 6, modifier = Modifier.fillMaxWidth()) { Text("Cek Tagihan") }
                vm.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Color(0xFFFFB4AB)) }
            }
        }
        vm.inquiry?.let { response ->
            item {
                Spacer(Modifier.height(10.dp))
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(response.customer.name ?: "Pelanggan", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(response.customer.customerNo, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.height(8.dp))
                    Text("Periode ${response.billing.periode}", color = Color.White.copy(alpha = 0.7f))
                    Text(formatRupiah(response.billing.amount), color = Color(0xFFFFD54F), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Admin ${formatRupiah(response.billing.admin)}", color = Color.White.copy(alpha = 0.65f))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Pembayaran Demo") }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(vm: MainViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Person, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(8.dp))
                Text(vm.user?.name ?: "Petugas", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(vm.user?.email ?: "", color = Color.White.copy(alpha = 0.68f))
                Text(vm.user?.role ?: "BILLER", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Status API", color = Color.White, fontWeight = FontWeight.Bold)
                Text(if (vm.apiOnline) "Online • Supabase" else "Offline • data review", color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(6.dp))
                Text("${vm.customers.size} pelanggan tersedia", color = Color.White.copy(alpha = 0.7f))
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, null, tint = Color(0xFFFFD54F))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Pengaturan", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Tema, sinkronisasi dan keamanan", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Button(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("Keluar") } }
    }
}

private fun formatRupiah(value: Double): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
    maximumFractionDigits = 0
    minimumFractionDigits = 0
}.format(value)

private fun openGoogleMaps(lat: Double, lng: Double) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng"))
    AppContextHolder.context?.let { context ->
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
