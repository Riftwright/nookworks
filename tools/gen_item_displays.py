"""How the low and small blocks look held, on the ground and in an item frame.

A block item without display transforms of its own takes vanilla's block/block ones, which are made for a full cube:
a model 1 to 11 pixels high standing on the block's floor (the Absorption Pad, the Dice, the Iron Pot...) is then held
low in the corner of the screen, mostly out of sight, and sits in the bottom of an item frame. Vanilla does the same
for its carpets and pressure plates with block/thin_block, which lifts them in first person.

This works each such model's transforms out of its size, the way block/thin_block is set up: the model's middle is
brought to where a full block's would be (in first person a little higher still, as vanilla lifts a carpet), and a
small one is held a little bigger - up to half as big again - so a die in the hand is not a speck. The gui transform,
which the models set themselves where they need one, is left alone, as is any model with first-person transforms of
its own (the Sconce, the Filament Bulb).

A pipe or a duct drawn as a straight run north to south - a rod along z - is turned side on in an item frame, which
shows a model from the south: there it was only its end, a small square (the Experience Pipe a dot).

It writes into the models the item definitions point at, which other generators write too: run it after them.
AssetAudit fails when a low model has lost its transforms. Run from the repo root:  python tools/gen_item_displays.py
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/nookworks"

# vanilla's block/block, which the rest is worked from
FIRST = {"rotation": [0, 45, 0], "scale": 0.4}
THIRD = {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": 0.375}
GROUND = {"translation": [0, 3, 0], "scale": 0.25}
FIXED = {"scale": 0.5}
LIFT = 1.4  # how much further than the middle vanilla lifts a thin block in first person (4.2 for a carpet's 3.0)
# items whose models carry transforms set by hand, left as they are
HAND_SET = {"sconce", "filament_bulb"}
# models whose own generator sets how they are held in first person (the rest worked out here): the Bookend's L
HELD_OWN = {"bookend"}
# long along z but no pipe: the Drive Wheel's axle runs the block's length, its wheel faces south (r21: a frame showed
# it edge-on, as a cross)
NOT_RODS = {"drive_wheel"}


def held(rotation, translation, scale):
    """The same view for both hands (a left hand mirrors the right's numbers itself)."""
    view = {"rotation": rotation, "translation": translation, "scale": [scale] * 3}
    return {"firstperson_righthand": view, "firstperson_lefthand": view}


def third(rotation, translation, scale):
    view = {"rotation": rotation, "translation": translation, "scale": [scale] * 3}
    return {"thirdperson_righthand": view, "thirdperson_lefthand": view}


# Views set by hand, for models whose shape the rules above do not fit (the r21 review's shots, 2026-11-06): merged
# into the model's own display last. Paths under models/.
TUNED = {
    # blocks smaller or lower than a cube: lifted into sight in the hand, as vanilla lifts a carpet
    "block/drive_wheel": {**held([0, 20, 0], [0, 3, 0], 0.4),  # its face to the eye: at 45 it was edge-on
                          "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]}},
    "block/spawner_controller_on": {**held([0, 45, 0], [0, 3.5, 0], 0.4),
                                    "fixed": {"rotation": [-30, 0, 0], "translation": [0, 1, 0], "scale": [0.5, 0.5, 0.5]}},
    "block/hanging_planter_green": held([0, 45, 0], [0, 3, 0], 0.35),
    "block/hanging_planter_pink": held([0, 45, 0], [0, 3, 0], 0.35),
    "block/chimney_top": held([10, 45, 0], [0, 3, 0], 0.4),
    "block/cobblestone_chimney_top": held([10, 45, 0], [0, 3, 0], 0.4),
    "block/nether_brick_chimney_top": held([10, 45, 0], [0, 3, 0], 0.4),
    "block/stone_brick_chimney_top": held([10, 45, 0], [0, 3, 0], 0.4),
    # thin runs held raised and turned along the arm, as nookTechnology's Storage Cable is: the Energy Cable runs along
    # x, the pipes and the duct along z
    "block/energy_cable_inventory": held([10, 100, 0], [0, 3, 0], 0.4),
    "block/duct_item": held([10, 10, 0], [0, 3, 0], 0.4),
    "block/experience_pipe_inventory": held([10, 10, 0], [0, 3, 0], 0.4),
    "block/mercury_pipe_inventory": held([10, 10, 0], [0, 3, 0], 0.4),
    # the Filament Bulb: its glass in sight in first person, upright in the fist in third
    "block/filament_bulb": {**held([0, 45, 0], [0, 4, 0], 0.6), **third([75, 45, 0], [0, 2.3, -0.6], 0.6)},
    "block/filament_bulb_off": {**held([0, 45, 0], [0, 4, 0], 0.6), **third([75, 45, 0], [0, 2.3, -0.6], 0.6)},
    # the Warp Anchor: its core and ring bigger, and tilted in a frame so the ring reads as a ring
    "item/warp_anchor": {**held([0, 45, 0], [0, 3, 0], 0.5),
                         "gui": {"rotation": [40, 225, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
                         "fixed": {"rotation": [-25, 0, 0], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]}},
    # the Grill in a frame: its grate turned out, not seen edge-on
    "block/grill_standing": {"fixed": {"rotation": [-60, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]}},
}


def model_ref(node):
    """The first model an item definition shows."""
    if isinstance(node, dict):
        if node.get("type") in ("minecraft:model", "model") and "model" in node:
            return node["model"]
        for value in node.values():
            found = model_ref(value)
            if found:
                return found
    elif isinstance(node, list):
        for value in node:
            found = model_ref(value)
            if found:
                return found
    return None


def model_path(ref):
    namespace, path = ref.split(":") if ":" in ref else ("minecraft", ref)
    return ASSETS / "models" / (path + ".json") if namespace == "nookworks" else None


def elements_of(ref):
    """The model's elements, from it or the nearest parent of ours that has them."""
    while ref:
        path = model_path(ref)
        if path is None or not path.exists():
            return None
        model = json.loads(path.read_text(encoding="utf-8"))
        if "elements" in model:
            return model["elements"]
        ref = model.get("parent")
    return None


def low(elements):
    """Whether the model stands on the floor and is small: what block/block's transforms show badly."""
    lo = [min(e["from"][i] for e in elements) for i in range(3)]
    hi = [max(e["to"][i] for e in elements) for i in range(3)]
    height = hi[1] - lo[1]
    across = max(hi[0] - lo[0], hi[2] - lo[2])
    return lo[1] <= 1 and (height < 12 or across < 10), lo, hi


def rod_along_z(elements):
    """Whether the model is a straight run along z, a block long and thin across: a pipe, a duct."""
    lo = [min(e["from"][i] for e in elements) for i in range(3)]
    hi = [max(e["to"][i] for e in elements) for i in range(3)]
    return hi[2] - lo[2] >= 15.9 and hi[0] - lo[0] <= 12 and hi[1] - lo[1] <= 12


def round2(v):
    return [round(x, 2) + 0.0 for x in v]


def displays(lo, hi):
    """The transforms for a model spanning lo..hi (pixels)."""
    middle = (lo[1] + hi[1]) / 2
    drop = 8 - middle  # how far below a full block's middle this model's is
    size = max(hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2])
    # in the hand only what is smaller than a stool grows (r21: a stool was held as big as a desk); on the ground and
    # in a frame, seen from further off, a small one grows more (a mug was a speck there)
    grow = min(1.5, max(1.0, 10 / size))
    seen = min(2.5, max(1.0, 12 / size))
    framed = min(2.0, max(1.0, 12 / size))
    flat = hi[1] - lo[1] <= 4  # a plate, a tray, a pad: in a frame its top is turned out, not its edge
    first = FIRST["scale"] * grow
    third = THIRD["scale"] * grow
    tilt = math.radians(THIRD["rotation"][0])
    out = {
        "firstperson_righthand": {"rotation": FIRST["rotation"], "translation": round2([0, drop * first * LIFT, 0]),
                                  "scale": round2([first] * 3)},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": round2([0, drop * first * LIFT, 0]),
                                 "scale": round2([first] * 3)},
        # held at the tilt: the model's middle, lifted, would swing out along the arm; it is brought back into the hand
        "thirdperson_righthand": {"rotation": THIRD["rotation"],
                                  "translation": round2([0, THIRD["translation"][1] + drop * third * math.cos(tilt),
                                                         drop * third * math.sin(tilt)]),
                                  "scale": round2([third] * 3)},
        # on the ground an item stands on its bottom, so only a small one is made bigger, not lifted
        "ground": {"rotation": [0, 0, 0], "translation": round2([0, max(1.0, GROUND["translation"][1] - (seen - 1) * 2), 0]),
                   "scale": round2([GROUND["scale"] * seen] * 3)},
        # turned top out, the model's middle comes out of the frame by as much as it sat below the block's: brought back
        "fixed": {"rotation": [-90, 180, 0], "translation": round2([0, 0, -drop * FIXED["scale"] * framed]),
                  "scale": round2([FIXED["scale"] * framed] * 3)} if flat
        else {"rotation": [0, 0, 0], "translation": round2([0, drop * FIXED["scale"] * framed, 0]),
              "scale": round2([FIXED["scale"] * framed] * 3)},
    }
    return out


GUI = {"rotation": [30, 225, 0], "scale": 0.625}  # vanilla's block/block
GUI_SPECK = 10.0  # a model drawn smaller than this many of the slot's 16 pixels across is a speck in the inventory
GUI_TARGET = 12.0  # what such a one is made up to
GUI_GROW = 2.4  # at most: a mug stays smaller than a block


def gui_for(elements):
    """For a model drawn as a speck by block/block's gui view (a Mug, a Wine Glass, a Plate, a Die): one scaled up to about
    twelve pixels across and brought to the middle of the slot (r21, 2026-11-06: they sat in the slot's bottom, a few
    pixels big). None when the model already reads at its size."""
    rx, ry = (math.radians(a) for a in GUI["rotation"][:2])

    def view(p):
        # ItemTransform turns X then Y (rotationXYZ: v' = Rx(Ry(v))), about the block's middle
        x, y, z = (c - 8 for c in p)
        x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
        y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
        return x, y

    corners = [view((e["from"][0] if i & 1 == 0 else e["to"][0], e["from"][1] if i & 2 == 0 else e["to"][1],
                     e["from"][2] if i & 4 == 0 else e["to"][2])) for e in elements for i in range(8)]
    xs, ys = [c[0] for c in corners], [c[1] for c in corners]
    across = max(max(xs) - min(xs), max(ys) - min(ys)) * GUI["scale"]
    if across >= GUI_SPECK:
        return None
    grow = min(GUI_GROW, GUI_TARGET / across)
    scale = GUI["scale"] * grow
    middle = ((max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2)
    return {"rotation": GUI["rotation"], "translation": round2([-middle[0] * scale, -middle[1] * scale, 0]),
            "scale": round2([scale] * 3)}


def main():
    written = []
    for item in sorted((ASSETS / "items").glob("*.json")):
        if item.stem in HAND_SET:
            continue
        ref = model_ref(json.loads(item.read_text(encoding="utf-8"))["model"])
        path = model_path(ref) if ref else None
        if path is None or not path.exists():
            continue
        model = json.loads(path.read_text(encoding="utf-8"))
        own = model.get("display", {})
        elements = elements_of(ref)
        if not elements:
            continue
        is_low, lo, hi = low(elements)
        if is_low:
            display = dict(own)
            display.update(displays(lo, hi))
            gui = None if "gui" in own else gui_for(elements)
            if gui:
                display["gui"] = gui
            if item.stem in HELD_OWN:
                display.update({k: v for k, v in own.items() if k.startswith("firstperson")})
        elif rod_along_z(elements) and item.stem not in NOT_RODS:
            display = dict(own)
            display["fixed"] = {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": round2([FIXED["scale"]] * 3)}
        else:
            continue
        model["display"] = {k: display[k] for k in sorted(display)}
        path.write_bytes((json.dumps(model, indent=2) + "\n").encode("utf-8"))
        written.append(item.stem)
    for ref, views in TUNED.items():
        path = ASSETS / "models" / (ref + ".json")
        model = json.loads(path.read_text(encoding="utf-8"))
        display = dict(model.get("display", {}))
        display.update(views)
        model["display"] = {k: display[k] for k in sorted(display)}
        path.write_bytes((json.dumps(model, indent=2) + "\n").encode("utf-8"))
    print("item transforms for: " + ", ".join(written) + "; tuned by hand: " + ", ".join(TUNED))


if __name__ == "__main__":
    main()
