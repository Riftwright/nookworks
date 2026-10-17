"""What Thin Rope hangs up besides a planter: the Filament Bulb, Dried Fish, and the rope strung between posts.

- block/filament_bulb_glass.png, filament_bulb_filament.png, filament_bulb_flex.png, and models/block/filament_bulb.json
  with its blockstate and item: a clear glass bulb (translucent) round a glowing coil (its elements give off light, so it
  shows bright in the dark), on a brass cap (our brass_fitting) and a short black flex up to whatever holds it
- block/filament_bulb_filament_off.png, filament_bulb_glass_off.png and models/block/filament_bulb_off.json: the bulb
  switched off (the user, 2026-10-16), the same drawing with its coil cold - dull tungsten, giving off no light - and its
  glass cooled to a smoky grey with a duller glint (2026-10-17: the amber glass, pale at night, read as still on), for
  the blockstate's lit=false
- item/dried_fish.png: a fish opened out flat and dried (干物), golden, its spine down the middle
- entity/thin_rope_span.png: Thin Rope drawn along a span - four faces of a pixel-thick cord, one to a column, each a
  step on in the twist, a turn of it down the texture (the Thin Rope block's cord colours)

All drawn flat and finished with texkit (not the glass and the filament, which must stay clear and exact). Deterministic.
Run from the repo root:  python tools/textures/gen_hanging.py
"""
import json
import math
from pathlib import Path

from PIL import Image

import texkit
from gen_hanging_planter import CORD

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
CLEAR = (0, 0, 0, 0)
OUTLINE = (40, 30, 26)


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c + (255,) if len(c) == 3 else c)


# ---------------------------------------------------------------------------------------------------------- the bulb
GLASS = (255, 206, 136, 40)        # amber glass, as the old filament bulbs have, warm with the light in it: clear
                                   # enough that the coil reads through it in a slot (at 72 it was a beige box)
GLASS_EDGE = (255, 226, 168, 104)  # where the glass turns away it shows more, and its rim catches the light
GLINT = (255, 250, 236, 184)       # the window caught on its side: a pale streak down the glass
GLASS_OFF = (112, 110, 116, 56)    # switched off: no warmth in it, a smoky grey, still glass (not black)
GLASS_EDGE_OFF = (132, 130, 138, 100)
GLINT_OFF = (176, 178, 186, 120)   # the glint dulled, cool
COIL = [(255, 186, 80), (255, 226, 140), (255, 250, 214)]
COIL_OFF = [(78, 70, 64), (104, 94, 84), (132, 120, 108)]  # the same coil cold: grey-brown tungsten, no glow
WIRE = (104, 92, 80)
FLEX = [(30, 28, 32), (46, 44, 50), (62, 60, 68)]


def glass(body=GLASS, edge=GLASS_EDGE, glint=GLINT):
    """The bulb's glass: nearly clear, a little more at the edges of each face, a glint down the left."""
    img = Image.new("RGBA", (16, 16), body)
    for y in range(16):
        for x in range(16):
            if x in (0,) or y in (0,):
                img.putpixel((x, y), edge)
    for y in range(1, 4):
        img.putpixel((1, y), glint)
    return img


def filament(coil=COIL):
    """Top left, 3 by 6: two support wires down from the stem, and the coil across between them, glowing (or cold)."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(0, 3):
        px(img, 0, y, WIRE)
        px(img, 2, y, WIRE)
    for x, y, c in ((0, 3, 1), (1, 3, 2), (2, 3, 1), (0, 4, 2), (1, 4, 1), (2, 4, 2), (0, 5, 0), (1, 5, 2), (2, 5, 0)):
        px(img, x, y, coil[c])
    return img


def flex():
    """A black flex, twisted as the rope is."""
    img = Image.new("RGBA", (16, 16), FLEX[1] + (255,))
    for y in range(16):
        for x in range(16):
            px(img, x, y, FLEX[(x + y * 2) % 3])
    return img


def face(uv, tex):
    return {"uv": uv, "texture": tex}


def glass_box(frm, to, skip=()):
    """A piece of the bulb's glass: every face but the ones against the next piece, the texture pixel for pixel."""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    w, h, d = x1 - x0, y1 - y0, z1 - z0
    faces = {"north": [0, 0, w, h], "south": [0, 0, w, h], "east": [0, 0, d, h], "west": [0, 0, d, h],
             "up": [0, 0, w, d], "down": [0, 0, w, d]}
    return {"from": list(frm), "to": list(to),
            "faces": {side: face(uv, "#glass") for side, uv in faces.items() if side not in skip}}


def bulb_model(on=True):
    """
    Hanging from the top of the block: a black flex a pixel thick, the brass cap with a collar at its foot, and the glass
    in five pieces - neck, shoulder, the full body, the rounding under it, the tip - so it reads as a bulb, not a box.
    Two crossed planes of filament stand in it, giving off light. They come first: an item's quads are drawn in the
    model's order, not sorted, so a coil drawn after the glass in front of it was hidden by it in a slot.
    """
    filament = [
        {"from": [8, 3, 6.5], "to": [8, 9, 9.5], "light_emission": 15,
         "faces": {"east": face([0, 0, 3, 6], "#filament"), "west": face([0, 0, 3, 6], "#filament")}},
        {"from": [6.5, 3, 8], "to": [9.5, 9, 8], "light_emission": 15,
         "faces": {"north": face([0, 0, 3, 6], "#filament"), "south": face([0, 0, 3, 6], "#filament")}},
    ]
    if not on:  # switched off: the coil cold, lit only as what is round it
        for plane in filament:
            del plane["light_emission"]
    elements = filament + [
        {"from": [7.5, 12, 7.5], "to": [8.5, 16, 8.5], "faces": {s: face([0, 0, 1, 4], "#flex") for s in ("north", "south", "east", "west")}},
        {"from": [6.5, 9.5, 6.5], "to": [9.5, 12, 9.5], "faces": {
            **{s: face([6, 4, 9, 6.5], "#brass") for s in ("north", "south", "east", "west")}, "up": face([6, 8, 9, 11], "#brass")}},
        {"from": [6, 9, 6], "to": [10, 9.5, 10], "faces": {
            **{s: face([6, 7.5, 10, 8], "#brass") for s in ("north", "south", "east", "west")},
            "up": face([6, 6, 10, 10], "#brass"), "down": face([6, 6, 10, 10], "#brass")}},
        glass_box((7, 8, 7), (9, 9, 9), skip=("up", "down")),
        glass_box((6, 7, 6), (10, 8, 10), skip=("down",)),
        glass_box((5.5, 3.5, 5.5), (10.5, 7, 10.5)),
        glass_box((6, 2.5, 6), (10, 3.5, 10), skip=("up",)),
        glass_box((7, 2, 7), (9, 2.5, 9), skip=("up",)),
    ]
    return {"parent": "minecraft:block/block", "render_type": "minecraft:translucent", "ambientocclusion": False,
            "textures": {"particle": "nookworks:block/filament_bulb_glass" + ("" if on else "_off"),
                         "glass": "nookworks:block/filament_bulb_glass" + ("" if on else "_off"),
                         "filament": "nookworks:block/filament_bulb_filament" + ("" if on else "_off"),
                         "flex": "nookworks:block/filament_bulb_flex",
                         "brass": "nookworks:block/brass_fitting"},
            "elements": elements,
            "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [1.1, 1.1, 1.1]},
                        "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
                        "fixed": {"translation": [0, -1, 0], "scale": [1, 1, 1]},
                        "thirdperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 1, 1], "scale": [0.6, 0.6, 0.6]},
                        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 1, 0], "scale": [0.6, 0.6, 0.6]}}}


# ---------------------------------------------------------------------------------------------------------- dried fish
FLESH = [(150, 86, 34), (186, 116, 48), (218, 152, 72), (240, 186, 104), (252, 218, 150)]
SKIN = [(96, 82, 70), (134, 118, 100), (170, 156, 136)]  # dried skin, brown through the silver
SPINE = (120, 70, 30)
EYE = (30, 24, 22)
FIN = [(104, 80, 56), (140, 110, 78), (176, 146, 108)]  # a dried fin, brown through the silver


def dried_fish():
    """
    A fish opened out flat along its back and dried (a 干物), the cut side up, laid on the diagonal as vanilla lays its
    fish: the split head at the upper left with an eye in each half, the body widest near the head and tapering to the
    forked tail at the lower right; the two halves of amber flesh mirrored about the spine, the half towards the light
    the brighter, ribs off the spine in a darker step; a thin rim of dried skin round it. (Drawn level and squared it
    read as a tin; drawn diagonally with a round head and no fins, as a drumstick.) Ringed and finished here: no grain,
    which over so few shapes reads as stripes.
    """
    img = Image.new("RGBA", (16, 16), CLEAR)
    hx, hy, tx, ty = 2.6, 3.4, 12.6, 12.4  # the head's tip and the tail's root
    length = math.dist((hx, hy), (tx, ty))
    ux, uy = (tx - hx) / length, (ty - hy) / length
    for y in range(16):
        for x in range(16):
            ax, ay = x + 0.5 - hx, y + 0.5 - hy
            t = (ax * ux + ay * uy) / length  # 0 at the head, 1 at the tail's root
            d = ax * -uy + ay * ux  # across it: below 0 the half towards the light
            if 0 <= t <= 1:
                half = 3.4 * math.sin(math.pi * min(1.0, (t + 0.05) ** 0.75)) ** 0.7 + 0.4
                if abs(d) > half:
                    continue
                if abs(d) > half - 0.9:
                    c = SKIN[2] if d < 0 else SKIN[1]  # the rim of skin
                elif abs(d) < 0.55:
                    c = SPINE
                elif t < 0.16:
                    c = SKIN[2] if d < 0 else SKIN[1]  # the split head
                else:
                    lit = 3 if d < 0 else 1
                    rib = (round(t * length) + (0 if d < 0 else 1)) % 2 == 0 and abs(d) < half - 1.4
                    c = FLESH[lit - 1] if rib else FLESH[lit]
                px(img, x, y, c)
            elif 1 < t <= 1.32:  # the tail fin, forked
                spread = (t - 1) * length * 1.15 + 0.6
                if abs(d) <= spread and not (t > 1.18 and abs(d) < spread - 1.6):
                    px(img, x, y, FIN[0] if (x + y) % 2 == 0 else FIN[2] if d < 0 else FIN[1])
    img = texkit.outline(img)
    for t, d in ((0.09, -1.3), (0.09, 1.3)):  # an eye in each half of the split head
        px(img, int(hx + ux * t * length - uy * d), int(hy + uy * t * length + ux * d), EYE)
    return img


def outline(img):
    """Where the ring goes round the shape: it is drawn at save time by texkit.icon, in the shape's own colours as
    vanilla rings its items, so anything drawn after this call is left unringed."""
    return img


# ---------------------------------------------------------------------------------------------------------- the span
def span():
    """Four columns, one to a face of the cord; down each, a turn of the twist, each column a step on."""
    img = Image.new("RGBA", (4, 3), CLEAR)
    for y in range(3):
        for x in range(4):
            px(img, x, y, CORD[(x + y * 2) % 3])
    return img


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    tex = ASSETS / "textures"
    glass().save(tex / "block/filament_bulb_glass.png")
    filament().save(tex / "block/filament_bulb_filament.png")
    texkit.finish(flex(), 601, **texkit.SMOOTH).save(tex / "block/filament_bulb_flex.png")
    filament(COIL_OFF).save(tex / "block/filament_bulb_filament_off.png")
    glass(GLASS_OFF, GLASS_EDGE_OFF, GLINT_OFF).save(tex / "block/filament_bulb_glass_off.png")
    write_json(ASSETS / "models/block/filament_bulb.json", bulb_model())
    write_json(ASSETS / "models/block/filament_bulb_off.json", bulb_model(on=False))
    write_json(ASSETS / "blockstates/filament_bulb.json", {"variants": {"lit=true": {"model": "nookworks:block/filament_bulb"},
                                                                        "lit=false": {"model": "nookworks:block/filament_bulb_off"}}})
    write_json(ASSETS / "items/filament_bulb.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/filament_bulb"}})
    dried_fish().save(tex / "item/dried_fish.png")
    write_json(ASSETS / "models/item/dried_fish.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "nookworks:item/dried_fish"}})
    write_json(ASSETS / "items/dried_fish.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/dried_fish"}})
    (tex / "entity").mkdir(parents=True, exist_ok=True)
    span().save(tex / "entity/thin_rope_span.png")
    print("wrote the bulb, the dried fish and the span")


if __name__ == "__main__":
    main()
