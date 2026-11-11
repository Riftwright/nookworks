"""Drafts of the Wrench and Building Wand icons in Nookworks' materials: dark machine steel, copper, brass, walnut
(2026-11-07, three of each for the user to pick from). Writes a comparison sheet, nothing into the mod.

Run from the repo root:  python tools/textures/draft_tool_icons.py OUT.png
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/textures"))
import itemkit  # noqa: E402

ITEM = ROOT / "src/main/resources/assets/nookworks/textures/item"

STEEL = {"1": (52, 56, 66), "2": (80, 88, 102), "3": (114, 124, 140), "4": (160, 170, 184), "5": (214, 222, 232)}
COPPER = {"c": (112, 54, 32), "C": (168, 88, 50), "k": (214, 128, 76), "K": (244, 176, 120)}
BRASS = {"b": (124, 84, 26), "B": (178, 130, 46), "g": (230, 190, 96), "G": (252, 232, 156)}
WOOD = {"w": (72, 44, 26), "W": (108, 70, 38), "v": (146, 98, 56), "V": (184, 136, 84)}
AMETHYST = {"a": (74, 44, 118), "A": (122, 82, 182), "m": (172, 130, 228), "M": (230, 212, 252)}
VEL = {"x": (52, 30, 94), "X": (112, 80, 182), "y": (178, 154, 236), "Y": (228, 218, 252), "t": (92, 210, 172)}
PAL = {**STEEL, **COPPER, **BRASS, **WOOD, **AMETHYST, **VEL}

# --- Wrench A: the spanner as it is, in the workshop's materials - dark steel, a copper-wound grip, a brass eye
WRENCH_A = itemkit.check([
    "................",
    "........445.....",
    "........431.....",
    "........431.....",
    "........321.....",
    "........3211144.",
    ".......32111321.",
    "......3211......",
    ".....kCkC.......",
    "....kCkC........",
    "..BB321.........",
    ".BggB21.........",
    ".g..B1..........",
    ".B..b1..........",
    ".BBbb...........",
    "................",
])

# --- Wrench B: a double-ended spanner - a jaw at each end, a brass band at its waist
_TOP = WRENCH_A[:8]
_FLIP = {"1": "4", "2": "3", "3": "2", "4": "1", "5": "2"}
_BOTTOM = ["".join(_FLIP.get(ch, ".") for ch in reversed(row)) for row in reversed(_TOP)]
WRENCH_B = itemkit.check([
    *_TOP[:7],
    "......gB........",
    ".....Bg.........",
    *_BOTTOM[1:],
])

# --- Wrench C: the spanner's steel head on a walnut handle, a brass ferrule and two brass rivets
WRENCH_C = itemkit.check([
    *WRENCH_A[:7],
    ".....gBb........",
    "....VvW.........",
    "...VgW..........",
    "..VvW...........",
    ".VgW............",
    ".vW.............",
    "................",
    "................",
    "................",
])

# --- Wand A: a walnut rod banded in copper, an amethyst point set in a brass claw
WAND_A = itemkit.check([
    "................",
    "............M...",
    "...........mMa..",
    "..........mAA...",
    ".........mAAa...",
    "........gBAa....",
    ".......gBb......",
    ".......Vw.......",
    "......kC........",
    ".....Vw.........",
    "....VW..........",
    "...kC...........",
    "..VW............",
    "..w.............",
    "................",
    "................",
])

# --- Wand B: a surveyor's staff - walnut banded light and dark, a brass cap and a brass knob at its head
WAND_B = itemkit.check([
    "................",
    "...........gG...",
    "..........gGgb..",
    "...........bb...",
    ".........gB.....",
    "........gB......",
    ".......VW.......",
    "......VW........",
    ".....ww.........",
    "....VW..........",
    "...VW...........",
    "..ww............",
    "..gb............",
    "................",
    "................",
    "................",
])

# --- Wand C: a walnut rod, a copper band, a velarium crystal in a brass claw with a teal glint
WAND_C = itemkit.check([
    "................",
    "............Y...",
    "...........yYx..",
    "..........ytXx..",
    ".........yXXx...",
    "........gBXx....",
    ".......gBb......",
    ".......Vw.......",
    "......VW........",
    ".....kC.........",
    "....VW..........",
    "...VW...........",
    "..VW............",
    "..w.............",
    "................",
    "................",
])


def sprite(rows):
    return itemkit.sprite(rows, PAL)


def sheet(out):
    S = 8
    rows = [
        [Image.open(ITEM / "wrench.png").convert("RGBA"), sprite(WRENCH_A), sprite(WRENCH_B), sprite(WRENCH_C)],
        [Image.open(ITEM / "building_wand.png").convert("RGBA"), sprite(WAND_A), sprite(WAND_B), sprite(WAND_C)],
        [Image.open(ITEM / n).convert("RGBA").crop((0, 0, 16, 16)) for n in ("camera.png", "oil_can.png", "iron_hammer.png", "brass_ingot.png")],
    ]
    gap = 12
    img = Image.new("RGBA", (4 * (16 * S + gap) + gap + 4 * (16 * 2 + 6), 3 * (16 * S + gap) + gap), (139, 139, 139, 255))
    for r, icons in enumerate(rows):
        for c, icon in enumerate(icons):
            img.alpha_composite(icon.resize((16 * S, 16 * S), Image.NEAREST), (gap + c * (16 * S + gap), gap + r * (16 * S + gap)))
            img.alpha_composite(icon.resize((32, 32), Image.NEAREST), (gap + 4 * (16 * S + gap) + c * 38, gap + r * (16 * S + gap)))
    img.save(out)


if __name__ == "__main__":
    sheet(sys.argv[1])
