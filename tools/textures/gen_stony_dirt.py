"""Stony Dirt: ground halfway between dirt and cobblestone - stones packed close, with earth between them. Original
pixel art (not vanilla's dirt or cobblestone). Deterministic.

Of three drafts - a few small stones in dirt, half and half, stones packed close with earth between - the user took the
last (2026-10-04). Redrawn 2026-10-06 so it sits beside vanilla's dirt and cobblestone without a seam (the user:
"となりに丸石や土のブロックを置いても上手く馴染むように"): the first drawing's earth was paler and smoother than dirt
and its stones round, soft and ringed in a dark line, so a block of it stood out from both.

- block/stony_dirt.png: the earth is in dirt's four browns and grain, the stones in cobblestone's greys and
  lighting - only the colours and how much of each there is are measured off vanilla's textures (as numbers, below);
  the drawing is ours. The stones are cells round points packed close on the face (wrapping, so the block tiles),
  each cut back from its neighbours by a seam of earth and given a rough edge, and lit as a cobblestone is: brightest
  near its upper left edge, darkest near its lower right; the earth just under a stone lies in its shadow. Drawn
  flat, finished with texkit (ROUGH, tiled); then each material's pixels, darkest to lightest as the finish left them,
  are handed out among its measured shades in vanilla's shares, so the earth has dirt's very tone and the stones
  cobblestone's: no shade or tone either lacks, which is what made the seam.
- models/block/stony_dirt.json (cube_all), blockstates/stony_dirt.json (turned about y at random, as vanilla's dirt
  is, so the stones do not line up block to block), items/stony_dirt.json

Run from the repo root:  python tools/textures/gen_stony_dirt.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"

# vanilla dirt's browns, darkest first, and cobblestone's greys: the colours only, measured off their textures
DIRT = [(89, 61, 41), (121, 85, 58), (150, 108, 74), (185, 133, 92)]
STONE = [(82, 82, 82), (97, 97, 97), (110, 109, 109), (136, 135, 136), (166, 166, 166), (181, 181, 181)]
# How much of the earth each brown covers, in per cent: dirt's own share of each, measured the same way. The stones'
# greys are cobblestone's shares (4, 24, 19, 29, 14, 9) with most of its two darkest taken out: in cobblestone those
# are the joints between the stones, and here the earth is the joint; left in, they made each stone darker than a
# cobblestone's.
DIRT_SHARE = (14, 43, 27, 16)
STONE_SHARE = (2, 13, 20, 36, 18, 11)
SEED = 21
APART = 5.8  # how close two stones' middles may be: about cobblestone's stones
SEAM = 0.8  # the earth between two stones, in px
ROUGH_EDGE = 0.5  # how far a stone's edge wanders from a clean line
EARTH = (1, 1, 1, 1, 2, 2, 2, 3, 3, 0)  # the earth's browns before the finish, drawn in about dirt's measure
SHADE = 0.5  # how often the earth just under a stone lies in its shadow
# How far the earth's order is shaken before its shades are handed out: dirt's grain is a pixel or two, finer than
# the finish's patches. The stones' only breaks ties.
EARTH_JITTER = 35


def wrapped(ax, ay, bx, by):
    """a - b across a face that repeats every 16 px."""
    dx = ax - bx
    dy = ay - by
    return dx - 16 * round(dx / 16), dy - 16 * round(dy / 16)


def middles(rng):
    """Stones' middles packed close, none nearer another than APART (darts thrown until no more fit)."""
    placed = []
    for _ in range(5000):
        x, y = rng.uniform(0, 16), rng.uniform(0, 16)
        if all(math.hypot(*wrapped(x, y, px, py)) >= APART for px, py, _ in placed):
            placed.append((x, y, rng.uniform(0.75, 1.0)))  # the last: how much of its cell the stone fills
    return placed


def cells(rng):
    """Which stone each pixel belongs to, or None for earth."""
    placed = middles(rng)
    wobble = [[rng.uniform(-ROUGH_EDGE, ROUGH_EDGE) for _ in range(16)] for _ in range(16)]
    grid = [[None] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            near = sorted((math.hypot(*wrapped(x + 0.5, y + 0.5, px, py)), i) for i, (px, py, _) in enumerate(placed))
            (d1, i), (d2, _) = near[0], near[1]
            fill = placed[i][2]
            # earth along the seam between two cells, and round a stone that does not fill its cell
            if (d2 - d1) / 2 > SEAM / 2 + wobble[y][x] * 0.5 and d1 < fill * APART * 0.62 + wobble[y][x]:
                grid[y][x] = i
    return grid


def reach(grid, x, y, steps):
    """How many px from (x, y) to the edge of its stone, the shortest of the given ways."""
    me = grid[y][x]
    best = 16
    for dx, dy in steps:
        n = 1
        while n < 16 and grid[(y + dy * n) % 16][(x + dx * n) % 16] == me:
            n += 1
        best = min(best, n)
    return best


def flat(grid, rng):
    """The drawing before the finish. A stone is lit as cobblestone's are, brightest near its upper left edge and
    darkest near its lower right, by how far each pixel is from the one edge against the other; the earth is dirt's
    mid brown with its lighter flecks, and dark just under a stone, in its shadow."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            me = grid[y][x]
            if me is not None:
                light = reach(grid, x, y, ((1, 0), (0, 1), (1, 1))) - reach(grid, x, y, ((-1, 0), (0, -1), (-1, -1)))
                step = math.ceil(light / 2) if light > 0 else math.floor(light / 2)  # a grey for each 2 px, outwards
                colour = STONE[max(0, min(5, 3 + step))]
            else:
                under = grid[y - 1][x] is not None and rng.random() < SHADE
                colour = DIRT[0] if under else DIRT[rng.choice(EARTH)]
            img.putpixel((x, y), colour + (255,))
    return img


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def matched(img, grid, rng):
    """Each material's pixels, darkest to lightest as the finish left them, handed out among its measured shades in
    the share vanilla's own texture has of each: the earth then has dirt's very tone, the stones cobblestone's."""
    out = img.copy()
    for is_stone, shades, shares, jitter in ((False, DIRT, DIRT_SHARE, EARTH_JITTER), (True, STONE, STONE_SHARE, 4)):
        spots = [(x, y) for y in range(16) for x in range(16) if (grid[y][x] is not None) == is_stone]
        spots.sort(key=lambda p: lum(img.getpixel(p)) + rng.uniform(0, jitter))
        total, done = sum(shares), 0
        for i, shade in enumerate(shades):
            upto = round(len(spots) * sum(shares[:i + 1]) / total)
            for p in spots[done:upto]:
                out.putpixel(p, shade + (255,))
            done = upto
    return out


def texture():
    rng = random.Random(SEED)
    grid = cells(rng)
    img = flat(grid, rng)
    done = texkit.finish(img, SEED, tile=True, keep=[STONE[5], DIRT[0]], **texkit.ROUGH)
    return matched(done, grid, rng)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    texture().save(ASSETS / "textures/block/stony_dirt.png")
    write_json(ASSETS / "models/block/stony_dirt.json", {"parent": "minecraft:block/cube_all", "textures": {"all": "nookworks:block/stony_dirt"}})
    turns = [{"model": "nookworks:block/stony_dirt", **({"y": y} if y else {})} for y in (0, 90, 180, 270)]
    write_json(ASSETS / "blockstates/stony_dirt.json", {"variants": {"": turns}})
    write_json(ASSETS / "items/stony_dirt.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/stony_dirt"}})
    print("wrote stony dirt")


if __name__ == "__main__":
    main()
