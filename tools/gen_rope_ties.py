"""Rope and Thin Rope: the piece that reaches up to whatever they hang from.

A rope hangs from the block above it. When that block's underside is not at the bottom of its space - a support's
beam (11 px up), a bracket's strut (4 px), a top slab (8 px) - the rope's TIE state holds how far up it has to reach,
and one of these models draws that length of rope (and, for the Thin Rope, its iron fitting moved up to meet the
underside). Writes the tie models and both blockstates. Run from the repo root:  python tools/gen_rope_ties.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODELS = ROOT / "src/main/resources/assets/nookworks/models/block"
STATES = ROOT / "src/main/resources/assets/nookworks/blockstates"
MAX_TIE = 15


def write(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def rope_tie(n):
    # The rope's own texture runs 16 px down; the reach continues its twist from the top (v counts down from 16).
    v0 = 16 - n
    side = {"uv": [6, v0, 9, 16], "texture": "#rope"}
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": "nookworks:block/rope", "rope": "nookworks:block/rope"},
        "elements": [{
            "from": [6.5, 16, 6.5], "to": [9.5, 16 + n, 9.5],
            "faces": {"north": side, "east": side, "south": side, "west": side,
                      "up": {"uv": [6, 6, 9, 9], "texture": "#rope"}},
        }],
    }


def thin_fitting(n):
    """The cord up to the underside and the fitting under it: thin_rope_anchored's top parts, raised n px."""
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": "nookworks:block/thin_rope", "rope": "nookworks:block/thin_rope"},
        "elements": ([{
            "from": [7.5, 16, 7.5], "to": [8.5, 16 + n - 1.5, 8.5],
            "faces": {s: {"texture": "#rope", "uv": [0, 0, 1, max(1, n - 1.5)]} for s in ("north", "east", "south", "west")},
        }] if n > 1.5 else []) + [
            {"from": [6, 15 + n, 6], "to": [10, 16 + n, 10],
             "faces": {**{s: {"texture": "#rope", "uv": [4, 0, 8, 1]} for s in ("north", "east", "south", "west")},
                       "down": {"texture": "#rope", "uv": [4, 0, 8, 4]}, "up": {"texture": "#rope", "uv": [4, 0, 8, 4]}}},
            {"from": [7, 13.5 + n, 7], "to": [9, 15 + n, 9],
             "faces": {**{s: {"texture": "#rope", "uv": [9, 2, 11, 3.5]} for s in ("north", "east", "south", "west")},
                       "down": {"texture": "#rope", "uv": [9, 2, 11, 4]}}},
        ],
    }


def main():
    for n in range(1, MAX_TIE + 1):
        write(MODELS / f"rope_tie_{n}.json", rope_tie(n))
        write(MODELS / f"thin_rope_fitting_{n}.json", thin_fitting(n))
    write(STATES / "rope.json", {"multipart": [{"apply": {"model": "nookworks:block/rope"}}] + [
        {"when": {"tie": str(n)}, "apply": {"model": f"nookworks:block/rope_tie_{n}"}} for n in range(1, MAX_TIE + 1)]})
    write(STATES / "thin_rope.json", {"multipart": [
        {"when": {"anchored": "false"}, "apply": {"model": "nookworks:block/thin_rope"}},
        {"when": {"anchored": "true", "tie": "0"}, "apply": {"model": "nookworks:block/thin_rope_anchored"}},
    ] + [
        {"when": {"anchored": "true", "tie": str(n)}, "apply": {"model": "nookworks:block/thin_rope"}} for n in range(1, MAX_TIE + 1)
    ] + [
        {"when": {"anchored": "true", "tie": str(n)}, "apply": {"model": f"nookworks:block/thin_rope_fitting_{n}"}}
        for n in range(1, MAX_TIE + 1)
    ]})


if __name__ == "__main__":
    main()
