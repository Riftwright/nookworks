"""Every generated file of the mod made again from its script, in an order that works.

Run from the repo root after a Gradle build (two texture scripts and the wood data read the patched game jar):
    python tools/gen_all.py
Afterwards nothing under src/ should differ from what is staged (or, with nothing staged, committed). A file that
does was either edited by hand - put the change into its script, or list it in tools/textures/HAND_FINISHED.md if it
is artwork no script draws - or its script was changed and not run.

The order: the texture scripts (tools/textures/gen_*.py) by name, as none reads what another writes; then the data
scripts; then gen_item_displays.py, last because it adds the in-hand and in-frame transforms to the item models the
others write bare. Left out: make_icon.py and make_logo.py (the desktop shortcut's icon and the page's logo, not the
mod's files) import_posters.py (it takes the poster packs the author supplies, by hand, one at a time) and
tools/sounds/make_sounds.py (it cuts the sounds out of Kenney's packs, downloaded by hand).
"""
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# gen_recycling.py after the machine models: it builds the Recycler's item model from its two halves
DATA = ["gen_machine_models.py", "gen_recycling.py", "gen_rope_ties.py", "gen_wood_data.py", "gen_fine_sifting.py",
        "gen_advancements.py", "gen_recipe_unlocks.py", "gen_feature_conditions.py", "gen_test_structure.py",
        "gen_lang_copies.py"]
LAST = "gen_item_displays.py"


def main():
    scripts = sorted((ROOT / "tools/textures").glob("gen_*.py")) + [ROOT / "tools" / name for name in DATA + [LAST]]
    for script in scripts:
        ran = subprocess.run([sys.executable, str(script)], cwd=ROOT, capture_output=True, text=True)
        if ran.returncode != 0:
            print(ran.stdout + ran.stderr)
            print(f"{script.relative_to(ROOT).as_posix()} failed; stopped there")
            return 1
    changed = subprocess.run(["git", "diff", "--name-status", "--", "src"], cwd=ROOT, capture_output=True, text=True).stdout
    untracked = subprocess.run(["git", "ls-files", "--others", "--exclude-standard", "--", "src"], cwd=ROOT,
                               capture_output=True, text=True).stdout
    changed += "".join(f"?\t{line}\n" for line in untracked.splitlines())
    print(f"ran {len(scripts)} scripts")
    if changed:
        print("these differ from what is staged or committed:\n" + changed)
        return 1
    print("every generated file is as its script makes it")
    return 0


if __name__ == "__main__":
    sys.exit(main())
