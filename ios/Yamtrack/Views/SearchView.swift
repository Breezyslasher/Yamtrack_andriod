import SwiftUI

/// Search a provider and add results to the library.
struct SearchView: View {
    @Environment(AppState.self) private var appState
    @State private var query = ""
    @State private var type: MediaType = .movie
    @State private var results: [SearchResult] = []
    @State private var loading = false
    @State private var error: String?
    @State private var message: String?
    @State private var artworkTarget: ArtworkTarget?

    var body: some View {
        VStack(spacing: 8) {
            ChipRow(options: MediaType.parentTypes, selection: $type) { $0.pluralName }
            if loading {
                ProgressView().frame(maxHeight: .infinity)
            } else if let error {
                ContentUnavailableView("Search failed", systemImage: "exclamationmark.triangle",
                                       description: Text(error))
            } else if results.isEmpty {
                ContentUnavailableView("Search \(type.pluralName)", systemImage: "magnifyingglass",
                                       description: Text("Type a title and press Search."))
            } else {
                List(results) { result in
                    row(result)
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("Search")
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always))
        .onSubmit(of: .search) { Task { await search() } }
        .onChange(of: type) { if !query.isEmpty { Task { await search() } } }
        .alert(message ?? "", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("OK", role: .cancel) {}
        }
        .task { _ = await appState.checkArtworkSupport() }
        .sheet(item: $artworkTarget) { ArtworkPickerSheet(target: $0) }
    }

    @ViewBuilder
    private func row(_ result: SearchResult) -> some View {
        let content = HStack(spacing: 12) {
            PosterImage(url: appState.image(forKey: AppState.coverKey(type: result.mediaType, source: result.source,
                                                                      mediaId: result.mediaId),
                                            fallback: result.image), cornerRadius: 6)
                .frame(width: 56, height: 84)
            VStack(alignment: .leading, spacing: 4) {
                Text(result.title ?? "Unknown").font(.headline).lineLimit(2)
                let label = result.type?.itemLabel ?? result.mediaType
                Text([label, result.year].compactMap { $0 }.joined(separator: " · "))
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button {
                Task { await add(result) }
            } label: {
                Image(systemName: "plus.circle.fill").font(.title2)
            }
            .buttonStyle(.borderless)
        }
        Group {
            if let ref = MediaRef(result: result) {
                NavigationLink(value: ref) { content }
            } else {
                content
            }
        }
        .contextMenu {
            if appState.artworkSupported != false, let type = result.type, type != .season, type != .episode {
                Button {
                    artworkTarget = ArtworkTarget(type: type, source: result.source, mediaId: result.mediaId,
                                                  title: result.title ?? "")
                } label: { Label("Change artwork", systemImage: "photo") }
            }
        }
    }

    private func search() async {
        let text = query.trimmingCharacters(in: .whitespaces)
        guard let client = appState.client, !text.isEmpty else { return }
        loading = true
        defer { loading = false }
        do {
            results = try await client.search(type: type, query: text)
            error = nil
        } catch {
            results = []
            self.error = error.localizedDescription
        }
    }

    private func add(_ result: SearchResult) async {
        guard let client = appState.client, let type = result.type else { return }
        do {
            try await client.add(type: type, source: result.source, mediaId: result.mediaId)
            message = "Added \(result.title ?? "it") to Planning"
        } catch {
            message = "Failed to add: \(error.localizedDescription)"
        }
    }
}
