package com.yamtrack.app.data.api

import com.yamtrack.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for the Yamtrack REST API.
 *
 * Source of truth: src/api/urls.py and src/api/views.py on the
 * `feat/add-api` branch (also merged to `dev`).
 *
 * Mounted under /api/v1/ via src/config/urls.py:
 *   path("api/v1/", include("api.urls"))
 *
 * Auth: HTTP Bearer header (`Authorization: Bearer <token>`) OR
 *       `X-API-Key: <token>` header (see api/authentication.py).
 *       /info/ and /health/ are public (no auth required).
 *
 * Pagination: every list endpoint accepts ?limit & ?offset.
 *   limit defaults to 20, capped at MAX_RESULT_LIMIT=200.
 *   offset defaults to 0.
 */
interface YamtrackApi {

    // ============== Public endpoints (no auth) ==============

    @GET("api/v1/health/")
    suspend fun health(): Response<HealthResponse>

    @GET("api/v1/info/")
    suspend fun info(): Response<AppInfo>

    // ============== Statistics ==============

    /**
     * GET /api/v1/statistics/
     * Optional query params:
     *   start_date — ISO date, default = today minus 1y
     *   end_date   — ISO date, default = today
     *   start_date=all & end_date=all — disables date filter
     */
    @GET("api/v1/statistics/")
    suspend fun getStatistics(
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null
    ): Response<UserStats>

    // ============== Media list ==============

    /**
     * GET /api/v1/media/
     * Aggregated list across every media_type (excludes seasons & episodes
     * by default for clarity).
     *
     * Query params:
     *   media_type — optional filter to a single type
     *   status     — numeric (0..4) or empty for "all"
     *   search     — case-insensitive title substring
     *   sort       — see MEDIA_EXISTING_SORTS / MEDIA_MANUAL_SORTS
     *   exclude    — comma-separated list of media_types to skip (used when
     *                media_type is omitted)
     *   limit, offset
     */
    @GET("api/v1/media/")
    suspend fun getAllMedia(
        @Query("media_type") mediaType: String? = null,
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
        @Query("sort") sort: String? = null,
        @Query("exclude") exclude: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<PaginatedResponse<MediaItem>>

    /**
     * GET /api/v1/media/{media_type}/
     * Same query params as the aggregated list view (no `media_type`/`exclude`).
     */
    @GET("api/v1/media/{media_type}/")
    suspend fun getMediaByType(
        @Path("media_type") mediaType: String,
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
        @Query("sort") sort: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<PaginatedResponse<MediaItem>>

    /**
     * POST /api/v1/media/{media_type}/
     * Body must contain at minimum: media_id, source.
     * status defaults to Planning (numeric 0) if omitted.
     */
    @POST("api/v1/media/{media_type}/")
    suspend fun addMedia(
        @Path("media_type") mediaType: String,
        @Body request: AddMediaRequest
    ): Response<MediaDetails>

    // ============== Media detail (CompleteMediaSerializer) ==============

    /**
     * GET /api/v1/media/{media_type}/{source}/{media_id}/
     * Returns the full CompleteMedia response (metadata + tracking state).
     */
    @GET("api/v1/media/{media_type}/{source}/{media_id}/")
    suspend fun getMediaDetails(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String
    ): Response<MediaDetails>

    /**
     * PATCH /api/v1/media/{media_type}/{source}/{media_id}/
     * Partial update. Only fields in MEDIA_MODIFIABLE_FIELDS for the given
     * media_type are honored — others are silently dropped server-side.
     */
    @PATCH("api/v1/media/{media_type}/{source}/{media_id}/")
    suspend fun updateMedia(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
        @Body request: UpdateMediaRequest
    ): Response<MediaDetails>

    /**
     * DELETE /api/v1/media/{media_type}/{source}/{media_id}/
     * Untracks the item and removes all consumption records. Returns 204.
     */
    @DELETE("api/v1/media/{media_type}/{source}/{media_id}/")
    suspend fun deleteMedia(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String
    ): Response<Unit>

    // ============== Media — children & relations ==============

    @GET("api/v1/media/{media_type}/{source}/{media_id}/seasons/")
    suspend fun getSeasons(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<PaginatedResponse<MediaItem>>

    @GET("api/v1/media/{media_type}/{source}/{media_id}/{season_number}/")
    suspend fun getSeasonDetails(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
        @Path("season_number") seasonNumber: Int
    ): Response<MediaDetails>

    @PATCH("api/v1/media/{media_type}/{source}/{media_id}/{season_number}/")
    suspend fun updateSeason(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
        @Path("season_number") seasonNumber: Int,
        @Body request: UpdateMediaRequest
    ): Response<MediaDetails>

    @GET("api/v1/media/{media_type}/{source}/{media_id}/{season_number}/episodes/")
    suspend fun getEpisodes(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
        @Path("season_number") seasonNumber: Int,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<PaginatedResponse<MediaItem>>

    @GET("api/v1/media/{media_type}/{source}/{media_id}/{season_number}/{episode_number}/")
    suspend fun getEpisodeDetails(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
        @Path("season_number") seasonNumber: Int,
        @Path("episode_number") episodeNumber: Int
    ): Response<MediaDetails>

    @PATCH("api/v1/media/{media_type}/{source}/{media_id}/{season_number}/{episode_number}/")
    suspend fun updateEpisode(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
        @Path("season_number") seasonNumber: Int,
        @Path("episode_number") episodeNumber: Int,
        @Body request: UpdateMediaRequest
    ): Response<MediaDetails>

    @GET("api/v1/media/{media_type}/{source}/{media_id}/recommendations/")
    suspend fun getMediaRecommendations(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String
    ): Response<List<SearchResult>>

    @GET("api/v1/media/{media_type}/{source}/{media_id}/history/")
    suspend fun getMediaHistory(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<PaginatedResponse<HistoryEntry>>

    // ============== Search ==============

    /**
     * GET /api/v1/search/{media_type}/
     * NOTE: query param is `search` (NOT `q`). Optional `source` to scope to
     * a single provider. Seasons/episodes search is rejected by the server.
     */
    @GET("api/v1/search/{media_type}/")
    suspend fun search(
        @Path("media_type") mediaType: String,
        @Query("search") search: String,
        @Query("source") source: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<PaginatedResponse<SearchResult>>

    // ============== Calendar ==============

    /**
     * GET /api/v1/calendar/
     * Defaults to the current month if no date params are given.
     * Optional query params:
     *   start_date / end_date — ISO dates, take precedence
     *   year / month — alternative form
     */
    @GET("api/v1/calendar/")
    suspend fun getCalendar(
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<PaginatedResponse<CalendarEvent>>

    /** POST /api/v1/calendar/update/ — queues a Celery refresh task. Returns 202. */
    @POST("api/v1/calendar/update/")
    suspend fun updateCalendar(): Response<Map<String, String>>

    // ============== Lists ==============

    @GET("api/v1/lists/")
    suspend fun getLists(
        @Query("search") search: String? = null,
        @Query("sort") sort: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<PaginatedResponse<CustomList>>

    @GET("api/v1/lists/{list_id}/")
    suspend fun getListDetails(
        @Path("list_id") listId: Long,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): Response<CustomList>
}
