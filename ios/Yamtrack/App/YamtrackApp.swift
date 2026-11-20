import SwiftUI

@main
struct YamtrackApp: App {
    @State private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            Group {
                if appState.client != nil {
                    MainTabView()
                } else {
                    LoginView()
                }
            }
            .environment(appState)
            .tint(Theme.accent)
            .preferredColorScheme(.dark)
        }
    }
}

struct MainTabView: View {
    var body: some View {
        TabView {
            NavigationStack { HomeView().mediaDestinations() }
                .tabItem { Label("Home", systemImage: "house") }
            NavigationStack { LibraryView().mediaDestinations() }
                .tabItem { Label("Library", systemImage: "books.vertical") }
            NavigationStack { CalendarView().mediaDestinations() }
                .tabItem { Label("Calendar", systemImage: "calendar") }
            NavigationStack { SearchView().mediaDestinations() }
                .tabItem { Label("Search", systemImage: "magnifyingglass") }
            NavigationStack { SettingsView() }
                .tabItem { Label("Settings", systemImage: "gearshape") }
        }
    }
}
