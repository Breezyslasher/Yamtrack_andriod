package com.yamtrack.app.ui.library

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamtrack.app.data.model.MediaItem
import com.yamtrack.app.data.model.MediaStatus
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: YamtrackRepository
) : ViewModel() {

    private val _mediaList = MutableLiveData<Result<List<MediaItem>>>()
    val mediaList: LiveData<Result<List<MediaItem>>> = _mediaList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private var currentMediaType: MediaType = MediaType.MOVIE
    private var currentStatus: MediaStatus? = null   // null == ALL

    init {
        loadMedia()
    }

    fun setMediaType(type: MediaType) {
        if (currentMediaType != type) {
            currentMediaType = type
            loadMedia()
        }
    }

    fun setStatus(status: MediaStatus?) {
        if (currentStatus != status) {
            currentStatus = status
            loadMedia()
        }
    }

    fun refresh() {
        loadMedia()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            _mediaList.value = Result.Loading

            val result = repository.getMediaByType(
                mediaType = currentMediaType,
                status = currentStatus,
                limit = 100
            )
            _mediaList.value = result
            _isLoading.value = false
        }
    }
}
