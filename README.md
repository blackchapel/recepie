# Recipai

A native Android recipe app. Save recipes with ingredients, quantities
and timed steps, then **scale any ingredient and the whole recipe
follows** -- amounts, servings and step times. Import recipes from pasted
text or a shared link, attach photos, tag everything, and back up to
your own Google Drive.

> **Status: pre-build.** This repo currently holds the approved design
> and the CI/release pipeline. There is no app code yet -- see
> `docs/SYSTEM_STATE.md` for exactly what exists.

## Features (target)

- Add, edit, read and delete recipes: ingredients with quantities and
  units, ingredient sections, timed method steps, notes, photos, tags.
- **Live scaling:** change the servings, pick a preset (½× to 3×), or
  type a new amount for any one ingredient. Every other amount, the
  servings and the step times scale to match. Units step up or down
  sensibly (3 tsp becomes 1 tbsp, 1000 g becomes 1 kg).
- **Step timers:** tap a step's time to start a countdown. Several can
  run at once and keep running with the screen off.
- **Cook mode:** one step at a time, large text, screen kept on, with the
  scaled amounts that step uses.
- **Import:** paste copied recipe text, or share a recipe link to Recipai
  from the browser's share sheet. Recipai converts it into its own format.
  Import is rule-based for now, with AI added later. That means no AI API
  key and no server, and the app works fully offline.
- **Google Drive backup:** connect a Google account, back up daily,
  weekly or only on demand, restore from earlier backups.
- Light and dark themes, haptic feedback, offline-first.

## Design

The approved UI is an interactive prototype:
`docs/design/recipai-prototype.html` (open it in a browser; on a desktop it
renders inside a phone frame). `docs/DESIGN.md` describes it in words.

## Tech

Kotlin, Jetpack Compose, Room, WorkManager, Google Drive REST API. Android
only, native. Min SDK 26. See `docs/SYSTEM_STATE.md`.

## Building

Once the Gradle project exists:

```sh
./gradlew testDebugUnitTest      # unit tests
./gradlew assembleDebug          # debug APK, no signing needed
```

Release APKs are signed and built by GitHub Actions only; there is no
local release build. See `docs/RELEASING.md`.

## Testing a change

1. Push your feature branch.
2. GitHub -> Actions -> **Test Release** -> Run workflow -> enter the
   branch name.
3. Download the APK from the run's artifacts and install it.

## Releasing

Merge approved PRs into `main`, then Actions -> **Production Release** ->
Run workflow (on `main`). It tags `v<major>.<minor>.<patch>`, builds the
signed APK and publishes a GitHub Release. Details in
`docs/RELEASING.md`.

## Contributing

Branch/PR rules, versioning and docs upkeep are in `CLAUDE.md`.
