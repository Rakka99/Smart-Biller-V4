package id.smartbiller.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
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

/**
 * Supabase repository isolated behind a small API surface.
 */
class SupabaseAuthRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    private val authModule: Auth
        get() = client.pluginManager.getPlugin(Auth)

    suspend fun login(email: String, password: String): Result<SupabaseLoginResult> = runCatching {
        authModule.signInWith(Email) {
            this.email = email
            this.password = password
        }

        val session = authModule.currentSessionOrNull()
            ?: error("Supabase session tidak tersedia")
        val userId = session.user?.id ?: error("User ID tidak tersedia")

        val profile = client
            .from("profiles")
            .select {
                filter { eq("id", userId) }
            }
            .decodeSingle<SupabaseProfile>()

        SupabaseLoginResult(
            accessToken = session.accessToken,
            profile = profile,
        )
    }

    suspend fun currentProfile(): Result<SupabaseProfile> = runCatching {
        val userId = authModule.currentSessionOrNull()?.user?.id
            ?: error("Belum login")

        client
            .from("profiles")
            .select {
                filter { eq("id", userId) }
            }
            .decodeSingle<SupabaseProfile>()
    }

    suspend fun logout() {
        authModule.signOut()
    }

    fun accessToken(): String? = authModule.currentSessionOrNull()?.accessToken
}
