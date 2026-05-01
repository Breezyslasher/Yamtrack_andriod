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
    private var currentSort: String = "added_desc"   // newest-first by default

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

            // The Yamtrack API only sorts on Item fields (title, source, …)
            // and the manual added/updated/itemid keys — `score` is not in
            // the allow-list. For your-score sorting we therefore skip the
            // server `sort` param and reorder client-side.
            val isScoreSort = currentSort == SORT_SCORE_DESC || currentSort == SORT_SCORE_ASC
            val serverSort = if (isScoreSort) null else currentSort

            val result = repository.getMediaByType(
                mediaType = currentMediaType,
                status = currentStatus,
                sort = serverSort,
                limit = 100
            )
            _mediaList.value = if (isScoreSort && result is Result.Success) {
                val descending = currentSort == SORT_SCORE_DESC
                val sorted = result.data.sortedWith(
                    compareBy(nullsLast()) { item ->
                        item.score.takeIf { it != null && it > 0.0 }
                            ?.let { if (descending) -it else it }
                    }
                )
                Result.Success(sorted)
            } else {
                result
            }
            _isLoading.value = false
        }
    }

    companion object {
        const val SORT_SCORE_DESC = "score_desc"
        const val SORT_SCORE_ASC = "score_asc"
    }
}
