package id.smartbiller.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "smart_biller_session")

class SessionStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("token")
    private val emailKey = stringPreferencesKey("email")
    private val nameKey = stringPreferencesKey("name")
    private val roleKey = stringPreferencesKey("role")
    val token: Flow<String?> = context.sessionDataStore.data.map { it[tokenKey] }
    val user: Flow<User?> = context.sessionDataStore.data.map { p ->
        val email = p[emailKey] ?: return@map null
        User(email, email, p[nameKey] ?: "", p[roleKey] ?: "BILLER")
    }
    suspend fun save(response: LoginResponse) { context.sessionDataStore.edit { p -> p[tokenKey]=response.token; p[emailKey]=response.user.email; p[nameKey]=response.user.name; p[roleKey]=response.user.role } }
    suspend fun clear() { context.sessionDataStore.edit { it.clear() } }
}
