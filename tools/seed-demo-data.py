#!/usr/bin/env python3
# Copyright (C) 2026 podxboq
#
# This file is part of Yahora.
#
# Yahora is free software: you can redistribute it and/or modify it under the
# terms of the GNU General Public License as published by the Free Software
# Foundation, either version 3 of the License, or (at your option) any later
# version.
#
# This program is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
# FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License along with
# this program. If not, see <https://www.gnu.org/licenses/>.
"""Fills a debug install with a plausible history, for screenshots and for
trying the app out by hand.

The detail screen groups entries into months, then days. Tapping chips this
afternoon therefore produces a tree of one month holding one day — nothing the
screen was built to show. And entry timestamps are not editable by design, so
there is no way to age them from inside the app. Hence this: it writes straight
into the app's database.

The data is generated from a fixed seed, so running it twice produces exactly
the same history and a screenshot can be retaken weeks later.

Requirements: adb, one connected device, and a *debug* build installed —
`run-as` is what grants access to the app's private data directory, and it
refuses on a release build. The app is stopped before writing and restarted
afterwards, because Room keeps its own connection and its Flows never hear
about a change made behind its back.

Usage:

    tools/seed-demo-data.py --locale es --clear     # seed a Spanish device
    tools/seed-demo-data.py --dry-run               # print the SQL, touch nothing
"""

import argparse
import random
import subprocess
import sys
from datetime import datetime, timedelta

PACKAGE = "com.podxboq.yahora"
DATABASE = "databases/yahora.db"
ACTIVITY = f"{PACKAGE}/.MainActivity"

# Chosen so a run is reproducible: same seed, same history, same screenshot.
SEED = 20260819

# How often a tag happens, and at what times of day. The hour windows are what
# keep the tree believable — coffee in the morning and mid-afternoon reads as a
# real habit, coffee at 03:41 reads as generated data.
PROFILES = {
    "several_daily": {"chance": 0.95, "count": (2, 4), "windows": [(7, 9), (11, 13), (16, 18)]},
    "twice_daily": {"chance": 0.9, "count": (2, 2), "windows": [(8, 10), (19, 21)]},
    "most_days": {"chance": 0.8, "count": (1, 1), "windows": [(7, 9)]},
    "thrice_weekly": {"chance": 0.4, "count": (1, 1), "windows": [(18, 21)]},
    "weekly": {"chance": 0.15, "count": (1, 1), "windows": [(10, 13)]},
    "occasional": {"chance": 0.3, "count": (1, 2), "windows": [(16, 19)]},
    "rare": {"chance": 0.05, "count": (1, 1), "windows": [(9, 22)]},
    # Tags with no history at all: they are what makes "delete tag" appear in
    # the context menu, and the pair of menu entries is worth a screenshot.
    "none": None,
}

# (name, is_favorite, profile). Neither favourite is first in the alphabet, so a
# screenshot shows what the spec promises: the star changes the emphasis, never
# the order.
TAGS = {
    "en": [
        ("Coffee", True, "several_daily"),
        ("Cold shower", False, "most_days"),
        ("Gym", False, "thrice_weekly"),
        ("Laundry", False, "weekly"),
        ("Migraine", False, "rare"),
        ("Nap", True, "occasional"),
        ("Reading", False, "none"),
        ("Stretch", False, "none"),
        ("Tea", False, "occasional"),
        ("Walk the dog", False, "twice_daily"),
    ],
    "es": [
        ("Café", True, "several_daily"),
        ("Ducha fría", False, "most_days"),
        ("Estiramientos", False, "none"),
        ("Gimnasio", False, "thrice_weekly"),
        ("Lavadora", False, "weekly"),
        ("Lectura", False, "none"),
        ("Migraña", False, "rare"),
        ("Paseo del perro", False, "twice_daily"),
        ("Siesta", True, "occasional"),
        ("Té", False, "occasional"),
    ],
}


def normalize(name):
    """The Python side of TagName.normalize — trim, then lowercase.

    The unique index is on name_key, so a key that disagrees with the Kotlin one
    would let a duplicate through the very check the schema exists to enforce.
    """
    return name.strip().lower()


def quote(text):
    return "'" + text.replace("'", "''") + "'"


def timestamps(profile, weeks, rng):
    """Epoch milliseconds for one tag, spread over the last `weeks` weeks.

    Times come from the machine's own time zone, which is the one the app groups
    by — seeding a device set to a different zone shifts entries across day
    boundaries.
    """
    shape = PROFILES[profile]
    if shape is None:  # a tag that has never been tapped
        return []

    # Yesterday backwards: today is left alone so the newest entries are not all
    # in the future of whatever hour the screenshot is taken at.
    end = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
    moments = []

    for day in range(1, weeks * 7 + 1):
        date = end - timedelta(days=day)
        if rng.random() > shape["chance"]:
            continue
        for _ in range(rng.randint(*shape["count"])):
            window = rng.choice(shape["windows"])
            moment = date.replace(
                hour=rng.randint(window[0], window[1]),
                minute=rng.randrange(60),
                second=rng.randrange(60),
            )
            moments.append(int(moment.timestamp() * 1000))

    return sorted(moments)


def sql(locale, weeks, clear):
    rng = random.Random(SEED)
    lines = [
        "PRAGMA foreign_keys = ON;",
        "BEGIN TRANSACTION;",
    ]

    if clear:
        # Entries first: the foreign key is RESTRICT, so the other order fails —
        # which is the invariant doing its job, not a bug in this script.
        lines += ["DELETE FROM entries;", "DELETE FROM tags;"]

    for name, favorite, profile in TAGS[locale]:
        key = normalize(name)
        lines.append(
            "INSERT INTO tags (name, is_favorite, name_key) VALUES "
            f"({quote(name)}, {1 if favorite else 0}, {quote(key)});"
        )

        moments = timestamps(profile, weeks, rng)
        if not moments:
            continue

        # The tag id is looked up rather than assumed: without --clear the table
        # already holds rows and AUTOINCREMENT decides the numbering.
        values = ",\n".join(
            f"  ((SELECT id FROM tags WHERE name_key = {quote(key)}), {moment})"
            for moment in moments
        )
        lines.append(f"INSERT INTO entries (tag_id, timestamp) VALUES\n{values};")

    lines.append("COMMIT;")
    return "\n".join(lines) + "\n"


def adb(args, device, **kwargs):
    command = ["adb"] + (["-s", device] if device else []) + args
    return subprocess.run(command, capture_output=True, text=True, **kwargs)


def check_device(device):
    listed = adb(["devices"], device).stdout.strip().splitlines()[1:]
    attached = [line for line in listed if line.strip() and "device" in line.split()]
    if not attached:
        return "no device attached — check the cable, or `adb connect` over Wi-Fi"
    if len(attached) > 1 and not device:
        return f"several devices attached, pass --serial:\n" + "\n".join(attached)

    if adb(["shell", "pm", "path", PACKAGE], device).returncode != 0:
        return f"{PACKAGE} is not installed"

    probe = adb(["shell", "run-as", PACKAGE, "ls", DATABASE], device)
    if probe.returncode != 0:
        detail = (probe.stderr or probe.stdout).strip()
        if "not debuggable" in detail:
            return "the installed build is not debuggable — install the debug variant"
        return (
            f"cannot reach {DATABASE}: {detail}\n"
            "If the database does not exist yet, open the app once and retry."
        )
    return None


def confirm_clear(device):
    """Asks before --clear throws away a history that is already on the device.

    The app itself refuses to delete a tag that holds entries, and makes
    deleting a single one a deliberate act. A helper that wipes the lot without
    a word would undo that care from the outside — and the device being seeded
    is usually the maintainer's own phone.
    """
    counted = adb(
        ["shell", "run-as", PACKAGE, "sqlite3", DATABASE, "SELECT count(*) FROM entries;"],
        device,
    )
    existing = counted.stdout.strip()
    if not existing.isdigit() or existing == "0":
        return True

    answer = input(f"--clear will discard {existing} existing entries. Continue? [y/N] ")
    return answer.strip().lower() in ("y", "yes")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--locale", choices=sorted(TAGS), default="en",
                        help="language of the tag names (default: en)")
    parser.add_argument("--weeks", type=int, default=10,
                        help="how far back the history reaches (default: 10)")
    parser.add_argument("--clear", action="store_true",
                        help="wipe existing tags and entries first")
    parser.add_argument("--serial", help="adb serial, when several devices are attached")
    parser.add_argument("--yes", action="store_true",
                        help="do not ask before --clear discards an existing history")
    parser.add_argument("--dry-run", action="store_true",
                        help="print the SQL instead of running it")
    args = parser.parse_args()

    if args.weeks < 1:
        parser.error("--weeks must be at least 1")

    statements = sql(args.locale, args.weeks, args.clear)

    if args.dry_run:
        sys.stdout.write(statements)
        return 0

    problem = check_device(args.serial)
    if problem:
        print(problem, file=sys.stderr)
        return 1

    if args.clear and not args.yes and not confirm_clear(args.serial):
        print("nothing written", file=sys.stderr)
        return 1

    adb(["shell", "am", "force-stop", PACKAGE], args.serial)

    written = adb(["shell", "run-as", PACKAGE, "sqlite3", DATABASE], args.serial,
                  input=statements)
    if written.returncode != 0:
        detail = (written.stderr or written.stdout).strip()
        print(f"seeding failed: {detail}", file=sys.stderr)
        if "not found" in detail:
            print("The device has no sqlite3 binary (it arrived in Android 10).",
                  file=sys.stderr)
        return 1

    adb(["shell", "am", "start", "-n", ACTIVITY], args.serial)

    tags = len(TAGS[args.locale])
    entries = statements.count("(SELECT id FROM tags")
    print(f"seeded {tags} tags and {entries} entries in {args.locale}, "
          f"over the last {args.weeks} weeks")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
