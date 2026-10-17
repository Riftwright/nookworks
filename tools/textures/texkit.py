"""Texkit: the finishing pass every generated texture goes through, so a flat drawing picks up the grain of a
vanilla texture. Import it from a generator and run `finish(img, seed)` on each image before saving it.

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


def icon(img, seed, keep=(), **tuning):
    """An item icon's whole finish: the grain at ICON strength (or {@code tuning}), then the ring. Pixels that are not
    fully opaque keep their colour exactly."""
    img = img.convert("RGBA")
    settings = dict(ICON)
    settings.update(tuning)
    done = finish(img, seed, keep=keep, **settings)
    src, d = img.load(), done.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            if 0 < src[x, y][3] < 255:
                d[x, y] = src[x, y]
    return outline(done)


def palette_report(img):
    """How many distinct opaque colours an image uses."""
    return len({p[:3] for p in img.convert("RGBA").getdata() if p[3]})
