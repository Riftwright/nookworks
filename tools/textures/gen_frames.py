"""The item frames of ours (ダークオークの額縁, 真鍮の額縁 and their glowing forms): the brass they are cut from, and their
models. Deterministic.

- block/brass_frame.png: brushed brass, the camera's brass (gen_camera.py) from deep to shine, every row the same brass
  brushed along its length. The brass frame's map form takes its rim from it (vanilla's template_item_frame_map, a
  sixteenth of the texture a run), as do the frame's particles.

- block/brass_moulding.png: the brass frame's moulding (the user, 2026-11-13: 「真鍮は、もう少し額縁を厚く、存在感ある
  感じにできないかな」, with a photo of antique brass frames, ornate at the corners): a rim two sixteenths wide and two
  deep (three and three round a poster three blocks or more on its longer side), a round boss a sixteenth more each
  way at each corner. Laid out for it, not as a picture: rows 0-2 the front of a run, brushed along its length, the
  outer row a lit bead, then falling into shade towards the picture (a rim two wide takes the first two); rows 4-7 a
  run's sides, from the front back to the wall; at (0, 8) the boss's stud for a rim two wide (3x3), at (4, 8) for one
  three wide (4x4), and at (8, 8) its sides; the rest plain brushed brass. The models below and a framed poster's rim
  (PosterRenderer#moulding) take it so: along a run the texture's u, the outer edge its top row.

- models/block/<frame>_item_frame(_map).json, models/item/<frame>_item_frame.json and the glowing forms: the dark oak
  frame is vanilla's template_item_frame (and _map) with dark oak planks by name; the brass frame's moulding is its own
  geometry, its map form vanilla's (a map in a frame covers its whole block, the rim behind it, so map walls stay
  whole). A glowing form is its frame with vanilla's glow_item_frame back. The item is the frame flat through the
  middle of the item's space, as a flat item lies, so the hands and the ground hold it as they hold any flat item.

The dark oak frame needs no texture of its own: vanilla's dark oak planks, by name in its models.
Run from the repo root:  python tools/textures/gen_frames.py
"""
import json
import random
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
TEXTURES = ASSETS / "textures/block"
BACK = "minecraft:block/item_frame"
GLOW_BACK = "minecraft:block/glow_item_frame"
DARK_OAK = "minecraft:block/dark_oak_planks"
BRASS_PLAIN = "nookworks:block/brass_frame"
MOULDING = "nookworks:block/brass_moulding"
# a flat item's places, as vanilla's item/generated has them
DISPLAY = {
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.2, 1.2, 1.2]},
}

DEEP = (138, 94, 28)
BRASS = (180, 132, 48)
LIGHT = (222, 178, 88)
SHINE = (248, 224, 152)
# the boss's stud, kept exact through the finish (its own shades, so the streaks' may move)
STUD_DEEP = (124, 82, 22)
STUD_LIGHT = (230, 186, 96)
STUD_SHINE = (252, 232, 168)


def brush(img, rng, y, x0, x1, weights):
    """Streaks along row {@code y} from x0 to x1, two to five pixels long, each a colour drawn by {@code weights}."""
    colours, odds = zip(*weights)
    x = x0
    while x < x1:
        run = rng.randint(2, 5)
        colour = rng.choices(colours, odds)[0]
        for i in range(run):
            if x + i < x1:
                img.putpixel((x + i, y), colour + (255,))
        x += run


def draw():
    rng = random.Random(1113)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        x = 0
        while x < 16:
            # a streak along the row, two to five pixels long
            run = rng.randint(2, 5)
            roll = rng.random()
            colour = LIGHT if roll < 0.28 else DEEP if roll < 0.40 else BRASS
            for i in range(run):
                if x + i < 16:
                    img.putpixel((x + i, y), colour + (255,))
            x += run
    for _ in range(5):  # a glint now and then
        img.putpixel((rng.randrange(16), rng.randrange(16)), SHINE + (255,))
    return img


FRONT_V = 0      # a run's front: rows 0-2, as many as it is wide from the top
SIDE_V = 4       # a run's sides: rows 4-7, as many as it is deep from the top (the front)
STUD_3 = (0, 8)  # the boss's stud, 3x3 (a rim two wide) and 4x4 (three wide)
STUD_4 = (4, 8)
BOSS_SIDE = (8, 8)  # the boss's sides, up to 4x4


def draw_moulding():
    rng = random.Random(1114)
    img = Image.new("RGBA", (16, 16))
    plain = [(BRASS, 0.62), (LIGHT, 0.24), (DEEP, 0.14)]
    for y in range(16):
        brush(img, rng, y, 0, 16, plain)
    brush(img, rng, FRONT_V, 0, 16, [(LIGHT, 0.7), (BRASS, 0.3)])      # the bead, out
    brush(img, rng, FRONT_V + 1, 0, 16, [(BRASS, 0.45), (DEEP, 0.55)])  # falling in, towards the picture
    brush(img, rng, FRONT_V + 2, 0, 16, [(DEEP, 0.6), (BRASS, 0.4)])    # the shade by the picture (a rim three wide)
    for _ in range(3):  # glints along the bead
        img.putpixel((rng.randrange(16), FRONT_V), SHINE + (255,))
    brush(img, rng, SIDE_V, 0, 16, [(BRASS, 0.6), (LIGHT, 0.4)])  # a side by the face
    for y in range(SIDE_V + 1, SIDE_V + 4):                      # back to the wall
        brush(img, rng, y, 0, 16, [(BRASS, 0.55), (DEEP, 0.45)])
    x0, y0 = BOSS_SIDE
    for y in range(y0, y0 + 4):  # the boss's sides
        brush(img, rng, y, x0, x0 + 4, [(BRASS, 0.6), (LIGHT, 0.25), (DEEP, 0.15)])
    colours = {"d": STUD_DEEP, "l": STUD_LIGHT, "s": STUD_SHINE, "b": BRASS}
    for (sx, sy), stud in ((STUD_3, ["dld", "lsl", "dld"]), (STUD_4, ["dlld", "lssl", "lsbl", "dlld"])):
        for y, row in enumerate(stud):
            for x, c in enumerate(row):
                img.putpixel((sx + x, sy + y), colours[c] + (255,))
    return img


def box(start, end, faces):
    return {"from": start, "to": end, "faces": faces}


# each face's right and up, in the model's axes, as its texture lies on it unturned
FACE_AXES = {
    "north": ((-1, 0, 0), (0, 1, 0)),
    "south": ((1, 0, 0), (0, 1, 0)),
    "east": ((0, 0, -1), (0, 1, 0)),
    "west": ((0, 0, 1), (0, 1, 0)),
    "up": ((1, 0, 0), (0, 0, -1)),
    "down": ((1, 0, 0), (0, 0, 1)),
}


def turned(face, top):
    """The face's rotation that puts its texture's top row towards {@code top} (clockwise, as a model turns it)."""
    right, up = FACE_AXES[face]
    neg = lambda v: tuple(-c for c in v)
    return {up: 0, right: 90, neg(up): 180, neg(right): 270}[top]


def face(uv, rotation=0, texture="#wood"):
    f = {"uv": uv, "texture": texture}
    if rotation:
        f["rotation"] = rotation
    return f


def moulding(rim_z, boss_z, back_faces):
    """The brass moulding's runs and bosses: outside 1 to 15, the opening 3 to 13 (vanilla's frame's), the runs between
    the bosses. {@code rim_z} and {@code boss_z} are (front, back); {@code back_faces} names the faces drawn as the
    front is - the side away from the wall for a hung frame, both for the item."""
    f0, f1 = rim_z
    b0, b1 = boss_z
    out = []
    # (from, to, the axis it runs along, the way out from the opening)
    runs = [
        ([4, 13, f0], [12, 15, f1], 0, (0, 1, 0)),   # top
        ([4, 1, f0], [12, 3, f1], 0, (0, -1, 0)),    # bottom
        ([1, 4, f0], [3, 12, f1], 1, (-1, 0, 0)),    # to -x
        ([13, 4, f0], [15, 12, f1], 1, (1, 0, 0)),   # to +x
    ]
    for start, end, along, outward in runs:
        a0, a1 = start[along], end[along]
        faces = {}
        for name in back_faces:  # the face out, and the back as vanilla's frame has it
            faces[name] = face([a0, FRONT_V, a1, FRONT_V + 2], turned(name, outward))
        sides = ("up", "down") if along == 0 else ("east", "west")
        for name in sides:  # the sides, their u along the run
            rotation = 0 if along == 0 else 90
            faces[name] = face([a0, SIDE_V, a1, SIDE_V + 2], rotation)
        out.append(box(start, end, faces))
    sx, sy = STUD_3
    bx, by = BOSS_SIDE
    for x, y in ((1, 1), (12, 1), (1, 12), (12, 12)):
        faces = {name: face([sx, sy, sx + 3, sy + 3]) for name in back_faces if name != "south" or b1 < 16}
        faces.update({name: face([bx, by, bx + 3, by + 3]) for name in ("east", "west", "up", "down")})
        out.append(box([x, y, b0], [x + 3, y + 3, b1], faces))
    return out


def brass_block():
    """The brass frame hung: vanilla's back (3 to 13, against the wall), the moulding in front of it."""
    back = {"north": face([3, 3, 13, 13], texture="#back"), "south": face([3, 3, 13, 13], texture="#back")}
    elements = [box([3, 3, 15.5], [13, 13, 16], back)] + moulding((14, 16), (13, 16), ("north", "south"))
    return {"textures": {"particle": BRASS_PLAIN, "wood": MOULDING, "back": BACK}, "elements": elements}


def item_model(wood):
    """The frame flat through the middle (z 7.5 to 8.5): the back 10 across, the rim round it 12 across."""
    back = {"north": {"uv": [3, 3, 13, 13], "texture": "#back"}, "south": {"uv": [3, 3, 13, 13], "texture": "#back"}}

    def rim(start, end):
        """A run of the rim, each face cut from the texture at its own size (front and back as they lie, the
        ends and edges a sixteenth deep)."""
        x0, y0, z0 = start
        x1, y1, z1 = end
        w, h, d = x1 - x0, y1 - y0, z1 - z0
        front = [x0, 16 - y1, x1, 16 - y0]

        def face(uv):
            return {"uv": uv, "texture": "#wood"}

        return box(start, end, {
            "north": face(front), "south": face(front),
            "east": face([0, 16 - y1, d, 16 - y0]), "west": face([0, 16 - y1, d, 16 - y0]),
            "up": face([x0, 0, x1, d]), "down": face([x0, 0, x1, d]),
        })

    elements = [
        box([3, 3, 8], [13, 13, 8.5], back),
        rim([2, 13, 7.5], [14, 14, 8.5]),  # top
        rim([2, 2, 7.5], [14, 3, 8.5]),    # bottom
        rim([2, 3, 7.5], [3, 13, 8.5]),    # left
        rim([13, 3, 7.5], [14, 13, 8.5]),  # right
    ]
    return {"gui_light": "front", "textures": {"particle": wood, "wood": wood, "back": BACK},
            "elements": elements, "display": DISPLAY}


def brass_item():
    """The brass frame as an item: the moulding round the back, both sides alike; 14 across, so the slot shows it at its
    own size (the dark oak one's 12 is drawn a fifth larger)."""
    back = {"north": face([3, 3, 13, 13], texture="#back"), "south": face([3, 3, 13, 13], texture="#back")}
    elements = [box([3, 3, 7.75], [13, 13, 8.25], back)] + moulding((7, 9), (6.5, 9.5), ("north", "south"))
    display = dict(DISPLAY, gui={"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]})
    return {"gui_light": "front", "textures": {"particle": BRASS_PLAIN, "wood": MOULDING, "back": BACK},
            "elements": elements, "display": display}


def glowing(parent):
    return {"parent": parent, "textures": {"back": GLOW_BACK}}


def write(path, model):
    out = ASSETS / path
    out.write_bytes((json.dumps(model, indent=2) + "\n").encode())
    print("wrote", out.relative_to(ROOT))


def main():
    for name, image, seed, keep in (("brass_frame", draw(), 1113, ()),
                                    ("brass_moulding", draw_moulding(), 1114, (STUD_DEEP, STUD_LIGHT, STUD_SHINE))):
        img = texkit.finish(image, seed, tile=True, keep=keep, **texkit.SMOOTH)
        img.save(TEXTURES / f"{name}.png")
        print("wrote", (TEXTURES / f"{name}.png").relative_to(ROOT))
    dark = {"particle": DARK_OAK, "wood": DARK_OAK}
    for glow in (False, True):
        g = "_glow" if glow else ""
        back = GLOW_BACK if glow else BACK
        write(f"models/block/dark_oak{g}_item_frame.json",
              {"parent": "minecraft:block/template_item_frame", "textures": dict(dark, back=back)})
        write(f"models/block/dark_oak{g}_item_frame_map.json",
              {"parent": "minecraft:block/template_item_frame_map", "textures": dict(dark, back=back)})
        write(f"models/block/brass{g}_item_frame_map.json",
              {"parent": "minecraft:block/template_item_frame_map",
               "textures": {"particle": BRASS_PLAIN, "wood": BRASS_PLAIN, "back": back}})
    write("models/block/brass_item_frame.json", brass_block())
    write("models/block/brass_glow_item_frame.json", glowing("nookworks:block/brass_item_frame"))
    write("models/item/dark_oak_item_frame.json", item_model(DARK_OAK))
    write("models/item/dark_oak_glow_item_frame.json", glowing("nookworks:item/dark_oak_item_frame"))
    write("models/item/brass_item_frame.json", brass_item())
    write("models/item/brass_glow_item_frame.json", glowing("nookworks:item/brass_item_frame"))
    for name in ("dark_oak_glow_item_frame", "brass_glow_item_frame"):
        write(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:item/{name}"}})


if __name__ == "__main__":
    main()
