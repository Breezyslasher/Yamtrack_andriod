package com.yamtrack.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.yamtrack.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "yamtrack_prefs")

/**
 * Manages user preferences and auth state.
 *
 * The API token is the only secret here, so it lives in
 * EncryptedSharedPreferences (AES256, key wrapped by the Android Keystore)
 * rather than the plaintext DataStore that holds non-sensitive settings.
 */
@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val API_TOKEN = stringPreferencesKey("api_token") // legacy (pre-encryption)
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val SHOW_ADULT_CONTENT = booleanPreferencesKey("show_adult_content")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val DEFAULT_MEDIA_TYPE = stringPreferencesKey("default_media_type")
        val HIDE_UNWATCHED_EPISODE_INFO = booleanPreferencesKey("hide_unwatched_episode_info")
    }

    private companion object {
        const val SECURE_FILE = "yamtrack_secure_prefs"
        const val TOKEN_KEY = "api_token"
    }

    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _apiToken = MutableStateFlow<String?>(null)
    val apiToken: Flow<String?> = _apiToken.asStateFlow()

    init {
        // One-time migration: move any legacy plaintext token out of the
        // DataStore into the encrypted store, then scrub the old copy.
        runBlocking {
            val existing = securePrefs.getString(TOKEN_KEY, null)
            if (existing != null) {
                _apiToken.value = existing
            } else {
                val legacy = runCatching {
                    context.dataStore.data.first()[Keys.API_TOKEN]
                }.getOrNull()
                if (!legacy.isNullOrBlank()) {
                    securePrefs.edit().putString(TOKEN_KEY, legacy).apply()
                    _apiToken.value = legacy
                    context.dataStore.edit { it.remove(Keys.API_TOKEN) }
                }
            }
        }
    }

    val serverUrl: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.SERVER_URL] ?: BuildConfig.DEFAULT_SERVER_URL }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.IS_LOGGED_IN] ?: false }

    val showAdultContent: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.SHOW_ADULT_CONTENT] ?: false }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.NOTIFICATIONS_ENABLED] ?: true }

    val defaultMediaType: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.DEFAULT_MEDIA_TYPE] ?: "movie" }

    val hideUnwatchedEpisodeInfo: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[Keys.HIDE_UNWATCHED_EPISODE_INFO] ?: false }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[Keys.SERVER_URL] = url.trimEnd('/') }
    }

    fun setApiToken(token: String?) {
        if (token.isNullOrBlank()) {
            securePrefs.edit().remove(TOKEN_KEY).apply()
            _apiToken.value = null
        } else {
            securePrefs.edit().putString(TOKEN_KEY, token).apply()
            _apiToken.value = token
        }
    }

    suspend fun setLoggedIn(loggedIn: Boolean) {
        context.dataStore.edit { it[Keys.IS_LOGGED_IN] = loggedIn }
    }

    suspend fun setShowAdultContent(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_ADULT_CONTENT] = show }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setDefaultMediaType(type: String) {
        context.dataStore.edit { it[Keys.DEFAULT_MEDIA_TYPE] = type }
    }

    suspend fun setHideUnwatchedEpisodeInfo(hide: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_UNWATCHED_EPISODE_INFO] = hide }
    }

    suspend fun clearSession() {
        setApiToken(null)
        context.dataStore.edit { it[Keys.IS_LOGGED_IN] = false }
    }

    suspend fun clearAll() {
        securePrefs.edit().clear().apply()
        _apiToken.value = null
        context.dataStore.edit { it.clear() }
    }
}
