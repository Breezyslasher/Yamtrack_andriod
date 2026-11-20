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

    var body: some View {
        LoadState(loading: loading, error: error, isEmpty: details == nil, retry: load) {
            if let details { content(details) }
        }
        .navigationTitle(details?.title ?? ref.title)
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
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
                statusSection(d)
                if !seasons.isEmpty { seasonsSection }
                if d.tracked {
                    Button(role: .destructive) { confirmRemove = true } label: {
                        Label("Remove from Library", systemImage: "trash").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
            }
            .padding()
        }
    }

    private func header(_ d: MediaDetails) -> some View {
        HStack(alignment: .top, spacing: 16) {
            PosterImage(url: d.image)
                .frame(width: 120, height: 180)
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
                                    PosterImage(url: season.item?.image)
                                        .frame(width: 100, height: 150)
                                    Text("Season \(number)").font(.footnote)
                                }
                            }
                            .buttonStyle(.plain)
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
    @State private var episodes: [MediaItem] = []
    @State private var loading = false
    @State private var error: String?
    @State private var message: String?

    var body: some View {
        LoadState(loading: loading, error: error, isEmpty: episodes.isEmpty,
                  emptyText: "No episodes", retry: load) {
            List(episodes) { episode in
                let number = episode.item?.episodeNumber ?? 0
                HStack(spacing: 12) {
                    PosterImage(url: episode.item?.image, cornerRadius: 6)
                        .frame(width: 112, height: 63)
                    Text("E\(number) · \(episode.title.isEmpty ? "Episode \(number)" : episode.title)")
                        .lineLimit(2)
                    Spacer()
                    Button {
                        Task { await toggle(number: number, watched: !episode.tracked) }
                    } label: {
                        Image(systemName: episode.tracked ? "checkmark.square.fill" : "square")
                            .font(.title2)
                    }
                    .buttonStyle(.borderless)
                }
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
