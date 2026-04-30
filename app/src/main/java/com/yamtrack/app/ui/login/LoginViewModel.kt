package com.yamtrack.app.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamtrack.app.BuildConfig
import com.yamtrack.app.data.model.AuthResult
import com.yamtrack.app.data.repository.PreferencesManager
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * LoginViewModel handles authentication against the Yamtrack REST API.
 *
 * The API only supports Bearer-token / X-API-Key auth (see
 * `src/api/authentication.py` on the `feat/add-api` branch). There is no
 * password-grant endpoint, so the only supported flow is for the user to
 * paste an API token copied from the Yamtrack web UI profile page.
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

    /** Validate (server, token) by hitting an authenticated endpoint. */
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

    /** Validate any saved session silently on startup. */
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
