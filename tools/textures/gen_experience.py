"""Generate the Liquid Experience textures (all original pixel art). Deterministic.

Blocks: experience_pipe (glass) and its brass collar, experience_tap, absorption_pad
Models: the pipe, the pad's risers and the tank's tap (see write_models)
        (the Experience Tank's own textures are hand-finished; see HAND_FINISHED.md)
Items:  experience_bucket_fill (overlay on the vanilla bucket)

The brass tap is shaded with texkit.shade as metal.

Liquid experience reads as the green-gold of an XP orb: bright, faintly glowing, slightly syrupy.

Run from the repo root:  python tools/textures/gen_experience.py
"""
import json
import math
import sys
import random
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import gen_pipe_ports  # noqa: E402

import itemkit
import texkit
from gen_water import pull_flange

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"
ITEM = ROOT / "src/main/resources/assets/nookworks/textures/item"

CLEAR = (0, 0, 0, 0)
OUTLINE = (30, 30, 36, 255)


def rgba(r, g, b, a=255):
    return (int(max(0, min(255, r))), int(max(0, min(255, g))), int(max(0, min(255, b))), a)


def save(img, path, frametime=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    if frametime:
        path.with_suffix(".png.mcmeta").write_text(json.dumps({"animation": {"frametime": frametime}}) + "\n")


def liquid_frames(size, frames, flow):
    """Green-gold with drifting brighter motes, like suspended orbs."""
    img = Image.new("RGBA", (size, size * frames))
    for f in range(frames):
        t = f / frames * 2 * math.pi
        for y in range(size):
            for x in range(size):
                yy = y - (f * size / frames if flow else 0)
                v = (math.sin(x * 0.6 + t) + math.sin(yy * 0.5 - t * (2 if flow else 1)) + math.sin((x + yy) * 0.35 + t)) / 3
                base = 150 + 70 * v
                colour = rgba(base * 0.55, base, base * 0.35)
                if v > 0.72:
                    colour = rgba(226, 252, 150)
                img.putpixel((x, y + f * size), colour)
    return img


GLASS = (196, 232, 210)


def pipe():
    """The glass of the tube: faint, with a few brighter flecks. No edge lines are drawn into it, because the
    same pane is used on the ends and the sides of the tube, and a frame drawn in would cross a straight run at
    every block. The collars at the joints and the glowing core inside give the pipe its shape instead.
    """
    rng = random.Random(3)
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            roll = rng.random()
            alpha = 170 if roll < 0.06 else 118 if roll < 0.22 else 96
            img.putpixel((x, y), GLASS + (alpha,))
    return img


def tap():
    """
    The brass tap on the Experience Tank: polished brass lit along its top edge and shaded along its bottom, in the
    machines' brass, and the dark of the spout's mouth. (Speckled with its own shadow colour it read as sandstone.)
    """
    img = Image.new("RGBA", (16, 16), CLEAR)
    lit, face, low, dark, mouth = (238, 204, 116, 255), (200, 156, 68, 255), (170, 128, 52, 255), (120, 84, 30, 255), (40, 30, 14, 255)
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), lit if y < 3 else dark if y > 12 else low if y > 9 else face)
    for y in range(6, 10):  # the spout's open end, sampled by the downward face of the nozzle
        for x in range(6, 10):
            img.putpixel((x, y), mouth)
    return texkit.shade(img, 560, keep=(mouth[:3], lit[:3]), material="metal", **texkit.SMOOTH)


def collar():
    """
    The brass collars at the pipe's joints and round the pad's risers: a glass tube is held in brass, as laboratory
    glass is, not in the Mercury Pipe's bolted iron (that read as an iron fitting on a glass tube). The joints sample
    rows 6-7, a lit edge over its shadow; the risers' plates rows 4-11, a plate with a lit rim.
    """
    img = Image.new("RGBA", (16, 16), CLEAR)
    face, lit, shade, deep = (170, 124, 46, 255), (214, 170, 84, 255), (118, 82, 30, 255), (80, 54, 20, 255)
    rng = random.Random(9)
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), face if rng.random() > 0.12 else shade)
    for x in range(16):
        img.putpixel((x, 4), lit)
        img.putpixel((x, 6), lit)
        img.putpixel((x, 7), shade)
        img.putpixel((x, 11), deep)
    for x in range(1, 16, 4):  # the seam where each ring is closed
        img.putpixel((x, 7), deep)
    return img


STUD, STUD_HI, STUD_LO = (174, 177, 182, 255), (219, 222, 227, 255), (134, 137, 142, 255)
BAR, BAR_IN, BAR_SH = (36, 38, 42, 255), (22, 24, 27, 255), (12, 13, 15, 255)


def pad(off=False):
    """
    The top face, in the machines' family: a steel plate framed in the dark steel of the machines with pale studs
    at its corners, and in its middle the round intake - rings of slots with liquid experience glowing up through
    them, brightest at the centre where it is drawn in. Switched off by redstone ({@code off}), the glow goes out.
    """
    img = Image.new("RGBA", (16, 16), CLEAR)
    plate, plate_lit, plate_dark = (150, 154, 160, 255), (178, 182, 188, 255), (118, 122, 130, 255)
    glow, glow_hot, slot = (120, 222, 54, 255), (206, 255, 132, 255), (30, 44, 26, 255)
    if off:
        glow, glow_hot = (46, 68, 40, 255), (62, 88, 52, 255)
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), plate_lit if (x + y) % 5 == 0 else plate)
    for i in range(16):
        for x, y, c in ((i, 0, BAR), (0, i, BAR), (i, 15, BAR_SH), (15, i, BAR_SH)):
            img.putpixel((x, y), c)
    for x, y, c in ((0, 0, STUD_HI), (15, 0, STUD), (0, 15, STUD_LO), (15, 15, STUD_LO)):
        img.putpixel((x, y), c)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r <= 5.6:
                ring = int(r)
                if r < 1.6:
                    img.putpixel((x, y), glow_hot)
                elif ring % 2 == 0:
                    img.putpixel((x, y), glow if (x * 3 + y) % 4 else slot)  # a ring of slots, glowing
                else:
                    img.putpixel((x, y), plate_dark)  # the bars between
    return img


def pad_side():
    """
    The plate's edge, one pixel high (the bottom row is what the model shows): the dark frame, a stud at each
    end - not the stripe of an iron block's edge it wore before.
    """
    img = Image.new("RGBA", (16, 16), BAR_IN)
    for x in range(16):
        img.putpixel((x, 15), BAR)
    img.putpixel((0, 15), STUD)
    img.putpixel((15, 15), STUD_LO)
    return img


def pad_model(off=False):
    top = "nookworks:block/absorption_pad" + ("_off" if off else "")
    return {"parent": "minecraft:block/block",
            "textures": {"particle": "nookworks:block/absorption_pad", "top": top,
                         "side": "nookworks:block/absorption_pad_side", "bottom": "nookworks:block/machine_bottom"},
            "elements": [{"from": [0, 0, 0], "to": [16, 1, 16], "faces": {
                **{d: {"texture": "#side", "cullface": d} for d in ("north", "east", "south", "west")},
                "up": {"texture": "#top"}, "down": {"texture": "#bottom", "cullface": "down"}}}]}


def bucket_fill():
    """Liquid experience in the bucket's mouth, lime green and bright, a spark of an orb's yellow in it."""
    return itemkit.bucket_fill([(64, 150, 24), (118, 206, 40), (176, 240, 80), (236, 255, 170)],
                               glints=((10, 4, (255, 255, 200)),))


MODELS = ROOT / "src/main/resources/assets/nookworks/models"
STATES = ROOT / "src/main/resources/assets/nookworks/blockstates"
ITEMS = ROOT / "src/main/resources/assets/nookworks/items"
LIQUID = "nookworks:block/liquid_experience_still"
COLLAR = "nookworks:block/experience_pipe_collar"  # brass, as glass is held


def face(uv, rotation=0, texture="#glass"):
    f = {"texture": texture, "uv": uv}
    if rotation:
        f["rotation"] = rotation
    return f


def box(frm, to, faces, glow=False):
    """One element. `faces` maps a direction to its face; a glowing element is the liquid, lit from within."""
    element = {"from": frm, "to": to, "faces": faces}
    if glow:
        element["shade_direction_override"] = "up"  # unshaded: 26.3 has no "shade": false
        element["light_emission"] = 12
    return element


def sized(frm, to, d, u0=0, v0=0):
    """The uv for face {@code d} of a box, the size of the face, from (u0, v0): pixel for pixel."""
    dx, dy, dz = (to[i] - frm[i] for i in range(3))
    w, h = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[d]
    return [u0, v0, u0 + w, v0 + h]


def liquid(frm, to, sides):
    """The glowing liquid, pixel for pixel on every face (the texture tiles, so any 16 px of it will do)."""
    return box(frm, to, {d: face(sized(frm, to, d), texture="#liquid") for d in sides}, glow=True)


def ring_along_z(z0, z1):
    """A collar round a run lying north-south, from z0 to z1."""
    return box([4.5, 4.5, z0], [11.5, 11.5, z1], {
        "east": face([4, 6, 12, 8], 90, "#collar"), "west": face([4, 6, 12, 8], 90, "#collar"),
        "up": face([4, 6, 12, 8], texture="#collar"), "down": face([4, 6, 12, 8], texture="#collar")})


def end_collar(z0, z1, end):
    """The collar at an open end of the item's run, closed across that end over the glass and the liquid."""
    ring = ring_along_z(z0, z1)
    ring["faces"][end] = face([4, 4, 11, 11], texture="#collar")
    return ring


def glass_model(elements, particle="nookworks:block/experience_pipe"):
    return {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
            "textures": {"particle": particle, "glass": "nookworks:block/experience_pipe", "liquid": LIQUID,
                         "collar": COLLAR},
            "elements": elements}


def run_glass(z0, z1):
    """The glass of a length of pipe lying north-south."""
    n = z1 - z0
    return box([5, 5, z0], [11, 11, z1], {"east": face([0, 0, n, 6]), "west": face([0, 0, n, 6]),
                                          "up": face([0, 0, 6, n]), "down": face([0, 0, 6, n])})


def pipe_models():
    """The pipe is a glass tube with the liquid glowing down its middle - while there is any in it.

    core         the liquid at the centre, shown while the pipe is filled
    cap          the glass over one side of the centre, shown only on a side with no neighbour, so a straight run
                 has no pane across it
    side         an arm to one neighbour: the glass and half of the collar at the joint
    side_liquid  the liquid down that arm, shown while the pipe is filled
    pull         the steel flange over an arm whose side pulls, drawn by the block entity's renderer
    """
    arm_liquid = liquid([6, 6, 0], [10, 10, 6], ("east", "west", "up", "down"))
    core = liquid([6, 6, 6], [10, 10, 10], ("north", "east", "south", "west", "up", "down"))
    cap = box([5, 5, 5], [11, 11, 5], {"north": face([0, 0, 6, 6])})
    yield "experience_pipe_core", glass_model([core])
    yield "experience_pipe_cap", glass_model([cap])
    yield "experience_pipe_side", glass_model([run_glass(0, 5), ring_along_z(0, 2)])
    yield "experience_pipe_side_liquid", glass_model([arm_liquid])
    # an arm whose side pulls (gen_water.pull_flange): the flange 9 across over the 7 px collar, the band round the
    # 6 px glass
    yield "experience_pipe_pull", pull_flange(3.5, 12.5, 2, 4.5, 11.5, 3.5)
    # the item: a straight run, north to south
    yield "experience_pipe_inventory", glass_model([
        run_glass(0, 16),
        # the ends are the collars' faces: the liquid's own ends would lie in the same planes and flicker under them
        liquid([6, 6, 0], [10, 10, 16], ("east", "west", "up", "down")),
        end_collar(0, 2, "north"), end_collar(14, 16, "south")])


def pad_models():
    """The pad's risers. Where a pipe comes in from the side (or from above), it drops down to the plate in a
    short upright length of glass standing in a flanged foot, so the pipe meets the pad instead of hanging in
    the air beside it."""
    side = [
        # the north end shows between the collar and the foot, so the glass, the liquid and the foot are closed
        # there too; the liquid starts a pixel in, so its end is not drawn on the glass's own plane
        box([5, 2, 0], [11, 11, 6], {"north": face([0, 0, 6, 9]), "east": face([0, 0, 6, 9]),
                                     "west": face([0, 0, 6, 9]), "south": face([0, 0, 6, 9]),
                                     "up": face([0, 0, 6, 6])}),
        liquid([6, 2, 1], [10, 10, 5], ("north", "east", "west", "south", "up")),
        box([4, 1, 0], [12, 2, 7], {
            "north": face([4, 6, 12, 7], texture="#collar"),
            "up": face([4, 4, 12, 11], texture="#collar"), "south": face([4, 6, 12, 7], texture="#collar"),
            "east": face([4, 6, 11, 7], texture="#collar"), "west": face([4, 6, 11, 7], texture="#collar")}),
        ring_along_z(0, 2),
    ]
    yield "absorption_pad_riser", glass_model(side, "nookworks:block/absorption_pad")
    sides = ("north", "east", "south", "west")
    up = [
        box([5, 2, 5], [11, 16, 11], {d: face([0, 0, 6, 14]) for d in sides}),
        liquid([6, 2, 6], [10, 16, 10], sides),
        box([4, 1, 4], [12, 2, 12], {**{d: face([4, 6, 12, 7], texture="#collar") for d in sides},
                                     "up": face([4, 4, 12, 12], texture="#collar")}),
        # the top collar is the pipe's own collar width (7 px), so it runs on into the arm of the pipe above with
        # no ledge left open between them
        box([4.5, 14, 4.5], [11.5, 16, 11.5], {**{d: face([4.5, 6, 11.5, 8], texture="#collar") for d in sides},
                                               "down": face([4.5, 4.5, 11.5, 11.5], texture="#collar")}),
    ]
    yield "absorption_pad_riser_up", glass_model(up, "nookworks:block/absorption_pad")


def tap_models():
    """The Experience Tank's tap, low on its front (north) face: a plate, a spout that turns down, and a handle
    across the spout when it is shut and along it when it is open, the way a real tap shows which it is. The plate
    runs back into the recess to the panel itself (z 1), so the tap is fixed to the tank rather than floating a
    pixel in front of it."""
    def solid(frm, to, sides):
        """A brass part, its texture taken pixel for pixel at the face's own size: the lit rows on top, the shadow
        underneath, the mottled face on the sides."""
        dx, dy, dz = (to[i] - frm[i] for i in range(3))
        size = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}
        faces = {}
        for d in sides:
            w, h = size[d]
            v0 = 0 if d == "up" else 16 - h if d == "down" else 3
            faces[d] = face([2, v0, 2 + w, v0 + h], texture="#tap")
        return box(frm, to, faces)

    every = ("north", "east", "south", "west", "up", "down")
    body = [
        solid([5.5, 2, -0.5], [10.5, 8, 1], ("north", "east", "west", "up", "down")),
        solid([6.5, 4, -3], [9.5, 7, -0.5], ("north", "east", "west", "up", "down")),
        box([6.5, 2, -3], [9.5, 4, -1.5], {"north": face([6, 2, 9, 4], texture="#tap"),
                                           "east": face([2, 2, 4, 4], texture="#tap"),
                                           "west": face([2, 2, 4, 4], texture="#tap"),
                                           "south": face([6, 2, 9, 4], texture="#tap"),
                                           "down": face([6, 6, 9, 8], texture="#tap")}),
        solid([7.5, 7, -2], [8.5, 9, -1], ("north", "east", "south", "west")),
    ]
    shut = solid([5, 9, -2], [11, 10, -1], every)
    turned = solid([7.5, 9, -5], [8.5, 10, 0], every)
    for name, handle in (("experience_tank_tap", shut), ("experience_tank_tap_open", turned)):
        yield name, {"parent": "minecraft:block/block",
                     "textures": {"particle": "nookworks:block/experience_tap", "tap": "nookworks:block/experience_tap"},
                     "elements": body + [handle]}


ROTATIONS = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def write_models():
    for gen in (pipe_models(), pad_models(), tap_models()):
        for name, data in gen:
            write_json(MODELS / "block" / f"{name}.json", data)
    parts = [{"when": {"filled": "true"}, "apply": {"model": "nookworks:block/experience_pipe_core"}}]
    for side, rot in ROTATIONS.items():
        parts.append({"when": {side: "false"}, "apply": {"model": "nookworks:block/experience_pipe_cap", **rot}})
        parts.append({"when": {side: "true"}, "apply": {"model": "nookworks:block/experience_pipe_side", **rot}})
        parts.append({"when": {side: "true", "filled": "true"},
                      "apply": {"model": "nookworks:block/experience_pipe_side_liquid", **rot}})
    write_json(STATES / "experience_pipe.json", {"multipart": parts})
    pad = [{"when": {"powered": "false"}, "apply": {"model": "nookworks:block/absorption_pad"}},
           {"when": {"powered": "true"}, "apply": {"model": "nookworks:block/absorption_pad_off"}}]
    for side in ("north", "east", "south", "west"):
        pad.append({"when": {side: "true"}, "apply": {"model": "nookworks:block/absorption_pad_riser", **ROTATIONS[side]}})
    pad.append({"when": {"up": "true"}, "apply": {"model": "nookworks:block/absorption_pad_riser_up"}})
    write_json(STATES / "absorption_pad.json", {"multipart": pad})
    # The cabinet, its windowless front where the tap is, and the sight glass in each side but that one, at the tank's
    # LEVEL (models from tools/gen_machine_models.py): all drawn for a tank facing north, turned with it as the tap is.
    tank = [{"when": {"facing": side}, "apply": {"model": "nookworks:block/experience_tank", **ROTATIONS[side]}}
            for side in ("north", "east", "south", "west")]
    for side in ("north", "east", "south", "west"):
        tank += [{"when": {"facing": side, "level": str(n)},
                  "apply": {"model": f"nookworks:block/experience_tank_gauge_{n}", **ROTATIONS[side]}} for n in range(7)]
    for side in ("north", "east", "south", "west"):
        for is_open in ("false", "true"):
            tap = "experience_tank_tap" + ("_open" if is_open == "true" else "")
            tank.append({"when": {"facing": side, "open": is_open},
                         "apply": {"model": f"nookworks:block/{tap}", **ROTATIONS[side]}})
    write_json(STATES / "experience_tank.json", gen_pipe_ports.with_ports("experience_tank", {"multipart": tank}))
    write_json(ITEMS / "experience_pipe.json",
               {"model": {"type": "minecraft:model", "model": "nookworks:block/experience_pipe_inventory"}})
    write_json(ITEMS / "experience_tank.json", {"model": {"type": "minecraft:composite", "models": [
        {"type": "minecraft:model", "model": "nookworks:block/experience_tank"},
        {"type": "minecraft:model", "model": "nookworks:block/experience_tank_tap"}]}})


def main():
    BLOCK.mkdir(parents=True, exist_ok=True)
    ITEM.mkdir(parents=True, exist_ok=True)
    # (the liquid itself is gen_liquids.py's)
    save(pipe(), BLOCK / "experience_pipe.png")
    save(tap(), BLOCK / "experience_tap.png")
    save(collar(), BLOCK / "experience_pipe_collar.png")
    write_models()
    save(pad(), BLOCK / "absorption_pad.png")
    save(pad(off=True), BLOCK / "absorption_pad_off.png")
    save(pad_side(), BLOCK / "absorption_pad_side.png")
    write_json(MODELS / "block" / "absorption_pad.json", pad_model())
    write_json(MODELS / "block" / "absorption_pad_off.json", pad_model(off=True))
    save(bucket_fill(), ITEM / "experience_bucket_fill.png")
    print("wrote experience textures")


if __name__ == "__main__":
    main()
