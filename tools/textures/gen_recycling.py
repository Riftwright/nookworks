"""The textures of recycling (DECISIONS, "Recycling", 2026-10-18): Scrap, the Baler, and what is built of scrap. Original
pixel art, drawn flat and finished with texkit. Deterministic.

- Scrap Bale (block/<age>scrap_bale_side, _top): a cube of crushed junk as a scrapyard's baler packs it, bound with
  steel straps (after the user's reference picture) - pressed layers on the sides, a jumble on top, each piece shaded
  as a lump, rusty, copper and painted bits among the steel; in the scrap item's colours.
- Corrugated Sheet (block/<age>corrugated_sheet): galvanized roofing, its ridges lit from the left (the model stands
  them out in relief, x 1-2, 5-6, 9-10 and 13-14: the texture's light stripes are where they are), a row of roofing
  screws on the ridges top and bottom.
- Painted sheets (block/<colour>_corrugated_sheet): the same sheet painted, a few chips showing the steel.
- Patched Sheet Metal (block/patched_sheet_metal_0..4): riveted steel plate with a soft sheen, weathered in rust
  blotches, some mended with a patch of other metal - five drawings the block picks among at random.
- item/scrap: a heap of scrap, after the reference picture the user gave. item/baler: the press - its cap's top showing, a ram
  coming down between two posts on a strapped bale.

The bare bale and sheet rust over four ages as copper weathers (DECISIONS): the galvanized grey goes dull, then rust
spreads in from the crevices and down from the screws, then it is rust nearly all over. Every age is drawn from the
same layout and one rust map, so a block keeps its shapes as it ages and only its colours move.

Run from the repo root:  python tools/textures/gen_recycling.py
"""
import random
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "src/main/resources/assets/nookworks/textures"
AGES = ("", "exposed_", "weathered_", "oxidized_")

# Five tones each (shadow .. highlight). The four stages of rust a pixel goes through.
CLEAN = [(82, 90, 102), (114, 122, 134), (142, 150, 162), (170, 178, 188), (196, 202, 210)]  # galvanized: bluer and darker than white paint
DULL = [(82, 82, 84), (112, 111, 110), (138, 136, 133), (162, 158, 153), (184, 180, 172)]
RUST = [(110, 60, 34), (146, 82, 44), (174, 104, 56), (196, 128, 74), (212, 152, 96)]
DEEP = [(66, 36, 24), (90, 48, 30), (116, 62, 36), (140, 78, 44), (164, 98, 56)]
STAGES = (CLEAN, DULL, RUST, DEEP)
CREVICE = (54, 52, 54)
PAINT = {
    "red": [(92, 26, 24), (128, 38, 32), (158, 50, 40), (184, 66, 52), (204, 90, 72)],
    "green": [(34, 66, 40), (48, 92, 54), (62, 116, 66), (82, 140, 82), (110, 162, 104)],
    "blue": [(30, 52, 92), (42, 72, 126), (56, 94, 156), (76, 118, 180), (104, 142, 198)],
    "white": [(150, 152, 150), (186, 188, 186), (212, 214, 210), (230, 230, 226), (244, 244, 240)],
}
COPPER_DULL = [(92, 50, 34), (120, 66, 44), (146, 86, 58), (166, 106, 76), (184, 128, 98)]


def value_noise(seed, size=4):
    """Smooth noise in [0, 1) over 16x16, wrapping at the edges (so a face tiles)."""
    rng = random.Random(seed)
    n = 16 // size
    grid = [[rng.random() for _ in range(n)] for _ in range(n)]
    out = [[0.0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            gx, gy = x / size, y / size
            x0, y0 = int(gx), int(gy)
            fx, fy = gx - x0, gy - y0
            fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
            a, b = grid[y0 % n][x0 % n], grid[y0 % n][(x0 + 1) % n]
            c, d = grid[(y0 + 1) % n][x0 % n], grid[(y0 + 1) % n][(x0 + 1) % n]
            out[y][x] = (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
    return out


def stage(age, score):
    """Which rust stage a pixel with this rust score (0 = last to rust, 1 = first) shows at an age."""
    if age == 0:
        return 0
    if age == 1:
        return 2 if score > 0.86 else 1
    if age == 2:
        return 3 if score > 0.82 else 2 if score > 0.46 else 1
    return 1 if score < 0.06 else 2 if score < 0.36 else 3


def rust_map(seed, spots=(), crevices=()):
    """The rust score of each pixel: smooth noise, raised round the crevices and in streaks running down from spots."""
    a, b = value_noise(seed, 4), value_noise(seed + 1, 2)
    score = [[0.7 * a[y][x] + 0.3 * b[y][x] for x in range(16)] for y in range(16)]
    for x, y in crevices:
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            score[(y + dy) % 16][(x + dx) % 16] += 0.2 if (dx, dy) == (0, 0) else 0.1
    for x, y in spots:
        for k in range(0, 6):
            score[(y + k) % 16][x] += 0.45 - 0.07 * k
    lo = min(min(r) for r in score)
    hi = max(max(r) for r in score)
    return [[(v - lo) / (hi - lo) for v in r] for r in score]


def paint(layout, score, age, stages=STAGES, crevice=CREVICE):
    """layout: per pixel (material, tone) - material "metal" rusts, a list of five colours is fixed, None is a
    crevice. Painted and copper bits take rust only at the last age, and then darker."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            cell = layout[y][x]
            if cell is None:
                img.putpixel((x, y), crevice + (255,))
                continue
            mat, tone = cell
            st = stage(age, score[y][x])
            if mat == "metal":
                colour = stages[st][tone]
            else:
                colour = mat[tone] if st < 3 or score[y][x] < 0.5 else RUST[max(0, tone - 1)]
            img.putpixel((x, y), colour + (255,))
    return img


# ---------------------------------------------------------------- the bale

# The bale after the reference picture the user gave (2026-10-18): crushed steel bound with steel straps, in the scrap
# item's colours - warm greys, browner rust - and shaded as vanilla's lumpy blocks are (raw iron, ancient debris):
# each piece a lump lit at its upper left and shadowed at its lower right, the gaps between a dark grey of the same
# hue rather than near-black lines (the user: crisp lines read badly; study vanilla when a texture stalls).
WARM = [(70, 64, 61), (98, 92, 89), (124, 117, 114), (150, 143, 140), (178, 171, 168)]
WARM_DULL = [(72, 66, 60), (96, 89, 82), (118, 110, 101), (140, 131, 121), (162, 152, 141)]
BALE_RUST = [(78, 44, 32), (104, 62, 44), (128, 80, 58), (150, 100, 76), (170, 122, 98)]
WARM_RUST = [(96, 52, 34), (124, 70, 46), (148, 90, 60), (168, 110, 78), (184, 130, 98)]  # the scrap's browner rust
BALE_STAGES = (WARM, WARM_DULL, WARM_RUST, DEEP)
GAP = (74, 63, 57)       # the gap between pieces: the steel's own shadow
GAP_DEEP = (56, 47, 42)  # where a gap is wide
STRAP = [(72, 74, 80), (90, 92, 98), (108, 110, 116), (128, 130, 136), (150, 152, 158)]
# The bits of colour among the steel: duller than fresh paint, scuffed - an old car door, a drum, a sign.
BALE_PAINT = [
    [(74, 34, 30), (98, 46, 40), (120, 60, 50), (140, 78, 64), (158, 98, 84)],
    [(44, 58, 78), (58, 74, 96), (74, 92, 116), (94, 112, 134), (118, 134, 152)],
    [(58, 70, 50), (74, 88, 62), (92, 106, 76), (112, 124, 94), (134, 144, 116)],
    [(150, 146, 136), (172, 168, 158), (190, 186, 176), (204, 200, 190), (216, 212, 202)],
]
BALE_MIX = (0.76, 0.16, 0.04)  # steel, rusty steel, copper; the rest a scrap of paint


def offset(a, b):
    """The shortest way from b to a round a 16 px face that tiles."""
    return ((a - b + 8) % 16) - 8


def jumble(seed, count, squash, gap=0.34):
    """
    Crushed junk: cells round scattered points (wrapping, so a face tiles), each a crushed piece, a gap where two meet.
    {@code squash} weighs the vertical distance: above 1 the cells come out flat and wide, pieces pressed down in
    layers, as on a bale's sides; 1 is the jumble on top. Returns the layout (material, tone per pixel; None in a gap),
    the gaps, and which piece owns each pixel.
    """
    rng = random.Random(seed)
    points = [(rng.uniform(0, 16), rng.uniform(0, 16)) for _ in range(count)]
    pieces = []
    for _ in points:
        roll = rng.random()
        m, r, c = BALE_MIX
        mat = ("metal" if roll < m else BALE_RUST if roll < m + r else COPPER_DULL if roll < m + r + c
               else rng.choice(BALE_PAINT))
        pieces.append((mat, rng.choice((1, 2, 2, 3))))
    owner = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            d = sorted(((offset(x + 0.5, p[0]) ** 2 + (squash * offset(y + 0.5, p[1])) ** 2) ** 0.5, i)
                       for i, p in enumerate(points))
            if d[1][0] - d[0][0] >= gap * squash ** 0.5:
                owner[y][x] = d[0][1]
    reach = {}  # each piece's extent, to shade it as one lump
    for y in range(16):
        for x in range(16):
            i = owner[y][x]
            if i is not None:
                ox, oy = offset(x + 0.5, points[i][0]), offset(y + 0.5, points[i][1])
                rx, ry = reach.get(i, (1.0, 1.0))
                reach[i] = (max(rx, abs(ox)), max(ry, abs(oy)))
    layout = [[None] * 16 for _ in range(16)]
    gaps = []
    for y in range(16):
        for x in range(16):
            i = owner[y][x]
            if i is None:
                gaps.append((x, y))
                continue
            mat, base = pieces[i]
            ox, oy = offset(x + 0.5, points[i][0]), offset(y + 0.5, points[i][1])
            rx, ry = reach[i]
            lean = -(ox / rx + oy / ry)  # +2 at its upper left .. -2 at its lower right
            tone = base + (1 if lean > 0.7 else -1 if lean < -0.9 else 0)
            if owner[(y - 1) % 16][x] is None and lean > -0.3:
                tone = max(tone, base + 1)  # a lit rim where it meets the gap above
            if owner[(y + 1) % 16][x] is None:
                tone = min(tone, base - 1)
            layout[y][x] = (mat, max(0, min(4, tone)))
    return layout, gaps, owner


def strap(layout, owner, cols, rows):
    """Steel straps round the bale, 2 px wide, lit along one edge; the scrap beside a strap a step into its shadow."""
    for sx in cols:
        for y in range(16):
            layout[y][sx] = (STRAP, 3)
            layout[y][sx + 1] = (STRAP, 2)
            owner[y][sx] = owner[y][sx + 1] = -1
            if layout[y][(sx + 2) % 16]:
                m, t = layout[y][(sx + 2) % 16]
                layout[y][(sx + 2) % 16] = (m, max(0, t - 1))
    for sy in rows:
        for x in range(16):
            layout[sy][x] = (STRAP, 4 if x not in cols else 3)
            layout[sy + 1][x] = (STRAP, 2)
            owner[sy][x] = owner[sy + 1][x] = -1
            if x not in cols and x - 1 not in cols and layout[(sy + 2) % 16][x]:
                m, t = layout[(sy + 2) % 16][x]
                layout[(sy + 2) % 16][x] = (m, max(0, t - 1))


def paint_bale(layout, owner, score, age):
    """paint(), the gaps in GAP, deeper where a gap is wide (a gap above and below it too)."""
    img = paint(layout, score, age, BALE_STAGES, GAP)
    for y in range(16):
        for x in range(16):
            if owner[y][x] is None and owner[(y - 1) % 16][x] is None and owner[(y + 1) % 16][x] is None:
                img.putpixel((x, y), GAP_DEEP + (255,))
    return img


# ---------------------------------------------------------------- the sheets

# Per column, period four: the trough lit side, the ridge's lit flank, its crown, the shadow beyond it.
SHEET_TONES = (2, 4, 3, 1)
# on the ridges' crowns (columns 2 and 10, a tone below the lit flank), so a screw's lit head shows against them
SCREWS = ((2, 2), (10, 2), (2, 13), (10, 13))


def sheet_layout(material):
    layout = []
    for y in range(16):
        row = []
        for x in range(16):
            tone = SHEET_TONES[x % 4]
            if y == 0 and tone > 1:
                tone -= 1  # the sheet's cut edge, a shade down
            row.append((material, tone))
        layout.append(row)
    for x, y in SCREWS:  # a screw head as vanilla draws a rivet (the copper block's): lit, its shadow under it - not a
        layout[y][x] = (material, 4)      # dark pixel, which read as a hole (the play-through, 2026-10-18)
        layout[y + 1][x] = (material, 0)
    return layout


def chips(img, seed, colours):
    """A few chips in the paint, the bare steel showing: single pixels on the ridges' crowns and edges."""
    rng = random.Random(seed)
    for x, y in ((2, 6), (14, 11)):
        if rng.random() < 0.8:
            img.putpixel((x, y), colours[SHEET_TONES[x % 4] - 1] + (255,))
    return img


# ---------------------------------------------------------------- patched sheet metal

# Patched Sheet Metal, after vanilla's copper block (2026-10-18): one plate a block, a faint sheen across it, rivets at
# its corners and edge middles (a lit head, its shadow), soft edges; weathered with rust gathered in soft blotches,
# as weathered copper's patina gathers, a little more toward its foot; and on some a patch of other metal riveted on -
# rusty steel, faded green-painted panel, darker steel - with the plate's own sheen, a little toward its grey so it
# sits in the wall, a soft shadow under it. Five drawings (two unpatched) the block picks among at random where it
# stands (its blockstate's weighted list, the plain ones most often).
PLATE = [(76, 72, 70), (102, 97, 94), (126, 120, 116), (148, 142, 137), (170, 164, 158)]
PLATE_RUST = [(80, 46, 32), (106, 64, 44), (130, 82, 58), (150, 102, 76), (168, 122, 96)]
PLATE_GREEN = [(52, 62, 50), (68, 82, 64), (86, 102, 80), (106, 122, 98), (126, 140, 116)]
PLATE_DARK = [(56, 56, 58), (76, 76, 78), (96, 95, 96), (116, 115, 115), (138, 136, 135)]


def muted(tones, f=0.3):
    """A patch's metal a little toward the plate's grey."""
    return [tuple(round(c + (p - c) * f) for c, p in zip(t, q)) for t, q in zip(tones, PLATE)]


PATCH_TONES = {"rust": muted(PLATE_RUST), "green": muted(PLATE_GREEN, 0.35), "dark": muted(PLATE_DARK, 0.2)}
# per drawing: (rust noise seed, how much rust, patches (x, y, w, h, kind), a weld bead (x, y0, y1))
PATCH_DRAWINGS = (
    (11, 0.26, (), None),
    (12, 0.42, (), None),
    (13, 0.3, ((2, 4, 10, 8, "rust"),), None),
    (14, 0.3, ((5, 2, 9, 9, "green"),), None),
    (15, 0.34, ((2, 3, 7, 7, "dark"),), (10, 3, 13)),
)


SHEEN_SHIFT = (0, -6, 5, -3, 3)


def patched(which):
    seed, amount, patches, weld = PATCH_DRAWINGS[which]
    img = Image.new("RGBA", (16, 16))

    def put(x, y, c):
        img.putpixel((x, y), tuple(int(v) for v in c) + (255,))

    def tone_of(tones, t):
        """A shade between two of the five, for the soft steps."""
        t = max(0.0, min(4.0, t))
        a, b = int(t), min(4, int(t) + 1)
        return tuple(tones[a][i] + (tones[b][i] - tones[a][i]) * (t - a) for i in range(3))

    for y in range(16):
        for x in range(16):
            t = 2.0
            # the sheen: a soft band across the plate, upper right to lower left - a little higher or lower on each
            # drawing, so a wall of them does not show the same stripe on every block
            d = (x + y) - 15 + SHEEN_SHIFT[which]
            t += 0.9 if -2 <= d <= 0 else 0.45 if -4 <= d <= 2 else 0
            t += 0.6 if y == 0 or x == 0 else 0
            t -= 0.8 if y == 15 or x == 15 else 0
            put(x, y, tone_of(PLATE, t))
    for x0, y0, w, h, kind in patches:
        tones = PATCH_TONES[kind]
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                d = (x + y) - (x0 + y0 + (w + h) // 2)  # its own sheen
                t = 2.0 + (0.7 if -1 <= d <= 0 else 0.35 if -2 <= d <= 1 else 0)
                t += 0.7 if y == y0 or x == x0 else -0.7 if y == y0 + h - 1 or x == x0 + w - 1 else 0
                put(x, y, tone_of(tones, t))
        for x in range(x0 + 1, x0 + w + 1):  # its soft shadow on the plate
            if y0 + h < 15:
                r, g, b, _ = img.getpixel((x, y0 + h))
                put(x, y0 + h, (r * 0.85, g * 0.85, b * 0.85))
        for y in range(y0 + 1, y0 + h + 1):
            if x0 + w < 15:
                r, g, b, _ = img.getpixel((x0 + w, y))
                put(x0 + w, y, (r * 0.85, g * 0.85, b * 0.85))
        for x, y in ((x0 + 1, y0 + 1), (x0 + w - 2, y0 + 1), (x0 + 1, y0 + h - 2), (x0 + w - 2, y0 + h - 2)):
            put(x, y, tones[4])  # its rivets
    noise, fine = value_noise(seed, 4), value_noise(seed + 50, 2)
    for y in range(16):  # rust in soft blotches, two strengths, more toward the foot
        for x in range(16):
            n = 0.7 * noise[y][x] + 0.3 * fine[y][x] + (0.08 if y > 10 else 0)
            if n > 1 - amount:
                f = 0.7 if n > 1 - amount * 0.5 else 0.4
                r, g, b, _ = img.getpixel((x, y))
                rust = PLATE_RUST[2] if (x + y) % 3 else PLATE_RUST[1]
                put(x, y, (r + (rust[0] - r) * f, g + (rust[1] - g) * f, b + (rust[2] - b) * f))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13), (7, 2), (7, 13)):  # the plate's rivets
        put(x, y, PLATE[4])
        put(x + 1, y + 1, PLATE[1])
    if weld:
        x, y0, y1 = weld
        for y in range(y0, y1):
            put(x, y, PLATE[4] if y % 2 else PLATE[1])
    return img


# ---------------------------------------------------------------- items

# Drawn after the reference picture the user gave (2026-10-18): a heap of scrap - a big bent sheet of steel at the
# back, a washer at the front, a tilted rusty plate, rusty bits, dark brown gaps between - in its warm greys and browns.
SCRAP_ITEM = """
................
................
......kWk.......
.....kLGRk...k..
....kLGkrRk.kWk.
..k.kGDkkRkLWWLk
.kLkWGkDGkLLLRLk
.kGLLGkkDkLWLRGk
.RokDGLkkGkLGRDk
kooRkkLWLkkkDGk.
kRrkWKKKWkPPpk..
.kkkLKKKLkPpopk.
..kkkLGLkkpPpork
...k.kkk.kkprrk.
..........kk....
................
"""

# The Baler: a small press seen as vanilla draws its things (the minecart, the bucket) - its cap's top face showing,
# lit from the upper left, two posts, the ram and its plate, and a bale of scrap held between them, strapped; open
# between the posts so it reads as a frame, not a box.
BALER_ITEM = """
................
...TTTTTTTTTT...
..TttttttttttT..
..FFFFFFFFFFFFf.
..Ff...RR...Ff..
..Ff...Rr...Ff..
..Ff.PPPPPP.Ff..
..Ff.pppppp.Ff..
..Ff.bBbbBb.Ff..
..Ff.cbSbbc.Ff..
..Ff.bcSbcb.Ff..
..Ff.BbSbBb.Ff..
..FFFFFFFFFFFFf.
..ffffffffffffk.
................
................
"""

BALER_COLOURS = {
    "T": (184, 188, 194), "t": (150, 154, 162), "F": (118, 122, 130), "f": (84, 88, 96), "R": (206, 210, 216),
    "r": (150, 154, 160), "P": (132, 136, 144), "p": (98, 102, 110), "b": (150, 143, 140), "B": (118, 110, 104),
    "c": (150, 90, 60), "S": (96, 98, 104), "k": (60, 62, 68),
}

SCRAP_COLOURS = {
    "K": (36, 27, 23), "k": (58, 44, 38), "D": (90, 84, 83), "G": (121, 114, 113), "L": (148, 142, 141),
    "W": (178, 172, 170), "r": (88, 51, 37), "R": (117, 72, 52), "o": (148, 92, 66), "p": (128, 94, 80),
    "P": (156, 120, 104),
}

def from_map(text, colours):
    rows = [r for r in text.strip("\n").split("\n")]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), colours[ch] + (255,))
    return img


# ---------------------------------------------------------------- out

def save(img, name, seed=None, keep=(), **tuning):
    if seed is not None:
        img = texkit.finish(img, seed, keep=keep, tile=True, **tuning)
    path = TEX / f"block/{name}.png"
    img.save(path)


def main():
    # seeds picked from sheets of twelve: pressed layers on the sides, a broken jumble on top; the straps cross the
    # top as they come up the sides
    side, side_gaps, side_owner = jumble(371, 15, 1.5)
    top, top_gaps, top_owner = jumble(332, 12, 1.0)
    strap(side, side_owner, (11,), (11,))
    strap(top, top_owner, (11,), (3,))
    side_rust = rust_map(303, crevices=side_gaps)
    top_rust = rust_map(304, crevices=top_gaps)
    sheet_rust = rust_map(305, spots=[(x, y + 1) for x, y in SCREWS])
    for age, prefix in enumerate(AGES):
        save(paint_bale(side, side_owner, side_rust, age), f"{prefix}scrap_bale_side", 310 + age, keep=(GAP_DEEP,),
             clusters=0.55, grain=0.14, size=3, reach=1)
        save(paint_bale(top, top_owner, top_rust, age), f"{prefix}scrap_bale_top", 320 + age, keep=(GAP_DEEP,),
             clusters=0.55, grain=0.14, size=3, reach=1)
        save(paint(sheet_layout("metal"), sheet_rust, age), f"{prefix}corrugated_sheet", 330 + age,
             clusters=0.4, grain=0.06, size=4, reach=1)
    for i, (colour, tones) in enumerate(PAINT.items()):
        img = chips(paint(sheet_layout(tones), sheet_rust, 0), 340 + i, CLEAN)
        save(img, f"{colour}_corrugated_sheet", 350 + i, clusters=0.4, grain=0, size=5, reach=1)
    for which in range(len(PATCH_DRAWINGS)):
        save(patched(which), f"patched_sheet_metal_{which}", 360 + which, clusters=0.35, grain=0.02, size=5, reach=1)
    texkit.icon(from_map(SCRAP_ITEM, SCRAP_COLOURS), 370, keep=(SCRAP_COLOURS["K"],)).save(TEX / "item/scrap.png")
    texkit.icon(from_map(BALER_ITEM, BALER_COLOURS), 371).save(TEX / "item/baler.png")
    print("wrote the recycling textures")


if __name__ == "__main__":
    main()
