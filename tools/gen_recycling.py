"""The data of recycling (DECISIONS, "Recycling", 2026-10-18): the blockstates, models and item definitions of what is
built of scrap, the loot tables, recipes, tags and data maps of the whole set,, its sounds and its part in features.json.

- The Recycler's list (data map nookworks:recycling): how many ingots each piece of metal junk took to make; gear as
  the Crusher counts it (data_maps/item/crushing.json), the rest from vanilla's recipes. The tag #nookworks:recyclable
  is the same list (for the Precision Sorter).
- #nookworks:crushable is the Crusher's list (crushing.json's keys), for sorting the same way - less the metal it grinds
  to a dust tag only another mod fills.
- The bare bales and sheets rust as copper weathers: their next ages and waxed forms go into neoforge's oxidizables and
  waxables data maps (merged with what is there).

Run from the repo root:  python tools/gen_recycling.py   (tools/gen_machine_models.py writes the two machines' models)
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/nookworks"
DATA = RES / "data/nookworks"
AGES = ("", "exposed_", "weathered_", "oxidized_")
PAINTS = ("red", "green", "blue", "white")
# the common tags, as the 1.2.0 compatibility sweep made the recipes take (another mod's dye or copper works)
DYES = {"red": "#c:dyes/red", "green": "#c:dyes/green", "blue": "#c:dyes/blue", "white": "#c:dyes/white"}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def bales():
    return [age + "scrap_bale" for age in AGES] + ["waxed_" + age + "scrap_bale" for age in AGES]


def bare_sheets():
    return [age + "corrugated_sheet" for age in AGES] + ["waxed_" + age + "corrugated_sheet" for age in AGES]


def painted_sheets():
    return [c + "_corrugated_sheet" for c in PAINTS]


def look(name):
    """The texture name a block wears: waxed ones look as their age does."""
    return name.replace("waxed_", "")


# ---------------------------------------------------------------- models and states

def sheet_model(texture, centered=False):
    """
    A corrugated sheet facing north, against the south face: the plate (z 15..16) and four ridges 2 px wide standing
    1 px off it (z 14..15), so it is corrugated in relief. Every face takes its pixels where a cube would put them, so
    the texture's light and dark stripes fall on the ridges' fronts and flanks.
    """
    def faces(skip=()):
        return {f: {"texture": "#sheet"} for f in ("north", "south", "west", "east", "up", "down") if f not in skip}
    z = 8 if centered else 15  # the plate's back: the block's face, or its middle (the ridges a pixel in front of it)
    plate = faces() if centered else {**faces(), "south": {"texture": "#sheet", "cullface": "south"}}
    elements = [{"from": [0, 0, z], "to": [16, 16, z + 1], "faces": plate, "name": "plate"}]
    for x in (1, 5, 9, 13):
        ridge = faces(skip=("south",))
        # the flanks take the sheet's lightest stripe (column 1): the game shades side faces down, and with their own
        # pixels they drew a dark crack-like line down every ridge, plainest on the white paint
        for flank in ("west", "east"):
            ridge[flank] = {"texture": "#sheet", "uv": [1, 0, 2, 16]}
        elements.append({"from": [x, 0, z - 1], "to": [x + 2, 16, z], "faces": ridge, "name": "ridge"})
    return {"parent": "minecraft:block/block", "textures": {"sheet": texture, "particle": texture}, "elements": elements,
            # in the inventory turned to show its ridged face; in an item frame flat against it, ridges out
            # the plate is at the back of its block: in the hand it is turned to show its ridged face and drawn forward
            # to where a block in the hand is, or it sat at the screen's edge, a sliver
            "display": {"gui": {"rotation": [25, 205, 0], "translation": [2.5, 0, 0], "scale": [0.7, 0.7, 0.7]},
                        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 7], "scale": [1, 1, 1]},
                        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, -2], "scale": [0.4, 0.4, 0.4]},
                        # held where a block is held, low at the screen's side, ridges to the eye and tipped back a
                        # little; the plate brought to the block's middle (it floated mid-screen: the user, 2026-10-20)
                        "firstperson_righthand": {"rotation": [-15, 200, 0], "translation": [1, 0.8, 2.9], "scale": [0.4, 0.4, 0.4]},
                        "firstperson_lefthand": {"rotation": [-15, 200, 0], "translation": [1, 0.8, 2.9], "scale": [0.4, 0.4, 0.4]},
                        "thirdperson_righthand": {"rotation": [75, 180, 0], "translation": [0, 2.5, -2.5], "scale": [0.4, 0.4, 0.4]},
                        "thirdperson_lefthand": {"rotation": [75, 180, 0], "translation": [0, 2.5, -2.5], "scale": [0.4, 0.4, 0.4]}}}


def sheet_states(name):
    """Turned to face the way it was set; flat ones turned again by the way their ridges run; centred or against."""
    variants = {}
    horizontal = {"north": 0, "east": 90, "south": 180, "west": 270}
    for centered in ("false", "true"):
        model = f"nookworks:block/{name}" + ("_centered" if centered == "true" else "")
        for axis in ("x", "z"):
            for facing, y in horizontal.items():
                v = {"model": model}
                if y:
                    v["y"] = y
                variants[f"axis={axis},centered={centered},facing={facing}"] = v
            for facing, x in (("up", 270), ("down", 90)):
                v = {"model": model, "x": x}
                if axis == "x":
                    v["y"] = 90
                variants[f"axis={axis},centered={centered},facing={facing}"] = v
    return {"variants": variants}


def write_assets():
    for name in bales():
        tex = look(name)
        write(ASSETS / f"models/block/{tex}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "side": f"nookworks:block/{tex}_side", "top": f"nookworks:block/{tex}_top", "bottom": f"nookworks:block/{tex}_top"}})
        write(ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": f"nookworks:block/{tex}"}}})
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:block/{tex}"}})
    for name in bare_sheets() + painted_sheets():
        tex = look(name)
        write(ASSETS / f"models/block/{tex}.json", sheet_model(f"nookworks:block/{tex}"))
        write(ASSETS / f"models/block/{tex}_centered.json", sheet_model(f"nookworks:block/{tex}", centered=True))
        write(ASSETS / f"blockstates/{name}.json", sheet_states(tex))
        write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:block/{tex}"}})
    # five drawings of the plate, picked at random where each block stands (plain ones most often), so a wall of it
    # is not one patch over and over (tools/textures/gen_recycling.py)
    weights = (4, 4, 2, 2, 1)  # two plain drawings, then the patched ones
    for which in range(len(weights)):
        write(ASSETS / f"models/block/patched_sheet_metal_{which}.json", {"parent": "minecraft:block/cube_all",
              "textures": {"all": f"nookworks:block/patched_sheet_metal_{which}"}})
    write(ASSETS / "blockstates/patched_sheet_metal.json", {"variants": {"": [
        {"model": f"nookworks:block/patched_sheet_metal_{which}", "weight": weight} for which, weight in enumerate(weights)]}})
    write(ASSETS / "items/patched_sheet_metal.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/patched_sheet_metal_2"}})
    for item in ("scrap", "baler"):
        write(ASSETS / f"models/item/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"nookworks:item/{item}"}})
        write(ASSETS / f"items/{item}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:item/{item}"}})
    write(ASSETS / "items/compressor.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/compressor"}})
    write(ASSETS / "items/recycler.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/recycler"}})
    write(ASSETS / "models/item/recycler.json", recycler_item())


def recycler_item():
    """The Recycler whole in the hand: its body and its intake one above the other, drawn smaller to fit."""
    lower = json.loads((ASSETS / "models/block/recycler.json").read_text(encoding="utf-8"))
    upper = json.loads((ASSETS / "models/block/recycler_upper.json").read_text(encoding="utf-8"))
    tex = dict(lower["textures"])
    renamed = {}
    for key, value in upper["textures"].items():
        renamed[key] = "u_" + key
        tex["u_" + key] = value
    elements = [dict(e) for e in lower["elements"]]
    for e in upper["elements"]:
        e = json.loads(json.dumps(e))
        for side, f in e["faces"].items():
            f.setdefault("uv", default_uv(e["from"], e["to"], side))  # where it was, before it is lifted out of the block
        e["from"][1] += 16
        e["to"][1] += 16
        for f in e["faces"].values():
            f["texture"] = "#" + renamed[f["texture"][1:]]
            f.pop("cullface", None)
        elements.append(e)
    return {"parent": "minecraft:block/block", "textures": tex, "elements": elements,
            "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -3.2, 0], "scale": [0.4, 0.4, 0.4]},
                        "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.2, 0.2, 0.2]},
                        "fixed": {"rotation": [0, 0, 0], "translation": [0, -4, 0], "scale": [0.4, 0.4, 0.4]},
                        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 0.5, 1.5], "scale": [0.22, 0.22, 0.22]},
                        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 0.5, 1.5], "scale": [0.22, 0.22, 0.22]},
                        # two blocks tall: held smaller than a block, so it does not fill a fifth of the screen
                        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 1.5, 0], "scale": [0.2, 0.2, 0.2]},
                        # left hand as the right: the game mirrors it itself
                        "firstperson_lefthand": {"rotation": [0, 45, 0], "translation": [0, 1.5, 0], "scale": [0.2, 0.2, 0.2]}}}


def default_uv(frm, to, side):
    """The uv a face takes when it gives none: its element's place on that side of the block."""
    x0, y0, z0 = frm
    x1, y1, z1 = to
    return {"north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
            "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
            "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0]}[side]


# ---------------------------------------------------------------- loot

def drop_self(name, keep_energy=False, lower_only=False):
    entry = {"type": "minecraft:item", "name": f"nookworks:{name}"}
    if keep_energy:
        entry["modifier"] = {"type": "minecraft:copy_components", "source": "block_entity", "include": ["nookworks:held_energy"]}
    if lower_only:
        entry["condition"] = {"type": "minecraft:match_block", "blocks": f"nookworks:{name}", "state": {"half": "lower"}}
    return {"type": "minecraft:block", "pools": [{"bonus_rolls": 0.0, "condition": {"type": "minecraft:survives_explosion"},
                                                    "entries": [entry], "rolls": 1}],
            "random_sequence": f"nookworks:blocks/{name}"}


def write_loot():
    for name in bales() + bare_sheets() + painted_sheets() + ["patched_sheet_metal"]:
        write(DATA / f"loot_table/blocks/{name}.json", drop_self(name))
    write(DATA / "loot_table/blocks/compressor.json", drop_self("compressor", keep_energy=True))
    write(DATA / "loot_table/blocks/recycler.json", drop_self("recycler", keep_energy=True, lower_only=True))


# ---------------------------------------------------------------- recipes

def shaped(pattern, key, result, count=1, category="misc", group=None):
    r = {"type": "minecraft:crafting_shaped", "category": category}
    if group:
        r["group"] = group
    r.update({"key": key, "pattern": pattern, "result": {"count": count, "id": result}})
    return r


def shapeless(ingredients, result, count=1, category="misc"):
    return {"type": "minecraft:crafting_shapeless", "category": category, "ingredients": ingredients,
            "result": {"count": count, "id": result}}


def write_recipes():
    R = DATA / "recipe"

    def write(path, recipe):
        # tools/gen_feature_conditions.py puts the part's condition on each recipe: run alone, this keeps it
        if path.exists():
            old = json.loads(path.read_text(encoding="utf-8"))
            if "neoforge:conditions" in old:
                recipe = {"neoforge:conditions": old["neoforge:conditions"], **recipe}
        globals()["write"](path, recipe)
    write(R / "compressor.json", shaped(["IPI", "IXI", "IRI"], {"I": "minecraft:iron_ingot", "P": "minecraft:piston",
                                                               "X": "nookworks:machine_chassis", "R": "minecraft:redstone"}, "nookworks:compressor"))
    write(R / "baler.json", shaped(["IPI", "BCB", "IPI"], {"I": "minecraft:iron_ingot", "P": "minecraft:piston",
                                                          "B": "minecraft:iron_bars", "C": "#c:ingots/copper"}, "nookworks:baler"))
    write(R / "recycler.json", shapeless(["nookworks:crusher", "nookworks:compressor", "nookworks:baler"], "nookworks:recycler"))
    write(R / "scrap_bale.json", shaped(["SSS", "SSS", "SSS"], {"S": "nookworks:scrap"}, "nookworks:scrap_bale", category="building"))
    write(R / "scrap_from_scrap_bale.json", shapeless(["#nookworks:scrap_bales"], "nookworks:scrap", count=9))
    # enough to build with: sixteen sheets from six scrap (as iron bars from six ingots), four plates from four scrap
    # (the user took the self-review's suggestion, 2026-10-18; it had been 6 and 1)
    write(R / "corrugated_sheet.json", shaped(["SSS", "SSS"], {"S": "nookworks:scrap"}, "nookworks:corrugated_sheet", count=16,
                                              category="building"))
    for colour in PAINTS:
        write(R / f"{colour}_corrugated_sheet.json", shaped(["CCC", "CDC", "CCC"], {"C": "#nookworks:bare_corrugated_sheets",
                                                                                      "D": DYES[colour]},
                                                              f"nookworks:{colour}_corrugated_sheet", count=8, category="building",
                                                              group="painted_corrugated_sheet"))
    write(R / "patched_sheet_metal.json", shaped(["SS", "SS"], {"S": "nookworks:scrap"}, "nookworks:patched_sheet_metal", count=4,
                                                 category="building"))
    write(R / "machine_chassis_from_scrap_bale.json", shaped(["ICI", "CBC", "ICI"], {"I": "minecraft:iron_ingot", "C": "#minecraft:coals",
                                                                                      "B": "#nookworks:scrap_bales"}, "nookworks:machine_chassis"))
    write(R / "iron_nugget_from_smelting_scrap.json", {"type": "minecraft:smelting", "category": "misc", "cookingtime": 200,
                                                        "experience": 0.1, "ingredient": "nookworks:scrap",
                                                        "result": {"id": "minecraft:iron_nugget"}})
    write(R / "iron_nugget_from_blasting_scrap.json", {"type": "minecraft:blasting", "category": "misc", "cookingtime": 100,
                                                        "experience": 0.1, "ingredient": "nookworks:scrap",
                                                        "result": {"id": "minecraft:iron_nugget"}})


# ---------------------------------------------------------------- the Recycler's list

GEAR = {"sword": 2, "pickaxe": 3, "axe": 3, "shovel": 1, "hoe": 2, "spear": 1,
        "helmet": 5, "chestplate": 8, "leggings": 7, "boots": 4}
JUNK = {
    # iron things, by what their recipe takes (a share for the ones made several at a time)
    "iron_bars": 0.375, "iron_chain": 1.22, "rail": 0.375, "detector_rail": 1, "activator_rail": 1,
    "bucket": 3, "water_bucket": 3, "lava_bucket": 3, "milk_bucket": 3, "powder_snow_bucket": 3,
    "minecart": 5, "chest_minecart": 5, "furnace_minecart": 5, "tnt_minecart": 5, "hopper_minecart": 10,
    "hopper": 5, "cauldron": 7, "anvil": 31, "chipped_anvil": 20, "damaged_anvil": 10,
    "iron_door": 2, "iron_trapdoor": 4, "heavy_weighted_pressure_plate": 2, "shears": 2, "flint_and_steel": 1,
    "compass": 4, "lantern": 0.89, "soul_lantern": 0.89, "tripwire_hook": 0.5, "crossbow": 1.5, "piston": 1, "sticky_piston": 1,
    "smithing_table": 2, "stonecutter": 1, "blast_furnace": 5, "shield": 1, "crafter": 5,
    "chainmail_helmet": 2, "chainmail_chestplate": 4, "chainmail_leggings": 3.5, "chainmail_boots": 2,
    "iron_horse_armor": 5, "copper_horse_armor": 5, "iron_nautilus_armor": 5, "copper_nautilus_armor": 5,
    # copper things (scrap too: the user took the recommendation); the ones that weather are in COPPER_JUNK
    "spyglass": 2, "brush": 1,
}
# Copper junk that weathers: each in its eight forms (four ages, waxed or not). Copper building blocks are left out:
# they are a material, not junk.
COPPER_JUNK = {"lightning_rod": 3, "copper_bars": 0.375, "copper_chain": 1.22, "copper_lantern": 0.92, "copper_door": 2,
               "copper_trapdoor": 4, "copper_chest": 8}
# Not the clock: a cured villager sells one for an emerald bought with a gold ingot, and it gave back two ingots'
# worth of nuggets (the self-review, 2026-10-18).
GOLD = {"powered_rail": 1, "light_weighted_pressure_plate": 2, "golden_horse_armor": 5, "golden_nautilus_armor": 5}
# Chainmail and the horse and nautilus armour have no recipe: they count as the plate armour nearest them.
# Nookworks' own metal things, by their recipes: (metal, gold or not).
OURS = {"iron_hammer": (5, False), "copper_hammer": (5, False), "golden_hammer": (5, True), "wrench": (4, False),
        "iron_pot": (3, False),
        # the rest of Nookworks' metal things (the user, 2026-10-20: 歯車とかブックエンドとか): the iron, copper or gold in
        # their recipes, followed down through our own parts (a Machine Chassis's iron block, a gear's nuggets). Out:
        # what is mostly wood (wall shelves, the presses worked by hand, planters), what holds a liquid or experience
        # (bottles, buckets, tanks), soap, and what has brass, Velarium, mercury or scrap in it
        "bookend": (0.17, False), "hook": (0.11, False), "sconce": (0.17, False), "thin_chain": (0.17, False),
        "drive_wheel": (0.72, False), "grill": (1.35, False), "copper_pipe": (0.5, False), "energy_cable": (0.25, False),
        "gold_sorter_gear": (4.44, True), "gold_storage_upgrade": (9, True), "iron_storage_upgrade": (8, False),
        "iron_storage_barrel": (8, False), "magnet": (5, False), "magnet_chest": (12, False), "trash_can": (7, False),
        "iron_workbench": (5.75, False), "item_sorter": (12, False), "precision_sorter": (12, False),
        "item_warp_sender": (5.75, False), "item_warp_receiver": (0.75, False),
        "horizontal_item_warp_sender": (6.5, False), "horizontal_item_warp_receiver": (1.5, False),
        "spawner_link_sender": (1.75, False), "spawner_link_receiver": (1, False), "absorption_pad": (9, False),
        "machine_chassis": (13, False), "generator": (19, False), "crusher": (19, False), "electric_furnace": (19, False),
        "stone_maker": (19, False), "sifter": (12.38, False), "compressor": (20, False), "baler": (7.75, False),
        "recycler": (46.75, False), "motor": (20, False), "water_pump": (10, False), "printing_press": (18, False),
        "retort": (14, False), "distiller": (6, False), "condenser": (8, False), "barter_terminal": (13, False)}


def recycling():
    values = {}
    for metal in ("iron", "copper"):
        for piece, n in GEAR.items():
            values[f"minecraft:{metal}_{piece}"] = {"metal": n}
    for piece, n in GEAR.items():
        values[f"minecraft:golden_{piece}"] = {"metal": n, "kind": "gold"}
    for item, n in JUNK.items():
        values[f"minecraft:{item}"] = {"metal": n}
    for item, n in COPPER_JUNK.items():
        for wax in ("", "waxed_"):
            for age in AGES:
                values[f"minecraft:{wax}{age}{item}"] = {"metal": n}
    for item, n in GOLD.items():
        values[f"minecraft:{item}"] = {"metal": n, "kind": "gold"}
    for item, (n, gold) in OURS.items():
        values[f"nookworks:{item}"] = {"metal": n, "kind": "gold"} if gold else {"metal": n}
    return values


# What vanilla packs nine into one of in the crafting grid (26.3's recipes: a 3x3 of one item, one out). A GameTest
# (compressible_is_what_the_compressor_takes) checks the Compressor takes each and nothing else of vanilla's.
VANILLA_NINEFOLD = ("bamboo", "bone_meal", "coal", "copper_ingot", "copper_nugget", "diamond", "disc_fragment_5",
                    "dried_kelp", "emerald", "gold_ingot", "gold_nugget", "ice", "iron_ingot", "iron_nugget", "lapis_lazuli",
                    "melon_slice", "nether_wart", "netherite_ingot", "packed_ice", "prismarine_shard", "raw_copper",
                    "raw_gold", "raw_iron", "redstone", "resin_clump", "slime_ball", "sulfur", "wheat")


def compressible():
    """#nookworks:compressible: what the Compressor takes - vanilla's nine-into-one ingredients and ours (read off our
    recipes: a 3x3 of one item, one out). Recipes are generated above, so this runs after write_recipes."""
    ours = []
    for path in sorted((DATA / "recipe").rglob("*.json")):
        r = json.loads(path.read_text(encoding="utf-8"))
        pattern = r.get("pattern", [])
        if (r.get("type") == "minecraft:crafting_shaped" and len(pattern) == 3 and all(len(row) == 3 for row in pattern)
                and len(set("".join(pattern))) == 1 and r["result"].get("count", 1) == 1):
            item = r["key"][pattern[0][0]]
            if not item.startswith("#") and item not in ours:
                ours.append(item)
    return [f"minecraft:{i}" for i in VANILLA_NINEFOLD] + ours


def axe_transform(block, into, kind):
    """An axe on {@code block} makes it {@code into}, its properties kept (vanilla's own way for copper)."""
    return {"transformer": "minecraft:axe", "transform_data": {
        "block_state_provider": {"type": "minecraft:rule_based", "rules": [{
            "if_true": {"type": "minecraft:matching_blocks", "blocks": block},
            "then": {"type": "minecraft:copy_properties", "source": {"id": into}}}]},
        "item_damage_per_use": 1,
        "particle": kind,
        "sound": "minecraft:item.axe.scrape" if kind == "scrape" else "minecraft:item.axe.wax_off"}}


def merge_map(path, entries):
    data = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"values": {}}
    data["values"].update(entries)
    write(path, data)


def write_maps_and_tags():
    values = recycling()
    write(DATA / "data_maps/item/recycling.json", {"values": values})
    write(DATA / "tags/item/recyclable.json", {"values": list(values)})
    crushing = json.loads((DATA / "data_maps/item/crushing.json").read_text(encoding="utf-8"))["values"]
    # only what the Crusher takes on its own: the metal it grinds to dust (ores, raw metal, gear) waits for a mod that
    # fills #c:dusts/<metal>, and till then it refuses them, so a sorter must not send them its way
    write(DATA / "tags/item/crushable.json", {"values": [{"id": k, "required": False} for k, v in crushing.items()
                                                          if not v["result"].startswith("#")]})
    write(DATA / "tags/item/compressible.json", {"values": compressible()})
    write(DATA / "tags/item/scrap_bales.json", {"values": [f"nookworks:{n}" for n in bales()]})
    write(DATA / "tags/item/bare_corrugated_sheets.json", {"values": [f"nookworks:{n}" for n in bare_sheets()]})
    neo = RES / "data/neoforge/data_maps/block"
    oxid, wax, axe = {}, {}, {}
    for base in ("scrap_bale", "corrugated_sheet"):
        # NeoForge makes the axe's scrape and wax-off from these maps by setting the block's default state, which turned
        # a sheet to face north (one lying on a floor stood up): the sheets have their own axe transforms instead, which
        # copy their facing, axis, place and water across (the play-through, 2026-10-18)
        own = {"generate_block_transform": False} if base == "corrugated_sheet" else {}
        for a, b in zip(AGES, AGES[1:]):
            oxid[f"nookworks:{a}{base}"] = {"next_oxidation_stage": f"nookworks:{b}{base}", **own}
            if own:
                axe[f"nookworks:{b}{base}"] = axe_transform(f"nookworks:{b}{base}", f"nookworks:{a}{base}", "scrape")
        for a in AGES:
            wax[f"nookworks:{a}{base}"] = {"waxed": f"nookworks:waxed_{a}{base}", **own}
            if own:
                axe[f"nookworks:waxed_{a}{base}"] = axe_transform(f"nookworks:waxed_{a}{base}", f"nookworks:{a}{base}", "wax_off")
    merge_map(neo / "oxidizables.json", oxid)
    merge_map(neo / "waxables.json", wax)
    merge_map(neo / "transformables.json", axe)
    # mined with a pickaxe; solid, so they block motion as 26.2 reckoned (DataTests' blocks_keep_their_solidity)
    for tag_file in ("mineable/pickaxe", "blocks_motion_no_leaves"):
        path = RES / f"data/minecraft/tags/block/{tag_file}.json"
        tag = json.loads(path.read_text(encoding="utf-8"))
        for name in ["compressor", "recycler", "patched_sheet_metal"] + bales() + bare_sheets() + painted_sheets():
            if f"nookworks:{name}" not in tag["values"]:
                tag["values"].append(f"nookworks:{name}")
        write(path, tag)


def write_sounds():
    sounds_path = ASSETS / "sounds.json"
    sounds = json.loads(sounds_path.read_text(encoding="utf-8"))
    sounds["block.compressor.run"] = {"subtitle": "subtitles.nookworks.block.compressor.run",
                                      "sounds": [{"name": "minecraft:block.piston.extend", "type": "event", "volume": 0.3, "pitch": 0.7}]}
    sounds["block.recycler.run"] = {"subtitle": "subtitles.nookworks.block.recycler.run",
                                    "sounds": [{"name": "minecraft:block.anvil.land", "type": "event", "volume": 0.15, "pitch": 1.6},
                                               {"name": "minecraft:block.grindstone.use", "type": "event", "volume": 0.4, "pitch": 0.6}]}
    write(sounds_path, sounds)


def write_feature():
    """The whole set is one part a pack can switch off (DECISIONS: "recycling" in features.json)."""
    path = RES / "nookworks/features.json"
    text = path.read_text(encoding="utf-8")
    items = (["nookworks:compressor", "nookworks:baler", "nookworks:recycler", "nookworks:scrap"]
             + [f"nookworks:{n}" for n in bales() + bare_sheets() + painted_sheets()] + ["nookworks:patched_sheet_metal"])
    if '"recycling"' in text:
        assert json.loads(text)["recycling"] == items, "features.json's recycling part differs from what this writes"
        return
    # the file is laid out by hand: the part goes in at its end, in its style (lines of ids under 120 wide)
    lines, line = [], "   "
    for item in items:
        word = f' "{item}",'
        if len(line) + len(word) > 118:
            lines.append(line)
            line = "   "
        line += word
    lines.append(line.rstrip(","))
    part = ',\n  "recycling": [\n' + "\n".join(lines) + "\n  ]\n}"
    end = text.rstrip().rindex("}")
    path.write_bytes((text[:end].rstrip() + part + "\n").encode("utf-8"))


def main():
    write_assets()
    write_loot()
    write_recipes()
    write_maps_and_tags()
    write_sounds()
    write_feature()
    print("wrote the recycling data")


if __name__ == "__main__":
    main()
