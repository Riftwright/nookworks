"""Every food as a small model of itself, set out on a Plate or a Wooden Tray - trial of the cooking review, 2026-11-14
(the user: ほとんどの料理はアイコンが載るだけで、正直かっこ悪い ... 「ケーキが立体なのにパンプキンパイが立体じゃない」
みたいな不整合が気になる).

This file: the dishes in bowls (ours and vanilla's stews, one bowl and ten fillings) and the rest of our own food.
gen_dishes_vanilla.py draws vanilla's food, gen_dishes_cooking.py the pot and pan and what lies in them. Original pixel
art in the manner of gen_tableware.py's four dishes (round in whole pixels), which are filed here with the rest:
models/dish/<namespace>/<item>.json, found by DishModels by their files.

Run from the repo root:  python tools/textures/gen_dishes.py   (it runs the other two as well)
"""
import texkit
import gen_tableware as tw
from dishkit import blob, box, cells, disc, mask, mat, put, rows_by_height, save_all, tex, turned, wobble

T = "nookworks:block/"  # gen_tableware's own pictures, shared

# ---- bowls
WOOD = [(84, 56, 30), (110, 76, 42), (136, 98, 56), (160, 122, 76)]
WOOD_SIDE = tex("bowl_wood_side", rows_by_height([(1, WOOD[0]), (3, WOOD[1])], WOOD[2]), 901)
WOOD_RIM = mat("bowl_wood_rim", [WOOD[2], WOOD[3]], 902, weights=[40])
GLAZE = tw.GLAZE
# plain white china, as the Corn Potage's icon (the user, 2026-11-21: its blue rim and band dropped to match)
WHITE_SIDE = tex("bowl_white_side", rows_by_height([(1, GLAZE[1])], GLAZE[3]), 903)
WHITE_RIM = mat("bowl_white_rim", [GLAZE[2], GLAZE[3]], 904, weights=[35])


def bowl(top, mound=0, side=WOOD_SIDE, rim=WOOD_RIM):
    """A round bowl on a foot, its filling under the rim - heaped over it for rice and what is fried."""
    e = blob(disc(8, 8, 2.5), 0, 1, "side", step=1) + blob(disc(8, 8, 4), 1, 4, "side", top="top", step=1) \
        + blob(disc(8, 8, 4, 3), 4, 4.5, "rim", step=1)
    if mound >= 1:
        e += blob(disc(8, 8, 2.6), 4, 4.75, "top", step=1)
    if mound >= 2:
        e += blob(disc(8, 8, 1.45), 4.75, 5.5, "top", step=1)
    return {"top": top, "side": side, "rim": rim}, e


def soup(name, broth, bits, seed):
    """A bowl's filling from above: the broth in its three shades, and what floats in it - bits is {colour: [(x, y)...]}."""
    at = {p: c for c, places in bits.items() for p in places}

    def paint(x, y):
        if (x, y) in at:
            return at[(x, y)]
        return broth[1 + (wobble(x, y, seed, 4) == 0) - (wobble(x, y, seed + 1, 5) == 0)]
    return tex(name, paint, seed, keep=list(bits))


CARROT_BIT, POTATO_BIT, GREEN_BIT, BEET_BIT = (238, 146, 46), (240, 220, 152), (96, 168, 60), (138, 28, 58)
SOUP_CARROT, SOUP_POTATO = (225, 117, 29), (245, 234, 186)  # the Vegetable Soup icon's own
RICE = [(222, 220, 210), (240, 238, 230), (252, 251, 246)]
FRIED_RICE = [(196, 148, 58), (222, 178, 84), (238, 204, 116)]
VEG_SOUP = [(212, 164, 76), (221, 181, 93), (233, 193, 105)]  # a clear golden broth, as the icon the user drew (2026-11-21)
CREAM = [(230, 220, 194), (243, 235, 213), (252, 247, 232)]
POTAGE = [(230, 190, 76), (243, 208, 98), (250, 224, 126)]


def rice_top():
    return mat("fill_rice", RICE, 911, weights=[18, 50], clump=1)


def fried_rice_top():
    return soup("fill_fried_rice", FRIED_RICE, {(250, 226, 104): [(6, 6), (9, 8), (7, 9), (8, 5)], GREEN_BIT: [(8, 7), (6, 8), (9, 6)],
                                                 (226, 124, 70): [(7, 7), (9, 9), (6, 5), (10, 7)]}, 912)


def stir_fry_top():
    veg = [(120, 172, 70), (176, 214, 110), (238, 146, 46), (240, 224, 170), (150, 100, 50)]
    return mat("fill_stir_fry", veg, 913, weights=[26, 24, 20, 20], clump=2, preset=texkit.PLANT)


def veg_soup_top():
    # carrot and potato in the clear broth, as the icon shows them (no beet or green: the user's consomme has none)
    return soup("fill_vegetable_soup", VEG_SOUP, {SOUP_CARROT: [(6, 6), (9, 8), (7, 10), (10, 6)],
                                                  SOUP_POTATO: [(8, 6), (6, 8), (9, 10), (8, 9)]}, 914)


def cream_stew_top():
    return soup("fill_cream_stew", CREAM, {CARROT_BIT: [(6, 6), (9, 7), (7, 9)], GREEN_BIT: [(8, 6), (6, 8), (9, 9)],
                                           (226, 190, 146): [(7, 7), (8, 8), (10, 8)], (246, 226, 160): [(9, 5), (6, 10)]}, 915)


def potage_top():
    return soup("fill_corn_potage", POTAGE, {(253, 247, 224): [(6, 8), (7, 7), (8, 7), (9, 6), (9, 9), (8, 9)],
                                             GREEN_BIT: [(7, 9), (9, 8)], (206, 150, 78): [(6, 6), (10, 8)]}, 916)


def plated():
    """Fried rice and stir-fried vegetables served on a plate (the user, 2026-11-14: チャーハンや野菜炒めは、皿に盛るのが
    普通): the food alone, for the plate is the Plate block's, or drawn under it on a tray. A dome of fried rice as a ladle
    turns it out, a pinch of red pickled ginger beside it; a low wide heap of stir-fry."""
    dome = blob(disc(8, 8, 3.6), 0, 1.25, "rice", step=1) + blob(disc(8, 8, 2.6), 1.25, 2.25, "rice", step=1) \
        + blob(disc(8, 8, 1.45), 2.25, 2.9, "rice", step=1) + [box([11.5, 0, 10.25], [12.75, 0.6, 11.75], "ginger")]
    put("dish/nookworks/plated_fried_rice", {"rice": fried_rice_top(), "ginger": mat("red_ginger", [(196, 40, 52), (226, 70, 76)], 919,
                                                                                    weights=[45], clump=1)}, dome)
    heap = blob(disc(8, 8, 3.6), 0, 0.9, "veg", step=1) + blob(disc(8, 8, 2.6), 0.9, 1.6, "veg", step=1) + [box([7, 1.6, 7], [9, 2.1, 9], "veg")]
    put("dish/nookworks/plated_stir_fried_vegetables", {"veg": stir_fry_top()}, heap)


def bowls():
    put("dish/nookworks/cooked_rice", *bowl(rice_top(), mound=2))
    put("dish/nookworks/fried_rice", *bowl(fried_rice_top(), mound=2))
    put("dish/nookworks/stir_fried_vegetables", *bowl(stir_fry_top(), mound=1))
    plated()
    put("dish/nookworks/vegetable_soup", *bowl(veg_soup_top()))
    put("dish/nookworks/cream_stew", *bowl(cream_stew_top()))
    put("dish/nookworks/corn_potage", *bowl(potage_top(), side=WHITE_SIDE, rim=WHITE_RIM))
    # vanilla's stews: the same bowl
    put("dish/minecraft/mushroom_stew", *bowl(soup("fill_mushroom_stew", [(166, 124, 92), (188, 146, 110), (206, 166, 130)],
                                                    {(116, 80, 60): [(6, 6), (9, 7), (7, 9)], (234, 224, 202): [(8, 6), (6, 8), (9, 9)],
                                                     (196, 66, 54): [(8, 8), (10, 7)]}, 917)))
    put("dish/minecraft/beetroot_soup", *bowl(soup("fill_beetroot_soup", [(118, 14, 30), (150, 24, 42), (178, 42, 58)],
                                                    {(210, 88, 100): [(6, 7), (9, 6), (8, 9), (7, 5)], (244, 226, 226): [(8, 7), (9, 8)]}, 918)))
    put("dish/minecraft/rabbit_stew", *bowl(soup("fill_rabbit_stew", [(146, 92, 48), (170, 114, 62), (192, 138, 82)],
                                                  {CARROT_BIT: [(6, 6), (9, 8)], POTATO_BIT: [(8, 6), (7, 9), (10, 7)],
                                                   (212, 168, 134): [(7, 7), (9, 6), (6, 9), (8, 10)]}, 919)))
    put("dish/minecraft/suspicious_stew", *bowl(soup("fill_suspicious_stew", [(104, 110, 58), (126, 132, 70), (148, 154, 88)],
                                                      {(216, 62, 62): [(6, 6), (8, 9)], (92, 124, 224): [(9, 7)], (240, 240, 230): [(7, 9), (9, 5)],
                                                       (240, 210, 60): [(9, 9), (6, 8)], (120, 84, 60): [(8, 6)]}, 920)))


# ---- our own food
EGG_WHITE = [(232, 229, 216), (246, 244, 236), (255, 255, 255)]
YOLK = [(232, 150, 26), (250, 186, 46), (255, 214, 92)]
CRISP = [(214, 180, 120), (234, 214, 170)]  # the edge fried crisp, and the white just inside it
FRIED_EGG = ["..####..",
             ".######.",
             "########",
             "########",
             ".#######",
             ".######.",
             "..####.."]


def edge_of(rows):
    """The pixels of a text shape that have clear beside them."""
    def on(i, j):
        return 0 <= j < len(rows) and 0 <= i < len(rows[j]) and rows[j][i] != "."
    return {(i, j) for j in range(len(rows)) for i in range(len(rows[j]))
            if on(i, j) and not (on(i - 1, j) and on(i + 1, j) and on(i, j - 1) and on(i, j + 1))}


def fried_egg(path, white=EGG_WHITE, crisp=CRISP, name="fried_egg"):
    edge = {(4 + i, 4 + j) for i, j in edge_of(FRIED_EGG)}

    def top(x, y):
        if (x, y) in edge:
            return crisp[wobble(x, y, 31, 3) != 0]
        return white[2] if abs(x - 8) + abs(y - 7) <= 2 else white[1 + (wobble(x, y, 32, 5) == 0) - (wobble(x, y, 33, 6) == 0)]
    e = blob(mask(FRIED_EGG, 4, 4), 0, 0.5, "white", top="top", step=1)
    e.append(box([7, 0.5, 6], [9, 1.25, 8], "yolk", top="yolk_top"))
    put(path, {"top": tex(name + "_top", top, 931, keep=list(crisp)), "white": mat(name + "_white", list(crisp), 932, weights=[40], clump=1),
               "yolk": mat("yolk", YOLK[:2], 933, weights=[45], clump=1),
               "yolk_top": tex("yolk_top", lambda x, y: YOLK[2] if (x, y) == (7, 6) else YOLK[1], 934, keep=[YOLK[2]])}, e)


OVAL = [".##.", "####", "####", "####", ".##."]
BOILED = [(218, 214, 200), (238, 235, 224), (250, 249, 244)]


def boiled_egg():
    """One egg cut in two, the halves lying cut side up, side by side. (It was that and a whole egg beside them, spread
    over the plate: two eggs to the one, and no room for another - the user, 2026-11-14, on how many of each a plate
    holds.)"""
    halves = ((4, 6), (8, 5))

    def cut(x, y):
        for x0, z0 in halves:
            if x0 + 1 <= x <= x0 + 2 and z0 + 1 <= y <= z0 + 3:
                return YOLK[2] if (x, y) == (x0 + 1, z0 + 1) else YOLK[1]
        return BOILED[2]
    side = rows_by_height([(1, BOILED[0])], BOILED[1])
    e = []
    for x0, z0 in halves:
        e += blob(mask(OVAL, x0, z0), 0, 1.25, "white", top="cut", step=1)
    put("dish/nookworks/boiled_egg", {"white": tex("boiled_white", side, 941), "cut": tex("boiled_cut", cut, 942, keep=YOLK[1:])}, e)


TAMAGO = [(224, 166, 46), (243, 194, 70), (252, 216, 106)]
TAMAGO_CUT = [(250, 222, 120), (255, 238, 160)]


SWIRL = ["LLL", "LDD", "LDL"]  # the roll's layers on a cut face, in the picture's top left corner


def tamagoyaki_textures():
    def cut(x, y):
        return TAMAGO[0] if x < 3 and y < 3 and SWIRL[y][x] == "D" else TAMAGO_CUT[(x + y) % 2]
    return {"skin": mat("tamagoyaki_skin", TAMAGO, 951, weights=[22, 50], clump=2), "cut": tex("tamagoyaki_cut", cut, 952, raw=True)}


def tamagoyaki_slice(x, z):
    """A slice of the roll, three pixels square and two thick, its cut faces north and south."""
    e = box([x, 0, z], [x + 3, 3, z + 2], "skin")
    for face in ("north", "south"):
        e["faces"][face] = {"texture": "#cut", "uv": [0, 0, 3, 3]}
    return e


def tamagoyaki():
    put("dish/nookworks/tamagoyaki", tamagoyaki_textures(),
        [tamagoyaki_slice(3, 7.5), tamagoyaki_slice(6.5, 7), tamagoyaki_slice(10, 7.5)])


NORI = [(18, 28, 22), (28, 42, 32), (40, 58, 44)]
SALMON_FLAKE = [(214, 104, 80), (236, 138, 106)]


def onigiri(path, salmon):
    rice = tex("onigiri_rice", rows_by_height([(1, (200, 198, 188))], lambda x, y: RICE[1 + (wobble(x, y, 961, 3) == 0) - (wobble(x, y, 962, 6) == 0)]), 961)
    textures = {"rice": rice, "nori": mat("nori", NORI, 962, weights=[30, 50], clump=2)}
    e = [box([5, 0, 6.5], [11, 1.5, 9.5], "rice"), box([6, 1.5, 6.5], [10, 3, 9.5], "rice"), box([7, 3, 7], [9, 4.5, 9.5], "rice"),
         box([6, 0, 6.35], [10, 2.25, 9.65], "nori")]
    if salmon:
        textures["salmon"] = mat("salmon_flake", SALMON_FLAKE, 963, weights=[45], clump=1)
        e.append(box([7.5, 4.25, 7.5], [8.5, 4.5, 8.5], "salmon"))
        e.append(box([7.5, 3.25, 9.5], [8.5, 4, 9.65], "salmon"))
    # leant back a little on the plate, so its three-cornered face and the nori on it look up at whoever is served. It
    # leans about its far bottom edge, which stays on the plate (about the near one, the far side went 1.3 px down into
    # the plate: the user, 2026-11-14, おにぎりがお皿に埋まって見えてる), and the near side it lifts stands on a foot of
    # rice with the nori carried down over it, stepped to stay under the leaning underside.
    e = [turned(piece, -22.5, "x", [8, 0, 6.35]) for piece in e]
    for x0, x1, key, out in ((5, 6, "rice", 0), (6, 10, "nori", 0.15), (10, 11, "rice", 0)):  # side by side: no two faces in one plane
        e += [box([x0, 0, 7.5], [x1, 0.4, 9.2 + out], key), box([x0, 0.4, 8.5], [x1, 0.8, 9.2 + out], key),
              box([max(x0, 5.5), 0.8, 9], [min(x1, 10.5), 1.2, 9.2 + out], key)]
    put(path, textures, e)


FISH = ["..####...#",
        ".######.##",
        "##########",
        ".######.##",
        "..####...#"]


def fried_fish():
    e = blob(mask(FISH, 3, 5), 0, 1.25, "crumb", step=1) + [box([4, 1.25, 6], [9, 1.75, 9], "crumb")]
    e.append(box([9.5, 0, 10.5], [12, 1.25, 12], "lemon", top="lemon_cut"))
    put("dish/nookworks/fried_fish", {"crumb": T + "dish_katsu_crumb", "lemon": T + "dish_lemon", "lemon_cut": T + "dish_lemon_cut"}, e)


FRY = [(210, 156, 46), (236, 190, 80), (248, 214, 122)]


def fries(e, key="fry"):
    """A pile of chips in three layers, each layer's laid one way so none runs through another."""
    for z, x0, x1 in ((6.5, 4, 10.5), (8, 5, 12), (9.5, 4.5, 11)):
        e.append(box([x0, 0, z - 0.375], [x1, 0.75, z + 0.375], key))
    for z, x0, x1 in ((6.6, 5.5, 11), (8, 4.5, 11.5), (9.4, 5, 10.5)):
        e.append(turned(box([x0, 0.75, z - 0.375], [x1, 1.5, z + 0.375], key), 45, "y", [8, 0.75, 8]))
    for x, z0, z1 in ((7, 5, 10.5), (9, 5.5, 11)):
        e.append(box([x - 0.375, 1.5, z0], [x + 0.375, 2.25, z1], key))
    return e


def french_fries():
    e = fries([]) + [box([10.5, 0, 10.5], [12.5, 0.5, 12.5], "ketchup")]
    put("dish/nookworks/french_fries", {"fry": mat("fry", FRY, 971, weights=[20, 50], clump=1),
                                        "ketchup": mat("ketchup", [(170, 22, 20), (200, 36, 28)], 972, weights=[40], clump=1)}, e)


PANCAKE = [(170, 106, 46), (194, 130, 62), (214, 152, 80)]
PANCAKE_EDGE = [(206, 160, 90), (228, 188, 118)]
SYRUP = (146, 70, 18)
BUTTER = [(244, 220, 120), (252, 238, 160)]


def pancake_textures():
    def top(x, y):
        d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
        if (x, y) in ((6, 9), (6, 10), (5, 10), (9, 9), (10, 8), (10, 9), (9, 6), (8, 10), (7, 10), (11, 8)):
            return SYRUP
        return PANCAKE_EDGE[1] if d > 3.3 else PANCAKE[1 + (wobble(x, y, 41, 4) == 0) - (wobble(x, y, 42, 5) == 0)]
    side = rows_by_height([(1, PANCAKE_EDGE[1]), (2, PANCAKE_EDGE[0])], PANCAKE_EDGE[1])
    return {"side": tex("pancake_side", side, 981), "top": tex("pancake_top", top, 982, keep=[SYRUP]),
            "butter": mat("butter", BUTTER, 983, weights=[40], clump=1)}


def pancake():
    e = []
    for y in range(3):
        e += blob(disc(8, 8, 3.6), y, y + 1, "side", top="top", step=1)
    e.append(box([7, 3, 7], [9, 3.5, 9], "butter"))
    put("dish/nookworks/pancake", pancake_textures(), e)


KERNEL = [(232, 180, 36), (246, 204, 62), (253, 226, 104)]
CHAR = (118, 68, 22)
HUSK = [(74, 128, 44), (104, 160, 60), (140, 190, 84)]


def corn_texture(name, seed, charred):
    def paint(x, y):
        if charred and (x * 2 + y * 3) % 7 == 0:
            return CHAR
        return KERNEL[(x + y % 2) % 2 + ((x + y * 3) % 5 == 0)]
    return tex(name, paint, seed, keep=[CHAR])


def grilled_corn():
    e = [box([3.5, 0, 6.5], [10.5, 2, 9.5], "corn"), box([4, 2, 7], [10, 2.75, 9], "corn"), box([10.5, 0.75, 7.5], [13, 1.5, 8.5], "stick")]
    put("dish/nookworks/grilled_corn", {"corn": corn_texture("corn_grilled", 991, True), "stick": "minecraft:block/stripped_bamboo_block"}, e)


def corn():
    e = [box([5.5, 0, 6.5], [12, 2, 9.5], "corn"), box([6, 2, 7], [11.5, 2.75, 9], "corn"),
         box([3, 0.05, 6.25], [6.5, 2.25, 9.75], "husk"), box([6.5, 0.05, 6.25], [8.5, 1.25, 7], "husk"),
         box([6.5, 0.05, 9], [8.5, 1.25, 9.75], "husk"),
         box([2, 0.5, 7.5], [3, 1.5, 8.5], "husk")]
    put("dish/nookworks/corn", {"corn": corn_texture("corn_raw", 992, False),
                                "husk": mat("corn_husk", HUSK, 993, weights=[25, 50], clump=2, preset=texkit.PLANT)}, e)


ONION_SKIN = [(170, 100, 36), (204, 134, 52), (228, 166, 80), (244, 196, 116)]


def onion():
    # the papery skin's lines run from root to tip: a darker one every third pixel round it
    skin = tex("onion_skin", lambda x, y: ONION_SKIN[0 if x % 3 == 0 else 2 if (x + y) % 5 else 3], 1001)
    e = blob(disc(8, 8, 2.1), 0, 0.5, "skin", step=1) + blob(disc(8, 8, 2.6), 0.5, 3, "skin", step=1) \
        + blob(disc(8, 8, 2.1), 3, 3.75, "skin", step=1) + [box([7, 3.75, 7], [9, 4.5, 9], "skin"), box([7.5, 4.5, 7.5], [8.5, 5.75, 8.5], "tip")]
    put("dish/nookworks/onion", {"skin": skin, "tip": mat("onion_tip", [(176, 140, 82), (204, 170, 108)], 1002, weights=[45], clump=1)}, e)


def pickles(path, name, skin, cut, core, n, width, step, height, depth, seed):
    """A pickle sliced on the slant, its slices side by side with a gap for the cut faces."""
    def cut_face(x, y):
        return core if (x in (7, 8) and y == 14) else cut[wobble(x, y, seed, 3) == 0]
    textures = {"skin": mat(name + "_skin", skin, seed, weights=[40], clump=2), "cut": tex(name + "_cut", cut_face, seed + 1, keep=[core])}
    x0 = 8 - ((n - 1) * step + width) / 2
    e = []
    for i in range(n):
        z = 8 - depth / 2 + (i % 2) * 0.5 - 0.25
        e.append(box([x0 + i * step, 0, z], [x0 + i * step + width, height, z + depth], "skin", cut="cut"))
    put(path, textures, e)


DRIED = ["..#####...",
         ".#######.#",
         ".#######.#",
         "##########",
         ".#######.#",
         ".#######.#",
         "..#####..."]
DRIED_FISH = [(128, 84, 44), (176, 124, 68), (222, 184, 128), (240, 212, 160)]


def dried_fish():
    """A fish split and opened flat to dry: the pale flesh either side of its backbone, the skin's edge round it."""
    edge = {(3 + i, 4 + j) for i, j in edge_of(DRIED)}

    def top(x, y):
        i, j = x - 3, y - 4
        if (i, j) == (1, 2):
            return (30, 24, 20)  # its eye
        if i >= 8:
            return DRIED_FISH[0]  # the tail
        if j == 3:
            return DRIED_FISH[1] if i > 0 else DRIED_FISH[0]  # the backbone
        if (x, y) in edge or i <= 1:
            return DRIED_FISH[1 if i > 1 else 0]
        return DRIED_FISH[3 if (i + j * 2) % 4 == 0 else 2]
    e = blob(mask(DRIED, 3, 4), 0, 0.75, "skin", top="top", step=1)
    put("dish/nookworks/dried_fish", {"top": tex("dried_fish_top", top, 1011, keep=[(30, 24, 20)]),
                                      "skin": mat("dried_fish_skin", DRIED_FISH[:2], 1012, weights=[45], clump=2)}, e)


def ours():
    fried_egg("dish/nookworks/fried_egg")
    boiled_egg()
    tamagoyaki()
    onigiri("dish/nookworks/salt_onigiri", False)
    onigiri("dish/nookworks/salmon_onigiri", True)
    fried_fish()
    french_fries()
    pancake()
    grilled_corn()
    corn()
    onion()
    pickles("dish/nookworks/carrot_nukazuke", "pickle_carrot", [(200, 98, 22), (230, 130, 34)], [(244, 166, 66), (250, 186, 96)],
            (253, 208, 130), 5, 1.2, 1.7, 1.75, 2, 1021)
    pickles("dish/nookworks/beetroot_nukazuke", "pickle_beet", [(112, 18, 52), (146, 28, 68)], [(186, 46, 92), (206, 70, 112)],
            (232, 124, 152), 4, 1.5, 2.1, 2.25, 3, 1023)
    dried_fish()
    # the four that were dishes already (gen_tableware.py), filed with the rest
    old = tw.models()
    for name in ("udon", "tonkatsu", "karaage", "donut"):
        m = old[f"block/dish_{name}"]
        put(f"dish/nookworks/{name}", {k: v for k, v in m["textures"].items() if k != "particle"}, m["elements"])


def main():
    bowls()
    ours()
    import gen_dishes_vanilla
    import gen_dishes_cooking
    gen_dishes_vanilla.main()
    gen_dishes_cooking.main()
    save_all()


if __name__ == "__main__":
    main()
