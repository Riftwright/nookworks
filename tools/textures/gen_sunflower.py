"""Sunflower Seeds and the sunflower crop they grow (the user, 2026-10-12). Original pixel art, drawn flat and finished
with texkit.shade; vanilla's sunflower is what the crop opens into, so nothing of it is drawn here.

- item/sunflower_seeds: four seeds lying this way and that, as vanilla's seeds are drawn - each a teardrop, black with
  a pale stripe down one side and a grey sheen down the other, as striped sunflower seeds are; the stripes kept exact
- block/sunflower_crop_stage0..2: one plant seen side on, on the cross model as corn is (one flower to a block):
  a sprout with its two seed leaves; a young plant, broad leaves in pairs up a short stalk; a tall stalk under a closed
  green bud, the yellow of the petals just showing at its tip
- models/block/sunflower_crop_stage0..2, blockstates/sunflower_crop, models/item and items/sunflower_seeds

Run from the repo root:  python tools/textures/gen_sunflower.py
"""
import json
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
CLEAR = (0, 0, 0, 0)

SEED = {"B": (44, 40, 42), "G": (112, 108, 106), "W": (214, 210, 198)}
STEM = [(54, 98, 36), (80, 130, 46)]
LEAF = [(46, 98, 34), (72, 136, 48), (104, 170, 64)]  # under, face, lit edge
BUD = [(56, 108, 40), (88, 148, 56), (120, 176, 70)]
PETAL = (236, 196, 52)


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c + (255,))


def seeds():
    """Four seeds, a quarter turn apart: the tip up, a pale stripe down the left, a grey sheen down the right."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    seed = [".B.",
            "WBG",
            "WBG",
            "WBB",
            "BBB",
            ".B."]
    for x0, y0, turns in ((2, 1, 0), (8, 3, 1), (2, 10, 3), (11, 8, 2)):
        shape = seed
        for _ in range(turns):  # a quarter turn clockwise
            shape = ["".join(row[i] for row in reversed(shape)) for i in range(len(shape[0]))]
        for dy, row in enumerate(shape):
            for dx, ch in enumerate(row):
                if ch in SEED:
                    px(img, x0 + dx, y0 + dy, SEED[ch])
    return img


def stem(img, top):
    """The stalk, two pixels wide up the middle from the ground to {@code top}: its lit side on the left."""
    for y in range(top, 16):
        px(img, 7, y, STEM[1])
        px(img, 8, y, STEM[0])


def leaf(img, y, reach, side, droop=0):
    """A broad heart-shaped leaf on a short stalk from the main one at row {@code y}, {@code reach} long out to
    {@code side} (-1 left, 1 right): the stalk a pixel, then the blade - widest a third of the way along, rounded at its
    base and drawn to a point - lit along its upper edge, a darker vein down its middle, its tip drooping {@code droop}."""
    base = 7 if side < 0 else 8
    px(img, base + side, y, STEM[1])                    # the leaf's own short stalk
    blade = reach - 1
    for i in range(1, blade + 1):
        x = base + side * (i + 1)
        t = i / blade
        half = 2.2 * (1 - t) ** 0.8 * min(1.0, 3.2 * t) if t < 1 else 0.6   # 0 at the stalk, widest near a third, a point
        dy = droop * t * t
        top, bottom = int(round(y + dy - half * 0.8)), int(round(y + dy + half))
        for yy in range(top, bottom + 1):
            # lit along the top where the blade is wide enough to show it, shaded along its underside
            c = LEAF[2] if yy == top and bottom > top else LEAF[0] if yy == bottom and bottom - top >= 2 else LEAF[1]
            px(img, x, yy, c)


def bud(img, cy):
    """The closed head on the stalk's top: a green ball of bracts, the petals' yellow just showing at its crown."""
    for y in range(cy - 2, cy + 2):
        for x in range(5, 11):
            dx, dy = (x + 0.5 - 8.0) / 2.6, (y + 0.5 - cy) / 1.9
            if dx * dx + dy * dy <= 1.0:
                px(img, x, y, BUD[2] if dy < -0.3 else BUD[1] if dy < 0.5 else BUD[0])
    px(img, 7, cy - 3, PETAL)
    px(img, 8, cy - 3, PETAL)
    px(img, 5, cy - 2, BUD[2])  # a bract's tip, either side
    px(img, 10, cy - 2, BUD[2])


def crop(stage):
    img = Image.new("RGBA", (16, 16), CLEAR)
    if stage == 0:  # a sprout: two seed leaves on a short stalk
        stem(img, 12)
        leaf(img, 12, 3, -1, droop=-1)
        leaf(img, 12, 3, 1, droop=-1)
    elif stage == 1:  # a young plant: broad leaves in pairs, the topmost small
        stem(img, 7)
        leaf(img, 12, 6, -1, droop=2)
        leaf(img, 12, 6, 1, droop=2)
        leaf(img, 8, 5, -1, droop=-1)
        leaf(img, 8, 5, 1, droop=-1)
    else:  # a tall stalk under its bud, the lower leaves the biggest
        stem(img, 4)
        leaf(img, 12, 7, -1, droop=2)
        leaf(img, 11, 7, 1, droop=2)
        leaf(img, 8, 6, -1, droop=0)
        leaf(img, 7, 6, 1, droop=0)
        bud(img, 3)
    return img


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    tex = ASSETS / "textures"
    texkit.icon(seeds(), 410, keep=[SEED["W"]]).save(tex / "item/sunflower_seeds.png")
    write_json(ASSETS / "models/item/sunflower_seeds.json", {"parent": "minecraft:item/generated",
                                                             "textures": {"layer0": "nookworks:item/sunflower_seeds"}})
    write_json(ASSETS / "items/sunflower_seeds.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/sunflower_seeds"}})
    for stage in range(3):
        texkit.shade(crop(stage), 420 + stage, keep=[CLEAR, PETAL], reach=1, **texkit.PLANT).save(tex / f"block/sunflower_crop_stage{stage}.png")
        write_json(ASSETS / f"models/block/sunflower_crop_stage{stage}.json", {
            "parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
            "textures": {"cross": f"nookworks:block/sunflower_crop_stage{stage}"}})
    write_json(ASSETS / "blockstates/sunflower_crop.json", {"variants": {
        f"age={a}": {"model": f"nookworks:block/sunflower_crop_stage{a}"} for a in range(3)}})
    print("wrote the sunflower seeds and crop")


if __name__ == "__main__":
    main()
