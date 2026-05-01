package com.yamtrack.app.ui.library

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamtrack.app.data.model.MediaItem
import com.yamtrack.app.data.model.MediaStatus
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.repository.PreferencesManager
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: YamtrackRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _mediaList = MutableLiveData<Result<List<MediaItem>>>()
    val mediaList: LiveData<Result<List<MediaItem>>> = _mediaList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    /**
     * Currently selected media type. Backed by the DataStore-persisted
     * `default_media_type` so the Library tab restores the user's last
     * choice across navigation and process restarts.
     */
    private val _mediaType = MutableLiveData<MediaType>(MediaType.MOVIE)
    val mediaType: LiveData<MediaType> = _mediaType

    private var currentMediaType: MediaType = MediaType.MOVIE
    private var currentStatus: MediaStatus? = null   // null == ALL
    private var currentSort: String = "added_desc"

    init {
        viewModelScope.launch {
            val saved = preferencesManager.defaultMediaType.first()
            val type = MediaType.fromValue(saved) ?: MediaType.MOVIE
            currentMediaType = type
            _mediaType.value = type
            loadMedia()
        }
    }

    fun setMediaType(type: MediaType) {
        if (currentMediaType != type) {
            currentMediaType = type
            _mediaType.value = type
            viewModelScope.launch { preferencesManager.setDefaultMediaType(type.value) }
            loadMedia()
        }
    }

    fun setStatus(status: MediaStatus?) {
        if (currentStatus != status) {
            currentStatus = status
            loadMedia()
        }
    }

    fun setSort(sort: String) {
        if (currentSort != sort) {
            currentSort = sort
            loadMedia()
        }
    }

    fun currentSort(): String = currentSort

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
                sort = currentSort,
                limit = 100
            )
            _mediaList.value = result
            _isLoading.value = false
        }
    }
}
