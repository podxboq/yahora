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
"""Renders the Play feature graphic, one per locale, at the 1024x500 Play wants.

F-Droid never asks for this image, so nothing in the repository had one. Play
does, and it refuses any size but 1024x500 exactly.

Everything on it is read rather than retyped: the drawing comes from the
launcher icon's own vector, the name from the locale's `title.txt` and the
tagline from its `short_description.txt`. A feature graphic with its own copy of
the name or the tagline is one more place to forget when either changes, which
is the same reason the listing icon is rasterised instead of drawn by hand.

Three things about the composition are decisions rather than taste:

  * **The mark keeps no background square.** The banner is already the icon's
    own background colour, so painting the square would draw a seam around a
    drawing that is otherwise standing on the same ground. What is cropped is
    not the adaptive icon's 72dp viewport either — the alpha channel is
    measured, so the drawing sits on its true bounding box no matter how the
    vector is redrawn later.
  * **The block is centred, not ranged left.** Play crops this image at the
    sides on some surfaces, and a centred block with wide flat margins survives
    that; a name pushed into the right half does not.
  * **The tagline is the short description's first sentence.** The second one
    ("There is nothing else to fill in") is a qualification that reads as
    filler at a glance, and a banner is only ever read at a glance.

Needs rsvg-convert (librsvg) and Pillow. The typeface is whichever of the
preferred families fontconfig can supply, so regenerating on a machine with a
different set of fonts will not reproduce the committed PNG byte for byte — the
committed file is the reference. Run from the repository root:

    tools/render-feature-graphic.py
"""

import io
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ANDROID = "{http://schemas.android.com/apk/res/android}"

REPO = Path(__file__).resolve().parent.parent
RES = REPO / "app/src/main/res"
FOREGROUND = RES / "drawable/ic_launcher_foreground.xml"
BACKGROUND = RES / "values/ic_launcher_background.xml"
METADATA = REPO / "fastlane/metadata/android"

LOCALES = ("en-US", "es-ES")

# The size Play demands, to the pixel.
WIDTH = 1024
HEIGHT = 500

# The layout, in pixels of the final image.
MARK_HEIGHT = 244
GAP = 64
NAME_SIZE = 92
TAGLINE_SIZE = 32
TAGLINE_LEADING = 44
TAGLINE_MAX_WIDTH = 470
NAME_TO_TAGLINE = 26

# The tagline takes the colour of the tally marks, the name the colour of the Y,
# which is how the drawing next to them is already divided.
TAGLINE_COLOUR = "#8FB8CE"
NAME_COLOUR = "#FFFFFF"

# Rasterise the mark far larger than it is shown, so cropping to its alpha
# bounding box and scaling back down stays sharp.
MARK_OVERSAMPLE = 4

FONT_PREFERENCES = ("Inter", "Noto Sans", "DejaVu Sans", "Liberation Sans")


def background_colour() -> str:
    """The #AARRGGBB colour resource, as the #RRGGBB that SVG understands."""
    root = ET.parse(BACKGROUND).getroot()
    value = root.find("./color[@name='ic_launcher_background']").text.strip()
    digits = value.lstrip("#")
    if len(digits) == 8:  # AARRGGBB — the alpha comes first in Android, not last
        digits = digits[2:]
    return "#" + digits


def mark_svg() -> str:
    """The launcher icon's foreground alone, on transparency."""
    paths = "\n".join(
        '  <path d="{d}" stroke="{stroke}" stroke-width="{width}" '
        'stroke-linecap="{cap}" fill="none" />'.format(
            d=path.get(ANDROID + "pathData"),
            stroke=path.get(ANDROID + "strokeColor"),
            width=path.get(ANDROID + "strokeWidth"),
            cap=path.get(ANDROID + "strokeLineCap", "butt"),
        )
        for path in ET.parse(FOREGROUND).getroot().findall("path")
    )
    side = MARK_HEIGHT * MARK_OVERSAMPLE
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" '
        f'width="{side}" height="{side}">\n{paths}\n</svg>\n'
    )


def mark() -> Image.Image:
    """The mark, cropped to its own ink and scaled to the height it is shown at."""
    side = MARK_HEIGHT * MARK_OVERSAMPLE
    raster = subprocess.run(
        ["rsvg-convert", "-w", str(side), "-h", str(side), "-f", "png"],
        input=mark_svg().encode(),
        check=True,
        capture_output=True,
    ).stdout

    drawing = Image.open(io.BytesIO(raster)).convert("RGBA")
    drawing = drawing.crop(drawing.getbbox())  # the vector's own margins, gone
    width = round(drawing.width * MARK_HEIGHT / drawing.height)
    return drawing.resize((width, MARK_HEIGHT), Image.LANCZOS)


def font(size: int, bold: bool) -> ImageFont.FreeTypeFont:
    """The first preferred family fontconfig actually has, at the given size."""
    for family in FONT_PREFERENCES:
        pattern = f"{family}:bold" if bold else family
        match = subprocess.run(
            ["fc-match", "-f", "%{family}\t%{file}", pattern],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
        found, _, path = match.partition("\t")
        # fc-match always answers something; only its own family means a hit.
        if family.lower() in (name.strip().lower() for name in found.split(",")):
            return ImageFont.truetype(path, size)
    raise SystemExit(f"none of {', '.join(FONT_PREFERENCES)} is installed")


def greedy(text: str, face: ImageFont.FreeTypeFont, limit: float) -> list[str]:
    """The text broken on word boundaries into lines no wider than `limit`."""
    lines: list[str] = []
    line = ""
    for word in text.split():
        candidate = f"{line} {word}".strip()
        if line and face.getlength(candidate) > limit:
            lines.append(line)
            line = word
        else:
            line = candidate
    if line:
        lines.append(line)
    return lines


def wrapped(text: str, face: ImageFont.FreeTypeFont, limit: int) -> list[str]:
    """The same break, then tightened until the lines are of a piece.

    Filling each line before starting the next one is what leaves a last line
    holding a single word — "happened" under a full line, "pasado" under
    another — and an orphan like that is the detail that makes a banner look
    unconsidered. Squeezing the limit down to the narrowest that still fits the
    same number of lines spreads the words evenly instead, which is the shape
    the eye reads as deliberate.
    """
    lines = greedy(text, face, limit)
    if len(lines) < 2:
        return lines

    narrow, wide = 0.0, float(limit)
    while wide - narrow > 1:
        midpoint = (narrow + wide) / 2
        if len(greedy(text, face, midpoint)) <= len(lines):
            wide = midpoint
        else:
            narrow = midpoint
    return greedy(text, face, wide)


def listing(locale: str, name: str) -> str:
    return (METADATA / locale / name).read_text(encoding="utf-8").strip()


def render(locale: str, drawing: Image.Image) -> Path:
    name = listing(locale, "title.txt")
    tagline = listing(locale, "short_description.txt").split(".")[0].strip()

    banner = Image.new("RGB", (WIDTH, HEIGHT), background_colour())
    canvas = ImageDraw.Draw(banner)

    name_face = font(NAME_SIZE, bold=True)
    tagline_face = font(TAGLINE_SIZE, bold=False)
    lines = wrapped(tagline, tagline_face, TAGLINE_MAX_WIDTH)

    text_width = max(
        [name_face.getlength(name)] + [tagline_face.getlength(line) for line in lines]
    )
    # The name's cap height, not the font's line box: the block is centred on
    # the letters that are actually visible.
    name_top, name_bottom = name_face.getbbox(name)[1], name_face.getbbox(name)[3]
    name_height = name_bottom - name_top
    text_height = name_height + NAME_TO_TAGLINE + TAGLINE_LEADING * (len(lines) - 1) + TAGLINE_SIZE

    block_width = drawing.width + GAP + text_width
    left = round((WIDTH - block_width) / 2)
    banner.paste(drawing, (left, round((HEIGHT - drawing.height) / 2)), drawing)

    text_left = round(left + drawing.width + GAP)
    cursor = round((HEIGHT - text_height) / 2)
    canvas.text((text_left, cursor - name_top), name, font=name_face, fill=NAME_COLOUR)

    cursor += name_height + NAME_TO_TAGLINE
    for line in lines:
        canvas.text((text_left, cursor), line, font=tagline_face, fill=TAGLINE_COLOUR)
        cursor += TAGLINE_LEADING

    out = METADATA / locale / "images/featureGraphic.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    banner.save(out, "PNG", optimize=True)
    return out


def main() -> int:
    for tool in ("rsvg-convert", "fc-match"):
        if shutil.which(tool) is None:
            print(f"{tool} not found", file=sys.stderr)
            return 1
    for path in (FOREGROUND, BACKGROUND):
        if not path.is_file():
            print(f"missing {path}", file=sys.stderr)
            return 1

    drawing = mark()
    for locale in LOCALES:
        out = render(locale, drawing)
        print(f"wrote {out.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
