package com.yamtrack.app.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamtrack.app.data.model.MediaItem
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.model.UserStats
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: YamtrackRepository
) : ViewModel() {

    private val _stats = MutableLiveData<Result<UserStats>>()
    val stats: LiveData<Result<UserStats>> = _stats

    private val _recentMedia = MutableLiveData<Result<List<MediaItem>>>()
    val recentMedia: LiveData<Result<List<MediaItem>>> = _recentMedia

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true

            // Load statistics and recent media in parallel-ish
            _stats.value = repository.getStatistics()
            _recentMedia.value = repository.getAllMedia(limit = 12)

            _isLoading.value = false
        }
    }
}
