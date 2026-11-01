"""Dishes and tableware for 1.3, the cooking update (the user, 2026-10-31). Original pixel art, drawn flat and finished
with texkit; models, blockstates, item definitions, loot tables and recipes written alongside. Deterministic.

Dishes, set out on a Wooden Tray or a Plate (drawn by DishModels; never straight on the table):
- dish_udon      udon in a deep navy glazed bowl (the item's bowl), its broth with noodles, green onion and fishcake
- dish_tonkatsu  a cutlet sliced in five, the cut faces pale, with shredded cabbage behind
- dish_karaage   five pieces of karaage piled, with a wedge of lemon
- dish_donut     a ring of dough with a sugar glaze on top
Tableware:
- plate          a white glazed plate (one dish on it)
- wine_glass     a clear glass on a stem, one to four to a block
- mug            a white glazed mug with a blue band and a handle, one to four to a block

Run from the repo root:  python tools/textures/gen_tableware.py
"""
import json
import math
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
DATA = ROOT / "src/main/resources/data/nookworks"
TEX = ASSETS / "textures/block"

GLAZE = [(196, 194, 184), (220, 218, 208), (238, 236, 228), (250, 249, 244)]
NAVY = [(22, 30, 62), (34, 46, 92), (50, 66, 124), (72, 92, 156)]
BROTH = [(150, 100, 48), (178, 126, 66), (204, 154, 90)]
NOODLE = [(214, 196, 150), (236, 222, 180), (250, 242, 212)]
ONION = [(58, 128, 40), (92, 170, 60)]
NARUTO = [(236, 236, 230), (226, 110, 140)]
CRUMB = [(150, 88, 30), (186, 122, 46), (214, 156, 70), (236, 190, 104)]
MEAT = [(222, 190, 160), (240, 216, 190), (252, 242, 222)]
CABBAGE = [(120, 172, 70), (156, 204, 96), (196, 230, 140), (230, 246, 196)]
KARAAGE = [(120, 64, 22), (160, 94, 34), (196, 128, 52), (222, 162, 76)]
LEMON = [(214, 170, 30), (244, 210, 64), (252, 236, 140)]
DOUGH = [(150, 92, 40), (186, 122, 58), (214, 154, 80)]
SUGAR = [(214, 200, 172), (234, 222, 198), (248, 242, 228)]
BLUE = [(74, 108, 160), (104, 140, 192)]
GLASS = [(196, 226, 236, 70), (220, 240, 246, 100), (246, 252, 255, 170)]


def img16():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def fill(img, colours, seed, pattern):
    """Each pixel's colour index from pattern(x, y) (an index into colours, or None for clear)."""
    for y in range(16):
        for x in range(16):
            i = pattern(x, y)
            if i is not None:
                c = colours[i]
                img.putpixel((x, y), c if len(c) == 4 else c + (255,))
    return img


def wobble(x, y, seed, n):
    """A small deterministic noise, 0..n-1."""
    return int((math.sin(x * 12.9898 + y * 78.233 + seed * 3.7) * 43758.5453) % 1 * n)


# ---- textures
def bowl_side():
    img = fill(img16(), NAVY, 1, lambda x, y: 3 if y <= 1 else (2 if y < 6 else 1) - (1 if y > 12 else 0) + (wobble(x, y, 1, 2) == 0) * 0)
    for x in range(16):  # the white rim, and a pale line of glaze round the belly
        img.putpixel((x, 0), GLAZE[3] + (255,))
        img.putpixel((x, 7), NAVY[3] + (255,))
    return texkit.finish(img, 801, keep=[GLAZE[3]], **texkit.SMOOTH)


def udon_top():
    img = img16()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c = GLAZE[3]  # the bowl's rim seen from above
            elif x in (1, 14) or y in (1, 14):
                c = NAVY[2]  # its inside, down to the broth
            else:
                c = BROTH[1 + (wobble(x, y, 2, 3) == 0)]
                # thick noodles in loose waves across the bowl, the broth between them
                wave = math.sin(x * 0.9 + (y // 3) * 1.7) * 0.9
                if (y + wave) % 3 < 1.6 and 2 <= y <= 13:
                    c = NOODLE[1 + ((x * 7 + y) % 6 == 0)]
            img.putpixel((x, y), c + (255,))
    for x, y in ((4, 4), (5, 4), (9, 10), (10, 10), (11, 9), (5, 11), (6, 11)):
        img.putpixel((x, y), ONION[1] + (255,))
    for x, y in ((10, 4), (11, 4), (10, 5), (11, 5)):  # a slice of fishcake with its pink swirl
        img.putpixel((x, y), NARUTO[0] + (255,))
    img.putpixel((11, 5), NARUTO[1] + (255,))
    return texkit.finish(img, 802, keep=[GLAZE[3], NARUTO[1]], **texkit.SMOOTH)


def katsu_crumb():
    img = fill(img16(), CRUMB, 3, lambda x, y: 1 + wobble(x, y, 3, 3))
    return texkit.finish(img, 803, **texkit.ROUGH)


def katsu_cut():
    def p(x, y):
        if y <= 2 or y >= 13 or x <= 1 or x >= 14:
            return 0  # the crumb round the cut
        return 1 if wobble(x, y, 4, 4) else 2
    img = img16()
    for y in range(16):
        for x in range(16):
            i = p(x, y)
            img.putpixel((x, y), (CRUMB[2] if i == 0 else MEAT[i]) + (255,))
    return texkit.finish(img, 804, **texkit.SMOOTH)


def cabbage():
    img = fill(img16(), CABBAGE, 5, lambda x, y: (x * 3 + y * 7 + wobble(x, y, 5, 3)) % 4)
    return texkit.finish(img, 805, **texkit.PLANT)


def karaage():
    img = fill(img16(), KARAAGE, 6, lambda x, y: 1 + wobble(x, y, 6, 3))
    return texkit.finish(img, 806, **texkit.ROUGH)


def lemon():
    img = fill(img16(), LEMON, 7, lambda x, y: 2 if y < 3 else 1 if y < 13 else 0)
    return texkit.finish(img, 807, **texkit.SMOOTH)


def donut_top():
    img = fill(img16(), SUGAR, 8, lambda x, y: 1 + (wobble(x, y, 8, 4) == 0))
    for x in range(16):
        if wobble(x, 0, 9, 3) == 0:
            img.putpixel((x, 15), DOUGH[2] + (255,))  # the glaze stops short of the edge here and there
    return texkit.finish(img, 808, **texkit.SMOOTH)


def donut_side():
    img = fill(img16(), DOUGH, 9, lambda x, y: (2 if y < 4 else 1) if wobble(x, y, 9, 5) else 0)
    for x in range(16):
        img.putpixel((x, 0), SUGAR[1] + (255,))
        if wobble(x, 1, 10, 2) == 0:
            img.putpixel((x, 1), SUGAR[0] + (255,))  # a drip of glaze over the side
    return texkit.finish(img, 809, **texkit.SMOOTH)


def plate_top():
    img = img16()
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            c = GLAZE[1] if d >= 7 else GLAZE[2] if d >= 5.5 else GLAZE[3]  # the rim, its slope, the well
            img.putpixel((x, y), c + (255,))
    return texkit.finish(img, 810, **texkit.SMOOTH)


def plate_side():
    img = fill(img16(), GLAZE, 11, lambda x, y: 1 if y > 8 else 2)
    return texkit.finish(img, 811, **texkit.SMOOTH)


def mug_side():
    img = fill(img16(), GLAZE, 12, lambda x, y: 3)
    for x in range(16):
        for y in (6, 7):
            img.putpixel((x, y), BLUE[1 if y == 6 else 0] + (255,))
    return texkit.finish(img, 812, keep=list(BLUE), **texkit.SMOOTH)


def mug_top():
    img = img16()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            img.putpixel((x, y), (GLAZE[3] if edge else (90, 84, 78)) + (255,))  # the rim round the empty inside
    return texkit.finish(img, 813, keep=[GLAZE[3]], **texkit.SMOOTH)


def glass():
    img = img16()
    for y in range(16):
        for x in range(16):
            c = GLASS[2] if x in (0, 15) or y in (0, 15) else GLASS[1] if (x + y) % 9 == 0 or x == 2 else GLASS[0]
            img.putpixel((x, y), c)
    return img  # left exact: texkit's grain would cloud the glass


TEXTURES = {
    "dish_bowl_side": bowl_side, "dish_udon_top": udon_top, "dish_katsu_crumb": katsu_crumb, "dish_katsu_cut": katsu_cut,
    "dish_cabbage": cabbage, "dish_karaage": karaage, "dish_lemon": lemon, "dish_donut_top": donut_top,
    "dish_donut_side": donut_side, "plate_top": plate_top, "plate_side": plate_side, "mug_side": mug_side,
    "mug_top": mug_top, "wine_glass": glass,
}


# ---- models
def box(frm, to, tex, top=None, bottom=None, sides=None, cut=None):
    """An element with every face on texture #tex (#top for up, #cut for east and west if given), uv from its size."""
    faces = {}
    for f in ("north", "south", "east", "west", "up", "down"):
        t = tex
        if f == "up" and top:
            t = top
        elif f == "down" and bottom:
            t = bottom
        elif f in ("east", "west") and cut:
            t = cut
        elif f in ("north", "south", "east", "west") and sides:
            t = sides
        faces[f] = {"texture": "#" + t}
    return {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}


def dish(textures, elements, translucent=False):
    m = {"parent": "minecraft:block/block", "textures": dict(textures, particle=list(textures.values())[0]),
         "elements": elements}
    if translucent:
        m["render_type"] = "minecraft:translucent"
    return m


def t(name):
    return "nookworks:block/" + name


def models():
    out = {}
    # a deep bowl narrowing to its foot, the broth just under the rim
    out["block/dish_udon"] = dish({"side": t("dish_bowl_side"), "top": t("dish_udon_top")}, [
        box([6.25, 0, 6.25], [9.75, 0.5, 9.75], "side"),
        box([5.5, 0.5, 5.5], [10.5, 1.5, 10.5], "side"),
        box([5, 1.5, 5], [11, 4, 11], "side", top="top")])
    slices = [box([4 + i * 1.65, 0, 5 + (i % 2) * 0.3], [5.5 + i * 1.65, 1.6, 10 + (i % 2) * 0.3], "crumb", cut="cut") for i in range(5)]
    out["block/dish_tonkatsu"] = dish({"crumb": t("dish_katsu_crumb"), "cut": t("dish_katsu_cut"), "cabbage": t("dish_cabbage")},
                                      slices + [box([4.5, 0, 10.6], [11.5, 1.4, 12.6], "cabbage")])
    pieces = [([5, 0, 5], [8, 2.5, 8]), ([8.5, 0, 5.5], [11, 2.2, 8.3]), ([5.5, 0, 8.5], [8.2, 2.3, 11]),
              ([8.4, 0, 8.6], [11, 2.6, 11.2]), ([6.8, 2.2, 6.8], [9.4, 4.4, 9.4])]
    out["block/dish_karaage"] = dish({"k": t("dish_karaage"), "lemon": t("dish_lemon")},
                                     [box(a, b, "k") for a, b in pieces] + [box([10.6, 0, 10.8], [12.6, 1.5, 12.2], "lemon")])
    ring = [([4, 0, 4], [12, 2.5, 6.5]), ([4, 0, 9.5], [12, 2.5, 12]), ([4, 0, 6.5], [6.5, 2.5, 9.5]), ([9.5, 0, 6.5], [12, 2.5, 9.5])]
    out["block/dish_donut"] = dish({"side": t("dish_donut_side"), "top": t("dish_donut_top")},
                                   [box(a, b, "side", top="top") for a, b in ring])
    out["block/plate"] = dish({"side": t("plate_side"), "top": t("plate_top")}, [box([2, 0, 2], [14, 1, 14], "side", top="top")])
    spots = {1: [(8, 8)], 2: [(5.5, 7.5), (10.5, 8.5)], 3: [(5.5, 6), (10.5, 6.5), (8, 10.5)], 4: [(5, 5), (11, 5), (5, 11), (11, 11)]}
    for n, places in spots.items():
        glass_el, mug_el = [], []
        for x, z in places:
            glass_el += [box([x - 1.5, 0, z - 1.5], [x + 1.5, 0.5, z + 1.5], "g"), box([x - 0.5, 0.5, z - 0.5], [x + 0.5, 3.5, z + 0.5], "g"),
                         box([x - 1.5, 3.5, z - 1.5], [x + 1.5, 7, z + 1.5], "g")]
            mug_el += [box([x - 1.5, 0, z - 1.5], [x + 1.5, 4, z + 1.5], "side", top="top"),
                       box([x + 1.5, 1, z - 0.5], [x + 2.5, 3, z + 0.5], "side")]
        out[f"block/wine_glass_{n}"] = dish({"g": t("wine_glass")}, glass_el, translucent=True)
        out[f"block/mug_{n}"] = dish({"side": t("mug_side"), "top": t("mug_top")}, mug_el)
    return out


def facing_variants(model, extra=""):
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    v = {}
    for f, y in rot.items():
        key = f"facing={f}" + extra
        v[key] = {"model": model} if y == 0 else {"model": model, "y": y}
    return v


def main():
    TEX.mkdir(parents=True, exist_ok=True)
    for name, draw in TEXTURES.items():
        draw().save(TEX / f"{name}.png")
    for path, model in models().items():
        write(ASSETS / "models" / f"{path}.json", model)
    # blockstates and items
    write(ASSETS / "blockstates/plate.json", {"variants": facing_variants("nookworks:block/plate")})
    for block in ("wine_glass", "mug"):
        variants = {}
        for n in range(1, 5):
            variants.update(facing_variants(f"nookworks:block/{block}_{n}", f",count={n}"))
        write(ASSETS / f"blockstates/{block}.json", {"variants": variants})
    for item, model in (("plate", "block/plate"), ("wine_glass", "block/wine_glass_1"), ("mug", "block/mug_1")):
        write(ASSETS / f"items/{item}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:{model}"}})
    # loot: the plate itself; a glass or mug for each in the block
    write(DATA / "loot_table/blocks/plate.json", simple_loot("plate"))
    for block in ("wine_glass", "mug"):
        write(DATA / f"loot_table/blocks/{block}.json", count_loot(block))
    # recipes
    write(DATA / "recipe/plate.json", shaped("misc", {"T": "minecraft:white_terracotta"}, ["TTT"], "plate", 6))
    write(DATA / "recipe/mug.json", shaped("misc", {"T": "minecraft:white_terracotta"}, ["T T", " T "], "mug", 4))
    write(DATA / "recipe/wine_glass.json", shaped("misc", {"G": "#c:glass_blocks/colorless"}, ["G G", " G ", " G "], "wine_glass", 4))


def simple_loot(name):
    return {"type": "minecraft:block", "random_sequence": f"nookworks:blocks/{name}", "pools": [
        {"bonus_rolls": 0.0, "rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
         "entries": [{"type": "minecraft:item", "name": f"nookworks:{name}"}]}]}


def count_loot(name):
    """As 26.3 writes a slab's: a set_count for each count over one, by the block's state, then explosion decay."""
    modifier = [{"type": "minecraft:set_count", "condition": {"type": "minecraft:match_block", "blocks": f"nookworks:{name}",
                                                              "state": {"count": str(n)}}, "count": n} for n in range(2, 5)]
    modifier.append({"type": "minecraft:explosion_decay"})
    return {"type": "minecraft:block", "pools": [{"entries": [{"type": "minecraft:item", "modifier": modifier, "name": f"nookworks:{name}"}],
                                                  "rolls": 1}], "random_sequence": f"nookworks:blocks/{name}"}


def shaped(category, key, pattern, result, count):
    return {"type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern,
            "result": {"id": f"nookworks:{result}", "count": count}}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


if __name__ == "__main__":
    main()
