package com.yamtrack.app.data.repository

import android.util.Log
import com.google.gson.Gson
import com.yamtrack.app.data.api.BaseUrlProvider
import com.yamtrack.app.data.api.TokenProvider
import com.yamtrack.app.data.api.YamtrackApi
import com.yamtrack.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
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
 *  - Provide a fallback `loginWithPassword` that uses Django session login
 *    to scrape the user's API token off /profile/ for the demo server.
 */
@Singleton
class YamtrackRepository @Inject constructor(
    private val api: YamtrackApi,
    private val tokenProvider: TokenProvider,
    private val baseUrlProvider: BaseUrlProvider,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
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

    /**
     * Convenience login flow for the demo server: log in via the Django
     * web UI to set a session cookie, then fetch /profile/ and try to
     * extract the API token from the rendered HTML.
     *
     * The Yamtrack API has no token-issuing endpoint, so this scraping
     * fallback is the only way to obtain a token from credentials alone.
     */
    suspend fun loginWithPassword(
        serverUrl: String,
        username: String,
        password: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = serverUrl.trimEnd('/')
            val cookies = mutableMapOf<String, String>()

            // Step 1: GET /accounts/login/ to grab the CSRF token
            val csrfToken = fetchCsrfToken(cleanUrl, cookies)
                ?: return@withContext Result.Error("Could not obtain CSRF token from server.")

            // Step 2: POST credentials, follow no redirects so we can detect 302 = success
            val cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            val formBody = FormBody.Builder()
                .add("login", username)
                .add("password", password)
                .add("csrfmiddlewaretoken", csrfToken)
                .build()

            val noRedirectClient = okHttpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val loginRequest = Request.Builder()
                .url("$cleanUrl/accounts/login/")
                .header("Cookie", cookieHeader)
                .header("Referer", "$cleanUrl/accounts/login/")
                .header("X-CSRFToken", csrfToken)
                .post(formBody)
                .build()

            noRedirectClient.newCall(loginRequest).execute().use { response ->
                response.headers("Set-Cookie").forEach { cookie ->
                    val parts = cookie.split(";").firstOrNull()?.split("=", limit = 2)
                    if (parts != null && parts.size == 2) {
                        cookies[parts[0].trim()] = parts[1].trim()
                    }
                }

                if (response.code !in 300..399) {
                    return@withContext Result.Error("Login failed. Check your username and password.")
                }
            }

            // Step 3: GET /profile/ and look for the API token in the markup
            val profileCookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            val profileRequest = Request.Builder()
                .url("$cleanUrl/profile/")
                .header("Cookie", profileCookieHeader)
                .get()
                .build()

            okHttpClient.newCall(profileRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.Error(
                        "Logged in but could not fetch profile. " +
                        "Please copy your API token manually."
                    )
                }
                val html = response.body?.string().orEmpty()

                val tokenPatterns = listOf(
                    Regex("""(?:api[_-]?token|api[_-]?key)["'\s:>]+([a-zA-Z0-9]{32,})""", RegexOption.IGNORE_CASE),
                    Regex("""<input[^>]*\bvalue=["']([a-zA-Z0-9]{40,})["'][^>]*>"""),
                    Regex("""<code[^>]*>\s*([a-zA-Z0-9]{40,})\s*</code>""", RegexOption.IGNORE_CASE),
                    Regex("""id=["']token["'][^>]*>\s*([a-zA-Z0-9]{32,})""", RegexOption.IGNORE_CASE)
                )

                tokenPatterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) }
                    ?.let { return@withContext Result.Success(it) }

                Result.Error(
                    "Could not auto-detect your API token on the profile page. " +
                    "Please copy it manually and use the API Token login mode."
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error during session login", e)
            Result.Error("Network error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error during session login", e)
            Result.Error("Error: ${e.message ?: "Unknown error"}")
        }
    }

    private fun fetchCsrfToken(cleanUrl: String, cookies: MutableMap<String, String>): String? {
        val request = Request.Builder()
            .url("$cleanUrl/accounts/login/")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            response.headers("Set-Cookie").forEach { cookie ->
                val parts = cookie.split(";").firstOrNull()?.split("=", limit = 2)
                if (parts != null && parts.size == 2) {
                    cookies[parts[0].trim()] = parts[1].trim()
                }
            }
            cookies["csrftoken"]?.let { return it }

            val html = response.body?.string().orEmpty()
            val match = Regex(
                """name=["']csrfmiddlewaretoken["']\s+value=["']([^"']+)["']"""
            ).find(html)
            return match?.groupValues?.get(1)
        }
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
                    gson.fromJson(errorBody, ApiError::class.java).getErrorMessage()
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
