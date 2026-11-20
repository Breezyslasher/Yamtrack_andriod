import SwiftUI

/// Key for the "hide unwatched episode details" setting (spoiler shield).
let hideUnwatchedEpisodeInfoKey = "hideUnwatchedEpisodeInfo"

struct SettingsView: View {
    @Environment(AppState.self) private var appState
    @AppStorage(hideUnwatchedEpisodeInfoKey) private var hideUnwatchedEpisodeInfo = false
    @State private var confirmLogout = false

    private var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
    }

    var body: some View {
        Form {
            Section {
                NavigationLink { ListsView() } label: { Label("Lists", systemImage: "list.bullet") }
                NavigationLink { StatisticsView() } label: { Label("Statistics", systemImage: "chart.bar") }
            }
            Section {
                Toggle("Hide unwatched episode details", isOn: $hideUnwatchedEpisodeInfo)
            } footer: {
                Text("Hides the name, image and description of episodes you haven't watched yet.")
            }
            Section("Server") {
                Text(appState.serverURL).textSelection(.enabled)
                Button("Log out", role: .destructive) { confirmLogout = true }
            }
            Section {
                LabeledContent("Version", value: version)
                Link("Privacy policy",
                     destination: URL(string: "https://github.com/Breezyslasher/Yamtrack_andriod/blob/main/PRIVACY.md")!)
                Link("Source and issues",
                     destination: URL(string: "https://github.com/Breezyslasher/Yamtrack_andriod")!)
            } footer: {
                Text("Yamtrack Unofficial is not affiliated with the Yamtrack project.")
            }
        }
        .navigationTitle("Settings")
        .confirmationDialog("Log out of \(appState.serverURL)?", isPresented: $confirmLogout,
                            titleVisibility: .visible) {
            Button("Log out", role: .destructive) { appState.logout() }
        }
    }
}

/// Tracked items by status, across all media types and all time.
struct StatisticsView: View {
    @Environment(AppState.self) private var appState
    @State private var stats: UserStats?
    @State private var error: String?

    var body: some View {
        Group {
            if let stats {
                Form {
                    LabeledContent("Total", value: "\(stats.total)")
                    ForEach(MediaStatus.allCases) { status in
                        LabeledContent(status.name, value: "\(stats.count(status))")
                    }
                }
            } else if let error {
                ContentUnavailableView("Couldn't load statistics", systemImage: "chart.bar",
                                       description: Text(error))
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Statistics")
        .task {
            do {
                stats = try await appState.client?.statistics()
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
}
