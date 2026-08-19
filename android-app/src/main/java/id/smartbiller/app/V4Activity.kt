package id.smartbiller.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import id.smartbiller.app.data.*
import id.smartbiller.app.ui.theme.SmartBillerTheme
import kotlinx.coroutines.*

private val V4Blue = Color(0xFF0A2A4A)
private val V4Glass = Color.White.copy(alpha = .10f)
private val V4Accent = Color(0xFFFFD54F)
private val V4Good = Color(0xFF35D07F)
private val V4Warn = Color(0xFFFFC857)
private val V4Bad = Color(0xFFFF6B6B)

class V4Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmartBillerTheme { V4App() } }
    }
}

private enum class Tab { HOME, CUSTOMER, BILLING, MAP, PROFILE }
private enum class SubPage { NONE, SETTINGS, REPORT, PDIL, EVIDENCE, PAYMENT, TOKEN, ABOUT }
private enum class BillFilter { ALL, UNPAID, OVERDUE }

@Composable
private fun V4App() {
    var splash by rememberSaveable { mutableStateOf(true) }
    val vm = remember { V4ViewModel() }
    LaunchedEffect(Unit) {
        delay(800)
        vm.restore()
        splash = false
    }
    when {
        splash -> V4Splash()
        vm.user == null -> V4Login(vm)
        else -> V4Shell(vm)
    }
}

private class V4ViewModel {
    private val local = CustomerSeed.all
    private var token: String? = "review-token"
    private val api by lazy { ApiProvider().create { token } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var user by mutableStateOf<User?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var customerList by mutableStateOf(local)
    var billings by mutableStateOf(buildBills(local))
    var billFilter by mutableStateOf(BillFilter.ALL)
    var subPage by mutableStateOf(SubPage.NONE)
    var inquiry by mutableStateOf<InquiryResponse?>(null)
    var syncText by mutableStateOf("Review seed aktif")

    fun restore() {
        user = User("demo-admin", "admin@example.com", "Admin Demo", "ADMIN")
        reload()
    }

    fun login(username: String, password: String) {
        loading = true
        error = null
        scope.launch {
            val remote = runCatching { api.login(mapOf("username" to username, "password" to password)) }.getOrNull()
            withContext(Dispatchers.Main) {
                when {
                    remote != null -> {
                        token = remote.token
                        user = remote.user
                        reload()
                    }
                    (username.equals("admin", true) || username.equals("admin@example.com", true)) && password == "change-me-now" -> {
                        token = "review-token"
                        user = User("demo-admin", "admin@example.com", "Admin Demo", "ADMIN")
                        reload()
                    }
                    else -> error = "Login gagal. Gunakan admin / change-me-now untuk review."
                }
                loading = false
            }
        }
    }

    fun logout() {
        user = null
        inquiry = null
        subPage = SubPage.NONE
    }

    fun reload() {
        customerList = local
        billings = buildBills(local)
        syncText = "Master Excel: ${CustomerSeed.masterRecordCount} record • seed lokal ${local.size}"
    }

    fun search(q: String) {
        query = q
        if (q.isBlank()) {
            customerList = local
            return
        }
        scope.launch {
            val remote = runCatching { api.search(q).items }.getOrNull()
            withContext(Dispatchers.Main) {
                customerList = if (!remote.isNullOrEmpty()) {
                    remote
                } else {
                    local.filter {
                        it.customerNo.contains(q, true) ||
                            (it.name ?: "").contains(q, true) ||
                            (it.address ?: "").contains(q, true) ||
                            it.rbm.contains(q, true)
                    }
                }
            }
        }
    }

    fun inquiry(customerNo: String) {
        scope.launch {
            val remote = runCatching { api.inquiry(InquiryRequest(customerNo)) }.getOrNull()
            withContext(Dispatchers.Main) {
                inquiry = remote ?: buildBills(listOfNotNull(local.firstOrNull { it.customerNo == customerNo }))
                    .firstOrNull()
                    ?.let {
                        InquiryResponse(
                            it.customer,
                            InquiryBilling(it.period, it.sellingPrice, it.total, it.admin),
                            Inquiry("INQ-$customerNo", "READY")
                        )
                    }
                if (inquiry == null) error = "Pelanggan tidak ditemukan."
            }
        }
    }

    private fun buildBills(list: List<Customer>): List<Billing> = list.mapIndexed { i, c ->
        val n = c.customerNo.takeLast(2).toIntOrNull() ?: i
        val status = when {
            n % 17 == 0 -> "OVERDUE"
            n % 3 == 0 -> "PAID"
            else -> "UNPAID"
        }
        val category = when {
            status == "OVERDUE" -> "KOREKTIF"
            i % 11 == 0 -> "IRISAN"
            else -> "PREVENTIF"
        }
        val total = 200_000.0 + (c.daya ?: 900) * 32.0 + (i % 9) * 10_000
        Billing(
            "V4-$i",
            "AGU26",
            category,
            status,
            total,
            if (status == "OVERDUE") "Lewat 20 Agu 2026" else "20 Agu 2026",
            c,
            total,
            2500.0,
            total
        )
    }

    fun paid() = billings.count { it.status == "PAID" }
    fun unpaid() = billings.count { it.status == "UNPAID" }
    fun overdue() = billings.count { it.status == "OVERDUE" }
    fun preventive() = billings.count { it.category == "PREVENTIF" }
    fun corrective() = billings.count { it.category == "KOREKTIF" }
    fun intersection() = billings.count { it.category == "IRISAN" }
}

@Composable
private fun V4Bg(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(V4Blue)) { content() }
}

@Composable
private fun V4Card(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.background(V4Glass, RoundedCornerShape(24.dp)).padding(16.dp),
        content = content
    )
}

@Composable
private fun V4Splash() {
    V4Bg {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(110.dp).background(V4Accent.copy(alpha = .15f), RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Bolt, null, tint = V4Accent, modifier = Modifier.size(60.dp)) }
            Spacer(Modifier.height(18.dp))
            Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("PLN Electricity Services", color = Color.White.copy(alpha = .7f))
        }
    }
}

@Composable
private fun V4Login(vm: V4ViewModel) {
    var username by rememberSaveable { mutableStateOf("admin") }
    var password by rememberSaveable { mutableStateOf("change-me-now") }
    var show by rememberSaveable { mutableStateOf(false) }
    V4Bg {
        Column(
            Modifier.fillMaxSize().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            V4Card(Modifier.fillMaxWidth()) {
                Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Masuk ke dashboard biller", color = Color.White.copy(alpha = .65f))
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(username, { username = it }, label = { Text("Username / Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Sembunyikan" else "Lihat") } }
                )
                vm.error?.let { Text(it, color = V4Bad, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(14.dp))
                Button(onClick = { vm.login(username, password) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.loading) "Memeriksa..." else "Masuk")
                }
                Spacer(Modifier.height(8.dp))
                Text("Demo: admin / change-me-now", color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun V4Shell(vm: V4ViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val nav = listOf(Tab.HOME to "Beranda", Tab.CUSTOMER to "Pelanggan", Tab.BILLING to "Tagihan", Tab.MAP to "Peta", Tab.PROFILE to "Profil")
    V4Bg {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar {
                    nav.forEach { (t, label) ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(navIcon(t), label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Smart Biller", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${vm.user?.name} • ${vm.user?.role}", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { vm.reload() }) { Icon(Icons.Default.Refresh, "Refresh", tint = Color.White) }
                }
                when (vm.subPage) {
                    SubPage.SETTINGS -> SettingsPage(vm)
                    SubPage.REPORT -> ReportPage(vm)
                    SubPage.PDIL -> PdilPage(vm)
                    SubPage.EVIDENCE -> EvidencePage(vm)
                    SubPage.PAYMENT -> PaymentPage(vm)
                    SubPage.TOKEN -> TokenPage(vm)
                    SubPage.ABOUT -> AboutPage(vm)
                    SubPage.NONE -> when (tab) {
                        Tab.HOME -> HomePage(vm)
                        Tab.CUSTOMER -> CustomerPage(vm)
                        Tab.BILLING -> BillingPage(vm)
                        Tab.MAP -> MapPage(vm)
                        Tab.PROFILE -> ProfilePage(vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePage(vm: V4ViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            V4Card(Modifier.fillMaxWidth()) {
                Text("Halo, ${vm.user?.name ?: "Petugas"}", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("ULP Sumedang • periode AGU26", color = Color.White.copy(alpha = .65f))
                Spacer(Modifier.height(8.dp))
                Text(vm.syncText, color = V4Good, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip("Preventif", vm.preventive(), V4Accent)
                    StatChip("Korektif", vm.corrective(), V4Bad)
                    StatChip("Irisan", vm.intersection(), Color(0xFF64B5F6))
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric(Modifier.weight(1f), "Pelanggan", CustomerSeed.masterRecordCount); Metric(Modifier.weight(1f), "Bayar", vm.paid()) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric(Modifier.weight(1f), "Belum", vm.unpaid()); Metric(Modifier.weight(1f), "Lewat", vm.overdue()) } }
        item {
            V4Card(Modifier.fillMaxWidth()) {
                Text("Fitur Unggulan", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Feature("Database Pelanggan", "${CustomerSeed.masterRecordCount} record dari Excel/backend", Icons.Default.People)
                Feature("Peta & Navigasi", "Koordinat pelanggan + Google Maps", Icons.Default.Map)
                Feature("Laporan & Ranking", "Harian, target dan leaderboard", Icons.Default.Leaderboard)
                Feature("Foto & Evidence", "Meter, lokasi dan kunjungan", Icons.Default.CameraAlt)
                Feature("Mode Offline", "Seed lokal untuk review tanpa API", Icons.Default.CloudOff)
                Feature("Notifikasi", "Pengingat jatuh tempo", Icons.Default.Notifications)
            }
        }
    }
}

@Composable private fun Metric(m: Modifier, label: String, value: Int) = V4Card(m) { Text(label, color = Color.White.copy(alpha = .62f), style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(4.dp)); Text("$value", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
@Composable private fun StatChip(label: String, value: Int, color: Color) = AssistChip(onClick = {}, label = { Text("$label $value") }, colors = AssistChipDefaults.assistChipColors(containerColor = color.copy(alpha = .18f), labelColor = Color.White))
@Composable private fun Feature(title: String, sub: String, icon: ImageVector) { Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = V4Accent, modifier = Modifier.size(25.dp)); Spacer(Modifier.width(10.dp)); Column { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold); Text(sub, color = Color.White.copy(alpha = .58f), style = MaterialTheme.typography.bodySmall) } } }
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
            item { Text("${vm.customerList.size} data lokal • master ${CustomerSeed.masterRecordCount}", color = Color.White.copy(alpha = .62f)) }
            items(vm.customerList) { c -> CustomerItem(vm, c) }
        }
    }
}

@Composable
private fun CustomerItem(vm: V4ViewModel, c: Customer) {
    val context = androidx.compose.ui.platform.LocalContext.current
    V4Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.name ?: "Pelanggan", color = Color.White, fontWeight = FontWeight.Bold)
                Text(c.customerNo, color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
            }
            AssistChip(onClick = { vm.reload() }, label = { Text(c.tarif ?: "-") })
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
    val filtered = vm.billings.filter { when (vm.billFilter) { BillFilter.ALL -> true; BillFilter.UNPAID -> it.status == "UNPAID"; BillFilter.OVERDUE -> it.status == "OVERDUE" } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { vm.billFilter = BillFilter.ALL }, label = { Text("Semua") })
            AssistChip(onClick = { vm.billFilter = BillFilter.UNPAID }, label = { Text("Belum Bayar") })
            AssistChip(onClick = { vm.billFilter = BillFilter.OVERDUE }, label = { Text("Lewat Tempo") })
        }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered) { b -> BillingItem(b) }
        }
    }
}

@Composable private fun BillingItem(b: Billing) = V4Card(Modifier.fillMaxWidth()) {
    Text(b.customer.name ?: "Pelanggan", color = Color.White, fontWeight = FontWeight.Bold)
    Text("${b.customer.customerNo} • ${b.period} • ${b.category}", color = Color.White.copy(alpha = .62f), style = MaterialTheme.typography.bodySmall)
    Text(rupiah(b.total), color = V4Accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(b.status, color = when (b.status) { "PAID" -> V4Good; "OVERDUE" -> V4Bad; else -> V4Warn })
}

@Composable
private fun MapPage(vm: V4ViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            V4Card(Modifier.fillMaxWidth().height(250.dp)) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0D3857), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Map, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(60.dp))
                        Text("Google Maps Review Mode", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Pins berdasarkan koordinat master", color = Color.White.copy(alpha = .6f))
                    }
                }
            }
        }
        items(vm.customerList.take(25)) { c ->
            V4Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name ?: "Pelanggan", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${c.latitude ?: "-"}, ${c.longitude ?: "-"}", color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { openMap(c, context) }) { Icon(Icons.Default.Navigation, "Navigasi", tint = V4Accent) }
                }
            }
        }
    }
}

@Composable
private fun ProfilePage(vm: V4ViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            V4Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).background(V4Accent.copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = V4Accent, modifier = Modifier.size(36.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column { Text(vm.user?.name ?: "Rahmat K", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Biller • UP3 Sumedang", color = Color.White.copy(alpha = .65f)) }
                }
            }
        }
        item { ActionRow("Laporan & Ranking", Icons.Default.Leaderboard) { vm.subPage = SubPage.REPORT } }
        item { ActionRow("Pengaturan", Icons.Default.Settings) { vm.subPage = SubPage.SETTINGS } }
        item { ActionRow("PDIL", Icons.Default.EditNote) { vm.subPage = SubPage.PDIL } }
        item { ActionRow("Foto & Evidence", Icons.Default.CameraAlt) { vm.subPage = SubPage.EVIDENCE } }
        item { ActionRow("Pembayaran Simulasi", Icons.Default.Payment) { vm.subPage = SubPage.PAYMENT } }
        item { ActionRow("PLN Prabayar / Token", Icons.Default.Bolt) { vm.subPage = SubPage.TOKEN } }
        item { ActionRow("Tentang Smart Biller", Icons.Default.Info) { vm.subPage = SubPage.ABOUT } }
        item { Button(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) { Text("Keluar") } }
    }
}

@Composable
private fun ActionRow(title: String, icon: ImageVector, onClick: () -> Unit) = V4Card(Modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = V4Accent)
        Spacer(Modifier.width(12.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text("Buka") }
    }
}

@Composable
private fun SettingsPage(vm: V4ViewModel) = SimplePage("Pengaturan", vm, listOf("Notifikasi jatuh tempo", "Mode offline", "Sinkronisasi master", "Keamanan sesi", "Tema glassmorphism"))
@Composable
private fun ReportPage(vm: V4ViewModel) = SimplePage("Laporan & Ranking", vm, listOf("Preventif: ${vm.preventive()}", "Korektif: ${vm.corrective()}", "Irisan: ${vm.intersection()}", "Bayar: ${vm.paid()}", "Belum bayar: ${vm.unpaid()}", "Lewat tempo: ${vm.overdue()}"))
@Composable
private fun PdilPage(vm: V4ViewModel) = SimplePage("PDIL", vm, listOf("Draft", "Submit", "Verifikasi", "Approve", "Sync"))
@Composable
private fun EvidencePage(vm: V4ViewModel) = SimplePage("Foto & Evidence", vm, listOf("Foto meter", "Foto lokasi", "Foto kunjungan", "Status evidence"))
@Composable
private fun PaymentPage(vm: V4ViewModel) = SimplePage("Pembayaran Simulasi", vm, listOf("Inquiry pelanggan", "Review nominal", "Simulasi bayar", "Reference pembayaran"))
@Composable
private fun TokenPage(vm: V4ViewModel) = SimplePage("PLN Prabayar / Token", vm, listOf("Inquiry meter", "Nominal token", "Generate token demo", "Riwayat token"))
@Composable
private fun AboutPage(vm: V4ViewModel) = SimplePage("Tentang Smart Biller", vm, listOf("Kotlin + Jetpack Compose", "Material 3", "Glassmorphism", "Supabase Edge Function", "Mode review"))

@Composable
private fun SimplePage(title: String, vm: V4ViewModel, rows: List<String>) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TextButton(onClick = { vm.subPage = SubPage.NONE }) { Text("← Kembali") }
        }
        item {
            V4Card(Modifier.fillMaxWidth()) {
                Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                rows.forEach { row ->
                    Text(row, color = Color.White.copy(alpha = .75f), modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }
    }
}

private fun rupiah(value: Double): String = "Rp ${"%,.0f".format(java.util.Locale("id", "ID"), value)}"

private fun openMap(customer: Customer, context: Context) {
    val lat = customer.latitude ?: return
    val lon = customer.longitude ?: return
    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}
