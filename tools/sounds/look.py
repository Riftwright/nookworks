"""Sounds looked at, since a script cannot listen: for each file its wave, its spectrogram and a few numbers, on a sheet.

    python tools/sounds/look.py OUT.png FILE [FILE ...]

Numbers: length, loudness while it sounds (A-weighted, sfx.loudness), peak, the frequency half its energy lies under
(mid) and 85 % (top), the share of energy under 150 Hz (low), and how even its level is from one 50 ms to the next
(flat: near 0 a held tone, 0.3-0.6 a live texture, above 1 separate strokes). The same numbers for a vanilla sound
tell how a new one sits beside it.
"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sfx  # noqa: E402


def numbers(x):
    sr = sfx.SR
    spec = np.abs(np.fft.rfft(x * np.hanning(len(x)))) ** 2
    f = np.fft.rfftfreq(len(x), 1 / sr)
    cum = np.cumsum(spec) / (spec.sum() + 1e-30)
    frame = sfx.n_of(0.05)
    lev = np.array([np.sqrt(np.mean(x[i:i + frame] ** 2)) for i in range(0, max(1, len(x) - frame), frame)])
    lev = lev[lev > lev.max() * 0.03] if len(lev) else np.array([0.0])
    return dict(dur=len(x) / sr, loud=sfx.loudness(x), peak=20 * np.log10(np.max(np.abs(x)) + 1e-12),
                mid=f[np.searchsorted(cum, 0.5)], top=f[np.searchsorted(cum, 0.85)], low=spec[f < 150].sum() / (spec.sum() + 1e-30),
                flat=float(np.std(lev) / (np.mean(lev) + 1e-12)))


def line(name, x):
    s = numbers(x)
    return (f"{name:44s} {s['dur']:5.2f} s  loud {s['loud']:6.1f}  peak {s['peak']:6.1f}  mid {s['mid']:6.0f}  top {s['top']:6.0f}"
            f"  low {s['low']:.2f}  flat {s['flat']:.2f}")


def sheet(out, files):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    fig, axes = plt.subplots(len(files), 2, figsize=(13, 1.9 * len(files)), squeeze=False, gridspec_kw={"width_ratios": [1, 2.2]})
    for row, path in enumerate(files):
        x = sfx.read_audio(path)
        t = np.arange(len(x)) / sfx.SR
        axes[row][0].plot(t, x, lw=0.4, color="#234")
        axes[row][0].set_ylim(-1, 1)
        axes[row][0].set_xlim(0, max(t[-1], 0.05))
        axes[row][0].set_title(line(Path(path).stem, x), fontsize=7, loc="left", family="monospace")
        nfft = 1024
        axes[row][1].specgram(x, NFFT=nfft, Fs=sfx.SR, noverlap=nfft - 128, cmap="magma", vmin=-125, vmax=-45)
        axes[row][1].set_yscale("symlog", linthresh=400)
        axes[row][1].set_ylim(40, 16000)
        axes[row][1].set_yticks([100, 400, 1000, 3000, 8000])
        axes[row][1].set_yticklabels(["100", "400", "1k", "3k", "8k"], fontsize=6)
        axes[row][1].set_xlim(0, max(t[-1], 0.05))
        for ax in axes[row]:
            ax.tick_params(labelsize=6)
    fig.tight_layout()
    fig.savefig(out, dpi=70)
    plt.close(fig)


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    paths = sys.argv[2:]
    for p in paths:
        print(line(Path(p).stem, sfx.read_audio(p)))
    sheet(sys.argv[1], paths)
