"""Generate the Mercury Pipe textures (all original pixel art, shaded with texkit.shade as metal). Deterministic.

The pipe is a thick iron tube in the same pale steel as the Mercury Tank, with a dark collar bolted around it
at every joint and an open bore where a run ends:

- mercury_pipe.png        : the barrel, plain pale steel so the collars carry the detail
- mercury_pipe_collar.png : the dark bolted ring that sits at each joint
- mercury_pipe_end.png    : the bore, seen when a pipe ends without a neighbour

The models sample the middle 8x8 of each of these, which is how wide the pipe is, so everything that has
to show sits inside that window.

Run from the repo root:  python tools/textures/gen_mercury_pipe.py
"""
import random
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"

# Straight off the Mercury Tank, so a pipe hanging off the tank reads as the same metal.
STEEL = (200, 202, 207, 255)
STEEL_LIGHT = (211, 213, 218, 255)
STEEL_DARK = (188, 190, 195, 255)
# The tank's trim is nearly black. On a ring only two pixels deep that reads as a gap in the pipe rather
# than a collar, so the band is lifted a couple of steps while staying in the same family.
TRIM = (52, 55, 60, 255)
TRIM_LIGHT = (66, 69, 75, 255)
BOLT = (216, 218, 222, 255)
BOLT_SHADE = (150, 152, 157, 255)
BORE = (12, 13, 15, 255)
BORE_RIM = (60, 62, 67, 255)


def mottle(img, rng, base, light, dark):
    """The tank's faint two-tone speckle, so large flat faces are not dead flat."""
    for y in range(16):
        for x in range(16):
            roll = rng.random()
            img.putpixel((x, y), light if roll < 0.13 else dark if roll < 0.26 else base)


def barrel():
    img = Image.new("RGBA", (16, 16))
    mottle(img, random.Random(11), STEEL, STEEL_LIGHT, STEEL_DARK)
    # Across the pipe (columns 4..11, the width every arm's face takes) shaded round: dark edges and a bright line
    # off the lit one, so the steel reads as a tube rather than a flat grey strip.
    for i, colour in enumerate(((150, 152, 158, 255), (236, 238, 242, 255), STEEL_LIGHT, None, None, None,
                                (172, 174, 180, 255), (150, 152, 158, 255))):
        if colour is not None:
            for y in range(16):
                img.putpixel((4 + i, y), colour)
    return img


def collar():
    """A dark band with a bolt at each end.

    The ring is only two pixels deep, so the models sample rows 6-7: a lit edge and a shadowed one, with a
    bolt across both. The bolts sit symmetrically, because the four faces of the ring sample that same strip
    at different rotations.
    """
    img = Image.new("RGBA", (16, 16))
    mottle(img, random.Random(29), TRIM, TRIM_LIGHT, TRIM)
    for x in range(16):
        img.putpixel((x, 6), TRIM_LIGHT)
        img.putpixel((x, 7), (38, 40, 44, 255))
    for bolt_x in (4, 10):
        for y in range(6, 8):
            for x in range(bolt_x, bolt_x + 2):
                img.putpixel((x, y), BOLT_SHADE if y == 7 else BOLT)
    return img


def end():
    """The bore: a steel flange with corner bolts around a dark opening. Only the middle 8x8 shows."""
    img = Image.new("RGBA", (16, 16))
    mottle(img, random.Random(47), STEEL, STEEL_LIGHT, STEEL_DARK)
    for y in range(6, 10):  # the hole
        for x in range(6, 10):
            img.putpixel((x, y), BORE)
    for i in range(5, 11):  # a rim so the hole reads as depth rather than a painted square
        for p in ((i, 5), (i, 10), (5, i), (10, i)):
            img.putpixel(p, BORE_RIM)
    for corner in ((4, 4), (10, 4), (4, 10), (10, 10)):  # flange bolts, matching the tank's corners
        for y in range(corner[1], corner[1] + 2):
            for x in range(corner[0], corner[0] + 2):
                img.putpixel((x, y), BOLT_SHADE if (x, y) == (corner[0] + 1, corner[1] + 1) else BOLT)
    return img


if __name__ == "__main__":
    BLOCK.mkdir(parents=True, exist_ok=True)
    for seed, (name, fn) in enumerate((("mercury_pipe", barrel), ("mercury_pipe_collar", collar), ("mercury_pipe_end", end))):
        texkit.shade(fn(), 550 + seed, keep=(BORE, BORE_RIM, BOLT, BOLT_SHADE), material="metal", **texkit.SMOOTH).save(BLOCK / f"{name}.png")
        print("wrote", BLOCK / f"{name}.png")
