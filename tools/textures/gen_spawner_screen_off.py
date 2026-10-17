"""Build the Spawner Controller's dark screen from its lit one.

The console's artwork is hand-finished and shows the screen on: teal bars and the spawner's cage in a black bezel.
The screen is now lit only while the console runs, as a furnace glows only while it burns, so the idle console needs
the same screen switched off.

Only the glass is touched: the display inside the bezel goes to the dark grey of a screen that is off, with the
faint diagonal sheen of the glass. The bezel and every pixel outside the display are copied across unchanged, so
the dark screen stays exactly the hand-finished art it came from.

- spawner_controller_screen_off.png  <- spawner_controller_screen.png

Run from the repo root:  python tools/textures/gen_spawner_screen_off.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"

# The display: inside the bezel, rows 2-7 and columns 2-13 of the face (the rest of the picture is the frame).
DISPLAY = (2, 2, 14, 8)
GLASS = (20, 24, 27)
SHEEN = (30, 36, 40)


def switch_off(image):
    dark = image.copy()
    pixels = dark.load()
    x0, y0, x1, y1 = DISPLAY
    for y in range(y0, y1):
        for x in range(x0, x1):
            # a band of sheen down the glass from top right, as light falls on a screen that is off
            band = (x + y) - (x1 + y0 - 4)
            pixels[x, y] = (SHEEN if 0 <= band <= 1 else GLASS) + (255,)
    return dark


if __name__ == "__main__":
    lit = Image.open(BLOCK / "spawner_controller_screen.png").convert("RGBA")
    switch_off(lit).save(BLOCK / "spawner_controller_screen_off.png")
    print("wrote spawner_controller_screen_off.png")
