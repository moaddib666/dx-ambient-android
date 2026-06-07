# DX Ambient — Plan & Stack Decision

## Stack decision: native Android/Kotlin (not React Native) for the MVP

The MVP is heavy on video playback, masks, projector hardware, and TV-remote UX — all
low-level media/rendering concerns. Native is the right tool:

- **Kotlin + Jetpack Compose for TV** for UI — Google's modern Android TV UI approach.
  <https://developer.android.com/training/tv/playback/compose>
- **Media3 ExoPlayer** for local video/audio playlists.
  <https://developer.android.com/media/media3/exoplayer>
- **Media3 Composition / Transformer** for masks, overlays, mixed local audio/video (upgrade
  path beyond the always-works Compose-overlay masking used in the prototype).
- **SAF / MediaStore** for local files, USB, gallery.
- **YouTube Data API + IFrame Player** only as an optional, isolated mode. No audio extraction,
  no background YouTube.

**React Native** is possible via [`react-native-tvos`](https://github.com/react-native-tvos/react-native-tvos)
(Android TV + D-pad focus), but it suits UI-heavy apps, not a low-level media/rendering MVP.
**Flutter** is possible but TV support is more manual (focus, remote handling, custom
traversal — <https://docs.flutter.dev/ui/adaptive-responsive/input>).

> Recommendation: start native Kotlin. Add React Native/Flutter later only for an
> admin/mobile companion app.

## Build instruction given to the coding agent

```md
Build an Android TV-first ambient projector app prototype.

Use:
- Kotlin
- Jetpack Compose for TV
- Media3 ExoPlayer
- Media3 Transformer / Composition APIs where needed
- Storage Access Framework for folder/file picking
- Clean Architecture: app / domain / data / playback / ui modules

Do not implement YouTube audio extraction.
Do not implement background YouTube playback.
YouTube support must be isolated behind an optional module using official IFrame playback only.

MVP features:
1. TV remote-first home screen
2. Local video loop playback
3. Local audio playback
4. Scene model: video source, audio source, mask, brightness, loop mode
5. Mask on/off with PNG alpha masks
6. Import media from local storage / USB folder
7. Save and load scenes
8. Performance-safe fallback: no mask mode
9. Basic projector-safe timer / dim mode
10. Device capability logging
```

## Repo shape

```txt
ambient-projector/
  app/
  core-domain/
  core-data/
  core-playback/
  core-rendering/
  feature-scenes/
  feature-library/
  feature-settings/
  optional-youtube/
```

## MVP first milestones

```txt
Week 1-2: local video loop + TV UI
Week 3-4: scene model + file picker + saved scenes
Week 5-6: masks/overlays + audio source selection
Week 7-8: projector QA + performance fallback
```

## Device landscape & graceful degradation

China-market and global projector devices span a wide spectrum: very old builds (e.g. Dangbei
C2, Android 6 / 1 GB), mid-range (Xiaomi L1 Pro, MT9630, 2 GB), Android TV 11 (JMGO N1, 2 GB),
up to strong devices (Dangbei X5 Pro, Android 11, 4 GB / 128 GB). The app must degrade
gracefully and must **not** assume Google services are present:

- **LOW** tier (~1 GB / old Android): 720p–1080p loops, masks off by default.
- **MID** tier (~2 GB Android TV 11): 1080p loops, optional masks.
- **HIGH** tier (4 GB+): 4K loops, composited masks.

This is captured in `DeviceCapabilities.tier` / `recommendMasks` and enforced at render time by
the default SurfaceView fast path plus the performance-safe fallback.

## Two-lane product thesis (context)

- **Lane 1 (the product):** local media scenes with projector-aware masks, presets, scheduled
  ambience, USB/network files, external speakers.
- **Lane 2 (optional):** a constrained, Google-certified YouTube mode for Android TV / Google TV
  devices — official embedded playback only, never audio-only extraction or background playback.

Distribution differs per lane: Google Play for certified devices; direct APK / OEM-store
channels for China-market projectors that ship without Google Play.

## Rendering strategy

- **Fast path (default):** Media3 `PlayerSurface` backed by `SurfaceView` — best performance,
  right default for weak projector SoCs.
- **Mask/dim path:** PNG alpha mask drawn as a Compose overlay above the picture; brightness as a
  black scrim. Always toggleable off (performance-safe fallback).
- **Upgrade path (capable devices):** true alpha-channel compositing via Media3 video effects
  (`Player.setVideoEffects` + `BitmapOverlay`).
