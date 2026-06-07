# AnMediaPlayer — Project Guide

Material 3 / Material You Android app. Browse remote media listings (HTTP
index · h5ai · Apache/nginx autoindex · FTP/WebDAV), play video in-app. Premium
media-app feel (Plex / Jellyfin), dark-first + full light theme.

Package: `xyz.devnerd.anmediaplayer` · minSdk 24 · targetSdk 36 · Jetpack Compose
(BOM 2026.02.01) · Kotlin 2.2.10 · AGP 9.2.1.

---

## Design source (authoritative)

From Claude Design handoff bundle. Spec = **`UI Design & Flow.md`**
(extracted to `/tmp/design_extract/ftp-player/`). Recreate visual output
pixel-perfect; do **not** copy prototype JSX structure. Key prototype
files: `src/theme.jsx` (tokens), `src/screens-*.jsx`, `player.jsx`,
`ui.jsx`, `data.jsx`.

Read `UI Design & Flow.md` before any screen work.

---

## Locked decisions

| Topic | Decision |
|---|---|
| Data backend | **Fake in-memory data first** (mirrors prototype). Real HTTP-index/h5ai/Apache/nginx/FTP/WebDAV parsers + streaming = Phase 7. |
| Player engine | **Media3 (ExoPlayer)**. libVLC fallback deferred. |
| Theme mode | **Light / Dark / Follow-system**, persisted. Dark = default look. |
| Color source | **Fixed M3 purple seed (`#6750A4`) + 5 swappable accents** (purple, green, amber, azure, crimson). No wallpaper dynamic color. |
| Type family | **Manrope** (bundled variable font `res/font/manrope.ttf`, weights 400–800). |

---

## Theme plan (planned up front)

- Two full M3 tonal schemes — **dark** + **light** — from design token
  tables (`UI Design & Flow.md` §1.1, mirrors `theme.jsx` `M3_DARK` / `M3_LIGHT`).
- **Accent** re-maps only `primary` family (primary/onPrimary/primaryContainer/
  onPrimaryContainer) per scheme; rest stays coherent.
- `ThemeMode` ∈ {SYSTEM, LIGHT, DARK}; `Accent` ∈ {PURPLE, GREEN, AMBER, AZURE, CRIMSON}.
- Both persisted via DataStore (`SettingsRepository`), read at app root, two-way
  bound to Settings ▸ Appearance.
- Edge-to-edge; status/nav bar icon contrast follows resolved dark/light.

---

## Architecture / package layout

```
xyz.devnerd.anmediaplayer
├─ MainActivity.kt            // edge-to-edge host; collects settings; AnMediaPlayerTheme { App() }
├─ AppRoot.kt / ui/App.kt     // Scaffold + NavigationBar + NavHost
├─ ui/theme/                  // Color.kt, Accent.kt, Type.kt, Shape.kt, Theme.kt
├─ ui/components/             // shared M3 kit (gradient cover, state-layer surfaces, etc.)
├─ ui/nav/                    // Destinations, top-level routes
├─ ui/screens/{home,servers,browser,downloads,settings,player}/
├─ data/                      // models (Entry, Server, Download, …), repository, fakedata
└─ settings/                  // SettingsRepository (DataStore), ThemeMode, Accent
```

Rule: **split code when files grow** — one screen per file/package, shared widgets
in `ui/components`, no god files. Mirror prototype `screens-*` split.

### Subtitles
Two paths: **external** sibling `.srt`/`.vtt` (matched by stem, sideloaded as Media3 `SubtitleConfiguration`) and **embedded** tracks inside container (e.g. scene-release "ESub" mkv/mp4). Enabling subtitles sets `preferredTextLanguage("en")` + `setSelectUndeterminedTextLanguage(true)` + un-disables `TRACK_TYPE_TEXT` — required for ExoPlayer to select embedded track (un-disabling alone not enough). Verified live: Shawshank Redemption embedded ESub renders.

### h5ai parsing (important)
**Raw HTTP response** from h5ai = fallback `<table>` (`td.fb-n` name anchor, `td.fb-d` date `yyyy-MM-dd HH:mm`, `td.fb-s` size like `1550541 KB`). Pretty `<li class="item">` list built client-side by JS, **not** in response. `HttpMediaSource` parses, in order: `li.item` (if present) → **fallback table** (primary path for h5ai — gives real date+size) → generic autoindex anchors. Folder/file dates + media sizes come from fallback table.

### Player playlist
`PlayerHost` builds playlist (`EpisodeRef`, cross-season for series via `buildSeriesPlaylist`, else folder video siblings), passes labels + currentIndex to `PlayerScreen`; top-chrome **Playlist** button opens `PlaylistSheet` listing all items (current highlighted, tap to switch). Shown only when >1 item.

### Cover art
No real posters. Each cover = deterministic two-stop gradient seeded from
file/folder name (`posterFor()` → `hsl(h 58% 44%) → hsl(h2 54% 26%)`). In Compose
render as `Brush.linearGradient`. Real `poster.jpg`/`folder.jpg` slot into
same frames later.

---

## Phase plan

Implement one phase, **test on wireless device, then stop** — user will
ask to proceed to next phase. Update Status column as phases land.

| #  | Phase | Scope                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Status |
|----|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---|
| 1  | Foundation | Theme system (dark+light tokens, 5 accents, Manrope type scale, shapes), `SettingsRepository` (DataStore) for theme mode + accent, app scaffold + bottom nav (Home/Servers/Downloads/Settings) + stub screens, Settings ▸ Appearance (theme + accent) working, edge-to-edge.                                                                                                                                                                                       | **done** (verified on device) |
| 2  | Data + Home | Fake data layer (Entry/Server/Download/bookmarks/continue-watching), gradient cover art, Home shelves (Continue watching, Bookmarks, Recently added, Your servers).                                                                                                                                                                                                                                                                                                | **done** (verified on device) |
| 3  | Servers + Connect | Servers list + context sheet, Connect full-screen modal (address field, live protocol/parser detection, auth toggle), saved-server reuse.                                                                                                                                                                                                                                                                                                                          | **done** (verified on device) |
| 4  | Browser | List/grid toggle, breadcrumb + back-stack nav, sort sheet, search, skeleton/empty/no-results states, per-item overflow, bookmark toggle, single-image-folder Collections.                                                                                                                                                                                                                                                                                          | **done** (verified on device) |
| 5  | Player | Landscape player (standard/minimal/cinema), transport + scrubber + double-tap seek + brightness/volume drags + subtitle/audio/speed/resize sheets, lock, captions, resume dialog, end-of-item panel + autoplay countdown, prev/next, progress save. **Simulated playback** (gradient surface); real Media3/ExoPlayer swaps in at Phase 7 with real URLs.                                                                                                           | **done** (verified on device) |
| 6  | Downloads + Settings | Downloads screen (Wi-Fi banner, Active, On-this-device), full Settings groups two-way bound.                                                                                                                                                                                                                                                                                                                                                                       | **done** (verified on device) |
| 7  | Real backend (HTTP + streaming) | HTTP MediaSource (jsoup: h5ai `li.item` rows + generic autoindex via `absUrl` + descendant check; rawurlencode segment encoding for `( ) ! ' *`), `AppRepo` persistence (servers/bookmarks/progress via DataStore JSON), fake data removed, async Browser load, Media3/ExoPlayer streaming + external + embedded subtitle. **Fully verified live on 172.16.50.14/DHAKA-FLIX-14: connect → persist → list → navigate → stream 1080p mp4 + embedded ESub subtitle.** | **done** (verified on device) |
| 8  | Visual polish | Real cover thumbnails (Coil + OkHttp; folder → inner image via `ThumbCache`/`firstImageUrl`, file → sibling poster, gradient fallback), pull-to-refresh (`refreshKey`), sort on real metadata. **Verified live: real posters in list + grid on DHAKA-FLIX.** Server-wide recursive search deferred (needs bounded depth/result-cap crawl to avoid hammering large trees).                                                                                          | **done** (verified on device) |
| 9  | Browser UX | 2-line names; persisted grid/list view; image-file preview thumbnails + fullscreen pinch-zoom viewer; poster **hero header** in media folders (`MediaHero`); **TV-series view** (`detectSeasons`/`SeriesView`) with **cross-season playlist** in `PlayerHost` (variable path + `EpisodeRef`). **Verified live on DHAKA-FLIX.** | **done** (verified on device) |
| 10 | Player depth | Real brightness/volume drag gestures, audio-track picker (real tracks), Picture-in-picture, tap-anywhere to show controls, double-tap edges to seek (YouTube-style), long-press to fast-forward, show current-folder playlist in player, back press hides player + resumes from history. | todo |
| 11 | Offline downloads | WorkManager background download, Wi-Fi-only constraint, live progress, resumable, persisted offline library, play offline. | todo |
| 12 | Auth + protocols | Basic-auth header on ExoPlayer datasource (login-protected streaming), FTP/SFTP/WebDAV MediaSources. | todo |


---

## Testing — wireless ADB

Device: **Samsung SM-S911B (Galaxy S23)**, wireless. Verify each phase
before stopping.

```bash
adb devices -l                                   # confirm device online
./gradlew :app:installDebug                      # build + install
adb shell am start -n xyz.devnerd.anmediaplayer/.MainActivity
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png /tmp/s.png   # screenshot to verify
adb logcat -s AnMediaPlayer:* AndroidRuntime:E   # crashes / logs
```

Device drops off Wi-Fi: `adb connect <ip>:<port>` (user pairs it).

**Device quirks (Galaxy S23):**
- Wireless adb sometimes lists device twice → "more than one device" errors. Pin serial: `adb -s adb-RFCW90B94XP-iwvOnw._adb-tls-connect._tcp …`.
- Samsung dialer / People-edge steals foreground + eats automated taps. Before tap sequence: `adb shell am force-stop com.samsung.android.dialer`, start app with `am start -W`, confirm `dumpsys window | grep mCurrentFocus` shows our activity.
- Lock portrait for stable tap coords: `adb shell settings put system accelerometer_rotation 0; … user_rotation 0`.

---

## Real-backend test target (Phase 7)

`http://data.speed4you.net/` — local **h5ai** server on local
network. Use to validate h5ai JSON parser, listing, streaming. Cleartext
HTTP — needs `networkSecurityConfig` / `usesCleartextTraffic` allowance for that host.

---

## Conventions

- Material 3 only — drive everything from `MaterialTheme.colorScheme`/`typography`/
  `shapes`. No ad-hoc colors; surfaces step through `surfaceContainer*` tones, not borders.
- State layers on every tappable element (M3 ripple / 8% hover · 12% press).
- Icons: UI icons outlined, media transport (play/pause/skip/replay-10) filled.
- Ask user (with options + recommendation) before guessing on scope.
- Confirm before destructive actions (`rm`, resets, overwrites).