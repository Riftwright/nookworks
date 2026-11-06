"""Deepslate Gravel, Nether Grit and End Gravel: what the Crusher makes of cobbled deepslate, netherrack and End
Stone, for the Sifter.
All original pixel art (pebbles packed close, the bed showing only in the cracks between them, drawn from scratch),
finished with texkit.shade (stone). Deterministic.

Run from the repo root:  python tools/textures/gen_grits.py
"""
import random
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"

# The bed, showing in the cracks; the pebbles' own tones - some cooler, some warmer, as stones in a heap differ - and
# each tone's lit corner, a step up and a little warmer.
PALETTES = {
    "deepslate_gravel": {"bed": (50, 50, 60),
                         "tones": [(62, 62, 72), (72, 71, 79), (80, 79, 85), (90, 88, 91), (68, 68, 77)],
                         "lit": [(78, 78, 88), (88, 87, 94), (98, 96, 99), (110, 107, 106), (84, 84, 93)]},
    "nether_grit": {"bed": (62, 18, 26),
                    "tones": [(92, 30, 36), (106, 38, 38), (120, 46, 42), (134, 58, 48), (98, 34, 40)],
                    "lit": [(110, 42, 46), (126, 52, 48), (142, 64, 54), (158, 78, 62), (118, 46, 50)]},
    # End Stone's pale yellow, crushed: chips a little apart in tone, the bed between them a dusky olive
    "end_gravel": {"bed": (128, 126, 86),
                   "tones": [(186, 186, 128), (198, 199, 139), (208, 209, 149), (218, 218, 158), (192, 194, 136)],
                   "lit": [(202, 203, 144), (214, 215, 155), (224, 225, 165), (232, 232, 176), (208, 210, 151)]},
}
PEBBLES = 34


def _torus_d2(a, b):
    dx, dy = abs(a[0] - b[0]), abs(a[1] - b[1])
    dx, dy = min(dx, 16 - dx), min(dy, 16 - dy)
    return dx * dx + dy * dy


def _spread(rng, n, candidates=12):
    """n points over the tile, wrapping at its edges, evenly but not in rows: each the farthest from those already
    placed of a dozen random tries (Mitchell's best candidate)."""
    points = [(rng.uniform(0, 16), rng.uniform(0, 16))]
    while len(points) < n:
        tries = [(rng.uniform(0, 16), rng.uniform(0, 16)) for _ in range(candidates)]
        points.append(max(tries, key=lambda p: min(_torus_d2(p, q) for q in points)))
    return points


def grit(palette, seed):
    """
    Some thirty pebbles two or three pixels across, packed so they touch: each pixel belongs to the pebble nearest it
    (some pebbles drawn out long, some across, so they are not all round). A pebble is one tone of its own, lit at its
    top-left corner; along its lower edge, and a little less often its right one, the bed shows in a crack a pixel
    wide, the shadow side under top-left light - so the bed is never a flat area of its own. Wraps at the edges.
    """
    rng = random.Random(seed)
    pebbles = [(x, y, rng.uniform(0.75, 1.33), rng.randrange(len(palette["tones"]))) for x, y in _spread(rng, PEBBLES)]

    def owner(x, y):
        def reach(k):
            px, py, stretch, _ = pebbles[k]
            dx, dy = abs(x + 0.5 - px), abs(y + 0.5 - py)
            dx, dy = min(dx, 16 - dx), min(dy, 16 - dy)
            return (dx / stretch) ** 2 + (dy * stretch) ** 2
        return min(range(len(pebbles)), key=reach)

    cell = {(x, y): owner(x, y) for y in range(16) for x in range(16)}
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            k = cell[(x, y)]
            tone = pebbles[k][3]
            colour = palette["tones"][tone]
            roll = rng.random()
            if (cell[(x, (y + 1) % 16)] != k and roll < 0.45) or (cell[((x + 1) % 16, y)] != k and roll < 0.4):
                colour = palette["bed"]
            elif cell[(x, (y - 1) % 16)] != k and cell[((x - 1) % 16, y)] != k:
                colour = palette["lit"][tone]
            img.putpixel((x, y), colour + (255,))
    # one shade either way at most: two made near-black pits that showed the tile's repeat
    return texkit.shade(img, seed, keep=(palette["bed"],), tile=True, material="stone", **dict(texkit.ROUGH, reach=1))


def main():
    for name, seed in (("deepslate_gravel", 91), ("nether_grit", 90), ("end_gravel", 92)):
        grit(PALETTES[name], seed).save(BLOCK / f"{name}.png")
    print("wrote the grits")


if __name__ == "__main__":
    main()
