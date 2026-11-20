import Foundation
import Observation

/// Who the app is signed in as. The server address is kept after logging
/// out so the login form is pre-filled; the token is deleted.
@Observable
final class AppState {
    private static let serverKey = "serverURL"

    private(set) var client: APIClient?
    var serverURL: String

    /// Covers changed this session, by media key. The server keeps the
    /// cover; this only updates lists that were loaded before the change.
    private(set) var coverOverrides: [String: String] = [:]
    /// Whether the server has the artwork endpoints; nil until asked.
    private(set) var artworkSupported: Bool?

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
        coverOverrides = [:]
        artworkSupported = nil
        client = candidate
    }

    @MainActor
    func logout() {
        Keychain.delete()
        client = nil
        coverOverrides = [:]
        artworkSupported = nil
    }

    // MARK: Artwork

    static func coverKey(type: String, source: String, mediaId: String, season: Int? = nil) -> String {
        "\(type)|\(source)|\(mediaId)|\(season.map(String.init) ?? "")"
    }

    /// The image to show for an item: a cover changed this session, if any.
    func image(for item: Item?) -> String? {
        guard let item else { return nil }
        let season = item.type == .season ? item.seasonNumber : nil
        let key = Self.coverKey(type: item.mediaType, source: item.source, mediaId: item.mediaId, season: season)
        return coverOverrides[key] ?? item.image
    }

    func image(forKey key: String, fallback: String?) -> String? {
        coverOverrides[key] ?? fallback
    }

    @MainActor
    func coverChanged(key: String, image: String?) {
        coverOverrides[key] = image
    }

    /// Asks the server once whether it can change artwork.
    @MainActor
    func checkArtworkSupport() async -> Bool {
        if let artworkSupported { return artworkSupported }
        guard let client else { return false }
        let supported = await client.supportsArtwork()
        artworkSupported = supported
        return supported
    }
}
