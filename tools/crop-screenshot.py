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
"""Crops the system bars off device screenshots, for the F-Droid listing.

A screenshot straight off a phone carries that phone's status bar and navigation
bar: a clock, a battery, someone's signal strength and unread icons. None of it
is the app, all of it dates the listing, and it differs between the two locales'
screenshots even when the app is showing exactly the same screen.

The crop is not a guess. Android knows how tall its own bars are, and reports
them as the display's non-decor insets, so this asks the connected device rather
than eyeballing where the app begins. Pass --top and --bottom when the device is
gone and only the images are left.

Output is PNG even when the input was JPEG: the crop has to re-encode, and a
second round of JPEG would add artefacts to a screen that is mostly flat colour
and thin type — the worst case for it. PNG also keeps the listing lossless from
here on.

Run from the repository root, after pulling the screenshots off the device:

    tools/crop-screenshot.py fastlane/metadata/android/*/images/phoneScreenshots/*.jpg --replace
"""

import argparse
import re
import subprocess
import sys
from pathlib import Path

from PIL import Image

# ROTATION_0 is the portrait layout, the only one phone screenshots are taken in.
INSETS = re.compile(
    r"ROTATION_0=\{[^}]*?overrideNonDecorInsets=\[\d+,(\d+)\]\[\d+,(\d+)\]"
)
DISPLAY_SIZE = re.compile(r"DisplayFrames w=(\d+) h=(\d+)")


def device_geometry():
    """Status bar height, navigation bar height and display size, from adb."""
    try:
        dump = subprocess.run(
            ["adb", "shell", "dumpsys", "window", "displays"],
            capture_output=True, text=True, check=True,
        ).stdout
    except (OSError, subprocess.CalledProcessError) as error:
        raise SystemExit(f"cannot read the insets from a device: {error}\n"
                         "Pass --top and --bottom instead.")

    insets = INSETS.search(dump)
    size = DISPLAY_SIZE.search(dump)
    if not insets or not size:
        raise SystemExit("no ROTATION_0 insets in dumpsys output; "
                         "pass --top and --bottom instead.")

    return (int(insets.group(1)), int(insets.group(2)),
            (int(size.group(1)), int(size.group(2))))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("screenshots", nargs="+", type=Path)
    parser.add_argument("--top", type=int, help="status bar height in pixels")
    parser.add_argument("--bottom", type=int, help="navigation bar height in pixels")
    parser.add_argument("-o", "--out-dir", type=Path,
                        help="where to write (default: beside each input)")
    parser.add_argument("--replace", action="store_true",
                        help="delete each input once its crop is written")
    args = parser.parse_args()

    display = None
    if args.top is None or args.bottom is None:
        top, bottom, display = device_geometry()
        top = args.top if args.top is not None else top
        bottom = args.bottom if args.bottom is not None else bottom
    else:
        top, bottom = args.top, args.bottom

    for path in args.screenshots:
        image = Image.open(path)
        if display and image.size != display:
            # Cropping by another device's insets would slice into the app.
            raise SystemExit(f"{path} is {image.size[0]}x{image.size[1]} but the "
                             f"device is {display[0]}x{display[1]}; pass --top "
                             "and --bottom for screenshots from elsewhere.")
        if top + bottom >= image.height:
            raise SystemExit(f"{path} is shorter than the bars being cropped")

        cropped = image.crop((0, top, image.width, image.height - bottom))
        destination = (args.out_dir or path.parent) / f"{path.stem}.png"
        destination.parent.mkdir(parents=True, exist_ok=True)
        cropped.save(destination, "PNG", optimize=True)
        if args.replace and path.resolve() != destination.resolve():
            path.unlink()

        print(f"{path} -> {destination} ({cropped.width}x{cropped.height})")


if __name__ == "__main__":
    sys.exit(main())
