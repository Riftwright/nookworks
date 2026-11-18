"""Nourished Soil: its textures, models, blockstate and item definition. Original pixel art. Deterministic.

Dirt fed with bone meal, and it should look it at a glance - in a bed among plain dirt, and in a chest: a darker,
richer loam. Redrawn quiet (the user, 2026-11-14: 豊かな土やその耕地のテクスチャがちょっと五月蠅い ... かなり昔のマイクラ風で、
テクスチャが浮いて見える; of three looks shown in the game beside vanilla's, they took A, the fine speckle). The first
picture ran from near black to near white (lightness 23-210, 22 colours) with white flecks of bone meal and, tilled,
four thin furrows ruled dark and light; vanilla's dirt is seven colours, lightness 67-144, and its farmland two broad
ridges a block. So, as vanilla's soils are drawn (studied, none of their pixels used):

- four browns of one hue a step apart at each richness, nothing near black or white, the tones in small soft clumps
  and most of the face the two middle ones;
- bone meal as single pale grains, a step and a half lighter than the soil's own light: three on fresh soil, then two,
  one, none - the spent soil has no bone meal left;
- tilled, two broad ridges a block, their troughs where vanilla farmland's lie, so a bed of it in a vanilla field
  lines up; they run down the texture (north to south in the world) as vanilla's do;
- watered (as vanilla's farmland, moisture 7 is the wet look), the same picture darker and more saturated - fresh soil
  much darker, spent soil less so.

As the soil is spent (the block's richness, 3 down to 0) it pales and dries towards plain dirt, and the grains go: a
bed that wants feeding can be seen from across the garden, watered or not. The same clumps lie there at every richness.

- block/nourished_soil_<r>.png                 the soil at richness r (0-3): every face, the tilled block's sides too
- block/nourished_soil_farmland_<r>.png        the tilled top, dry
- block/nourished_soil_farmland_moist_<r>.png  the tilled top, watered
- models/block/nourished_soil_<r>.json, nourished_soil_tilled_<r>.json, nourished_soil_tilled_moist_<r>.json
- blockstates/nourished_soil.json, items/nourished_soil.json (the fresh soil)

Not finished with texkit, on purpose: its extra shades and grain are the noise that was taken out; the ramp's own hue
moves along it instead (the shade a little redder, the light a little yellower). Run from the repo root:
python tools/textures/gen_soil.py
"""
import colorsys
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
TEXTURES = ASSETS / "textures/block"
N = 16
RICHNESS = range(4)

# how much of a face each tone takes, darkest first: most of it the two middle ones, as dirt's is
SOIL_SHARES = [0.14, 0.40, 0.30, 0.16]
TILLED_SHARES = [0.13, 0.26, 0.36, 0.25]
# how far the ridges carry the tilled top over the soil's own clumps
RIDGE = 0.60
# bone meal: a single pale grain each (x, y), and the least richness it still shows at
GRAINS = [(3, 2, 1), (11, 6, 2), (6, 12, 3)]


def hsv(h, s, v):
    return tuple(round(c * 255) for c in colorsys.hsv_to_rgb(h / 360, s, v))


def ramp(r, wet=False):
    """The four browns at richness r (3 fresh, 0 spent), darkest first, and the bone meal's grain."""
    t = r / 3
    hue = 27 - 4 * t                       # redder as it is richer
    sat = 0.42 + 0.20 * t                  # the spent soil is dry and dull
    vals = [(0.37 - 0.14 * t), (0.48 - 0.17 * t), (0.59 - 0.19 * t), (0.70 - 0.20 * t)]
    if wet:
        # watered, fresh soil goes dark and deep; spent soil darkens less, so a bed that wants feeding still shows
        sat = min(0.86, sat + 0.18 * (0.4 + 0.6 * t))
        vals = [v * (0.80 - 0.16 * t) for v in vals]
    # the hue moves along the ramp, as painters move it: the shade a little redder, the light a little yellower
    tones = [hsv(hue + (i - 1.5) * 1.6, min(0.9, sat + (1.5 - i) * 0.03), v) for i, v in enumerate(vals)]
    # a grain of bone meal: paler than the soil's own light by a step and a half, never white (dirt's pebbles are no
    # lighter than dirt's light)
    bone = hsv(40, 0.14, 0.86)
    grain = tuple(round(a + (b - a) * (0.42 if not wet else 0.34)) for a, b in zip(tones[3], bone))
    return tones, grain


def hash01(x, y, seed):
    n = math.sin((x % N) * 127.1 + (y % N) * 311.7 + seed * 74.7) * 43758.5453
    return n - math.floor(n)


def smooth(x, y, seed, period):
    """Value noise that wraps at the face's edges, a lattice point every `period` pixels."""
    gx, gy = x / period, y / period
    x0, y0 = math.floor(gx), math.floor(gy)
    fx, fy = gx - x0, gy - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    m = N // period

    def at(i, j):
        return hash01((i % m) * period, (j % m) * period, seed)
    a, b = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * fx, at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * fx
    return a + (b - a) * fy


def speckle(x, y):
    """The soil's clumps, as a score a pixel (higher is lighter): mostly a pixel or two across, as dirt's are, with only
    a little of anything wider - a wider patch shows as a spot on every block of a field."""
    return 0.10 * smooth(x, y, 11, 4) + 0.42 * smooth(x, y, 12, 2) + 0.48 * hash01(x, y, 13)


def ridge(x):
    """Two ridges a block, their troughs where vanilla farmland's lie (columns 3-4 and 11-12): 0 in the trough, 1 on the crest."""
    return 0.5 - 0.5 * math.cos((x - 3.5) * 2 * math.pi / 8)


def quantise(score, shares):
    """Tone indices from a score a pixel: the lowest scores take the darkest tone, in the shares given (darkest first)."""
    order = sorted(((score[y][x], y, x) for y in range(N) for x in range(N)))
    out = [[0] * N for _ in range(N)]
    cut, k = [], 0
    for w in shares:
        k += w
        cut.append(round(k * N * N))
    tone = 0
    for i, (_, y, x) in enumerate(order):
        while tone < len(cut) - 1 and i >= cut[tone]:
            tone += 1
        out[y][x] = tone
    return out


SOIL = quantise([[speckle(x, y) for x in range(N)] for y in range(N)], SOIL_SHARES)
TILLED = quantise([[RIDGE * ridge(x) + (1 - RIDGE) * speckle(x, y) for x in range(N)] for y in range(N)], TILLED_SHARES)


def picture(tones_at, r, wet=False):
    tones, grain = ramp(r, wet)
    img = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            img.putpixel((x, y), tones[tones_at[y][x]] + (255,))
    for x, y, least in GRAINS:
        if r >= least:
            img.putpixel((x, y), grain + (255,))
    return img


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
        picture(SOIL, r).save(TEXTURES / f"nourished_soil_{r}.png")
        picture(TILLED, r).save(TEXTURES / f"nourished_soil_farmland_{r}.png")
        picture(TILLED, r, wet=True).save(TEXTURES / f"nourished_soil_farmland_moist_{r}.png")
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
