"""
The ports drawn where a pipe of ours meets a machine whose panels are set back a pixel (the cabinets, the round tanks),
so the pipe's collar, which ends at the block's edge, does not stand a pixel off the machine in the air (the user,
2026-10-31: 「口金直していいよ」). A flange 10 px across and 1 deep, as wide as the Copper and Mercury Pipes' collars, on
each side a pipe joins (the port_<side> states, dev.nookworks.fluid.PipePorts); the Experience Pipe's narrower collar
sits on it as on a plate.

Writes models/block/pipe_port.json and adds the port parts to each machine's blockstate, a variants one turned into
multipart first. The generators that write those blockstates (gen_machine_models.py, textures/gen_experience.py) call
with_ports themselves; run this after editing a hand-written one (the Generator's, the Retort's):
    python tools/gen_pipe_ports.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
HORIZONTAL = ("north", "east", "south", "west")

# block -> the sides it shows a port on: where its face under a pipe is set back a pixel (a round tank's plinth and
# lid reach the edge above and below; a machine with a stack or a dome on top has nothing there for a pipe to meet).
# The Java side lists the same sides (PipePorts.add in each block).
PORTS = {
    "electric_furnace": HORIZONTAL + ("up", "down"),
    "electrolytic_cell": HORIZONTAL + ("up", "down"),
    "generator": HORIZONTAL + ("up", "down"),
    "retort": HORIZONTAL + ("up", "down"),
    "mercury_tank": HORIZONTAL + ("up", "down"),
    "experience_tank": HORIZONTAL + ("up", "down"),
    "diesel_generator": HORIZONTAL + ("down",),
    "distiller": HORIZONTAL + ("down",),
    "water_pump": HORIZONTAL + ("down",),
    "stirring_tank": HORIZONTAL,
    "copper_tank": HORIZONTAL,
    # the finned body stands 2-3 px in from the edge (its hood and flange reach the top and bottom ones): a deeper port
    # (the user, 2026-11-10: 「パイプと冷却器の間に隙間ができる」, for nookTechnology's pipe off a Condenser)
    "condenser": HORIZONTAL,
}
# a block whose port is not the flat flange: the model it shows instead
PORT_MODELS = {"condenser": "pipe_port_condenser"}
# a side a block shows no port on while it faces that way: the Experience Tank's tap is on its front
NOT_FACING = {"experience_tank"}
TURN = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}


def port_model():
    """The flange, to the north: its face against the pipe's collar at the block's edge, its back on the panel."""
    def face(uv):
        return {"texture": "#iron", "uv": uv}
    return {"parent": "minecraft:block/block", "textures": {"particle": "nookworks:block/black_iron", "iron": "nookworks:block/black_iron"},
            "elements": [{"from": [3, 3, 0], "to": [13, 13, 1], "faces": {
                "north": face([3, 3, 13, 13]), "south": face([3, 3, 13, 13]),
                "east": face([0, 3, 1, 13]), "west": face([15, 3, 16, 13]),
                "up": face([3, 0, 13, 1]), "down": face([3, 15, 13, 16])}}]}


def condenser_port_model():
    """
    The flange, with a neck behind it back to the Condenser's body (z 1..3, 8 across and 7 high: its top and bottom
    land between the fins' faces, so no face of it lies in one plane with a fin's and flickers).
    """
    model = port_model()
    def face(uv):
        return {"texture": "#iron", "uv": uv}
    # its back drawn too, though the body covers it: should the body ever change, no hole shows (check_model_faces)
    model["elements"].append({"from": [4, 4, 1], "to": [12, 11, 3], "faces": {
        "east": face([13, 5, 15, 12]), "west": face([1, 5, 3, 12]),
        "up": face([4, 1, 12, 3]), "down": face([4, 13, 12, 15]), "south": face([4, 5, 12, 12])}})
    return model


def as_multipart(state):
    """A blockstate's variants as multipart parts, each under the conditions its key names."""
    if "multipart" in state:
        return state["multipart"]
    parts = []
    for key, variant in state["variants"].items():
        when = dict(pair.split("=") for pair in key.split(",")) if key else {}
        for applied in (variant if isinstance(variant, list) else [variant]):
            parts.append({"when": when, "apply": applied} if when else {"apply": applied})
    return parts


def with_ports(name, state):
    """{@code state} (a blockstate's JSON) with the port parts of block {@code name} added, any old ones dropped."""
    if name not in PORTS:
        return state
    models = {"nookworks:block/pipe_port"} | {"nookworks:block/" + m for m in PORT_MODELS.values()}
    parts = [part for part in as_multipart(state) if part["apply"].get("model") not in models]
    port = "nookworks:block/" + PORT_MODELS.get(name, "pipe_port")
    for side in PORTS[name]:
        when = {f"port_{side}": "true"}
        if name in NOT_FACING and side in HORIZONTAL:
            when["facing"] = "|".join(other for other in HORIZONTAL if other != side)
        parts.append({"when": when, "apply": {"model": port, **TURN[side]}})
    return {"multipart": parts}


def write_json(path, data):
    """Written in the line endings the file had (some blockstates are CRLF): a run that changes nothing changes no file."""
    text = json.dumps(data, indent=2) + "\n"
    if path.exists() and b"\r\n" in path.read_bytes():
        text = text.replace("\n", "\r\n")
    path.write_bytes(text.encode("utf-8"))


def main():
    write_json(ASSETS / "models/block/pipe_port.json", port_model())
    write_json(ASSETS / "models/block/pipe_port_condenser.json", condenser_port_model())
    for name in PORTS:
        path = ASSETS / f"blockstates/{name}.json"
        write_json(path, with_ports(name, json.loads(path.read_text(encoding="utf-8"))))
    print(f"wrote the pipe port and the ports of {len(PORTS)} blockstates")


if __name__ == "__main__":
    main()
