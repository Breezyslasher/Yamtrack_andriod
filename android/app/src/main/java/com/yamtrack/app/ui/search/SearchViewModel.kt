package com.yamtrack.app.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamtrack.app.data.model.MediaStatus
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.model.SearchResult
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: YamtrackRepository
) : ViewModel() {

    private val _searchResults = MutableLiveData<Result<List<SearchResult>>>()
    val searchResults: LiveData<Result<List<SearchResult>>> = _searchResults

    private val _addResult = MutableLiveData<AddResult>()
    val addResult: LiveData<AddResult> = _addResult

    private var currentMediaType: MediaType = MediaType.MOVIE
    private var lastQuery: String = ""

    fun setMediaType(type: MediaType) {
        currentMediaType = type
        if (lastQuery.isNotEmpty()) {
            search(lastQuery)
        }
    }

    fun search(query: String) {
        lastQuery = query

        viewModelScope.launch {
            _searchResults.value = Result.Loading
            _searchResults.value = repository.search(
                mediaType = currentMediaType,
                query = query
            )
        }
    }

    fun addToLibrary(item: SearchResult) {
        val type = item.type ?: return
        val source = item.source
        val mediaId = item.mediaId
        if (source.isNullOrBlank() || mediaId.isNullOrBlank()) {
            _addResult.value = AddResult.Error("Result is missing source or media id; cannot track.")
            return
        }
        viewModelScope.launch {
            val result = repository.addMedia(
                mediaType = type,
                source = source,
                mediaId = mediaId,
                status = MediaStatus.PLANNING
            )
            _addResult.value = when (result) {
                is Result.Success -> AddResult.Success(item.displayTitle)
                is Result.Error -> AddResult.Error(result.message)
                else -> AddResult.Error("Unknown error")
            }
        }
    }

    sealed class AddResult {
        data class Success(val title: String) : AddResult()
        data class Error(val message: String) : AddResult()
    }
}
