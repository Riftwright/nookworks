"""The bakery and the noodle bar, corn and onions: the food, the dough set down to rise, and the models of the Hand
Quern and the Noodle Maker. All original pixel art, drawn flat and finished with texkit.shade, all plain (ITEM for the food, its dark
outline kept exact); the machines are vanilla textures composed in JSON, with our black iron. Deterministic.

- item/bread_dough, risen_dough, udon_dough, raw_udon, pancake_batter; udon, cream_stew, corn_potage (in bowls);
  donut, pancake, grilled_corn; corn (the ear), corn_kernels (planted), onion - the doughs and the raw udon placed
  and ringed here to itemkit's bar, without the finish's grain, and so is the onion
- block/bread_dough.png, block/risen_dough.png: the dough's skin, for the balls set on a tray to rise
- models/block/dough_ball[_risen].json: one ball of dough, and one risen, which the Wooden Tray draws on itself
- models/block/wooden_tray.json and its blockstate: a board of oak with a low rim, vanilla's oak composed
- models/block/hand_quern[_base|_crank|_axle].json: two millstones, the upper turned by an oak peg; a spout at the front
- models/block/noodle_maker[_base|_crank|_axle].json: a black iron box of rollers on a board, a tray on top, a handle
- the crops: block/corn_stage0..5 (the lower block) and corn_top_stage3..5 (the upper), block/onions_stage0..3

Run from the repo root:  python tools/textures/gen_kitchen.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

import itemkit
import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
CLEAR = (0, 0, 0, 0)
OUTLINE = (40, 30, 26)

BOWL = [(72, 44, 24), (104, 66, 36), (136, 90, 52), (164, 116, 70)]          # the turned wooden bowl the soups come in
DONBURI = [(34, 38, 66), (48, 56, 96), (70, 82, 132), (112, 124, 170), (206, 200, 186)]  # indigo glaze; its pale lip
CHINA = [(150, 150, 150), (196, 198, 200), (228, 230, 232), (248, 248, 250), (70, 96, 150)]  # white china, blue band
DOUGH = [(182, 150, 104), (220, 192, 146), (240, 222, 184), (252, 244, 222)]
RISEN = [(198, 166, 116), (232, 208, 162), (247, 233, 202), (254, 250, 238)]
FLOUR_DUST = (252, 250, 244)
NOODLE = [(176, 166, 136), (216, 208, 184), (240, 236, 222), (254, 252, 246)]
DASHI = [(132, 82, 34), (170, 112, 50), (200, 146, 72), (224, 180, 104)]
NEGI = [(62, 120, 40), (110, 170, 64)]
KAMABOKO = [(222, 104, 124), (252, 246, 242)]
CREAM = [(196, 184, 160), (226, 218, 200), (244, 240, 230), (254, 252, 248)]
CARROT = [(196, 92, 24), (236, 132, 44)]
POTATO = [(196, 170, 96), (232, 212, 144)]
CHICKEN = [(196, 150, 104), (232, 196, 150)]
CORN = [(156, 104, 14), (214, 160, 32), (244, 204, 70), (255, 234, 136)]
HUSK = [(64, 100, 34), (102, 146, 52), (150, 188, 88), (196, 214, 140)]
DRY_HUSK = [(150, 128, 70), (196, 176, 110), (224, 208, 150)]
SILK = [(150, 104, 46), (206, 158, 84)]
CHAR = [(66, 40, 20), (104, 62, 28)]
# The ear's icon: kernels by how lit they are (the lit side first) and the seams between their rows, a step down from
# each; the husk, light to dark; the cut stem it was broken off by.
EAR_KERNEL = [(255, 240, 158), (252, 218, 92), (236, 184, 48), (204, 142, 26)]
EAR_SEAM = [(232, 186, 66), (212, 156, 36), (184, 122, 20), (146, 90, 12)]
EAR_HUSK = [(206, 224, 150), (156, 196, 92), (106, 152, 56), (66, 104, 36)]
SHANK = [(204, 190, 126), (150, 136, 80)]
GLAZE_KERNEL = [(254, 222, 128), (240, 180, 72), (210, 136, 44), (164, 94, 26)]   # soy sauce brushed on and grilled
GLAZE_SEAM = [(218, 152, 58), (188, 118, 36), (150, 86, 24), (108, 58, 16)]
# Seed corn, dried: the crown pale and dented with starch, the body a deeper yellow than the fresh ear, the tip cap pale.
SEED_CORN = {"C": (252, 236, 168), "c": (236, 210, 128), "L": (246, 196, 62), "M": (226, 160, 36), "D": (184, 116, 22),
             "T": (232, 214, 172)}
ONION = [(92, 40, 18), (136, 68, 28), (184, 108, 46), (220, 156, 78), (240, 194, 118), (252, 226, 168)]  # its sheen last
ONION_NECK = [(96, 58, 30), (140, 96, 52), (178, 134, 80)]
ONION_ROOT = [(168, 146, 106), (218, 202, 166)]
# fried dough, deep shade to highlight, the shades redder and the lights yellower; the sugar on it in shade, mid, lit
DONUT = [(96, 46, 26), (138, 72, 34), (178, 106, 46), (210, 144, 66), (234, 182, 98), (248, 214, 140)]
DONUT_SUGAR = [(214, 196, 170), (238, 228, 210), (252, 248, 240)]
SUGAR = DONUT_SUGAR[2]
PANCAKE = [(128, 76, 30), (180, 120, 52), (218, 166, 90), (240, 204, 140)]
BUTTER = [(232, 196, 84), (254, 232, 140)]
SYRUP = [(96, 44, 12), (150, 76, 20), (196, 118, 40)]
BATTER = [(206, 176, 104), (234, 210, 144), (248, 232, 180)]
JUG = [(150, 156, 166), (206, 212, 220), (236, 240, 244), (252, 252, 254)]   # white enamel
JUG_RIM = (46, 70, 124)


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def get(img, x, y):
    return img.getpixel((x, y)) if 0 <= x < 16 and 0 <= y < 16 else CLEAR


def outline(img):
    """Where the ring goes round the shape: it is drawn at save time by texkit.icon, in the shape's own colours as
    vanilla rings its items, so anything drawn after this call (steam) is left unringed."""
    return img


def disc(img, cx, cy, rx, ry, colours, light=(-1, -1)):
    """A filled ellipse shaded as a ball lit from {@code light}: darkest on the far side, a highlight near."""
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            d = dx * dx + dy * dy
            if d > 1.0:
                continue
            lit = (dx * light[0] + dy * light[1]) / 1.4142 + (1 - d) * 0.35
            i = 0 if lit < -0.35 else 1 if lit < 0.1 else 2 if lit < 0.55 else 3
            px(img, x, y, colours[min(i, len(colours) - 1)])


def bowl(img, top=9, colours=BOWL):
    """The turned wooden bowl, a little from above: rim at row {@code top}, down to a foot."""
    rows = [(1, 14), (1, 14), (2, 13), (3, 12), (4, 11), (5, 10)]
    for i, (a, b) in enumerate(rows):
        y = top + i
        for x in range(a, b + 1):
            c = colours[2] if x < 5 and i < 3 else colours[1]
            if i == len(rows) - 1 or x == b:
                c = colours[0]
            px(img, x, y, c)
    for x in range(2, 14):
        px(img, x, top, colours[3] if x < 9 else colours[2])


def donburi(img, top=8):
    """A deep noodle bowl in indigo glaze, its lip left pale, a little from above."""
    rows = [(0, 15), (1, 14), (1, 14), (2, 13), (3, 12), (4, 11), (6, 9)]
    for i, (a, b) in enumerate(rows):
        y = top + i
        for x in range(a, b + 1):
            c = DONBURI[2] if x < 5 and i < 4 else DONBURI[1]
            if i == len(rows) - 1 or x >= b - 1:
                c = DONBURI[0]
            px(img, x, y, c)
    for x in range(1, 15):
        px(img, x, top, DONBURI[4] if x < 11 else DONBURI[3])
    for x in range(3, 13, 3):  # a band of pale brushwork round it
        px(img, x, top + 3, DONBURI[3])


def china_bowl(img, top=9):
    """A white china soup bowl with a blue band under the lip."""
    rows = [(1, 14), (1, 14), (2, 13), (3, 12), (4, 11), (5, 10)]
    for i, (a, b) in enumerate(rows):
        y = top + i
        for x in range(a, b + 1):
            c = CHINA[3] if x < 6 and i < 4 else CHINA[2]
            if i == len(rows) - 1 or x >= b:
                c = CHINA[1]
            px(img, x, y, c)
    for x in range(2, 14):
        px(img, x, top, CHINA[3])
        px(img, x, top + 1, CHINA[4] if 2 < x < 13 else CHINA[1])


def surface(img, top, colours, half=6):
    """What fills a bowl, seen over the rim: an ellipse of it from row {@code top - 2} to the rim at {@code top}."""
    for y, h in ((top - 2, half - 2), (top - 1, half), (top, half + 1)):
        for x in range(8 - h, 8 + h):
            c = colours[2] if x < 6 else colours[1] if x < 11 else colours[0]
            px(img, x, y, c)


def steam(img, spots):
    for x, y, a in spots:
        px(img, x, y, (240, 240, 240, a))


# ---------------------------------------------------------------------------------------------------------- the food
# The doughs as items, lit as soft round things are: the ramps run from a cool brown shade to a warm cream light.
ITEM_DOUGH = [(148, 106, 72), (190, 148, 102), (222, 188, 138), (240, 216, 172), (252, 238, 212)]
ITEM_RISEN = [(160, 116, 80), (204, 164, 116), (232, 204, 156), (246, 228, 192), (255, 246, 226)]
CUT = [(178, 116, 62), (250, 236, 196)]  # a scored cut opened by the rising: its shadowed wall, the crumb inside
FLOUR = (255, 255, 250)


def soft_ball(ramp, cx, cy, rx, top, foot, spread):
    """
    A ball of dough sitting on the ground: round over its top ({@code top} tall above its centre), its foot shorter
    ({@code foot}) and spread wider ({@code spread}) where it sags onto what it rests on; lit from the upper left and
    a little from the front, the light running into the shade in soft bands, the foot darkest where it meets the ground.
    """
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            dx = (x + 0.5 - cx) / rx
            dy = (y + 0.5 - cy) / (top if y + 0.5 < cy else foot)
            if dy > 0:
                dx /= 1 + spread * dy
            d = dx * dx + dy * dy
            if d > 1:
                continue
            lam = -0.55 * dx - 0.65 * dy + 0.52 * math.sqrt(1 - d)
            shade = 4 if lam > 0.78 else 3 if lam > 0.45 else 2 if lam > 0.1 else 1 if lam > -0.3 else 0
            px(img, x, y, ramp[min(shade, 1) if dy > 0.78 else shade])
    return img


def bread_dough():
    """A ball of kneaded dough set down, sagging a little onto its foot, flour on its top and the seam where it was
    tucked under showing at the back right. Ringed and finished here: grain over its smooth skin would read as lumps."""
    img = soft_ball(ITEM_DOUGH, 8.0, 9.4, 6.6, 5.2, 4.2, 0.18)
    for x, y in ((5, 6), (6, 5), (8, 5), (7, 7), (4, 8), (10, 6)):
        px(img, x, y, FLOUR)
    for x, y in ((9, 12), (10, 12), (11, 11)):
        px(img, x, y, ITEM_DOUGH[1])
    return texkit.outline(img)


def risen_dough():
    """The dough risen: taller and fuller, puffed out to its foot, the cross scored in its top opened by the rising -
    each cut its upper wall in shadow, the paler crumb showing below it."""
    img = soft_ball(ITEM_RISEN, 8.0, 9.0, 7.0, 6.2, 4.6, 0.15)
    for i in (-2, -1, 0, 1, 2):
        for x, y in ((8 + i, 8 + i), (8 + i, 8 - i)):
            px(img, x, y, CUT[1])
    for i in (-2, -1, 0, 1, 2):
        for x, y in ((8 + i, 7 + i), (8 + i, 7 - i)):
            px(img, x, y, CUT[0])
    for x, y in ((4, 6), (5, 5), (11, 5)):
        px(img, x, y, FLOUR)
    return texkit.outline(img)


UDON_DOUGH = {"5": (254, 252, 244), "4": (240, 234, 214), "3": (220, 210, 182), "2": (190, 178, 144),
              "1": (150, 136, 102), "s": (122, 108, 78), "f": (255, 255, 255)}
UDON_FOLD = (  # (row, column, pixels)
    (4, 4, "455555554"),
    (5, 3, "45f4444443"),
    (6, 2, "444444f4443"), (6, 13, "3"),
    (7, 2, "4f444444443"), (7, 13, "2"),
    (8, 2, "44444444f43"), (8, 13, "2"),
    (9, 2, "33333333332"), (9, 13, "2"),
    (10, 1, "432222222221"), (10, 13, "2"),
    (11, 1, "32sssssssssss"),
    (12, 2, "33333333333"), (12, 13, "2"),
    (13, 3, "1222222221"),
)


def udon_dough():
    """
    A thick sheet of udon dough folded in half, as it is before it is cut: the upper half's face lit and floured, its
    edge in front; at the left the bend where the sheet turns under, rounded; a dark seam between the halves; the
    lower half a pixel longer at the right, in the shadow of the half over it. (Drawn as one slab it read as tofu.)
    """
    rows = [["."] * 16 for _ in range(16)]
    for y, x, run in UDON_FOLD:
        rows[y][x:x + len(run)] = run
    return itemkit.sprite(itemkit.check(["".join(r) for r in rows]), UDON_DOUGH)


# Raw udon (itemkit): a portion of fresh noodles, thick and floured, in a loose nest - a strand two pixels thick looping
# round the outside, another looping inside it and passing over and under it, the slot showing through the middle, and
# two ends trailing off at the foot. Each strand lit along its upper or left edge (l), in shade along the other (s), its
# body between (b) where it turns; a deep groove (g) where one passes under another; flour in white specks (f). The
# warm cream of the dough, ringed in its brown. (Concentric rings from a formula read as a plate, or a target.)
RAW_UDON = {"f": (255, 255, 252), "l": (246, 242, 230), "b": (226, 218, 196), "s": (186, 172, 140), "g": (126, 112, 82)}
RAW_UDON_NEST = itemkit.check([
    "................",
    "................",
    "................",
    "....llflllll....",
    "..llbsssssbll...",
    ".lbssglllfgsbl..",
    ".ls.lbssssbl.ls.",
    ".ls.lsg..gls.ls.",
    ".ls.ls...lsglls.",
    ".ls.lbllllsgsls.",
    ".lbgsssssglslbs.",
    "..lbllfllglsbs..",
    "..lssssssglss...",
    ".ls.......lsl...",
    "..s........ls...",
    "................",
])


def raw_udon():
    return itemkit.sprite(RAW_UDON_NEST, RAW_UDON, holes=True)


def udon():
    """Kake udon: thick white noodles in a dashi broth in an indigo bowl, a slice of kamaboko and green onion on top."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    donburi(img, top=8)
    surface(img, 8, DASHI, 7)
    for x in range(2, 14):  # noodles lifting out of the broth
        for y in (7, 8):
            if (x + y) % 3:
                px(img, x, y, NOODLE[2] if x < 9 else NOODLE[1])
    for x in range(4, 12, 2):
        px(img, x, 6, NOODLE[3])
    px(img, 10, 6, KAMABOKO[1])  # kamaboko: white with a pink edge
    px(img, 11, 6, KAMABOKO[1])
    px(img, 10, 5, KAMABOKO[0])
    px(img, 11, 5, KAMABOKO[0])
    for x, y in ((5, 6), (6, 7), (8, 6)):
        px(img, x, y, NEGI[1])
    px(img, 7, 7, NEGI[0])
    img = outline(img)
    steam(img, ((6, 3, 140), (7, 2, 100), (6, 1, 70)))
    return img


def cream_stew():
    """White stew: a thick cream sauce, with carrot, potato, chicken and a leaf of parsley showing."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    bowl(img, top=9)
    surface(img, 9, CREAM, 6)
    for x, y, c in ((4, 8, CARROT[1]), (5, 8, CARROT[0]), (8, 7, POTATO[1]), (9, 8, POTATO[0]), (11, 8, CHICKEN[1]),
                    (7, 9, CARROT[1]), (10, 9, CHICKEN[0]), (6, 7, NEGI[1])):
        px(img, x, y, c)
    img = outline(img)
    steam(img, ((9, 5, 140), (8, 4, 100), (9, 3, 70)))
    return img


def corn_potage():
    """Corn potage: a smooth pale-yellow soup in white china, a swirl of cream and a pinch of parsley."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    china_bowl(img, top=9)
    surface(img, 9, [(214, 170, 64), (240, 204, 104), (252, 228, 150)], 6)
    for x, y in ((6, 8), (7, 7), (8, 7), (9, 8)):
        px(img, x, y, (254, 248, 226))
    px(img, 10, 8, NEGI[1])
    px(img, 5, 9, NEGI[0])
    img = outline(img)
    steam(img, ((7, 5, 140), (8, 4, 100), (7, 3, 70)))
    return img


# The doughnut's shape, in pixels: the top of the ring an ellipse round the middle, the hole another, and the side
# showing DONUT_SIDE below the top, as seen a little from above.
DONUT_MIDDLE = (8.0, 6.9)
DONUT_RIM = (6.55, 4.9)
DONUT_HOLE = (2.5, 1.9)
DONUT_SIDE = 2.1
# light from the upper left and above, as on every item
DONUT_LIGHT = tuple(v / math.sqrt(0.55 ** 2 + 0.65 ** 2 + 0.75 ** 2) for v in (-0.55, -0.65, 0.75))
# where the sugar lies on the top (only those on it count)
DONUT_GRAINS = ((3, 5), (5, 3), (7, 2), (10, 3), (12, 5), (13, 7), (11, 9), (4, 9), (2, 7), (6, 10), (9, 10), (12, 3),
                (4, 7), (8, 3), (11, 6), (6, 4))


def _donut_ellipse(x, y, radii, drop=0.0):
    dx = (x - DONUT_MIDDLE[0]) / radii[0]
    dy = (y - drop - DONUT_MIDDLE[1]) / radii[1]
    return math.sqrt(dx * dx + dy * dy), math.atan2(dy, dx)


def _donut_parts():
    """Each pixel's part: ("top", s, angle) on the ring's top, s from -1 at the hole to 1 at the rim; ("side", c) on the
    side below it, c the cosine round it; ("wall",) where the hole's far wall shows; ("hole",) where it is open."""
    parts = {}
    for y in range(16):
        for x in range(16):
            px_, py_ = x + 0.5, y + 0.5
            rim, angle = _donut_ellipse(px_, py_, DONUT_RIM)
            hole, _ = _donut_ellipse(px_, py_, DONUT_HOLE)
            if rim <= 1.0 and hole > 1.0:
                # across the ring at this angle, from the hole's edge to the rim's
                inner = DONUT_HOLE[0] * DONUT_HOLE[1] / math.hypot(DONUT_HOLE[1] * math.cos(angle), DONUT_HOLE[0] * math.sin(angle))
                outer = DONUT_RIM[0] * DONUT_RIM[1] / math.hypot(DONUT_RIM[1] * math.cos(angle), DONUT_RIM[0] * math.sin(angle))
                d = math.hypot(px_ - DONUT_MIDDLE[0], py_ - DONUT_MIDDLE[1])
                parts[(x, y)] = ("top", max(-1.0, min(1.0, 2 * (d - inner) / (outer - inner) - 1)), angle)
            elif hole <= 1.0:
                parts[(x, y)] = ("wall",) if _donut_ellipse(px_, py_, DONUT_HOLE, DONUT_SIDE)[0] > 1.0 else ("hole",)
            elif py_ > DONUT_MIDDLE[1] and (_donut_ellipse(px_, py_, DONUT_RIM, DONUT_SIDE)[0] <= 1.0
                                            or _donut_ellipse(px_, py_, DONUT_RIM, DONUT_SIDE / 2)[0] <= 1.0):
                parts[(x, y)] = ("side", math.cos(_donut_ellipse(px_, py_, DONUT_RIM, DONUT_SIDE)[1]))
    return parts


def _donut_light(s, angle):
    """How lit the ring's top is here: round in section, it leans out at the rim and in at the hole."""
    nx, ny = 0.95 * s * math.cos(angle), 0.95 * s * math.sin(angle)
    nz = math.sqrt(max(0.05, 1 - nx * nx - ny * ny))
    return nx * DONUT_LIGHT[0] + ny * DONUT_LIGHT[1] + nz * DONUT_LIGHT[2]


def donut():
    """A ring doughnut, fried golden and rolled in sugar, seen a little from above (the user chose it from four,
    2026-10-05: the old one, flat and speckled, read as a bagel): the top of the ring round in section, lit from the
    upper left, its side showing below, and the hole open where its far wall does not fill it - the slot shows through.
    Ringed as vanilla rings its items, without the finish's grain, which over a smooth fried skin reads as crumbs."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    parts = _donut_parts()
    lit = DONUT[1:]
    for (x, y), part in parts.items():
        if part[0] == "top":
            v = (_donut_light(part[1], part[2]) + 0.1) / 1.1
            px(img, x, y, lit[max(0, min(len(lit) - 1, int(v * len(lit))))])
        elif part[0] == "side":
            px(img, x, y, DONUT[2] if part[1] < -0.35 else DONUT[1] if part[1] < 0.45 else DONUT[0])
        elif part[0] == "wall":
            px(img, x, y, DONUT[1])
    for x, y in DONUT_GRAINS:
        part = parts.get((x, y))
        if part and part[0] == "top":
            v = _donut_light(part[1], part[2])
            px(img, x, y, DONUT_SUGAR[2] if v > 0.6 else DONUT_SUGAR[1] if v > 0.3 else DONUT_SUGAR[0])
    img = texkit.outline(img)
    for (x, y), part in parts.items():
        if part[0] == "hole":
            img.putpixel((x, y), CLEAR)
    return img


def pancake():
    """A stack of three pancakes, a pat of butter melting on top and syrup running down the side."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for k, y0 in enumerate((11, 8, 5)):  # three cakes, the bottom one first
        for y in range(y0, y0 + 3):
            for x in range(2, 14):
                edge = y == y0 + 2
                c = PANCAKE[0] if edge else PANCAKE[1] if y == y0 + 1 else PANCAKE[2]
                if x in (2, 13) and y == y0:
                    continue
                px(img, x, y, c)
    for x in range(3, 13):  # the top cake's golden face
        px(img, x, 5, PANCAKE[3] if x < 8 else PANCAKE[2])
    for x in range(6, 10):  # the butter
        px(img, x, 4, BUTTER[1])
        px(img, x, 5, BUTTER[0])
    for x, y0, n in ((4, 6, 5), (11, 6, 7), (8, 7, 2)):  # syrup running down
        for y in range(y0, y0 + n):
            px(img, x, y, SYRUP[1] if y < y0 + n - 1 else SYRUP[0])
    for x in range(3, 13):
        if x not in (6, 7, 8, 9):
            px(img, x, 6, SYRUP[2])
    return outline(img)


def pancake_batter():
    """Batter in a white enamel jug with a blue rim, seen a little from above so the thick pale batter shows inside,
    a drip of it running from the lip."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(6, 15):  # the jug's body, bellied a little
        a, b = (4, 11) if 7 < y < 13 else (5, 10)
        for x in range(a, b + 1):
            c = JUG[3] if x < 6 else JUG[2] if x < 9 else JUG[1]
            if x == b or y == 14:
                c = JUG[0]
            px(img, x, y, c)
    for x in range(4, 12):  # the rim, seen from above: blue enamel round the batter
        px(img, x, 4, JUG_RIM)
        px(img, x, 6, JUG_RIM)
    px(img, 4, 5, JUG_RIM)
    px(img, 11, 5, JUG_RIM)
    for x in range(5, 11):
        px(img, x, 5, BATTER[2] if x < 8 else BATTER[1])
    px(img, 3, 4, JUG_RIM)  # the lip, and the drip down the side
    px(img, 3, 5, BATTER[1])
    px(img, 4, 7, BATTER[1])
    px(img, 4, 8, BATTER[0])
    for y in range(7, 12):  # the handle
        px(img, 13, y, JUG[1])
    px(img, 12, 7, JUG[1])
    px(img, 12, 11, JUG[1])
    return outline(img)


def cob(img, rows, kernels, seams):
    """The cob drawn from a grid of letters, lying from lower left up to the right: A to D are its kernels by how lit
    they are (A along the lit upper side, D along the shadowed lower one). The kernels run in rows along the cob - the
    diagonal x + y = const, every other one a seam between rows - and along each row alternate a shade, so that each
    kernel shows. Other letters are left to the caller."""
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in "ABCD":
                lit = "ABCD".index(ch)
                if (x + y) % 2:
                    px(img, x, y, seams[lit])
                else:
                    px(img, x, y, kernels[lit] if (x - y) // 2 % 2 == 0 else kernels[min(lit + 1, 3)])


def paint(img, rows, colours):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in colours:
                px(img, x, y, colours[ch])


# The ear and the grilled ear share one cob: plump, rounded at its tip, its cut stem at the bottom left (where a
# Thin Rope ties it on, by the stalk, rope/HungBy.java).
EAR = [
    "............AB..",
    "...........ABBC.",
    "..........ABBCCD",
    ".........ABBCCD.",
    "........ABBCCD..",
    ".......ABBCCD...",
    "....1.ABBCCD.3..",
    "....12ABCCD.24..",
    "....12ABCD.23...",
    "....121BC2333...",
    "....1212D234....",
    "....121233......",
    "...1212334......",
    "..tu21334.......",
    ".tu.334.........",
    "tu..............",
]
GRILLED = [
    "............AB..",
    "...........ABBC.",
    "..........ABBCCD",
    ".........ABBCCD.",
    "........ABBCCD..",
    ".......ABBCCD...",
    "......ABBCCD....",
    ".....ABBCCD.....",
    "....ABBCCD......",
    "...ABBCCD.......",
    "...ABCCD........",
    "....BCD.........",
    "...tu...........",
    "..tu............",
    ".tu.............",
    "tu..............",
]


def corn():
    """An ear of corn fresh off the stalk, as it is drawn the world over: the husk still round its lower half, two
    leaves cradling the cob and flaring at their points - the near one lit along its edge where it lies over the far
    one, a vein down it - and the plump yellow cob standing out of them, the cut stem below."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    cob(img, EAR, EAR_KERNEL, EAR_SEAM)
    paint(img, EAR, {"1": EAR_HUSK[0], "2": EAR_HUSK[1], "3": EAR_HUSK[2], "4": EAR_HUSK[3], "t": SHANK[0], "u": SHANK[1]})
    return outline(img)


def grilled_corn():
    """The same cob husked and grilled on the fire, brushed with soy sauce: golden-brown kernels, charred here and
    there, on a skewer driven into the stem end."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    cob(img, GRILLED, GLAZE_KERNEL, GLAZE_SEAM)
    rng = random.Random(11)
    for y, row in enumerate(GRILLED):
        for x, ch in enumerate(row):
            if ch in "ABCD" and rng.random() < 0.13:
                px(img, x, y, CHAR[0] if rng.random() < 0.4 else CHAR[1])
    paint(img, GRILLED, {"t": SHANK[0], "u": SHANK[1]})
    return outline(img)


def corn_kernels():
    """Seed corn: four dried kernels scattered as vanilla's seeds are, each a wedge seen face on - the crown pale and
    dented, the body narrowing to the pale tip cap it sat on the cob by - lying this way and that."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    kernel = ["CCc",
              "LMD",
              "LMD",
              ".T."]
    for x0, y0, turns in ((2, 2, 0), (9, 3, 1), (3, 9, 3), (10, 10, 0)):
        shape = kernel
        for _ in range(turns):  # a quarter turn clockwise
            shape = ["".join(row[i] for row in reversed(shape)) for i in range(len(shape[0]))]
        for dy, row in enumerate(shape):
            for dx, ch in enumerate(row):
                if ch in SEED_CORN:
                    px(img, x0 + dx, y0 + dy, SEED_CORN[ch])
    return outline(img)


def onion_width(t):
    """The bulb's half width at {@code t} down it (0 its top, 1 its root): drawn up to a point from its widest, a little
    below the middle, as an onion narrows into its neck; round below, to the root."""
    if t < 0.58:
        return 6.0 * math.sin(math.pi / 2 * t / 0.58) ** 2.2
    return 6.0 * math.sqrt(max(0.0, 1 - ((t - 0.58) / 0.42) ** 2))


def onion():
    """
    An onion: the bulb drawn up to a point at its neck and round below, lit from the upper left as a ball is, with a
    sheen on its papery skin; the lines of its skin running from the neck to the root, bowed with the bulb - a step
    darker in shade, two where the light is, so they show; a dry twisted neck at the top and a tuft of roots below.
    Ringed and finished here: the finish's grain over the skin hid its lines.
    """
    img = Image.new("RGBA", (16, 16), CLEAR)
    top, bottom, cx = 3.4, 14.0, 7.5
    for y in range(16):
        t = (y + 0.5 - top) / (bottom - top)
        if not 0 < t < 1:
            continue
        w = onion_width(t)
        v = (t - 0.58) / 0.42 if t > 0.58 else (t - 0.58) / 0.58  # -1 at the top, 1 at the root
        for x in range(16):
            u = (x + 0.5 - cx) / max(w, 0.01)  # across the bulb, -1 to 1
            if abs(u) > 1:
                continue
            k = math.sqrt(1 - v * v)
            lit = -0.55 * u * k - 0.6 * v + 0.58 * math.sqrt(1 - u * u) * k
            shade = 4 if lit > 0.7 else 3 if lit > 0.4 else 2 if lit > 0.05 else 1 if lit > -0.32 else 0
            if 0.1 < t < 0.94 and any(abs(u - line) < 0.5 / max(w, 1) + 0.02 for line in (-0.62, -0.2, 0.25, 0.66)):
                shade = max(0, shade - (2 if shade >= 3 else 1))
            px(img, x, y, ONION[shade])
    for x, y in ((5, 6), (4, 7), (5, 7)):
        px(img, x, y, ONION[5])
    for x, y, c in ((8, 3, 1), (8, 2, 2), (9, 1, 1), (10, 1, 0)):  # the neck
        px(img, x, y, ONION_NECK[c])
    img = texkit.outline(img)
    for x, y, c in ((6, 14, 0), (7, 15, 1), (8, 15, 0), (9, 14, 1)):  # the roots, after the ring: a wisp, not a bar
        px(img, x, y, ONION_ROOT[c])
    return img


# ---------------------------------------------------------------------------------------------------------- the dough
def dough_skin(colours, seed, scored=False):
    """The dough's skin, for the model: soft patches of shade and a dusting of flour (risen, fewer, and smoother)."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16), colours[2] + (255,))
    for y in range(16):
        for x in range(16):
            r = rng.random()
            if r < 0.12:
                px(img, x, y, colours[1])
            elif r < 0.16:
                px(img, x, y, colours[3])
            elif r < 0.19 and not scored:
                px(img, x, y, FLOUR_DUST)
    if scored:  # the cross cut into the top, opened by the rising
        for i in range(-3, 4):
            px(img, 8 + i, 8 + i, colours[0])
            px(img, 8 + i, 8 - i, colours[0])
    return img


# A ball of dough in section, from the bottom up: (half width, height) of each layer. Set down it sags into a bun, a
# little wider at its middle than where it sits; risen, it swells into a taller dome.
BALL = [(2.5, 0.8), (3.0, 1.0), (2.6, 0.7), (1.8, 0.5)]
RISEN_BALL = [(3.0, 1.0), (3.6, 1.6), (3.3, 1.0), (2.6, 0.8), (1.6, 0.6)]


def ball_model(risen):
    """
    One ball of dough, centred on its spot and sitting on it, built up in layers - narrow where it sits, widest a little
    above, then rounding over to its top - so it reads as a soft ball and not a box; risen, taller and fuller. The tray
    draws it at each place, a little smaller when it shares the tray, and swells it as it rises.
    """
    tex = "nookworks:block/risen_dough" if risen else "nookworks:block/bread_dough"
    els = []
    y = 0.0
    for w, h in (RISEN_BALL if risen else BALL):
        x0, x1, z0, z1 = 8 - w, 8 + w, 8 - w, 8 + w
        els.append({"from": [round(x0, 3), round(y, 3), round(z0, 3)], "to": [round(x1, 3), round(y + h, 3), round(z1, 3)],
                    "faces": {f: {"texture": "#dough",
                                  "uv": [round(x0, 3), round(z0, 3), round(x1, 3), round(z1, 3)] if f in ("up", "down")
                                  else [round(x0, 3), round(16 - y - h, 3), round(x1, 3), round(16 - y, 3)]}
                              for f in ("north", "south", "east", "west", "up", "down")}})
        y += h
    return {"parent": "minecraft:block/block", "textures": {"particle": tex, "dough": tex}, "elements": els}


def tray_model():
    """
    Facing north: a board of oak planks 14 px across and one high, and a rim of stripped oak one high round its edge -
    the front and back rails the full width, the side ones between them. Faces that meet another part are left out.
    """
    def face(uv, tex):
        return {"uv": uv, "texture": tex}
    board = {"from": [1, 0, 1], "to": [15, 1, 15], "faces": {
        "up": face([1, 1, 15, 15], "#board"), "down": face([1, 1, 15, 15], "#board"),
        "north": face([1, 15, 15, 16], "#board"), "south": face([1, 15, 15, 16], "#board"),
        "east": face([1, 15, 15, 16], "#board"), "west": face([1, 15, 15, 16], "#board")}}
    rails = []
    for z0, z1 in ((1, 2), (14, 15)):  # back and front, the full width
        rails.append({"from": [1, 1, z0], "to": [15, 2, z1], "faces": {
            "up": face([1, z0, 15, z1], "#rim"), "north": face([1, 14, 15, 15], "#rim"), "south": face([1, 14, 15, 15], "#rim"),
            "east": face([z0, 14, z1, 15], "#rim"), "west": face([z0, 14, z1, 15], "#rim")}})
    for x0, x1 in ((1, 2), (14, 15)):  # the sides, between them
        rails.append({"from": [x0, 1, 2], "to": [x1, 2, 14], "faces": {
            "up": face([x0, 2, x1, 14], "#rim"), "east": face([2, 14, 14, 15], "#rim"), "west": face([2, 14, 14, 15], "#rim")}})
    return {"parent": "minecraft:block/block", "textures": {"particle": "minecraft:block/oak_planks", "board": "minecraft:block/oak_planks",
                                                            "rim": "minecraft:block/stripped_oak_log"},
            "elements": [board] + rails}


# ---------------------------------------------------------------------------------------------------------- the machines
def box(frm, to, tex, faces="nsewud", uv=None):
    names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    out = {}
    for f in faces:
        face = {"texture": tex[f] if isinstance(tex, dict) else tex}
        if uv:
            face["uv"] = uv
        out[names[f]] = face
    return {"from": frm, "to": to, "faces": out}


def disc(x0, x1, y0, y1, tex):
    """A round stone, stepped as a pixel circle centred in the block: columns two pixels wide, each as deep as the
    circle is there - three or so boxes that do not overlap, so their tops never fight."""
    cx, r = (x0 + x1) / 2, (x1 - x0) / 2
    els = []
    x = x0
    while x < x1 - 0.01:
        xe = min(x1, x + 2)
        d = max(abs(x - cx), abs(xe - cx)) - 1
        half = round(math.sqrt(max(0.0, r * r - d * d)) * 2) / 2
        els.append(box([x, y0, 8 - half], [xe, y1, 8 + half], tex))
        x = xe
    return els


def quern_model(part="all"):
    """
    Facing north: the lower stone on the ground with a spout at the front for the flour, the upper stone on it, both
    round; two spruce posts and a crossbeam over them, as the Oil Press has; and the iron spindle up from the upper
    stone's eye through the beam, a bar across its top to turn it by and a grip standing up off the bar's end. (The
    user's pick of four, 2026-10-10: the stones were square and a Motor beside them turned them by nothing that met;
    a quern is turned by its spindle, from above or below.)

    In parts: the base (stones, spout, frame - all that stands still); the crank (the upper stone, the spindle and the
    bar: what turns); the crank as a Motor above leaves it ("crank_driven": the bar is off, where the motor sits); and
    what a Motor fastens by ("axle_up": a coupling on the spindle's top; "axle_down": an iron plate under the stones,
    bolted down onto the motor). They are drawn by client/render/CrankRenderer.
    """
    textures = {"particle": "minecraft:block/smooth_stone", "stone": "minecraft:block/smooth_stone",
                "stone_side": "minecraft:block/smooth_stone_slab_side", "upper": "minecraft:block/polished_andesite",
                "eye": "minecraft:block/tuff", "peg": "minecraft:block/stripped_oak_log", "iron": "nookworks:block/black_iron",
                "post": "minecraft:block/stripped_spruce_log", "planks": "minecraft:block/spruce_planks"}
    side = {"n": "#stone_side", "s": "#stone_side", "e": "#stone_side", "w": "#stone_side", "u": "#stone", "d": "#stone"}
    base = disc(2, 14, 0, 4, side) + [
        box([6, 1, 0], [10, 3, 2], {"n": "#stone_side", "s": "#stone_side", "e": "#stone_side", "w": "#stone_side",
                                    "u": "#eye", "d": "#stone"}),
        # the frame: two posts on the ground either side of the stones, clear of the upper one as it turns, and the beam
        box([0.5, 0, 7], [2, 12, 9], "#post"), box([14, 0, 7], [15.5, 12, 9], "#post"),
        box([0.5, 12, 6.5], [15.5, 14, 9.5], "#planks")]
    stone = disc(3, 13, 4, 8, "#upper") + [box([7, 8, 7], [9, 8.02, 9], "#eye", faces="u"),
                                           box([7.25, 8, 7.25], [8.75, 16, 8.75], "#iron")]
    # the bar in two either side of the spindle's head (laid over it, the tops fought), and the grip off its end -
    # above the block, so its faces name their part of the log
    grip = box([2, 16, 7], [3, 18, 9], "#peg", uv=[7, 3, 9, 5])
    for side in ("north", "south", "up", "down"):
        grip["faces"][side]["uv"] = [7, 3, 8, 5]  # a pixel across, as the grip is: the log's grain unstretched
    bar = [box([2, 15, 7.5], [7.25, 16, 8.5], "#iron"), box([8.75, 15, 7.5], [14, 16, 8.5], "#iron"), grip]
    if part == "crank":
        return {"textures": textures, "elements": stone + bar}
    if part == "crank_driven":
        return {"textures": textures, "elements": stone}
    if part == "axle_up":
        return {"textures": textures, "elements": [box([6.5, 14.5, 6.5], [9.5, 16, 9.5], "#iron")]}
    if part == "axle_down":
        # a plate the stones sit on, a little wider than the lower stone, its corners bolted down
        return {"textures": textures, "elements": [box([1, 0, 1], [15, 1, 15], "#iron")]}
    els = list(base)
    if part == "all":
        els += stone + bar
    return {"parent": "minecraft:block/block", "textures": textures, "elements": els}


def noodle_maker_model(part="all"):
    """Facing north: a board, a black iron box of rollers clamped on it, a tray on top where the dough goes in, a slot
    at the front where the noodles come out, and a handle on the east side on the rollers' shaft."""
    textures = {"particle": "nookworks:block/black_iron", "iron": "nookworks:block/black_iron",
                "board": "minecraft:block/spruce_planks", "roller": "minecraft:block/iron_block",
                "chute": "minecraft:block/hopper_outside", "chute_in": "minecraft:block/hopper_inside",
                "grip": "minecraft:block/stripped_spruce_log", "slot": "minecraft:block/black_concrete"}
    base = [box([1, 0, 2], [15, 2, 14], "#board"),
            # the frame: two cast-iron cheeks standing on the board, a stay across the back and one across the foot
            box([4, 2, 5], [5, 11, 11], "#iron"), box([11, 2, 5], [12, 11, 11], "#iron"),
            box([5, 2, 9], [11, 10, 11], "#iron"), box([5, 2, 5], [11, 3, 9], "#iron"),
            # the rollers between the cheeks, bright where they are worn, one over the other, and the cutter comb
            box([5, 7, 6], [11, 9, 9], "#roller"), box([5, 4.5, 6], [11, 6.5, 9], "#roller"),
            box([5, 3, 5.5], [11, 4, 6], "#slot"),
            # the feed chute on top, where the sheet of dough goes in
            box([4.5, 11, 4.5], [11.5, 13, 11.5], {"n": "#chute", "s": "#chute", "e": "#chute", "w": "#chute",
                                                    "u": "#chute_in", "d": "#chute"}),
            box([2, 2, 6], [4, 4, 10], "#iron"),     # the clamp
            box([12, 6, 7], [13, 8, 9], "#iron")]    # the shaft's bearing on the east side
    crank = [box([13, 6, 7], [14, 8, 9], "#iron"), box([14, 6, 7], [15, 13, 9], "#iron"),
             box([15, 11, 7.5], [16, 13, 8.5], "#grip")]
    axle = [box([6.5, 6.5, 0], [9.5, 9.5, 4], "#iron"), box([5, 5, 3.5], [11, 11, 4], "#iron")]
    if part == "crank":
        return {"textures": textures, "elements": crank}
    if part == "axle":
        return {"textures": textures, "elements": axle}
    els = list(base)
    if part == "all":
        els += crank
    return {"parent": "minecraft:block/block", "textures": textures, "elements": els}


FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def driven_states(name):
    """The machine turned to its facing; its handle (and a Motor's axle) are drawn by client/render/CrankRenderer."""
    return {"multipart": [{"when": {"facing": f}, "apply": dict({"model": f"nookworks:block/{name}_base"}, **({"y": y} if y else {}))}
                          for f, y in FACINGS]}


# ---------------------------------------------------------------------------------------------------------- the crops
LEAF = [(40, 88, 34), (66, 126, 46), (100, 160, 62), (146, 196, 96)]
STALK = [(64, 110, 40), (92, 142, 52)]
TASSEL = [(170, 150, 80), (214, 196, 120)]
RIPE_LEAF = [(96, 110, 44), (136, 146, 62), (170, 172, 90), (200, 196, 124)]


def leaf(img, x0, y0, dx, length, colours, droop=0.0):
    """A long corn leaf from the stalk at ({@code x0}, {@code y0}) out to one side, arching over and down at the tip:
    two pixels broad for the first half of its length, where a corn leaf is broad, then narrowing to its point."""
    last = None
    for i in range(length):
        t = i / max(1, length - 1)
        x = round(x0 + dx * i)
        y = round(y0 - (1.2 * i) + droop * i * i)
        c = colours[1] if t < 0.4 else colours[2] if t < 0.85 else colours[3]
        px(img, x, y, c)
        if t < 0.5:  # the broad part: its underside, a shade darker
            px(img, x, y + 1, colours[0] if t < 0.25 else colours[1])
        if last and abs(y - last[1]) > 1:
            px(img, x, (y + last[1]) // 2, c)
        last = (x, y)


def corn_lower(stage):
    img = Image.new("RGBA", (16, 16), CLEAR)
    heights = [4, 8, 13, 16, 16, 16]
    h = heights[stage]
    colours = RIPE_LEAF if stage == 5 else LEAF
    for y in range(16 - h, 16):  # the stalk, two pixels wide from stage 2
        px(img, 7, y, STALK[0])
        if stage >= 2:
            px(img, 8, y, STALK[1])
    specs = {0: [(7, 14, -1, 3, 0.35), (8, 13, 1, 3, 0.35)],
             1: [(7, 14, -1, 5, 0.25), (8, 12, 1, 5, 0.25), (7, 10, -1, 3, 0.3)],
             2: [(7, 14, -1, 6, 0.18), (8, 11, 1, 6, 0.2), (7, 8, -1, 5, 0.2), (8, 5, 1, 4, 0.25)],
             3: [(7, 14, -1, 7, 0.15), (8, 11, 1, 7, 0.16), (7, 7, -1, 6, 0.18), (8, 3, 1, 5, 0.2)],
             4: [(7, 14, -1, 7, 0.15), (8, 11, 1, 7, 0.16), (7, 7, -1, 6, 0.18), (8, 3, 1, 5, 0.2)],
             5: [(7, 14, -1, 7, 0.12), (8, 11, 1, 7, 0.12), (7, 7, -1, 6, 0.15), (8, 3, 1, 5, 0.17)]}
    for x0, y0, dx, n, droop in specs[stage]:
        leaf(img, x0 + dx, y0, dx, n, colours, droop)
    if stage >= 4:  # the ears on the stalk: green husks, gold silk; at the last stage the husk yellows
        husk = DRY_HUSK if stage == 5 else HUSK
        for y in range(6, 11):
            px(img, 9, y, husk[1])
            px(img, 10, y + 1, husk[0] if stage == 4 else husk[2])
        px(img, 9, 5, SILK[1])
        px(img, 10, 5, SILK[0])
        if stage == 5:
            px(img, 9, 8, CORN[2])
            px(img, 9, 9, CORN[1])
    return img


def corn_upper(stage):
    img = Image.new("RGBA", (16, 16), CLEAR)
    heights = {3: 6, 4: 12, 5: 14}
    h = heights[stage]
    colours = RIPE_LEAF if stage == 5 else LEAF
    for y in range(16 - h, 16):
        px(img, 7, y, STALK[0])
        px(img, 8, y, STALK[1] if y > 16 - h + 2 else STALK[0])
    leafs = {3: [(7, 15, -1, 5, 0.25), (8, 13, 1, 4, 0.3)],
             4: [(7, 15, -1, 7, 0.14), (8, 12, 1, 6, 0.18), (7, 9, -1, 4, 0.3)],
             5: [(7, 15, -1, 7, 0.12), (8, 12, 1, 6, 0.15), (7, 9, -1, 4, 0.25)]}
    for x0, y0, dx, n, droop in leafs[stage]:
        leaf(img, x0 + dx, y0, dx, n, colours, droop)
    if stage >= 4:  # the tassel at the top
        top = 16 - h
        for x, y in ((6, top), (9, top), (5, top + 1), (10, top + 1), (7, top - 1), (8, top - 1)):
            px(img, x, y, TASSEL[1] if stage == 5 else TASSEL[0])
        px(img, 4, top + 2, TASSEL[0])
        px(img, 11, top + 2, TASSEL[0])
    return img


ONION_LEAF = [(34, 80, 46), (54, 114, 60), (84, 148, 80), (98, 156, 88)]     # glaucous, blue-green; the tips soft
ONION_RIPE = [(112, 116, 50), (150, 150, 70), (186, 176, 98), (214, 200, 132)]  # fallen over, turning to straw
ONION_TOP = [(118, 66, 28), (164, 104, 48), (204, 146, 76), (232, 188, 118)]    # the top of a bulb, at the ground

# Three plants to a picture, as vanilla's crops have: (foot x, [(control dx, control y, tip dx, tip y), ...]), each
# leaf a curve from its foot at the ground by the control point to its tip (y counts down from the top).
ONION_PLANTS = {
    0: [(3, [(0, 13, -1, 11), (0, 13, 1, 12)]), (8, [(0, 12, 1, 10), (0, 13, -1, 11)]), (12, [(0, 13, 1, 11)])],
    1: [(3, [(-1, 11, -2, 8), (0, 10, 1, 7), (1, 12, 2, 10)]), (8, [(0, 10, -1, 6), (1, 10, 2, 7), (-1, 12, -3, 10)]),
        (13, [(0, 11, 1, 8), (-1, 11, -2, 9)])],
    2: [(3, [(-1, 9, -3, 4), (0, 8, 0, 3), (1, 10, 3, 6), (-1, 12, -3, 10)]),
        (8, [(0, 8, -1, 2), (1, 9, 3, 4), (-1, 10, -3, 6), (1, 12, 3, 11)]),
        (13, [(0, 8, 0, 3), (-1, 10, -2, 5), (1, 11, 2, 8)])],
    3: [(3, [(-1, 4, -3, 11), (1, 5, 4, 10), (0, 8, -2, 13)]),  # ripe: the leaves broken over (倒伏)
        (8, [(0, 3, 4, 9), (-1, 5, -3, 11), (1, 9, 2, 13)]),
        (13, [(-1, 4, -4, 10), (1, 6, 2, 11)])],
}


def onion_leaf(img, p0, p1, p2, colours, fade):
    """A hollow leaf 1 px wide along a quadratic curve: pale near the foot, then green, the tip lighter."""
    last = None
    for i in range(121):
        t = i / 120
        p = (int(round((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0])),
             int(round((1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1])))
        if p != last:
            px(img, p[0], p[1], colours[2] if t < fade else colours[1] if t < 0.85 else colours[3])
            last = p


def onion_top(img, cx, width, rows):
    """The top of a bulb showing at the ground, `rows` high: the rest of it is under the soil."""
    for r in range(rows):
        w = width - 2 * r
        for x in range(cx - w // 2, cx - w // 2 + w):
            c = ONION_TOP[2] if x < cx else ONION_TOP[1]
            if r == rows - 1 and x == cx - w // 2 + (w - 1) // 2:
                c = ONION_TOP[3]
            px(img, x, 15 - r, c)


def onions(stage):
    """Onions seen side on, as carrots are: hollow leaves in fans; the bulbs swell under the ground, and only their
    tops show at the soil - a sliver while they grow, a little more when ripe, the leaves fallen over and yellowing."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for x0, leaves in ONION_PLANTS[stage]:
        for cdx, cy, tdx, ty in leaves:
            if stage == 3:  # green up to where it broke, straw-yellow past it
                onion_leaf(img, (x0, 15.4), (x0 + cdx, cy), (x0 + tdx, ty), [ONION_RIPE[0], ONION_RIPE[1], ONION_LEAF[1], ONION_RIPE[3]], 0.5)
            else:
                onion_leaf(img, (x0, 15.4), (x0 + cdx, cy), (x0 + tdx, ty), ONION_LEAF, 0.3)
    if stage >= 2:
        for x0, _ in ONION_PLANTS[stage]:
            onion_top(img, x0, 5 if stage == 3 else 3, 2 if stage == 3 else 1)
    return img


# ---------------------------------------------------------------------------------------------------------- output
def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def item_model(name):
    write_json(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"nookworks:item/{name}"}})
    write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:item/{name}"}})


def main():
    tex = ASSETS / "textures"
    items = {"bread_dough": bread_dough(), "risen_dough": risen_dough(), "udon_dough": udon_dough(), "raw_udon": raw_udon(),
             "udon": udon(), "cream_stew": cream_stew(), "corn_potage": corn_potage(), "donut": donut(), "pancake": pancake(),
             "pancake_batter": pancake_batter(), "corn": corn(), "grilled_corn": grilled_corn(), "onion": onion(),
             "corn_kernels": corn_kernels()}
    # the kernels are the cob's grain already, and a seed's crown and tip are its marks: left as drawn
    kept = {"corn": EAR_KERNEL + EAR_SEAM, "grilled_corn": GLAZE_KERNEL + GLAZE_SEAM + CHAR,
            "corn_kernels": [SEED_CORN["C"], SEED_CORN["T"]]}
    # ringed already and left without grain, which over their smooth skins and coils would read as lumps
    finished = {"bread_dough", "risen_dough", "udon_dough", "raw_udon", "onion", "donut"}
    for i, (name, img) in enumerate(items.items()):
        if name not in finished:
            img = texkit.icon(img, 300 + i, keep=[FLOUR_DUST, SUGAR, JUG_RIM] + kept.get(name, []))
        img.save(tex / f"item/{name}.png")
        item_model(name)
    texkit.shade(dough_skin(DOUGH, 1), 320, tile=True, keep=[FLOUR_DUST], **texkit.SMOOTH).save(tex / "block/bread_dough.png")
    texkit.shade(dough_skin(RISEN, 2, scored=True), 321, **texkit.SMOOTH).save(tex / "block/risen_dough.png")
    write_json(ASSETS / "models/block/dough_ball.json", ball_model(False))
    write_json(ASSETS / "models/block/dough_ball_risen.json", ball_model(True))
    write_json(ASSETS / "models/block/wooden_tray.json", tray_model())
    write_json(ASSETS / "blockstates/wooden_tray.json", {"variants": {
        f"facing={facing}": {"model": "nookworks:block/wooden_tray", **({"y": y} if y else {})} for facing, y in FACINGS}})
    write_json(ASSETS / "items/wooden_tray.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/wooden_tray"}})
    for name, model, parts in (("hand_quern", quern_model, ("base", "crank", "crank_driven", "axle_up", "axle_down")),
                               ("noodle_maker", noodle_maker_model, ("base", "crank", "axle"))):
        write_json(ASSETS / f"models/block/{name}.json", model())
        for part in parts:
            write_json(ASSETS / f"models/block/{name}_{part}.json", model(part))
        write_json(ASSETS / f"blockstates/{name}.json", driven_states(name))
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:block/{name}"}})
    corn_variants = {}
    for stage in range(6):
        texkit.shade(corn_lower(stage), 330 + stage, **texkit.PLANT).save(tex / f"block/corn_stage{stage}.png")
        write_json(ASSETS / f"models/block/corn_stage{stage}.json", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
                                                                     "textures": {"cross": f"nookworks:block/corn_stage{stage}"}})
        corn_variants[f"age={stage},half=lower"] = {"model": f"nookworks:block/corn_stage{stage}"}
        if stage >= 3:
            texkit.shade(corn_upper(stage), 340 + stage, **texkit.PLANT).save(tex / f"block/corn_top_stage{stage}.png")
            write_json(ASSETS / f"models/block/corn_top_stage{stage}.json", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
                                                                             "textures": {"cross": f"nookworks:block/corn_top_stage{stage}"}})
            corn_variants[f"age={stage},half=upper"] = {"model": f"nookworks:block/corn_top_stage{stage}"}
        else:  # a top half never exists this young; the state does, so give it the nearest look
            corn_variants[f"age={stage},half=upper"] = {"model": "nookworks:block/corn_top_stage3"}
    write_json(ASSETS / "blockstates/corn.json", {"variants": corn_variants})
    for stage in range(4):
        texkit.shade(onions(stage), 350 + stage, keep=[CLEAR], reach=1, **texkit.PLANT).save(tex / f"block/onions_stage{stage}.png")
        write_json(ASSETS / f"models/block/onions_stage{stage}.json", {"parent": "minecraft:block/crop", "render_type": "minecraft:cutout",
                                                                       "textures": {"crop": f"nookworks:block/onions_stage{stage}"}})
    stage_of_age = [0, 0, 1, 1, 2, 2, 2, 3]  # as carrots and potatoes show their eight ages in four pictures
    write_json(ASSETS / "blockstates/onions.json", {"variants": {f"age={a}": {"model": f"nookworks:block/onions_stage{stage_of_age[a]}"}
                                                                 for a in range(8)}})
    print("wrote the kitchen")


if __name__ == "__main__":
    main()
