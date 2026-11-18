"""Storage Barrel: every face of the five tiers, plus the models and blockstates that put them on a barrel.

Drawn here, every pixel, from cells and ramps (2026-11-12): the set the user chose, F2b1t, over six rounds of drawings
(DECISIONS.md, "The Storage Barrels' new faces"). It replaced their own drawing of the diamond barrel (2026-10-11), at
their own word: 「樽はF2b1tで採用にします。」 Their asks along the way: the lid's and the side's colours a match, the lid's
square frame less dark, the grain less rough; then 「とにかくバニラになじむ、おしゃれな樽」, a picture of the barrel they
had in mind (bands, corner plates with rivets), 「上面の模様はもう少しさりげなく」; the wood nearer vanilla's barrel, the
metal paler; the dark line by the bands made a natural shadow.

- **Side** (one texture all round): staves with seams at x = 1, 5, 10, 14 (the same from either end, so sides meet
  cleanly at the corners); rows 0-2 the staves' tops with a riveted 3x3 corner plate at each end; rows 3-4 a band of the
  tier's metal, row 5 its shadow; a small plate in the middle (x 7-8, y 7-9); rows 12-13 the lower band, row 14 its
  shadow and the foot's corner plates; row 15 the foot.
- **Lid**: a 2-pixel rim (stave ends, notches at 5 and 10), a soft frame groove one ring in from corner plate to corner
  plate, boards inside with seams on rows 5 and 10 running through to the rim's notches, riveted 3x3 corner plates. The
  frame is the lid's outline; seams and notches a shade paler (a wall of lying barrels is no lattice). Drawn turned half
  round, as the blockstate turns the lid on a barrel lying on its side.
- **Open lid**: rim and corner plates; the square inside open on the barrel's floor (boards laid as the lid's, unevenly
  dark, seeded), a warm dark inside, nearer vanilla's open barrel. The whole square opens (DECISIONS: not a round lid).
- **Base**: boards and the four corner plates.
- **Colours**: one wood ramp of seven shades on every face, 85% of the way from a warm brown to a ramp of our own in
  vanilla's barrel's hue, saturation and lightness; each tier's metal (`METALS`) paled - 42% of its colour out, its
  lightness drawn a quarter of the way to the wood's - gold given a paled ramp of its own (`GOLD_PALE`), and the
  brightest shade (rivets, a band's sheen) a tint of each metal's own, lifted less on netherite and velarium.
- Not finished with texkit, on purpose: the user's third complaint was the rough grain. The cells are drawn in ramps, as
  vanilla draws wood. Nothing is taken from the picture they showed, from vanilla's PNGs (measured for their statistics
  only) or from another mod.

- block/<tier>_storage_barrel_side.png, _top.png, _top_open.png, _bottom.png
- models/block/<tier>_storage_barrel.json, <tier>_storage_barrel_open.json   (cube_bottom_top, like the vanilla barrel)
- blockstates/<tier>_storage_barrel.json                               (FACING is the lid; rotations as the vanilla barrel)

Run from the repo root:  python tools/textures/gen_storage.py
"""
import io
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
BLOCK = ASSETS / "textures/block"
TIERS = ("iron", "gold", "diamond", "netherite", "velarium")

# Each tier's metal, dark to light: the shadowed edge, the face, the lit edge and a glint - paled on the barrel
# (metal_ramp); gen_sorter_gears and the upgrades read their own.
METALS = {
    "iron": [(140, 142, 150), (200, 202, 208), (240, 240, 244), (255, 255, 255)],
    "gold": [(156, 116, 34), (230, 184, 56), (255, 226, 96), (255, 246, 176)],
    "diamond": [(46, 142, 134), (80, 206, 194), (126, 250, 238), (214, 255, 250)],
    # netherite's dark metal, lit along its top edge in a pale grey so a band shows against the wood
    "netherite": [(38, 32, 42), (82, 72, 86), (150, 138, 152), (196, 184, 200)],
    # the Block of Velarium's violet
    "velarium": [(70, 44, 124), (140, 100, 206), (206, 176, 246), (232, 216, 255)],
}

# A warm brown between spruce and acacia: 0 a seam's dark, 1-2 shade, 3-4 the body, 5 a lit edge, 6 a highlight.
WOOD = [(84, 52, 24), (103, 66, 31), (121, 79, 38), (135, 89, 43), (147, 98, 48), (159, 108, 54), (171, 118, 61)]
# A ramp of our own in the hue, saturation and lightness of the wood of vanilla's barrel (hue about 32.8, saturation
# about 0.58): what the wood is drawn towards.
BARREL_LIKE = [(82, 58, 30), (102, 74, 40), (117, 85, 47), (128, 93, 52), (139, 102, 58), (149, 110, 63), (159, 117, 68)]
NEARER = 0.85      # how far the wood is from WOOD to BARREL_LIKE
WOOD_LIGHT = 105   # the lightness of the wood's body, which the metals' lightness is drawn towards
DESAT, PULL = 0.42, 0.25
# Gold paled as the others turns to the wood's own tan at its dark end, and its plates lose their edges: its own ramp.
GOLD_PALE = [(150, 126, 52), (192, 168, 88), (212, 196, 124), (236, 228, 172)]
# How far a metal's brightest shade is lifted from its lit shade towards white: less on the dark metals, whose rivets
# stood out like the dots of dice.
GLINT = {"netherite": 0.2, "velarium": 0.32}
GLINT_DEFAULT = 0.45
# The barrel's inside, warm: nearer the inside of vanilla's open barrel than a grey-black pit.
INSIDE = [(34, 22, 9), (44, 29, 11), (53, 36, 14), (72, 50, 22), (86, 61, 29)]

SEAMS = (1, 5, 10, 14)  # staves of 3 and 4 pixels, the same from either end: a 4-pixel stave in the middle
STAVE_TONE = {0: 0, 1: -1, 2: 0, 3: -1}  # the corner post and the middle stave; the two between a shade darker
# Metal by shade, 0 the darkest to 3 the brightest; a half is the colour between two. The plates are darker than the
# bands, so that their rivets stand out.
PLATE = ((1, 1), (3, 0.5), (0, 0))  # the small plate on the side: lit along its top, a rivet's glint
SHEEN = {3: (5, 6, 11), 4: (6, 7), 12: (9, 10, 3), 13: (10, 11)}  # a band's sheen, the lower one not under the upper
CAP = ((1, 1, 1), (0.5, 3, 0.5), (0, 0, 0))  # a corner plate, three by three, a rivet in its middle
FOOT_CAP = ((0.5, 3, 0.5), (0, 0, 0))  # the corner plates at the foot, under the band: two rows
SHADOW = {5: 2, 14: 2}  # under each band, a shadow two shades down in the wood's own shades (staves and seams go on)


def wood_ramp():
    return [tuple(round(a + (b - a) * NEARER) for a, b in zip(ours, theirs)) for ours, theirs in zip(WOOD, BARREL_LIKE)]


def soften(colour):
    """A metal's colour paled, to stand out less from the barrel: DESAT of its colour out, its lightness drawn PULL of
    the way to the wood's (a bright metal a little less bright, a dark one less dark)."""
    light = 0.299 * colour[0] + 0.587 * colour[1] + 0.114 * colour[2]
    grey = [light + (c - light) * (1 - DESAT) for c in colour]
    to = light + (WOOD_LIGHT - light) * PULL
    return tuple(max(0, min(255, round(c * to / light))) for c in grey)


def metal_ramp(tier):
    if tier == "gold":
        return list(GOLD_PALE)
    ramp = [soften(c) for c in METALS[tier]]
    k = GLINT.get(tier, GLINT_DEFAULT)
    ramp[3] = tuple(round(c + (255 - c) * k) for c in ramp[2])
    return ramp


def canvas():
    return [[None] * 16 for _ in range(16)]


def stave(x):
    return sum(1 for s in SEAMS if x > s) % 4


def stamp(cells, x0, y0, rows):
    for dy, row in enumerate(rows):
        for dx, shade in enumerate(row):
            cells[y0 + dy][x0 + dx] = ("m", shade)


def side():
    """The staves between the bands. A stave in a band's shadow is kept above a seam's dark (or the shadow reads as
    dashes); the seams fade at the staves' top ends; no highlight shade; the middle stave's lit edge broken once."""
    cells = canvas()
    lift = {(3, 8), (3, 9), (12, 7), (12, 8), (7, 1), (8, 1), (6, 14), (11, 1), (4, 14)}   # runs of light along a stave
    drop = {(4, 7), (9, 9), (9, 10), (11, 9), (2, 10), (0, 8), (15, 7), (8, 2), (12, 14), (6, 10)}  # and of shade
    deep = {(5, 8), (10, 7), (10, 9), (1, 9), (14, 8), (5, 1)}                             # a seam opening darker
    for y in range(16):
        for x in range(16):
            fall = SHADOW.get(y, 0)
            if y in (3, 12):                                               # a band's lit edge, a sheen along it
                cells[y][x] = ("m", 3 if x in SHEEN[y] else 2)
                continue
            if y in (4, 13):
                cells[y][x] = ("m", 2 if x in SHEEN[y] else 1)
                continue
            if x in SEAMS:
                cells[y][x] = ("w", 2 if y == 0 else 0 if (x, y) in deep or fall >= 2 else 1)
                continue
            shade = 5 if (x - 1) % 16 in SEAMS else 4                      # a stave's first pixel catches the light
            shade += STAVE_TONE[stave(x)]
            if y == 0:
                shade += 1                                                 # the staves' top ends, lit
            if y == 15:
                shade -= 1                                                 # the foot
            if (x, y) in lift:
                shade += 1
            if (x, y) in drop:
                shade -= 1
            shade -= fall
            shade = min(5, shade)
            if fall:
                shade = max(2, shade)
            cells[y][x] = ("w", max(0, min(6, shade)))
    for x0 in (0, 13):
        stamp(cells, x0, 0, CAP)
        stamp(cells, x0, 14, FOOT_CAP)
    stamp(cells, 7, 7, PLATE)
    for x in (7, 8):
        cells[10][x] = ("w", cells[10][x][1] - 1)                          # the plate's shadow
    return cells


def rim(cells, x, y, seam=2):
    """The staves' ends round the lid, two pixels: the sides' seams come up over the edge."""
    along = x if (y < 2 or y > 13) else y
    if along in SEAMS:
        cells[y][x] = ("w", seam)
        return
    cells[y][x] = ("w", 5 if (y == 0 or x == 0) else 4)                    # the outer edge above and left, lit


def boards(cells, lo, hi, seams, seam_shade):
    """Boards across the square lo..hi, their seams on the rows named, each board lit along its first row."""
    for y in range(lo, hi + 1):
        for x in range(lo, hi + 1):
            if y in seams:
                cells[y][x] = ("w", seam_shade)
            else:
                first = y == lo or (y - 1) in seams
                cells[y][x] = ("w", 5 if first else 4)


def corner_plates(cells):
    for x0 in (0, 13):
        for y0 in (0, 13):
            stamp(cells, x0, y0, CAP)


def half_turn(cells):
    """As the lid is seen on a barrel lying on its side, its lid to the front: the blockstate turns the texture half
    round there, so the texture is the drawing turned half round - lit from above where most barrels stand."""
    return [row[::-1] for row in cells[::-1]]


def lid():
    """A groove two shades under the wood for the frame (a shade less on its lower and right sides), the boards' seams
    on the rows where the staves' seams come over the rim, on through the frame to them, a shade paler than the frame."""
    cells = canvas()
    seam = 3
    for y in range(16):
        for x in range(16):
            if min(x, y, 15 - x, 15 - y) <= 1:
                rim(cells, x, y, seam)
    seams = (5, 10)
    boards(cells, 3, 12, seams, seam)
    for i in range(2, 14):                                                 # the frame, from corner plate to corner plate
        cells[2][i] = ("w", 2)
        cells[i][2] = ("w", 2)
        cells[13][i] = ("w", 3)
        cells[i][13] = ("w", 3)
    for y in seams:
        for x in (2, 13):
            cells[y][x] = ("w", min(cells[y][x][1], seam))                 # the seam goes on through the frame
    for x, y in ((5, 4), (6, 4), (7, 4), (8, 8), (9, 8), (10, 8), (4, 12), (5, 12)):
        cells[y][x] = ("w", min(6, cells[y][x][1] + 1))                    # light streaks along the boards
    for x, y in ((10, 4), (11, 4), (4, 7), (5, 7), (6, 7), (10, 6), (11, 6), (9, 12), (10, 12), (7, 11)):
        cells[y][x] = ("w", max(0, cells[y][x][1] - 1))                    # and darker ones
    corner_plates(cells)
    return half_turn(cells)


def open_lid():
    """The lid off: the rim and its corner plates, and the square inside them open down to the barrel's floor - its
    boards laid across as the lid's, unevenly dark."""
    cells = canvas()
    rng = random.Random(7)
    for y in range(16):
        for x in range(16):
            if min(x, y, 15 - x, 15 - y) <= 1:
                rim(cells, x, y)
                continue
            if y == 2 or x == 2:
                inside = 0                                                 # the walls under the rim, in shadow
            elif y == 13 or x == 13:
                inside = 4 if (x, y) == (13, 13) else 3                    # the far walls, catching light
            elif y in (5, 10):
                inside = 0                                                 # the joins of the floor's boards
            elif y == 3 or x == 3:
                inside = 1
            else:
                inside = rng.choice((1, 2, 2, 2))
            cells[y][x] = ("i", inside)
    corner_plates(cells)
    return half_turn(cells)


def base():
    cells = canvas()
    for y in range(16):
        for x in range(16):
            if min(x, y, 15 - x, 15 - y) <= 1:
                rim(cells, x, y)
    boards(cells, 2, 13, (5, 10), 2)
    for x, y in ((4, 3), (5, 3), (9, 7), (10, 7), (5, 12), (6, 12)):
        cells[y][x] = ("w", cells[y][x][1] + 1)
    for x, y in ((10, 4), (11, 4), (4, 8), (5, 8), (8, 11), (9, 11)):
        cells[y][x] = ("w", cells[y][x][1] - 1)
    corner_plates(cells)
    return cells


def paint(cells, wood, metal):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            kind, shade = cells[y][x]
            ramp = {"w": wood, "m": metal, "i": INSIDE}[kind]
            low = int(shade)
            colour = ramp[low]
            if shade != low:                                               # a half: the colour between two shades
                colour = tuple(round((a + b) / 2) for a, b in zip(ramp[low], ramp[low + 1]))
            img.putpixel((x, y), tuple(colour) + (255,))
    return img


def faces():
    return {"side": side(), "top": lid(), "top_open": open_lid(), "bottom": base()}


def write_png(img, path):
    """Saved only if it is not there already as it is: a face that has not changed is not touched."""
    buffer = io.BytesIO()
    img.save(buffer, "PNG")
    if not path.exists() or path.read_bytes() != buffer.getvalue():
        path.write_bytes(buffer.getvalue())


def model(tier, open_lid):
    return {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": f"nookworks:block/{tier}_storage_barrel_top" + ("_open" if open_lid else ""),
            "bottom": f"nookworks:block/{tier}_storage_barrel_bottom",
            "side": f"nookworks:block/{tier}_storage_barrel_side",
        },
    }


# The vanilla barrel's layout: the model's top is the lid, turned to face FACING.
ROTATIONS = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
             "west": {"x": 90, "y": 270}, "east": {"x": 90, "y": 90}}


def blockstate(tier):
    variants = {}
    for facing in ("north", "east", "south", "west", "up", "down"):
        for locked in ("false", "true"):
            for open_lid in ("false", "true"):
                variant = {"model": f"nookworks:block/{tier}_storage_barrel" + ("_open" if open_lid == "true" else "")}
                variant.update(ROTATIONS[facing])
                variants[f"facing={facing},locked={locked},open={open_lid}"] = variant
    return {"variants": variants}


def write_json(path, data):
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


if __name__ == "__main__":
    wood = wood_ramp()
    drawn = faces()
    for tier in TIERS:
        metal = metal_ramp(tier)
        for which, cells in drawn.items():
            write_png(paint(cells, wood, metal), BLOCK / f"{tier}_storage_barrel_{which}.png")
        write_json(ASSETS / f"models/block/{tier}_storage_barrel.json", model(tier, False))
        write_json(ASSETS / f"models/block/{tier}_storage_barrel_open.json", model(tier, True))
        write_json(ASSETS / f"blockstates/{tier}_storage_barrel.json", blockstate(tier))
    print("wrote storage barrel textures, models and blockstates")
