package id.smartbiller.app.data

import id.bmax.app.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Single Android entry point for the Supabase client.
 * Only the publishable/anon key is allowed here; service-role credentials stay on the backend.
 */
object SupabaseClientProvider {
    val client by lazy {
        require(BuildConfig.SUPABASE_URL.isNotBlank()) { "SUPABASE_URL is not configured" }
        require(BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) { "SUPABASE_PUBLISHABLE_KEY is not configured" }

        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Auth)
            install(Postgrest)
        }
    }

    /** Installed Auth module exposed through one stable provider API. */
    val auth get() = client.auth
}
