# Default masks

Drop PNG or WebP images here to make them available as alpha masks in the Scene editor
(Mask picker). They are bundled into the app and listed automatically — no code change needed.

## How a mask works

A mask is composited over the scene's video. The image's **alpha channel** is what matters:

- **Opaque pixels dim/cover the video** (e.g. solid black edges → a vignette).
- **Transparent pixels let the video show through** (e.g. a clear centre → a spotlight/porthole).

So design masks as black + alpha: paint black where you want the picture hidden/darkened, and
leave transparent where you want it visible.

## Guidelines

- 16:9, ideally 1920×1080 (they are scaled to the output surface).
- WebP with alpha (lossless) keeps files tiny; PNG with alpha also works.
- Keep the file name simple — it becomes the label (e.g. `soft_vignette.webp` → "Soft vignette").

## Bundled examples

| File | Effect |
|------|--------|
| `vignette_soft.webp` | Gentle darkening toward the edges |
| `vignette_strong.webp` | Stronger edge falloff |
| `letterbox.webp` | Cinematic top/bottom black bars |
| `spotlight_oval.webp` | Clear oval centre, dark surround (porthole) |
