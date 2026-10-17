"""Textures for the Laser, which is modelled as a gun rather than a cube: a mounting plate at the back, the
housing, and a barrel ending in the lens. Drawn in the Generator's charcoal and steel so it reads as one of the
powered machines.

- laser_mount.png   the plate it is bolted on by (back and rim)
- laser_body.png    the housing: steel panel in the charcoal frame, cooling slots
- laser_barrel.png  the barrel's sides: a steel tube with a ring round it
- laser_lens.png    the muzzle: a red lens in a dark ring

Run from the repo root:  python tools/textures/gen_laser.py
"""
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"

# The charcoal and steel of the powered machines (once shared with the Crusher's old faces).
FRAME = (22, 24, 26)
FRAME_LIT = (42, 45, 50)
PANEL = (100, 106, 112)
PANEL_DARK = (68, 73, 78)
BEVEL = (180, 188, 195)
RIVET = (210, 215, 220)
DEEP = (12, 13, 15)
# Roller steel.
ROLL_LIT = (196, 202, 208)
ROLL = (140, 146, 152)
ROLL_DARK = (78, 83, 88)


def px(img, x, y, c):
    img.putpixel((x, y), c + (255,))


def housing(seed):
    """The Generator's frame and panel: charcoal edge, lit bevel top and left, rivets in the corners."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c = FRAME_LIT if (x == 0 or y == 0) else FRAME
            elif x == 1 or y == 1:
                c = BEVEL
            elif x == 14 or y == 14:
                c = PANEL_DARK
            else:
                d = rng.randint(-4, 4)
                c = tuple(v + d for v in PANEL)
            px(img, x, y, c)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px(img, x, y, RIVET)
    return img

LENS_DEEP = (60, 8, 8)
LENS = (170, 22, 18)
LENS_HOT = (240, 70, 40)
LENS_GLINT = (255, 200, 180)


def mount():
    img = housing(31)
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):  # bolts through to the wall
        px(img, x, y, DEEP)
        px(img, x + 1, y + 1, FRAME_LIT)
    return img


def body():
    img = housing(33)
    for y in range(4, 12, 2):  # cooling slots
        for x in range(4, 12):
            px(img, x, y, DEEP)
            px(img, x, y + 1, PANEL_DARK)
    return img


def barrel():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = ROLL_LIT if x % 16 < 5 else ROLL if x % 16 < 11 else ROLL_DARK
            px(img, x, y, c)
    for x in range(16):  # a ring round the barrel
        px(img, x, 7, FRAME)
        px(img, x, 8, FRAME_LIT)
    return img


def lens():
    """The muzzle, at the barrel's own 6 px, pixel for pixel (texture x/y 5..10, the model's uv [5, 5, 11, 11]): a red
    lens hottest at its middle, in a steel ring, the corners the dark of the barrel's end."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            px(img, x, y, ROLL_DARK)
    for y in range(5, 11):
        for x in range(5, 11):
            d = (x - 7.5) ** 2 + (y - 7.5) ** 2
            c = LENS_HOT if d <= 0.5 else LENS if d <= 2.5 else LENS_DEEP if d <= 4.5 else FRAME_LIT if d <= 6.5 and y < 8 else FRAME if d <= 8.5 else ROLL_DARK
            px(img, x, y, c)
    px(img, 6, 7, LENS_GLINT)
    return img


if __name__ == "__main__":
    faces = {"laser_mount": mount(), "laser_body": body(), "laser_barrel": barrel(), "laser_lens": lens()}
    for name, image in faces.items():
        image.save(BLOCK / f"{name}.png")
        print("wrote", BLOCK / f"{name}.png")
