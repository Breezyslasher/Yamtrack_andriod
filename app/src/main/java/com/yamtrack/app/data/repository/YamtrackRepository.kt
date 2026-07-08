package com.yamtrack.app.data.repository

import android.util.Log
import com.squareup.moshi.Moshi
import com.yamtrack.app.data.api.BaseUrlProvider
import com.yamtrack.app.data.api.TokenProvider
import com.yamtrack.app.data.api.YamtrackApi
import com.yamtrack.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository wrapping the Yamtrack REST API.
 *
 * Source of truth: src/api/views.py and src/api/urls.py on the
 * `feat/add-api` branch.
 *
 * Responsibilities:
 *  - Map HTTP responses + {"detail": "..."} error envelopes to Result<T>.
 *  - Manage the bearer token + base URL providers.
 *
 * Auth is Bearer / X-API-Key only — the API has no password-grant endpoint.
 */
@Singleton
class YamtrackRepository @Inject constructor(
    private val api: YamtrackApi,
    private val tokenProvider: TokenProvider,
    private val baseUrlProvider: BaseUrlProvider,
    private val moshi: Moshi
) {
    private val apiErrorAdapter by lazy { moshi.adapter(ApiError::class.java) }

    companion object {
        private const val TAG = "YamtrackRepository"
    }

    fun setServerUrl(url: String) {
        baseUrlProvider.setBaseUrl(url)
    }

    fun setToken(token: String?) {
        tokenProvider.setToken(token)
    }

    fun getToken(): String? = tokenProvider.getToken()

    // ===================== Auth =====================

    /**
     * Validate the (server, token) pair by calling /api/v1/statistics/ —
     * an authenticated endpoint that should always succeed for a valid token.
     *
     * Restores the previous URL+token on failure so that a failed login
     * attempt does not corrupt the active session.
     */
    suspend fun testConnection(serverUrl: String, token: String): AuthResult = withContext(Dispatchers.IO) {
        val previousUrl = baseUrlProvider.getBaseUrl()
        val previousToken = tokenProvider.getToken()

        try {
            baseUrlProvider.setBaseUrl(serverUrl)
            tokenProvider.setToken(token)

            val response = api.getStatistics()
            when {
                response.isSuccessful -> AuthResult.Success
                response.code() == 401 -> {
                    restorePrevious(previousUrl, previousToken)
                    AuthResult.Error("Invalid API token. Please check the value and try again.")
                }
                response.code() == 404 -> {
                    restorePrevious(previousUrl, previousToken)
                    AuthResult.Error(
                        "API not found. Make sure your server is running a build " +
                        "that includes the REST API (dev branch / PR #924)."
                    )
                }
                else -> {
                    restorePrevious(previousUrl, previousToken)
                    AuthResult.Error("Server error: HTTP ${response.code()}")
                }
            }
        } catch (e: IOException) {
            restorePrevious(previousUrl, previousToken)
            Log.e(TAG, "Network error during connection test", e)
            AuthResult.Error("Could not connect to server. Check the URL and your network.")
        } catch (e: Exception) {
            restorePrevious(previousUrl, previousToken)
            Log.e(TAG, "Error during connection test", e)
            AuthResult.Error("Error: ${e.message ?: "Unknown error"}")
        }
    }

    private fun restorePrevious(url: String?, token: String?) {
        if (url != null) baseUrlProvider.setBaseUrl(url)
        tokenProvider.setToken(token)
    }

    // ===================== Statistics & info =====================

    suspend fun getStatistics(
        startDate: String? = null,
        endDate: String? = null
    ): Result<UserStats> = apiCall {
        api.getStatistics(startDate, endDate)
    }

    suspend fun getAppInfo(): Result<AppInfo> = apiCall { api.info() }

    suspend fun getHealth(): Result<HealthResponse> = apiCall { api.health() }

    // ===================== Media list & detail =====================

    /**
     * GET /api/v1/media/  — aggregated across all user-tracked media types.
     *
     * Status is sent as a numeric string (or empty for ALL).
     */
    suspend fun getAllMedia(
        status: MediaStatus? = null,
        search: String? = null,
        sort: String? = null,
        excludeTypes: List<MediaType> = emptyList(),
        limit: Int = 50,
        offset: Int = 0
    ): Result<List<MediaItem>> = apiCall {
        api.getAllMedia(
            mediaType = null,
            status = status?.code?.toString(),
            search = search?.takeIf { it.isNotBlank() },
            sort = sort?.takeIf { it.isNotBlank() },
            exclude = excludeTypes.takeIf { it.isNotEmpty() }?.joinToString(",") { it.value },
            limit = limit,
            offset = offset
        )
    }.map { it.results }

    suspend fun getMediaByType(
        mediaType: MediaType,
        status: MediaStatus? = null,
        search: String? = null,
        sort: String? = null,
        limit: Int = 50,
        offset: Int = 0
    ): Result<List<MediaItem>> = apiCall {
        api.getMediaByType(
            mediaType = mediaType.value,
            status = status?.code?.toString(),
            search = search?.takeIf { it.isNotBlank() },
            sort = sort?.takeIf { it.isNotBlank() },
            limit = limit,
            offset = offset
        )
    }.map { it.results }

    suspend fun getMediaDetails(
        mediaType: MediaType,
        source: String,
        mediaId: String
    ): Result<MediaDetails> = apiCall {
        api.getMediaDetails(mediaType.value, source, mediaId)
    }

    suspend fun addMedia(
        mediaType: MediaType,
        source: String,
        mediaId: String,
        status: MediaStatus = MediaStatus.PLANNING,
        score: Double? = null,
        progress: Int? = null,
        notes: String? = null
    ): Result<MediaDetails> = apiCall {
        api.addMedia(
            mediaType = mediaType.value,
            request = AddMediaRequest(
                mediaId = mediaId,
                source = source,
                status = status.code,
                score = score,
                progress = progress,
                notes = notes
            )
        )
    }

    suspend fun updateMedia(
        mediaType: MediaType,
        source: String,
        mediaId: String,
        update: UpdateMediaRequest
    ): Result<MediaDetails> = apiCall {
        api.updateMedia(mediaType.value, source, mediaId, update)
    }

    suspend fun deleteMedia(
        mediaType: MediaType,
        source: String,
        mediaId: String
    ): Result<Unit> = apiCall {
        api.deleteMedia(mediaType.value, source, mediaId)
    }

    suspend fun getMediaRecommendations(
        mediaType: MediaType,
        source: String,
        mediaId: String
    ): Result<List<SearchResult>> = apiCall {
        api.getMediaRecommendations(mediaType.value, source, mediaId)
    }

    // ===================== Seasons & episodes =====================

    suspend fun getSeasons(
        source: String,
        mediaId: String
    ): Result<List<MediaItem>> = apiCall {
        api.getSeasons(MediaType.TV.value, source, mediaId, limit = 100)
    }.map { it.results }

    suspend fun getSeasonDetails(
        source: String,
        mediaId: String,
        seasonNumber: Int
    ): Result<MediaDetails> = apiCall {
        api.getSeasonDetails(MediaType.TV.value, source, mediaId, seasonNumber)
    }

    suspend fun updateSeason(
        source: String,
        mediaId: String,
        seasonNumber: Int,
        update: UpdateMediaRequest
    ): Result<MediaDetails> = apiCall {
        api.updateSeason(MediaType.TV.value, source, mediaId, seasonNumber, update)
    }

    suspend fun getEpisodes(
        source: String,
        mediaId: String,
        seasonNumber: Int
    ): Result<List<MediaItem>> = apiCall {
        api.getEpisodes(MediaType.TV.value, source, mediaId, seasonNumber, limit = 200)
    }.map { it.results }

    suspend fun getEpisodeDetails(
        source: String,
        mediaId: String,
        seasonNumber: Int,
        episodeNumber: Int
    ): Result<MediaDetails> = apiCall {
        api.getEpisodeDetails(MediaType.TV.value, source, mediaId, seasonNumber, episodeNumber)
    }

    suspend fun updateEpisode(
        source: String,
        mediaId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        update: UpdateMediaRequest
    ): Result<MediaDetails> = apiCall {
        api.updateEpisode(MediaType.TV.value, source, mediaId, seasonNumber, episodeNumber, update)
    }

    private suspend fun deleteEpisode(
        source: String,
        mediaId: String,
        seasonNumber: Int,
        episodeNumber: Int
    ): Result<Unit> = apiCall {
        api.deleteEpisode(MediaType.TV.value, source, mediaId, seasonNumber, episodeNumber)
    }

    /**
     * Mark an episode watched/unwatched.
     *
     * Per the API PR author (FuzzyGrim/Yamtrack#924, 66Bunz May-2026),
     * episodes are NOT a top-level resource — they live as children of
     * the parent tv serie at
     * `/api/v1/media/tv/{source}/{id}/{season}/{episode}/`. There is no
     * POST endpoint for creating a brand-new episode tracking via the
     * REST API yet; only PATCH/DELETE on existing ones are exposed.
     *
     *  - watched  -> PATCH end_date = today on the child url.
     *                If the episode has never been tracked the server
     *                replies 404; surfaced as a clear message because
     *                there's no client-side way to create one yet.
     *  - unwatched -> DELETE the episode tracking.
     */
    suspend fun setEpisodeWatched(
        source: String,
        mediaId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        watched: Boolean
    ): Result<Unit> {
        if (!watched) {
            return deleteEpisode(source, mediaId, seasonNumber, episodeNumber)
        }
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        val r = updateEpisode(
            source, mediaId, seasonNumber, episodeNumber,
            UpdateMediaRequest(endDate = today)
        )
        return when (r) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> {
                val friendly = if (r.code == 404) {
                    "This episode hasn't been tracked yet, and the API can't " +
                        "create new episode trackings — mark it watched on the " +
                        "Yamtrack web UI first, then it'll toggle here."
                } else r.message
                Result.Error(friendly, r.code)
            }
            else -> Result.Error("Unknown error")
        }
    }

    // ===================== Search =====================

    /**
     * GET /api/v1/search/{media_type}/?search=...
     * NOTE: server expects `search` (not `q`). `season` and `episode` are
     * not searchable.
     */
    suspend fun search(
        mediaType: MediaType,
        query: String,
        source: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<SearchResult>> {
        if (mediaType == MediaType.SEASON || mediaType == MediaType.EPISODE) {
            return Result.Error("Search for ${mediaType.displayName} is not supported.")
        }
        return apiCall {
            api.search(
                mediaType = mediaType.value,
                search = query,
                source = source,
                limit = limit,
                offset = offset
            )
        }.map { it.results }
    }

    // ===================== Calendar =====================

    suspend fun getCalendar(
        startDate: String? = null,
        endDate: String? = null,
        year: Int? = null,
        month: Int? = null,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<CalendarEvent>> = apiCall {
        api.getCalendar(startDate, endDate, year, month, limit, offset)
    }.map { it.results }

    // ===================== Lists =====================

    suspend fun getLists(
        search: String? = null,
        sort: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): Result<List<CustomList>> = apiCall {
        api.getLists(search?.takeIf { it.isNotBlank() }, sort?.takeIf { it.isNotBlank() }, limit, offset)
    }.map { it.results }

    suspend fun getListDetails(listId: Long): Result<CustomList> = apiCall {
        api.getListDetails(listId)
    }

    suspend fun createList(
        name: String,
        description: String? = null
    ): Result<CustomList> = apiCall {
        api.createList(
            CreateListRequest(
                name = name.trim(),
                description = description?.takeIf { it.isNotBlank() }
            )
        )
    }

    suspend fun deleteList(listId: Long): Result<Unit> = apiCall {
        api.deleteList(listId)
    }

    suspend fun addMediaToList(
        mediaType: MediaType,
        source: String,
        mediaId: String,
        listId: Long
    ): Result<Unit> = apiCall {
        api.addMediaToList(mediaType.value, source, mediaId, listId)
    }

    suspend fun removeMediaFromList(
        mediaType: MediaType,
        source: String,
        mediaId: String,
        listId: Long
    ): Result<Unit> = apiCall {
        api.removeMediaFromList(mediaType.value, source, mediaId, listId)
    }

    // ===================== History =====================

    suspend fun getMediaHistory(
        mediaType: MediaType,
        source: String,
        mediaId: String
    ): Result<List<HistoryEntry>> = apiCall {
        api.getMediaHistory(mediaType.value, source, mediaId)
    }.map { it.results }

    // ===================== Helpers =====================

    /**
     * Generic API call wrapper. Maps:
     *   - 2xx with body         -> Result.Success
     *   - 2xx with empty body   -> Result.Success(Unit) (cast)
     *   - non-2xx               -> Result.Error with parsed {"detail": "..."}
     *   - IOException           -> Result.Error("Network error: ...")
     *   - Anything else         -> Result.Error("Error: ...")
     */
    private suspend fun <T> apiCall(block: suspend () -> Response<T>): Result<T> = withContext(Dispatchers.IO) {
        try {
            val response = block()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Result.Success(body)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    Result.Success(Unit as T)
                }
            } else {
                val errorMessage = parseErrorBody(response)
                Log.e(TAG, "API error: ${response.code()} - $errorMessage")
                Result.Error(errorMessage, response.code())
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error", e)
            Result.Error("Network error: ${e.message ?: "No connection"}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            Result.Error("Error: ${e.message ?: "Unknown error"}")
        }
    }

    private fun <T> parseErrorBody(response: Response<T>): String {
        return try {
            val errorBody = response.errorBody()?.string()
            if (errorBody.isNullOrBlank()) {
                httpCodeMessage(response.code())
            } else {
                try {
                    apiErrorAdapter.fromJson(errorBody)?.getErrorMessage()
                        ?: httpCodeMessage(response.code())
                } catch (e: Exception) {
                    httpCodeMessage(response.code())
                }
            }
        } catch (e: Exception) {
            httpCodeMessage(response.code())
        }
    }

    private fun httpCodeMessage(code: Int): String = when (code) {
        400 -> "Bad request."
        401 -> "Not authenticated. Please check your API token."
        403 -> "Forbidden. Your token may not have access to this resource."
        404 -> "Not found. The server may not have API support enabled."
        408 -> "Request timeout."
        409 -> "Conflict. This item already exists."
        429 -> "Too many requests. Please slow down."
        500 -> "Server error. Please try again later."
        502, 503, 504 -> "Server unavailable. Please try again later."
        else -> "Error: HTTP $code"
    }
}

/** Map Result.Success values while preserving errors / loading. */
private fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Error -> this
    is Result.Loading -> this
}
