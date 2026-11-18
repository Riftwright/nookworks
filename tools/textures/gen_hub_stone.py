"""Hub Stone: smooth stone with redstone let into it - on every face a square ring with a spur to the middle of each
edge, sunk a pixel into the stone, so a run of Hub Stones is rings joined by links, and every face shows whether the
line is live. Powered, the redstone glows, and a pulse of light runs along the line from what powers it.

(The user, 2026-11-14, of seven looks shown in the game: "Dがダントツでかっこいいじゃないか", and of the light: "赤いライン
が、来た方向から向かう方向へ走るように順番に光るようなアニメーションがつけば最高だけどな" - then, shown it: "このまま採用で
よいです". It was a bolted metal casing with two red tubes on its sides and nothing on its top, hand-finished art:
those three pictures are kept in tools/textures/retired/.)

How the light runs with nothing told to the block: a Hub Stone's power falls by one for every Hub Stone the signal has
come through, so 15 - power is how far it stands from its source. The powered face is an animation of eight frames -
a hot pulse over the red, its tail, then the red at rest - and there are eight copies of it, each begun one frame
later than the last; the blockstate gives a stone the copy for its distance (mod 8). So the pulse is on the first
stone, then the second, then the third: round corners and down branches, away from the source.

Writes
  textures/block/hub_stone_off.png, hub_stone_on.png (lit and still: what shows with the client setting Hub Stone
      Light Runs off), hub_stone_flow_0..7.png with their .mcmeta, hub_stone_wall.png (the walls of the recesses)
  models/block/hub_stone_shape.json and hub_stone_shape_lit.json (the block in relief; lit, what is let in glows),
      hub_stone_off.json, hub_stone_on.json, hub_stone_flow_0..7.json (a shape and its face)
  blockstates/hub_stone.json
Our own pixels, drawn here in smooth stone's manner (none of vanilla's). Deterministic.
Run from the repo root:  python tools/textures/gen_hub_stone.py
"""
import json
import sys
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import gen_machine_models as machines  # noqa: E402  (box and face, as the machines' models are written)

ASSETS = ROOT / "src/main/resources/assets/nookworks"
TEXTURES, MODELS = ASSETS / "textures/block", ASSETS / "models/block"
NW = "nookworks:block/"
ALL6 = ("north", "south", "west", "east", "up", "down")
SEED = 540

# ---------------------------------------------------------------- the face

CELLS = {(u, v) for u in range(16) for v in range(16)}
RIM = {(u, v) for u, v in CELLS if u in (0, 15) or v in (0, 15)}
RING = {(u, v) for u in range(3, 13) for v in range(3, 13) if u in (3, 4, 11, 12) or v in (3, 4, 11, 12)}
SPURS = {(u, v) for u, v in CELLS if (u in (7, 8) and (v < 3 or v > 12)) or (v in (7, 8) and (u < 3 or u > 12))}
LINES = RING | SPURS
# What is let into the stone: the lines, but for each spur's last pixel - the rim stays whole, so the block is closed
# at its edges and nothing shows through beside a block set against it. Those last pixels light up with the rest.
SUNK, ENDS = LINES - RIM, SPURS & RIM

STONE, STONE_EDGE, STONE_LIT, STONE_SHADE = (166, 166, 166), (124, 124, 124), (180, 180, 180), (146, 146, 146)
IRON, IRON_LIT, IRON_DARK = (60, 62, 68), (98, 102, 110), (40, 41, 46)
OFF = {"hi": (88, 34, 30), "lo": (66, 25, 23)}
ON = {"hi": (252, 92, 68), "lo": (232, 58, 44)}
HOT = {"hi": (255, 232, 210), "lo": (255, 190, 160)}
WALL = (112, 112, 114)  # the recesses' walls: the stone in its own shadow

FRAMES = 8
PULSE = (1.0, 0.62, 0.34, 0.14, 0.0, 0.0, 0.0, 0.0)  # how hot the red is, frame by frame: the head and its tail
FRAMETIME = 2  # ticks a frame: the pulse passes ten stones a second, and one follows another eight stones behind


def put(img, cell, colour):
    img.putpixel(cell, tuple(colour) + (255,))


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def slab():
    """Our own smooth stone: one slab, its edge a darker line, lit along the top and left inside it."""
    img = Image.new("RGBA", (16, 16), STONE + (255,))
    for i in range(16):
        for cell in ((i, 0), (i, 15), (0, i), (15, i)):
            put(img, cell, STONE_EDGE)
    for i in range(1, 15):
        put(img, (i, 14), STONE_SHADE)
        put(img, (14, i), STONE_SHADE)
    for i in range(1, 14):
        put(img, (i, 1), STONE_LIT)
        put(img, (1, i), STONE_LIT)
    return texkit.shade(img, SEED, material="stone", **texkit.SMOOTH)


def face(tones):
    """The stone with its lines in the two reds given (a line is two pixels: its upper or left one the lighter), and
    in the island the ring leaves, an iron stud."""
    img = slab()
    for u, v in LINES:
        right_edge = (u - 1, v) in LINES and (u + 1, v) not in LINES
        foot = (u, v - 1) in LINES and (u, v + 1) not in LINES
        put(img, (u, v), tones["lo"] if right_edge or foot else tones["hi"])
    for cell, colour in (((7, 7), IRON_LIT), ((8, 7), IRON), ((7, 8), IRON), ((8, 8), IRON_DARK)):
        put(img, cell, colour)
    return img


def flow():
    """The powered face with the pulse passing: eight frames down a strip."""
    strip = Image.new("RGBA", (16, 16 * FRAMES))
    for frame, heat in enumerate(PULSE):
        strip.paste(face({key: mix(ON[key], HOT[key], heat) for key in ("hi", "lo")}), (0, 16 * frame))
    return strip


def wall():
    return texkit.shade(Image.new("RGBA", (16, 16), WALL + (255,)), SEED + 1, material="stone", **texkit.SMOOTH)


# ---------------------------------------------------------------- the shape

AXIS = {"west": (0, 0), "east": (0, 1), "down": (1, 0), "up": (1, 1), "north": (2, 0), "south": (2, 1)}
# The block's twelve edges belong to one face each, so no two plates share a pixel of them (two faces drawn in one
# plane by boxes of different sizes need not shade alike, and would flicker): the north and south faces have all
# their pixels, the west and east ones all but their upright edges, the top and bottom none of their rim.
OWNED = {"north": CELLS, "south": CELLS,
         "west": {(u, v) for u, v in CELLS if 1 <= u <= 14}, "east": {(u, v) for u, v in CELLS if 1 <= u <= 14},
         "up": CELLS - RIM, "down": CELLS - RIM}


def place(side, u0, v0, u1, v1):
    """The box a pixel deep under a face's pixels u0..u1, v0..v1 (ends exclusive): a cube's own projection, so the face
    it shows wears those very pixels with no uv given."""
    return {"south": ([u0, 16 - v1, 15], [u1, 16 - v0, 16]),
            "north": ([16 - u1, 16 - v1, 0], [16 - u0, 16 - v0, 1]),
            "west": ([0, 16 - v1, u0], [1, 16 - v0, u1]),
            "east": ([15, 16 - v1, 16 - u1], [16, 16 - v0, 16 - u0]),
            "up": ([u0, 15, v0], [u1, 16, v1]),
            "down": ([u0, 0, 16 - v1], [u1, 1, 16 - v0])}[side]


def rects(cells):
    """A set of pixels as rectangles (u0, v0, u1, v1), ends exclusive: row runs, grown downward."""
    left, found = set(cells), []
    while left:
        u0, v0 = min(left, key=lambda c: (c[1], c[0]))
        u1 = u0
        while (u1 + 1, v0) in left:
            u1 += 1
        v1 = v0
        while all((u, v1 + 1) in left for u in range(u0, u1 + 1)):
            v1 += 1
        for u in range(u0, u1 + 1):
            for v in range(v0, v1 + 1):
                left.discard((u, v))
        found.append((u0, v0, u1 + 1, v1 + 1))
    return found


def hidden(box, side, boxes):
    """Whether the face of a box on that side lies wholly against the other boxes (tools/check_model_faces.py asks the
    same of every face a model leaves out): every pixel of it, a hair beyond the face, is inside one of them."""
    frm, to = box
    axis, high = AXIS[side]
    a, b = [i for i in range(3) if i != axis]
    beyond = to[axis] + 0.01 if high else frm[axis] - 0.01
    for i in range(frm[a], to[a]):
        for j in range(frm[b], to[b]):
            p = [0.0, 0.0, 0.0]
            p[axis], p[a], p[b] = beyond, i + 0.5, j + 0.5
            if not any(o is not box and all(o[0][k] <= p[k] <= o[1][k] for k in range(3)) for o in boxes):
                return False
    return True


def opening(box, side, boxes):
    """The face of the block a recess wall is seen through: the one whose pixel-deep band holds the open space in front
    of the wall. That is not always the face the plate lies in - a plate of a side's rim, along the block's top edge,
    walls the end of the top's spur - so it is asked of the space itself, every open pixel of it (a wall may run on
    past the recess, against another plate), and it must be one face's."""
    frm, to = box
    axis, high = AXIS[side]
    a, b = [i for i in range(3) if i != axis]
    found = set()
    for i in range(frm[a], to[a]):
        for j in range(frm[b], to[b]):
            p = [0.0, 0.0, 0.0]
            p[axis], p[a], p[b] = (to[axis] + 0.01 if high else frm[axis] - 0.01), i + 0.5, j + 0.5
            if any(o is not box and all(o[0][k] <= p[k] <= o[1][k] for k in range(3)) for o in boxes):
                continue
            found.add(tuple(s for s, (ax, hi) in AXIS.items() if (p[ax] > 15 if hi else p[ax] < 1)))
    assert len(found) == 1 and len(next(iter(found))) == 1, (box, side, found)
    return next(iter(found))[0]


def wall_face(side, box, boxes):
    """A recess's wall, in the wall's own picture a texel to a pixel (AssetAudit: no face stretches its texture),
    culled with the face the recess opens through."""
    frm, to = box
    dx, dy, dz = (to[i] - frm[i] for i in range(3))
    across = dz if side in ("east", "west") else dx
    down = dz if side in ("up", "down") else dy
    return machines.face("#wall", cull=opening(box, side, boxes), uv=[0, 0, across, down])


def shape(lit):
    """The block in relief: a core a pixel in from every face - all that shows of it is what is let in, so lit it is
    the lines that glow - and over it the rest of each face as plates a pixel thick, a spur's last pixel a plate of
    its own that lights with the lines. A plate has the faces that can be seen: its own pixels where it lies in one
    of the block's faces, a wall where it looks into a recess, none where it lies against another.

    The rim is whole, so a recess opens through one face only, and what lies in it - the core's face beneath and the
    walls round it - is culled with that face: a Hub Stone let into a floor or a wall draws its open face alone, not
    the five buried faces' recesses as well (about 150 of its 264 faces would be drawn there unseen)."""
    assert not (SUNK & RIM)
    core = ([1, 1, 1], [15, 15, 15])
    plates = []  # (box, name, light)
    for side in ALL6:
        for cells, name, light in (((CELLS - SUNK - ENDS) & OWNED[side], "stone", 0),
                                   (ENDS & OWNED[side], "spur end", 15 if lit else 0)):
            plates += [(place(side, *rect), name, light) for rect in rects(cells)]
    boxes = [core] + [box for box, _, _ in plates]
    elements = [machines.box(core[0], core[1], {f: machines.face("#face", cull=f) for f in ALL6}, name="let in",
                             light=15 if lit else 0)]
    for box, name, light in plates:
        frm, to = box
        faces = {}
        for side, (axis, high) in AXIS.items():
            if (to[axis] if high else frm[axis]) == (16 if high else 0):
                faces[side] = machines.face("#face", cull=side)
            elif not hidden(box, side, boxes):
                faces[side] = wall_face(side, box, boxes)
        elements.append(machines.box(frm, to, faces, name=name, light=light))
    return {"parent": "minecraft:block/block", "textures": {"particle": "#face", "wall": NW + "hub_stone_wall"},
            "elements": elements}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def phase(power):
    """Which copy of the animation a Hub Stone of this power shows: its distance from the source, mod eight."""
    return (15 - power) % FRAMES


def main():
    face(OFF).save(TEXTURES / "hub_stone_off.png")
    face(ON).save(TEXTURES / "hub_stone_on.png")
    wall().save(TEXTURES / "hub_stone_wall.png")
    strip = flow()
    write(MODELS / "hub_stone_shape.json", shape(False))
    write(MODELS / "hub_stone_shape_lit.json", shape(True))
    write(MODELS / "hub_stone_off.json", {"parent": NW + "hub_stone_shape", "textures": {"face": NW + "hub_stone_off"}})
    write(MODELS / "hub_stone_on.json", {"parent": NW + "hub_stone_shape_lit", "textures": {"face": NW + "hub_stone_on"}})
    for n in range(FRAMES):
        name = f"hub_stone_flow_{n}"
        strip.save(TEXTURES / f"{name}.png")
        write(TEXTURES / f"{name}.png.mcmeta", {"animation": {
            "frametime": FRAMETIME, "interpolate": True, "frames": [(i - n) % FRAMES for i in range(FRAMES)]}})
        write(MODELS / f"{name}.json", {"parent": NW + "hub_stone_shape_lit", "textures": {"face": NW + name}})
    variants = {"power=0": {"model": NW + "hub_stone_off"}}
    for power in range(1, 16):
        variants[f"power={power}"] = {"model": f"{NW}hub_stone_flow_{phase(power)}"}
    write(ASSETS / "blockstates/hub_stone.json", {"variants": variants})
    print("wrote the hub stone:", len(shape(False)["elements"]), "elements a shape")


if __name__ == "__main__":
    main()
