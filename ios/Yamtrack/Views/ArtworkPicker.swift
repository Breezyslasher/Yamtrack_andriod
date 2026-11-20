import SwiftUI

/// What a cover belongs to: a media, or one of a TV show's seasons.
struct ArtworkTarget: Identifiable, Hashable {
    let type: MediaType
    let source: String
    let mediaId: String
    let season: Int?
    let title: String

    var id: String { key }

    /// Key for AppState's cover overrides, matching AppState.image(for:).
    var key: String {
        season == nil
            ? AppState.coverKey(type: type.rawValue, source: source, mediaId: mediaId)
            : AppState.coverKey(type: MediaType.season.rawValue, source: source, mediaId: mediaId, season: season)
    }

    init(type: MediaType, source: String, mediaId: String, season: Int? = nil, title: String) {
        self.type = type
        self.source = source
        self.mediaId = mediaId
        self.season = season
        self.title = title
    }

    /// Nil for items without a cover of their own (episodes).
    init?(item: Item?) {
        guard let item, let type = item.type, !item.mediaId.isEmpty else { return nil }
        switch type {
        case .episode:
            return nil
        case .season:
            guard let number = item.seasonNumber else { return nil }
            self.init(type: .tv, source: item.source, mediaId: item.mediaId, season: number, title: item.title ?? "")
        default:
            self.init(type: type, source: item.source, mediaId: item.mediaId, title: item.title ?? "")
        }
    }
}

/// Grid of images to choose a cover from. The choice is saved on the
/// server, for every user and the web interface too.
struct ArtworkPickerSheet: View {
    let target: ArtworkTarget
    var onChanged: () -> Void = {}

    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss
    @State private var options: [MediaImage] = []
    @State private var custom: String?
    @State private var loading = true
    @State private var saving = false
    @State private var error: String?
    @State private var saveError: String?

    var body: some View {
        NavigationStack {
            Group {
                if loading {
                    ProgressView()
                } else if let error, options.isEmpty {
                    ContentUnavailableView("Couldn't load artwork", systemImage: "photo",
                                           description: Text(error))
                } else if options.isEmpty {
                    ContentUnavailableView("No artwork available", systemImage: "photo")
                } else {
                    ScrollView {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 100), spacing: 10)], spacing: 10) {
                            ForEach(options) { option in
                                Button { Task { await save(option.url) } } label: {
                                    PosterImage(url: option.url)
                                        .aspectRatio(2 / 3, contentMode: .fit)
                                        .overlay {
                                            if option.url == custom || option.original == custom {
                                                RoundedRectangle(cornerRadius: 8).stroke(Theme.accent, lineWidth: 3)
                                            }
                                        }
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding()
                    }
                }
            }
            .disabled(saving)
            .navigationTitle(target.season == nil ? "Change artwork" : "Change season cover")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                if custom != nil {
                    ToolbarItem(placement: .destructiveAction) {
                        Button("Reset") { Task { await save(nil) } }
                    }
                }
            }
            .task { await load() }
            .alert(saveError ?? "", isPresented: Binding(get: { saveError != nil },
                                                         set: { if !$0 { saveError = nil } })) {
                Button("OK", role: .cancel) {}
            }
        }
    }

    private func load() async {
        guard let client = appState.client else { return }
        async let images = client.artworkOptions(type: target.type, source: target.source,
                                                 mediaId: target.mediaId, season: target.season)
        async let cover = try? client.cover(type: target.type, source: target.source,
                                            mediaId: target.mediaId, season: target.season)
        do {
            options = try await images
        } catch {
            self.error = error.localizedDescription
        }
        let current = await cover
        custom = current?.customImage
        loading = false
    }

    /// nil resets the cover to the provider's.
    private func save(_ url: String?) async {
        guard let client = appState.client else { return }
        saving = true
        defer { saving = false }
        do {
            let cover = try await client.setCover(type: target.type, source: target.source,
                                                  mediaId: target.mediaId, season: target.season, image: url)
            appState.coverChanged(key: target.key, image: cover.image)
            onChanged()
            dismiss()
        } catch {
            saveError = error.localizedDescription
        }
    }
}
