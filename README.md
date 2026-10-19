# Recipai

A native Android recipe app. Save recipes with ingredients, quantities
and timed steps, then **scale any ingredient and the whole recipe
follows** -- amounts, servings and step times. Import recipes from pasted
text or a shared link, attach photos, tag everything, and back up to
your own Google Drive.

> **Status: first milestone.** The library, recipe detail with live
> scaling, paste-text import, search and settings work, offline, with
> 8 example recipes. The editor, timers and cook mode, link import,
> photos and Drive backup come next -- see `docs/SYSTEM_STATE.md` for
> exactly what exists.

## Works today

- Browse recipes in a grid or list: search, filter by tag or "Under 30
  min", sort, favorite. Press and hold a recipe for quick actions.
- **Live scaling** on every recipe: change servings, pick ½× to 3×, or
  tap any amount and type a new one. Everything else, including step
  times, follows.
- **Import by pasting text:** see the parsed recipe update as you paste
  or type, then save it.
- Check off ingredients and steps while cooking, delete with undo,
  restore the examples, light/dark/auto theme, haptics on or off.

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

Kotlin and Jetpack Compose, Android only, native. Min SDK 26. Two
modules: `core` (plain Kotlin: scaling, the importer, the recipe file
format, with unit tests) and `app` (the Android UI). Recipes are stored
as a JSON file on the phone, settings in DataStore. See
`docs/SYSTEM_STATE.md`.

## Building

Needs JDK 17 and the Android SDK (compile SDK 35).

```sh
./gradlew :core:test testDebugUnitTest   # unit tests
./gradlew assembleDebug                  # debug APK, no signing needed
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
