import Foundation
import Observation

/// Who the app is signed in as. The server address is kept after logging
/// out so the login form is pre-filled; the token is deleted.
@Observable
final class AppState {
    private static let serverKey = "serverURL"

    private(set) var client: APIClient?
    var serverURL: String

    init() {
        serverURL = UserDefaults.standard.string(forKey: Self.serverKey) ?? ""
        if !serverURL.isEmpty, let token = Keychain.load() {
            client = APIClient(baseURL: serverURL, token: token)
        }
    }

    /// Checks the server and token, and keeps them only if they work.
    @MainActor
    func login(server: String, token: String) async throws {
        var address = server.trimmingCharacters(in: .whitespacesAndNewlines)
        if !address.lowercased().hasPrefix("http://") && !address.lowercased().hasPrefix("https://") {
            address = "https://" + address
        }
        let candidate = APIClient(baseURL: address, token: token.trimmingCharacters(in: .whitespacesAndNewlines))
        do {
            try await candidate.checkConnection()
        } catch let error as APIError where error.status == 401 || error.status == 403 {
            throw APIError(status: error.status, message: "Invalid API token. Check the value and try again.")
        } catch let error as APIError where error.status == 404 {
            throw APIError(status: 404, message: "API not found. Make sure the server runs a Yamtrack version with the REST API.")
        }
        serverURL = candidate.baseURL
        UserDefaults.standard.set(serverURL, forKey: Self.serverKey)
        Keychain.save(token.trimmingCharacters(in: .whitespacesAndNewlines))
        client = candidate
    }

    @MainActor
    func logout() {
        Keychain.delete()
        client = nil
    }
}
