#!/usr/bin/env python3
"""Writes the Ponder schematics of the 1.21.1 port's Create part (dev.nookworks.compat.create.client.DrivePonder).

A Create shaft set straight against an Oil Press, and one on a Drive Wheel's axle with a press on its other end. Nothing
of Create's is copied: the schematics name its shaft by id.

    python tools/port1211/gen_create.py
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import nbt  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources"


def ponder_structure(name: str, blocks: dict) -> None:
    """A Ponder scene's schematic: a 5x5 plate of snow and white concrete, as Create's own scenes stand on, and
    {(x, y, z): (block id, {property: value})} on it."""
    palette, entries = [], []

    def state(block, props):
        entry = {"Name": block}
        if props:
            entry["Properties"] = dict(props)
        if entry not in palette:
            palette.append(entry)
        return palette.index(entry)

    for x in range(5):
        for z in range(5):
            entries.append({"pos": nbt.List([nbt.Int(x), nbt.Int(0), nbt.Int(z)]),
                            "state": nbt.Int(state("minecraft:snow_block" if (x + z) % 2 == 0 else "minecraft:white_concrete", None))})
    for (x, y, z), (block, props) in blocks.items():
        entries.append({"pos": nbt.List([nbt.Int(x), nbt.Int(y), nbt.Int(z)]), "state": nbt.Int(state(block, props))})
    path = RES / f"assets/nookworks/ponder/{name}.nbt"
    path.parent.mkdir(parents=True, exist_ok=True)
    nbt.write(path, {"DataVersion": nbt.Int(3955), "size": nbt.List([nbt.Int(5), nbt.Int(3), nbt.Int(5)]),
                     "palette": nbt.List(palette), "blocks": nbt.List(entries), "entities": nbt.List([])})


def ponders() -> None:
    shaft_x = ("create:shaft", {"axis": "x", "waterlogged": "false"})
    # a shaft from the east edge straight into an Oil Press
    ponder_structure("create_drive/machine", {
        (1, 1, 2): ("nookworks:oil_press", {"facing": "south", "drive": "east"}),
        (2, 1, 2): shaft_x, (3, 1, 2): shaft_x, (4, 1, 2): shaft_x})
    # a shaft into a Drive Wheel's axle, an Oil Press on the wheel's other end
    ponder_structure("create_drive/wheel", {
        (1, 1, 2): ("nookworks:oil_press", {"facing": "south", "drive": "east"}),
        (2, 1, 2): ("nookworks:drive_wheel", {"facing": "east", "turning": "true"}),
        (3, 1, 2): shaft_x, (4, 1, 2): shaft_x})
    # a powered Motor with shafts run from its axle
    ponder_structure("create_drive/motor", {
        (1, 1, 2): ("nookworks:motor", {"facing": "east", "powered": "true"}),
        (2, 1, 2): shaft_x, (3, 1, 2): shaft_x, (4, 1, 2): shaft_x})


if __name__ == "__main__":
    ponders()
