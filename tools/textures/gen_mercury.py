"""Generate the mercury process textures (all original pixel art; machine overlays are transparent and are laid
over vanilla iron/brick/deepslate textures by the models). Deterministic.

Blocks: (the liquid itself is gen_liquids.py's; the Stirring Tank and the distillation tower are drawn with the
        other machines in gen_machine_faces.py)
        (the Sealed Retort's and Mercury Tank's own faces are hand-finished; see HAND_FINISHED.md,
        and the Mercury Pipe has a script of its own in gen_mercury_pipe.py)
Items:  mercury_bucket_fill (overlay on the vanilla bucket); the material items are in gen_materials.py

Run from the repo root:  python tools/textures/gen_mercury.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

import itemkit

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"
ITEM = ROOT / "src/main/resources/assets/nookworks/textures/item"

CLEAR = (0, 0, 0, 0)
OUTLINE = (30, 30, 36, 255)


def rgba(r, g, b, a=255):
    return (int(max(0, min(255, r))), int(max(0, min(255, g))), int(max(0, min(255, b))), a)


def save(img, path, frametime=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    if frametime:
        path.with_suffix(".png.mcmeta").write_text(json.dumps({"animation": {"frametime": frametime}}) + "\n")


# ---- liquid mercury: bright, mirror-like silver with slow drifting highlights ----
def mercury_frames(size, frames, flow):
    img = Image.new("RGBA", (size, size * frames))
    for f in range(frames):
        t = f / frames * 2 * math.pi
        for y in range(size):
            for x in range(size):
                yy = y - (f * size / frames if flow else 0)
                v = (math.sin((x * 0.55) + t) + math.sin((yy * 0.45) - t * (2 if flow else 1)) + math.sin((x + yy) * 0.3 + t)) / 3
                base = 176 + 44 * v
                if v > 0.62:
                    base = 238
                img.putpixel((x, y + f * size), rgba(base - 6, base - 2, base + 6))
    return img


# ---- small helpers for item sprites ----
def bucket_fill():
    """Quicksilver in the bucket's mouth: a mirror, near white where it is lit and dark where it gives back the rim -
    bright enough that the bucket does not read as empty."""
    return itemkit.bucket_fill([(70, 76, 90), (150, 156, 170), (206, 212, 224), (255, 255, 255)])


# ---- machine overlays (transparent, over vanilla textures) ----
def frame(img, colour, inset=0):
    for i in range(inset, 16 - inset):
        for p in ((i, inset), (i, 15 - inset), (inset, i), (15 - inset, i)):
            img.putpixel(p, colour)


if __name__ == "__main__":
    # (the liquid itself is gen_liquids.py's)
    save(bucket_fill(), ITEM / "mercury_bucket_fill.png")
    print("wrote mercury textures")
