"""Decorations of 2026-10-12: the unlit lanterns and the bottles set down on a block. Original pixel art, drawn here.

- Unlit Lantern / Unlit Soul Lantern / the eight Unlit Copper Lanterns: vanilla's own lantern models and textures,
  referenced by name (never copied; a waxed copper lantern uses the unwaxed one's, as vanilla's does), with a pane of sooty glass laid just over each side's window - our own pixels, covering only where
  the flame shows - so the unlit one matches the lit one in every other line (the user: "ついてるランタンと消えてる
  ランタンのテクスチャが全然違う、みたいにならない？"). block/lantern_soot.png sits over the block faces (uv 0,2-6,9),
  item/lantern_soot.png over the item sprite as its second layer.
- Placed bottles: small - three pixels across, five tall with the cork (the user: "黄昏の森とかbeautiflyで使える瓶の
  サイズ感", not big) - in four places on the block; a clear glass shell, a liquid inside tinted per place (tintindex
  = place) from what the bottle holds, a cork. Multipart blockstate: a place's glass while that many stand there, its
  liquid while that one is filled.

Run from the repo root:  python tools/textures/gen_decor.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
CLEAR = (0, 0, 0, 0)

# Where the flame shows, the window inside the lantern's copper frame: its top-left pixel on the side face (the face
# takes uv 0,2-6,9) and on the item sprite; four across, five down.
WINDOW_FACE = (1, 3)
WINDOW_ITEM = (6, 8)
# The cold window, drawn by hand (too small for texkit): soot thick along the top where the smoke rose, dark glass
# below with a sheen down the left (vanilla's light comes from the top left), the stub of a wick at the foot.
COLD = ["TTTT",
        "TMMT",
        "HDDM",
        "HDDM",
        "DWWD"]
COLD_COLOURS = {"T": (31, 28, 30), "M": (60, 63, 76), "D": (45, 47, 58), "H": (94, 101, 118), "W": (24, 20, 19)}

GLASS = (222, 238, 244, 34)  # faint: the liquid behind it is what shows
GLASS_EDGE = (184, 210, 222, 112)  # the sides' outline
GLASS_SHINE = (250, 254, 255, 190)
LIQUID = [(196, 196, 196, 236), (224, 224, 224, 236), (246, 246, 246, 236)]  # greys, for the tint
CORK = [(112, 78, 46), (146, 104, 64), (178, 134, 86)]


def soot(corner):
    """The cold window over the flame, its top-left pixel at {@code corner}; clear everywhere else."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for row, line in enumerate(COLD):
        for col, key in enumerate(line):
            img.putpixel((corner[0] + col, corner[1] + row), COLD_COLOURS[key] + (255,))
    return img


def glass():
    """The bottle's glass: clear and faint, denser down its two sides (a face is three texels across), a shine on one."""
    img = Image.new("RGBA", (16, 16), GLASS)
    for y in range(16):
        img.putpixel((0, y), GLASS_EDGE)
        img.putpixel((2, y), GLASS_EDGE)
    img.putpixel((0, 0), GLASS_SHINE)
    img.putpixel((0, 1), GLASS_SHINE)
    return img


def liquid():
    img = Image.new("RGBA", (16, 16), LIQUID[1])
    for x in range(16):
        img.putpixel((x, 0), LIQUID[2])  # its surface catches the light
        img.putpixel((x, 3), LIQUID[0])
    return img


def cork():
    img = Image.new("RGBA", (16, 16), CORK[1] + (255,))
    for x in range(16):
        img.putpixel((x, 0), CORK[2] + (255,))
    img.putpixel((1, 1), CORK[0] + (255,))
    return img


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def pane(lo, hi, y0):
    """The four panes of sooty glass a hair outside the lantern's sides (its body from {@code lo} to {@code hi})."""
    y1 = y0 + 7
    face = {"uv": [0, 2, 6, 9], "texture": "#soot"}
    e = 0.01
    return [
        {"from": [lo, y0, lo - e], "to": [hi, y1, lo - e], "faces": {"north": face}},
        {"from": [lo, y0, hi + e], "to": [hi, y1, hi + e], "faces": {"south": face}},
        {"from": [lo - e, y0, lo], "to": [lo - e, y1, hi], "faces": {"west": face}},
        {"from": [hi + e, y0, lo], "to": [hi + e, y1, hi], "faces": {"east": face}},
    ]


def bottle_elements(place, part):
    cx, cz = [(5.0, 5.0), (11.0, 4.5), (4.5, 11.0), (11.0, 11.5)][place]  # PlacedBottlesBlock.AT
    turn = {"origin": [cx, 0, cz], "axis": "y", "angle": 22.5} if place in (1, 2) else None

    def box(x0, y0, z0, x1, y1, z1, tex, uv, tint=None, top=None):
        faces = {}
        for side in ("north", "south", "east", "west", "up", "down"):
            f = {"uv": top if side in ("up", "down") and top else uv, "texture": tex}
            if tint is not None:
                f["tintindex"] = tint
            faces[side] = f
        el = {"from": [cx + x0, y0, cz + z0], "to": [cx + x1, y1, cz + z1], "faces": faces}
        if turn:
            el["rotation"] = turn
        return el

    if part == "glass":
        return [box(-1.5, 0, -1.5, 1.5, 3, 1.5, "#glass", [0, 0, 3, 3]),
                box(-0.5, 3, -0.5, 0.5, 4.5, 0.5, "#glass", [0, 0, 1, 1.5], top=[0, 0, 1, 1]),
                box(-0.6, 4.5, -0.6, 0.6, 5.25, 0.6, "#cork", [0, 0, 1.2, 0.75], top=[0, 1, 1.2, 2.2])]
    return [box(-1.25, 0.25, -1.25, 1.25, 2.5, 1.25, "#liquid", [0, 0, 2.5, 2.25], tint=place, top=[0, 1, 2.5, 3.5])]


def main():
    tex = ASSETS / "textures"
    soot(WINDOW_FACE).save(tex / "block/lantern_soot.png")
    soot(WINDOW_ITEM).save(tex / "item/lantern_soot.png")
    glass().save(tex / "block/bottle_glass.png")
    liquid().save(tex / "block/bottle_liquid.png")
    cork().save(tex / "block/bottle_cork.png")

    models = ASSETS / "models"
    for name, y0 in (("lantern_soot", 0), ("lantern_soot_hanging", 1)):
        write(models / f"block/{name}.json", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                              "textures": {"soot": "nookworks:block/lantern_soot", "particle": "minecraft:block/lantern"},
                                              "elements": pane(5, 11, y0)})
    ages = ["", "exposed_", "weathered_", "oxidized_"]
    lanterns = [("lantern", "lantern"), ("soul_lantern", "soul_lantern")]  # (lit id, the look it takes)
    lanterns += [(f"{age}copper_lantern", f"{age}copper_lantern") for age in ages]
    lanterns += [(f"waxed_{age}copper_lantern", f"{age}copper_lantern") for age in ages]
    for lit, look in lanterns:
        block = f"unlit_{lit}"
        write(ASSETS / f"blockstates/{block}.json", {"multipart": [
            {"when": {"hanging": "false"}, "apply": {"model": f"minecraft:block/{look}"}},
            {"when": {"hanging": "true"}, "apply": {"model": f"minecraft:block/{look}_hanging"}},
            {"when": {"hanging": "false"}, "apply": {"model": "nookworks:block/lantern_soot"}},
            {"when": {"hanging": "true"}, "apply": {"model": "nookworks:block/lantern_soot_hanging"}}]})
        write(models / f"item/{block}.json", {"parent": "minecraft:item/generated",
                                              "textures": {"layer0": f"minecraft:item/{look}", "layer1": "nookworks:item/lantern_soot"}})
        write(ASSETS / f"items/{block}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:item/{block}"}})

    parts = []
    for place in range(4):
        for part in ("glass", "liquid"):
            write(models / f"block/placed_bottle_{part}_{place}.json", {
                "render_type": "minecraft:translucent", "ambientocclusion": False,
                "textures": {"glass": "nookworks:block/bottle_glass", "liquid": "nookworks:block/bottle_liquid",
                             "cork": "nookworks:block/bottle_cork", "particle": "nookworks:block/bottle_glass"},
                "elements": bottle_elements(place, part)})
        standing = "|".join(str(n) for n in range(place + 1, 5))
        glass_model = {"model": f"nookworks:block/placed_bottle_glass_{place}"}
        liquid_model = {"model": f"nookworks:block/placed_bottle_liquid_{place}"}
        if place == 0:
            # the first bottle stands whenever the block does: a condition on it would always hold, which the game logs
            parts.append({"apply": glass_model})
            parts.append({"when": {f"filled_{place}": "true"}, "apply": liquid_model})
        else:
            parts.append({"when": {"bottles": standing}, "apply": glass_model})
            parts.append({"when": {"AND": [{"bottles": standing}, {f"filled_{place}": "true"}]}, "apply": liquid_model})
    write(ASSETS / "blockstates/placed_bottles.json", {"multipart": parts})
    print("wrote the unlit lanterns and the placed bottles")


if __name__ == "__main__":
    main()
