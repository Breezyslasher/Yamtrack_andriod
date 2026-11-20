import Foundation

struct APIError: LocalizedError {
    let status: Int?
    let message: String

    var errorDescription: String? { message }
}

/// Talks to a Yamtrack server's REST API with a Bearer token.
final class APIClient {
    let baseURL: String
    private let token: String
    private let session: URLSession

    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()

    private let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.keyEncodingStrategy = .convertToSnakeCase
        return e
    }()

    init(baseURL: String, token: String) {
        self.baseURL = baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/ "))
        self.token = token
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        // Tracking state changes often; never answer from a stale cache.
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        self.session = URLSession(configuration: config)
    }

    // MARK: - Requests

    func get<T: Decodable>(_ path: String, query: [String: String?] = [:]) async throws -> T {
        try decoder.decode(T.self, from: try await send("GET", path, query: query))
    }

    /// Sends a request whose response body isn't needed.
    func call(_ method: String, _ path: String, body: (any Encodable)? = nil) async throws {
        _ = try await send(method, path, body: body)
    }

    @discardableResult
    private func send(_ method: String, _ path: String,
                      query: [String: String?] = [:], body: (any Encodable)? = nil) async throws -> Data {
        guard var components = URLComponents(string: "\(baseURL)/\(path)") else {
            throw APIError(status: nil, message: "Invalid server address.")
        }
        let items = query.compactMap { key, value in value.map { URLQueryItem(name: key, value: $0) } }
        if !items.isEmpty { components.queryItems = items }
        guard let url = components.url else {
            throw APIError(status: nil, message: "Invalid server address.")
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try encoder.encode(body)
        }

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw APIError(status: nil, message: "Could not connect to the server. Check the address and your network.")
        }
        guard let http = response as? HTTPURLResponse else {
            throw APIError(status: nil, message: "Unexpected response from the server.")
        }
        guard (200..<300).contains(http.statusCode) else {
            let detail = (try? decoder.decode(APIErrorBody.self, from: data))?.detail
            throw APIError(status: http.statusCode, message: detail ?? Self.message(for: http.statusCode))
        }
        return data
    }

    private static func message(for code: Int) -> String {
        switch code {
        case 400: return "Bad request."
        case 401: return "Not authenticated. Check your API token."
        case 403: return "Forbidden. Your token may not have access to this."
        case 404: return "Not found."
        case 409: return "This item already exists."
        case 500: return "Server error. Please try again later."
        case 502, 503, 504: return "Server unavailable. Please try again later."
        default: return "Error: HTTP \(code)"
        }
    }

    // MARK: - Endpoints

    /// An authenticated call that succeeds for any valid token.
    func checkConnection() async throws {
        let _: JSONValue = try await get("api/v1/statistics/")
    }

    /// Tracked media of every type, or of one when `type` is set.
    func media(type: MediaType? = nil, status: MediaStatus? = nil, sort: String? = nil,
               limit: Int = 200) async throws -> [MediaItem] {
        let path = type.map { "api/v1/media/\($0.rawValue)/" } ?? "api/v1/media/"
        let page: Paginated<MediaItem> = try await get(path, query: [
            "status": status.map { String($0.rawValue) },
            "sort": sort,
            "limit": String(limit),
        ])
        return page.results
    }

    func details(type: MediaType, source: String, mediaId: String) async throws -> MediaDetails {
        try await get("api/v1/media/\(type.rawValue)/\(source)/\(mediaId)/")
    }

    func search(type: MediaType, query: String) async throws -> [SearchResult] {
        let page: Paginated<SearchResult> = try await get("api/v1/search/\(type.rawValue)/",
                                                          query: ["search": query, "limit": "30"])
        return page.results
    }

    /// Starts tracking a media. The status is set with a separate edit:
    /// some servers' add endpoint rejects a status it converts twice.
    func add(type: MediaType, source: String, mediaId: String, status: MediaStatus = .planning) async throws {
        try await call("POST", "api/v1/media/\(type.rawValue)/", body: TrackingUpdate(
            mediaId: mediaId, source: source, progress: status == .completed ? 1 : 0))
        if status != .planning {
            try await update(type: type, source: source, mediaId: mediaId, TrackingUpdate(status: status.rawValue))
        }
    }

    func update(type: MediaType, source: String, mediaId: String, _ change: TrackingUpdate) async throws {
        try await call("PATCH", "api/v1/media/\(type.rawValue)/\(source)/\(mediaId)/", body: change)
    }

    func remove(type: MediaType, source: String, mediaId: String) async throws {
        try await call("DELETE", "api/v1/media/\(type.rawValue)/\(source)/\(mediaId)/")
    }

    func seasons(source: String, showId: String) async throws -> [MediaItem] {
        let page: Paginated<MediaItem> = try await get("api/v1/media/tv/\(source)/\(showId)/seasons/",
                                                       query: ["limit": "100"])
        return page.results
    }

    func episodes(source: String, showId: String, season: Int) async throws -> [MediaItem] {
        let page: Paginated<MediaItem> = try await get("api/v1/media/tv/\(source)/\(showId)/\(season)/episodes/",
                                                       query: ["limit": "200"])
        return page.results
    }

    /// Marks an episode watched now, creating its tracking (and its season's
    /// and show's) when the server doesn't have it yet; or untracks it.
    func setEpisodeWatched(source: String, showId: String, season: Int, episode: Int, watched: Bool) async throws {
        let episodePath = "api/v1/media/tv/\(source)/\(showId)/\(season)/\(episode)/"
        guard watched else {
            try await call("DELETE", episodePath)
            return
        }
        let now = TrackingUpdate(endDate: ISO8601DateFormatter().string(from: Date()))
        do {
            try await call("PATCH", episodePath, body: now)
            return
        } catch let error as APIError where error.status == 404 {
            // Not tracked yet: create it below.
        }
        do {
            try await call("POST", episodePath, body: now)
        } catch let error as APIError where error.status == 400 {
            // "Parent season is not tracked yet."
            try await trackSeason(source: source, showId: showId, season: season)
            try await call("POST", episodePath, body: now)
        } catch let error as APIError where error.status == 405 {
            throw APIError(status: 405, message:
                "This server can't create episode trackings. Update it, or mark the episode on the web first.")
        }
    }

    private func trackSeason(source: String, showId: String, season: Int) async throws {
        let seasonPath = "api/v1/media/tv/\(source)/\(showId)/\(season)/"
        let body = TrackingUpdate(status: MediaStatus.inProgress.rawValue)
        do {
            try await call("POST", seasonPath, body: body)
        } catch let error as APIError where error.status == 409 {
            return // already tracked
        } catch let error as APIError where error.status == 400 {
            // "Parent tv serie is not tracked yet."
            try await add(type: .tv, source: source, mediaId: showId, status: .inProgress)
            do {
                try await call("POST", seasonPath, body: body)
            } catch let error as APIError where error.status == 409 {
                return
            }
        }
    }

    func calendar(from start: Date, to end: Date) async throws -> [CalendarEvent] {
        let format = DateFormatter()
        format.calendar = Calendar(identifier: .gregorian)
        format.locale = Locale(identifier: "en_US_POSIX")
        format.dateFormat = "yyyy-MM-dd"
        let page: Paginated<CalendarEvent> = try await get("api/v1/calendar/", query: [
            "start_date": format.string(from: start),
            "end_date": format.string(from: end),
            "limit": "200",
        ])
        return page.results
    }
}
