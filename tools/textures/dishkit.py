"""Dishkit: what the dish generators share (gen_dishes.py, gen_dishes_vanilla.py, gen_dishes_cooking.py) - trial of
the cooking review, 2026-11-14.

A dish model is a handful of boxes in a block's space, centred on (8, 8), its foot at y 0, drawn in whole pixels as
gen_tableware.py's four dishes are. Its textures are 16x16 pictures in textures/block/dish/: a face with no uv of its
own takes the part of the picture that lies under it, so a "plan" picture is the dish seen from above, pixel for pixel,
and a "side" picture is read by height, row 15 the bottom pixel. Every pixel is opaque (a solid model shows a clear one
black).

    tex(name, paint, seed)      a picture: paint(x, y) -> (r, g, b); returns its texture id
    mat(name, colours, ...)     a plain material: its colours mixed in clumps
    put(path, textures, elements)   a model, under models/ (dish/minecraft/bread)
    mask(rows, x0, z0)          inside(x, z) of a shape drawn as rows of text, for gen_tableware.blob
    save_all()                  writes every picture and model registered
"""
import json
import math
from pathlib import Path

from PIL import Image

import gen_tableware as tw
import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
TEX = ASSETS / "textures/block/dish"

box, blob, disc, turned, wobble = tw.box, tw.blob, tw.disc, tw.turned, tw.wobble

_TEXTURES = {}
_MODELS = {}


def tex(name, paint, seed, preset=None, keep=(), raw=False):
    """Registers picture {@code name}: paint(x, y) gives each pixel's colour. Finished with texkit.shade (SMOOTH unless
    another preset is given) unless {@code raw}; colours in {@code keep} stay exact."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for y in range(16):
        for x in range(16):
            c = paint(x, y)
            img.putpixel((x, y), tuple(c[:3]) + (c[3] if len(c) > 3 else 255,))
    if not raw:
        img = texkit.shade(img, seed, keep=list(keep), **(preset or texkit.SMOOTH))
    _TEXTURES[name] = img
    return "nookworks:block/dish/" + name


def mat(name, colours, seed, weights=None, clump=2, preset=None, keep=()):
    """A plain material: {@code colours} mixed in clumps {@code clump} pixels across, by {@code weights} (parts in a
    hundred each, the last taking what is left)."""
    weights = weights or [100 // len(colours)] * (len(colours) - 1)

    def paint(x, y):
        n = wobble(x // clump + (y // clump) % 2, y // clump, seed, 100)
        if clump > 1 and wobble(x, y, seed + 7, 5) == 0:
            n = wobble(x, y, seed + 3, 100)  # a single pixel off its clump, here and there
        total = 0
        for i, w in enumerate(weights):
            total += w
            if n < total:
                return colours[i]
        return colours[len(weights)]
    return tex(name, paint, seed, preset=preset, keep=keep)


def rows_by_height(bands, default):
    """A side picture's painter: bands is [(height in pixels from the foot, colour), ...], lowest first; a pixel takes
    the colour of the first band it is under."""
    def paint(x, y):
        h = 15 - y
        for top, colour in bands:
            if h < top:
                return colour(x, y) if callable(colour) else colour
        return default(x, y) if callable(default) else default
    return paint


def mask(rows, x0, z0, chars="#"):
    """inside(x, z) of a shape drawn as text rows: row 0 at z0, column 0 at x0, a pixel each; any of {@code chars} is in."""
    def inside(x, z):
        i, j = int(math.floor(x - x0)), int(math.floor(z - z0))
        return 0 <= j < len(rows) and 0 <= i < len(rows[j]) and rows[j][i] in chars
    return inside


def cells(rows, x0, z0):
    """The pixel of each character of {@code rows}: yields (x, y, char)."""
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            yield x0 + i, z0 + j, ch


def ubox(frm, to, faces):
    """An element whose faces are given outright: {"up": "key"} or {"up": ("key", (u0, v0, u1, v1))}; a face not named
    is left out."""
    out = {}
    for face, f in faces.items():
        out[face] = {"texture": "#" + f} if isinstance(f, str) else {"texture": "#" + f[0], "uv": list(f[1])}
    return {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": out}


def glow(elements, light):
    """The elements given a light of their own (glow berries)."""
    for e in elements:
        e["light_emission"] = light
    return elements


def shifted(elements, dx=0.0, dy=0.0, dz=0.0):
    """Copies of {@code elements} moved; faces with no uv keep reading the picture under where they were drawn."""
    out = []
    for e in elements:
        c = json.loads(json.dumps(e))
        x0, y0, z0 = e["from"]
        x1, y1, z1 = e["to"]
        default = {"up": (x0, z0, x1, z1), "down": (x0, 16 - z1, x1, 16 - z0),
                   "north": (16 - x1, 16 - y1, 16 - x0, 16 - y0), "south": (x0, 16 - y1, x1, 16 - y0),
                   "west": (z0, 16 - y1, z1, 16 - y0), "east": (16 - z1, 16 - y1, 16 - z0, 16 - y0)}
        for face, f in c["faces"].items():
            f.setdefault("uv", [round(min(16, max(0, v)), 3) for v in default[face]])
        c["from"] = [round(x0 + dx, 3), round(y0 + dy, 3), round(z0 + dz, 3)]
        c["to"] = [round(x1 + dx, 3), round(y1 + dy, 3), round(z1 + dz, 3)]
        if "rotation" in c:
            o = c["rotation"]["origin"]
            c["rotation"]["origin"] = [round(o[0] + dx, 3), round(o[1] + dy, 3), round(o[2] + dz, 3)]
        out.append(c)
    return out


def put(path, textures, elements, translucent=False, cutout=False):
    """Registers model {@code path} (under models/): its textures by key (the first is the particle) and its elements."""
    m = {"parent": "minecraft:block/block", "textures": dict(textures, particle=list(textures.values())[0]), "elements": elements}
    if translucent:
        m["render_type"] = "minecraft:translucent"
    elif cutout:
        m["render_type"] = "minecraft:cutout"
    _MODELS[path] = m
    return m


def models():
    return _MODELS


def save_all():
    TEX.mkdir(parents=True, exist_ok=True)
    for name, img in _TEXTURES.items():
        img.save(TEX / f"{name}.png")
    for path, model in _MODELS.items():
        tw.write(ASSETS / "models" / f"{path}.json", model)
    print(f"wrote {len(_TEXTURES)} dish pictures and {len(_MODELS)} dish models")
