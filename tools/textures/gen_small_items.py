"""Two small items. Original pixel art. Deterministic.

- item/brass_mesh.png  the Sifter's fine mesh: a round brass hoop with a fine woven screen across it
- item/lode_pearl.png  an ender pearl bound to a lodestone: the pearl's sea-green, dark at its heart, held in a band
                       of the lodestone's iron with a rivet; drawn flat, finished with texkit.shade

Run from the repo root:  python tools/textures/gen_small_items.py
"""
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ITEM = ROOT / "src/main/resources/assets/nookworks/textures/item"
CLEAR = (0, 0, 0, 0)
OUTLINE = (40, 26, 10, 255)

BRASS = [(62, 42, 12), (122, 84, 24), (168, 122, 42), (206, 160, 72), (236, 200, 108), (255, 240, 176)]
WIRE = (150, 108, 40, 255)
WIRE_LIT = (214, 172, 88, 255)


def outline(img):
    out = img.copy()
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] == 0 and any(
                    0 <= x + dx < 16 and 0 <= y + dy < 16 and img.getpixel((x + dx, y + dy))[3] > 0
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), OUTLINE)
    return out


def brass_mesh():
    img = Image.new("RGBA", (16, 16), CLEAR)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 5.2 <= d < 7.0:  # the hoop, lit from the upper left
                light = -(x - cx) - (y - cy)
                level = 3 + (1 if light > 3 else 0) + (1 if light > 7 else 0) - (1 if light < -3 else 0) - (1 if light < -7 else 0)
                img.putpixel((x, y), BRASS[max(1, min(5, level))] + (255,))
    img = texkit.outline(img)  # round the hoop only, in its own brass: the holes in the screen stay open
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d < 5.2 and (x % 3 == 1 or y % 3 == 1):  # the screen: thin wires with open squares between them
                img.putpixel((x, y), WIRE_LIT if (x + y) % 2 else WIRE)
    return img


PEARL = [(6, 28, 32), (10, 52, 54), (18, 84, 82), (38, 122, 110), (88, 174, 150), (168, 232, 208)]
IRON = [(34, 34, 40), (62, 62, 70), (98, 98, 106), (146, 146, 152), (198, 198, 202)]
PEARL_OUTLINE = (6, 22, 22, 255)


def lode_pearl():
    """A round pearl lit from the upper left, darkening to its heart, with an iron band round its middle."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d >= 5.6:
                continue
            light = (-(x - cx) - (y - cy)) / 5.6  # -1.4 .. 1.4, up and to the left
            if 7 <= y <= 8:  # the band: its top edge catches the light, its lower edge is in shade
                level = 2 + (1 if light > 0.35 else 0) + (1 if light > 0.9 else 0) - (1 if light < -0.5 else 0) - (y - 7)
                img.putpixel((x, y), IRON[max(0, min(4, level))] + (255,))
                continue
            core = max(0.0, 1.0 - d / 4.4)  # the dark heart an ender pearl has, showing above and below the band
            level = 3.2 + light * 1.5 - core * 3.0
            img.putpixel((x, y), PEARL[max(0, min(5, round(level)))] + (255,))
    img.putpixel((5, 4), PEARL[5] + (255,))  # the shine
    img.putpixel((4, 5), PEARL[4] + (255,))
    img.putpixel((10, 7), IRON[4] + (255,))  # a rivet on the band
    img.putpixel((10, 8), IRON[1] + (255,))
    img = texkit.shade(img, 57, keep=[PEARL[5], IRON[4], IRON[1]],
                       material={**{c: "metal" for c in IRON}, None: "plain"}, **texkit.ITEM)
    return texkit.outline(img)  # ringed in the pearl's own deep teal


def main():
    brass_mesh().save(ITEM / "brass_mesh.png")
    lode_pearl().save(ITEM / "lode_pearl.png")
    print("wrote brass_mesh and lode_pearl")


if __name__ == "__main__":
    main()
