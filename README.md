# Yamtrack Android App

A native Android client for [Yamtrack](https://github.com/FuzzyGrim/Yamtrack), built against the REST API on the `feat/add-api` / `dev` branches ([PR #924](https://github.com/FuzzyGrim/Yamtrack/pull/924)).

This client was written against the actual API source (`src/api/urls.py`, `src/api/views.py`, `src/api/serializers.py`, `src/api/authentication.py`) — not the PR description — so the data shapes and query params match exactly.

## Features

- 🎬 Track Movies, TV, Anime, Manga, Games, Books, Comics, and Board Games
- 🔑 Bearer token (or X-API-Key) authentication
- 📊 Dashboard with server-computed statistics
- 📚 Library browsing with type & status filters
- 🔍 Search across providers (TMDB, MAL, IGDB, OpenLibrary, ComicVine, BGG…)
- ✏️ Add, update, and remove tracked items (PATCH supported per modifiable field set)
- ⚙️ Settings with server URL & app preferences
- 🌙 Dark theme

## Server requirement

Your Yamtrack instance must include the API code from PR #924. Use:
- the `dev` branch, or
- the `ghcr.io/fuzzygrim/yamtrack:dev` Docker image

You can verify with `GET https://your-server/api/v1/health/` (public endpoint, no auth needed).

## Default Configuration

The app ships with the demo server pre-configured:
- **Server URL**: `https://yamtrack.fuzzygrim.com`
- **Demo credentials**: `demo` / `demo`

## Authentication

Per `src/api/authentication.py`, two header styles work:
- `Authorization: Bearer <token>` (used by this app)
- `X-API-Key: <token>` (also supported)

Tokens are stored on the User model server-side; there is no API endpoint that issues tokens. Users get their token from the web UI (profile page).

The login screen has two modes:

### 1. API Token (recommended)
- Log in to your Yamtrack instance in a browser
- Visit your profile page
- Copy your API token
- In the app: switch to **API Token**, paste it, login

### 2. Username / Password (fallback for the demo server)
- Switch to **Username**, enter creds
- The app does Django session login, then scrapes `/profile/` for the token
- If scraping fails, you'll be prompted to copy the token manually

## API Coverage

| Endpoint | Method | Used by |
|---|---|---|
| `/api/v1/health/` | GET | Settings (server health) — public |
| `/api/v1/info/` | GET | Settings (server info) — public |
| `/api/v1/statistics/` | GET | Home dashboard |
| `/api/v1/media/` | GET | Home (recent across types) |
| `/api/v1/media/{type}/` | GET | Library |
| `/api/v1/media/{type}/` | POST | Search → add to library |
| `/api/v1/media/{type}/{source}/{id}/` | GET | Media details |
| `/api/v1/media/{type}/{source}/{id}/` | PATCH | Update status/score/progress/notes/dates |
| `/api/v1/media/{type}/{source}/{id}/` | DELETE | Remove from library |
| `/api/v1/media/{type}/{source}/{id}/seasons/` | GET | TV seasons |
| `/api/v1/media/{type}/{source}/{id}/{season}/episodes/` | GET | Season episodes |
| `/api/v1/media/{type}/{source}/{id}/recommendations/` | GET | Related media |
| `/api/v1/media/{type}/{source}/{id}/history/` | GET | Consumption history |
| `/api/v1/search/{type}/?search=...` | GET | Search |
| `/api/v1/calendar/` | GET | Upcoming releases |
| `/api/v1/lists/` | GET | Custom lists |

Pagination on every list endpoint: `?limit=` (default 20, max 200) + `?offset=` (default 0). Response envelope is `{pagination: {total, limit, offset, next, previous}, results: [...]}`.

## Important schema notes

These details are easy to get wrong from the PR description and required reading the actual serializers:

- **Status is numeric** in JSON (both directions): `0=Planning, 1=In progress, 2=Paused, 3=Completed, 4=Dropped`. The server converts via `MEDIA_STATUS_MAP` in `api/helpers.py`.
- **Search query param is `search`**, not `q`.
- **Sources** are constrained per media type (`VALID_SOURCES`):
  - `tmdb` for movie/tv
  - `mal` for anime; `mal` or `mangaupdates` for manga
  - `igdb` for games
  - `openlibrary` or `hardcover` for books
  - `comicvine` for comics
  - `bgg` for board games
  - `manual` for everything (custom entries)
- **PATCH** silently drops fields that are not in `MEDIA_MODIFIABLE_FIELDS` for the given media type. For example, you cannot PATCH `progress` on a movie — only score/status/dates/notes.
- **Errors** use `{"detail": "..."}`, sometimes with an `"errors"` sub-field for validation details.
- **Search** rejects `season` and `episode` media types with HTTP 400.
- **MediaDetails** (the `CompleteMediaSerializer` response) embeds the user's tracking state in `consumptions[0]`, not at the top level — the app exposes it via `userStatus`/`userScore`/`userProgress` convenience accessors.

## Building

### Android Studio
1. Open the project, let Gradle sync
2. Run (`Shift + F10`)

### Command Line
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
app/src/main/java/com/yamtrack/app/
├── data/
│   ├── api/
│   │   ├── YamtrackApi.kt          # Retrofit interface (mirrors api/urls.py)
│   │   └── AuthInterceptor.kt      # Bearer token + dynamic base URL
│   ├── model/
│   │   └── Models.kt               # Data classes matching api/serializers.py
│   └── repository/
│       ├── PreferencesManager.kt   # DataStore: server URL, API token, prefs
│       └── YamtrackRepository.kt   # Wraps API with Result<T>
├── ui/
│   ├── login/       # Token + password login
│   ├── home/        # Dashboard
│   ├── library/     # Filterable media list
│   ├── search/      # Search & add
│   ├── details/     # View & edit tracked media
│   └── settings/    # Server + app settings
└── util/
    └── AppModule.kt # Hilt DI: Retrofit, OkHttp, Gson, interceptors
```

## Tech Stack

- **Kotlin** + **Coroutines**
- **Retrofit** + **OkHttp** + **Gson**
- **Hilt** for dependency injection
- **DataStore** for preferences
- **Navigation Component** with Bottom Nav
- **Coil** for image loading
- **Material Design 3**

## Troubleshooting

**Build error: `module jdk.compiler does not export com.sun.tools.javac.main`**  
kapt on JDK 16+ needs explicit access to internal `javac` APIs. The included `gradle.properties` adds the required `--add-opens` JVM args. If you still hit this:

- In Android Studio: **File → Settings → Build → Build Tools → Gradle** → set **Gradle JDK** to the embedded JDK 17, or
- Kill stale daemons: `./gradlew --stop && ./gradlew clean assembleDebug`

JDK 17 is recommended. JDK 21 works with the included `--add-opens`. JDK 22+ may require switching from kapt to KSP.

**Login says "API not found"**  
Your Yamtrack server is on `main` (no API). Switch to `dev` branch / `:dev` Docker tag. Verify with `GET /api/v1/health/`.

**Login says "Invalid API token"**  
Token regenerated or never existed. Copy a fresh one from the profile page.

**Search returns 400 for seasons/episodes**  
The server explicitly rejects these in `SearchProviderView.get()` because their metadata isn't stored. Search the parent `tv` show instead.

## License

Open source. See the [Yamtrack repository](https://github.com/FuzzyGrim/Yamtrack) for license details.

## Credits

- [Yamtrack](https://github.com/FuzzyGrim/Yamtrack) by FuzzyGrim
- REST API ([PR #924](https://github.com/FuzzyGrim/Yamtrack/pull/924)) by 66Bunz
