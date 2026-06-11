# Release build & signing — DX Ambient

## Toolchain

- JDK 17 (Zulu 17 verified)
- Android SDK with platform **API 35** and Build-Tools **35.0.0**
- `local.properties` → `sdk.dir=/path/to/Android/sdk`

## Signing

Release signing is configured in [`app/build.gradle.kts`](../app/build.gradle.kts).
It reads credentials from `keystore.properties` at the project root (preferred for
local builds) or from environment variables (preferred for CI). Both the keystore
and `keystore.properties` are **gitignored** and must never be committed.

### keystore.properties (local)

```properties
storeFile=dx-ambient-upload.keystore
storePassword=********
keyAlias=dxambient-upload
keyPassword=********
```

### Environment variables (CI)

| Variable | Meaning |
|----------|---------|
| `DXA_STORE_FILE` | Path to the keystore (relative to project root) |
| `DXA_STORE_PASSWORD` | Keystore password |
| `DXA_KEY_ALIAS` | Key alias |
| `DXA_KEY_PASSWORD` | Key password |

If neither source provides `storeFile`, the release build falls back to the debug
key so `assembleRelease` still works for local smoke tests — **never upload a
debug-signed bundle to Play.**

> ⚠️ **Back up the upload keystore and its passwords offline.** With Play App
> Signing you can reset a lost *upload* key via Play Console support, but losing it
> still blocks releases until reset. Store a copy in a password manager / secure
> vault.

### Regenerating the upload keystore (only if starting over)

```bash
keytool -genkeypair -v \
  -keystore dx-ambient-upload.keystore \
  -alias dxambient-upload \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=DX Ambient, OU=DimensionX, O=DimensionX, L=Unknown, ST=Unknown, C=UA"
```

## Build the release bundle (AAB)

```bash
./gradlew :app:bundleRelease
```

Output:

- **Bundle:** `app/build/outputs/bundle/release/app-release.aab` (upload this to Play)
- **Mapping:** `app/build/outputs/mapping/release/mapping.txt`
  (upload alongside each release so crash stack traces de-obfuscate)

Verify the signature:

```bash
jarsigner -verify app/build/outputs/bundle/release/app-release.aab   # → "jar verified."
```

### Optional: build a universal APK for sideload testing on a TV

```bash
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Release config notes

- `minifyEnabled` + `shrinkResources` are **on** for release (R8 full mode). Keep
  rules live in [`app/proguard-rules.pro`](../app/proguard-rules.pro) — currently
  only kotlinx.serialization needs explicit rules; everything else uses library
  consumer rules.
- `versionCode` / `versionName` are in `app/build.gradle.kts` `defaultConfig`.
  **Bump `versionCode` for every upload** (Play rejects duplicate codes).
- The AAB (~53 MB) is dominated by the bundled demo scene media in
  `app/src/main/assets/scenes/`. This is intentional so the app has content on
  first launch; it is well under Play's 200 MB base-module limit.
