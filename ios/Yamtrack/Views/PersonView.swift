import SwiftUI

/// A person page to open (cast members, TMDB only).
struct PersonRef: Hashable {
    let source: String
    let personId: Int
    let name: String
}

/// Streaming and rent/buy services. A service opens its own link from
/// JustWatch when the server provides one, else TMDB's region page.
struct ProvidersSection: View {
    let providers: WatchProviders
    let openURL: (URL) -> Void

    var body: some View {
        let free = providers.noCharge ?? []
        let paid = providers.paid ?? []
        if !free.isEmpty || !paid.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Button { open(nil) } label: {
                    HStack {
                        Text("Where to watch").font(.headline)
                        if providers.link != nil { Image(systemName: "arrow.up.right.square").font(.caption) }
                    }
                }
                .foregroundStyle(.primary)
                if !free.isEmpty { row("Stream", free) }
                if !paid.isEmpty { row("Rent or buy", paid) }
            }
        }
    }

    private func row(_ title: String, _ services: [WatchProvider]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.subheadline).foregroundStyle(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 12) {
                    ForEach(services) { provider in
                        Button { open(provider) } label: { tile(provider) }
                            .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func tile(_ provider: WatchProvider) -> some View {
        VStack(spacing: 4) {
            PosterImage(url: provider.image, cornerRadius: 12)
                .frame(width: 56, height: 56)
            Text(provider.providerName ?? "").font(.caption2).multilineTextAlignment(.center).lineLimit(2)
            ForEach(prices(provider), id: \.self) {
                Text($0).font(.caption2).foregroundStyle(.secondary)
            }
        }
        .frame(width: 76)
    }

    /// "Rent $3.99 (HD)", "Buy $14.99 (HD)".
    private func prices(_ p: WatchProvider) -> [String] {
        func label(_ kind: String, _ price: String?, _ quality: String?) -> String? {
            guard let price, !price.isEmpty else { return nil }
            if let quality, !quality.isEmpty { return "\(kind) \(price) (\(quality))" }
            return "\(kind) \(price)"
        }
        return [label("Rent", p.rentPrice, p.rentQuality), label("Buy", p.buyPrice, p.buyQuality)].compactMap { $0 }
    }

    private func open(_ provider: WatchProvider?) {
        let link = provider?.link.flatMap { $0.isEmpty ? nil : $0 } ?? providers.link
        if let link, let url = URL(string: link) { openURL(url) }
    }
}

/// Photo, bio and what a person is known for.
struct PersonView: View {
    let ref: PersonRef

    @Environment(AppState.self) private var appState
    @State private var person: Person?
    @State private var knownFor: [KnownFor] = []
    @State private var filter: MediaType? = nil
    @State private var loadingKnownFor = false
    @State private var bioExpanded = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .top, spacing: 16) {
                    PosterImage(url: person?.image, cornerRadius: 60)
                        .frame(width: 120, height: 120)
                    VStack(alignment: .leading, spacing: 6) {
                        Text(person?.name ?? ref.name).font(.title2.bold())
                        if let department = person?.knownForDepartment, !department.isEmpty {
                            Text(department).foregroundStyle(.secondary)
                        }
                        ForEach(facts, id: \.self) { Text($0).font(.caption).foregroundStyle(.secondary) }
                    }
                }
                if let bio = person?.biography, !bio.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Biography").font(.headline)
                        Text(bio).foregroundStyle(.secondary)
                            .lineLimit(bioExpanded ? nil : 6)
                            .onTapGesture { bioExpanded.toggle() }
                    }
                }
                Text("Known for").font(.headline)
                Picker("Type", selection: $filter) {
                    Text("All").tag(MediaType?.none)
                    Text("Movies").tag(MediaType?.some(.movie))
                    Text("TV Shows").tag(MediaType?.some(.tv))
                }
                .pickerStyle(.segmented)
                if loadingKnownFor && knownFor.isEmpty {
                    ProgressView().frame(maxWidth: .infinity)
                } else {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 100), spacing: 12)], spacing: 16) {
                        ForEach(knownFor) { credit in knownForCard(credit) }
                    }
                }
            }
            .padding()
        }
        .navigationTitle(person?.name ?? ref.name)
        .navigationBarTitleDisplayMode(.inline)
        .task { person = try? await appState.client?.person(source: ref.source, personId: ref.personId) }
        .task(id: filter) { await loadKnownFor() }
    }

    private var facts: [String] {
        guard let person else { return [] }
        return [
            MediaMeta.fullDate(person.birthday).map { "Born \($0)" },
            person.placeOfBirth.flatMap { $0.isEmpty ? nil : $0 },
            MediaMeta.fullDate(person.deathday).map { "Died \($0)" },
        ].compactMap { $0 }
    }

    @ViewBuilder
    private func knownForCard(_ credit: KnownFor) -> some View {
        let year = credit.releaseDate.map { String($0.prefix(4)) }
        let role = credit.character.flatMap { $0.isEmpty ? nil : $0 } ?? credit.jobs.joined(separator: ", ")
        let card = VStack(alignment: .leading, spacing: 4) {
            PosterImage(url: appState.image(
                forKey: AppState.coverKey(type: credit.mediaType, source: credit.source, mediaId: credit.mediaId),
                fallback: credit.image))
                .aspectRatio(2 / 3, contentMode: .fit)
            Text(year.map { "\(credit.title ?? "") (\($0))" } ?? (credit.title ?? ""))
                .font(.footnote).lineLimit(2)
            if !role.isEmpty {
                Text(role).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            }
        }
        if let type = credit.type, !credit.mediaId.isEmpty {
            NavigationLink(value: MediaRef(type: type, source: credit.source, mediaId: credit.mediaId,
                                           title: credit.title ?? "")) { card }
                .buttonStyle(.plain)
        } else {
            card
        }
    }

    private func loadKnownFor() async {
        guard let client = appState.client else { return }
        loadingKnownFor = true
        defer { loadingKnownFor = false }
        knownFor = (try? await client.knownFor(source: ref.source, personId: ref.personId, mediaType: filter)) ?? []
    }
}
