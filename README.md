# Yahora

A minimalist event logger for Android. One tap on a tag records the date and
time of a recurring event — nothing else. Tags are laid out as a cloud of
uniform chips, and logging something takes a single touch: no intermediate
screen, no confirmation dialog, no extra fields.

> **Early days.** Yahora works and is being prepared for its first release
> (0.1.0), but it is still young: the scope below is what the app does today,
> and it may still shift.

## Concept

Most tracking apps grow into note-taking tools: nested categories, custom
fields, statistics, reminders. Yahora deliberately does not. A tag has a name
and a favorite flag; an entry has a timestamp. That is the entire model.

The design goal is that recording an event costs one tap and zero attention.

## Features

- **Tag cloud** — chips flowing across the screen, each as wide as its name and
  all sharing one type size, sorted alphabetically. Favorites are marked with a
  star without changing their position in the ordering.
- **One-tap logging** — a short tap on a tag records the current timestamp
  immediately, with a haptic tick as the confirmation, and offers to undo it
  from its own snackbar.
- **Context menu** — a long press offers renaming, toggling favorite, and
  browsing that tag's entries. Actions that do not apply are hidden rather than
  shown disabled.
- **History as a tree** — months, then days, then the entries themselves at
  `HH:mm:ss`, each branch stating how many children it holds.
- **Safe by construction** — a tag that has entries cannot be deleted, so
  history is never lost by accident. An entry can be removed shortly after it
  was logged, which is the window in which a slip of the finger is noticed;
  timestamps are never edited.

## Out of scope for now

Data export (CSV or otherwise) is planned as a future extension — the schema is
designed so it can be added without a migration. Nested categories, notes, and
additional fields on an entry are explicitly not part of the design.

## Tech stack

Kotlin, Jetpack Compose with Material 3, and Room for local storage. The app is
fully offline: no network access, no accounts, no telemetry. The
[privacy policy](https://podxboq.github.io/yahora/privacy.html)
([español](https://podxboq.github.io/yahora/privacidad.html)) states that in
full.

## Building

Once the Gradle project is in place:

```sh
./gradlew assembleDebug   # build a debug APK
./gradlew installDebug    # build and install on a connected device
./gradlew test            # run unit tests
```

Requires the Android SDK and a JDK 21, which the build asks for by declaring a
Java toolchain. Gradle will use any JDK 21 it can find but will not download
one: the foojay toolchain resolver is left out on purpose, because fetching a
JDK during the build is incompatible with how F-Droid builds this app.

## Distribution

Free software from the start, with no freemium model, no ads and no in-app
purchases. F-Droid is the first channel; GitHub releases and Google Play are
meant to follow.

The store listing lives in this repository, under
`fastlane/metadata/android/<locale>/`, which is where F-Droid reads it from —
description, changelog per version code, icon and screenshots. Its limits are
checked by `FdroidMetadataTest`, so an over-long description fails the build
rather than the merge request. The listing icon is not drawn by hand:
`tools/render-listing-icon.py` rasterises it from the launcher icon's own
drawables.

Screenshots come from a real device carrying a history that was written into a
debug build's database: the tree groups entries by month and day, and timestamps
are never editable, so an install tapped into existence this afternoon has
nothing to show. `tools/seed-demo-data.py` is the quickest way to get one — a
fixed-seed history, so the same run weeks later still matches the screenshots it
produced. They are then cropped with `tools/crop-screenshot.py`, which takes the
phone's status and navigation bars off.

The project pages at <https://podxboq.github.io/yahora/> are served by GitHub
Pages from `docs/` in this repository, and exist because Google Play asks for
the privacy policy as a live URL rather than a file. Editing the policy means
editing `docs/privacy.html` and its Spanish counterpart, never a copy kept
somewhere else.

`fdroid/com.podxboq.yahora.yml` is the build recipe F-Droid needs. It is kept
here for review, but F-Droid reads its own copy in
[fdroiddata](https://gitlab.com/fdroid/fdroiddata), where each release arrives
as a merge request. A release is a signed, annotated git tag named `v<version
name>` — `v0.1.0` for `versionName = "0.1.0"` — and `versionCode` only ever
goes up.

## Contributing

The project is still taking shape, so the scope may shift under you. Issues and
discussion about the concept are welcome; please open an issue before starting
substantial work.

The codebase is English-only: identifiers, comments, commit messages and
documentation. User-facing text lives in string resources, with English as the
default locale — translations are welcome and never require code changes.

Yahora is developed test-first (TDD). Patches are expected to arrive with the
tests that describe the behaviour they add or the bug they fix.

## Development tooling

This project uses [Claude Code](https://claude.com/claude-code) as an assistive
tool during development. All AI-assisted output is reviewed, tested and approved
by the maintainer before it is committed. Responsibility for the contents of
this repository rests with the maintainer.

## License

Yahora is free software, licensed under the **GNU General Public License v3.0**.
See [LICENSE](LICENSE) for the full text.

Copyright (C) 2026 podxboq

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details.
