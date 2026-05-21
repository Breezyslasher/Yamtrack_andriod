package com.yamtrack.app.ui.settings

import androidx.lifecycle.*
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.model.UserStats
import com.yamtrack.app.data.repository.PreferencesManager
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: YamtrackRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _serverUrl = MutableLiveData<String>()
    val serverUrl: LiveData<String> = _serverUrl

    private val _showAdultContent = MutableLiveData<Boolean>()
    val showAdultContent: LiveData<Boolean> = _showAdultContent

    private val _notificationsEnabled = MutableLiveData<Boolean>()
    val notificationsEnabled: LiveData<Boolean> = _notificationsEnabled

    private val _hideUnwatchedEpisodeInfo = MutableLiveData<Boolean>()
    val hideUnwatchedEpisodeInfo: LiveData<Boolean> = _hideUnwatchedEpisodeInfo

    private val _logoutComplete = MutableLiveData<Boolean>()
    val logoutComplete: LiveData<Boolean> = _logoutComplete

    private val _serverInfo = MutableLiveData<String>()
    val serverInfo: LiveData<String> = _serverInfo

    init {
        loadSettings()
        loadServerInfo()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _serverUrl.value = preferencesManager.serverUrl.first()
            _showAdultContent.value = preferencesManager.showAdultContent.first()
            _notificationsEnabled.value = preferencesManager.notificationsEnabled.first()
            _hideUnwatchedEpisodeInfo.value =
                preferencesManager.hideUnwatchedEpisodeInfo.first()
        }
    }

    private fun loadServerInfo() {
        viewModelScope.launch {
            when (val result = repository.getAppInfo()) {
                is Result.Success -> {
                    val info = result.data
                    _serverInfo.value = buildString {
                        append("Yamtrack")
                        info.version?.takeIf { it.isNotBlank() }?.let { append(" v").append(it) }
                        info.timezone?.takeIf { it.isNotBlank() }?.let { append(" • ").append(it) }
                    }
                }
                is Result.Error -> {
                    _serverInfo.value = "Offline: ${result.message}"
                }
                else -> { /* No-op */ }
            }
        }
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch {
            val cleaned = url.trimEnd('/')
            preferencesManager.setServerUrl(cleaned)
            repository.setServerUrl(cleaned)
            _serverUrl.value = cleaned
        }
    }

    fun setShowAdultContent(show: Boolean) {
        viewModelScope.launch {
            preferencesManager.setShowAdultContent(show)
            _showAdultContent.value = show
        }
    }

    fun setHideUnwatchedEpisodeInfo(hide: Boolean) {
        viewModelScope.launch {
            preferencesManager.setHideUnwatchedEpisodeInfo(hide)
            _hideUnwatchedEpisodeInfo.value = hide
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setNotificationsEnabled(enabled)
            _notificationsEnabled.value = enabled
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.setToken(null)
            preferencesManager.clearSession()
            _logoutComplete.value = true
        }
    }

    /**
     * One-shot stats fetch for the dialog. The home screen no longer renders
     * stats, so this VM owns the lookup. Returns Loading first, then either
     * Success(stats) or Error(message).
     */
    private val _stats = MutableLiveData<Result<UserStats>>()
    val stats: LiveData<Result<UserStats>> = _stats

    fun loadStats() {
        viewModelScope.launch {
            _stats.value = Result.Loading
            // start_date=all & end_date=all disables the default 1-year
            // window; otherwise Planning items (no start/progress date)
            // fall outside it and report as 0.
            _stats.value = repository.getStatistics(startDate = "all", endDate = "all")
        }
    }
}
