"""Dishes and tableware for 1.3, the cooking update (the user, 2026-10-31). Original pixel art, drawn flat and finished
with texkit.shade (all plain: glaze and food); models, blockstates, item definitions, loot tables and recipes written alongside. Deterministic.

Dishes, set out on a Wooden Tray or a Plate (drawn by DishModels; never straight on the table):
- dish_udon      udon in a round navy glazed bowl (the item's bowl) under a white rim, fishcake, chopsticks across
- dish_tonkatsu  a cutlet sliced in five, the cut faces pale, with a heap of shredded cabbage behind and mustard
- dish_karaage   five pieces of karaage piled on a lettuce leaf, with a wedge of lemon
- dish_donut     a round ring of dough with a sugar glaze on top
Tableware:
- plate          a round white glazed plate, its rim raised (one dish on it)
- wine_glass     a clear glass on a stem, one to four to a block
- mug            a white glazed mug with a blue band, a lip and a handle, one to four to a block
Redrawn 2026-11-05: round where they were square, in whole pixels as vanilla's round things are (the user found a
half-pixel version too much: "マイクラらしさを残して").

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
GLASS = [(196, 226, 236, 110), (220, 240, 246, 150), (246, 252, 255, 220)]  # firmer since r21: it barely showed


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
    return texkit.shade(img, 801, keep=[GLAZE[3]], **texkit.SMOOTH)


def udon_top():
    img = img16()
    for y in range(16):
        for x in range(16):
            if math.hypot(x - 7.5, y - 7.5) > 6.6:
                c = NAVY[2]  # the bowl's inside, at the edge of the broth under the rim
            else:
                c = BROTH[1 + (wobble(x, y, 2, 3) == 0)]
                # thick noodles in loose waves across the bowl, the broth between them
                wave = math.sin(x * 0.9 + (y // 3) * 1.7) * 0.9
                if (y + wave) % 3 < 1.6 and 2 <= y <= 13:
                    c = NOODLE[1 + ((x * 7 + y) % 6 == 0)]
            img.putpixel((x, y), c + (255,))
    for x, y in ((4, 4), (5, 4), (9, 10), (10, 10), (11, 9), (5, 11), (6, 11)):
        img.putpixel((x, y), ONION[1] + (255,))
    return texkit.shade(img, 802, **texkit.SMOOTH)


def naruto():
    """The fishcake slice from above, its pink swirl round the middle (laid on a small disc, so drawn round the centre)."""
    img = fill(img16(), NARUTO, 14, lambda x, y: 0)
    for x, y in ((7, 6), (8, 6), (9, 7), (9, 8), (8, 9), (7, 9), (6, 8), (6, 7), (7, 7), (8, 8)):
        img.putpixel((x, y), NARUTO[1] + (255,))
    return texkit.shade(img, 814, keep=[NARUTO[1]], **texkit.SMOOTH)


def lettuce():
    img = fill(img16(), CABBAGE, 15, lambda x, y: 2 if (x + y) % 5 == 0 or abs(x - y) % 7 == 0 else 1 + (wobble(x, y, 15, 4) == 0))
    return texkit.shade(img, 815, **texkit.PLANT)


def lemon_cut():
    """A wedge's flesh: pale segments split by white pith, the rind's yellow at the edges."""
    def p(x, y):
        if y in (0, 15) or x in (0, 15):
            return 1
        return 2 if x % 4 == 0 else 0
    img = img16()
    flesh = [(240, 220, 110), LEMON[1], (252, 246, 210)]
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), flesh[p(x, y)] + (255,))
    return texkit.shade(img, 816, **texkit.SMOOTH)


def katsu_crumb():
    img = fill(img16(), CRUMB, 3, lambda x, y: (0 if wobble(x, y, 13, 6) == 0 else 1 + wobble(x // 2, y // 2 + (x // 2) % 2, 3, 3)))  # crumbs in clumps
    return texkit.shade(img, 803, **texkit.ROUGH)


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
    return texkit.shade(img, 804, **texkit.SMOOTH)


def cabbage():
    img = fill(img16(), CABBAGE, 5, lambda x, y: (x * 3 + y * 7 + wobble(x, y, 5, 3)) % 4)
    return texkit.shade(img, 805, **texkit.PLANT)


def karaage():
    img = fill(img16(), KARAAGE, 6, lambda x, y: (0 if wobble(x, y, 16, 5) == 0 else 1 + wobble(x // 2 + (y // 3) % 2, y // 2, 6, 3)))  # craggy crust
    return texkit.shade(img, 806, **texkit.ROUGH)


def lemon():
    img = fill(img16(), LEMON, 7, lambda x, y: 2 if y < 3 else 1 if y < 13 else 0)
    return texkit.shade(img, 807, **texkit.SMOOTH)


def donut_top():
    img = fill(img16(), SUGAR, 8, lambda x, y: 1 + (wobble(x, y, 8, 4) == 0))
    for x in range(16):
        if wobble(x, 0, 9, 3) == 0:
            img.putpixel((x, 15), DOUGH[2] + (255,))  # the glaze stops short of the edge here and there
    return texkit.shade(img, 808, **texkit.SMOOTH)


def donut_side():
    img = fill(img16(), DOUGH, 9, lambda x, y: (2 if y < 4 else 1) if wobble(x, y, 9, 5) else 0)
    for x in range(16):
        img.putpixel((x, 0), SUGAR[1] + (255,))
        if wobble(x, 1, 10, 2) == 0:
            img.putpixel((x, 1), SUGAR[0] + (255,))  # a drip of glaze over the side
    return texkit.shade(img, 809, **texkit.SMOOTH)


def plate_top():
    img = img16()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            c = GLAZE[2] if d >= 5.2 else GLAZE[1] if d >= 4.2 else GLAZE[3]  # the rim, the shadow at its foot, the well
            img.putpixel((x, y), c + (255,))
    return texkit.shade(img, 810, **texkit.SMOOTH)


def plate_side():
    img = fill(img16(), GLAZE, 11, lambda x, y: 1 if y > 8 else 2)
    return texkit.shade(img, 811, **texkit.SMOOTH)


def mug_side():
    img = fill(img16(), GLAZE, 12, lambda x, y: 3)
    for x in range(16):
        for y in (12, 13):  # rows 11-16 are the mug's five pixels: the band a pixel under its rim
            img.putpixel((x, y), BLUE[1 if y == 12 else 0] + (255,))
    return texkit.shade(img, 812, keep=list(BLUE), **texkit.SMOOTH)


def mug_top():
    img = img16()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            img.putpixel((x, y), (GLAZE[3] if edge else (90, 84, 78)) + (255,))  # the rim round the empty inside
    return texkit.shade(img, 813, keep=[GLAZE[3]], **texkit.SMOOTH)


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
    "mug_top": mug_top, "wine_glass": glass, "dish_naruto": naruto, "dish_lettuce": lettuce, "dish_lemon_cut": lemon_cut,
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


def blob(inside, y0, y1, tex, step=0.5, area=(0, 16, 0, 16), **faces):
    """Boxes filling the cells of a grid (cells {@code step} wide over {@code area}, x0 x1 z0 z1) whose centres
    inside(x, z) takes, from y0 to y1: runs along x merged, then rows of the same runs - a round or ring shape in few
    boxes that do not overlap (overlapping faces would flicker)."""
    x0, x1, z0, z1 = area
    rows = []
    z = z0
    while z < z1 - 1e-6:
        runs, start, x = [], None, x0
        while x <= x1 + 1e-6:
            hit = x < x1 - 1e-6 and inside(x + step / 2, z + step / 2)
            if hit and start is None:
                start = x
            elif not hit and start is not None:
                runs.append((start, x))
                start = None
            x += step
        rows.append((z, tuple(runs)))
        z += step
    out, i = [], 0
    while i < len(rows):
        j = i
        while j + 1 < len(rows) and rows[j + 1][1] == rows[i][1]:
            j += 1
        for a, b in rows[i][1]:
            out.append(box([a, y0, rows[i][0]], [b, y1, rows[j][0] + step], tex, **faces))
        i = j + 1
    return out


def disc(cx, cz, r, rin=0.0, sx=1.0):
    """inside() of a disc (or a ring from rin), round {@code cx, cz}, stretched {@code sx} along x."""
    return lambda x, z: rin * rin <= ((x - cx) / sx) ** 2 + (z - cz) ** 2 <= r * r


def turned(element, angle, axis, origin):
    element["rotation"] = {"angle": angle, "axis": axis, "origin": [round(v, 3) for v in origin]}
    return element


def models():
    out = {}
    # a round bowl on a foot, the broth under a white rim, a fishcake on it and chopsticks laid across - in whole
    # pixels, round as vanilla's round things are
    udon = blob(disc(8, 8, 2.5), 0, 1, "side", step=1) + blob(disc(8, 8, 4), 1, 4, "side", top="top", step=1) \
        + blob(disc(8, 8, 4, 3), 4, 4.5, "rim", step=1)
    udon += [box([9, 4, 6], [11, 4.25, 8], "naruto"),
             box([4, 4.5, 9], [12, 5, 9.5], "stick"), box([4, 4.5, 10], [12, 5, 10.5], "stick")]
    out["block/dish_udon"] = dish({"side": t("dish_bowl_side"), "top": t("dish_udon_top"), "rim": t("plate_side"),
                                   "naruto": t("dish_naruto"), "stick": "minecraft:block/stripped_bamboo_block"}, udon)
    # five slices side by side, a pixel's gap between them for the pale cut faces, a heap of shredded cabbage behind
    slices = [box([3.5 + i * 1.9, 0, 4.5 + (i % 2) * 0.5], [5 + i * 1.9, 1.5, 9.5 + (i % 2) * 0.5], "crumb", cut="cut") for i in range(5)]
    cabbage = blob(disc(8, 11.5, 2, sx=2), 0, 1, "cabbage", step=1, area=(3, 13, 9, 14)) \
        + blob(disc(8, 11.5, 1, sx=2.5), 1, 2, "cabbage", step=1, area=(3, 13, 9, 14))
    out["block/dish_tonkatsu"] = dish({"crumb": t("dish_katsu_crumb"), "cut": t("dish_katsu_cut"), "cabbage": t("dish_cabbage"),
                                       "mustard": t("dish_lemon")},
                                      slices + cabbage + [box([11.5, 0, 4.5], [12.5, 0.5, 5.5], "mustard")])
    # five pieces piled on a lettuce leaf, every other one turned, with a wedge of lemon
    pieces = [((6.5, 6.5), 3, 2.5, 0), ((9.7, 6.6), 2.5, 2.25, 22.5), ((6.6, 9.7), 2.5, 2.25, 22.5),
              ((9.6, 9.6), 3, 2.5, 0), ((8, 8), 2.5, 2.25, 22.5)]
    karaage = []
    for n, ((x, z), w, h, a) in enumerate(pieces):
        y = 0.25 if n < 4 else 2.5
        e = box([x - w / 2, y, z - w / 2], [x + w / 2, y + h, z + w / 2], "k")
        karaage.append(turned(e, a, "y", [x, y, z]) if a else e)
    leaf = blob(disc(8, 8, 5), 0, 0.25, "leaf", step=1)
    out["block/dish_karaage"] = dish({"k": t("dish_karaage"), "lemon": t("dish_lemon"), "cut": t("dish_lemon_cut"),
                                      "leaf": t("dish_lettuce")},
                                     leaf + karaage + [box([10.5, 0.25, 11], [13, 1.5, 12.5], "lemon", top="cut")])
    # a round ring of dough under its glaze
    out["block/dish_donut"] = dish({"side": t("dish_donut_side"), "top": t("dish_donut_top")},
                                   blob(disc(8, 8, 4, 1.5), 0, 2.5, "side", top="top", step=1))
    # a round plate, its rim standing up round the well where the food sits
    out["block/plate"] = dish({"side": t("plate_side"), "top": t("plate_top")},
                              blob(disc(8, 8, 6), 0, 1, "side", top="top", step=1)
                              + blob(disc(8, 8, 6, 5), 1, 1.5, "side", top="top", step=1))
    spots = {1: [(8, 8)], 2: [(5.5, 7.5), (10.5, 8.5)], 3: [(5.5, 6), (10.5, 6.5), (8, 10.5)], 4: [(5, 5), (11, 5), (5, 11), (11, 11)]}
    mug_spots = {1: [(8, 8)], 2: [(4.5, 7.5), (11, 8.5)], 3: [(4.5, 5.5), (11, 6), (7.5, 11)], 4: spots[4]}
    for n, places in spots.items():
        glass_el, mug_el = [], []
        for k, (x, z) in enumerate(places):
            near = (x - 3, x + 3, z - 3, z + 3)  # a grid of whole pixels round this spot
            # a round foot, a thin stem, an open bowl
            glass_el += blob(disc(x, z, 1.5), 0, 0.5, "g", step=0.5, area=near) + [box([x - 0.5, 0.5, z - 0.5], [x + 0.5, 3.5, z + 0.5], "g")] \
                + blob(disc(x, z, 1.75, 1.0), 3.5, 7, "g", step=0.5, area=near) + blob(disc(x, z, 1.0), 3.5, 4, "g", step=0.5, area=near)
            x, z = mug_spots[n][k]  # spaced wider, for the handles
            near = (x - 3, x + 3, z - 3, z + 3)
            # a mug (square at this size, as a flower pot is), its dark inside under a white lip, a handle on its right
            mug_el += blob(disc(x, z, 2.2), 0, 4.5, "side", top="inside", step=1, area=near) \
                + blob(disc(x, z, 2.2, 1), 4.5, 5, "rim", step=1, area=near) \
                + [box([x + 2, 3.5, z - 0.5], [x + 3, 4.5, z + 0.5], "side"), box([x + 2, 1, z - 0.5], [x + 3, 2, z + 0.5], "side"),
                   box([x + 3, 1, z - 0.5], [x + 4, 4.5, z + 0.5], "side")]
        out[f"block/wine_glass_{n}"] = dish({"g": t("wine_glass")}, glass_el, translucent=True)
        out[f"block/mug_{n}"] = dish({"side": t("mug_side"), "inside": t("mug_top"), "rim": t("plate_side")}, mug_el)
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
