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
  Settings). The tab bar slides away while a page is pushed. Back is the
  top-left button or the system back gesture / swipe from the left edge.
- Editor: opens as a modal page sliding up from the bottom
  (Cancel / Save in its top bar). Cancel with unsaved changes asks first.
- Import: a large bottom sheet (Paste text / From a link, live preview of
  the parsed recipe, Review and Save buttons).
- Recipes and Favorites show a floating **+** button (new recipe) at the
  bottom right, above the tab bar. It hides on pushed pages and when
  other tabs are active.

## Screens

**Recipes** -- large title that collapses into a translucent top bar on
scroll; search field; example-recipes banner (dismissible, only while
example recipes exist); tag chips with counts (plus an "Under 30 min"
chip); count, sort (Recent / A to Z / Quickest) and grid/list toggle;
the recipes. Grid cards: square photo, time badge, heart button, title,
servings. List rows: thumbnail, title, time and servings, tag dots.
Empty states for no recipes / no matches.

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
mode; reorder; remove; detected-time chip "Found 12 min, use it"),
Notes. Validation: a title is required.

**Settings** -- Backup (Google Drive row with status, Back up now),
Appearance (theme), Cooking (Haptic feedback, Keep screen on in cook
mode, Timer sound), Library (counts, Restore example recipes), About.

**Backup** (pushed from Settings) -- not connected: explainer (what is
saved, where, privacy) and **Connect Google account**. Connected:
account row with Disconnect; last backup, size and **Back up now** with a
progress bar; Automatic backups (Daily, Weekly with weekday picker,
Only when I tap) with time of day and the next run; options (Include
photos, Wi-Fi only); the six most recent backups with Restore. Restoring
first takes a "Before restore" backup so it can be undone.

## Gestures

| Gesture | Where | Result |
|---|---|---|
| Swipe from the left edge | Any pushed page | Back (page follows the finger) |
| Swipe a row left | List layout | Reveals Favorite and Delete; Delete has Undo |
| Press and hold | Recipe card or row | Preview sheet: Open, Start cooking, Favorite, Edit, Delete |
| Drag down | Bottom sheets | Dismiss |
| Swipe left/right | Cook mode, photo viewer | Next/previous step or photo |
| Tap a time | Method step | Start/pause/clear that step's timer |

## Haptics

Every interaction names a feedback type; the app maps types to Android
constants in one place (`haptics/Haptics.kt`). There is a Settings
switch; when off, nothing vibrates. Respect the system touch-feedback
setting too.

| Type | Used for | Android constant (fallback) |
|---|---|---|
| selection | Tabs, tag chips, scale presets and steppers, toggles, reordering, a swipe passing its action point, step/photo swipes | `SEGMENT_TICK` / `CLOCK_TICK` (API 34+/earlier) |
| light | Unchecking, dismissing a sheet, back swipe, removing a favorite | `KEYBOARD_TAP` |
| medium | Press-and-hold menu, starting a timer, an incoming shared link | `LONG_PRESS` |
| success | Saving a recipe, adding a favorite, finishing a step, a completed backup | `CONFIRM` (API 30+; else `LONG_PRESS`) |
| warning | Deleting a recipe, a timer finishing | `REJECT` (API 30+; else `LONG_PRESS`) |
| error | Reserved: failed save/backup/import | `REJECT` (API 30+; else `LONG_PRESS`) |

A finishing timer in the background uses its notification channel's
vibration pattern, not this table.

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
- **Surfaces:** layered by surface / surface-2 / surface-3, hairline
  borders, translucent blurred bars for the top bar, tab bar and bottom
  bars (use a solid fallback where blur is unavailable).
- **Illustrations:** recipes without a photo show a generated top-down
  plated illustration (seeded from the recipe so it is stable). The
  prototype paints these on a canvas; the app can paint them with a
  Compose `Canvas` or ship a simpler placeholder first. This is a
  nice-to-have, not a launch requirement.

## Copy

Active voice, sentence case, names for what people recognize ("Back up
now", "Start cooking"), errors that say how to fix them. No emoji as
markers.
