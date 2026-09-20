package dev.kamlendu.preptracker.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "account")

data class Account(
    val token: String? = null,
    val userId: String? = null,
    val email: String? = null,
    val name: String? = null,
    /** Server clock at the last successful sync; the next pull asks for changes after it. */
    val lastSyncAt: Long = 0,
) {
    val signedIn: Boolean get() = !token.isNullOrBlank()
}

/**
 * Where the signed-in account lives on the phone.
 *
 * The token is the only credential stored — the password is used once, at sign-in, and never
 * written down.
 */
class AuthStore(private val context: Context) {
    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val NAME = stringPreferencesKey("name")
        val LAST_SYNC = longPreferencesKey("last_sync_at")
    }

    val account: Flow<Account> = context.authDataStore.data.map { p ->
        Account(
            token = p[Keys.TOKEN],
            userId = p[Keys.USER_ID],
            email = p[Keys.EMAIL],
            name = p[Keys.NAME],
            lastSyncAt = p[Keys.LAST_SYNC] ?: 0,
        )
    }

    suspend fun current(): Account = account.first()

    suspend fun signIn(token: String, userId: String, email: String, name: String?) {
        context.authDataStore.edit { p ->
            p[Keys.TOKEN] = token
            p[Keys.USER_ID] = userId
            p[Keys.EMAIL] = email
            if (name != null) p[Keys.NAME] = name else p.remove(Keys.NAME)
            // A fresh sign-in pulls the account's whole history, so the cursor starts at zero.
            p[Keys.LAST_SYNC] = 0
        }
    }

    suspend fun setLastSync(serverTime: Long) {
        context.authDataStore.edit { it[Keys.LAST_SYNC] = serverTime }
    }

    suspend fun signOut() {
        context.authDataStore.edit { it.clear() }
    }
}
