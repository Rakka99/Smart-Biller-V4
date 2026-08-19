package id.smartbiller.app.data

data class LoginResponse(val token: String, val user: User)
data class User(val id: String, val email: String, val name: String, val role: String)
data class BillingSummary(val period: String, val category: String, val preventif: Int, val korektif: Int, val irisan: Int)
data class Customer(
    val id: String,
    val customerNo: String,
    val name: String?,
    val meterNo: String?,
    val address: String?,
    val ulp: ULP?,
    val latitude: Double?,
    val longitude: Double?,
    val tarif: String? = null,
    val daya: Int? = null,
    val rbm: String = "",
    val langkah: Int = 0,
    val gardu: String = "",
    val tiang: String = ""
)
data class ULP(val name: String?)
data class Billing(
    val id: String,
    val period: String,
    val category: String,
    val status: String,
    val total: Double,
    val dueDate: String,
    val sellingPrice: Double,
    val admin: Double,
    val amount: Double,
    val customer: Customer
)
data class BillingPage(val items: List<Billing> = emptyList(), val total: Int = 0, val page: Int = 1, val limit: Int = 0)
data class CustomerPage(val items: List<Customer> = emptyList())
data class LeaderRow(val userId: String, val name: String, val ulp: String?, val region: String?, val uniqueCustomers: Int)
data class Leaderboard(val rows: List<LeaderRow> = emptyList())
data class InquiryRequest(val customerNo: String)
data class PaymentRequest(val inquiryId: String)
data class InquiryResponse(val customer: Customer, val billing: InquiryBilling, val inquiry: Inquiry)
data class InquiryBilling(val periode: String, val selling_price: Double, val amount: Double, val admin: Double)
data class Inquiry(val id: String, val status: String)
data class PaymentResponse(val payment: PaymentResult, val demo: Boolean, val message: String)
data class PaymentResult(val refId: String, val status: String, val sellingPrice: Double, val createdAt: String, val customer: PaymentCustomer)
data class PaymentCustomer(val customerNo: String, val name: String?)
