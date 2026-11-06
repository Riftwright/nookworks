"""Nourished Soil: its textures, models, blockstate and item definition. Original pixel art. Deterministic.

Dirt fed with bone meal, and it should look it at a glance - in a bed among plain dirt, and in a chest: a darker,
richer loam lying in crumbs. Each crumb is lit along its top and casts a shadow into the soil below it (to its right as
well while the soil stands fresh), the bigger clods catch the light at their top left; flecks of bone meal lie white on
the crumbs, with bits of straw and dark leaf mould worked in between. The crumbs are laid by hand (CRUMBS below), not
scattered, so they read as clods rather than noise, and they tile.

As the soil is spent (the block's richness, 3 down to 0) it pales and dries, its crumbs settle and their shadows
shorten, and the bone meal, straw and mould thin out - the spent soil has none of the bone meal left. So a bed that
wants feeding can be seen from across the garden, and the same crumbs lie there at every richness.

Tilled, the top is the same loam with four furrows cut down it, each a deep trough with its far side in shade and the
near side of the next ridge catching the light, running a pixel aside here and there as a hoe leaves it. They run down
the texture as vanilla's farmland's do (north to south in the world), so a bed of it in a vanilla field lies the same
way; they are drawn across and the finished picture turned (transposed: the light still comes from the top left).
Watered, it is darker and deeper, with a wet sheen along a few ridges - as vanilla's farmland, moisture 7 is the wet
look.

- block/nourished_soil_<r>.png                 the soil at richness r (0-3): every face, the tilled block's sides too
- block/nourished_soil_farmland_<r>.png        the tilled top, dry
- block/nourished_soil_farmland_moist_<r>.png  the tilled top, watered
- models/block/nourished_soil_<r>.json, nourished_soil_tilled_<r>.json, nourished_soil_tilled_moist_<r>.json
- blockstates/nourished_soil.json, items/nourished_soil.json (the fresh soil)

Drawn flat with a few colours, then finished with texkit.shade, plain (lightly: the crumbs carry the structure; tiling); the bone
meal is kept exact so it stays crisp. Run from the repo root:  python tools/textures/gen_soil.py
"""
import json
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
TEXTURES = ASSETS / "textures/block"
N = 16
RICHNESS = range(4)

# The loam fresh (richness 3) and spent (0), mixed between: crevice, shade (the soil between crumbs), crumb, lit top,
# highlight. Watered tilled soil is drawn deeper still.
FRESH = [(40, 24, 15), (62, 38, 24), (88, 56, 34), (116, 78, 47), (142, 101, 62)]
SPENT = [(86, 68, 54), (108, 86, 66), (130, 104, 80), (152, 126, 98), (168, 142, 112)]
WET = [(30, 18, 11), (48, 30, 19), (68, 44, 28), (94, 64, 41), (118, 90, 70)]
SHEEN = (136, 120, 112)
BONE, BONE_SHADE = (216, 210, 192), (170, 162, 142)  # off-white: a granule of meal, not a glint
STRAW, STRAW_END = (158, 126, 80), (122, 92, 56)
MOULD = (70, 74, 40)
KEEP = (BONE, BONE_SHADE)

# The crumbs, laid by hand: '#' is crumb, '.' the soil between. Fifteen clods of every size, none touching another but
# at a corner (the crevice between them is what shows them as clods), and wrapping at the edges so the face tiles.
CRUMBS = [
    ".##..#....###...",
    "####.##..#####..",
    ".###.....####..#",
    "..#..##...##..##",
    ".....###......##",
    "##...###..##....",
    "###...#..####...",
    ".##.......####.#",
    "......##...##.##",
    ".###.####.....##",
    "####.####..#....",
    ".##...##..###...",
    ".......#...###..",
    "##..##.......#..",
    "###.###.##......",
    ".......###....#.",
]
# What the feeding leaves, each with the least richness it shows at: bone meal on crumb tops (a fleck and its shade),
# straw (start, step, length), leaf mould between the crumbs.
FLECKS = [(2, 0, 1), (9, 6, 1), (13, 1, 2), (5, 9, 2), (10, 11, 3)]
STRAWS = [((4, 7), (1, 0), 3, 1), ((12, 14), (1, 1), 2, 2), ((13, 9), (0, 1), 2, 3)]
MOULDS = [(4, 1, 1), (8, 4, 2), (3, 12, 2), (12, 4, 3)]
# The furrows of the tilled top: the trough row of each, and where it runs a pixel high or low.
TROUGHS = {3: {4: -1, 5: -1, 12: 1}, 7: {8: 1, 9: 1, 14: -1, 15: -1}, 11: {1: 1, 2: 1, 10: -1}, 15: {6: -1, 7: -1, 13: 1}}
SHEENS = [(2, 0), (9, 4), (14, 8), (6, 12), (11, 1)]


def mix(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def palette(r, wet=False):
    t = r / 3
    pal = [mix(s, f, t) for s, f in zip(SPENT, FRESH)]
    if wet:
        pal = [mix(w, p, 0.3 * (1 - t) + 0.1) for w, p in zip(WET, pal)]
    return pal


def wrap(x, y):
    return x % N, y % N


def crumb(x, y):
    x, y = wrap(x, y)
    return CRUMBS[y][x] == "#"


def clods():
    """The crumbs as clods: the sets of their cells."""
    seen, found = set(), []
    for y in range(N):
        for x in range(N):
            if crumb(x, y) and (x, y) not in seen:
                stack, cells = [(x, y)], set()
                while stack:
                    cell = stack.pop()
                    if cell not in cells:
                        cells.add(cell)
                        cx, cy = cell
                        stack += [n for n in (wrap(cx + 1, cy), wrap(cx - 1, cy), wrap(cx, cy + 1), wrap(cx, cy - 1))
                                  if crumb(*n) and n not in cells]
                seen |= cells
                found.append(cells)
    return found


def loam(r, pal, raked=False):
    """The soil at richness r. Raked (the tilled top), the crumbs are broken down: no shadows of their own, so the furrows
    read over them."""
    deep, dark, body, lit, high = pal
    img = Image.new("RGBA", (N, N), dark + (255,))
    px = img.load()
    for y in range(N):
        for x in range(N):
            if not raked and not crumb(x, y) and (crumb(x, y - 1) or (r >= 2 and crumb(x - 1, y))):
                px[x, y] = deep + (255,)
    for cells in clods():
        for cx, cy in cells:
            px[cx, cy] = (lit if not crumb(cx, cy - 1) else body) + (255,)
        if len(cells) >= 7 and r >= 1:
            cy, cx = min((cy, cx) for cx, cy in cells if not crumb(cx, cy - 1))
            px[cx, cy] = high + (255,)
    for x, y, least in MOULDS:
        if r >= least:
            px[x, y] = MOULD + (255,)
    for (x, y), (dx, dy), length, least in STRAWS:
        if r >= least:
            for k in range(length):
                px[wrap(x + dx * k, y + dy * k)] = (STRAW if k < length - 1 else STRAW_END) + (255,)
    for x, y, least in FLECKS:
        if r >= least:
            px[x, y] = BONE + (255,)
            px[wrap(x + 1, y)] = BONE_SHADE + (255,)
    return img


def farmland(r, wet):
    pal = palette(r, wet)
    deep, dark, body, lit, high = pal
    img = loam(r, pal, raked=True)
    px = img.load()
    for row, wobble in TROUGHS.items():
        for x in range(N):
            y = (row + wobble.get(x, 0)) % N
            px[x, y] = deep + (255,)
            if px[x, (y - 1) % N][:3] not in KEEP:
                px[x, (y - 1) % N] = dark + (255,)
            if px[x, (y + 1) % N][:3] not in KEEP + (deep,):
                px[x, (y + 1) % N] = high + (255,)
    if wet:
        for x, row in SHEENS:
            y = (row + TROUGHS.get((row + 3) % N, {}).get(x, 0)) % N
            if px[x, y][:3] not in KEEP:
                px[x, y] = SHEEN + (255,)
    return img


def finish(img, seed):
    return texkit.shade(img, seed, clusters=0.45, grain=0.12, size=4, keep=KEEP, tile=True, material="plain")


def tilled_top(r, wet, seed):
    """The tilled top, finished, then turned so its furrows run down it as vanilla's farmland's do. Turned about the
    diagonal (a transpose), the side each furrow is lit on moves from its bottom to its left: still towards the light."""
    return finish(farmland(r, wet), seed).transpose(Image.Transpose.TRANSPOSE)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def tilled(side, top):
    # the top stands a pixel below the block's: not culled by a block above it (as vanilla's farmland is not), or a
    # block set on it left a slit looking into the hollow soil
    faces = {"up": {"texture": "#top"}, "down": {"texture": "#side", "cullface": "down"}}
    for face in ("north", "east", "south", "west"):
        faces[face] = {"texture": "#side", "uv": [0, 1, 16, 16], "cullface": face}
    return {"parent": "minecraft:block/block", "textures": {"particle": side, "side": side, "top": top},
            "elements": [{"from": [0, 0, 0], "to": [16, 15, 16], "faces": faces}]}


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for r in RICHNESS:
        finish(loam(r, palette(r)), 400 + r).save(TEXTURES / f"nourished_soil_{r}.png")
        tilled_top(r, False, 500 + r).save(TEXTURES / f"nourished_soil_farmland_{r}.png")
        tilled_top(r, True, 600 + r).save(TEXTURES / f"nourished_soil_farmland_moist_{r}.png")
        side = f"nookworks:block/nourished_soil_{r}"
        write_json(ASSETS / f"models/block/nourished_soil_{r}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": side}})
        write_json(ASSETS / f"models/block/nourished_soil_tilled_{r}.json", tilled(side, f"nookworks:block/nourished_soil_farmland_{r}"))
        write_json(ASSETS / f"models/block/nourished_soil_tilled_moist_{r}.json",
                   tilled(side, f"nookworks:block/nourished_soil_farmland_moist_{r}"))
    variants = {}
    for moisture in range(8):
        for r in RICHNESS:
            for till in (False, True):
                model = f"nourished_soil_{r}" if not till else f"nourished_soil_tilled_moist_{r}" if moisture == 7 \
                    else f"nourished_soil_tilled_{r}"
                variants[f"moisture={moisture},richness={r},tilled={str(till).lower()}"] = {"model": f"nookworks:block/{model}"}
    write_json(ASSETS / "blockstates/nourished_soil.json", {"variants": variants})
    write_json(ASSETS / "items/nourished_soil.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/nourished_soil_3"}})
    print("nourished soil: 12 textures, 12 models, blockstate, item")


if __name__ == "__main__":
    main()
