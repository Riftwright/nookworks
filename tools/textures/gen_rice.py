"""Rice: the grains (brown and white, as small heaps), the bran, the crop - a clump standing in water that heads up
into a second block of drooping panicles - and the Rice Polisher's model. All original pixel art, and the models and
blockstates. Deterministic.

- item/brown_rice.png, white_rice.png, rice_bran.png
- models/block/rice_polisher.json: a spruce box with a hopper's funnel on top, a chute at the front and a crank at the
  side, in vanilla textures referenced by name
- block/rice_stage0..3.png        the lower block, in the water: a fan of blades from one clump
- block/rice_panicles_stage0..3.png the upper block: the fan carries on, heads come up green and hang heavy and gold
- models/block/rice_stage*.json, rice_panicles_stage*.json (crop models, cutout), blockstates/rice*.json

The crop is drawn flat and then finished with texkit.shade (PLANT, plain), which breaks each colour into its hue-shifted shades;
the grains and the bran's sack are placed pixel by pixel (itemkit) and left as placed.

The crop model shows the texture on four planes, so each texture is the plant seen side on, rooted at the middle of
the bottom edge. The lower texture's blades leave its top edge where the upper texture's pick them up.

Run from the repo root:  python tools/textures/gen_rice.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

import itemkit
import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
CLEAR = (0, 0, 0, 0)

LEAF = [(40, 84, 36), (64, 118, 46), (94, 150, 60), (114, 164, 72)]          # dark, mid, light, highlight (soft)
RIPE_LEAF = [(92, 104, 40), (126, 136, 54), (160, 164, 74), (190, 188, 104)]  # the leaves yellowing at harvest
GREEN_GRAIN = [(98, 128, 52), (140, 168, 76), (158, 182, 96)]
GOLD_GRAIN = [(128, 92, 30), (182, 140, 52), (218, 180, 88), (242, 216, 140)]
# the grains as items: highlight, light, body, a cool shade - and G, brown rice's germ, a darker fleck at one end
WHITE_GRAIN = {"H": (255, 255, 250), "L": (245, 242, 232), "B": (229, 224, 207), "S": (192, 193, 205), "G": (192, 193, 205)}
BROWN_GRAIN = {"H": (236, 208, 150), "L": (214, 176, 114), "B": (188, 144, 86), "S": (148, 104, 60), "G": (112, 76, 36)}
BRAN = [(150, 112, 64), (190, 152, 98), (218, 186, 132), (238, 214, 168)]


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def blade(img, base_x, lean, top, colours, start=15):
    """One slender leaf blade, a pixel wide, from row {@code start} up to row {@code top}. It stands nearly upright
    and arches out by {@code lean} pixels only near its tip, the way rice leaves do. Dark at the base, lighter up
    its length, and pale at the very tip."""
    dark, mid, light, high = colours
    length = start - top
    last = None
    for i in range(length + 1):
        t = i / max(1, length)
        x = round(base_x + lean * t ** 2.4)
        y = start - i
        c = dark if t < 0.3 else mid if t < 0.75 else light
        if last is not None and abs(x - last) > 1:  # keep a steep arch joined up
            px(img, (x + last) // 2, y + 1, c)
        px(img, x, y, c)
        last = x
    px(img, last, top, high)


def fan(img, rng, blades, colours, start=15):
    """A clump: {@code blades} as (lean, top) pairs, all rising from the middle two columns."""
    for i, (lean, top) in enumerate(blades):
        base = 7 + (i % 2) + (0.4 if lean > 0 else -0.4) * (i % 3 == 0)
        blade(img, base, lean + rng.uniform(-0.3, 0.3), max(0, top), colours, start)


LOWER = (
    [(-2, 11), (2, 10), (0, 9)],
    [(-3, 7), (3, 6), (-1, 5), (1, 4), (-4, 9), (4, 8)],
    [(-4, 3), (4, 2), (-2, 1), (2, 0), (0, 0), (-5, 6), (5, 5), (-1, 0)],
    [(-3, 0), (3, 0), (-1, 0), (1, 0), (0, 0), (-5, 3), (5, 2), (-6, 7), (6, 6), (-2, 0)],
)


def lower(stage):
    """The plant in the water: a few blades at first, then a full clump that runs out of the top of the block."""
    rng = random.Random(40 + stage)
    img = Image.new("RGBA", (16, 16), CLEAR)
    fan(img, rng, LOWER[stage], LEAF)
    for y in range(13, 16):  # the base of the clump, a little denser at the root
        px(img, 7, y, LEAF[0])
        px(img, 8, y, LEAF[0])
    return img


def upper_leaves(img, rng, colours, height):
    """The clump's blades carried on from the block below, arching out and ending at different heights."""
    tops = (height, height + 2, height + 4, height + 1, height + 6, height + 3)
    leans = (-3, 3, -5, 5, -6, 1)
    for i, (lean, top) in enumerate(zip(leans, tops)):
        blade(img, 6 + (i % 4), lean, min(14, top), colours)


def panicle(img, stalk_x, rise, side, hang, colours, stalk):
    """A head of rice: a thin stalk up out of the clump that bends over and hangs {@code hang} rows back down,
    beaded with grains - one to a row, now on this side of the stem and now on that, lit on the top."""
    y = 15
    for _ in range(rise):
        px(img, stalk_x, y, stalk)
        y -= 1
    top = y
    px(img, stalk_x, top, stalk)
    bend = [(stalk_x + side, top - 1), (stalk_x + 2 * side, top - 1), (stalk_x + 3 * side, top)]
    for x, yy in bend:
        px(img, x, yy, stalk)
    x = stalk_x + 3 * side
    for j in range(1, hang + 1):
        yy = top + j
        if j in (3, 6):
            x += side  # hanging a little further out as it lengthens
        px(img, x, yy, colours[0])
        grain = x + (side if j % 2 else -side)
        px(img, grain, yy, colours[1] if j % 2 else colours[2])
        if len(colours) > 3 and j % 2:
            px(img, grain, yy - 1 if img.getpixel((grain, max(0, yy - 1)))[3] == 0 else yy, colours[3])
    px(img, x, top + hang + 1, colours[1])  # the tip of the head


def upper(stage):
    """The top block. 0: the clump's upper half. 1: green stalks come up out of it. 2: green heads, just bending.
    3: heavy gold heads hanging over leaves gone yellow."""
    rng = random.Random(70 + stage)
    img = Image.new("RGBA", (16, 16), CLEAR)
    if stage == 0:
        upper_leaves(img, rng, LEAF, 8)
    elif stage == 1:
        upper_leaves(img, rng, LEAF, 4)
        for x, rise in ((7, 11), (9, 9)):
            for y in range(15, 15 - rise, -1):
                px(img, x, y, LEAF[1] if y > 15 - rise + 3 else LEAF[2])
            px(img, x, 15 - rise, GREEN_GRAIN[2])
            px(img, x, 16 - rise, GREEN_GRAIN[1])
    elif stage == 2:
        upper_leaves(img, rng, LEAF, 4)
        panicle(img, 7, 11, -1, 3, GREEN_GRAIN, LEAF[1])
        panicle(img, 9, 10, 1, 4, GREEN_GRAIN, LEAF[1])
    else:
        upper_leaves(img, rng, RIPE_LEAF, 5)
        panicle(img, 7, 11, -1, 8, GOLD_GRAIN, RIPE_LEAF[1])
        panicle(img, 9, 12, 1, 9, GOLD_GRAIN, RIPE_LEAF[1])
        panicle(img, 8, 9, 1, 5, GOLD_GRAIN, RIPE_LEAF[2])
    return img


# A low, loose heap of fourteen small grains (itemkit), placed by hand: lying, aslant and on end, a few whole ones
# (three by two) on top and the rest half hidden, the slot showing between them. Each grain lit at its upper left (H,
# L), its body (B) and a cool shade at its lower right (S); G is the end where brown rice keeps its germ, a shade on
# white rice. Ringed once round the whole heap, the ring only a step under the grain's shade, as a pale thing's is.
# (Seven big grains each ringed on its own swelled into capsules: white rice read as bone shards, brown as peanuts.)
RICE_HEAP = itemkit.check([
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".......HLB......",
    "....HS..BG.H....",
    "..........HL....",
    "..HLB.LS.BS.HLB.",
    "...BG........BS.",
    ".....HLB.HS...H.",
    ".HS...BS.....HG.",
    "...HG...HLB.BS..",
    ".........BS.....",
    "................",
])


def rice_item(colours, lit, dark):
    """The heap in {@code colours}, ringed {@code lit} and {@code dark} of its shade (itemkit.sprite)."""
    return itemkit.sprite(RICE_HEAP, colours, lit=lit, dark=dark, holes=True)


# Rice bran (itemkit) in a small cloth sack, as it is kept for the pickling bed: a sack a little taller than wide, its
# top gathered into a tan ruffle with the pale bran showing in its mouth (b, B), tied at the neck with a single red
# cord (r, R its lit turn) whose two ends hang loose by the knot; the cloth in three values lit from the upper left (H,
# L, M, S) with a fold down its belly (F); and in front of it a little heap of the spilled powder (b, B, d, e). Ringed
# once round it all in the cloth's own dark - the cord and the bran count as cloth for it. (Drawn as a formula and
# grained, the cord spread into a strawberry top, the weave went mottled and the spill ran off the edge unringed.)
SACK = {"H": (222, 196, 150), "L": (200, 168, 120), "M": (166, 132, 88), "S": (126, 96, 60), "F": (140, 108, 70),
        "r": (190, 52, 40), "R": (226, 86, 64), "b": BRAN[3], "B": BRAN[2], "d": BRAN[1], "e": BRAN[0]}
SACK_RING = dict(SACK, r=SACK["M"], R=SACK["M"], b=SACK["M"], B=SACK["M"])
BRAN_SACK = itemkit.check([
    "................",
    "................",
    "....H.L.M.......",
    "....LbbBMS......",
    ".....LMMS.......",
    "....rRrrrR......",
    "...HLMMMMrr.....",
    "..HLLMMFMrSr....",
    "..LLMMMFMMMS....",
    "..LLMMMMFMMS....",
    "..LLMMMMFMMS....",
    "..LMMMMMFMSS....",
    "..LMMMMMMMSSbB..",
    "...SMMMMMSbBBdd.",
    "....SSSSSbBddde.",
    "................",
])


def bran():
    return itemkit.sprite(BRAN_SACK, SACK, ring_from=SACK_RING)


def box(frm, to, tex, faces="nsewud", uv=None):
    names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    out = {}
    for f in faces:
        face = {"texture": tex[f] if isinstance(tex, dict) else tex}
        if uv:
            face["uv"] = uv
        out[names[f]] = face
    return {"from": frm, "to": to, "faces": out}


def polisher_model(part="all"):
    """Facing north: the chute at the front, the crank on the east side, the funnel on top. In parts: the base, the hand
    crank, and the axle a Motor drives it by, out of the north face at the height of the motor's shaft with a flange on
    the box ("all" is base and crank, for the item)."""
    textures = {"particle": "minecraft:block/spruce_planks", "planks": "minecraft:block/spruce_planks",
                "funnel": "minecraft:block/hopper_outside", "funnel_in": "minecraft:block/hopper_inside",
                "dark": "minecraft:block/dark_oak_planks", "iron": "nookworks:block/black_iron",
                "grip": "minecraft:block/stripped_spruce_log"}
    crank = [box([14, 5, 7], [15, 7, 9], "#iron"), box([15, 5, 7], [16, 12, 9], "#iron"), box([14, 10, 7.5], [15, 12, 8.5], "#grip")]
    # the flange on the box, five across with its corners cut, so it reads round as it turns (a six-pixel square plate
    # read as a board - the user, 2026-10-10): a column and a piece either side, none overlapping
    axle = [box([6.5, 6.5, 0], [9.5, 9.5, 2], "#iron"), box([6.5, 5.5, 1.5], [9.5, 10.5, 2], "#iron"),
            box([5.5, 6.5, 1.5], [6.5, 9.5, 2], "#iron"), box([9.5, 6.5, 1.5], [10.5, 9.5, 2], "#iron")]
    if part == "crank":
        return {"textures": textures, "elements": crank}
    if part == "axle":
        return {"textures": textures, "elements": axle}
    side = {"n": "#planks", "s": "#planks", "e": "#planks", "w": "#planks", "u": "#planks", "d": "#planks"}
    els = [
        box([2, 0, 2], [14, 10, 14], side),
        box([3, 10, 3], [13, 11, 13], "#funnel"),
        box([4, 11, 4], [12, 14, 12], {"n": "#funnel", "s": "#funnel", "e": "#funnel", "w": "#funnel", "u": "#funnel_in",
                                       "d": "#funnel"}),
        box([6, 2, 0], [10, 5, 2], {"n": "#dark", "s": "#planks", "e": "#planks", "w": "#planks", "u": "#planks", "d": "#planks"}),
    ]
    if part == "all":
        els += crank
    return {"parent": "minecraft:block/block", "textures": textures, "elements": els}


FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def driven_states(name):
    """The machine turned to its facing. Its hand crank, and the axle out to a Motor when one drives it, are drawn by
    client/render/CrankRenderer, which turns them as the handle is worked (models/block/<name>_crank and _axle)."""
    parts = []
    for facing, y in FACINGS:
        parts.append({"when": {"facing": facing}, "apply": dict({"model": f"nookworks:block/{name}_base"}, **({"y": y} if y else {}))})
    return {"multipart": parts}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    tex = ASSETS / "textures/block"
    rice_item(WHITE_GRAIN, 0.82, 0.7).save(ASSETS / "textures/item/white_rice.png")  # a light cool grey ring
    rice_item(BROWN_GRAIN, 0.85, 0.7).save(ASSETS / "textures/item/brown_rice.png")
    bran().save(ASSETS / "textures/item/rice_bran.png")
    for stage in range(4):
        for i, (name, fn) in enumerate((("rice", lower), ("rice_panicles", upper))):
            texkit.shade(fn(stage), 10 + stage * 2 + i, reach=1, **texkit.PLANT).save(tex / f"{name}_stage{stage}.png")
            write_json(ASSETS / f"models/block/{name}_stage{stage}.json", {
                "parent": "minecraft:block/crop", "render_type": "minecraft:cutout",
                "textures": {"crop": f"nookworks:block/{name}_stage{stage}"}})
    for name in ("rice", "rice_panicles"):
        write_json(ASSETS / f"blockstates/{name}.json",
                   {"variants": {f"age={a}": {"model": f"nookworks:block/{name}_stage{a}"} for a in range(4)}})
    for name in ("brown_rice", "white_rice", "rice_bran"):
        write_json(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"nookworks:item/{name}"}})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:item/{name}"}})
    write_json(ASSETS / "models/block/rice_polisher.json", polisher_model())
    for part in ("base", "crank", "axle"):
        write_json(ASSETS / f"models/block/rice_polisher_{part}.json", polisher_model(part))
    write_json(ASSETS / "blockstates/rice_polisher.json", driven_states("rice_polisher"))
    write_json(ASSETS / "items/rice_polisher.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/rice_polisher"}})
    print("wrote rice")


if __name__ == "__main__":
    main()
