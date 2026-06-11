# DX Ambient — Android TV Ambient Projector App

An **Android TV-first** ambient projector app prototype. Loop local video/audio, apply PNG
masks and projector-safe dimming, build and save reusable **scenes**, and drive it all from a
TV remote. Built native (Kotlin + Jetpack Compose for TV + Media3 ExoPlayer) because the MVP is
media/render-heavy and remote-driven — see [docs/PLAN.md](docs/PLAN.md) for the stack rationale.

> **Local-first, not a YouTube wrapper.** The core product is *your own media*. YouTube is an
> optional, isolated mode using **only** the official IFrame player — no audio extraction, no
> background playback.

## Stack

- **Kotlin**, single-activity, **Jetpack Compose for TV** (`androidx.tv.material3`)
- **Media3 ExoPlayer** for local video/audio playback and playlists
- **Storage Access Framework (SAF)** for folder/USB/gallery import
- **Hilt** DI, **Room** + **DataStore** persistence, Coroutines/Flow
- **Clean Architecture** across Gradle modules

## Module map

```
dx-ambient-app/
├── app/                # Single activity, Hilt app, Compose-Navigation graph, TV manifest
├── core-domain/        # Pure-Kotlin models, repository contracts, use cases (no Android)
├── core-data/          # Room, DataStore, SAF media indexer, device capability probe, Hilt wiring
├── core-playback/      # Media3 ExoPlayer PlaybackController (separate video + audio players)
├── core-rendering/     # TV theme/UI kit + AmbientStage (SurfaceView fast path + mask/dim overlay)
├── feature-scenes/     # Home (scene grid), Player, Scene editor
├── feature-library/    # SAF media import + browser
├── feature-settings/   # Projector settings + device capability log
└── optional-youtube/   # ISOLATED official IFrame player mode (INTERNET permission lives here only)
```

Dependency direction is strictly inward: `feature-*` → `core-rendering` → `core-playback` →
`core-domain`. Features talk to data/playback only through domain interfaces injected by Hilt.

## MVP feature → where it lives

| # | MVP feature | Implementation |
|---|-------------|----------------|
| 1 | TV remote-first home screen | `feature-scenes/HomeScreen.kt` |
| 2 | Local video loop playback | `core-playback/AmbientPlayer.kt`, `LoopMode` |
| 3 | Local audio playback | `AmbientPlayer` second player / audio source |
| 4 | Scene model (video, audio, mask, brightness, loop) | `core-domain/model/Scene.kt`, `SceneEditorScreen.kt` |
| 5 | Mask on/off with PNG alpha masks | `core-rendering/AmbientStage.kt`, `Mask` |
| 6 | Import media from local / USB folder | `core-data/saf/SafMediaIndexer.kt`, `feature-library` |
| 7 | Save and load scenes | Room `SceneRepository`, `SaveSceneUseCase` |
| 8 | Performance-safe fallback (no mask) | `ResolveEffectiveSceneUseCase`, `ProjectorSettings.performanceSafeMode` |
| 9 | Projector-safe timer / dim mode | `PlayerViewModel` sleep timer + auto-dim, brightness scrim |
| 10 | Device capability logging | `core-data/device/AndroidDeviceCapabilityProvider.kt`, `DeviceInfoScreen.kt` |

## Building

Requirements: **JDK 17**, **Android SDK** (API 35), Android Studio Ladybug+ recommended.

The Gradle wrapper is committed — clone and build:

```bash
./gradlew :app:assembleDebug      # build the APK
./gradlew test                    # JVM unit tests (domain + features)
./gradlew :app:installDebug       # install on a connected TV / emulator
```

`local.properties` already points at the local Android SDK.

Releases are published entirely from Gradle (no Play Console UI) — see
[docs/PUBLISHING.md](docs/PUBLISHING.md).

> **Status (verified):** the project builds and ships a debug APK. `./gradlew :app:assembleDebug`
> succeeds (Hilt aggregation, KSP, manifest merge, dexing) and the full unit-test suite —
> **75 tests across 15 suites, 0 failures** — passes via `./gradlew testDebugUnitTest`. It has not
> yet been run on a physical projector / emulator (UI behaviour, real SAF/USB import, and the
> WebView YouTube path still need on-device QA).

## Tests

`./gradlew testDebugUnitTest :core-domain:test` runs the suite (75 tests, all green). Coverage:

- **core-domain** — `Scene`, `DeviceCapabilities` tiering, `ProjectorSettings`, media models, and
  the `SaveScene` / `ResolveEffectiveScene` use cases.
- **optional-youtube** — the URL parsers (`extractVideoId`/`extractPlaylistId`), the highest-risk
  code, with the documented edge cases pinned.
- **core-data** — JSON round-trip mappers and `SceneRepositoryImpl` id/timestamp/duplicate logic.
- **core-playback** — the loop-mode → ExoPlayer repeat-mode mapping.
- **feature-\*** — every ViewModel (Home, Player, SceneEditor, Settings, DeviceInfo, Library) with
  fake repositories + a `MainDispatcherRule`.

Room/SAF integration, Compose UI behaviour, and the WebView path are intentionally left to
instrumented/on-device tests.

## Build notes

- **Media3 `@UnstableApi`:** opted in module-wide in `core-playback` and `core-rendering` via
  `-opt-in=androidx.media3.common.util.UnstableApi`.
- **`PlayerSurface`** comes from `androidx.media3:media3-ui-compose` — which only exists from
  Media3 **1.6.0+** (this project pins 1.6.1).
- JVM unit tests use `testOptions { unitTests.isReturnDefaultValues = true }` so `android.util.Log`
  doesn't throw under test.

## YouTube policy (optional-youtube)

The optional module embeds the **official YouTube IFrame Player** in a WebView and:

- performs **no** audio extraction and **no** audio/video separation;
- **pauses on `ON_PAUSE`/`ON_STOP`** — never plays in the background;
- keeps the player viewport full-screen (≥ 200×200 px per the embed rules).

It is the only module with the `INTERNET` permission, and the app entry point is hidden on
devices without Google Play Services. Replace the blank demo id in `AmbientNavHost.kt` with a
real source picker before enabling it.
