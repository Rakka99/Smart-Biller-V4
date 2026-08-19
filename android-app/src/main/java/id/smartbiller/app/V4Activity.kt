package id.smartbiller.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import id.smartbiller.app.data.ApiProvider
import id.smartbiller.app.data.Billing
import id.smartbiller.app.data.Customer
import id.smartbiller.app.data.CustomerSeed
import id.smartbiller.app.data.Inquiry
import id.smartbiller.app.data.InquiryBilling
import id.smartbiller.app.data.InquiryRequest
import id.smartbiller.app.data.InquiryResponse
import id.smartbiller.app.data.User
import id.smartbiller.app.ui.theme.SmartBillerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import kotlin.random.Random

private val Blue900 = Color(0xFF061B35)
private val Blue700 = Color(0xFF0A4B8F)
private val Cyan = Color(0xFF18C9F8)
private val Accent = Color(0xFFFFD21F)
private val Green = Color(0xFF53E26B)
private val Red = Color(0xFFFF6B6B)
private val Glass = Color.White.copy(alpha = 0.10f)
private val GlassStrong = Color.White.copy(alpha = 0.16f)

class V4Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmartBillerTheme { SmartBillerV4App() } }
    }
}

private enum class Tab { HOME, CUSTOMER, BILLING, MAP, PROFILE }
private enum class SubPage { NONE, SETTINGS, REPORT, PDIL, EVIDENCE, PAYMENT, TOKEN, ABOUT }
private enum class BillFilter { ALL, UNPAID, OVERDUE }

@Composable
private fun SmartBillerV4App() {
    var splash by rememberSaveable { mutableStateOf(true) }
    var session by rememberSaveable { mutableStateOf(false) }
    val vm = remember { V4ViewModel() }
    LaunchedEffect(Unit) { delay(1100); splash = false }
    when {
        splash -> SplashScreen()
        !session -> LoginScreen(vm) { session = true }
        else -> Shell(vm)
    }
}

private class V4ViewModel {
    private val local = CustomerSeed.all
    private var token: String? = "review-token"
    private val api by lazy { ApiProvider().create { token } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var user by mutableStateOf(User("demo-admin", "admin@example.com", "Rahmat K", "BILLER"))
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var customers by mutableStateOf(local)
    var bills by mutableStateOf(buildBills(local))
    var filter by mutableStateOf(BillFilter.ALL)
    var subPage by mutableStateOf(SubPage.NONE)
    var inquiry by mutableStateOf<InquiryResponse?>(null)
    var selectedBill by mutableStateOf<Billing?>(null)
    var paymentRef by mutableStateOf<String?>(null)
    var tokenValue by mutableStateOf<String?>(null)
    var syncText by mutableStateOf("Master Excel 1.328 pelanggan • mode review")
    var notificationEnabled by mutableStateOf(true)
    var autoSyncEnabled by mutableStateOf(true)
    var offlineEnabled by mutableStateOf(true)
    var darkEnabled by mutableStateOf(true)

    fun login(username: String, password: String, onSuccess: () -> Unit) {
        loading = true
        error = null
        scope.launch {
            val remote = runCatching { api.login(mapOf("username" to username, "password" to password)) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (remote != null) {
                    token = remote.token
                    user = remote.user
                    reload()
                    onSuccess()
                } else if ((username.equals("admin", true) || username.equals("admin@example.com", true)) && password == "change-me-now") {
                    user = User("demo-admin", "admin@example.com", "Rahmat K", "ADMIN")
                    token = "review-token"
                    reload()
                    onSuccess()
                } else {
                    error = "Login gagal. Gunakan admin / change-me-now untuk review."
                }
                loading = false
            }
        }
    }

    fun reload() {
        customers = local
        bills = buildBills(local)
        syncText = "Master Excel 1.328 pelanggan • seed lokal ${local.size} • ${if (autoSyncEnabled) "auto-sync ON" else "manual sync"}"
    }

    fun search(q: String) {
        query = q
        if (q.isBlank()) {
            customers = local
            return
        }
        scope.launch {
            val remote = runCatching { api.search(q).items }.getOrNull()
            withContext(Dispatchers.Main) {
                customers = if (!remote.isNullOrEmpty()) remote else local.filter {
                    it.customerNo.contains(q, true) || (it.name ?: "").contains(q, true) || (it.address ?: "").contains(q, true) || it.rbm.contains(q, true)
                }
            }
        }
    }

    fun inquiry(customerNo: String) {
        scope.launch {
            val remote = runCatching { api.inquiry(InquiryRequest(customerNo)) }.getOrNull()
            withContext(Dispatchers.Main) {
                inquiry = remote ?: buildBills(listOfNotNull(local.firstOrNull { it.customerNo == customerNo })).firstOrNull()?.let {
                    InquiryResponse(it.customer, InquiryBilling(it.period, it.sellingPrice, it.total, it.admin), Inquiry("INQ-$customerNo", "READY"))
                }
                if (inquiry == null) error = "Pelanggan tidak ditemukan."
            }
        }
    }

    fun selectBill(bill: Billing) { selectedBill = bill; paymentRef = null; subPage = SubPage.PAYMENT }

    fun simulatePayment() {
        val bill = selectedBill ?: return
        val ref = "SBL-${System.currentTimeMillis().toString().takeLast(8)}"
        bills = bills.map { if (it.id == bill.id) it.copy(status = "PAID") else it }
        selectedBill = bill.copy(status = "PAID")
        paymentRef = ref
    }

    fun generateToken() {
        tokenValue = (1..20).joinToString("") { Random.nextInt(0, 10).toString() }
    }

    fun paid() = bills.count { it.status == "PAID" }
    fun unpaid() = bills.count { it.status == "UNPAID" }
    fun overdue() = bills.count { it.status == "OVERDUE" }
    fun preventive() = bills.count { it.category == "PREVENTIF" }
    fun corrective() = bills.count { it.category == "KOREKTIF" }
    fun intersection() = bills.count { it.category == "IRISAN" }

    private fun buildBills(list: List<Customer>): List<Billing> = list.mapIndexed { index, customer ->
        val n = customer.customerNo.takeLast(2).toIntOrNull() ?: index
        val status = when {
            n % 17 == 0 -> "OVERDUE"
            n % 3 == 0 -> "PAID"
            else -> "UNPAID"
        }
        val category = when {
            status == "OVERDUE" -> "KOREKTIF"
            index % 11 == 0 -> "IRISAN"
            else -> "PREVENTIF"
        }
        val total = 180_000.0 + (customer.daya ?: 900) * 30.0 + (index % 9) * 10_000
        Billing("V4-$index", "AGU26", category, status, total, "20 Agu 2026", customer, total, 2_500.0, total)
    }
}

@Composable
private fun Bg(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Blue700, Blue900, Color(0xFF020914))))) { content() }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Glass), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), content = content)
}

@Composable
private fun SplashScreen() {
    Bg {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(shape = RoundedCornerShape(34.dp), color = GlassStrong) {
                Icon(Icons.Default.Bolt, "Smart Biller", tint = Accent, modifier = Modifier.padding(24.dp).size(72.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text("PLN Electricity Services", color = Color.White.copy(alpha = .72f))
            Spacer(Modifier.height(10.dp))
            Text("Monitoring • Edukasi • Pelayanan", color = Cyan, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LoginScreen(vm: V4ViewModel, onSuccess: () -> Unit) {
    var username by rememberSaveable { mutableStateOf("admin") }
    var password by rememberSaveable { mutableStateOf("change-me-now") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    Bg {
        LazyColumn(contentPadding = PaddingValues(22.dp), modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Surface(shape = RoundedCornerShape(28.dp), color = GlassStrong) { Icon(Icons.Default.Bolt, null, tint = Accent, modifier = Modifier.padding(22.dp).size(54.dp)) }
                    Spacer(Modifier.height(12.dp))
                    Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                    Text("Monitoring • Edukasi • Pelayanan", color = Cyan)
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Selamat Datang 👋", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Masuk untuk mulai bekerja", color = Color.White.copy(alpha = .62f))
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(username, { username = it }, label = { Text("Username / Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } })
                    vm.error?.let { Text(it, color = Red, modifier = Modifier.padding(top = 8.dp)) }
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { vm.login(username, password, onSuccess) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) { Text(if (vm.loading) "Memeriksa..." else "Masuk") }
                    Spacer(Modifier.height(8.dp))
                    Text("Demo review: admin / change-me-now", color = Color.White.copy(alpha = .48f), style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniInfo("Kotlin", "Native")
                    MiniInfo("Compose", "Material 3")
                    MiniInfo("Maps", "Google")
                }
            }
        }
    }
}

@Composable private fun MiniInfo(title: String, subtitle: String) { GlassCard(Modifier.weight(1f)) { Text(title, color = Accent, fontWeight = FontWeight.Bold); Text(subtitle, color = Color.White.copy(alpha = .58f), style = MaterialTheme.typography.bodySmall) } }

@Composable
private fun Shell(vm: V4ViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val context = LocalContext.current
    val nav = listOf(Tab.HOME to "Beranda", Tab.CUSTOMER to "Pelanggan", Tab.BILLING to "Tagihan", Tab.MAP to "Peta", Tab.PROFILE to "Profil")
    Bg {
        Scaffold(containerColor = Color.Transparent, bottomBar = { NavigationBar(containerColor = Blue900.copy(alpha = .92f)) { nav.forEach { (t, label) -> NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(navIcon(t), label) }, label = { Text(label) }) } } }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                TopBar(vm) { vm.reload() }
                when (vm.subPage) {
                    SubPage.NONE -> when (tab) {
                        Tab.HOME -> HomePage(vm) { vm.subPage = it }
                        Tab.CUSTOMER -> CustomerPage(vm)
                        Tab.BILLING -> BillingPage(vm)
                        Tab.MAP -> MapPage(vm)
                        Tab.PROFILE -> ProfilePage(vm) { vm.subPage = it }
                    }
                    SubPage.SETTINGS -> SettingsPage(vm, context)
                    SubPage.REPORT -> ReportPage(vm, context)
                    SubPage.PDIL -> PdilPage(vm)
                    SubPage.EVIDENCE -> EvidencePage(vm)
                    SubPage.PAYMENT -> PaymentPage(vm, context)
                    SubPage.TOKEN -> TokenPage(vm, context)
                    SubPage.ABOUT -> AboutPage(vm)
                }
            }
            vm.inquiry?.let { response -> InquiryDialog(response) { vm.inquiry = null } }
        }
    }
}

@Composable private fun TopBar(vm: V4ViewModel, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text("${vm.user.name} • ${vm.user.role} • UP3 Sumedang", color = Color.White.copy(alpha = .62f), style = MaterialTheme.typography.bodySmall) }
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh", tint = Color.White) }
    }
}

@Composable
private fun HomePage(vm: V4ViewModel, open: (SubPage) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Halo, ${vm.user.name}", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Biller • ULP Sumedang", color = Color.White.copy(alpha = .62f))
                Spacer(Modifier.height(8.dp))
                Text(vm.syncText, color = Green, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatChip("Preventif", vm.preventive(), Cyan); StatChip("Korektif", vm.corrective(), Red); StatChip("Irisan", vm.intersection(), Accent) }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric(Modifier.weight(1f), "Total Pelanggan", CustomerSeed.masterRecordCount); Metric(Modifier.weight(1f), "Sudah Bayar", vm.paid()) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric(Modifier.weight(1f), "Belum Bayar", vm.unpaid()); Metric(Modifier.weight(1f), "Lewat Tempo", vm.overdue()) } }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Aktivitas & Shortcut", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                QuickAction("Laporan & Ranking", "Harian • mingguan • bulanan", Icons.Default.Leaderboard) { open(SubPage.REPORT) }
                QuickAction("PDIL", "Draft • Verifikasi • Sync", Icons.Default.EditNote) { open(SubPage.PDIL) }
                QuickAction("Foto & Evidence", "Meter • lokasi • kunjungan", Icons.Default.CameraAlt) { open(SubPage.EVIDENCE) }
                QuickAction("Pembayaran Simulasi", "Inquiry • bayar • reference", Icons.Default.Payment) { open(SubPage.PAYMENT) }
                QuickAction("Prabayar / Token", "Generate token demo", Icons.Default.Bolt) { open(SubPage.TOKEN) }
                QuickAction("Pengaturan", "Notifikasi • offline • sync", Icons.Default.Settings) { open(SubPage.SETTINGS) }
            }
        }
    }
}

@Composable private fun QuickAction(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = Accent.copy(alpha = .15f)) { Icon(icon, null, tint = Accent, modifier = Modifier.padding(8.dp).size(24.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = onClick) { Text("Buka") }
    }
}

@Composable private fun Metric(modifier: Modifier, label: String, value: Int) { GlassCard(modifier) { Text(label, color = Color.White.copy(alpha = .58f), style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(4.dp)); Text("$value", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) } }
@Composable private fun StatChip(label: String, value: Int, color: Color) = AssistChip(onClick = {}, label = { Text("$label $value") }, colors = AssistChipDefaults.assistChipColors(containerColor = color.copy(alpha = .18f), labelColor = Color.White))
private fun navIcon(tab: Tab): ImageVector = when (tab) { Tab.HOME -> Icons.Default.Dashboard; Tab.CUSTOMER -> Icons.Default.People; Tab.BILLING -> Icons.Default.ReceiptLong; Tab.MAP -> Icons.Default.Map; Tab.PROFILE -> Icons.Default.Person }

@Composable
private fun CustomerPage(vm: V4ViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = true, onClick = { vm.reload() }, label = { Text("Semua") })
            FilterChip(selected = false, onClick = { vm.filter = BillFilter.UNPAID }, label = { Text("Belum Bayar") })
            FilterChip(selected = false, onClick = { vm.filter = BillFilter.OVERDUE }, label = { Text("Lewat Tempo") })
        }
        OutlinedTextField(vm.query, { vm.search(it) }, label = { Text("Cari IDPEL / nama / alamat / RBM") }, modifier = Modifier.fillMaxWidth().padding(16.dp), leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text("${vm.customers.size} hasil • master ${CustomerSeed.masterRecordCount} pelanggan", color = Color.White.copy(alpha = .58f)) }
            items(vm.customers) { customer -> CustomerCard(vm, customer) }
        }
    }
}

@Composable private fun CustomerCard(vm: V4ViewModel, customer: Customer) {
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(customer.name ?: "Pelanggan", color = Color.White, fontWeight = FontWeight.Bold); Text(customer.customerNo, color = Color.White.copy(alpha = .6f), style = MaterialTheme.typography.bodySmall) }
            AssistChip(onClick = {}, label = { Text(customer.tarif ?: "-") })
        }
        Spacer(Modifier.height(6.dp))
        Text(customer.address ?: "Alamat belum tersedia", color = Color.White.copy(alpha = .68f), style = MaterialTheme.typography.bodySmall)
        Text("${customer.daya ?: 0} VA • ${customer.rbm} • ${customer.ulp?.name ?: "ULP Sumedang"}", color = Color.White.copy(alpha = .52f), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { TextButton(onClick = { vm.inquiry(customer.customerNo) }) { Text("Inquiry") }; TextButton(onClick = { openMap(customer, context) }) { Text("Peta") } }
    }
}

@Composable
private fun BillingPage(vm: V4ViewModel) {
    val filtered = vm.bills.filter { when (vm.filter) { BillFilter.ALL -> true; BillFilter.UNPAID -> it.status == "UNPAID"; BillFilter.OVERDUE -> it.status == "OVERDUE" } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = vm.filter == BillFilter.ALL, onClick = { vm.filter = BillFilter.ALL }, label = { Text("Semua") })
            FilterChip(selected = vm.filter == BillFilter.UNPAID, onClick = { vm.filter = BillFilter.UNPAID }, label = { Text("Belum Bayar") })
            FilterChip(selected = vm.filter == BillFilter.OVERDUE, onClick = { vm.filter = BillFilter.OVERDUE }, label = { Text("Lewat Tempo") })
        }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) { items(filtered) { bill -> BillingCard(vm, bill) } }
    }
}

@Composable private fun BillingCard(vm: V4ViewModel, bill: Billing) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(bill.customer.name ?: "Pelanggan", color = Color.White, fontWeight = FontWeight.Bold); Text("${bill.customer.customerNo} • ${bill.period}", color = Color.White.copy(alpha = .58f), style = MaterialTheme.typography.bodySmall) }
            Text(rupiah(bill.total), color = Accent, fontWeight = FontWeight.Black)
        }
        Text("${bill.category} • ${bill.status}", color = if (bill.status == "PAID") Green else if (bill.status == "OVERDUE") Red else Accent, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextButton(onClick = { vm.selectBill(bill) }) { Text("Detail / Bayar") }; TextButton(onClick = { vm.inquiry(bill.customer.customerNo) }) { Text("Inquiry") } }
    }
}

@Composable
private fun MapPage(vm: V4ViewModel) {
    val context = LocalContext.current
    val sumedang = LatLng(-6.8585, 107.9228)
    val cameraState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(sumedang, 13f) }
    val mapUi = remember { MapUiSettings(zoomControlsEnabled = true, compassEnabled = true, mapToolbarEnabled = true) }
    val mapProps = remember { MapProperties(mapType = MapType.NORMAL) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(310.dp).padding(12.dp)) {
            GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraState, uiSettings = mapUi, properties = mapProps) {
                vm.customers.take(50).forEach { customer ->
                    val lat = customer.latitude
                    val lon = customer.longitude
                    if (lat != null && lon != null) Marker(state = MarkerState(position = LatLng(lat, lon)), title = customer.name ?: "Pelanggan", snippet = customer.customerNo)
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text("Peta pelanggan • ${vm.customers.size} hasil", color = Color.White.copy(alpha = .62f)) }
            items(vm.customers.take(20)) { c -> GlassCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, null, tint = Accent); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(c.name ?: "Pelanggan", color = Color.White, fontWeight = FontWeight.SemiBold); Text("${c.latitude ?: "-"}, ${c.longitude ?: "-"}", color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall) }; TextButton(onClick = { openMap(c, context) }) { Text("Navigasi") } } } }
        }
    }
}

@Composable
private fun ProfilePage(vm: V4ViewModel, open: (SubPage) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { GlassCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Surface(shape = CircleShape, color = Accent.copy(alpha = .17f)) { Icon(Icons.Default.Person, null, tint = Accent, modifier = Modifier.padding(12.dp).size(38.dp)) }; Spacer(Modifier.width(12.dp)); Column { Text(vm.user.name, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text("Biller • UP3 Sumedang", color = Color.White.copy(alpha = .62f)); Text(vm.user.email, color = Cyan, style = MaterialTheme.typography.bodySmall) } } } }
        item { ActionRow("Laporan & Ranking", Icons.Default.Leaderboard) { open(SubPage.REPORT) } }
        item { ActionRow("Pengaturan", Icons.Default.Settings) { open(SubPage.SETTINGS) } }
        item { ActionRow("PDIL", Icons.Default.EditNote) { open(SubPage.PDIL) } }
        item { ActionRow("Foto & Evidence", Icons.Default.CameraAlt) { open(SubPage.EVIDENCE) } }
        item { ActionRow("Pembayaran Simulasi", Icons.Default.Payment) { open(SubPage.PAYMENT) } }
        item { ActionRow("PLN Prabayar / Token", Icons.Default.Bolt) { open(SubPage.TOKEN) } }
        item { ActionRow("Tentang Smart Biller", Icons.Default.Info) { open(SubPage.ABOUT) } }
        item { Text("Mode review aman untuk demo. Data master Excel tetap menjadi sumber utama di backend.", color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun ActionRow(title: String, icon: ImageVector, onClick: () -> Unit) = GlassCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Accent); Spacer(Modifier.width(12.dp)); Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); TextButton(onClick = onClick) { Text("Buka") } } }

@Composable
private fun SettingsPage(vm: V4ViewModel, context: Context) {
    SubPageScaffold("Pengaturan", vm) {
        SettingRow("Notifikasi jatuh tempo", Icons.Default.Notifications, vm.notificationEnabled) { vm.notificationEnabled = it }
        SettingRow("Sinkronisasi otomatis", Icons.Default.Sync, vm.autoSyncEnabled) { vm.autoSyncEnabled = it; vm.reload() }
        SettingRow("Mode offline", Icons.Default.CloudOff, vm.offlineEnabled) { vm.offlineEnabled = it }
        SettingRow("Mode gelap / glass", Icons.Default.Visibility, vm.darkEnabled) { vm.darkEnabled = it }
        ActionRow("Sinkronisasi sekarang", Icons.Default.Refresh) { vm.reload() }
        ActionRow("Status API / Supabase", Icons.Default.Sync) { Toast.makeText(context, "Review API aktif", Toast.LENGTH_SHORT).show() }
    }
}

@Composable private fun SettingRow(title: String, icon: ImageVector, value: Boolean, onChange: (Boolean) -> Unit) = GlassCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Accent); Spacer(Modifier.width(12.dp)); Text(title, color = Color.White, modifier = Modifier.weight(1f)); Switch(checked = value, onCheckedChange = onChange) } }

@Composable
private fun ReportPage(vm: V4ViewModel, context: Context) {
    SubPageScaffold("Laporan & Ranking", vm) {
        ReportMetric("Preventif", vm.preventive(), Cyan)
        ReportMetric("Korektif", vm.corrective(), Red)
        ReportMetric("Irisan", vm.intersection(), Accent)
        ReportMetric("Sudah Bayar", vm.paid(), Green)
        ReportMetric("Belum Bayar", vm.unpaid(), Accent)
        ReportMetric("Lewat Tempo", vm.overdue(), Red)
        Button(onClick = { exportReport(context, vm) }, modifier = Modifier.fillMaxWidth()) { Text("Export PDF") }
    }
}

@Composable private fun ReportMetric(label: String, value: Int, color: Color) = GlassCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Text(label, color = Color.White, modifier = Modifier.weight(1f)); Text("$value", color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) } }

@Composable
private fun PdilPage(vm: V4ViewModel) {
    var status by rememberSaveable { mutableStateOf("DRAFT") }
    SubPageScaffold("PDIL", vm) {
        GlassCard(Modifier.fillMaxWidth()) { Text("Workflow PDIL", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("DRAFT → SUBMITTED → VERIFIED → APPROVED → SYNCED", color = Cyan, style = MaterialTheme.typography.bodySmall); Text("Status: $status", color = Accent, modifier = Modifier.padding(top = 10.dp)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { status = "SUBMITTED" }, modifier = Modifier.weight(1f)) { Text("Submit") }; Button(onClick = { status = "VERIFIED" }, modifier = Modifier.weight(1f)) { Text("Verify") } }
        Button(onClick = { status = "APPROVED / SYNCED" }, modifier = Modifier.fillMaxWidth()) { Text("Approve & Sync") }
    }
}

@Composable
private fun EvidencePage(vm: V4ViewModel) {
    var captured by remember { mutableStateOf<Bitmap?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { captured = it }
    val context = LocalContext.current
    SubPageScaffold("Foto & Evidence", vm) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Evidence Kunjungan", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Ambil foto meter, lokasi, atau kunjungan.", color = Color.White.copy(alpha = .62f))
            Spacer(Modifier.height(12.dp))
            Button(onClick = { launcher.launch(null) }, modifier = Modifier.fillMaxWidth()) { Text("Buka Kamera") }
            captured?.let { bitmap ->
                Spacer(Modifier.height(12.dp))
                Image(bitmap.asImageBitmap(), null, modifier = Modifier.fillMaxWidth().height(220.dp))
                TextButton(onClick = { Toast.makeText(context, "Evidence tersimpan untuk review", Toast.LENGTH_SHORT).show() }) { Text("Simpan Evidence") }
            }
        }
    }
}

@Composable
private fun PaymentPage(vm: V4ViewModel, context: Context) {
    val bill = vm.selectedBill
    SubPageScaffold("Pembayaran Simulasi", vm) {
        if (bill == null) {
            Text("Pilih tagihan dari menu Tagihan.", color = Color.White.copy(alpha = .65f))
            Button(onClick = { vm.subPage = SubPage.NONE }, modifier = Modifier.fillMaxWidth()) { Text("Kembali ke Tagihan") }
        } else {
            GlassCard(Modifier.fillMaxWidth()) { Text(bill.customer.name ?: "Pelanggan", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(bill.customer.customerNo, color = Color.White.copy(alpha = .58f)); Text("Periode ${bill.period}", color = Cyan); Text(rupiah(bill.total), color = Accent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) }
            if (vm.paymentRef == null) Button(onClick = { vm.simulatePayment() }, modifier = Modifier.fillMaxWidth()) { Text("Bayar Simulasi") } else {
                GlassCard(Modifier.fillMaxWidth()) { Text("Pembayaran berhasil (DEMO)", color = Green, fontWeight = FontWeight.Black); Text("Reference: ${vm.paymentRef}", color = Color.White); Text("Status: PAID", color = Green) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { shareText(context, "Smart Biller\nIDPEL: ${bill.customer.customerNo}\nReference: ${vm.paymentRef}\nStatus: PAID") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Bagikan") }; Button(onClick = { vm.subPage = SubPage.NONE; vm.paymentRef = null }, modifier = Modifier.weight(1f)) { Text("Selesai") } }
            }
        }
    }
}

@Composable
private fun TokenPage(vm: V4ViewModel, context: Context) {
    var amount by rememberSaveable { mutableStateOf("100000") }
    SubPageScaffold("PLN Prabayar / Token", vm) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Token Demo", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Simulasi pembelian token untuk review UI.", color = Color.White.copy(alpha = .62f))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Nominal") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { vm.generateToken() }, modifier = Modifier.fillMaxWidth()) { Text("Generate Token Demo") }
            vm.tokenValue?.let { code ->
                Text("Token: $code", color = Accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp))
                TextButton(onClick = { shareText(context, "Token Smart Biller Demo: $code\nNominal: Rp $amount") }) { Text("Bagikan Token") }
            }
        }
    }
}

@Composable private fun AboutPage(vm: V4ViewModel) = SubPageScaffold("Tentang Smart Biller", vm) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Smart Biller V4", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text("Aplikasi biller PLN modern untuk monitoring, edukasi, pelayanan, inquiry, peta, laporan dan review pembayaran.", color = Color.White.copy(alpha = .68f), modifier = Modifier.padding(top = 8.dp))
        FeatureLine("Kotlin + Jetpack Compose + Material 3")
        FeatureLine("Glassmorphism • Responsive Smartphone & Tablet")
        FeatureLine("Supabase Edge Function + mode review offline")
        FeatureLine("Google Maps SDK + marker pelanggan")
        FeatureLine("Database master Excel 1.328 pelanggan")
    }
}

@Composable private fun FeatureLine(text: String) { Row(Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("•", color = Accent, fontWeight = FontWeight.Black); Spacer(Modifier.width(8.dp)); Text(text, color = Color.White.copy(alpha = .75f)) } }

@Composable private fun SubPageScaffold(title: String, vm: V4ViewModel, content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TextButton(onClick = { vm.subPage = SubPage.NONE }) { Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(4.dp)); Text("Kembali") } }
        item { Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) }
        item { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
    }
}

@Composable private fun InquiryDialog(response: InquiryResponse, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }, title = { Text("Detail Pelanggan") }, text = { Column { Text(response.customer.name ?: "Pelanggan"); Text(response.customer.customerNo); Text("Periode: ${response.billing.periode}"); Text("Tagihan: ${rupiah(response.billing.amount)}"); Text("Inquiry: ${response.inquiry.id} • ${response.inquiry.status}") } })
}

private fun openMap(customer: Customer, context: Context) {
    val lat = customer.latitude ?: return
    val lon = customer.longitude ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon"))
    runCatching { context.startActivity(intent) }.onFailure { Toast.makeText(context, "Aplikasi peta tidak tersedia", Toast.LENGTH_SHORT).show() }
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(intent, "Bagikan"))
}

private fun exportReport(context: Context, vm: V4ViewModel) {
    val document = PdfDocument()
    val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val paint = Paint().apply { textSize = 18f; color = android.graphics.Color.BLACK }
    page.canvas.drawText("Smart Biller V4 - Laporan", 32f, 48f, paint)
    paint.textSize = 12f
    val rows = listOf("Pelanggan master: ${CustomerSeed.masterRecordCount}", "Preventif: ${vm.preventive()}", "Korektif: ${vm.corrective()}", "Irisan: ${vm.intersection()}", "Sudah Bayar: ${vm.paid()}", "Belum Bayar: ${vm.unpaid()}", "Lewat Tempo: ${vm.overdue()}")
    rows.forEachIndexed { index, row -> page.canvas.drawText(row, 32f, 86f + index * 26f, paint) }
    document.finishPage(page)
    val fileName = "SmartBiller_${System.currentTimeMillis()}.pdf"
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, fileName); put(MediaStore.Downloads.MIME_TYPE, "application/pdf"); put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS) }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Gagal membuat file")
            resolver.openOutputStream(uri)?.use { document.writeTo(it) }
        } else {
            val file = java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            file.outputStream().use { document.writeTo(it) }
        }
        Toast.makeText(context, "PDF berhasil dibuat", Toast.LENGTH_LONG).show()
    }.onFailure { Toast.makeText(context, "Export gagal: ${it.message}", Toast.LENGTH_LONG).show() }
    document.close()
}

private fun rupiah(value: Double): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(value).replace(",00", "")
