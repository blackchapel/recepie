# Recipai -- design

The approved UI. The interactive reference is
`docs/design/recipai-prototype.html`: open it in a browser (on a desktop it
sits inside a phone frame beside a short gesture guide; on a phone it runs
full screen). Where this file and the prototype disagree, the prototype
wins; fix this file.

The prototype is HTML, so it is a reference for look, structure and
behavior, not code to reuse. Build the real thing in Compose.

## Principles

- It is a phone app, not a website: bottom tab bar, pushed pages,
  bottom sheets, swipe gestures, haptics. No hover states, no
  desktop-style dialogs.
- Dark-first, with a designed light theme. Theme setting: Auto / Light /
  Dark (Auto follows the system).
- One accent idea: a saffron-to-paprika-to-pink "flame" gradient, used
  only for the Import button, the Start cooking button, progress and
  active timers. Everything else is neutral surfaces with colored tags.
- Motion is springy but short (about 250-500 ms). Honor the system's
  "remove animations" setting.

## Navigation

Bottom tab bar, five slots: **Recipes**, **Favorites**, **Import**
(center, gradient button), **Search**, **Settings**.

- Pushed pages (slide in from the right): recipe detail, Backup (from
  Settings). The tab bar slides away while a page is pushed, and the
  tab behind a sideways page shifts left and dims. Back is the top-left
  button or the system back gesture / swipe from the left edge, and it
  is silent (no haptic).
- **Back walks up, then home.** System back closes an open sheet, then
  pops pushed pages, then (from Favorites, Search or Settings) returns to
  Recipes, the home tab; back on Recipes with nothing open exits the
  app.
- Editor and cook mode: open as modal pages sliding up from the bottom
  (the editor has Cancel / Save in its top bar). The page behind them
  does not move: only the new page slides up. Cancel with unsaved
  changes asks first.
- Import: a large bottom sheet (Paste text / From a link, live preview of
  the parsed recipe, Review and Save buttons).
- Recipes and Favorites show a floating **+** button (new recipe) at the
  bottom right, above the tab bar. It hides on pushed pages and when
  other tabs are active.

## Screens

**Recipes** -- large title that collapses into a translucent top bar on
scroll; search field; tag chips with counts (plus an "Under 30 min"
chip); count, sort (Recent / A to Z / Quickest) and grid/list toggle;
the recipes. Grid cards: square photo, time badge, heart button, title,
servings. List rows: thumbnail, title, time and servings, tag dots.
Empty states: no recipes (an illustration of an empty plate with a fork
and knife, "No recipes yet", and Import a recipe / Write one yourself),
no favorites, no matches. There are no sample recipes.

**Favorites** -- the same list, favorites only.

**Search** -- a search field; with no query, colorful browse tiles
(Under 30 minutes, Favorites, one tile per tag); with a query, grouped
results: Recipes (title or ingredient match, with "Uses garlic"), Tags,
Actions (New recipe, Import, Back up now, Backup settings).

**Recipe detail** -- full-bleed photo with parallax and an overlaid back /
favorite / more (...) bar; a rounded content sheet with tags, title,
description, optional source line, stats (time, servings with - / +,
item count), photo strip with Add photo, Ingredients, Method, Notes. A
sticky bottom bar: timers button and **Start cooking**.
- Ingredients: a scale selector (½× 1× 1½× 2× 3×); each row has a check
  circle, name with note, and the amount. Tapping an amount opens the
  **amount sheet** (- / + steppers, quick scale chips, a live preview such
  as "everything scales 1.5x, 6 servings, 30 min", **Scale recipe**).
  When scaled, a banner shows the factor and which ingredient it
  matches, with Reset.
- Method: numbered steps (tap the number to mark done). A step with a
  time shows a chip: tap to start a timer; the chip becomes a live
  countdown. When scaled, the original time shows struck through. Each
  step shows its scaling mode (fixed / scales a little / scales with
  batch).
- More menu (action sheet): Edit recipe, Add photo, Add/Remove favorite,
  Delete recipe.

**Cook mode** -- full screen; progress segments; one step in large
type; the scaled ingredients that step uses; a big timer button if the
step has a time; swipe left/right or use the buttons; Finish on the last
step. Keeps the screen on (setting).

**Editor** -- photo strip (add, set cover, remove; shows the generated
illustration until a photo exists), Basics (title, description,
servings, "counted as"), Tags (chips with suggestions), Ingredients
(quantity, unit, name, note; sections; reorder; remove; pasting several
lines into a name field adds them all), Method (text, minutes, scaling
mode; reorder; remove; detected-time chip "Found 12 min · use it"),
Notes. Validation: a title is required.

**Settings** -- Backup (Google Drive row with status, Back up now),
Appearance (theme), Cooking (Haptic feedback, Keep screen on in cook
mode, Timer sound), Library (recipe and photo counts), About.

**Backup** (pushed from Settings) -- not connected: explainer (what is
saved, where, privacy) and **Connect Google account**. Connected:
account row with Disconnect; last backup, size and **Back up now** with a
progress bar; Automatic backups (Daily, Weekly with weekday picker,
Only when I tap) with time of day and the next run; options (Include
photos, Wi-Fi only); the six most recent backups with Restore. Restoring
first takes a "Before restore" backup so it can be undone.

**Welcome tour** (not in the prototype) -- shown once, on a brand-new
install, before the library. Three full-window pages in a swipeable
pager: (1) the recipe app (live scaling, step timers, cook mode, photos
and tags), (2) import (paste text or share a link; review before
saving), (3) Google Drive backup (daily or weekly, restore, optional).
Each has a 300 dp illustration drawn in Compose from the theme tokens,
an eyebrow, a title, two lines of body and feature chips. Skip (top
right, pages 1 and 2), page dots (the current one is a pill), and a
bottom button: **Next**, then **Get started** on the last page. Follows
the system light/dark theme; content stays inside the system bars while
the background fills the window. Back steps to the previous page, and
leaves the tour from page 1. It shows once ever: closing or updating the app halfway through does not
bring it back. Haptics: one tap per button press; the
page-change tick plays only for a swipe, never on top of Next, and Back
is silent. Replay: Settings > About > Welcome tour.

**Update card** (not in the prototype) -- appears at the top of Settings,
under the title, only while a newer GitHub release exists: "Update
available: x.y.z" in the accent color, "Tap to open the release page and
download the new APK". Tapping opens the release page in the browser.
There is no manual check, no "checking" or "up to date" state, and no
error state: the check runs silently each time the app is opened.

## Gestures

| Gesture | Where | Result |
|---|---|---|
| Swipe from the left edge | Any pushed page | Back (a sideways page follows the finger on Android 14+; the editor and cook mode pop on release) |
| Swipe a row left | List layout | Reveals Favorite and Delete; Delete has Undo |
| Press and hold | Recipe card or row | Preview sheet: Open, Start cooking, Edit, Add photo, Favorite, Delete |
| Drag down | Bottom sheets | Dismiss |
| Swipe left/right | Cook mode, photo viewer | Next/previous step or photo |
| Tap a time | Method step | Start/pause/clear that step's timer |

## Haptics

Every interaction names a feedback type; the app maps types to vibration
effects in one place, `haptics/Haptics.kt` (tuning in
`haptics/HapticPatterns.kt`). There is a Settings switch; when off,
nothing vibrates. The system Touch feedback setting is respected too, and
vibrations use touch usage so the system's touch intensity applies.

Each type has three tiers: a `VibrationEffect` composition of primitives
(API 30+, when the phone supports them), a predefined effect (API 29),
and a `View.performHapticFeedback` constant (API 26-28 or no usable
vibrator).

| Type | Used for | Composition / predefined / fallback |
|---|---|---|
| selection | Tag chips, scale presets and steppers, toggles, radios and the day picker, reordering (editor move up/down), picking a unit, adding a tag, choosing a scaling mode, using a detected time, a swipe row crossing its action point, cook-mode Next/Previous and step swipes, photo swipes, picking a hero thumbnail, setting the cover, a link conversion finishing, pausing/resuming a timer | `PRIMITIVE_CLICK` 0.8 / `EFFECT_CLICK` / `VIRTUAL_KEY` |
| light | The tabs, the Import (center) and + buttons, the empty state's Import button, unchecking, dismissing a sheet, removing a favorite, Start cooking, Cancel in the editor, adding or removing a row/section/step/tag, pasting rows, opening the scaling-mode pill, Convert / Paste link / Clear in link import, the Add photo tile, removing a photo, opening or closing the viewer, clearing a finished timer, Back up now | `PRIMITIVE_CLICK` 0.85 / `EFFECT_CLICK` / `VIRTUAL_KEY` |
| medium | Press-and-hold menu, starting a timer, an incoming shared link, the Restore backup confirm | `PRIMITIVE_CLICK` 1.0 / `EFFECT_HEAVY_CLICK` / `LONG_PRESS` |
| success | Saving a recipe, adding a favorite, cook mode's Finish, photos added, a completed backup, restore or connect | `CLICK` 0.9 / `EFFECT_CLICK` / `VIRTUAL_KEY` |
| warning | Deleting a recipe (including swipe-Delete), a timer finishing, the Disconnect confirm | `CLICK` 1.0 / `EFFECT_HEAVY_CLICK` / `LONG_PRESS` |
| error | A Save blocked by validation, a failed link import or bad link, every picked photo failing to read, a failed backup, restore or connect | `CLICK` 1.0 / `EFFECT_HEAVY_CLICK` / `LONG_PRESS` |

Every type is a single click: feedback never buzzes twice for one
action, and the types differ only in strength. User feedback shaped the
tuning: the Import and + buttons were too strong at the full long-press
buzz, then everyday feedback was too weak at 0.6, so buttons, tabs, the
Import and + buttons now sit at about 70% of the original Import button
(light, 0.85), selection just below (0.8), so chips, segmented controls and sheets never feel weaker than buttons. Going back (the back
button, the back swipe) is silent. An action that plays its own feedback
(e.g. favoriting from a sheet) turns the tap's feedback off, so the tap
and the result never both vibrate. Moving to the next step in
cook mode is selection, not success, so cooking through a recipe doesn't
buzz hard on every step. A finishing timer in the background uses its
notification channel's vibration pattern (0-400-200-400-200-600 ms), not
this table.

## Tokens

Dark (default) / light.

| Token | Dark | Light |
|---|---|---|
| canvas | `#0D0C0B` | `#F4F2EE` |
| surface | `#151311` | `#FFFFFF` |
| surface-2 | `#1C1A17` | `#EFECE7` |
| surface-3 | `#27241F` | `#E4E0D9` |
| ink | `#F7F2EB` | `#18140F` |
| body | `#D3CBBF` | `#3E382F` |
| mute | `#A39A8D` | `#655E53` |
| faint | `#857D72` | `#7A7267` |
| line | white-warm 8% | brown 9% |
| saffron | `#FFB547` | `#E3961A` |
| paprika | `#FF6B3D` | `#EC5427` |
| basil | `#57D39A` | `#1C9E66` |
| ube | `#B49CFF` | `#7A5AF0` |
| sumac | `#FF5C8D` | `#E0396F` |
| ocean | `#5BC4FF` | `#1C92D3` |
| danger | `#FF5A4E` | `#D9342B` |

- **Flame gradient:** `#FFB547` -> `#FF6B3D` (55%) -> `#FF5C8D`, 120 degrees;
  text on it is `#1C0F06`.
- **Tag colors:** a few tags have fixed colors (breakfast saffron, dinner
  and spicy paprika, vegetarian and salad basil, dessert/sweet/japanese/
  korean sumac, drinks/quick/gluten-free ocean, baking/italian ube);
  others take one of the six by a stable hash of the name. Tag chip: 15%
  tint fill, 30% tint border, text mixed toward ink for contrast.
- **Type:** Bricolage Grotesque (display: titles, large titles, recipe
  names; semi-condensed width, tight tracking), Geist (body and UI),
  Geist Mono (amounts, times, counts, eyebrows, timers, tabular digits).
- **Shape:** cards 20 dp, sheets 28 dp top corners, buttons 14-16 dp,
  chips and tags fully rounded, FAB 20 dp.
- **Edge to edge.** The app draws behind the status and navigation bars,
  so everything interactive keeps clear of them: pages and bars pad for
  the insets, and bottom sheets stop below the status bar (a tall sheet
  such as Import never puts its grabber, title or close button under it).
- **Surfaces:** layered by surface / surface-2 / surface-3, hairline
  borders, near-opaque bars (about 95%) for the top bar, tab bar and
  bottom bars, so text scrolling behind them stays readable (the app
  does not blur; a blur would be a refinement).
- **Illustrations:** recipes without a photo show a generated top-down
  plated illustration (seeded from the recipe so it is stable). The
  prototype paints these on a canvas; the app can paint them with a
  Compose `Canvas` or ship a simpler placeholder first. This is a
  nice-to-have, not a launch requirement.

## Copy

Active voice, sentence case, names for what people recognize ("Back up
now", "Start cooking"), errors that say how to fix them. No emoji as
markers.

## Deliberate departures from the prototype

Recorded so they aren't "fixed" back by accident. The prototype still
wins everywhere else.

Onboarding and updates
- The Settings update card is new too: it only opens the GitHub
  release page; the app never downloads or installs an APK itself.
- The first-run Welcome tour is new in the app; the prototype has no
  onboarding screen.

Gestures and motion
- The swipe-row selection tick fires on every crossing of the action
  point, in both directions (the prototype ticks once per gesture), and a
  left fling faster than 500 dp/s also opens a row.
- The `rise` entrance replays after a grid/list switch (as in the
  prototype) but not after a sort change; reorders animate item
  placement instead.

Editor
- The scaling mode is picked from a bottom sheet listing the three modes
  with an icon and explanation, instead of an HTML select; the step shows
  a pill with icon, label and chevron.
- Unit suggestions are a chip row under a focused unit field, instead of
  an HTML datalist.
- Validation is wider: besides the title, an amount that isn't a number,
  a row with an amount but no name, and minutes that aren't a number are
  flagged inline (the prototype silently dropped or zeroed them).
- A comma also adds a tag. Backspace on an empty tag field does not
  remove the last tag; use the chip's x.
- A short pasted line ending in ":" becomes a section heading.
- Reorder is up/down buttons only, no drag.

Timers and cook mode
- Start cooking uses the light haptic; cook mode's Next/Previous and
  swipes use selection; Finish uses success.
- A finishing timer plays the prototype's chime twice (about 2.2 s) on
  the alarm stream, so it is audible in silent mode like a kitchen timer.
- The finish notification channel is silent and vibrates; the chime is
  the app's own, so the Timer sound switch governs it.
- The ingredient matcher also matches an ingredient word in its own
  plural form ("flakes", "leaves").

Link import
- No "Demo links" row (prototype-only); a ghost "Paste link" button
  instead.
- The "Link shared to Recipai" banner is a card at the top of the link
  tab inside the import sheet, not an overlay with a 1.7 s progress bar;
  conversion starts at once.
- Steps 3 to 5 of the progress list happen instantly, so they are spaced
  about 320 ms apart for readability.
- Error actions are Paste text instead, Edit the page text, and Try
  again.
- A saffron note appears on a recipe read from page text rather than a
  recipe card.

Photos
- The detail hero shows a photo-count badge ("2 of 5" / "1 photo").
- The hero defaults to the cover photo (the prototype showed the first
  photo, a bug).
- Tapping the thumbnail already shown in the hero opens the viewer.
- Spinner tiles show while photos are copied in.
- The editor shows photo read failures inline under the strip.
- The press-and-hold preview sheet offers Add photo too (the prototype
  had it only in the More menu).

Backup
- The time of day is picked from an action sheet, not a native select.
- The Drive folder is "Recipai backups" (sentence case), not "Recipai
  Backups".
- The explainer's demo-account note is replaced by "Google's own window
  opens so you can choose the account. Recipai asks only for access to
  the files it creates."
- The restore title lower-cases only today, yesterday and tomorrow.
- The account row has a "Sign in again" state and banner, which the
  prototype lacks.

