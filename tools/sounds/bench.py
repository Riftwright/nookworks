"""Our computed sounds measured against vanilla's whole sound library - a strict second look, since a script cannot
listen and the one who wrote the recipes should not be their only judge.

    python tools/sounds/bench.py REPORT.md [--nooktech PATH/TO/nook_technology/mod] [--cache DIR]

Vanilla's sounds (read from the Gradle cache's asset store on this machine, as measuring references only: nothing of
theirs is copied anywhere) are some 4,900 recordings made and mixed by professionals for this very game. Each sound,
theirs and ours, is reduced to the same numbers (how its spectrum is shaped and moves: MFCC statistics; how tonal, how
noise-like, how changing, how regular it is). Then:

1. How far the method can be trusted: for each vanilla sound, is its nearest neighbour one of its own family
   (block/gravel/break2 next to block/gravel/break4)? That rate is what "nearest" is worth here.
2. What each of ours sounds nearest to, among vanilla's - a blind naming by vanilla's own library.
3. Whether ours lie inside the cloud of vanilla's sounds or outside it: the distance from each of ours to its nearest
   vanilla sounds, as a percentile of the same distance among vanilla's own (leaving each one's own family out, so a
   sound is not credited for its twin takes).
4. The traits the first synthetic attempt was turned down for (DECISIONS.md: "too regular", "mains hum"), and the
   others a computed sound is prone to - a bare tone, plain noise, a spectrum that never moves - each as a percentile
   of vanilla's.

It can find what is off; it cannot say a sound is good. That stays with ears.
"""
import json
import re
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import numpy as np
from scipy import fft, signal

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sfx  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
SR = 22050  # enough for what tells sounds apart, and half the work
ASSETS = Path.home() / ".gradle/caches/neoformruntime/assets"
PLAYED_AT = {"dice_roll": 1.6, "gondola_rattle": 0.7}  # stored slowed or quickened: measured as the game plays them


def decode(path, rate=1.0, seconds=6.0):
    chain = f"asetrate=44100*{rate},aresample={SR}" if rate != 1.0 else f"aresample={SR}"
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-t", str(seconds / rate), "-af", chain, "-ac", "1", "-f", "f32le", "-"],
                         capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).astype(np.float64)


def mel_bank(n_fft, bands=40, lo=60.0, hi=10000.0):
    def mel(f):
        return 2595.0 * np.log10(1.0 + f / 700.0)
    edges = 700.0 * (10 ** (np.linspace(mel(lo), mel(hi), bands + 2) / 2595.0) - 1.0)
    freqs = np.fft.rfftfreq(n_fft, 1.0 / SR)
    bank = np.zeros((bands, len(freqs)))
    for b in range(bands):
        a, c, d = edges[b], edges[b + 1], edges[b + 2]
        bank[b] = np.clip(np.minimum((freqs - a) / (c - a), (d - freqs) / (d - c)), 0.0, None)
    return bank, freqs


N_FFT, HOP = 512, 256
BANK, FREQS = mel_bank(N_FFT)
NAMES = ([f"mfcc{i}_mean" for i in range(1, 13)] + [f"mfcc{i}_sd" for i in range(1, 13)] + [f"dmfcc{i}_sd" for i in range(1, 13)]
         + ["centroid", "centroid_sd", "rolloff", "flatness", "flux", "tonal", "level_sd", "regular", "crest", "log_dur", "hum"])
TRAITS = {"tonal": "a bare tone (energy on one pitch)", "flatness": "plain noise (a spectrum with no shape)",
          "flux": "movement (how much the spectrum changes from moment to moment)", "regular": "regularity (the level repeating at a fixed pace)",
          "hum": "mains hum (energy on 50/60/100/120 Hz over what lies beside them)"}


def numbers(x):
    """One sound as a vector (NAMES), or None for one too short or silent to measure."""
    if len(x) < N_FFT * 2 or np.max(np.abs(x)) < 1e-5:
        return None
    _, _, z = signal.stft(x, fs=SR, nperseg=N_FFT, noverlap=N_FFT - HOP, window="hann", padded=False, boundary=None)
    power = np.abs(z) ** 2
    energy = power.sum(axis=0)
    live = energy > energy.max() * 1e-5  # frames within 50 dB of the loudest: the sound, not the silence round it
    if live.sum() < 3:
        return None
    p = power[:, live]
    logmel = 10 * np.log10(BANK @ p + 1e-12)
    cc = fft.dct(logmel, type=2, norm="ortho", axis=0)[1:13]
    d = np.diff(cc, axis=1) if cc.shape[1] > 1 else np.zeros_like(cc)
    band = (FREQS >= 100) & (FREQS <= 10000)
    pb = p[band] + 1e-14
    total = pb.sum(axis=0)
    cen = (FREQS[band, None] * pb).sum(axis=0) / total
    cum = np.cumsum(pb, axis=0) / total
    roll = FREQS[band][np.argmax(cum >= 0.85, axis=0)]
    flat = np.exp(np.mean(np.log(pb), axis=0)) / np.mean(pb, axis=0)
    shape = pb / total
    flux = np.sqrt(np.sum(np.diff(shape, axis=1) ** 2, axis=0)) if shape.shape[1] > 1 else np.zeros(1)
    tonal = 10 * np.log10(pb.max(axis=0) / np.mean(pb, axis=0))
    level = 10 * np.log10(energy[live] + 1e-14)
    env = level - level.mean()
    regular = 0.0
    if len(env) > 24:  # the strongest repeat of the level between 30 ms and 0.5 s
        ac = np.correlate(env, env, mode="full")[len(env) - 1:]
        ac = ac / (ac[0] + 1e-12)
        lo, hi = max(2, int(0.03 * SR / HOP)), min(len(ac) - 1, int(0.5 * SR / HOP))
        if hi > lo:
            regular = float(np.max(ac[lo:hi]))
    spec = np.abs(np.fft.rfft(x * np.hanning(len(x)))) ** 2
    f = np.fft.rfftfreq(len(x), 1.0 / SR)

    def near(f0, width):
        return spec[(f > f0 - width) & (f < f0 + width)].sum()

    # only where the low band carries something: in a sound with next to nothing down there the ratio is of two nothings
    low_share = spec[(f > 40) & (f < 130)].sum() / (spec.sum() + 1e-14)
    hum = 0.0
    if len(x) > SR // 2 and low_share > 0.02:
        hum = max(near(f0, 2.0) / (near(f0, 12.0) - near(f0, 2.0) + 1e-14) for f0 in (50.0, 60.0, 100.0, 120.0))
    crest = 20 * np.log10(np.max(np.abs(x)) / (np.sqrt(np.mean(x ** 2)) + 1e-12))
    return np.concatenate([cc.mean(axis=1), cc.std(axis=1), d.std(axis=1),
                           [np.log2(cen.mean()), np.log2(cen + 1).std(), np.log2(roll.mean() + 1), flat.mean(), flux.mean(), tonal.mean(),
                            level.std(), regular, crest, np.log(len(x) / SR), np.log10(hum + 1e-3)]])


def vanilla():
    """(name, path) of every vanilla sound but the music, from this machine's asset store."""
    index = max((ASSETS / "indexes").glob("*.json"), key=lambda p: int(p.stem))
    out = []
    for key, entry in json.loads(index.read_text())["objects"].items():
        if key.startswith("minecraft/sounds/") and key.endswith(".ogg") and key.split("/")[2] not in ("music", "records"):
            out.append((key[len("minecraft/sounds/"):-4], ASSETS / "objects" / entry["hash"][:2] / entry["hash"]))
    return sorted(out)


def measure(items, cache=None):
    """names and vectors for (name, path, rate) items; cached by name when a cache folder is given."""
    file = cache / "vanilla.npz" if cache else None
    if file and file.exists():
        got = np.load(file, allow_pickle=True)
        return list(got["names"]), got["vectors"]
    with ThreadPoolExecutor(12) as pool:
        vectors = list(pool.map(lambda it: numbers(decode(it[1], it[2])), items))
    names = [it[0] for it, v in zip(items, vectors) if v is not None]
    vectors = np.array([v for v in vectors if v is not None])
    if file:
        cache.mkdir(parents=True, exist_ok=True)
        np.savez(file, names=np.array(names, dtype=object), vectors=vectors)
    return names, vectors


def family(name):
    return re.sub(r"[_\d]+$", "", name)


def ours(assets, modid):
    table = json.loads((assets / "sounds.json").read_bytes())
    out = []
    for event, entry in table.items():
        for s in entry["sounds"]:
            if isinstance(s, dict) and s.get("type") != "event":
                path = s["name"].split(":", 1)[1]
                rate = next((r for k, r in PLAYED_AT.items() if k in path), 1.0)
                out.append((f"{modid}:{event}", path, assets / "sounds" / f"{path}.ogg", rate))
    return out


def main(args):
    if not args:
        sys.exit(__doc__)
    report = Path(args[0])
    cache = Path(args[args.index("--cache") + 1]) if "--cache" in args else None
    v_names, v = measure([(n, p, 1.0) for n, p in vanilla()], cache)
    mine = ours(ROOT / "src/main/resources/assets/nookworks", "nookworks")
    if "--nooktech" in args:
        mine += ours(Path(args[args.index("--nooktech") + 1]) / "src/main/resources/assets/nooktech", "nooktech")
    m_vec = [numbers(decode(path, rate)) for _, _, path, rate in mine]
    keep = [i for i, vec in enumerate(m_vec) if vec is not None]
    mine = [mine[i] for i in keep]
    m = np.array([m_vec[i] for i in keep])

    # one scale for every number: vanilla's own spread (robust, so a few odd files do not set it)
    med = np.median(v, axis=0)
    spread = 1.4826 * np.median(np.abs(v - med), axis=0) + 1e-9
    timbre = slice(0, len(NAMES) - 2)  # everything but length and hum: what a sound is like, not how long
    zv, zm = ((v - med) / spread)[:, timbre], ((m - med) / spread)[:, timbre]
    zv, zm = np.clip(zv, -6, 6), np.clip(zm, -6, 6)
    fam = np.array([family(n) for n in v_names])

    def dist(a, b):
        return np.sqrt(((a[:, None, :] - b[None, :, :]) ** 2).mean(axis=2))

    lines = ["# Our sounds against vanilla's library", "",
             f"{len(v_names)} vanilla sounds (all but the music), {len(mine)} of ours. Written by tools/sounds/bench.py; read its top for what each part means.", ""]

    # 1. the method's own worth
    dv = np.vstack([dist(zv[i:i + 400], zv) for i in range(0, len(zv), 400)])
    np.fill_diagonal(dv, np.inf)
    sizes = {f: int((fam == f).sum()) for f in set(fam)}
    has_kin = np.array([sizes[f] >= 3 for f in fam])
    order = np.argsort(dv, axis=1)[:, :5]
    top1 = np.mean([fam[order[i, 0]] == fam[i] for i in np.nonzero(has_kin)[0]])
    top5 = np.mean([np.any(fam[order[i]] == fam[i]) for i in np.nonzero(has_kin)[0]])
    chance = np.mean([(sizes[fam[i]] - 1) / (len(fam) - 1) for i in np.nonzero(has_kin)[0]])
    lines += ["## 1. How far to trust it", "",
              f"A vanilla sound's nearest neighbour is of its own family {top1:.0%} of the time, one of its five nearest {top5:.0%} "
              f"(by chance {chance:.1%}; {int(has_kin.sum())} sounds in families of three or more). So a nearest neighbour here is a strong hint, not a verdict.", ""]

    # 3. inside the cloud or outside: distance to the 5 nearest vanilla sounds not of one's own family
    own_family = fam[:, None] == fam[None, :]
    dv_other = np.where(own_family, np.inf, dv)
    v_knn = np.sort(dv_other, axis=1)[:, :5].mean(axis=1)
    dm = dist(zm, zv)
    m_knn = np.sort(dm, axis=1)[:, :5].mean(axis=1)
    pct = np.array([np.mean(v_knn < d) for d in m_knn])

    # 2. nearest vanilla sounds, an event at a time
    lines += ["## 2. What each of ours is nearest to", "",
              "For each event: the vanilla families its variants land nearest (how many of the variants' five nearest are of that family), and how far out",
              "it lies - the share of vanilla sounds that sit closer to other vanilla sounds than ours does (50 % is a typical vanilla sound, over 95 % an outsider).", "",
              "| event | nearest vanilla families | out |", "|---|---|---|"]
    events = []
    for name in dict.fromkeys(e for e, _, _, _ in mine):
        rows = [i for i, it in enumerate(mine) if it[0] == name]
        votes = {}
        for i in rows:
            for j in np.argsort(dm[i])[:5]:
                votes[fam[j]] = votes.get(fam[j], 0) + 1
        best = sorted(votes.items(), key=lambda kv: -kv[1])[:4]
        out = float(np.mean(pct[rows]))
        events.append((name, best, out))
        lines.append(f"| {name} | {', '.join(f'{f} ({c})' for f, c in best)} | {out:.0%} |")
    lines += ["", f"Ours lie at the {np.median(pct):.0%} point of vanilla's own spread on the median; {np.mean(pct > 0.95):.0%} of our files are further out than 95 % of vanilla's "
              f"(5 % would be par), {np.mean(pct > 0.5):.0%} further than half.", ""]

    # 4. the traits a computed sound is prone to
    lines += ["## 3. Traits a computed sound is prone to", "",
              "Each trait as a percentile of vanilla's sounds (of like length: sounds over half a second against sounds over half a second). Listed: our files beyond vanilla's 97.5 % point",
              "(for movement, under its 2.5 % point - a spectrum that stands still).", ""]
    long_v = v[:, NAMES.index("log_dur")] > np.log(0.5)
    long_m = m[:, NAMES.index("log_dur")] > np.log(0.5)
    for trait, meaning in TRAITS.items():
        k = NAMES.index(trait)
        flagged = []
        ours_pct = []
        for group_m, group_v in ((long_m, long_v), (~long_m, ~long_v)):
            if trait == "hum" and group_m is not long_m:
                continue
            ref = np.sort(v[group_v, k])
            for i in np.nonzero(group_m)[0]:
                q = np.searchsorted(ref, m[i, k]) / len(ref)
                ours_pct.append(q)
                if (q < 0.025) if trait == "flux" else (q > 0.975):
                    flagged.append(f"{mine[i][1].split('/')[-1]} ({q:.1%})")
        lines += [f"- **{trait}** - {meaning}: ours at the {np.median(ours_pct):.0%} point of vanilla's on the median. "
                  + (f"Beyond: {', '.join(flagged)}." if flagged else "None beyond."), ""]
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_bytes("\n".join(lines).encode("utf-8"))
    print(f"trust: top1 {top1:.0%} top5 {top5:.0%} (chance {chance:.1%}); ours out: median {np.median(pct):.0%}, beyond 95%: {np.mean(pct > 0.95):.0%}")
    return events


if __name__ == "__main__":
    main(sys.argv[1:])
