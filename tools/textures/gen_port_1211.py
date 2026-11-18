"""What only the 1.21.1 port has: Minecraft 1.21.1 has no cinnabar and no copper nugget of its own, so the port brings
its own (the 26.3 line uses vanilla's).

- block/cinnabar.png: the rock, a mass of blocky vermilion crystals grown together. Cells round points packed close
  on the face (wrapping, so the block tiles), each one crystal face in one of four reds; a face is lit along its
  upper left edge and falls off towards its lower right, and where two faces meet there is a dark seam along part
  of the join, as between the grains of a cut stone. A few pale flecks where a face catches the light. Drawn flat,
  then finished with texkit.shade (stone, tiled).
- item/copper_nugget.png: a small rounded chunk of copper, lit on its upper left. Placed by hand (itemkit), about the
  size of our Velarium nugget, in copper's own orange.

All drawn here from nothing: no vanilla texture is read or copied.

Run from the repo root:  python tools/textures/gen_port_1211.py [preview.png [other.png ...]]
With a file name it also writes a sheet to look at: the block tiled 3x3, the nugget at 8x on a light and a dark
ground, and any other pictures named, for comparison.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

import itemkit
import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks/textures"

# ---- cinnabar, the block ----

# The reds, seam to fleck: a brick red rather than redstone's scarlet or netherrack's maroon
SEAM = (102, 28, 32)
REDS = [(130, 36, 36), (147, 43, 40), (163, 51, 44), (180, 62, 50)]
FLECK = (206, 90, 72)
SEED = 7
APART = 3.7      # how close two crystals' middles may be
SEAM_WIDTH = 0.75  # how near the join a pixel is a seam
SEAM_SHARE = 0.45  # how much of the joins show a seam at all
FLECKS = 5


def wrapped(ax, ay, bx, by):
    """a - b across a face that repeats every 16 px."""
    dx = ax - bx
    dy = ay - by
    return dx - 16 * round(dx / 16), dy - 16 * round(dy / 16)


def cinnabar_block():
    rng = random.Random(SEED)
    middles = []
    for _ in range(4000):
        x, y = rng.uniform(0, 16), rng.uniform(0, 16)
        if all(math.hypot(*wrapped(x, y, px, py)) >= APART for px, py, _ in middles):
            # a crystal's own red: mostly the two middle ones, so the face is neither blotched nor flat
            middles.append((x, y, rng.choice((0, 1, 1, 1, 2, 2, 2, 3))))
    shown = {}  # whether the join between two crystals shows a seam
    cell = [[0] * 16 for _ in range(16)]
    seam = [[False] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            near = sorted((math.hypot(*wrapped(x + 0.5, y + 0.5, px, py)), i) for i, (px, py, _) in enumerate(middles))
            (d1, a), (d2, b) = near[0], near[1]
            cell[y][x] = a
            pair = (min(a, b), max(a, b))
            if pair not in shown:
                shown[pair] = rng.random() < SEAM_SHARE
            # the seam lies on the lower right side of a join only, a pixel wide: the shadow one face throws on the next
            dx, dy = wrapped(middles[b][0], middles[b][1], middles[a][0], middles[a][1])
            seam[y][x] = shown[pair] and d2 - d1 < SEAM_WIDTH and dx + dy < 0
    img = Image.new("RGBA", (16, 16), SEAM + (255,))
    for y in range(16):
        for x in range(16):
            if seam[y][x]:
                continue
            a = cell[y][x]
            level = middles[a][2]
            # lit along the face's upper left edge, a step down along its lower right
            if cell[(y - 1) % 16][x] != a or cell[y][(x - 1) % 16] != a:
                level = min(3, level + 1)
            elif cell[(y + 1) % 16][x] != a and cell[y][(x + 1) % 16] != a:
                level = max(0, level - 1)
            img.putpixel((x, y), REDS[level] + (255,))
    placed = 0
    while placed < FLECKS:
        x, y = rng.randrange(16), rng.randrange(16)
        if not seam[y][x] and img.getpixel((x, y))[:3] == REDS[3]:
            img.putpixel((x, y), FLECK + (255,))
            placed += 1
    return texkit.shade(img, SEED, tile=True, material="stone", clusters=0.5, grain=0.2, size=3, keep=(FLECK,))


# ---- the copper nugget ----

# Copper, the foot (1) to the highlight (7)
COPPER = {"1": (86, 36, 28), "2": (122, 54, 36), "3": (160, 76, 48), "4": (196, 102, 66), "5": (226, 134, 90),
          "6": (244, 170, 124), "7": (255, 212, 176)}
COPPER_NUGGET = itemkit.check([
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "......566.......",
    ".....5677654....",
    ".....46665543...",
    "......4554432...",
    ".......33321....",
    "................",
    "................",
    "................",
    "................",
    "................",
])


def copper_nugget():
    return itemkit.sprite(COPPER_NUGGET, COPPER)


def main():
    block = cinnabar_block()
    nugget = copper_nugget()
    block.save(ASSETS / "block/cinnabar.png")
    nugget.save(ASSETS / "item/copper_nugget.png")
    if len(sys.argv) > 1:
        tiles = [block] + [Image.open(p).convert("RGBA") for p in map(Path, sys.argv[2:])]
        scale = 5
        side = 3 * 16 * scale
        sheet = Image.new("RGBA", (len(tiles) * (side + 8) + 2 * 16 * 8, side), (40, 40, 44, 255))
        for n, tile in enumerate(tiles):
            for ty in range(3):
                for tx in range(3):
                    sheet.paste(tile.resize((16 * scale, 16 * scale), Image.NEAREST),
                                (n * (side + 8) + tx * 16 * scale, ty * 16 * scale))
        x0 = len(tiles) * (side + 8)
        big = nugget.resize((128, 128), Image.NEAREST)
        for n, ground in enumerate(((198, 198, 198, 255), (56, 56, 60, 255))):
            back = Image.new("RGBA", big.size, ground)
            back.alpha_composite(big)
            sheet.paste(back, (x0 + n * 128, 0))
        sheet.save(sys.argv[1])


if __name__ == "__main__":
    main()
