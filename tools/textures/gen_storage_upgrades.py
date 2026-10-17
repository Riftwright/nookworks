"""The Storage Upgrades: one item each, a barrel's new hoop in the tier's metal - a band of it seen a little from above,
open in the middle so the slot shows through, riveted - with a green arrow up at its shoulder. Original pixel art,
placed pixel by pixel to itemkit's bar and ringed as vanilla rings its items. Deterministic.

- item/iron_storage_upgrade.png, gold_, diamond_, netherite_, velarium_ and their item models

Run from the repo root:  python tools/textures/gen_storage_upgrades.py
"""
import json
import math
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
CLEAR = (0, 0, 0, 0)
ARROW = [(40, 120, 40), (90, 196, 72), (170, 240, 140)]

METALS = {  # shade, dark, mid, light, highlight - each a little cooler going down
    "iron": [(62, 64, 76), (100, 104, 116), (150, 154, 164), (200, 202, 208), (244, 244, 246)],
    "gold": [(120, 68, 16), (170, 112, 22), (216, 164, 40), (248, 210, 74), (255, 246, 170)],
    "diamond": [(14, 76, 84), (30, 132, 130), (64, 190, 178), (128, 232, 220), (214, 255, 250)],
    "netherite": [(26, 20, 24), (48, 40, 44), (72, 64, 68), (102, 92, 96), (150, 140, 142)],
    "velarium": [(30, 16, 58), (62, 38, 118), (98, 66, 172), (138, 108, 214), (196, 176, 244)],
}
CENTRE, RADII, THICKNESS, HEIGHT = (7.0, 6.8), (6.6, 3.6), (1.1, 0.8), 4  # the band's top edge, and how tall it stands


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def hoop(metal):
    """
    The band as a short cylinder seen from above and in front: its top edge an elliptical ring, lit, brightest at the
    back left; the outside of its near wall below that, lit on the left and turning into shade to the right; through
    the middle the inside of the far wall, the other way round (its left part faces away from the light), dark at its
    foot - and below that the hole, left clear after the ring is drawn so the slot shows through. Three rivets on the
    near wall.
    """
    img = Image.new("RGBA", (16, 16), CLEAR)
    (cx, cy), (a, b), (ta, tb), h = CENTRE, RADII, THICKNESS, HEIGHT
    ai, bi = a - ta, b - tb
    hole = []
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            outer = (dx / a) ** 2 + (dy / b) ** 2
            if outer <= 1 and (dx / ai) ** 2 + (dy / bi) ** 2 > 1:  # the top edge
                c = metal[3] if dy < 0 else metal[2]
                if dy < 0 and dx < -1:
                    c = metal[4] if outer > 0.72 else metal[3]
            elif outer <= 1:  # through the middle: the far wall's inside, down to its foot, then the hole
                top = -bi * math.sqrt(max(0.0, 1 - (dx / ai) ** 2))
                if dy >= top + h:
                    hole.append((x, y))
                    continue
                c = metal[0] if dy >= top + h - 1 else metal[1] if dx < 0 else metal[2]
            else:  # the near wall's outside, below the top edge
                edge = b * math.sqrt(max(0.0, 1 - (dx / a) ** 2)) if abs(dx) < a else None
                if edge is None or dy <= 0 or not edge < dy <= edge + h:
                    continue
                across = dx / a  # -1 at the left, where the light falls
                shade = 3 if across < -0.55 else 2 if across < 0.1 else 1 if across < 0.7 else 0
                c = metal[max(0, shade - 1) if dy > edge + h - 1 else shade]  # its foot a step darker
            px(img, x, y, c)
    for rx in (2, 7, 12):  # the rivets
        dx = rx + 0.5 - cx
        px(img, rx, int(cy + b * math.sqrt(max(0.0, 1 - (dx / a) ** 2)) + 1.5), metal[4])
    img = texkit.outline(img)
    for x, y in hole:  # the ring would have filled it
        px(img, x, y, CLEAR)
    return img


def arrow(img):
    """A green arrow up, in the top right: this makes a barrel bigger."""
    for x, y, c in ((12, 1, 2), (11, 2, 1), (12, 2, 2), (13, 2, 1), (10, 3, 0), (11, 3, 1), (12, 3, 1), (13, 3, 1), (14, 3, 0),
                    (12, 4, 1), (12, 5, 1), (12, 6, 0)):
        px(img, x, y, ARROW[c])
    px(img, 11, 4, ARROW[0])
    px(img, 11, 5, ARROW[0])
    px(img, 11, 6, ARROW[0])


def upgrade(metal):
    img = hoop(metal)
    arrow(img)
    return img


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    for tier, metal in METALS.items():
        name = f"{tier}_storage_upgrade"
        upgrade(metal).save(ASSETS / f"textures/item/{name}.png")
        write_json(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"nookworks:item/{name}"}})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:item/{name}"}})
    print("wrote the storage upgrades")


if __name__ == "__main__":
    main()
