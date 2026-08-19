package id.smartbiller.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.smartbiller.app.data.ApiProvider
import id.smartbiller.app.data.Billing
import id.smartbiller.app.data.BillingSummary
import id.smartbiller.app.data.Customer
import id.smartbiller.app.data.InquiryRequest
import id.smartbiller.app.data.InquiryResponse
import id.smartbiller.app.data.LeaderRow
import id.smartbiller.app.data.SessionStore
import id.smartbiller.app.data.SmartBillerApi
import id.smartbiller.app.data.User
import id.smartbiller.app.ui.AppBackground
import id.smartbiller.app.ui.GlassCard
import id.smartbiller.app.ui.theme.SmartBillerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class SessionTokenHolder {
    var token: String? = null
}

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
                error = "Login gagal. Periksa kredensial atau koneksi."
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
            } catch (_: Exception) {
                error = "Dashboard gagal dimuat."
            } finally {
                loading = false
            }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            try {
                customers = api.search(query).items
            } catch (_: Exception) {
                error = "Pencarian gagal."
            }
        }
    }

    fun checkInquiry(customerNo: String) {
        viewModelScope.launch {
            loading = true
            try {
                inquiry = api.inquiry(InquiryRequest(customerNo))
            } catch (_: Exception) {
                error = "Pelanggan tidak ditemukan atau API review bermasalah."
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
            SmartBillerTheme {
                SmartBillerApp(vm)
            }
        }
    }
}

@Composable
private fun SmartBillerApp(vm: MainViewModel) {
    if (vm.token == null) {
        LoginScreen(vm)
    } else {
        DashboardShell(vm)
    }
}

@Composable
private fun LoginScreen(vm: MainViewModel) {
    var email by rememberSaveable { mutableStateOf("admin") }
    var password by rememberSaveable { mutableStateOf("change-me-now") }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Smart Biller",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("PLN Electricity Services", color = Color.White.copy(alpha = 0.75f))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email / Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                vm.error?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(it, color = Color(0xFFFFB4AB))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { vm.login(email, password) },
                    enabled = !vm.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (vm.loading) "Memeriksa..." else "Masuk ke Dashboard")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Review mode • admin / supervisor / biller",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall,
                )
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

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar {
                nav.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.second, contentDescription = item.first) },
                        label = { Text(item.first) },
                    )
                }
            }
        },
    ) { padding ->
        AppBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Smart Biller",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${vm.user?.name ?: "Petugas"} • ${vm.user?.role ?: "BILLER"}",
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                }

                when (tab) {
                    0 -> Home(vm)
                    1 -> Customers(vm)
                    2 -> BillingList(vm)
                    3 -> PayScreen(vm)
                    else -> Profile(vm)
                }
            }
        }
    }
}

@Composable
private fun Home(vm: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Periode berjalan", color = Color.White.copy(alpha = 0.75f))
                Text(
                    vm.summary?.period ?: "—",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    vm.summary?.category ?: "Review",
                    color = Color(0xFFFFD54F),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        item {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.height(190.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Metric("Preventif", vm.summary?.preventif ?: 0, Icons.Default.Schedule) }
                item { Metric("Korektif", vm.summary?.korektif ?: 0, Icons.Default.Warning) }
                item { Metric("Irisan", vm.summary?.irisan ?: 0, Icons.Default.Layers) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    "Prioritas Tagihan",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                vm.billings.take(5).forEach { billing ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                billing.customer.name ?: billing.customer.customerNo,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                billing.customer.customerNo,
                                color = Color.White.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            formatRupiah(billing.total),
                            color = Color(0xFFFFD54F),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: Int, icon: ImageVector) {
    GlassCard(Modifier.fillMaxSize()) {
        Icon(icon, contentDescription = label, tint = Color(0xFFFFD54F))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            value.toString(),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(label, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun Customers(vm: MainViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Cari ID pelanggan / nama / alamat") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { vm.search(query) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cari Pelanggan")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(vm.customers) { customer ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        customer.name ?: "Tanpa nama",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(customer.customerNo, color = Color.White.copy(alpha = 0.72f))
                    Text(
                        customer.address ?: "Alamat belum tersedia",
                        color = Color.White.copy(alpha = 0.62f),
                    )
                    Text(
                        "${customer.ulp?.name ?: "ULP —"} • ${customer.meterNo ?: "Meter —"}",
                        color = Color(0xFF9BD7FF),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun BillingList(vm: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(vm.billings) { billing ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            billing.customer.name ?: billing.customer.customerNo,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${billing.customer.customerNo} • ${billing.period}",
                            color = Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        formatRupiah(billing.total),
                        color = Color(0xFFFFD54F),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${billing.category} • Jatuh tempo ${billing.dueDate.take(10)}",
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun PayScreen(vm: MainViewModel) {
    var customerNo by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                "Cek Tagihan PLN",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = customerNo,
                onValueChange = { customerNo = it },
                label = { Text("ID Pelanggan") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { vm.checkInquiry(customerNo) },
                modifier = Modifier.fillMaxWidth(),
                enabled = customerNo.length >= 6 && !vm.loading,
            ) {
                Text(if (vm.loading) "Memeriksa..." else "Inquiry Demo")
            }
        }

        vm.inquiry?.let { response ->
            Spacer(modifier = Modifier.height(12.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    response.customer.name ?: "Pelanggan",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    response.customer.customerNo,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("Total Tagihan", color = Color.White.copy(alpha = 0.7f))
                Text(
                    formatRupiah(response.billing.amount),
                    color = Color(0xFFFFD54F),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Pembayaran Demo")
                }
            }
        }
    }
}

@Composable
private fun Profile(vm: MainViewModel) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                vm.user?.name ?: "Petugas",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(vm.user?.email ?: "", color = Color.White.copy(alpha = 0.7f))
            Text(
                vm.user?.role ?: "BILLER",
                color = Color(0xFFFFD54F),
                fontWeight = FontWeight.Bold,
            )
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Mode Review", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "Database pelanggan berasal dari master Excel yang disinkronkan ke Supabase review.",
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        Button(
            onClick = { vm.logout() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
        ) {
            Text("Keluar")
        }
    }
}

private fun formatRupiah(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }.format(value)
