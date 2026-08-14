# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Yahora is a minimalist Android event logger: one tap on a tag chip records a
timestamp. The repository currently holds **no source code** — only `LICENSE`,
`.gitignore` and the local spec. The first substantial task is scaffolding the
Gradle/Android project itself, so the commands below only work once `gradlew`
exists.

## Product spec

`personal_docs/init.md` is the functional source of truth, but it is listed in
`.gitignore` — it is a local document and may be absent in other clones. This
file restates every invariant that matters, so do not assume the spec is
readable.

## Stack

Kotlin, Jetpack Compose with Material 3, Room for persistence, ViewModel + Flow.
Single module `:app`, base package `com.podxboq.yahora`. Fully offline — no
network layer. Versions are pinned in `gradle/libs.versions.toml`; add
dependencies there, never inline in `app/build.gradle.kts`.

Four constraints that are easy to trip over and cost a broken build:

- **AGP 9 ships built-in Kotlin support.** Applying `org.jetbrains.kotlin.android`
  makes the build fail outright. Only `com.android.application`,
  `org.jetbrains.kotlin.plugin.compose` and `com.google.devtools.ksp` are applied.
- **KSP pins the Kotlin version.** Room needs KSP, and the latest stable KSP
  tracks Kotlin 2.3.x — so Kotlin stays at 2.3.21 even though 2.4.x exists.
  Lint's `NewerVersionAvailable` warning about this is expected; do not "fix" it.
- **Robolectric lags the platform.** It supports up to SDK 36 while the app
  targets 37, so `app/src/test/resources/robolectric.properties` pins `sdk=36`.
  Without it every JVM test fails at startup.
- **`java.time` needs desugaring.** It is API 26 and `minSdk` is 24, so
  `isCoreLibraryDesugaringEnabled` plus `coreLibraryDesugaring(...)` are what
  make the detail screen's date grouping compile. Removing either breaks the
  build; raising `minSdk` instead is not the trade this project wants.

## Commands

```sh
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected device
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (device/emulator required)
./gradlew lint                   # Android Lint

# single test or class — note the task: --tests is not accepted by `test`
./gradlew testDebugUnitTest --tests "com.podxboq.yahora.data.TagRepositoryTest"
```

Always use the Gradle wrapper, never a system-wide `gradle`.

The build does not depend on whichever JDK the machine happens to have.
`gradle/gradle-daemon-jvm.properties` pins the daemon to Java 21 and
`app/build.gradle.kts` declares `jvmToolchain(21)`; Gradle downloads a matching
JDK if needed via the foojay resolver in `settings.gradle.kts`. So `./gradlew`
works even when `java -version` reports something much newer. Do not add
`org.gradle.java.home` — that would hardcode one machine's layout.

Machine-specific paths and installed SDK versions belong in `CLAUDE.local.md`
(untracked), not here.

## Architecture

Two layers under `com.podxboq.yahora`:

- `data` — Room entities, DAOs, database. DAOs expose `Flow`.
- `ui` — Compose screens and their ViewModels, which turn those flows into
  `StateFlow` for the composables.

Room is the single source of truth; no in-memory caches parallel to it.

Two conventions worth following rather than reinventing:

- **One-shot user messages** (snackbars) travel over a `Channel` exposed as a
  `Flow`, never as a field of the UI state — state would replay them on every
  recomposition and configuration change. See `TagCloudMessage`. The screen
  collects them with `collectLatest`, so a second tap replaces the first
  announcement instead of queueing behind it: an offer to undo must always be
  about the tap that just happened.
- **The clock is injected** (`EntryRepository(dao) { now }`), so tests pin
  timestamps instead of asserting against wall time. Never call
  `System.currentTimeMillis()` directly inside a repository or ViewModel. The
  time **zone** is injected for the same reason (`TagDetailViewModel(…, zone)`):
  grouping entries by month and day is a calendar question, not a UTC one.
- **Navigation is a single saved tag id** in `YahoraApp`, not a library. Two
  destinations — the cloud and one tag's detail — are the whole back stack.

Schema changes need a `Migration` in `YahoraDatabase` plus a test; copy the SQL
verbatim from the exported schema under `app/schemas`, which is what Room
validates against. `YahoraDatabaseMigrationTest` shows the pattern.

## Domain model and invariants

`Tag(id, name, isFavorite)` and `Entry(id, tagId, timestamp)`.

- `tagId` is a non-null, strict foreign key: **orphan entries can never exist**.
- **A tag with entries cannot be deleted.** Use `onDelete = RESTRICT` on the
  foreign key — never `CASCADE` — and hide the delete action in the UI instead
  of showing it disabled.
- Tags are sorted alphabetically by `name`. The favorite flag only changes
  visual emphasis; it **never** reorders the cloud.
- Tag names are capped at `TagName.MAX_LENGTH` (128), measured in **code
  points**, not `String.length` — the latter counts UTF-16 units and would
  reject an emoji-laden name at half the visible length. The cap is enforced in
  the repository and the dialog truncates as you type.
- Alphabetical means **`ORDER BY name COLLATE LOCALIZED`**, not byte order.
  Stock SQLite only ships `BINARY`, `NOCASE` and `RTRIM`, but Android adds the
  ICU-backed `LOCALIZED` and `UNICODE` collations, and they work under
  Robolectric too. Sorting by the normalized key or by plain `name` would strand
  "Árbol" and "ñu" after "Zumo".

## Interaction rules

These are easy to get wrong; the spec is deliberate about each one.

- **Short tap** on a chip creates an `Entry` with the current timestamp
  immediately — no intermediate screen, no confirmation dialog. It fires a
  haptic tick: nothing moves on screen, so that tick is the only confirmation
  a user gets without looking.
- **A tap is taken back from its own snackbar**, which offers "undo" for as long
  as it is up. Deliberately not a double tap: two taps must keep logging two
  entries, and asking Compose to tell a double tap from a single one delays
  *every* tap by the double-tap timeout, which would cost the immediacy the
  whole app is built on. The snackbar carries the new entry's id, so undoing
  never has to guess which entry was the last one.
- **Motion is purposeful, never decorative** — see "Animation criteria" below
  for what is allowed and what is over-engineering.
- Favorites are marked with a **star**, never by color alone, so the highlight
  survives color blindness and greyscale. The icon carries a content
  description.
- The cloud uses a flowing layout where **each chip is as wide as its name**.
  The spec's "uniform size" refers to typography — every tag uses the same font
  size and weight, with no per-tag styling — not to a uniform chip width.
- **Long press** opens a context menu: rename, toggle favorite, view entries,
  and delete tag (only when it has no entries). Inapplicable actions are
  **omitted**, not greyed out. All four are built. Each item names what it will
  do — "Mark as favorite" or "Remove from favorites" — not what the tag is.
- **View entries and delete are the two halves of one condition**: whether the
  tag has history. With entries there is something to show and nothing that may
  be deleted; without them, the reverse. Exactly one of the two is ever in the
  menu. That condition comes from `SELECT DISTINCT tag_id FROM entries`,
  observed alongside the tags themselves, so a tap swaps the pair at once. The
  UI hiding delete is a courtesy; the foreign key is the guarantee, and the
  repository still handles the refusal that arrives when an entry lands while
  the menu is open.
- **Renaming** reuses the creation dialog — same field, same 128 code point cap,
  same errors — prefilled with the current name. A tag never collides with
  itself, so recapitalizing "coffee" to "Coffee" must go through; the duplicate
  check compares ids, not just keys. The renamed tag keeps its id, its favorite
  flag and its entries, and simply takes its new alphabetical place.
- **Viewing entries** opens the tag's own screen: its name as the title and its
  history as a collapsible tree — months, then days, then the entries
  themselves at `HH:mm:ss`. Branches state how many children they hold (a month
  counts days, a day counts entries); an entry states its own time instead,
  since it has nothing to count. The tree opens fully collapsed. Row labels are
  formatted from patterns in `strings.xml`, so a new locale never means
  touching Kotlin.
- Deleting an individual entry **does** require a confirmation dialog. Deleting
  a tag (only possible when empty) does **not** — no history is lost. The entry
  is reached by **long pressing its row** in the tree, the same gesture that
  opens a chip's menu; a swipe would promise an immediacy the mandatory
  confirmation then takes back. The short tap on an entry does nothing.
- **Entry timestamps are not editable.** This is a decision, not a gap. What the
  app measures is *how many times* something happened; the timestamp is how
  those occurrences are grouped and ordered, not a figure the user is meant to
  curate. An editor would be the shortest path to rewriting the record, and it
  would buy accuracy in a dimension that is not the point. Do not add editing
  back without the owner asking for it.
- **Deleting an entry is for slips of the finger** — a chip tapped twice, the
  chip next to the intended one — and those are noticed within seconds. So the
  action is offered for `DELETE_WINDOW` after the entry was logged and then
  withdrawn: a long press on an older entry opens nothing. The snackbar's undo
  covers the first few seconds; this covers noticing on the way to pocketing the
  phone. Because entries are never edited, `timestamp` is also the moment of
  creation, so the window needs no extra column to measure from.

## Animation criteria

Animation must be subtle and always serve a clear purpose — state feedback, or
orienting the user through a transition — never decoration for its own sake.

- Immediate feedback when a tag chip is tapped (a slight scale or color change
  as the entry is recorded, for instance).
- Smooth transitions as a tag's context menu opens and closes.
- Use Jetpack Compose's own animation APIs exclusively: `AnimatedVisibility`,
  `animateFloatAsState`, and `SharedTransitionLayout` for transitions between
  screens.
- Avoid over-engineering: a native C++ animation engine — or any equivalent
  low-level machinery of the kind apps like Telegram build — is out of scope.

## Development workflow: TDD

**This project is built test-first.** Do not write production code before a
failing test asks for it. The cycle is red → green → refactor: write a test that
fails for the right reason, add the minimum implementation that makes it pass,
then clean up with the tests as a safety net.

Practical consequences when working here:

- Start any feature or bug fix by naming the behaviour as a test. If a task
  seems too vague to test, it is too vague to implement — clarify it first.
- **Every invariant in the two sections above must have a test.** They are the
  executable specification: deleting a tag that has entries must fail, sorting
  must stay alphabetical regardless of favorites, a short tap must produce
  exactly one entry. An invariant with no test is unfinished work.
- Never weaken a test to make it pass. If a test blocks a change, either the
  change is wrong or the specification changed — decide which, explicitly.
- A bug fix starts with a regression test that reproduces the bug.

Where each layer is tested:

- **DAOs and invariants** — JVM unit tests against an in-memory Room database
  (`Room.inMemoryDatabaseBuilder`). This is where the foreign-key and deletion
  rules are pinned down. Foreign keys must be explicitly enabled, or `RESTRICT`
  will not be enforced and the test will pass for the wrong reason.
- **ViewModels** — unit tests over the exposed `StateFlow`. Two things are
  required for determinism, and omitting either produces tests that fail or
  flake for reasons unrelated to the code: pass the dispatcher to `runTest` so
  it shares the scheduler with `Dispatchers.setMain`, and route Room's own
  threads through it with `setQueryExecutor`/`setTransactionExecutor`. See
  `TagCloudViewModelTest`.
- **Interaction rules** — Compose UI tests, which run **on the JVM** under
  Robolectric (in `src/test`, not `src/androidTest`), so they stay in the fast
  suite. Screens are split into a stateless `…Content` composable taking a
  state object plus a callbacks data class, which is what makes this cheap; see
  `TagCloudScreen.kt` and `TagCloudContentTest`.

Keep the fast JVM suite (`./gradlew test`) as the default feedback loop; reserve
instrumented tests for what genuinely needs a device.

## Conventions

- **The codebase is English-only by design** — this is an open, international
  project. Identifiers, comments, commit messages and documentation in English,
  with no exceptions. The only Spanish lives in the localized resource files.
- User-facing text always goes through string resources — never hardcode a
  literal in a composable. `res/values/strings.xml` is the English default and
  fallback for untranslated locales; Spanish lives in `res/values-es/strings.xml`.
  Adding a locale must never require touching Kotlin code.
- `applicationId` is `com.podxboq.yahora`. Licensed GPL-3.0; add the license
  header to new source files.
- Out of scope for this phase: data export (CSV), nested categories, extra
  fields on `Entry`. Keep the schema such that export can be added later
  without a migration.
- App icon: adaptive icon with separate background/foreground layers, plus a
  monochrome variant for Material You themed icons. The drawing is a tally of
  five — three uprights and two diagonals — with the app's initial picked out
  inside it rather than drawn on top: the lower half of the middle upright is
  the Y's stem, the upper half of each diagonal a branch. Four things about it
  are decisions, not accidents, and each is easy to "fix" into a regression:
  - **The diagonals run all the way across.** A branch that dies on the mark it
    crosses meets it at an open angle and the same weight; the eye joins them
    into one down-up-down stroke and the icon reads as an M. Running through to
    the opposite corner leaves nothing to fuse.
  - **The Y is painted last**, after the diagonals' tails. Painted before them,
    its crossing vertex takes the colour of the marks and the letter breaks at
    exactly the point that identifies it.
  - **The two figures differ in luminance, not merely in hue.** The first
    attempt paired white marks with an amber Y at 1.44:1, which collapses into
    a single stroke in greyscale and for several kinds of colour blindness.
    Keep the ratio above 2:1. Same reasoning as the favourite star: never
    colour alone.
  - **The monochrome layer deliberately draws less** — the Y alone. Tinted flat,
    the whole tally reads as neither a count nor a letter, and a flat tint is
    exactly what that layer gets. The two layers are not meant to match.

  Everything stays inside the 66dp safe circle of the 108dp canvas, *including*
  the round caps, which extend each stroke by half its width past its endpoint.
  Measuring the path endpoints alone is the classic way to ship an icon whose
  tips get clipped by the launcher mask.
