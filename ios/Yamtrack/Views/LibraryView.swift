import SwiftUI

/// Everything tracked of one media type, filtered by status.
struct LibraryView: View {
    @Environment(AppState.self) private var appState
    @State private var type: MediaType = .movie
    @State private var status: MediaStatus? = nil
    @State private var sort = "added_desc"
    @State private var items: [MediaItem] = []
    @State private var loading = false
    @State private var error: String?

    private struct SortOption: Hashable {
        let value: String
        let label: String
    }

    private let sorts = [
        SortOption(value: "added_desc", label: "Recently added"),
        SortOption(value: "added", label: "Oldest added"),
        SortOption(value: "updated_desc", label: "Recently updated"),
        SortOption(value: "title", label: "Title (A–Z)"),
        SortOption(value: "title_desc", label: "Title (Z–A)"),
    ]

    var body: some View {
        VStack(spacing: 8) {
            ChipRow(options: MediaType.parentTypes, selection: $type) { $0.pluralName }
            ChipRow(options: [nil] + MediaStatus.allCases.map { Optional($0) }, selection: $status) {
                $0?.name ?? "All"
            }
            LoadState(loading: loading, error: error, isEmpty: items.isEmpty,
                      emptyText: "No \(type.pluralName.lowercased()) here", retry: load) {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 105), spacing: 12)], spacing: 16) {
                        ForEach(items) { MediaCard(entry: $0, width: nil) }
                    }
                    .padding()
                }
            }
        }
        .navigationTitle("Library")
        .toolbar {
            Menu {
                Picker("Sort", selection: $sort) {
                    ForEach(sorts, id: \.value) { Text($0.label).tag($0.value) }
                }
            } label: {
                Label("Sort", systemImage: "arrow.up.arrow.down")
            }
        }
        .refreshable { await load() }
        .task(id: "\(type.rawValue)|\(status?.rawValue ?? -1)|\(sort)") { await load() }
    }

    private func load() async {
        guard let client = appState.client else { return }
        loading = true
        defer { loading = false }
        do {
            items = try await client.media(type: type, status: status, sort: sort)
            error = nil
        } catch {
            items = []
            self.error = error.localizedDescription
        }
    }
}
