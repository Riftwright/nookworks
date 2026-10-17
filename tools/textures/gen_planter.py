"""Textures for the Planter: a wooden seed box with a drill chute in front.

It is the one farm machine, so it is made of the farm's materials rather than the workshop's steel: oak boards
held by iron corner straps, a top of tilled soil sown in rows, and on the front the hopper window where the seeds
show and the brass chute they drop through into the field.

- planter_side.png    the sides and back
- planter_top.png     soil in furrows, sprouting
- planter_front.png   seed window and chute (the face the field is in front of)
- planter_bottom.png  plain boards

Every face is 16x16 and fully opaque: the block is a plain cube.

Run from the repo root:  python tools/textures/gen_planter.py
"""
import random
from pathlib import Path

from PIL import Image

from gen_machine_faces import LAMPS, lamp

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"

OAK_LIT = (196, 160, 102)
OAK = (168, 134, 82)
OAK_DARK = (132, 102, 58)
OAK_SEAM = (92, 68, 38)
IRON_LIT = (190, 190, 186)
IRON = (128, 128, 126)
IRON_DARK = (70, 70, 70)
SOIL_LIT = (116, 84, 56)
SOIL = (88, 62, 40)
SOIL_DARK = (60, 42, 28)
LEAF_LIT = (140, 206, 86)
LEAF = (92, 160, 58)
BRASS_LIT = (236, 204, 120)
BRASS = (196, 152, 70)
BRASS_DARK = (122, 88, 38)
GLASS = (46, 52, 50)
SEED = (214, 190, 110)
SEED_DARK = (150, 124, 60)
DEEP = (24, 18, 12)


def px(img, x, y, c):
    img.putpixel((x, y), c + (255,))


def boards(seed):
    """Three horizontal oak boards with grain, seams between them."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        row = y % 5
        for x in range(16):
            if row == 4 or y == 15:
                c = OAK_SEAM
            elif row == 0:
                c = OAK_LIT
            else:
                c = rng.choice((OAK, OAK, OAK, OAK_DARK))
            px(img, x, y, c)
    return img


def straps(img):
    """Iron straps down both edges, a nail through each board."""
    for y in range(16):
        px(img, 0, y, IRON_DARK)
        px(img, 1, y, IRON_LIT)
        px(img, 2, y, IRON)
        px(img, 13, y, IRON_LIT)
        px(img, 14, y, IRON)
        px(img, 15, y, IRON_DARK)
    for y in (2, 7, 12):
        px(img, 1, y, IRON_DARK)
        px(img, 14, y, IRON_DARK)


def side():
    img = boards(31)
    straps(img)
    return img


def top():
    """Tilled soil in three furrows, a seedling up in each, inside a wooden rim."""
    rng = random.Random(37)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c = OAK_SEAM
            elif x in (1, 14) or y in (1, 14):
                c = OAK_LIT if x == 1 or y == 1 else OAK_DARK
            else:
                ridge = (y - 2) % 4
                c = SOIL_LIT if ridge == 0 else SOIL_DARK if ridge == 3 else rng.choice((SOIL, SOIL, SOIL_DARK))
            px(img, x, y, c)
    for i, y in enumerate((3, 7, 11)):
        for x in (4 + i % 2 * 2, 9 + i % 2 * 2):
            px(img, x, y, LEAF_LIT)
            px(img, x - 1, y, LEAF)
            px(img, x, y + 1, LEAF)
    return img


def front():
    img = boards(41)
    straps(img)
    # The seed window: a pane in an iron frame with the seeds heaped behind it.
    for y in range(2, 8):
        for x in range(4, 12):
            edge = x in (4, 11) or y in (2, 7)
            px(img, x, y, IRON if edge else GLASS)
    for x in range(5, 11):
        for y in range(5, 7):
            px(img, x, y, SEED if (x + y) % 3 else SEED_DARK)
    px(img, 5, 4, SEED)
    px(img, 9, 4, SEED_DARK)
    px(img, 5, 3, (120, 130, 126))  # a glint on the glass
    # The chute the seeds drop out of, down to the field.
    for y in range(9, 14):
        w = 2 if y < 12 else 3
        for x in range(8 - w, 8 + w):
            c = BRASS_LIT if x == 8 - w else BRASS_DARK if x == 8 + w - 1 else BRASS
            px(img, x, y, c)
    px(img, 7, 13, DEEP)
    px(img, 8, 13, DEEP)
    lamp(img, *LAMPS["planter_front"])  # its status lamp, lit while it plants
    return img


if __name__ == "__main__":
    faces = {"planter_side": side(), "planter_top": top(), "planter_front": front(), "planter_bottom": boards(43)}
    for name, image in faces.items():
        image.save(BLOCK / f"{name}.png")
        print("wrote", BLOCK / f"{name}.png")
