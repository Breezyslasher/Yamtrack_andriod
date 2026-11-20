import SwiftUI

// MARK: - Navigation targets

/// A media to open; seasons and episodes open their TV show.
struct MediaRef: Hashable {
    let type: MediaType
    let source: String
    let mediaId: String
    let title: String

    init(type: MediaType, source: String, mediaId: String, title: String) {
        self.type = (type == .season || type == .episode) ? .tv : type
        self.source = source
        self.mediaId = mediaId
        self.title = title
    }

    init?(item: Item?) {
        guard let item, let type = item.type, !item.mediaId.isEmpty else { return nil }
        self.init(type: type, source: item.source, mediaId: item.mediaId, title: item.title ?? "")
    }

    init?(result: SearchResult) {
        guard let type = result.type, !result.mediaId.isEmpty else { return nil }
        self.init(type: type, source: result.source, mediaId: result.mediaId, title: result.title ?? "")
    }
}

/// A season's episode list.
struct SeasonRef: Hashable {
    let source: String
    let showId: String
    let season: Int
}

extension View {
    /// Where MediaRef and SeasonRef navigation values lead, in any tab.
    func mediaDestinations() -> some View {
        navigationDestination(for: MediaRef.self) { MediaDetailView(ref: $0) }
            .navigationDestination(for: SeasonRef.self) { EpisodesView(ref: $0) }
            .navigationDestination(for: PersonRef.self) { PersonView(ref: $0) }
    }
}

// MARK: - Theme

enum Theme {
    static let accent = Color(red: 0x4F / 255, green: 0x46 / 255, blue: 0xE5 / 255)
    static let surface = Color(red: 0x2A / 255, green: 0x2E / 255, blue: 0x33 / 255)
    static let background = Color(red: 0x21 / 255, green: 0x25 / 255, blue: 0x29 / 255)
}

// MARK: - Components

/// A poster that falls back to a placeholder (missing or unreadable images,
/// such as the server's SVG "no image" picture).
struct PosterImage: View {
    let url: String?
    var cornerRadius: CGFloat = 8

    var body: some View {
        AsyncImage(url: url.flatMap(URL.init(string:))) { phase in
            if let image = phase.image {
                image.resizable().scaledToFill()
            } else {
                ZStack {
                    Theme.surface
                    Image(systemName: "photo").foregroundStyle(.secondary)
                }
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
    }
}

/// Long-press actions a screen offers on its poster cards.
struct CardActions {
    var changeArtwork: ((ArtworkTarget) -> Void)?
    var addToList: ((MediaItem) -> Void)?
    var removeFromList: ((MediaItem) -> Void)?
}

/// Poster card with title and status, linking to the media.
struct MediaCard: View {
    let entry: MediaItem
    var width: CGFloat? = 120
    var actions = CardActions()

    @Environment(AppState.self) private var appState

    var body: some View {
        let card = VStack(alignment: .leading, spacing: 4) {
            PosterImage(url: appState.image(for: entry.item))
                .aspectRatio(2 / 3, contentMode: .fit)
            Text(entry.title)
                .font(.footnote)
                .lineLimit(2, reservesSpace: true)
            if let status = entry.status {
                StatusBadge(status: status)
            }
        }
        .frame(width: width)

        Group {
            if let ref = MediaRef(item: entry.item) {
                NavigationLink(value: ref) { card }.buttonStyle(.plain)
            } else {
                card
            }
        }
        .contextMenu { menu }
    }

    @ViewBuilder
    private var menu: some View {
        if let change = actions.changeArtwork, appState.artworkSupported != false,
           let target = ArtworkTarget(item: entry.item) {
            Button { change(target) } label: { Label("Change artwork", systemImage: "photo") }
        }
        if let add = actions.addToList {
            Button { add(entry) } label: { Label("Add to list", systemImage: "text.badge.plus") }
        }
        if let remove = actions.removeFromList {
            Button(role: .destructive) { remove(entry) } label: {
                Label("Remove from list", systemImage: "trash")
            }
        }
    }
}

struct StatusBadge: View {
    let status: MediaStatus

    var body: some View {
        Text(status.name)
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(Theme.accent, in: RoundedRectangle(cornerRadius: 4))
            .foregroundStyle(.white)
    }
}

/// A horizontally scrolling row of single-choice chips.
struct ChipRow<Value: Hashable>: View {
    let options: [Value]
    @Binding var selection: Value
    let label: (Value) -> String

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(options, id: \.self) { option in
                    let selected = option == selection
                    Button(label(option)) { selection = option }
                        .font(.subheadline)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background(selected ? Theme.accent : Color.clear, in: Capsule())
                        .overlay(Capsule().stroke(Theme.accent, lineWidth: 1))
                        .foregroundStyle(selected ? .white : .primary)
                }
            }
            .padding(.horizontal)
        }
    }
}

// MARK: - Formatting

enum MediaMeta {
    private static func text(_ details: [String: JSONValue], _ key: String) -> String? {
        details[key]?.text
    }

    /// "2012-11-09" -> "Nov 9, 2012" in the user's format; a year stays a year.
    static func fullDate(_ value: String?) -> String? {
        guard let value, !value.isEmpty else { return nil }
        let day = String(value.prefix(10))
        let parser = DateFormatter()
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.dateFormat = "yyyy-MM-dd"
        if day.count == 10, let date = parser.date(from: day) {
            return date.formatted(date: .abbreviated, time: .omitted)
        }
        return value.range(of: #"\d{4}(-\d{2})?"#, options: .regularExpression).map { String(value[$0]) }
    }

    /// Release date, or an air-date range: "Sep 3, 2015 – May 2, 2019".
    static func releaseLabel(_ details: [String: JSONValue]) -> String? {
        let start = ["first_air_date", "release_date", "air_date", "start_date", "start_year", "year"]
            .lazy.compactMap { fullDate(text(details, $0)) }.first
        let end = ["last_air_date", "end_date", "end_year"]
            .lazy.compactMap { fullDate(text(details, $0)) }.first
        guard let start else { return nil }
        guard let end, end != start else { return start }
        return "\(start) – \(end)"
    }

    /// "2h 30m" (TMDB sends it formatted) or "45 min" from plain minutes.
    static func runtimeLabel(_ details: [String: JSONValue]) -> String? {
        guard let value = ["runtime", "length", "duration"].lazy.compactMap({ text(details, $0) }).first
        else { return nil }
        return Int(value).map { "\($0) min" } ?? value
    }

    static func score(_ value: Double) -> String { String(format: "%.1f", value) }
}

enum CalendarDay {
    /// The event's day on this device. Events without a time of their own
    /// carry the server's 11:59:59.999999 UTC sentinel and keep their date.
    static func localDay(_ raw: String?) -> Date? {
        guard let raw, raw.count >= 10 else { return nil }
        let dayOnly = DateFormatter()
        dayOnly.locale = Locale(identifier: "en_US_POSIX")
        dayOnly.dateFormat = "yyyy-MM-dd"
        if raw.range(of: #"T11:59:59(\.9+)?(Z|\+00:00)$"#, options: .regularExpression) != nil {
            return dayOnly.date(from: String(raw.prefix(10)))
        }
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = iso.date(from: raw) { return Calendar.current.startOfDay(for: date) }
        iso.formatOptions = [.withInternetDateTime]
        if let date = iso.date(from: raw) { return Calendar.current.startOfDay(for: date) }
        return dayOnly.date(from: String(raw.prefix(10)))
    }
}

/// Loading / error / content wrapper used by the screens.
struct LoadState<Content: View>: View {
    let loading: Bool
    let error: String?
    let isEmpty: Bool
    var emptyText: String = "Nothing here yet."
    let retry: () async -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        if let error, isEmpty {
            ContentUnavailableView {
                Label("Couldn't load", systemImage: "exclamationmark.triangle")
            } description: {
                Text(error)
            } actions: {
                Button("Try again") { Task { await retry() } }
            }
        } else if loading && isEmpty {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if isEmpty {
            ContentUnavailableView(emptyText, systemImage: "tray")
        } else {
            content()
        }
    }
}
