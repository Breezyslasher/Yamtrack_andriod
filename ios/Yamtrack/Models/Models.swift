import Foundation

// Models for the Yamtrack REST API (/api/v1/). JSON keys are snake_case and
// decoded with .convertFromSnakeCase, so properties use camelCase.

enum MediaType: String, CaseIterable, Identifiable, Hashable {
    case movie, tv, season, episode, anime, manga, game, book, comic, boardgame

    var id: String { rawValue }

    /// Types the user tracks directly; seasons and episodes belong to a TV show.
    static let parentTypes: [MediaType] = [.movie, .tv, .anime, .manga, .game, .book, .comic, .boardgame]

    /// Name for a filter or section, e.g. "Movies".
    var pluralName: String {
        switch self {
        case .movie: return "Movies"
        case .tv: return "TV Shows"
        case .season: return "Seasons"
        case .episode: return "Episodes"
        case .anime: return "Anime"
        case .manga: return "Manga"
        case .game: return "Games"
        case .book: return "Books"
        case .comic: return "Comics"
        case .boardgame: return "Board Games"
        }
    }

    /// Label for a single item, e.g. "Movie"; seasons and episodes are "TV".
    var itemLabel: String {
        switch self {
        case .movie: return "Movie"
        case .tv, .season, .episode: return "TV"
        case .anime: return "Anime"
        case .manga: return "Manga"
        case .game: return "Game"
        case .book: return "Book"
        case .comic: return "Comic"
        case .boardgame: return "Board game"
        }
    }
}

/// Tracking status. The API sends and accepts the numeric code; older
/// servers send the name, so both are read.
enum MediaStatus: Int, CaseIterable, Identifiable, Hashable {
    case planning = 0, inProgress = 1, paused = 2, completed = 3, dropped = 4

    var id: Int { rawValue }

    var name: String {
        switch self {
        case .planning: return "Planning"
        case .inProgress: return "In Progress"
        case .paused: return "Paused"
        case .completed: return "Completed"
        case .dropped: return "Dropped"
        }
    }

    static func from(api value: String?) -> MediaStatus? {
        guard let value, !value.isEmpty else { return nil }
        if let code = Int(value) { return MediaStatus(rawValue: code) }
        let normalized = value.lowercased().replacingOccurrences(of: "_", with: " ")
        return allCases.first { $0.name.lowercased() == normalized }
    }
}

/// Name of the metadata provider, e.g. for "7.3 / 10 · TMDB".
func sourceLabel(_ source: String?) -> String? {
    switch source {
    case "tmdb": return "TMDB"
    case "mal": return "MyAnimeList"
    case "mangaupdates": return "MangaUpdates"
    case "igdb": return "IGDB"
    case "openlibrary": return "Open Library"
    case "hardcover": return "Hardcover"
    case "comicvine": return "Comic Vine"
    case "bgg": return "BoardGameGeek"
    default: return nil
    }
}

/// A list response: `{"pagination": {...}, "results": [...]}`.
struct Paginated<T: Decodable>: Decodable {
    let results: [T]
}

/// The media an entry is about. `media_id` arrives as a string or a number.
struct Item: Decodable, Hashable {
    let mediaId: String
    let source: String
    let mediaType: String
    let title: String?
    let image: String?
    let seasonNumber: Int?
    let episodeNumber: Int?

    var type: MediaType? { MediaType(rawValue: mediaType) }

    enum CodingKeys: String, CodingKey {
        case mediaId, source, mediaType, title, image, seasonNumber, episodeNumber
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        mediaId = c.flexibleString(.mediaId) ?? ""
        source = (try? c.decode(String.self, forKey: .source)) ?? ""
        mediaType = (try? c.decode(String.self, forKey: .mediaType)) ?? ""
        title = try? c.decodeIfPresent(String.self, forKey: .title)
        image = try? c.decodeIfPresent(String.self, forKey: .image)
        seasonNumber = try? c.decodeIfPresent(Int.self, forKey: .seasonNumber)
        episodeNumber = try? c.decodeIfPresent(Int.self, forKey: .episodeNumber)
    }
}

/// A tracked entry, from /media/, /media/{type}/, seasons and episodes lists.
struct MediaItem: Decodable, Identifiable, Hashable {
    let item: Item?
    let tracked: Bool
    let score: Double?
    let statusValue: String?
    let progress: Int?
    let progressedAt: String?
    let endDate: String?

    var id: String {
        guard let item else { return UUID().uuidString }
        return [item.mediaType, item.source, item.mediaId,
                item.seasonNumber.map(String.init) ?? "", item.episodeNumber.map(String.init) ?? ""]
            .joined(separator: "/")
    }
    var title: String { item?.title ?? "" }
    var status: MediaStatus? { MediaStatus.from(api: statusValue) }

    enum CodingKeys: String, CodingKey {
        case item, tracked, score, status, progress, progressedAt, endDate
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        item = try? c.decodeIfPresent(Item.self, forKey: .item)
        tracked = (try? c.decodeIfPresent(Bool.self, forKey: .tracked)) ?? false
        score = c.flexibleDouble(.score)
        statusValue = c.flexibleString(.status)
        progress = try? c.decodeIfPresent(Int.self, forKey: .progress)
        progressedAt = try? c.decodeIfPresent(String.self, forKey: .progressedAt)
        endDate = try? c.decodeIfPresent(String.self, forKey: .endDate)
    }
}

/// One consumption of a media (the user's tracking state is the first one).
struct HistoryEntry: Decodable, Hashable {
    let statusValue: String?
    let score: Double?
    let progress: Int?
    let endDate: String?
    let notes: String?

    var status: MediaStatus? { MediaStatus.from(api: statusValue) }

    enum CodingKeys: String, CodingKey { case status, score, progress, endDate, notes }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        statusValue = c.flexibleString(.status)
        score = c.flexibleDouble(.score)
        progress = try? c.decodeIfPresent(Int.self, forKey: .progress)
        endDate = try? c.decodeIfPresent(String.self, forKey: .endDate)
        notes = try? c.decodeIfPresent(String.self, forKey: .notes)
    }
}

/// GET /api/v1/media/{type}/{source}/{id}/ — metadata plus the user's state.
struct MediaDetails: Decodable {
    let mediaId: String
    let source: String?
    let mediaType: String?
    let title: String?
    let image: String?
    let synopsis: String?
    let genres: [String]
    let score: Double?
    let details: [String: JSONValue]
    let tracked: Bool
    let consumptions: [HistoryEntry]

    var type: MediaType? { mediaType.flatMap(MediaType.init(rawValue:)) }
    var userEntry: HistoryEntry? { consumptions.first }
    var userStatus: MediaStatus? { userEntry?.status }
    var userScore: Double? { userEntry?.score }

    enum CodingKeys: String, CodingKey {
        case mediaId, source, mediaType, title, image, synopsis, genres, score, details, tracked, consumptions
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        mediaId = c.flexibleString(.mediaId) ?? ""
        source = try? c.decodeIfPresent(String.self, forKey: .source)
        mediaType = try? c.decodeIfPresent(String.self, forKey: .mediaType)
        title = try? c.decodeIfPresent(String.self, forKey: .title)
        image = try? c.decodeIfPresent(String.self, forKey: .image)
        synopsis = try? c.decodeIfPresent(String.self, forKey: .synopsis)
        genres = (try? c.decodeIfPresent([String].self, forKey: .genres)) ?? []
        score = c.flexibleDouble(.score)
        details = (try? c.decodeIfPresent([String: JSONValue].self, forKey: .details)) ?? [:]
        tracked = (try? c.decodeIfPresent(Bool.self, forKey: .tracked)) ?? false
        consumptions = (try? c.decodeIfPresent([HistoryEntry].self, forKey: .consumptions)) ?? []
    }
}

/// GET /api/v1/search/{type}/ result.
struct SearchResult: Decodable, Identifiable, Hashable {
    let mediaId: String
    let source: String
    let mediaType: String
    let title: String?
    let image: String?
    let year: String?

    var id: String { "\(mediaType)/\(source)/\(mediaId)" }
    var type: MediaType? { MediaType(rawValue: mediaType) }

    enum CodingKeys: String, CodingKey { case mediaId, source, mediaType, title, image, year }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        mediaId = c.flexibleString(.mediaId) ?? ""
        source = (try? c.decode(String.self, forKey: .source)) ?? ""
        mediaType = (try? c.decode(String.self, forKey: .mediaType)) ?? ""
        title = try? c.decodeIfPresent(String.self, forKey: .title)
        image = try? c.decodeIfPresent(String.self, forKey: .image)
        year = c.flexibleString(.year)
    }
}

/// GET /api/v1/calendar/ event: a release or an airing.
struct CalendarEvent: Decodable, Identifiable, Hashable {
    let eventId: Int?
    let item: Item?
    let datetime: String?
    let contentNumber: Int?

    var id: String { "\(eventId ?? 0)-\(datetime ?? "")-\(item?.mediaId ?? "")" }

    enum CodingKeys: String, CodingKey { case id, item, datetime, contentNumber }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        eventId = try? c.decodeIfPresent(Int.self, forKey: .id)
        item = try? c.decodeIfPresent(Item.self, forKey: .item)
        datetime = try? c.decodeIfPresent(String.self, forKey: .datetime)
        contentNumber = try? c.decodeIfPresent(Int.self, forKey: .contentNumber)
    }
}

/// Error body: `{"detail": "..."}`.
struct APIErrorBody: Decodable {
    let detail: String?
}

/// Body for creating or editing a tracking. nil fields are left out.
struct TrackingUpdate: Encodable {
    var mediaId: String?
    var source: String?
    var status: Int?
    var score: Double?
    var progress: Int?
    var endDate: String?
}

/// Any JSON value, for the provider-specific `details` object.
enum JSONValue: Decodable, Hashable {
    case string(String), number(Double), bool(Bool), array([JSONValue]), object([String: JSONValue]), null

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null }
        else if let v = try? c.decode(Bool.self) { self = .bool(v) }
        else if let v = try? c.decode(Double.self) { self = .number(v) }
        else if let v = try? c.decode(String.self) { self = .string(v) }
        else if let v = try? c.decode([JSONValue].self) { self = .array(v) }
        else { self = .object(try c.decode([String: JSONValue].self)) }
    }

    /// The value as display text, for strings and numbers.
    var text: String? {
        switch self {
        case .string(let s): return s.isEmpty ? nil : s
        case .number(let n): return n == n.rounded() ? String(Int(n)) : String(n)
        default: return nil
        }
    }
}

extension KeyedDecodingContainer {
    /// A string that some servers send as a number.
    func flexibleString(_ key: Key) -> String? {
        if let s = try? decodeIfPresent(String.self, forKey: key) { return s }
        if let i = try? decodeIfPresent(Int.self, forKey: key) { return String(i) }
        if let d = try? decodeIfPresent(Double.self, forKey: key) { return String(d) }
        return nil
    }

    /// A number that some servers send as a string.
    func flexibleDouble(_ key: Key) -> Double? {
        if let d = try? decodeIfPresent(Double.self, forKey: key) { return d }
        if let s = try? decodeIfPresent(String.self, forKey: key) { return Double(s) }
        return nil
    }
}
