# Google Play submission guide — DX Ambient (Android TV)

This is a complete, ordered checklist for publishing DX Ambient to Google Play,
including the Android-TV-specific requirements. Pre-filled answers reflect the
app's actual behaviour (local-first, no data collection, optional isolated
YouTube). Replace every `FILL:` placeholder before submitting.

App identity:
- **Package / applicationId:** `com.dx.ambient`
- **Version:** `1.0.0` (versionCode `1`)
- **Form factor:** Android TV / Google TV (also runs on phone/tablet)

---

## 0. One-time account setup

- [ ] Google Play Developer account ($25 one-time) — https://play.google.com/console
- [ ] Accept the Developer Distribution Agreement
- [ ] (Org accounts) complete identity/D-U-N-S verification — this can take days,
      do it early

## 1. Create the app in Play Console

- [ ] **Create app** → App name `DX Ambient`, default language `English (US)`,
      type **App**, **Free**
- [ ] Confirm Play policy + US export law declarations

## 2. Build & upload the bundle

- [ ] Build the signed AAB — see [RELEASE.md](RELEASE.md):
      `./gradlew :app:bundleRelease`
- [ ] Keep **Play App Signing** enabled (default). You upload with the *upload*
      key (`dx-ambient-upload.keystore`); Google manages the app signing key.
- [ ] Create a release in **Testing → Internal testing** first (fast review,
      add your own test account), then promote to Production.
- [ ] Upload `app-release.aab` and `mapping.txt` (Release → App bundle explorer
      handles the mapping, or attach via the release page).

## 3. Store listing (main)

Copy is ready in [`fastlane/metadata/android/en-US/`](../fastlane/metadata/android/en-US/):

- [ ] **App name:** `title.txt` → "DX Ambient"
- [ ] **Short description:** `short_description.txt`
- [ ] **Full description:** `full_description.txt`
- [ ] **App icon:** 512×512 (see `images/README.md`)
- [ ] **Feature graphic:** 1024×500
- [ ] **Phone screenshots:** optional (app runs on phone too)

## 4. Android TV listing & review (REQUIRED for TV)

Android TV apps go through an extra **TV quality review**. In Play Console:

- [ ] **Advanced settings → Form factors → Android TV → Add Android TV** and opt
      the release into the TV catalogue.
- [ ] **TV banner:** 1280×720 (the in-app `tv_banner.png` is only 320×180 — export
      a 1280×720 version; see `images/README.md`).
- [ ] **TV screenshots:** at least 1, 1280×720 or 1920×1080 (16:9).
- [ ] Confirm the manifest declares the TV entry point — already done:
      - `LEANBACK_LAUNCHER` intent-filter on `MainActivity` ✅
      - `android.hardware.touchscreen` `required="false"` ✅
      - `android.software.leanback` present (`required="false"`, so phones still
        get it) ✅
      - No `android.hardware.touchscreen` / camera / telephony hard requirement ✅
- [ ] TV quality notes the reviewer checks (all satisfied by the current build):
      D-pad navigation with visible focus, landscape orientation, no reliance on
      touch, immersive full-screen playback.

## 5. Privacy policy (REQUIRED)

- [ ] Host [`docs/PRIVACY_POLICY.md`](PRIVACY_POLICY.md) at a public URL
      (e.g. GitHub Pages, your site) and set the **CONTACT_EMAIL_HERE** placeholder.
- [ ] Enter the URL in **Policy → App content → Privacy policy**.

## 6. App content declarations

### Data safety  (Policy → App content → Data safety)
Answers reflect actual behaviour — the app collects nothing itself:

- [ ] **Does your app collect or share any of the required user data types?** → **No**
- [ ] **Is all data encrypted in transit?** → N/A (no data collected; optional
      YouTube uses HTTPS via Google's player)
- [ ] **Do you provide a way to request data deletion?** → N/A (no data collected)
- [ ] In the description, note the optional YouTube mode loads Google's official
      player (Google's data practices then apply). The app stores media/scenes
      **locally only** — local-only storage is not "collection" under Play's
      definition, so it is not declared.

### Content rating  (Policy → App content → Content rating)
Complete the IARC questionnaire. Expected answers → **Everyone / PEGI 3**:

- [ ] Category: **Utility / Productivity / Other** (not a game)
- [ ] Violence / sexual content / profanity / drugs / gambling → **No** to all
- [ ] User-generated content shared with others → **No** (media stays local)
- [ ] Does the app share user location → **No**

### Other required declarations
- [ ] **Target audience & content:** target age **13+** (or 18+) — *not* a "designed
      for families / children" app, to avoid the stricter kids program.
- [ ] **Ads:** **No, this app does not contain ads.**
- [ ] **Government app:** No.
- [ ] **Financial features:** No.
- [ ] **Health:** No.
- [ ] **Data safety / permissions:** the only sensitive-looking permission is
      `INTERNET` (optional YouTube). No restricted permissions are requested.
- [ ] **News app / COVID / etc.:** No.

## 7. Store settings

- [ ] **App category:** Apps → **Entertainment** (or Lifestyle)
- [ ] **Tags:** ambient, projector, relaxation, TV
- [ ] **Contact details:** support email (required), website (optional)
- [ ] **Countries / regions:** select distribution territories

## 8. Pricing & distribution

- [ ] Free
- [ ] Confirm it is available on **Android TV** form factor
- [ ] Content guidelines + US export laws checkboxes

## 9. Pre-launch checks

- [ ] Roll out to **Internal testing**, install on a real Android TV / Google TV
      device (or the TV emulator) from the test link, verify:
      - launches into the scene grid
      - D-pad navigation + visible focus everywhere
      - a demo scene plays, masks + dim apply, sleep timer works
      - SAF import opens the system picker
- [ ] Review the **Pre-launch report** (Play runs the app on real devices) for
      crashes / accessibility warnings.

## 10. Submit for review

- [ ] Promote the reviewed build from Internal testing → **Production**
      (or Closed/Open testing first if you want a staged rollout).
- [ ] Submit. TV review typically takes longer than phone-only review; expect a
      few days. Watch for the TV-quality result specifically.

---

## Fastlane (optional automation)

The metadata is laid out for `fastlane supply`. After a one-time
`fastlane supply init` (to fetch the service-account JSON), you can push listing
text + the bundle with:

```bash
fastlane supply \
  --aab app/build/outputs/bundle/release/app-release.aab \
  --mapping app/build/outputs/mapping/release/mapping.txt \
  --metadata_path fastlane/metadata \
  --track internal
```

## Placeholders to replace before submitting

- `docs/PRIVACY_POLICY.md` → `CONTACT_EMAIL_HERE`, then host it and grab the URL
- `images/` → app icon (512²), feature graphic (1024×500), TV banner (1280×720),
  ≥1 TV screenshot (1280×720)
- Support email + (optional) website in Store settings
