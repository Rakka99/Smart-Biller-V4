package id.smartbiller.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseProfile(
    val id: String,
    @SerialName("full_name") val fullName: String = "",
    val role: String = "BILLER",
    val region: String? = null,
    val ulp: String? = null,
    val rbm: String? = null,
    val phone: String? = null,
)

data class SupabaseLoginResult(
    val accessToken: String,
    val profile: SupabaseProfile,
)

class SupabaseAuthRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    suspend fun login(email: String, password: String): Result<SupabaseLoginResult> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }

        val session = client.auth.currentSessionOrNull()
            ?: error("Supabase session tidak tersedia")

        val profile = client
            .from("profiles")
            .select {
                filter {
                    eq("id", session.user?.id ?: error("User ID tidak tersedia"))
                }
            }
            .decodeSingle<SupabaseProfile>()

        SupabaseLoginResult(
            accessToken = session.accessToken,
            profile = profile,
        )
    }

    suspend fun currentProfile(): Result<SupabaseProfile> = runCatching {
        val userId = client.auth.currentSessionOrNull()?.user?.id
            ?: error("Belum login")

        client
            .from("profiles")
            .select {
                filter { eq("id", userId) }
            }
            .decodeSingle<SupabaseProfile>()
    }

    suspend fun logout() {
        client.auth.signOut()
    }

    fun accessToken(): String? = client.auth.currentSessionOrNull()?.accessToken
}
