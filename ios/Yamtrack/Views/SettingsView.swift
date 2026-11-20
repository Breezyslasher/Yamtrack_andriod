import SwiftUI

struct SettingsView: View {
    @Environment(AppState.self) private var appState
    @State private var confirmLogout = false

    private var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
    }

    var body: some View {
        Form {
            Section("Server") {
                Text(appState.serverURL).textSelection(.enabled)
            }
            Section {
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
