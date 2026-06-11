# CLAUDE.md — dx-ambient-app

Android TV / Google TV ambient-scenes app (Kotlin, Jetpack Compose for TV, Hilt, multi-module Gradle). Modules: `app` (navigation, boot), `core-domain`, `core-data`, `core-playback`, `core-rendering` (shared components, theme, **shared string resources**), `feature-scenes`, `feature-library`, `feature-settings`, `optional-youtube` (isolated IFrame-only YouTube mode).

## Localization (MANDATORY for every change)

The app is fully localized. **Never hardcode user-facing text in Kotlin code.** Every string a user can see — button labels, titles, hints, error messages, content descriptions, formatted values — must be a string resource.

### Where strings live

- All shared UI strings: `core-rendering/src/main/res/values/strings.xml` (English, the default locale). Every UI module depends on `core-rendering`, so reference them via `import com.dx.ambient.rendering.R`.
- Supported locales (a `values-<locale>/strings.xml` sibling each): `uk`, `de`, `es`, `fr`, `pt-rBR`, `ja`.
- `app/src/main/res/values/strings.xml` holds only `app_name` (brand, untranslated).
- The locale list is also declared in `app/src/main/res/xml/locales_config.xml` — keep it in sync when adding a locale.

### Rules when adding or changing any user-facing string

1. Add the key to `core-rendering/src/main/res/values/strings.xml` AND to **every** `values-<locale>/strings.xml` with a proper translation (translate yourself — do not leave English placeholders and do not skip locales). Skip locale files only for `translatable="false"` entries (brand names, unit formats like `%1$d MB`).
2. In composables use `stringResource(R.string.key)`; for counts use `<plurals>` + `pluralStringResource` (mind locale plural forms: `uk` needs one/few/many/other, `ja` only other).
3. Parameterized text uses positional placeholders (`%1$s`, `%1$d`) in the resource — never build sentences by string concatenation in code.
4. **ViewModels, repositories, and domain code must not produce display text.** They expose enums, sealed types, or resource IDs (see `LibraryError`, `SceneEditorViewModel.SaveError`, `YouTubeUiState.Error.ErrorKind`, `FeaturedStatus`); the composable maps them to `stringResource`. Exception messages may stay English — they are diagnostics, shown at most as a detail suffix.
5. Strings persisted to the DB that need a localized default at creation time (e.g. the default scene name) are resolved via `context.getString(...)` at the moment of creation.
6. Escape apostrophes in resources (`\'`) and use `&amp;` for `&`.
7. Key naming: `<screen>_<what>` (`editor_video_source`, `library_error_import`); cross-screen strings use `common_`.

### Verification

Before committing UI changes run `./gradlew :app:assembleDebug` and ensure no `MissingTranslation` lint errors. A quick grep for regressions: `grep -rnE 'text = "[A-Za-z]|label = "[A-Za-z]|title = "[A-Za-z]|contentDescription = "[A-Za-z]' --include='*.kt' feature-* optional-youtube app core-rendering` should return nothing.

## Other conventions

- `optional-youtube` plays YouTube ONLY through the official IFrame Player API in a WebView — no audio extraction, no background playback (policy header in `YouTubeIFrameScreen.kt`).
- TV-first UI: every interactive element must be D-pad focusable; touch is bridged via `touchClickable`.
- Unit tests: `./gradlew test` (core-domain, core-data have JVM tests).
