"""Generate the laser beam texture (original pixel art). Deterministic.

A beacon-style beam strip: white, so the renderer's colour does the tinting, with soft lengthwise striations
so the scrolling reads as movement rather than a flat bar.

Run from the repo root:  python tools/textures/gen_laser_beam.py
"""
import math
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/nookworks/textures/entity/laser_beam.png"


def main():
    size = 16
    img = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            # Bright down the middle, falling off to the edges, with a faint travelling ripple.
            across = abs(x - (size - 1) / 2.0) / ((size - 1) / 2.0)
            core = 1.0 - across * across
            ripple = 0.88 + 0.12 * math.sin(y * 0.9 + x * 0.3)
            value = int(max(0, min(255, 255 * core * ripple)))
            alpha = int(max(0, min(255, 255 * (0.35 + 0.65 * core))))
            img.putpixel((x, y), (255, value, value, alpha))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
