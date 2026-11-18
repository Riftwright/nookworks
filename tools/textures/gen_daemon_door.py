"""The Dock-a-Daemon Door and its Destination Panel: textures, models and blockstates.

The door is a sliding door (the user's pick, 2026-10-04, after a photo of one): oiled Dawnwood, as the If Mirror is,
four boards with three slits of dark glass between them, hung from a rail along the top and sliding along the wall in
front of it. Its fittings are galvanised zinc, as the Trash Can is: a pull bar on two plates, an old key's escutcheon
under it, the hangers at the top, caps at the slits' ends. The leaf and the rail are drawn by DaemonDoorRenderer - they
slide, and stand off the wall - from one texture laid out below; the block's own model has nothing but its particle.

- entity/daemon_door.png          the leaf (front, back, edges), the rail, the zinc of the handle, 64x64, laid out as
                                  DaemonDoorRenderer reads it (its LEAF_FRONT etc. must match)
- block/daemon_door.png           the particle: a piece of the leaf
- item/daemon_door.png            the door in the slot
- block/destination_panel.png     the panel: its front (10x12 at 3,2), the side and edge strips, the knob - oiled
                                  Dawnwood framing a zinc-rimmed screen
- block/destination_panel_screen.png   the screen lit: a short list, one entry picked (laid over it, giving off light)
- item/destination_panel.png      the panel in the slot
- misc/daemon_door_light.png      plain white, which the renderer colours pixel by pixel

and the door's and panel's blockstates and models, and the item models. Run from the repo root:
python tools/textures/gen_daemon_door.py

Ours are shaded with texkit.shade: the Dawnwood as wood, the zinc as metal.
"""
import json
import random
from pathlib import Path

from PIL import Image

import itemkit
import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
BLOCK = ASSETS / "textures/block"
ITEM = ASSETS / "textures/item"
MISC = ASSETS / "textures/misc"
ENTITY = ASSETS / "textures/entity"
CLEAR = (0, 0, 0, 0)

# Dawnwood oiled for furniture, as the If Mirror's (gen_if_mirror.WOOD), with a deeper shade under it
WOOD = [(44, 41, 24), (58, 54, 32), (76, 72, 45), (96, 92, 59), (118, 114, 75), (140, 136, 91), (160, 156, 107)]
# galvanised zinc, as the Trash Can's (gen_trash_can), and a dark for a keyhole and screws
ZINC_HI = (214, 218, 220)
ZINC_LIT = (192, 197, 200)
ZINC = (170, 176, 180)
ZINC_LOW = (146, 152, 158)
ZINC_SEAM = (112, 118, 124)
ZINC_EDGE = (74, 78, 84)
ZINC_DARK = (44, 48, 54)
# the slits' glass: dark and see-through, a little; the gate beyond shows in it
GLASS = (30, 38, 44, 120)
GLASS_SHINE = (110, 130, 138, 160)
GLASS_DEEP = (20, 26, 32, 175)

# The leaf, as seen from its front: four boards across - (first column, last column, base shade) - with a slit of glass
# between each two, from row 2 to row 29. The first board is the stile the handle is on.
BOARDS = [(0, 3, 4), (5, 7, 5), (9, 11, 4), (13, 15, 5)]
SLITS = (4, 8, 12)
SLIT_TOP, SLIT_BOTTOM = 2, 29
# the handle's two plates (rows), the escutcheon's top row, the hangers' columns: DaemonDoorRenderer.HANDLE matches
HANDLE_PLATES = (10, 20)
ESCUTCHEON = 23
HANGERS = (2, 13)

# Where each part sits in entity/daemon_door.png (x, y, w, h). DaemonDoorRenderer's UV table must match.
LAYOUT = {
    "leaf_front": (0, 0, 16, 32),
    "leaf_back": (16, 0, 16, 32),
    "leaf_side": (32, 0, 2, 32),
    "leaf_end": (34, 0, 16, 2),
    "rail_front": (0, 32, 32, 3),
    "rail_top": (0, 35, 32, 3),
    "rail_end": (32, 32, 3, 3),
    "zinc": (48, 32, 4, 12),
}


def px(img, x, y, colour):
    img.putpixel((x, y), tuple(colour) + ((255,) if len(colour) == 3 else ()))


def leaf_face(seed, back):
    """A face of the leaf, 16x32. The back is plainer: no handle bar plates, a flush pull cut into the stile instead."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 32))
    for (x0, x1, base) in BOARDS:
        for x in range(x0, x1 + 1):
            for y in range(32):
                c = WOOD[base]
                if x == x0:
                    c = WOOD[min(6, base + 1)]  # its lit edge, towards the light (upper left)
                if x == x1:
                    c = WOOD[max(1, base - 2)]  # the shadowed edge by the slit, or the leaf's own edge
                px(img, x, y, c)
        for x in range(x0 + 1, x1):  # grain: long runs a shade off, down the board
            y = rng.randrange(32)
            covered = 0
            while covered < 26:
                length = rng.randint(4, 11)
                shade = WOOD[base - 1] if rng.random() < 0.6 else WOOD[min(6, base + 1)]
                for i in range(length):
                    px(img, x, (y + i) % 32, shade)
                gap = rng.randint(2, 5)
                y = (y + length + gap) % 32
                covered += length + gap
        if rng.random() < 0.6 and x1 - x0 >= 2:  # now and then a small knot
            kx = rng.randint(x0 + 1, x1 - 1)
            ky = rng.randint(4, 27)
            px(img, kx, ky, WOOD[max(0, base - 2)])
            px(img, kx, ky + 1, WOOD[max(1, base - 1)])
    for x in range(16):  # its top lit, its foot in shadow
        px(img, x, 0, WOOD[6] if x not in SLITS else WOOD[5])
        px(img, x, 31, WOOD[1])
    for x in SLITS:  # glass a pixel back, its top in the cap's shadow, a shine high up; zinc caps at the ends
        for y in range(SLIT_TOP, SLIT_BOTTOM + 1):
            img.putpixel((x, y), GLASS_DEEP if y == SLIT_TOP else GLASS_SHINE if y in (4, 5, 6) and x != 8 else GLASS)
        px(img, x, SLIT_TOP - 1, ZINC_LIT)
        px(img, x, SLIT_BOTTOM + 1, ZINC_LOW)
    for x in HANGERS:  # the hangers it rides the rail by
        px(img, x, 0, ZINC_HI)
        px(img, x, 1, ZINC_LOW)
    if back:
        for y in range(13, 19):  # a pull let into the stile, its far side lit
            px(img, 1, y, WOOD[1])
            px(img, 2, y, WOOD[2] if y in (13, 18) else WOOD[5])
    else:
        for y in HANDLE_PLATES:  # the bar's two plates, a screw in each
            px(img, 1, y, ZINC_LIT)
            px(img, 2, y, ZINC)
            px(img, 1, y + 1, ZINC)
            px(img, 2, y + 1, ZINC_SEAM)
        y = ESCUTCHEON  # an old key's escutcheon under it
        px(img, 1, y, ZINC_LIT)
        px(img, 2, y, ZINC)
        px(img, 1, y + 1, ZINC_DARK)
        px(img, 2, y + 1, ZINC_LOW)
        px(img, 1, y + 2, ZINC_LOW)
        px(img, 2, y + 2, ZINC_SEAM)
    keep = [GLASS, GLASS_SHINE, GLASS_DEEP, ZINC_HI, ZINC_LIT, ZINC, ZINC_LOW, ZINC_SEAM, ZINC_EDGE, ZINC_DARK]
    return texkit.shade(img, seed, keep=keep, material="wood", clusters=0.55, grain=0.06, size=3, reach=1)


def leaf_side():
    """The leaf's edges, 2 wide: end grain, lit on the face side."""
    img = Image.new("RGBA", (2, 32))
    for y in range(32):
        px(img, 0, y, WOOD[4] if y % 7 else WOOD[3])
        px(img, 1, y, WOOD[3] if y % 5 else WOOD[2])
    return img


def leaf_end():
    """Its top and foot, 16 by 2."""
    img = Image.new("RGBA", (16, 2))
    for x in range(16):
        px(img, x, 0, WOOD[5] if x % 4 else WOOD[4])
        px(img, x, 1, WOOD[4] if x % 4 else WOOD[3])
    for x in HANGERS:
        px(img, x, 0, ZINC)
        px(img, x, 1, ZINC_LOW)
    return img


def rail(seed):
    """The rail along the top, 32 long: its face 3 high (lit top, shadowed foot, grain along), its top and foot, its ends."""
    rng = random.Random(seed)
    front = Image.new("RGBA", (32, 3))
    for x in range(32):
        px(front, x, 0, WOOD[5])
        px(front, x, 1, WOOD[3])
        px(front, x, 2, WOOD[1])
    x = 0
    while x < 32:  # grain along it
        length = rng.randint(3, 8)
        for i in range(length):
            if x + i < 32:
                px(front, x + i, 1, WOOD[2] if rng.random() < 0.6 else WOOD[4])
        x += length + rng.randint(2, 6)
    for x in (1, 30):  # a zinc bracket at each end, a screw in it
        px(front, x, 0, ZINC_LIT)
        px(front, x, 1, ZINC_DARK)
        px(front, x, 2, ZINC_LOW)
    top = Image.new("RGBA", (32, 3))
    for x in range(32):
        for y in range(3):
            px(top, x, y, WOOD[4] if (x + y) % 9 else WOOD[3])
    end = Image.new("RGBA", (3, 3))
    for x in range(3):
        for y in range(3):
            px(end, x, y, WOOD[2] if (x + y) % 2 else WOOD[3])
    return front, top, end


def zinc_strip():
    """The zinc the handle's bar and posts are cut from, 4 by 12: lit along its left, its spangle in soft flakes."""
    rng = random.Random(3)
    img = Image.new("RGBA", (4, 12))
    for y in range(12):
        for x, c in enumerate((ZINC_HI, ZINC_LIT, ZINC, ZINC_LOW)):
            px(img, x, y, c)
    for _ in range(6):
        x, y = rng.randrange(4), rng.randrange(12)
        px(img, x, y, ZINC_LIT if rng.random() < 0.5 else ZINC_LOW)
    for x in range(4):
        px(img, x, 0, ZINC_HI)
        px(img, x, 11, ZINC_SEAM)
    return img


def entity_texture():
    img = Image.new("RGBA", (64, 64), CLEAR)
    parts = {"leaf_front": leaf_face(21, False), "leaf_back": leaf_face(22, True), "leaf_side": leaf_side(), "leaf_end": leaf_end(),
             "zinc": zinc_strip()}
    parts["rail_front"], parts["rail_top"], parts["rail_end"] = rail(5)
    for name, (x, y, w, h) in LAYOUT.items():
        part = parts[name]
        assert part.size == (w, h), (name, part.size)
        img.paste(part, (x, y))
    # the leaf's foot below its top (leaf_end is 16x2: the top at y 0-1, the foot under it at y 2-3)
    foot = leaf_end().transpose(Image.FLIP_TOP_BOTTOM)
    img.paste(foot, (34, 2))
    return img


def particle(entity):
    """The block's particle: a piece of the leaf's front, glass made solid."""
    piece = entity.crop((0, 8, 16, 24))
    out = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            r, g, b, a = piece.getpixel((x, y))
            out.putpixel((x, y), (r, g, b, 255))
    return out


# The door in the slot (itemkit), tall as a door is and filling the slot's height as vanilla's doors do: the leaf hung
# from a 1-px zinc rail no wider than itself, the two hangers peeping over the rail; four boards, each lit along its left
# and in shade along its right, with a full-height slit of dark glass between each two, a gleam at its top under the
# leaf's head and the gate's violet haze in its middle (the Dawnwood Door's glass is clear); the zinc pull bar on its two
# plates on the stile and the old key's escutcheon under it; the head lit, the foot in shade. Ringed once round it all in
# the wood's own darkest (the rail in the zinc's): the glass and the keyhole count as wood for it, so nothing is ringed
# near black. (The leaf of three boards under a wide two-row rail read as a ribbed bin with its lid, as the Trash Can.)
DOOR_ITEM = itemkit.check([
    ".....T....T.....",
    "...rRRRRRRRRs...",
    "...6666666665...",
    "...5545545544...",
    "...55G54G54G4...",
    "...5yg43g43g4...",
    "...5zq43q43q4...",
    "...5zh43h43h4...",
    "...5zH43H43H3...",
    "...5zh43h43h3...",
    "...5yq43q43q3...",
    "...54g43g43g3...",
    "...4xg43g42g3...",
    "...4k33333333...",
    "...2222222221...",
    "................",
])
DOOR_GLASS = {"g": (34, 42, 52), "G": (150, 176, 186), "q": (50, 46, 72), "h": (70, 58, 98), "H": (96, 76, 132)}
DOOR_ITEM_COLOURS = dict({str(i): WOOD[i] for i in range(1, 7)}, T=ZINC_HI, R=ZINC_HI, r=ZINC_LIT, s=ZINC, z=ZINC_HI,
                         y=ZINC_LOW, x=ZINC_SEAM, k=ZINC_DARK, **DOOR_GLASS)
# what each pixel counts as for the ring: the rail and hangers zinc, everything on the leaf its wood
DOOR_ITEM_RING = dict({str(i): WOOD[i] for i in range(1, 7)}, T=ZINC_EDGE, R=ZINC_EDGE, r=ZINC_EDGE, s=ZINC_EDGE,
                      **{ch: WOOD[4] for ch in "zyxk"}, **{ch: WOOD[3] for ch in DOOR_GLASS})


def door_item():
    return itemkit.sprite(DOOR_ITEM, DOOR_ITEM_COLOURS, lit=0.95, dark=0.78, ring_from=DOOR_ITEM_RING)


# ---- the Destination Panel ----

# Its front, 10x12, as it is drawn at (3, 2) in its texture: e the wood's edge, p wood, H/B/b the screen's zinc rim
# lit/mid/low, g the screen's glass (dark, the lit screen laid over it), s/S the seat the knob stands in.
PANEL_FRONT = [
    "eeeeeeeeee",
    "eHHHHHHHBe",
    "eHggggggbe",
    "eHggggggbe",
    "eHggggggbe",
    "eHggggggbe",
    "eHggggggbe",
    "eBbbbbbbbe",
    "eppppppppe",
    "eppsssSppe",
    "eppsSSSppe",
    "eeeeeeeeee",
]
PANEL_COLOURS = {"e": WOOD[1], "p": WOOD[4], "H": ZINC_HI, "B": ZINC, "b": ZINC_SEAM, "g": (22, 30, 36), "s": WOOD[2],
                 "S": WOOD[3]}
# The lit screen, 6x5 over the glass: a list of three places, the middle one picked. k the screen's dark, t a name, T the
# picked name, > the marker, and each entry's colour: o overworld sky, n nether red, d the End's violet.
PANEL_SCREEN = [
    "kokttk",
    "kkkkkk",
    ">knTTT",
    "kkkkkk",
    "kdkttk",
]
SCREEN_COLOURS = {"k": (10, 20, 24), "t": (58, 120, 116), "T": (150, 236, 222), ">": (236, 200, 120),
                  "o": (93, 143, 232), "n": (210, 80, 28), "d": (122, 92, 200)}


def panel():
    img = Image.new("RGBA", (16, 16), CLEAR)
    for fy, row in enumerate(PANEL_FRONT):
        for fx, ch in enumerate(row):
            px(img, 3 + fx, 2 + fy, PANEL_COLOURS[ch])
    # the sides (x 0-1) and the top and foot (y 0-1): wood, edged dark
    for y in range(2, 14):
        px(img, 0, y, WOOD[1])
        px(img, 1, y, WOOD[4])
    for x in range(3, 13):
        px(img, x, 0, WOOD[5])
        px(img, x, 1, WOOD[1])
    # the knob (14-15, 0-1): zinc
    px(img, 14, 0, ZINC_HI)
    px(img, 15, 0, ZINC)
    px(img, 14, 1, ZINC)
    px(img, 15, 1, ZINC_SEAM)
    return texkit.shade(img, 141, keep=(WOOD[1], ZINC_HI, (22, 30, 36), ZINC, ZINC_SEAM), clusters=0.4, grain=0.0, size=4, reach=1,
                        material={**{c: "metal" for c in (ZINC_HI, ZINC_LIT, ZINC, ZINC_LOW, ZINC_SEAM, ZINC_EDGE, ZINC_DARK)},
                                  None: "wood"})


def panel_screen():
    img = Image.new("RGBA", (16, 16), CLEAR)
    for sy, row in enumerate(PANEL_SCREEN):
        for sx, ch in enumerate(row):
            px(img, 5 + sx, 4 + sy, SCREEN_COLOURS[ch])
    return img


def panel_item():
    """The panel in the slot: its front, lit, on its own."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    face = panel()
    screen = panel_screen()
    for y in range(12):
        for x in range(10):
            c = screen.getpixel((3 + x, 2 + y)) if screen.getpixel((3 + x, 2 + y))[3] else face.getpixel((3 + x, 2 + y))
            img.putpixel((3 + x, 2 + y), c)
    return img


# ---- models ----

def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def door_models():
    """The door's blockstate: every state the one model, its particle only - the renderer draws the leaf and the rail."""
    models = ASSETS / "models/block"
    for old in models.glob("daemon_door_*.json"):
        old.unlink()  # the eight hinged models of the hatch it was
    write_json(models / "daemon_door.json", {"textures": {"particle": "nookworks:block/daemon_door"}})
    variants = {}
    for facing in ("east", "north", "south", "west"):
        for part in ("lower", "upper"):
            for hinge in ("left", "right"):
                for opened in (False, True):
                    variants[f"facing={facing},half={part},hinge={hinge},open={str(opened).lower()}"] = {"model": "nookworks:block/daemon_door"}
    write_json(ASSETS / "blockstates/daemon_door.json", {"variants": dict(sorted(variants.items()))})
    write_json(ASSETS / "models/item/daemon_door.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "nookworks:item/daemon_door"}})
    write_json(ASSETS / "items/daemon_door.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/daemon_door"}})


def panel_models():
    """The panel facing north on a wall to its south: the plate, a zinc knob under the screen, the screen's glow."""
    face = {"texture": "#panel"}
    write_json(ASSETS / "models/block/destination_panel.json", {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"panel": "nookworks:block/destination_panel", "screen": "nookworks:block/destination_panel_screen",
                     "particle": "nookworks:block/destination_panel"},
        "elements": [
            {"name": "plate", "from": [3, 2, 14], "to": [13, 14, 16], "faces": {
                "north": dict(face, uv=[3, 2, 13, 14]),
                "south": dict(face, uv=[3, 2, 13, 14], cullface="south"),
                "east": dict(face, uv=[0, 2, 2, 14]),
                "west": dict(face, uv=[0, 2, 2, 14]),
                "up": dict(face, uv=[3, 0, 13, 2]),
                "down": dict(face, uv=[3, 0, 13, 2])}},
            {"name": "knob", "from": [7, 3, 13], "to": [9, 5, 14], "faces": {
                "north": dict(face, uv=[14, 0, 16, 2]),
                "east": dict(face, uv=[14, 0, 15, 2]),
                "west": dict(face, uv=[15, 0, 16, 2]),
                "up": dict(face, uv=[14, 0, 16, 1]),
                "down": dict(face, uv=[14, 1, 16, 2])}},
            {"name": "screen", "from": [5, 7, 13.98], "to": [11, 12, 13.98], "light_emission": 15,
             "shade_direction_override": "up", "faces": {"north": {"texture": "#screen", "uv": [5, 4, 11, 9]}}},
        ],
    })
    write_json(ASSETS / "blockstates/destination_panel.json", {"variants": {
        f"facing={f}": ({"model": "nookworks:block/destination_panel", "y": y} if y else {"model": "nookworks:block/destination_panel"})
        for f, y in (("east", 90), ("north", 0), ("south", 180), ("west", 270))}})
    write_json(ASSETS / "models/item/destination_panel.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "nookworks:item/destination_panel"}})
    write_json(ASSETS / "items/destination_panel.json",
               {"model": {"type": "minecraft:model", "model": "nookworks:item/destination_panel"}})


def main():
    ENTITY.mkdir(parents=True, exist_ok=True)
    entity = entity_texture()
    entity.save(ENTITY / "daemon_door.png")
    particle(entity).save(BLOCK / "daemon_door.png")
    for old in ("daemon_door_top.png", "daemon_door_bottom.png"):
        if (BLOCK / old).exists():
            (BLOCK / old).unlink()  # the hatch's leaf
    door_item().save(ITEM / "daemon_door.png")
    panel().save(BLOCK / "destination_panel.png")
    panel_screen().save(BLOCK / "destination_panel_screen.png")
    texkit.outline(panel_item()).save(ITEM / "destination_panel.png")
    MISC.mkdir(parents=True, exist_ok=True)
    Image.new("RGBA", (4, 4), (255, 255, 255, 255)).save(MISC / "daemon_door_light.png")
    door_models()
    panel_models()
    print("wrote the Dock-a-Daemon Door's and the Destination Panel's textures, models and blockstates")


if __name__ == "__main__":
    main()
