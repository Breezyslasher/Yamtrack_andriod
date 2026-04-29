package com.yamtrack.app.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamtrack.app.BuildConfig
import com.yamtrack.app.data.model.AuthResult
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.repository.PreferencesManager
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * LoginViewModel handles authentication against the Yamtrack REST API.
 * 
 * The API (PR #924) uses Bearer token auth. There are two entry points:
 *   1. Paste an API token directly (recommended — copy from web UI)
 *   2. Log in with username/password — the app will try to scrape the
 *      token off the profile page as a convenience for the demo server
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: YamtrackRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _loginState = MutableLiveData<LoginState>(LoginState.Idle)
    val loginState: LiveData<LoginState> = _loginState

    private val _serverUrl = MutableLiveData(BuildConfig.DEFAULT_SERVER_URL)
    val serverUrl: LiveData<String> = _serverUrl

    init {
        loadSavedServerUrl()
    }

    private fun loadSavedServerUrl() {
        viewModelScope.launch {
            val savedUrl = preferencesManager.serverUrl.first()
            _serverUrl.value = savedUrl
            repository.setServerUrl(savedUrl)
        }
    }

    fun setServerUrl(url: String) {
        val cleaned = url.trimEnd('/')
        _serverUrl.value = cleaned
        repository.setServerUrl(cleaned)
        viewModelScope.launch {
            preferencesManager.setServerUrl(cleaned)
        }
    }

    /**
     * Log in by providing an API token directly.
     * This is the recommended flow — users can generate a token in the
     * Yamtrack web UI profile page.
     */
    fun loginWithToken(serverUrl: String, token: String) {
        if (serverUrl.isBlank()) {
            _loginState.value = LoginState.Error("Please enter a server URL")
            return
        }
        if (token.isBlank()) {
            _loginState.value = LoginState.Error("Please enter an API token")
            return
        }

        val cleanUrl = serverUrl.trimEnd('/')
        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            when (val result = repository.testConnection(cleanUrl, token)) {
                is AuthResult.Success -> {
                    // Persist everything
                    preferencesManager.setServerUrl(cleanUrl)
                    preferencesManager.setApiToken(token)
                    preferencesManager.setLoggedIn(true)
                    _loginState.value = LoginState.Success
                }
                is AuthResult.Error -> {
                    _loginState.value = LoginState.Error(result.message)
                }
            }
        }
    }

    /**
     * Log in with username/password (convenience flow for demo server).
     * This attempts session login, then scrapes the API token from /profile/.
     * If the scrape fails, the user must paste the token manually.
     */
    fun loginWithPassword(serverUrl: String, username: String, password: String) {
        if (serverUrl.isBlank()) {
            _loginState.value = LoginState.Error("Please enter a server URL")
            return
        }
        if (username.isBlank()) {
            _loginState.value = LoginState.Error("Please enter a username")
            return
        }
        if (password.isBlank()) {
            _loginState.value = LoginState.Error("Please enter a password")
            return
        }

        val cleanUrl = serverUrl.trimEnd('/')
        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            when (val tokenResult = repository.loginWithPassword(cleanUrl, username, password)) {
                is Result.Success -> {
                    // Got a token — validate it against the API
                    val token = tokenResult.data
                    when (val authResult = repository.testConnection(cleanUrl, token)) {
                        is AuthResult.Success -> {
                            preferencesManager.setServerUrl(cleanUrl)
                            preferencesManager.setApiToken(token)
                            preferencesManager.setLoggedIn(true)
                            _loginState.value = LoginState.Success
                        }
                        is AuthResult.Error -> {
                            _loginState.value = LoginState.Error(
                                "Logged in but token didn't work: ${authResult.message}"
                            )
                        }
                    }
                }
                is Result.Error -> {
                    _loginState.value = LoginState.Error(tokenResult.message)
                }
                else -> { /* Loading — ignore */ }
            }
        }
    }

    /**
     * Check if we have a saved session, and if so, validate it.
     */
    fun checkExistingSession() {
        viewModelScope.launch {
            val isLoggedIn = preferencesManager.isLoggedIn.first()
            if (!isLoggedIn) return@launch

            val token = preferencesManager.apiToken.first() ?: return@launch
            val serverUrl = preferencesManager.serverUrl.first()

            repository.setServerUrl(serverUrl)
            repository.setToken(token)

            when (repository.testConnection(serverUrl, token)) {
                is AuthResult.Success -> {
                    _loginState.value = LoginState.Success
                }
                is AuthResult.Error -> {
                    preferencesManager.clearSession()
                }
            }
        }
    }
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}
