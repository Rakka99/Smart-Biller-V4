package id.bmax.app.domain

/** Runtime source selection: Sheets is temporary/dev, Supabase is production. */
enum class DataSourceMode { GOOGLE_SHEETS, SUPABASE }

enum class UserRole { ADMIN, SUPERVISOR, BILLER }

enum class RbmCode { A, B, C, D, E }

enum class BillingStatus { UNPAID, PENDING, PAID, FAILED }

enum class BillingCategory { PREVENTIF, KOREKTIF, IRISAN }

enum class PaymentStatus { PENDING, SUCCESS, FAILED, UNKNOWN }

enum class PdilStatus { DRAFT, SUBMITTED, VERIFIED, APPROVED, REJECTED, SYNCED }

data class OrganizationScope(
    val regionId: String? = null,
    val ulpId: String? = null,
    val billerId: String? = null,
    val rbmId: String? = null,
)

data class CustomerRecord(
    val id: String,
    val idpel: String,
    val name: String,
    val address: String,
    val tariff: String,
    val powerVa: Int,
    val regionId: String,
    val ulpId: String,
    val billerId: String,
    val rbmId: String,
    val rbmCode: RbmCode,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class BillingRecord(
    val id: String,
    val customerId: String,
    val period: String,
    val amount: Double,
    val adminFee: Double,
    val penalty: Double,
    val total: Double,
    val dueDate: String,
    val status: BillingStatus,
    val category: BillingCategory,
)

data class PaymentRecord(
    val id: String,
    val refId: String,
    val inquiryId: String,
    val customerId: String,
    val billerId: String,
    val rbmId: String,
    val period: String,
    val amount: Double,
    val adminFee: Double,
    val penalty: Double,
    val total: Double,
    val status: PaymentStatus,
    val iakTrId: Long? = null,
    val serialNumber: String? = null,
)

data class PdilRecord(
    val id: String,
    val customerId: String,
    val fieldName: String,
    val oldValue: String?,
    val newValue: String?,
    val status: PdilStatus,
    val notes: String? = null,
)

interface CustomerRepository {
    suspend fun search(query: String): List<CustomerRecord>
    suspend fun observeScope(scope: OrganizationScope): List<CustomerRecord>
}

interface BillingRepository {
    suspend fun observeCustomer(customerId: String): List<BillingRecord>
    suspend fun observeScope(scope: OrganizationScope): List<BillingRecord>
}

interface PaymentRepository {
    suspend fun createInquiry(customerId: String, period: String): Result<String>
    suspend fun pay(refId: String): Result<PaymentRecord>
    suspend fun checkStatus(refId: String): Result<PaymentStatus>
}

interface InvoiceRepository {
    suspend fun createForPayment(paymentId: String): Result<String>
}

interface PdilRepository {
    suspend fun create(record: PdilRecord): Result<PdilRecord>
    suspend fun approve(id: String): Result<PdilRecord>
}

interface LeaderboardRepository {
    suspend fun ranking(scope: OrganizationScope, period: String): List<Pair<String, Int>>
}
