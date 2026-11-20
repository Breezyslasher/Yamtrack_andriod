import Foundation

// Responses for the extended endpoints (cast, people, providers, artwork,
// lists, statistics). Fields are optional: servers without the fork's
// extensions don't send them.

/// How long to beat a game, in seconds (IGDB).
struct TimeToBeat: Decodable, Hashable {
    let hastily: Double?
    let normally: Double?
    let completely: Double?

    var isEmpty: Bool { hastily == nil && normally == nil && completely == nil }
}

/// A list holding the item, from a media's `lists` field.
struct ListMembership: Decodable, Hashable {
    let listId: Int?
}

struct CastMember: Decodable, Identifiable, Hashable {
    let personId: Int?
    let name: String?
    let character: String?
    let image: String?
    let order: Int?

    var id: String { "\(personId ?? 0)-\(character ?? "")-\(name ?? "")" }
}

/// GET /api/v1/people/{source}/{person_id}/
struct Person: Decodable {
    let name: String?
    let image: String?
    let biography: String?
    let birthday: String?
    let deathday: String?
    let placeOfBirth: String?
    let knownForDepartment: String?
}

/// A movie or TV show a person is known for.
struct KnownFor: Decodable, Identifiable, Hashable {
    let mediaId: String
    let source: String
    let mediaType: String
    let title: String?
    let image: String?
    let character: String?
    let jobs: [String]
    let releaseDate: String?

    var id: String { "\(mediaType)/\(source)/\(mediaId)" }
    var type: MediaType? { MediaType(rawValue: mediaType) }

    enum CodingKeys: String, CodingKey { case mediaId, source, mediaType, title, image, character, jobs, releaseDate }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        mediaId = c.flexibleString(.mediaId) ?? ""
        source = (try? c.decode(String.self, forKey: .source)) ?? ""
        mediaType = (try? c.decode(String.self, forKey: .mediaType)) ?? ""
        title = try? c.decodeIfPresent(String.self, forKey: .title)
        image = try? c.decodeIfPresent(String.self, forKey: .image)
        character = try? c.decodeIfPresent(String.self, forKey: .character)
        jobs = (try? c.decodeIfPresent([String].self, forKey: .jobs)) ?? []
        releaseDate = try? c.decodeIfPresent(String.self, forKey: .releaseDate)
    }
}

/// A streaming, rent or buy service. `link`, prices and their quality come
/// from JustWatch when the server enables it.
struct WatchProvider: Decodable, Identifiable, Hashable {
    let providerId: Int?
    let providerName: String?
    let image: String?
    let link: String?
    let buyPrice: String?
    let rentPrice: String?
    let buyQuality: String?
    let rentQuality: String?

    var id: String { "\(providerId ?? 0)-\(providerName ?? "")" }
}

/// GET .../providers/ — services for the server's region.
struct WatchProviders: Decodable {
    let region: String?
    /// TMDB's region-wide "where to watch" page.
    let link: String?
    let noCharge: [WatchProvider]?
    let paid: [WatchProvider]?
}

struct MediaImage: Decodable, Identifiable, Hashable {
    let image: String?
    let original: String?

    var id: String { image ?? original ?? UUID().uuidString }
    var url: String? { image ?? original }
}

/// GET .../images/
struct MediaImages: Decodable {
    let posters: [MediaImage]?
    let backdrops: [MediaImage]?
    let screenshots: [MediaImage]?
}

/// GET/PUT/DELETE .../cover/ — the server-wide custom cover.
struct Cover: Decodable {
    let image: String?
    let customImage: String?
}

struct CoverUpdate: Encodable {
    let image: String
}

/// A user's custom list. `items` is only sent by the list detail endpoint.
struct CustomList: Decodable, Identifiable, Hashable {
    let id: Int
    let name: String
    let description: String?
    let itemsCount: Int?

    static func == (a: CustomList, b: CustomList) -> Bool { a.id == b.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}

struct CustomListDetail: Decodable {
    let id: Int
    let name: String
    let items: Paginated<MediaItem>?
}

struct NewList: Encodable {
    let name: String
    let description: String?
}

/// GET /api/v1/statistics/ — only the per-status counts are shown.
struct UserStats: Decodable {
    struct Dataset: Decodable {
        let label: String?
        let total: Int?
    }
    struct StatusDistribution: Decodable {
        let datasets: [Dataset]?
    }

    let statusDistribution: StatusDistribution?

    func count(_ status: MediaStatus) -> Int {
        let wanted = status.name.lowercased()
        return statusDistribution?.datasets?
            .first { ($0.label ?? "").lowercased() == wanted }?.total ?? 0
    }

    var total: Int { statusDistribution?.datasets?.reduce(0) { $0 + ($1.total ?? 0) } ?? 0 }
}

/// GET .../{season}/{episode}/ — only the fields the episode list shows.
struct EpisodeDetail: Decodable {
    let title: String?
    let synopsis: String?
    let details: [String: JSONValue]

    enum CodingKeys: String, CodingKey { case title, synopsis, details }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        title = try? c.decodeIfPresent(String.self, forKey: .title)
        synopsis = try? c.decodeIfPresent(String.self, forKey: .synopsis)
        details = (try? c.decodeIfPresent([String: JSONValue].self, forKey: .details)) ?? [:]
    }
}
