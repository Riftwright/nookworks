"""Recipe-book unlocks for every recipe of ours, the way vanilla does it.

A recipe only shows in the vanilla recipe book once it is unlocked, and vanilla unlocks a recipe with a hidden
advancement at data/<ns>/advancement/recipes/<category>/<recipe>.json: granted when you pick up one of its
ingredients (or already have the recipe). Without these, anyone playing without JEI never sees our recipes.

This writes one for every recipe in data/nookworks/recipe that has none yet (the Dawnwood set already has
vanilla's own). Run from the repo root after adding recipes:  python tools/gen_recipe_unlocks.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/nookworks"
CATEGORY = {"building": "building_blocks", "redstone": "redstone", "equipment": "tools", "food": "food", "misc": "misc"}



def write_kept(path, text):
    """Written in the file's own line endings (the repo holds both; a new file gets LF): run on another OS, nothing changes."""
    newline = "\r\n" if path.exists() and b"\r\n" in path.read_bytes() else "\n"
    path.write_text(text, encoding="utf-8", newline=newline)

def ingredients(recipe):
    """Item ids and #tags a recipe takes, in order, without repeats."""
    found = []

    def add(value):
        if isinstance(value, str):
            if value not in found:
                found.append(value)
        elif isinstance(value, list):
            for v in value:
                add(v)
        elif isinstance(value, dict):
            for key in ("item", "tag", "items"):
                if key in value:
                    add(("#" + value[key]) if key == "tag" else value[key])

    for key in ("key",):
        if key in recipe:
            for v in recipe[key].values():
                add(v)
    for key in ("ingredients", "ingredient", "base", "addition", "template", "input", "material"):
        if key in recipe:
            add(recipe[key])
    return found


def main():
    existing = {p.stem for p in (DATA / "advancement/recipes").rglob("*.json")} if (DATA / "advancement/recipes").exists() else set()
    written = 0
    for path in sorted((DATA / "recipe").glob("*.json")):
        name = path.stem
        if name in existing:
            continue
        recipe = json.loads(path.read_text(encoding="utf-8"))
        items = ingredients(recipe)[:4]
        if not items:
            continue
        criteria = {"has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipes": f"nookworks:{name}"}}}
        for i, item in enumerate(items):
            criteria[f"has_{i}"] = {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}}
        advancement = {"parent": "minecraft:recipes/root", "criteria": criteria,
                       "requirements": [list(criteria.keys())], "rewards": {"recipes": [f"nookworks:{name}"]}}
        category = CATEGORY.get(recipe.get("category", "misc"), "misc")
        out = DATA / "advancement/recipes" / category / f"{name}.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        write_kept(out, json.dumps(advancement, indent=2) + "\n")
        written += 1
    print(f"wrote {written} recipe unlocks")


if __name__ == "__main__":
    main()
