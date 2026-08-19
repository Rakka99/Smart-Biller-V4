package id.smartbiller.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.smartbiller.app.data.*
import id.smartbiller.app.ui.*
import id.smartbiller.app.ui.theme.SmartBillerTheme
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class SessionTokenHolder { var token:String?=null }

class MainViewModel(private val store:SessionStore,private val api:SmartBillerApi,private val holder:SessionTokenHolder):ViewModel(){
    var token by mutableStateOf<String?>(null);private set
    var user by mutableStateOf<User?>(null);private set
    var summary by mutableStateOf<BillingSummary?>(null);private set
    var billings by mutableStateOf<List<Billing>>(emptyList());private set
    var customers by mutableStateOf<List<Customer>>(emptyList());private set
    var leaders by mutableStateOf<List<LeaderRow>>(emptyList());private set
    var inquiry by mutableStateOf<InquiryResponse?>(null);private set
    var loading by mutableStateOf(false);private set
    var error by mutableStateOf<String?>(null);private set
    init{viewModelScope.launch{store.token.firstOrNull().also{token=it;holder.token=it};store.user.firstOrNull().also{user=it};if(token!=null)refresh()}}
    fun login(email:String,password:String,onSuccess:()->Unit)=viewModelScope.launch{loading=true;error=null;try{val r=api.login(mapOf("email" to email,"password" to password));store.save(r);token=r.token;holder.token=r.token;user=r.user;refresh();onSuccess()}catch(e:Exception){error="Login gagal. Periksa kredensial atau koneksi."}finally{loading=false}}
    fun refresh()=viewModelScope.launch{try{loading=true;summary=api.summary();billings=api.billing().items;leaders=api.leaderboard().rows}catch(e:Exception){error="Dashboard gagal dimuat."}finally{loading=false}}
    fun search(q:String)=viewModelScope.launch{try{customers=api.search(q).items}catch(e:Exception){error="Pencarian gagal."}}
    fun checkInquiry(no:String)=viewModelScope.launch{try{loading=true;inquiry=api.inquiry(InquiryRequest(no))}catch(e:Exception){error="Pelanggan tidak ditemukan atau API review bermasalah."}finally{loading=false}}
    fun logout()=viewModelScope.launch{store.clear();token=null;holder.token=null;user=null;summary=null;billings=emptyList();customers=emptyList()}
}

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{
        val store=remember{SessionStore(applicationContext)}
        val holder=remember{SessionTokenHolder()}
        val vm=remember{MainViewModel(store,ApiProvider().create{holder.token},holder)}
        SmartBillerTheme{SmartBillerApp(vm)}
    }}
}

@Composable private fun SmartBillerApp(vm:MainViewModel){if(vm.token==null)LoginScreen(vm)else DashboardShell(vm)}

@Composable private fun LoginScreen(vm:MainViewModel){
    var email by rememberSaveable{mutableStateOf("admin")}
    var password by rememberSaveable{mutableStateOf("change-me-now")}
    AppBackground{Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
        GlassCard(Modifier.fillMaxWidth().widthIn(max=460.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Bolt,null,tint=Color(0xFFFFD54F),modifier=Modifier.size(44.dp));Spacer(Modifier.width(10.dp));Column{Text("Smart Biller",color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("PLN Electricity Services",color=Color.White.copy(.75f))}}
            Spacer(Modifier.height(24.dp));OutlinedTextField(email,{email=it},label={Text("Email / Username")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Spacer(Modifier.height(10.dp));OutlinedTextField(password,{password=it},label={Text("Password")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            vm.error?.let{Spacer(Modifier.height(10.dp));Text(it,color=Color(0xFFFFB4AB))}
            Spacer(Modifier.height(16.dp));Button(onClick={vm.login(email,password){ }},enabled=!vm.loading,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text(if(vm.loading)"Memeriksa…" else "Masuk ke Dashboard")}
            Spacer(Modifier.height(8.dp));Text("Review mode • admin / supervisor / biller",color=Color.White.copy(.65f),style=MaterialTheme.typography.bodySmall)
        }
    }}
}

@Composable private fun DashboardShell(vm:MainViewModel){
    var tab by rememberSaveable{mutableIntStateOf(0)}
    Scaffold(containerColor=Color.Transparent,bottomBar={NavigationBar{val nav=listOf("Beranda" to Icons.Default.Dashboard,"Pelanggan" to Icons.Default.People,"Tagihan" to Icons.Default.ReceiptLong,"Bayar" to Icons.Default.Bolt,"Profil" to Icons.Default.Person);nav.forEachIndexed{i,(label,icon)->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icon,null)},label={Text(label)})}}}){pad->
        AppBackground{Column(Modifier.fillMaxSize().padding(pad)){
            Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Smart Biller",color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("${vm.user?.name?:"Petugas"} • ${vm.user?.role?:"BILLER"}",color=Color.White.copy(.72f))};IconButton(onClick={vm.refresh}){Icon(Icons.Default.Refresh,null,tint=Color.White)}}
            when(tab){0->Home(vm);1->Customers(vm);2->BillingList(vm);3->PayScreen(vm);else->Profile(vm)}
        }}
    }
}

@Composable private fun Home(vm:MainViewModel){LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=24.dp)){item{GlassCard(Modifier.fillMaxWidth()){Text("Periode berjalan",color=Color.White.copy(.75f));Text(vm.summary?.period?:"—",color=Color.White,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold);Text(vm.summary?.category?:"Review",color=Color(0xFFFFD54F),fontWeight=FontWeight.SemiBold)}};item{LazyVerticalGrid(columns=GridCells.Adaptive(150.dp),modifier=Modifier.height(190.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Metric("Preventif",vm.summary?.preventif?:0,Icons.Default.Schedule)};item{Metric("Korektif",vm.summary?.korektif?:0,Icons.Default.Warning)};item{Metric("Irisan",vm.summary?.irisan?:0,Icons.Default.Layers)}}};item{GlassCard(Modifier.fillMaxWidth()){Text("Prioritas Tagihan",color=Color.White,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));vm.billings.take(5).forEach{b->Row(Modifier.fillMaxWidth().padding(vertical=7.dp),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(b.customer.name?:b.customer.customerNo,color=Color.White,fontWeight=FontWeight.SemiBold);Text(b.customer.customerNo,color=Color.White.copy(.65f),style=MaterialTheme.typography.bodySmall)};Text(formatRupiah(b.total),color=Color(0xFFFFD54F),fontWeight=FontWeight.Bold)}}}}}}
}

@Composable private fun Metric(label:String,value:Int,icon:androidx.compose.ui.graphics.vector.ImageVector){GlassCard(Modifier.fillMaxWidth().fillMaxHeight()){Icon(icon,null,tint=Color(0xFFFFD54F));Spacer(Modifier.height(8.dp));Text(value.toString(),color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text(label,color=Color.White.copy(.7f))}}

@Composable private fun Customers(vm:MainViewModel){var q by rememberSaveable{mutableStateOf("")};Column(Modifier.fillMaxSize().padding(16.dp)){GlassCard(Modifier.fillMaxWidth()){OutlinedTextField(q,{q=it},label={Text("Cari ID pelanggan / nama / alamat")},modifier=Modifier.fillMaxWidth(),singleLine=true);Spacer(Modifier.height(8.dp));Button(onClick={vm.search(q)},modifier=Modifier.fillMaxWidth()){Text("Cari Pelanggan")}};Spacer(Modifier.height(12.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){items(vm.customers){c->GlassCard(Modifier.fillMaxWidth()){Text(c.name?:"Tanpa nama",color=Color.White,fontWeight=FontWeight.Bold);Text(c.customerNo,color=Color.White.copy(.72f));Text(c.address?:"Alamat belum tersedia",color=Color.White.copy(.62f));Text("${c.ulp?.name?:"ULP —"} • ${c.meterNo?:"Meter —"}",color=Color(0xFF9BD7FF),style=MaterialTheme.typography.bodySmall)}}}}}}

@Composable private fun BillingList(vm:MainViewModel){LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)){items(vm.billings){b->GlassCard(Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(b.customer.name?:b.customer.customerNo,color=Color.White,fontWeight=FontWeight.Bold);Text("${b.customer.customerNo} • ${b.period}",color=Color.White.copy(.65f),style=MaterialTheme.typography.bodySmall)};Text(formatRupiah(b.total),color=Color(0xFFFFD54F),fontWeight=FontWeight.Bold)};Spacer(Modifier.height(6.dp));Text("${b.category} • Jatuh tempo ${b.dueDate.take(10)}",color=Color.White.copy(.7f))}}}}

@Composable private fun PayScreen(vm:MainViewModel){var no by rememberSaveable{mutableStateOf("")};Column(Modifier.fillMaxSize().padding(16.dp)){GlassCard(Modifier.fillMaxWidth()){Text("Cek Tagihan PLN",color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));OutlinedTextField(no,{no=it},label={Text("ID Pelanggan")},modifier=Modifier.fillMaxWidth(),singleLine=true);Spacer(Modifier.height(8.dp));Button(onClick={vm.checkInquiry(no)},modifier=Modifier.fillMaxWidth(),enabled=no.length>=6){Text("Inquiry Demo")}};vm.inquiry?.let{r->Spacer(Modifier.height(12.dp));GlassCard(Modifier.fillMaxWidth()){Text(r.customer.name?:"Pelanggan",color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(r.customer.customerNo,color=Color.White.copy(.7f));Spacer(Modifier.height(10.dp));Text("Total Tagihan",color=Color.White.copy(.7f));Text(formatRupiah(r.billing.amount),color=Color(0xFFFFD54F),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));Button(onClick={},modifier=Modifier.fillMaxWidth()){Text("Pembayaran Demo")}}}}}

@Composable private fun Profile(vm:MainViewModel){Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){GlassCard(Modifier.fillMaxWidth()){Text(vm.user?.name?:"Petugas",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(vm.user?.email?:"",color=Color.White.copy(.7f));Text(vm.user?.role?:"BILLER",color=Color(0xFFFFD54F),fontWeight=FontWeight.Bold)};GlassCard(Modifier.fillMaxWidth()){Text("Mode Review",color=Color.White,fontWeight=FontWeight.Bold);Text("Database pelanggan berasal dari master Excel yang disinkronkan ke Supabase review.",color=Color.White.copy(.7f))};Button(onClick={vm.logout()},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFB3261E))){Text("Keluar")}}}

private fun formatRupiah(v:Double)=java.text.NumberFormat.getCurrencyInstance(java.util.Locale("id","ID")).apply{maximumFractionDigits=0;minimumFractionDigits=0}.format(v)
