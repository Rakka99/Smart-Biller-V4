package id.smartbiller.app.data

import io.github.jan.supabase.SupabaseClient
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

class SupabaseAuthRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
) {
    suspend fun login(email: String, password: String): Result<SupabaseLoginResult> = runCatching {
        val auth = client.auth
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }

        val session = auth.currentSessionOrNull()
            ?: error("Supabase session tidak tersedia")

        val userId = session.user?.id ?: error("User ID tidak tersedia")
        val profile = client
            .from("profiles")
            .select()
            .decodeSingle<SupabaseProfile>()

        if (profile.id != userId) {
            error("Profil Supabase tidak sesuai dengan pengguna yang login")
        }

        SupabaseLoginResult(
            accessToken = session.accessToken,
            profile = profile,
        )
    }

    suspend fun currentProfile(): Result<SupabaseProfile> = runCatching {
        val userId = client.auth.currentSessionOrNull()?.user?.id
            ?: error("Belum login")

        val profile = client
            .from("profiles")
            .select()
            .decodeSingle<SupabaseProfile>()

        if (profile.id != userId) {
            error("Profil Supabase tidak sesuai dengan pengguna yang login")
        }
        profile
    }

    suspend fun logout() {
        client.auth.signOut()
    }

    fun accessToken(): String? = client.auth.currentSessionOrNull()?.accessToken
}
