"""The machine screens' textures: the steel their panels are cut from, and the little icons over the gauges.

- textures/gui/steel.png: 64x64, tiles. Dark blue-grey steel with a faint brushed grain running across, finished with
  texkit.shade (plain, light). Kept low in contrast on purpose: text and slots sit on it and must stay easy to read.
- textures/gui/icons.png: 64x16. Eight 8x8 icons on the top row, one per gauge: energy (a bolt), water, vegetable
  oil, biodiesel, mercury, experience (drops), liquid fuel (a drop with a flame in it) and the Nether's air (a crimson
  puff, the Barter Terminal's). The second row
  holds the same icons dimmed, for a gauge with nothing in it.
- textures/gui/sort_orders.png: 32x8. The Storage Barrel's sort orders, drawn as the button's own lettering is (white
  with a dark shadow one pixel down and right): bars falling away (most first), a # (item ID), a folder (grouped by
  mod) and a clock (last added first).
- textures/gui/sort_directions.png: 16x8. The button beside them, which way round the order runs, lettered the same:
  ascending (an arrow up beside bars growing down the icon) and descending (an arrow down beside bars shrinking).

Original pixel art. Run from the repo root:  python tools/textures/gen_gui.py
"""
import random
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/nookworks/textures/gui"
CLEAR = (0, 0, 0, 0)
OUTLINE = (16, 17, 20, 255)

STEEL = (59, 64, 72)


def steel():
    """Brushed steel: each row of the grain a shade off its neighbours in long runs, the odd scratch lighter."""
    rng = random.Random(41)
    img = Image.new("RGBA", (64, 64), STEEL + (255,))
    for y in range(64):
        x = 0
        while x < 64:
            run = rng.randint(6, 22)
            shade = rng.choice((-3, -2, -1, 0, 0, 0, 1, 2))
            for i in range(run):
                c = tuple(max(0, min(255, v + shade)) for v in STEEL)
                img.putpixel(((x + i) % 64, y), c + (255,))
            x += run
    for _ in range(9):  # scratches, a few pixels long, all running with the grain
        y = rng.randrange(64)
        x = rng.randrange(64)
        for i in range(rng.randint(3, 7)):
            c = tuple(min(255, v + 7) for v in STEEL)
            img.putpixel(((x + i) % 64, y), c + (255,))
    return texkit.shade(img, 12, tile=True, clusters=0.3, grain=0.0, size=8, reach=1, material="plain", strength=0.6)


# 8x8 masks: '#' outline, 'o' body, 'l' light, 'd' shade.
DROP = [
    "...#....",
    "..#o#...",
    ".#loo#..",
    "#loooo#.",
    "#loood#.",
    "#ooodd#.",
    ".#ddd#..",
    "..###...",
]
BOLT = [
    "....###.",
    "...#ll#.",
    "..#lo#..",
    ".#looo##",
    "#oooool#",
    ".##oo#..",
    "..#o#...",
    "..##....",
]
PUFF = [
    "........",
    "...##...",
    "..#ll#..",
    ".#lloo##",
    "#looooo#",
    "#oooodd#",
    ".######.",
    "........",
]
FUEL = [
    "...#....",
    "..#o#...",
    ".#oyo#..",
    "#oyroo#.",
    "#oyrro#.",
    "#oyrrd#.",
    ".#ddd#..",
    "..###...",
]


def icon(img, ox, oy, mask, body, light=None, shade=None, extra=None, dim=False):
    light = light or tuple(min(255, c + 60) for c in body)
    shade = shade or tuple(max(0, c - 45) for c in body)
    extra = extra or {}
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            colour = {"#": OUTLINE[:3], "o": body, "l": light, "d": shade}.get(ch) or extra.get(ch)
            if colour is None:
                continue
            if dim and ch != "#":
                grey = sum(colour) // 3
                colour = tuple((grey + c) // 2 * 3 // 5 for c in colour)
            img.putpixel((ox + x, oy + y), tuple(colour) + (255,))


def icons():
    img = Image.new("RGBA", (64, 16), CLEAR)
    specs = [
        (BOLT, (236, 188, 52), (255, 236, 140), (178, 120, 28), None),       # energy
        (DROP, (58, 110, 214), (150, 190, 250), (32, 62, 150), None),        # water
        (DROP, (214, 156, 38), (252, 214, 110), (150, 96, 20), None),        # vegetable oil
        (DROP, (206, 196, 92), (248, 240, 168), (140, 128, 48), None),       # biodiesel
        (DROP, (176, 184, 196), (240, 244, 250), (104, 110, 124), None),     # mercury
        (DROP, (98, 198, 58), (200, 250, 130), (46, 120, 30), None),         # experience
        (FUEL, (170, 110, 40), None, (104, 64, 24), {"y": (255, 214, 90), "r": (236, 96, 34)}),  # liquid fuel
        (PUFF, (176, 48, 42), (236, 116, 84), (110, 24, 28), None),          # the Nether's air
    ]
    for i, (mask, body, light, shade, extra) in enumerate(specs):
        icon(img, i * 8, 0, mask, body, light, shade, extra)
        icon(img, i * 8, 8, mask, body, light, shade, extra, dim=True)
    return img


# 7x7 glyphs; 'o' is lettering. Each gets the font's shadow.
COUNT = [
    "o......",
    "o......",
    "o.o....",
    "o.o....",
    "o.o.o..",
    "o.o.o.o",
    "ooooooo",
]
HASH = [
    ".......",
    "..o.o..",
    ".ooooo.",
    "..o.o..",
    ".ooooo.",
    "..o.o..",
    ".......",
]
FOLDER = [
    ".......",
    "ooo....",
    "ooooooo",
    "o.....o",
    "o.....o",
    "o.....o",
    "ooooooo",
]
CLOCK = [
    "..ooo..",
    ".o...o.",
    "o..o..o",
    "o..oo.o",
    "o.....o",
    ".o...o.",
    "..ooo..",
]
# ascending and descending: the arrow, and bars short to long (or long to short) beside it
ASCENDING = [
    ".o.....",
    "ooo.o..",
    ".o.....",
    ".o..oo.",
    ".o.....",
    ".o..ooo",
    ".o.....",
]
DESCENDING = [
    ".o.....",
    ".o..ooo",
    ".o.....",
    ".o..oo.",
    ".o.....",
    "ooo.o..",
    ".o.....",
]
LETTER = (224, 224, 224, 255)
SHADOW = (56, 56, 56, 255)


def lettered(glyphs):
    """Glyphs side by side, 8 px apart, each in the buttons' lettering with its shadow."""
    img = Image.new("RGBA", (8 * len(glyphs), 8), CLEAR)
    for i, glyph in enumerate(glyphs):
        for dx, dy, colour in ((1, 1, SHADOW), (0, 0, LETTER)):
            for y, row in enumerate(glyph):
                for x, ch in enumerate(row):
                    if ch == "o":
                        img.putpixel((i * 8 + x + dx, y + dy), colour)
    return img


def sort_orders():
    return lettered((COUNT, HASH, FOLDER, CLOCK))


def sort_directions():
    return lettered((ASCENDING, DESCENDING))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    steel().save(OUT / "steel.png")
    icons().save(OUT / "icons.png")
    sort_orders().save(OUT / "sort_orders.png")
    sort_directions().save(OUT / "sort_directions.png")
    print("wrote the machine screens' steel and gauge icons, and the barrel's sort order and direction icons")


if __name__ == "__main__":
    main()
