"""Our sounds put before judges that can hear: models trained on what people call sounds and how people rate them.
Run with the judges' own Python (PyTorch and transformers in a venv of their own, the models under its hf folder):

    <sound_judges>/venv/Scripts/python tools/sounds/judge.py OUT_DIR [--nooktech PATH/TO/nook_technology/mod] [--vanilla] [--quality]

Every judge is first asked about sounds whose answer is known - vanilla's sounds in their own roles (judge_data.CONTROL):
what it scores there is what its word is worth. Then three sets are put to the same questions: ours (NEW), what our
events played before (OLD: the vanilla sounds they borrowed, at the pitch they were played), and the control.

- CLAP (laion/larger_clap_general; sound and words in one space): which of fifteen kinds of sound is this (blind, by the
  sound alone)? how high does its own description rank among all the descriptions? does the new sound fit its machine's
  description better than the borrowed one did? and how much nearer does it lie to "a real recording" than to "a
  synthesized sound"?
- AST (MIT/ast-finetuned-audioset-10-10-0.4593; AudioSet's 527 labels): do the labels it gives fit the kind, and does it
  hear a machine's artefact (mains hum, a sine wave, static, a synthesizer)?
- --vanilla: every vanilla sound embedded too (some minutes), for where ours lie among them in CLAP's space.
- --quality: Audiobox Aesthetics (facebook/audiobox-aesthetics), marks learnt from people's ratings. Held back: the
  recipes are worked on with CLAP's and AST's hearing, never with this one's, so it can say whether that helped.

Vanilla's sounds are read from this machine's asset store as measuring references; nothing of theirs is copied.

Setting the judges up again (about 3.3 GB; the models come from Hugging Face on first use, into <sound_judges>/hf):
    python -m venv <sound_judges>/venv
    <venv python> -m pip install --index-url https://download.pytorch.org/whl/cpu torch torchaudio
    <venv python> -m pip install transformers scipy soundfile requests submitit rich
    <venv python> -m pip install --no-deps audiobox_aesthetics
<sound_judges> is ../sound_judges beside this checkout, or the folder SOUND_JUDGES names.
"""
import json
import os
import subprocess
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bench  # noqa: E402
from judge_data import ARTEFACTS, BORROWED, CONTROL, KINDS, OURS  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
HOME = Path(os.environ.get("SOUND_JUDGES", ROOT.parent / "sound_judges"))
os.environ.setdefault("HF_HOME", str(HOME / "hf"))
ASSETS = bench.ASSETS


def decode(path, sr, rate=1.0, seconds=10.0):
    chain = f"asetrate=44100*{rate},aresample={sr}" if rate != 1.0 else f"aresample={sr}"
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-t", str(seconds / rate), "-af", chain, "-ac", "1", "-f", "f32le", "-"],
                         capture_output=True).stdout
    x = np.frombuffer(raw, dtype=np.float32).copy()
    peak = np.max(np.abs(x)) if len(x) else 0.0
    return x / peak * 0.5 if peak > 0 else x  # one level for all: a judge should not go by loudness


class Vanilla:
    def __init__(self):
        index = max((ASSETS / "indexes").glob("*.json"), key=lambda p: int(p.stem))
        self.objects = json.loads(index.read_text())["objects"]
        self.events = json.loads(self.path("minecraft/sounds.json").read_text())

    def path(self, key):
        h = self.objects[key]["hash"]
        return ASSETS / "objects" / h[:2] / h

    def files(self, event):
        out = []
        for s in self.events[event.replace("minecraft:", "")]["sounds"]:
            name = s if isinstance(s, str) else s["name"]
            if isinstance(s, dict) and s.get("type") == "event":
                out += self.files(name)
            else:
                out.append(self.path(f"minecraft/sounds/{name.replace('minecraft:', '')}.ogg"))
        return list(dict.fromkeys(out))


def old_table(repo, rev, path):
    return json.loads(subprocess.run(["git", "-C", str(repo), "show", f"{rev}:{path}"], capture_output=True, check=True).stdout)


def gather(args):
    """The three sets as lists of dicts: set, event, kind, text, path, rate."""
    van = Vanilla()
    items = []
    mine = bench.ours(ROOT / "src/main/resources/assets/nookworks", "nookworks")
    olds = {"nookworks": old_table(ROOT, "tools-trial", "src/main/resources/assets/nookworks/sounds.json")}
    if "--nooktech" in args:
        tec = Path(args[args.index("--nooktech") + 1])
        mine += bench.ours(tec / "src/main/resources/assets/nooktech", "nooktech")
        olds["nooktech"] = old_table(tec.parent, "main", "mod/src/main/resources/assets/nooktech/sounds.json")
    for event, file, path, rate in mine:
        if event in OURS:
            items.append(dict(set="new", event=event, kind=OURS[event][0], text=OURS[event][1], path=path, rate=rate, name=file.split("/")[-1]))
    for event, (kind, text) in OURS.items():
        modid, key = event.split(":")
        if modid not in olds:
            continue
        borrowed = BORROWED.get(event) or [(s["name"], s.get("pitch", 1.0)) for s in olds[modid][key]["sounds"]
                                           if isinstance(s, dict) and s.get("type") == "event"]
        for vanilla_event, pitch in dict.fromkeys(borrowed):
            for path in van.files(vanilla_event)[:4]:
                items.append(dict(set="old", event=event, kind=kind, text=text, path=path, rate=pitch, name=f"{vanilla_event.replace('minecraft:', '')}@{pitch}"))
    for event, (kind, text) in CONTROL.items():
        for path in van.files(event)[:4]:
            items.append(dict(set="control", event=event, kind=kind, text=text, path=path, rate=1.0, name=event))
    return items


def clap(items, vanilla_too, out_dir):
    import torch
    from transformers import ClapModel, ClapProcessor
    name = "laion/larger_clap_general"
    model, proc = ClapModel.from_pretrained(name).eval(), ClapProcessor.from_pretrained(name)

    def feats(got):
        return got if isinstance(got, torch.Tensor) else got.pooler_output

    def embed_audio(waves):
        out = []
        for i in range(0, len(waves), 16):
            batch = [w if len(w) > 480 else np.zeros(4800, dtype=np.float32) for w in waves[i:i + 16]]
            try:
                inputs = proc(audio=batch, sampling_rate=48000, return_tensors="pt")
            except TypeError:
                inputs = proc(audios=batch, sampling_rate=48000, return_tensors="pt")
            with torch.no_grad():
                e = feats(model.get_audio_features(**inputs))
            out.append(torch.nn.functional.normalize(e, dim=-1).numpy())
        return np.vstack(out)

    def embed_text(texts):
        inputs = proc(text=texts, return_tensors="pt", padding=True)
        with torch.no_grad():
            e = feats(model.get_text_features(**inputs))
        return torch.nn.functional.normalize(e, dim=-1).numpy()

    audio = embed_audio([decode(it["path"], 48000, it["rate"]) for it in items])
    kinds = list(KINDS)
    kind_text = np.stack([embed_text([f"the sound of {p}" for p in KINDS[k][0]]).mean(axis=0) for k in kinds])
    real = embed_text(["a real recording of a sound", "a field recording of an everyday sound"]).mean(axis=0)
    synth = embed_text(["a synthesized sound", "a computer generated sound effect", "an electronic synthesizer tone"]).mean(axis=0)
    ours_text = {e: v for e, v in zip(OURS, embed_text([f"the sound of {t}" for _, t in OURS.values()]))}
    ctrl_text = {e: v for e, v in zip(CONTROL, embed_text([f"the sound of {t}" for _, t in CONTROL.values()]))}
    for it, a in zip(items, audio):
        scores = kind_text @ a
        order = np.argsort(-scores)
        it["kind_heard"] = [kinds[j] for j in order[:3]]
        it["kind_rank"] = int(np.nonzero(np.array(kinds)[order] == it["kind"])[0][0]) + 1
        it["real"] = float(real @ a - synth @ a)
        pool = ctrl_text if it["set"] == "control" else ours_text
        sims = {e: float(v @ a) for e, v in pool.items()}
        it["fit"] = sims[it["event"]]
        it["text_rank"] = 1 + sum(1 for e, s in sims.items() if s > sims[it["event"]])
        it["pool"] = len(pool)
    if vanilla_too:
        cache = out_dir / "vanilla_clap.npz"
        if cache.exists():
            got = np.load(cache, allow_pickle=True)
            v_names, v = list(got["names"]), got["vectors"]
        else:
            refs = bench.vanilla()
            v_names, chunks = [n for n, _ in refs], []
            for i in range(0, len(refs), 64):
                chunks.append(embed_audio([decode(p, 48000) for _, p in refs[i:i + 64]]))
                print(f"  vanilla {i + 64}/{len(refs)}", flush=True)
            v = np.vstack(chunks)
            np.savez(cache, names=np.array(v_names, dtype=object), vectors=v)
        fam = np.array([bench.family(n) for n in v_names])
        sim = v @ v.T
        np.fill_diagonal(sim, -1)
        sizes = {f: int((fam == f).sum()) for f in set(fam)}
        kin = np.nonzero(np.array([sizes[f] >= 3 for f in fam]))[0]
        best = np.argmax(sim, axis=1)
        trust = float(np.mean(fam[best[kin]] == fam[kin]))
        other = np.where(fam[:, None] == fam[None, :], -1, sim)
        v_knn = np.sort(other, axis=1)[:, -5:].mean(axis=1)  # how alike a vanilla sound's five nearest strangers are
        real_v = v @ real - v @ synth
        for it, a in zip(items, audio):
            s = v @ a
            near = np.argsort(-s)[:5]
            it["near"] = [fam[j] for j in near]
            it["out"] = float(np.mean(v_knn > np.sort(s)[-5:].mean()))  # the share of vanilla sounds with nearer neighbours than this has
            it["real_pct"] = float(np.mean(real_v < it["real"]))
        return trust
    return None


def ast(items):
    import torch
    from transformers import ASTFeatureExtractor, ASTForAudioClassification
    name = "MIT/ast-finetuned-audioset-10-10-0.4593"
    model, fe = ASTForAudioClassification.from_pretrained(name).eval(), ASTFeatureExtractor.from_pretrained(name)
    labels = [model.config.id2label[i] for i in range(len(model.config.id2label))]
    known = set(labels)
    missing = sorted({lab for _, labs in KINDS.values() for lab in labs if lab not in known} | {a for a in ARTEFACTS if a not in known})
    if missing:
        print("not AudioSet labels (ignored):", missing)
    art = [labels.index(a) for a in ARTEFACTS if a in known]
    for i in range(0, len(items), 8):
        batch = items[i:i + 8]
        waves = []
        for it in batch:
            w = decode(it["path"], 16000, it["rate"])
            waves.append(w if len(w) > 400 else np.zeros(1600, dtype=np.float32))
        inputs = fe(waves, sampling_rate=16000, return_tensors="pt")
        with torch.no_grad():
            p = torch.sigmoid(model(**inputs).logits).numpy()
        for it, row in zip(batch, p):
            top = np.argsort(-row)[:5]
            it["tags"] = [(labels[j], round(float(row[j]), 3)) for j in top]
            want = [labels.index(lab) for lab in KINDS[it["kind"]][1] if lab in known]
            it["tag_fit"] = bool(set(top) & set(want))
            it["tag_fit_p"] = float(max(row[want])) if want else 0.0
            j = max(art, key=lambda k: row[k])
            it["artefact"] = (labels[j], round(float(row[j]), 3))


def quality(items):
    """Audiobox Aesthetics (Meta): four marks out of ten learnt from people's ratings - PQ production quality, CE
    enjoyment, CU usefulness, PC complexity. No recipe here is tuned against it: it is kept as the judge held back."""
    import torch
    from audiobox_aesthetics.infer import initialize_predictor
    predictor = initialize_predictor()
    for i in range(0, len(items), 16):
        batch = items[i:i + 16]
        waves = []
        for it in batch:
            w = decode(it["path"], 16000, it["rate"])
            if len(w) < 1600:
                w = np.concatenate([w, np.zeros(1600 - len(w), dtype=np.float32)])
            waves.append({"path": torch.from_numpy(w)[None, :], "sample_rate": 16000})
        with torch.no_grad():
            got = predictor.forward(waves)
        for it, g in zip(batch, got):
            it["aes"] = {k: round(float(v), 3) for k, v in g.items()}


def report(items, trust, out_dir):
    def of(name):
        return [it for it in items if it["set"] == name]

    def rate(rows, f):
        return float(np.mean([f(it) for it in rows])) if rows else float("nan")

    lines = ["# Our sounds before judges that can hear", "",
             "Written by tools/sounds/judge.py; read its top for what is asked. control = vanilla's sounds in their own roles (what the judges make of",
             "professionals' work, the ceiling); old = the vanilla sounds our events borrowed; new = ours.", "",
             "## The score", "", "| asked | control | old | new |", "|---|---|---|---|"]
    rows = [("CLAP: names the kind of sound (1 of 15, blind)", lambda it: it["kind_rank"] == 1),
            ("CLAP: the kind among its first three", lambda it: it["kind_rank"] <= 3),
            ("CLAP: its own description ranked first among all", lambda it: it["text_rank"] == 1),
            ("CLAP: its own description among the first five", lambda it: it["text_rank"] <= 5),
            ("AST: a label fitting the kind among its five", lambda it: it["tag_fit"]),
            ("AST: hears a machine's artefact (p > 0.15)", lambda it: it["artefact"][1] > 0.15)]
    for label, f in rows:
        lines.append(f"| {label} | {rate(of('control'), f):.0%} | {rate(of('old'), f):.0%} | {rate(of('new'), f):.0%} |")
    # a judge's word only where it knows the answer: the kinds it gets right in the control six times in ten
    ctrl = of("control")
    knows_clap = {k for k in KINDS if [it for it in ctrl if it["kind"] == k] and rate([it for it in ctrl if it["kind"] == k], lambda it: it["kind_rank"] == 1) >= 0.6}
    knows_ast = {k for k in KINDS if [it for it in ctrl if it["kind"] == k] and rate([it for it in ctrl if it["kind"] == k], lambda it: it["tag_fit"]) >= 0.6}
    for label, f, knows in (("CLAP names the kind, on the kinds it knows (" + ", ".join(sorted(knows_clap)) + ")", lambda it: it["kind_rank"] == 1, knows_clap),
                            ("AST's labels fit, on the kinds it knows (" + ", ".join(sorted(knows_ast)) + ")", lambda it: it["tag_fit"], knows_ast)):
        cells = [rate([it for it in of(s) if it["kind"] in knows], f) for s in ("control", "old", "new")]
        lines.append(f"| {label} | {cells[0]:.0%} | {cells[1]:.0%} | {cells[2]:.0%} |")
        print(f"{label[:70]:70s} control {cells[0]:4.0%}  old {cells[1]:4.0%}  new {cells[2]:4.0%}")
    if items and "aes" in items[0]:
        for axis, meaning in (("PQ", "production quality"), ("CE", "enjoyment"), ("CU", "usefulness")):
            cells = [rate(of(s), lambda it: it["aes"][axis]) for s in ("control", "old", "new")]
            lines.append(f"| Audiobox Aesthetics, {meaning} (out of 10; held back, nothing tuned to it) | {cells[0]:.2f} | {cells[1]:.2f} | {cells[2]:.2f} |")
            print(f"Audiobox {axis}: control {cells[0]:.2f}  old {cells[1]:.2f}  new {cells[2]:.2f}")
    lines.append(f"| CLAP: nearer 'a real recording' than 'a synthesized sound' (mean, higher is more real) | {rate(of('control'), lambda it: it['real']):+.3f} | "
                 f"{rate(of('old'), lambda it: it['real']):+.3f} | {rate(of('new'), lambda it: it['real']):+.3f} |")
    events = list(dict.fromkeys(it["event"] for it in of("new")))
    paired = []
    for e in events:
        new = [it["fit"] for it in of("new") if it["event"] == e]
        old = [it["fit"] for it in of("old") if it["event"] == e]
        if new and old:
            paired.append((e, float(np.mean(new)), float(np.mean(old))))
    better = sum(1 for _, n, o in paired if n > o)
    lines += ["", f"Does the new sound fit its machine's description better than the borrowed one did (CLAP)? In {better} of {len(paired)} events "
              f"(mean fit {np.mean([n for _, n, _ in paired]):.3f} against {np.mean([o for _, _, o in paired]):.3f}).", ""]
    if trust is not None:
        news = of("new")
        lines += [f"Among all of vanilla's sounds in CLAP's space (a vanilla sound's nearest is of its own family {trust:.0%} of the time): ours lie at the "
                  f"{np.median([it['out'] for it in news]):.0%} point of vanilla's own spread on the median, {np.mean([it['out'] > 0.95 for it in news]):.0%} beyond its 95 % point "
                  f"(5 % would be par); on the real-or-synthesized scale ours sit at the {np.median([it['real_pct'] for it in news]):.0%} point of vanilla's "
                  f"({np.mean([it['real_pct'] < 0.05 for it in news]):.0%} under its 5 % point).", ""]
    lines += ["## Each of ours", "",
              "kind: what it is meant as -> what CLAP heard first (over its variants). desc: the rank of its own description among all 65, averaged. fit: new against old.",
              "tags: AST's first labels. Marked ! where the kind was not among CLAP's first three, AST's labels did not fit, or an artefact was heard.", "",
              "| event | kind -> heard | desc | fit new / old | AST | artefact |" + (" nearest vanilla | out | real |" if trust is not None else ""),
              "|---|---|---|---|---|---|" + ("---|---|---|" if trust is not None else "")]
    flagged = []
    for e in events:
        rows_e = [it for it in of("new") if it["event"] == e]
        heard = {}
        for it in rows_e:
            heard[it["kind_heard"][0]] = heard.get(it["kind_heard"][0], 0) + 1
        kind_ok = np.mean([it["kind_rank"] <= 3 for it in rows_e]) >= 0.5
        tag_ok = np.mean([it["tag_fit"] for it in rows_e]) >= 0.5
        art = max(rows_e, key=lambda it: it["artefact"][1])["artefact"]
        tags = {}
        for it in rows_e:
            for lab, p in it["tags"][:3]:
                tags[lab] = max(tags.get(lab, 0), p)
        fit = next(((n, o) for ev, n, o in paired if ev == e), (float(np.mean([it["fit"] for it in rows_e])), float("nan")))
        marks = ("" if kind_ok else "kind ") + ("" if tag_ok else "tags ") + ("artefact " if art[1] > 0.15 else "") + ("fit " if fit[0] < fit[1] else "")
        if marks:
            flagged.append((e, marks.strip()))
        row = (f"| {'! ' if marks else ''}{e} | {rows_e[0]['kind']} -> {', '.join(f'{k} ({c})' for k, c in sorted(heard.items(), key=lambda kv: -kv[1]))} | "
               f"{np.mean([it['text_rank'] for it in rows_e]):.0f} | {fit[0]:.2f} / {fit[1]:.2f} | "
               f"{', '.join(f'{lab} {p:.2f}' for lab, p in sorted(tags.items(), key=lambda kv: -kv[1])[:3])} | {art[0]} {art[1]:.2f} |")
        if trust is not None:
            near = {}
            for it in rows_e:
                for f in it["near"]:
                    near[f] = near.get(f, 0) + 1
            row += (f" {', '.join(f for f, _ in sorted(near.items(), key=lambda kv: -kv[1])[:3])} | {np.mean([it['out'] for it in rows_e]):.0%} | "
                    f"{np.mean([it['real_pct'] for it in rows_e]):.0%} |")
        lines.append(row)
    lines += ["", f"Marked: {len(flagged)} of {len(events)} events - " + "; ".join(f"{e.split(':')[1]} ({m})" for e, m in flagged), ""]
    lines += ["## The control, by kind", "", "| kind | CLAP names it | AST's labels fit |", "|---|---|---|"]
    for k in KINDS:
        rows_k = [it for it in of("control") if it["kind"] == k]
        if rows_k:
            lines.append(f"| {k} | {rate(rows_k, lambda it: it['kind_rank'] == 1):.0%} | {rate(rows_k, lambda it: it['tag_fit']):.0%} |")
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "judges.md").write_bytes("\n".join(lines).encode("utf-8"))
    slim = [{k: (str(v) if isinstance(v, Path) else v) for k, v in it.items()} for it in items]
    (out_dir / "judges.json").write_bytes(json.dumps(slim, ensure_ascii=False, indent=1).encode("utf-8"))
    for label, f in rows[:6]:
        print(f"{label:55s} control {rate(of('control'), f):4.0%}  old {rate(of('old'), f):4.0%}  new {rate(of('new'), f):4.0%}")
    print(f"fits its description better than the borrowed sound: {better} of {len(paired)}; marked {len(flagged)} of {len(events)}")


def main(args):
    if not args:
        sys.exit(__doc__)
    out_dir = Path(args[0])
    out_dir.mkdir(parents=True, exist_ok=True)
    items = gather(args)
    print(len(items), "sounds:", {s: sum(1 for it in items if it["set"] == s) for s in ("new", "old", "control")}, flush=True)
    trust = clap(items, "--vanilla" in args, out_dir)
    ast(items)
    if "--quality" in args:
        quality(items)
    report(items, trust, out_dir)


if __name__ == "__main__":
    main(sys.argv[1:])
