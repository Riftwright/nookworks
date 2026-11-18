"""Vanilla's food as small models of itself on a Plate or a Wooden Tray - trial of the cooking review, 2026-11-14 (the
user: バニラのアイテムも必要に応じて立体化して ... 現在の環境であれば立体化したほうが自然に感じる、というのが基準).

Every vanilla item with the food component has one (its four stews are in gen_dishes.py with our bowls): with a steak a
model and a raw chop a picture lying flat, the odd one out is what shows. Thirty-nine of 26.3's forty: the Honey Bottle
is a bottle, set down as bottles are (PlacedBottlesBlock), and goes on no plate or tray (the user, 2026-11-14:
ハチミツ入りの瓶を皿にのせるのはちょっと微妙 ... 瓶関係は、すでに全部ブロックとして配置できるはず). Cake is drawn whole, for
the Plate and the tray (it was left out, a block of its own, until the user missed it there). All of it our own drawing - no vanilla pixel; a bamboo skewer aside, no vanilla texture either.

Run through gen_dishes.py.
"""
import math

import texkit
from dishkit import blob, box, disc, glow, mask, mat, put, rows_by_height, tex, turned, wobble

M = "dish/minecraft/"


def edge_of(rows):
    """The pixels of a text shape that have clear beside them."""
    def on(i, j):
        return 0 <= j < len(rows) and 0 <= i < len(rows[j]) and rows[j][i] != "."
    return {(i, j) for j in range(len(rows)) for i in range(len(rows[j]))
            if on(i, j) and not (on(i - 1, j) and on(i + 1, j) and on(i, j - 1) and on(i, j + 1))}


def lump(rows, x0, z0, height, side, top=None):
    kw = {"top": top} if top else {}
    return blob(mask(rows, x0, z0), 0, height, side, step=1, **kw)


def round_fruit(skin, top="skin"):
    """An apple's shape: six pixels across at its middle, drawn in at the foot and the shoulder."""
    return blob(disc(8, 8, 2.1), 0, 0.5, skin, step=1) + blob(disc(8, 8, 2.6), 0.5, 3.5, skin, step=1) \
        + blob(disc(8, 8, 2.1), 3.5, 4.25, skin, top=top, step=1)


# ---- fruit and vegetables
LEAF = [(60, 116, 40), (88, 150, 54), (124, 184, 76)]
STEM = [(96, 64, 34), (126, 88, 48)]


def leaf():
    return mat("leaf", LEAF, 1102, weights=[25, 50], clump=1, preset=texkit.PLANT)


def apples():
    stem = mat("stem", STEM, 1101, weights=[45], clump=1)
    for name, colours, seed in (("apple", [(146, 18, 22), (194, 34, 34), (224, 60, 48), (246, 120, 100)], 1103),
                                ("golden_apple", [(190, 134, 18), (234, 186, 46), (252, 222, 100), (255, 244, 170)], 1104)):
        skin = tex(name, lambda x, y, c=colours, s=seed: c[3] if (x, y) in ((6, 12), (6, 5)) else
                   c[1 + (wobble(x // 2, y // 2, s, 4) == 0) - (wobble(x // 2, y, s + 1, 4) == 0)], seed, keep=[colours[3]])
        e = round_fruit("skin") + [box([7.5, 4.25, 7.5], [8.5, 5.25, 8.5], "stem"), box([8.5, 4.5, 7.25], [10, 4.75, 8.25], "leaf")]
        put(M + name, {"skin": skin, "stem": stem, "leaf": leaf()}, e)
        if name == "golden_apple":
            put(M + "enchanted_golden_apple", {"skin": skin, "stem": stem, "leaf": leaf()}, e)


WEDGE = ["R......",
         "RFF....",
         "RFFFF..",
         "RFFFFFF",
         "RFFFF..",
         "RFF....",
         "R......"]


def melon_slice():
    red, pith, seed_c = [(196, 34, 38), (224, 56, 52), (240, 88, 76)], (246, 214, 200), (44, 24, 20)
    seeds = {(7, 6), (8, 8), (7, 9), (9, 7), (6, 7)}

    def top(x, y):
        if (x, y) in seeds:
            return seed_c
        return pith if x == 5 else red[1 + (wobble(x, y, 71, 4) == 0) - (wobble(x, y, 72, 5) == 0)]
    rind = tex("melon_rind", lambda x, y: [(40, 96, 36), (70, 132, 48), (104, 164, 64)][(1 + (y % 3 == 0)) if x % 2 else 0], 1111)
    e = blob(mask(WEDGE, 4, 4, chars="R"), 0, 1.5, "rind", step=1) + blob(mask(WEDGE, 4, 4, chars="F"), 0, 1.5, "flesh", top="top", step=1)
    put(M + "melon_slice", {"top": tex("melon_top", top, 1112, keep=[pith, seed_c]), "flesh": mat("melon_flesh", red, 1113, weights=[25, 50], clump=2),
                            "rind": rind}, e)


def berries():
    spots = ((5.5, 6), (8.5, 6.5), (6.75, 9))
    for name, colours, light, seed in (("sweet_berries", [(120, 14, 24), (170, 24, 34), (208, 46, 50), (240, 110, 110)], 0, 1121),
                                       ("glow_berries", [(198, 110, 18), (240, 162, 36), (253, 206, 84), (255, 240, 170)], 9, 1122)):
        skin = tex(name, lambda x, y, c=colours, s=seed: c[0] if (x + y * 2) % 5 == 0 else c[1 + (wobble(x, y, s, 3) == 0)], seed)
        cap = tex(name + "_top", lambda x, y, c=colours, s=seed: c[3] if (x * 3 + y) % 7 == 0 else c[2], seed + 5)
        e = [box([x, 0, z], [x + 2, 1.75, z + 2], "skin", top="cap") for x, z in spots]
        if light:
            glow(e, light)
        e += [box([9, 0, 9.5], [11.5, 0.25, 11], "leaf"), box([8.75, 0.25, 10], [9.75, 0.5, 10.5], "leaf")]
        put(M + name, {"skin": skin, "cap": cap, "leaf": leaf()}, e)


def chorus_fruit():
    colours = [(100, 72, 110), (138, 104, 146), (172, 138, 176), (214, 196, 208)]
    skin = tex("chorus", lambda x, y: colours[3] if (x * 5 + y * 3) % 11 == 0 else
               colours[1 + (wobble(x // 2, y // 2, 81, 4) == 0) - (wobble(x, y // 2, 82, 4) == 0)], 1131, preset=texkit.ROUGH)
    e = [box([6, 0, 6], [10, 3.5, 10], "skin"), box([7, 3.5, 7], [9, 4.25, 9], "skin"),
         box([5.25, 1, 7], [6, 2.5, 9], "skin"), box([10, 1, 7.5], [10.75, 2.5, 9.5], "skin"), box([7, 0.75, 10], [9, 2.25, 10.75], "skin")]
    put(M + "chorus_fruit", {"skin": skin}, e)


def carrots():
    green = mat("carrot_top", LEAF, 1141, weights=[25, 50], clump=1, preset=texkit.PLANT)
    for name, colours, seed in (("carrot", [(190, 90, 14), (230, 126, 24), (250, 158, 48)], 1142),
                                ("golden_carrot", [(196, 140, 20), (238, 190, 48), (253, 226, 104)], 1143)):
        skin = tex(name, lambda x, y, c=colours, s=seed: c[0] if x % 3 == 1 and (y + x) % 4 else c[1 + (wobble(x, y, s, 4) == 0)], seed)
        e = [box([4, 0, 7], [8, 2, 9], "skin"), box([8, 0.25, 7.25], [10, 1.75, 8.75], "skin"), box([10, 0.5, 7.5], [11.5, 1.5, 8.5], "skin"),
             box([2.5, 0.5, 7.25], [4, 1.75, 8.75], "green"), box([1.75, 0.75, 7.6], [2.5, 1.5, 8.4], "green")]
        put(M + name, {"skin": skin, "green": green}, e)


SPUD = [".####.",
        "######",
        "######",
        ".####."]


def potatoes():
    for name, colours, eyes, seed in (("potato", [(166, 126, 66), (198, 158, 94), (222, 188, 126)], (120, 86, 44), 1151),
                                      ("baked_potato", [(132, 88, 42), (166, 118, 60), (194, 148, 86)], (98, 60, 28), 1152),
                                      ("poisonous_potato", [(116, 134, 58), (150, 166, 80), (184, 194, 106)], (84, 100, 40), 1153)):
        skin = tex(name, lambda x, y, c=colours, k=eyes, s=seed: k if (x * 7 + y * 5) % 23 == 0 else
                   c[1 + (wobble(x // 2, y // 2, s, 4) == 0) - (wobble(x // 2, y, s + 1, 4) == 0)], seed, keep=[eyes], preset=texkit.ROUGH)
        textures = {"skin": skin}
        e = lump(SPUD, 5, 6, 2, "skin") + [box([6, 2, 7], [10, 2.75, 9], "skin")]
        if name == "baked_potato":  # split along its top, the flesh steaming in the split, a pat of butter on it
            textures["flesh"] = mat("baked_flesh", [(240, 220, 150), (252, 240, 184)], 1154, weights=[40], clump=1)
            textures["butter"] = "nookworks:block/dish/butter"
            e += [box([6.5, 2.75, 7.5], [9.5, 3, 8.5], "flesh"), box([7.5, 3, 7.5], [8.5, 3.4, 8.5], "butter")]
        if name == "poisonous_potato":
            textures["shoot"] = mat("potato_shoot", [(170, 190, 110), (206, 220, 150)], 1155, weights=[45], clump=1)
            e += [box([10.5, 1, 7.5], [11.5, 1.75, 8.25], "shoot"), box([7, 2.75, 8], [7.75, 3.5, 8.75], "shoot")]
        put(M + name, textures, e)


def beetroot():
    colours = [(104, 14, 38), (142, 24, 52), (176, 44, 68)]
    skin = tex("beetroot", lambda x, y: colours[0] if (x + y // 4) % 4 == 0 else colours[1 + (wobble(x, y, 91, 4) == 0)], 1161)
    stalk = tex("beet_stalk", lambda x, y: (150, 40, 70) if x % 2 else LEAF[1], 1162)
    e = blob(disc(8, 8, 2.1), 0, 0.5, "skin", step=1) + blob(disc(8, 8, 2.6), 0.5, 3, "skin", step=1) \
        + blob(disc(8, 8, 2.1), 3, 3.5, "skin", step=1) \
        + [box([7.5, 3.5, 7.5], [8.5, 5.25, 8.5], "stalk"), box([8.5, 4.5, 7.25], [10.25, 4.75, 8.5], "leaf"), box([7, 0, 10.5], [8, 0.6, 12], "skin")]
    put(M + "beetroot", {"skin": skin, "stalk": stalk, "leaf": leaf()}, e)


def dried_kelp():
    colours = [(28, 48, 34), (42, 68, 46), (62, 94, 60)]
    kelp = tex("dried_kelp", lambda x, y: colours[2] if (x + y) % 6 == 0 else colours[wobble(x // 2, y, 95, 3) != 0], 1171)
    e = [box([4, 0, 6], [12, 0.5, 7.5], "kelp"), box([4.5, 0, 8.5], [11.5, 0.5, 10], "kelp"),
         turned(box([4.5, 0.5, 7.25], [11.5, 1, 8.75], "kelp"), 45, "y", [8, 0.5, 8])]
    put(M + "dried_kelp", {"kelp": kelp}, e)


# ---- bread and sweets
def bread():
    crust = [(146, 92, 38), (182, 124, 56), (208, 154, 82)]
    slash = (236, 202, 138)

    def top(x, y):
        return slash if (x - y * 0.5) % 3 < 1 and 5 <= x <= 10 and 7 <= y <= 8 else crust[2 if wobble(x, y, 101, 3) else 1]
    e = [box([3.5, 0, 6], [12.5, 2, 10], "crust"), box([4.5, 2, 6.5], [11.5, 3, 9.5], "crust", top="top")]
    put(M + "bread", {"crust": tex("bread_crust", rows_by_height([(1, crust[0])], lambda x, y: crust[1 + (wobble(x, y, 102, 3) == 0)]), 1181),
                      "top": tex("bread_top", top, 1182, keep=[slash])}, e)


def cookie():
    dough, chip = [(168, 110, 52), (196, 138, 72), (220, 166, 96)], (78, 44, 22)
    # one cookie to the item: a plate takes five and a tray sixteen, each drawn (it was three in a heap for the one)
    chips = {(6, 7), (8, 6), (7, 9), (9, 8)}
    top = tex("cookie_top", lambda x, y: chip if (x, y) in chips else dough[1 + (wobble(x, y, 105, 3) == 0)], 1191, keep=[chip])
    e = blob(disc(8, 8, 2.1), 0, 0.75, "side", top="top", step=1)
    put(M + "cookie", {"side": mat("cookie_side", dough[:2], 1192, weights=[45], clump=1), "top": top}, e)


def pumpkin_pie():
    crust, filling = [(160, 104, 46), (194, 138, 70), (220, 170, 100)], [(206, 104, 20), (232, 132, 34), (246, 160, 60)]
    cream = (250, 240, 214)

    def top(x, y):
        d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
        if d < 1.2:
            return cream
        return crust[2 if (x + y) % 2 else 1] if d > 3.0 else filling[1 + (wobble(x, y, 111, 4) == 0) - (wobble(x, y, 112, 5) == 0)]
    e = blob(disc(8, 8, 4), 0, 1.5, "side", top="top", step=1) + blob(disc(8, 8, 4, 3), 1.5, 2, "side", top="top", step=1) \
        + [box([7, 1.5, 7], [9, 2, 9], "cream")]
    put(M + "pumpkin_pie", {"side": mat("pie_crust", crust, 1201, weights=[25, 50], clump=2), "top": tex("pumpkin_pie_top", top, 1202, keep=[cream]),
                            "cream": mat("pie_cream", [(240, 228, 200), cream], 1203, weights=[35], clump=1)}, e)


def cake():
    """A whole cake for the Plate and the tray (the user, 2026-11-14, play-testing: 「あんなに料理凝ってるのに、ケーキだけは
    皿に載せられない、トレイに置こうとするとアイコンになっちゃうの、悲しいな…」): round, two layers of sponge with cream
    between them and over the top, a strawberry at each of six places round its rim - our own drawing, as the pie is."""
    sponge = [(206, 156, 78), (226, 182, 102), (240, 204, 130)]
    cream = [(232, 226, 214), (246, 242, 234), (255, 253, 248)]
    berry = [(150, 22, 30), (196, 36, 42), (232, 74, 70)]

    def side(x, y):
        return sponge[1 + (wobble(x, y, 121, 4) == 0) - (wobble(x, y, 122, 5) == 0)]

    def top(x, y):
        return cream[2 if wobble(x, y, 123, 4) else 1]
    e = blob(disc(8, 8, 4.6), 0, 5, "side", top="top", step=1)
    for k in range(6):
        a = math.radians(30 + k * 60)
        x, z = 8 + 3.2 * math.cos(a), 8 + 3.2 * math.sin(a)
        e.append(box([round(x - 0.5), 5, round(z - 0.5)], [round(x - 0.5) + 1, 6, round(z - 0.5) + 1], "berry"))
    e.append(box([7.5, 5, 7.5], [8.5, 5.75, 8.5], "berry"))
    put(M + "cake", {"side": tex("cake_side", rows_by_height([(2, side), (3, cream[1]), (4, side)], cream[1]), 1211,
                                 keep=[cream[1]]),
                     "top": tex("cake_top", top, 1212),
                     "berry": mat("cake_berry", berry, 1213, weights=[30, 45], clump=1)}, e)


# ---- meat
BONE = [(214, 204, 178), (236, 228, 206)]
STEAK = [".#####.",
         "#######",
         "#######",
         "######.",
         ".####.."]
CHOP = ["..####.",
        ".######",
        "#######",
        ".######",
        "..###.."]
LEG = ["..###..",
       ".#####.",
       "######.",
       ".#####.",
       "..###.."]


def cut_of_meat(name, rows, raw, colours, fat, mark, seed, bone=None):
    """A slab of meat an inch thick. Raw: marbled, a rim of fat along its far edge. Cooked: seared, the grill's lines across it."""
    edge = {(4 + i, 5 + j) for i, j in edge_of(rows)}

    def top(x, y):
        if (x, y) in edge and y <= 6:
            return fat
        if raw:
            return fat if (x, y) in ((6, 8), (7, 8), (8, 7), (9, 7)) else colours[1 + (wobble(x, y, seed, 4) == 0) - (wobble(x, y, seed + 1, 4) == 0)]
        return mark if (x + y) % 4 == 0 and (x, y) not in edge else colours[1 + (wobble(x, y, seed, 4) == 0) - (wobble(x, y, seed + 1, 5) == 0)]
    keep = [fat] + ([mark] if mark else [])
    textures = {"top": tex(name + "_top", top, seed, keep=keep), "side": mat(name + "_side", colours[:2] + [fat], seed + 2, weights=[30, 50], clump=2)}
    e = lump(rows, 4, 5, 1.25, "side", top="top")
    if bone:
        textures["bone"] = mat("bone", BONE, 1211, weights=[40], clump=1)
        e.append(box(bone[0], bone[1], "bone"))
    put(M + name, textures, e)


def meats():
    cut_of_meat("beef", STEAK, True, [(146, 28, 28), (188, 46, 42), (214, 78, 68)], (240, 220, 208), None, 1221)
    cut_of_meat("cooked_beef", STEAK, False, [(92, 50, 24), (126, 72, 36), (156, 98, 54)], (196, 150, 96), (60, 30, 14), 1224)
    chop_bone = ([3, 0.25, 7], [4, 1, 8])
    cut_of_meat("porkchop", CHOP, True, [(212, 128, 128), (236, 160, 156), (250, 196, 188)], (252, 238, 230), None, 1227, bone=chop_bone)
    cut_of_meat("cooked_porkchop", CHOP, False, [(166, 118, 68), (200, 152, 96), (226, 184, 128)], (240, 212, 160), (124, 80, 40), 1230, bone=chop_bone)
    leg_bone = ([10, 0.25, 7], [12.5, 1, 8])
    cut_of_meat("mutton", LEG, True, [(136, 34, 34), (174, 54, 48), (204, 84, 72)], (244, 226, 214), None, 1233, bone=leg_bone)
    cut_of_meat("cooked_mutton", LEG, False, [(100, 62, 38), (132, 86, 54), (160, 112, 74)], (200, 160, 110), (70, 40, 22), 1236, bone=leg_bone)
    # rotten flesh: a ragged piece, mould on it
    rot, mould = [(120, 60, 44), (150, 82, 56), (176, 108, 74)], (98, 124, 58)
    ragged = [".###...", "######.", "#######", ".#####.", "..###.."]
    top = tex("rotten_flesh_top", lambda x, y: mould if (x * 5 + y * 7) % 9 < 2 else rot[1 + (wobble(x, y, 121, 3) == 0) - (wobble(x, y, 122, 4) == 0)],
              1241, keep=[mould], preset=texkit.ROUGH)
    put(M + "rotten_flesh", {"top": top, "side": mat("rotten_flesh_side", rot[:2] + [mould], 1242, weights=[35, 45], clump=2)},
        lump(ragged, 4, 5, 1, "side", top="top"))


def birds():
    for name, colours, seed in (("chicken", [(222, 164, 144), (240, 194, 174), (250, 220, 204)], 1251),
                                ("cooked_chicken", [(146, 80, 28), (188, 118, 48), (220, 160, 84)], 1252)):
        skin = mat(name, colours, seed, weights=[25, 50], clump=2)
        e = [box([5.5, 0, 5.5], [10.5, 2.5, 9.5], "skin"), box([6.5, 2.5, 6], [9.5, 3.25, 9], "skin"),
             box([5, 0.25, 9], [6.5, 1.75, 11.5], "skin"), box([9.5, 0.25, 9], [11, 1.75, 11.5], "skin"),
             box([5.4, 0.6, 11.5], [6.1, 1.3, 12.25], "bone"), box([9.9, 0.6, 11.5], [10.6, 1.3, 12.25], "bone"),
             box([4.75, 0.5, 6], [5.5, 1.75, 8.5], "skin"), box([10.5, 0.5, 6], [11.25, 1.75, 8.5], "skin")]
        put(M + name, {"skin": skin, "bone": mat("bone", BONE, 1211, weights=[40], clump=1)}, e)
    for name, colours, seed in (("rabbit", [(186, 106, 96), (212, 138, 124), (234, 170, 154)], 1255),
                                ("cooked_rabbit", [(136, 80, 40), (170, 110, 60), (198, 140, 86)], 1256)):
        skin = mat(name, colours, seed, weights=[25, 50], clump=2)
        e = [box([6, 0, 5], [10, 2, 10], "skin"), box([6.5, 2, 5.5], [9.5, 2.5, 9], "skin"),
             box([5.25, 0, 8], [6, 1.5, 11], "skin"), box([10, 0, 8], [10.75, 1.5, 11], "skin"),
             box([6.5, 0, 4], [7.5, 1, 5], "skin"), box([8.5, 0, 4], [9.5, 1, 5], "skin")]
        put(M + name, {"skin": skin}, e)


# ---- fish
WHOLE_FISH = ["..#####...",
              ".#######.#",
              "##########",
              ".#######.#",
              "..#####..."]
SMALL_FISH = ["..###..",
              ".####.#",
              "#######",
              ".####.#",
              "..###.."]
EYE = (26, 22, 20)


def fish(name, back, flank, belly, seed, marks=None):
    """A fish lying on its side, head to the west: its back along the far edge, its belly the near one."""
    def top(x, y):
        i, j = x - 3, y - 5
        if (i, j) == (1, 1):
            return EYE
        if i >= 8:
            return back[0]
        if marks and (x + y) % 4 == 0 and 2 <= i <= 7 and 1 <= j <= 3:
            return marks
        row = back if j <= 1 else belly if j >= 3 else flank
        return row[1 if wobble(x, y, seed, 3) else 0]
    e = lump(WHOLE_FISH, 3, 5, 1.25, "side", top="top") + [box([5, 1.25, 6], [9, 1.75, 9], "side", top="top")]
    keep = [EYE] + ([marks] if marks else [])
    put(M + name, {"top": tex(name + "_top", top, seed, keep=keep),
                   "side": mat(name + "_side", [flank[0], flank[1], belly[1]], seed + 1, weights=[30, 40], clump=2)}, e)


def fishes():
    fish("cod", [(118, 96, 66), (146, 120, 84)], [(176, 148, 110), (198, 172, 134)], [(214, 196, 162), (232, 218, 190)], 1261)
    fish("cooked_cod", [(190, 166, 126), (212, 190, 150)], [(226, 212, 182), (240, 230, 206)], [(238, 230, 210), (250, 244, 230)], 1264,
         marks=(150, 104, 56))
    fish("salmon", [(90, 104, 122), (118, 132, 150)], [(196, 74, 70), (222, 104, 92)], [(206, 200, 198), (228, 224, 220)], 1267)
    fish("cooked_salmon", [(168, 88, 56), (194, 110, 70)], [(222, 128, 86), (240, 158, 112)], [(240, 176, 132), (250, 204, 166)], 1270,
         marks=(136, 70, 40))
    # the tropical fish: small, orange, banded white
    orange, white = [(226, 108, 20), (244, 138, 36)], (248, 244, 236)

    def top(x, y):
        i, j = x - 5, y - 5
        if (i, j) == (1, 1):
            return EYE
        return white if i in (2, 4) else orange[1 if wobble(x, y, 131, 3) else 0]
    put(M + "tropical_fish", {"top": tex("tropical_fish_top", top, 1281, keep=[EYE, white]),
                              "side": mat("tropical_fish_side", orange + [white], 1282, weights=[35, 45], clump=1)},
        lump(SMALL_FISH, 5, 5, 1.25, "side", top="top"))
    # the pufferfish: a ball of spines, yellow over a white belly
    yellow, pale = [(200, 154, 26), (234, 194, 54), (250, 224, 110)], (244, 240, 222)
    side = tex("pufferfish_side", rows_by_height([(1, pale)], lambda x, y: EYE if (x, y) in ((6, 13), (9, 13)) else
                                                 yellow[0] if (x * 3 + y) % 5 == 0 else yellow[1 + (wobble(x, y, 135, 3) == 0)]), 1291, keep=[EYE, pale])
    topt = tex("pufferfish_top", lambda x, y: yellow[0] if (x * 3 + y * 2) % 5 == 0 else yellow[1 + (wobble(x, y, 136, 3) == 0)], 1292)
    spine = mat("pufferfish_spine", [yellow[2], pale], 1293, weights=[50], clump=1)
    e = [box([6, 0, 6], [10, 3, 10], "side", top="top"), box([6.5, 3, 6.5], [9.5, 3.5, 9.5], "top"), box([10, 1, 7.5], [11.5, 2, 8.5], "side")]
    for x, z in ((6.75, 6.75), (8.75, 6.75), (6.75, 8.75), (8.75, 8.75)):
        e.append(box([x, 3.5, z], [x + 0.5, 4, z + 0.5], "spine"))
    for x, z in ((5.5, 6.75), (5.5, 8.75), (6.75, 5.5), (8.75, 5.5), (6.75, 10), (8.75, 10)):
        e.append(box([x, 1.5, z], [x + 0.5, 2, z + 0.5], "spine"))
    put(M + "pufferfish", {"side": side, "top": topt, "spine": spine}, e)


# ---- the odd ones
def spider_eye():
    red, pupil, glint = [(116, 10, 22), (166, 20, 36), (206, 48, 60)], (24, 6, 10), (250, 190, 190)

    def top(x, y):
        if (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
            return glint if (x, y) == (7, 7) else pupil
        return red[1 + (wobble(x, y, 141, 3) == 0)]
    e = blob(disc(8, 8, 1.45), 0, 0.5, "skin", step=1) + blob(disc(8, 8, 2.1), 0.5, 2.5, "skin", step=1) \
        + [box([7, 2.5, 7], [9, 3, 9], "skin", top="top"), box([10, 0, 7.5], [12, 0.5, 8.5], "skin")]
    put(M + "spider_eye", {"skin": mat("spider_eye", red, 1301, weights=[30, 50], clump=1),
                           "top": tex("spider_eye_top", top, 1302, keep=[pupil, glint])}, e)


def main():
    apples()
    melon_slice()
    berries()
    chorus_fruit()
    carrots()
    potatoes()
    beetroot()
    dried_kelp()
    bread()
    cookie()
    pumpkin_pie()
    cake()
    meats()
    birds()
    fishes()
    spider_eye()
