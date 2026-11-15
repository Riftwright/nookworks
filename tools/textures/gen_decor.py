"""Decorations of 2026-10-12: the unlit lanterns and the bottles set down on a block. Original pixel art, drawn here.

- Unlit Lantern / Unlit Soul Lantern / the eight Unlit Copper Lanterns: vanilla's own lantern models and textures,
  referenced by name (never copied; a waxed copper lantern uses the unwaxed one's, as vanilla's does), with a pane of
  cold glass laid just over each side's window - our own pixels, covering only where the flame shows - so the unlit one
  matches the lit one in every other line (the user: "ついてるランタンと消えてるランタンのテクスチャが全然違う、みたいに
  ならない？"). block/lantern_soot*.png sits over the block faces (uv 0,2-6,9), item/lantern_soot*.png over the item
  sprite as its second layer.
  Since 2026-11-14 (the user, on the look: "何に違和感を覚えていたのか分かった。たぶん、枠のところのオレンジだわ"): the
  unlit Lantern stands in the Soul Lantern's frame - vanilla's lantern has orange-brown posts and a cap of it, which
  read as lit by a flame that is out - and the glass is a dim, clouded grey, of four looks tried the one between the
  smoked and the pale ("E2で、色味の差は残してください"): each kind's grey leans to what burned in it, which is all that
  tells an unlit Lantern from an unlit Soul Lantern.
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
# The cold window, drawn by hand (too small for texkit): a dim, clouded glass - soot in its top corners where the smoke
# rose, a paler patch up its left where the day catches it (vanilla's light comes from the top left; kept out of the
# middle, where with the burner under it it made a face of the window), the burner a shadow at its foot.
COLD = ["s00s",
        "1321",
        "2211",
        "1110",
        "0qq0"]
# The glass of each kind, by the ending of its files: the lantern's a warm grey, the soul lantern's a blue grey, the
# copper lanterns' a green grey, as their flames were. 0-3 the glass from dim to its palest, s the soot, q the burner.
COLD_COLOURS = {
    "": {"0": (70, 70, 78), "1": (88, 90, 100), "2": (106, 108, 120), "3": (134, 136, 148), "s": (52, 48, 52), "q": (48, 44, 46)},
    "_soul": {"0": (60, 74, 84), "1": (76, 94, 106), "2": (96, 116, 128), "3": (124, 146, 156), "s": (44, 52, 60), "q": (40, 48, 54)},
    "_copper": {"0": (64, 78, 70), "1": (82, 98, 88), "2": (102, 120, 108), "3": (130, 148, 136), "s": (46, 56, 50), "q": (42, 50, 45)},
}
# The vanilla lantern each kind's pane names for its particle (the blockstate's first model, vanilla's own, is the one
# that gives the block's)
COLD_PARTICLE = {"": "soul_lantern", "_soul": "soul_lantern", "_copper": "copper_lantern"}

GLASS = (222, 238, 244, 34)  # faint: the liquid behind it is what shows
GLASS_EDGE = (184, 210, 222, 112)  # the sides' outline
GLASS_SHINE = (250, 254, 255, 190)
LIQUID = [(196, 196, 196, 236), (224, 224, 224, 236), (246, 246, 246, 236)]  # greys, for the tint
CORK = [(112, 78, 46), (146, 104, 64), (178, 134, 86)]


def soot(corner, kind):
    """The cold window of a {@code kind} of lantern over its flame, its top-left pixel at {@code corner}; clear
    everywhere else."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for row, line in enumerate(COLD):
        for col, key in enumerate(line):
            img.putpixel((corner[0] + col, corner[1] + row), COLD_COLOURS[kind][key] + (255,))
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
    """The four panes of cold glass a hair outside the lantern's sides (its body from {@code lo} to {@code hi})."""
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
    for kind in COLD_COLOURS:
        soot(WINDOW_FACE, kind).save(tex / f"block/lantern_soot{kind}.png")
        soot(WINDOW_ITEM, kind).save(tex / f"item/lantern_soot{kind}.png")
    glass().save(tex / "block/bottle_glass.png")
    liquid().save(tex / "block/bottle_liquid.png")
    cork().save(tex / "block/bottle_cork.png")

    models = ASSETS / "models"
    for kind, particle in COLD_PARTICLE.items():
        for name, y0 in ((f"lantern_soot{kind}", 0), (f"lantern_soot{kind}_hanging", 1)):
            write(models / f"block/{name}.json", {
                "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                "textures": {"soot": f"nookworks:block/lantern_soot{kind}", "particle": f"minecraft:block/{particle}"},
                "elements": pane(5, 11, y0)})
    ages = ["", "exposed_", "weathered_", "oxidized_"]
    # (lit id, the vanilla lantern whose look it takes, its glass): the unlit Lantern takes the Soul Lantern's look -
    # the Lantern's own frame is orange-brown, which unlit read as lit (the user, 2026-11-14)
    lanterns = [("lantern", "soul_lantern", ""), ("soul_lantern", "soul_lantern", "_soul")]
    lanterns += [(f"{age}copper_lantern", f"{age}copper_lantern", "_copper") for age in ages]
    lanterns += [(f"waxed_{age}copper_lantern", f"{age}copper_lantern", "_copper") for age in ages]
    for lit, look, kind in lanterns:
        block = f"unlit_{lit}"
        write(ASSETS / f"blockstates/{block}.json", {"multipart": [
            {"when": {"hanging": "false"}, "apply": {"model": f"minecraft:block/{look}"}},
            {"when": {"hanging": "true"}, "apply": {"model": f"minecraft:block/{look}_hanging"}},
            {"when": {"hanging": "false"}, "apply": {"model": f"nookworks:block/lantern_soot{kind}"}},
            {"when": {"hanging": "true"}, "apply": {"model": f"nookworks:block/lantern_soot{kind}_hanging"}}]})
        write(models / f"item/{block}.json", {"parent": "minecraft:item/generated", "textures": {
            "layer0": f"minecraft:item/{look}", "layer1": f"nookworks:item/lantern_soot{kind}"}})
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
