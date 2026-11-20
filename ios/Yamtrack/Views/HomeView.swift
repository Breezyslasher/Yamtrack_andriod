import SwiftUI

/// Recently updated and planned media, in a row per media type.
struct HomeView: View {
    @Environment(AppState.self) private var appState
    @State private var recent: [MediaItem] = []
    @State private var planning: [MediaItem] = []
    @State private var loading = false
    @State private var error: String?
    @State private var artworkTarget: ArtworkTarget?

    var body: some View {
        LoadState(loading: loading, error: error, isEmpty: recent.isEmpty && planning.isEmpty,
                  emptyText: "Nothing tracked yet", retry: load) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 20) {
                    section("Recently updated", recent)
                    section("Planning", planning)
                }
                .padding(.vertical)
            }
        }
        .navigationTitle("Home")
        .refreshable { await load() }
        // Reload whenever the screen is shown again, e.g. after tracking something.
        .onAppear { Task { await load() } }
        .task { _ = await appState.checkArtworkSupport() }
        .sheet(item: $artworkTarget) { ArtworkPickerSheet(target: $0) }
    }

    @ViewBuilder
    private func section(_ title: String, _ items: [MediaItem]) -> some View {
        if !items.isEmpty {
            Text(title).font(.title2.bold()).padding(.horizontal)
            ForEach(MediaType.parentTypes) { type in
                let row = items.filter { $0.item?.type == type }
                if !row.isEmpty {
                    Text(type.pluralName).font(.headline).padding(.horizontal)
                    ScrollView(.horizontal, showsIndicators: false) {
                        LazyHStack(alignment: .top, spacing: 12) {
                            ForEach(row) {
                                MediaCard(entry: $0, actions: CardActions(changeArtwork: { artworkTarget = $0 }))
                            }
                        }
                        .padding(.horizontal)
                    }
                }
            }
        }
    }

    private func load() async {
        guard let client = appState.client, !loading else { return }
        loading = true
        defer { loading = false }
        do {
            async let updated = client.media(sort: "updated_desc")
            async let planned = client.media(status: .planning, sort: "updated_desc")
            (recent, planning) = try await (updated, planned)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}
