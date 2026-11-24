"""The pictures beside a setting's explanation on the settings screen (client/gui/NookworksConfigScreen,
client/SettingsGroups.PICTURES): shots from the agent client, cut to 16:9 and made 320x180.

They are game screenshots, as the guide's pictures are - no texture file of vanilla's is copied - and hold no words,
so one picture serves every language (the two with words, the chest's Sort buttons and the Create World screen's
button, are drawn by the screen itself in the player's language).

How they were shot (2026-11-22, in the worktree's agent client, world CookTest, a grass set at x 88..112, z 96..120,
noon, HUD on for the hand, off for the Hub Stones; the Jade box at the top and the hotbar are cut away):
- hammer_3x3     a stone wall x 93..103, y 200..205, z 112; its middle dug in survival with an iron hammer from
                 z 108.5; shot from (98.5, 200, 105) looking at (98.5, 202.5, 112), the drops cleared
- building_wand  oak planks x 95..101, z 99..102 on the grass; the Building Wand aimed at their top from
                 (98.5, 203, 97.5), field of view 90, so its preview frames the whole layer it would lay
- squint_hand    from (108.5, 200, 98.5) looking at the far poster wall near (14, 204, 0), an empty hand, the Squint
                 key (C) held: the raised hand at the top of the view
- hub_stone_flow Hub Stones x 89..95, z 105 and x 95, z 106..108, a redstone block at x 88; seen from (92.5, 203,
                 101.5), HUD off, shot as the pulse of light runs along

Run from the repo root:  python tools/textures/settings_pictures.py [SHOTS_DIR]   (default run-agent/screenshots)
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/nookworks/textures/gui/settings"
SIZE = (320, 180)

# picture: (the shot it is cut from, the 16:9 box in its 1280x720 frame)
PICTURES = {
    "hammer_3x3": ("scene_a4", (160, 40, 1120, 580)),
    "building_wand": ("scene_b7", (160, 95, 1120, 635)),
    "squint_hand": ("scene_c5", (0, 0, 1120, 630)),
    "hub_stone_flow": ("scene_d4", (200, 150, 1120, 667)),
}


def main():
    shots = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "run-agent/screenshots"
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (shot, box) in PICTURES.items():
        w, h = box[2] - box[0], box[3] - box[1]
        assert abs(w * 9 - h * 16) <= 16, f"{name}: {w}x{h} is not 16:9"
        img = Image.open(shots / f"{shot}.png").convert("RGB").crop(box).resize(SIZE, Image.LANCZOS)
        img.save(OUT / f"{name}.png", optimize=True)
        print(f"{name}.png from {shot}.png {box}")


if __name__ == "__main__":
    main()
