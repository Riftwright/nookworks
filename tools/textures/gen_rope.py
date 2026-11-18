"""Generate rope textures (block + item) as 16x16 PNGs.

Run from the repo root:  python tools/textures/gen_rope.py
"""
import math
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "src/main/resources/assets/nookworks/textures"

# Hemp palette, light -> dark.
HL = (222, 199, 146, 255)
LIGHT = (196, 164, 106, 255)
MID = (158, 124, 72, 255)
DARK = (112, 84, 46, 255)
OUTLINE = (66, 48, 26, 255)
TWIST = [HL, LIGHT, MID, DARK]


def rope_block():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # The model samples columns 6..8 for the sides and a 3x3 patch at 6..8 / 6..8 for the ends.
    for y in range(16):
        for i, x in enumerate(range(6, 9)):
            step = (y + i) % 4
            if i == 2:  # right column sits in shadow
                step = min(step + 1, 3)
            img.putpixel((x, y), TWIST[step])
    return img


def rope_item():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cx, cy = 7.5, 7.0
    # A coiled ring: fill pixels whose distance from centre is within the band.
    for y in range(16):
        for x in range(16):
            dx, dy = (x - cx), (y - cy) * 1.15
            d = math.hypot(dx, dy)
            if 2.6 <= d <= 6.2:
                angle = math.atan2(dy, dx)
                step = int(((angle + math.pi) / (2 * math.pi)) * 16 + d) % 4
                # light from the top-left
                if dx + dy > 3:
                    step = min(step + 1, 3)
                img.putpixel((x, y), TWIST[step])
    # Loose end hanging down to the bottom right.
    for i, (x, y) in enumerate([(11, 11), (12, 12), (12, 13), (13, 14), (13, 15)]):
        img.putpixel((x, y), TWIST[(i + 1) % 4])
    # the ring round the coil, as vanilla rings its items: in the rope's own browns, darker below and right
    return texkit.outline(img)


if __name__ == "__main__":
    (TEX / "block").mkdir(parents=True, exist_ok=True)
    (TEX / "item").mkdir(parents=True, exist_ok=True)
    rope_block().save(TEX / "block/rope.png")
    rope_item().save(TEX / "item/rope.png")
    print("wrote", TEX / "block/rope.png", TEX / "item/rope.png")
