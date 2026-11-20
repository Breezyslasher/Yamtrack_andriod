import SwiftUI

/// Events falling on one day.
private struct DayGroup: Identifiable {
    let day: Date
    let events: [CalendarEvent]
    var id: Date { day }
}

/// Upcoming releases and episodes for the next three months.
struct CalendarView: View {
    @Environment(AppState.self) private var appState
    @State private var days: [DayGroup] = []
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        LoadState(loading: loading, error: error, isEmpty: days.isEmpty,
                  emptyText: "Nothing coming up", retry: load) {
            List {
                ForEach(days) { group in
                    Section(header: Text(dayTitle(group.day))) {
                        ForEach(group.events) { event in
                            row(event)
                        }
                    }
                }
            }
            .listStyle(.plain)
        }
        .navigationTitle("Calendar")
        .refreshable { await load() }
        .task { await load() }
    }

    @ViewBuilder
    private func row(_ event: CalendarEvent) -> some View {
        let content = HStack(spacing: 12) {
            PosterImage(url: event.item?.image, cornerRadius: 6)
                .frame(width: 48, height: 72)
            VStack(alignment: .leading, spacing: 4) {
                Text(event.item?.title ?? "").font(.headline).lineLimit(2)
                Text(detailLine(event)).font(.caption).foregroundStyle(.secondary)
            }
        }
        if let ref = MediaRef(item: event.item) {
            NavigationLink(value: ref) { content }
        } else {
            content
        }
    }

    /// "TV · Season 2 · Episode 4", "Anime · Episode 7", "Movie".
    private func detailLine(_ event: CalendarEvent) -> String {
        var parts: [String] = []
        if let type = event.item?.type { parts.append(type.itemLabel) }
        if let season = event.item?.seasonNumber { parts.append("Season \(season)") }
        if let episode = event.item?.episodeNumber ?? event.contentNumber { parts.append("Episode \(episode)") }
        return parts.joined(separator: " · ")
    }

    private func dayTitle(_ day: Date) -> String {
        let calendar = Calendar.current
        if calendar.isDateInToday(day) { return "Today" }
        if calendar.isDateInTomorrow(day) { return "Tomorrow" }
        return day.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day())
    }

    private func load() async {
        guard let client = appState.client else { return }
        loading = true
        defer { loading = false }
        let today = Calendar.current.startOfDay(for: Date())
        let end = Calendar.current.date(byAdding: .month, value: 3, to: today) ?? today
        do {
            let events = try await client.calendar(from: today, to: end)
            var grouped: [Date: [CalendarEvent]] = [:]
            for event in events {
                guard let day = CalendarDay.localDay(event.datetime), day >= today else { continue }
                grouped[day, default: []].append(event)
            }
            days = grouped.keys.sorted().map { day in
                DayGroup(day: day, events: (grouped[day] ?? []).sorted { ($0.datetime ?? "") < ($1.datetime ?? "") })
            }
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}
