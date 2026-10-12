package com.yamtrack.app.ui.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamtrack.app.data.model.*
import com.yamtrack.app.data.repository.ArtworkOverrides
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    private val _seasons = MutableLiveData<List<MediaItem>>(emptyList())
    val seasons: LiveData<List<MediaItem>> = _seasons

    private val _cast = MutableLiveData<List<CastMember>>(emptyList())
    val cast: LiveData<List<CastMember>> = _cast

    /** Null until loaded, or when the item has no providers in this region. */
    private val _providers = MutableLiveData<WatchProviders?>(null)
    val providers: LiveData<WatchProviders?> = _providers

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
                ArtworkOverrides.sync(result.data.artworkKey, result.data.image)
            }
        }
        if (mediaType == MediaType.TV) loadSeasons()

        // Recommendations, cast and providers don't change when the user
        // edits status/score, so only fetch them once per item.
        val key = "${mediaType.value}|$source|$mediaId"
        if (key == extrasLoadedFor) return
        extrasLoadedFor = key
        loadRecommendations()
        if (mediaType in CAST_TYPES) loadCast()
        if (mediaType == MediaType.MOVIE || mediaType == MediaType.TV) loadProviders()
    }

    private var extrasLoadedFor: String? = null

    private fun loadCast() {
        viewModelScope.launch {
            _cast.value = when (val r = repository.getCast(currentMediaType, currentSource, currentMediaId)) {
                is Result.Success -> r.data.sortedBy { it.order ?: Int.MAX_VALUE }
                else -> emptyList()
            }
        }
    }

    private fun loadProviders() {
        viewModelScope.launch {
            _providers.value = when (val r = repository.getProviders(currentMediaType, currentSource, currentMediaId)) {
                is Result.Success -> r.data
                else -> null
            }
        }
    }

    /** Images the cover can be picked from, plus the custom cover in use (if any). */
    data class ArtworkChoices(val options: List<MediaImage>, val customImage: String?)

    /**
     * Cover options for the item, or for one of its seasons when
     * [seasonNumber] is set. Poster-shaped images first; falls back to any
     * image when there are no posters.
     */
    suspend fun fetchArtworkChoices(seasonNumber: Int? = null): Result<ArtworkChoices> =
        coroutineScope {
            val cover = async {
                repository.getCover(currentMediaType, currentSource, currentMediaId, seasonNumber)
            }
            val images = repository.getArtworkImages(
                currentMediaType, currentSource, currentMediaId, seasonNumber
            )
            when (images) {
                is Result.Success -> {
                    val options = images.data.posters.orEmpty().ifEmpty {
                        images.data.backdrops.orEmpty() + images.data.screenshots.orEmpty()
                    }.filter { !(it.image ?: it.original).isNullOrBlank() }
                    val custom = (cover.await() as? Result.Success)?.data?.customImage
                    Result.Success(ArtworkChoices(options, custom))
                }
                is Result.Error -> {
                    cover.cancel()
                    images
                }
                Result.Loading -> Result.Loading
            }
        }

    /**
     * Saves [image] as the cover on the server, or resets it to the
     * provider image when null. On success every screen picks it up.
     */
    suspend fun applyCover(image: String?, seasonNumber: Int? = null): Result<Cover> {
        val result = if (image != null) {
            repository.setCover(currentMediaType, currentSource, currentMediaId, image, seasonNumber)
        } else {
            repository.resetCover(currentMediaType, currentSource, currentMediaId, seasonNumber)
        }
        if (result is Result.Success) {
            val key = if (seasonNumber != null) {
                ArtworkOverrides.key(MediaType.SEASON.value, currentSource, currentMediaId, seasonNumber)
            } else {
                ArtworkOverrides.key(currentMediaType.value, currentSource, currentMediaId)
            }
            key?.let { ArtworkOverrides.set(it, result.data.image) }
        }
        return result
    }

    private fun loadSeasons() {
        viewModelScope.launch {
            _seasons.value = when (val r = repository.getSeasons(currentSource, currentMediaId)) {
                is Result.Success -> {
                    r.data.forEach { ArtworkOverrides.sync(it.item?.artworkKey, it.item?.image) }
                    r.data.sortedBy { it.item?.seasonNumber ?: 0 }
                }
                else -> emptyList()
            }
        }
    }

    // Episode-level tracking lives in EpisodesActivity/EpisodesViewModel
    // (via the correct POST /media/episode/ + DELETE endpoints), not here.

    /** Returns all the user's lists. */
    suspend fun fetchUserLists(): List<com.yamtrack.app.data.model.CustomList> =
        when (val r = repository.getLists(limit = 100)) {
            is Result.Success -> r.data
            else -> emptyList()
        }

    fun addToList(listId: Long) {
        viewModelScope.launch {
            val r = repository.addMediaToList(
                currentMediaType, currentSource, currentMediaId, listId
            )
            _updateResult.value = when (r) {
                is Result.Success -> {
                    loadDetails(currentMediaType, currentSource, currentMediaId)
                    OperationResult.Success
                }
                is Result.Error -> OperationResult.Error(r.message)
                else -> OperationResult.Error("Unknown error")
            }
        }
    }

    fun removeFromList(listId: Long) {
        viewModelScope.launch {
            val r = repository.removeMediaFromList(
                currentMediaType, currentSource, currentMediaId, listId
            )
            _updateResult.value = when (r) {
                is Result.Success -> {
                    loadDetails(currentMediaType, currentSource, currentMediaId)
                    OperationResult.Success
                }
                is Result.Error -> OperationResult.Error(r.message)
                else -> OperationResult.Error("Unknown error")
            }
        }
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

    companion object {
        /** Media types the server can return a cast for. */
        private val CAST_TYPES = setOf(MediaType.MOVIE, MediaType.TV, MediaType.ANIME)
    }

    sealed class OperationResult {
        object Success : OperationResult()
        data class Error(val message: String) : OperationResult()
    }
}
