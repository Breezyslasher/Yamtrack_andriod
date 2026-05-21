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
     * Currently selected media type, mirrored as LiveData so the fragment
     * can resync the chip checked state after the view is recreated on
     * bottom-nav switches. Backed by `default_media_type` in DataStore so
     * the choice survives across navigation and process restarts.
     */
    private val _mediaType = MutableLiveData<MediaType>(MediaType.MOVIE)
    val mediaType: LiveData<MediaType> = _mediaType

    private var currentMediaType: MediaType = MediaType.MOVIE
    private var currentStatus: MediaStatus? = null   // null == ALL
    private var currentSort: String = "added_desc"   // newest-first by default

    /** Active page-fetch coroutine. Cancelled when filters change so a
     *  slow previous load can't overwrite a newer one. */
    private var loadJob: kotlinx.coroutines.Job? = null

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
        // Cancel any in-flight previous load — switching tabs/filters fast
        // would otherwise let an older fetch overwrite the newer one.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _mediaList.value = Result.Loading

            // Snapshot the request key so we can ignore the result if the
            // user changed filters again while we were paging.
            val requestType = currentMediaType
            val requestStatus = currentStatus
            val requestSort = currentSort

            // The API caps a single page at MAX_RESULT_LIMIT (200), so a
            // flat limit silently truncates large libraries. Page through
            // until a short page signals the end and show the full set.
            val pageSize = 200
            val all = mutableListOf<MediaItem>()
            var offset = 0
            var error: Result.Error? = null
            while (true) {
                val page = repository.getMediaByType(
                    mediaType = requestType,
                    status = requestStatus,
                    sort = requestSort,
                    limit = pageSize,
                    offset = offset
                )
                when (page) {
                    is Result.Success -> {
                        all += page.data
                        if (page.data.size < pageSize) break
                        offset += pageSize
                    }
                    is Result.Error -> {
                        error = page
                        break
                    }
                    is Result.Loading -> break
                }
            }

            // Belt-and-suspenders: if the user changed filters after we
            // already started paging, drop this result so the UI keeps
            // whatever the newer load produces.
            if (requestType != currentMediaType ||
                requestStatus != currentStatus ||
                requestSort != currentSort
            ) {
                return@launch
            }

            _mediaList.value = when {
                error != null && all.isEmpty() -> error
                else -> Result.Success(all)
            }
            _isLoading.value = false
        }
    }
}
