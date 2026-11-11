"""Does anything of ours creak? "No creaking wood, for anything" is the user's rule (DECISIONS.md, "Sound is left to
me"), and a script cannot hear a creak. AudioSet has the labels Creak and Squeak: this asks the tagger (AST, as
judge.py) how much of each it hears in every sound of ours, after asking it the same of vanilla sounds that do creak
(the Creaking's, a wooden door, a chest's lid) - if it does not hear those, its silence about ours means nothing.

    <sound_judges>/venv/Scripts/python tools/sounds/creak.py [--nooktech PATH/TO/nook_technology/mod]
"""
import os
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bench  # noqa: E402
import judge  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]


def main(args):
    import torch
    from transformers import ASTFeatureExtractor, ASTForAudioClassification
    name = "MIT/ast-finetuned-audioset-10-10-0.4593"
    model, fe = ASTForAudioClassification.from_pretrained(name).eval(), ASTFeatureExtractor.from_pretrained(name)
    labels = [model.config.id2label[i] for i in range(len(model.config.id2label))]
    want = [labels.index("Creak"), labels.index("Squeak")]

    def hear(paths_rates):
        out = []
        for i in range(0, len(paths_rates), 8):
            waves = []
            for path, rate in paths_rates[i:i + 8]:
                w = judge.decode(path, 16000, rate)
                waves.append(w if len(w) > 400 else np.zeros(1600, dtype=np.float32))
            with torch.no_grad():
                p = torch.sigmoid(model(**fe(waves, sampling_rate=16000, return_tensors="pt")).logits).numpy()
            out += [float(max(row[want])) for row in p]
        return np.array(out)

    refs = bench.vanilla()
    groups = {"the Creaking (mob/creaking, block/creaking_heart)": [p for n, p in refs if n.startswith(("mob/creaking/", "block/creaking_heart/"))],
              "wooden doors and trapdoors": [p for n, p in refs if n.startswith(("block/wooden_door/", "block/wooden_trapdoor/"))],
              "chest lids": [p for n, p in refs if n.startswith("block/chest/")],
              "stone and gravel (should not creak)": [p for n, p in refs if n.startswith(("block/stone/", "block/gravel/", "dig/stone", "dig/gravel"))]}
    print("vanilla, for what the tagger's word is worth:")
    for label, paths in groups.items():
        p = hear([(q, 1.0) for q in paths[:60]])
        print(f"  {label:52s} {len(p):3d} files  mean {p.mean():.3f}  over 0.1: {np.mean(p > 0.1):4.0%}  highest {p.max():.2f}")
    mine = bench.ours(ROOT / "src/main/resources/assets/nookworks", "nookworks")
    if "--nooktech" in args:
        mine += bench.ours(Path(args[args.index("--nooktech") + 1]) / "src/main/resources/assets/nooktech", "nooktech")
    p = hear([(path, rate) for _, _, path, rate in mine])
    print(f"ours: {len(p)} files  mean {p.mean():.3f}  over 0.1: {np.mean(p > 0.1):4.0%}  highest {p.max():.2f}")
    for (event, file, _, _), q in sorted(zip(mine, p), key=lambda kv: -kv[1])[:12]:
        if q > 0.03:
            print(f"  {q:.2f}  {file}")


if __name__ == "__main__":
    os.environ.setdefault("HF_HOME", str(judge.HOME / "hf"))
    main(sys.argv[1:])
