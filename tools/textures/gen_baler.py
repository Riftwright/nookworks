"""The Baler as a block (the user, 2026-11-10: 「べーラーも中間素材だけど、装飾用に置けるブロックとして、機械っぽい見た目で
おしゃれに作りこんで。アイコンもブロックに差し替えて。」): a scrap baler press that does nothing, set down beside the
machines as decor - their near-black frame with pale studs, pale steel where it moves, brass where it is bolted, a
strapped bale of scrap pressed in it (the Scrap Bale's own textures, by name).

Three drafts were drawn (AI_review/baler_1110); the block wears DRAFT. To switch, set DRAFT (or pass --draft a|b|c)
and run this again: it writes that draft's model, faces and blockstate over the block's.

- a: a four-post screw press - a plinth, a post at each corner shod in brass, the bale on the bed under a ram plate,
  the screw up through a crosshead to a brass handwheel lying on top.
- b: a cabinet baler, as a scrapyard's downstroke baler stands: the machines' cabinet, a barred loading door in front
  with the bale behind it, the hydraulic cylinder standing on the lid in a brass flange.
- c: an H-frame hydraulic press: two heavy side cheeks bolted to the plinth with brass brackets, a crosshead over
  them, the cylinder standing on it, its chrome rod down to a hazard-striped ram on the bale - open front and back,
  so the bale shows through.

Front to the south, as the machines are drawn (gen_machine_models' FRONT_SOUTH_GUI turns the item to show it); the
blockstate turns it to its facing. Drawn flat, shaded with texkit.shade as gen_machine_faces shades the machines.
Original work, deterministic. Run from the repo root:  python tools/textures/gen_baler.py [--draft c] [--all OUT]
(--all writes every draft under baler_a.., baler_b.., baler_c.. into the resources folder OUT, for a look in the
client beside the block, with an item definition each for an item display: nothing of it belongs in the mod.)
"""
import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/textures"))
sys.path.insert(0, str(ROOT / "tools"))
import texkit  # noqa: E402
from gen_machine_faces import (  # noqa: E402
    BAR, BAR_IN, BAR_SH, BOLT, BRASS, BRASS_DK, BRASS_HI, BRASS_LO, DARK, DARK_DK, DARK_HI, DARK_LO, FRAME_KEEP,
    HAZARD, PALE, PALE_DK, PALE_HI, PALE_LO, STUD, STUD_DK, STUD_HI, STUD_LO, STUD_SH, blank, cylinder_shade,
    frame_band, hline, material, put, rect, vline)
import gen_machine_models as gm  # noqa: E402

DRAFT = "c"
RES = ROOT / "src/main/resources"
SIDES = ("north", "south", "west", "east")
BALE = "nookworks:block/scrap_bale_side"
BALE_TOP = "nookworks:block/scrap_bale_top"
CHROME = ((236, 238, 242), (206, 210, 216), (160, 164, 172), (112, 116, 124))
BRASS4 = (BRASS_HI, BRASS, BRASS_LO, BRASS_DK)


def column(img, x0, x1, y0, y1, ramp):
    """Rows y0..y1 of a round thing spanning columns x0..x1, lit a third of the way in."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, cylinder_shade(x, x0, x1, *ramp))


def hazard_row(img, x0, x1, y, phase=0):
    for x in range(x0, x1 + 1):
        put(img, x, y, HAZARD[0] if ((x + phase) // 2) % 2 == 0 else HAZARD[1])


def bracket(img, x0, x1, y0, y1, bolts=()):
    """A brass bracket plate: lit along its top and left, shaded along its bottom and right, its bolts steel."""
    rect(img, x0, y0, x1, y1, BRASS)
    hline(img, x0, x1, y0, BRASS_HI)
    vline(img, x0, y0, y1, BRASS_HI)
    hline(img, x0 + 1, x1, y1, BRASS_DK)
    vline(img, x1, y0 + 1, y1, BRASS_LO)
    for x, y in bolts:
        put(img, x, y, BOLT)


def plate(img, x0, x1, y0, y1, panel=DARK):
    """A plate of the frame's steel: a dark rim round a panel."""
    rect(img, x0, y0, x1, y1, BAR_IN)
    hline(img, x0, x1, y0, BAR)
    hline(img, x0, x1, y1, BAR_SH)
    rect(img, x0 + 1, y0 + 1, x1 - 1, y1 - 1, panel)


def stud_corners(img, x0, x1, y0, y1):
    """The frame's pale studs at a plate's four corners."""
    put(img, x0, y0, STUD)
    put(img, x1, y0, STUD_LO)
    put(img, x0, y1, STUD_DK)
    put(img, x1, y1, STUD_SH)


# ---------------------------------------------------------------- c: H-frame hydraulic press
# plinth y 0..2; cheeks x 0..2 and 14..16, y 2..11, z 2..14; crosshead y 11..13 (x 0..16, z 2..14); a brass collar
# round 10 y 13..14 and the cylinder round 8 y 14..16 on it; the chrome rod round 4 y 8..11; the ram x 2..14, y 7..8,
# z 2..14; the bale x 3..13, y 2..7, z 3..13. Brass L brackets bolt each cheek's foot to the plinth, front and back;
# brass gussets in the window's upper corners; a pressure gauge on the crosshead's front right. Open front and back.
# Rows of a face: row = 15 - y.
DIAL = ((236, 232, 220), (196, 40, 32))


def c_front(gauge=True):
    img = blank()
    frame_band(img, 14, 15)
    for x0 in (0, 14):  # the cheeks' ends, y 2..11 (rows 5..13)
        plate(img, x0, x0 + 1, 5, 13)
    # the crosshead, y 11..13 (rows 3..4), studs at its ends
    plate(img, 0, 15, 3, 4)
    stud_corners(img, 0, 15, 3, 4)
    # brackets: the L's upright, y 2..5 (rows 10..13), on each cheek's end
    for x0 in (0, 14):
        bracket(img, x0, x0 + 1, 10, 13, bolts=((x0 + (1 if x0 == 0 else 0), 11),))
    # gussets in the window's upper corners, y 9..11 (rows 5..6)
    bracket(img, 2, 3, 5, 6, bolts=((2, 5),))
    bracket(img, 12, 13, 5, 6, bolts=((13, 5),))
    # the collar (round 10, y 13..14, row 2) and the cylinder (round 8, y 14..16, rows 0..1)
    column(img, 3, 12, 2, 2, BRASS4)
    column(img, 4, 11, 0, 1, (PALE_HI, PALE, PALE_LO, PALE_DK))
    hline(img, 4, 11, 0, PALE_HI)
    # the rod, round 4, y 8..11 (rows 5..7), chrome
    column(img, 6, 9, 5, 7, CHROME)
    # the ram's edge, y 7..8 (row 8): hazard
    hazard_row(img, 2, 13, 8)
    # the gauge, x 13..16, y 12..15 (rows 1..3): a brass bezel round a white dial, the needle red
    if not gauge:
        return img
    rect(img, 13, 1, 15, 3, BRASS)
    put(img, 13, 1, BRASS_HI)
    put(img, 15, 3, BRASS_DK)
    put(img, 14, 2, DIAL[0])
    put(img, 15, 2, DIAL[0])
    put(img, 14, 1, DIAL[1])
    return img


def c_side():
    img = blank()
    frame_band(img, 14, 15)
    # the cheek, z 2..14 (columns 2..13), y 2..11 (rows 5..13): a heavy plate, bolted, a brass maker's plate on it
    plate(img, 2, 13, 5, 13)
    stud_corners(img, 2, 13, 5, 13)
    for x, y in ((4, 7), (11, 7), (4, 11), (11, 11)):
        put(img, x, y, BOLT)
    rect(img, 6, 8, 9, 10, BRASS_LO)
    hline(img, 6, 9, 8, BRASS_HI)
    vline(img, 6, 8, 10, BRASS)
    put(img, 7, 9, BRASS_DK)
    put(img, 8, 9, BRASS_DK)
    # the crosshead, y 11..13 (rows 3..4)
    plate(img, 2, 13, 3, 4)
    stud_corners(img, 2, 13, 3, 4)
    # the L brackets side on: upright z 1..2 / 14..15, y 3..5 (rows 11..12); foot z 0..2 / 14..16, y 2..3 (row 13)
    for x in (1, 14):
        vline(img, x, 10, 13, BRASS)
        put(img, x, 10, BRASS_HI)
    for x in (0, 1, 14, 15):
        put(img, x, 13, BRASS_LO)
    # the collar and the cylinder, as from the front
    column(img, 3, 12, 2, 2, BRASS4)
    column(img, 4, 11, 0, 1, (PALE_HI, PALE, PALE_LO, PALE_DK))
    hline(img, 4, 11, 0, PALE_HI)
    # the gauge's case side on (z 14..15: column 1 on the east, 14 on the west), rows 1..3
    for x in (1, 14):
        vline(img, x, 1, 3, BRASS_LO)
        put(img, x, 1, BRASS)
    return img


def c_top():
    img = blank()
    rect(img, 0, 0, 15, 15, BAR_IN)
    # the plinth's ends (z 0..2, 14..16): its rim, studs at the corners
    hline(img, 0, 15, 0, BAR)
    hline(img, 0, 15, 15, BAR_SH)
    stud_corners(img, 0, 15, 0, 15)
    # the crosshead, z 2..14
    rect(img, 0, 2, 15, 13, DARK)
    hline(img, 0, 15, 2, DARK_HI)
    hline(img, 0, 15, 13, DARK_LO)
    for x, y in ((1, 3), (14, 3), (1, 12), (14, 12)):  # the cheeks' bolt heads through it
        put(img, x, y, BOLT)
    for x0 in (0, 14):  # the brackets' feet on the plinth
        rect(img, x0, 0, x0 + 1, 1, BRASS)
        rect(img, x0, 14, x0 + 1, 15, BRASS_LO)
        put(img, x0, 0, BRASS_HI)
    # the collar, and the cylinder's cap in it with its bolts and the hose port
    gm_round(img, 10, lambda x, y, d: BRASS_HI if d < -4 else BRASS if d < 4 else BRASS_LO,
             ring=lambda x, y, d: BRASS_DK if d > 0 else BRASS)
    gm_round(img, 8, lambda x, y, d: PALE_HI if d < -3 else PALE if d < 3 else PALE_LO,
             ring=lambda x, y, d: PALE_DK if d > 0 else PALE)
    for x, y in ((5, 7), (10, 8), (7, 5), (8, 10)):
        put(img, x, y, BOLT)
    rect(img, 7, 7, 8, 8, BRASS)  # the hose union
    put(img, 7, 7, BRASS_HI)
    put(img, 8, 8, BRASS_DK)
    # the gauge's top, x 13..16, z 14..15
    hline(img, 13, 15, 14, BRASS)
    return img


def c_parts():
    """The ram's top (pale, hazard along its front and back edges), seen through the window under the crosshead."""
    img = blank()
    rect(img, 0, 0, 15, 15, PALE)
    hline(img, 0, 15, 3, PALE_HI)
    hline(img, 0, 15, 12, PALE_DK)
    for x in range(2, 14):
        put(img, x, 2, HAZARD[0] if (x // 2) % 2 == 0 else HAZARD[1])
        put(img, x, 13, HAZARD[0] if (x // 2) % 2 == 0 else HAZARD[1])
    rect(img, 6, 6, 9, 9, PALE_LO)  # where the rod meets it
    return img


def gm_round(img, width, colours, ring=None):
    from gen_machine_faces import round_plan
    round_plan(img, width, colours, ring)


def c_elements():
    e = [part([0, 0, 0], [16, 2, 16], cull=("down",) + SIDES, name="plinth")]
    for x0 in (0, 14):
        x1 = x0 + 2
        out_side = "west" if x0 == 0 else "east"
        e.append(part([x0, 2, 2], [x1, 11, 14], cull=(out_side,), skip=("up", "down"), name="cheek"))
        for z_up, z_foot in (((1, 2), (0, 2)), ((14, 15), (14, 16))):
            end = "north" if z_up[0] == 1 else "south"
            e.append(part([x0, 3, z_up[0]], [x1, 5, z_up[1]], cull=(out_side,), skip=("down",), name="bracket"))
            e.append(part([x0, 2, z_foot[0]], [x1, 3, z_foot[1]], cull=(out_side, end), skip=("down",), name="bracket foot"))
    e.append(part([0, 11, 2], [16, 13, 14], cull=("west", "east"), name="crosshead"))
    for z0 in (2, 13):  # gussets in the window's upper corners
        for x0 in (2, 12):
            e.append(part([x0, 9, z0], [x0 + 2, 11, z0 + 1], skip=("up",), name="gusset"))
    e += gm_column(13, 14, 10, "collar")
    e += gm_column(14, 16, 8, "cylinder", cull=("up",))
    # the rod: every side from the front's drawing of it (the cheek's is where its own sides would look)
    e += gm_column(8, 11, 4, "rod", skip=("up", "down"), tex=dict(ALL_FRONT))
    ram = part([2, 7, 2], [14, 8, 14], skip=("west", "east"), name="ram")  # its underside shows round the bale
    ram["faces"]["up"] = gm.face("#parts", uv=[2, 2, 14, 14])
    e.append(ram)
    e.append(bale([3, 2, 3], [13, 7, 13], skip=("up", "down")))
    e.append(part([13, 12, 14], [16, 15, 15], cull=("east",), name="gauge"))
    return e


ALL_FRONT = {"north": "front", "south": "front", "west": "front", "east": "front", "up": "top", "down": "bottom"}


def c_back():
    """The back: the front without its gauge - but the gauge's own back, seen over the crosshead (x 13..16 from behind:
    columns 0..2, rows 1..3), a brass case."""
    img = c_front(gauge=False)
    rect(img, 0, 1, 2, 3, BRASS_LO)
    hline(img, 0, 2, 1, BRASS)
    put(img, 2, 3, BRASS_DK)
    return img


# ---------------------------------------------------------------- a: four-post screw press
# plinth y 0..3; posts 2x2 at the corners y 3..13, brass shoes 3x3 y 3..5; bale x 3..13, y 3..9, z 3..13; ram
# x 2..14, y 9..11, z 2..14; screw round 4 y 11..13; crosshead y 13..15 over the whole block; the handwheel lying on
# top, y 15..16: a ring 12 across on four spokes and a hub.

def a_front():
    img = blank()
    rect(img, 0, 13, 15, 15, BAR_IN)  # plinth, y 0..3 (rows 13..15)
    hline(img, 0, 15, 13, BAR)
    hline(img, 0, 15, 15, BAR_SH)
    hazard_row(img, 2, 13, 14)
    stud_corners(img, 0, 15, 13, 15)
    for x0 in (0, 14):  # posts, y 3..13 (rows 3..12)
        rect(img, x0, 3, x0 + 1, 12, BAR_IN)
        vline(img, x0, 3, 12, BAR if x0 == 0 else BAR_IN)
        vline(img, x0 + 1, 3, 12, BAR_IN if x0 == 0 else BAR_SH)
    for x0 in (0, 13):  # shoes, y 3..5 (rows 11..12)
        bracket(img, x0, x0 + 2, 11, 12, bolts=((x0 + 1, 11),))
    # the ram, y 9..11 (rows 5..6), pale with the hazard along its lower edge
    rect(img, 2, 5, 13, 5, PALE)
    hline(img, 2, 13, 5, PALE_HI)
    hazard_row(img, 2, 13, 6, 1)
    # the screw, y 11..13 (rows 3..4): square-threaded, dark steel
    for y in (3, 4):
        for x in range(6, 10):
            put(img, x, y, DARK_HI if (x + y) % 2 == 0 else DARK_LO)
    # the crosshead, y 13..15 (rows 1..2), and the wheel's rim, y 15..16 (row 0)
    rect(img, 0, 1, 15, 2, BAR_IN)
    hline(img, 0, 15, 1, BAR)
    hline(img, 0, 15, 2, BAR_SH)
    stud_corners(img, 0, 15, 1, 2)
    rect(img, 6, 1, 9, 2, BRASS_LO)  # the nut the screw turns in
    hline(img, 6, 9, 1, BRASS)
    column(img, 2, 13, 0, 0, BRASS4)
    return img


def a_side():
    return a_front()


def a_top():
    img = blank()
    rect(img, 0, 0, 15, 15, BAR_IN)
    rect(img, 1, 1, 14, 14, DARK)
    hline(img, 0, 15, 0, BAR)
    vline(img, 0, 0, 15, BAR)
    hline(img, 0, 15, 15, BAR_SH)
    vline(img, 15, 0, 15, BAR_SH)
    stud_corners(img, 0, 15, 0, 15)
    put(img, 1, 1, STUD_HI)
    rect(img, 6, 6, 9, 9, BRASS_LO)  # the hub over the nut
    put(img, 6, 6, BRASS_HI)
    rect(img, 7, 7, 8, 8, DARK_LO)  # the screw's end in it
    return img


def A_WHEEL(x, y):
    ring = (2 <= x <= 13 and y in (2, 13) and 4 <= x <= 11) or (x in (2, 13) and 4 <= y <= 11) \
        or (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12))
    spoke = (x in (7, 8) and 3 <= y <= 12) or (y in (7, 8) and 3 <= x <= 12)
    return ring or spoke


def a_parts():
    img = c_parts()
    return img


def a_elements():
    e = [part([0, 0, 0], [16, 3, 16], cull=("down",) + SIDES, name="plinth")]
    for cx in (0, 14):
        for cz in (0, 14):
            e.append(part([cx, 3, cz], [cx + 2, 13, cz + 2], cull=tuple(s for s in (("west" if cx == 0 else "east"),
                                                                                   ("north" if cz == 0 else "south"))),
                          skip=("up", "down"), name="post"))
            sx, sz = (0 if cx == 0 else 13), (0 if cz == 0 else 13)
            e.append(part([sx, 3, sz], [sx + 3, 5, sz + 3], skip=("down",), name="shoe"))
    e.append(bale([3, 3, 3], [13, 9, 13], skip=("up", "down")))
    ram = part([2, 9, 2], [14, 11, 14], name="ram")
    ram["faces"]["up"] = gm.face("#parts", uv=[2, 2, 14, 14])
    e.append(ram)
    e += gm_column(11, 13, 4, "screw", skip=("up", "down"))
    e.append(part([0, 13, 0], [16, 15, 16], cull=SIDES, name="crosshead"))
    # the handwheel, a pixel thick
    for frm, to in (([4, 15, 2], [12, 16, 3]), ([4, 15, 13], [12, 16, 14]), ([2, 15, 4], [3, 16, 12]),
                    ([13, 15, 4], [14, 16, 12]), ([3, 15, 3], [4, 16, 4]), ([12, 15, 3], [13, 16, 4]),
                    ([3, 15, 12], [4, 16, 13]), ([12, 15, 12], [13, 16, 13]), ([7, 15, 3], [9, 16, 13]),
                    ([3, 15, 7], [7, 16, 9]), ([9, 15, 7], [13, 16, 9])):
        wheel = part(frm, to, cull=("up",), skip=("down",), name="wheel", tex=WHEEL_TEX)
        for f in wheel["faces"].values():
            f["uv"] = [2, 0, 13, 1]  # the brass of the wheel's rim, the front's top row
        e.append(wheel)
    return e


WHEEL_TEX = {"north": "rim", "south": "rim", "west": "rim", "east": "rim", "up": "top", "down": "top"}


# ---------------------------------------------------------------- b: cabinet baler
# The machines' cabinet y 0..12 (gen_machine_models.cabinet), the loading door in front with bars and the bale behind;
# a brass flange round 10 y 12..13, the cylinder round 8 y 13..16.

def b_front():
    img = blank()
    column(img, 4, 11, 0, 2, (PALE_HI, PALE, PALE_LO, PALE_DK))
    hline(img, 4, 11, 1, BRASS)  # a band round the cylinder
    put(img, 5, 1, BRASS_HI)
    column(img, 3, 12, 3, 3, BRASS4)
    from gen_machine_faces import cabinet_elev
    cabinet_elev(img, 4, 15)
    # the loading door: a bale behind three bars, hazard over it
    hazard_row(img, 2, 13, 6)
    rect(img, 3, 7, 12, 12, BAR_SH)
    bale_img = __import__("PIL.Image", fromlist=["Image"]).open(RES / "assets/nookworks/textures/block/scrap_bale_side.png").convert("RGBA")
    for y in range(8, 12):
        for x in range(3, 13):
            put(img, x, y, bale_img.getpixel((x, y))[:3])
    for x in (4, 8, 11):
        vline(img, x, 7, 12, PALE_LO)
        put(img, x, 7, PALE_HI)
    hline(img, 3, 12, 13, DARK_HI)
    return img


def b_side():
    img = blank()
    column(img, 4, 11, 0, 2, (PALE_HI, PALE, PALE_LO, PALE_DK))
    hline(img, 4, 11, 1, BRASS)
    column(img, 3, 12, 3, 3, BRASS4)
    from gen_machine_faces import cabinet_elev
    cabinet_elev(img, 4, 15)
    # louvres and a brass plate
    for y in (7, 9, 11):
        hline(img, 3, 9, y, DARK_DK)
        hline(img, 3, 9, y + 1, DARK_HI)
    bracket(img, 10, 12, 7, 9)
    return img


def b_top():
    from gen_machine_faces import plan_cabinet, brass_ring_plan
    img = blank()
    plan_cabinet(img)
    brass_ring_plan(img, width=10, bore=8)
    gm_round(img, 8, lambda x, y, d: PALE_HI if d < -3 else PALE if d < 3 else PALE_LO,
             ring=lambda x, y, d: PALE_DK if d > 0 else PALE)
    rect(img, 7, 7, 8, 8, BRASS)
    return img


def b_elements():
    els = gm.cabinet(top=12, recess_top=False)
    for el in els:  # the cabinet's faces by side, as the machines name them
        for f in el["faces"].values():
            f["texture"] = {"#north": "#back", "#south": "#front", "#west": "#side", "#east": "#side",
                            "#up": "#top", "#down": "#bottom"}.get(f["texture"], f["texture"])
    els += gm_column(12, 13, 10, "flange")
    els += gm_column(13, 16, 8, "cylinder", cull=("up",))
    return els


def b_parts():
    return c_parts()


# ---------------------------------------------------------------- building blocks

def part(frm, to, cull=(), skip=(), name=None, tex=None):
    """A box dressed from the draft's elevations by where it stands: south the front, north the back, the sides, the top."""
    tex = tex or {"north": "back", "south": "front", "west": "side", "east": "side", "up": "top", "down": "bottom"}
    faces = {}
    for f in SIDES + ("up", "down"):
        if f in skip:
            continue
        faces[f] = gm.face("#" + tex[f], cull=f if f in cull and edge(frm, to, f) else None)
    return gm.box(frm, to, faces, name=name)


def edge(frm, to, f):
    """Whether face {@code f} lies on the block's edge (only then may it be culled)."""
    return {"down": frm[1] == 0, "up": to[1] == 16, "north": frm[2] == 0, "south": to[2] == 16,
            "west": frm[0] == 0, "east": to[0] == 16}[f]


def bale(frm, to, skip=()):
    faces = {f: gm.face("#bale_top" if f in ("up", "down") else "#bale") for f in SIDES + ("up", "down") if f not in skip}
    return gm.box(frm, to, faces, name="bale")


def gm_column(y0, y1, width, name, skip=("down",), cull=(), tex=None):
    return [part([8 - hx, y0, 8 - hz], [8 + hx, y1, 8 + hz], skip=skip, cull=cull, name=name, tex=tex)
            for hx, hz in gm.ROUNDS[width]]


DRAFTS = {
    "a": (a_front, a_front, a_side, a_top, a_parts, a_elements),
    "b": (b_front, b_side, b_side, b_top, b_parts, b_elements),
    "c": (c_front, c_back, c_side, c_top, c_parts, c_elements),
}


def save(img, path, seed):
    img = texkit.shade(img, seed, keep=FRAME_KEEP + (BOLT,) + HAZARD + DIAL, material=material(), **texkit.SMOOTH)
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def write(draft, stem, res):
    """Draft {@code draft}'s faces and model, named {@code stem} (block/<stem>.json, block/<stem>_front.png...)."""
    front, back, side, top, parts, elements = DRAFTS[draft]
    tex_dir = res / "assets/nookworks/textures/block"
    seed = 400 + "abc".index(draft) * 10
    save(front(), tex_dir / f"{stem}_front.png", seed)
    save(side(), tex_dir / f"{stem}_side.png", seed + 1)
    if back is not front and back is not side:
        save(back(), tex_dir / f"{stem}_back.png", seed + 4)
    save(top(), tex_dir / f"{stem}_top.png", seed + 2)
    save(parts(), tex_dir / f"{stem}_parts.png", seed + 3)
    nw = gm.nw
    textures = {"front": nw(f"{stem}_front"), "back": nw(f"{stem}_back" if back not in (front, side) else f"{stem}_front" if back is front else f"{stem}_side"),
                "side": nw(f"{stem}_side"), "top": nw(f"{stem}_top"), "bottom": nw("machine_bottom"),
                "parts": nw(f"{stem}_parts"), "bale": BALE, "bale_top": BALE_TOP, "particle": nw(f"{stem}_side")}
    if draft == "a":
        textures["rim"] = nw(f"{stem}_front")
    model = {"parent": "minecraft:block/block", "textures": textures, "elements": elements(),
             "display": dict(gm.FRONT_SOUTH_GUI)}
    write_json(res / f"assets/nookworks/models/block/{stem}.json", model)


def states(stem):
    variants = {}
    for facing, turn in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
        v = {"model": gm.nw(stem)}
        if turn:
            v["y"] = turn
        variants[f"facing={facing}"] = v
    return {"variants": variants}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--draft", default=DRAFT, choices=sorted(DRAFTS))
    parser.add_argument("--all", metavar="RESOURCES", help="write every draft as baler_<x> into this resources folder")
    args = parser.parse_args()
    if args.all:
        out = Path(args.all)
        for draft in DRAFTS:
            write(draft, f"baler_{draft}", out)
            write_json(out / f"assets/nookworks/items/baler_draft_{draft}.json",
                       {"model": {"type": "minecraft:model", "model": gm.nw(f"baler_{draft}")}})
        print("wrote the drafts")
        return
    write(args.draft, "baler", RES)
    write_json(RES / "assets/nookworks/blockstates/baler.json", states("baler"))
    write_json(RES / "assets/nookworks/items/baler.json", {"model": {"type": "minecraft:model", "model": gm.nw("baler")}})
    print(f"wrote the Baler (draft {args.draft})")


if __name__ == "__main__":
    main()
