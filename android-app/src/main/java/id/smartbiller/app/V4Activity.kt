package id.smartbiller.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
    override fun onCreate(savedInstanceState: Bundle?) {
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
    fun generateToken() { tokenValue = (1..20).joinToString("") { Random.nextInt(0, 10).toString() } }
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

@Composable private fun MiniInfo(title: String, subtitle: String) { GlassCard(Modifier.fillMaxWidth()) { Text(title, color = Accent, fontWeight = FontWeight.Bold); Text(subtitle, color = Color.White.copy(alpha = .58f), style = MaterialTheme.typography.bodySmall) } }

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
                Text("Halo, ${vm.user.name}", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("ULP Sumedang • periode AGU26", color = Color.White.copy(alpha = .62f))
                Spacer(Modifier.height(8.dp))
                Text(vm.syncText, color = Green, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip("Preventif", vm.preventive(), Accent)
                    StatChip("Korektif", vm.corrective(), Red)
                    StatChip("Irisan", vm.intersection(), Color(0xFF64B5F6))
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric("Pelanggan", CustomerSeed.masterRecordCount, Modifier.weight(1f)); Metric("Bayar", vm.paid(), Modifier.weight(1f)) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric("Belum", vm.unpaid(), Modifier.weight(1f)); Metric("Lewat", vm.overdue(), Modifier.weight(1f)) } }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Fitur Unggulan", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Feature("Database Pelanggan", "1.328 record dari Excel/backend", Icons.Default.People)
                Feature("Peta & Navigasi", "Koordinat pelanggan + Google Maps", Icons.Default.Map)
                Feature("Laporan & Ranking", "Harian, target dan leaderboard", Icons.Default.Leaderboard)
                Feature("Foto & Evidence", "Meter, lokasi dan kunjungan", Icons.Default.CameraAlt)
                Feature("Mode Offline", "Seed lokal untuk review tanpa API", Icons.Default.CloudOff)
                Feature("Notifikasi", "Pengingat jatuh tempo", Icons.Default.Notifications)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureAction("Laporan", Icons.Default.Leaderboard) { open(SubPage.REPORT) }
                FeatureAction("PDIL", Icons.Default.EditNote) { open(SubPage.PDIL) }
                FeatureAction("Evidence", Icons.Default.CameraAlt) { open(SubPage.EVIDENCE) }
            }
        }
    }
}

@Composable private fun Metric(label: String, value: Int, modifier: Modifier = Modifier) = GlassCard(modifier) { Text(label, color = Color.White.copy(alpha = .62f), style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(4.dp)); Text("$value", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) }
@Composable private fun StatChip(label: String, value: Int, color: Color) = AssistChip(onClick = {}, label = { Text("$label  $value") }, colors = AssistChipDefaults.assistChipColors(containerColor = color.copy(alpha = .18f), labelColor = Color.White))
@Composable private fun Feature(title: String, sub: String, icon: ImageVector) { Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Accent, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(10.dp)); Column { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(sub, color = Color.White.copy(alpha = .56f), style = MaterialTheme.typography.bodySmall) } } }
@Composable private fun FeatureAction(title: String, icon: ImageVector, onClick: () -> Unit) = GlassCard(Modifier.fillMaxWidth().widthIn(min = 96.dp).wrapContentHeight()) { Icon(icon, null, tint = Accent); Spacer(Modifier.height(5.dp)); Text(title, color = Color.White, fontWeight = FontWeight.SemiBold); TextButton(onClick = onClick) { Text("Buka") } }
private fun navIcon(tab: Tab): ImageVector = when (tab) { Tab.HOME -> Icons.Default.Dashboard; Tab.CUSTOMER -> Icons.Default.People; Tab.BILLING -> Icons.Default.ReceiptLong; Tab.MAP -> Icons.Default.Map; Tab.PROFILE -> Icons.Default.Person }

@Composable
private fun CustomerPage(vm: V4ViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { vm.reload() }, label = { Text("Semua") })
            AssistChip(onClick = {}, label = { Text("Belum Bayar") })
            AssistChip(onClick = {}, label = { Text("Lewat Tempo") })
        }
        OutlinedTextField(vm.query, { vm.search(it) }, label = { Text("Cari ID / nama / alamat / RBM") }, modifier = Modifier.fillMaxWidth().padding(16.dp), leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text("${vm.customers.size} data lokal • master ${CustomerSeed.masterRecordCount}", color = Color.White.copy(alpha = .62f)) }
            items(vm.customers) { c -> CustomerItem(vm, c) }
        }
    }
}

@Composable
private fun CustomerItem(vm: V4ViewModel, c: Customer) {
    val context = LocalContext.current
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.name ?: "Pelanggan", color = Color.White, fontWeight = FontWeight.Bold)
                Text(c.customerNo, color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
            }
            AssistChip(onClick = { vm.inquiry(c.customerNo) }, label = { Text(c.tarif ?: "-") })
        }
        Spacer(Modifier.height(8.dp))
        Text(c.address ?: "Alamat belum tersedia", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
        Text("${c.daya ?: 0} VA • ${c.rbm} • ${c.ulp?.name ?: "ULP Sumedang"}", color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { vm.inquiry(c.customerNo) }) { Text("Inquiry") }
            TextButton(onClick = { openMap(c, context) }) { Text("Peta") }
        }
    }
}

@Composable
private fun BillingPage(vm: V4ViewModel) {
    val filtered = vm.bills.filter { when (vm.filter) { BillFilter.ALL -> true; BillFilter.UNPAID -> it.status == "UNPAID"; BillFilter.OVERDUE -> it.status == "OVERDUE" } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { vm.filter = BillFilter.ALL }, label = { Text("Semua") })
            AssistChip(onClick = { vm.filter = BillFilter.UNPAID }, label = { Text("Belum Bayar") })
            AssistChip(onClick = { vm.filter = BillFilter.OVERDUE }, label = { Text("Lewat Tempo") })
        }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered) { b -> BillingItem(b) { vm.selectBill(b) } }
        }
    }
}

@Composable private fun BillingItem(b: Billing, onClick: () -> Unit) = GlassCard(Modifier.fillMaxWidth()) {
    Text(b.customer.name ?: "Pelanggan", color = Color.White, fontWeight = FontWeight.Bold)
    Text("${b.customer.customerNo} • ${b.period} • ${b.category}", color = Color.White.copy(alpha = .62f), style = MaterialTheme.typography.bodySmall)
    Text(rupiah(b.total), color = Accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    Text(b.status, color = when (b.status) { "PAID" -> Green; "OVERDUE" -> Red; else -> Accent })
    TextButton(onClick = onClick) { Text("Bayar / Edukasi") }
}

@Composable
private fun MapPage(vm: V4ViewModel) {
    val context = LocalContext.current
    val coordinates = vm.customers.mapNotNull { c ->
        val lat = c.latitude
        val lon = c.longitude
        if (lat != null && lon != null) LatLng(lat, lon) to c else null
    }
    val cameraState = rememberCameraPositionState()
    LaunchedEffect(coordinates.size) { coordinates.firstOrNull()?.first?.let { cameraState.position = CameraPosition.fromLatLngZoom(it, 15f) } }
    Column(Modifier.fillMaxSize()) {
        GoogleMap(Modifier.fillMaxWidth().height(320.dp).padding(16.dp), cameraPositionState = cameraState, properties = MapProperties(mapType = MapType.NORMAL), uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false)) {
            coordinates.forEach { (position, customer) -> Marker(state = MarkerState(position), title = customer.name ?: customer.customerNo, snippet = customer.customerNo) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text("${coordinates.size} pelanggan terplot dari data koordinat", color = Color.White.copy(alpha = .62f)) }
            items(vm.customers.take(25)) { c ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(c.name ?: "Pelanggan", color = Color.White, fontWeight = FontWeight.Bold); Text("${c.latitude ?: "-"}, ${c.longitude ?: "-"}", color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall) }
                        IconButton(onClick = { openMap(c, context) }) { Icon(Icons.Default.Navigation, "Navigasi", tint = Accent) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilePage(vm: V4ViewModel, open: (SubPage) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { GlassCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(66.dp).background(Cyan.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Cyan, modifier = Modifier.size(38.dp)) }; Spacer(Modifier.width(12.dp)); Column { Text(vm.user.name, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text("Biller • UP3 Sumedang", color = Color.White.copy(alpha = .65f)) } } } }
        item { ActionRow("Laporan & Ranking", Icons.Default.Leaderboard) { open(SubPage.REPORT) } }
        item { ActionRow("Pengaturan", Icons.Default.Settings) { open(SubPage.SETTINGS) } }
        item { ActionRow("PDIL", Icons.Default.EditNote) { open(SubPage.PDIL) } }
        item { ActionRow("Foto & Evidence", Icons.Default.CameraAlt) { open(SubPage.EVIDENCE) } }
        item { ActionRow("Pembayaran Simulasi", Icons.Default.Payment) { open(SubPage.PAYMENT) } }
        item { ActionRow("PLN Prabayar / Token", Icons.Default.Bolt) { open(SubPage.TOKEN) } }
        item { ActionRow("Tentang Smart Biller", Icons.Default.Info) { open(SubPage.ABOUT) } }
    }
}

@Composable private fun ActionRow(title: String, icon: ImageVector, onClick: () -> Unit) = GlassCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Accent); Spacer(Modifier.width(12.dp)); Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); TextButton(onClick = onClick) { Text("Buka") } } }

@Composable private fun SettingsPage(vm: V4ViewModel, context: Context) = SimpleListPage("Pengaturan", vm) {
    SettingSwitch("Notifikasi jatuh tempo", vm.notificationEnabled) { vm.notificationEnabled = it }
    SettingSwitch("Sinkronisasi otomatis", vm.autoSyncEnabled) { vm.autoSyncEnabled = it; vm.reload() }
    SettingSwitch("Mode offline", vm.offlineEnabled) { vm.offlineEnabled = it }
    SettingSwitch("Mode gelap", vm.darkEnabled) { vm.darkEnabled = it }
    ActionButton("Sinkronkan sekarang") { vm.reload(); Toast.makeText(context, "Sinkronisasi review selesai", Toast.LENGTH_SHORT).show() }
}
@Composable private fun SettingSwitch(title: String, checked: Boolean, onChanged: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, color = Color.White, modifier = Modifier.weight(1f)); Switch(checked, onChanged) } }
@Composable private fun ActionButton(title: String, onClick: () -> Unit) { Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(title) } }

@Composable private fun ReportPage(vm: V4ViewModel, context: Context) = SimpleListPage("Laporan & Ranking", vm) {
    Text("Preventif ${vm.preventive()} • Korektif ${vm.corrective()} • Irisan ${vm.intersection()}", color = Color.White)
    Text("Bayar ${vm.paid()} • Belum ${vm.unpaid()} • Lewat ${vm.overdue()}", color = Color.White.copy(alpha = .70f))
    ActionButton("Export PDF") { exportReport(context, vm) }
}

@Composable private fun PdilPage(vm: V4ViewModel) = SimpleListPage("PDIL", vm) { listOf("DRAFT", "SUBMITTED", "VERIFIED", "APPROVED", "SYNCED").forEach { Text(it, color = Color.White.copy(alpha = .82f), modifier = Modifier.padding(vertical = 6.dp)) } }

@Composable private fun EvidencePage(vm: V4ViewModel) {
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> photo = bmp }
    SimpleListPage("Foto & Evidence", vm) {
        Button(onClick = { launcher.launch(null) }, modifier = Modifier.fillMaxWidth()) { Text("Ambil Foto Meter / Lokasi") }
        photo?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "Evidence", modifier = Modifier.fillMaxWidth().height(220.dp)) }
        Text("Evidence lokal siap untuk review.", color = Color.White.copy(alpha = .68f))
    }
}

@Composable private fun PaymentPage(vm: V4ViewModel, context: Context) = SimpleListPage("Pembayaran Simulasi", vm) {
    val bill = vm.selectedBill
    Text(bill?.customer?.name ?: "Belum ada tagihan dipilih", color = Color.White, fontWeight = FontWeight.Bold)
    bill?.let {
        Text("Total ${rupiah(it.total)}", color = Accent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text("Periode ${it.period} • ${it.category}", color = Color.White.copy(alpha = .65f))
        ActionButton("Simulasikan Pembayaran") { vm.simulatePayment(); Toast.makeText(context, "Pembayaran demo berhasil", Toast.LENGTH_SHORT).show() }
    }
    vm.paymentRef?.let { ref -> Text("Reference: $ref", color = Green, fontWeight = FontWeight.Bold) }
}

@Composable private fun TokenPage(vm: V4ViewModel, context: Context) = SimpleListPage("PLN Prabayar / Token", vm) {
    Text("Token demo untuk review UI", color = Color.White.copy(alpha = .72f))
    ActionButton("Generate Token Demo") { vm.generateToken() }
    vm.tokenValue?.let { Text("Token: $it", color = Accent, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
    ActionButton("Salin Token") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Smart Biller Token", vm.tokenValue.orEmpty()))
        Toast.makeText(context, "Token disalin", Toast.LENGTH_SHORT).show()
    }
}

@Composable private fun AboutPage(vm: V4ViewModel) = SimpleListPage("Tentang Smart Biller", vm) { Text("Kotlin + Jetpack Compose + Material 3", color = Color.White); Text("Glassmorphism • Supabase • Google Maps", color = Color.White.copy(alpha = .72f)); Text("Smart Biller V4", color = Accent, fontWeight = FontWeight.Bold) }

@Composable private fun SimpleListPage(title: String, vm: V4ViewModel, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = { vm.subPage = SubPage.NONE }) { Text("← Kembali") }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) { item { GlassCard(Modifier.fillMaxWidth(), content) } }
    }
}

@Composable private fun InquiryDialog(response: InquiryResponse, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Inquiry Pelanggan") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(response.customer.name ?: "Pelanggan"); Text(response.customer.customerNo); Text("Periode ${response.billing.periode}"); Text(rupiah(response.billing.amount)); Text("Reference ${response.inquiry.id}") } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } })
}

private fun rupiah(value: Double): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(value).replace(",00", "").replace("Rp", "Rp ")

private fun openMap(customer: Customer, context: Context) {
    val lat = customer.latitude ?: return
    val lon = customer.longitude ?: return
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon")))
}

private fun exportReport(context: Context, vm: V4ViewModel) {
    val document = PdfDocument()
    val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f }
    page.canvas.drawText("Smart Biller V4 - Laporan", 40f, 60f, paint)
    paint.textSize = 13f
    page.canvas.drawText("Preventif: ${vm.preventive()}", 40f, 100f, paint)
    page.canvas.drawText("Korektif: ${vm.corrective()}", 40f, 125f, paint)
    page.canvas.drawText("Irisan: ${vm.intersection()}", 40f, 150f, paint)
    page.canvas.drawText("Sudah Bayar: ${vm.paid()}", 40f, 175f, paint)
    page.canvas.drawText("Belum Bayar: ${vm.unpaid()}", 40f, 200f, paint)
    page.canvas.drawText("Lewat Tempo: ${vm.overdue()}", 40f, 225f, paint)
    document.finishPage(page)
    val fileName = "SmartBiller-Laporan-${System.currentTimeMillis()}.pdf"
    if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, fileName); put(MediaStore.Downloads.MIME_TYPE, "application/pdf"); put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS) }
        context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)?.let { uri -> context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) } }
    }
    document.close()
    Toast.makeText(context, "Laporan diekspor ke Download", Toast.LENGTH_SHORT).show()
}
