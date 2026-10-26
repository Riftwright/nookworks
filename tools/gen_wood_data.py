"""Blockstates, models, item definitions, loot tables, recipes and tags for the wood blocks.

Two families:

- Dawnwood, a whole wood set. Its JSON has exactly the shape of a vanilla wood's, so it is written by taking the
  cherry set out of the game jar (JSON only, never a texture) and renaming it - signs, hanging signs, the shelf and
  the boats with the rest - and the vanilla tags that list a wood's pieces are given Dawnwood's.
- Supports, one per wood (every vanilla wood and Dawnwood), drawn with that wood's stripped log textures by name.
- Chairs, stools and desks, one of each per wood, on the template models block/template_chair, template_stool and
  template_desk (a desk's blockstate is built of its parts, so desks side by side join into one long desk): the seat
  and the top in that wood's planks, the legs and the back's posts in its stripped log.
- Benches, one per wood, on block/template_bench and its parts (tools/textures/gen_bench.py): the seat in that wood's
  planks, the legs and rails in its stripped log; the blockstate leaves an end's legs out where the bench is joined to
  another on that side (BenchBlock), and runs the rail on instead. Made of three of the wood's slabs over two sticks.
- Wall shelves, one per wood: that wood's planks on block/template_wall_shelf_board, and the black iron brackets the
  same for all (tools/textures/gen_wall_shelf.py); the blockstate leaves a bracket out where the shelf is joined to
  another on that side (WallShelfBlock). Made of the wood's trapdoor and two iron nuggets.

Run from the repo root after a Gradle build has unpacked the game jar:  python tools/gen_wood_data.py
"""
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/nookworks"
DATA = RES / "data/nookworks"
# the game jar of the version gradle.properties builds against: a build/ that has known an older one keeps it too,
# and the first found was the old one (26.2's beside 26.3's, 2026-10-12)
NEO_VERSION = re.search(r"^neo_version=(\S+)", (ROOT / "gradle.properties").read_text(encoding="utf-8"), re.M).group(1)
JAR = ROOT / f"build/moddev/artifacts/minecraft-patched-{NEO_VERSION}.jar"

SKIPPED = ()



def write_kept(path, text):
    """Written in the file's own line endings (the repo holds both; a new file gets LF): run on another OS, nothing changes."""
    newline = "\r\n" if path.exists() and b"\r\n" in path.read_bytes() else "\n"
    path.write_text(text, encoding="utf-8", newline=newline)

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    write_kept(path, json.dumps(data, indent=2) + "\n")


# ---------------------------------------------------------------- Dawnwood

def rename(text):
    text = text.replace('"minecraft:cherry_sapling"', '"nookworks:rare_seed"')  # the item; the block's texture keeps its name
    text = text.replace("#minecraft:cherry_logs", "#nookworks:dawnwood_logs")
    text = re.sub(r"minecraft:(block/|item/|blocks/)?(stripped_|potted_)?cherry_", r"nookworks:\1\2dawnwood_", text)
    return text


def rename_path(name):
    return re.sub(r"(stripped_|potted_)?cherry_", r"\1dawnwood_", name)


def wanted(name):
    stem = name.rsplit("/", 1)[-1].removesuffix(".json")
    if "cherry" not in stem:
        return False
    for skip in SKIPPED:
        if re.search(rf"cherry_{skip}(_|$)", stem) or stem.endswith("_" + skip):
            return False
    return True


def dawnwood():
    written = 0
    with zipfile.ZipFile(JAR) as jar:
        for name in jar.namelist():
            if not name.endswith(".json") or not wanted(name):
                continue
            if name.startswith("assets/minecraft/"):
                rel = name.removeprefix("assets/minecraft/")
                if not rel.startswith(("blockstates/", "models/", "items/")):
                    continue
                out = ASSETS / rename_path(rel)
            elif name.startswith("data/minecraft/"):
                rel = name.removeprefix("data/minecraft/")
                if not rel.startswith(("recipe/", "loot_table/blocks/", "advancement/recipes/")):
                    continue
                out = DATA / rename_path(rel)
            else:
                continue
            if out.stem in ("dawnwood_boat", "dawnwood_chest_boat") and out.parent.name in ("items", "item"):
                continue  # the boats' items are vanilla's birch boats tinted, written by textures/gen_dawnwood_extras.py
            text = rename(jar.read(name).decode("utf-8"))
            if out.name == "dawnwood_sapling.json" and "/items/" in out.as_posix():
                out = out.with_name("rare_seed.json")  # the sapling's item is the seed
                text = text.replace("nookworks:item/dawnwood_sapling", "nookworks:item/rare_seed")
            if out.name == "dawnwood_sapling.json" and "models/item" in out.as_posix():
                out = out.with_name("rare_seed.json")
                text = text.replace("nookworks:block/dawnwood_sapling", "nookworks:item/rare_seed")
            if out.parent.name == "recipe":
                text = text.replace('"minecraft:stick"', '"#c:rods/wooden"')  # any mod's sticks, as our other recipes take
            write(out, json.loads(text))
            written += 1
    write(DATA / "tags/item/dawnwood_logs.json", {"values": [f"nookworks:{n}" for n in LOGS]})
    write(DATA / "tags/block/dawnwood_logs.json", {"values": [f"nookworks:{n}" for n in LOGS]})
    write(DATA / "worldgen/feature/dawnwood.json", tree())
    for kind, values in VANILLA_TAGS.items():
        write(RES / f"data/minecraft/tags/{kind}.json", {"replace": False, "values": [f"nookworks:{v}" for v in values]})
    return written


# The vanilla tags a wood's pieces are listed in (the doors, fences and the rest were written by hand before these).
VANILLA_TAGS = {
    "block/standing_signs": ["dawnwood_sign"],
    "block/wall_signs": ["dawnwood_wall_sign"],
    "block/ceiling_hanging_signs": ["dawnwood_hanging_sign"],
    "block/wall_hanging_signs": ["dawnwood_wall_hanging_sign"],
    "block/wooden_shelves": ["dawnwood_shelf"],
    "item/signs": ["dawnwood_sign"],
    "item/hanging_signs": ["dawnwood_hanging_sign"],
    "item/wooden_shelves": ["dawnwood_shelf"],
    "item/boats": ["dawnwood_boat"],
    "item/chest_boats": ["dawnwood_chest_boat"],
    "entity_type/boat": ["dawnwood_boat"],
}


LOGS = ("dawnwood_log", "dawnwood_wood", "stripped_dawnwood_log", "stripped_dawnwood_wood")


def tree():
    """A tall, narrow conifer: a straight trunk under a cone of feathery foliage. Never placed by worldgen; only
    a sapling grows one."""
    def uniform(lo, hi):
        return {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}

    # 26.3's form (a feature is its own config; a plain block state is written as itself): dirt under the trunk where
    # it may go, as every vanilla tree has it (minecraft:soil_beneath_tree)
    return {
        "type": "minecraft:tree",
        "below_trunk_provider": "minecraft:soil_beneath_tree",
        "decorators": [],
        "foliage_placer": {"type": "minecraft:spruce_foliage_placer", "offset": uniform(0, 1), "radius": uniform(2, 3),
                           "trunk_height": uniform(2, 3)},
        "foliage_provider": {"id": "nookworks:dawnwood_leaves",
                             "properties": {"distance": "7", "persistent": "false", "waterlogged": "false"}},
        "ignore_vines": True,
        "minimum_size": {"type": "minecraft:two_layers_feature_size", "limit": 2, "upper_size": 2},
        "trunk_placer": {"type": "minecraft:straight_trunk_placer", "base_height": 8, "height_rand_a": 3, "height_rand_b": 1},
        "trunk_provider": {"id": "nookworks:dawnwood_log", "properties": {"axis": "y"}},
    }


# ---------------------------------------------------------------- Supports

# wood -> (stripped log item, side texture, end texture)
WOODS = {
    "oak": "stripped_oak_log", "spruce": "stripped_spruce_log", "birch": "stripped_birch_log",
    "jungle": "stripped_jungle_log", "acacia": "stripped_acacia_log", "dark_oak": "stripped_dark_oak_log",
    "mangrove": "stripped_mangrove_log", "cherry": "stripped_cherry_log", "pale_oak": "stripped_pale_oak_log",
    "poplar": "stripped_poplar_log",
    "crimson": "stripped_crimson_stem", "warped": "stripped_warped_stem", "bamboo": "stripped_bamboo_block",
    "dawnwood": "stripped_dawnwood_log",
}


def support_textures(wood):
    log = WOODS[wood]
    ns = "nookworks" if wood == "dawnwood" else "minecraft"
    return f"{ns}:{log}", f"{ns}:block/{log}", f"{ns}:block/{log}_top"


def timber(frm, to, along, rotation=None):
    """A squared timber with the grain running `along` an axis ('x', 'y' or 'z'): the side faces use the log's side,
    turned so the grain follows the timber, and the two ends show the log's end."""
    faces = {}
    for direction, axis in (("north", "z"), ("south", "z"), ("east", "x"), ("west", "x"), ("up", "y"), ("down", "y")):
        if axis == along:
            faces[direction] = {"texture": "#end"}
        else:
            # a side face's v runs up the block for north/south/east/west, along z for up/down
            turned = (along == "z" and direction in ("east", "west")) or (along == "x" and direction in ("north", "south", "up", "down"))
            faces[direction] = {"texture": "#side", "rotation": 90} if turned else {"texture": "#side"}
    element = {"from": frm, "to": to, "faces": faces}
    if rotation:
        element["rotation"] = rotation
    return element


def support_models(wood):
    """Four shapes, all facing north (the beam runs north-south, the bracket stands against a wall to the south):

    post     an upright timber
    beam     a timber lying along the top of the block, which is what a shelf or a floor rests on
    bracket  a wall bracket: an upright against the wall, the beam over it, and a brace between the two
    wall_post the bracket's upright alone, the whole height of the block, against the wall (the user, 2026-10-18)
    """
    _, side, end = support_textures(wood)
    textures = {"particle": side, "side": side, "end": end}
    post = [timber([5, 0, 5], [11, 16, 11], "y")]
    beam = [timber([5, 11, 0], [11, 16, 16], "z")]
    brace = timber([6, 5.5, 3], [10, 8.5, 15], "z", {"origin": [8, 7, 9], "axis": "x", "angle": 45})
    bracket = [timber([5, 0, 12], [11, 11, 16], "y"), timber([5, 11, 0], [11, 16, 16], "z"), brace]
    wall_post = [timber([5, 0, 12], [11, 16, 16], "y")]
    return {shape: {"parent": "minecraft:block/block", "textures": textures, "elements": elements}
            for shape, elements in (("post", post), ("beam", beam), ("bracket", bracket), ("wall_post", wall_post))}


def supports():
    names = []
    for wood in WOODS:
        name = f"{wood}_support"
        names.append(name)
        for shape, model in support_models(wood).items():
            write(ASSETS / f"models/block/{name}_{shape}.json", model)
        variants = {}
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for shape in ("post", "beam", "bracket", "wall_post"):
                variant = {"model": f"nookworks:block/{name}_{shape}"}
                if y:
                    variant["y"] = y
                variants[f"facing={facing},shape={shape}"] = variant
        write(ASSETS / f"blockstates/{name}.json", {"variants": variants})
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:block/{name}_bracket"}})
        write(DATA / f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block", "random_sequence": f"nookworks:blocks/{name}",
            "pools": [{"rolls": 1, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"nookworks:{name}"}],
                       "condition": {"type": "minecraft:survives_explosion"}}]})
        log, _, _ = support_textures(wood)
        write(DATA / f"recipe/{name}.json", {
            "type": "minecraft:crafting_shaped", "category": "building", "group": "support",
            "key": {"L": log}, "pattern": ["LLL", " L "], "result": {"count": 6, "id": f"nookworks:{name}"}})
    burnable = [f"nookworks:{n}" for n in names if not n.startswith(("crimson", "warped"))]
    write(DATA / "tags/block/supports.json", {"values": [f"nookworks:{n}" for n in names]})
    write(DATA / "tags/item/supports.json", {"values": [f"nookworks:{n}" for n in names]})
    write(DATA / "tags/item/burnable_supports.json", {"values": burnable})
    return len(names)


# ---------------------------------------------------------------- Chairs and desks

def planks_of(wood):
    ns = "nookworks" if wood == "dawnwood" else "minecraft"
    return f"{ns}:{wood}_planks", f"{ns}:block/{wood}_planks"


# A desk's parts, cut from block/template_desk (drawn whole, as the item shows it): the top, the north-west leg, the
# rail under the north edge between the legs, and that rail run on to the east or the west edge - where the desk is
# joined on that side and the leg there has gone. Turned in the blockstate for the other corners and sides.
DESK_PARTS = {"top": "top", "leg": "leg_nw", "rail": "apron_north"}
# how far the north rail runs on past a leg that is not there, as (part, from x, to x)
DESK_RAIL_ENDS = (("rail_east", 12, 16), ("rail_west", 0, 4))
# (y turn, its side, the side clockwise of it, the side anticlockwise) for the north-west leg and the north rail
DESK_TURNS = ((0, "north", "east", "west"), (90, "east", "south", "north"), (180, "south", "west", "east"),
              (270, "west", "north", "south"))


def desk_part_templates():
    """block/template_desk_<part>: the parts of the whole desk's model, each alone."""
    whole = json.loads((ASSETS / "models/block/template_desk.json").read_text(encoding="utf-8"))
    elements = {e["name"]: e for e in whole["elements"]}
    parts = {part: [elements[name]] for part, name in DESK_PARTS.items()}
    for part, x0, x1 in DESK_RAIL_ENDS:
        end = json.loads(json.dumps(elements["apron_north"]))
        end["name"] = part
        end["from"][0], end["to"][0] = x0, x1
        end["faces"] = {k: v for k, v in end["faces"].items() if k not in ("east", "west")}  # it meets the next desk's
        parts[part] = [end]
    for part, elems in parts.items():
        write(ASSETS / f"models/block/template_desk_{part}.json",
              {"parent": "minecraft:block/block", "textures": whole["textures"], "elements": elems})
    return list(parts)


def desk_states(name):
    """A desk's blockstate: its top; a leg at each corner neither side of which is joined to another desk; the rail
    along each side not joined, running on to the corner where the next side is (the user, 2026-10-05: desks side by
    side are one long desk)."""
    model = lambda part: f"nookworks:block/{name}_{part}"
    multipart = [{"apply": {"model": model("top")}}]
    for y, side, clockwise, anticlockwise in DESK_TURNS:
        turn = {"y": y, "uvlock": True} if y else {}
        multipart.append({"when": {side: "false", anticlockwise: "false"}, "apply": {"model": model("leg"), **turn}})
        multipart.append({"when": {side: "false"}, "apply": {"model": model("rail"), **turn}})
        multipart.append({"when": {side: "false", clockwise: "true"}, "apply": {"model": model("rail_east"), **turn}})
        multipart.append({"when": {side: "false", anticlockwise: "true"}, "apply": {"model": model("rail_west"), **turn}})
    return {"multipart": multipart}


def furniture():
    names = {"chair": [], "stool": [], "desk": []}
    desk_parts = desk_part_templates()
    for wood in WOODS:
        planks_item, planks = planks_of(wood)
        _, post, _ = support_textures(wood)
        for kind in ("chair", "stool", "desk"):
            name = f"{wood}_{kind}"
            names[kind].append(f"nookworks:{name}")
            write(ASSETS / f"models/block/{name}.json", {"parent": f"nookworks:block/template_{kind}",
                                                         "textures": {"particle": planks, "planks": planks, "post": post}})
            if kind == "desk":
                for part in desk_parts:
                    write(ASSETS / f"models/block/{name}_{part}.json", {"parent": f"nookworks:block/template_desk_{part}",
                                                                        "textures": {"particle": planks, "planks": planks, "post": post}})
            variants = {}
            for waterlogged in ("false", "true"):
                if kind in ("chair", "stool"):
                    for facing, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
                        variant = {"model": f"nookworks:block/{name}"}
                        if y:
                            variant["y"] = y
                        variants[f"facing={facing},waterlogged={waterlogged}"] = variant
            write(ASSETS / f"blockstates/{name}.json", {"variants": variants} if kind != "desk" else desk_states(name))
            write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:block/{name}"}})
            write(DATA / f"loot_table/blocks/{name}.json", {
                "type": "minecraft:block", "random_sequence": f"nookworks:blocks/{name}",
                "pools": [{"rolls": 1, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"nookworks:{name}"}],
                           "condition": {"type": "minecraft:survives_explosion"}}]})
            pattern = {"chair": ["P  ", "PPP", "S S"], "stool": ["PPP", "S S"], "desk": ["PPP", "S S", "S S"]}[kind]
            write(DATA / f"recipe/{name}.json", {
                "type": "minecraft:crafting_shaped", "category": "building", "group": kind,
                "key": {"P": planks_item, "S": "#c:rods/wooden"}, "pattern": pattern, "result": {"count": 1, "id": f"nookworks:{name}"}})
    for kind, values in names.items():
        write(DATA / f"tags/block/{kind}s.json", {"values": values})
        write(DATA / f"tags/item/{kind}s.json", {"values": values})
    return len(WOODS)


def slab_of(wood):
    return f"{'nookworks' if wood == 'dawnwood' else 'minecraft'}:{wood}_slab"


# (facing, y turn): the bench's models face south, their left (to someone in front) at the west end
BENCH_TURNS = (("south", 0), ("west", 90), ("north", 180), ("east", 270))
BENCH_PARTS = ("seat", "rail", "end_left", "end_right", "rail_left", "rail_right")


def benches():
    names = []
    for wood in WOODS:
        planks_item, planks = planks_of(wood)
        _, post, _ = support_textures(wood)
        name = f"{wood}_bench"
        names.append(f"nookworks:{name}")
        textures = {"particle": planks, "planks": planks, "post": post}
        write(ASSETS / f"models/block/{name}.json", {"parent": "nookworks:block/template_bench", "textures": textures})
        for part in BENCH_PARTS:
            write(ASSETS / f"models/block/{name}_{part}.json", {"parent": f"nookworks:block/template_bench_{part}", "textures": textures})
        multipart = []
        for facing, y in BENCH_TURNS:
            turn = {"y": y} if y else {}
            model = lambda part: {"model": f"nookworks:block/{name}_{part}", **turn}
            multipart.append({"when": {"facing": facing}, "apply": model("seat")})
            multipart.append({"when": {"facing": facing}, "apply": model("rail")})
            for side in ("left", "right"):
                multipart.append({"when": {"facing": facing, side: "false"}, "apply": model(f"end_{side}")})
                multipart.append({"when": {"facing": facing, side: "true"}, "apply": model(f"rail_{side}")})
        write(ASSETS / f"blockstates/{name}.json", {"multipart": multipart})
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:block/{name}"}})
        write(DATA / f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block", "random_sequence": f"nookworks:blocks/{name}",
            "pools": [{"rolls": 1, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"nookworks:{name}"}],
                       "condition": {"type": "minecraft:survives_explosion"}}]})
        write(DATA / f"recipe/{name}.json", {
            "type": "minecraft:crafting_shaped", "category": "building", "group": "bench",
            "key": {"L": slab_of(wood), "S": "#c:rods/wooden"}, "pattern": ["LLL", "S S"],
            "result": {"count": 1, "id": f"nookworks:{name}"}})
    write(DATA / "tags/block/benches.json", {"values": names})
    write(DATA / "tags/item/benches.json", {"values": names})
    return len(WOODS)


def trapdoor_of(wood):
    return f"{'nookworks' if wood == 'dawnwood' else 'minecraft'}:{wood}_trapdoor"


# (facing, y turn): the shelf's models face north, their wall to the south
SHELF_TURNS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def wall_shelves():
    names = []
    for wood in WOODS:
        _, planks = planks_of(wood)
        name = f"{wood}_wall_shelf"
        names.append(f"nookworks:{name}")
        textures = {"particle": planks, "planks": planks}
        write(ASSETS / f"models/block/{name}_board.json", {"parent": "nookworks:block/template_wall_shelf_board", "textures": textures})
        write(ASSETS / f"models/block/{name}.json", {"parent": "nookworks:block/template_wall_shelf",
                                                     "textures": dict(textures, iron="nookworks:block/hook")})
        multipart = []
        for facing, y in SHELF_TURNS:
            turn = {"y": y} if y else {}
            multipart.append({"when": {"facing": facing}, "apply": {"model": f"nookworks:block/{name}_board", **turn}})
            for side in ("left", "right"):
                multipart.append({"when": {"facing": facing, side: "false"},
                                  "apply": {"model": f"nookworks:block/wall_shelf_bracket_{side}", **turn}})
        write(ASSETS / f"blockstates/{name}.json", {"multipart": multipart})
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:block/{name}"}})
        write(DATA / f"loot_table/blocks/{name}.json", {
            "type": "minecraft:block", "random_sequence": f"nookworks:blocks/{name}",
            "pools": [{"rolls": 1, "bonus_rolls": 0.0, "entries": [{"type": "minecraft:item", "name": f"nookworks:{name}"}],
                       "condition": {"type": "minecraft:survives_explosion"}}]})
        write(DATA / f"recipe/{name}.json", {
            "type": "minecraft:crafting_shapeless", "category": "building", "group": "wall_shelf",
            "ingredients": [trapdoor_of(wood), "minecraft:iron_nugget", "minecraft:iron_nugget"],
            "result": {"count": 1, "id": f"nookworks:{name}"}})
    write(DATA / "tags/block/wall_shelves.json", {"values": names})
    write(DATA / "tags/item/wall_shelves.json", {"values": names})
    return len(WOODS)


if __name__ == "__main__":
    print(f"dawnwood: {dawnwood()} files; supports: {supports()} woods; chairs, stools and desks: {furniture()} woods; "
          f"benches: {benches()} woods; wall shelves: {wall_shelves()} woods")
