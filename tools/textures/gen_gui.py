"""The machine screens' textures: the steel their panels are cut from, and the little icons over the gauges.

- textures/gui/steel.png: 64x64, tiles. Dark blue-grey steel with a faint brushed grain running across, finished with
  texkit.shade (plain, light). Kept low in contrast on purpose: text and slots sit on it and must stay easy to read.
- textures/gui/icons.png: 80x16. Ten 8x8 icons on the top row, one per gauge: energy (a bolt), water, vegetable
  oil, biodiesel, mercury, experience (drops), liquid fuel (a drop with a flame in it), the Nether's air (a crimson
  puff, the Barter Terminal's), methanol (a pale blue-white drop) and wood vinegar (an amber-brown drop) - the last two
  since 1.5, last, so the others keep their place.
  The second row
  holds the same icons dimmed, for a gauge with nothing in it.
- textures/gui/sort_orders.png: 32x8. The Storage Barrel's sort orders, drawn as the button's own lettering is (white
  with a dark shadow one pixel down and right): bars falling away (most first), a # (item ID), a folder (grouped by
  mod) and a clock (last added first).
- textures/gui/sort_directions.png: 16x8. The button beside them, which way round the order runs, lettered the same:
  ascending (an arrow up beside bars growing down the icon) and descending (an arrow down beside bars shrinking).

- textures/gui/tabs.png: 48x16. The machine screens' tab icons (1.5, the user's drawing of 2026-11-08), 16x16 each,
  outlined and lit from the top left as the gauge icons are: Settings (three slider tracks with brass knobs), Liquid
  (an iron bucket with its handle) and Extra Slots (an empty slot with a brass plus in it, even every way round).
- textures/gui/dial.png: 294x14. The Liquid tab's timer, 21 frames of a round brass-rimmed dial filling clockwise from
  twelve o'clock in brass, one for each tick of its second.

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
    img = Image.new("RGBA", (80, 16), CLEAR)
    specs = [
        (BOLT, (236, 188, 52), (255, 236, 140), (178, 120, 28), None),       # energy
        (DROP, (58, 110, 214), (150, 190, 250), (32, 62, 150), None),        # water
        (DROP, (214, 156, 38), (252, 214, 110), (150, 96, 20), None),        # vegetable oil
        (DROP, (206, 196, 92), (248, 240, 168), (140, 128, 48), None),       # biodiesel
        (DROP, (176, 184, 196), (240, 244, 250), (104, 110, 124), None),     # mercury
        (DROP, (98, 198, 58), (200, 250, 130), (46, 120, 30), None),         # experience
        (FUEL, (170, 110, 40), None, (104, 64, 24), {"y": (255, 214, 90), "r": (236, 96, 34)}),  # liquid fuel
        (PUFF, (176, 48, 42), (236, 116, 84), (110, 24, 28), None),          # the Nether's air
        (DROP, (200, 222, 242), (250, 253, 255), (122, 152, 192), None),     # methanol: clear, colder than mercury's grey
        (DROP, (150, 88, 42), (206, 142, 88), (92, 50, 22), None),           # wood vinegar: a clear amber-brown (1.5)
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


# ---- the machine screens' tabs (1.5, the user's design of 2026-11-08) ----

TAB_EDGE = (16, 17, 20)
BRASS = (176, 138, 62)
BRASS_LIT = (226, 194, 122)
BRASS_DARK = (110, 84, 36)
IRON = (168, 173, 180)
IRON_LIT = (218, 222, 226)
IRON_DARK = (100, 106, 116)
COPPER = (194, 112, 60)
COPPER_LIT = (232, 160, 112)
COPPER_DARK = (122, 62, 34)
TRACK = (24, 26, 30)
TRACK_LIT = (88, 94, 104)


def shaped(img, ox, oy, pixels, body, light, shade):
    """A shape of {@code pixels} (a set of (x, y)) drawn as the gauge icons are: outlined, lit on its top and left
    inside the outline, shaded on its bottom and right."""
    for (x, y) in pixels:
        if any((x + dx, y + dy) not in pixels for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            colour = TAB_EDGE
        elif (x - 1, y) not in pixels or (x, y - 1) not in pixels or (x - 2, y) not in pixels and (x, y - 2) not in pixels:
            colour = light
        elif (x + 2, y) not in pixels or (x, y + 2) not in pixels:
            colour = shade
        else:
            colour = body
        img.putpixel((ox + x, oy + y), colour + (255,))


def rect(x0, y0, x1, y1):
    return {(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)}


def disc(cx, cy, r):
    return {(x, y) for x in range(16) for y in range(16) if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r}


def sliders(img, ox):
    """Settings: three slider tracks, each with a brass knob set at its own place (the user's drawing)."""
    for track_y, knob_x in ((3, 9), (8, 3), (13, 7)):
        for x in range(1, 15):
            img.putpixel((ox + x, track_y), TRACK + (255,))
            img.putpixel((ox + x, track_y + 1), TRACK_LIT + (255,))
        shaped(img, ox, 0, rect(knob_x, track_y - 2, knob_x + 3, track_y + 2), BRASS, BRASS_LIT, BRASS_DARK)


def bucket(img, ox):
    """Liquid: an iron bucket, its handle up over it and a band round its middle."""
    body = set()
    for y in range(5, 15):
        inset = (y - 5) // 4
        body |= {(x, y) for x in range(2 + inset, 14 - inset)}
    body |= rect(1, 4, 14, 5)  # the rim
    shaped(img, ox, 0, body, IRON, IRON_LIT, IRON_DARK)
    for x in range(3, 13):
        if (x, 9) in body and img.getpixel((ox + x, 9))[:3] != TAB_EDGE:
            img.putpixel((ox + x, 9), IRON_DARK + (255,))
    # the handle: an arc from the rim's ends over the top, one pixel wide
    for (x, y) in ((2, 3), (3, 2), (4, 1), (5, 1), (6, 0), (7, 0), (8, 0), (9, 0), (10, 1), (11, 1), (12, 2), (13, 3)):
        img.putpixel((ox + x, y), TAB_EDGE + (255,))


SLOT_WELL = (34, 36, 41)


def extra_slot(img, ox):
    """Extra Slots: an empty slot, flat, with a brass plus in it. The tab was renamed from Add-on on 2026-11-14 and the
    puzzle piece went with the name (「アイコンも直す？」); the first plus had arms of three and four pixels and was lit from
    the top left pixel by pixel, which read as lopsided (「＋なのに非対称なのが悪いんだと思う」), so this one is the same
    every way round: the slot 1..14 (fourteen wide, so the tab's middle is its own), the plus over 4..11 with two-pixel
    bars at 7..8 and arms of three each side, lit along its top edges and shaded along its bottom ones only, and the
    slot's well left flat rather than bevelled (the user's pick of five, 「D2がいいかな」)."""
    for y in range(1, 15):
        for x in range(1, 15):
            edge = x in (1, 14) or y in (1, 14)
            img.putpixel((ox + x, y), (TAB_EDGE if edge else SLOT_WELL) + (255,))
    bar = range(7, 9)
    plus = {(x, y) for x in range(4, 12) for y in bar} | {(x, y) for x in bar for y in range(4, 12)}
    for (x, y) in plus:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in plus:
                img.putpixel((ox + x + dx, y + dy), TAB_EDGE + (255,))
    for (x, y) in plus:
        colour = BRASS_LIT if (x, y - 1) not in plus else BRASS_DARK if (x, y + 1) not in plus else BRASS
        img.putpixel((ox + x, y), colour + (255,))


def tab_icons():
    """textures/gui/tabs.png, 48x16: Settings, Liquid and Extra Slots, 16x16 each."""
    img = Image.new("RGBA", (48, 16), CLEAR)
    sliders(img, 0)
    bucket(img, 16)
    extra_slot(img, 32)
    return img


DIAL = 14
DIAL_FRAMES = 21  # 0 to 20 ticks of the Liquid tab's second (LiquidTab.TICKS)
DIAL_FACE = (21, 23, 27)
DIAL_FILL = (214, 176, 92)
DIAL_FILL_LIT = (240, 214, 150)


def dial_frame(img, ox, done):
    """A round brass-rimmed dial, filled clockwise from twelve o'clock as far as {@code done} (0 to 1)."""
    import math
    c = DIAL / 2
    for y in range(DIAL):
        for x in range(DIAL):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            r = math.hypot(dx, dy)
            if r > 7.0:
                continue
            if r > 6.2:
                colour = TAB_EDGE
            elif r > 5.0:
                colour = BRASS_LIT if dx + dy < -1.5 else BRASS_DARK if dx + dy > 1.5 else BRASS
            elif r > 4.3:
                colour = TAB_EDGE  # a dark ring inside the rim, so a full dial still reads as a dial
            else:
                turned = (math.degrees(math.atan2(dx, -dy)) + 360) % 360  # clockwise from straight up
                if done > 0 and turned < done * 360:
                    colour = DIAL_FILL_LIT if dx + dy < -2 else DIAL_FILL
                else:
                    colour = DIAL_FACE
            img.putpixel((ox + x, y), colour + (255,))
    # the hand's pin, and the mark at twelve
    img.putpixel((ox + 6, 6), BRASS_LIT + (255,))
    img.putpixel((ox + 7, 7), BRASS_DARK + (255,))
    img.putpixel((ox + 6, 7), BRASS + (255,))
    img.putpixel((ox + 7, 6), BRASS + (255,))
    if done <= 0:
        img.putpixel((ox + 6, 2), BRASS + (255,))
        img.putpixel((ox + 7, 2), BRASS + (255,))


def dial():
    """textures/gui/dial.png: the Liquid tab's timer, {@code DIAL_FRAMES} frames of 14x14 side by side."""
    img = Image.new("RGBA", (DIAL * DIAL_FRAMES, DIAL), CLEAR)
    for i in range(DIAL_FRAMES):
        dial_frame(img, i * DIAL, i / (DIAL_FRAMES - 1))
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    steel().save(OUT / "steel.png")
    icons().save(OUT / "icons.png")
    sort_orders().save(OUT / "sort_orders.png")
    sort_directions().save(OUT / "sort_directions.png")
    tab_icons().save(OUT / "tabs.png")
    dial().save(OUT / "dial.png")
    print("wrote the machine screens' steel, gauge icons, tab icons and timer dial, and the barrel's sort order and direction icons")


if __name__ == "__main__":
    main()
