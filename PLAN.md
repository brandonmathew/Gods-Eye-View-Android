# God's Eye View Android — Plan

Started 2026-11-21. Built from scratch in Kotlin. Inspired by, but not a port of, bilawalsidhu/gods-eye-view (MIT). Planned one question at a time — 42 locked decisions (Q1–Q42), then Brandon directed: write the plan and start building the core.

- App name: **God's Eye View Android**
- Repo: **brandonmathew/Gods-Eye-View-Android** (public)
- Package ID: **com.makoallc.godseyeview**
- License: **MIT** for our code. Data sources keep their own terms, listed in the in-app Credits section.
- First stable release: **v1.0.0 = the finished core globe, with no data layers.**

## The one-sentence version

A mobile-first, photorealistic 3D globe for Android with a clean, modular layer system built into its core — shipped with zero data layers, so every layer after it (flights, satellites, ships, …) plugs into a proven system instead of being bolted onto a globe that was built around flights.

## Locked decisions

### Scope
- Q1: Mobile-first core globe, layers added later. Not full desktop parity.
- Q2: NO data layers in the core. Core is just the globe, plus the empty machinery layers need (see Core contents). Tested only with a throwaway sample layer that is deleted before release.
- Q37: First layer and layer order are decided only AFTER the core is built and confirmed on Brandon's phone. Core performance results decide what layer is feasible first.
- Q17: Clean globe only. Sensor styles (FLIR / NVG / CRT / Noir) and the military HUD are later modules, not core.
- Q6: Public open-source release. Not personal-only, not Play Store first.
- Q7: Name is "God's Eye View Android" (Brandon's choice; noted during planning that "God's Eye View" is a trademark of Metaversity LLC used under license by the original project).

### Tech
- Q3: Kotlin app + CesiumJS globe in a WebView. Not fully native globe, not Unity.
- Q5: Native Kotlin UI. The WebView renders ONLY the globe; panels, search, settings, and detail cards are native Android.
- Q8: Jetpack Compose for the native UI.
- Q9: Gradle + GitHub Actions builds, like Pool Helper.
- Q12: CesiumJS bundled in the APK, not loaded from a CDN.
- Q42: CesiumJS version = latest stable on the day building starts, pinned exactly. Upgrades are their own issue and get a phone test.
- Q10: Minimum Android 12 (API 31).
- Q11: Portrait-first; landscape also works, with no separate landscape layout in the core.
- Q33: Works on tablets with the same UI and more globe; no dedicated tablet layout in the core.
- Q13: No backend for the core. Revisit per layer only if a specific layer truly needs one.

### The globe
- Q4: Basemap ladder, like the original: keyless default (Esri satellite imagery + keyless terrain, OSM fallback if Esri is unreachable), and a user-supplied Cesium ion token unlocks Google Photorealistic 3D + world terrain.
- Q14: Each user pastes their own Cesium ion token in Settings. No shared/developer token is built in.
- Q35: Tokens are stored encrypted with the Android Keystore — never plaintext, never backed up, never leaving the device.
- Q18: First launch opens on full Earth from orbit. Later launches restore the last view exactly.
- Q19: Standard tile cache with a size limit the user can change or clear in Settings. No offline area downloads in the core.
- Q20: Performance auto-detects device specs (RAM / GPU / screen) at runtime and picks a quality level, with a manual override in Settings: High / Balanced / Battery Saver. Nothing is hardcoded to one phone.
- Q36: Share the current view (camera + basemap) from the core.

### Core contents (the "empty machinery")
The core ships no data layer, but it ships everything a layer needs:
- Globe + basemap ladder (above), touch camera (pan / pinch zoom / tilt / rotate), Reset Globe.
- Q15 Search: keyless. Coordinates and bundled places/landmarks fly with no network request; other names fall back to Photon, then Nominatim. Google Places is an optional later upgrade, not core.
- Q16 My Location: dot + fly-to-me, only when the user taps it. Never silently on launch.
- Layer registry: Q21 every layer is its own Gradle module depending only on a shared layer-API module — never on the app, never on another layer.
- Q26 Layer drawing API v1: points, tracks/polylines, polygons, labels. 3D models and heatmaps come later through a versioned API, without breaking existing layers.
- Q28 Selection + detail card, and follow/track camera mode, both in the core (generic — a layer supplies the entity and its card fields).
- Q27 World clock in the API only: layers read world time from the core, never wall time directly. No timeline/scrub UI in the core.
- Q29 Core-managed data fetching for every layer: a layer declares its refresh interval and freshness TTL; the core handles polling, disk/memory caching, retry with backoff, stale marking in the UI, and pausing when the layer is off or the app is backgrounded.
- Settings: Cesium ion token entry, quality override, tile-cache size/clear, update channel (below), Credits (below).
- Q32 Credits: a Credits section in the Settings menu listing every provider, dataset, and library in the core with its terms, plus the one small on-globe credit line Cesium/Google/Esri terms require while their tiles are showing. Every future layer adds its source to Credits as part of its module.
- Q25 Crash handler like Pool Helper's — a crash saves a report on the phone and opens a screen with the reason and Copy / Restart / Close — with NO test-crash button (Brandon's correction).
- Q34 Privacy: no accounts, no analytics, no tracking.
- Q30/Q31: Dark theme only. English only, with all strings in resources so translation later is a translation job, not a refactor.

### Build, release, workflow (same as Pool Helper where noted)
- Q22 GitHub workflow: public repo; an issue for every feature, enhancement, and bug, with factual issue comments only; branch per issue; PRs into `beta`; Brandon tests beta builds on his phone; confirmed beta PRs into `main`; tags on `main` produce stable Releases. Conventional Commits, "Refs #N" until phone-confirmed (nothing auto-closes early), `awaiting-confirmation` label until then. Done = code done + CI green + confirmed on Brandon's phone, all three.
- Q38 Versioning: stable majors X.0.0; hotfix patches X.0.N; betas named after their target stable (1.0.0-beta.1, 1.0.0-beta.2, …) and flagged as GitHub prereleases; versionCode is a separate CI-assigned monotonic counter across both channels.
- Q41 Signing: one stable key for all builds, kept only in GitHub secrets plus a local keys folder outside the repo (`~/workspace/gods-eye-view-android-keys/`). Never in the repo.
- Q23 Updater: the app checks GitHub Releases itself, downloads the APK in-app with its own downloader (not DownloadManager, no browser), verifies SHA-256, and the install itself is one user tap on the system prompt. Stable/Beta channel switch in Settings, same signing key for both, switching triggers an immediate re-check.
- Q40 The finished core is v1.0.0.

### Deliberately NOT in the core (each is a later, separate decision)
Data layers of any kind; sensor styles and HUD; cockpit view; voice/chat/AI; MCP; Director/scenes; street level; weather; radio; transit/rail; CCTV; offline area downloads; light theme; dedicated tablet/landscape layouts; timeline UI; 3D models and heatmaps in the layer API; a backend server; Google Places search; accounts/analytics (not planned at all).

### Responsible-use line (carried over from the original project)
This app visualises public data about events, assets, infrastructure, and systems. It does not do named-person search, face recognition, or tracking individuals. Data can be delayed, incomplete, modelled, or wrong; it is not for navigation, emergency response, or other safety-critical use. That wording ships in the README and the Credits/About section.

## Architecture sketch

```
:app                Compose shell, navigation, settings, updater, crash handler
:core-globe         WebView + bundled CesiumJS, Kotlin<->JS bridge, camera,
                    basemap ladder, cache, performance profiles
:core-ui            Shared Compose components: layer panel, detail-card
                    bottom sheet, search bar, credits
:layer-api          Layer interface, registry, entity model, drawing
                    primitives (point/track/polygon/label), world clock,
                    fetch-manager contracts, selection/follow contracts
:layer-sample       THROWAWAY proof layer — deleted before v1.0.0
:layer-<name>       One Gradle module per future layer, depends on
                    :layer-api only
```

Bridge rule: Kotlin owns state, networking, storage, and lifecycle. The WebView owns only rendering. Layers never talk to the WebView directly — they hand entities to the core, and the core renders them.

## Build stages to v1.0.0

- **Stage 1 — Shell + globe:** repo, CI, signing, Gradle module skeleton, Compose shell, bundled CesiumJS globe rendering keyless Esri + terrain, touch camera, full-Earth first launch, restore last view. Beta APK on Brandon's phone is the stage gate.
- **Stage 2 — Basemap ladder + settings:** Cesium ion token entry (Keystore-encrypted), photorealistic 3D unlock, OSM fallback, tile cache with size limit, device auto-detect quality + manual override, Settings + Credits screens.
- **Stage 3 — Find and share:** keyless search (coordinates, bundled places, Photon/Nominatim), My Location (tap-only), share current view, Reset Globe.
- **Stage 4 — Layer system proof:** layer-api + registry, drawing primitives, selection/detail card, follow/track, world clock API, core fetch manager — all proven end-to-end by the throwaway sample layer, which is then deleted.
- **Stage 5 — Release hardening:** in-app updater (stable/beta), crash handler, README + DATA_SOURCES/Credits completeness, final beta → Brandon confirms → v1.0.0 on main.

Each stage follows the Q22 workflow: issues first, branch per issue, PR to beta, CI green, Brandon confirms on his phone before anything is called done.
