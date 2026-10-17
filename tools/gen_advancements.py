"""The Nookworks advancement tab: every advancement's JSON, from one table. Deterministic.

One tab off nookworks:root, a branch for each part of the mod, in vanilla's tone: a short title and one line saying
what to do (the titles and lines are in the lang files, under advancements.nookworks.<id>.title / .description).

Most are "have this item" (minecraft:inventory_changed). When any of several items will do, they go in ONE item
predicate's list: several predicates in a criterion all have to be met at once, which is how "make something out of
Velarium" came to ask for all ten pieces together.

Run from the repo root:  python tools/gen_advancements.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/data/nookworks/advancement"


def has(*items):
    """Have any one of these items."""
    return {"trigger": "minecraft:inventory_changed",
            "conditions": {"items": [{"items": [f"nookworks:{i}" if ":" not in i else i for i in items]}]}}


# 26.3's form: a criterion's location, entity or player is one loot condition (several would be joined by
# minecraft:all_of), each keyed by "type"; block_state_property is match_block.
def placed(block):
    return {"trigger": "minecraft:placed_block", "conditions": {"location":
        {"type": "minecraft:match_block", "blocks": f"nookworks:{block}"}}}


def placed_any(tag):
    """Place a block of this block tag: the tag can hold other mods' blocks too, as farmersdelight:rice."""
    return {"trigger": "minecraft:placed_block", "conditions": {"location":
        {"type": "minecraft:location_check", "predicate": {"block": {"blocks": f"#nookworks:{tag}"}}}}}


def used_on_entity(item):
    return {"trigger": "minecraft:player_interacted_with_entity", "conditions": {"item": {"items": f"nookworks:{item}"}}}


def shorn(item):
    """Shear a sheep with this item: the click counts only when the sheep comes away shorn (a villager traded with
    while holding them is no shearing)."""
    return {"trigger": "minecraft:player_interacted_with_entity", "conditions": {
        "item": {"items": f"nookworks:{item}"},
        "entity": {"type": "minecraft:entity_properties", "entity": "this",
                   "predicate": {"minecraft:type_specific/sheep": {"sheared": True}}}}}


def by_the_mod():
    """Awarded by the mod's own code, when no vanilla trigger tells the deed from a mere click (a mob bottled, not a
    boat boarded with the bottle in hand; a field sprinkled with wood vinegar, not a Distiller loaded with it)."""
    return {"trigger": "minecraft:impossible"}


def riding(entity):
    return {"trigger": "minecraft:started_riding", "conditions": {"player":
        {"type": "minecraft:entity_properties", "entity": "this",
         "predicate": {"minecraft:vehicle": {"minecraft:entity_type": f"nookworks:{entity}"}}}}}


HAMMERS = ("stone_hammer", "copper_hammer", "iron_hammer", "golden_hammer", "diamond_hammer", "netherite_hammer", "velarium_hammer")
VELARIUM = ("velarium_sword", "velarium_pickaxe", "velarium_axe", "velarium_shovel", "velarium_hoe", "velarium_hammer",
            "velarium_helmet", "velarium_chestplate", "velarium_leggings", "velarium_boots")
CHAIRS = [f"{w}_chair" for w in ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak",
                                 "crimson", "warped", "bamboo", "dawnwood")]

# id: (parent, icon, frame, criteria)
#
# Each hangs from what it really takes, so the tab reads in the order things can be done: off the root only what can be
# made from the start, out of vanilla's materials; the rest under the step of ours it needs (the machine it is made in,
# the material it is made of). A thing that takes two of ours hangs from the one that comes later in play, and its
# line or a sibling says the other (brass from the Retort, its zinc sifted out of gravel). Vanilla's own gates - the
# Nether's quartz for a comparator, diamonds - are vanilla's to show: a thing behind only those stays off the root.
TREE = {
    # from the start
    "rope": ("root", "rope", "task", {"rope": has("rope", "thin_rope")}),
    "filament_bulb": ("rope", "filament_bulb", "task", {"bulb": has("filament_bulb")}),
    "dried_fish": ("rope", "dried_fish", "task", {"fish": has("dried_fish")}),
    "seat": ("root", "oak_chair", "task", {"seat": riding("seat")}),
    "hammer": ("root", "iron_hammer", "task", {"hammer": has(*HAMMERS)}),
    "nourished_soil": ("root", "nourished_soil", "task", {"soil": has("nourished_soil")}),
    "flour": ("root", "flour", "task", {"flour": has("flour")}),
    # under the salt it takes, as udon is: salt comes long after flour (a Generator, a Retort, the Sifter)
    "bread": ("salt", "minecraft:bread", "task", {"risen": has("risen_dough")}),
    "oil_press": ("root", "vegetable_oil_bottle", "task", {"oil": has("vegetable_oil_bottle", "vegetable_oil_bucket")}),
    "oil_lamp": ("oil_press", "oil_lamp", "task", {"lamp": has("oil_lamp")}),
    "tonkatsu": ("oil_press", "tonkatsu", "goal", {"tonkatsu": has("tonkatsu")}),
    "copper_pipe": ("root", "copper_pipe", "task", {"pipe": has("copper_pipe")}),
    "workbench": ("root", "iron_workbench", "task", {"workbench": has("iron_workbench", "brass_workbench")}),
    "storage": ("root", "iron_storage_barrel", "task",
                {"storage": has("iron_storage_barrel", "gold_storage_barrel", "diamond_storage_barrel", "netherite_storage_barrel",
                                "velarium_storage_barrel")}),
    "storage_upgrade": ("storage", "iron_storage_upgrade", "task",
                        {"upgrade": has("iron_storage_upgrade", "gold_storage_upgrade", "diamond_storage_upgrade",
                                        "netherite_storage_upgrade", "velarium_storage_upgrade")}),
    "magnet": ("root", "magnet", "task", {"magnet": has("magnet", "advanced_magnet")}),
    "lode_pearl": ("root", "lode_pearl", "task", {"pearl": has("lode_pearl")}),
    "dark_matter": ("root", "dark_matter", "task", {"dark": placed("dark_matter")}),
    "mob_bottle": ("root", "mob_bottle", "task", {"bottled": by_the_mod()}),  # MobBottleItem.tryCapture
    "spawn_egg_brewer": ("mob_bottle", "spawn_egg_brewer", "task", {"brewer": has("spawn_egg_brewer")}),
    "experience": ("root", "experience_bucket", "task", {"bucket": has("experience_bucket")}),
    "repair_box": ("experience", "repair_box", "task", {"box": has("repair_box")}),
    "item_sorter": ("root", "item_sorter", "task", {"sorter": has("item_sorter", "precision_sorter")}),
    "sorter_gear": ("item_sorter", "gold_sorter_gear", "task",
                    {"gear": has("gold_sorter_gear", "diamond_sorter_gear", "netherite_sorter_gear", "velarium_sorter_gear")}),
    # sifting, and what turns up in it
    "sifter": ("root", "sifter", "task", {"sifter": has("sifter")}),
    # by a tag of its own, which takes the common onion tag in: with Farmer's Delight the Sifter gives its onions instead
    "new_crops": ("sifter", "corn", "task", {"crop": {"trigger": "minecraft:inventory_changed",
                                                       "conditions": {"items": [{"items": "#nookworks:new_crops"}]}}}),
    # with Farmer's Delight the Sifter gives its rice instead of ours: planting either counts
    "rice": ("sifter", "brown_rice", "task", {"rice": placed_any("rice_crops")}),
    "white_rice": ("rice", "white_rice", "task", {"rice": has("white_rice")}),
    "cooked_rice": ("white_rice", "cooked_rice", "task", {"rice": has("cooked_rice")}),
    "rare_seed": ("sifter", "rare_seed", "task", {"seed": has("rare_seed")}),
    "dawnwood": ("rare_seed", "dawnwood_log", "goal", {"log": has("dawnwood_log")}),
    "zinc": ("sifter", "raw_zinc", "task", {"zinc": has("raw_zinc")}),
    "ventilation": ("zinc", "ventilation_fan", "task", {"air": has("range_hood", "ventilation_fan")}),
    # machines, and the power they run on
    "machine_chassis": ("root", "machine_chassis", "task", {"machine_chassis": has("machine_chassis")}),
    "generator": ("machine_chassis", "generator", "task", {"generator": has("generator")}),
    "energy_cable": ("generator", "energy_cable", "task", {"cable": has("energy_cable")}),
    "electric_furnace": ("generator", "electric_furnace", "task", {"furnace": has("electric_furnace")}),
    "crusher": ("generator", "crusher", "task", {"crusher": has("crusher")}),
    "wood_chips": ("crusher", "wood_chips", "task", {"chips": has("wood_chips")}),
    "spawner_controller": ("generator", "spawner_controller", "task", {"controller": has("spawner_controller")}),
    "barter_terminal": ("generator", "barter_terminal", "task", {"terminal": has("barter_terminal")}),
    # the Sealed Retort (a powered machine), and what is made in it
    "retort": ("generator", "retort", "task", {"retort": has("retort")}),
    # WoodVinegarItem.useOn, once a field has taken it: any click with the bottle on a block (loading a Distiller) counted
    "wood_vinegar": ("retort", "wood_vinegar", "task", {"vinegar": by_the_mod()}),
    "crude_salt": ("retort", "crude_salt", "task", {"crude_salt": has("crude_salt")}),
    "salt": ("crude_salt", "salt", "task", {"salt": has("salt")}),
    "udon": ("salt", "udon", "task", {"udon": has("udon")}),
    "nukazuke": ("salt", "carrot_nukazuke", "task", {"pickle": has("carrot_nukazuke", "beetroot_nukazuke")}),
    "cinnabar": ("retort", "minecraft:cinnabar", "task", {"cinnabar": has("minecraft:cinnabar")}),
    "mercury": ("cinnabar", "mercury_bucket", "task", {"mercury": has("mercury_bucket")}),
    "sulfur_dust": ("cinnabar", "sulfur_dust", "task", {"dust": has("sulfur_dust")}),
    "if_world": ("mercury", "if_mirror", "goal", {"if": {"trigger": "minecraft:changed_dimension", "conditions": {"to": "nookworks:if"}}}),
    "amalgam": ("mercury", "gold_amalgam", "task", {"amalgam": has("gold_amalgam", "iron_amalgam", "copper_amalgam", "mixed_amalgam")}),
    "distilled": ("amalgam", "distiller", "task", {"distiller": has("distiller")}),
    "velarium_nugget": ("distilled", "velarium_nugget", "goal", {"nugget": has("velarium_nugget")}),
    "velarium_ingot": ("velarium_nugget", "velarium_ingot", "task", {"ingot": has("velarium_ingot")}),
    "tempered": ("velarium_ingot", "tempered_velarium_ingot", "task", {"tempered": has("tempered_velarium_ingot")}),
    "velarium_gear": ("tempered", "velarium_chestplate", "challenge", {"gear": has(*VELARIUM)}),
    # brass, and what is built of it
    "brass": ("retort", "brass_ingot", "task", {"brass": has("brass_ingot")}),
    "sight_glass": ("brass", "sight_glass", "task", {"glass": has("sight_glass")}),
    "copper_tank": ("sight_glass", "copper_tank", "task", {"tank": has("copper_tank")}),
    "trading_counter": ("brass", "trading_counter", "task", {"counter": placed("trading_counter")}),
    "wool_shears": ("brass", "wool_shears", "task", {"shorn": shorn("wool_shears")}),
    "daemon_door": ("dawnwood", "daemon_door", "task", {"door": placed("daemon_door")}),
    "electrolytic_cell": ("brass", "electrolytic_cell", "task", {"cell": has("electrolytic_cell")}),
    "caustic_soda": ("electrolytic_cell", "caustic_soda", "task", {"soda": has("caustic_soda")}),
    "biodiesel": ("caustic_soda", "biodiesel_bucket", "task", {"biodiesel": has("biodiesel_bottle", "biodiesel_bucket")}),
    # under the fuel it alone burns, the last of what it takes in play; its line names the Generator and tank it is built of
    "diesel_generator": ("biodiesel", "diesel_generator", "task", {"engine": has("diesel_generator")}),
    "wood_pulp": ("caustic_soda", "wood_pulp", "task", {"pulp": has("wood_pulp")}),
    "fine_paper": ("wood_pulp", "fine_paper", "goal", {"paper": has("fine_paper")}),
    "poster": ("fine_paper", "poster", "task", {"poster": has("poster")}),
    "printing_press": ("poster", "printing_press", "task", {"press": has("printing_press")}),
    "label": ("printing_press", "label", "task", {"label": has("label")}),
}

# The part of the mod each advancement is of (src/main/resources/nookworks/features.json): a pack that switches the part
# off has these not loaded, with every advancement under them. A part's advancements hang from the root or from their
# own part's, so switching one part off takes none of another's with it.
FEATURE = {
    "hammer": "hammers",
    "storage": "storage", "storage_upgrade": "storage",
    "magnet": "magnets",
    "lode_pearl": "lode_pearl",
    "dark_matter": "dark_matter",
    "poster": "printing", "printing_press": "printing", "label": "printing",
    "rice": "kitchen", "white_rice": "kitchen", "cooked_rice": "kitchen", "nukazuke": "kitchen", "tonkatsu": "kitchen",
    "new_crops": "kitchen", "flour": "kitchen", "bread": "kitchen", "udon": "kitchen",
    "item_sorter": "sorting", "sorter_gear": "sorting",
    "experience": "liquid_experience", "repair_box": "liquid_experience",
    "mob_bottle": "mob_bottle", "spawn_egg_brewer": "mob_bottle",
    "spawner_controller": "spawner_control",
    "daemon_door": "daemon_door",
    "if_world": "if_world",
    "barter_terminal": "barter_terminal",
    "trading_counter": "trading_counter",
}


def features_of(name):
    """The parts an advancement needs on: its own, and those of everything above it."""
    if name not in TREE:
        return []
    parent = TREE[name][0]
    own = [FEATURE[name]] if name in FEATURE else []
    return [f for f in features_of(parent) if f not in own] + own


def advancement(parent, icon, frame, criteria):
    display = {"icon": {"id": icon if ":" in icon else f"nookworks:{icon}"}}
    return {"criteria": criteria, "display": display, "requirements": [list(criteria)], "sends_telemetry_event": False,
            "parent": f"nookworks:{parent}", "_frame": frame}


def main():
    written = 0
    for name, (parent, icon, frame, criteria) in TREE.items():
        data = advancement(parent, icon, frame, criteria)
        data.pop("_frame")
        features = features_of(name)
        if features:
            data = {"neoforge:conditions": [{"type": "nookworks:feature", "feature": f} for f in features], **data}
        display = data["display"]
        display["title"] = {"translate": f"advancements.nookworks.{name}.title"}
        display["description"] = {"translate": f"advancements.nookworks.{name}.description"}
        if frame != "task":
            display["frame"] = frame
        (OUT / f"{name}.json").write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))
        written += 1
    print(f"wrote {written} advancements (root is kept by hand)")


if __name__ == "__main__":
    main()
