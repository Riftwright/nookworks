"""A recipe heard at once, while it is being worked on: its variants rendered and put before two of the judges
(tools/sounds/judge.py says who they are), a line each - what AST calls it, what kind CLAP takes it for, where its own
description ranks among all of ours. Run with the judges' Python:

    <sound_judges>/venv/Scripts/python tools/sounds/hear.py [--nooktech PATH/TO/nook_technology/mod] NAME-PART [NAME-PART ...]

With --nooktech the add-on's recipes are the ones looked through (its gen_sounds.py), else this mod's. Nothing is
written: it hears what gen_sounds.py would make. The judges are not the truth - judge.py's control says how far each
is right about vanilla's own sounds - but they catch what a spectrogram cannot: a clank heard as a drum, an arc as a
belch, a servo as a "boing".
"""
import importlib.util
import os
import sys
from pathlib import Path

import numpy as np
from scipy import signal

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from judge_data import ARTEFACTS, KINDS, OURS  # noqa: E402

ROOT = HERE.parents[1]
os.environ.setdefault("HF_HOME", str(Path(os.environ.get("SOUND_JUDGES", ROOT.parent / "sound_judges")) / "hf"))
PLAYED_AT = {"dice.roll": 1.6, "gondola.rattle": 0.7}


def load(path, name):
    sys.path.insert(0, str(path.parent))
    for cached in ("sfx", "gen_sounds"):
        sys.modules.pop(cached, None)
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    sys.path.pop(0)
    return module


def main(args):
    if "--nooktech" in args:
        at = args.index("--nooktech")
        tec = Path(args[at + 1])
        args = args[:at] + args[at + 2:]
        gen = load(tec / "tools/sounds/gen_sounds.py", "gen_tec")
    else:
        gen = load(HERE / "gen_sounds.py", "gen_nw")
    events = [e for e in gen.SOUNDS if not args or any(a in e for a in args)]
    import torch
    from transformers import ASTFeatureExtractor, ASTForAudioClassification, ClapModel, ClapProcessor
    ast_model = ASTForAudioClassification.from_pretrained("MIT/ast-finetuned-audioset-10-10-0.4593").eval()
    ast_fe = ASTFeatureExtractor.from_pretrained("MIT/ast-finetuned-audioset-10-10-0.4593")
    labels = [ast_model.config.id2label[i] for i in range(len(ast_model.config.id2label))]
    clap_model, clap_proc = ClapModel.from_pretrained("laion/larger_clap_general").eval(), ClapProcessor.from_pretrained("laion/larger_clap_general")

    def feats(got):
        return got if isinstance(got, torch.Tensor) else got.pooler_output

    def text(texts):
        with torch.no_grad():
            e = feats(clap_model.get_text_features(**clap_proc(text=texts, return_tensors="pt", padding=True)))
        return torch.nn.functional.normalize(e, dim=-1).numpy()

    kinds = list(KINDS)
    kind_text = np.stack([text([f"the sound of {p}" for p in KINDS[k][0]]).mean(axis=0) for k in kinds])
    ours_text = dict(zip(OURS, text([f"the sound of {t}" for _, t in OURS.values()])))
    for event in events:
        full = f"{gen.MODID}:{event}"
        kind, words = OURS.get(full, ("?", "?"))
        print(f"\n{event}  [{kind}: {words}]")
        rate = next((r for k, r in PLAYED_AT.items() if k in event), 1.0)
        for v in range(gen.SOUNDS[event][1]):
            x = gen.render(event, v)
            if rate != 1.0:  # as the game plays it
                x = signal.resample_poly(x, 100, int(round(rate * 100)))
            x = x / (np.max(np.abs(x)) + 1e-12) * 0.5
            x16 = signal.resample_poly(x, 160, 441).astype(np.float32)
            x48 = signal.resample_poly(x, 160, 147).astype(np.float32)
            with torch.no_grad():
                p = torch.sigmoid(ast_model(**ast_fe([x16], sampling_rate=16000, return_tensors="pt")).logits)[0].numpy()
                try:
                    inputs = clap_proc(audio=[x48], sampling_rate=48000, return_tensors="pt")
                except TypeError:
                    inputs = clap_proc(audios=[x48], sampling_rate=48000, return_tensors="pt")
                a = torch.nn.functional.normalize(feats(clap_model.get_audio_features(**inputs)), dim=-1).numpy()[0]
            top = np.argsort(-p)[:5]
            order = np.argsort(-(kind_text @ a))
            heard = [kinds[j] for j in order[:3]]
            sims = {e: float(vec @ a) for e, vec in ours_text.items()}
            rank = 1 + sum(1 for s in sims.values() if s > sims.get(full, -9)) if full in sims else 0
            best = max(sims, key=sims.get).split(".", 1)[1]
            art = max(((lab, p[labels.index(lab)]) for lab in ARTEFACTS if lab in labels), key=lambda kv: kv[1])
            mark = "ok " if kind in heard else "?? "
            want = [(lab, p[labels.index(lab)]) for lab in KINDS.get(kind, ([], []))[1] if lab in labels]
            fit = max(want, key=lambda kv: kv[1]) if want else ("-", 0.0)
            print(f"  {v + 1}: {mark}CLAP {'/'.join(heard):26s} desc #{rank:<2d} (first: {best[:26]:26s}) | AST "
                  + ", ".join(f"{labels[j]} {p[j]:.2f}" for j in top[:4]) + f" | fit {fit[0]} {fit[1]:.2f}" + (f" | ! {art[0]} {art[1]:.2f}" if art[1] > 0.15 else ""))


if __name__ == "__main__":
    main(sys.argv[1:])
