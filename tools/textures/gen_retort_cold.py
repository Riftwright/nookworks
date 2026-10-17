"""Build the unlit faces of the Sealed Retort from its lit ones.

The retort's artwork is hand-finished and shows the firebox burning: an orange window on the front and hot
slits down the sides. A machine that looks the same whether it is working or not is no use to read, so this
takes those two faces and puts the fire out, leaving the same iron with a dark, cold opening behind it.

Only the fire is touched. Every other pixel is copied across unchanged, so the cold faces stay exactly the
hand-finished art they came from.

- retort_front.png  <- retort_front_lit.png
- retort_side.png   <- retort_side_lit.png

Run from the repo root:  python tools/textures/gen_retort_cold.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"
FACES = ["retort_front", "retort_side"]

# What is left when the fire is out: cold iron in shadow, keeping the shape the flames had.
EMBER_OUT = (26, 24, 26)
DEEP_OUT = (16, 15, 17)


def burning(pixel):
    """A pixel that is part of the fire rather than the iron around it: warm, and not merely dark rust."""
    red, green, blue = pixel[:3]
    return red > blue + 40 and red > 90


def put_out(image):
    cold = image.copy()
    pixels = cold.load()
    for y in range(cold.height):
        for x in range(cold.width):
            pixel = pixels[x, y]
            if not burning(pixel):
                continue
            # The brightest part of the fire was the deepest part of the firebox, so it ends up darkest.
            out = DEEP_OUT if pixel[0] > 200 else EMBER_OUT
            pixels[x, y] = out + pixel[3:]
    return cold


if __name__ == "__main__":
    for face in FACES:
        lit = Image.open(BLOCK / f"{face}_lit.png").convert("RGBA")
        put_out(lit).save(BLOCK / f"{face}.png")
        print("wrote", BLOCK / f"{face}.png")
