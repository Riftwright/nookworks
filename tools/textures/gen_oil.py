"""Vegetable oil: textures (all original pixel art) and item models. Deterministic.

Blocks: vegetable_oil_still / _flow (animated): glossy gold with slow darker swirls and soft light patches
Items:  oil_can: a squat steel oiler with a brass thumb pump and a long spout;
        vegetable_oil_bucket_fill (over the vanilla bucket), vegetable_oil_bottle_fill (under the vanilla glass
        bottle, so the glass and its highlights stay on top); the models stack them over vanilla sprites by name

Run from the repo root:  python tools/textures/gen_oil.py
"""
import json
import math
from pathlib import Path

from PIL import Image

import itemkit
import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
BLOCK = ASSETS / "textures/block"
ITEM = ASSETS / "textures/item"

CLEAR = (0, 0, 0, 0)
GOLD = (222, 170, 44)
LIGHT = (250, 222, 120)
DEEP = (176, 118, 22)


def rgba(c, a=255):
    return tuple(int(max(0, min(255, v))) for v in c) + (a,)


def mix(a, b, t):
    return tuple(x + (y - x) * t for x, y in zip(a, b))


def save(img, path, frametime=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    if frametime:
        path.with_suffix(".png.mcmeta").write_text(json.dumps({"animation": {"frametime": frametime}}) + "\n")


def liquid_frames(size, frames, flow, gold=GOLD, light=LIGHT, deep=DEEP):
    """Gold, darker in slow swirls, with soft light patches where the surface catches the light; flowing oil runs
    downward. Smooth on purpose: oil is glossy, and single bright pixels read as bubbles, not gloss. Not put through
    texkit either: its noise differs from frame to frame of the strip, and the surface would flicker."""
    img = Image.new("RGBA", (size, size * frames))
    for f in range(frames):
        t = f / frames * 2 * math.pi
        for y in range(size):
            for x in range(size):
                yy = y - (f * size / frames if flow else 0)
                v = (math.sin(x * 2 * math.pi / size + t) + math.sin(yy * 2 * math.pi / size * 2 - t)
                     + math.sin((x - yy) * 2 * math.pi / size + t)) / 3
                if v < 0:
                    colour = mix(gold, deep, min(1.0, -v * 1.2))
                else:
                    colour = mix(gold, light, min(1.0, max(0.0, v - 0.35) * 1.6))
                img.putpixel((x, y + f * size), rgba(colour))
    return img


def bucket_fill(gold=GOLD, light=LIGHT, deep=DEEP):
    """Just the liquid in the vanilla bucket's mouth (itemkit); the model stacks it over the bucket sprite."""
    return itemkit.bucket_fill([deep, gold, light, mix(light, (255, 255, 255), 0.5)])


# The body of a glass bottle, lowest rows up to the shoulder: (row, first column, last column). The glass sprite
# is drawn over this, so only what shows between its lines matters.
BODY = ((7, 7, 9), (8, 6, 10), (9, 5, 11), (10, 5, 11), (11, 5, 11), (12, 5, 11), (13, 6, 10))


def bottle_fill(light=LIGHT, mid=GOLD, deep=DEEP, seed=12, grain=texkit.SMOOTH["grain"]):
    """The liquid seen through the glass: its lit top (the meniscus, kept exact) and under it the colour deepening
    towards the bottom. {@code grain} 0 leaves no single specks: in a clear liquid one reads as a speck of dirt."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for row, first, last in BODY:
        for x in range(first, last + 1):
            depth = (row - 7) / 6
            img.putpixel((x, row), rgba(light if row == 8 else mix(mid, deep, depth * 0.8)))
    return texkit.finish(img, seed, keep=[rgba(light)], **{**texkit.SMOOTH, "grain": grain})


# Biodiesel in a bottle or a bucket, lit top, body and depth: a saturated amber, between the oil's gold and the
# vinegar's brown
BIODIESEL = ((250, 200, 96), (212, 130, 30), (140, 72, 16))

INK = (36, 34, 40)
# The oiler (itemkit): a steel drum, lit down its left as a cylinder is, and brass for its cone, spout and pump.
OILER_COLOURS = {"1": (60, 64, 78), "2": (96, 102, 116), "3": (138, 146, 158), "4": (184, 192, 202), "5": (230, 236, 242),
                 "a": (120, 82, 26), "b": (178, 130, 44), "c": (224, 180, 80), "d": (252, 228, 140)}
OILER = (  # (row, column, pixels)
    (0, 14, "d"), (1, 13, "c"), (2, 12, "b"), (3, 10, "cb"), (4, 9, "b"),  # the spout, up to its tip
    (5, 6, "dcb"),
    (6, 5, "dccba"), (6, 11, "cb"),  # the cone; the thumb pump on the shoulder
    (7, 4, "dcccbba"), (7, 11, "a"),
    (8, 2, "3455544433"),  # the lid's rim
    (9, 1, "345544333221"),
    (10, 1, "345443332221"),
    (11, 1, "234332222111"),  # the seam round the drum
    (12, 1, "345443332221"),
    (13, 1, "345443332221"),
    (14, 2, "2333222111"),  # the foot
)


def oil_can():
    """
    A squat steel oiler, read in one look: a round drum seen a little from above - its lid's rim lit, a seam round its
    middle, its foot in shade, a gleam down its left - the brass cone on the lid, the long thin spout rising out of the
    cone's tip to the top right, and the thumb pump on the drum's shoulder.
    """
    rows = [["."] * 16 for _ in range(16)]
    for y, x, run in OILER:
        rows[y][x:x + len(run)] = run
    return itemkit.sprite(itemkit.check(["".join(r) for r in rows]), OILER_COLOURS)


SOAP = [(106, 128, 64), (146, 168, 94), (182, 202, 126), (208, 224, 158), (228, 240, 188), (246, 250, 220)]
SUDS = [(196, 214, 226), (236, 244, 250), (255, 255, 255)]


def soap():
    """
    A bar of soap as it is made here, from vegetable oil boiled with caustic soda: the soft olive green of an oil soap,
    a thick bar with rounded edges seen from above and in front - the top the lightest, lit along its far edge, the
    front a band of shade under its rounded near edge, darker towards the right - with a leaf pressed into the top
    (its hollow in shade, its lower edge catching the light), and suds at its far corner.
    """
    img = Image.new("RGBA", (16, 16), CLEAR)
    top = {}
    for y in range(3, 10):  # the top: a rounded oblong, its far edge a pixel in at each corner
        a, b = (3, 12) if y in (3, 9) else (2, 13)
        for x in range(a, b + 1):
            top[(x, y)] = True
    for (x, y) in top:
        c = SOAP[4]
        if y == 3 or (x == 2 and y < 8) or (x == 3 and y == 4):
            c = SOAP[5]  # the far edge and the left end, lit
        elif y == 9 or x == 13:
            c = SOAP[3]  # rolling over into the front and the right end
        img.putpixel((x, y), rgba(c))
    for y in range(10, 13):  # the front, under the rounded near edge; the right end in deeper shade
        a, b = (3, 12) if y < 12 else (4, 11)
        for x in range(a, b + 1):
            c = SOAP[2] if y == 10 else SOAP[1]
            if x >= b - 1:
                c = SOAP[1] if y == 10 else SOAP[0]
            img.putpixel((x, y), rgba(c))
    # the leaf pressed into the top: its hollow a step darker, its lower right lip catching the light
    leaf = [(6, 7), (7, 6), (8, 5), (9, 5), (7, 7), (8, 6), (9, 6), (8, 7), (10, 5)]
    for (x, y) in leaf:
        img.putpixel((x, y), rgba(SOAP[2]))
    for (x, y) in ((6, 8), (7, 8), (8, 8), (9, 7), (10, 6), (11, 5)):
        img.putpixel((x, y), rgba(SOAP[5]))
    for (x, y) in ((5, 7), (6, 6), (7, 5), (8, 4)):
        img.putpixel((x, y), rgba(SOAP[3]))  # the far lip of the hollow, in its shade
    # suds at the far right corner: two bubbles and a smaller one, each a ring with a glint
    for (cx, cy, r) in ((12.5, 2.5, 1.6), (14.2, 4.8, 1.1)):
        for y in range(16):
            for x in range(16):
                d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
                if d <= r:
                    img.putpixel((x, y), rgba(SUDS[1] if d > r - 0.9 else SUDS[0]))
    img.putpixel((12, 2), rgba(SUDS[2]))
    img.putpixel((1, 11), rgba(SUDS[1]))  # a fleck of lather by the foot
    return texkit.icon(img, 14, keep=[rgba(SOAP[5]), rgba(SUDS[2])], **texkit.SMOOTH)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf8"))


def main():
    # (the liquid itself is gen_liquids.py's)
    save(bucket_fill(), ITEM / "vegetable_oil_bucket_fill.png")
    save(bottle_fill(), ITEM / "vegetable_oil_bottle_fill.png")
    save(oil_can(), ITEM / "oil_can.png")
    save(soap(), ITEM / "soap.png")
    # biodiesel: a clear, saturated amber, a fuel's colour - between the pale gold oil it is made from and the smoky
    # brown of wood vinegar, and told from both at a glance (it was nearly wood vinegar's brown)
    bio = dict(gold=BIODIESEL[1], light=BIODIESEL[0], deep=BIODIESEL[2])
    save(bucket_fill(**bio), ITEM / "biodiesel_bucket_fill.png")
    write_json(ASSETS / "models/item/biodiesel_bucket.json", {"parent": "minecraft:item/generated", "textures": {
        "layer0": "minecraft:item/bucket", "layer1": "nookworks:item/biodiesel_bucket_fill"}})
    write_json(ASSETS / "items/biodiesel_bucket.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/biodiesel_bucket"}})
    write_json(ASSETS / "blockstates/biodiesel.json", {"variants": {"": {"model": "nookworks:block/biodiesel"}}})
    write_json(ASSETS / "models/block/biodiesel.json", {"textures": {"particle": "nookworks:block/biodiesel_still"}})
    # the other bottled liquids, drawn the same way under the vanilla glass: smoky brown wood vinegar, near-clear
    # methanol, and amber biodiesel, clear and without a speck
    for name, colours, seed, grain in (("wood_vinegar", ((176, 110, 60), (120, 64, 30), (72, 36, 16)), 15, None),
                                       ("methanol", ((236, 244, 250), (206, 220, 232), (170, 188, 204)), 16, None),
                                       ("biodiesel_bottle", BIODIESEL, 17, 0),
                                       # liquid experience, the pale glowing green of its pipes and tanks
                                       ("liquid_experience_bottle", ((214, 252, 140), (150, 226, 70), (78, 150, 32)), 18,
                                        None)):
        tuning = {} if grain is None else {"grain": grain}
        save(bottle_fill(*colours, seed=seed, **tuning), ITEM / f"{name}_fill.png")
        write_json(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {
            "layer0": f"nookworks:item/{name}_fill", "layer1": "minecraft:item/glass_bottle"}})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:item/{name}"}})
    write_json(ASSETS / "models/item/soap.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "nookworks:item/soap"}})
    write_json(ASSETS / "items/soap.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/soap"}})
    write_json(ASSETS / "models/item/oil_can.json", {"parent": "minecraft:item/handheld",
                                                      "textures": {"layer0": "nookworks:item/oil_can"}})
    write_json(ASSETS / "items/oil_can.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/oil_can"}})
    write_json(ASSETS / "models/item/vegetable_oil_bucket.json", {"parent": "minecraft:item/generated", "textures": {
        "layer0": "minecraft:item/bucket", "layer1": "nookworks:item/vegetable_oil_bucket_fill"}})
    write_json(ASSETS / "models/item/vegetable_oil_bottle.json", {"parent": "minecraft:item/generated", "textures": {
        "layer0": "nookworks:item/vegetable_oil_bottle_fill", "layer1": "minecraft:item/glass_bottle"}})
    for name in ("vegetable_oil_bucket", "vegetable_oil_bottle"):
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:item/{name}"}})
    write_json(ASSETS / "blockstates/vegetable_oil.json", {"variants": {"": {"model": "nookworks:block/vegetable_oil"}}})
    write_json(ASSETS / "models/block/vegetable_oil.json", {"textures": {"particle": "nookworks:block/vegetable_oil_still"}})


if __name__ == "__main__":
    main()
