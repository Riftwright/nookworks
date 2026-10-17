"""Dark Matter and what it is made of. Original pixel art, drawn flat and finished with texkit. Deterministic.

The block stands in the dark it makes (light 0 all round it), so what is lit of it must give its own light: the body
is drawn plain and the glow is a second layer the model gives light_emission 15.

- block/dark_matter.png              the body, a geode cut open: a crust of char (what it is made of) round the edge,
                                     a ring of amethyst shards pointing in, and a black heart
- block/dark_matter_glow.png (+ .mcmeta)  what glows: the shards' tips, and in the heart three violet arms turning slowly
                                     inward with motes coming and going - the light going down into it (32 frames,
                                     blended). Clear everywhere else.
- block/dark_matter_beaten.png       the body gone dull, a dead grey-violet, while a light in its disc has beaten it
- block/dark_matter_glow_dim.png     the shard tips gone cold and grey, the heart still
- item/charred_lump.png              cooked food cooked again: a black, blistered lump with grey ash on its bumps and a
                                     dull ember-brown showing in its cracks
- models/block/dark_matter.json, dark_matter_beaten.json  the body, and the glow a hair outside it

Run from the repo root:  python tools/textures/gen_dark_matter.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
TEX = ASSETS / "textures"
CLEAR = (0, 0, 0, 0)

CENTRE = 7.5
CRUST_R = 6.9      # beyond this, the charred crust (and the corners)
SHARD_R = 4.4      # between this and the crust, the ring of amethyst shards pointing in
SHARDS = 12
CRUST = [(10, 8, 9), (22, 18, 18), (38, 33, 32), (60, 55, 52)]
AMETHYST = [(40, 22, 70), (70, 40, 118), (104, 64, 164), (142, 98, 206)]
VOID = (5, 3, 9)
TIP = [(150, 96, 228), (206, 160, 255), (246, 228, 255)]
SWIRL = [(58, 30, 104), (108, 62, 186), (172, 124, 244)]
MOTE = [(88, 54, 140), (160, 116, 230), (236, 214, 255)]
MOTES = [(6, 6, 0), (9, 8, 9), (7, 10, 17), (8, 5, 25)]
FRAMES = 32


def polar(x, y):
    dx, dy = x + 0.5 - CENTRE - 0.5, y + 0.5 - CENTRE - 0.5
    return math.hypot(dx, dy), math.atan2(dy, dx)


def shard(r, a):
    """How far along a shard (0 at its root in the crust, 1 at its tip), or None between shards."""
    if not SHARD_R <= r < CRUST_R:
        return None
    step = 2 * math.pi / SHARDS
    off = abs(((a + step / 2) % step) - step / 2) / (step / 2)   # 0 on a shard's middle line, 1 halfway to the next
    along = (CRUST_R - r) / (CRUST_R - SHARD_R)                   # 0 at the crust, 1 at the inner edge
    return along if off < 0.95 * (1 - along) + 0.12 else None


def body(dull=False):
    rng = random.Random(51)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            r, a = polar(x, y)
            lit = -math.cos(a - math.radians(225))  # up and to the left, as the game lights a face
            if r >= CRUST_R:
                level = 1 + (1 if rng.random() < 0.35 else 0) + (1 if lit > 0.5 and rng.random() < 0.5 else 0)
                colour = CRUST[min(3, level)]
            elif r >= SHARD_R:
                along = shard(r, a)
                if along is None:
                    colour = CRUST[0]
                else:
                    level = 1 + (1 if lit > 0.2 else 0) + (1 if along > 0.5 else 0) - (1 if lit < -0.5 else 0)
                    colour = AMETHYST[max(0, min(3, level))]
            else:
                colour = VOID
            if dull:
                grey = sum(colour) // 3
                colour = tuple(min(255, (grey + v) // 2 + 30) for v in colour)
            img.putpixel((x, y), colour + (255,))
    return img


def glow_frame(frame, dim=False):
    img = Image.new("RGBA", (16, 16), CLEAR)
    turn = frame / FRAMES * 2 * math.pi
    for y in range(16):
        for x in range(16):
            r, a = polar(x, y)
            along = shard(r, a)
            if along is not None and along > 0.62:  # the shard tips, lit from within
                colour = TIP[2] if along > 0.9 else TIP[1] if along > 0.76 else TIP[0]
                img.putpixel((x, y), ((96, 88, 108) if dim else colour) + (255,))
            elif r < SHARD_R - 0.3 and not dim:
                # three arms curling in as a whirlpool does, turning slowly, brighter towards the heart
                arm = math.cos(3 * (a - turn) + 1.9 * r)
                heat = arm * (1.1 - r / SHARD_R)
                if heat > 0.62:
                    img.putpixel((x, y), SWIRL[2] + (255,))
                elif heat > 0.4:
                    img.putpixel((x, y), SWIRL[1] + (255,))
                elif heat > 0.22:
                    img.putpixel((x, y), SWIRL[0] + (255,))
    if not dim:
        for mx, my, phase in MOTES:
            age = (frame - phase) % FRAMES
            strength = {0: 1, 1: 2, 2: 3, 3: 2, 4: 1}.get(age, 0)
            if strength:
                img.putpixel((mx, my), MOTE[strength - 1] + (255,))
        img.putpixel((7, 7), TIP[2] + (255,)) if frame % 8 < 4 else None  # the heart flickers
    return img


def glow():
    sheet = Image.new("RGBA", (16, 16 * FRAMES), CLEAR)
    for frame in range(FRAMES):
        sheet.paste(glow_frame(frame), (0, 16 * frame))
    return sheet


def glow_dim():
    return glow_frame(0, dim=True)


CHAR = [(12, 10, 10), (28, 25, 24), (48, 44, 42), (78, 74, 70)]
EMBER = [(74, 30, 16), (116, 52, 24)]
OUTLINE = (6, 5, 5, 255)


def charred_lump():
    rng = random.Random(33)
    img = Image.new("RGBA", (16, 16), CLEAR)
    blobs = [(7.5, 8.5, 5.2), (5.0, 9.8, 3.4), (10.2, 7.2, 3.6), (8.0, 11.2, 3.2)]  # a lopsided lump
    for y in range(16):
        for x in range(16):
            inside = [b for b in blobs if (x - b[0]) ** 2 + (y - b[1]) ** 2 < b[2] ** 2]
            if not inside:
                continue
            bx, by, r = min(inside, key=lambda b: (x - b[0]) ** 2 + (y - b[1]) ** 2)
            light = (-(x - bx) - (y - by)) / r  # each bump lit from the upper left
            level = 1 + (1 if light > 0.3 else 0) + (1 if light > 0.85 else 0) - (1 if light < -0.6 else 0)
            img.putpixel((x, y), CHAR[max(0, min(3, level))] + (255,))
    for (x, y) in [(6, 8), (7, 9), (8, 9), (9, 10), (11, 6), (10, 7), (5, 11), (4, 10)]:  # cracks, the ember still in them
        if img.getpixel((x, y))[3]:
            img.putpixel((x, y), EMBER[rng.randrange(2)] + (255,))
    img = texkit.finish(img, 34, keep=EMBER, **texkit.ITEM)
    out = img.copy()
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] == 0 and any(
                    0 <= x + dx < 16 and 0 <= y + dy < 16 and img.getpixel((x + dx, y + dy))[3] > 0
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), OUTLINE)
    return out


def model(body_texture, glow_texture, emission):
    # every face its whole texture: the glow stands a hair outside the block, and the uv the game would work out
    # from that reaches past the image's edge, which fails the model
    faces = lambda texture: {side: {"texture": texture, "uv": [0, 0, 16, 16], "cullface": side}
                             for side in ("down", "up", "north", "south", "west", "east")}
    glow_element = {"from": [-0.01, -0.01, -0.01], "to": [16.01, 16.01, 16.01], "shade_direction_override": "up",
                    "faces": faces("#glow")}
    if emission:
        glow_element["light_emission"] = 15
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"particle": body_texture, "body": body_texture, "glow": glow_texture},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces("#body")}, glow_element]}


def write_json(path, data):
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    finish = dict(clusters=0.4, grain=0.1, size=3, reach=1)
    texkit.finish(body(), 91, keep=[VOID], **finish).save(TEX / "block/dark_matter.png")
    glow().save(TEX / "block/dark_matter_glow.png")
    write_json(TEX / "block/dark_matter_glow.png.mcmeta", {"animation": {"frametime": 2, "interpolate": True}})
    texkit.finish(body(dull=True), 92, **finish).save(TEX / "block/dark_matter_beaten.png")
    glow_dim().save(TEX / "block/dark_matter_glow_dim.png")
    charred_lump().save(TEX / "item/charred_lump.png")
    write_json(ASSETS / "models/block/dark_matter.json",
               model("nookworks:block/dark_matter", "nookworks:block/dark_matter_glow", True))
    write_json(ASSETS / "models/block/dark_matter_beaten.json",
               model("nookworks:block/dark_matter_beaten", "nookworks:block/dark_matter_glow_dim", False))
    stale = TEX / "block/dark_matter.png.mcmeta"  # the body used to be the animated one
    if stale.exists():
        stale.unlink()
    print("wrote dark_matter (body, animated glow), dark_matter_beaten and charred_lump")


if __name__ == "__main__":
    main()
