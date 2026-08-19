package id.smartbiller.app.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

private const val BASE_URL = "https://vgnynrzhanfnbifjedga.supabase.co/functions/v1/smart-biller-native-api/"

interface SmartBillerApi {
    @POST("auth/login")
    suspend fun login(@Body body: Map<String, String>): LoginResponse

    @GET("health")
    suspend fun health(): Map<String, Any>

    @GET("billing/summary")
    suspend fun summary(): BillingSummary

    @GET("billing")
    suspend fun billing(
        @Query("status") status: String = "UNPAID",
        @Query("category") category: String? = null,
    ): BillingPage

    @GET("customers/search")
    suspend fun search(@Query("q") q: String): CustomerPage

    @GET("leaderboard")
    suspend fun leaderboard(): Leaderboard

    @POST("pln/inquiry")
    suspend fun inquiry(@Body body: InquiryRequest): InquiryResponse

    @POST("payments")
    suspend fun pay(@Body body: PaymentRequest): PaymentResponse
}

class ApiProvider {
    fun create(tokenProvider: () -> String?): SmartBillerApi {
        val auth = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")

            tokenProvider()
                ?.takeIf { it.isNotBlank() }
                ?.let { request.header("Authorization", "Bearer $it") }

            chain.proceed(request.build())
        }

        val log = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(log)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SmartBillerApi::class.java)
    }
}
