"""Camera (カメラ): the item's icon. All original pixel art, placed pixel by pixel (itemkit). Deterministic.

A film-era camera seen from the front, as the user wanted it (2026-10-11: "フィルム時代の真鍮のずっしりした感じ"): a
brass top plate with the two windows of a rangefinder and the shutter button standing on it, a band of black
leatherette round the body, the lens in brass in the middle - its glass deep blue with the light caught at the upper
left - and a brass bottom plate in shade.

- item/camera.png
and the item models. Run from the repo root:  python tools/textures/gen_camera.py
"""
import json
from pathlib import Path

import itemkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"

COLOURS = {
    # brass, as the ingot has it, deep to shine
    "e": (96, 58, 16), "d": (138, 94, 28), "b": (180, 132, 48), "l": (222, 178, 88), "w": (248, 224, 152),
    # leatherette, black gone a little warm
    "k": (30, 25, 25), "K": (42, 36, 35), "m": (56, 49, 46), "n": (74, 65, 60),
    # glass: the lens deep, the windows lighter, a gleam
    "g": (18, 24, 38), "G": (40, 58, 88), "c": (86, 124, 164), "h": (214, 234, 246),
}

# The lens: a brass ring lit at the upper left, its glass within; leatherette at the corners.
LENS = [
    "mwllbm",
    "lhcGgd",
    "lcGggd",
    "bGgggd",
    "bgggGe",
    "mddeem",
]

# The body, x 1 to 14: the top plate (with its windows), the band round the lens, the bottom plate.
BODY = [
    "wlllllllllllbd",
    "lGhbbbbbbbGcbd",
    "bdddddddddddde",
    "nmmm" + LENS[0] + "mmmK",
    "nmKm" + LENS[1] + "mKmK",
    "nmmm" + LENS[2] + "mmmK",
    "nKmm" + LENS[3] + "mmKK",
    "nmmm" + LENS[4] + "mmmK",
    "mmKm" + LENS[5] + "mKKk",
    "bdddddddddddde",
]

ART = itemkit.check(
    ["." * 16, "." * 16,
     "...........wl...",  # the shutter button, standing on the top plate
     "...........ld..."]
    + ["." + row + "." for row in BODY]
    + ["." * 16, "." * 16])


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def main():
    # the ring worked out as if the windows were brass: by the glass it went navy
    ring_from = dict(COLOURS, **{ch: COLOURS["b"] for ch in "gGch"})
    itemkit.sprite(ART, COLOURS, ring_from=ring_from).save(ASSETS / "textures/item/camera.png")
    write_json(ASSETS / "models/item/camera.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "nookworks:item/camera"}})
    write_json(ASSETS / "items/camera.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/camera"}})
    print("wrote the Camera")


if __name__ == "__main__":
    main()
