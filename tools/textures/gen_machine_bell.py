"""The Machine Bell: a small electric alarm bell fitted beside a machine, that rings when the machine stops and gives a
redstone signal while it stays stopped (the user, 2026-11-10: 「できそうなことはぜんぶやっていいよ」, of the sound session's
survey). Its own picture, drawn here (no vanilla pixels): a red enamelled gong on a black iron plate, the coil's housing
under it and a brass hammer at its rim - the black iron and brass of our pipes and fittings.

The play-test after 1.5 (the user, 2026-11-12): 「機械に取り付けると微妙に隙間ができるのが結構気になる」, 「奥まっている部分だけ
でなく、高さが低めの機械や形が特殊な機械にもちゃんと美しく張り付くように」, 「ベルの色がすこし派手すぎるから、もう少し彩度を
抑えて」, and its icon redrawn. So:
- **The mount.** A machine's side is a panel set in from its frame, and some are lower or shaped (a Crusher's neck, a
  Sifter's legs, a Spawn Egg Brewer's flask). For every block of ours, this script reads its models as its blockstate
  turns them and measures, for each face, how deep the surface lies under the bell's plate (`survey`). It picks how far
  to move the bell up or down the face (`shift`, -3 to 3 px, kept as 0-6 in its state: down on a lower machine whose
  panel ends under the bell's top - a Diesel Generator, a Printing Press, a Nukadoko - up on a Sifter, open between its
  legs below) and how deep a black iron backing pad behind the plate has to reach to meet the surface (`inset`, 0-7 px). The pad goes into the
  neighbour's cell; where the surface is nearer, the neighbour's own faces hide what of it is inside them. The table is
  data/nookworks/bell_mounts/nookworks.json (read by `BellMounts`), the models one bell for each shift and one pad for
  each inset and shift. A block not in the table is flat (a wall). An add-on lists its own machines the same way:
  `--mod <its src/main/resources> <its namespace>` writes data/<namespace>/bell_mounts/<namespace>.json there.
- **The colours** a step less saturated: an enamel red nearer a signal-box red than a toy's.
- **The signal switch** (「機械からRS信号出ちゃうのは、ちょっと心配」): a small pilot lamp on the housing shows when the bell
  gives its signal (`signal=true`).

- textures/block/machine_bell_enamel.png   the gong's red enamel, finished by texkit
- textures/item/machine_bell.png            the item
- models/block/machine_bell_*.json, models/item/machine_bell.json, items/machine_bell.json, blockstates/machine_bell.json
- data/nookworks/bell_mounts/nookworks.json each block's mount, by its facing and the face the bell is on
- data: the loot table, the recipe

Run from the repo root:  python tools/textures/gen_machine_bell.py [--preview OUT.png | --mod RESOURCES NAMESPACE]
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image

import itemkit
import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
DATA = ROOT / "src/main/resources/data/nookworks"
TABLE = DATA / "bell_mounts/nookworks.json"

# a red a step from a toy's: saturation 0.69 where it was 0.80 (172, 36, 34), a little darker
RED_LIT, RED, RED_DK = (198, 96, 82), (162, 58, 50), (112, 38, 36)
TURN = {"north": 0, "east": 90, "south": 180, "west": 270}
MAX_INSET = 7
MAX_SHIFT = 3


def enamel():
    """The gong's enamel: a red with a sheen across its upper left, deeper toward its rim below and right."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            t = (x + y) / 30.0
            c = RED_LIT if t < 0.3 else RED if t < 0.75 else RED_DK
            img.putpixel((x, y), c + (255,))
    return texkit.finish(img, 4711, **texkit.SMOOTH)


def box(frm, to, tex, **extra):
    """A box of one material, its faces taking the texture where they lie (as a block's do) - or, for a box reaching out of
    the block (a pad into the next), each face a patch of the texture its own size from the corner, as the faces' own
    places would fall off the texture."""
    sides = ("north", "south", "east", "west", "up", "down")
    if all(0 <= c <= 16 for c in (*frm, *to)):
        return {"from": frm, "to": to, "faces": {side: {"texture": tex} for side in sides}, **extra}
    dx, dy, dz = (min(16, abs(to[i] - frm[i])) for i in range(3))
    size = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}
    return {"from": frm, "to": to,
            "faces": {side: {"texture": tex, "uv": [0, 0, size[side][0], size[side][1]]} for side in sides}, **extra}


# On a wall, pointing north, its plate on the wall to the south: the plate, the coil's housing low on it, the gong in
# three steps out from the plate with a boss at its middle, and the hammer's arm up from the housing to the gong's rim.
WALL = [
    ([5, 3, 15], [11, 13, 16], "#iron"),        # the plate
    ([6, 3.5, 13.5], [10, 5.5, 15], "#iron"),   # the coil's housing
    ([5, 6.5, 14], [11, 12.5, 15], "#enamel"),  # the gong's rim
    ([5.5, 7, 13], [10.5, 12, 14], "#enamel"),
    ([6.5, 8, 12], [9.5, 11, 13], "#enamel"),
    ([7.5, 9, 11.5], [8.5, 10, 12], "#brass"),  # the boss
    ([7.5, 5.5, 12.5], [8.5, 7, 13.5], "#brass"),  # the hammer's arm
    ([7, 6.5, 12], [9, 7.5, 13], "#brass"),     # its head, at the rim
]
# the pilot lamp, on the housing's face right of the hammer, lit while the bell gives its signal
LAMP = ([8.75, 4, 13.25], [9.5, 4.75, 13.5], "#lamp")
# the backing pad behind the plate, a quarter pixel in from its edges (no face of it in a plane with a neighbour's),
# from the plate's back into the neighbour's cell as deep as its surface lies
PAD_X = (5.25, 10.75)
PAD_Y = (3.25, 12.75)


def shifted(boxes, up):
    return [([f[0], f[1] + up, f[2]], [t[0], t[1] + up, t[2]], tex) for f, t, tex in boxes]


def ceiling_of(frm, to):
    """The wall's box hung under a ceiling: its plate up, the gong facing down, what was up on the wall to the north."""
    ys = (frm[2], to[2])
    zs = (16 - frm[1], 16 - to[1])
    return [frm[0], min(ys), min(zs)], [to[0], max(ys), max(zs)]


TEXTURES = {"iron": "nookworks:block/black_iron", "enamel": "nookworks:block/machine_bell_enamel",
            "brass": "nookworks:block/brass_fitting", "particle": "nookworks:block/machine_bell_enamel"}


def model(boxes, textures=None, lit=()):
    els = []
    for f, t, tex in boxes:
        extra = {"light_emission": 12} if tex in lit else {}
        els.append(box(f, t, tex, **extra))
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": textures or TEXTURES, "elements": els}


def pad_wall(inset, up):
    return [([PAD_X[0], PAD_Y[0] + up, 16], [PAD_X[1], PAD_Y[1] + up, 16 + inset], "#iron")]


def pad_ceiling(inset):
    # the wall's pad turned up, as ceiling_of turns the bell: under a ceiling the plate is up, so the pad goes up
    return [([PAD_X[0], 16, 16 - PAD_Y[1]], [PAD_X[1], 16 + inset, 16 - PAD_Y[0]], "#iron")]


def lamp_model(boxes):
    return model(boxes, {"iron": "nookworks:block/black_iron", "lamp": "minecraft:block/redstone_block",
                         "particle": "nookworks:block/black_iron"}, lit=("#lamp",))


def states():
    parts = []
    for face in ("wall", "ceiling", "floor"):
        for facing, turn in TURN.items():
            # on a floor, the ceiling's bell turned over
            rot = {"x": 180, "y": (turn + 180) % 360} if face == "floor" else {"y": turn} if turn else {}
            if face == "wall":
                for up in range(-MAX_SHIFT, MAX_SHIFT + 1):
                    when = {"face": face, "facing": facing, "shift": str(up + MAX_SHIFT)}
                    parts.append({"when": when, "apply": {"model": f"nookworks:block/machine_bell_wall{suffix(up)}", **rot}})
                    parts.append({"when": {**when, "signal": "true"},
                                  "apply": {"model": f"nookworks:block/machine_bell_wall_lamp{suffix(up)}", **rot}})
                    for inset in range(1, MAX_INSET + 1):
                        parts.append({"when": {**when, "inset": str(inset)},
                                      "apply": {"model": f"nookworks:block/machine_bell_pad_wall_{inset}{suffix(up)}", **rot}})
            else:
                when = {"face": face, "facing": facing}
                parts.append({"when": when, "apply": {"model": "nookworks:block/machine_bell_ceiling", **rot}})
                parts.append({"when": {**when, "signal": "true"}, "apply": {"model": "nookworks:block/machine_bell_ceiling_lamp", **rot}})
                for inset in range(1, MAX_INSET + 1):
                    parts.append({"when": {**when, "inset": str(inset)},
                                  "apply": {"model": f"nookworks:block/machine_bell_pad_ceiling_{inset}", **rot}})
    return {"multipart": parts}


def suffix(up):
    return f"_up{up}" if up > 0 else f"_down{-up}" if up < 0 else ""


# ---- the survey: how deep each block's surface lies under the bell's plate ----

SURVEYED = {"assets": ASSETS, "ns": "nookworks"}


def load_model(ref):
    ns, path = ref.split(":") if ":" in ref else ("minecraft", ref)
    if ns != SURVEYED["ns"]:
        return None
    f = SURVEYED["assets"] / "models" / (path + ".json")
    if not f.exists():
        return None
    data = json.loads(f.read_text(encoding="utf-8"))
    if "elements" in data:
        return data["elements"]
    return load_model(data["parent"]) if "parent" in data else None


def turn_box(frm, to, y):
    """A box turned about the block's middle as a blockstate's `y` turns its model (clockwise seen from above)."""
    pts = []
    for x0 in (frm[0], to[0]):
        for z0 in (frm[2], to[2]):
            x, z = x0, z0
            for _ in range((y // 90) % 4):
                x, z = 16 - z, x
            pts.append((x, z))
    xs, zs = [p[0] for p in pts], [p[1] for p in pts]
    return [min(xs), frm[1], min(zs)], [max(xs), to[1], max(zs)]


OFF = {"false", "none", "0", "single", "bottom", "lower", "off"}


def applies(when, facing):
    """Whether a multipart part's condition holds for a state facing `facing` (None: no facing) with everything else at
    rest (lit, running, powered false): the body a bell would be fitted to."""
    if not when:
        return True
    if "OR" in when or "AND" in when:
        return False
    for key, value in when.items():
        values = str(value).split("|")
        if key == "facing":
            if facing is None or facing not in values:
                return False
        elif not any(v in OFF for v in values):
            return False
    return True


def bodies(blockstate):
    """{facing or None: [(elements, y)]} - the models a block shows, as turned, by its facing."""
    out = {}
    if "variants" in blockstate:
        for key, v in blockstate["variants"].items():
            v = v[0] if isinstance(v, list) else v
            props = dict(p.split("=") for p in key.split(",") if "=" in p)
            facing = props.get("facing")
            if any(k != "facing" and val not in OFF for k, val in props.items()):
                continue
            els = load_model(v["model"])
            if els:
                out.setdefault(facing if facing in TURN else None, [(els, v.get("y", 0))])
    else:
        facings = set()
        for part in blockstate["multipart"]:
            f = (part.get("when") or {}).get("facing")
            if f:
                facings.update(x for x in str(f).split("|") if x in TURN)
        for facing in sorted(facings) or [None]:
            got = []
            for part in blockstate["multipart"]:
                if applies(part.get("when"), facing):
                    a = part["apply"]
                    a = a[0] if isinstance(a, list) else a
                    els = load_model(a["model"])
                    if els and not a.get("x"):
                        got.append((els, a.get("y", 0)))
            if got:
                out[facing] = got
    return out


def surface(boxes, face, u, v):
    """How deep from the block's `face` the first box lies at the face's point (u, v), px: 16 where nothing is."""
    best = 16.0
    for frm, to in boxes:
        x0, y0, z0 = frm
        x1, y1, z1 = to
        if face in ("north", "south"):
            if not (x0 <= u < x1 and y0 <= v < y1):
                continue
            d = z0 if face == "north" else 16 - z1
        elif face in ("west", "east"):
            if not (z0 <= u < z1 and y0 <= v < y1):
                continue
            d = x0 if face == "west" else 16 - x1
        else:
            if not (x0 <= u < x1 and z0 <= v < z1):
                continue
            d = 16 - y1 if face == "up" else y0
        best = min(best, max(0.0, d))
    return best


def mount(boxes, face):
    """[inset, up] for a bell on `face`: the least shift (wall faces only; down before up) that has something within the
    pad's reach under all of it, and the inset that reaches the deepest point there - where the surface is nearer, the
    pad goes into the block and its faces hide it. Flat ground, or nothing to reach at any shift (an open frame): [0, 0]."""
    if face in ("up", "down"):
        depths = [surface(boxes, face, u + 0.5, v + 0.5) for u in range(3, 13) for v in range(3, 13)]
        deepest = max(depths)
        return [0, 0] if deepest <= 0 or deepest > MAX_INSET else [math.ceil(deepest - 1e-6), 0]
    for up in sorted(range(-MAX_SHIFT, MAX_SHIFT + 1), key=lambda s: (abs(s), s > 0)):
        # under the pad (x 5.25-10.75, y 3.25-12.75, moved by the shift)
        depths = [surface(boxes, face, u + 0.5, v + 0.5) for u in range(5, 11) for v in range(3 + up, 13 + up)]
        if max(depths) <= MAX_INSET:
            return [0, 0] if max(depths) <= 0 and up == 0 else [math.ceil(max(depths) - 1e-6), up]
    return [0, 0]


def survey():
    table = {}
    for bs in sorted((SURVEYED["assets"] / "blockstates").glob("*.json")):
        if bs.stem == "machine_bell":
            continue
        by_facing = bodies(json.loads(bs.read_text(encoding="utf-8")))
        entry = {}
        for facing, models in by_facing.items():
            boxes = []
            for els, y in models:
                for e in els:
                    if e.get("rotation") and e["rotation"].get("angle", 0):
                        continue
                    boxes.append(turn_box(e["from"], e["to"], y))
            faces = {face: mount(boxes, face) for face in ("north", "south", "east", "west", "up", "down")}
            faces = {f: m for f, m in faces.items() if m != [0, 0]}
            if faces:
                entry[facing or "none"] = faces
        if entry:
            table[f"{SURVEYED['ns']}:{bs.stem}"] = entry
    return table


# ---- the icon ----

ICON = [
    "......OOOO......",
    "....OOLLLLOO....",
    "...OLHHLLLRRO...",
    "...OHWLLRRRDO...",
    "..OLHLLLLRRRDO..",
    "..OLLRLyBDRRDO..",
    "..OLRRRBbDRDDO..",
    "..ORRRRDDDRDDO..",
    "...ORRRRRRDDO...",
    "...ODRRRRDDDO...",
    "....OODyBDOO....",
    "......OObO......",
    "....KIIIbIIK....",
    "....KiiiiiiK....",
    "....KKKKKKKK....",
    "................",
]
ICON_COLOURS = {
    "O": (86, 28, 28), "D": (122, 40, 36), "R": (168, 62, 52), "L": (204, 100, 86), "H": (234, 156, 136),
    "W": (252, 222, 210),                                         # the gong: its rim, shade, enamel, light, sheen, glint
    "y": (238, 206, 120), "B": (200, 160, 72), "b": (140, 104, 42),  # brass: the boss, the hammer
    "K": (28, 28, 34), "i": (70, 70, 82), "I": (122, 122, 138),      # black iron: the coil's housing on the plate
}


def icon():
    """The bell as a fire-alarm bell is drawn, in vanilla's way with round things (the clock, the compass): a round red gong
    ringed in its own darkest red, its upper left lit broad with a streak of sheen and a glint, its lower right in shade,
    a raised ring round the brass boss at its middle (a gong's ridge, so it reads as a bell and not a ball), the brass hammer up out of the coil's black iron housing under it, struck against its
    rim. Drawn pixel by pixel ({@code ICON}); the shape's own darkest shades are its outline, as vanilla's are."""
    return itemkit.sprite(itemkit.check(ICON), ICON_COLOURS, ring=False)


def preview(out):
    """The icon at 8x beside the one it replaces (from git) and the enamel, for a look before it goes in."""
    import subprocess
    old = None
    try:
        raw = subprocess.run(["git", "show", "HEAD:src/main/resources/assets/nookworks/textures/item/machine_bell.png"],
                             cwd=ROOT, capture_output=True, check=True).stdout
        import io
        old = Image.open(io.BytesIO(raw)).convert("RGBA")
    except Exception:
        pass
    new = icon()
    pics = [p for p in (old, new, enamel()) if p is not None]
    sheet = Image.new("RGBA", (len(pics) * 144 + 16, 160), (139, 139, 139, 255))
    for i, p in enumerate(pics):
        sheet.alpha_composite(p.resize((128, 128), Image.NEAREST), (16 + i * 144, 16))
    sheet.save(out)


def loot():
    return {"type": "minecraft:block", "random_sequence": "nookworks:blocks/machine_bell", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "nookworks:machine_bell"}],
         "condition": {"type": "minecraft:survives_explosion"}}]}


def recipe():
    return {"type": "minecraft:crafting_shaped", "category": "redstone",
            "pattern": [" C ", "CRC", " I "],
            "key": {"C": "minecraft:copper_ingot", "R": "minecraft:redstone", "I": "minecraft:iron_ingot"},
            "result": {"id": "nookworks:machine_bell", "count": 1}}


def write_json(path, data, indent=2):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=indent) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
        sys.exit(0)
    if "--mod" in sys.argv:
        resources, ns = Path(sys.argv[sys.argv.index("--mod") + 1]), sys.argv[sys.argv.index("--mod") + 2]
        SURVEYED.update(assets=resources / "assets" / ns, ns=ns)
        out = resources / "data" / ns / "bell_mounts" / f"{ns}.json"
        write_json(out, survey(), indent=1)
        print(f"wrote {out}")
        sys.exit(0)
    enamel().save(ASSETS / "textures/block/machine_bell_enamel.png")
    icon().save(ASSETS / "textures/item/machine_bell.png")
    models = ASSETS / "models/block"
    for old in models.glob("machine_bell_*.json"):
        old.unlink()  # what the shifts and pads were before
    for up in range(-MAX_SHIFT, MAX_SHIFT + 1):
        write_json(models / f"machine_bell_wall{suffix(up)}.json", model(shifted(WALL, up)))
        write_json(models / f"machine_bell_wall_lamp{suffix(up)}.json", lamp_model(shifted([LAMP], up)))
        for inset in range(1, MAX_INSET + 1):
            write_json(models / f"machine_bell_pad_wall_{inset}{suffix(up)}.json", model(pad_wall(inset, up)))
    write_json(models / "machine_bell_ceiling.json", model([(*ceiling_of(f, t), tex) for f, t, tex in WALL]))
    write_json(models / "machine_bell_ceiling_lamp.json", lamp_model([(*ceiling_of(LAMP[0], LAMP[1]), LAMP[2])]))
    for inset in range(1, MAX_INSET + 1):
        write_json(models / f"machine_bell_pad_ceiling_{inset}.json", model(pad_ceiling(inset)))
    write_json(ASSETS / "models/item/machine_bell.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "nookworks:item/machine_bell"}})
    write_json(ASSETS / "items/machine_bell.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/machine_bell"}})
    write_json(ASSETS / "blockstates/machine_bell.json", states())
    write_json(DATA / "loot_table/blocks/machine_bell.json", loot())
    recipe_file = DATA / "recipe/machine_bell.json"
    kept = json.loads(recipe_file.read_text(encoding="utf-8")).get("neoforge:conditions") if recipe_file.exists() else None
    write_json(recipe_file, {"neoforge:conditions": kept, **recipe()} if kept else recipe())  # the part's condition kept
    table = survey()
    write_json(TABLE, table, indent=1)
    print(f"wrote the machine bell; {len(table)} blocks in the mount table")
