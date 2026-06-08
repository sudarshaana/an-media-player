# AnMediaPlayer (macOS) — Project Guide

Native macOS port of the Android **AnMediaPlayer**. Browse remote media listings
(HTTP index · h5ai · Apache/nginx autoindex · FTP/WebDAV), play video in-app.
Premium media-app feel (Plex / Jellyfin desktop), dark-first + full light theme.

Bundle id: `xyz.devnerd.anmediaplayer.mac` · macOS 14 (Sonoma) min · SwiftUI +
AppKit · Swift 6 · Xcode 16.

> **Sibling app, not a 1:1 reskin.** Same product, re-thought for desktop:
> pointer + keyboard + multiple resizable windows + larger canvas. Where the
> platform demands, the design changes — see **Platform deltas**.

---

## Design source (authoritative)

From Claude Design handoff bundle. Spec = **`UI Design & Flow.md`** (same bundle
as Android). Recreate visual output (tokens, color, type, component intent);
**adapt layout** for desktop per Platform deltas. Do **not** copy prototype JSX
structure. Key prototype files: `src/theme.jsx` (tokens), `src/screens-*.jsx`,
`player.jsx`, `ui.jsx`, `data.jsx`.

Read `UI Design & Flow.md` before any screen work.

---

## Locked decisions

| Topic | Decision |
|---|---|
| UI framework | **SwiftUI** for screens; **AppKit** bridges where SwiftUI is thin (window chrome, video overlay, drag-drop, `NSMenu` context menus). |
| Data backend | **Fake in-memory data first** (mirrors prototype). Real HTTP-index/h5ai/Apache/nginx/FTP/WebDAV parsers + streaming = Phase 7. |
| Player engine | **AVKit / AVFoundation** (`AVPlayer` + `AVPlayerView`). **VLCKit** fallback for codecs AVFoundation rejects (MKV/HEVC) deferred to Phase 12. |
| Navigation | **`NavigationSplitView`** — left sidebar (Home/Servers/Downloads/Settings) + detail. No bottom nav. |
| Theme mode | **Light / Dark / Follow-system**, persisted. Dark = default look. Respect `NSApp.effectiveAppearance`. |
| Color source | **Fixed M3-derived seed (`#6750A4`) + 5 swappable accents** (purple, green, amber, azure, crimson). No wallpaper dynamic color. (Open: optionally honor macOS System accent.) |
| Type family | **Manrope** (bundled variable font `Manrope.ttf`, weights 400–800). (Open: SF Pro for more native feel.) |
| Persistence | `UserDefaults` + JSON in `~/Library/Application Support/AnMediaPlayer/`. Mirrors Android DataStore role. |
| Window model | Single main window + separate **detached player window** (`WindowGroup`); full-screen + native PiP. |

---

## Platform deltas (macOS vs Android) — read before any screen

| Concern | Android (source) | macOS (this app) |
|---|---|---|
| Top-level nav | Bottom `NavigationBar` | `NavigationSplitView` **sidebar** |
| Primary input | Touch, tap, swipe | **Pointer + keyboard + trackpad**; right-click menus; hover first-class |
| Hover | none | Reveal overflow/actions on hover; cursor changes |
| Player gestures | Double-tap seek, brightness/volume drag | Keyboard (`Space`, `←/→`, `↑/↓` vol, `F` full, `M` mute), scrubber drag; **no brightness drag** (OS owns it) |
| Window | Single activity, orientation-locked | Resizable, multi-window, full-screen, **detached PiP window** |
| Menu bar | n/a | **App menu commands** (Playback/View/Go) with shortcuts |
| Drag-drop | n/a | Drag server URL / local file onto window to add/open |
| Density | Phone shelves | Wider canvas → denser multi-column grids, larger hero |
| PiP | `enterPip` | `AVPictureInPictureController` native |
| Cleartext HTTP | `networkSecurityConfig` | **App Transport Security** exception for `http://` hosts |
| Background audio | MediaSessionService (deferred) | `MPNowPlayingInfoCenter` + remote command center |
| Testing | Wireless ADB to S23 | **Run locally** on the Mac; `screencapture` |

---

## Theme plan

- Two full tonal schemes — **dark** + **light** — from design token tables
  (`UI Design & Flow.md` §1.1, mirrors `theme.jsx` `M3_DARK` / `M3_LIGHT`).
- **Accent** re-maps only the `primary` family (primary / onPrimary /
  primaryContainer / onPrimaryContainer) per scheme; rest stays coherent.
- `ThemeMode` ∈ {SYSTEM, LIGHT, DARK}; `Accent` ∈ {PURPLE, GREEN, AMBER, AZURE, CRIMSON}.
- Both persisted via `SettingsStore` (UserDefaults), read at app root, two-way
  bound to Settings ▸ Appearance.
- Resolved scheme drives `.preferredColorScheme`; native materials/vibrancy
  honor the same dark/light.

---

## Architecture / folder layout

```
AnMediaPlayerMac/
├─ App/
│  ├─ AnMediaPlayerApp.swift      // @main; WindowGroup(s); injects AppEnvironment
│  ├─ AppEnvironment.swift        // root observable: settings, repo, theme
│  └─ Commands.swift              // menu-bar commands (Playback/View/Go)
├─ Theme/                         // Color.swift, Accent.swift, Typography.swift, Shapes.swift, Theme.swift
├─ Components/                    // shared SwiftUI kit (GradientCover, hover surfaces, StateLayer)
├─ Navigation/                    // Destination enum, SidebarView, RootSplitView
├─ Screens/{Home,Servers,Browser,Downloads,Settings,Player}/
├─ Data/                          // Models (Entry, Server, Download…), AppRepo, FakeData
│  ├─ Sources/                    // HttpMediaSource, FtpMediaSource, WebDavMediaSource (Phase 7+)
│  └─ Thumbnails/                 // ThumbCache, firstImageUrl, poster resolution
└─ Settings/                      // SettingsStore (UserDefaults), ThemeMode, Accent
```

Rule: **split code as files grow** — one screen per folder/file, shared widgets
in `Components`, no god files. Mirror Android screen split.

### Subtitles
Two paths: **external** sibling `.srt`/`.vtt` (matched by stem, sideloaded as a
secondary text track) and **embedded** tracks inside container. Select English
legible group via `AVPlayerItem.select(_:in:)` on the legible characteristic
(`AVMediaCharacteristicLegible`).

### h5ai parsing (important — carry over from Android)
**Raw HTTP response** from h5ai = fallback `<table>` (`td.fb-n` name anchor,
`td.fb-d` date `yyyy-MM-dd HH:mm`, `td.fb-s` size like `1550541 KB`). Pretty
`<li class="item">` list built client-side by JS, **not** in response.
`HttpMediaSource` parses, in order: `li.item` (if present) → **fallback table**
(primary path for h5ai — real date+size) → generic autoindex anchors. Use
**SwiftSoup** (Swift jsoup port). Percent-encode path segments for `( ) ! ' *`.

### Player playlist
Player host builds a playlist (`EpisodeRef`, cross-season for series via
`buildSeriesPlaylist`, else folder video siblings), passes labels + currentIndex
to the player. Top-chrome **Playlist** button opens a playlist sheet listing all
items (current highlighted, click to switch). Shown only when >1 item.

### Cover art
No real posters at first. Each cover = deterministic two-stop gradient seeded
from file/folder name (`posterFor()` → `hsl(h 58% 44%) → hsl(h2 54% 26%)`),
rendered as SwiftUI `LinearGradient`. Real `poster.jpg`/`folder.jpg` slot into
the same frames in Phase 8.

---

## Phase plan

Implement one phase, **verify on the Mac, then stop** — user reviews and asks to
proceed. User supplies the detailed spec per phase before Claude builds it.
Full phase table lives in **`MacOS_App_Phases.md`** (Phases 0–12). Update Status
there as phases land.

Current: **Phase 0 (Scaffold) — todo.**

---

## Conventions

- **macOS-native feel** — system materials (`.regularMaterial`), vibrancy where
  apt, native context menus, toolbar, menu-bar commands, focus ring on keyboard
  nav. Don't ship a phone app stretched wide.
- Drive everything from theme tokens (`Theme.colors` / typography / shapes). No
  ad-hoc colors; surfaces step through container tones, not borders.
- **Hover + state layers** on every actionable element (8% hover · 12% press);
  pointer cursor changes.
- Icons: UI icons outlined (SF Symbols regular or bundled set); media transport
  (play/pause/skip/replay) filled.
- Keyboard-first: every primary action has a shortcut; player + full-screen
  fully drivable without the mouse.
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

Repeatable UI checks: launch the built `.app`, capture with `screencapture -l <windowID>` or `-R <region>`.

---

## Real-backend test target (Phase 7)

Reuse an h5ai server on the local network (cleartext HTTP). Needs **App
Transport Security** exception for the host (`NSAppTransportSecurity` →
`NSExceptionDomains`, or `NSAllowsArbitraryLoads` debug-only). Validate h5ai
parser, listing, streaming end-to-end.
