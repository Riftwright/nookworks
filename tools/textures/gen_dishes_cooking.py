"""The pot and the pan and what lies in them - trial of the cooking review, 2026-11-14.

- The Iron Pot made hollow (it was a solid block with a rim two pixels deep, its water a face on top): a floor, walls a
  pixel thick, room for the water to stand at the height of how much there is. Its water, oil and broth are models of
  their own now, drawn by IronPotRenderer at that height (block/pot_water, pot_oil, pot_broth).
- The Frying Pan: a shallow round pan, a wooden grip on its handle (block/frying_pan[_on_grill], block/pan_oil).
- What lies in them, found by DishModels by its files: models/dish_in_pot/<namespace>/<item>.json and
  models/dish_in_pan/... - an ingredient as a piece of itself, the dish waiting as itself.
- The icons of the four things got ready at the crafting table (Coated Chicken, Coated Fish, Doughnut Dough, Beaten Eggs).

Run through gen_dishes.py.
"""
import json

from PIL import Image

import hand_finished
import itemkit
import texkit
import gen_tableware as tw
from dishkit import ASSETS, blob, box, disc, mask, mat, put, rows_by_height, shifted, tex, wobble

T = "nookworks:block/"
D = "nookworks:block/dish/"
IRON = T + "black_iron"


# ---- the vessels
def octagon(cx, cz, grow=0):
    """inside() of the pot's plan: twelve pixels across (ten with grow -1), its corners cut."""
    def inside(x, z):
        ax, az = abs(x - cx), abs(z - cz)
        return (ax < 6 + grow and az < 4 + grow) or (ax < 4 + grow and az < 6 + grow) or (ax < 5 + grow and az < 5 + grow)
    return inside


def ring(cx, cz):
    outer, inner = octagon(cx, cz), octagon(cx, cz, -1)
    return lambda x, z: outer(x, z) and not inner(x, z)


def flat(inside, key, tint=None):
    """Faces lying at y 0 over the cells inside(x, z) takes, looking up: a liquid's surface."""
    out = []
    for e in blob(inside, 0, 0, key, step=1):
        face = {"texture": "#" + key, "uv": [e["from"][0], e["from"][2], e["to"][0], e["to"][2]]}
        if tint is not None:
            face["tintindex"] = tint
        out.append({"from": e["from"], "to": e["to"], "faces": {"up": face}})
    return out


GUI = {"gui": {"rotation": [30, 225, 0], "translation": [0, 3, 0], "scale": [0.9, 0.9, 0.9]}}


def iron_pot(on_grill):
    d = 1 if on_grill else 0
    e = blob(octagon(8, 8, -1), d, d + 1, "iron", step=1)            # the foot, drawn in a little
    e += blob(octagon(8, 8), d + 1, d + 2, "iron", step=1)            # the floor
    e += blob(ring(8, 8), d + 2, d + 7, "iron", step=1)               # the walls
    e += [box([0.5, d + 6, 6], [2, d + 7, 10], "iron"), box([14, d + 6, 6], [15.5, d + 7, 10], "iron")]  # the bails
    return {"parent": "minecraft:block/block", "textures": {"particle": IRON, "iron": IRON}, "elements": e, "display": GUI}


def frying_pan(on_grill):
    """Its bowl a pixel off the middle of the block, away from the handle at its north."""  # the handle points at FACING
    d = 1 if on_grill else 0
    e = blob(octagon(8, 9), d, d + 1, "iron", step=1) + blob(ring(8, 9), d + 1, d + 3, "iron", step=1)
    # the handle (the user, 2026-11-14: 取っ手が短すぎる…持ちにくそう): as long as a pan's is - a thin neck of iron from the
    # rim, then six pixels of wooden grip, rising a little as it goes. It reaches some four pixels past the block's
    # edge, as a pan's handle stands out over the edge of a stove.
    lift = {"angle": 22.5, "axis": "x", "origin": [8, d + 2.5, 3]}
    neck = {"from": [7.5, d + 2.25, 1], "to": [8.5, d + 2.75, 3], "rotation": dict(lift), "faces": {
        face: {"texture": "#iron", "uv": uv} for face, uv in (("up", [7, 1, 8, 3]), ("down", [7, 1, 8, 3]), ("east", [1, 6, 3, 6.5]), ("west", [1, 6, 3, 6.5]),
                                                       ("north", [7, 6, 8, 6.5]), ("south", [7, 6, 8, 6.5]))}}
    grip = {"from": [7, d + 2, -5], "to": [9, d + 3, 1], "rotation": dict(lift), "faces": {
        "up": {"texture": "#grip", "uv": [7, 0, 9, 6]}, "down": {"texture": "#grip", "uv": [7, 0, 9, 6]},
        "east": {"texture": "#grip", "uv": [0, 6, 6, 7]}, "west": {"texture": "#grip", "uv": [0, 6, 6, 7]},
        "north": {"texture": "#grip", "uv": [7, 6, 9, 7]}, "south": {"texture": "#grip", "uv": [7, 6, 9, 7]}}}
    e += [neck, grip]
    return {"parent": "minecraft:block/block", "textures": {"particle": IRON, "iron": IRON, "grip": "minecraft:block/stripped_spruce_log"},
            "elements": e, "display": {"gui": {"rotation": [30, 225, 0], "translation": [-1.75, 4, 0], "scale": [0.86, 0.86, 0.86]}}}


def facing_parts(model, grill):
    parts = []
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": model}
        if y:
            v["y"] = y
        parts.append({"when": {"facing": facing, "on_grill": grill}, "apply": v})
    return parts


def vessels():
    for block, model in (("iron_pot", iron_pot), ("frying_pan", frying_pan)):
        tw.write(ASSETS / f"models/block/{block}.json", model(False))
        tw.write(ASSETS / f"models/block/{block}_on_grill.json", model(True))
        parts = [{"when": {"on_grill": "true"}, "apply": {"model": "nookworks:block/grill"}}]
        parts += facing_parts(f"nookworks:block/{block}", "false") + facing_parts(f"nookworks:block/{block}_on_grill", "true")
        tw.write(ASSETS / f"blockstates/{block}.json", {"multipart": parts})
        tw.write(ASSETS / f"items/{block}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:block/{block}"}})
    for gone in ("iron_pot_water", "iron_pot_oil", "iron_pot_water_on_grill", "iron_pot_oil_on_grill"):
        (ASSETS / f"models/block/{gone}.json").unlink(missing_ok=True)  # the liquid is the renderer's now
    # the liquids: the pot's inside from above, at y 0 - the renderer lifts each to its height
    inner = octagon(8, 8, -1)

    def broth(x, y):
        d = max(abs(x - 7.5), abs(y - 7.5))
        return (214, 212, 204) if d > 4 else (250, 249, 244) if (x + y * 2) % 7 == 0 else (236, 234, 226)

    def film(x, y):  # a sheen on the iron: barely there, a streak of light on it here and there
        return (255, 236, 160, 110) if (x, y) in ((5, 6), (6, 5), (7, 5), (10, 9), (10, 10), (9, 11)) else (240, 196, 70, 26)
    put("block/pot_water", {"water": "minecraft:block/water_still"}, flat(inner, "water", tint=0), translucent=True)
    put("block/pot_oil", {"oil": T + "vegetable_oil_still"}, flat(inner, "oil"))
    put("block/pot_broth", {"broth": tex("pot_broth", broth, 0, raw=True)}, flat(inner, "broth", tint=0))
    put("block/pan_oil", {"oil": tex("pan_oil", film, 0, raw=True)}, flat(inner, "oil"), translucent=True)


# ---- what lies in the pot
POT = "dish_in_pot/"
PAN = "dish_in_pan/"
BREADED = [(206, 178, 124), (226, 202, 150), (242, 226, 184)]
FLOURED = [(226, 208, 170), (240, 228, 198), (250, 244, 224)]
DOUGH = [(226, 200, 150), (242, 222, 180), (250, 238, 208)]
NOODLE = tw.NOODLE
EGG_SHAPE = [".#.", "###", "###", ".#."]
FILLET = ["..####.", "#######", "..####."]


def chunk(paths, name, colours, seed, size=(2, 1.5, 2)):
    """A piece cut for the pot: a small block of the thing's own colours."""
    w, h, dz = size
    e = [box([8 - w / 2, 0, 8 - dz / 2], [8 + w / 2, h, 8 + dz / 2], "c")]
    for path in paths:
        put(path, {"c": mat("piece_" + name, colours, seed, weights=[100 // len(colours)] * (len(colours) - 1), clump=1)}, e)


def egg(paths, name, colours, seed):
    side = tex("piece_" + name, rows_by_height([(1, colours[0])], colours[1]), seed)
    e = blob(mask(EGG_SHAPE, 6.5, 6), 0, 1.5, "s", step=1, area=(0.5, 15.5, 0, 16)) + [box([7.5, 1.5, 7], [8.5, 2.1, 9], "s")]
    for path in paths:
        put(path, {"s": side}, e)


def scatter(name, broth, bits, seed, every=7):
    """A pot's whole surface: the broth, and a piece of something every few pixels."""
    def paint(x, y):
        if wobble(x, y, seed, every) == 0 and (x + y) % 2 == 0:
            return bits[wobble(x, y, seed + 3, len(bits))]
        return broth[1 + (wobble(x, y, seed + 1, 4) == 0) - (wobble(x, y, seed + 2, 5) == 0)]
    return tex(name, paint, seed, keep=list(bits))


def in_pot():
    both = lambda item: [POT + item, PAN + item]  # noqa: E731 - cut the same for the pan
    chunk(both("minecraft/potato"), "potato", [(232, 208, 136), (248, 230, 168)], 1401)
    chunk(both("minecraft/carrot"), "carrot", [(226, 122, 22), (248, 156, 46)], 1402)
    chunk([POT + "minecraft/beetroot"], "beetroot", [(138, 22, 50), (174, 42, 66)], 1403)
    chunk([POT + "nookworks/onion"], "onion", [(236, 226, 198), (250, 246, 230), (206, 170, 190)], 1404)
    chunk([POT + "minecraft/chicken"], "chicken", [(232, 186, 166), (248, 214, 198)], 1405, size=(2.5, 1.5, 2))
    chunk([POT + "nookworks/coated_chicken"], "coated_chicken", FLOURED, 1406, size=(2.5, 2, 2.5))
    put(POT + "nookworks/corn", {"c": D + "corn_raw"}, [box([6.75, 0, 7], [9.25, 2, 9], "c")])
    egg([POT + "minecraft/egg"], "egg", [(206, 184, 148), (232, 214, 180)], 1407)
    egg([POT + "minecraft/brown_egg"], "brown_egg", [(150, 100, 70), (178, 124, 88)], 1408)
    egg([POT + "minecraft/blue_egg"], "blue_egg", [(128, 174, 176), (156, 200, 200)], 1409)
    egg([POT + "nookworks/boiled_egg"], "boiled_egg", [(226, 222, 208), (246, 244, 236)], 1410)
    # a tangle of raw noodles, a strip of kelp
    noodles = tex("piece_noodles", lambda x, y: NOODLE[2 if (x + (y // 2) * 3) % 4 == 0 else 1] if y % 2 else NOODLE[0], 1411)
    put(POT + "nookworks/raw_udon", {"n": noodles}, [box([6, 0, 6], [10, 0.75, 10], "n")])
    put(POT + "minecraft/dried_kelp", {"k": D + "dried_kelp"}, [box([5.5, 0, 7.25], [10.5, 0.5, 8.75], "k")])
    # what is fried, before and after
    put(POT + "nookworks/breaded_pork", {"c": mat("piece_breaded", BREADED, 1412, weights=[25, 50], clump=1)}, [box([5.5, 0, 6.5], [10.5, 1.25, 9.5], "c")])
    put(POT + "nookworks/tonkatsu", {"c": T + "dish_katsu_crumb"}, [box([5.5, 0, 6.5], [10.5, 1.5, 9.5], "c")])
    floured = mat("piece_floured", FLOURED, 1413, weights=[25, 50], clump=1)
    put(POT + "nookworks/coated_fish", {"c": floured}, blob(mask(FILLET, 4.5, 6.5), 0, 1, "c", step=1, area=(0.5, 15.5, 0.5, 15.5)))
    put(POT + "nookworks/fried_fish", {"c": T + "dish_katsu_crumb"}, blob(mask(FILLET, 4.5, 6.5), 0, 1.25, "c", step=1, area=(0.5, 15.5, 0.5, 15.5)))
    put(POT + "nookworks/karaage", {"c": T + "dish_karaage"}, [box([6.75, 0, 6.75], [9.25, 2, 9.25], "c")])
    dough = mat("piece_dough", DOUGH, 1414, weights=[25, 50], clump=2)
    put(POT + "nookworks/donut_dough", {"c": dough}, blob(disc(8, 8, 2.1, 0.9), 0, 1.25, "c", step=1))
    put(POT + "nookworks/donut", {"side": T + "dish_donut_side", "top": mat("piece_donut_top", tw.DOUGH[1:], 1415, weights=[45], clump=1)},
        blob(disc(8, 8, 2.1, 0.9), 0, 1.5, "side", top="top", step=1))
    put(POT + "nookworks/french_fries", {"f": D + "fry"},
        [box([5.5, 0, 6.6], [10.5, 0.75, 7.35], "f"), box([6, 0, 7.65], [11, 0.75, 8.4], "f"), box([5, 0, 8.7], [10, 0.75, 9.45], "f")])
    # the dishes ladled out by the bowl: the pot's whole surface
    inner = octagon(8, 8, -1)
    put(POT + "nookworks/cooked_rice", {"r": D + "fill_rice"},
        flat(inner, "r") + blob(disc(8, 8, 3.6), 0, 0.5, "r", step=1) + blob(disc(8, 8, 2.6), 0.5, 1, "r", step=1))
    carrot, green = (238, 146, 46), (96, 168, 60)
    # the clear golden broth of the user's Vegetable Soup icon (2026-11-21), carrot and potato in it
    put(POT + "nookworks/vegetable_soup", {"s": scatter("pot_vegetable_soup", [(212, 164, 76), (221, 181, 93), (233, 193, 105)],
                                                        [(225, 117, 29), (245, 234, 186)], 1421)}, flat(inner, "s"))
    put(POT + "nookworks/cream_stew", {"s": scatter("pot_cream_stew", [(230, 220, 194), (243, 235, 213), (252, 247, 232)],
                                                    [carrot, green, (226, 190, 146), (246, 226, 160)], 1422)}, flat(inner, "s"))
    put(POT + "nookworks/corn_potage", {"s": scatter("pot_corn_potage", [(230, 190, 76), (243, 208, 98), (250, 224, 126)],
                                                     [(253, 247, 224), (253, 247, 224), green], 1423, every=9)}, flat(inner, "s"))

    def udon(x, y):  # thick noodles in loose waves across the broth
        import math
        wave = math.sin(x * 0.9 + (y // 3) * 1.7) * 0.9
        if (y + wave) % 3 < 1.6:
            return NOODLE[1 + ((x * 7 + y) % 6 == 0)]
        return green if (x * 5 + y * 3) % 13 == 0 else tw.BROTH[1 + (wobble(x, y, 2, 3) == 0)]
    put(POT + "nookworks/udon", {"s": tex("pot_udon", udon, 1424, keep=[green])}, flat(inner, "s"))


# ---- what lies in the pan
def in_pan():
    import gen_dishes as dishes  # the fried egg, the tamagoyaki's slice and the pancake are the plate's
    small = [".###.", "#####", "#####", ".###."]
    for items, name, white, rim in (
            (("minecraft/egg", "minecraft/brown_egg", "minecraft/blue_egg"), "pan_raw_egg", [(240, 236, 220), (250, 248, 238)], (228, 224, 206)),
            (("nookworks/fried_egg",), "pan_fried_egg", [(246, 244, 236), (255, 255, 255)], (222, 192, 136))):
        edge = {(5 + i, 6 + j) for i, j in dishes.edge_of(small)}
        top = tex(name, lambda x, y, w=white, r=rim, e=edge: r if (x, y) in e and (x + y) % 3 else w[(x + y * 3) % 4 != 0], 1430, keep=[rim])
        e = blob(mask(small, 5.5, 6), 0, 0.5, "white", top="top", step=1, area=(0.5, 15.5, 0, 16))
        e.append(box([7, 0.5, 7], [9, 1.25, 9], "yolk", top="yolk_top"))
        for item in items:
            put(PAN + item, {"top": top, "white": mat(name + "_side", [rim, white[0]], 1431, weights=[50], clump=1),
                             "yolk": D + "yolk", "yolk_top": tex("pan_yolk_top", lambda x, y: (255, 214, 92) if (x, y) == (7, 7) else (250, 186, 46), 1432,
                                                                keep=[(255, 214, 92)])}, e)
    put(PAN + "nookworks/beaten_eggs", {"e": mat("piece_beaten_eggs", [(232, 184, 56), (246, 206, 84), (253, 226, 120)], 1431, weights=[25, 50], clump=2)},
        blob(disc(8, 8, 3.6), 0, 0.4, "e", step=1))
    slice_ = box([6.5, 0, 6.5], [9.5, 2, 9.5], "skin")
    slice_["faces"]["up"] = {"texture": "#cut", "uv": [0, 0, 3, 3]}
    put(PAN + "nookworks/tamagoyaki", dishes.tamagoyaki_textures(), [slice_])
    put(PAN + "nookworks/pancake_batter", {"b": mat("piece_batter", [(238, 216, 156), (250, 234, 188)], 1432, weights=[40], clump=2)},
        blob(disc(8, 8, 3.6), 0, 0.5, "b", step=1))
    griddled = tex("pancake_plain", lambda x, y: dishes.PANCAKE_EDGE[1] if ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 > 3.3 else
                   dishes.PANCAKE[1 + (wobble(x // 2, y // 2, 41, 5) == 0) - (wobble(x // 2, y // 2, 42, 6) == 0)], 1433)
    put(PAN + "nookworks/pancake", {"side": D + "pancake_side", "top": griddled}, blob(disc(8, 8, 3.6), 0, 1, "side", top="top", step=1))
    put(PAN + "nookworks/cooked_rice", {"r": D + "fill_rice"}, blob(disc(8, 8, 2.6), 0, 1, "r", step=1) + blob(disc(8, 8, 1.45), 1, 1.6, "r", step=1))
    put(PAN + "nookworks/fried_rice", {"r": D + "fill_fried_rice"}, blob(disc(8, 8, 3.6), 0, 1, "r", step=1) + blob(disc(8, 8, 2.6), 1, 1.75, "r", step=1))
    put(PAN + "nookworks/stir_fried_vegetables", {"v": D + "fill_stir_fry"},
        blob(disc(8, 8, 3.6), 0, 0.9, "v", step=1) + blob(disc(8, 8, 2.6), 0.9, 1.6, "v", step=1))


# ---- the icons of what is got ready at the crafting table
def paler(source, ramp):
    """Our own icon {@code source} redrawn in {@code ramp}, darkest to lightest by how light each pixel was: the same
    thing before it is fried."""
    img = Image.open(ASSETS / f"textures/item/{source}.png").convert("RGBA")
    lums = [0.3 * r + 0.59 * g + 0.11 * b for r, g, b, a in img.getdata() if a > 0]
    low, high = min(lums), max(lums)
    steps = (0.2, 0.42, 0.64, 0.84)  # the ring, the shade, the body, the light, the highlight
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.getpixel((x, y))
            if a:
                t = (0.3 * r + 0.59 * g + 0.11 * b - low) / max(1.0, high - low)
                out.putpixel((x, y), ramp[sum(t >= s for s in steps)] + (a,))
    return out


# Beaten Eggs in the white enamel bowl with a blue rim (the Pancake Batter's), in the bowl family's size and angle (the
# user, 2026-11-21, after the check for 配置・大きさ・余白・角度): ten wide before the ring, twelve with it, the egg's
# surface inside the rim's ring, the rim's front an arc mirroring its back. Ringed from the bowl alone.
# Q/R/r the rim lit, middle, shade; W/w/v/V the enamel lit to shade; h/Y/y/o the egg from its glint to its shade.
BOWL_OF_EGG_ICON = {"W": (250, 250, 246), "w": (226, 230, 234), "v": (186, 194, 206), "V": (146, 156, 174),
                    "Q": (96, 134, 204), "R": (52, 86, 160), "r": (34, 58, 122),
                    "h": (255, 236, 150), "Y": (250, 206, 70), "y": (232, 172, 44), "o": (206, 140, 30)}
BOWL_OF_EGG_RING = dict(BOWL_OF_EGG_ICON, h=BOWL_OF_EGG_ICON["W"], Y=BOWL_OF_EGG_ICON["w"], y=BOWL_OF_EGG_ICON["v"],
                        o=BOWL_OF_EGG_ICON["v"])
BOWL_OF_EGG = itemkit.check([
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".....QQQRRr.....",
    "...RYhhYYYyyr...",
    "...RRYYYyyorr...",
    "...WWRRrrrrvV...",
    "....WWwwwvvV....",
    "......wvvV......",
    "................",
    "................",
    "................",
    "................",
])


def icons():
    floury = [(120, 90, 54), (172, 140, 92), (212, 184, 130), (236, 216, 168), (251, 242, 214)]
    doughy = [(128, 96, 56), (184, 150, 98), (222, 194, 142), (242, 224, 180), (252, 244, 220)]
    made = {"coated_chicken": paler("karaage", floury), "coated_fish": paler("fried_fish", floury), "donut_dough": paler("donut", doughy),
            "beaten_eggs": itemkit.sprite(BOWL_OF_EGG, BOWL_OF_EGG_ICON, lit=0.8, dark=0.55, ring_from=BOWL_OF_EGG_RING)}
    for name, img in made.items():
        if not hand_finished.kept(ASSETS / f"textures/item/{name}.png"):  # the author's own drawing stands for some
            img.save(ASSETS / f"textures/item/{name}.png")
        tw.write(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"nookworks:item/{name}"}})
        tw.write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:item/{name}"}})


def main():
    vessels()
    in_pot()
    in_pan()
    icons()
