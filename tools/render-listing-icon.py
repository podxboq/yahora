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
"""Renders the store listing icon from the launcher icon's own drawables.

F-Droid wants a 512x512 PNG, and Android has no such file: the launcher icon is
an adaptive one, two vectors and a colour. Redrawing it by hand in a paint
program is how the listing ends up showing an older icon than the app does, so
this reads the very same resources and rasterises them.

Two things it has to get right, and both are about the adaptive icon's geometry:
the canvas is 108dp but a launcher only ever shows the middle 72dp, so the
listing must show that crop or the drawing looks shrunken; and the background
layer is a flat colour that has to fill the crop, since a listing icon is opaque.

Needs rsvg-convert (librsvg). Run from the repository root:

    tools/render-listing-icon.py
"""

import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"

REPO = Path(__file__).resolve().parent.parent
RES = REPO / "app/src/main/res"
FOREGROUND = RES / "drawable/ic_launcher_foreground.xml"
BACKGROUND = RES / "values/ic_launcher_background.xml"
OUT = REPO / "fastlane/metadata/android/en-US/images/icon.png"

# The adaptive icon's canvas, and the part of it a launcher actually shows.
CANVAS = 108.0
VIEWPORT = 72.0
SIZE = 512


def background_colour() -> str:
    """The #AARRGGBB colour resource, as the #RRGGBB that SVG understands."""
    root = ET.parse(BACKGROUND).getroot()
    value = root.find("./color[@name='ic_launcher_background']").text.strip()
    digits = value.lstrip("#")
    if len(digits) == 8:  # AARRGGBB — the alpha comes first in Android, not last
        digits = digits[2:]
    return "#" + digits


def strokes(vector: Path):
    """Every <path> of a vector drawable, as the SVG attributes it maps to."""
    for path in ET.parse(vector).getroot().findall("path"):
        yield {
            "d": path.get(ANDROID + "pathData"),
            "stroke": path.get(ANDROID + "strokeColor"),
            "stroke-width": path.get(ANDROID + "strokeWidth"),
            "stroke-linecap": path.get(ANDROID + "strokeLineCap", "butt"),
        }


def svg() -> str:
    inset = (CANVAS - VIEWPORT) / 2
    paths = "\n".join(
        '  <path d="{d}" stroke="{stroke}" stroke-width="{stroke-width}" '
        'stroke-linecap="{stroke-linecap}" fill="none" />'.format(**stroke)
        for stroke in strokes(FOREGROUND)
    )
    return f"""<svg xmlns="http://www.w3.org/2000/svg"
     viewBox="{inset} {inset} {VIEWPORT} {VIEWPORT}" width="{SIZE}" height="{SIZE}">
  <rect x="0" y="0" width="{CANVAS}" height="{CANVAS}" fill="{background_colour()}" />
{paths}
</svg>
"""


def main() -> int:
    if shutil.which("rsvg-convert") is None:
        print("rsvg-convert not found — install librsvg", file=sys.stderr)
        return 1

    for path in (FOREGROUND, BACKGROUND):
        if not path.is_file():
            print(f"missing {path}", file=sys.stderr)
            return 1

    OUT.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["rsvg-convert", "-w", str(SIZE), "-h", str(SIZE), "-o", str(OUT)],
        input=svg().encode(),
        check=True,
    )
    print(f"wrote {OUT.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
