import SwiftUI

/// The media an add-to-list sheet is for, with the lists already holding it.
struct ListTarget: Identifiable, Hashable {
    let type: MediaType
    let source: String
    let mediaId: String
    let title: String
    let memberOf: Set<Int>

    var id: String { "\(type.rawValue)/\(source)/\(mediaId)" }

    init(type: MediaType, source: String, mediaId: String, title: String, memberOf: Set<Int>) {
        self.type = type
        self.source = source
        self.mediaId = mediaId
        self.title = title
        self.memberOf = memberOf
    }

    init?(entry: MediaItem) {
        guard let ref = MediaRef(item: entry.item) else { return nil }
        self.init(type: ref.type, source: ref.source, mediaId: ref.mediaId, title: ref.title,
                  memberOf: Set(entry.lists.compactMap(\.listId)))
    }
}

/// Choose which lists hold an item; saves only the changes.
struct AddToListSheet: View {
    let target: ListTarget
    var onSaved: () -> Void = {}

    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss
    @State private var lists: [CustomList] = []
    @State private var selected: Set<Int> = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Group {
                if loading {
                    ProgressView()
                } else if lists.isEmpty {
                    ContentUnavailableView("No lists yet", systemImage: "list.bullet",
                                           description: Text(error ?? "Create a list in Settings → Lists."))
                } else {
                    List(lists) { list in
                        Button {
                            if selected.contains(list.id) { selected.remove(list.id) } else { selected.insert(list.id) }
                        } label: {
                            HStack {
                                Text(list.name)
                                Spacer()
                                if selected.contains(list.id) {
                                    Image(systemName: "checkmark").foregroundStyle(Theme.accent)
                                }
                            }
                        }
                        .foregroundStyle(.primary)
                    }
                }
            }
            .navigationTitle("Add to list")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }.disabled(loading || lists.isEmpty)
                }
            }
            .task { await load() }
        }
    }

    private func load() async {
        selected = target.memberOf
        do {
            lists = try await appState.client?.lists() ?? []
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    private func save() async {
        guard let client = appState.client else { return }
        do {
            for id in selected.subtracting(target.memberOf) {
                try await client.setInList(true, listId: id, type: target.type, source: target.source, mediaId: target.mediaId)
            }
            for id in target.memberOf.subtracting(selected) {
                try await client.setInList(false, listId: id, type: target.type, source: target.source, mediaId: target.mediaId)
            }
            onSaved()
            dismiss()
        } catch {
            self.error = error.localizedDescription
            lists = []
        }
    }
}

/// The user's lists; opened from Settings.
struct ListsView: View {
    @Environment(AppState.self) private var appState
    @State private var lists: [CustomList] = []
    @State private var loading = false
    @State private var error: String?
    @State private var creating = false
    @State private var newName = ""
    @State private var newDescription = ""
    @State private var deleting: CustomList?

    var body: some View {
        LoadState(loading: loading, error: error, isEmpty: lists.isEmpty,
                  emptyText: "No lists yet", retry: load) {
            List(lists) { list in
                NavigationLink(value: list) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(list.name)
                        if let count = list.itemsCount {
                            Text("\(count) items").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                .swipeActions {
                    Button(role: .destructive) { deleting = list } label: { Label("Delete", systemImage: "trash") }
                }
            }
        }
        .navigationTitle("Lists")
        .navigationDestination(for: CustomList.self) { ListItemsView(list: $0) }
        .toolbar {
            Button { creating = true } label: { Label("New list", systemImage: "plus") }
        }
        .refreshable { await load() }
        .task { await load() }
        .alert("New list", isPresented: $creating) {
            TextField("Name", text: $newName)
            TextField("Description (optional)", text: $newDescription)
            Button("Create") { Task { await create() } }
            Button("Cancel", role: .cancel) {}
        }
        .confirmationDialog("Delete \(deleting?.name ?? "list")?",
                            isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
                            titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                if let list = deleting { Task { await delete(list) } }
            }
        }
    }

    private func load() async {
        guard let client = appState.client else { return }
        loading = true
        defer { loading = false }
        do {
            lists = try await client.lists()
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func create() async {
        let name = newName.trimmingCharacters(in: .whitespaces)
        guard let client = appState.client, !name.isEmpty else { return }
        let description = newDescription.trimmingCharacters(in: .whitespaces)
        do {
            try await client.createList(name: name, description: description.isEmpty ? nil : description)
        } catch {
            self.error = error.localizedDescription
        }
        newName = ""
        newDescription = ""
        await load()
    }

    private func delete(_ list: CustomList) async {
        do {
            try await appState.client?.deleteList(id: list.id)
        } catch {
            self.error = error.localizedDescription
        }
        await load()
    }
}

/// The items in one list. Long-press to change artwork or remove an item.
struct ListItemsView: View {
    let list: CustomList

    @Environment(AppState.self) private var appState
    @State private var items: [MediaItem] = []
    @State private var loading = false
    @State private var error: String?
    @State private var artworkTarget: ArtworkTarget?
    @State private var removing: MediaItem?

    var body: some View {
        LoadState(loading: loading, error: error, isEmpty: items.isEmpty,
                  emptyText: "This list is empty", retry: load) {
            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 105), spacing: 12)], spacing: 16) {
                    ForEach(items) { entry in
                        MediaCard(entry: entry, width: nil, actions: CardActions(
                            changeArtwork: { artworkTarget = $0 },
                            removeFromList: { removing = $0 }))
                    }
                }
                .padding()
            }
        }
        .navigationTitle(list.name)
        .refreshable { await load() }
        .task {
            _ = await appState.checkArtworkSupport()
            await load()
        }
        .sheet(item: $artworkTarget) { ArtworkPickerSheet(target: $0) }
        .confirmationDialog("Remove \(removing?.title ?? "item") from \(list.name)?",
                            isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } }),
                            titleVisibility: .visible) {
            Button("Remove", role: .destructive) {
                if let entry = removing { Task { await remove(entry) } }
            }
        }
    }

    private func load() async {
        guard let client = appState.client else { return }
        loading = true
        defer { loading = false }
        do {
            items = try await client.listItems(id: list.id)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func remove(_ entry: MediaItem) async {
        guard let client = appState.client, let ref = MediaRef(item: entry.item) else { return }
        do {
            try await client.setInList(false, listId: list.id, type: ref.type, source: ref.source, mediaId: ref.mediaId)
        } catch {
            self.error = error.localizedDescription
        }
        await load()
    }
}
