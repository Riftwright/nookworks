"""Build the Magnet Chest's side from its hand-finished front.

The front is hand-finished (HAND_FINISHED.md): wooden boards held by iron corner brackets, an iron strap round the
middle, and in the centre the iron plate with the magnet and its brass latch. A chest has one front - the one it is
placed facing you with - so its other three sides are this: the same boards and brackets and strap, and where the
front has its plate, more boards, the strap running on across them.

Only the plate and latch are replaced; the brackets, the side boards and the strap's ends are copied across pixel
for pixel, so the sides are the same hand-finished chest. Its colours come from the front's own boards.

- magnet_chest_side.png  <- magnet_chest_front.png

Run from the repo root:  python tools/textures/gen_magnet_chest_side.py
"""
import colorsys
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"

MIDDLE = range(3, 13)  # the columns the plate and latch stand in, top to bottom
STRAP = (7, 8)  # the rows of the iron strap round the chest


def is_wood(p):
    h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in p[:3]))
    return s > 0.35 and 0.02 < h < 0.14 and 0.12 < v < 0.6  # not the brass latch


def main():
    front = Image.open(BLOCK / "magnet_chest_front.png").convert("RGBA")
    side = front.copy()
    # the boards' own colours, darkest to lightest, from the wood the front shows beside its plate
    wood = sorted({front.getpixel((x, y)) for x in range(16) for y in range(16) if is_wood(front.getpixel((x, y)))},
                  key=lambda p: sum(p[:3]))
    dark, light = wood[: len(wood) // 4], wood[len(wood) // 2:]
    mid = wood[len(wood) // 4: len(wood) * 3 // 4]
    rng = random.Random(7)
    # boards across, three rows each, a dark seam under each; the strap's two rows are left for the iron
    boards = [(0, 2), (3, 6), (9, 12), (13, 15)]
    for top, bottom in boards:
        for y in range(top, bottom + 1):
            for x in MIDDLE:
                if y == bottom:
                    c = rng.choice(dark)  # the seam
                elif y == top:
                    c = rng.choice(light)  # the board's lit upper edge
                else:
                    c = rng.choice(mid)
                side.putpixel((x, y), c)
        # a knot on the longer boards
        if bottom - top >= 3:
            kx = rng.choice(list(MIDDLE)[2:-2])
            side.putpixel((kx, top + 1), dark[0])
            side.putpixel((kx + 1, top + 1), dark[len(dark) // 2])
    # the strap runs on across: its rows as the front draws them at the chest's edge, repeated
    for y in STRAP:
        edge = [front.getpixel((x, y)) for x in range(0, 3)]
        for x in MIDDLE:
            side.putpixel((x, y), edge[(x - 3) % 3])
        # rivets where the strap crosses the middle
        for x in (5, 10):
            side.putpixel((x, STRAP[0]), front.getpixel((1, 1)))
    side.save(BLOCK / "magnet_chest_side.png")
    print("wrote magnet_chest_side")


if __name__ == "__main__":
    main()
