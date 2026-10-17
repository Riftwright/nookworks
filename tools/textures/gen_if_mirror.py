"""The If Mirror: a cheval glass - a tall mirror swung between two posts on splayed feet - in polished Dawnwood.

The glass is silvered (old mirrors were: tin and mercury). The renderer (IfMirrorRenderer) draws over it the dice as
they are thrown and, once all six are, the If beyond; this draws the glass as it is before.

- block/if_mirror_frame.png      polished Dawnwood: the posts, rails, feet, crest and the glass's wooden back
- block/if_mirror_glass_top.png, if_mirror_glass_bottom.png   the silvered glass, upper and lower half
- item/if_mirror.png             the mirror in the slot, its glass onto the If's dawn

and the mirror's two models, its blockstate and its item model. Run from the repo root:
python tools/textures/gen_if_mirror.py
"""
import json
import random
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
BLOCK = ASSETS / "textures/block"
ITEM = ASSETS / "textures/item"
CLEAR = (0, 0, 0, 0)

# Dawnwood's sage heartwood (gen_dawnwood.SAGE), oiled a little darker for a piece of furniture
WOOD = [(58, 54, 32), (76, 72, 45), (96, 92, 59), (118, 114, 75), (140, 136, 91), (160, 156, 107)]
# silvered glass, deep to bright
SILVER = [(108, 120, 132), (140, 152, 164), (168, 182, 192), (196, 208, 216), (226, 234, 240)]
# the If's dawn, for the item's glass: DaemonDestinations' If, top and bottom
DAWN_TOP = (0x74, 0x66, 0xC8)
DAWN_BOTTOM = (0xFF, 0xC7, 0x9A)

# The glass, 10 across and 26 high: the upper half shows rows 0-12, the lower rows 13-25 (see the models below).
GLASS_W, GLASS_H = 10, 26


def px(img, x, y, colour):
    img.putpixel((x, y), tuple(colour) + (255,))


def frame():
    """Polished wood, its grain running up: long streaks a shade either side of the middle, a few dark knots of grain."""
    rng = random.Random(201)
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        base = 2 if x % 4 else 1  # a darker line every four: the edge of each member, where two meet
        for y in range(16):
            px(img, x, y, WOOD[base + (1 if x % 4 == 2 else 0)])
    for _ in range(9):  # grain runs
        x = rng.randrange(16)
        y = rng.randrange(16)
        shade = WOOD[3] if rng.random() < 0.5 else WOOD[1]
        for i in range(rng.randint(3, 7)):
            px(img, x, (y + i) % 16, shade)
    return texkit.finish(img, 201, keep=(WOOD[0],), tile=True, clusters=0.5, grain=0.05, size=4, reach=1)


def glass():
    """
    Silver, a little lighter towards the top: two glints running down from the upper right at a slope of one (a broad
    one and a thin one beside it), a fainter pair lower down, and the rim a shade deeper. Glass gets no grain.
    """
    img = Image.new("RGBA", (GLASS_W, GLASS_H))
    for y in range(GLASS_H):
        for x in range(GLASS_W):
            px(img, x, y, SILVER[3] if y < 9 else SILVER[2])
    for top, widths in ((1, ((0, SILVER[4]), (1, SILVER[4]), (3, SILVER[4]))), (13, ((0, SILVER[3]), (2, SILVER[3])))):
        for offset, colour in widths:
            for i in range(GLASS_W):
                x = GLASS_W - 1 - i
                y = top + i + offset
                if 0 < x < GLASS_W - 1 and 0 < y < GLASS_H - 1:
                    px(img, x, y, colour)
    for y in range(GLASS_H):
        px(img, 0, y, SILVER[1])
        px(img, GLASS_W - 1, y, SILVER[1])
    for x in range(GLASS_W):
        px(img, x, 0, SILVER[1])
        px(img, x, GLASS_H - 1, SILVER[0])
    return img


def glass_halves(g):
    """The glass cut across its middle into the two halves' textures, where the models' faces read them."""
    top = Image.new("RGBA", (16, 16), SILVER[2] + (255,))
    bottom = Image.new("RGBA", (16, 16), SILVER[2] + (255,))
    for y in range(13):
        for x in range(GLASS_W):
            top.putpixel((3 + x, 3 + y), g.getpixel((x, y)))
            bottom.putpixel((3 + x, y), g.getpixel((x, 13 + y)))
    return top, bottom


def item():
    """The mirror upright in the slot: its two posts on their feet, the crest, and the glass onto the If's dawn."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(1, 15):
        for x in (3, 12):
            px(img, x, y, WOOD[2])
        px(img, 4, y, WOOD[1])
        px(img, 11, y, WOOD[1])
    for x in range(4, 12):
        px(img, x, 1, WOOD[3])  # the top rail
        px(img, x, 13, WOOD[2])  # the bottom rail
    for x in range(6, 10):
        px(img, x, 0, WOOD[3])  # the crest
    for x in (2, 3, 4, 11, 12, 13):
        px(img, x, 15, WOOD[1])  # the feet
    for y in range(2, 13):
        t = (y - 2) / 10
        for x in range(5, 11):
            px(img, x, y, tuple(round(DAWN_TOP[i] + (DAWN_BOTTOM[i] - DAWN_TOP[i]) * t) for i in range(3)))
    for y in range(10, 13):  # the If's hills along the foot of the glass
        for x in range(5, 11):
            if y >= 12 - (1 if x in (6, 7) else 0) - (1 if x == 9 else 0):
                px(img, x, y, (0x4E, 0x6E, 0x4A) if y > 10 else (0x8F, 0xAE, 0x6A))
    px(img, 9, 3, (240, 226, 236))  # the glint
    px(img, 10, 2, (240, 226, 236))
    return texkit.outline(img)


# ---- models ----

def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def box(name, start, end, glass_uv=None):
    """An element in the frame's wood on every face; the glass, if {@code glass_uv}, on its front (north) face."""
    faces = {side: {"texture": "#frame"} for side in ("north", "south", "east", "west", "up", "down")}
    if glass_uv is not None:
        faces["north"] = {"texture": "#glass", "uv": glass_uv}
    return {"name": name, "from": start, "to": end, "faces": faces}


def models():
    textures = {"frame": "nookworks:block/if_mirror_frame", "particle": "nookworks:block/if_mirror_frame"}
    write_json(ASSETS / "models/block/if_mirror_lower.json", {
        "parent": "minecraft:block/block",
        "textures": dict(textures, glass="nookworks:block/if_mirror_glass_bottom"),
        "elements": [
            box("foot west", [1, 0, 4], [3, 1, 12]),
            box("foot east", [13, 0, 4], [15, 1, 12]),
            box("post west", [1, 1, 7], [3, 16, 9]),
            box("post east", [13, 1, 7], [15, 16, 9]),
            box("rail", [3, 2, 7], [13, 3, 9]),
            box("glass", [3, 3, 7.5], [13, 16, 8.5], glass_uv=[3, 0, 13, 13]),
        ],
    })
    write_json(ASSETS / "models/block/if_mirror_upper.json", {
        "parent": "minecraft:block/block",
        "textures": dict(textures, glass="nookworks:block/if_mirror_glass_top"),
        "elements": [
            box("post west", [1, 0, 7], [3, 13, 9]),
            box("post east", [13, 0, 7], [15, 13, 9]),
            box("glass", [3, 0, 7.5], [13, 13, 8.5], glass_uv=[3, 3, 13, 16]),
            box("top rail", [1, 13, 7], [15, 14, 9]),
            box("crest", [4, 14, 7.5], [12, 15, 8.5]),
            box("crest top", [6, 15, 7.5], [10, 16, 8.5]),
        ],
    })
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for half in ("lower", "upper"):
            variant = {"model": f"nookworks:block/if_mirror_{half}"}
            if y:
                variant["y"] = y
            variants[f"facing={facing},half={half}"] = variant
    write_json(ASSETS / "blockstates/if_mirror.json", {"variants": dict(sorted(variants.items()))})
    write_json(ASSETS / "models/item/if_mirror.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "nookworks:item/if_mirror"}})
    write_json(ASSETS / "items/if_mirror.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/if_mirror"}})


def main():
    frame().save(BLOCK / "if_mirror_frame.png")
    top, bottom = glass_halves(glass())
    top.save(BLOCK / "if_mirror_glass_top.png")
    bottom.save(BLOCK / "if_mirror_glass_bottom.png")
    item().save(ITEM / "if_mirror.png")
    models()
    print("wrote the If Mirror's textures, models and blockstate")


if __name__ == "__main__":
    main()
