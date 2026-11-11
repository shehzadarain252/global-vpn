package com.globalvpn.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "global_vpn_prefs")

/**
 * Local preferences (DataStore). Stores only settings and caches — the
 * credit balance is authoritative on the backend, never here.
 *
 * The last WireGuard config (including the client private key) is stored
 * here so the app can auto-reconnect after a reboot when the user enabled
 * it. This matches how the official WireGuard app stores tunnel configs
 * on-device. The config is never uploaded anywhere.
 */
class Preferences(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val KEY_GUEST_ID = stringPreferencesKey("guest_id")
        private val KEY_ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        private val KEY_THEME = stringPreferencesKey("theme") // SYSTEM, LIGHT, DARK
        private val KEY_SELECTED_SERVER = stringPreferencesKey("selected_server_id")
        private val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        private val KEY_LAST_CONFIG = stringPreferencesKey("last_wireguard_config")
        private val KEY_LAST_SERVER_NAME = stringPreferencesKey("last_server_name")
        private val KEY_LAST_SERVER_CODE = stringPreferencesKey("last_server_code")
        private val KEY_WELCOME_CLAIMED = booleanPreferencesKey("welcome_claimed")
        private val KEY_HISTORY = stringPreferencesKey("connection_history_json")
    }

    suspend fun guestId(): String {
        val existing = context.dataStore.data.map { it[KEY_GUEST_ID] }.first()
        if (!existing.isNullOrBlank()) return existing
        val fresh = UUID.randomUUID().toString()
        context.dataStore.edit { it[KEY_GUEST_ID] = fresh }
        return fresh
    }

    val onboardingSeen: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ONBOARDING_SEEN] ?: false }

    suspend fun setOnboardingSeen(seen: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_SEEN] = seen }
    }

    val theme: Flow<String> =
        context.dataStore.data.map { it[KEY_THEME] ?: "SYSTEM" }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[KEY_THEME] = theme }
    }

    val selectedServerId: Flow<String?> =
        context.dataStore.data.map { it[KEY_SELECTED_SERVER] }

    suspend fun setSelectedServerId(id: String?) {
        context.dataStore.edit {
            if (id == null) it.remove(KEY_SELECTED_SERVER) else it[KEY_SELECTED_SERVER] = id
        }
    }

    val autoReconnect: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUTO_RECONNECT] ?: true }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_RECONNECT] = enabled }
    }

    /** Wipes all local preferences (used by Delete Account). */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    suspend fun saveLastConfig(config: String, serverName: String, serverCode: String) {
        context.dataStore.edit {
            it[KEY_LAST_CONFIG] = config
            it[KEY_LAST_SERVER_NAME] = serverName
            it[KEY_LAST_SERVER_CODE] = serverCode
        }
    }

    suspend fun lastConfig(): String? =
        context.dataStore.data.map { it[KEY_LAST_CONFIG] }.first()

    suspend fun lastServerName(): String? =
        context.dataStore.data.map { it[KEY_LAST_SERVER_NAME] }.first()

    suspend fun lastServerCode(): String? =
        context.dataStore.data.map { it[KEY_LAST_SERVER_CODE] }.first()

    suspend fun clearLastConfig() {
        context.dataStore.edit {
            it.remove(KEY_LAST_CONFIG)
            it.remove(KEY_LAST_SERVER_NAME)
            it.remove(KEY_LAST_SERVER_CODE)
        }
    }

    suspend fun isWelcomeClaimed(): Boolean =
        context.dataStore.data.map { it[KEY_WELCOME_CLAIMED] ?: false }.first()

    suspend fun setWelcomeClaimed() {
        context.dataStore.edit { it[KEY_WELCOME_CLAIMED] = true }
    }

    suspend fun getHistory(): List<ConnectionSession> {
        val json = context.dataStore.data.map { it[KEY_HISTORY] }.first() ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ConnectionSession>>() {}.type
            gson.fromJson<List<ConnectionSession>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addHistoryEntry(entry: ConnectionSession) {
        val current = getHistory().toMutableList()
        current.add(0, entry)
        val trimmed = current.take(50)
        context.dataStore.edit { it[KEY_HISTORY] = gson.toJson(trimmed) }
    }
}
