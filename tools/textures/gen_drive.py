"""The Drive Wheel and its belts (dev.nookworks.drive): models and textures. Deterministic. All original.

- models/block/drive_wheel.json   the toothed wheel on its axle, pointing north (the axle along z): the item's model,
                                  and what client/render/DriveWheelRenderer turns to face and spins
- models/block/drive_wheel_block.json  the block's own model: nothing but its breaking bits (the renderer draws it all)
- blockstates/drive_wheel.json, items/drive_wheel.json
- textures/block/drive_belt_rope.png, drive_belt_chain.png  a belt's cord, a block a tile, along v; four pixels wide in
  the middle of the tile (the renderer shows columns 6-9 across the cord). The chain is a link flat and a link edge-on
  by turns, four pixels each; the rope three strands twisted. Shaded by hand in a few close tones: at a cord this thin
  texkit's grain would only speckle it.

Run from the repo root:  python tools/textures/gen_drive.py
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"

IRON_HI, IRON, IRON_LO, IRON_DK = (196, 200, 208), (150, 154, 164), (104, 108, 118), (62, 64, 72)
ROPE_HI, ROPE, ROPE_LO = (204, 170, 118), (166, 130, 84), (118, 88, 56)
NAMES = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}


def put(img, x, y, c):
    img.putpixel((x, y), c + (255,))


def chain():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for top in range(0, 16, 8):
        # a link flat: an oval ring, its hole showing through
        for x in (7, 8):
            put(img, x, top, IRON_HI if x == 7 else IRON)
            put(img, x, top + 3, IRON_LO if x == 7 else IRON_DK)
        for y in (top + 1, top + 2):
            put(img, 6, y, IRON_HI)
            put(img, 9, y, IRON_LO)
        # a link edge-on: a bar, lit along one side
        for y in range(top + 4, top + 8):
            put(img, 7, y, IRON if y in (top + 4, top + 7) else IRON_HI)
            put(img, 8, y, IRON_LO if y in (top + 4, top + 7) else IRON)
    return img


def rope():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(6, 10):
            k = (x + y) % 4  # three strands laid round each other: a light one, its shadow, and the groove
            put(img, x, y, ROPE_HI if k == 0 else ROPE if k in (1, 2) else ROPE_LO)
    return img


def box(frm, to, tex, faces="nsewud"):
    return {"from": frm, "to": to, "faces": {NAMES[f]: {"texture": tex} for f in faces}}


def wheel_model():
    """Pointing north: an axle the length of the block, and at its middle a wheel 9 across with eight teeth, a hub on
    it. The wheel is stepped as a pixel circle: columns a pixel and a half wide, none overlapping."""
    els = [box([7, 7, 0], [9, 9, 16], "#iron")]
    r = 4.5
    x = 8 - r
    while x < 8 + r - 0.01:
        xe = min(8 + r, x + 1.5)
        d = max(abs(x - 8), abs(xe - 8)) - 0.75
        half = round(math.sqrt(max(0.0, r * r - d * d)) * 2) / 2
        els.append(box([x, 8 - half, 7], [xe, 8 + half, 9], "#iron"))
        x = xe
    for k in range(8):
        a = k * math.pi / 4
        cx, cy = 8 + 5 * math.cos(a), 8 + 5 * math.sin(a)
        els.append(box([round(cx - 0.6, 2), round(cy - 0.6, 2), 7.25], [round(cx + 0.6, 2), round(cy + 0.6, 2), 8.75], "#tooth"))
    # the hub a little wider than the wheel's middle columns, so no face of it lies on one of theirs
    els.append(box([6.25, 6.25, 6.25], [9.75, 9.75, 9.75], "#hub"))
    # In the inventory, the wheel's face turned to the viewer - a little aside and from above, so the axle shows - and
    # drawn bigger: at block/block's 45 degrees and 0.625 it was a thin dark sliver, hard to tell for a wheel (r16
    # visual review NIT-1). The other transforms are gen_item_displays.py's (run after this).
    display = {"gui": {"rotation": [30, 200, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]}}
    return {"parent": "minecraft:block/block", "textures": {
        "particle": "nookworks:block/black_iron", "iron": "nookworks:block/black_iron", "tooth": "minecraft:block/iron_block",
        "hub": "minecraft:block/iron_block"}, "elements": els, "display": display}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))  # LF, on Windows too


def main():
    chain().save(ASSETS / "textures/block/drive_belt_chain.png")
    rope().save(ASSETS / "textures/block/drive_belt_rope.png")
    write_json(ASSETS / "models/block/drive_wheel.json", wheel_model())
    write_json(ASSETS / "models/block/drive_wheel_block.json", {"textures": {"particle": "nookworks:block/black_iron"}, "elements": []})
    variants = {}
    for facing in ("north", "south", "east", "west", "up", "down"):
        for turning in ("false", "true"):
            variants[f"facing={facing},turning={turning}"] = {"model": "nookworks:block/drive_wheel_block"}
    write_json(ASSETS / "blockstates/drive_wheel.json", {"variants": variants})
    write_json(ASSETS / "items/drive_wheel.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/drive_wheel"}})
    print("wrote the drive wheel")


if __name__ == "__main__":
    main()
