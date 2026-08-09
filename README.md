# Yahora

A minimalist event logger for Android. One tap on a tag records the date and
time of a recurring event — nothing else. Tags are laid out as a cloud of
uniform chips, and logging something takes a single touch: no intermediate
screen, no confirmation dialog, no extra fields.

> **Experimental concept.** Yahora is at an early design stage. The repository
> currently contains the specification and project scaffolding only — there is
> no working application yet, no release, and no installable build. Everything
> below describes the intended design and may change.

## Concept

Most tracking apps grow into note-taking tools: nested categories, custom
fields, statistics, reminders. Yahora deliberately does not. A tag has a name
and a favorite flag; an entry has a timestamp. That is the entire model.

The design goal is that recording an event costs one tap and zero attention.

## Planned features

- **Tag cloud** — uniform chips in a grid, sorted alphabetically. Favorites are
  visually highlighted without changing their position in the ordering.
- **One-tap logging** — a short tap on a tag records the current timestamp
  immediately.
- **Context menu** — a long press offers renaming, toggling favorite, and
  browsing that tag's entries. Actions that do not apply are hidden rather than
  shown disabled.
- **Editable history** — the date and time of any entry can be corrected after
  the fact, and individual entries can be deleted.
- **Safe by construction** — a tag that has entries cannot be deleted, so
  history is never lost by accident.

## Out of scope for now

Data export (CSV or otherwise) is planned as a future extension — the schema is
designed so it can be added without a migration. Nested categories, notes, and
additional fields on an entry are explicitly not part of the design.

## Tech stack

Kotlin, Jetpack Compose with Material 3, and Room for local storage. The app is
fully offline: no network access, no accounts, no telemetry.

## Building

Once the Gradle project is in place:

```sh
./gradlew assembleDebug   # build a debug APK
./gradlew installDebug    # build and install on a connected device
./gradlew test            # run unit tests
```

Requires the Android SDK and a JDK compatible with the current Android Gradle
Plugin (a Java toolchain is declared in the build, so Gradle can provision one
automatically).

## Distribution

Free software from the start. Distribution is planned through Google Play,
F-Droid and GitHub, with no freemium model, no ads and no in-app purchases.

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
