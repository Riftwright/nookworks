"""Faces of the machines built by tools/gen_machine_models.py, in the family the hand-finished machines set.

The hand-finished Generator, Sealed Retort and Mercury Tank (HAND_FINISHED.md) are the pattern: a near-black steel
frame two pixels wide with pale studs at its corners, and inside it a panel - dark steel for the machines that work
with heat and force, pale steel for the ones that hold and work liquids - with brass and copper where there is a
fitting, a band or a pipe. The rest of the machines are drawn here to that pattern, so a row of them reads as one
maker's line.

Each face is drawn as an elevation: the whole machine seen square from that side, as a draughtsman would draw it.
The models give their parts no UVs of their own, so every part - a vessel set back from the frame, a motor on the lid,
a flange - takes its slice of the elevation from where it stands; one drawing dresses every part of a side.

Drawn flat, then finished with texkit, which leaves the frame colours exact. Original work, deterministic.
Run from the repo root:  python tools/textures/gen_machine_faces.py
"""
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"

# the frame, as the hand-finished machines draw it
STUD_HI, STUD, STUD_LO, STUD_DK, STUD_SH = (219, 222, 227), (174, 177, 182), (134, 137, 142), (89, 92, 96), (64, 66, 69)
BAR, BAR_IN, BAR_SH = (36, 38, 42), (22, 24, 27), (12, 13, 15)
FRAME_KEEP = (STUD_HI, STUD, STUD_LO, STUD_DK, STUD_SH, BAR, BAR_IN, BAR_SH)

PALE_HI, PALE, PALE_LO, PALE_DK = (224, 227, 231), (199, 202, 207), (164, 168, 175), (124, 128, 136)
DARK_HI, DARK, DARK_LO, DARK_DK = (72, 76, 82), (50, 53, 58), (38, 40, 44), (26, 28, 31)
BRASS_HI, BRASS, BRASS_LO, BRASS_DK = (238, 204, 116), (200, 156, 68), (146, 106, 42), (96, 66, 26)
COPPER_HI, COPPER, COPPER_LO, COPPER_DK = (236, 160, 112), (196, 112, 70), (146, 76, 46), (98, 48, 30)
GLASS_DK, GLASS, GLASS_HI = (24, 32, 38), (44, 60, 70), (150, 184, 198)

# The status lamp of the machines that carry one: two pixels of dark green in a bezel, as on the Electric Furnace,
# drawn on the face here and lit while the machine runs by a glowing overlay (tools/gen_machine_models.py, which
# reads these places). Each is the lamp's left pixel on that face, set where the panel is plain.
LAMP_OFF, LAMP_ON = (38, 60, 40), (120, 236, 120)
LAMPS = {
    "crusher_side": (11, 8),
    "stirring_tank_side": (11, 14),  # on the plinth: the vessel above it is round
    "distiller_side": (12, 10),      # on the burner cabinet, over the grate
    "stone_maker_side": (11, 2),
    "water_pump_side": (11, 7),
    "printing_press_side": (11, 6),
    "planter_front": (9, 2),         # in the iron frame of the seed window (tools/textures/gen_planter.py)
    "barter_terminal_front": (11, 12),  # beside its keys, under the screen
    "diesel_generator_front": (11, 13),  # under the radiator, beside the maker's plate
    "compressor_side": (11, 7),      # beside the press bay
    "recycler_side": (11, 4),        # over the sorting window
}


def blank():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(c) + (255,))


def rect(img, x0, y0, x1, y1, c):
    """Fill x0..x1, y0..y1, both inclusive."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, c)


def hline(img, x0, x1, y, c):
    rect(img, x0, y, x1, y, c)


def vline(img, x, y0, y1, c):
    rect(img, x, y0, x, y1, c)


def frame_band(img, y0, y1, x0=0, x1=15):
    """
    A strip of the frame from row y0 to y1 (a plinth, a rim), studs at both ends: the hand-finished frame's corner,
    cut to height. Two rows at least.
    """
    rect(img, x0, y0, x1, y1, BAR_IN)
    hline(img, x0, x1, y0, BAR)
    hline(img, x0, x1, y1, BAR_SH)
    for sx, hi_col in ((x0, True), (x1 - 1, False)):
        put(img, sx, y0, STUD if hi_col else STUD_LO)
        put(img, sx + 1, y0, STUD_LO if hi_col else STUD)
        put(img, sx, y0 + 1, STUD_LO if hi_col else STUD_HI)
        put(img, sx + 1, y0 + 1, STUD_HI if hi_col else STUD_DK)
        for y in range(y0 + 2, y1 + 1):
            put(img, sx, y, STUD_DK if hi_col else BAR_IN)
            put(img, sx + 1, y, BAR_IN if hi_col else BAR_SH)
    put(img, x0, y1, STUD_SH)
    put(img, x1, y1, STUD_SH)


# the pixel circles the models build round parts from (gen_machine_models.ROUNDS): width -> boxes' half sizes
ROUNDS = {
    4: ((2, 2),),
    6: ((3, 2), (2, 3)),
    8: ((4, 3), (3, 4)),
    10: ((5, 3), (4, 4), (3, 5)),
    12: ((6, 3), (5, 5), (3, 6)),
    14: ((7, 3), (6, 5), (5, 6), (3, 7)),
    16: ((8, 3), (7, 5), (5, 7), (3, 8)),
}


def inside(width, x, y):
    """Whether pixel (x, y) of a top face lies within the model's round part of that width."""
    return any(8 - hx <= x < 8 + hx and 8 - hz <= y < 8 + hz for hx, hz in ROUNDS[width])


def rim_of(width, x, y):
    """Whether pixel (x, y) is on the edge of that round: inside it, with a neighbour outside."""
    return inside(width, x, y) and not all(inside(width, x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))


def cylinder_shade(x, x0, x1, lit, mid, low, dark):
    """
    The shade of column x across a round thing spanning x0..x1, lit from the upper left as vanilla lights its
    blocks: a bright stripe a third of the way in, darkening to the far edge.
    """
    t = (x - x0) / max(1, x1 - x0)
    if t < 0.08 or t > 0.93:
        return dark
    if t < 0.18:
        return low
    if t < 0.42:
        return lit
    if t < 0.74:
        return mid
    return low


# ---------------------------------------------------------------- Stirring Tank
# Plinth y 0..3 (rows 13..15), vessel y 3..12 (rows 4..12), lid y 12..13 (row 3), motor y 13..16 (rows 0..2).


def stirring_tank_side():
    img = blank()
    # motor: dark housing with cooling ribs, x 5..10
    for x in range(5, 11):
        put(img, x, 0, DARK_HI if x < 9 else DARK)
        put(img, x, 1, DARK_LO if x % 2 else DARK_HI)
        put(img, x, 2, DARK_DK)
    put(img, 5, 1, DARK)
    put(img, 10, 1, DARK_DK)
    put(img, 10, 0, DARK_LO)
    # lid: a pale rim, x 2..13
    for x in range(2, 14):
        put(img, x, 3, cylinder_shade(x, 2, 13, PALE_HI, PALE_HI, PALE, PALE_LO))
    # vessel, x 1..14
    for y in range(4, 13):
        for x in range(1, 15):
            put(img, x, y, cylinder_shade(x, 1, 14, PALE_HI, PALE, PALE_LO, PALE_DK))
    # brass bands with rivets
    for y in (5, 11):
        for x in range(1, 15):
            put(img, x, y, cylinder_shade(x, 1, 14, BRASS_HI, BRASS, BRASS_LO, BRASS_DK))
        for x in (3, 6, 9, 12):
            put(img, x, y, BRASS_HI if x < 9 else BRASS)
        for x in range(1, 15):
            put(img, x, y + 1, cylinder_shade(x, 1, 14, PALE, PALE_LO, PALE_LO, PALE_DK))  # the band's shadow
    # a sight glass between the bands: dark, the liquid behind, a glint at its top
    rect(img, 7, 7, 8, 9, GLASS)
    put(img, 7, 7, GLASS_HI)
    put(img, 8, 9, GLASS_DK)
    vline(img, 6, 7, 9, PALE_DK)
    vline(img, 9, 7, 9, PALE_HI)
    # the vessel's foot, shadowed where it meets the plinth
    for x in range(1, 15):
        put(img, x, 12, cylinder_shade(x, 1, 14, PALE, PALE_LO, PALE_DK, PALE_DK))
    # plinth: the frame, cut to three rows, with a brass maker's plate
    frame_band(img, 13, 15)
    rect(img, 6, 14, 9, 14, BRASS)
    put(img, 6, 14, BRASS_HI)
    put(img, 9, 14, BRASS_LO)
    return img


def stirring_tank_top():
    img = blank()
    # the plinth's corners, outside the round of the vessel
    rect(img, 0, 0, 15, 15, BAR_IN)
    for cx, cy in ((0, 0), (14, 0), (0, 14), (14, 14)):
        rect(img, cx, cy, cx + 1, cy + 1, STUD)
        put(img, cx, cy, STUD_HI)
        put(img, cx + 1, cy + 1, STUD_LO)
    # the vessel's round (14 across), pale, lit from the upper left; the lid (12 across) on it, its seam a ring
    for y in range(16):
        for x in range(16):
            if not inside(14, x, y):
                continue
            d = (x - 7.5) + (y - 7.5)
            if rim_of(14, x, y):
                shade = PALE if d < 0 else PALE_DK
            elif rim_of(12, x, y):
                shade = PALE_HI if d < 0 else PALE_LO
            else:
                shade = PALE_HI if d < -5 else PALE if d < 5 else PALE_LO
            put(img, x, y, shade)
    # the motor, 5..10, from above: dark with a brass cap over the shaft
    rect(img, 5, 5, 10, 10, DARK)
    hline(img, 5, 10, 5, DARK_HI)
    vline(img, 5, 5, 10, DARK_HI)
    hline(img, 5, 10, 10, DARK_DK)
    vline(img, 10, 5, 10, DARK_DK)
    rect(img, 7, 7, 8, 8, BRASS)
    put(img, 7, 7, BRASS_HI)
    put(img, 8, 8, BRASS_LO)
    return img


def stirring_tank_bottom():
    img = blank()
    rect(img, 0, 0, 15, 15, BAR_IN)
    rect(img, 2, 2, 13, 13, DARK_LO)
    for cx, cy in ((0, 0), (14, 0), (0, 14), (14, 14)):
        rect(img, cx, cy, cx + 1, cy + 1, STUD_LO)
    return img

# ---------------------------------------------------------------- shared pieces


def cabinet_elev(img, y0, y1, panel=DARK):
    """
    A cabinet seen from the side, rows y0..y1: the hand-finished frame - studs at the corners, a dark rim top and
    bottom, posts down both sides - round a panel (rows y0+2..y1-2, columns 2..13) of {@code panel}.
    """
    rect(img, 2, y0 + 2, 13, y1 - 2, panel)
    for y in range(y0, y1 + 1):
        put(img, 0, y, BAR)
        put(img, 1, y, BAR_IN)
        put(img, 14, y, BAR_IN)
        put(img, 15, y, BAR_SH)
    for x in range(2, 14):
        put(img, x, y0, BAR)
        put(img, x, y0 + 1, BAR_IN)
        put(img, x, y1 - 1, BAR_IN)
        put(img, x, y1, BAR_SH)
    for yy, row in ((y0, (STUD, STUD_LO, STUD_LO, STUD)), (y0 + 1, (STUD_LO, STUD_HI, STUD_HI, STUD_DK)),
                    (y1 - 1, (STUD_LO, STUD_HI, STUD_HI, STUD_DK)), (y1, (STUD_DK, STUD_DK, STUD_DK, STUD_SH))):
        for x, c in zip((0, 1, 14, 15), row):
            put(img, x, yy, c)


def plan_cabinet(img, panel=DARK):
    """A cabinet's lid from above: the frame round its edge, studs at the corners, {@code panel} inside."""
    rect(img, 0, 0, 15, 15, BAR)
    rect(img, 1, 1, 14, 14, BAR_IN)
    rect(img, 2, 2, 13, 13, panel)
    for cx, cy in ((0, 0), (14, 0), (0, 14), (14, 14)):
        put(img, cx, cy, STUD)
        put(img, cx + 1, cy, STUD_LO)
        put(img, cx, cy + 1, STUD_LO)
        put(img, cx + 1, cy + 1, STUD_HI if (cx, cy) != (14, 14) else STUD_DK)


def round_plan(img, width, colours, ring=None):
    """A round part from above: filled with colours(x, y, d), d being how far towards the lower right it lies."""
    for y in range(16):
        for x in range(16):
            if inside(width, x, y):
                d = (x - 7.5) + (y - 7.5)
                c = ring(x, y, d) if ring and rim_of(width, x, y) else colours(x, y, d)
                if c:
                    put(img, x, y, c)


def lit(d, hi, mid, lo, spread=4):
    """A shade by position on a round part from above, lit from the upper left."""
    return hi if d < -spread else mid if d < spread else lo


def band_row(img, y, x0, x1, ramp, rivets=()):
    """One row across a round part: its shade by column, brighter where the light strikes; rivets as highlights."""
    hi, mid, lo, dk = ramp
    for x in range(x0, x1 + 1):
        put(img, x, y, cylinder_shade(x, x0, x1, hi, mid, lo, dk))
    for x in rivets:
        put(img, x, y, hi if x < (x0 + x1) / 2 else mid)


BOLT = (234, 236, 238)


def flange_rows(img, y0, y1, x0=1, x1=14):
    """A bolted brass flange, rows y0..y1: steel bolt heads along its upper row, its lower row in shadow."""
    for y in range(y0, y1 + 1):
        band_row(img, y, x0, x1, (BRASS_HI, BRASS, BRASS_LO, BRASS_DK))
    for x in range(x0 + 2, x1 - 1, 3):
        put(img, x, y0, BOLT if x < 9 else PALE)
    for x in range(x0, x1 + 1):
        put(img, x, y1, cylinder_shade(x, x0, x1, BRASS, BRASS_LO, BRASS_DK, BRASS_DK))


BRASS4 = (BRASS_HI, BRASS, BRASS_LO, BRASS_DK)
COPPER4 = (COPPER_HI, COPPER, COPPER_LO, COPPER_DK)
PALE4 = (PALE_HI, PALE, PALE_LO, PALE_DK)
DARK4 = (DARK_HI, DARK, DARK_LO, DARK_DK)
FIRE = ((255, 214, 120), (250, 150, 44), (206, 78, 22), (120, 36, 14))
WATER = ((120, 176, 236), (64, 118, 214), (40, 76, 168), (26, 46, 110))
LAVA = ((255, 200, 90), (240, 120, 30), (190, 60, 20), (110, 30, 12))
HAZARD = ((232, 186, 40), (34, 34, 36))
PAPER = ((238, 234, 220), (206, 200, 182), (60, 60, 64))


def brass_ring_plan(img, width=14, bore=8):
    """A flange from above: a brass ring, bolts at the four quarters, the dark bore in its middle."""
    round_plan(img, width, lambda x, y, d: lit(d, BRASS_HI, BRASS, BRASS_LO), ring=lambda x, y, d: BRASS_LO if d > 0 else BRASS)
    round_plan(img, bore, lambda x, y, d: DARK_DK, ring=lambda x, y, d: BAR_SH if d < 0 else DARK_LO)
    for x, y in ((2, 7), (13, 8), (7, 2), (8, 13)):
        put(img, x, y, BOLT)


def machine_bottom():
    """The underside every cabinet shares: the frame round a plain dark plate."""
    img = blank()
    plan_cabinet(img, DARK_LO)
    return img


# ---------------------------------------------------------------- the distillation tower
# Vacuum Distiller at the foot, Distillation Columns on it, the Condenser on top: flanges meet flanges, so a built
# tower reads as one bolted-up apparatus.
# Distiller: cabinet y 0..8 (rows 8..15), copper boiler y 8..13 (rows 3..7, 14 across), shoulder y 13..14 (row 2,
# 12 across), neck y 14..15 (row 1, 10 across), flange y 15..16 (row 0, 14 across).


def distiller_side():
    img = blank()
    band_row(img, 0, 1, 14, BRASS4, rivets=(3, 6, 9, 12))
    band_row(img, 1, 3, 12, DARK4)
    band_row(img, 2, 2, 13, COPPER4)
    for y in range(3, 8):
        band_row(img, y, 1, 14, COPPER4)
    band_row(img, 3, 1, 14, (COPPER_HI, COPPER_HI, COPPER, COPPER_LO))  # the boiler's shoulder catches the light
    band_row(img, 5, 1, 14, COPPER4, rivets=(2, 5, 8, 11, 14))  # a riveted seam round its middle
    band_row(img, 7, 1, 14, (COPPER, COPPER_LO, COPPER_DK, COPPER_DK))
    # the cabinet: a burner behind a grille, its glow on the bars
    cabinet_elev(img, 8, 15)
    rect(img, 4, 11, 11, 12, FIRE[3])
    for x in range(4, 12):
        put(img, x, 11, FIRE[2] if x % 2 else FIRE[3])
        put(img, x, 12, FIRE[1] if x % 2 else FIRE[3])
    hline(img, 3, 12, 10, DARK_DK)
    hline(img, 3, 12, 13, DARK_HI)
    vline(img, 3, 10, 13, DARK_DK)
    vline(img, 12, 10, 13, DARK_HI)
    return img


def distiller_top():
    img = blank()
    plan_cabinet(img)
    brass_ring_plan(img)
    return img


# Column: flanges y 0..2 (rows 14..15) and y 14..16 (rows 0..1), 14 across; the tube y 2..14 (rows 2..13), 10 across.


def column_side():
    img = blank()
    flange_rows(img, 0, 1)
    for y in range(2, 14):
        band_row(img, y, 3, 12, PALE4)
    band_row(img, 2, 3, 12, (PALE, PALE_LO, PALE_DK, PALE_DK))  # under the flange, in its shadow
    # a sight glass: dark, the silver of condensed mercury running down it
    rect(img, 6, 5, 9, 10, GLASS_DK)
    rect(img, 7, 5, 8, 10, GLASS)
    put(img, 7, 6, (206, 212, 222))
    put(img, 8, 8, (170, 176, 188))
    put(img, 7, 9, (226, 230, 238))
    hline(img, 5, 10, 4, PALE_DK)
    hline(img, 5, 10, 11, PALE_HI)
    vline(img, 5, 4, 11, PALE_DK)
    vline(img, 10, 4, 11, PALE_HI)
    flange_rows(img, 14, 15)
    return img


def column_end():
    img = blank()
    brass_ring_plan(img)
    return img


# Condenser: flange y 0..2 (rows 14..15), finned body y 2..12 (rows 4..13: the body 12 across, a fin 14 across on
# every other row), cap y 12..14 (rows 2..3, 10 across), vent y 14..15 (row 1, 6 across) under a hood y 15..16 (row
# 0, 8 across).


def condenser_side():
    img = blank()
    band_row(img, 0, 4, 11, DARK4)
    band_row(img, 1, 5, 10, (DARK, DARK_LO, DARK_DK, DARK_DK))
    band_row(img, 2, 3, 12, PALE4)
    band_row(img, 3, 3, 12, (PALE, PALE_LO, PALE_DK, PALE_DK))
    for y in range(4, 14):
        if y % 2 == 0:  # a fin
            band_row(img, y, 1, 14, COPPER4)
        else:  # the body between, in the fins' shadow
            band_row(img, y, 2, 13, (COPPER_LO, COPPER_DK, COPPER_DK, (70, 34, 22)))
    flange_rows(img, 14, 15)
    return img


def condenser_top():
    img = blank()
    round_plan(img, 14, lambda x, y, d: lit(d, COPPER_HI, COPPER, COPPER_LO), ring=lambda x, y, d: COPPER_LO if d > 0 else COPPER)
    round_plan(img, 10, lambda x, y, d: lit(d, PALE_HI, PALE, PALE_LO), ring=lambda x, y, d: PALE_DK if d > 0 else PALE)
    round_plan(img, 8, lambda x, y, d: lit(d, DARK_HI, DARK, DARK_LO), ring=lambda x, y, d: DARK_DK if d > 0 else DARK_HI)
    rect(img, 7, 7, 8, 8, BAR_SH)  # the vent's mouth under its hood
    return img


# ---------------------------------------------------------------- Crusher
# Cabinet y 0..11 (rows 5..15); the hopper's neck y 11..13 (rows 3..4, 12 across) and mouth y 13..16 (rows 0..2,
# 14 across), hazard-striped.


def crusher_side():
    img = blank()
    band_row(img, 0, 1, 14, (PALE_HI, PALE_HI, PALE, PALE_LO))
    for x in range(1, 15):
        put(img, x, 1, HAZARD[0] if (x // 2) % 2 == 0 else HAZARD[1])
    band_row(img, 2, 1, 14, (PALE, PALE_LO, PALE_DK, PALE_DK))
    for y in (3, 4):
        band_row(img, y, 2, 13, (PALE_LO, PALE_DK, PALE_DK, DARK_HI))
    cabinet_elev(img, 5, 15)
    # an inspection slot: the jaws inside, their teeth meeting
    rect(img, 3, 8, 12, 12, BAR_SH)
    for x in range(3, 13):
        put(img, x, 8, DARK)
        put(img, x, 9, DARK_HI if x % 2 == 0 else BAR_SH)
        put(img, x, 11, DARK_HI if x % 2 == 1 else BAR_SH)
        put(img, x, 12, DARK)
    hline(img, 3, 12, 7, DARK_DK)
    hline(img, 3, 12, 13, DARK_HI)
    return img


def crusher_top():
    img = blank()
    plan_cabinet(img)
    # the mouth: a pale rim, then the throat going down into the dark, the jaws' teeth across it
    for y in range(1, 15):
        for x in range(1, 15):
            put(img, x, y, PALE if (x + y) < 15 else PALE_LO)
    hline(img, 1, 14, 1, PALE_HI)
    vline(img, 1, 1, 14, PALE_HI)
    rect(img, 3, 3, 12, 12, DARK_DK)
    rect(img, 4, 4, 11, 11, BAR_SH)
    for x in range(4, 12):
        put(img, x, 6, DARK if x % 2 else DARK_LO)
        put(img, x, 7, DARK_LO if x % 2 else BAR_SH)
        put(img, x, 8, DARK_LO if x % 2 == 0 else BAR_SH)
        put(img, x, 9, DARK if x % 2 == 0 else DARK_LO)
    return img


# ---------------------------------------------------------------- Sifter
# Four legs (the frame's posts) and rims; the sieve tray y 10..15 (rows 1..5, 12 across), the funnel y 4..10 (rows
# 6..11, 8 across), its spout y 2..4 (rows 12..13, 4 across). Open between the legs under the tray.


def sifter_side():
    img = blank()
    for y in range(16):  # the legs
        put(img, 0, y, BAR)
        put(img, 1, y, BAR_IN)
        put(img, 14, y, BAR_IN)
        put(img, 15, y, BAR_SH)
    for x in range(2, 14):  # the rims
        put(img, x, 0, BAR)
        put(img, x, 14, BAR_IN)
        put(img, x, 15, BAR_SH)
    for x, c in zip((0, 1, 14, 15), (STUD, STUD_LO, STUD_LO, STUD)):
        put(img, x, 0, c)
    for x, c in zip((0, 1, 14, 15), (STUD_DK, STUD_DK, STUD_DK, STUD_SH)):
        put(img, x, 15, c)
    # the tray: brass-rimmed, wooden slats
    band_row(img, 1, 2, 13, BRASS4)
    for y in range(2, 5):
        for x in range(2, 14):
            put(img, x, y, (116, 84, 50) if x % 3 else (86, 60, 34))
    band_row(img, 5, 2, 13, (BRASS, BRASS_LO, BRASS_DK, BRASS_DK))
    # the funnel and its spout
    for y in range(6, 12):
        band_row(img, y, 4, 11, PALE4)
    band_row(img, 6, 4, 11, (PALE, PALE_LO, PALE_DK, PALE_DK))
    for y in (12, 13):
        band_row(img, y, 6, 9, (PALE_LO, PALE_DK, DARK_HI, DARK))
    return img


def sifter_top():
    img = blank()
    plan_cabinet(img, BRASS_LO)
    # the brass mesh over the dark of the tray
    for y in range(2, 14):
        for x in range(2, 14):
            if x % 3 == 1 or y % 3 == 1:
                put(img, x, y, BRASS_HI if (x % 3 == 1) and (y % 3 == 1) else BRASS)
            else:
                put(img, x, y, BAR_SH if (x + y) % 5 else DARK_LO)
    return img


def sifter_bottom():
    img = blank()
    plan_cabinet(img, DARK_LO)
    rect(img, 6, 6, 9, 9, PALE_DK)
    rect(img, 7, 7, 8, 8, BAR_SH)
    return img


# ---------------------------------------------------------------- Water Pump
# Cabinet y 0..11 (rows 5..15); the copper pump head y 11..15 (rows 1..4, 12 across); a brass dome y 15..16 (row 0,
# 6 across).


def water_pump_side():
    img = blank()
    band_row(img, 0, 5, 10, BRASS4)
    for y in range(1, 5):
        band_row(img, y, 2, 13, COPPER4)
    band_row(img, 1, 2, 13, (COPPER_HI, COPPER_HI, COPPER, COPPER_LO))
    band_row(img, 3, 2, 13, COPPER4, rivets=(3, 6, 9, 12))
    band_row(img, 4, 2, 13, (COPPER, COPPER_LO, COPPER_DK, COPPER_DK))
    cabinet_elev(img, 5, 15)
    # a water gauge: a glass tube, the water standing in it
    rect(img, 4, 8, 5, 12, GLASS_DK)
    rect(img, 4, 10, 5, 12, WATER[1])
    put(img, 4, 10, WATER[0])
    put(img, 5, 12, WATER[2])
    vline(img, 3, 8, 12, DARK_DK)
    vline(img, 6, 8, 12, DARK_HI)
    # an intake grille
    for y in (8, 10, 12):
        hline(img, 8, 12, y, BAR_SH)
        if y < 12:
            hline(img, 8, 12, y + 1, DARK_HI)
    return img


def water_pump_top():
    img = blank()
    plan_cabinet(img)
    round_plan(img, 12, lambda x, y, d: lit(d, COPPER_HI, COPPER, COPPER_LO), ring=lambda x, y, d: COPPER_LO if d > 0 else COPPER)
    round_plan(img, 6, lambda x, y, d: lit(d, BRASS_HI, BRASS, BRASS_LO, 2), ring=lambda x, y, d: BRASS_LO if d > 0 else BRASS)
    return img


def water_pump_bottom():
    img = blank()
    plan_cabinet(img, DARK_LO)
    for y in range(4, 12, 2):
        hline(img, 4, 11, y, BAR_SH)
    return img


# ---------------------------------------------------------------- Stone Maker: a plain cabinet, the tubes in its panel


def stone_maker_side():
    img = blank()
    cabinet_elev(img, 0, 15)
    # lava down the left, water down the right, meeting in a chamber where the stone forms
    for y in range(3, 10):
        put(img, 3, y, DARK_DK)
        put(img, 4, y, LAVA[1] if y % 3 else LAVA[0])
        put(img, 5, y, LAVA[2])
        put(img, 6, y, DARK_HI)
        put(img, 9, y, DARK_DK)
        put(img, 10, y, WATER[1] if y % 3 else WATER[0])
        put(img, 11, y, WATER[2])
        put(img, 12, y, DARK_HI)
    rect(img, 3, 10, 12, 13, DARK_DK)
    rect(img, 4, 11, 11, 12, (104, 104, 106))
    for x, y in ((5, 11), (8, 12), (10, 11), (6, 12)):
        put(img, x, y, (136, 136, 138))
    put(img, 4, 11, LAVA[2])
    put(img, 11, 11, WATER[2])
    return img


def stone_maker_top():
    img = blank()
    plan_cabinet(img)
    rect(img, 5, 5, 10, 10, DARK_DK)
    rect(img, 6, 6, 9, 9, BAR_SH)
    hline(img, 5, 10, 11, DARK_HI)
    vline(img, 11, 5, 11, DARK_HI)
    return img


# ---------------------------------------------------------------- Printing Press
# Cabinet y 0..12 (rows 4..15); two rollers along x, y 12..15 (rows 1..3), z 3..6 and 10..13; their round ends wear
# printing_press_roller.


def printing_press_side():
    img = blank()
    for x in range(1, 15):
        put(img, x, 1, PALE_HI if x < 12 else PALE)
        put(img, x, 2, PALE)
        put(img, x, 3, PALE_DK)
    put(img, 1, 2, BRASS)
    put(img, 14, 2, BRASS_LO)
    cabinet_elev(img, 4, 15)
    # a slot where the prints come out, a sheet of paper under it
    hline(img, 3, 12, 7, BAR_SH)
    hline(img, 3, 12, 8, DARK_HI)
    rect(img, 4, 10, 11, 12, PAPER[0])
    hline(img, 4, 11, 12, PAPER[1])
    hline(img, 5, 9, 11, PAPER[2])  # a line of print
    return img


def printing_press_top():
    img = blank()
    plan_cabinet(img)
    for z0 in (3, 10):  # the rollers
        hline(img, 1, 14, z0, PALE_HI)
        hline(img, 1, 14, z0 + 1, PALE)
        hline(img, 1, 14, z0 + 2, PALE_LO)
        put(img, 1, z0 + 1, BRASS)
        put(img, 14, z0 + 1, BRASS_LO)
    rect(img, 2, 6, 13, 9, DARK_LO)
    hline(img, 3, 12, 7, (40, 40, 44))  # the ink bed between them
    return img


def printing_press_roller():
    """A roller's end, seen square: a steel disc 3 across round a brass hub; the rest of the texture is unused."""
    img = blank()
    rect(img, 0, 0, 15, 15, PALE_LO)
    rect(img, 0, 0, 2, 2, PALE)
    put(img, 0, 0, PALE_HI)
    put(img, 1, 1, BRASS)
    put(img, 2, 2, PALE_DK)
    return img


# ---------------------------------------------------------------- Compressor (2026-10-18)
# Cabinet y 0..12 (rows 4..15), the ram's housing on it, y 12..16 (rows 0..3, 10 across: columns 3..12): a dark,
# brass-banded block under a pale lid, the ram coming down out of it. In the cabinet's panel a bay where the press plate comes down on a cake of metal.


def compressor_side():
    img = blank()
    # the ram housing: pale steel, a brass band, bolt heads
    band_row(img, 0, 3, 12, (PALE_HI, PALE_HI, PALE, PALE_LO))  # the lid's edge
    for y in (1, 3):
        band_row(img, y, 3, 12, DARK4)
    band_row(img, 2, 3, 12, BRASS4)
    for x in (4, 7, 10):
        put(img, x, 2, BOLT)
    cabinet_elev(img, 4, 15)
    # the press bay: the plate (pale, a row of shadow under it), the guide rods, and a pressed cake on the anvil
    rect(img, 3, 7, 9, 12, BAR_SH)
    hline(img, 3, 9, 7, PALE_HI)
    hline(img, 3, 9, 8, PALE_LO)
    vline(img, 3, 9, 12, DARK_HI)
    vline(img, 9, 9, 12, DARK_HI)
    rect(img, 5, 11, 7, 11, (196, 198, 204))  # the cake of metal, freshly pressed
    hline(img, 5, 7, 12, (120, 124, 132))
    hline(img, 3, 9, 13, DARK_HI)
    return img


def compressor_top():
    img = blank()
    plan_cabinet(img)
    # the housing from above: a square with a bolted lid and the ram's rod in its middle
    rect(img, 3, 3, 12, 12, PALE_LO)
    hline(img, 3, 12, 3, PALE_HI)
    vline(img, 3, 3, 12, PALE_HI)
    hline(img, 3, 12, 12, PALE_DK)
    vline(img, 12, 3, 12, PALE_DK)
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        put(img, x, y, BOLT)
    round_plan(img, 6, lambda x, y, d: lit(d, BRASS_HI, BRASS, BRASS_LO), ring=lambda x, y, d: BRASS_DK if d > 0 else BRASS)
    return img


# ---------------------------------------------------------------- Recycler (2026-10-18)
# Two blocks: the body below (a whole cabinet, a sorting window in it where the shredded bits fall past a magnet drum),
# the intake above (a hopper of riveted plate, hazard-striped round its lip, the shredder's two toothed shafts in its
# throat). SCRAP_BITS are the colours of what falls: steel, rust, copper, a fleck of gold.
SCRAP_BITS = ((150, 154, 160), (176, 98, 52), (196, 112, 70), (120, 124, 132), (232, 196, 84))


def recycler_side():
    img = blank()
    cabinet_elev(img, 0, 15)
    # the sorting window: dark inside, bits falling, the magnet drum across the bottom holding the iron
    rect(img, 3, 5, 9, 12, BAR_SH)
    hline(img, 3, 9, 5, DARK_DK)
    for (x, y), c in zip(((4, 6), (7, 7), (5, 9), (8, 10), (6, 8), (4, 11)), SCRAP_BITS + (SCRAP_BITS[0],)):
        put(img, x, y, c)
    hline(img, 3, 9, 12, PALE_LO)  # the drum
    hline(img, 3, 9, 13, PALE_DK)
    for x in (4, 6, 8):
        put(img, x, 12, (150, 154, 160))  # iron held on it
    # a maker's plate over the lamp
    rect(img, 10, 9, 12, 12, BRASS_LO)
    hline(img, 10, 12, 9, BRASS_HI)
    put(img, 11, 11, BRASS_DK)
    return img


def recycler_top_side():
    """
    The intake's sides, as the Crusher's mouth is drawn: a pale rim with the hazard stripes (rows 0..2), then dark
    plate between pale corner posts, bolted; the lip where the hopper steps in (row 6, y 10) a pale ledge. The neck
    into the body (rows 12..15) is the same dark plate.
    """
    img = blank()
    band_row(img, 0, 0, 15, (PALE_HI, PALE_HI, PALE, PALE_LO))
    for x in range(16):
        put(img, x, 1, HAZARD[0] if (x // 2) % 2 == 0 else HAZARD[1])
    band_row(img, 2, 0, 15, (PALE, PALE_LO, PALE_DK, PALE_DK))
    for y in range(3, 16):
        band_row(img, y, 2, 13, DARK4)
        for x, c in ((0, PALE_LO), (1, PALE_DK), (14, PALE_LO), (15, PALE_DK)):
            put(img, x, y, c)
    band_row(img, 6, 0, 15, (PALE, PALE_LO, PALE_DK, PALE_DK))  # the ledge where it steps in
    hline(img, 2, 13, 7, DARK_DK)
    for y in (4, 9, 13):
        put(img, 1, y, BOLT)
        put(img, 14, y, BOLT)
    hline(img, 0, 15, 15, BAR_SH)
    return img


def recycler_top():
    """The intake from above: its lip, and down its throat the shredder's two shafts, their teeth meshing."""
    img = blank()
    rect(img, 0, 0, 15, 15, PALE)
    hline(img, 0, 15, 0, PALE_HI)
    vline(img, 0, 0, 15, PALE_HI)
    hline(img, 0, 15, 15, PALE_DK)
    vline(img, 15, 0, 15, PALE_DK)
    rect(img, 2, 2, 13, 13, BAR_SH)
    for row, phase in ((5, 0), (6, 0), (9, 1), (10, 1)):
        for x in range(3, 13):
            on = (x + phase) % 2 == 0
            put(img, x, row, (DARK_HI if row in (5, 9) else DARK) if on else DARK_DK)
    hline(img, 3, 12, 7, DARK_LO)
    hline(img, 3, 12, 8, DARK_LO)
    return img


# ---------------------------------------------------------------- Machine Chassis: the frame alone, open


def machine_chassis():
    img = blank()
    cabinet_elev(img, 0, 15, panel=(0, 0, 0))
    for y in range(2, 14):
        for x in range(2, 14):
            img.putpixel((x, y), (0, 0, 0, 0))
    return img

# ---------------------------------------------------------------- sight glass gauges
# Laid over the hand-finished tanks' windows by the gauge models: columns 0-3 the liquid (row 0 its surface, catching
# the light), columns 4-7 the empty glass above it, rows 0-5 (the window is 6 tall).


def gauge(surface, body, glass):
    """{@code body}: the liquid's four columns, lit edge to shadowed edge; {@code glass}: the empty glass's four."""
    img = blank()
    for y in range(6):
        for x in range(4):
            put(img, x, y, surface[x] if y == 0 else body[x])
            put(img, 4 + x, y, glass[x] if y < 3 else tuple(max(0, c - 8) for c in glass[x]))
    return img


def mercury_gauge():
    img = gauge(((244, 247, 252), (252, 253, 255), (226, 230, 238), (180, 186, 198)),
                ((196, 202, 214), (228, 232, 240), (168, 174, 188), (118, 124, 138)),
                ((30, 32, 36), (62, 68, 76), (26, 28, 32), (18, 19, 22)))
    put(img, 1, 3, (252, 253, 255))  # the sky in it
    return img


def experience_gauge():
    img = gauge(((226, 255, 150), (240, 255, 188), (196, 246, 110), (150, 214, 66)),
                ((124, 204, 52), (164, 234, 84), (104, 184, 42), (72, 142, 30)),
                ((22, 30, 22), (52, 66, 50), (20, 26, 20), (14, 18, 14)))
    put(img, 2, 2, (226, 255, 150))  # a bubble rising
    put(img, 1, 4, (206, 250, 124))
    return img

# ---------------------------------------------------------------- Spawn Egg Brewer
# A blackstone plate; on it a round glass vessel (10 across, y 2..9, rows 7..13) of violet brew with an egg in it, a
# brass foot band (12 across, y 3..4, row 12), its shoulder (8, y 9..10, row 6) and neck (4, y 10..12, rows 4..5),
# a brass stopper (6, y 12..13, row 3) with a shard of amethyst standing in it; two brass rods either side (columns 1
# and 14, y 2..12, rows 4..13) and arms from them to the neck (y 10..11, row 5).

BREW = ((56, 22, 88), (92, 42, 140), (132, 72, 190), (178, 128, 226))
GLASS_EDGE = (40, 30, 56)
EGG = ((196, 180, 148), (232, 220, 190), (250, 244, 226), (120, 150, 60))


def brewer_side():
    img = blank()
    # the vessel: dark glass at its edges, the brew inside, a streak of light down its left
    for y in range(7, 14):
        for x in range(3, 13):
            c = cylinder_shade(x, 3, 12, BREW[3], BREW[2], BREW[1], GLASS_EDGE)
            if y == 13:
                c = BREW[0]
            put(img, x, y, c)
        put(img, 4, y, (214, 196, 240) if 8 <= y <= 11 else BREW[2])
    # the egg in the brew, speckled
    for y, (a, b) in ((8, (7, 8)), (9, (6, 9)), (10, (6, 9)), (11, (6, 9)), (12, (7, 8))):
        for x in range(a, b + 1):
            put(img, x, y, EGG[2] if x < 8 and y < 11 else EGG[1] if x < 9 else EGG[0])
    put(img, 8, 9, EGG[3])
    put(img, 7, 11, EGG[3])
    band_row(img, 12, 2, 13, BRASS4, rivets=(4, 8, 11))
    band_row(img, 6, 4, 11, (BREW[3], BREW[2], BREW[1], GLASS_EDGE))  # the shoulder
    for y in (4, 5):
        band_row(img, y, 6, 9, ((200, 190, 220), (150, 130, 190), (110, 90, 150), GLASS_EDGE))  # the neck, clear glass
    band_row(img, 3, 5, 10, BRASS4)  # the stopper
    for y in range(4, 14):  # the rods
        put(img, 1, y, BRASS_HI if y % 3 else BRASS)
        put(img, 14, y, BRASS_LO if y % 3 else BRASS_DK)
    for x in (2, 3, 4, 5):  # the arms
        put(img, x, 5, BRASS_HI if x < 4 else BRASS)
    for x in (10, 11, 12, 13):
        put(img, x, 5, BRASS if x < 12 else BRASS_LO)
    return img


def brewer_top():
    img = blank()
    round_plan(img, 10, lambda x, y, d: lit(d, BREW[3], BREW[2], BREW[1]), ring=lambda x, y, d: GLASS_EDGE)
    round_plan(img, 8, lambda x, y, d: lit(d, (200, 190, 220), (150, 130, 190), (110, 90, 150)))
    round_plan(img, 6, lambda x, y, d: lit(d, BRASS_HI, BRASS, BRASS_LO), ring=lambda x, y, d: BRASS_LO if d > 0 else BRASS)
    for y in (7, 8):  # the rods' tops and the arms
        for x in (1, 14):
            put(img, x, y, BRASS_HI if x == 1 else BRASS_LO)
        for x in list(range(2, 6)) + list(range(10, 14)):
            put(img, x, y, BRASS if y == 7 else BRASS_LO)
    return img

# ---------------------------------------------------------------- the copper line: Copper Tank, Sight Glass, Valve
# Copper Tank: plinth y 0..3 (rows 13..15), a copper vessel 14 across y 3..15 (rows 1..12) with a sight glass down the
# middle of each flat side (x 6..10, framed in brass at columns 5 and 10), a brass lid 12 across y 15..16 (row 0).


def copper_tank_side():
    img = blank()
    band_row(img, 0, 2, 13, BRASS4)
    for y in range(1, 13):
        band_row(img, y, 1, 14, COPPER4)
    band_row(img, 1, 1, 14, (COPPER_HI, COPPER_HI, COPPER, COPPER_LO))  # the vessel's shoulder
    for y in (3, 10):  # riveted brass bands, broken by the window
        band_row(img, y, 1, 14, BRASS4, rivets=(2, 13))
    band_row(img, 12, 1, 14, (COPPER, COPPER_LO, COPPER_DK, COPPER_DK))
    # the window's brass frame and the dark of the glass (the gauge models lay the liquid over it)
    for y in range(1, 13):
        put(img, 5, y, BRASS_HI)
        put(img, 10, y, BRASS_LO)
        for x in range(6, 10):
            put(img, x, y, GLASS_DK)
    for x in range(5, 11):
        put(img, x, 1, BRASS_HI)
        put(img, x, 12, BRASS_LO)
    frame_band(img, 13, 15)
    return img


def copper_tank_top():
    img = blank()
    rect(img, 0, 0, 15, 15, BAR_IN)
    for cx, cy in ((0, 0), (14, 0), (0, 14), (14, 14)):
        rect(img, cx, cy, cx + 1, cy + 1, STUD)
        put(img, cx, cy, STUD_HI)
        put(img, cx + 1, cy + 1, STUD_LO)
    round_plan(img, 14, lambda x, y, d: lit(d, COPPER_HI, COPPER, COPPER_LO), ring=lambda x, y, d: COPPER_LO if d > 0 else COPPER)
    round_plan(img, 12, lambda x, y, d: lit(d, BRASS_HI, BRASS, BRASS_LO), ring=lambda x, y, d: BRASS_LO if d > 0 else BRASS)
    round_plan(img, 6, lambda x, y, d: lit(d, BRASS, BRASS_LO, BRASS_DK, 2), ring=lambda x, y, d: BRASS_DK)  # the filler cap
    rect(img, 6, 7, 9, 8, BRASS_HI)  # its handle across
    return img


def line_gauge(surface, body, glass, rows=12):
    """A gauge {@code rows} tall: columns 0-3 the liquid (row 0 its surface), 4-7 the empty glass above it."""
    img = blank()
    for y in range(rows):
        for x in range(4):
            put(img, x, y, surface[x] if y == 0 else body[x])
            put(img, 4 + x, y, glass[x] if y < rows // 2 else tuple(max(0, c - 8) for c in glass[x]))
    return img


EMPTY_GLASS = ((64, 78, 86), (118, 134, 144), (54, 66, 74), (40, 50, 56))  # glass with nothing behind it, not a hole
LINE_GAUGES = {
    "water": (((150, 196, 246), (196, 226, 255), (120, 170, 236), (90, 132, 206)),
              ((70, 120, 214), (104, 152, 232), (58, 98, 186), (40, 70, 150))),
    "oil": (((250, 226, 140), (255, 240, 180), (236, 200, 96), (206, 160, 56)),
            ((226, 176, 56), (240, 200, 92), (204, 150, 34), (170, 118, 22))),
    "biodiesel": (((214, 160, 80), (236, 188, 110), (178, 120, 44), (140, 86, 26)),
                  ((150, 94, 28), (178, 118, 40), (122, 74, 22), (92, 54, 16))),
    "other": (((200, 202, 206), (224, 226, 230), (170, 172, 178), (140, 142, 148)),
              ((140, 142, 148), (170, 172, 178), (116, 118, 124), (92, 94, 100))),
}


def sight_glass_glass():
    """
    The Sight Glass's glass: clear, faintly grey-blue, a pixel of highlight running along it - drawn without edge
    lines, as the Experience Pipe's is, so the same pane can dress every face of the chamber.
    """
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            a = 70 if (x * 7 + y * 3) % 11 else 110
            img.putpixel((x, y), (214, 228, 236, a))
    for i in range(16):
        img.putpixel((i, 3), (240, 248, 252, 150))  # the glint running along the chamber
    return img


def brass_fitting():
    """
    Cast and turned brass for the fittings' bodies and flanges: smooth, in broad bands of light and shade as a
    turned surface catches it, a fine darker line where one band meets the next. Tileable, and kept free of grain -
    speckled, it read as sponge.
    """
    img = blank()
    bands = (BRASS_HI, BRASS_HI, BRASS, BRASS, BRASS, BRASS_LO, BRASS, BRASS)
    for y in range(16):
        for x in range(16):
            put(img, x, y, bands[y % 8])
    for x in range(16):
        put(img, x, 7, BRASS_DK)  # the line between two turned bands
    return img


def valve_handle():
    """The Valve's handle: red enamelled, as a valve handle is painted to be seen, lit along its top."""
    img = blank()
    red = ((226, 72, 58), (190, 44, 36), (138, 26, 24))
    for y in range(16):
        for x in range(16):
            put(img, x, y, red[0] if y % 4 == 0 else red[1] if y % 4 in (1, 2) else red[2])
    return img


def lamp(img, x, y):
    """A status lamp, dark: two pixels, and a bezel on their left."""
    put(img, x - 1, y, DARK_DK)
    rect(img, x, y, x + 1, y, LAMP_OFF)


def lamp_overlay():
    """The lamp lit, for the overlay: its two pixels at the corner of an otherwise empty sheet."""
    img = blank()
    rect(img, 0, 0, 1, 0, LAMP_ON)
    return img


def save(img, name, seed, keep=()):
    img = texkit.finish(img, seed, keep=FRAME_KEEP + tuple(keep), **texkit.SMOOTH)
    if name in LAMPS:
        lamp(img, *LAMPS[name])  # after the finish, so the lamp and its bezel stay exact
    img.save(BLOCK / f"{name}.png")


# ---------------------------------------------------------------- Electric Furnace: a cabinet with an oven window
# The window (rows 4..11, columns 3..12) in a brass frame, the heating coils behind its glass - dull copper while it
# stands, glowing through the glass while it smelts - a lamp over it and a vent under it. Louvres down its sides.
COIL_COLD = ((150, 84, 52), (98, 48, 30))
OVEN_DARK = (30, 14, 10)


def electric_furnace_front(lit):
    img = blank()
    cabinet_elev(img, 0, 15)
    rect(img, 3, 4, 12, 11, BRASS_LO)
    hline(img, 3, 12, 4, BRASS_HI)
    vline(img, 3, 4, 11, BRASS)
    hline(img, 4, 12, 11, BRASS_DK)
    vline(img, 12, 5, 11, BRASS_DK)
    for y in range(5, 11):  # the glass, and the oven behind it: dark, or glowing from the coils down
        for x in range(4, 12):
            put(img, x, y, (FIRE[3] if y < 7 else FIRE[2]) if lit else GLASS_DK if y < 7 else OVEN_DARK)
    for row in (7, 10):  # two coils across, each looping up every third pixel
        for x in range(4, 12):
            put(img, x, row, (FIRE[1] if x % 3 else FIRE[0]) if lit else COIL_COLD[1])
            if x % 3 == 2:
                put(img, x, row - 1, FIRE[0] if lit else COIL_COLD[0])
    put(img, 5, 5, (255, 236, 200) if lit else GLASS_HI)  # the glass catching the light
    put(img, 6, 5, FIRE[1] if lit else GLASS)
    rect(img, 11, 2, 12, 2, (120, 236, 120) if lit else (38, 60, 40))  # the lamp: green while it works
    put(img, 10, 2, DARK_DK)
    for x in range(4, 12, 2):  # the vent under the window
        put(img, x, 13, BAR_SH)
        put(img, x + 1, 13, DARK_HI)
    return img


def electric_furnace_side():
    img = blank()
    cabinet_elev(img, 0, 15)
    for y in (4, 7, 10):  # louvres
        hline(img, 4, 11, y, BAR_SH)
        hline(img, 4, 11, y + 1, DARK_HI)
    rect(img, 6, 12, 9, 13, COPPER_LO)  # the terminal the power comes in by
    hline(img, 6, 9, 12, COPPER_HI)
    put(img, 7, 13, COPPER_DK)
    return img


def electric_furnace_top():
    img = blank()
    plan_cabinet(img)
    for y in (4, 6, 8, 10):  # the heat's vents
        hline(img, 4, 11, y, BAR_SH)
        hline(img, 4, 11, y + 1, DARK_HI)
    return img


# ---------------------------------------------------------------- Electrolytic Cell: a window onto the bath
# The window (rows 4..12) shows the cell's bath: pale water over a floor of mercury, two electrode plates hanging into
# it from a copper busbar across the top - one copper, one dark lead - and, while the current runs, bubbles rising off
# them. A pair of brass terminals over the window, a red and a black cable lug.
BATH_HI, BATH, BATH_DK = (170, 206, 224), (120, 168, 196), (84, 128, 160)
MERCURY_HI, MERCURY_LO = (220, 224, 230), (150, 156, 166)
LEAD, LEAD_HI = (70, 74, 84), (104, 108, 120)


def electrolytic_cell_front(lit):
    img = blank()
    cabinet_elev(img, 0, 15)
    rect(img, 3, 3, 12, 12, BRASS_LO)  # the window's frame
    hline(img, 3, 12, 3, BRASS_HI)
    vline(img, 3, 3, 12, BRASS)
    hline(img, 4, 12, 12, BRASS_DK)
    vline(img, 12, 4, 12, BRASS_DK)
    rect(img, 4, 4, 11, 11, BATH)  # the bath
    hline(img, 4, 11, 4, GLASS_DK)  # air over the water
    hline(img, 4, 11, 5, BATH_HI)
    hline(img, 4, 11, 11, MERCURY_LO)  # the mercury floor
    hline(img, 5, 10, 10, MERCURY_HI)
    for x in range(4, 12, 3):
        put(img, x, 8, BATH_DK)
    hline(img, 4, 11, 4, COPPER_LO)  # the busbar the plates hang from
    rect(img, 5, 5, 6, 9, COPPER)  # the copper plate
    vline(img, 5, 5, 9, COPPER_HI)
    rect(img, 9, 5, 10, 9, LEAD)  # the lead plate
    vline(img, 9, 5, 9, LEAD_HI)
    if lit:  # bubbles off the plates
        for x, y in ((7, 6), (8, 8), (4, 7), (11, 6), (7, 9), (11, 9)):
            put(img, x, y, (236, 246, 250))
    put(img, 5, 1, (190, 40, 36))  # the terminals: red and black lugs on brass posts
    put(img, 10, 1, (30, 30, 34))
    put(img, 5, 2, BRASS_HI)
    put(img, 10, 2, BRASS_HI)
    put(img, 7, 13, (120, 236, 120) if lit else (38, 60, 40))  # the lamp: green while the current runs
    return img


def electrolytic_cell_side():
    img = blank()
    cabinet_elev(img, 0, 15)
    rect(img, 4, 4, 11, 11, DARK_LO)  # a pipe port for the water, and a gauge of the bath
    rect(img, 6, 6, 9, 9, COPPER_LO)
    rect(img, 7, 7, 8, 8, (16, 16, 18))
    hline(img, 6, 9, 6, COPPER_HI)
    rect(img, 12, 5, 13, 10, GLASS_DK)
    rect(img, 12, 7, 13, 10, BATH)
    return img


def electrolytic_cell_top():
    img = blank()
    plan_cabinet(img)
    rect(img, 4, 4, 11, 11, DARK_LO)  # the lid, and the vent the gas leaves by
    for y in (5, 7, 9):
        hline(img, 5, 10, y, BAR_SH)
        hline(img, 5, 10, y + 1, DARK_HI)
    return img


# ---------------------------------------------------------------- Barter Terminal: the screen of a piglin's room
# A screen in a brass bezel (rows 3..10, columns 3..12): dark glass while it stands; while its piglins barter it lights
# up teal, showing a piglin's snout and the gold it is handed (the lit screen is a glowing quad of its own,
# barter_terminal_screen, so it shines at night). Two brass keys and the lamp under it. Louvres down its sides and on
# its lid: the Nether's air it makes goes out of it along the Ducts.
SCREEN_DK, SCREEN, SCREEN_HI = (10, 40, 44), (20, 92, 98), (90, 220, 214)
SNOUT, SNOUT_DK = (232, 156, 140), (178, 100, 90)
INGOT, INGOT_HI = (240, 196, 60), (255, 236, 140)


def barter_terminal_front():
    img = blank()
    cabinet_elev(img, 0, 15)
    rect(img, 3, 3, 12, 10, BRASS_LO)  # the bezel
    hline(img, 3, 12, 3, BRASS_HI)
    vline(img, 3, 3, 10, BRASS)
    hline(img, 4, 12, 10, BRASS_DK)
    vline(img, 12, 4, 10, BRASS_DK)
    rect(img, 4, 4, 11, 9, SCREEN_DK)  # the glass, dark
    put(img, 5, 5, GLASS_HI)  # catching the light
    put(img, 6, 5, GLASS)
    for x in (5, 8):  # two brass keys
        put(img, x - 1, 12, BRASS_LO)
        put(img, x, 12, BRASS)
    return img


def barter_terminal_screen():
    """The screen lit, alone on its sheet (rows 4..9, columns 4..11): the snout, an arrow, the ingot."""
    img = blank()
    rect(img, 4, 4, 11, 9, SCREEN_DK)
    hline(img, 4, 11, 4, SCREEN)  # the glass's sheen along its top
    for x, y in ((5, 6), (6, 6), (5, 7), (6, 7)):  # the snout, its nostrils
        put(img, x, y, SNOUT)
    put(img, 5, 7, SNOUT_DK)
    put(img, 6, 7, SNOUT_DK)
    put(img, 5, 5, SNOUT)
    put(img, 6, 5, SNOUT)
    put(img, 7, 7, SCREEN_HI)  # the arrow between
    put(img, 7, 6, SCREEN)
    put(img, 8, 6, INGOT_HI)  # the ingot
    put(img, 9, 6, INGOT_HI)
    put(img, 8, 7, INGOT)
    put(img, 9, 7, INGOT)
    put(img, 10, 7, INGOT)
    hline(img, 4, 11, 9, SCREEN)  # a line of text along its foot
    put(img, 6, 9, SCREEN_HI)
    put(img, 9, 9, SCREEN_HI)
    return img


def barter_terminal_side():
    img = blank()
    cabinet_elev(img, 0, 15)
    for y in (4, 7, 10):  # louvres
        hline(img, 4, 11, y, BAR_SH)
        hline(img, 4, 11, y + 1, DARK_HI)
    return img


def barter_terminal_top():
    img = blank()
    plan_cabinet(img)
    for y in (4, 7, 10):
        hline(img, 4, 11, y, BAR_SH)
        hline(img, 4, 11, y + 1, DARK_HI)
    return img


# ---------------------------------------------------------------- Diesel Generator: an engine set in a heavy cabinet
# Cabinet y 0..12 (rows 4..15), its lid flat; on the lid an exhaust stack, 4 across (y 12..16, rows 0..3), over the
# back right-hand corner as seen from the front (x 10..14, z 2..6), and the radiator's brass filler cap at the front
# left (x 3..5, z 11..13, row 3). Each side sees those two where it stands, so the four sides are drawn apart: the
# front (south) and the right-hand side (east) see the stack in columns 10..13 and the cap in 3..4; the back (north)
# and the left-hand side (west) see them mirrored, the stack in 2..5 and the cap in 11..12.
# The front is the radiator - a brass-framed core of dark fins - with the lamp under it, lit while it runs
# (tools/gen_machine_models.py lays it on); down each side, louvres over a copper fuel drum strapped in brass; on the
# back, the dial and the copper terminals the power leaves by. All of it a darker, heavier steel than the Generator's.
SOOT = (20, 20, 22)
STOP_RED = ((214, 60, 48), (150, 34, 30))


def diesel_top_parts(img, stack_x, cap_x):
    """The stack and the filler cap, as one side sees them over the lid: the stack from column {@code stack_x}."""
    for y in range(1, 4):  # the pipe: blackened steel, round
        band_row(img, y, stack_x, stack_x + 3, DARK4)
    band_row(img, 0, stack_x, stack_x + 3, (PALE_LO, PALE_DK, DARK_HI, DARK_LO))  # the lip, sooted
    band_row(img, 2, stack_x, stack_x + 3, (DARK_HI, DARK_HI, DARK, DARK_DK))  # a clamp band round it
    put(img, cap_x, 3, BRASS_HI)
    put(img, cap_x + 1, 3, BRASS_LO)


def diesel_generator_front():
    img = blank()
    diesel_top_parts(img, 10, 3)
    cabinet_elev(img, 4, 15)
    # the radiator: a brass frame (columns 3..12, rows 6..12) round its core, a mesh of dark fins
    rect(img, 3, 6, 12, 12, BRASS_LO)
    hline(img, 3, 12, 6, BRASS_HI)
    vline(img, 3, 6, 12, BRASS)
    hline(img, 4, 12, 12, BRASS_DK)
    vline(img, 12, 7, 12, BRASS_DK)
    for y in range(7, 12):
        for x in range(4, 12):
            put(img, x, y, DARK_HI if (x + y) % 2 == 0 else BAR_SH)
    hline(img, 4, 11, 7, BAR_SH)  # the frame's shadow over the core
    put(img, 5, 8, DARK_HI)
    # under it, a brass maker's plate (and the lamp, set at LAMPS)
    hline(img, 4, 7, 13, BRASS)
    put(img, 4, 13, BRASS_HI)
    put(img, 7, 13, BRASS_LO)
    return img


def diesel_generator_side(right):
    """A side: louvres over the fuel drum, a copper cylinder lying along it in brass straps."""
    img = blank()
    diesel_top_parts(img, 10 if right else 2, 3 if right else 11)
    cabinet_elev(img, 4, 15)
    for y in (6, 8):  # louvres
        hline(img, 3, 12, y, BAR_SH)
        hline(img, 3, 12, y + 1, DARK_HI)
    for y, c in zip(range(10, 14), (COPPER_HI, COPPER, COPPER_LO, COPPER_DK)):  # the drum, lit from above
        hline(img, 2, 13, y, c)
    for x in (4, 11):  # its straps
        for y, c in zip(range(10, 14), BRASS4):
            put(img, x, y, c)
    put(img, 2, 10, COPPER)  # its ends, rounding off
    put(img, 13, 10, COPPER_LO)
    put(img, 2, 13, BAR_SH)
    put(img, 13, 13, BAR_SH)
    return img


def diesel_generator_back():
    """The control side: a dial, a red stop button, and the copper terminals the power leaves by."""
    img = blank()
    diesel_top_parts(img, 2, 11)
    cabinet_elev(img, 4, 15)
    # the dial (columns 3..7, rows 7..11): a face in a brass bezel, its needle from the hub up to the right
    for x, y in ((4, 7), (5, 7), (6, 7), (3, 8), (3, 9), (3, 10)):
        put(img, x, y, BRASS)
    for x, y in ((7, 8), (7, 9), (7, 10), (4, 11), (5, 11), (6, 11)):
        put(img, x, y, BRASS_DK)
    rect(img, 4, 8, 6, 10, PALE_LO)
    put(img, 4, 8, PALE)
    put(img, 5, 9, BAR_SH)
    put(img, 6, 8, BAR_SH)
    put(img, 4, 10, STOP_RED[1])  # the red mark at the bottom of its scale
    # the stop button, in a dark collar
    rect(img, 9, 10, 10, 11, STOP_RED[0])
    put(img, 10, 11, STOP_RED[1])
    put(img, 11, 11, DARK_DK)
    hline(img, 9, 11, 12, DARK_DK)
    # the terminals: two copper posts with brass nuts
    for x in (9, 12):
        put(img, x, 7, BRASS_HI)
        put(img, x, 8, COPPER)
        put(img, x + 1, 8, COPPER_DK)
    hline(img, 3, 12, 13, DARK_LO)  # a cable trough along the foot
    return img


def diesel_generator_top():
    img = blank()
    plan_cabinet(img)
    # the stack from above (columns 10..13, rows 2..5): its rim lit from the upper left, the sooted bore
    rect(img, 10, 2, 13, 5, DARK)
    hline(img, 10, 13, 2, DARK_HI)
    vline(img, 10, 2, 5, DARK_HI)
    hline(img, 11, 13, 5, DARK_DK)
    vline(img, 13, 3, 5, DARK_DK)
    rect(img, 11, 3, 12, 4, SOOT)
    # louvres over the engine
    for y in (8, 10, 12):
        hline(img, 6, 12, y, BAR_SH)
        hline(img, 6, 12, y + 1, DARK_HI)
    # the radiator's filler cap, front left (columns 3..4, rows 11..12)
    put(img, 3, 11, BRASS_HI)
    put(img, 4, 11, BRASS)
    put(img, 3, 12, BRASS)
    put(img, 4, 12, BRASS_LO)
    return img


# ---------------------------------------------------------------- Creative Generator: a Generator for creative mode
# The Generator's cabinet, told apart at a glance by creative's purple: where the Generator has its firebox, a window of
# violet glass with an infinity sign in it, glowing (the sign is laid on again, lit, by creative_generator_glow - see
# tools/gen_machine_models.py); violet light behind the louvres of its sides and back; a violet lens in its lid.
VIOLET_DK, VIOLET, VIOLET_HI = (34, 14, 50), (88, 36, 132), (146, 72, 198)
MAGENTA, MAGENTA_HI = (228, 96, 232), (255, 196, 255)
CREATIVE = (VIOLET_DK, VIOLET, VIOLET_HI, MAGENTA, MAGENTA_HI)
# the infinity sign in the window, 8 by 4: two loops crossing in the middle
INFINITY = ((5, 6), (6, 6), (9, 6), (10, 6),
            (4, 7), (7, 7), (8, 7), (11, 7),
            (4, 8), (7, 8), (8, 8), (11, 8),
            (5, 9), (6, 9), (9, 9), (10, 9))
INFINITY_LIT = ((5, 6), (9, 6), (4, 7))  # where the light catches the upper left of each loop


def creative_window(img):
    """The window (columns 3..12, rows 4..11): a brass bezel round violet glass, the sign glowing in it."""
    rect(img, 3, 4, 12, 11, BRASS_LO)
    hline(img, 3, 12, 4, BRASS_HI)
    vline(img, 3, 4, 11, BRASS)
    hline(img, 4, 12, 11, BRASS_DK)
    vline(img, 12, 5, 11, BRASS_DK)
    rect(img, 4, 5, 11, 10, VIOLET_DK)
    for x, y in ((5, 5), (6, 5), (9, 5), (10, 5), (5, 10), (6, 10), (9, 10), (10, 10)):
        put(img, x, y, VIOLET)  # the glow over and under the loops; dark inside them and at the waist, so they read as loops
    for x, y in INFINITY:
        put(img, x, y, MAGENTA_HI if (x, y) in INFINITY_LIT else MAGENTA)


def creative_generator_front():
    img = blank()
    cabinet_elev(img, 0, 15)
    creative_window(img)
    for x in range(4, 12, 2):  # the vent under the window, as the Electric Furnace's
        put(img, x, 13, BAR_SH)
        put(img, x + 1, 13, VIOLET)
    return img


def creative_generator_glow():
    """The sign alone, lit: laid over the window by its own quad, full bright (exact: a glow is lit, not textured)."""
    img = blank()
    for x, y in INFINITY:
        put(img, x, y, MAGENTA_HI if (x, y) in INFINITY_LIT else MAGENTA)
    return img


def creative_generator_side():
    """Louvres with violet light behind them, and the copper terminal the power leaves by."""
    img = blank()
    cabinet_elev(img, 0, 15)
    for y in (3, 6, 9):
        hline(img, 4, 11, y, BAR_SH)
        hline(img, 4, 11, y + 1, VIOLET)
    put(img, 4, 4, VIOLET_HI)  # the light brightest where it first shows
    rect(img, 6, 12, 9, 13, COPPER_LO)
    hline(img, 6, 9, 12, COPPER_HI)
    put(img, 7, 13, COPPER_DK)
    return img


def creative_generator_back():
    """Two copper terminals in brass nuts over a grille lit violet from inside."""
    img = blank()
    cabinet_elev(img, 0, 15)
    for x in (4, 10):
        put(img, x, 3, BRASS_HI)
        put(img, x + 1, 3, BRASS_LO)
        put(img, x, 4, COPPER)
        put(img, x + 1, 4, COPPER_DK)
    for y in range(7, 13, 2):
        for x in range(4, 12, 2):
            put(img, x, y, VIOLET)
            put(img, x + 1, y, BAR_SH)
    put(img, 4, 7, VIOLET_HI)
    return img


def creative_generator_top():
    """The lid: a violet lens in a brass ring, lit from the upper left."""
    img = blank()
    plan_cabinet(img)
    round_plan(img, 10, lambda x, y, d: lit(d, BRASS_HI, BRASS, BRASS_LO), ring=lambda x, y, d: BRASS_LO if d > 0 else BRASS)
    round_plan(img, 6, lambda x, y, d: lit(d, VIOLET_HI, VIOLET, VIOLET_DK, spread=1),
               ring=lambda x, y, d: VIOLET if d < 0 else VIOLET_DK)
    put(img, 6, 6, MAGENTA_HI)  # the light caught on the lens
    return img


def main():
    save(creative_generator_front(), "creative_generator_front", 281, keep=CREATIVE)
    save(creative_generator_side(), "creative_generator_side", 282, keep=CREATIVE)
    save(creative_generator_back(), "creative_generator_back", 283, keep=CREATIVE)
    save(creative_generator_top(), "creative_generator_top", 284, keep=CREATIVE)
    creative_generator_glow().save(BLOCK / "creative_generator_glow.png")  # exact: a glow is lit, not textured
    save(diesel_generator_front(), "diesel_generator_front", 271)
    save(diesel_generator_side(False), "diesel_generator_left", 272)
    save(diesel_generator_side(True), "diesel_generator_right", 272)
    save(diesel_generator_back(), "diesel_generator_back", 273, keep=STOP_RED)
    save(diesel_generator_top(), "diesel_generator_top", 274, keep=(SOOT,))
    save(barter_terminal_front(), "barter_terminal_front", 261, keep=(SCREEN_DK, GLASS_HI, GLASS))
    texkit.finish(barter_terminal_screen(), 262, keep=(SCREEN_DK, SCREEN, SCREEN_HI, SNOUT, SNOUT_DK, INGOT, INGOT_HI),
                  **texkit.SMOOTH).save(BLOCK / "barter_terminal_screen.png")
    save(barter_terminal_side(), "barter_terminal_side", 263)
    save(barter_terminal_top(), "barter_terminal_top", 264)
    save(electrolytic_cell_front(False), "electrolytic_cell_front", 241,
         keep=(BATH_HI, BATH, BATH_DK, MERCURY_HI, MERCURY_LO, GLASS_DK, (38, 60, 40), (190, 40, 36), (30, 30, 34)))
    save(electrolytic_cell_front(True), "electrolytic_cell_front_lit", 242,
         keep=(BATH_HI, BATH, BATH_DK, MERCURY_HI, MERCURY_LO, GLASS_DK, (120, 236, 120), (236, 246, 250), (190, 40, 36), (30, 30, 34)))
    save(electrolytic_cell_side(), "electrolytic_cell_side", 243, keep=(BATH, GLASS_DK))
    save(electrolytic_cell_top(), "electrolytic_cell_top", 244)
    save(electric_furnace_front(False), "electric_furnace_front", 231, keep=(GLASS_HI, GLASS, GLASS_DK, OVEN_DARK) + COIL_COLD)
    save(electric_furnace_front(True), "electric_furnace_front_lit", 232, keep=FIRE + ((255, 236, 200), (120, 236, 120)))
    save(electric_furnace_side(), "electric_furnace_side", 233)
    save(electric_furnace_top(), "electric_furnace_top", 234)
    save(stirring_tank_side(), "stirring_tank_side", 101, keep=(GLASS_HI, GLASS, GLASS_DK))
    save(stirring_tank_top(), "stirring_tank_top", 102)
    save(stirring_tank_bottom(), "stirring_tank_bottom", 103)
    glass = (GLASS_HI, GLASS, GLASS_DK)
    save(distiller_side(), "distiller_side", 111, keep=FIRE)
    save(distiller_top(), "distiller_top", 112, keep=(BOLT,))
    save(machine_bottom(), "machine_bottom", 113)
    save(column_side(), "distillation_column_side", 121, keep=glass + (BOLT,))
    save(column_end(), "distillation_column_end", 122, keep=(BOLT,))
    save(condenser_side(), "condenser_side", 131, keep=(BOLT,))
    save(condenser_top(), "condenser_top", 132)
    save(column_end(), "condenser_bottom", 133, keep=(BOLT,))
    save(crusher_side(), "crusher_side", 141, keep=HAZARD)
    save(crusher_top(), "crusher_top", 142)
    save(sifter_side(), "sifter_side", 151)
    save(sifter_top(), "sifter_top", 152)
    save(sifter_bottom(), "sifter_bottom", 153)
    save(water_pump_side(), "water_pump_side", 161, keep=glass + WATER)
    save(water_pump_top(), "water_pump_top", 162)
    save(water_pump_bottom(), "water_pump_bottom", 163)
    save(stone_maker_side(), "stone_maker_side", 171, keep=LAVA + WATER)
    save(stone_maker_top(), "stone_maker_top", 172)
    save(printing_press_side(), "printing_press_side", 181, keep=PAPER)
    save(printing_press_top(), "printing_press_top", 182)
    save(printing_press_roller(), "printing_press_roller", 183)
    save(machine_chassis(), "machine_chassis", 191)
    save(compressor_side(), "compressor_side", 192, keep=(BOLT,))
    save(compressor_top(), "compressor_top", 193, keep=(BOLT,))
    save(recycler_side(), "recycler_side", 194, keep=SCRAP_BITS)
    save(recycler_top_side(), "recycler_top_side", 195, keep=HAZARD + (BOLT,))
    save(recycler_top(), "recycler_top", 196)
    save(brewer_side(), "spawn_egg_brewer_side", 201, keep=EGG + (GLASS_EDGE,))
    save(brewer_top(), "spawn_egg_brewer_top", 202)
    save(copper_tank_side(), "copper_tank_side", 211, keep=(GLASS_DK,))
    save(copper_tank_top(), "copper_tank_top", 212)
    for name, (surface, body) in LINE_GAUGES.items():
        line_gauge(surface, body, EMPTY_GLASS).save(BLOCK / f"copper_tank_gauge_{name}.png")
    sight_glass_glass().save(BLOCK / "sight_glass_glass.png")
    brass_fitting().save(BLOCK / "brass_fitting.png")  # exact: turned brass is smooth
    save(valve_handle(), "valve_handle", 214)
    mercury_gauge().save(BLOCK / "mercury_tank_gauge.png")  # exact: a gauge is read, not textured
    experience_gauge().save(BLOCK / "experience_tank_gauge.png")
    lamp_overlay().save(BLOCK / "machine_lamp.png")  # exact: a lamp is lit, not textured
    print("wrote the machine faces")


if __name__ == "__main__":
    main()
