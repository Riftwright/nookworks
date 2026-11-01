"""The Warp Anchor (2026-10-31, the user: 「浮いているエンパ色の四角いブロックに土星みたいな環がついている見た目」, smart enough
for decor). Original pixel art, drawn flat and finished with texkit. Deterministic.

- block/warp_anchor_core.png       the floating cube's face: ender pearl colours, deep teal at the rim through sea-green
                                   to a pale mint eye in the middle, a darker line round the face so the cube's edges read
- block/warp_anchor_core_off.png   the same gone dull and grey-green: switched off
- block/warp_anchor_ring.png       the ring: a band of polished amethyst with a thin gold edge on each side (a strip,
                                   tiled along the ring's four sides)
- models/block/warp_anchor_core.json, warp_anchor_core_off.json, warp_anchor_ring.json   what the renderer draws
- models/block/warp_anchor.json    the block's own model: nothing but its particle (the renderer draws it floating)
- models/item/warp_anchor.json     the cube and its ring together, still, for the hand and the inventory

Run from the repo root:  python tools/textures/gen_warp_anchor.py
"""
import json
import math
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
TEX = ASSETS / "textures/block"
MODELS = ASSETS / "models"

# ender pearl colours, rim to eye
PEARL = [(10, 52, 50), (18, 82, 74), (34, 118, 100), (62, 158, 128), (118, 202, 166), (186, 236, 206)]
DULL = [(36, 44, 44), (52, 62, 60), (70, 82, 78), (88, 100, 94), (112, 124, 116), (134, 146, 138)]
EDGE = (6, 30, 30)
EDGE_OFF = (26, 30, 30)
AMETHYST = [(52, 30, 92), (86, 52, 140), (124, 84, 190), (168, 128, 230)]
GOLD = [(122, 84, 24), (196, 150, 50), (246, 214, 104)]


def core(colours, edge):
    """The cube's face, 6 px across as the cube is (drawn in the middle of the sheet, which the model's uv takes):
    a darker line round it, a soft eye a little up and left, as a pearl's highlight sits."""
    img = Image.new("RGBA", (16, 16))
    for y in range(5, 11):
        for x in range(5, 11):
            if x in (5, 10) or y in (5, 10):
                img.putpixel((x, y), edge + (255,))
                continue
            d = math.hypot(x - 7.1, y - 7.0) / 3.2
            level = max(0, min(len(colours) - 1, int((1 - d) * len(colours))))
            img.putpixel((x, y), colours[level] + (255,))
    return texkit.finish(img, 731 if colours is PEARL else 732, keep=[edge], **texkit.SMOOTH)


def ring():
    """The ring: its broad faces polished amethyst catching the light here and there (the top two rows and the next
    two, for the 1.5 px band seen from above and below), its thin edge a line of gold (the rows under)."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            if y < 4:
                c = AMETHYST[3] if (x * 5 + y * 3) % 11 == 0 else AMETHYST[2] if y % 2 == 0 else AMETHYST[1]
            else:
                c = GOLD[2] if (x + y) % 6 == 0 else GOLD[1]
            img.putpixel((x, y), c + (255,))
    return texkit.finish(img, 733, keep=list(GOLD), **texkit.SMOOTH)


def cube_model(texture):
    """The core: a 6 px cube centred on the block's middle (the renderer moves and turns it)."""
    faces = {f: {"uv": [5, 5, 11, 11], "texture": "#core"} for f in ("north", "south", "east", "west", "up", "down")}
    return {"parent": "minecraft:block/block", "textures": {"core": texture, "particle": texture},
            "elements": [{"from": [5, 5, 5], "to": [11, 11, 11], "faces": faces}]}


def ring_elements(y=8.0, radius=7.0, width=1.5, thick=0.5):
    """A ring round the block's middle, flat at height y: an octagon of eight bars, the four on the diagonals turned
    45 degrees about the upright (a model can turn an element by 22.5 degree steps), so it reads round, not square."""
    side = 2 * radius * math.tan(math.pi / 8)  # an octagon's side for this inner-to-outer middle radius
    y0, y1 = y - thick / 2, y + thick / 2
    out = []
    for k in range(8):
        # each bar is laid along x at the ring's north, then the eight are set round by turning about the middle;
        # a model turns an element by 22.5 degree steps only, so four sit on the axes and four are turned 45
        length = round(side + 0.3, 3)
        # each face shows as many texels as it is pixels: the broad faces the amethyst rows, the thin edges the gold
        edge = [0, 8, length, 8 + thick]
        end = [0, 8, width, 8 + thick]
        broad = [0, 0, length, width]
        angle = k * 45
        along_x = angle % 180 != 90
        faces = {"north": {"uv": edge if along_x else end, "texture": "#ring"}, "south": {"uv": edge if along_x else end, "texture": "#ring"},
                 "east": {"uv": end if along_x else edge, "texture": "#ring"}, "west": {"uv": end if along_x else edge, "texture": "#ring"},
                 "up": {"uv": broad if along_x else [0, 0, width, length], "texture": "#ring"},
                 "down": {"uv": broad if along_x else [0, 2, width, 2 + length], "texture": "#ring"}}
        a = math.radians(angle)
        cx, cz = 8 + radius * math.sin(a), 8 - radius * math.cos(a)
        e = {"from": [cx - side / 2 - 0.15, y0, cz - width / 2], "to": [cx + side / 2 + 0.15, y1, cz + width / 2], "faces": faces}
        if angle % 90 == 45:
            e["rotation"] = {"angle": -45 if angle in (45, 225) else 45, "axis": "y", "origin": [cx, y, cz]}
        elif angle % 180 == 90:
            e = {"from": [cx - width / 2, y0, cz - side / 2 - 0.15], "to": [cx + width / 2, y1, cz + side / 2 + 0.15], "faces": faces}
        e["from"] = [round(v, 3) for v in e["from"]]
        e["to"] = [round(v, 3) for v in e["to"]]
        out.append(e)
    return out


def main():
    TEX.mkdir(parents=True, exist_ok=True)
    core(PEARL, EDGE).save(TEX / "warp_anchor_core.png")
    core(DULL, EDGE_OFF).save(TEX / "warp_anchor_core_off.png")
    ring().save(TEX / "warp_anchor_ring.png")
    block = MODELS / "block"
    write(block / "warp_anchor_core.json", cube_model("nookworks:block/warp_anchor_core"))
    write(block / "warp_anchor_core_off.json", cube_model("nookworks:block/warp_anchor_core_off"))
    write(block / "warp_anchor_ring.json", {"parent": "minecraft:block/block",
                                            "textures": {"ring": "nookworks:block/warp_anchor_ring",
                                                         "particle": "nookworks:block/warp_anchor_ring"},
                                            "elements": ring_elements()})
    write(block / "warp_anchor.json", {"textures": {"particle": "nookworks:block/warp_anchor_core"}})
    # the item: the cube, and the ring tilted as it is seen turning (one bar set at 22.5 degrees about x)
    tilted = [e for e in ring_elements() if "rotation" not in e]  # (an element turns about one axis only: the square four)
    for e in tilted:
        e["rotation"] = {"angle": -22.5, "axis": "x", "origin": [8, 8, 8]}
    item = cube_model("nookworks:block/warp_anchor_core")
    item["textures"]["ring"] = "nookworks:block/warp_anchor_ring"
    item["elements"] += tilted
    write(MODELS / "item/warp_anchor.json", item)


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


if __name__ == "__main__":
    main()
