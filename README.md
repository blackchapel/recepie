# Recipai

A native Android recipe app. Save recipes with ingredients, quantities
and timed steps, then **scale any ingredient and the whole recipe
follows** -- amounts, servings and step times. Import recipes from pasted
text or a link, attach photos, cook step by step with timers, and back
up to your own Google Drive.

> **Status: feature-complete for the first release, not yet tested on a
> device.** Every milestone (M1 to M6) is built and merged on
> `feature/app-scaffold`. The Android build has not been compiled or run
> on a phone yet; see `docs/KNOWN_GAPS.md`. `docs/SYSTEM_STATE.md` says
> exactly what exists.

## What it does

- **Library:** browse recipes in a grid or list; search, filter by tag
  or "Under 30 min", sort, favorite. Cards and rows rise in on first
  view. In the list layout, swipe a row left for Favorite and Delete
  (with Undo). Press and hold a recipe for quick actions: Open, Start
  cooking, Edit, Add photo, Favorite, Delete.
- **Create and edit recipes** with the + button, Search's "New recipe",
  or "Edit recipe" in a recipe's more menu or the press-and-hold sheet:
  title, description, servings and what they're counted as, tags with
  suggestions, ingredients with quantity, unit, name and note, section
  headings, timed method steps with a scaling mode, and notes. Pasting
  several lines into an ingredient name adds them all; a time mentioned
  in a step ("bake 12 minutes") is offered as the step's timer. Unsaved
  changes are confirmed before they're discarded.
- **Live scaling** on every recipe: change servings, pick ½× to 3×, or
  tap any amount and type a new one. Everything else, including step
  times, follows. Units step up or down sensibly (3 tsp becomes 1 tbsp).
- **Step timers:** tap a step's time to start a countdown; tap again to
  pause. Several can run at once, keep running with the screen off (an
  ongoing notification shows the countdown) and alert with a "Time's
  up" notification, a vibration and an optional chime. A timers sheet
  and a mini timer show everything that's running.
- **Cook mode:** one step at a time in large type, the scaled
  ingredients that step uses, a big timer button, swipe between steps,
  and the screen kept on.
- **Import:** paste recipe text and watch the parsed recipe update as
  you type, or paste a recipe page link and tap Convert. Share a page to
  Recipai from any browser or app and it converts on its own. Recipai
  reads the page's schema.org recipe card when there is one, otherwise
  the page text. Review opens the result in the editor before saving;
  saved recipes show where they came from. A shared page opens in
  Recipai's own task, so Back afterwards goes to the home screen or
  Recents rather than the sharing app. A site that only offers http
  can't be imported ("doesn't seem to offer this page over a secure
  connection"), and a link that takes over 45 seconds stops.
  Import is rule-based for now, with AI added later. That means no AI
  API key and no server, and the app works fully offline: it uses the
  network only to fetch a page you asked it to import and for Google
  Drive backup.
- **Photos:** add them on a recipe's page (the Add photo tile, or More >
  Add photo) or in the editor (add, choose the cover, remove). The cover
  shows everywhere; tap the big picture or a thumbnail for the
  full-screen viewer. Photos stay in the app's private storage, scaled
  to at most 2048 px; no storage permission is needed.
- **Google Drive backup:** connect from Settings > Backup or Search.
  Backups go to a "Recipai backups" folder in your own Drive (the
  `drive.file` scope: Recipai sees only the files it created). Back up
  on demand, daily or weekly at a time of day, optionally on Wi-Fi only
  and with photos. The six newest backups are kept; restoring one first
  saves a "Before restore" backup of the phone's current recipes.
  Connecting needs a Google Cloud OAuth client and the Drive API turned
  on (see `docs/RELEASING.md`); without them Connect explains that
  sign-in isn't set up for this build.
- Light/dark/auto theme, tuned haptic feedback (with an off switch), a
  predictive back swipe on Android 14+ where the page follows the
  finger. The library starts empty (an illustration and two ways to add
  a recipe); there are no sample recipes.

## Design

The approved UI is an interactive prototype:
`docs/design/recipai-prototype.html` (open it in a browser; on a desktop it
renders inside a phone frame). `docs/DESIGN.md` describes it in words and
records where the app deliberately differs.

## Tech

Kotlin and Jetpack Compose, Android only, native. Min SDK 26. Two
modules: `core` (plain Kotlin with unit tests: scaling, the importers,
page reading and fetching, the editor's draft rules, timers, photo
rules, the backup format and Drive client) and `app` (the Android UI and
platform glue). Recipes are stored as a JSON file on the phone, photos
as JPEGs beside it, settings in DataStore. See `docs/SYSTEM_STATE.md`.

## Building

Needs JDK 17 and the Android SDK (compile SDK 35).

```sh
./gradlew :core:test testDebugUnitTest   # unit tests
./gradlew assembleDebug                  # debug APK, no signing needed
```

Without the Android SDK (for example in a sandbox), the shared code can
still be compile-checked and its tests run against Compose Desktop:

```sh
./gradlew -p tools/desktop-check :core:test :ui:compileKotlin :ui:test
```

See `tools/desktop-check/README.md`. It does not compile the Android-only
files (`platform/**` and a few named files); only the real Android build
does.

Release APKs are signed and built by GitHub Actions only; there is no
local release build. See `docs/RELEASING.md`.

## Testing a change

1. Push your feature branch.
2. GitHub -> Actions -> **Test Release** -> Run workflow -> enter the
   branch name.
3. Download the APK from the run's artifacts and install it.

Things only a phone can check: haptics, timers with the screen off and
their notifications, the photo picker, the back swipe, and Google Drive
backup (which also needs the OAuth client from `docs/RELEASING.md`).

## Releasing

Merge approved PRs into `main`, then Actions -> **Production Release** ->
Run workflow (on `main`). It tags `v<major>.<minor>.<patch>`, builds the
signed APK and publishes a GitHub Release. Details in
`docs/RELEASING.md`.

## Contributing

Branch/PR rules, versioning and docs upkeep are in `CLAUDE.md`.
