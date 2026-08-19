import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import axios from "axios";
import "./styles.css";

const API = "https://vgnynrzhanfnbifjedga.supabase.co/functions/v1/smart-biller-api";
const api = axios.create({ baseURL: API });

type Billing = { id:string; period:string; category:string; status:string; total:number; dueDate:string; customer:any };
type Payment = { refId:string; status:string; sellingPrice?:number; createdAt:string; customer:{customerNo:string;name?:string} };
const money=(n:number|undefined|null)=>new Intl.NumberFormat("id-ID",{style:"currency",currency:"IDR",maximumFractionDigits:0}).format(n??0);

function App(){
  const [token,setToken]=useState(localStorage.getItem("token"));
  const [user,setUser]=useState<any>(()=>JSON.parse(localStorage.getItem("user")||"null"));
  const [email,setEmail]=useState("admin@example.com"),[password,setPassword]=useState("change-me-now");
  const [tab,setTab]=useState("home"),[summary,setSummary]=useState<any>(null),[billings,setBillings]=useState<Billing[]>([]);
  const [payments,setPayments]=useState<Payment[]>([]),[leader,setLeader]=useState<any[]>([]);
  const [q,setQ]=useState(""),[search,setSearch]=useState<any[]>([]),[error,setError]=useState(""),[busy,setBusy]=useState(false);
  const [customerNo,setCustomerNo]=useState(""),[inquiryResult,setInquiryResult]=useState<any>(null);

  useEffect(()=>{if(token){api.defaults.headers.common.Authorization=`Bearer ${token}`;refresh();}},[token]);
  async function login(e:React.FormEvent){e.preventDefault();setError("");try{const {data}=await api.post("/auth/login",{email,password});localStorage.setItem("token",data.token);localStorage.setItem("user",JSON.stringify(data.user));setToken(data.token);setUser(data.user);}catch(e:any){setError(e.response?.data?.error||"Login gagal")}}
  async function refresh(){try{const [s,b,p,l]=await Promise.all([api.get("/billing/summary"),api.get("/billing?status=UNPAID"),api.get("/payments?limit=8"),api.get("/leaderboard")]);setSummary(s.data);setBillings(b.data.items);setPayments(p.data.items);setLeader(l.data.rows)}catch(e:any){setError(e.response?.data?.error||"Gagal memuat data")}}
  async function doSearch(){if(q.length<2)return;try{const {data}=await api.get("/customers/search",{params:{q}});setSearch(data.items)}catch(e:any){setError(e.response?.data?.error||"Pencarian gagal")}}
  async function checkInquiry(){setBusy(true);setError("");try{const {data}=await api.post("/pln/inquiry",{customerNo});setInquiryResult(data);await refresh()}catch(e:any){setError(e.response?.data?.error||"Inquiry gagal")}finally{setBusy(false)}}
  async function pay(){if(!inquiryResult?.inquiry?.id||!confirm("Lanjutkan pembayaran?"))return;setBusy(true);try{await api.post("/payments",{inquiryId:inquiryResult.inquiry.id});await refresh();alert("Pembayaran diproses")}catch(e:any){setError(e.response?.data?.error||"Pembayaran gagal")}finally{setBusy(false)}}

  if(!token)return <main className="login"><form className="login-card" onSubmit={login}><div className="brand">⚡ <b>PLN Monitoring</b></div><p>Masuk ke sistem operasional.</p><input value={email} onChange={e=>setEmail(e.target.value)} type="email" placeholder="Email"/><input value={password} onChange={e=>setPassword(e.target.value)} type="password" placeholder="Password"/>{error&&<div className="error">{error}</div>}<button>Masuk</button></form></main>;

  const cards=[["PREVENTIF",summary?.preventif??0,"1–20 bulan","preventif"],["KOREKTIF",summary?.korektif??0,"21–akhir bulan","korektif"],["IRISAN",summary?.irisan??0,"lintas bulan","irisan"]];
  return <main className="app">
    <header className="top"><div><div className="brand">⚡ PLN Monitoring</div><small>{user?.name} · {user?.role}</small></div><button className="icon" onClick={()=>{localStorage.clear();setToken(null)}}>↪</button></header>
    {error&&<div className="error">{error}</div>}
    {tab==="home"&&<><section className="hero"><span>Periode berjalan</span><strong>{summary?.period||"—"}</strong><em>{summary?.category||"—"}</em></section><section className="cards">{cards.map(c=><div className={`metric ${c[3]}`} key={c[0] as string}><span>{c[0]}</span><strong>{c[1]}</strong><small>{c[2]}</small></div>)}</section><section className="section"><div className="section-title"><h2>Prioritas tunggakan</h2><button className="link" onClick={()=>setTab("billing")}>Lihat semua</button></div>{billings.slice(0,5).map(b=><div className="list" key={b.id}><div><b>{b.customer.name||b.customer.customerNo}</b><small>{b.customer.customerNo} · {b.customer.ulp?.name||"ULP —"}</small></div><div><span className={`pill ${b.category.toLowerCase()}`}>{b.category}</span><b>{money(b.total)}</b></div></div>)}</section></>}
    {tab==="billing"&&<><section className="section"><div className="section-title"><h2>Monitoring Tagihan</h2><button className="icon" onClick={refresh}>↻</button></div><div className="filter-row">{["PREVENTIF","KOREKTIF","IRISAN"].map(x=><button key={x} className="chip" onClick={async()=>{const {data}=await api.get("/billing",{params:{category:x,status:"UNPAID"}});setBillings(data.items)}}>{x}</button>)}</div>{billings.map(b=><div className="list" key={b.id}><div><b>{b.customer.name||b.customer.customerNo}</b><small>{b.customer.customerNo} · {b.period} · jatuh tempo {new Date(b.dueDate).toLocaleDateString("id-ID")}</small></div><div><span className={`pill ${b.category.toLowerCase()}`}>{b.category}</span><b>{money(b.total)}</b></div></div>)}</section></>}
    {tab==="pln"&&<section className="section"><h2>Cek Tagihan PLN</h2><div className="search-box"><input value={customerNo} onChange={e=>setCustomerNo(e.target.value.replace(/\D/g,""))} placeholder="ID Pelanggan"/><button disabled={busy||customerNo.length<6} onClick={checkInquiry}>{busy?"...":"Cek"}</button></div>{inquiryResult?.customer&&<div className="invoice-card"><h3>{inquiryResult.customer.name||"Pelanggan"}</h3><p>{inquiryResult.customer.customerNo} · Meter {inquiryResult.customer.meterNo||"—"}</p>{inquiryResult.billing&&<><div className="total">{money(inquiryResult.billing.selling_price)}</div><p>Periode {inquiryResult.billing.periode||"—"}</p></>}<button onClick={pay}>Bayar Sekarang</button></div>}</section>}
    {tab==="search"&&<section className="section"><h2>Pencarian Pelanggan</h2><div className="search-box"><input value={q} onChange={e=>setQ(e.target.value)} placeholder="ID, meter, nama, alamat"/><button onClick={doSearch}>Cari</button></div>{search.map(c=><div className="list" key={c.id}><div><b>{c.name||"—"}</b><small>{c.customerNo} · {c.ulp?.name||"ULP —"} · {c.address||"Alamat belum diisi"}</small></div><span className="map-dot">{c.latitude&&c.longitude?"📍":"—"}</span></div>)}</section>}
    {tab==="leader"&&<section className="section"><h2>Leaderboard Petugas</h2><p className="muted">Ranking utama berdasarkan <b>pelanggan unik terselesaikan</b>.</p>{leader.map((r,i)=><div className="rank" key={r.userId}><strong>{i+1}</strong><div><b>{r.name}</b><small>{r.ulp||"ULP —"} · {r.region||"Wilayah —"}</small></div><div><b>{r.uniqueCustomers}</b><small>pelanggan unik</small></div></div>)}</section>}
    <nav className="bottom"><button onClick={()=>setTab("home")} className={tab==="home"?"active":""}>⌂<small>Home</small></button><button onClick={()=>setTab("billing")} className={tab==="billing"?"active":""}>⚡<small>Tagihan</small></button><button onClick={()=>setTab("pln")} className={tab==="pln"?"active":""}>₿<small>Bayar</small></button><button onClick={()=>setTab("search")} className={tab==="search"?"active":""}>⌕<small>Pelanggan</small></button><button onClick={()=>setTab("leader")} className={tab==="leader"?"active":""}>♛<small>Leader</small></button></nav>
  </main>
}
createRoot(document.getElementById("root")!).render(<React.StrictMode><App/></React.StrictMode>);
