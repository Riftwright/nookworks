"""Pictures and models of the Item Warp and the Horizontal Item Warp: the look the user picked on 2026-11-13 ("C2").

What the four blocks are made of says what they are drawn from: polished blackstone (the casing), an amethyst shard
(the ring round a sender's mouth, a sender's cut stones), an ender pearl (a receiver's pearl and its teal mouth), gold
for the chevrons, as on the Elevator's and the Sorter's arrows. No vanilla pixel is used: the casing is our own
blotches in polished blackstone's six colours.

How the four read (the user asked that sending and receiving, the way a sender sends, and where things go in and
come out be seen at a glance):
- **violet is what you put things into**: a sender's mouth, on its top (both senders take what is dropped on them);
- **teal is what things come out of**: a receiver's mouth - underneath the upright one, in front of the Horizontal one;
- **gold chevrons are the way things go**: the upright sender's sides say up and down (it sends along its column,
  either way) with a cut amethyst between; the Horizontal sender's sides say forwards, and so does a chevron on its
  top before the mouth; a receiver's sides carry its pearl and one chevron out towards its mouth;
- a receiver's top is a closed cap with the pearl; the Horizontal sender has a cut stone in a ring in front (the way
  it sends), where the mouths have the warp.

The mouths are sunk two pixels into the block (the rim as flat pieces round an eight-sided hole, the hole's walls,
its floor with the warp turning on it and glowing a little in the dark). The blocks stay full cubes: only the four
models change. The warp winds inwards on a sender and outwards on a receiver.

Pictures (all 16x16, the three warps strips of 16 frames with a .mcmeta):
- warp_casing                      the plain casing: undersides, the particle
- warp_back                        an empty sunk panel: the Horizontal sender's back
- warp_side_in / warp_side_out     the upright sender's sides, the upright receiver's
- warp_cap / warp_cap_arrow        a receiver's top; the Horizontal receiver's, with a chevron towards the front
- warp_lens                        the Horizontal sender's front
- warp_send_east/west, warp_give_east/west    the Horizontal pair's sides (east: the front is on the right)
- warp_rim, warp_rim_give, warp_rim_send_top  the faces the sunk mouths' rims are cut from
- warp_lining, warp_lining_give    the mouths' walls
- warp_vortex, warp_vortex_out, warp_vortex_top   the warp: a sender's, a receiver's, the Horizontal sender's smaller one

The shades are placed by this script itself (no texkit pass: the look that was picked is these pixels).

Run from the repo root:  python tools/textures/gen_warp.py        (another assets folder may be named, for a check)
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
MODID = "nookworks"
# polished blackstone's six, dark to light (colours only)
BS = [(22, 15, 16), (31, 18, 27), (39, 34, 28), (49, 44, 54), (60, 57, 71), (78, 75, 84)]
AM = [(58, 34, 104), (84, 52, 142), (104, 74, 164), (126, 94, 186), (146, 110, 208), (170, 126, 240), (204, 150, 242),
      (254, 206, 232)]
VOID = [(10, 6, 20), (22, 10, 44), (40, 16, 82), (64, 26, 128), (98, 44, 178), (140, 76, 224), (190, 134, 248),
        (236, 206, 255)]
PEARL = [(6, 44, 38), (12, 74, 64), (24, 112, 98), (44, 150, 132), (70, 198, 172), (150, 242, 224)]
TEAL = [(6, 44, 38), (10, 62, 54), (16, 84, 74), (24, 108, 96), (36, 134, 118), (52, 160, 140), (84, 200, 176),
        (170, 246, 230)]
TVOID = [(4, 16, 14), (6, 30, 27), (8, 52, 46), (12, 82, 72), (22, 120, 106), (44, 168, 148), (110, 224, 202),
         (200, 252, 240)]
# a chevron's five tones: shadow, low, mid, high, glint - the gold of the Elevator's and the Sorter's arrows
GOLD = [(96, 66, 26), (146, 106, 42), (200, 156, 68), (238, 204, 116), (255, 236, 170)]
FRAMES = 16
ANIMATION = {"animation": {"frametime": 3, "interpolate": True}}
DEPTH = 2          # how far a mouth is sunk, in pixels
GLOW = 12          # the warp's light_emission


def hash01(x, y, seed):
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 0xFFFF


def smooth(x, y, size, seed):
    gx, gy = x / size, y / size
    x0, y0 = math.floor(gx), math.floor(gy)
    fx, fy = gx - x0, gy - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = hash01(x0, y0, seed), hash01(x0 + 1, y0, seed)
    c, d = hash01(x0, y0 + 1, seed), hash01(x0 + 1, y0 + 1, seed)
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def new():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(c) + (255,))


def stone(seed):
    """The casing: blotches of polished blackstone's colours, a lit top and left edge, a shaded bottom and right."""
    img = new()
    for y in range(16):
        for x in range(16):
            t = 0.62 * smooth(x, y, 2.4, seed) + 0.38 * hash01(x, y, seed + 7)
            i = 1 if t < 0.17 else 2 if t < 0.27 else 3 if t < 0.52 else 4 if t < 0.8 else 5
            if y == 14 or x == 14:                       # the shade gathering towards the dark edge
                i = max(1, i - (1 if hash01(x, y, seed + 3) < 0.55 else 0))
            put(img, x, y, BS[i])
    for k in range(16):
        put(img, k, 0, BS[5 if hash01(k, 0, seed + 11) > 0.22 else 4])
        put(img, 0, k, BS[5 if hash01(0, k, seed + 13) > 0.3 else 4])
    for k in range(16):
        put(img, k, 15, BS[2 if hash01(k, 15, seed + 17) > 0.25 else 1])
        put(img, 15, k, BS[(3, 2, 1, 2, 3, 1, 2, 0)[int(hash01(15, k, seed + 19) * 8) % 8]])
    put(img, 0, 0, BS[5])
    put(img, 15, 0, BS[3])
    put(img, 0, 15, BS[3])
    put(img, 15, 15, BS[2])
    return img


def octagon(x, y, half, cut, cx=7.5, cy=7.5):
    dx, dy = abs(x - cx), abs(y - cy)
    return max(dx, dy) <= half and dx + dy <= 2 * half - cut


class Mouth:
    """Where a mouth sits on its face and how big it is: twelve pixels across with a hole of eight, or ten with six."""

    def __init__(self, cy=7.5, big=True):
        self.cx, self.cy = 7.5, cy
        self.half_out, self.cut_out, self.half_in, self.cut_in = (5.5, 3, 3.5, 2) if big else (4.5, 2, 2.5, 1)

    def outer(self, x, y):
        return octagon(x, y, self.half_out, self.cut_out, self.cx, self.cy)

    def hole(self, x, y):
        return octagon(x, y, self.half_in, self.cut_in, self.cx, self.cy)


BIG = Mouth()
TOP = Mouth(cy=9.5, big=False)        # the Horizontal sender's: set back, a chevron before it


def ring(img, m, ramp, glints=None):
    """The raised ring round a mouth, lit from the upper left: its outer slope bright there and dark below right, its
    inner slope the other way round; two glints; its shadow on the stone below right of it. What is inside it is
    left for the caller."""
    cells = [(x, y) for y in range(16) for x in range(16) if m.outer(x, y) and not m.hole(x, y)]
    by_hole = lambda x, y: any(m.hole(x + ax, y + ay) for ax, ay in ((1, 0), (-1, 0), (0, 1), (0, -1)))  # noqa: E731
    for x, y in cells:
        dx, dy = x - m.cx, y - m.cy
        b = -(dx + dy) / (math.hypot(dx, dy) * math.sqrt(2))          # 1 facing the light, -1 facing away
        v = 0.42 - 0.34 * b if by_hole(x, y) else 0.56 + 0.44 * b
        put(img, x, y, ramp[max(0, min(6, round(0.4 + v * 5.8)))])
    if glints is None:
        glints = sorted((c for c in cells if not by_hole(*c)), key=lambda c: (c[0] + c[1], c[1]))[:2]
    put(img, glints[0][0], glints[0][1], ramp[7])
    put(img, glints[1][0], glints[1][1], ramp[6])
    for x, y in cells:
        for ax, ay in ((1, 0), (0, 1), (1, 1)):
            nx, ny = x + ax, y + ay
            if (nx, ny) not in cells and not m.outer(nx, ny) and 0 < nx < 15 and 0 < ny < 15 and (nx - m.cx) + (ny - m.cy) > 2:
                put(img, nx, ny, BS[1])


def vortex(x, y, phase, m, ramp):
    """The warp turning in a mouth: two arms winding into a dark middle. Sampled finely and averaged, so it turns
    smoothly."""
    acc = [0.0, 0.0, 0.0]
    n = 4
    for sy in range(n):
        for sx in range(n):
            px, py = x + (sx + 0.5) / n - 0.5, y + (sy + 0.5) / n - 0.5
            dx, dy = px - m.cx, py - m.cy
            r = math.hypot(dx, dy)
            arm = math.sin(2 * math.atan2(dy, dx) + r * 1.5 - phase)     # two arms, winding
            t = (0.5 + 0.5 * arm) ** 1.3                                  # bright arms, darker between
            depth = min(1.0, r / (m.half_in + 0.4))                       # dark in the middle, lit near the ring
            v = t * (0.42 + 0.7 * depth) + 0.12 * depth
            if r < 1.3:
                v *= (r / 1.3) ** 1.5
            f = max(0.0, min(1.0, v)) * 5.6
            i = int(f)
            k = f - i
            c0, c1 = ramp[i], ramp[min(i + 1, len(ramp) - 1)]
            for j in range(3):
                acc[j] += c0[j] * (1 - k) + c1[j] * k
    return tuple(round(c / (n * n)) for c in acc)


def vortex_strip(turn, m, ramp):
    """The warp's frames, one under another. Two arms: half a turn is the whole loop. `turn` -1 winds the arms
    inwards (a sender takes), 1 outwards (a receiver gives)."""
    out = Image.new("RGBA", (16, 16 * FRAMES), (0, 0, 0, 0))
    for f in range(FRAMES):
        phase = turn * 2 * math.pi * f / FRAMES
        img = new()
        for y in range(16):
            for x in range(16):
                put(img, x, y, vortex(x, y, phase, m, ramp))
        out.paste(img, (0, 16 * f))
    return out


def rim(seed, m, ramp, void, marks=None):
    """A mouth's face with nothing turning in it: a sunk mouth's rim is cut from it (its middle is not seen, and is
    filled in the warp's colour for the game's far-off, shrunken copies of the picture). `marks`: chevrons on the stone
    beside the mouth."""
    img = stone(seed)
    if marks:
        inlay(img, marks)
    ring(img, m, ramp)
    for y in range(16):
        for x in range(16):
            if m.hole(x, y):
                put(img, x, y, void[2])
    return img


def lining(ramp):
    """A sunk mouth's wall, in the warp's light, darker towards the lip."""
    img = new()
    for y in range(16):
        for x in range(16):
            row = y % 2
            c = (ramp[1], ramp[0])[row] if hash01(x, y, 5) > 0.3 else (ramp[2], ramp[1])[row]
            put(img, x, y, c)
    return img


# --- marks -----------------------------------------------------------------------------------------------------------

CHEVRON = ((0, 0), (1, 0), (6, 0), (7, 0), (1, 1), (2, 1), (5, 1), (6, 1), (2, 2), (3, 2), (4, 2), (5, 2), (3, 3), (4, 3))


def chevron(direction, ox, oy):
    """An eight-wide chevron's cells, pointing down, up, left or right, its box's corner at (ox, oy)."""
    cells = []
    for x, y in CHEVRON:
        if direction == "down":
            cells.append((ox + x, oy + y))
        elif direction == "up":
            cells.append((ox + x, oy + 3 - y))
        elif direction == "right":
            cells.append((ox + y, oy + x))
        else:
            cells.append((ox + 3 - y, oy + x))
    return cells


def small_up(ox, oy):
    """A chevron six pixels wide and three high, pointing up (towards the front on a top face)."""
    return [(ox + x, oy + y) for x, y in ((2, 0), (3, 0), (1, 1), (2, 1), (3, 1), (4, 1), (0, 2), (1, 2), (4, 2), (5, 2))]


def inlay(img, groups, lift=True):
    """Gold marks set into the stone. Each mark (a list of cells) is lightest at its upper left and a tone down towards
    its lower right, with one glint where it starts; on bare stone (`lift`) its shadow lies under it."""
    every = set(c for g in groups for c in g)
    for cells in groups:
        lo = min(x + y for x, y in cells)
        hi = max(x + y for x, y in cells)
        for x, y in cells:
            t = (x + y - lo) / max(1, hi - lo)
            put(img, x, y, GOLD[3] if t < 0.3 else GOLD[2] if t < 0.78 else GOLD[1])
        first = min(cells, key=lambda c: (c[0] + c[1], c[1]))
        put(img, first[0], first[1], GOLD[4])
    if lift:
        for x, y in every:
            n = (x, y + 1)
            if n not in every and 0 < n[0] < 15 and 0 < n[1] < 15:
                put(img, n[0], n[1], BS[1])


def pearl(img, cx, cy):
    """A receiver's pearl, four pixels across, lit from the upper left, in a dark seat. (cx, cy): its upper left pixel."""
    rows = ((None, 3, 3, None), (3, 5, 4, 2), (3, 4, 2, 1), (None, 2, 1, None))
    for dy, row in enumerate(rows):
        for dx, i in enumerate(row):
            if i is not None:
                put(img, cx + dx, cy + dy, PEARL[i])
    for x, y in ((cx, cy), (cx + 3, cy), (cx, cy + 3), (cx + 3, cy + 3),
                 (cx + 1, cy - 1), (cx + 2, cy - 1), (cx - 1, cy + 1), (cx - 1, cy + 2),
                 (cx + 4, cy + 1), (cx + 4, cy + 2), (cx + 1, cy + 4), (cx + 2, cy + 4)):
        put(img, x, y, BS[0])


def gem(img, cx, cy):
    """A sender's cut amethyst, four pixels wide and three high, lit from the upper left. (cx, cy): its box's upper
    left pixel."""
    for dx, dy, i in ((1, 0, 6), (2, 0, 5), (0, 1, 6), (1, 1, 5), (2, 1, 4), (3, 1, 2), (1, 2, 3), (2, 2, 1)):
        put(img, cx + dx, cy + dy, AM[i])
    put(img, cx + 1, cy, AM[7])
    for x, y in ((cx, cy), (cx + 3, cy), (cx, cy + 2), (cx + 3, cy + 2), (cx - 1, cy + 1), (cx + 4, cy + 1)):
        put(img, x, y, BS[0])


def panel(img, x0, y0, x1, y1, seed):
    """A panel sunk into the stone: darker inside, the shadow of its upper and left edges falling into it, its lower
    and right edges catching the light."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            t = 0.6 * smooth(x, y, 2.4, seed) + 0.4 * hash01(x, y, seed + 7)
            i = 1 if t < 0.3 else 2 if t < 0.62 else 3
            if y == y0 or x == x0:
                i = 0 if hash01(x, y, seed + 1) > 0.35 else 1
            put(img, x, y, BS[i])
    for x in range(x0, x1 + 1):
        put(img, x, y1 + 1, BS[5] if hash01(x, y1, seed + 2) > 0.3 else BS[4])
    for y in range(y0, y1 + 1):
        put(img, x1 + 1, y, BS[5] if hash01(x1, y, seed + 3) > 0.45 else BS[4])


def side(seed, marks, pearl_at=None, gem_at=None):
    """One side: the casing, a sunk panel, gold chevrons in it, a receiver's pearl or a sender's cut stone."""
    img = stone(seed)
    panel(img, 2, 2, 13, 13, seed + 50)
    if marks:
        inlay(img, marks, lift=False)
    if pearl_at:
        pearl(img, *pearl_at)
    if gem_at:
        gem(img, *gem_at)
    return img


def cap(seed):
    """A receiver's top: closed, the pearl set in its middle."""
    img = stone(seed)
    panel(img, 3, 3, 12, 12, seed + 50)
    pearl(img, 6, 6)
    return img


def cap_arrow(seed):
    """The Horizontal receiver's top: a chevron towards the front (the top of the picture), the pearl behind it."""
    img = stone(seed)
    panel(img, 2, 2, 13, 13, seed + 50)
    inlay(img, [chevron("up", 4, 3)], lift=False)
    pearl(img, 6, 9)
    return img


def lens(seed):
    """The Horizontal sender's front: the ring with a cut stone in it, where a mouth has the warp. A deep stone - the
    warp's own violet - its four facets meeting in a bright point."""
    img = stone(seed)
    ring(img, BIG, AM, glints=((4, 3), (3, 5)))
    for y in range(16):
        for x in range(16):
            if not BIG.hole(x, y):
                continue
            dx, dy = x - 7.5, y - 7.5
            ax, ay = abs(dx), abs(dy)
            if ax < 1 and ay < 1:
                i = 6                                                     # the point
            elif abs(ax - ay) < 0.1:
                i = 5 if dx < 0 and dy < 0 else 2 if dx > 0 and dy > 0 else 3   # the edges between facets
            elif ax > ay:
                i = 4 if dx < 0 else 2                                    # the left facet, the right
            else:
                i = 5 if dy < 0 else 1                                    # the upper facet, the lower
            if (not BIG.hole(x - 1, y) or not BIG.hole(x, y - 1)) and i < 5:
                i = max(1, i - 1)                                         # the ring's shadow on it
            put(img, x, y, VOID[i])
    put(img, 7, 7, VOID[7])
    put(img, 6, 6, VOID[6])
    return img


def pictures():
    pair = lambda direction: [chevron(direction, ox, 4) for ox in (4, 9)]  # noqa: E731
    return {
        "warp_casing": stone(3),
        "warp_back": side(47, []),
        "warp_side_in": side(11, [chevron("up", 4, 3), chevron("down", 4, 10)], gem_at=(6, 7)),
        "warp_side_out": side(13, [chevron("down", 4, 9)], pearl_at=(6, 4)),
        "warp_cap": cap(17),
        "warp_cap_arrow": cap_arrow(23),
        "warp_lens": lens(29),
        "warp_send_east": side(31, pair("right")),
        "warp_send_west": side(37, pair("left")),
        "warp_give_east": side(41, [chevron("right", 9, 4)], pearl_at=(4, 6)),
        "warp_give_west": side(43, [chevron("left", 3, 4)], pearl_at=(8, 6)),
        "warp_rim": rim(5, BIG, AM, VOID),
        "warp_rim_give": rim(7, BIG, TEAL, TVOID),
        "warp_rim_send_top": rim(9, TOP, AM, VOID, marks=[small_up(5, 1)]),
        "warp_lining": lining(AM),
        "warp_lining_give": lining(TEAL),
    }


WARPS = {"warp_vortex": (-1, BIG, VOID), "warp_vortex_out": (1, BIG, TVOID), "warp_vortex_top": (-1, TOP, VOID)}


# --- models ----------------------------------------------------------------------------------------------------------

def tex(name):
    return f"{MODID}:block/{name}"


def well(face, m):
    """The elements of a mouth sunk into `face` (up, down or north): the rim as flat pieces round the hole, the hole's
    walls, its floor with the warp on it.

    (u, v) are the face's picture coordinates as the game lays a picture on that face by default."""
    def point(u, v, d):
        """Model coordinates of picture corner (u, v), d pixels into the block."""
        if face == "up":
            return (u, 16 - d, v)
        if face == "down":
            return (u, d, 16 - v)
        return (16 - u, 16 - v, d)                                        # north

    def box(u0, v0, u1, v1, d0, d1):
        a, b = point(u0, v0, d0), point(u1, v1, d1)
        return [min(a[i], b[i]) for i in range(3)], [max(a[i], b[i]) for i in range(3)]

    hole = m.hole
    # which model direction a wall faces whose normal points along +u, -u, +v, -v of the picture
    wall_dir = {"up": {"u+": "east", "u-": "west", "v+": "south", "v-": "north"},
                "down": {"u+": "east", "u-": "west", "v+": "north", "v-": "south"},
                "north": {"u+": "west", "u-": "east", "v+": "down", "v-": "up"}}[face]
    elements = []
    # the rim: runs of rim cells along each picture row, merged down the rows
    runs = {}
    for v in range(16):
        u = 0
        while u < 16:
            if hole(u, v):
                u += 1
                continue
            u1 = u
            while u1 < 16 and not hole(u1, v):
                u1 += 1
            runs.setdefault((u, u1), []).append(v)
            u = u1
    for (u0, u1), rows in runs.items():
        start = prev = rows[0]
        for v in rows[1:] + [None]:
            if v is None or v != prev + 1:
                lo, hi = box(u0, start, u1, prev + 1, 0, 0)
                elements.append({"from": lo, "to": hi, "faces": {face: {"texture": "#rim", "cullface": face}}})
                start = v
            prev = v
    # the walls: each edge between the hole and the rim, edges in one line joined
    edges = {"u+": {}, "u-": {}, "v+": {}, "v-": {}}
    for v in range(16):
        for u in range(16):
            if not hole(u, v):
                continue
            if not hole(u - 1, v):
                edges["u+"].setdefault(u, []).append(v)
            if not hole(u + 1, v):
                edges["u-"].setdefault(u + 1, []).append(v)
            if not hole(u, v - 1):
                edges["v+"].setdefault(v, []).append(u)
            if not hole(u, v + 1):
                edges["v-"].setdefault(v + 1, []).append(u)
    for way, lines in edges.items():
        for at, along in lines.items():
            along = sorted(along)
            start = prev = along[0]
            for k in along[1:] + [None]:
                if k is None or k != prev + 1:
                    lo, hi = box(at, start, at, prev + 1, 0, DEPTH) if way[0] == "u" else box(start, at, prev + 1, at, 0, DEPTH)
                    wall = {"texture": "#lining", "uv": [start, 0, prev + 1, DEPTH]}
                    if face == "north" and wall_dir[way] in ("east", "west"):
                        # a front mouth's side walls run up the block: their picture turned so it runs along them, its
                        # lip row at the front (unturned, two pixels of depth showed one texel stretched over them)
                        wall["rotation"] = 90 if wall_dir[way] == "east" else 270
                    elements.append({"from": lo, "to": hi, "faces": {wall_dir[way]: wall}})
                    start = k
                prev = k
    us = [u for v in range(16) for u in range(16) if hole(u, v)]
    vs = [v for v in range(16) for u in range(16) if hole(u, v)]
    u0, u1, v0, v1 = min(us), max(us) + 1, min(vs), max(vs) + 1
    lo, hi = box(u0, v0, u1, v1, DEPTH, DEPTH)
    elements.append({"from": lo, "to": hi, "light_emission": GLOW,
                     "faces": {face: {"texture": "#warp", "uv": [u0, v0, u1, v1]}}})
    return elements


def model(textures, face, m, rim_name, lining_name, warp_name):
    """A cube with one face a sunk mouth."""
    box = {d: {"texture": f"#{d}", "cullface": d} for d in ("north", "south", "east", "west", "up", "down") if d != face}
    t = dict(textures)
    t.update({"rim": tex(rim_name), "lining": tex(lining_name), "warp": tex(warp_name)})
    return {"parent": "minecraft:block/block", "textures": t,
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": box}] + well(face, m)}


def models():
    casing = tex("warp_casing")
    side_in, side_out = tex("warp_side_in"), tex("warp_side_out")
    return {
        "item_warp_sender": model(
            {"particle": casing, "down": casing, "north": side_in, "south": side_in, "east": side_in, "west": side_in},
            "up", BIG, "warp_rim", "warp_lining", "warp_vortex"),
        "item_warp_receiver": model(
            {"particle": casing, "up": tex("warp_cap"), "north": side_out, "south": side_out, "east": side_out, "west": side_out},
            "down", BIG, "warp_rim_give", "warp_lining_give", "warp_vortex_out"),
        "horizontal_item_warp_sender": model(
            {"particle": casing, "down": casing, "north": tex("warp_lens"), "south": tex("warp_back"),
             "east": tex("warp_send_east"), "west": tex("warp_send_west")},
            "up", TOP, "warp_rim_send_top", "warp_lining", "warp_vortex_top"),
        "horizontal_item_warp_receiver": model(
            {"particle": casing, "up": tex("warp_cap_arrow"), "down": casing, "south": tex("warp_cap"),
             "east": tex("warp_give_east"), "west": tex("warp_give_west")},
            "north", BIG, "warp_rim_give", "warp_lining_give", "warp_vortex_out"),
    }


def write_json(path, data):
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def shown(path):
    """A path as said: from the repo root when under it (no one's own folders in the output)."""
    try:
        return path.resolve().relative_to(ROOT)
    except ValueError:
        return path


if __name__ == "__main__":
    assets = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "src/main/resources/assets" / MODID
    tdir, mdir = assets / "textures" / "block", assets / "models" / "block"
    tdir.mkdir(parents=True, exist_ok=True)
    mdir.mkdir(parents=True, exist_ok=True)
    for name, image in pictures().items():
        image.save(tdir / f"{name}.png")
        print("wrote", shown(tdir / f"{name}.png"))
    for name, (turn, mouth, ramp) in WARPS.items():
        vortex_strip(turn, mouth, ramp).save(tdir / f"{name}.png")
        write_json(tdir / f"{name}.png.mcmeta", ANIMATION)
        print("wrote", shown(tdir / f"{name}.png"))
    for name, data in models().items():
        write_json(mdir / f"{name}.json", data)
        print("wrote", shown(mdir / f"{name}.json"))
