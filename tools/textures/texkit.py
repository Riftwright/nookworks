"""Texkit: the finishing pass every generated texture goes through, so a flat drawing picks up the grain of a
vanilla texture. Import it from a generator and run `shade(img, seed, material=...)` on each image before saving it.

`shade` (2026-11-04, notes by it below) replaces `finish`: it shades each drawn region in fractions of a step by its
material instead of rounding noise to whole steps, which left speckle. Every generator has moved to it (2026-11-05);
`finish`, described next, is kept for compare_finish.py's before/after sheets.

A generator draws with a handful of flat colours - that is how the shapes stay readable. Vanilla textures are drawn
the same way and then worked over by hand: each colour is really a short ramp of neighbouring shades, the shades
shift hue as they go (shadows cooler, lights warmer), and they are scattered in small clumps and single pixels so a
face is never one flat colour. This does that working-over, deterministically:

1. Every colour becomes a ramp (`ramp`): two steps darker and two lighter, hue-shifted - darker steps turn towards
   blue and gain saturation, lighter ones turn towards yellow and lose a little.
2. Two noises pick a step for each pixel (`finish`):
   - cluster noise: smooth value noise a few pixels across, so shades gather in patches, as on stone or leaves;
   - pixel noise: a few single pixels a step off, for the fine grain.
   The sum is rounded to a step, most pixels stay where they were, and a step never goes past +/-2.
3. What must stay exact is left alone: fully transparent pixels, and any colour listed in `keep` (item outlines,
   highlights, a colour that marks something).
4. Thin lines (a pixel with two or fewer opaque neighbours - a leaf blade, a stem, a cord) get no single-pixel grain,
   only the patches: a one-pixel line speckled pixel by pixel reads as dotted, not textured.

It never moves a pixel or changes its alpha, so a shape drawn one way stays that shape. With `tile=True` the cluster
noise wraps at the edges, so a block face still tiles.

Tuning: `clusters` (how strong the patches are, 0-1.5), `grain` (how many single pixels move, 0-1), `size` (the patch
size in pixels, 2-8), `reach` (how many shades a pixel may move, 1-2). Start from a preset - `finish(img, seed, **PLANT)`:

- PLANT: leaves, crops, flowers. Patches along the blades, little grain.
- ITEM: a held item. Moderate, so the icon still reads at a glance.
- ROUGH: stone, soil, gravel, rusty or cast metal. Strong patches and grain.
- SMOOTH: glaze, polished metal, planed wood, glass. A light touch, mostly patches.

`palette_report(img)` counts colours, to compare before and after.

Item icons have one more pass, `icon(img, seed)`: the finish above at the ICON strength, then `outline`, the ring
round the shape drawn as vanilla draws it. Vanilla never rings an item in black: the ring is the item's own colour
taken down - the darkest colour of the shape near each ring pixel, a little darker on the side the light comes from
(above and left of the shape) and much darker on the side away from it (below and right), so a loaf has a dark crust
round it, iron a dark grey edge and sugar a pale blue-grey one that is darker along its bottom. A generator draws the
shape alone and leaves the ring to this.
"""
import colorsys
import math
import random

from PIL import Image


def _shift(rgb, step):
    """One step darker (negative) or lighter (positive), with the hue moving as painters move it."""
    r, g, b = (c / 255 for c in rgb)
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    # A step is biggest in the mid-tones and smaller towards black and white, as the eye sees it: a pale colour a
    # full mid-tone step darker reads as a stain, not a shade.
    delta = 0.05 + 0.28 * v * (1 - v)
    if step > 0:
        v = v + delta * step
        s = s * (1 - 0.07 * step)
        target = 1 / 6  # yellow
    else:
        v = v + delta * step
        s = min(1.0, s * (1 - 0.06 * step) + 0.02 * -step)
        target = 2 / 3  # blue
    if s > 0.05:  # greys stay grey
        d = ((target - h + 0.5) % 1.0) - 0.5
        h = (h + d * 0.045 * abs(step)) % 1.0
    r, g, b = colorsys.hsv_to_rgb(h, max(0.0, min(1.0, s)), max(0.0, min(1.0, v)))
    return (round(r * 255), round(g * 255), round(b * 255))


def ramp(rgb):
    """The five shades of a colour: index 0 is two steps darker, 2 is the colour, 4 two steps lighter."""
    return [_shift(rgb, s) if s else tuple(rgb) for s in (-2, -1, 0, 1, 2)]


def _value_noise(w, h, size, rng, tile):
    """Smooth noise in [-1, 1]: random values on a grid {@code size} apart, eased between."""
    gw, gh = math.ceil(w / size) + 1, math.ceil(h / size) + 1
    grid = [[rng.uniform(-1, 1) for _ in range(gw)] for _ in range(gh)]
    if tile:
        gw, gh = max(1, w // size), max(1, h // size)

    def at(gx, gy):
        if tile:
            return grid[gy % gh][gx % gw]
        return grid[min(gy, len(grid) - 1)][min(gx, len(grid[0]) - 1)]

    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            fx, fy = x / size, y / size
            x0, y0 = int(fx), int(fy)
            tx, ty = fx - x0, fy - y0
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            top = at(x0, y0) * (1 - tx) + at(x0 + 1, y0) * tx
            bottom = at(x0, y0 + 1) * (1 - tx) + at(x0 + 1, y0 + 1) * tx
            out[y][x] = top * (1 - ty) + bottom * ty
    return out


PLANT = {"clusters": 0.7, "grain": 0.2, "size": 4}
# An item icon: patches of shade and hardly any single-pixel grain - vanilla's icons are shaded in clean bands and
# clumps, not speckled.
ICON = {"clusters": 0.55, "grain": 0.1, "size": 3}
ITEM = {"clusters": 0.8, "grain": 0.3, "size": 3}
ROUGH = {"clusters": 1.0, "grain": 0.45, "size": 3}
SMOOTH = {"clusters": 0.6, "grain": 0.08, "size": 5, "reach": 1}


def _thin(img, x, y):
    w, h = img.size
    around = sum(1 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                 if 0 <= x + dx < w and 0 <= y + dy < h and img.getpixel((x + dx, y + dy))[3])
    return around <= 2


def finish(img, seed, clusters=0.6, grain=0.3, size=3, keep=(), tile=False, reach=2):
    """Returns a copy of {@code img} worked over as described above."""
    img = img.convert("RGBA")
    w, h = img.size
    rng = random.Random(seed)
    noise = _value_noise(w, h, size, rng, tile)
    keep = {tuple(c[:3]) for c in keep}
    ramps = {}
    out = img.copy()
    for y in range(h):
        for x in range(w):
            r, g, b, a = img.getpixel((x, y))
            if a == 0 or (r, g, b) in keep:
                continue
            n = noise[y][x] * clusters
            roll = rng.random()
            if _thin(img, x, y):
                roll = 1.0  # a line keeps its run unbroken; the patches still shade it along its length
            if roll < grain * 0.5:
                n += 1.0
            elif roll < grain:
                n -= 1.0
            step = max(-reach, min(reach, round(n)))
            if step:
                shades = ramps.setdefault((r, g, b), ramp((r, g, b)))
                out.putpixel((x, y), shades[step + 2] + (a,))
    return out


def _lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def _ring(base, factor):
    """A ring colour: {@code base} taken down to {@code factor} of its value. A coloured thing's ring turns a little
    towards blue and deepens; a white or grey one's goes a cool blue-grey, as vanilla's sugar and bone meal do."""
    h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in base))
    if s < 0.08:
        if v > 0.55:
            h, s = 2 / 3, max(s, 0.09)
    else:
        d = ((2 / 3 - h + 0.5) % 1.0) - 0.5
        h = (h + d * 0.06) % 1.0
        s = min(1.0, s * 1.08 + 0.04)
    r, g, b = colorsys.hsv_to_rgb(h, s, v * factor)
    return (round(r * 255), round(g * 255), round(b * 255))


def outline(img, lit=0.8, dark=0.55, reach=2):
    """
    The ring round an item icon, as vanilla draws it (see the module notes): on each clear pixel next to the shape, the
    darkest colour of the shape within {@code reach} of it, taken to {@code lit} of its value where the ring lies above
    or left of the shape and to {@code dark} of it below or right. Only fully opaque pixels count as the shape: a wisp
    of steam or the gleam on glass is neither ringed nor drawn over.
    """
    img = img.convert("RGBA")
    w, h = img.size
    src = img.load()

    def solid(x, y):
        return 0 <= x < w and 0 <= y < h and src[x, y][3] == 255

    out = img.copy()
    o = out.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3]:
                continue
            shape_after = solid(x, y + 1) or solid(x + 1, y)   # the shape is below or right: this is its lit side
            shape_before = solid(x, y - 1) or solid(x - 1, y)  # above or left: its shaded side
            if not (shape_after or shape_before):
                continue
            near = [src[x + dx, y + dy][:3] for dy in range(-reach, reach + 1) for dx in range(-reach, reach + 1)
                    if solid(x + dx, y + dy)]
            base = min(near, key=_lum)
            factor = lit if shape_after and not shape_before else dark if shape_before and not shape_after else (lit + dark) / 2
            o[x, y] = _ring(base, factor) + (255,)
    return out


def retone(img, outline_colour, lit=None, reach=2):
    """
    Two tones for an outline a generator draws itself in one colour: its pixels open only above or to the left, the
    side the light comes from, go {@code lit} - or, with none given, the ring colour `outline` would put there - and
    the rest keep the dark colour, as vanilla's tools keep a near-black outline on their shaded side.
    """
    img = img.convert("RGBA")
    w, h = img.size
    src = img.load()
    target = tuple(outline_colour[:3])

    def clear(x, y):
        return not (0 <= x < w and 0 <= y < h) or src[x, y][3] == 0

    out = img.copy()
    o = out.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3] == 0 or src[x, y][:3] != target:
                continue
            open_sides = [(dx, dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if clear(x + dx, y + dy)]
            if not open_sides or not all(side in ((-1, 0), (0, -1)) for side in open_sides):
                continue
            if lit is not None:
                o[x, y] = tuple(lit[:3]) + (src[x, y][3],)
                continue
            near = [src[x + dx, y + dy][:3] for dy in range(-reach, reach + 1) for dx in range(-reach, reach + 1)
                    if not clear(x + dx, y + dy) and src[x + dx, y + dy][:3] != target and src[x + dx, y + dy][3] == 255]
            if near:
                o[x, y] = _ring(min(near, key=_lum), 0.8) + (src[x, y][3],)
    return out


def icon(img, seed, keep=(), material="plain", **tuning):
    """An item icon's whole finish: shade at ICON strength (or {@code tuning}) by {@code material}, then the ring.
    Pixels that are not fully opaque keep their colour exactly. (shade since 2026-11-05; finish before.)"""
    img = img.convert("RGBA")
    settings = dict(ICON)
    settings.update(tuning)
    done = shade(img, seed, keep=keep, material=material, **settings)
    src, d = img.load(), done.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            if 0 < src[x, y][3] < 255:
                d[x, y] = src[x, y]
    return outline(done)


# ---- shade: the finish that shades instead of speckling (2026-11-04)
# finish above rounds its noise to whole ramp steps, so a pixel either stays or jumps a full shade - islands one step
# off and lone pixels a full step off, the "speckle" the user saw (「まだらに変な点が出るだけ」), and it knows nothing of
# what a region is, so grain beside rivets read as more rivets. shade (first drawn by Nook Technology's session as a
# prototype, texfinish.py) works region by region - a region a 4-connected run of one drawn colour - in quarters of a
# step taken between the ramp's shades, and holds a lone pixel near its own region's neighbours. Each region is shaded
# as its material is:
# - metal: lit across from its top left, sunk under the darker frame round it, brushed in faint dashes along its rows,
#   a few short scratches;
# - brick: each brick a tone of its own, lit along its top and left edges, shaded along its bottom and right, its face
#   mottled, a pit or two; mortar: a gritty grain, darker under a brick's lower edge;
# - wood: each plank a tone of its own, grain along its length, its butt ends darker, its upper edge lit;
# - stone: each block a tone of its own, patches at two sizes, edges chipped light at the top left, dark at the bottom
#   right;
# - plain (food, glaze, cloth, leaves, anything else): soft patches, and a gentle fall of light across a broad face.
# Small details hardly move.

def _hash(x, y, seed):
    h = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
    h = (h * 2654435761) & 0xFFFFFFFF
    h ^= h >> 15
    h = (h * 2246822519) & 0xFFFFFFFF
    h ^= h >> 13
    return ((h >> 8) & 0xFFFF) / 0xFFFF * 2 - 1


def _smooth(x, y, sx, sy, seed, w, h, tile):
    fx, fy = x / sx, y / sy
    x0, y0 = math.floor(fx), math.floor(fy)
    tx, ty = fx - x0, fy - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    cw, ch = max(1, round(w / sx)), max(1, round(h / sy))

    def at(i, j):
        if tile:
            i, j = i % cw, j % ch
        return _hash(i, j, seed)
    top = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * tx
    bottom = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * tx
    return top + (bottom - top) * ty


def _shade(shades, t):
    t = max(-2.0, min(2.0, t))
    i = min(3, math.floor(t + 2))
    f = t + 2 - i
    a, b = shades[i], shades[i + 1]
    return tuple(round(a[k] + (b[k] - a[k]) * f) for k in range(3))


_DASH_TONES = (-0.75, -0.5, 0.0, 0.0, 0.0, 0.0, 0.5, 0.75)


def _dash(x, y, seed):
    """The tone of the brushed dash at (x, y): rows cut into runs of 2 to 5 pixels, each run a tone of its own."""
    pos, k = -int((_hash(y, 0, seed + 20) * 0.5 + 0.5) * 6), 0
    while True:
        n = 4 + int((_hash(y, k, seed + 21) * 0.5 + 0.5) * 5.99)
        if pos + n > x:
            break
        pos += n
        k += 1
    return _DASH_TONES[int((_hash(y, k, seed + 22) * 0.5 + 0.5) * 7.99)]


def _regions(src, w, h, keep):
    rid = [[-1] * w for _ in range(h)]
    regions = []
    for y in range(h):
        for x in range(w):
            if rid[y][x] >= 0 or src[x, y][3] == 0 or src[x, y][:3] in keep:
                continue
            c = src[x, y][:3]
            stack, px = [(x, y)], []
            rid[y][x] = len(regions)
            while stack:
                cx, cy = stack.pop()
                px.append((cx, cy))
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = cx + dx, cy + dy
                    if 0 <= nx < w and 0 <= ny < h and rid[ny][nx] < 0 and src[nx, ny][3] and src[nx, ny][:3] == c:
                        rid[ny][nx] = len(regions)
                        stack.append((nx, ny))
            regions.append(px)
    return rid, regions


# how far a pixel may stand off its like neighbours, by material
_LONE = {"metal": 0.3, "brick": 0.4, "mortar": 0.5, "wood": 0.4, "stone": 0.45, "plain": 0.35}


def shade(img, seed, clusters=0.6, grain=0.3, size=3, keep=(), tile=False, reach=2, levels=4,
          light=0.45, bevel=0.45, material="plain", scratches=2, strength=1.0):
    """
    Returns a copy of {@code img} shaded as its material is (the module notes). {@code material}: one material for the
    whole image, or a dict of drawn colour to material (colours not named take the None key's, else "plain"). The
    tuning of `finish` still applies - {@code clusters} the patches' strength, {@code grain} the single pixels, {@code size} the patch size,
    {@code reach} how far a shade may go - and {@code light} (the lit-from-the-top-left fall across a broad face),
    {@code bevel} (a face sunk under a darker frame), {@code scratches} (bright nicks on broad metal), {@code levels}
    (shades kept to this fraction of a step) and {@code strength} (all of it scaled) are added.
    """
    img = img.convert("RGBA")
    w, h = img.size
    src = img.load()
    keep = {tuple(c[:3]) for c in keep}
    rid, regions = _regions(src, w, h, keep)

    def mat(c):
        if isinstance(material, dict):
            return material.get(tuple(c), material.get(None, "plain"))
        return material

    def other(x, y, dx, dy, r):
        """Whether the neighbour that way is opaque and of another region."""
        nx, ny = x + dx, y + dy
        if tile:
            nx, ny = nx % w, ny % h
        return 0 <= nx < w and 0 <= ny < h and src[nx, ny][3] and rid[ny][nx] != r

    def darker(x, y, dx, dy, c):
        nx, ny = x + dx, y + dy
        return 0 <= nx < w and 0 <= ny < h and src[nx, ny][3] and _lum(src[nx, ny]) < _lum(c) - 6

    t = [[None] * w for _ in range(h)]
    mats = []
    for r, px in enumerate(regions):
        xs = [p[0] for p in px]
        ys = [p[1] for p in px]
        x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
        c = src[px[0][0], px[0][1]][:3]
        m = mat(c)
        mats.append(m)
        broad = len(px) >= 10 and x1 - x0 >= 2 and y1 - y0 >= 2
        own = 0.4 * _hash(r, 7, seed + 30)  # a brick's, a plank's, a stone's own tone
        along_x = x1 - x0 >= y1 - y0         # a plank's grain runs its long way
        for x, y in px:
            v = 0.0
            if m == "metal":
                if broad:
                    u = (x - x0) / max(1, x1 - x0) * 0.4 + (y - y0) / max(1, y1 - y0) * 0.6
                    v += light * (1 - 2 * u)
                    if darker(x, y, 0, -1, c):
                        v -= bevel
                    elif darker(x, y, 0, -2, c) and y1 - y0 >= 5:
                        v -= bevel * 0.4
                    if darker(x, y, -1, 0, c):
                        v -= bevel * 0.6
                    if darker(x, y, 0, 1, c):
                        v += bevel * 0.5
                    if darker(x, y, 1, 0, c):
                        v += bevel * 0.25
                    # brushed: long faint streaks along the rows, a slow wash under them; a pixel's own grain only
                    # where the tuning asks for grain (a smooth plate is not sand)
                    v += clusters * 0.3 * _dash(x, y, seed) + clusters * 0.25 * _smooth(x, y, size + 3, size + 3, seed + 3, w, h, tile)
                    v += 0.25 * grain * _hash(x, y, seed + 4)
                else:
                    v += 0.25 * clusters * _smooth(x, y, size, size, seed, w, h, tile) + 0.15 * grain * _hash(x, y, seed + 2)
            elif m == "plain":
                # food, glaze, cloth, a leaf: soft patches and, on a broad face, a gentle fall of light
                if broad:
                    u = (x - x0) / max(1, x1 - x0) * 0.4 + (y - y0) / max(1, y1 - y0) * 0.6
                    v += light * 0.6 * (1 - 2 * u)
                v += clusters * 0.55 * _smooth(x, y, size, size, seed + 70, w, h, tile) + 0.3 * grain * _hash(x, y, seed + 71)
            elif m == "brick":
                v += own
                if other(x, y, 0, -1, r):
                    v += 0.45
                elif other(x, y, 0, 1, r):
                    v -= 0.5
                if other(x, y, -1, 0, r):
                    v += 0.2
                elif other(x, y, 1, 0, r):
                    v -= 0.3
                v += 0.45 * _smooth(x, y, 2.0, 2.0, seed + r, w, h, tile) + (0.1 + 0.25 * grain) * _hash(x, y, seed + 4)
            elif m == "mortar":
                v += 0.3 * _smooth(x, y, 2.0, 2.0, seed + 5, w, h, tile) + 0.4 * _hash(x, y, seed + 6)
                if other(x, y, 0, -1, r):
                    v -= 0.4  # under a brick's lower edge
            elif m == "wood":
                v += own
                a, b = (x, y) if along_x else (y, x)
                v += 0.45 * _smooth(a, b, 7.0, 1.0, seed + r, w, h, tile)
                # grain lines wandering along the plank
                wave = b + 0.9 * _smooth(a, b, 5.0, 3.0, seed + 40 + r, w, h, tile)
                if abs(((wave + 0.5 * (_hash(r, 3, seed) + 1)) % 3.0) - 1.5) < 0.35:
                    v -= 0.65
                if other(x, y, *((-1, 0) if along_x else (0, -1)), r) or other(x, y, *((1, 0) if along_x else (0, 1)), r):
                    v -= 0.35  # its ends, butted against the next
                if other(x, y, *((0, -1) if along_x else (-1, 0)), r):
                    v += 0.3   # its lit long edge
                v += 0.08 * _hash(x, y, seed + 4)
            elif m == "stone":
                v += own
                v += 0.65 * _smooth(x, y, size, size, seed + r, w, h, tile) + 0.35 * _smooth(x, y, 1.5, 1.5, seed + 50 + r, w, h, tile)
                if other(x, y, 0, -1, r) or other(x, y, -1, 0, r):
                    v += 0.5
                if other(x, y, 0, 1, r) or other(x, y, 1, 0, r):
                    v -= 0.55
                v += (0.08 + 0.2 * grain) * _hash(x, y, seed + 4)
            t[y][x] = v * strength
    # scratches: a short bright run on broad metal faces
    k = 0
    for r, px in enumerate(regions):
        if mats[r] != "metal" or len(px) < 30:
            continue
        for s in range(scratches):
            k += 1
            x, y = px[int((_hash(r, s, seed + 9) * 0.5 + 0.5) * (len(px) - 1))]
            dx = 1 if _hash(r, s, seed + 10) > 0 else -1
            for i in range(2 + (k % 2)):
                xx, yy = x + i * dx, y + (i if (s + k) % 2 else 0)
                if 0 <= xx < w and 0 <= yy < h and rid[yy][xx] == r:
                    t[yy][xx] += 0.55
    # brick pits: one or two dark pixels on a brick, left as they are
    pits = set()
    for r, px in enumerate(regions):
        if mats[r] == "brick" and len(px) >= 8:
            for s in range(1 + (r % 2)):
                x, y = px[int((_hash(r, s, seed + 60) * 0.5 + 0.5) * (len(px) - 1))]
                pits.add((x, y))
    out = img.copy()
    o = out.load()
    for y in range(h):
        for x in range(w):
            if t[y][x] is None:
                continue
            c = src[x, y][:3]
            v = t[y][x]
            r = rid[y][x]
            near = [t[y + dy][x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                    if 0 <= x + dx < w and 0 <= y + dy < h and t[y + dy][x + dx] is not None and rid[y + dy][x + dx] == r]
            if len(near) >= 3:
                m = sum(near) / len(near)
                lim = _LONE[mats[r]]
                if abs(v - m) > lim:
                    v = m + math.copysign(lim, v - m)
            if (x, y) in pits:
                v -= 0.75
            v = max(-reach, min(reach, v))
            v = round(v * levels) / levels
            if v:
                o[x, y] = _shade(ramp(c), v) + (src[x, y][3],)
    return out


def palette_report(img):
    """How many distinct opaque colours an image uses."""
    return len({p[:3] for p in img.convert("RGBA").getdata() if p[3]})
