# Store graphic assets (Gradle Play Publisher layout)

Drop the final PNG/JPEG assets into these folders — `./gradlew publishListing` uploads
them together with the listing text. Each folder takes plain numbered images
(`1.png`, `2.png`, …); for single-image slots (icon, feature graphic, banner) the
first file in the folder is used. Google Play **requires** the starred (★) items
before an Android TV app can be published.

| Asset | Folder | Spec | Required |
|-------|--------|------|----------|
| App icon ★ | `icon/` | 512 × 512, 32-bit PNG, < 1 MB | ✅ Play listing |
| Feature graphic ★ | `feature-graphic/` | 1024 × 500, PNG/JPEG (no alpha) | ✅ |
| TV banner ★ | `tv-banner/` | 1280 × 720, PNG/JPEG | ✅ for TV apps |
| TV screenshots ★ | `tv-screenshots/1.png` … (1–8) | 1280 × 720 or 1920 × 1080, 16:9 | ✅ min 1 |
| Phone screenshots | `phone-screenshots/1.png` … | 16:9 or 9:16, ≥ 320 px short side | optional (app also runs on phone) |

## Source material already in the repo

- **App icon** — render at 512×512 from `app/src/main/res/mipmap-*/ic_launcher_foreground.png`
  on the brand background (`#000000`), or re-export from the icon source art.
- **TV banner** — the in-app banner `app/src/main/res/drawable-xhdpi/tv_banner.png`
  is only 320×180. Re-export the same artwork at **1280×720** for the store (the
  in-app one is fine as-is; the store needs the larger version).
- **Screenshots** — capture from a TV emulator / device:
  ```bash
  adb shell screencap -p /sdcard/shot.png && adb pull /sdcard/shot.png tv-screenshots/1.png
  ```
  Capture the Home scene grid, the Player with a mask + dim, and the Scene editor.

> Tip: keep screenshots free of device frames; Play adds its own chrome.
