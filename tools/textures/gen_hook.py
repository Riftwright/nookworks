"""The Hook: a small iron hook for a wall or a ceiling, and the sprigs hung on it - sweet berries and glow berries.

All original pixel art, drawn here (no vanilla pixels): the iron in the black of the Thin Rope's ceiling fitting, and
the sprigs drawn hanging, gathered in a twist of twine at the hook and falling away from it:
- sweet berries: a bunch of the bush, stems and leaves filling out as they fall, the berries where they droop;
- glow berries: strands of the cave vine, a few small leaves along each, the berries alight among them.
On a wall a sprig lies against the wall, with a second one standing out from the hook; under a ceiling two hang crossed,
as a bunch of herbs hung up to dry. The sweet berries' sprig is what they look like hung on a Thin Rope (HungItemRenderer
draws it with the client item hung_sweet_berries: there is no item of that name); glow berries hang on a rope as
vanilla's cave vine itself (the user, 2026-10-16), so theirs has no rope picture.

- textures/block/hook.png                     the iron: lit, body, shade and edge, in bands for the models' faces
- textures/block/hook_<sprig>.png             the sprig against the wall (or the first under a ceiling)
- textures/block/hook_<sprig>_out.png         the sprig standing out, sparser
- textures/item/hook.png                      the item: the hook on its plate
- textures/item/hung_sweet_berries.png        the sweet berries' sprig as it hangs from a rope
- models/block/hook_{wall,ceiling}[_<sprig>].json, items/hook.json, items/hung_sweet_berries.json, blockstates/hook.json
- data: the loot table, the recipe, and #nookworks:rope_posts (fence posts, bars, rods, the hook and the supports, of which a
  wall bracket's brace takes a rope)

Run from the repo root:  python tools/textures/gen_hook.py
"""
import json
from pathlib import Path

from PIL import Image

import itemkit
import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
DATA = ROOT / "src/main/resources/data/nookworks"

# the Thin Rope's fitting, in thin_rope.png: edge, shade, body, lit
EDGE, SHADE, BODY, LIT = (22, 22, 26), (40, 40, 46), (62, 62, 70), (92, 92, 102)
TWINE, TWINE_DK = (176, 150, 104), (122, 100, 66)
# The colours are vanilla's own, as measured off the sweet berry bush and the cave vines (only the colours: the drawing
# is ours), so a sprig hung by a bush or a vine reads as cut from it. Ours were picked by eye, and stood apart: the
# bush's leaves yellower, its berries pinker, its stems brown; the glow berries' light too pale. (The user, 2026-10-03.)
# The berries are the size of the bush's and the vine's - three pixels across, two for a small one: at a pixel or two
# they read as specks, not the same plant (the user, 2026-10-11).
# the bush: blue-green leaves on dark green stems, its berries a deep red with a pink glint
STEM, STEM_DK = (41, 75, 56), (29, 56, 34)
LEAF_DK, LEAF, LEAF_LT = (41, 82, 48), (60, 110, 66), (72, 127, 79)
BERRY_DK, BERRY, BERRY_LT, BERRY_HI = (105, 31, 33), (130, 11, 5), (165, 7, 0), (223, 70, 126)
SWEET_BERRIES = (BERRY_HI, BERRY_LT, BERRY, BERRY_DK)  # exact, as the glow berries are: shading pulls a red off
# the cave vine: a yellower green than the bush, green stems, and its berries the orange of a lamp
VINE, VINE_DK = (72, 97, 36), (64, 84, 30)
VLEAF_DK, VLEAF, VLEAF_LT = (66, 85, 45), (80, 114, 51), (112, 146, 45)
VINE_BK, VLEAF_BK = (62, 80, 32), (70, 96, 44)  # the strands behind, a shade back
GLOW_DK, GLOW, GLOW_LT, GLOW_HI = (191, 103, 23), (235, 137, 49), (244, 192, 94), (247, 226, 107)
SPRIGS = ("sweet_berries", "glow_berries")


def blank():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(c) + (255,))


def iron():
    """Bands of the iron, top to bottom: lit (0..3), body (4..11), shade (12..15), for tops, sides and undersides."""
    img = blank()
    for y in range(16):
        for x in range(16):
            c = LIT if y < 4 else BODY if y < 12 else SHADE
            if (x + y) % 5 == 0 and 4 <= y < 12:
                c = SHADE  # a little pitting in the body
            put(img, x, y, c)
    return img


def line(img, x0, y0, x1, y1, c):
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(steps + 1):
        put(img, round(x0 + (x1 - x0) * i / steps), round(y0 + (y1 - y0) * i / steps), c)


def berry(img, x, y, big=False):
    """A berry the size of the bush's: two pixels across, or three for a full one, lit at its upper left."""
    if big:
        for dx, dy, c in ((0, 0, BERRY_DK), (1, 0, BERRY_LT), (2, 0, BERRY), (0, 1, BERRY_LT), (1, 1, BERRY_HI),
                          (2, 1, BERRY), (0, 2, BERRY), (1, 2, BERRY_DK), (2, 2, BERRY_DK)):
            put(img, x + dx, y + dy, c)
    else:
        for dx, dy, c in ((0, 0, BERRY_HI), (1, 0, BERRY_LT), (0, 1, BERRY), (1, 1, BERRY_DK)):
            put(img, x + dx, y + dy, c)


def leaf(img, x, y, left):
    """A narrow leaf off a stem, pointing down and out."""
    d = -1 if left else 1
    put(img, x + d, y, LEAF)
    put(img, x + 2 * d, y + 1, LEAF_DK)
    put(img, x + d, y + 1, LEAF_LT if left else LEAF)


def sprig(spread, stems, berries, seed, tie=(7, 3)):
    """
    A bunch of the bush hung up by its stems: gathered in twine at {@code tie}, a few bare stems below it, then the
    leaves filling out as they fall away - {@code spread} pixels wider each row - with gaps between the leaves, and
    the berries in among them where the sprigs droop.
    """
    import random
    rng = random.Random(seed)
    img = blank()
    tx, ty = tie
    top = ty + 3  # the leaves begin under the bare stems
    widest = 12   # the bunch fills out to here, then falls away in the tips of the sprigs
    for y in range(top, 16):
        half = 1.2 + (min(y, widest) - top) * spread - max(0, y - widest) * 1.1
        left, right = round(7.5 - half + rng.uniform(-0.6, 0.6)), round(7.5 + half + rng.uniform(-0.6, 0.6))
        for x in range(max(0, left), min(15, right) + 1):
            if y > widest and rng.random() < 0.3 + 0.15 * (y - widest):
                continue  # the ragged ends of the sprigs
            if rng.random() < 0.16 and left + 1 < x < right - 1:
                continue  # a gap between the leaves, the wall showing through
            shade = rng.random()
            put(img, x, y, LEAF_LT if shade < 0.22 and y < 13 else LEAF_DK if shade > 0.72 else LEAF)
    for x1, y1 in stems:  # the stems, bare where they leave the twine and seen here and there among the leaves
        line(img, tx, ty + 1, x1, y1, STEM)
    for x, y, big in berries:
        berry(img, x, y, big)
    twine(img, tie)
    return img


def twine(img, tie):
    tx, ty = tie
    put(img, tx, ty, TWINE)       # the twine they are gathered in, round the hook
    put(img, tx + 1, ty, TWINE_DK)
    put(img, tx, ty + 1, TWINE_DK)
    put(img, tx + 1, ty + 1, STEM_DK)


def glow_berry(img, x, y, big=False):
    """A glow berry the size of the vine's: three pixels across, its core alight, or two for a small one."""
    if big:
        # round: the corners a shade down, the dark at the foot, the light at the upper left as on the vine's
        for dx, dy, c in ((0, 0, GLOW_DK), (1, 0, GLOW_LT), (2, 0, GLOW), (0, 1, GLOW_LT), (1, 1, GLOW_HI),
                          (2, 1, GLOW), (0, 2, GLOW), (1, 2, GLOW_DK), (2, 2, GLOW_DK)):
            put(img, x + dx, y + dy, c)
    else:
        for dx, dy, c in ((0, 0, GLOW_HI), (1, 0, GLOW_LT), (0, 1, GLOW), (1, 1, GLOW_DK)):
            put(img, x + dx, y + dy, c)


def vines(strands, seed, tie=(7, 3)):
    """
    Strands of the cave vine hung up by their tops, a tassel of them: each leaves the twine at {@code tie}, curves out
    and falls to its end (x, y), a small leaf off it every other pixel, to one side and then the other; its berries
    hang off it at the rows given (row, side, big), and the strands behind are a shade darker than those in front.
    """
    import random
    rng = random.Random(seed)
    img = blank()
    tx, ty = tie
    berries = []
    for n, (x_end, y_end, buds, behind) in enumerate(strands):
        y0 = ty + 2
        pts = []
        for y in range(y0, y_end + 1):
            t = (y - y0) / max(1, y_end - y0)
            pts.append((round(tx + 0.5 + (x_end - tx - 0.5) * (1 - (1 - t) ** 2.4)), y))  # out at once, then down
        prev = None
        for i, (x, y) in enumerate(pts):
            if prev is not None and abs(x - prev) > 1:  # joined where it swings out
                put(img, (x + prev) // 2, y - 1, VINE_BK if behind else VINE)
            put(img, x, y, VINE_BK if behind else VINE_DK if i % 3 == 2 else VINE)
            prev = x
            if i % 2 == 1 and y < y_end - 1:
                side = 1 if (i // 2 + n) % 2 else -1
                put(img, x + side, y, VLEAF_BK if behind else VLEAF_LT if rng.random() < 0.4 else VLEAF)
                put(img, x + 2 * side, y + 1, VLEAF_BK if behind else VLEAF_DK if rng.random() < 0.5 else VLEAF)
        along = {y: x for x, y in pts}
        for row, side, big in buds:
            if row in along:
                berries.append((along[row] + side - (1 if big and side < 0 else 0), row, big))
    for x, y, big in berries:  # over the leaves: the vine bears them out in front
        glow_berry(img, x, y, big)
    twine(img, tie)
    put(img, tx + 1, ty + 1, VINE)  # the vines' tops in the twine, not the bush's brown stems
    return img


GLOW_BERRIES = (GLOW_HI, GLOW_LT, GLOW, GLOW_DK)  # lit from within: kept clean of the leaves' shading


def glow_flat():
    return vines([(5, 14, [(8, -1, True)], True),
                    (10, 15, [(13, 0, False)], True),
                    (1, 13, [(10, 0, True)], False),
                    (14, 12, [(7, -1, True)], False),
                    (8, 15, [(12, -1, True)], False)], 93)


def glow_out():
    return vines([(4, 13, [(9, 0, True)], True),
                    (11, 14, [(12, 0, True)], False),
                    (7, 15, [(7, 0, False)], False)], 94)


def berries_flat():
    return sprig(0.95, stems=((4, 8), (7, 9), (11, 8)),
                   berries=((2, 9, True), (9, 8, False), (6, 11, True), (11, 10, True), (3, 13, False), (10, 13, False)),
                   seed=91)


def berries_out():
    return sprig(0.62, stems=((6, 8), (9, 8)), berries=((4, 10, True), (9, 10, True), (7, 13, False)), seed=92)


# The item (itemkit): the hook's shape as it was, its iron lifted so it reads as iron in the slot, not a black stroke
# (the same three pixels thick): the edge towards the light lit (L), the body (B), a cooler underside (S), a highlight
# where the bend and the plate catch the light (h); the plate a rosette, its rim lighter (R, r) round a dark screw slot
# (o). Ringed in the iron's own darkest: the slot counts as iron for it.
ICON_COLOURS = {"o": (30, 32, 40), "S": (60, 64, 80), "B": (98, 100, 112), "L": (140, 142, 154), "h": (198, 200, 212),
                "R": (164, 166, 178), "r": (122, 124, 136)}
ICON_RING = {ch: ICON_COLOURS["S"] for ch in ICON_COLOURS}
ICON = [
    "................",
    "................",
    "..RRRR..........",
    ".RhLLrS.........",
    ".RLooBS.........",
    ".RLBBBSLLLLL....",
    "..SSSSBBBBBBBS..",
    "...........LhBS.",
    "............hBS.",
    "............LBS.",
    "....h.......LBS.",
    "....LB......LBS.",
    "....LBB....LBBS.",
    ".....SBBBBBBBS..",
    "......SSSSSS....",
    "................",
]


def item_icon():
    """The hook as it is held: its round plate with the screw in it, the shank, and the bend up to the point."""
    return itemkit.sprite(itemkit.check(ICON), ICON_COLOURS, lit=0.9, dark=0.72, ring_from=ICON_RING)


def face(tex, uv, cull=None):
    f = {"texture": tex, "uv": uv}
    if cull:
        f["cullface"] = cull
    return f


def iron_box(frm, to, skip=(), cull=None):
    """A piece of the hook: its top from the lit band, its sides from the body, its underside from the shade - each
    face a texel to a pixel, as big on the texture as it is on the block."""
    dx, dy, dz = (to[i] - frm[i] for i in range(3))
    size = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}
    band = {"up": 0, "down": 12}  # the lit rows and the shaded ones; the sides from the body, from row 4
    faces = {}
    for side, (w, h) in size.items():
        if side not in skip:
            v = band.get(side, 4)
            faces[side] = face("#iron", [4, v, 4 + w, v + h])
    if cull:
        for side, c in cull.items():
            faces[side]["cullface"] = c
    return {"from": frm, "to": to, "faces": faces}


def hook_wall():
    """Pointing north, its plate on the wall to the south: a plate, a shank out from it, the point turned up."""
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"iron": "nookworks:block/hook", "particle": "nookworks:block/hook"},
            "elements": [iron_box([7, 7.5, 15.5], [9, 10.5, 16], skip=("south",)),
                         iron_box([7.5, 8.5, 12.5], [8.5, 9.5, 15.5], skip=("south",)),
                         iron_box([7.5, 9.5, 12.5], [8.5, 11.5, 13.5], skip=("down",))]}


def hook_ceiling():
    """Under a ceiling: a plate, a shank down, and the bend turned north and up to the point."""
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"iron": "nookworks:block/hook", "particle": "nookworks:block/hook"},
            "elements": [iron_box([7, 15.5, 7], [9, 16, 9], skip=("up",)),
                         iron_box([7.5, 11, 7.5], [8.5, 15.5, 8.5], skip=("up",)),
                         iron_box([7.5, 10, 6], [8.5, 11, 8.5]),
                         iron_box([7.5, 11, 6], [8.5, 12.5, 7], skip=("down",))]}


def plane(frm, to, faces_on, tex, uv, rotation=None):
    element = {"from": frm, "to": to, "shade_direction_override": "up",
               "faces": {side: {"texture": tex, "uv": uv if side not in ("south", "west") else [uv[2], uv[1], uv[0], uv[3]]}
                         for side in faces_on}}
    if rotation:
        element["rotation"] = rotation
    return element


def berries_wall(sprig):
    """The hook, and the sprigs: one lying against the wall, one standing out from the point."""
    model = hook_wall()
    model["textures"].update({"flat": f"nookworks:block/hook_{sprig}", "out": f"nookworks:block/hook_{sprig}_out"})
    model["elements"] += [
        plane([1, 0, 15.3], [15, 13, 15.3], ("north",), "#flat", [1, 3, 15, 16]),
        plane([2, 0, 13.5], [14, 12, 13.5], ("north", "south"), "#out", [2, 4, 14, 16],
              rotation={"origin": [8, 12, 13.5], "axis": "x", "angle": 22.5}),
    ]
    return model


def berries_ceiling(sprig):
    """The hook, and two sprigs hanging crossed under it."""
    model = hook_ceiling()
    model["textures"].update({"flat": f"nookworks:block/hook_{sprig}", "out": f"nookworks:block/hook_{sprig}_out"})
    model["elements"] += [
        plane([1, 0, 8], [15, 12.5, 8], ("north", "south"), "#flat", [1, 3.5, 15, 16],
              rotation={"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}),
        plane([8, 0, 1], [8, 12.5, 15], ("east", "west"), "#out", [1, 3.5, 15, 16],
              rotation={"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}),
    ]
    return model


# a wall hook as drawn points north; turned for the others (and a ceiling hook's bend with it)
TURN = {"north": 0, "east": 90, "south": 180, "west": 270}


def states():
    parts = []
    for face_name in ("wall", "ceiling", "floor"):
        for facing, turn in TURN.items():
            # on a floor only by a command: the ceiling's hook, stood on end
            model = "ceiling" if face_name == "floor" else face_name
            rot = {"x": 180, "y": (turn + 180) % 360} if face_name == "floor" else {"y": turn} if turn else {}
            for sprig in ("none",) + SPRIGS:
                suffix = "" if sprig == "none" else "_" + sprig
                parts.append({"when": {"face": face_name, "facing": facing, "sprig": sprig},
                              "apply": {"model": f"nookworks:block/hook_{model}{suffix}", **rot}})
    return {"multipart": parts}


def loot():
    return {"type": "minecraft:block", "random_sequence": "nookworks:blocks/hook", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "nookworks:hook"}],
         "condition": {"type": "minecraft:survives_explosion"}},
        *({"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"minecraft:{sprig}"}],
           "condition": {"type": "minecraft:match_block", "blocks": "nookworks:hook", "state": {"sprig": sprig}}}
          for sprig in SPRIGS)]}


def recipe():
    return {"type": "minecraft:crafting_shaped", "category": "misc",
            "pattern": [" N", " N", "NN"], "key": {"N": "minecraft:iron_nugget"},
            "result": {"id": "nookworks:hook", "count": 4}}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    block = ASSETS / "textures/block"
    iron().save(block / "hook.png")  # exact: a few flat bands the tiny faces read from
    pictures = {"sweet_berries": (berries_flat, berries_out, 91, SWEET_BERRIES), "glow_berries": (glow_flat, glow_out, 93, GLOW_BERRIES)}
    for kind, (flat, out, seed, keep) in pictures.items():
        hung = texkit.finish(flat(), seed, keep=keep, **texkit.PLANT)
        hung.save(block / f"hook_{kind}.png")
        texkit.finish(out(), seed + 1, keep=keep, **texkit.PLANT).save(block / f"hook_{kind}_out.png")
        write_json(ASSETS / f"models/block/hook_wall_{kind}.json", berries_wall(kind))
        write_json(ASSETS / f"models/block/hook_ceiling_{kind}.json", berries_ceiling(kind))
        if kind == "sweet_berries":  # on a rope, the same sprig as on the hook (glow berries hang there as the vine)
            hung.save(ASSETS / f"textures/item/hung_{kind}.png")
            write_json(ASSETS / f"models/item/hung_{kind}.json",
                       {"parent": "minecraft:item/generated", "textures": {"layer0": f"nookworks:item/hung_{kind}"}})
            write_json(ASSETS / f"items/hung_{kind}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:item/hung_{kind}"}})
    item_icon().save(ASSETS / "textures/item/hook.png")
    write_json(ASSETS / "models/block/hook_wall.json", hook_wall())
    write_json(ASSETS / "models/block/hook_ceiling.json", hook_ceiling())
    write_json(ASSETS / "models/item/hook.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "nookworks:item/hook"}})
    write_json(ASSETS / "items/hook.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/hook"}})
    write_json(ASSETS / "blockstates/hook.json", states())
    write_json(DATA / "loot_table/blocks/hook.json", loot())
    write_json(DATA / "recipe/hook.json", recipe())
    write_json(DATA / "tags/block/rope_posts.json", {"values": ["#minecraft:fences", "#minecraft:bars", "#minecraft:lightning_rods", "minecraft:end_rod", "nookworks:hook", "#nookworks:supports"]})
    print("wrote the hook")
