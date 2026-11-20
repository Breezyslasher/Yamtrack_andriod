import Foundation

// Extended endpoints: cast, people, providers, artwork, lists, statistics.
// Several only exist on servers with the extended API; callers hide what
// fails.

extension APIClient {
    private func mediaPath(_ type: MediaType, _ source: String, _ mediaId: String) -> String {
        "api/v1/media/\(type.rawValue)/\(source)/\(mediaId)/"
    }

    func recommendations(type: MediaType, source: String, mediaId: String) async throws -> [SearchResult] {
        try await get(mediaPath(type, source, mediaId) + "recommendations/")
    }

    func cast(type: MediaType, source: String, mediaId: String) async throws -> [CastMember] {
        let page: Paginated<CastMember> = try await get(mediaPath(type, source, mediaId) + "cast/",
                                                        query: ["limit": "50"])
        return page.results.sorted { ($0.order ?? .max) < ($1.order ?? .max) }
    }

    /// Uses the server's region preference; without one (400), the device's.
    func providers(type: MediaType, source: String, mediaId: String) async throws -> WatchProviders {
        let path = mediaPath(type, source, mediaId) + "providers/"
        do {
            return try await get(path)
        } catch let error as APIError where error.status == 400 {
            guard let region = Locale.current.region?.identifier else { throw error }
            return try await get(path, query: ["region": region])
        }
    }

    func person(source: String, personId: Int) async throws -> Person {
        try await get("api/v1/people/\(source)/\(personId)/")
    }

    /// `mediaType` nil = movies and TV together.
    func knownFor(source: String, personId: Int, mediaType: MediaType?) async throws -> [KnownFor] {
        let page: Paginated<KnownFor> = try await get("api/v1/people/\(source)/\(personId)/known_for/",
                                                      query: ["media_type": mediaType?.rawValue, "limit": "100"])
        // Newest first; undated (announced) titles at the top.
        return page.results.sorted { ($0.releaseDate ?? "9999") > ($1.releaseDate ?? "9999") }
    }

    // MARK: Artwork

    /// Whether the server has the images and cover endpoints (a fork
    /// extension). OPTIONS doesn't run the view, so nothing is fetched from
    /// the provider: 404 means the route doesn't exist.
    func supportsArtwork() async -> Bool {
        let code = await status("OPTIONS", "api/v1/media/movie/tmdb/603/images/")
        return code != 404
    }

    /// Images to pick a cover from, posters first.
    func artworkOptions(type: MediaType, source: String, mediaId: String, season: Int?) async throws -> [MediaImage] {
        let path = season.map { mediaPath(type, source, mediaId) + "\($0)/images/" }
            ?? mediaPath(type, source, mediaId) + "images/"
        let images: MediaImages = try await get(path)
        let posters = images.posters ?? []
        let options = posters.isEmpty ? (images.backdrops ?? []) + (images.screenshots ?? []) : posters
        return options.filter { $0.url?.isEmpty == false }
    }

    private func coverPath(_ type: MediaType, _ source: String, _ mediaId: String, _ season: Int?) -> String {
        season.map { mediaPath(type, source, mediaId) + "\($0)/cover/" } ?? mediaPath(type, source, mediaId) + "cover/"
    }

    func cover(type: MediaType, source: String, mediaId: String, season: Int?) async throws -> Cover {
        try await get(coverPath(type, source, mediaId, season))
    }

    /// Sets the cover for every user of the server; nil resets it.
    func setCover(type: MediaType, source: String, mediaId: String, season: Int?, image: String?) async throws -> Cover {
        let path = coverPath(type, source, mediaId, season)
        if let image {
            return try await request("PUT", path, body: CoverUpdate(image: image))
        }
        return try await request("DELETE", path)
    }

    // MARK: Lists

    func lists() async throws -> [CustomList] {
        let page: Paginated<CustomList> = try await get("api/v1/lists/", query: ["limit": "100"])
        return page.results
    }

    func listItems(id: Int) async throws -> [MediaItem] {
        let detail: CustomListDetail = try await get("api/v1/lists/\(id)/", query: ["limit": "200"])
        return detail.items?.results ?? []
    }

    func createList(name: String, description: String?) async throws {
        try await call("POST", "api/v1/lists/", body: NewList(name: name, description: description))
    }

    func deleteList(id: Int) async throws {
        try await call("DELETE", "api/v1/lists/\(id)/")
    }

    func setInList(_ inList: Bool, listId: Int, type: MediaType, source: String, mediaId: String) async throws {
        try await call(inList ? "PUT" : "DELETE", mediaPath(type, source, mediaId) + "lists/\(listId)/")
    }

    // MARK: Other

    func statistics() async throws -> UserStats {
        // start/end "all": the whole history, not the default last year.
        try await get("api/v1/statistics/", query: ["start_date": "all", "end_date": "all"])
    }

    func episodeDetail(source: String, showId: String, season: Int, episode: Int) async throws -> EpisodeDetail {
        try await get("api/v1/media/tv/\(source)/\(showId)/\(season)/\(episode)/")
    }
}
