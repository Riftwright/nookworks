"""Oil Press, Motor and Oil Cake: textures (all original pixel art), models and blockstates. Deterministic.

Oil Press: a wooden tray with two posts, a crossbeam and an iron screw worked by a bar handle on top. The wood is
vanilla spruce (planks and stripped log) and the plate black iron, referenced by name; ours are the tray's floor,
dark with the oil that has soaked into it, and the threaded screw. The oil stands in the tray at the height of what
the press holds (its OIL state: none, some, plenty), the vegetable oil's own still texture.
Motor: a housing in the machines' dark steel with cooling fins along it, and an iron shaft out of its front (north,
like an observer's face) that turns while it runs (client/render/MotorRenderer). Through a window in each side its
copper coil glows while it runs (motor_casing_on, and a glowing quad over the window: motor_coil_glow).
Oil Cake: the pressed cake of seed husks left in the press.
Ours are shaded with texkit.shade: the tray's boards wood, the screw metal, the cake plain.

Run from the repo root:  python tools/textures/gen_oil_press.py
"""
import json
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
BLOCK = ASSETS / "textures/block"
ITEM = ASSETS / "textures/item"
CLEAR = (0, 0, 0, 0)


def solid(size, colour):
    return Image.new("RGBA", size, colour + (255,))


def put(img, x, y, colour):
    img.putpixel((x, y), colour + (255,))


# ---- Oil Press ----

RIM = (104, 74, 44)
RIM_DARK = (70, 48, 28)
OIL = (214, 162, 40)
OIL_LIGHT = (246, 212, 108)
OIL_DEEP = (170, 112, 24)


def basin_top():
    """The tray's floor: boards running front to back, dark with the oil soaked into them, a channel worn down the
    middle to the spout at the front (north), and a sheen of oil left along it."""
    img = solid((16, 16), RIM)
    for y in range(16):
        for x in range(16):
            if x % 4 == 3:
                put(img, x, y, RIM_DARK)  # the gaps between the boards
    for y in range(0, 16):  # the channel down the middle, deepest at the spout
        for x in (7, 8):
            put(img, x, y, OIL_DEEP if y < 5 else RIM_DARK)
    for x, y in ((7, 1), (8, 2), (7, 3), (5, 9), (10, 6), (2, 12), (13, 11)):
        put(img, x, y, OIL_LIGHT if y < 5 else OIL_DEEP)
    return texkit.shade(img, 21, keep=[OIL_LIGHT + (255,), RIM_DARK + (255,)], material={None: "wood", OIL_DEEP: "plain"},
                        **texkit.SMOOTH)


IRON = (150, 150, 158)
IRON_DARK = (92, 92, 102)
IRON_LIGHT = (198, 198, 206)


def screw():
    """A threaded iron rod: light and dark bands slanting across it."""
    img = solid((16, 16), IRON)
    for y in range(16):
        for x in range(16):
            band = (x + y * 2) % 4
            put(img, x, y, IRON_LIGHT if band == 0 else IRON_DARK if band == 2 else IRON)
    return texkit.shade(img, 22, tile=True, material="metal", **texkit.SMOOTH)


# ---- Motor ----
# The machines' dark steel (gen_machine_faces.py), so the motor reads as one of them beside a generator: the housing's
# fins in close shades, the end plates bevelled, the bearing and bolts in the frame's pale steel; a copper coil seen
# through a window in each side. (It was a pale grey in hard one-pixel stripes, a window on the top as on the sides and
# the top's fins running across the motor where the sides' ran along it - the user, 2026-10-10.)

# each in two close shades, picked by a soft noise so a face has patches of wear, not a flat stripe: texkit's steps on
# a colour this dark are far too big (it read as camouflage on the Repair Box), so these are shaded here and kept
FIN_HI, FIN, GROOVE = ((68, 72, 78), (63, 67, 73)), ((53, 56, 61), (49, 52, 57)), ((32, 34, 38), (29, 31, 35))
BELL = ((42, 44, 49), (39, 41, 46))  # the plain band at each end of the fins
FRAME_DK, FRAME = (14, 15, 17), (24, 26, 29)
COIL_DK, COIL_MID = (92, 48, 28), (134, 72, 42)  # the windings, dim behind the glass
GLOW_DK, GLOW_MID, GLOW_HI = (196, 92, 34), (244, 150, 62), (255, 214, 140)  # the windings while it runs
GLASS_HI = (120, 138, 150)
PLATE_HI, PLATE, PLATE_LO = (70, 74, 80), (50, 53, 58), (36, 38, 42)
PLATE_WORN = (47, 50, 55)
BOSS_HI, BOSS, BOSS_LO = (164, 168, 175), (124, 128, 136), (82, 85, 90)
BOLT_HI, BOLT, BOLT_LO = (219, 222, 227), (164, 168, 175), (89, 92, 96)
BORE = (20, 21, 24)
# The housing's faces show columns and rows 2-13 of their textures (the model gives them that region): its fins run
# 3..12 along the motor's length, between plain bands at 2 and 13 where the end bells are.
WINDOW = (4, 6, 11, 10)  # the coil window on the sides, frame included: x0, y0, x1, y1


def soft(x, y, salt):
    """A soft noise in [0, 1): a few pixels across, so neighbours mostly agree."""
    import math

    def h(i, j):
        n = math.sin(i * 12.9898 + j * 78.233 + salt * 37.719) * 43758.5453
        return n - math.floor(n)

    gx, gy = x / 3, y / 3
    i, j = math.floor(gx), math.floor(gy)
    fx, fy = gx - i, gy - j
    a, b, c, d = h(i, j), h(i + 1, j), h(i, j + 1), h(i + 1, j + 1)
    return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy


def fins(img, along_rows, salt):
    """Fins running along the motor: a rib two pixels wide, lit on its upper (or left) edge, then a groove; along_rows
    for the sides (the motor's length runs across the texture), else for the top and bottom (it runs down)."""
    for y in range(16):
        for x in range(16):
            across, along = (y, x) if along_rows else (x, y)
            shade = 1 if soft(x, y, salt) > 0.55 else 0
            if along <= 2 or along >= 13:
                c = BELL[shade]
            else:
                k = (across - 2) % 3
                c = (FIN_HI if k == 0 else FIN if k == 1 else GROOVE)[shade]
            put(img, x, y, c)
    return img


def casing(lit):
    """A side of the housing: fins along it, and the coil's window in the middle - its windings dim copper behind the
    glass, or glowing while the motor runs (lit: a glowing quad over it too, in the model)."""
    img = fins(solid((16, 16), FIN[0]), True, 41)
    x0, y0, x1, y1 = WINDOW
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if x in (x0, x1) or y in (y0, y1):
                put(img, x, y, FRAME_DK if y == y1 or x == x1 else FRAME)
            else:
                put(img, x, y, coil(x, y, lit))
    if not lit:
        put(img, x0 + 1, y0 + 1, GLASS_HI)  # the glass catching the light
    return img  # shaded as drawn (FIN_HI...): texkit's steps are too big on steel this dark


def coil(x, y, lit):
    """The coil's windings: copper turns side by side, brightest at the middle when it runs."""
    x0, y0, x1, y1 = WINDOW
    turn = (x - x0) % 2
    if not lit:
        return COIL_MID if turn else COIL_DK
    middle = abs(x - (x0 + x1) / 2) < 1.5
    return GLOW_HI if middle and turn else GLOW_MID if turn or middle else GLOW_DK


def coil_glow():
    """The glowing quad over a running motor's window: the windings alone, lit, clear everywhere else."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    x0, y0, x1, y1 = WINDOW
    for y in range(y0 + 1, y1):
        for x in range(x0 + 1, x1):
            put(img, x, y, coil(x, y, True))
    return img


def casing_top():
    """The top and bottom of the housing: the same fins, running along the motor, and no window."""
    return fins(solid((16, 16), FIN[0]), False, 43)


PLATE_ROWS = {4: (6, 9), 5: (5, 10), 6: (4, 11), 7: (4, 11), 8: (4, 11), 9: (4, 11), 10: (5, 10), 11: (6, 9)}


def in_boss(x, y):
    return y in PLATE_ROWS and PLATE_ROWS[y][0] <= x <= PLATE_ROWS[y][1]


def end_plate():
    """
    Front and back: a dark steel plate bevelled round its edge (lit along the top and left, in shade along the bottom
    and right), a pale bolt head in each corner, and in the middle the bearing - a round boss lit on its upper left,
    shaded on its lower right and casting a shadow on the plate, with the dark bore the shaft turns in. The model's
    flange shows columns and rows 1-14, the body's back 2-13.
    """
    img = solid((16, 16), PLATE)
    for y in range(16):
        for x in range(16):
            if soft(x, y, 47) > 0.6:
                put(img, x, y, PLATE_WORN)
    for i in range(16):
        for xy in ((i, 0), (0, i), (i, 15), (15, i)):
            put(img, *xy, FRAME)
    for i in range(1, 15):
        put(img, i, 1, PLATE_HI)
        put(img, 1, i, PLATE_HI)
        put(img, i, 14, PLATE_LO)
        put(img, 14, i, PLATE_LO)
    for bx, by in ((2, 2), (12, 2), (2, 12), (12, 12)):  # the bolt heads, lit on their upper left
        put(img, bx, by, BOLT_HI)
        put(img, bx + 1, by, BOLT)
        put(img, bx, by + 1, BOLT)
        put(img, bx + 1, by + 1, BOLT_LO)
    for y in range(16):
        for x in range(16):
            if in_boss(x, y):
                rim = not (in_boss(x - 1, y) and in_boss(x + 1, y) and in_boss(x, y - 1) and in_boss(x, y + 1))
                towards_light = (x - 7.5) + (y - 7.5) < 0
                put(img, x, y, (BOSS_HI if towards_light else BOSS_LO) if rim else BOSS)
            elif (in_boss(x - 1, y) or in_boss(x, y - 1)) and not in_boss(x + 1, y) and (x - 7.5) + (y - 7.5) > 1:
                put(img, x, y, PLATE_LO)  # the boss's shadow on the plate
    for x in range(6, 10):  # the bore the shaft turns in, a ring a shade off round it
        for y in range(6, 10):
            put(img, x, y, BORE if 7 <= x <= 8 and 7 <= y <= 8 else BOSS_LO)
    return img  # shaded as drawn, as the housing is


# ---- Oil Cake ----

CAKE = (150, 112, 62)
CAKE_DARK = (104, 74, 38)
CAKE_LIGHT = (190, 152, 92)
OUTLINE = (58, 38, 20)


def oil_cake():
    """A round pressed cake seen from above at a slant: a light top, a darker rim below, flecks of husk."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            top = ((x - 7.5) / 6.5) ** 2 + ((y - 6.5) / 4.5) ** 2
            side = ((x - 7.5) / 6.5) ** 2 + ((y - 9.0) / 4.5) ** 2
            if top <= 1.0:
                put(img, x, y, CAKE_LIGHT if top < 0.55 else CAKE)
            elif side <= 1.0 and y > 6:
                put(img, x, y, CAKE_DARK)
    for x, y in ((5, 5), (9, 4), (10, 7), (6, 8), (8, 6)):
        put(img, x, y, CAKE_DARK)
    return texkit.icon(img, 41)  # ringed in its own browns


# ---- Models ----

def port_model():
    """A copper port where a pipe meets the press, to the north: a collar the pipe's own butts against at the block's
    edge, the pipe's bore carried in a hand's width, and a short spout turned down into the tray over its rim."""
    tex = {"particle": "nookworks:block/copper_pipe", "barrel": "nookworks:block/copper_pipe",
           "collar": "nookworks:block/copper_pipe_collar", "bore": "nookworks:block/copper_pipe_end"}

    def el(frm, to, faces):
        return {"from": frm, "to": to, "faces": {f: {"texture": t} for f, t in faces.items()}}
    side = {"east": "#barrel", "west": "#barrel", "up": "#barrel", "down": "#barrel"}
    return {"parent": "minecraft:block/block", "textures": tex, "elements": [
        el([3, 3, 0], [13, 13, 1], dict(side, north="#bore", south="#collar")),
        el([4, 4, 1], [12, 12, 3], dict(side, south="#barrel")),
        el([5, 2, 3], [11, 10, 5], {"north": "#barrel", "south": "#barrel", "east": "#barrel", "west": "#barrel", "up": "#barrel",
                                   "down": "#bore"})]}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf8"))


def box(frm, to, tex, faces="nsewud", uv=None, cull=None):
    names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    out = {}
    for f in faces:
        t = tex[f] if isinstance(tex, dict) else tex
        face = {"texture": t}
        if uv:
            face["uv"] = uv[f] if isinstance(uv, dict) else uv
        if cull and f in cull:
            face["cullface"] = names[f]
        out[names[f]] = face
    return {"from": frm, "to": to, "faces": out}


def press_model(part="all"):
    """The press in parts: the base, the hand crank on top of the screw, and the axle a Motor drives it by - out of the
    north side at the height of the motor's shaft into a gear collar round the screw ("all" is base and crank, for the
    item)."""
    textures = {"particle": "minecraft:block/spruce_planks", "planks": "minecraft:block/spruce_planks",
                "post": "minecraft:block/stripped_spruce_log", "iron": "nookworks:block/black_iron",
                "basin": "nookworks:block/oil_press_basin", "screw": "nookworks:block/oil_press_screw"}
    # the handle's bar in two, either side of the screw's head (laid over it, the two tops fought in one plane), and the
    # wooden grip standing up off its end: hanging down, it went through the crossbeam as the handle went round (the
    # user, 2026-10-10)
    crank = [box([2, 15, 7.5], [7, 16, 8.5], "#iron"), box([9, 15, 7.5], [14, 16, 8.5], "#iron"),
             # above the block, so its faces name their part of the log (their own would fall off the texture)
             box([2, 16, 7], [3, 18, 9], "#post", uv={"n": [7, 3, 8, 5], "s": [8, 3, 9, 5], "e": [7, 3, 9, 5],
                                                       "w": [7, 3, 9, 5], "u": [7, 2, 8, 4], "d": [7, 2, 8, 4]})]
    axle = [box([6.5, 6.5, 0], [9.5, 9.5, 6], "#iron"), box([6, 6, 6], [10, 10, 10], "#screw")]
    if part == "crank":
        return {"textures": textures, "elements": crank}
    if part == "axle":
        return {"textures": textures, "elements": axle}
    els = [
        # the tray: a floor of oil-dark boards, and a rim round it the oil stands in
        box([1, 0, 1], [15, 1, 15], {"n": "#planks", "s": "#planks", "e": "#planks", "w": "#planks", "u": "#basin",
                                     "d": "#planks"}, cull="d"),
        box([1, 1, 1], [15, 3, 2], "#planks"),
        box([1, 1, 14], [15, 3, 15], "#planks"),
        box([1, 1, 2], [2, 3, 14], "#planks"),
        box([14, 1, 2], [15, 3, 14], "#planks"),
        # the spout at the front
        box([7, 1, 0], [9, 3, 1], "#iron"),
        # two posts and the crossbeam on them (the posts stop under it: run up through it, their tops and its fought)
        box([2, 3, 7], [4, 12, 9], "#post"),
        box([12, 3, 7], [14, 12, 9], "#post"),
        box([1, 12, 6], [15, 14, 10], "#planks"),
        # the plate, the screw through the beam, and the handle across its top
        box([4, 4, 4], [12, 6, 12], "#iron"),
        box([7, 6, 7], [9, 16, 9], "#screw", faces="nsewu"),
    ]
    if part == "all":
        els += crank
    return {"parent": "minecraft:block/block", "textures": textures, "elements": els}


FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def driven_states(name, oil=False):
    """The machine turned to its facing. Its hand crank, and the axle out to a Motor when one drives it, are drawn by
    client/render/CrankRenderer, which turns them as the handle is worked (models/block/<name>_crank and _axle).
    {@code oil}: the oil standing in the tray at its OIL state (none, some, plenty)."""
    parts = []
    for facing, y in FACINGS:
        parts.append({"when": {"facing": facing}, "apply": dict({"model": f"nookworks:block/{name}_base"}, **({"y": y} if y else {}))})
    if oil:
        for level in (1, 2):
            parts.append({"when": {"oil": str(level)}, "apply": {"model": f"nookworks:block/{name}_oil_{level}"}})
    return {"multipart": parts}


def oil_surface(level):
    """The oil standing in the tray: a pixel over the floor with some in, a pixel under the rim with plenty."""
    h = 1.6 if level == 1 else 2.6
    return {"parent": "minecraft:block/block", "textures": {"particle": "nookworks:block/vegetable_oil_still",
                                                             "oil": "nookworks:block/vegetable_oil_still"},
            "elements": [{"from": [2, h, 2], "to": [14, h, 14], "shade_direction_override": "up",
                          "faces": {"up": {"texture": "#oil", "uv": [2, 2, 14, 14]}}}]}


def motor_model(lit, shaft=False):
    """The housing and its flange; the shaft is turned by client/render/MotorRenderer (models/block/motor_shaft), and is
    in the model only for the item. Its sides, top and bottom show columns and rows 2-13 of their textures, the same
    region on every face, so the fins line up all round. Running, a glowing quad lies over each side's window."""
    side = "#side_on" if lit else "#side"
    region = [2, 2, 14, 14]
    els = [
        box([2, 2, 4], [14, 14, 16], {"n": "#end", "s": "#end", "e": side, "w": side, "u": "#top", "d": "#top"},
            uv={"n": region, "s": region, "e": region, "w": region, "u": region, "d": region}, cull="s"),
        # the flange: its faces the end plate's, its rim the plate's own edge, pixel for pixel
        box([1, 1, 3], [15, 15, 5], "#end", uv={"n": [1, 1, 15, 15], "s": [1, 1, 15, 15], "e": [1, 1, 3, 15],
                                                "w": [13, 1, 15, 15], "u": [1, 1, 15, 3], "d": [1, 13, 15, 15]}),
    ]
    if lit:
        for face, x in (("e", 14.01), ("w", 1.99)):
            glow = box([x, 2, 4], [x, 14, 16], {face: "#glow"}, faces=face, uv={face: region})
            glow["light_emission"] = 15
            els.append(glow)
    if shaft:
        els += shaft_model()["elements"]
    textures = {"particle": "nookworks:block/motor_end", "end": "nookworks:block/motor_end",
                "side": "nookworks:block/motor_casing", "top": "nookworks:block/motor_casing_top",
                "shaft": "nookworks:block/black_iron"}
    if lit:
        textures.update({"side_on": "nookworks:block/motor_casing_on", "glow": "nookworks:block/motor_coil_glow"})
    return {"parent": "minecraft:block/block", "textures": textures, "elements": els}


def shaft_model():
    """The shaft out of the motor's front, square, to the edge of the block: where it meets the axle of the machine it
    drives, which turns with it (client/render/CrankRenderer)."""
    return {"textures": {"particle": "nookworks:block/black_iron", "shaft": "nookworks:block/black_iron"},
            "elements": [box([6.5, 6.5, 0], [9.5, 9.5, 3], "#shaft", faces="nsewud", cull="n")]}


def main():
    for name, img in (("oil_press_basin", basin_top()), ("oil_press_screw", screw()), ("motor_casing", casing(False)),
                      ("motor_casing_on", casing(True)), ("motor_casing_top", casing_top()), ("motor_coil_glow", coil_glow()),
                      ("motor_end", end_plate())):
        img.save(BLOCK / f"{name}.png")
    oil_cake().save(ITEM / "oil_cake.png")

    write_json(ASSETS / "models/block/oil_press.json", press_model())
    for part in ("base", "crank", "axle"):
        write_json(ASSETS / f"models/block/oil_press_{part}.json", press_model(part))
    states = driven_states("oil_press", oil=True)
    # where a Copper Pipe joins it, a port on the tray's rim (the user, 2026-10-31): drawn to the north, turned to each side
    write_json(ASSETS / "models/block/oil_press_port.json", port_model())
    for side, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        part = {"when": {f"port_{side}": "true"}, "apply": {"model": "nookworks:block/oil_press_port"}}
        if y:
            part["apply"]["y"] = y
        states["multipart"].append(part)
    write_json(ASSETS / "blockstates/oil_press.json", states)
    for level in (1, 2):
        write_json(ASSETS / f"models/block/oil_press_oil_{level}.json", oil_surface(level))
    write_json(ASSETS / "items/oil_press.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/oil_press"}})

    write_json(ASSETS / "models/block/motor.json", motor_model(False))
    write_json(ASSETS / "models/block/motor_on.json", motor_model(True))
    write_json(ASSETS / "models/block/motor_shaft.json", shaft_model())
    write_json(ASSETS / "models/block/motor_item.json", motor_model(False, shaft=True))
    rotations = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    variants = {}
    for facing, rot in rotations.items():
        for powered in ("false", "true"):
            variants[f"facing={facing},powered={powered}"] = dict(
                {"model": "nookworks:block/motor_on" if powered == "true" else "nookworks:block/motor"}, **rot)
    write_json(ASSETS / "blockstates/motor.json", {"variants": variants})
    write_json(ASSETS / "items/motor.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/motor_item"}})

    write_json(ASSETS / "models/item/oil_cake.json", {"parent": "minecraft:item/generated",
                                                        "textures": {"layer0": "nookworks:item/oil_cake"}})
    write_json(ASSETS / "items/oil_cake.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/oil_cake"}})


if __name__ == "__main__":
    main()
