"""Block models of the machines: one shape language for all of them, so they read as a family and as objects.

A machine is a cabinet: four corner posts and a rim round the top and bottom, 2 px thick, and between them its panels
set back 1 px - the frame drawn round the edge of every machine face becomes a real frame, and the panel inside it a
real recess that catches shadow. Nothing moves in a face texture for this: the model is laid over the same 16x16
faces exactly where a plain cube would put them (default UVs are a cube's), so a face drawn for a cube - the
hand-finished ones included - lands pixel for pixel where it did, only now in relief. On that cabinet each machine
has the parts that say what it is (see each builder below): they stay within the block, so machines still stack.

Writes models/block/<machine>.json (and the lit variants). Run from the repo root:  python tools/gen_machine_models.py
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools/textures"))
from gen_machine_faces import LAMPS  # noqa: E402  (where each machine's lamp is drawn)
sys.path.insert(0, str(ROOT / "tools"))
import gen_pipe_ports  # noqa: E402

MODELS = ROOT / "src/main/resources/assets/nookworks/models/block"
STATES = ROOT / "src/main/resources/assets/nookworks/blockstates"

SIDES = ("north", "south", "west", "east")
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west", "up": "down", "down": "up"}


def default_uv(frm, to, face):
    """The UV a face gets when a model gives none: the cube's own projection (FaceBakery.defaultFaceUV)."""
    x1, y1, z1 = frm
    x2, y2, z2 = to
    return {
        "down": [x1, 16 - z2, x2, 16 - z1],
        "up": [x1, z1, x2, z2],
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
    }[face]


def box(frm, to, faces, name=None, light=0, shade=True):
    """An element. {@code faces}: face -> dict (texture, optional uv/cullface/rotation); a face left out is not drawn."""
    element = {"from": list(frm), "to": list(to), "faces": faces}
    if name:
        element["name"] = name
    if light:
        element["light_emission"] = light
    if not shade:
        element["shade_direction_override"] = "up"  # unshaded: 26.3 has no "shade": false
    return element


def face(texture, cull=None, uv=None, rotation=None):
    f = {"texture": texture}
    if uv is not None:
        f["uv"] = uv
    if cull:
        f["cullface"] = cull
    if rotation:
        f["rotation"] = rotation
    return f


def cabinet(inset=1, top=16, frame=2, recess_top=True):
    """
    The frame and the recessed body. Posts and rims wear the block's own face textures (#north, #south, ...) where a
    cube would; the 1 px sides of each recess wear the frame's own pixels from the face they border, so the frame
    reads as solid metal turned into the recess, not as a stripe of some other texture.
    {@code top}: the cabinet's height (a machine with parts on top is shorter); {@code recess_top}: whether the top
    panel is set back like the sides (a flat lid otherwise).
    """
    t, f, i = top, frame, inset
    out = []
    lo, hi = f, 16 - f
    # the body: the panels, set back
    body_top = t - i if recess_top else t
    out.append(box([i, i, i], [16 - i, body_top, 16 - i], {
        "north": face("#north"), "south": face("#south"), "west": face("#west"), "east": face("#east"),
        "up": face("#up", cull="up" if body_top == 16 else None), "down": face("#down"),
    }, name="panels"))

    def recess_side(side, frm, to, facing):
        """A face of a frame piece that looks into the recess beside it: that side's frame pixels, turned in."""
        return face("#" + side, uv=default_uv(frm, to, side))

    # corner posts
    for cx in (0, 16 - f):
        for cz in (0, 16 - f):
            frm, to = [cx, 0, cz], [cx + f, t, cz + f]
            xs = "west" if cx == 0 else "east"
            zs = "north" if cz == 0 else "south"
            faces = {
                xs: face("#" + xs, cull=xs), zs: face("#" + zs, cull=zs),
                "up": face("#up", cull="up" if t == 16 else None), "down": face("#down", cull="down"),
                OPPOSITE[xs]: recess_side(zs, frm, to, OPPOSITE[xs]),
                OPPOSITE[zs]: recess_side(xs, frm, to, OPPOSITE[zs]),
            }
            out.append(box(frm, to, faces, name="post"))
    # rims, top and bottom, between the posts
    for y0, y1, edge in ((t - f, t, "up"), (0, f, "down")):
        if edge == "up" and not recess_top:
            continue  # a flat lid: the body's own top reaches the edge
        inward = OPPOSITE[edge]
        for side in SIDES:
            if side == "north":
                frm, to = [lo, y0, 0], [hi, y1, f]
            elif side == "south":
                frm, to = [lo, y0, 16 - f], [hi, y1, 16]
            elif side == "west":
                frm, to = [0, y0, lo], [f, y1, hi]
            else:
                frm, to = [16 - f, y0, lo], [16, y1, hi]
            # A west or east rim runs along z, but the strips it borrows run along u: turned a quarter, so they lie
            # along it pixel for pixel (unturned, 12 px were squeezed into 2 and 2 stretched over 12), the side's edge
            # row on the rim's outer edge.
            turn = None
            if side in ("west", "east"):
                turn = (270 if side == "west" else 90) if inward == "up" else (90 if side == "west" else 270)
            faces = {
                side: face("#" + side, cull=side),
                edge: face("#" + edge, cull=edge if (edge == "down" or t == 16) else None),
                inward: face("#" + side, uv=default_uv(frm, to, side), rotation=turn),  # into the recess of the side below/above
                # and into the recess of the lid (the top panel, set back): the lid's frame pixels, turned in
                OPPOSITE[side]: face("#" + edge, uv=default_uv(frm, to, edge), rotation=turn),
            }
            out.append(box(frm, to, faces, name="rim"))
    return out


def solid(frm, to, textures="side", cull=(), skip=(), name=None, light=0):
    """
    A plain part: every face from the machine's own textures by where it stands (#side for the four sides unless
    {@code textures} maps them otherwise, #up, #down), culled against the block edges listed in {@code cull}.
    """
    tex = textures if isinstance(textures, dict) else {s: textures for s in SIDES}
    faces = {}
    for f in SIDES + ("up", "down"):
        if f in skip:
            continue
        faces[f] = face("#" + tex.get(f, f), cull=f if f in cull else None)
    return box(frm, to, faces, name=name, light=light)


ROUNDS = {
    # width -> (half across x, half across z) of each box: a pixel circle, the way vanilla draws its round things
    4: ((2, 2),),
    6: ((3, 2), (2, 3)),
    8: ((4, 3), (3, 4)),
    10: ((5, 3), (4, 4), (3, 5)),
    12: ((6, 3), (5, 5), (3, 6)),
    14: ((7, 3), (6, 5), (5, 6), (3, 7)),
    16: ((8, 3), (7, 5), (5, 7), (3, 8)),
}


def round_column(y0, y1, width, name, textures="side", skip=("down",), cull=()):
    """
    A round thing standing on end, as blocks draw one: boxes crossed, stepping in at the corners - a pixel circle
    {@code width} px across on the middle of the block. Where the boxes overlap their faces lie in one plane, but
    with a cube's UVs they draw the same pixels there, so nothing flickers.
    """
    return [solid([8 - hx, y0, 8 - hz], [8 + hx, y1, 8 + hz], textures=textures, skip=skip, cull=cull, name=name)
            for hx, hz in ROUNDS[width]]


def model(textures, elements, particle=None):
    tex = dict(textures)
    tex.setdefault("particle", particle or tex.get("south") or tex.get("north"))
    return {"parent": "minecraft:block/block", "textures": tex, "elements": elements}


# vanilla's block/block shows a block's north face in the inventory, where its furnace has its front; these machines are
# drawn with their front to the south, so their items are shown turned half round: the front where a furnace's is. Every
# view is turned, not the inventory's alone - an item frame and a shelf showed the back (r21, 2026-11-06)
FRONT_SOUTH_GUI = {
    "gui": {"rotation": [30, 45, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "on_shelf": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}


def front_in_gui(data):
    """An item showing a machine drawn front-south: turned half round in every view, so the front shows as a furnace's."""
    return {**data, "display": dict(FRONT_SOUTH_GUI)}


def faces6(side=None, top=None, bottom=None, **named):
    """Texture map: {@code side} for all four sides unless one is named."""
    t = {s: named.get(s, side) for s in SIDES}
    t["up"] = top
    t["down"] = bottom
    return t


def nw(name):
    return "nookworks:block/" + name


def write(name, data):
    (MODELS / f"{name}.json").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def write_state(name, data):
    data = gen_pipe_ports.with_ports(name, data)  # the pipe ports, on the machines that show them
    (STATES / f"{name}.json").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


# ---------------------------------------------------------------- the machines


def generator(lit):
    tex = faces6(north=nw("generator_back"), south=nw("generator_front_lit" if lit else "generator_front"),
                 west=nw("generator_side"), east=nw("generator_side"), top=nw("generator_top"), bottom=nw("generator_bottom"))
    return model(tex, cabinet())


def creative_generator():
    """
    The Generator's cabinet in creative's faces (gen_machine_faces.py), and the infinity sign of its window laid on
    again by a quad of its own, full bright: it never stops. The quad covers the sign's box only, its glow texture clear
    round the sign, just in front of the recessed panel (as a lamp is).
    """
    tex = faces6(north=nw("creative_generator_back"), south=nw("creative_generator_front"), west=nw("creative_generator_side"),
                 east=nw("creative_generator_side"), top=nw("creative_generator_top"), bottom=nw("machine_bottom"))
    tex["glow"] = nw("creative_generator_glow")
    parts = cabinet()
    parts.append(box([4, 6, 15.05], [12, 10, 15.05], {"south": face("#glow", uv=[4, 6, 12, 10])}, name="glow", light=15,
                     shade=False))
    return model(tex, parts)


def creative_generator_states():
    """Facing south as drawn, turned for the rest."""
    variants = {}
    for facing, turn in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
        variants[f"facing={facing}"] = {"model": nw("creative_generator"), **({"y": turn} if turn else {})}
    return {"variants": variants}


def diesel_generator():
    """
    A heavy cabinet (y 0..12, its lid flat) with the radiator for its front, an exhaust stack 4 across (y 12..16) over
    the back right-hand corner of the lid (DieselGeneratorBlock smokes from its mouth), and the radiator's brass filler
    cap at the front left. The stack and the cap stand where no side sees one in front of the other, so each side's
    elevation draws both (gen_machine_faces.py).
    """
    tex = faces6(north=nw("diesel_generator_back"), south=nw("diesel_generator_front"), west=nw("diesel_generator_left"),
                 east=nw("diesel_generator_right"), top=nw("diesel_generator_top"), bottom=nw("machine_bottom"))
    own = {s: s for s in SIDES}
    parts = cabinet(top=12, recess_top=False)
    parts.append(solid([10, 12, 2], [14, 16, 6], textures=own, skip=("down",), cull=("up",), name="stack"))
    parts.append(solid([3, 12, 11], [5, 13, 13], textures=own, skip=("down",), name="filler cap"))
    return model(tex, parts)


def diesel_generator_states():
    """Facing south as drawn, turned for the rest; the lamp lit while it runs (LIT)."""
    parts = []
    for facing, turn in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
        rot = {"y": turn} if turn else {}
        parts.append({"when": {"facing": facing}, "apply": {"model": nw("diesel_generator"), **rot}})
        parts.append({"when": {"facing": facing, "lit": "true"}, "apply": {"model": nw("diesel_generator_lamp"), **rot}})
    return {"multipart": parts}


def electric_furnace(lit):
    tex = faces6(side=nw("electric_furnace_side"), south=nw("electric_furnace_front_lit" if lit else "electric_furnace_front"),
                 top=nw("electric_furnace_top"), bottom=nw("machine_bottom"))
    return model(tex, cabinet())


def electrolytic_cell(lit):
    tex = faces6(side=nw("electrolytic_cell_side"), south=nw("electrolytic_cell_front_lit" if lit else "electrolytic_cell_front"),
                 top=nw("electrolytic_cell_top"), bottom=nw("machine_bottom"))
    return model(tex, cabinet())


def barter_terminal():
    tex = faces6(side=nw("barter_terminal_side"), south=nw("barter_terminal_front"), top=nw("barter_terminal_top"),
                 bottom=nw("machine_bottom"))
    return model(tex, cabinet())


def barter_terminal_on():
    """While its piglins barter: the lit screen, glowing on the recessed front panel, and the lamp under it."""
    screen = box([4, 6, 15.05], [12, 12, 15.05], {"south": face("#screen", uv=[4, 4, 12, 10])}, name="screen", light=15,
                 shade=False)
    return {"parent": "minecraft:block/block",
            "textures": {"screen": nw("barter_terminal_screen"), "lamp": nw("machine_lamp"), "particle": nw("barter_terminal_front")},
            "elements": [screen] + lamp_quads("barter_terminal_front", 1, sides=("south",))}


def barter_terminal_states():
    """Facing south as drawn, turned for the rest; the screen and lamp lit while it runs."""
    parts = []
    for facing, turn in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
        rot = {"y": turn} if turn else {}
        parts.append({"when": {"facing": facing}, "apply": {"model": nw("barter_terminal"), **rot}})
        parts.append({"when": {"facing": facing, "running": "true"}, "apply": {"model": nw("barter_terminal_on"), **rot}})
    return {"multipart": parts}


def facing_states(name):
    """Facing south as drawn, turned for the other three; the lit model while LIT."""
    variants = {}
    for lit in ("false", "true"):
        for facing, turn in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
            variant = {"model": nw(name + ("_lit" if lit == "true" else ""))}
            if turn:
                variant["y"] = turn
            variants[f"facing={facing},lit={lit}"] = variant
    return {"variants": variants}


def retort(lit):
    s = "_lit" if lit else ""
    tex = faces6(north=nw("retort_back"), south=nw("retort_front" + s), west=nw("retort_side" + s), east=nw("retort_side" + s),
                 top=nw("retort_top"), bottom=nw("retort_bottom"))
    return model(tex, cabinet())


def mercury_tank():
    tex = faces6(north=nw("mercury_tank_back"), south=nw("mercury_tank_front"), west=nw("mercury_tank_side"),
                 east=nw("mercury_tank_side"), top=nw("mercury_tank_top"), bottom=nw("mercury_tank_bottom"))
    return model(tex, cabinet())


def experience_tank():
    """The cabinet with a window in each side but the front (north), where the tap is: that face is the side with its
    window closed over (tools/textures/gen_experience_tank_front.py). The blockstate turns it with the tank."""
    tex = faces6(side=nw("experience_tank_side"), top=nw("experience_tank_end"), bottom=nw("experience_tank_end"),
                 north=nw("experience_tank_front"))
    return model(tex, cabinet(), particle=nw("experience_tank_side"))



def stirring_tank():
    """
    A round steel vessel on a frame plinth, brass-banded, a sight glass on its front, and the stirrer's motor on the
    lid. Plinth y 0..3, vessel 3..12 (14 across), lid 12..13 (12 across), motor 13..16 (6 across).
    """
    tex = {"side": nw("stirring_tank_side"), "up": nw("stirring_tank_top"), "down": nw("stirring_tank_bottom")}
    parts = [solid([0, 0, 0], [16, 3, 16], cull=SIDES + ("down",), name="plinth")]
    parts += round_column(3, 12, 14, "vessel")
    parts += round_column(12, 13, 12, "lid")
    parts.append(solid([5, 13, 5], [11, 16, 11], skip=("down",), cull=("up",), name="motor"))
    return model(tex, parts, particle=nw("stirring_tank_side"))

def round_textures(name, top=None, bottom=None):
    """A symmetric machine's texture map: one elevation for all four sides (and for its round parts), a top, a bottom."""
    side = nw(name + "_side")
    tex = {s: side for s in SIDES}
    tex.update({"side": side, "up": nw(top or name + "_top"), "down": nw(bottom or "machine_bottom")})
    return tex


def ring(y0, y1, width, name, bottom=False, top=False, skip=()):
    """A round part culled against the block's floor or ceiling when it reaches it."""
    cull = (("down",) if bottom else ()) + (("up",) if top else ())
    return round_column(y0, y1, width, name, skip=skip, cull=cull)


def distiller():
    """
    The foot of the distillation tower: a burner cabinet (y 0..8) under a copper boiler (14 across, y 8..13), its
    shoulder and neck, and a brass flange (y 15..16) for the first column to bolt on to.
    """
    parts = cabinet(top=8, recess_top=False)
    parts += ring(8, 13, 14, "boiler")
    parts += ring(13, 14, 12, "shoulder", skip=("down",))
    parts += ring(14, 15, 10, "neck", skip=("down",))
    parts += ring(15, 16, 14, "flange", top=True)
    return model(round_textures("distiller"), parts)


def distillation_column():
    """A steel tube (10 across) between two bolted brass flanges (14 across, 2 high): stacked, flange meets flange."""
    tex = {s: nw("distillation_column_side") for s in SIDES + ("side",)}
    tex.update({"up": nw("distillation_column_end"), "down": nw("distillation_column_end")})
    parts = ring(0, 2, 14, "flange", bottom=True, skip=())
    parts += ring(2, 14, 10, "tube", skip=("up", "down"))
    parts += ring(14, 16, 14, "flange", top=True, skip=())
    return model(tex, parts)


def condenser():
    """
    The top of the tower: a flange (y 0..2), a finned copper body (12 across, fins 14 across on every other pixel),
    a steel cap and a vent under a hood.
    """
    tex = round_textures("condenser", bottom="condenser_bottom")
    fin = {s: "side" for s in SIDES}
    fin.update({"up": "up", "down": "up"})  # a fin's underside is copper too, not the flange drawn below
    parts = ring(0, 2, 14, "flange", bottom=True, skip=())
    parts += ring(2, 12, 12, "body", skip=("down",))
    for y in (3, 5, 7, 9, 11):
        parts += round_column(y, y + 1, 14, "fin", textures=fin, skip=())
    parts += ring(12, 14, 10, "cap", skip=("down",))
    parts += ring(14, 15, 6, "vent", skip=("down",))
    parts += ring(15, 16, 8, "hood", top=True, skip=())
    return model(tex, parts)


def crusher():
    """A cabinet (y 0..11) with a hopper on it: a neck (12 across) and a hazard-striped mouth (14 across) to y 16."""
    parts = cabinet(top=11, recess_top=False)
    parts.append(solid([2, 11, 2], [14, 13, 14], skip=("up", "down"), name="neck"))
    parts.append(solid([1, 13, 1], [15, 16, 15], textures={**{s: "side" for s in SIDES}, "down": "up"}, cull=("up",), name="mouth"))
    return model(round_textures("crusher"), parts)


def compressor():
    """A cabinet (y 0..12) with the ram's housing on it: a block 10 across, y 12..16, the ram's rod showing on top."""
    parts = cabinet(top=12, recess_top=False)
    parts.append(solid([3, 12, 3], [13, 16, 13], cull=("up",), name="housing"))
    return model(round_textures("compressor"), parts)


def recycler_lower():
    """The Recycler's body: a whole cabinet, as the Stone Maker's (its top is under the intake)."""
    return model(round_textures("recycler"), cabinet())


def recycler_upper():
    """
    The intake: a hopper of plate on the body - a neck (12 across, y 0..4), then walls 2 px thick in two tiers stepping
    out as a hopper flares (14 across y 4..10, the whole block y 10..16), open on top, the shredder's toothed bed down
    in its throat (y 6).
    """
    tex = {"side": nw("recycler_top_side"), "up": nw("recycler_top"), "down": nw("recycler_top_side"),
           "throat": nw("recycler_top"), "particle": nw("recycler_top_side")}
    parts = [solid([2, 0, 2], [14, 4, 14], skip=("up",), name="neck")]
    parts.append(box([3, 6, 3], [13, 6, 13], {"up": face("#throat", uv=[3, 3, 13, 13])}, name="shredder"))
    for lo, hi, y0, y1 in ((1, 15, 4, 10), (0, 16, 10, 16)):
        for side in SIDES:
            frm, to = {"north": ([lo, y0, lo], [hi, y1, lo + 2]), "south": ([lo, y0, hi - 2], [hi, y1, hi]),
                       "west": ([lo, y0, lo + 2], [lo + 2, y1, hi - 2]), "east": ([hi - 2, y0, lo + 2], [hi, y1, hi - 2])}[side]
            faces = {f: face("#side", cull=f if hi == 16 and f in (side, "up") or f == "up" and y1 == 16 else None)
                     for f in SIDES + ("up", "down")}
            faces["up"] = face("#up", uv=default_uv(frm, to, "up"), cull="up" if y1 == 16 else None)
            parts.append(box(frm, to, faces, name="wall"))
    return model(tex, parts)


def recycler_states():
    """The body below with its lamp lit while it runs; the intake above."""
    return {"multipart": [{"when": {"half": "lower"}, "apply": {"model": nw("recycler")}},
                          {"when": {"half": "lower", "running": "true"}, "apply": {"model": nw("recycler_lamp")}},
                          {"when": {"half": "upper"}, "apply": {"model": nw("recycler_upper")}}]}


def open_frame(recess_rims=True):
    """
    The frame alone, every face of it drawn - it is seen through: four posts and the rims top and bottom. Each face
    takes the frame's pixels from the side it lies on or looks across.
    """
    out = []
    for cx in (0, 14):
        for cz in (0, 14):
            out.append(solid([cx, 0, cz], [cx + 2, 16, cz + 2], textures={s: s for s in SIDES},
                             cull=(("west",) if cx == 0 else ("east",)) + (("north",) if cz == 0 else ("south",)) + ("up", "down"),
                             name="post"))
    for y0, y1 in ((0, 2), (14, 16)):
        vert = ("down",) if y0 == 0 else ("up",)
        out.append(solid([2, y0, 0], [14, y1, 2], textures={s: s for s in SIDES}, cull=("north",) + vert, skip=("west", "east"), name="rim"))
        out.append(solid([2, y0, 14], [14, y1, 16], textures={s: s for s in SIDES}, cull=("south",) + vert, skip=("west", "east"), name="rim"))
        out.append(solid([0, y0, 2], [2, y1, 14], textures={s: s for s in SIDES}, cull=("west",) + vert, skip=("north", "south"), name="rim"))
        out.append(solid([14, y0, 2], [16, y1, 14], textures={s: s for s in SIDES}, cull=("east",) + vert, skip=("north", "south"), name="rim"))
    return out


def sifter():
    """
    A sieve on legs: the open frame, a slatted tray with the brass mesh on top (set 1 px into the top rim), and under
    it the funnel and spout the sifted grit runs out of.
    """
    tex = round_textures("sifter", bottom="sifter_bottom")
    parts = open_frame()
    parts.append(solid([2, 10, 2], [14, 15, 14], name="tray"))
    parts.append(solid([4, 4, 4], [12, 10, 12], skip=("up",), name="funnel"))
    parts.append(solid([6, 2, 6], [10, 4, 10], skip=("up",), name="spout"))
    return model(tex, parts)


def water_pump():
    """A cabinet (y 0..11) under a copper pump head (12 across, y 11..15) with a brass dome on it."""
    parts = cabinet(top=11, recess_top=False)
    parts += ring(11, 15, 12, "head", skip=("down",))
    parts += ring(15, 16, 6, "dome", top=True, skip=("down",))
    return model(round_textures("water_pump", bottom="water_pump_bottom"), parts)


def stone_maker():
    return model(round_textures("stone_maker"), cabinet())


def printing_press():
    """A cabinet (y 0..12) with two steel rollers across its top (y 12..15), their round ends showing at the sides."""
    parts = cabinet(top=12, recess_top=False)
    for z0 in (3, 10):
        parts.append(box([1, 12, z0], [15, 15, z0 + 3], {
            "north": face("#side"), "south": face("#side"), "up": face("#up"),
            "west": face("#roller", uv=[0, 0, 3, 3]), "east": face("#roller", uv=[0, 0, 3, 3]),
        }, name="roller"))
    tex = round_textures("printing_press")
    tex["roller"] = nw("printing_press_roller")
    return model(tex, parts)

def spawn_egg_brewer():
    """
    An alchemist's still on a blackstone plate: a round glass vessel of violet brew with the egg in it, a brass foot
    band, a narrow neck under a brass stopper with a shard of amethyst standing in it, and brass rods either side
    holding the neck by two arms. (The layout is in gen_machine_faces.py, over brewer_side.)
    """
    tex = round_textures("spawn_egg_brewer")
    tex.update({"base": "minecraft:block/polished_blackstone", "shard": "minecraft:block/amethyst_block",
                "particle": "minecraft:block/amethyst_block"})
    base = {s: "base" for s in SIDES}
    base.update({"up": "base", "down": "base"})
    parts = [solid([1, 0, 1], [15, 2, 15], textures=base, cull=("down",), name="plate")]
    parts += round_column(2, 9, 10, "vessel")
    # the foot band and the stopper stand out past what is under them, so their undersides show
    parts += round_column(3, 4, 12, "foot band", skip=())
    parts += round_column(9, 10, 8, "shoulder")
    parts += round_column(10, 12, 4, "neck", skip=("down", "up"))
    parts += round_column(12, 13, 6, "stopper", skip=())
    parts.append(solid([7, 13, 7], [9, 16, 9], textures={**{s: "shard" for s in SIDES}, "up": "shard"}, skip=("down",),
                       cull=("up",), name="amethyst"))
    parts.append(solid([1, 2, 7], [2, 12, 9], skip=("down",), name="rod"))
    parts.append(solid([14, 2, 7], [15, 12, 9], skip=("down",), name="rod"))
    parts.append(solid([2, 10, 7], [6, 11, 9], skip=("west",), name="arm"))
    parts.append(solid([10, 10, 7], [14, 11, 9], skip=("east",), name="arm"))
    return model(tex, parts)


def machine_chassis():
    """The frame every machine is built in, and nothing in it yet."""
    tex = {s: nw("machine_chassis") for s in SIDES}
    tex.update({"up": nw("machine_chassis"), "down": nw("machine_chassis")})
    return model(tex, open_frame())

# ---------------------------------------------------------------- sight glasses
# The tanks' windows (x 6..10, y 5..11 on a side, hand-finished) show the liquid standing as high as the block's
# LEVEL (SightGlass.LEVEL, 0..6): the liquid below, the empty glass above, laid 0.02 px in front of the window.
WINDOW = (6, 10, 5, 11)
GLASS_OUT = 0.02


def window_quad(side, y0, y1, texture, uv, light=0):
    x0, x1 = WINDOW[0], WINDOW[1]
    if side == "south":
        frm, to = [x0, y0, 15 + GLASS_OUT], [x1, y1, 15 + GLASS_OUT]
    elif side == "north":
        frm, to = [x0, y0, 1 - GLASS_OUT], [x1, y1, 1 - GLASS_OUT]
    elif side == "west":
        frm, to = [1 - GLASS_OUT, y0, x0], [1 - GLASS_OUT, y1, x1]
    else:
        frm, to = [15 + GLASS_OUT, y0, x0], [15 + GLASS_OUT, y1, x1]
    return box(frm, to, {side: face(texture, uv=uv)}, name="sight glass", light=light)


def sight_glass(level, sides, gauge, light=0):
    """
    The window at one LEVEL: liquid from the bottom up {@code level} px, its surface (the gauge texture's top row)
    at the top; the empty glass above it. The gauge texture holds the liquid in columns 0-3 and the glass in 4-7.
    """
    y_mid = WINDOW[2] + level
    parts = []
    for side in sides:
        if level > 0:
            parts.append(window_quad(side, WINDOW[2], y_mid, "#gauge", [0, 0, 4, level], light))
        if level < 6:
            parts.append(window_quad(side, y_mid, WINDOW[3], "#gauge", [4, 0, 8, 6 - level]))
    return {"parent": "minecraft:block/block", "textures": {"particle": gauge, "gauge": gauge}, "elements": parts}


def mercury_tank_states():
    """The Mercury Tank turns with its facing; its windows are on the two sides beside the front."""
    rot = {"south": 0, "west": 90, "north": 180, "east": 270}
    parts = []
    for facing, y in rot.items():
        spin = {"y": y} if y else {}
        parts.append({"when": {"facing": facing}, "apply": {"model": "nookworks:block/mercury_tank", **spin}})
        for level in range(7):
            parts.append({"when": {"facing": facing, "level": str(level)},
                          "apply": {"model": f"nookworks:block/mercury_tank_gauge_{level}", **spin}})
    return {"multipart": parts}

# ---------------------------------------------------------------- the copper line
LINE_CONTENTS = ("water", "oil", "biodiesel", "other")


def copper_tank():
    """
    Copper Tank: a riveted copper vessel (14 across, y 3..15) on the machines' frame plinth, a brass lid with its
    filler cap; the sight glass down each flat side is laid over it by the gauge models, at the tank's LEVEL.
    """
    tex = round_textures("copper_tank")
    parts = [solid([0, 0, 0], [16, 3, 16], cull=SIDES + ("down",), name="plinth")]
    parts += round_column(3, 15, 14, "vessel")
    parts += round_column(15, 16, 12, "lid", cull=("up",))
    return model(tex, parts, particle=nw("copper_tank_side"))


def copper_tank_gauge(content, level):
    """
    One of the Copper Tank's sight glasses at a LEVEL (0..6), on all four sides: the window is 12 px tall (y 3..15),
    two pixels a step - the liquid from the bottom up, its surface the gauge texture's top row, empty glass above.
    """
    gauge = nw(f"copper_tank_gauge_{content}")
    y_mid = 3 + 2 * level
    parts = []
    for side in SIDES:
        if level > 0:
            liquid = window_quad(side, 3, y_mid, "#gauge", [0, 0, 4, 2 * level])
            if content == "other":  # another mod's liquid, in its own colour (the block's tint 0, WindowTint)
                liquid["faces"][side]["tintindex"] = 0
            parts.append(liquid)
        if level < 6:
            parts.append(window_quad(side, y_mid, 15, "#gauge", [4, 0, 8, 12 - 2 * level]))
    return {"parent": "minecraft:block/block", "textures": {"particle": gauge, "gauge": gauge}, "elements": parts}


def copper_tank_states():
    parts = [{"apply": {"model": "nookworks:block/copper_tank"}}]
    parts.append({"when": {"content": "empty"}, "apply": {"model": "nookworks:block/copper_tank_gauge_empty"}})
    for content in LINE_CONTENTS:
        for level in range(7):
            name = "copper_tank_gauge_empty" if level == 0 else f"copper_tank_gauge_{content}_{level}"
            parts.append({"when": {"content": content, "level": str(level)}, "apply": {"model": f"nookworks:block/{name}"}})
    return {"multipart": parts}


def stub(z0, z1, end):
    """A short length of the Copper Pipe's barrel running out of a fitting's end, north-south, to meet the line. Its
    open end (on side {@code end}) shows the pipe's bore: with no pipe beside it, that end is seen."""
    barrel = {"texture": "#copper", "uv": [4, 0, 12, z1 - z0]}
    return box([4, 4, z0], [12, 12, z1], {"up": dict(barrel), "down": dict(barrel), "east": dict(barrel, rotation=90),
                                           "west": dict(barrel, rotation=90),
                                           end: {"texture": "#bore", "uv": [4, 4, 12, 12]}}, name="stub")


def brass(frm, to, skip=(), name=None):
    return box(frm, to, {f: face("#brass") for f in SIDES + ("up", "down") if f not in skip}, name=name)


def sight_glass_model():
    """
    Sight Glass, lying north-south (the blockstate turns it to its axis): pipe stubs at both ends, brass flanges,
    four brass rods between them at the corners, and the glass chamber. Its liquid is a model of its own.
    """
    parts = [stub(0, 2, "north"), stub(14, 16, "south"),
             brass([2, 2, 2], [14, 14, 4], name="flange"), brass([2, 2, 12], [14, 14, 14], name="flange")]
    for x0, y0 in ((2, 2), (12, 2), (2, 12), (12, 12)):
        parts.append(brass([x0, y0, 4], [x0 + 2, y0 + 2, 12], skip=("north", "south"), name="rod"))
    parts.append(box([3, 3, 4], [13, 13, 12], {f: face("#glass") for f in ("up", "down", "east", "west")}, name="glass"))
    return {"parent": "minecraft:block/block",
            "textures": {"particle": nw("brass_fitting"), "brass": nw("brass_fitting"), "glass": nw("sight_glass_glass"),
                         "copper": nw("copper_pipe"), "bore": nw("copper_pipe_end")},
            "elements": parts}


LIQUID_TEXTURE = {"water": "minecraft:block/water_still", "oil": nw("vegetable_oil_still"),
                  "biodiesel": nw("biodiesel_still"), "other": "minecraft:block/water_still"}
STEP_HEIGHT = (0, 1, 3, 4, 5, 7, 8)  # of the chamber's 8 px, at each LEVEL


def sight_glass_liquid(content, level, vertical):
    """
    What stands in the chamber at a LEVEL: lying along the line it fills from the chamber's floor up; standing up it
    fills from the bottom of the column. Water is tinted as the water where it stands (the block's tint 0); another
    mod's fluid in the grey of water tinted with its own colour (tint 0 too, WindowTint).
    """
    h = STEP_HEIGHT[level]
    if vertical:
        frm, to = [4, 4.05, 4], [12, 4.05 + h - 0.1, 12]
    else:
        frm, to = [4, 4.05, 4.05], [12, 4.05 + h - 0.1, 11.95]
    liquid = {"texture": "#liquid"}
    if content in ("water", "other"):
        liquid["tintindex"] = 0
    element = box(frm, to, {f: dict(liquid) for f in SIDES + ("up", "down")}, name="liquid")
    return {"parent": "minecraft:block/block", "textures": {"particle": LIQUID_TEXTURE[content], "liquid": LIQUID_TEXTURE[content]},
            "elements": [element]}


AXIS_SPIN = {"z": {}, "x": {"y": 90}, "y": {"x": 90}}


def sight_glass_states():
    parts = []
    for axis, spin in AXIS_SPIN.items():
        parts.append({"when": {"axis": axis}, "apply": {"model": "nookworks:block/sight_glass", **spin}})
        for content in LINE_CONTENTS:
            for level in range(1, 7):
                if axis == "y":
                    parts.append({"when": {"axis": "y", "content": content, "level": str(level)},
                                  "apply": {"model": f"nookworks:block/sight_glass_liquid_v_{content}_{level}"}})
                else:
                    parts.append({"when": {"axis": axis, "content": content, "level": str(level)},
                                  "apply": {"model": f"nookworks:block/sight_glass_liquid_h_{content}_{level}",
                                            **({"y": 90} if axis == "x" else {})}})
    return {"multipart": parts}


def valve_model(open_):
    """
    Valve, lying north-south: pipe stubs, brass flanges, a cast brass body, the bonnet and stem, and the red handle -
    along the pipe when open, across it when shut, as a quarter-turn valve's handle shows.
    """
    handle = [7, 15, 2], [9, 16, 14]
    if not open_:
        handle = [2, 15, 7], [14, 16, 9]
    parts = [stub(0, 2, "north"), stub(14, 16, "south"),
             brass([3, 3, 2], [13, 13, 4], name="flange"), brass([3, 3, 12], [13, 13, 14], name="flange"),
             brass([4, 3, 4], [12, 13, 12], skip=("north", "south"), name="body"),
             brass([5, 13, 5], [11, 14, 11], skip=("down",), name="bonnet"),
             box([7, 14, 7], [9, 15, 9], {f: face("#stem") for f in SIDES}, name="stem"),
             box(handle[0], handle[1], {f: face("#handle") for f in SIDES + ("up", "down")}, name="handle")]
    return {"parent": "minecraft:block/block",
            "textures": {"particle": nw("brass_fitting"), "brass": nw("brass_fitting"), "copper": nw("copper_pipe"),
                         "bore": nw("copper_pipe_end"), "stem": nw("black_iron"), "handle": nw("valve_handle")},
            "elements": parts}


def valve_states():
    parts = []
    for axis, spin in AXIS_SPIN.items():
        for open_ in ("true", "false"):
            parts.append({"when": {"axis": axis, "open": open_},
                          "apply": {"model": "nookworks:block/valve" + ("" if open_ == "true" else "_shut"), **spin}})
    return {"multipart": parts}


def write_line():
    write("copper_tank", copper_tank())
    write("copper_tank_gauge_empty", copper_tank_gauge("water", 0))
    for content in LINE_CONTENTS:
        for level in range(1, 7):
            write(f"copper_tank_gauge_{content}_{level}", copper_tank_gauge(content, level))
            write(f"sight_glass_liquid_h_{content}_{level}", sight_glass_liquid(content, level, False))
            write(f"sight_glass_liquid_v_{content}_{level}", sight_glass_liquid(content, level, True))
    write_state("copper_tank", copper_tank_states())
    write("sight_glass", sight_glass_model())
    write_state("sight_glass", sight_glass_states())
    write("valve", valve_model(True))
    write("valve_shut", valve_model(False))
    write_state("valve", valve_states())
    items = ROOT / "src/main/resources/assets/nookworks/items"
    for name, data in (
            ("sight_glass", {"model": {"type": "minecraft:model", "model": "nookworks:block/sight_glass"}}),
            ("valve", {"model": {"type": "minecraft:model", "model": "nookworks:block/valve"}}),
            ("copper_tank", {"model": {"type": "minecraft:composite", "models": [
                {"type": "minecraft:model", "model": "nookworks:block/copper_tank"},
                {"type": "minecraft:model", "model": "nookworks:block/copper_tank_gauge_empty"}]}})):
        (items / f"{name}.json").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


# ---------------------------------------------------------------- status lamps
# A machine with a lamp (gen_machine_faces.LAMPS) has a second model, the lamp alone, lit: a glowing quad laid just in
# front of each dark lamp drawn on its faces. The blockstate adds it while the block is RUNNING.


def lamp_quads(face_name, depth, sides=SIDES):
    """
    The lit lamp over the one drawn at LAMPS[face_name] on each of {@code sides}, laid on the plane {@code depth} px in
    from the block's edge (1 on a cabinet's recessed panel, 0 on a plain face) with the face's own cube UVs reversed,
    so it lands on the lamp's two pixels. A quad on the block's edge is culled against a neighbour there.
    """
    u, v = LAMPS[face_name]
    y0, y1 = 16 - v - 1, 16 - v
    d = depth - 0.05
    planes = {
        "north": ([16 - u - 2, y0, d], [16 - u, y1, d]),
        "south": ([u, y0, 16 - d], [u + 2, y1, 16 - d]),
        "west": ([d, y0, u], [d, y1, u + 2]),
        "east": ([16 - d, y0, 16 - u - 2], [16 - d, y1, 16 - u]),
    }
    return [box(frm, to, {side: face("#lamp", uv=[0, 0, 2, 1], cull=side if depth == 0 else None)},
                name="lamp", light=15, shade=False)
            for side, (frm, to) in planes.items() if side in sides]


def lamp_model(face_name, depth, sides=SIDES):
    return {"parent": "minecraft:block/block", "textures": {"lamp": nw("machine_lamp"), "particle": nw(face_name)},
            "elements": lamp_quads(face_name, depth, sides)}


def lamp_states(name):
    """A symmetric machine: its model, and its lamp lit while it runs."""
    return {"multipart": [{"apply": {"model": nw(name)}},
                          {"when": {"running": "true"}, "apply": {"model": nw(name + "_lamp")}}]}


def planter_states():
    """The Planter faces its field (north as drawn, turned for the rest); its lamp is on that face."""
    parts = []
    for facing, turn in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        rot = {"y": turn} if turn else {}
        parts.append({"when": {"facing": facing}, "apply": {"model": nw("planter"), **rot}})
        parts.append({"when": {"facing": facing, "running": "true"}, "apply": {"model": nw("planter_lamp"), **rot}})
    return {"multipart": parts}


def write_lamps():
    for name, depth in (("crusher", 1), ("stirring_tank", 0), ("distiller", 1), ("stone_maker", 1), ("water_pump", 1),
                        ("printing_press", 1), ("compressor", 1)):
        write(name + "_lamp", lamp_model(name + "_side", depth))
        write_state(name, lamp_states(name))
    write("planter_lamp", lamp_model("planter_front", 0, sides=("north",)))
    write_state("planter", planter_states())


def main():
    write("generator", front_in_gui(generator(False)))
    write("generator_lit", generator(True))
    write("diesel_generator", front_in_gui(diesel_generator()))
    write("diesel_generator_lamp", lamp_model("diesel_generator_front", 1, sides=("south",)))
    write_state("diesel_generator", diesel_generator_states())
    write("creative_generator", front_in_gui(creative_generator()))
    write_state("creative_generator", creative_generator_states())
    write("retort", front_in_gui(retort(False)))
    write("retort_lit", retort(True))
    write("mercury_tank", mercury_tank())
    for level in range(7):
        write(f"mercury_tank_gauge_{level}", sight_glass(level, ("west", "east"), nw("mercury_tank_gauge")))
        # three windows: the front, where the tap is, has none (the blockstate turns them with the tank)
        write(f"experience_tank_gauge_{level}", sight_glass(level, ("east", "south", "west"), nw("experience_tank_gauge"), light=10))
    write_state("mercury_tank", mercury_tank_states())
    write("experience_tank", experience_tank())
    write("stirring_tank", stirring_tank())
    write("distiller", distiller())
    write("distillation_column", distillation_column())
    write("condenser", condenser())
    write("crusher", crusher())
    write("sifter", sifter())
    write("water_pump", water_pump())
    write("stone_maker", stone_maker())
    write("electric_furnace", front_in_gui(electric_furnace(False)))
    write("electrolytic_cell", front_in_gui(electrolytic_cell(False)))
    write("electrolytic_cell_lit", electrolytic_cell(True))
    write_state("electrolytic_cell", facing_states("electrolytic_cell"))
    write("electric_furnace_lit", electric_furnace(True))
    write_state("electric_furnace", facing_states("electric_furnace"))
    write("printing_press", printing_press())
    write("machine_chassis", machine_chassis())
    write("spawn_egg_brewer", spawn_egg_brewer())
    write("barter_terminal", front_in_gui(barter_terminal()))
    write("barter_terminal_on", barter_terminal_on())
    write_state("barter_terminal", barter_terminal_states())
    write_line()
    write("compressor", compressor())
    write("recycler", recycler_lower())
    write("recycler_upper", recycler_upper())
    write("recycler_lamp", lamp_model("recycler_side", 1))
    write_state("recycler", recycler_states())
    write_lamps()
    print("wrote the machine models")


if __name__ == "__main__":
    main()
