"""The material items: everything the machines turn one thing into another with. All original pixel art.

Each family shares a shape so it reads as a family, and each member is told apart by colour and by what is in it:

- ingots (velarium, zinc, brass): a bar seen from above one end, its faces placed by hand (BAR)
- velarium_nugget: a small chunk of the metal to match, placed by hand (NUGGET)
- red_hot_velarium_ingot, tempered_velarium_ingot: the Velarium bar out of the fire, glowing from deep red to a
  straw-white top, and quenched in oil, darker, its flecks gone the blue of tempered steel (2026-10-12)
- reinforced_rod: a breeze rod, pale blue-grey, held in two raised bands of brass, lying as a stick lies
- raw_zinc: a rough lump of ore in three lobes with a green vein through it, placed by hand (itemkit)
- *_amalgam: a slumped bead of quicksilver, a liquid mirror, with flecks of the metal it took up pressed into it (iron
  grey, gold, copper; the mixed amalgam duller, with clusters of all three), placed by hand (itemkit)
- crude_salt, salt, caustic_soda, sulfur_dust: what each is made of (grey chips of rock salt, a heap of clear cubes,
  pearls, a heap of yellow powder); crude salt, sulfur, the wood chips and the pulp placed pixel by pixel (itemkit)
- wood_chips, wood_pulp, fine_paper, poster: the paper chain (fine paper placed by hand, itemkit)

The rest are drawn as masks and lit from the upper left with a small colour ramp per material, then outlined as
vanilla outlines its items: in the ramp's darkest colour where the edge faces away from the light, and its next
where it faces the light (above and left), so nothing is a flat fill and nothing is ringed in black.

Run from the repo root:  python tools/textures/gen_materials.py
"""
import math
import random
from pathlib import Path

from PIL import Image

import itemkit
import texkit

ROOT = Path(__file__).resolve().parents[2]
ITEM = ROOT / "src/main/resources/assets/nookworks/textures/item"

CLEAR = (0, 0, 0, 0)
LIGHT = (-0.55, -0.65, 0.52)  # upper left, a little towards the viewer


def rgb(hexcode):
    h = hexcode.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def ramp(*codes):
    """Darkest (the outline) to brightest."""
    return [rgb(c) for c in codes]


ZINC = ramp("2a3238", "56646c", "7e8c94", "a2b0b6", "c6d2d6", "eef6f8")
BRASS = ramp("3e2a0c", "7a5418", "a87a2a", "cea048", "ecc86c", "fff0b0")
MERCURY = ramp("22262e", "4c525e", "7c8492", "a8b0bc", "d2d8e2", "ffffff")


class Canvas:
    def __init__(self):
        self.img = Image.new("RGBA", (16, 16), CLEAR)

    def put(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.img.putpixel((x, y), c)

    def get(self, x, y):
        return self.img.getpixel((x, y)) if 0 <= x < 16 and 0 <= y < 16 else CLEAR

    def outline(self, colour, mask=None, lit=None):
        """Turns every filled pixel on the edge of the shape (or of {@code mask}) into the outline colour - or, where
        the edge faces only the light (open above or to the left of it), into {@code lit}, a step lighter."""
        filled = mask if mask is not None else {(x, y) for y in range(16) for x in range(16) if self.get(x, y)[3]}
        for (x, y) in filled:
            open_sides = [(dx, dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if (x + dx, y + dy) not in filled]
            if open_sides:
                facing_light = all(side in ((-1, 0), (0, -1)) for side in open_sides)
                self.put(x, y, lit if lit is not None and facing_light else colour)

    def retone(self, dark, lit):
        """The same for an outline already drawn in {@code dark}: its pixels open only above or to the left go
        {@code lit}."""
        for y in range(16):
            for x in range(16):
                if self.get(x, y)[:3] == dark[:3]:
                    open_sides = [(dx, dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if not self.get(x + dx, y + dy)[3]]
                    if open_sides and all(side in ((-1, 0), (0, -1)) for side in open_sides):
                        self.put(x, y, lit)

    def save(self, name):
        self.img.save(ITEM / f"{name}.png")


def lit(nx, ny, nz):
    n = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
    return max(0.0, (nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]) / n)


def shade(level, colours, lo=1):
    """Picks a colour off a ramp for a light level in 0..1, never the outline colour at index 0."""
    i = lo + int(level * (len(colours) - lo) + 0.25)
    return colours[max(lo, min(len(colours) - 1, i))]


def ellipse(cx, cy, rx, ry, wobble=0.0, seed=0):
    rng = random.Random(seed)
    phase = rng.random() * 6.28
    cells = {}
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            angle = math.atan2(dy, dx)
            limit = 1.0 + wobble * math.sin(3 * angle + phase) + wobble * 0.5 * math.sin(5 * angle + 2 * phase)
            if dx * dx + dy * dy <= limit * limit:
                cells[(x, y)] = (dx, dy)
    return cells


def dome(canvas, cells, colours, flatten=1.0, lo=1):
    for (x, y), (dx, dy) in cells.items():
        nz = math.sqrt(max(0.0, 1.0 - dx * dx - dy * dy)) * flatten + 0.15
        canvas.put(x, y, shade(lit(dx, dy, nz), colours, lo))


# ---- ingots and nuggets ----

# An ingot lying diagonally: the top face (t), the long front face (f), the near end (e). Seen from above its left end.
# The ingots, placed pixel by pixel (2026-10-02, the user's pick): a bar seen from above one end, three faces told
# apart as vanilla tells them - the top the brightest, lit along its far edge and brightest of all along the ridge where
# it turns down; the end towards the light a step under; the long front facing away, darkest along its foot. Shades 1
# (deepest) to 7 (highlight); t u v the Velarium's teal fleck, from shadow to lit (read as the metal in the others).
BAR = [
    "................",
    "................",
    "................",
    ".....55.........",
    "...766655.......",
    "..47766u655.....",
    "..444776vu655...",
    "..33344776t652..",
    "...23t34475221..",
    ".....233342111..",
    ".......223111...",
    ".........21.....",
    "................",
    "................",
    "................",
    "................",
]
# Velarium in its tools' colours (gen_velarium_gear.py), with the teal of the Block of Velarium
VELARIUM_BAR = {
    "1": (30, 14, 62), "2": (52, 30, 94), "3": (78, 50, 138), "4": (112, 80, 182), "5": (150, 122, 218),
    "6": (178, 154, 236), "7": (212, 198, 250), "t": (60, 160, 140), "u": (92, 210, 172), "v": (172, 250, 222),
}


# Out of the fire: red at the foot, orange up the faces, straw-white along the ridge; the flecks lost in the glow
RED_HOT_BAR = {"1": (96, 18, 8), "2": (150, 34, 10), "3": (196, 62, 14), "4": (232, 104, 24), "5": (250, 150, 46),
               "6": (255, 196, 96), "7": (255, 236, 170)}
# Quenched in oil: the Velarium gone dark and blue, as blued steel goes, its teal flecks a bright steel blue
TEMPERED_BAR = {"1": (12, 8, 34), "2": (26, 20, 66), "3": (42, 38, 102), "4": (60, 62, 144), "5": (86, 96, 182),
                "6": (118, 134, 214), "7": (168, 190, 242), "t": (36, 120, 176), "u": (70, 170, 226), "v": (170, 226, 252)}


def bar_shades(colours):
    """A metal's ramp (darkest to brightest) as the bar's shades: one between its two darkest, and no fleck."""
    c = [tuple(x[:3]) for x in colours]
    mid = tuple((a + b) // 2 for a, b in zip(c[0], c[1]))
    return {"1": c[0], "2": mid, "3": c[1], "4": c[2], "5": c[3], "6": c[4], "7": c[5]}


# The Velarium nugget, placed to match the bar: a small chunk of the metal, lit on its top and the face towards the light,
# a teal fleck where the light catches it (2026-10-02, the user's pick).
NUGGET = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "......6655......",
    ".....477u655....",
    ".....3447622....",
    ".....2334211....",
    "......223111....",
    "........21......",
    "................",
    "................",
    "................",
    "................",
    "................",
]


def placed_metal(rows, shades, name, fleck=False):
    """
    A metal item placed by hand: the shades as placed, then vanilla's ring round it (texkit.outline), worked out from the
    metal alone so a fleck does not tint it. No grain pass: the faces are placed by hand, and noise would break their
    edges. Without {@code fleck} the teal is read as the metal under it.
    """
    if not fleck:
        rows = [r.replace("t", "3").replace("u", "5").replace("v", "6") for r in rows]
    img = Image.new("RGBA", (16, 16), CLEAR)
    metal = Image.new("RGBA", (16, 16), CLEAR)
    plate = {"t": "3", "u": "4", "v": "5"}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), shades[ch] + (255,))
                metal.putpixel((x, y), shades[plate.get(ch, ch)] + (255,))
    ring = texkit.outline(metal)
    for y in range(16):
        for x in range(16):
            if not img.getpixel((x, y))[3] and ring.getpixel((x, y))[3]:
                img.putpixel((x, y), ring.getpixel((x, y)))
    img.save(ITEM / f"{name}.png")


# Raw zinc (itemkit), as big in the slot as vanilla's raw iron: a lump of three lobes - the big one at the upper left,
# one at the right, one under them - with dark crevices (0) between; each lit on its upper left, its facets there a
# bluish white (4, 5), going to a cool deep shade at its lower right (1). Through it a vein of green ore as one unbroken
# band two pixels deep, lit along its top (V) and dark under (v). Ringed in the zinc's own dark. (An 11x10 lump of two
# greys with a dotted vein read as mouldy, and small beside raw iron.)
RAW_ZINC = {"0": (42, 50, 58), "1": (84, 98, 110), "2": (124, 138, 150), "3": (160, 174, 184), "4": (198, 210, 218),
            "5": (236, 244, 250), "V": (128, 194, 156), "v": (62, 124, 100)}
RAW_ZINC_LUMP = itemkit.check([
    "................",
    "................",
    "..3455443.......",
    ".345545443......",
    ".3545444533.....",
    ".34454453321344.",
    ".3343333VVv0453.",
    ".233332VVv23432.",
    ".2222VVvv123321.",
    "..2VVvv11023321.",
    "..1v11012033221.",
    "...1100345321...",
    "....03454432....",
    "....3444321.....",
    ".....22110......",
    "................",
])


ROD = {"a": (214, 228, 240), "b": (150, 172, 202), "c": (104, 124, 158),   # the breeze rod: lit, face, shade
       "Y": (238, 206, 112), "y": (170, 124, 44), "z": (110, 76, 24)}          # brass: lit, face, shade


def reinforced_rod():
    """A rod lying as vanilla's stick lies, from the lower left up to the right, two pixels thick - its upper left lit,
    its lower right in shade - and round it two raised bands of brass, a pixel proud of the rod on both sides."""
    rows = [["."] * 16 for _ in range(16)]
    for y in range(2, 14):
        x = 15 - y
        band = y in (4, 5, 10, 11)
        rows[y][x - 1] = "Y" if band else "a"
        rows[y][x] = "y" if band else "b" if y in (2, 13) else "c"
        if band:
            rows[y][x - 2] = "Y"
            if x + 1 < 16:
                rows[y][x + 1] = "z"
    itemkit.sprite(["".join(r) for r in rows], ROD).save(ITEM / "reinforced_rod.png")


def raw_zinc():
    ring = {ch: RAW_ZINC["1"] for ch in RAW_ZINC}  # the vein and the crevices count as zinc: ringed in its dark
    itemkit.sprite(RAW_ZINC_LUMP, RAW_ZINC, ring_from=ring).save(ITEM / "raw_zinc.png")


# ---- amalgams ----

# An amalgam (itemkit): a bead of quicksilver slumped on its flat base, a small droplet broken off it at the right.
# A liquid mirror, in the mercury's own ramp (0 deep to 5 the gleam): the sky bright across its top with a 2x2 gleam
# at the upper left, a crisp dark horizon curving down across it, the ground darker under that and lit again by the
# bounce along its foot; its far side in shade. The metal it has taken up shows only in a few crisp flecks pressed into it
# (F lit, f deep), and the body leans a tenth of the way towards that metal at most. (A disc with grain slashes and a
# strong tint read as a cookie, and iron's two rust pixels made a face.)
AMALGAM_BEAD = itemkit.check([
    "................",
    "................",
    "................",
    ".....3444433....",
    "...34554444333..",
    "..3455444444332.",
    ".34444444443332.",
    ".34444444433221.",
    ".23344444332110.",
    ".22333333200011.",
    ".23200000011122.",
    "..321111122222..",
    "..34322222233...",
    "...33333332..43.",
    "............321.",
    "................",
])
# where the flecks sit, (x, y, F or f): scattered, never two at one height across the middle (two read as eyes)
AMALGAM_FLECKS = ((9, 4, "F"), (10, 4, "f"), (2, 9, "F"), (3, 9, "f"), (6, 10, "F"), (7, 10, "f"), (10, 11, "F"),
                  (11, 11, "f"), (4, 12, "F"), (5, 12, "f"))
# the mixed amalgam's: three small clusters of muted metals (G/g copper, H/h iron) in a duller body
MIXED_FLECKS = ((8, 5, "F"), (9, 5, "f"), (9, 6, "g"), (2, 9, "G"), (3, 9, "h"), (3, 10, "F"), (10, 11, "H"), (11, 11, "f"),
                (10, 12, "g"))


def amalgam(name, tint, flecks, colours, share=0.1, dull=None):
    """The bead in the mercury's ramp taken {@code share} of the way to {@code tint} (or, {@code dull}, a third of the way
    to that grey and without its gleam), {@code flecks} in {@code colours} laid in it. Ringed in the bead's own dark: the
    flecks count as quicksilver for it."""
    ramp = [mix(c, dull, 0.32) if dull else mix(c, tint, share) for c in MERCURY]
    if dull:
        ramp[5] = ramp[4]
    rows = [list(r) for r in AMALGAM_BEAD]
    for x, y, ch in flecks:
        rows[y][x] = ch
    rows = ["".join(r) for r in rows]
    palette = dict({str(i): c[:3] for i, c in enumerate(ramp)}, **colours)
    ring = {ch: ramp[1][:3] for ch in palette}
    itemkit.sprite(rows, palette, lit=0.9, dark=0.72, ring_from=ring).save(ITEM / f"{name}.png")


def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3)) + (255,)


# ---- salts, powders and pellets ----

def heap_mask(top, bottom, width, seed, bumps=0.8):
    """A pile resting on the bottom of the frame, a little uneven along its top."""
    rng = random.Random(seed)
    cells = set()
    peaks = [rng.uniform(-bumps, bumps) for _ in range(16)]
    for x in range(16):
        dx = (x + 0.5 - 8.0) / width
        if abs(dx) > 1.0:
            continue
        height = (bottom - top) * (1.0 - dx * dx) ** 0.8 + peaks[x]
        for y in range(16):
            if bottom - height <= y + 0.5 <= bottom:
                cells.add((x, y))
    return cells


# Crude salt (itemkit): three chips of grey rock salt crusted together, broken as rock salt breaks - each a lit top
# facet, a mid face to the left, a dark face to the right - the dirt still in them; where a front chip sits against the
# one behind, that one is in its shadow. Chips, not a heap, so it is not mistaken for the clean salt's pile.
CRUDE = {"1": (100, 100, 110), "2": (134, 134, 142), "3": (166, 166, 170), "4": (196, 196, 196), "5": (228, 226, 220),
         "g": (128, 112, 94), "h": (160, 144, 120)}
CRUDE_CHIPS = (
    (["...55...", "..5444..", ".544443.", "5544g332", "4433322.", ".33221..", "..21...."], (4, 2), "back"),
    (["..55..", ".5443.", "54h432", "443321", ".3221.", "..1..."], (2, 7), "front"),
    ([".55....", "54443..", "443432.", ".3332g1", "..2211."], (8, 8), "front"),
)


def crude_salt():
    img = Image.new("RGBA", (16, 16), CLEAR)
    owner = {}
    for rows, (ox, oy), tag in CRUDE_CHIPS:
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in CRUDE:
                    img.putpixel((ox + x, oy + y), CRUDE[ch] + (255,))
                    owner[(ox + x, oy + y)] = tag
    for (x, y), tag in owner.items():
        if tag == "back" and owner.get((x, y + 1)) == "front":
            img.putpixel((x, y), CRUDE["1"] + (255,))
    texkit.outline(img).save(ITEM / "crude_salt.png")


def cube(c, x, y, top, side, dark, outline):
    """A salt crystal a few pixels across: a lit top, a front and a shaded side."""
    cells = {(x + 1, y): top, (x + 2, y): top, (x, y + 1): top, (x + 1, y + 1): top,
             (x, y + 2): side, (x + 1, y + 2): side, (x + 2, y + 1): dark, (x + 2, y + 2): dark}
    for (px, py), col in cells.items():
        c.put(px, py, col)
    return set(cells)


def salt():
    """Clean salt: a heap of small clear cubes."""
    c = Canvas()
    colours = ramp("56606e", "a6b0bc", "c8d0da", "e4e8ee", "f6f8fa", "ffffff")
    mask = heap_mask(6.0, 14.0, 6.8, 6, 0.4)
    for (x, y) in mask:
        c.put(x, y, colours[2] if (x * 3 + y) % 4 else colours[1])
    cubes = ((6, 4), (3, 8), (9, 6), (6, 9), (11, 10), (2, 11), (8, 11))
    for (x, y) in cubes:
        mask |= cube(c, x, y, colours[5], colours[3], colours[1], colours[0])
    c.outline(colours[0], mask, lit=colours[1])
    for (x, y) in cubes:  # re-lay the tops the outline pass took off
        c.put(x + 1, y, colours[5])
        c.put(x + 1, y + 1, colours[4])
    c.save("salt")


def caustic_soda():
    """Caustic soda: glossy white pearls, each with its own highlight."""
    c = Canvas()
    colours = ramp("4a5058", "a4aab2", "c8ced4", "e2e6ea", "f4f6f8", "ffffff")
    mask = set()
    for (cx, cy, r) in ((8.0, 5.6, 2.8), (5.2, 8.6, 2.8), (10.8, 8.6, 2.8), (3.4, 12.2, 2.8), (8.0, 12.0, 3.0),
                        (12.6, 12.4, 2.6)):
        cells = ellipse(cx, cy, r, r, 0.0, int(cx * 10))
        dome(c, cells, colours[:5])
        c.outline(colours[0], set(cells), lit=colours[1])
        mask |= set(cells)
        c.put(int(cx - r * 0.3), int(cy - r * 0.3), colours[5])
    c.save("caustic_soda")


# Sulfur (itemkit): a heap of yellow powder, its top rounded and its foot spreading flat, the grains showing - lemon
# where the light falls, ochre in the shade - and a few rolled off it.
SULFUR = {"1": (150, 104, 22), "2": (194, 150, 30), "3": (226, 196, 50), "4": (246, 228, 100), "5": (255, 250, 186)}
SULFUR_HEAP = itemkit.check([
    "................",
    "................",
    "................",
    "................",
    "................",
    "......455.......",
    ".....54443......",
    "....5445432.....",
    "...454434332....",
    "...4434343322...",
    "..54433433232...",
    ".4543343232321..",
    ".3433322322221..",
    "..22221221111...",
    "................",
    "................",
])


def sulfur_dust():
    img = itemkit.sprite(SULFUR_HEAP, SULFUR)
    for x, y, c in ((13, 13, "4"), (14, 12, "3"), (1, 14, "3")):  # grains rolled off, not ringed
        img.putpixel((x, y), SULFUR[c] + (255,))
    img.save(ITEM / "sulfur_dust.png")


# ---- the paper chain ----

# Wood chips (itemkit): a small pile of fresh chips, each lit along its cut face, its thickness in shade beneath and
# a strip of bark along some of their edges.
CHIPS = {"1": (70, 50, 28), "2": (138, 106, 64), "3": (184, 148, 94), "4": (214, 180, 124), "5": (236, 211, 160),
         "b": (92, 64, 40)}
CHIP_PILE = itemkit.check([
    "................",
    "................",
    "................",
    "......45........",
    ".....4554.......",
    "....45434b......",
    "...454342b..45..",
    "...4342b2..4554.",
    "....22b...45434b",
    "..455...4543422.",
    ".45544.45342b2..",
    ".4543445435b2...",
    "..43342343b2....",
    "...222b222b.....",
    "................",
    "................",
])


def wood_chips():
    itemkit.sprite(CHIP_PILE, CHIPS).save(ITEM / "wood_chips.png")


PULP = [(112, 100, 80), (150, 138, 112), (186, 176, 150), (214, 206, 182), (236, 230, 210), (250, 248, 238)]
PULP_WET = (206, 230, 240)


def pulp_lump(img, cx, cy, rx, ry):
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            d = dx * dx + dy * dy
            if d <= 1:
                lam = -0.6 * dx - 0.6 * dy + 0.5 * math.sqrt(1 - d)
                img.putpixel((x, y), PULP[4 if lam > 0.72 else 3 if lam > 0.4 else 2 if lam > 0.05 else 1 if lam > -0.35
                                          else 0] + (255,))


def wood_pulp():
    """Wood pulp: a wet wad of fibre squeezed together - three soft lumps, each lit on its own, strands of fibre through
    them and loose ends standing out of the wad, water glinting on it."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for cx, cy, rx, ry in ((10.0, 8.0, 4.2, 3.8), (5.6, 8.6, 4.4, 4.0), (8.6, 11.0, 4.6, 3.2)):  # back to front
        pulp_lump(img, cx, cy, rx, ry)
    for x, y in ((3, 7), (4, 6), (11, 6), (12, 7), (7, 11), (8, 12), (10, 10), (11, 11), (5, 10)):
        img.putpixel((x, y), PULP[1] + (255,))
    for x, y in ((1, 9), (0, 10), (14, 5), (15, 4), (13, 13)):
        img.putpixel((x, y), PULP[2] + (255,))
    img = texkit.outline(img)
    for x, y, c in ((4, 6, PULP[5]), (9, 5, PULP[5]), (6, 10, PULP[5]), (10, 6, PULP_WET), (5, 7, PULP_WET)):
        img.putpixel((x, y), c + (255,))
    img.save(ITEM / "wood_pulp.png")


# Fine paper (itemkit): two blank sheets lying one on the other, fanned a little, seen from above at a slant as vanilla's
# paper lies - the one behind turned further round, so its corner stands out above the front one's. A warm ivory (I,
# W where the light catches the upper edges), the sheet behind a step down (i); a cool grey line (c) where the front
# sheet lies on it; the front sheet's near edges crisp (d), its far edges in shade (s); its right corner curling up,
# the underside showing (u, e) under a soft highlight. Ringed in the paper's own warm grey (the cool line counts as
# paper for it). (A flat white sheet with a dog-ear and ruled lines read as a desktop's document icon.)
PAPER = {"W": (255, 253, 244), "I": (247, 242, 226), "i": (236, 229, 208), "s": (216, 207, 184), "d": (192, 182, 158),
         "c": (176, 178, 192), "u": (226, 218, 196), "e": (204, 194, 170)}
PAPER_SHEETS = itemkit.check([
    "................",
    "................",
    "................",
    "...........Is...",
    ".........IIiis..",
    ".......IIcciiis.",
    ".....IIccWsciiis",
    "...sIccWWIIsciss",
    "....sWWIIIIIss..",
    "...WWIIIIIIWWs..",
    ".dWIIIIIIIWuue..",
    "..dIIIIIIIdee...",
    "...dIIIIdd......",
    "....dIdd........",
    ".....d..........",
    "................",
])


def fine_paper():
    ring = dict(PAPER, c=PAPER["d"], W=PAPER["I"])
    itemkit.sprite(PAPER_SHEETS, PAPER, lit=0.8, dark=0.62, ring_from=ring).save(ITEM / "fine_paper.png")


def poster():
    """The item: a poster rolled up and tied, lying diagonally, so it reads as "not yet on the wall"."""
    c = Canvas()
    paper = ramp("4a4640", "a8a292", "d0cabc", "e8e4d8", "f8f6ee", "ffffff")
    tie = rgb("c05a40")
    tie_dark = rgb("7a2e20")
    a, b = (3.0, 13.0), (13.0, 3.0)
    ux, uy = (b[0] - a[0]) / math.dist(a, b), (b[1] - a[1]) / math.dist(a, b)
    cells = {}
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - a[0], y + 0.5 - a[1]
            along = px * ux + py * uy
            across = px * -uy + py * ux  # negative towards the upper left
            if -0.5 <= along <= math.dist(a, b) + 0.5 and abs(across) <= 2.1:
                cells[(x, y)] = (along, across)
    for (x, y), (along, across) in cells.items():
        c.put(x, y, paper[4] if across < -0.6 else paper[3] if across < 0.7 else paper[2])
    c.outline(paper[0], set(cells), lit=paper[1])
    for (x, y), (along, across) in cells.items():
        if abs(along - math.dist(a, b) / 2) < 0.8 and abs(across) < 1.8:
            c.put(x, y, tie if across < 0.5 else tie_dark)
    for (x, y), (along, across) in cells.items():  # the top end, showing the curl of the roll
        if along > math.dist(a, b) - 0.6 and abs(across) < 1.2:
            c.put(x, y, paper[1])
    c.put(5, 10, paper[5])
    c.put(6, 9, paper[5])
    c.save("poster")


if __name__ == "__main__":
    ITEM.mkdir(parents=True, exist_ok=True)
    placed_metal(BAR, VELARIUM_BAR, "velarium_ingot", fleck=True)
    placed_metal(BAR, bar_shades(ZINC), "zinc_ingot")
    placed_metal(BAR, bar_shades(BRASS), "brass_ingot")
    placed_metal(NUGGET, VELARIUM_BAR, "velarium_nugget", fleck=True)
    placed_metal(BAR, RED_HOT_BAR, "red_hot_velarium_ingot")
    placed_metal(BAR, TEMPERED_BAR, "tempered_velarium_ingot", fleck=True)
    reinforced_rod()
    raw_zinc()
    amalgam("iron_amalgam", rgb("969ca6"), AMALGAM_FLECKS[:8], {"F": (138, 132, 126), "f": (72, 68, 66)}, share=0.06)
    amalgam("gold_amalgam", rgb("e8c460"), AMALGAM_FLECKS, {"F": (246, 200, 76), "f": (186, 132, 34)})
    amalgam("copper_amalgam", rgb("e0906a"), AMALGAM_FLECKS, {"F": (234, 146, 98), "f": (168, 86, 52)})
    amalgam("mixed_amalgam", None, MIXED_FLECKS, {"F": (200, 164, 88), "f": (134, 100, 56), "G": (196, 126, 92),
                                                  "g": (118, 78, 60), "H": (150, 146, 140), "h": (86, 84, 80)},
            dull=rgb("706c62"))
    crude_salt()
    salt()
    caustic_soda()
    sulfur_dust()
    wood_chips()
    wood_pulp()
    fine_paper()
    poster()
    print("wrote material item textures to", ITEM)
