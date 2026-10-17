"""The Spawner Link Sender's and Receiver's resting and working looks, built from their hand-finished faces.

The faces are hand-finished and show the link at work: a cross of red (sender) or teal (receiver) lines lit up on
black steel. They now light only while a Spawner Controller runs through them, as the controller's screen does, so the
resting blocks need the same faces with the lines dark; and the working ones a glow over their lines.

Only the lines are touched. Every pixel of steel is copied across unchanged:

- spawner_link_{sender,receiver}_{face}_off.png   <- the face, its lines dimmed (the block at rest)
- spawner_link_{sender,receiver}_{face}_glow.png  <- the face's lines alone, on nothing (laid over the working block,
                                                     giving off light, so the lines glow in the dark)

It also writes the models (the resting cube; the working cube with a glowing layer a hair over each face) and the
blockstates (facing and lit). Run from the repo root:  python tools/textures/gen_spawner_links.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
BLOCK = ASSETS / "textures/block"
FACES = ("front", "side", "top", "bottom")
# the cube's faces and the texture each shows, as the models have always had them (the front to the south)
SIDES = {"down": "bottom", "up": "top", "north": "side", "south": "front", "west": "side", "east": "side"}
GLOW = 12
LIFT = 0.02  # how far the glowing layer stands off its face


def is_line(pixel):
    """A pixel of the lit lines, not the steel: the steel and its studs are grey, the lines coloured - even where they
    cross and go near white, they keep their tint."""
    r, g, b = pixel[:3]
    return max(r, g, b) - min(r, g, b) > 30


def dimmed(image):
    out = image.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            p = px[x, y]
            if is_line(p):
                px[x, y] = tuple(int(c * 0.32) + 8 for c in p[:3]) + p[3:]
    return out


def glow(image):
    out = Image.new("RGBA", image.size, (0, 0, 0, 0))
    src = image.load()
    px = out.load()
    for y in range(image.height):
        for x in range(image.width):
            if is_line(src[x, y]):
                px[x, y] = src[x, y]
    return out


def textures(block, suffix=""):
    return {"particle": f"nookworks:block/{block}_front{suffix}",
            **{side: f"nookworks:block/{block}_{face}{suffix}" for side, face in SIDES.items()}}


def working_model(block):
    faces = {side: {"uv": [0, 0, 16, 16], "texture": f"#{side}", "cullface": side} for side in SIDES}
    elements = [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]
    lifted = {"down": ([0, -LIFT, 0], [16, -LIFT, 16]), "up": ([0, 16 + LIFT, 0], [16, 16 + LIFT, 16]),
              "north": ([0, 0, -LIFT], [16, 16, -LIFT]), "south": ([0, 0, 16 + LIFT], [16, 16, 16 + LIFT]),
              "west": ([-LIFT, 0, 0], [-LIFT, 16, 16]), "east": ([16 + LIFT, 0, 0], [16 + LIFT, 16, 16])}
    for side, (frm, to) in lifted.items():
        elements.append({"from": frm, "to": to, "light_emission": GLOW, "name": "glow",
                         "faces": {side: {"uv": [0, 0, 16, 16], "texture": f"#{side}_glow", "cullface": side}}})
    tex = textures(block)
    tex.update({f"{side}_glow": f"nookworks:block/{block}_{face}_glow" for side, face in SIDES.items()})
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": tex, "elements": elements}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    for block in ("spawner_link_sender", "spawner_link_receiver"):
        for face in FACES:
            lit = Image.open(BLOCK / f"{block}_{face}.png").convert("RGBA")
            dimmed(lit).save(BLOCK / f"{block}_{face}_off.png")
            glow(lit).save(BLOCK / f"{block}_{face}_glow.png")
        write_json(ASSETS / f"models/block/{block}.json", {"parent": "minecraft:block/cube", "textures": textures(block, "_off")})
        write_json(ASSETS / f"models/block/{block}_on.json", working_model(block))
        variants = {}
        for facing, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
            for lit in (False, True):
                variant = {"model": f"nookworks:block/{block}" + ("_on" if lit else "")}
                if y:
                    variant["y"] = y
                variants[f"facing={facing},lit={str(lit).lower()}"] = variant
        write_json(ASSETS / f"blockstates/{block}.json", {"variants": variants})
        write_json(ASSETS / f"items/{block}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:block/{block}_on"}})
    print("wrote the spawner links' resting and working looks")


if __name__ == "__main__":
    main()
