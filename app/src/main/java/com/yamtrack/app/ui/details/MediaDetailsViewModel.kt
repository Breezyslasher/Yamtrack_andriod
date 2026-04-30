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

    private var currentMediaType: MediaType = MediaType.MOVIE
    private var currentSource: String = "tmdb"
    private var currentMediaId: String = ""

    fun loadDetails(mediaType: MediaType, source: String, mediaId: String) {
        currentMediaType = mediaType
        currentSource = source
        currentMediaId = mediaId

        viewModelScope.launch {
            _details.value = Result.Loading
            _details.value = repository.getMediaDetails(mediaType, source, mediaId)
        }
        loadRecommendations()
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
            val result = repository.updateMedia(
                currentMediaType,
                currentSource,
                currentMediaId,
                UpdateMediaRequest(status = status.code)
            )
            _updateResult.value = when (result) {
                is Result.Success -> {
                    loadDetails(currentMediaType, currentSource, currentMediaId)
                    OperationResult.Success
                }
                is Result.Error -> OperationResult.Error(result.message)
                else -> OperationResult.Error("Unknown error")
            }
        }
    }

    fun updateScore(score: Double?) {
        viewModelScope.launch {
            val result = repository.updateMedia(
                currentMediaType,
                currentSource,
                currentMediaId,
                UpdateMediaRequest(score = score)
            )
            _updateResult.value = when (result) {
                is Result.Success -> {
                    loadDetails(currentMediaType, currentSource, currentMediaId)
                    OperationResult.Success
                }
                is Result.Error -> OperationResult.Error(result.message)
                else -> OperationResult.Error("Unknown error")
            }
        }
    }

    fun updateProgress(progress: Int) {
        viewModelScope.launch {
            val result = repository.updateMedia(
                currentMediaType,
                currentSource,
                currentMediaId,
                UpdateMediaRequest(progress = progress)
            )
            _updateResult.value = when (result) {
                is Result.Success -> {
                    loadDetails(currentMediaType, currentSource, currentMediaId)
                    OperationResult.Success
                }
                is Result.Error -> OperationResult.Error(result.message)
                else -> OperationResult.Error("Unknown error")
            }
        }
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
