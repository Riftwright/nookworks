"""The cooking: the Grill, the Iron Pot and the food that comes out of them. All original pixel art, drawn flat and
finished with texkit; and the models and blockstates. Deterministic.

- block/black_iron.png, block/grill_grate.png  the Grill (its legs and frame; its grate, cutout) and the Iron Pot
- models/block/grill.json (over a campfire), grill_standing.json (on its own), blockstates/, items/
- The Iron Pot: a deep black iron pot with a bail handle each side, with water (vanilla water_still, tinted like
  water) or oil (vegetable_oil_still) standing in it while it has some - models/block/iron_pot[_water|_oil][_on_grill]
- item/cooked_rice, vegetable_soup, fried_rice, stir_fried_vegetables (in bowls); salt_onigiri, salmon_onigiri;
  french_fries, karaage, fried_fish, tonkatsu; carrot_nukazuke, beetroot_nukazuke; flour, breadcrumbs; fried_egg,
  boiled_egg, tamagoyaki - the dishes
  (tonkatsu and the pickles) placed and ringed piece by piece to itemkit's bar, without the finish's grain
- The Nukadoko: a tub of wooden staves with an iron hoop, full to the rim with bran - block/nukadoko_side.png,
  nukadoko_top.png, models/block/nukadoko.json

Run from the repo root:  python tools/textures/gen_cooking.py
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

BOWL = [(72, 44, 24), (104, 66, 36), (136, 90, 52), (164, 116, 70)]  # a turned wooden bowl, dark to light
RICE = [(200, 196, 184), (230, 228, 220), (248, 247, 242)]
NORI = [(18, 28, 22), (30, 44, 34), (48, 66, 50)]
SALMON = [(196, 92, 70), (232, 128, 98), (250, 170, 140)]
BROTH = [(170, 92, 38), (204, 126, 52), (232, 164, 84)]


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c + (255,) if len(c) == 3 else c)


# Black cast iron, deepest to the cool edge a lit rim catches: the shadows a touch bluer, the lights a touch warmer
# (until the very edge, which takes the sky's blue), as a dark metal is painted.
CAST_IRON = [(24, 24, 30), (35, 35, 42), (48, 47, 54), (61, 60, 66), (78, 77, 86), (110, 114, 126)]


def _wrapping_noise(seed, cells):
    """Smooth noise 0..1 over a 16 px tile that wraps at its edges: random values on a {@code cells} x {@code cells}
    grid, eased between."""
    rng = random.Random(seed)
    grid = [[rng.random() for _ in range(cells)] for _ in range(cells)]

    def at(x, y):
        u, v = x * cells / 16, y * cells / 16
        x0, y0 = int(u) % cells, int(v) % cells
        x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
        fx, fy = u - int(u), v - int(v)
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        top = grid[y0][x0] * (1 - fx) + grid[y0][x1] * fx
        bottom = grid[y1][x0] * (1 - fx) + grid[y1][x1] * fx
        return top * (1 - fy) + bottom * fy
    return at


def black_iron():
    """
    Black cast iron, as an old pan or a stove is: one dark grey with a cold cast to it, mottled in soft patches two to
    four pixels across - a shade darker where the casting sand left it duller, a shade lighter where it is worn - and a
    few pits, each a dark pixel with its far rim catching the light under it, and a few single specks of light. No
    rust: black iron in use is kept oiled. Tiles every way (the noise wraps), as it covers the Grill, the Iron Pot and
    a score of machines' frames, shafts and guards.
    """
    broad, fine = _wrapping_noise(9, 4), _wrapping_noise(10, 8)
    field = {(x, y): broad(x, y) * 0.55 + fine(x, y) * 0.45 for y in range(16) for x in range(16)}
    order = sorted(field, key=field.get)
    shade = {}
    for i, p in enumerate(order):  # by share: about a quarter a shade darker, a fifth a shade lighter, the rest the iron
        shade[p] = 1 if i < 60 else 2 if i < 206 else 3
    img = Image.new("RGBA", (16, 16))
    for (x, y), s in shade.items():
        px(img, x, y, CAST_IRON[s])
    for x, y in ((3, 2), (12, 5), (6, 10), (14, 13), (9, 15)):  # pits: the hollow, its lower rim lit
        px(img, x, y, CAST_IRON[0])
        px(img, x, (y + 1) % 16, CAST_IRON[4])
    for x, y, c in ((8, 3, 4), (1, 7, 5), (11, 9, 4), (4, 14, 5)):  # specks of light
        px(img, x, y, CAST_IRON[c])
    return img


def grate():
    """The grill's grate from above: a black iron frame round the edge, bars across it two pixels apart, and one bar
    down the middle to carry them; the gaps are open (cutout), so the fire shows through."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            px(img, *p, (40, 38, 40))
    for x in range(1, 15):
        for y in range(1, 15):
            if y % 2 == 1 or x in (7, 8):
                px(img, x, y, (58, 54, 56) if y % 2 == 1 else (40, 38, 40))
    return img


def grill_model(over_fire=True):
    """Over a campfire: a grate a pixel thick at the bottom of its block, over the flames, and four legs at the corners
    all the way down to the ground the campfire stands on - it stands beside the fire, not on it. Standing on its own
    (for building): the same grate at the top of its block on legs a block tall."""
    top = 0 if over_fire else 15
    frame = {s: {"texture": "#iron", "uv": [0, 0, 16, 1]} for s in ("north", "east", "south", "west")}
    elements = [{"from": [0, top, 0], "to": [16, top + 1, 16], "faces": {**frame, "up": {"texture": "#grate", "uv": [0, 0, 16, 16]},
                                                                   "down": {"texture": "#grate", "uv": [0, 0, 16, 16]}}}]
    for x, z in ((0, 0), (15, 0), (0, 15), (15, 15)):
        elements.append({"from": [x, -16 if over_fire else 0, z], "to": [x + 1, 0 if over_fire else 15, z + 1],
                         "faces": {**{s: {"texture": "#iron", "uv": [0, 0, 1, 16 if over_fire else 15]}
                                      for s in ("north", "east", "south", "west")},
                                   "down": {"texture": "#iron", "uv": [0, 0, 1, 1]}}})  # a leg's foot, 1 px square
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
            "textures": {"particle": "nookworks:block/black_iron", "iron": "nookworks:block/black_iron",
                         "grate": "nookworks:block/grill_grate"},
            "elements": elements,
            "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}}}


def outline(img):
    """Where the ring goes round the shape: it is drawn at save time by texkit.icon, in the shape's own colours as
    vanilla rings its items, so anything drawn after this call (steam) is left unringed."""
    return img


def bowl(img, top=9):
    """A turned wooden bowl seen a little from above: a rim ellipse at row {@code top}, curving down to a foot."""
    rows = [(1, 14), (1, 14), (2, 13), (3, 12), (4, 11), (5, 10)]
    for i, (a, b) in enumerate(rows):
        y = top + i
        for x in range(a, b + 1):
            c = BOWL[2] if x < 5 and i < 3 else BOWL[1]
            if i == len(rows) - 1 or x == b:
                c = BOWL[0]
            px(img, x, y, c)
    for x in range(2, 14):      # the rim's lit edge
        px(img, x, top, BOWL[3] if x < 9 else BOWL[2])


CHAWAN = [(36, 28, 26), (56, 44, 38), (80, 64, 52), (108, 90, 74), (150, 128, 104)]  # dark earthen glaze, lip


def chawan(img, top=9):
    """
    A rice bowl, as rice is served: a ceramic chawan in a dark earthen glaze, its lip catching the light and a pale
    drip of glaze running down its side, seen a little from above - not the turned wooden bowl the soups and
    stir-fries come in. Dark, so the white of the rice stands off it.
    """
    rows = [(1, 14), (1, 14), (2, 13), (3, 12), (4, 11), (6, 9)]
    for i, (a, b) in enumerate(rows):
        y = top + i
        for x in range(a, b + 1):
            c = CHAWAN[3] if x < 5 and i < 3 else CHAWAN[2] if x < 11 else CHAWAN[1]
            if i == len(rows) - 1 or x == b:
                c = CHAWAN[0]
            px(img, x, y, c)
    for x in range(2, 14):  # the lip
        px(img, x, top, CHAWAN[4] if x < 10 else CHAWAN[3])
    for y in (top + 1, top + 2):  # a drip of paler glaze
        px(img, 10, y, CHAWAN[3])
    px(img, 10, top + 3, CHAWAN[4])


def rice_heap(img, top_row, colours, glint):
    """
    A rounded heap of cooked rice, grain by grain: short two-pixel grains laid every which way, lit on the upper
    left of the heap and shadowed low on its right; the heap's edge left ragged, as grains sit on a heap.
    """
    rng = random.Random(4)
    widths = [2, 4, 5, 6, 6]
    for i, half in enumerate(widths):
        y = top_row + i
        for x in range(8 - half, 8 + half):
            if i == 0 and x in (8 - half, 8 + half - 1) and rng.random() < 0.5:
                continue  # a grain missing from the heap's top edge
            light = (x - 8) + (i - 2) * 1.2  # upper left lit
            c = colours[2] if light < -3 else colours[1] if light < 3 else colours[0]
            px(img, x, y, c)
    for _ in range(9):  # grains: a lit pixel beside a shadowed one, lying across or along
        x, y = rng.randrange(4, 12), rng.randrange(top_row + 1, top_row + 5)
        if img.getpixel((x, y))[3]:
            px(img, x, y, colours[2])
            nx, ny = (x + 1, y) if rng.random() < 0.6 else (x, y + 1)
            if img.getpixel((nx, ny))[3]:
                px(img, nx, ny, colours[0])
    for x, y in glint:
        px(img, x, y, colours[3])


def cooked_rice():
    img = Image.new("RGBA", (16, 16), CLEAR)
    chawan(img)
    rice_heap(img, 4, [(200, 196, 182), (234, 232, 222), (250, 249, 243), (255, 255, 255)], ((6, 5), (7, 4)))
    return outline(img)


PAPER_CONE = [(150, 142, 126), (206, 198, 180), (236, 230, 214), (250, 246, 234)]
PAPER_STRIPE = [(150, 40, 34), (196, 64, 52)]


def french_fries():
    """
    Chips standing up out of a cone of striped paper, the way they are sold from a stall: straight sticks a pixel
    apart, their cut ends lit, the cone's rolled top edge and its point, the stripes running round it.
    """
    img = Image.new("RGBA", (16, 16), CLEAR)
    for i, (x, top) in enumerate(((4, 5), (5, 3), (6, 4), (7, 1), (8, 3), (9, 2), (10, 4), (11, 5))):
        for y in range(top, 9):
            c = POTATO[1] if i % 2 else POTATO[0]
            if y == top:
                c = POTATO[2]
            px(img, x, y, c)
        if i % 2 == 0:  # every other chip a shade back, so they read as sticks side by side
            for y in range(top + 1, 9):
                px(img, x, y, (184, 136, 52))
    for y in range(8, 16):  # the cone, down to its point
        half = max(0, 5 - (y - 8) * 5 // 7)
        for x in range(8 - half - 1, 8 + half + 1):
            c = PAPER_CONE[3] if x < 7 else PAPER_CONE[2] if x < 9 else PAPER_CONE[1]
            if (x + y) % 4 in (0, 1):  # stripes wound round it
                c = PAPER_STRIPE[1] if x < 9 else PAPER_STRIPE[0]
            px(img, x, y, c)
    for x in range(2, 14):  # the rolled top edge
        px(img, x, 8, PAPER_CONE[3] if x < 9 else PAPER_CONE[2])
    return outline(img)


def vegetable_soup():
    img = Image.new("RGBA", (16, 16), CLEAR)
    bowl(img, top=8)
    for x in range(2, 14):      # the broth filling the rim
        px(img, x, 8, BROTH[1] if x > 3 else BROTH[2])
    for x in range(3, 13):
        px(img, x, 7, BROTH[1])
    for x in range(5, 11):
        px(img, x, 6, BROTH[0] if x > 8 else BROTH[1])
    for x, y, c in ((4, 7, (232, 140, 40)), (7, 6, (230, 214, 150)), (10, 7, (150, 30, 60)), (8, 8, (232, 140, 40)),
                    (12, 8, (230, 214, 150)), (6, 8, (150, 30, 60))):  # carrot, potato, beetroot bits
        px(img, x, y, c)
    img = outline(img)
    for x, y, a in ((9, 4, 150), (8, 3, 110), (9, 2, 80)):  # a wisp of steam, after the outline so it has none
        px(img, x, y, (240, 240, 240, a))
    return img


def onigiri(salmon):
    img = Image.new("RGBA", (16, 16), CLEAR)
    # the rice: a triangle with its top and bottom corners rounded - half-widths from the top row down
    halves = {3: 1, 4: 2, 5: 3, 6: 3, 7: 4, 8: 4, 9: 5, 10: 5, 11: 6, 12: 6, 13: 5}
    for y, half in halves.items():
        for x in range(8 - half, 8 + half):
            c = RICE[1]
            if x < 8 - half + 2 and y < 10:
                c = RICE[2]
            if x >= 8 + half - 1:
                c = RICE[0]
            px(img, x, y, c)
    for y in range(10, 14):     # the nori wrapped round its base
        for x in range(5, 11):
            px(img, x, y, NORI[1] if x > 5 else NORI[2])
        px(img, 10, y, NORI[0])
    if salmon:                  # a flake of grilled salmon on top
        for x, y, c in ((7, 5, SALMON[2]), (8, 5, SALMON[1]), (7, 6, SALMON[1]), (8, 6, SALMON[0]), (9, 6, SALMON[1])):
            px(img, x, y, c)
    return outline(img)


EGG = [(214, 170, 40), (240, 200, 70), (252, 228, 130)]
CARROT = (232, 140, 40)
GREEN = [(60, 110, 40), (90, 150, 56)]


def fried_rice():
    """A heap of golden rice in a bowl, flecked with egg and green."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    bowl(img)
    for y in range(4, 10):
        half = [2, 4, 5, 6, 6, 6][y - 4]
        for x in range(8 - half, 8 + half):
            c = EGG[1]
            if x < 8 - half + 2 and y < 8:
                c = EGG[2]
            if x >= 8 + half - 2 or y == 9:
                c = EGG[0]
            px(img, x, y, c)
    for x, y, c in ((6, 5, EGG[2]), (9, 6, GREEN[1]), (5, 7, CARROT), (10, 8, EGG[2]), (7, 8, GREEN[0]), (8, 4, CARROT)):
        px(img, x, y, c)
    return outline(img)


def stir_fried_vegetables():
    """Carrot, potato and greens tossed together in a bowl, with a sheen of oil."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    bowl(img, top=9)
    pieces = ((3, 8, CARROT), (4, 8, CARROT), (5, 7, POTATO[1]), (6, 7, POTATO[1]), (7, 6, GREEN[1]), (8, 6, GREEN[0]),
              (9, 7, CARROT), (10, 7, CARROT), (11, 8, POTATO[2]), (12, 8, GREEN[1]), (6, 8, GREEN[0]), (8, 8, POTATO[0]),
              (9, 8, GREEN[1]), (7, 7, CARROT), (10, 8, POTATO[1]), (5, 8, GREEN[1]), (8, 5, POTATO[2]), (11, 7, GREEN[0]))
    for x, y, c in pieces:
        px(img, x, y, c)
    return outline(img)


FRY = [(150, 84, 26), (198, 128, 44), (232, 176, 80), (248, 214, 128)]   # batter, dark to light
POTATO = [(196, 150, 60), (230, 190, 90), (250, 222, 140)]


def karaage():
    """Three knobbly pieces of fried chicken in a heap."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for cx, cy, r in ((5.5, 10.0, 3.4), (10.5, 10.5, 3.2), (8.0, 6.5, 3.3)):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - cx, (y - cy) * 1.1)
                bump = 0.5 * math.sin(x * 2.1 + y * 1.3)
                if d <= r + bump:
                    shade = FRY[3] if d < r * 0.35 and x < cx else FRY[2] if x < cx + 0.5 and y < cy + 0.5 else FRY[1]
                    if d > r + bump - 0.9 and y > cy:  # the shadowed underside, so each piece stands apart
                        shade = FRY[0]
                    px(img, x, y, shade)
    for x, y in ((5, 11), (11, 12), (9, 7), (4, 9)):
        px(img, x, y, FRY[0])
    return outline(img)


def fried_fish():
    """
    A whole small fish fried in batter, side on: a plump body narrowing to the snout at the left, the batter lumpy
    round its edge and crumbed - each crumb lit above the middle and shaded below it - lit from the upper left as a
    rounded thing is, its belly in shade; the battered tail forked at the right. (Flat-shaded it read as a fish-shaped
    cake.)
    """
    img = Image.new("RGBA", (16, 16), CLEAR)
    cx, cy, rx, ry = 6.6, 8.4, 5.4, 3.2
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            if dx < 0:
                dy /= 1 + 0.45 * dx  # the head narrows to its snout
            d = dx * dx + dy * dy
            if d <= (1 + 0.075 * math.sin(7 * math.atan2(dy, dx) + 1.3)) ** 2:
                lam = -0.5 * dx - 0.7 * dy + 0.5 * math.sqrt(max(0.0, 1 - min(1.0, d)))
                shade = 4 if lam > 0.8 else 3 if lam > 0.45 else 2 if lam > 0.0 else 1 if lam > -0.4 else 0
                if (x * 5 + y * 3) % 7 == 0 and 0 < shade < 4:
                    shade += 1 if dy < 0.2 else -1
                px(img, x, y, CUTLET[shade])
                continue
            t = x + 0.5 - (cx + rx - 0.6)  # the tail, past the body
            spread = abs(y + 0.5 - cy)
            if 0 < t <= 3.6 and spread <= 0.6 + t * 0.75 and not (t > 2.2 and spread < t * 0.75 - 1.2):
                px(img, x, y, CUTLET[3] if y + 0.5 < cy else CUTLET[1] if t > 1.5 else CUTLET[2])
    return texkit.outline(img)


STAVE = [(84, 56, 32), (140, 100, 62), (166, 124, 80)]
HOOP = [(46, 44, 48), (78, 76, 82)]
BRAN = [(150, 112, 64), (184, 146, 94), (212, 180, 128)]


def nukadoko_side():
    """Upright staves, a dark joint every four pixels, and two iron hoops round the tub, one under the rim and one
    near the foot. Only rows 2-12 show on the model."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            c = STAVE[0] if x % 4 == 0 else STAVE[2] if x % 4 == 1 else STAVE[1]
            if y in (4, 5, 10, 11):
                c = HOOP[1] if y in (4, 10) else HOOP[0]
            px(img, x, y, c)
    return img


# The top: the model shows columns and rows 1-14 of it (the tub is 14 across), so the staves' ends are drawn there.
NUKA_GRAIN_LIT, NUKA_GRAIN = (150, 108, 66), (124, 86, 50)  # the staves' end grain: the far and left rim, the near and right
NUKA_SHADE = (138, 102, 58)  # bran in the shadow of the far and left staves
NUKA_HUSK = (112, 78, 42)
NUKA_PATTED = (172, 134, 84)  # the smooth floor of a hollow patted down
NUKA_CRUMB = (222, 192, 142)  # a crumb's top, catching the light
NUKA_HOLLOWS = [(9.5, 6.0, 2.3, 1.6), (4.6, 10.0, 2.1, 1.5)]  # centre x, y and half across, half down
NUKA_CRUMBS = [(4, 3, 2, 1), (11, 3, 1, 1), (3, 5, 1, 1), (12, 9, 2, 1), (8, 10, 1, 1), (10, 12, 2, 1), (6, 12, 1, 1),
          (7, 4, 1, 1), (3, 7, 2, 1), (12, 6, 1, 1), (8, 8, 1, 1)]  # x, y, width, height
NUKA_HUSKS = [(9, 3), (5, 8), (13, 11), (7, 13), (11, 9)]


def nukadoko_top():
    """
    The bran filling the tub to the rim, seen from above: the staves' end grain round the edge (rows and columns 1 and
    14, where the model shows them), a dark joint where two meet as on the side; the bran just inside the far and left
    staves in their shadow; crumbs lying on it, each lit on its top and shading the bran below it, a few bits of husk;
    and two hollows patted smooth where it has been dug, in shade on their far side and lit on their near one.
    """
    img = Image.new("RGBA", (16, 16), STAVE[0] + (255,))
    p = img.load()
    for y in range(2, 14):
        for x in range(2, 14):
            p[x, y] = BRAN[1] + (255,)
    for i in range(2, 14):
        p[i, 2] = NUKA_SHADE + (255,)
        p[2, i] = NUKA_SHADE + (255,)

    def hollow(h, x, y):
        cx, cy, rx, ry = h
        return ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1

    for h in NUKA_HOLLOWS:
        for y in range(3, 14):
            for x in range(3, 14):
                if hollow(h, x, y):
                    far = not hollow(h, x - 1, y) or not hollow(h, x, y - 1)
                    near = not hollow(h, x + 1, y) or not hollow(h, x, y + 1)
                    p[x, y] = (BRAN[0] if far else BRAN[2] if near else NUKA_PATTED) + (255,)
    for x0, y0, w, h in NUKA_CRUMBS:
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                p[x, y] = (NUKA_CRUMB if (x, y) == (x0, y0) else BRAN[2]) + (255,)
        for x, y in ((x0 + w, y0 + h - 1), (x0 + w - 1, y0 + h), (x0 + w, y0 + h)):
            if 3 <= x < 14 and 3 <= y < 14 and p[x, y][:3] == BRAN[1]:
                p[x, y] = BRAN[0] + (255,)
    for x, y in NUKA_HUSKS:
        if p[x, y][:3] == BRAN[1]:
            p[x, y] = NUKA_HUSK + (255,)
    for i in range(1, 15):
        joint = i in (4, 8, 12)
        p[i, 1] = (STAVE[0] if joint else NUKA_GRAIN_LIT) + (255,)
        p[1, i] = (STAVE[0] if joint else NUKA_GRAIN_LIT) + (255,)
        p[i, 14] = (STAVE[0] if joint else NUKA_GRAIN) + (255,)
        p[14, i] = (STAVE[0] if joint else NUKA_GRAIN) + (255,)
    return img


def nukadoko_model():
    faces = {s: {"texture": "#side", "uv": [1, 2, 15, 13]} for s in ("north", "east", "south", "west")}
    faces["up"] = {"texture": "#top", "uv": [1, 1, 15, 15]}
    faces["down"] = {"texture": "#side", "uv": [1, 1, 15, 15], "cullface": "down"}
    return {"parent": "minecraft:block/block",
            "textures": {"particle": "nookworks:block/nukadoko_side", "side": "nookworks:block/nukadoko_side",
                         "top": "nookworks:block/nukadoko_top"},
            "elements": [{"from": [1, 0, 1], "to": [15, 11, 15], "faces": faces}]}


DISH = [(132, 142, 160), (196, 204, 218), (232, 236, 244), (250, 252, 255)]  # white china: rim shade, rim, glaze, gleam
BRAN_FLECK = (176, 136, 84)


def dish():
    """A white dish seen from above at a slant, low in the frame: its glazed well, the rim round it lit along the back
    left and in shade along the front, ringed. What is served on it is laid over it, each piece ringed on its own."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            d = ((x + 0.5 - 8.0) / 7.6) ** 2 + ((y + 0.5 - 11.0) / 4.2) ** 2
            if d > 1.0:
                continue
            front = y + 0.5 > 11.0
            c = DISH[2] if 0.3 < d <= 0.74 else DISH[1]
            if d > 0.74:
                c = DISH[0] if front else DISH[3] if x < 7 else DISH[1]
            px(img, x, y, c)
    return texkit.outline(img)


def served(img, piece):
    """{@code piece} drawn on a layer of its own, ringed, and laid over {@code img}."""
    layer = Image.new("RGBA", (16, 16), CLEAR)
    piece(layer)
    img.alpha_composite(texkit.outline(layer))


# Carrot nukazuke (itemkit), laid on the dish: a whole pickled carrot lying corner to corner as vanilla's carrot does,
# its leaves cut off - the cut top pale (y, C, c) with a stub of the green crown (G, g) - tapering to its point at the
# lower left, lit along its upper edge (L), its body (M) and shade (S), the ridges across a carrot notched in (D); in
# front two round slices of it, the orange round a paler core (o, k), their thickness under them. Ringed once in the
# carrot's own dark; flecks of the bran it was buried in. (Three sticks at 45 degrees, each ringed, ran into one
# checkered band and read as fried shrimp; a carrot lying flat without its crown read as one too.)
PICKLED_CARROT = {"y": (255, 240, 200), "C": (255, 226, 160), "c": (252, 196, 104), "L": (255, 170, 72), "o": (246, 150, 50),
                  "k": (255, 200, 110), "M": (232, 120, 26), "S": (186, 74, 10), "D": (150, 52, 6), "G": (120, 140, 60),
                  "g": (92, 110, 44)}
CARROT_SERVED = itemkit.check([
    "................",
    "...........gG...",
    "..........yCcG..",
    ".........LCccS..",
    "........LMMCS...",
    ".......LMDMS....",
    "......LMMDS.....",
    ".....LMDMS......",
    "....LMMSS.......",
    "...LMDS...LLL...",
    "...MSS...LookL..",
    "...S.LLL.MkkoM..",
    "....LookL.SSS...",
    "....MkkoM.......",
    ".....SSS........",
    "................",
])


def carrot_nukazuke():
    img = dish()
    img.alpha_composite(itemkit.sprite(CARROT_SERVED, PICKLED_CARROT, lit=0.8, dark=0.62))
    for x, y in ((13, 13), (1, 10)):
        px(img, x, y, BRAN_FLECK)
    return img


PICKLED_BEET = [(70, 10, 28), (112, 20, 46), (160, 40, 72), (206, 100, 132), (236, 160, 182)]


def beetroot_nukazuke():
    """
    Beetroot in two round slices laid overlapping on a dish: each cut face the deep red of pickled beet with the root's
    paler growth ring in it, its skin round the edge and its thickness a band of skin below; a fleck of bran. (Smaller
    slices, or a ring a pixel thin, read as berries or a slice of ham.)
    """
    img = dish()
    for cx, cy in ((10.0, 7.4), (5.6, 9.6)):  # back to front
        def beet_slice(layer, cx=cx, cy=cy, rx=4.6, ry=3.0):
            for y in range(16):
                for x in range(16):
                    dx, dy, up = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry, (y - 0.5 - cy) / ry
                    if dx * dx + dy * dy <= 1:
                        r = math.hypot(dx, dy)
                        c = PICKLED_BEET[1] if r > 0.84 else PICKLED_BEET[3] if 0.5 < r < 0.75 else PICKLED_BEET[2]
                        if r <= 0.84 and dx + dy < -1.0:
                            c = PICKLED_BEET[4]
                        px(layer, x, y, c)
                    elif dx * dx + up * up <= 1:  # the slice's thickness, below its face
                        px(layer, x, y, PICKLED_BEET[0])
        served(img, beet_slice)
    for x, y in ((12, 12), (2, 8)):
        px(img, x, y, BRAN_FLECK)
    return img


KRAFT = [(150, 118, 78), (190, 156, 108), (218, 190, 142), (236, 216, 176)]
FLOUR_WHITE = [(214, 208, 196), (238, 234, 226), (252, 250, 246)]
WHEAT_MARK = [(178, 136, 48), (216, 176, 72)]


def flour():
    """
    Flour in a paper sack, the way it is bought: a kraft bag with its top folded over, a little flour dusted on the
    fold, and a sheaf of wheat printed on the front - a sack of paper, where the Rice Bran's is tied cloth.
    """
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(4, 15):
        for x in range(3, 13):
            c = KRAFT[2] if x < 6 else KRAFT[1] if x < 11 else KRAFT[0]
            px(img, x, y, c)
    for x in range(3, 13):  # the folded top, a shade lighter, and its crease
        px(img, x, 3, KRAFT[3] if x < 10 else KRAFT[2])
        px(img, x, 4, KRAFT[0])
    for x in range(4, 12):
        px(img, x, 2, KRAFT[3] if x < 9 else KRAFT[2])
    for x, y in ((5, 2), (7, 3), (9, 2), (10, 3), (6, 1)):  # flour dusted on the fold
        px(img, x, y, FLOUR_WHITE[2])
    for y in range(7, 13):  # the sheaf printed on the front
        px(img, 8, y, WHEAT_MARK[0])
    for x, y in ((7, 7), (9, 7), (7, 9), (9, 9), (6, 8), (10, 8)):
        px(img, x, y, WHEAT_MARK[1])
    for x in range(3, 13):
        px(img, x, 14, KRAFT[0])
    return outline(img)


CRUMB = [(150, 96, 36), (196, 142, 62), (230, 186, 102), (246, 216, 150)]


def breadcrumbs():
    """
    A heap of coarse golden crumbs: lumps two pixels across in light and shade, piled close with no gaps through the
    heap (a gap outlined would read as a hole), crumbs fallen off round its foot - lumpy, not a smooth powder.
    """
    img = Image.new("RGBA", (16, 16), CLEAR)
    rng = random.Random(8)
    for y in range(6, 15):
        half = [2, 3, 4, 5, 5, 6, 6, 7, 7][y - 6]
        for x in range(8 - half, 8 + half):
            lump = ((x // 2) * 7 + (y // 2) * 3 + rng.randrange(2)) % 4  # which lump, and how it catches the light
            light = (x - 8) + (y - 10) * 0.8
            base = 3 if light < -4 else 2 if light < 1 else 1
            c = CRUMB[max(0, min(3, base + (1 if lump == 0 else -1 if lump == 3 else 0)))]
            px(img, x, y, c)
    for x, y in ((2, 14), (13, 14), (1, 13)):  # crumbs fallen off it
        px(img, x, y, CRUMB[2])
    return outline(img)


CUTLET = [(128, 64, 20), (180, 108, 36), (222, 158, 64), (246, 204, 116), (255, 232, 168)]  # the crumb coat
PORK_CUT = [(226, 196, 178), (248, 230, 218)]
CABBAGE = {"g": (58, 112, 36), "G": (112, 170, 56), "H": (178, 220, 112), "h": (220, 240, 170)}
SHREDDED_CABBAGE = ["..hH..", ".HGhG.", "HGgHGh", "GgHGgG", ".gGg.."]


def tonkatsu():
    """
    Tonkatsu as it is served: a heap of shredded cabbage at the back of a dish and the cutlet laid across it on the
    slant, sliced into four - the golden crumb coat lit along its far side, flecked where the crumb catches the light,
    and at each cut the pale pork showing, the slice before it in shadow. (A round cutlet over the dish read as a pie.)
    """
    img = dish()
    served(img, lambda layer: itemkit.paint(SHREDDED_CABBAGE, CABBAGE, (1, 5), layer))
    angle, length, width, slices, (cx, cy) = math.radians(-20), 13.0, 5.6, 4, (8.6, 8.2)
    each = length / slices

    def cutlet(layer):
        for y in range(16):
            for x in range(16):
                ax, ay = x + 0.5 - cx, y + 0.5 - cy
                u = ax * math.cos(angle) + ay * math.sin(angle)  # along the cutlet
                v = (-ax * math.sin(angle) + ay * math.cos(angle)) / (width / 2)  # across it: -1 at the far side
                if (abs(u) / (length / 2)) ** 4 + v ** 4 > 1:
                    continue
                k, f = divmod(u + length / 2, each)
                c = CUTLET[3] if v < -0.45 else CUTLET[2] if v < 0.25 else CUTLET[1] if v <= 0.7 else CUTLET[0]
                if (x * 5 + y * 3) % 7 == 0 and v < 0.5:
                    c = CUTLET[4] if v < -0.2 else CUTLET[3]
                if k > 0 and f < 0.9 and v > -0.75:
                    c = PORK_CUT[1] if v < 0.3 else PORK_CUT[0]
                elif k < slices - 1 and f > each - 0.8 and v > -0.6:
                    c = CUTLET[0]
                px(layer, x, y, c)
    served(img, cutlet)
    return img


BREADED = [(196, 166, 112), (222, 196, 140), (238, 220, 172), (250, 238, 204)]  # raw crumb coat, not yet fried
CRUMB_FLECK = (206, 160, 92)


def breaded_pork():
    """A porkchop coated for frying and not yet fried: the same slanted oval as the cutlet on the dish, whole, in a pale
    crumb coat lit along its far side and flecked with the crumbs' darker bits; no dish under it."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    angle, length, width, (cx, cy) = math.radians(-20), 13.0, 7.0, (8.0, 8.4)
    for y in range(16):
        for x in range(16):
            ax, ay = x + 0.5 - cx, y + 0.5 - cy
            u = ax * math.cos(angle) + ay * math.sin(angle)
            v = (-ax * math.sin(angle) + ay * math.cos(angle)) / (width / 2)
            if (abs(u) / (length / 2)) ** 4 + v ** 4 > 1:
                continue
            c = BREADED[3] if v < -0.55 else BREADED[2] if v < 0.15 else BREADED[1] if v <= 0.65 else BREADED[0]
            if (x * 7 + y * 5) % 9 == 0 and -0.6 < v < 0.6:
                c = CRUMB_FLECK
            px(img, x, y, c)
    return img


# The egg dishes (the user, 2026-10-16: 卵料理が欲しくない？), drawn flat - a few colours a part - and finished with
# texkit.icon: the grain at icon strength, then the ring. The gleams and lines that say what a thing is are kept exact.
# Fried egg (itemkit rows): sunny side up, seen a little from above - the white spread out unevenly, lit along its
# upper left (W), shaded low on its right (s) and in the yolk's shadow; its edge crisped golden (c, C) along the bottom
# and right, where the pan was hottest; the yolk a dome, lit upper left (Y) with a gleam (G), deep orange low on its
# right (o, O).
FRIED_EGG = {"W": (255, 255, 252), "w": (244, 241, 232), "s": (218, 212, 198), "c": (236, 200, 128), "C": (200, 146, 70),
             "Y": (255, 238, 150), "G": (255, 255, 236), "y": (252, 198, 46), "o": (236, 150, 22), "O": (204, 112, 12)}
FRIED_EGG_ROWS = itemkit.check([
    "................",
    "................",
    "......wwww......",
    "....wwWWwwwwc...",
    "...wWWwwwwwwwc..",
    "..wWwwwYYyywwwc.",
    ".wWwwwYGYyyowwsc",
    ".wWwwYYyyyyoowsc",
    "wwwwwYyyyyyoOwsC",
    "wwwwwyyyyyooOswC",
    ".wwwwsyyooOOsswC",
    ".wwwwwsssssssswc",
    "..cwwwwwwwwwsscC",
    "...ccwwwwsssccC.",
    ".....CCcccCCC...",
    "................"])


def fried_egg():
    return texkit.icon(itemkit.paint(FRIED_EGG_ROWS, FRIED_EGG), 40, keep=[FRIED_EGG["G"], FRIED_EGG["Y"], FRIED_EGG["W"]])


# The boiled egg's white, lit to shaded: the shade goes a cool blue-grey, so the white keeps an edge on a light ground
# (the user, 2026-10-17, of the first drawing: ゆで卵が、あんまりゆで卵にみえないかもね - picked this one, B).
PEELED = [(168, 172, 186), (198, 200, 212), (228, 228, 232), (244, 243, 238), (255, 255, 252)]
PEELED_GLOSS = (255, 255, 255)
BOILED_YOLK = [(214, 128, 18), (238, 160, 28), (250, 196, 64), (255, 222, 122)]  # deep middle to the paler ring


def _lit(nx, ny):
    """How much a surface leaning {@code nx}, {@code ny} (-1..1, from its middle) faces the light, upper left."""
    return -(nx * 0.62 + ny * 0.78)


def _peeled_whole(layer, cx, cy, rx0, ry):
    """A peeled egg standing: narrower at the top, glossy on its upper left, cool shade round its lower right."""
    for y in range(16):
        t = (y + 0.5 - cy) / ry
        if abs(t) >= 1:
            continue
        rx = rx0 * math.sqrt(1 - t * t) * (1 + 0.12 * t)
        for x in range(16):
            u = (x + 0.5 - cx) / max(rx, 0.01)
            if abs(u) > 1:
                continue
            light = _lit(u * 0.9, t * 0.9)
            px(layer, x, y, PEELED[4] if light > 0.7 else PEELED[3] if light > 0.2 else PEELED[2] if light > -0.35
               else PEELED[1] if light > -0.75 else PEELED[0])
    gx, gy = int(cx - rx0 * 0.45), int(cy - ry * 0.45)
    px(layer, gx, gy, PEELED_GLOSS)
    px(layer, gx, gy + 1, PEELED_GLOSS)


def _peeled_half(layer, cx, cy, rx, ry, back, yolk_r):
    """A half lying cut face up, seen from above at a slant: the cut face an oval, the curve of its back showing
    {@code back} px under it in cool shade, and a round yolk set in the white, deep in the middle and paler at its
    edge, as a hard-boiled yolk is cut."""
    squash = ry / rx
    for y in range(16):
        for x in range(16):
            u, v = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            face = u * u + v * v <= 1
            under = y + 0.5 > cy and u * u + ((y + 0.5 - cy - back) / ry) ** 2 <= 1
            if not (face or under):
                continue
            if face:
                d = math.hypot((x + 0.5 - cx) / yolk_r, (y + 0.5 - cy) / (yolk_r * squash))
                if d <= 1:
                    c = BOILED_YOLK[3] if d > 0.8 else BOILED_YOLK[1] if d < 0.5 else BOILED_YOLK[2]
                else:
                    r2 = u * u + v * v
                    c = PEELED[4] if r2 > 0.55 and _lit(u, v) > 0.35 else PEELED[2] if r2 > 0.6 and _lit(u, v) < -0.4 else PEELED[3]
            else:
                c = PEELED[1] if u < 0.3 else PEELED[0]
            px(layer, x, y, c)


def boiled_egg_layers():
    """
    A boiled egg, peeled: one whole, standing, an egg's shape - narrower at the top - glossy on its upper left and
    shaded cool round to its lower right; and before it on the right a half lying cut face up, a round yolk in the
    white. Each is drawn flat, on a layer of its own.
    """
    whole, half = Image.new("RGBA", (16, 16), CLEAR), Image.new("RGBA", (16, 16), CLEAR)
    _peeled_whole(whole, 5.4, 6.4, 4.1, 5.8)
    _peeled_half(half, 10.4, 10.8, 4.5, 3.6, 1.5, 2.5)
    return whole, half


def _ringed_white(layer, seed):
    """Finished as its siblings are, the ring worked out from the white alone: the yolk is inside the white, and a ring
    taken from it ran orange along the back of the cut."""
    white = layer.copy()
    for y in range(16):
        for x in range(16):
            p = white.getpixel((x, y))
            if p[3] and p[:3] in BOILED_YOLK:
                white.putpixel((x, y), PEELED[3] + (255,))
    out = texkit.outline(white)
    out.alpha_composite(texkit.finish(layer, seed, keep=[PEELED_GLOSS, PEELED[4]] + BOILED_YOLK, **texkit.ICON))
    return out


def boiled_egg():
    whole, half = boiled_egg_layers()
    img = _ringed_white(whole, 53)
    img.alpha_composite(_ringed_white(half, 54))  # ringed on its own, so it stands in front
    return img


# Tamagoyaki (itemkit rows): a rolled omelette, its first slice cut - a block seen from its cut end, a little from above
# and to the left: the cut face (F light, f) showing the rolled layers as lines (l) going round into the middle; the top
# lit (T, t), browned here and there (k), the cut across it (K); the far side in shade (R, r), the cut down it (g).
TAMAGOYAKI = {"T": (255, 238, 128), "t": (252, 214, 74), "k": (234, 170, 58), "K": (198, 128, 38),
              "F": (255, 246, 176), "f": (255, 226, 112), "l": (230, 172, 56),
              "R": (240, 184, 56), "r": (216, 150, 40), "g": (176, 110, 28)}
TAMAGOYAKI_ROWS = itemkit.check([
    "................",
    "................",
    "................",
    "................",
    "................",
    "......ttktttktR.",
    ".....tktttkttRR.",
    "...KKKKKKKKgRRR.",
    "..TTTTkTTtRgRRR.",
    "..FFFFFFFRRgRRR.",
    ".FllllllFRRgRRr.",
    ".FlfffflFRrgrr..",
    ".FlflllfFrrgr...",
    ".Flffffffrr.....",
    "..fffffffr......",
    "................"])


def tamagoyaki():
    return texkit.icon(itemkit.paint(TAMAGOYAKI_ROWS, TAMAGOYAKI), 42,
                       keep=[TAMAGOYAKI["l"], TAMAGOYAKI["K"], TAMAGOYAKI["g"]])


def iron_pot_model(liquid, on_grill):
    """A deep black iron pot: an octagonal body, walls a pixel thick standing up round a pool of water or oil (when it
    has some; otherwise the black floor), and a bail handle each side. On a Grill over a fire the pot takes the grill's
    block, and the blockstate draws the grill with it: the pot stands on the grate, a pixel up."""
    d = 1 if on_grill else 0
    iron = "#iron"
    elements = []

    def box(x0, y0, z0, x1, y1, z1, top=iron, uv_side=None):
        faces = {}
        # each side as the cube projects it, so the iron lies on it pixel for pixel (east and west took the x span)
        sides = {"north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
                 "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0], "west": [z0, 16 - y1, z1, 16 - y0]}
        for side in ("north", "east", "south", "west"):
            faces[side] = {"texture": iron, "uv": uv_side or sides[side]}
        faces["up"] = {"texture": top, "uv": [x0, z0, x1, z1]}
        if top == "#water":
            faces["up"]["tintindex"] = 0
        faces["down"] = {"texture": iron, "uv": [x0, z0, x1, z1]}
        elements.append({"from": [x0, y0 + d, z0], "to": [x1, y1 + d, z1], "faces": faces})

    floor = {"water": "#water", "oil": "#oil"}.get(liquid, iron)
    for (ax, az) in ((5, 3), (3, 5), (4, 4)):              # the base, drawn in a little
        box(8 - ax, 0, 8 - az, 8 + ax, 1, 8 + az)
    for (ax, az) in ((6, 4), (4, 6), (5, 5)):              # the body up to the oil
        box(8 - ax, 1, 8 - az, 8 + ax, 5, 8 + az, top=floor)
    for x0, z0, x1, z1 in ((4, 2, 12, 3), (4, 13, 12, 14), (2, 4, 3, 12), (13, 4, 14, 12),   # the walls round the oil
                           (3, 3, 4, 4), (12, 3, 13, 4), (3, 12, 4, 13), (12, 12, 13, 13)):
        box(x0, 5, z0, x1, 7, z1)
    for x0, x1 in ((0.5, 2), (14, 15.5)):                  # the bails
        box(x0, 6, 6, x1, 7, 10)
    textures = {"particle": "nookworks:block/black_iron", "iron": "nookworks:block/black_iron"}
    if liquid == "oil":
        textures["oil"] = "nookworks:block/vegetable_oil_still"
    if liquid == "water":
        textures["water"] = "minecraft:block/water_still"
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements,
            "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 3, 0], "scale": [0.9, 0.9, 0.9]}}}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    tex = ASSETS / "textures"
    # the mottling is drawn: the finish adds only a little grain, no patches of its own over the drawn ones
    texkit.finish(black_iron(), 4, tile=True, clusters=0.3, grain=0.03, size=4, reach=1,
                  keep=[CAST_IRON[0], CAST_IRON[4], CAST_IRON[5]]).save(tex / "block/black_iron.png")
    texkit.finish(grate(), 5, keep=[CLEAR], **texkit.ROUGH).save(tex / "block/grill_grate.png")
    texkit.finish(nukadoko_side(), 6, tile=True, keep=[HOOP[0], HOOP[1], STAVE[0]], **texkit.SMOOTH).save(tex / "block/nukadoko_side.png")
    texkit.finish(nukadoko_top(), 7, keep=[STAVE[0], NUKA_CRUMB, NUKA_HUSK, NUKA_PATTED], **texkit.SMOOTH).save(tex / "block/nukadoko_top.png")
    write_json(ASSETS / "models/block/nukadoko.json", nukadoko_model())
    write_json(ASSETS / "blockstates/nukadoko.json", {"variants": {"": {"model": "nookworks:block/nukadoko"}}})
    write_json(ASSETS / "items/nukadoko.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/nukadoko"}})
    write_json(ASSETS / "models/block/grill.json", grill_model())
    write_json(ASSETS / "models/block/grill_standing.json", grill_model(False))
    write_json(ASSETS / "blockstates/grill.json", {"variants": {"over_fire=true": {"model": "nookworks:block/grill"},
                                                                "over_fire=false": {"model": "nookworks:block/grill_standing"}}})
    write_json(ASSETS / "items/grill.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/grill_standing"}})
    items = {"cooked_rice": cooked_rice(), "vegetable_soup": vegetable_soup(),
             "salt_onigiri": onigiri(False), "salmon_onigiri": onigiri(True),
             "french_fries": french_fries(), "karaage": karaage(), "fried_fish": fried_fish(),
             "fried_rice": fried_rice(), "stir_fried_vegetables": stir_fried_vegetables(),
             "carrot_nukazuke": carrot_nukazuke(),
             "beetroot_nukazuke": beetroot_nukazuke(), "tonkatsu": tonkatsu(), "flour": flour(),
             "breadcrumbs": breadcrumbs(), "breaded_pork": breaded_pork(),
             "fried_egg": fried_egg(), "boiled_egg": boiled_egg(), "tamagoyaki": tamagoyaki()}
    finished = {"fried_fish", "carrot_nukazuke", "beetroot_nukazuke", "tonkatsu",  # ringed piece by piece already, and left smooth
                "fried_egg", "boiled_egg", "tamagoyaki"}  # finished with what they keep exact, and ringed, as drawn
    for i, (name, img) in enumerate(items.items()):
        (img if name in finished else texkit.icon(img, 10 + i)).save(tex / f"item/{name}.png")
        write_json(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"nookworks:item/{name}"}})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:item/{name}"}})
    parts = [{"when": {"on_grill": "true"}, "apply": {"model": "nookworks:block/grill"}}]
    for liquid in ("empty", "water", "oil"):
        for grill in ("false", "true"):
            name = "iron_pot" + ("" if liquid == "empty" else "_" + liquid) + ("_on_grill" if grill == "true" else "")
            write_json(ASSETS / f"models/block/{name}.json", iron_pot_model(liquid, grill == "true"))
            for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                v = {"model": f"nookworks:block/{name}"}
                if y:
                    v["y"] = y
                parts.append({"when": {"facing": facing, "liquid": liquid, "on_grill": grill}, "apply": v})
    write_json(ASSETS / "blockstates/iron_pot.json", {"multipart": parts})
    write_json(ASSETS / "items/iron_pot.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/iron_pot"}})
    print("wrote the grill, the iron pot and their food")


if __name__ == "__main__":
    main()
