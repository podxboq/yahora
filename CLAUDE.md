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

Three constraints that are easy to trip over and cost a broken build:

- **AGP 9 ships built-in Kotlin support.** Applying `org.jetbrains.kotlin.android`
  makes the build fail outright. Only `com.android.application`,
  `org.jetbrains.kotlin.plugin.compose` and `com.google.devtools.ksp` are applied.
- **KSP pins the Kotlin version.** Room needs KSP, and the latest stable KSP
  tracks Kotlin 2.3.x — so Kotlin stays at 2.3.21 even though 2.4.x exists.
  Lint's `NewerVersionAvailable` warning about this is expected; do not "fix" it.
- **Robolectric lags the platform.** It supports up to SDK 36 while the app
  targets 37, so `app/src/test/resources/robolectric.properties` pins `sdk=36`.
  Without it every JVM test fails at startup.

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

- **One-shot user messages** (toasts) travel over a `Channel` exposed as a
  `Flow`, never as a field of the UI state — state would replay them on every
  recomposition and configuration change. See `TagCloudMessage`.
- **The clock is injected** (`EntryRepository(dao) { now }`), so tests pin
  timestamps instead of asserting against wall time. Never call
  `System.currentTimeMillis()` directly inside a repository or ViewModel.

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
- Alphabetical means **`ORDER BY name COLLATE LOCALIZED`**, not byte order.
  Stock SQLite only ships `BINARY`, `NOCASE` and `RTRIM`, but Android adds the
  ICU-backed `LOCALIZED` and `UNICODE` collations, and they work under
  Robolectric too. Sorting by the normalized key or by plain `name` would strand
  "Árbol" and "ñu" after "Zumo".

## Interaction rules

These are easy to get wrong; the spec is deliberate about each one.

- **Short tap** on a chip creates an `Entry` with the current timestamp
  immediately — no intermediate screen, no confirmation dialog.
- **Long press** opens a context menu: rename, toggle favorite, view entries,
  and delete tag (only when it has no entries). Inapplicable actions are
  **omitted**, not greyed out.
- Deleting an individual entry **does** require a confirmation dialog. Deleting
  a tag (only possible when empty) does **not** — no history is lost.
- Entry timestamps are editable after the fact.

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
  monochrome variant for Material You themed icons.
