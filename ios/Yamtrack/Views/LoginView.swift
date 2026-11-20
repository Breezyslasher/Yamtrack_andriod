import SwiftUI

struct LoginView: View {
    @Environment(AppState.self) private var appState
    @State private var server = ""
    @State private var token = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("https://yamtrack.example.com", text: $server)
                        .keyboardType(.URL)
                        .textContentType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } header: {
                    Text("Server")
                }
                Section {
                    SecureField("API token", text: $token)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } header: {
                    Text("API token")
                } footer: {
                    Text("Open your Yamtrack server in a browser, log in, go to your profile page and copy your API token.")
                }
                if let error {
                    Section { Text(error).foregroundStyle(.red) }
                }
                Section {
                    Button {
                        Task { await login() }
                    } label: {
                        HStack {
                            Text("Log in")
                            Spacer()
                            if busy { ProgressView() }
                        }
                    }
                    .disabled(busy || server.isEmpty || token.isEmpty)
                }
            }
            .navigationTitle("Yamtrack Unofficial")
            .onAppear { if server.isEmpty { server = appState.serverURL } }
        }
    }

    private func login() async {
        busy = true
        error = nil
        do {
            try await appState.login(server: server, token: token)
        } catch {
            self.error = error.localizedDescription
        }
        busy = false
    }
}
