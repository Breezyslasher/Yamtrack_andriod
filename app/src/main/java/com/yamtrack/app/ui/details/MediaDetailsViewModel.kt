package com.yamtrack.app.ui.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamtrack.app.data.model.*
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaDetailsViewModel @Inject constructor(
    private val repository: YamtrackRepository
) : ViewModel() {

    private val _details = MutableLiveData<Result<MediaDetails>>()
    val details: LiveData<Result<MediaDetails>> = _details

    private val _updateResult = MutableLiveData<OperationResult>()
    val updateResult: LiveData<OperationResult> = _updateResult

    private val _removeResult = MutableLiveData<OperationResult>()
    val removeResult: LiveData<OperationResult> = _removeResult

    private val _recommendations = MutableLiveData<List<SearchResult>>(emptyList())
    val recommendations: LiveData<List<SearchResult>> = _recommendations

    /** Whether the item is currently in the user's library. Drives the
     *  Add-vs-Remove button and whether edits PATCH or POST. */
    private val _tracked = MutableLiveData(false)
    val tracked: LiveData<Boolean> = _tracked

    private var currentMediaType: MediaType = MediaType.MOVIE
    private var currentSource: String = "tmdb"
    private var currentMediaId: String = ""
    private var isTracked: Boolean = false

    fun loadDetails(mediaType: MediaType, source: String, mediaId: String) {
        currentMediaType = mediaType
        currentSource = source
        currentMediaId = mediaId

        viewModelScope.launch {
            _details.value = Result.Loading
            val result = repository.getMediaDetails(mediaType, source, mediaId)
            _details.value = result
            if (result is Result.Success) {
                isTracked = result.data.tracked
                _tracked.value = isTracked
            }
        }
        loadRecommendations()
    }

    /** Add with default Planning status (used by the explicit button). */
    fun addToLibrary() {
        viewModelScope.launch {
            val result = repository.addMedia(
                currentMediaType, currentSource, currentMediaId,
                status = MediaStatus.PLANNING
            )
            _updateResult.value = applyMutationResult(result)
        }
    }

    private fun loadRecommendations() {
        viewModelScope.launch {
            when (val result = repository.getMediaRecommendations(
                currentMediaType, currentSource, currentMediaId
            )) {
                is Result.Success -> _recommendations.value = result.data
                else -> _recommendations.value = emptyList()
            }
        }
    }

    fun updateStatus(status: MediaStatus) {
        viewModelScope.launch {
            // Untracked items have no record to PATCH — create it instead so
            // picking a status (or score) from a search result just works.
            val result = if (isTracked) {
                repository.updateMedia(
                    currentMediaType, currentSource, currentMediaId,
                    UpdateMediaRequest(status = status.code)
                )
            } else {
                repository.addMedia(
                    currentMediaType, currentSource, currentMediaId,
                    status = status
                )
            }
            _updateResult.value = applyMutationResult(result)
        }
    }

    fun updateScore(score: Double?) {
        viewModelScope.launch {
            val result = if (isTracked) {
                repository.updateMedia(
                    currentMediaType, currentSource, currentMediaId,
                    UpdateMediaRequest(score = score)
                )
            } else {
                repository.addMedia(
                    currentMediaType, currentSource, currentMediaId,
                    status = MediaStatus.PLANNING,
                    score = score
                )
            }
            _updateResult.value = applyMutationResult(result)
        }
    }

    fun updateProgress(progress: Int) {
        viewModelScope.launch {
            val result = if (isTracked) {
                repository.updateMedia(
                    currentMediaType, currentSource, currentMediaId,
                    UpdateMediaRequest(progress = progress)
                )
            } else {
                repository.addMedia(
                    currentMediaType, currentSource, currentMediaId,
                    status = MediaStatus.PLANNING,
                    progress = progress
                )
            }
            _updateResult.value = applyMutationResult(result)
        }
    }

    private fun <T> applyMutationResult(result: Result<T>): OperationResult = when (result) {
        is Result.Success -> {
            loadDetails(currentMediaType, currentSource, currentMediaId)
            OperationResult.Success
        }
        is Result.Error -> OperationResult.Error(result.message)
        else -> OperationResult.Error("Unknown error")
    }

    fun remove() {
        viewModelScope.launch {
            val result = repository.deleteMedia(
                currentMediaType,
                currentSource,
                currentMediaId
            )
            _removeResult.value = when (result) {
                is Result.Success -> OperationResult.Success
                is Result.Error -> OperationResult.Error(result.message)
                else -> OperationResult.Error("Unknown error")
            }
        }
    }

    sealed class OperationResult {
        object Success : OperationResult()
        data class Error(val message: String) : OperationResult()
    }
}
