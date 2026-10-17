"""The Oil Lamp (油火皿): its textures, models, blockstate and item. Original pixel art. Deterministic.

An unglazed clay saucer - kawarake, fired but not glazed, a pale orange-buff with darker grit in it - with a wick of
rush pith lying across it from the middle out over the rim, where it burns. The dish has the thickness of a flower
pot's wall, and the oil stands in it as a dark amber surface at one of four depths, a bottle each. The flame is its
own: a small teardrop drawn on two crossed planes at the wick's end, lit, with a thread of smoke going up from it -
not the game's candle particle, which made it look like a torch's flame set on a saucer.

- block/oil_lamp_clay.png   the clay, for every face of the dish
- block/oil_lamp_wick.png   the pith, off-white and fibrous, charred at the lit end
- block/oil_lamp_oil.png    the oil standing in the dish: dark amber, a gleam across it
- block/oil_lamp_flame.png  the flame: a teardrop three pixels across, white at its heart
- item/oil_lamp.png         the dish from the side and a little above, wick lit
- models/block/oil_lamp_<oil>[_lit].json, blockstates/oil_lamp.json, items/oil_lamp.json

Run from the repo root:  python tools/textures/gen_oil_lamp.py
"""
import json
import random
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
CLEAR = (0, 0, 0, 0)

CLAY = [(150, 96, 62), (186, 124, 82), (212, 154, 108), (230, 182, 138)]  # shadow, body, lit, lip
GRIT = (118, 80, 56)
PITH = [(196, 188, 164), (226, 220, 200), (244, 240, 226)]
CHAR = (60, 44, 34)
EMBER = [(255, 170, 60), (255, 222, 120)]
OIL = [(206, 150, 34), (232, 184, 70), (250, 226, 150)]
LAMP_OIL = [(96, 58, 14), (124, 80, 22), (150, 102, 32), (214, 170, 86)]  # the oil in the dish: deep, mid, lit, gleam
FLAME = {"tip": (255, 128, 36), "outer": (255, 160, 48), "inner": (255, 214, 96), "heart": (255, 248, 214)}


def px(img, x, y, c):
    img.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def clay():
    """Kawarake clay: a buff body a shade lighter at the top of each face, flecked with darker grit. Tileable."""
    rng = random.Random(3)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            px(img, x, y, CLAY[2] if y % 16 < 2 else CLAY[1])
    for _ in range(22):
        px(img, rng.randrange(16), rng.randrange(16), GRIT)
    return texkit.finish(img, 3, keep=(GRIT,), tile=True, **texkit.ROUGH)


def wick():
    """Rush pith: soft off-white fibres along its length; the last pixels at the lit end charred."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            px(img, x, y, PITH[2] if x % 3 == 0 else PITH[1] if x % 3 == 1 else PITH[0])
    for y in range(13, 16):
        for x in range(16):
            px(img, x, y, CHAR)
    return img


def item():
    """The dish from the side and a little above: its lip, the oil in it, the wick over the rim and its flame."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    # the dish: an ellipse of oil inside the lip, the body under it narrowing to the foot
    for y in range(8, 15):
        for x in range(1, 15):
            dx, dy = (x + 0.5 - 8) / 7.0, (y + 0.5 - 9.5) / 2.2
            if y <= 10 and dx * dx + dy * dy <= 1.0:
                inner = ((x + 0.5 - 8) / 5.6) ** 2 + ((y + 0.5 - 9.6) / 1.4) ** 2 <= 1.0
                px(img, x, y, (LAMP_OIL[2] if x < 8 else LAMP_OIL[1]) if inner else CLAY[3] if y <= 9 else CLAY[2])
            elif 10 < y <= 13 and 2 + (y - 11) // 2 <= x <= 13 - (y - 11) // 2:
                px(img, x, y, CLAY[2] if x < 5 else CLAY[1] if x < 10 else CLAY[0])  # the wall, as thick as a pot's
            elif y == 14 and 5 <= x <= 10:
                px(img, x, y, CLAY[0])
    px(img, 4, 9, LAMP_OIL[3])
    # the wick, from the middle out over the right rim
    for x in range(8, 14):
        px(img, x, 9 if x < 13 else 8, PITH[1] if x % 2 else PITH[2])
    px(img, 13, 8, CHAR)
    out = texkit.outline(img)  # the dish ringed in its own clay; a flame is light, and has no outline
    for (x, y), part in {(13, 4): "tip", (12, 5): "outer", (13, 5): "inner", (14, 5): "outer",
                         (12, 6): "outer", (13, 6): "heart", (14, 6): "outer", (13, 7): "inner"}.items():
        px(out, x, y, FLAME[part])
    return out


def element(frm, to, faces, light=0):
    e = {"from": frm, "to": to, "faces": faces}
    if light:
        e["light_emission"] = light
    return e


def oil_surface():
    """The oil standing in the dish from above: a deep amber, lighter towards the light, with a gleam across it."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            px(img, x, y, LAMP_OIL[2] if x + y < 13 else LAMP_OIL[1] if x + y < 22 else LAMP_OIL[0])
    for x, y in ((6, 7), (7, 6), (8, 6), (9, 7)):  # the gleam of the sky in it
        px(img, x, y, LAMP_OIL[3])
    return texkit.finish(img, 4, keep=(LAMP_OIL[3],), clusters=0.3, grain=0.05, size=4, reach=1)


def flame():
    """The flame, three pixels across and four high at the bottom middle of the texture (its UV is [6, 12, 9, 16]): a
    red-orange tip, orange sides, and a white heart low down, the way a small oil flame burns."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for (x, y), part in {(7, 12): "tip", (6, 13): "outer", (7, 13): "inner", (8, 13): "outer",
                         (6, 14): "outer", (7, 14): "heart", (8, 14): "outer", (7, 15): "inner"}.items():
        px(img, x, y, FLAME[part])
    return img


def lamp_model(oil, lit):
    """
    The dish, wick lying south (the blockstate turns it to face the placer): a round foot and floor (8 across), a wall
    two pixels high round the oil - as thick as a flower pot's, so the dish reads as a dish - the oil at its depth,
    the wick from the middle out over the south rim, and when lit the flame on its end: two crossed planes, lit
    whatever the light round it, as a flame is.
    """
    clay_face = {"texture": "#clay"}
    sides = ("north", "east", "south", "west")
    els = []
    # foot and floor
    for x0, z0, x1, z1 in ((4, 5, 12, 11), (5, 4, 11, 12)):
        els.append(element([x0, 0, z0], [x1, 1, z1], {f: dict(clay_face) for f in sides + ("up", "down")}))
    # the wall
    for x0, z0, x1, z1 in ((4, 5, 5, 11), (11, 5, 12, 11), (5, 4, 11, 5), (5, 11, 11, 12)):
        els.append(element([x0, 1, z0], [x1, 3, z1], {f: dict(clay_face) for f in sides + ("up",)}))
    if oil:
        top = 1 + 0.45 * oil
        els.append(element([5, 1, 5], [11, top, 11], {"up": {"texture": "#oil", "uv": [5, 5, 11, 11]}}))
    # the wick: from the middle, resting on the south rim and out over it
    # pixel for pixel: the last 5.6 rows of the pith, its charred end at the lit tip over the rim
    wick = {"texture": "#wick", "uv": [7, 10.4, 8, 16]}
    els.append(element([7.5, 2.8, 7], [8.5, 3.3, 12.6], {"up": dict(wick), "east": {"texture": "#wick", "uv": [0, 0, 5.6, 0.5]},
                                                         "west": {"texture": "#wick", "uv": [0, 0, 5.6, 0.5]},
                                                         "south": {"texture": "#wick", "uv": [7, 15, 8, 15.5]}}))
    if lit:
        blaze = {"texture": "#flame", "uv": [6, 12, 9, 16]}
        plane_a = element([6.5, 3.3, 12.2], [9.5, 7.3, 12.2], {"north": dict(blaze), "south": dict(blaze)}, light=15)
        plane_b = element([8, 3.3, 10.7], [8, 7.3, 13.7], {"east": dict(blaze), "west": dict(blaze)}, light=15)
        for plane in (plane_a, plane_b):
            plane["shade_direction_override"] = "up"  # unshaded: 26.3 has no "shade": false
            els.append(plane)
    textures = {"particle": "nookworks:block/oil_lamp_clay", "clay": "nookworks:block/oil_lamp_clay",
                "wick": "nookworks:block/oil_lamp_wick", "oil": "nookworks:block/oil_lamp_oil",
                "flame": "nookworks:block/oil_lamp_flame"}
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "render_type": "minecraft:cutout",
            "textures": textures, "elements": els}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def main():
    clay().save(ASSETS / "textures/block/oil_lamp_clay.png")
    wick().save(ASSETS / "textures/block/oil_lamp_wick.png")
    oil_surface().save(ASSETS / "textures/block/oil_lamp_oil.png")
    flame().save(ASSETS / "textures/block/oil_lamp_flame.png")
    item().save(ASSETS / "textures/item/oil_lamp.png")
    variants = {}
    for oil in range(5):
        for lit in (False, True):
            if lit and oil == 0:
                continue
            name = f"oil_lamp_{oil}" + ("_lit" if lit else "")
            write_json(ASSETS / f"models/block/{name}.json", lamp_model(oil, lit))
    for facing, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
        for oil in range(5):
            for lit in ("false", "true"):
                name = f"oil_lamp_{oil}" + ("_lit" if lit == "true" and oil else "")
                v = {"model": f"nookworks:block/{name}"}
                if y:
                    v["y"] = y
                variants[f"facing={facing},lit={lit},oil={oil}"] = v
    write_json(ASSETS / "blockstates/oil_lamp.json", {"variants": variants})
    write_json(ASSETS / "models/item/oil_lamp.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "nookworks:item/oil_lamp"}})
    write_json(ASSETS / "items/oil_lamp.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/oil_lamp"}})
    print("wrote the oil lamp")


if __name__ == "__main__":
    main()
