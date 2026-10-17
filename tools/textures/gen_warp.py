"""Textures for the Item Warp and the Horizontal Item Warp, one family carved from the same dark stone.

Both kinds of warp are a block of dark rune-stone with a violet glow cut into it: a swirling mouth where items go
in or come out, and channels and chevrons on the faces that say which way they travel.

- warp_stone.png               plain rune-stone, for the faces that do nothing
- warp_portal.png              the swirling mouth: sender's top, receiver's underside, the horizontal warps' front
- item_warp_sender_side.png    chevron at the top, a channel running down into a rune: items go in above
- item_warp_receiver_side.png  a rune, a channel running down to a chevron: items come out below
- item_warp_receiver_top.png   a closed rune ring, so the receiver is not mistaken for a sender from above
- horizontal_item_warp_sender_top.png     an arrow along the top towards the front
- horizontal_item_warp_receiver_top.png   the same arrow ending at a bar

Every face is 16x16 and fully opaque: the blocks are plain cubes.

Run from the repo root:  python tools/textures/gen_warp.py
"""
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"

STONE_DEEP = (26, 23, 34)
STONE_DARK = (38, 34, 49)
STONE = (52, 47, 66)
STONE_LIT = (72, 66, 90)
EDGE_LIT = (92, 86, 112)
CUT = (18, 15, 26)  # the bottom of a carved line, before the glow
GLOW_DIM = (98, 52, 168)
GLOW = (150, 88, 230)
GLOW_HOT = (214, 170, 255)


def px(img, x, y, c):
    img.putpixel((x, y), c + (255,))


def stone(seed):
    """Dark dressed stone: a lit top and left edge, a shaded bottom and right, a little grain and a hairline crack."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            if x == 0 or y == 0:
                c = EDGE_LIT
            elif x == 15 or y == 15:
                c = STONE_DEEP
            elif x == 1 or y == 1:
                c = STONE_LIT
            elif x == 14 or y == 14:
                c = STONE_DARK
            else:
                c = rng.choice((STONE,) * 7 + (STONE_DARK, STONE_DARK, STONE_LIT))
            px(img, x, y, c)
    x, y = rng.randint(3, 6), rng.randint(9, 12)
    for _ in range(4):
        px(img, x, y, STONE_DARK)
        x += 1
        y += rng.choice((-1, 0, 0, 1))
    return img


def carve(img, cells, hot=()):
    """Glowing lines cut into the stone: the glow in the cut, a dark lip on its lower-right side."""
    cells = set(cells)
    for x, y in cells:
        px(img, x, y, GLOW_HOT if (x, y) in hot else GLOW)
    for x, y in cells:
        for nx, ny in ((x + 1, y), (x, y + 1)):
            if (nx, ny) not in cells and 1 < nx < 15 and 1 < ny < 15:
                px(img, nx, ny, CUT)


def chevron_down(top):
    return [(x, top) for x in range(5, 11)] + [(x, top + 1) for x in range(6, 10)] + [(7, top + 2), (8, top + 2)]


def rune(cx, cy):
    """A small diamond rune."""
    return [(cx, cy - 2), (cx + 1, cy - 2), (cx - 1, cy - 1), (cx + 2, cy - 1), (cx - 1, cy), (cx + 2, cy),
            (cx, cy + 1), (cx + 1, cy + 1)]


def portal():
    """A swirling violet mouth in a glowing ring, sunk into the stone."""
    img = stone(5)
    c = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            if r <= 4.6:
                swirl = math.sin(math.atan2(dy, dx) * 2 + r * 1.3)
                colour = STONE_DEEP if r < 1.2 else GLOW if swirl > 0.55 else GLOW_DIM if swirl > -0.2 else CUT
                px(img, x, y, colour)
            elif r <= 5.6:
                px(img, x, y, GLOW_HOT if dx + dy < -3 else GLOW)
            elif r <= 6.4:
                px(img, x, y, CUT if dx + dy > 0 else STONE_DARK)
    return img


def sender_side():
    img = stone(11)
    carve(img, chevron_down(2) + [(7, y) for y in range(5, 8)] + [(8, y) for y in range(5, 8)] + rune(7, 10),
          hot=[(7, 2), (8, 2)])
    return img


def receiver_side():
    img = stone(13)
    carve(img, rune(7, 5) + [(7, y) for y in range(7, 10)] + [(8, y) for y in range(7, 10)] + chevron_down(10),
          hot=[(7, 12), (8, 12)])
    return img


def receiver_top():
    img = stone(17)
    ring = []
    for y in range(16):
        for x in range(16):
            if 4.6 <= math.hypot(x - 7.5, y - 7.5) <= 5.6:
                ring.append((x, y))
    carve(img, ring + rune(7, 8), hot=[(4, 4), (5, 3), (3, 5)])
    return img


def arrow(bar):
    """An arrow up the top face, towards the front; the receiver's ends at a bar."""
    img = stone(23 if bar else 19)
    shaft = [(7, y) for y in range(5, 13)] + [(8, y) for y in range(5, 13)]
    head = [(x, 4) for x in range(5, 11)] + [(x, 3) for x in range(6, 10)] + [(7, 2), (8, 2)]
    cells = shaft + head
    if bar:
        cells += [(x, 13) for x in range(4, 12)]
    carve(img, cells, hot=[(7, 2), (8, 2)])
    return img


if __name__ == "__main__":
    faces = {
        "warp_stone": stone(3),
        "warp_portal": portal(),
        "item_warp_sender_side": sender_side(),
        "item_warp_receiver_side": receiver_side(),
        "item_warp_receiver_top": receiver_top(),
        "horizontal_item_warp_sender_top": arrow(False),
        "horizontal_item_warp_receiver_top": arrow(True),
    }
    for name, image in faces.items():
        image.save(BLOCK / f"{name}.png")
        print("wrote", BLOCK / f"{name}.png")
