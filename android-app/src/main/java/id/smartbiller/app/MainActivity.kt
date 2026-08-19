package id.smartbiller.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.smartbiller.app.data.ApiProvider
import id.smartbiller.app.data.Billing
import id.smartbiller.app.data.BillingSummary
import id.smartbiller.app.data.Customer
import id.smartbiller.app.data.InquiryRequest
import id.smartbiller.app.data.InquiryResponse
import id.smartbiller.app.data.LeaderRow
import id.smartbiller.app.data.LoginResponse
import id.smartbiller.app.data.SessionStore
import id.smartbiller.app.data.SmartBillerApi
import id.smartbiller.app.data.User
import id.smartbiller.app.ui.theme.SmartBillerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.text.NumberFormat
import java.util.Locale

private enum class Screen { HOME, CUSTOMERS, BILLING, MAP, PROFILE, DETAIL, PAYMENT, REPORTS, SETTINGS }

private data class DemoCustomer(
    val no: String,
    val name: String,
    val address: String,
    val tariff: String,
    val power: String,
    val bill: Double,
    val due: String,
)

private val demoCustomers = listOf(
    DemoCustomer("S35111194993", "H. Asep Seepudin", "Jl. R. Ayu Situraja No. 123, Desa Mekarsari, Sumedang", "R1M", "2200 VA", 412800.0, "20 Aug 2026"),
    DemoCustomer("S351133729697", "Ibu Rina Lestari", "Jl. Sumedang-Tanjungsari KM 8", "R1M", "1300 VA", 256400.0, "18 Aug 2026"),
    DemoCustomer("S35113333398", "Dede Ahmad", "Desa Haurngombong, Sumedang", "R1M", "2200 VA", 389500.0, "05 Aug 2026"),
    DemoCustomer("S35111198564", "Siti Nurhaliza", "Jl. Raya Situraja No. 88", "R1M", "900 VA", 218900.0, "20 Aug 2026"),
)

class SessionTokenHolder { var token: String? = null }

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
    var lastSync by mutableStateOf("Belum disinkronkan"); private set
    var apiOnline by mutableStateOf(false); private set

    init {
        viewModelScope.launch {
            token = store.token.first()
            holder.token = token
            user = store.user.first()
            if (token != null) refresh()
        }
    }

    fun login(identifier: String, password: String) {
        viewModelScope.launch {
            loading = true
            error = null
            val normalized = identifier.trim().lowercase()
            val role = when (normalized) {
                "admin", "admin@example.com" -> "ADMIN"
                "supervisor", "supervisor@example.com" -> "SUPERVISOR"
                "biller", "biller@example.com" -> "BILLER"
                else -> ""
            }

            if (role.isEmpty() || password != "change-me-now") {
                error = "Kredensial demo tidak valid. Gunakan admin / supervisor / biller dan password change-me-now."
                loading = false
                return@launch
            }

            try {
                val response = api.login(mapOf("email" to normalized, "password" to password))
                finishLogin(response)
                apiOnline = true
                refresh()
            } catch (e: Exception) {
                // Supabase review API accepts the same review-* token format.
                // Keep demo review usable even while the device is offline or the endpoint is restarting.
                val email = when (role) {
                    "ADMIN" -> "admin@example.com"
                    "SUPERVISOR" -> "supervisor@example.com"
                    else -> "biller@example.com"
                }
                finishLogin(
                    LoginResponse(
                        token = "review-$role-${System.currentTimeMillis()}",
                        user = User(email, email, when (role) {
                            "ADMIN" -> "Administrator Demo"
                            "SUPERVISOR" -> "Supervisor Demo"
                            else -> "Rahmat K"
                        }, role),
                    ),
                )
                apiOnline = false
                loadDemoData()
            } finally {
                loading = false
            }
        }
    }

    private suspend fun finishLogin(response: LoginResponse) {
        store.save(response)
        token = response.token
        holder.token = response.token
        user = response.user
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
                lastSync = "Disinkronkan barusan"
            } catch (_: Exception) {
                if (token?.startsWith("review-") == true) loadDemoData()
                error = null
            } finally {
                loading = false
            }
        }
    }

    private fun loadDemoData() {
        val now = "2026-08"
        summary = BillingSummary(now, "PREVENTIF", demoCustomers.size, 1, 1)
        lastSync = "Mode Review • data demo"
    }

    fun search(query: String) {
        viewModelScope.launch {
            try {
                customers = api.search(query).items
            } catch (_: Exception) {
                customers = demoCustomers.filter {
                    query.isBlank() || listOf(it.no, it.name, it.address).any { value -> value.contains(query, true) }
                }.map {
                    Customer(it.no, it.no, it.name, "3200${it.no.takeLast(6)}", it.address, null, null, null, null)
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
                val c = demoCustomers.firstOrNull { it.no == customerNo }
                if (c != null) {
                    inquiry = InquiryResponse(
                        customer = Customer(c.no, c.no, c.name, "320012345678", c.address, null, null, null, null),
                        billing = id.smartbiller.app.data.InquiryBilling("AGU 2026", c.bill, c.bill, 2500.0),
                        inquiry = id.smartbiller.app.data.Inquiry("INQ-DEMO", "SUCCESS"),
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
            SmartBillerTheme {
                SmartBillerRoot()
            }
        }
    }

    @Composable
    private fun SmartBillerRoot() {
        val store = remember { SessionStore(applicationContext) }
        val holder = remember { SessionTokenHolder() }
        val vm = remember { MainViewModel(store, ApiProvider().create { holder.token }, holder) }
        var splash by rememberSaveable { mutableStateOf(true) }
        var screen by rememberSaveable { mutableStateOf(Screen.HOME) }

        LaunchedEffect(Unit) {
            delay(1100)
            splash = false
        }

        when {
            splash -> SplashScreen()
            vm.token == null -> LoginScreen(vm)
            else -> AppShell(vm, screen, onScreenChange = { screen = it })
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF031B32), Color(0xFF0B60AC), Color(0xFF0D8DDA)))
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(118.dp).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(32.dp)).border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(76.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Text("PLN Electricity Services", color = Color.White.copy(alpha = 0.78f))
            Spacer(Modifier.height(18.dp))
            Text("Bekerja Dengan Hati • Melayani Dengan Integritas", color = Color.White.copy(alpha = 0.64f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LoginScreen(vm: MainViewModel) {
    var identifier by rememberSaveable { mutableStateOf("admin") }
    var password by rememberSaveable { mutableStateOf("change-me-now") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF031A30), Color(0xFF0A57A0), Color(0xFF1B8EE5)))
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.size(88.dp).background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(58.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Text("PLN Electricity Services", color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(22.dp))

            GlassPanel {
                Text("Selamat Datang 👋", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Masuk untuk melanjutkan", color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(identifier, { identifier = it }, label = { Text("Username / Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = showPassword, onCheckedChange = { showPassword = it })
                        Text("Tampilkan", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { identifier = "admin"; password = "change-me-now" }) { Text("Reset Demo") }
                }
                vm.error?.let {
                    Text(it, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                Button(onClick = { vm.login(identifier, password) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Text(if (vm.loading) "Memeriksa…" else "Masuk")
                }
                Spacer(Modifier.height(8.dp))
                Text("Demo: admin / supervisor / biller", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                Text("Password: change-me-now", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AppShell(vm: MainViewModel, screen: Screen, onScreenChange: (Screen) -> Unit) {
    val bottom = listOf(Screen.HOME to ("Beranda" to Icons.Default.Dashboard), Screen.CUSTOMERS to ("Pelanggan" to Icons.Default.People), Screen.BILLING to ("Tagihan" to Icons.Default.ReceiptLong), Screen.MAP to ("Peta" to Icons.Default.Map), Screen.PROFILE to ("Profil" to Icons.Default.Person))

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (screen in bottom.map { it.first }) NavigationBar {
                bottom.forEach { (target, pair) ->
                    NavigationBarItem(selected = screen == target, onClick = { onScreenChange(target) }, icon = { Icon(pair.second, pair.first) }, label = { Text(pair.first) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Brush.linearGradient(listOf(Color(0xFF041E37), Color(0xFF0A65B4), Color(0xFF0F91DC))))) {
            when (screen) {
                Screen.HOME -> DashboardScreen(vm, onScreenChange)
                Screen.CUSTOMERS -> CustomerScreen(vm, onScreenChange)
                Screen.BILLING -> BillingScreen(vm, onScreenChange)
                Screen.MAP -> MapScreen(vm, onScreenChange)
                Screen.PROFILE -> ProfileScreen(vm, onScreenChange)
                Screen.DETAIL -> CustomerDetailScreen(vm, onScreenChange)
                Screen.PAYMENT -> PaymentScreen(vm, onScreenChange)
                Screen.REPORTS -> ReportsScreen(vm, onScreenChange)
                Screen.SETTINGS -> SettingsScreen(onBack = { onScreenChange(Screen.PROFILE) })
            }
        }
    }
}

@Composable
private fun TopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Kembali", tint = Color.White) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall) }
        }
        trailing?.invoke()
    }
}

@Composable
private fun DashboardScreen(vm: MainViewModel, onScreenChange: (Screen) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TopBar("Smart Biller", "${vm.user?.name ?: "Rahmat K"} • ${vm.user?.role ?: "BILLER"}") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "Refresh", tint = Color.White) }
                    IconButton(onClick = { onScreenChange(Screen.SETTINGS) }) { Icon(Icons.Default.Settings, "Pengaturan", tint = Color.White) }
                }
            }
        }
        item {
            GlassPanel(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Halo, ${vm.user?.name ?: "Rahmat K"}", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Semangat memberikan pelayanan terbaik", color = Color.White.copy(alpha = 0.78f))
                    }
                    Icon(Icons.Default.AccountCircle, null, tint = Color.White, modifier = Modifier.size(58.dp))
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Total Pelanggan", (vm.summary?.preventif ?: demoCustomers.size).toString(), Icons.Default.Group, Modifier.weight(1f))
                StatCard("Sudah Bayar", "90", Icons.Default.CheckCircle, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Belum Bayar", "32", Icons.Default.Warning, Modifier.weight(1f))
                StatCard("Lewat Tempo", "10", Icons.Default.Schedule, Modifier.weight(1f))
            }
        }
        item {
            GlassPanel(Modifier.padding(horizontal = 16.dp)) {
                Text("Aktivitas Hari Ini", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ActivityRow("Booking Dibuat", "08:30 – 16:00", "15")
                ActivityRow("Pembacaan Meter", "08:30 – 16:30", "12")
                ActivityRow("Edukasi Pelanggan", "Sepanjang hari", "6")
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Cek Tagihan", Icons.Default.Search) { onScreenChange(Screen.PAYMENT) }
                ActionTile("Laporan", Icons.Default.BarChart) { onScreenChange(Screen.REPORTS) }
                ActionTile("Peta", Icons.Default.LocationOn) { onScreenChange(Screen.MAP) }
            }
        }
        item {
            GlassPanel(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, null, tint = Color(0xFF9BD7FF))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Sinkronisasi Data", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(vm.lastSync, color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (vm.apiOnline) "ONLINE" else "REVIEW", color = if (vm.apiOnline) Color(0xFF71E29A) else Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CustomerScreen(vm: MainViewModel, onScreenChange: (Screen) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        TopBar("Pelanggan", "Master pelanggan demo / Excel")
        GlassPanel(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(q, { q = it }, label = { Text("Cari ID / Nama / Alamat") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.search(q) }, modifier = Modifier.fillMaxWidth()) { Text("Cari Pelanggan") }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val source = if (vm.customers.isEmpty() && q.isBlank()) demoCustomers else vm.customers.map { DemoCustomer(it.customerNo, it.name ?: "Tanpa nama", it.address ?: "Alamat belum tersedia", it.ulp?.name ?: "R1M", "2200 VA", 412800.0, "20 Aug 2026") }
            items(source) { c ->
                GlassPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, null, tint = Color.White, modifier = Modifier.size(46.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.no, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(c.name, color = Color.White.copy(alpha = 0.85f))
                            Text(c.address, color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onScreenChange(Screen.DETAIL) }) { Text("Detail") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerDetailScreen(vm: MainViewModel, onScreenChange: (Screen) -> Unit) {
    val c = demoCustomers.first()
    Column(Modifier.fillMaxSize()) {
        TopBar("Detail Pelanggan", c.name, onBack = { onScreenChange(Screen.CUSTOMERS) })
        GlassPanel(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, null, tint = Color.White, modifier = Modifier.size(58.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.no, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(c.name, color = Color.White.copy(alpha = 0.78f))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(c.address, color = Color.White.copy(alpha = 0.72f))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pill(c.tariff); Pill(c.power); Pill("Pascabayar") }
            Spacer(Modifier.height(16.dp))
            Text("Tagihan Berjalan", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("AGU 2026", color = Color.White.copy(alpha = 0.7f))
            Text(formatRupiah(c.bill), color = Color(0xFFFFD54F), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text("Jatuh tempo ${c.due}", color = Color(0xFFFF9B8A))
            Spacer(Modifier.height(14.dp))
            Button(onClick = { onScreenChange(Screen.MAP) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(6.dp)); Text("Lihat di Peta") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onScreenChange(Screen.PAYMENT) }, modifier = Modifier.fillMaxWidth()) { Text("Buat Booking / Bayar Demo") }
        }
    }
}

@Composable
private fun MapScreen(vm: MainViewModel, onScreenChange: (Screen) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Peta Pelanggan", "Lokasi demo • Google Maps siap diintegrasikan", onBack = { onScreenChange(Screen.HOME) })
        Box(Modifier.fillMaxWidth().weight(1f).padding(16.dp).background(Color(0xFFBFD9E7), RoundedCornerShape(24.dp))) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFFE9F4FA), Color(0xFF9FC5D6)))))
            val points = listOf(0.18f to 0.25f, 0.65f to 0.20f, 0.46f to 0.45f, 0.75f to 0.58f, 0.28f to 0.72f, 0.57f to 0.80f)
            points.forEachIndexed { index, pair ->
                Icon(Icons.Default.LocationOn, null, tint = if (index == 0) Color(0xFFE53935) else Color(0xFF1E88E5), modifier = Modifier.align(Alignment.TopStart).padding(top = (pair.second * 520).dp, start = (pair.first * 300).dp).size(34.dp))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp).background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(20.dp)).padding(14.dp)) {
                Text("H. Asep Seepudin", color = Color(0xFF0B3A68), fontWeight = FontWeight.Bold)
                Text(demoCustomers.first().no, color = Color(0xFF0B3A68).copy(alpha = 0.7f))
                Text(formatRupiah(demoCustomers.first().bill), color = Color(0xFF0B75D1), fontWeight = FontWeight.Bold)
            }
        }
        Button(onClick = { onScreenChange(Screen.CUSTOMERS) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Lihat Daftar Pelanggan") }
    }
}

@Composable
private fun BillingScreen(vm: MainViewModel, onScreenChange: (Screen) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Tagihan", "Preventif • Korektif • Irisan")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Pill("Semua"); Pill("Belum Bayar"); Pill("Lewat Tempo") } }
            items(demoCustomers) { c ->
                GlassPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.no, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(c.name, color = Color.White.copy(alpha = 0.78f))
                            Text("${c.tariff} • ${c.power}", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Pill(if (c.no == demoCustomers.first().no) "Belum Bayar" else "Sudah Bayar")
                            Text(formatRupiah(c.bill), color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Jatuh tempo ${c.due}", color = if (c.no == demoCustomers.first().no) Color(0xFFFF9B8A) else Color.White.copy(alpha = 0.62f))
                    Button(onClick = { onScreenChange(Screen.PAYMENT) }, modifier = Modifier.fillMaxWidth()) { Text("Lihat / Inquiry") }
                }
            }
        }
    }
}

@Composable
private fun PaymentScreen(vm: MainViewModel, onScreenChange: (Screen) -> Unit) {
    var no by rememberSaveable { mutableStateOf(demoCustomers.first().no) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Cek Tagihan", "Inquiry • Manual • Scan QR", onBack = { onScreenChange(Screen.HOME) })
        GlassPanel(Modifier.padding(16.dp)) {
            OutlinedTextField(no, { no = it }, label = { Text("ID Pelanggan") }, modifier = Modifier.fillMaxWidth(), singleLine = true, trailingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) })
            Spacer(Modifier.height(10.dp))
            Button(onClick = { vm.checkInquiry(no) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) { Text(if (vm.loading) "Memeriksa…" else "Inquiry Demo") }
        }
        val c = demoCustomers.firstOrNull { it.no == no } ?: demoCustomers.first()
        GlassPanel(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, null, tint = Color.White, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Text(c.name, color = Color.White, fontWeight = FontWeight.Bold); Text(c.no, color = Color.White.copy(alpha = 0.65f)) }
                Pill(c.tariff)
            }
            Spacer(Modifier.height(12.dp))
            Text("Total Tagihan", color = Color.White.copy(alpha = 0.68f))
            Text(formatRupiah(c.bill), color = Color(0xFFFFD54F), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text("Periode AGU 2026 • Administrasi Rp2.500", color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onScreenChange(Screen.PAYMENT) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Paid, null); Spacer(Modifier.width(6.dp)); Text("Pembayaran (Simulasi)") }
        }
    }
}

@Composable
private fun ReportsScreen(vm: MainViewModel, onScreenChange: (Screen) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Laporan", "Harian • Mingguan • Bulanan", onBack = { onScreenChange(Screen.PROFILE) })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                GlassPanel {
                    Text("18 Agu 2026", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MiniReport("Pelanggan", "101")
                        MiniReport("Sudah Bayar", "67")
                        MiniReport("Belum Bayar", "24")
                        MiniReport("Lewat Tempo", "10")
                    }
                }
            }
            item {
                GlassPanel {
                    Text("Grafik Pembayaran", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        listOf(45, 72, 50, 86, 38, 64, 78).forEachIndexed { index, height ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                Box(Modifier.width(26.dp).height(height.dp).background(if (index == 4) Color(0xFFFFD54F) else Color(0xFF3CC2F5), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)))
                                Text(listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")[index], color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item { Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Export PDF") } }
        }
    }
}

@Composable
private fun ProfileScreen(vm: MainViewModel, onScreenChange: (Screen) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Profil", "Biller • ULP Sumedang", trailing = { IconButton(onClick = { onScreenChange(Screen.SETTINGS) }) { Icon(Icons.Default.Settings, null, tint = Color.White) } })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                GlassPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, null, tint = Color.White, modifier = Modifier.size(66.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(vm.user?.name ?: "Rahmat K", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(vm.user?.email ?: "biller@example.com", color = Color.White.copy(alpha = 0.65f))
                            Text(vm.user?.role ?: "BILLER", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.Edit, null, tint = Color.White)
                    }
                }
            }
            item { ProfileAction("Edit Profil", Icons.Default.Edit) {} }
            item { ProfileAction("Sinkronisasi Data", Icons.Default.Sync) { vm.refresh() } }
            item { ProfileAction("Laporan & Ranking", Icons.Default.BarChart) { onScreenChange(Screen.REPORTS) } }
            item { ProfileAction("Pengaturan", Icons.Default.Settings) { onScreenChange(Screen.SETTINGS) } }
            item { ProfileAction("Tentang Aplikasi", Icons.Default.Info) {} }
            item {
                Button(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE05245))) {
                    Icon(Icons.Default.Logout, null); Spacer(Modifier.width(6.dp)); Text("Keluar")
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    var notifications by rememberSaveable { mutableStateOf(true) }
    var offline by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Pengaturan", "Smart Biller", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SettingRow("Notifikasi", "Firebase / lokal", notifications, { notifications = it }) }
            item { SettingRow("Mode Offline", "Room Database + Sync", offline, { offline = it }) }
            item { ClickSetting("Sinkronisasi sekarang", "Tarik master pelanggan terbaru", Icons.Default.Sync) {} }
            item { ClickSetting("Keamanan", "PIN aplikasi dan sesi login", Icons.Default.Lock) {} }
            item { ClickSetting("Versi Aplikasi", "Smart Biller 1.0.0 • Kotlin + Jetpack Compose", Icons.Default.Info) {} }
            item {
                GlassPanel {
                    Text("Tema Visual", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Glassmorphism • Material 3 • Responsive Smartphone & Tablet", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.17f), Color.White.copy(alpha = 0.06f))), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    GlassPanel(modifier) {
        Icon(icon, null, tint = Color(0xFF9BD7FF))
        Spacer(Modifier.height(6.dp))
        Text(value, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(title, color = Color.White.copy(alpha = 0.66f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ActivityRow(title: String, time: String, count: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(Color(0xFF57D4F9), CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(time, color = Color.White.copy(alpha = 0.58f), style = MaterialTheme.typography.bodySmall) }
        Text(count, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f))) { Icon(icon, null); Spacer(Modifier.width(4.dp)); Text(label) }
}

@Composable
private fun Pill(text: String) {
    Text(text, color = Color(0xFF084A72), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0xFFBDEAFF), RoundedCornerShape(50),).padding(horizontal = 10.dp, vertical = 6.dp))
}

@Composable
private fun ProfileAction(title: String, icon: ImageVector, onClick: () -> Unit) {
    GlassPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFFB8E8FF))
            Spacer(Modifier.width(12.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClick) { Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.7f)) }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    GlassPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Color.White.copy(alpha = 0.58f), style = MaterialTheme.typography.bodySmall) }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ClickSetting(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    GlassPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFFB8E8FF))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Color.White.copy(alpha = 0.58f), style = MaterialTheme.typography.bodySmall) }
            IconButton(onClick = onClick) { Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.7f)) }
        }
    }
}

@Composable
private fun MiniReport(label: String, value: String) {
    Column(Modifier.weight(1f).background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(10.dp)) {
        Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Color.White.copy(alpha = 0.64f), style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatRupiah(value: Double): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
    maximumFractionDigits = 0
    minimumFractionDigits = 0
}.format(value)
