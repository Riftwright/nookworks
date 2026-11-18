"""The Block of Velarium: cut crystal facets packed seamlessly, with no rim, so a wall of them shows no grid (the user,
2026-11-06: 「ブロックはむしろNWのブロックを書き直して」 after nookTechnology's Tempered Velarium Block, then 「中間色とか
グラデーション使ってもっと丁寧に描いて」).

Shaded as vanilla's crystals are (amethyst_block, amethyst_cluster, studied for how, not copied): a long ramp of
in-between tones, hue-shifted - bluer in the shadows, pinker in the lights, the Velarium Ingot's lavender between. Each
facet is a tilted plane lit from the upper left, and light runs across it, so it grades from its lit edge to its far
one; the steps are left a little ragged, as vanilla's are, not ruled. Where two facets meet, the edge facing the light
is a bright ridge and the other a crease one step darker in the same hue, never near-black. The brightest point of the
two lightest facets carries a pale sparkle, and two teal inclusions sit in the crystal with a soft halo of in-between
colours. Everything tiles: the facets' seeds and the soft lumps over them repeat round the block, so they run on over
its edges. Not put through texkit, which the other textures are: its noise broke the gradients into speckle (tried
side by side); the ragged steps and soft lumps here do that work. Original pixel art. Deterministic.

Run from the repo root:  python tools/textures/gen_velarium_block.py [--sheet OUT.png]
  --sheet   also writes a before/after sheet at 8x: the block alone and 3x3 (for the seams), beside the ingot
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/nookworks/textures/block/velarium_block.png"
INGOT = ROOT / "src/main/resources/assets/nookworks/textures/item/velarium_ingot.png"

# deep shadow (bluer) to sparkle (pinker), the ingot's ramp - (112, 80, 182), (150, 122, 218) - among them
RAMP = [(32, 18, 70), (44, 26, 92), (57, 35, 114), (72, 46, 136), (89, 60, 158), (108, 77, 178), (128, 97, 196),
        (149, 120, 212), (171, 146, 226), (195, 174, 238), (222, 204, 247), (240, 226, 251)]
# the ingot's teal, and a paler core
TEAL = [(70, 150, 150), (96, 200, 178), (150, 236, 214)]
LIGHT = (-0.55, -0.62, 0.56)  # from the upper left, a little in front

SEED = 2031
FACETS = 8
BASE = 0.6  # the ramp position of a facet square to the viewer
CONTRAST = 0.12  # how far a facet's tilt moves it along the ramp: little, so a wall shows no lattice of dark facets
GRADE = 0.07  # how much light runs across a facet, a pixel
RIDGE = 0.18  # the lit edge where two facets meet
CREASE = 0.12  # and the shadowed one
LUMPS = 0.10  # soft patches over the whole
RAGGED = 0.06  # the steps' ragged edges
DARKEST = 4  # the deepest ramp step a facet goes to, so no dark patch repeats loudly across a wall
GLINTS = ((3, 4, 2), (12, 10, 1))  # teal inclusions: x, y, pixels wide
HALO = 0.3


def _unit(v):
    n = math.sqrt(sum(c * c for c in v))
    return tuple(c / n for c in v)


def _lumps(rng, size=4):
    """Smooth value noise over the block that tiles: a size x size grid of random heights, eased between."""
    grid = [[rng.random() for _ in range(size)] for _ in range(size)]

    def at(x, y):
        fx, fy = x / 16 * size, y / 16 * size
        x0, y0 = int(fx) % size, int(fy) % size
        x1, y1 = (x0 + 1) % size, (y0 + 1) % size
        tx, ty = fx - int(fx), fy - int(fy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        top = grid[y0][x0] * (1 - tx) + grid[y0][x1] * tx
        bottom = grid[y1][x0] * (1 - tx) + grid[y1][x1] * tx
        return top * (1 - ty) + bottom * ty

    return at


def _mix(a, b, t):
    return tuple(round(a[i] * (1 - t) + b[i] * t) for i in range(3))


def velarium_block():
    rng = random.Random(SEED)
    facets = []
    for _ in range(FACETS):
        normal = _unit((rng.uniform(-0.8, 0.8), rng.uniform(-0.8, 0.8), rng.uniform(0.9, 1.4)))
        facets.append((rng.uniform(0, 16), rng.uniform(0, 16), normal, rng.uniform(-0.06, 0.06)))
    # each facet repeated round the block, so they run on over its edges
    seeds = [(sx + ox, sy + oy, normal, tint, i) for i, (sx, sy, normal, tint) in enumerate(facets)
             for ox in (-16, 0, 16) for oy in (-16, 0, 16)]
    lumps = _lumps(rng)
    value = [[0.0] * 16 for _ in range(16)]
    owner = [[0] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            near = sorted(((px - s[0]) ** 2 + (py - s[1]) ** 2, k) for k, s in enumerate(seeds))
            own, other = seeds[near[0][1]], seeds[near[1][1]]
            sx, sy, normal, tint, i = own
            owner[y][x] = i
            facing = sum(normal[k] * LIGHT[k] for k in range(3))
            v = BASE + CONTRAST * (facing - 0.55) + tint
            v += GRADE * (-(px - sx) - (py - sy)) / 2  # lighter toward the facet's upper left
            v += LUMPS * (lumps(px, py) - 0.5) + RAGGED * (rng.random() - 0.5)
            gap = math.sqrt(near[1][0]) - math.sqrt(near[0][0])
            if gap < 1.0:
                toward_light = (other[0] - sx) + (other[1] - sy) < 0  # the next facet lies up and to the left
                v += RIDGE * (1.0 - gap) if toward_light else -CREASE * (1.0 - gap)
            value[y][x] = v
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            step = max(DARKEST, min(len(RAMP) - 2, round(value[y][x] * (len(RAMP) - 1))))
            img.putpixel((x, y), RAMP[step] + (255,))
    brightest = {}
    for y in range(16):
        for x in range(16):
            i = owner[y][x]
            if i not in brightest or value[y][x] > brightest[i][0]:
                brightest[i] = (value[y][x], x, y)
    for _, x, y in sorted(brightest.values(), reverse=True)[:2]:
        img.putpixel((x, y), RAMP[-1] + (255,))
    for gx, gy, wide in GLINTS:
        core = [(gx + k, gy) for k in range(wide)]
        for cx, cy in core:
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                at = ((cx + ox) % 16, (cy + oy) % 16)
                if at not in core:
                    img.putpixel(at, _mix(img.getpixel(at)[:3], TEAL[1], HALO) + (255,))
        img.putpixel(core[0], TEAL[2] + (255,))
        for at in core[1:]:
            img.putpixel(at, TEAL[1] + (255,))
    return img


def sheet(before, after, out):
    """The old block and the new, each alone and 3x3, with the ingot, at 8x on a mid grey."""
    ingot = Image.open(INGOT).convert("RGBA")
    scale, gap = 8, 16
    w = (16 + 48 + 16) * scale + gap * 4
    img = Image.new("RGBA", (w, 2 * 48 * scale + gap * 3), (110, 110, 110, 255))
    for row, block in enumerate((before, after)):
        y = gap + row * (48 * scale + gap)
        img.alpha_composite(block.resize((16 * scale, 16 * scale), Image.NEAREST), (gap, y))
        tiled = Image.new("RGBA", (48, 48))
        for tx in range(3):
            for ty in range(3):
                tiled.alpha_composite(block, (tx * 16, ty * 16))
        img.alpha_composite(tiled.resize((48 * scale, 48 * scale), Image.NEAREST), (gap * 2 + 16 * scale, y))
        img.alpha_composite(ingot.resize((16 * scale, 16 * scale), Image.NEAREST), (gap * 3 + 64 * scale, y))
    img.save(out)


def main(argv):
    before = Image.open(OUT).convert("RGBA") if OUT.exists() else None
    after = velarium_block()
    after.save(OUT)
    if "--sheet" in argv:
        sheet(before or after, after, argv[argv.index("--sheet") + 1])


if __name__ == "__main__":
    main(sys.argv[1:])
