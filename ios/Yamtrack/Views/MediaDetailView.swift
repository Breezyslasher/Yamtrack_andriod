import SwiftUI

struct MediaDetailView: View {
    let ref: MediaRef

    @Environment(AppState.self) private var appState
    @State private var details: MediaDetails?
    @State private var seasons: [MediaItem] = []
    @State private var loading = false
    @State private var error: String?
    @State private var message: String?
    @State private var editingScore = false
    @State private var scoreText = ""
    @State private var confirmRemove = false
    @State private var cast: [CastMember] = []
    @State private var providers: WatchProviders?
    @State private var recommendations: [SearchResult] = []
    @State private var extrasLoaded = false
    @State private var artworkTarget: ArtworkTarget?
    @State private var listTarget: ListTarget?

    @Environment(\.openURL) private var openURL

    private var coverKey: String {
        AppState.coverKey(type: ref.type.rawValue, source: ref.source, mediaId: ref.mediaId)
    }

    var body: some View {
        LoadState(loading: loading, error: error, isEmpty: details == nil, retry: load) {
            if let details { content(details) }
        }
        .navigationTitle(details?.title ?? ref.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let d = details {
                Menu {
                    if appState.artworkSupported != false {
                        Button { artworkTarget = mediaArtworkTarget(d) } label: {
                            Label("Change artwork", systemImage: "photo")
                        }
                    }
                    if d.tracked {
                        Button {
                            listTarget = ListTarget(type: ref.type, source: ref.source, mediaId: ref.mediaId,
                                                    title: d.title ?? ref.title,
                                                    memberOf: Set(d.lists.compactMap(\.listId)))
                        } label: { Label("Add to list", systemImage: "text.badge.plus") }
                    }
                } label: {
                    Label("More", systemImage: "ellipsis.circle")
                }
            }
        }
        .task {
            _ = await appState.checkArtworkSupport()
            await load()
            await loadExtras()
        }
        .sheet(item: $artworkTarget) { target in
            ArtworkPickerSheet(target: target) { Task { await load() } }
        }
        .sheet(item: $listTarget) { target in
            AddToListSheet(target: target) { Task { await load() } }
        }
        .alert(message ?? "", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("OK", role: .cancel) {}
        }
        .alert("Your score", isPresented: $editingScore) {
            TextField("0.0 – 10.0", text: $scoreText).keyboardType(.decimalPad)
            Button("Save") { Task { await saveScore() } }
            Button("Clear", role: .destructive) { Task { await setScore(nil) } }
            Button("Cancel", role: .cancel) {}
        }
        .confirmationDialog("Remove from your library?", isPresented: $confirmRemove, titleVisibility: .visible) {
            Button("Remove", role: .destructive) { Task { await remove() } }
        }
    }

    @ViewBuilder
    private func content(_ d: MediaDetails) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                header(d)
                if let synopsis = d.synopsis, !synopsis.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Overview").font(.headline)
                        Text(synopsis).foregroundStyle(.secondary)
                    }
                }
                if d.type == .game, let ttb = d.timeToBeat, !ttb.isEmpty { timeToBeatSection(ttb) }
                statusSection(d)
                if let providers { ProvidersSection(providers: providers, openURL: { openURL($0) }) }
                if !seasons.isEmpty { seasonsSection }
                if !cast.isEmpty { castSection }
                if d.tracked {
                    Button(role: .destructive) { confirmRemove = true } label: {
                        Label("Remove from Library", systemImage: "trash").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
                if !recommendations.isEmpty { recommendationsSection }
            }
            .padding()
        }
    }

    private func mediaArtworkTarget(_ d: MediaDetails) -> ArtworkTarget {
        ArtworkTarget(type: ref.type, source: ref.source, mediaId: ref.mediaId, title: d.title ?? ref.title)
    }

    private func timeToBeatSection(_ ttb: TimeToBeat) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("How long to beat").font(.headline)
            HStack {
                ttbColumn("Hastily", ttb.hastily)
                ttbColumn("Normally", ttb.normally)
                ttbColumn("Completionist", ttb.completely)
            }
        }
    }

    private func ttbColumn(_ title: String, _ seconds: Double?) -> some View {
        VStack(spacing: 4) {
            Text(Self.hours(seconds)).font(.title3.bold())
            Text(title).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 8))
    }

    /// Seconds -> "12h" / "12.5h".
    private static func hours(_ seconds: Double?) -> String {
        guard let seconds, seconds > 0 else { return "—" }
        let hours = seconds / 3600
        return hours >= 10 || hours.truncatingRemainder(dividingBy: 1) < 0.05
            ? "\(Int(hours.rounded()))h" : String(format: "%.1fh", hours)
    }

    private var castSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Cast").font(.headline)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 14) {
                    ForEach(cast) { member in
                        let card = VStack(spacing: 4) {
                            PosterImage(url: member.image, cornerRadius: 40)
                                .frame(width: 80, height: 80)
                            Text(member.name ?? "").font(.caption).multilineTextAlignment(.center).lineLimit(2)
                            if let character = member.character, !character.isEmpty {
                                Text(character).font(.caption2).foregroundStyle(.secondary)
                                    .multilineTextAlignment(.center).lineLimit(2)
                            }
                        }
                        .frame(width: 90)
                        // People pages exist for TMDB only (anime voice actors come from AniList).
                        if ref.source == "tmdb", let id = member.personId {
                            NavigationLink(value: PersonRef(source: "tmdb", personId: id, name: member.name ?? "")) {
                                card
                            }
                            .buttonStyle(.plain)
                        } else {
                            card
                        }
                    }
                }
            }
        }
    }

    private var recommendationsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Recommendations").font(.headline)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 12) {
                    ForEach(recommendations) { result in
                        if let target = MediaRef(result: result) {
                            NavigationLink(value: target) {
                                VStack(alignment: .leading, spacing: 4) {
                                    PosterImage(url: appState.image(
                                        forKey: AppState.coverKey(type: result.mediaType, source: result.source,
                                                                  mediaId: result.mediaId),
                                        fallback: result.image))
                                        .frame(width: 100, height: 150)
                                    Text(result.title ?? "").font(.caption).lineLimit(2)
                                }
                                .frame(width: 100)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }

    private func header(_ d: MediaDetails) -> some View {
        HStack(alignment: .top, spacing: 16) {
            PosterImage(url: appState.image(forKey: coverKey, fallback: d.image))
                .frame(width: 120, height: 180)
                .onTapGesture {
                    // Tap the poster to pick new artwork, like on Android.
                    if appState.artworkSupported != false { artworkTarget = mediaArtworkTarget(d) }
                }
            VStack(alignment: .leading, spacing: 8) {
                Text(d.title ?? ref.title).font(.title3.bold())
                let release = MediaMeta.releaseLabel(d.details)
                let runtime = MediaMeta.runtimeLabel(d.details).map { d.type == .tv ? "\($0) per episode" : $0 }
                let line = [release, runtime].compactMap { $0 }.joined(separator: " · ")
                if !line.isEmpty {
                    Text(line).font(.subheadline).foregroundStyle(.secondary)
                }
                if let score = d.score, score > 0 {
                    let source = sourceLabel(d.source).map { " · \($0)" } ?? ""
                    Label("\(MediaMeta.score(score)) / 10\(source)", systemImage: "star.fill")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                Button {
                    scoreText = d.userScore.map { MediaMeta.score($0) } ?? ""
                    editingScore = true
                } label: {
                    if let score = d.userScore, score > 0 {
                        Label("\(MediaMeta.score(score)) / 10 · your score", systemImage: "star.fill")
                    } else {
                        Label("Tap to rate", systemImage: "star")
                    }
                }
                .foregroundStyle(.yellow)
                if !d.genres.isEmpty {
                    Text(d.genres.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }

    private func statusSection(_ d: MediaDetails) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(d.tracked ? "Status" : "Add to Library").font(.headline)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(MediaStatus.allCases) { status in
                        let selected = d.tracked && d.userStatus == status
                        Button(status.name) { Task { await setStatus(status) } }
                            .font(.subheadline)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 6)
                            .background(selected ? Theme.accent : Color.clear, in: Capsule())
                            .overlay(Capsule().stroke(Theme.accent, lineWidth: 1))
                            .foregroundStyle(selected ? .white : .primary)
                    }
                }
            }
        }
    }

    private var seasonsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Seasons").font(.headline)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 12) {
                    ForEach(seasons) { season in
                        if let number = season.item?.seasonNumber {
                            NavigationLink(value: SeasonRef(source: ref.source, showId: ref.mediaId, season: number)) {
                                VStack(alignment: .leading, spacing: 4) {
                                    PosterImage(url: appState.image(for: season.item))
                                        .frame(width: 100, height: 150)
                                    Text("Season \(number)").font(.footnote)
                                }
                            }
                            .buttonStyle(.plain)
                            .contextMenu {
                                if appState.artworkSupported != false {
                                    Button {
                                        artworkTarget = ArtworkTarget(type: .tv, source: ref.source, mediaId: ref.mediaId,
                                                                      season: number, title: "Season \(number)")
                                    } label: { Label("Change season cover", systemImage: "photo") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // MARK: - Actions

    private func load() async {
        guard let client = appState.client else { return }
        loading = true
        defer { loading = false }
        do {
            details = try await client.details(type: ref.type, source: ref.source, mediaId: ref.mediaId)
            error = nil
            if ref.type == .tv {
                seasons = ((try? await client.seasons(source: ref.source, showId: ref.mediaId)) ?? [])
                    .sorted { ($0.item?.seasonNumber ?? 0) < ($1.item?.seasonNumber ?? 0) }
            }
        } catch {
            self.error = error.localizedDescription
        }
    }

    /// Cast, providers and recommendations don't change with the user's
    /// edits, so they load once. Each is hidden when the server lacks it.
    private func loadExtras() async {
        guard let client = appState.client, !extrasLoaded else { return }
        extrasLoaded = true
        async let rec = fetchRecommendations(client)
        async let castList = fetchCast(client)
        async let whereToWatch = fetchProviders(client)
        recommendations = await rec
        cast = await castList
        providers = await whereToWatch
    }

    private func fetchRecommendations(_ client: APIClient) async -> [SearchResult] {
        (try? await client.recommendations(type: ref.type, source: ref.source, mediaId: ref.mediaId)) ?? []
    }

    /// The server has casts for movies, TV and anime.
    private func fetchCast(_ client: APIClient) async -> [CastMember] {
        guard [MediaType.movie, .tv, .anime].contains(ref.type) else { return [] }
        return (try? await client.cast(type: ref.type, source: ref.source, mediaId: ref.mediaId)) ?? []
    }

    private func fetchProviders(_ client: APIClient) async -> WatchProviders? {
        guard [MediaType.movie, .tv].contains(ref.type) else { return nil }
        return try? await client.providers(type: ref.type, source: ref.source, mediaId: ref.mediaId)
    }

    private func setStatus(_ status: MediaStatus) async {
        guard let client = appState.client, let d = details else { return }
        await perform {
            if d.tracked {
                try await client.update(type: ref.type, source: ref.source, mediaId: ref.mediaId,
                                        TrackingUpdate(status: status.rawValue))
            } else {
                try await client.add(type: ref.type, source: ref.source, mediaId: ref.mediaId, status: status)
            }
        }
    }

    private func saveScore() async {
        let text = scoreText.replacingOccurrences(of: ",", with: ".")
        guard let value = Double(text), (0...10).contains(value) else {
            message = "Enter a score between 0 and 10."
            return
        }
        await setScore(value)
    }

    private func setScore(_ value: Double?) async {
        guard let client = appState.client, let d = details else { return }
        await perform {
            if !d.tracked {
                try await client.add(type: ref.type, source: ref.source, mediaId: ref.mediaId)
            }
            // Clearing sends 0: the API leaves out nulls, and 0 means unrated.
            try await client.update(type: ref.type, source: ref.source, mediaId: ref.mediaId,
                                    TrackingUpdate(score: value ?? 0))
        }
    }

    private func remove() async {
        guard let client = appState.client else { return }
        await perform {
            try await client.remove(type: ref.type, source: ref.source, mediaId: ref.mediaId)
        }
    }

    /// Runs a change, then reloads to show the server's state.
    private func perform(_ change: () async throws -> Void) async {
        do {
            try await change()
        } catch {
            message = error.localizedDescription
        }
        await load()
    }
}

struct EpisodesView: View {
    let ref: SeasonRef

    @Environment(AppState.self) private var appState
    @AppStorage(hideUnwatchedEpisodeInfoKey) private var hideUnwatched = false
    @State private var episodes: [MediaItem] = []
    @State private var details: [Int: EpisodeDetail] = [:]
    @State private var loading = false
    @State private var error: String?
    @State private var message: String?

    var body: some View {
        LoadState(loading: loading, error: error, isEmpty: episodes.isEmpty,
                  emptyText: "No episodes", retry: load) {
            List(episodes) { episode in
                row(episode)
            }
            .listStyle(.plain)
        }
        .navigationTitle("Season \(ref.season)")
        .refreshable { await load() }
        .task { await load() }
        .alert(message ?? "", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("OK", role: .cancel) {}
        }
    }

    private func row(_ episode: MediaItem) -> some View {
        let number = episode.item?.episodeNumber ?? 0
        // Spoiler shield: no name, image or description until watched.
        let hide = hideUnwatched && !episode.tracked
        let detail = details[number]
        // The episode details carry the episode's own name; older servers'
        // lists name tracked episodes after the show.
        let name = detail?.title.flatMap { $0.isEmpty ? nil : $0 } ?? episode.title
        return HStack(alignment: .top, spacing: 12) {
            PosterImage(url: hide ? nil : episode.item?.image, cornerRadius: 6)
                .frame(width: 112, height: 63)
            VStack(alignment: .leading, spacing: 4) {
                Text(hide || name.isEmpty ? "Episode \(number)" : "E\(number) · \(name)")
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(2)
                if !hide, let detail {
                    let line = [MediaMeta.fullDate(detail.details["air_date"]?.text),
                                MediaMeta.runtimeLabel(detail.details)].compactMap { $0 }.joined(separator: " · ")
                    if !line.isEmpty { Text(line).font(.caption).foregroundStyle(.secondary) }
                    if let synopsis = detail.synopsis, !synopsis.isEmpty {
                        Text(synopsis).font(.caption).foregroundStyle(.secondary).lineLimit(3)
                    }
                }
            }
            Spacer()
            Button {
                Task { await toggle(number: number, watched: !episode.tracked) }
            } label: {
                Image(systemName: episode.tracked ? "checkmark.square.fill" : "square")
                    .font(.title2)
            }
            .buttonStyle(.borderless)
        }
        .task(id: number) {
            if !hide, details[number] == nil { await loadDetail(number) }
        }
    }

    private func load() async {
        guard let client = appState.client else { return }
        loading = true
        defer { loading = false }
        do {
            episodes = try await client.episodes(source: ref.source, showId: ref.showId, season: ref.season)
                .sorted { ($0.item?.episodeNumber ?? 0) < ($1.item?.episodeNumber ?? 0) }
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func loadDetail(_ number: Int) async {
        guard let client = appState.client else { return }
        if let detail = try? await client.episodeDetail(source: ref.source, showId: ref.showId,
                                                        season: ref.season, episode: number) {
            details[number] = detail
        }
    }

    private func toggle(number: Int, watched: Bool) async {
        guard let client = appState.client else { return }
        do {
            try await client.setEpisodeWatched(source: ref.source, showId: ref.showId,
                                               season: ref.season, episode: number, watched: watched)
        } catch {
            message = error.localizedDescription
        }
        await load()
    }
}
