"""The item icons no script may write: the files in src/main/resources/assets/nookworks/textures/item/ are their master
copies (HAND_FINISHED.md, beside this file, says where each came from). A generator that used to draw one of them asks
kept() before it saves, and leaves the file as it is.

The block textures listed in HAND_FINISHED.md need no such check: the code that drew them is gone.
"""
from pathlib import Path

ITEMS = {
    # the author's own drawings, chosen over the scripts' on 2026-11-21
    "cooked_rice", "fried_rice", "cream_stew", "udon", "salt_onigiri", "salmon_onigiri", "plated_fried_rice",
    "daemon_door", "french_fries", "dawnwood_door", "plated_stir_fried_vegetables", "destination_panel",
    "stir_fried_vegetables", "tonkatsu", "beaten_eggs", "vegetable_soup", "corn_potage",
    "sulfur_dust", "flour", "tamagoyaki", "rice_bran", "velarium_hoe", "velarium_pickaxe",
    # the hammers: the stone one the author's own drawing, the others Grok's heads on its handle, 2026-11-21
    # (gen_hammers.py, which drew them before, is retired)
    "stone_hammer", "copper_hammer", "iron_hammer", "golden_hammer", "diamond_hammer", "netherite_hammer",
    "velarium_hammer",
    # Gemini's redraw of the liquids in bottles and buckets and of the Mob Bottle, chosen on 2026-11-21
    "biodiesel_bottle_fill", "biodiesel_bucket_fill", "experience_bucket_fill", "liquid_experience_bottle_fill",
    "mercury_bucket_fill", "methanol_bucket_fill", "methanol_fill", "vegetable_oil_bottle_fill",
    "vegetable_oil_bucket_fill", "wood_vinegar_bucket_fill", "wood_vinegar_fill",
    "mob_bottle", "mob_bottle_contents", "mob_bottle_eyes", "mob_bottle_spots",
}


def kept(path) -> bool:
    """True for one of the icons above: the caller must not write it."""
    path = Path(path)
    return path.parent.name == "item" and path.suffix == ".png" and path.stem in ITEMS
