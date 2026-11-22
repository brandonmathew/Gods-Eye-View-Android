# God's Eye View Android

A mobile-first, photorealistic 3D globe for Android, built from scratch in
Kotlin. Inspired by, but not a port of,
[bilawalsidhu/gods-eye-view](https://github.com/bilawalsidhu/gods-eye-view) (MIT).

**Status: the core globe is in development (target v1.0.0).** The core ships
with zero data layers on purpose: it is the globe plus the modular layer
system every future layer (flights, satellites, ships, ...) plugs into.
See [PLAN.md](PLAN.md) for the locked decisions and build stages.

## What the core does

- CesiumJS globe (bundled in the APK, pinned version) in a WebView that
  renders only the globe; all UI around it is native Jetpack Compose.
- Keyless basemap: Esri World Imagery + keyless terrain, with an automatic
  OpenStreetMap fallback. A Cesium ion token you paste in Settings unlocks
  Google Photorealistic 3D (Stage 2).
- Touch camera, full Earth from orbit on first launch, and the app reopens
  exactly where you left it.
- A layer system: every layer is its own Gradle module depending only on
  `:layer-api` (points, tracks, polygons, labels; selection/detail card;
  follow/track; world clock; core-managed fetching).

## Responsible use

This app visualises public data about events, assets, infrastructure, and
systems. It does not do named-person search, face recognition, or tracking
individuals. Data can be delayed, incomplete, modelled, or wrong; it is not
for navigation, emergency response, or other safety-critical use.

## Build

Requires JDK 17 and the Android SDK. CI (GitHub Actions) builds every beta
and release; see `.github/workflows/`.

## License

MIT for our code - see [LICENSE](LICENSE). Data sources and libraries keep
their own terms; they are listed in the in-app Credits section and in
DATA_SOURCES.md as layers land.
