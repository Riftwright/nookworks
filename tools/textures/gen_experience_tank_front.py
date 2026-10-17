"""Build the Experience Tank's front from its hand-finished side.

The side is hand-finished (HAND_FINISHED.md): a steel frame round a recessed panel with a window in its middle, the
liquid experience glowing through it. The front is the side the tap is on, and a tap has no window over it: the
front is the same frame and panel with the window closed over in the panel's own steel. The sight glass models lay
the level over the windows of the other three sides.

Only the window and its dark rim are replaced; the frame and the rest of the panel are copied across pixel for pixel,
so the front is the same hand-finished tank. The steel put in comes from the panel's own pixels.

- experience_tank_front.png  <- experience_tank_side.png

Run from the repo root:  python tools/textures/gen_experience_tank_front.py
"""
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"

PANEL = (2, 2, 13, 13)  # the recessed panel inside the frame, inclusive
WINDOW = (5, 4, 10, 11)  # the window with its rim, inclusive


def inside(box, x, y):
    return box[0] <= x <= box[2] and box[1] <= y <= box[3]


def main():
    side = Image.open(BLOCK / "experience_tank_side.png").convert("RGBA")
    front = side.copy()
    # the panel's steel: every pixel of the panel round the window, as drawn
    steel = sorted({side.getpixel((x, y)) for x in range(16) for y in range(16)
                    if inside(PANEL, x, y) and not inside(WINDOW, x, y)}, key=lambda p: sum(p[:3]))
    steel = steel[len(steel) // 8: len(steel) * 7 // 8]  # not the few darkest and lightest: no new marks
    rng = random.Random(11)
    for y in range(WINDOW[1], WINDOW[3] + 1):
        for x in range(WINDOW[0], WINDOW[2] + 1):
            front.putpixel((x, y), rng.choice(steel))
    front.save(BLOCK / "experience_tank_front.png")
    print("wrote experience_tank_front.png")


if __name__ == "__main__":
    main()
