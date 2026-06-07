# AnMediaPlayer — Project Guide

Material 3 / Material You Android app for browsing remote media listings (HTTP
index · h5ai · Apache/nginx autoindex · FTP/WebDAV) and playing video in an
in-app player. Premium media-app feel (Plex / Jellyfin), dark-first with full
light theme.

Package: `xyz.devnerd.anmediaplayer` · minSdk 24 · targetSdk 36 · Jetpack Compose
(BOM 2026.02.01) · Kotlin 2.2.10 · AGP 9.2.1.

---

## Design source (authoritative)

Built from a Claude Design handoff bundle. The spec is **`UI Design & Flow.md`**
(extracted to `/tmp/design_extract/ftp-player/`). Recreate the visual output
pixel-perfectly; do **not** copy the prototype's JSX structure. Key prototype
files for reference: `src/theme.jsx` (tokens), `src/screens-*.jsx`, `player.jsx`,
`ui.jsx`, `data.jsx`.

Read `UI Design & Flow.md` before any screen work.

---

## Locked decisions

| Topic | Decision |
|---|---|
| Data backend | **Fake in-memory data first** (mirrors prototype). Real HTTP-index/h5ai/Apache/nginx/FTP/WebDAV parsers + streaming come in Phase 7. |
| Player engine | **Media3 (ExoPlayer)**. libVLC fallback deferred. |
| Theme mode | **Light / Dark / Follow-system**, persisted. Dark is the default look. |
| Color source | **Fixed M3 purple seed (`#6750A4`) + 5 swappable accents** (purple, green, amber, azure, crimson). No wallpaper dynamic color. |
| Type family | **Manrope** (bundled variable font `res/font/manrope.ttf`, weights 400–800). |

---

## Theme plan (planned up front)

- Two full M3 tonal schemes — **dark** and **light** — from the design's token
  tables (`UI Design & Flow.md` §1.1, mirrors `theme.jsx` `M3_DARK` / `M3_LIGHT`).
- **Accent** only re-maps the `primary` family (primary/onPrimary/primaryContainer/
  onPrimaryContainer) per scheme; the rest of the scheme stays coherent.
- `ThemeMode` ∈ {SYSTEM, LIGHT, DARK}; `Accent` ∈ {PURPLE, GREEN, AMBER, AZURE, CRIMSON}.
- Both persisted via DataStore (`SettingsRepository`), read at app root, two-way
  bound to Settings ▸ Appearance.
- Edge-to-edge; status/nav bar icon contrast follows the resolved dark/light.

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
in `ui/components`, no god files. Mirror the prototype's `screens-*` split.

### Cover art
No real posters. Each cover is a deterministic two-stop gradient seeded from the
file/folder name (`posterFor()` → `hsl(h 58% 44%) → hsl(h2 54% 26%)`). In Compose
render as a `Brush.linearGradient`. Real `poster.jpg`/`folder.jpg` slot into the
same frames later.

---

## Phase plan

Implement one phase, **test on the wireless device, then stop** — the user will
ask to proceed to the next phase. Update the Status column as phases land.

| # | Phase | Scope | Status |
|---|---|---|---|
| 1 | Foundation | Theme system (dark+light tokens, 5 accents, Manrope type scale, shapes), `SettingsRepository` (DataStore) for theme mode + accent, app scaffold + bottom nav (Home/Servers/Downloads/Settings) + stub screens, Settings ▸ Appearance (theme + accent) working, edge-to-edge. | **done** (verified on device) |
| 2 | Data + Home | Fake data layer (Entry/Server/Download/bookmarks/continue-watching), gradient cover art, Home shelves (Continue watching, Bookmarks, Recently added, Your servers). | **done** (verified on device) |
| 3 | Servers + Connect | Servers list + context sheet, Connect full-screen modal (address field, live protocol/parser detection, auth toggle), saved-server reuse. | **done** (verified on device) |
| 4 | Browser | List/grid toggle, breadcrumb + back-stack nav, sort sheet, search, skeleton/empty/no-results states, per-item overflow, bookmark toggle, single-image-folder Collections. | **done** (verified on device) |
| 5 | Player | Landscape player (standard/minimal/cinema), transport + scrubber + double-tap seek + brightness/volume drags + subtitle/audio/speed/resize sheets, lock, captions, resume dialog, end-of-item panel + autoplay countdown, prev/next, progress save. **Simulated playback** (gradient surface); real Media3/ExoPlayer swaps in at Phase 7 with real URLs. | **done** (verified on device) |
| 6 | Downloads + Settings | Downloads screen (Wi-Fi banner, Active, On-this-device), full Settings groups two-way bound. | **done** (verified on device) |
| 7 | Real backend (HTTP + streaming) | HTTP MediaSource (jsoup: h5ai `li.item` rows + generic autoindex via `absUrl` + descendant check; rawurlencode segment encoding for `( ) ! ' *`), `AppRepo` persistence (servers/bookmarks/progress via DataStore JSON), fake data removed, async Browser load, Media3/ExoPlayer streaming + sideloaded subtitle. **Fully verified live on a real h5ai server (172.16.50.14/DHAKA-FLIX-14): connect → persist → list → navigate (parens/`&`/apostrophe) → stream real 1080p mp4 (ExoPlayer, real duration).** FTP/SFTP/WebDAV + WorkManager offline downloads deferred. | **done** (verified on device) |

---

## Testing — wireless ADB

Device: **Samsung SM-S911B (Galaxy S23)**, connected wirelessly. Verify each phase
on it before stopping.

```bash
adb devices -l                                   # confirm device online
./gradlew :app:installDebug                      # build + install
adb shell am start -n xyz.devnerd.anmediaplayer/.MainActivity
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png /tmp/s.png   # screenshot to verify
adb logcat -s AnMediaPlayer:* AndroidRuntime:E   # crashes / logs
```

If the device drops off Wi-Fi: `adb connect <ip>:<port>` (user pairs it).

**Device quirks (Galaxy S23):**
- Wireless adb sometimes lists the device twice → "more than one device" errors. Pin the serial: `adb -s adb-RFCW90B94XP-iwvOnw._adb-tls-connect._tcp …`.
- The Samsung dialer / People-edge steals foreground and eats automated taps. Before a tap sequence: `adb shell am force-stop com.samsung.android.dialer`, start the app with `am start -W`, and confirm `dumpsys window | grep mCurrentFocus` shows our activity.
- Lock portrait for stable tap coords: `adb shell settings put system accelerometer_rotation 0; … user_rotation 0`.

---

## Real-backend test target (Phase 7)

`http://data.speed4you.net/` — a local **h5ai** server reachable on the local
network. Use it to validate the h5ai JSON parser, listing, and streaming. Cleartext
HTTP — needs a `networkSecurityConfig` / `usesCleartextTraffic` allowance for that host.

---

## Conventions

- Material 3 only — drive everything from `MaterialTheme.colorScheme`/`typography`/
  `shapes`. No ad-hoc colors; surfaces step through `surfaceContainer*` tones, not borders.
- State layers on every tappable element (M3 ripple / 8% hover · 12% press).
- Icons: UI icons outlined, media transport (play/pause/skip/replay-10) filled.
- Ask the user (with options + a recommendation) before guessing on scope.
- Confirm before destructive actions (`rm`, resets, overwrites).
