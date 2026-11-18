#!/usr/bin/env python3
"""Writes the models of where a Create shaft meets a Nookworks machine or Drive Wheel (the 1.21.1 port's Create part).

Create's shaft is 4 px thick; our machines' axles are 3 px and the Drive Wheel's 2 px, so a shaft run straight into one
ended in a step. The join is a flange coupling, drawn inside our own block on the side the shaft enters, turning with it
(dev.nookworks.compat.create.client.ShaftJoins): a bolted round flange wider than the shaft, a boss behind it, then our
slim axle - in our dark iron with iron bolt heads. (The owner picked it of four looks, 2026-11-15; the others - our axle
as it was, our axle carried in at Create's thickness, a reducing coupling - are gone.)

Each model points north (the shaft's side is z = 0), as the machines' own _axle models do, and stands in for that model
(it carries the rest of it: the Oil Press's collar, the noodle maker's plate). The quern's are upright (from above: the
top is the shaft's side; from below: drawn over the still plate, not instead of it). The wheel's is drawn at each end of
its axle a shaft meets, over its own axle. The Motor's stands in for its own shaft model (its axle at z 0-3).

    python tools/port1211/gen_create_joins.py
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODELS = ROOT / "src/main/resources/assets/nookworks/models"
OUT = MODELS / "compat/create"

TEXTURES = {
    "particle": "nookworks:block/black_iron",
    "iron": "nookworks:block/black_iron",
    "bolt": "minecraft:block/iron_block",
}
FACES = ("north", "south", "east", "west", "up", "down")


def box(frm, to, tex="#iron"):
    return {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to],
            "faces": {face: {"texture": tex} for face in FACES}}


def square_z(half, z0, z1, tex="#iron", c=8.0):
    return box((c - half, c - half, z0), (c + half, c + half, z1), tex)


def square_y(half, y0, y1, tex="#iron", c=8.0):
    return box((c - half, y0, c - half), (c + half, y1, c + half), tex)


def round_z(radius, z0, z1, tex="#iron"):
    """A round plate across the axis (z), drawn the pixel way: a plus and a square between."""
    narrow = radius * 0.6
    inner = radius * 0.8
    return [box((8 - radius, 8 - narrow, z0), (8 + radius, 8 + narrow, z1), tex),
            box((8 - narrow, 8 - radius, z0), (8 + narrow, 8 + radius, z1), tex),
            box((8 - inner, 8 - inner, z0), (8 + inner, 8 + inner, z1), tex)]


def round_y(radius, y0, y1, tex="#iron"):
    narrow = radius * 0.6
    inner = radius * 0.8
    return [box((8 - radius, y0, 8 - narrow), (8 + radius, y1, 8 + narrow), tex),
            box((8 - narrow, y0, 8 - radius), (8 + narrow, y1, 8 + radius), tex),
            box((8 - inner, y0, 8 - inner), (8 + inner, y1, 8 + inner), tex)]


def bolts_z(at, z0, z1):
    """Four bolt heads on a flange, at {at} px from the axis, up, down and to either side."""
    return [box((8 + dx - 0.5, 8 + dy - 0.5, z0), (8 + dx + 0.5, 8 + dy + 0.5, z1), "#bolt")
            for dx, dy in ((at, 0), (-at, 0), (0, at), (0, -at))]


def bolts_y(at, y0, y1):
    return [box((8 + dx - 0.5, y0, 8 + dz - 0.5), (8 + dx + 0.5, y1, 8 + dz + 0.5), "#bolt")
            for dx, dz in ((at, 0), (-at, 0), (0, at), (0, -at))]


def rod_z(half, z0, z1):
    return [square_z(half, z0, z1)] if z1 - z0 > 0.01 else []


def stock(name):
    """A machine's own _axle model: its axle rod (the first element) and the rest."""
    data = json.loads((MODELS / f"block/{name}.json").read_text(encoding="utf-8"))
    return data["elements"][0], data["elements"][1:], data["textures"]


# the side-driven machines and their own axle models
SIDE = {
    "oil_press": "oil_press_axle",
    "rice_polisher": "rice_polisher_axle",
    "noodle_maker": "noodle_maker_axle",
}


def side_join(name):
    rod, rest, textures = stock(SIDE[name])
    end = rod["to"][2]  # where our rod meets the machine
    half = (rod["to"][0] - rod["from"][0]) / 2
    boss_end = min(end, 2.75)
    elements = round_z(4, 0, 1.25)
    elements += bolts_z(3.25, 1.25, 2.0)
    elements.append(square_z(2.5, 1.25, boss_end))
    elements += rod_z(half, boss_end, end)
    elements += rest if name != "rice_polisher" else []  # the polisher's own cross at its box is the flange's place
    tex = dict(textures)
    tex.update(TEXTURES)
    return tex, elements


def wheel_join():
    """At the wheel's north end (its own 2 px axle runs on through to its hub at 6.25)."""
    o = -0.01  # a hair out over the wheel's own axle end, which ends at the block's face too
    return round_z(3.5, o, 1) + bolts_z(2.9, 1, 1.6) + [square_z(2.25, 1, 2.5)]


def motor_join():
    """At the Motor's axle (its own 3 px shaft, z 0-3, pointing north): the flange at its face, a boss, our rod to its housing."""
    return round_z(4, 0, 1.25) + bolts_z(3.25, 1.25, 2.0) + [square_z(2.5, 1.25, 2.5)] + rod_z(1.5, 2.5, 3)


def quern_up_join():
    """From above: the shaft's side is the top (y 16); the quern's beam is at y 12-14 with its spindle through it."""
    return round_y(4, 14.75, 16) + bolts_y(3.25, 14.0, 14.75) + [square_y(2.5, 14.0, 14.75)]


def quern_down_join():
    """From below, over the still 14 x 14 plate at y 0-1 (kept): a hair below it, so the plate's face is not fought over."""
    o = -0.02
    return round_y(4, o, 0.5) + bolts_y(3.25, o - 0.01, 0.3)


def write(name, textures, elements):
    model = {"textures": dict(textures), "elements": elements}
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f"join_{name}.json").write_bytes((json.dumps(model, indent=2) + "\n").encode("utf-8"))


def main():
    for name in SIDE:
        tex, elements = side_join(name)
        write(name, tex, elements)
    write("drive_wheel", TEXTURES, wheel_join())
    write("motor", TEXTURES, motor_join())
    write("hand_quern_up", TEXTURES, quern_up_join())
    write("hand_quern_down", TEXTURES, quern_down_join())
    print(f"{len(SIDE) + 4} models in {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
