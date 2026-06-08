# AnMediaPlayer for macOS — Project Guide & Phase Plan

Native macOS port of the Android **AnMediaPlayer**. Browse remote media
listings (HTTP index · h5ai · Apache/nginx autoindex · FTP/WebDAV), play video
in-app. Premium media-app feel (Plex / Jellyfin desktop), dark-first + full
light theme. Built by **Claude Code (the AI agent)**; the user supplies a
per-phase specification, Claude implements, then stops for device verification.

Bundle id: `xyz.devnerd.anmediaplayer.mac` · macOS 14 (Sonoma) min · SwiftUI +
AppKit · Swift 6 · Xcode 16.

> This is a **sibling** app, not a 1:1 reskin. Same product, re-thought for a
> desktop: pointer + keyboard + multiple resizable windows + larger canvas.
> Where the platform demands it, the design changes (see **Platform deltas**).

---

## Locked decisions

| Topic | Decision |
|---|---|
| UI framework | **SwiftUI** for all screens; **AppKit** bridges where SwiftUI is thin (window chrome, custom video overlay, drag-drop, context menus via `NSMenu`). |
| Builder | **Claude Code** is the implementing agent. User writes spec per phase. One phase at a time, verify on Mac, then proceed. |
| Data backend | **Fake in-memory data first** (mirrors Android prototype). Real HTTP-index/h5ai/Apache/nginx/FTP/WebDAV parsers + streaming = Phase 7. |
| Player engine | **AVKit / AVFoundation** (`AVPlayer` + `AVPlayerView`). **VLCKit** fallback for codecs AVFoundation can't decode (MKV/HEVC variants) — deferred to Phase 12. |
| Navigation | **`NavigationSplitView`** — left sidebar (Home/Servers/Downloads/Settings) + detail. No bottom nav. |
| Theme mode | **Light / Dark / Follow-system**, persisted. Dark = default look. Respect macOS `NSApp.effectiveAppearance`. |
| Color source | **Fixed seed (`#6750A4`) + 5 swappable accents** (purple, green, amber, azure, crimson). Optional: honor macOS System accent color. No wallpaper dynamic color. |
| Type family | **Manrope** (bundled variable font, weights 400–800). Fall back to **SF Pro** if a native-mac variant is wanted. |
| Persistence | `UserDefaults` + JSON files in Application Support (`~/Library/Application Support/AnMediaPlayer/`). Mirrors Android DataStore role. |
| Window model | Single main window + separate **detached player window** (own `WindowGroup`); full-screen + native PiP supported. |

---

## Platform deltas (macOS vs Android) — read before any screen

| Concern | Android (source) | macOS (this app) |
|---|---|---|
| Top-level nav | Bottom `NavigationBar` | `NavigationSplitView` **sidebar** |
| Primary input | Touch, tap, swipe | **Pointer + keyboard + trackpad**; right-click context menus; hover states are first-class |
| Hover | none | Reveal overflow/actions on hover; cursor changes |
| Player gestures | Double-tap seek, brightness/volume drags | Keyboard (`Space`, `←/→`, `↑/↓` vol, `F` full, `M` mute), scrubber drag, **no brightness drag** (OS owns it); trackpad scrub optional |
| Window | Single activity, orientation-locked | Resizable, multi-window, full-screen, **detached PiP window** |
| Menu bar | n/a | **App menu commands** (Playback, View, Go) with shortcuts |
| Drag-drop | n/a | Drag a server URL / local file onto window to add/open |
| Density | Phone shelves | Wider canvas → denser multi-column grids, larger hero |
| PiP | `enterPip` Android | `AVPictureInPictureController` native |
| Cleartext HTTP | `networkSecurityConfig` | **App Transport Security** exception (`NSAllowsArbitraryLoads` / per-domain) for `http://` servers |
| Background audio | MediaSessionService (deferred) | `MPNowPlayingInfoCenter` + remote command center |
| Testing | Wireless ADB to S23 | **Run locally** on the Mac (`xcodebuild` + launch); screenshot via `screencapture` |

---

## Architecture / package layout

```
AnMediaPlayerMac/
├─ App/
│  ├─ AnMediaPlayerApp.swift      // @main; WindowGroup(s); injects AppEnvironment
│  ├─ AppEnvironment.swift        // root observable: settings, repo, theme
│  └─ Commands.swift              // menu-bar commands (Playback/View/Go)
├─ Theme/                         // Color.swift, Accent.swift, Typography.swift, Shapes.swift, Theme.swift
├─ Components/                    // shared SwiftUI kit (GradientCover, hover surfaces, StateLayer)
├─ Navigation/                    // Destination enum, SidebarView, RootSplitView
├─ Screens/
│  ├─ Home/                       // shelves: Continue, Bookmarks, Recently added, Your servers
│  ├─ Servers/                    // list + connect sheet/window, protocol detect
│  ├─ Browser/                    // list/grid toggle, breadcrumb, sort, search, states
│  ├─ Downloads/
│  ├─ Settings/                   // Appearance + groups
│  └─ Player/                     // AVPlayerView wrapper + custom overlay chrome
├─ Data/                          // Models (Entry, Server, Download…), AppRepo, FakeData
│  ├─ Sources/                    // HttpMediaSource, FtpMediaSource, WebDavMediaSource (Phase 7+)
│  └─ Thumbnails/                 // ThumbCache, firstImageUrl, poster resolution
└─ Settings/                      // SettingsStore (UserDefaults), ThemeMode, Accent
```

Rule: **split code as files grow** — one screen per folder/file, shared widgets
in `Components`, no god files. Mirror the Android screen split.

---

## Design source

Reuse the **same Claude Design handoff** (`UI Design & Flow.md`) as visual
authority for tokens, color, type, component intent. Recreate visual output;
**adapt layout** for desktop per the Platform deltas table (sidebar, hover,
denser grids). Do **not** copy prototype JSX structure. Read `UI Design &
Flow.md` before any screen work.

### Cover art
No real posters at first. Each cover = deterministic two-stop gradient seeded
from file/folder name (`posterFor()` → `hsl(h 58% 44%) → hsl(h2 54% 26%)`),
rendered as SwiftUI `LinearGradient`. Real `poster.jpg`/`folder.jpg` slot into
the same frames in Phase 8.

### h5ai parsing (carry over from Android)
Raw HTTP response from h5ai = fallback `<table>` (`td.fb-n` name anchor,
`td.fb-d` date `yyyy-MM-dd HH:mm`, `td.fb-s` size like `1550541 KB`). Pretty
`<li class="item">` list is built client-side by JS, **not** in the response.
`HttpMediaSource` parses, in order: `li.item` → **fallback table** (primary for
h5ai — real date+size) → generic autoindex anchors. Use **SwiftSoup** (Swift
jsoup port) for HTML parsing.

### Subtitles
Two paths: **external** sibling `.srt`/`.vtt` (matched by stem, loaded as a
secondary `AVMediaSelectionGroup` / sideloaded text track) and **embedded**
tracks inside container. Prefer English legible group via
`AVPlayerItem.select(_:in:)` on the legible characteristic.

---

## Phase plan

Implement one phase, **verify on the Mac, then stop** — user reviews and asks to
proceed. User provides the detailed spec for each phase before Claude builds it.
Update Status as phases land.

| #  | Phase | Scope | Status |
|----|---|---|---|
| 0  | Scaffold | Xcode project, bundle id, Swift 6, folder layout, Manrope font bundled, ATS exception placeholder, empty `WindowGroup` launches. | todo |
| 1  | Foundation | Theme system (dark+light tokens, 5 accents, Manrope type scale, shapes), `SettingsStore` (UserDefaults) for theme mode + accent, `NavigationSplitView` shell + sidebar (Home/Servers/Downloads/Settings) + stub detail screens, Settings ▸ Appearance (theme + accent) working, honors system appearance. | todo |
| 2  | Data + Home | Fake data layer (Entry/Server/Download/bookmarks/continue-watching), gradient cover art, Home shelves (Continue watching, Bookmarks, Recently added, Your servers), hover-reveal actions. | todo |
| 3  | Servers + Connect | Servers list + right-click context menu, Connect modal (sheet or detached window: address field, live protocol/parser detection, auth toggle), saved-server reuse, drag-drop URL to add. | todo |
| 4  | Browser | List/grid toggle, breadcrumb + back-stack nav, sort menu, search field, skeleton/empty/no-results states, per-item overflow on hover + right-click, bookmark toggle, single-image-folder Collections, keyboard arrow-key selection. | todo |
| 5  | Player (simulated) | Detached player window, custom overlay chrome (transport + scrubber + prev/next), keyboard shortcuts (`Space`/`←→`/`↑↓`/`F`/`M`), full-screen, subtitle/audio/speed/resize menus, resume dialog, end-of-item panel + autoplay countdown, progress save. **Simulated playback** (gradient surface); real AVPlayer swaps in Phase 7. | todo |
| 6  | Downloads + Settings | Downloads screen (Wi-Fi banner, Active, On-this-device), full Settings groups two-way bound. | todo |
| 7  | Real backend (HTTP + streaming) | `HttpMediaSource` (SwiftSoup: h5ai `li.item` + fallback table + generic autoindex via absolute URLs; percent-encode segments for `( ) ! ' *`), `AppRepo` persistence (servers/bookmarks/progress as JSON in Application Support), fake data removed, async Browser load, **AVPlayer streaming** + external + embedded subtitle. Verify live against an h5ai/autoindex server. | todo |
| 8  | Visual polish | Real cover thumbnails (`URLSession` + caching, or **Nuke/Kingfisher**; folder → inner image via `ThumbCache`/`firstImageUrl`, file → sibling poster, gradient fallback), pull-to-refresh equivalent (toolbar refresh + `refreshKey`), sort on real metadata. | todo |
| 9  | Browser UX | 2-line names; persisted grid/list view; image-file preview thumbnails + full-screen QuickLook-style zoom viewer; poster **hero header** in media folders; **TV-series view** (`detectSeasons`/`SeriesView`) with **cross-season playlist** in player host. | todo |
| 10 | Player depth | Real audio-track picker (AVMediaSelection), native **Picture-in-picture** (`AVPictureInPictureController`), in-player playlist sheet, season-variant labels, `Esc`/close saves progress → Continue, `MPNowPlayingInfoCenter` now-playing + media keys. | todo |
| 11 | Offline downloads | Background download (`URLSession` background config), Wi-Fi-only constraint (`allowsCellularAccess`/path monitor), live progress, resumable, persisted offline library, play offline. | todo |
| 12 | Auth + protocols + VLCKit | Basic-auth header on AVPlayer datasource (login-protected streaming), FTP/SFTP/WebDAV MediaSources, **VLCKit fallback** for codecs AVFoundation rejects (MKV/HEVC). | todo |

---

## Conventions

- **macOS-native feel** — system materials (`.regularMaterial`), vibrancy where
  apt, native context menus, toolbar, menu-bar commands, focus ring on keyboard
  nav. Avoid feeling like a phone app stretched wide.
- Drive everything from theme tokens (`Theme.colors` / typography / shapes). No
  ad-hoc colors; surfaces step through container tones, not borders.
- **Hover + state layers** on every actionable element (8% hover · 12% press),
  pointer cursor changes.
- Icons: UI icons outlined (SF Symbols thin/regular or bundled set); media
  transport (play/pause/skip/replay) filled.
- Keyboard-first: every primary action reachable by shortcut; full-screen and
  player fully drivable without the mouse.
- Ask user (with options + recommendation) before guessing on scope.
- Confirm before destructive actions (file delete, resets, overwrites).

---

## Testing — local Mac

No wireless ADB. Build, run, screenshot locally.

```bash
xcodebuild -scheme AnMediaPlayerMac -configuration Debug build   # build
open -a AnMediaPlayerMac                                          # or run from Xcode
screencapture -x /tmp/s.png                                       # screenshot to verify
log stream --predicate 'subsystem == "xyz.devnerd.anmediaplayer.mac"' --level debug   # logs
```

For repeatable UI checks, prefer launching the built `.app` and using
`screencapture -l <windowID>` (or `-R` region) to capture the window.

---

## Real-backend test target (Phase 7)

Reuse an h5ai server on the local network (cleartext HTTP). Needs an **App
Transport Security** exception for the host (`NSAppTransportSecurity` →
`NSExceptionDomains`, or `NSAllowsArbitraryLoads` in debug only). Validate h5ai
parser, listing, streaming end-to-end.
