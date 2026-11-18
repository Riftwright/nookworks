"""A small procedural sound kit: the mod's own sound effects are computed from it, never recorded or borrowed.

Everything is built from a few physical ideas - struck things ring in their modes (modal), rough things are many small
events (scatter), bubbles chirp (bubble), air and steam are shaped noise - and every sound is drawn from a seeded
random source, so a file comes out the same on each run and each variant of a sound differs as two real strokes do.
What the earlier synthetic attempt got wrong (DECISIONS.md, "Sound is left to me": too regular, a mains hum in them)
is what the kit works against: nothing here repeats sample for sample, steady tones are bands of noise or detuned
pairs rather than bare sines, and nothing sits on 50/60 Hz or their doubles.

Used by tools/sounds/gen_sounds.py (and the add-on's own gen_sounds.py, which imports this file).
"""
import subprocess
import wave
import zlib
from pathlib import Path

import numpy as np
from scipy import signal

SR = 44100


def n_of(seconds):
    return int(round(seconds * SR))


def rng_for(*key):
    """A random source of its own for each sound and variant, the same on every run."""
    return np.random.default_rng(zlib.crc32("/".join(str(k) for k in key).encode("utf-8")))


# ---------------------------------------------------------------- noise

def white(n, rng):
    return rng.standard_normal(n)


def colored(n, rng, tilt=-3.0):
    """Noise whose spectrum falls (or rises) by tilt dB an octave: 0 white, -3 pink, -6 brown."""
    n = max(int(n), 16)
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1.0 / SR)
    f[0] = f[1]
    spec *= (f / 1000.0) ** (tilt / 6.0206)
    spec[f < 20.0] = 0.0
    x = np.fft.irfft(spec, n)
    return x / (np.std(x) + 1e-12)


# ---------------------------------------------------------------- filters

def _butter(kind, f, order):
    f = np.clip(np.asarray(f, dtype=float), 10.0, SR * 0.47)
    return signal.butter(order, f / (SR / 2), btype=kind, output="sos")


def lp(x, f, order=2):
    return signal.sosfilt(_butter("low", f, order), x)


def hp(x, f, order=2):
    return signal.sosfilt(_butter("high", f, order), x)


def bp(x, lo, hi, order=2):
    return signal.sosfilt(_butter("band", [lo, hi], order), x)


def reson(x, f, q, gain=1.0):
    """A two-pole resonance at f (peak gain 1): a thing's one mode, driven by x."""
    f = min(f, SR * 0.45)
    w0 = 2 * np.pi * f / SR
    alpha = np.sin(w0) / (2 * q)
    b = np.array([alpha, 0.0, -alpha]) / (1 + alpha)
    a = np.array([1.0, -2 * np.cos(w0) / (1 + alpha), (1 - alpha) / (1 + alpha)])
    return gain * signal.lfilter(b, a, x)


def reson_bank(x, modes):
    """x through several resonances at once: modes is [(f, q, gain), ...]."""
    out = np.zeros(len(x))
    for f, q, g in modes:
        out += reson(x, f, q, g)
    return out


def shelf(x, f, gain_db, high=True):
    """A shelf: everything above (or below) f raised or lowered by gain_db."""
    a_ = 10 ** (gain_db / 40.0)
    w0 = 2 * np.pi * f / SR
    cs, sn = np.cos(w0), np.sin(w0)
    alpha = sn / 2 * np.sqrt(2.0)
    sq = 2 * np.sqrt(a_) * alpha
    if high:
        b = [a_ * ((a_ + 1) + (a_ - 1) * cs + sq), -2 * a_ * ((a_ - 1) + (a_ + 1) * cs), a_ * ((a_ + 1) + (a_ - 1) * cs - sq)]
        a = [(a_ + 1) - (a_ - 1) * cs + sq, 2 * ((a_ - 1) - (a_ + 1) * cs), (a_ + 1) - (a_ - 1) * cs - sq]
    else:
        b = [a_ * ((a_ + 1) - (a_ - 1) * cs + sq), 2 * a_ * ((a_ - 1) - (a_ + 1) * cs), a_ * ((a_ + 1) - (a_ - 1) * cs - sq)]
        a = [(a_ + 1) + (a_ - 1) * cs + sq, -2 * ((a_ - 1) + (a_ + 1) * cs), (a_ + 1) + (a_ - 1) * cs - sq]
    return signal.lfilter(np.array(b) / a[0], np.array(a) / a[0], x)


def svf(x, cutoff, q=0.7, mode="lp"):
    """A filter whose cutoff moves: cutoff is one number or one a sample. mode lp, bp or hp."""
    n = len(x)
    fc = np.clip(np.broadcast_to(np.asarray(cutoff, dtype=float), (n,)), 15.0, SR * 0.45)
    g = np.tan(np.pi * fc / SR)
    k = 1.0 / q
    a1 = 1.0 / (1.0 + g * (g + k))
    a2 = g * a1
    a3 = g * a2
    out = np.empty(n)
    ic1 = ic2 = 0.0
    pick = {"lp": 0, "bp": 1, "hp": 2}[mode]
    xs, a1s, a2s, a3s = x.tolist(), a1.tolist(), a2.tolist(), a3.tolist()
    for i in range(n):
        v3 = xs[i] - ic2
        v1 = a1s[i] * ic1 + a2s[i] * v3
        v2 = ic2 + a2s[i] * ic1 + a3s[i] * v3
        ic1 = 2.0 * v1 - ic1
        ic2 = 2.0 * v2 - ic2
        out[i] = v2 if pick == 0 else v1 if pick == 1 else xs[i] - k * v1 - v2
    return out


# ---------------------------------------------------------------- envelopes and shapes

def exp_decay(n, t60):
    """Falling by 60 dB in t60 seconds."""
    return np.exp(-6.908 * np.arange(int(n)) / SR / max(t60, 1e-4))


def env_ad(n, attack, t60):
    """Up in attack seconds (a half cosine), then falling by 60 dB in t60."""
    n = int(n)
    e = exp_decay(n, t60)
    a = min(n, max(1, n_of(attack)))
    e[:a] *= 0.5 - 0.5 * np.cos(np.pi * np.arange(a) / a)
    return e


def env_points(n, points):
    """A line through (time s, level) points, held flat after the last."""
    t = np.arange(int(n)) / SR
    return np.interp(t, [p[0] for p in points], [p[1] for p in points])


def fade(x, fin=0.004, fout=0.03):
    x = np.array(x, dtype=float)
    a = min(len(x), n_of(fin))
    b = min(len(x), n_of(fout))
    if a > 1:
        x[:a] *= 0.5 - 0.5 * np.cos(np.pi * np.arange(a) / a)
    if b > 1:
        x[-b:] *= 0.5 + 0.5 * np.cos(np.pi * np.arange(b) / b)
    return x


def wobble(n, rng, rate, lo=-1.0, hi=1.0):
    """A slow random curve between lo and hi that turns about `rate` times a second: what keeps a sound from being
    the same from one moment to the next."""
    n = int(n)
    pad = n_of(2.0 / rate)
    x = lp(white(n + pad, rng), rate, 2)[pad:]
    x = x / (np.max(np.abs(x)) + 1e-12)
    return lo + (hi - lo) * (x * 0.5 + 0.5)


def place(out, x, at, gain=1.0):
    """x added into out starting at `at` seconds (cut off at out's end)."""
    i = max(0, n_of(at))
    k = min(len(x), len(out) - i)
    if k > 0:
        out[i:i + k] += gain * x[:k]
    return out


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def pad(x, seconds):
    return np.concatenate([x, np.zeros(n_of(seconds))])


def drive(x, amount):
    """Soft saturation: glues layers and rounds peaks, as a tape or a small speaker would."""
    return np.tanh(x * amount) / np.tanh(amount)


# ---------------------------------------------------------------- struck things

PLATE = (1.0, 1.59, 2.14, 2.30, 2.65, 2.92, 3.16, 3.50, 3.60, 4.06, 4.15, 4.60, 5.1, 5.6)  # a free plate's modes
BAR = (1.0, 2.756, 5.404, 8.933, 13.344)  # a free bar's
CUP = (1.0, 2.32, 3.9, 5.75, 7.8)  # a cup or small bell


def modes(f0, t60, ratios=PLATE, count=8, rng=None, bright=0.75, damp=0.35, jitter=0.03):
    """A struck thing's modes as [(f, t60, amp)]: partials at f0 * ratios, the higher ones weaker (bright) and
    shorter-lived (damp); jitter moves each a little so no two things ring alike."""
    out = []
    for k, r in enumerate(ratios[:count]):
        f = f0 * r
        a = bright ** k
        if rng is not None:
            f *= 1 + rng.uniform(-jitter, jitter)
            a *= rng.uniform(0.55, 1.0)
        out.append((f, t60 / (1 + damp * k), a))
    return out


def modal(dur, mode_list):
    """The ring of a struck thing: each mode a dying sine."""
    n = n_of(dur)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for f, t60, a in mode_list:
        if f < SR * 0.45:
            out += a * np.exp(-6.908 * t / t60) * np.sin(2 * np.pi * f * t)
    return out


def click(rng, ms=3.0, cutoff=6000.0, lowcut=200.0):
    """The contact itself: a few milliseconds of noise, duller for soft things, brighter for hard ones."""
    n = max(8, n_of(ms / 1000.0 * 3))
    x = white(n, rng) * exp_decay(n, ms / 1000.0)
    x = hp(lp(x, cutoff), lowcut)
    return x / (np.max(np.abs(x)) + 1e-12)


def strike(dur, mode_list, rng, click_gain=0.5, click_ms=2.5, click_cut=7000.0):
    """A thing struck: the contact's click, then its modes ringing."""
    body = modal(dur, mode_list)
    body /= np.max(np.abs(body)) + 1e-12
    return place(body, click(rng, click_ms, click_cut), 0.0, click_gain)


def thump(dur, f_start, f_end, t60, rng, noise=0.25, noise_cut=400.0):
    """A heavy, soft blow: a low tone falling as it dies (a mass settling), with a breath of low noise."""
    n = n_of(dur)
    t = np.arange(n) / SR
    f = f_end + (f_start - f_end) * np.exp(-t / (t60 * 0.12))
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * env_ad(n, 0.002, t60)
    air = lp(white(n, rng), noise_cut) * env_ad(n, 0.001, t60 * 0.5)
    return tone + noise * air / (np.max(np.abs(air)) + 1e-12)


# ---------------------------------------------------------------- many small events

def scatter(dur, rng, rate, grain, sigma=0.7, density=None, tail=0.5):
    """Small events at random moments, `rate` a second on average; grain(rng) makes each one, their strengths
    spread log-normally (a few loud ones among many soft, as crackles, pebbles and bubbles are). density, a curve a
    sample long in 0..1, thins them where it is low."""
    n = n_of(dur)
    out = np.zeros(n + n_of(tail))
    t = 0.0
    while True:
        t += rng.exponential(1.0 / rate)
        if t >= dur:
            break
        if density is not None and rng.random() > density[min(n - 1, int(t * SR))]:
            continue
        g = grain(rng)
        place(out, g, t, min(rng.lognormal(0.0, sigma), np.exp(2.0 * sigma)))  # no one event over the rest by more than a real one is
    return out[:n + n_of(tail)]


def bubble(rng, f0, rise=0.1, gain=1.0):
    """One bubble: a tone at its size's pitch, rising a little as it nears the surface, damped as real ones are
    (van den Doel's model: big bubbles low and long, small ones high and brief)."""
    d = 0.13 * f0 + 0.0072 * f0 ** 1.5
    n = n_of(min(0.3, 7.0 / d))
    t = np.arange(n) / SR
    f = f0 * (1 + rise * d * t)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-d * t)
    a = min(n, n_of(0.0015))
    x[:a] *= np.arange(a) / a
    return gain * x


def bubbles(dur, rng, rate, f_lo, f_hi, rise=0.1, sigma=0.6, density=None):
    """Bubbling: bubbles of sizes between f_lo and f_hi Hz, the big ones louder."""
    def grain(r):
        f0 = float(np.exp(r.uniform(np.log(f_lo), np.log(f_hi))))
        return bubble(r, f0, rise * r.uniform(0.4, 1.3), (f_lo / f0) ** 0.6)
    return scatter(dur, rng, rate, grain, sigma, density, tail=0.3)


def crackles(dur, rng, rate, f_lo=1200.0, f_hi=7000.0, sigma=0.9, density=None, ms=(0.6, 5.0)):
    """Crackling: tiny bursts of noise, each coloured by where it snapped."""
    def grain(r):
        n = max(6, n_of(r.uniform(*ms) / 1000.0 * 3))
        x = white(n, r) * exp_decay(n, n / SR / 3)
        f0 = float(np.exp(r.uniform(np.log(f_lo), np.log(f_hi))))
        return reson(np.concatenate([x, np.zeros(n)]), f0, r.uniform(0.8, 2.5))
    return scatter(dur, rng, rate, grain, sigma, density, tail=0.1)


def crunches(dur, rng, rate, f_lo=400.0, f_hi=3500.0, sigma=0.8, density=None, ms=(4.0, 30.0), q=(1.5, 5.0)):
    """Crunching: grains of rock or grit breaking - longer and lower than a crackle, each with a knock of its own."""
    def grain(r):
        length = r.uniform(*ms) / 1000.0
        n = n_of(length * 3)
        x = colored(n, r, -2.0) * exp_decay(n, length)
        f1 = float(np.exp(r.uniform(np.log(f_lo), np.log(f_hi))))
        y = reson(x, f1, r.uniform(*q)) + 0.6 * reson(x, f1 * r.uniform(1.4, 2.6), r.uniform(*q)) + 0.15 * x
        return y
    return scatter(dur, rng, rate, grain, sigma, density, tail=0.2)


# ---------------------------------------------------------------- turning and humming things

def band_tone(n, rng, f, width=0.02, steady=0.0):
    """A tone that is a narrow band of noise about f, `width` of f wide: steady in pitch, alive in level - a hum or
    whine with no bare sine in it. `steady` (0..1) mixes in a held tone whose phase strays a little, for a hum that
    should not flutter: at 0 its level comes and goes as a band of noise does, at 0.7 it only breathes."""
    n = int(n)
    lead = n_of(0.05)
    y = bp(white(n + lead, rng), f * (1 - width / 2), f * (1 + width / 2), 2)[lead:]
    y = y / (np.std(y) + 1e-12)
    if steady > 0:
        stray = 0.6 * np.cumsum(wobble(n, rng, 0.7)) / SR * 2 * np.pi  # under a hertz off, back and forth
        held = np.sqrt(2.0) * np.sin(2 * np.pi * f * np.arange(n) / SR + rng.uniform(0, 2 * np.pi) + stray)
        y = (1 - steady) * y + steady * held
        y = y / (np.std(y) + 1e-12)
    return y


def harmonics(n, rng, f0, amps, width=0.012, beat=0.0, twin=0.4, steady=0.0):
    """A pitched machine tone: band tones on f0's harmonics at the given amps; beat detunes a twin of each (at
    `twin` of its strength) by that fraction, so they swell against each other slowly."""
    out = np.zeros(int(n))
    for k, a in enumerate(amps, start=1):
        if a == 0 or f0 * k > SR * 0.4:
            continue
        out += a * band_tone(n, rng, f0 * k, width, steady)
        if beat:
            out += a * twin * band_tone(n, rng, f0 * k * (1 + beat), width, steady)
    return out


def glide(n, rng, f_from, f_to, amps=(1.0, 0.5, 0.25), curve=1.0, rough=0.15):
    """A tone that runs up or down (a reel spinning up, a servo): harmonics on a pitch going from f_from to f_to,
    a little rough so it is no whistle."""
    n = int(n)
    t = np.linspace(0.0, 1.0, n) ** curve
    f = f_from * (f_to / f_from) ** t
    phase = 2 * np.pi * np.cumsum(f) / SR
    out = np.zeros(n)
    for k, a in enumerate(amps, start=1):
        out += a * np.sin(k * phase + rng.uniform(0, 2 * np.pi))
    return out * (1 + rough * wobble(n, rng, 60.0))


def pulses(dur, rng, rate, pulse, jitter=0.02, accent=None, amp_jitter=0.12):
    """A machine's strokes: pulse(rng, i) placed `rate` times a second, each a hair early or late and a little
    stronger or weaker - an engine's firings, a ratchet's teeth. accent gives stroke i's strength (a cycle)."""
    n = n_of(dur)
    out = np.zeros(n)
    t = rng.uniform(0, 1.0 / rate) * 0.5
    i = 0
    while t < dur:
        g = 1.0 + rng.uniform(-amp_jitter, amp_jitter)
        if accent is not None:
            g *= accent(i)
        place(out, pulse(rng, i), t, g)
        t += (1.0 / rate) * (1 + rng.uniform(-jitter, jitter))
        i += 1
    return out


def norm(x):
    """x with its peak at 1."""
    return x / (np.max(np.abs(x)) + 1e-12)


def rmsn(x):
    """x with its level (rms) at 1."""
    return x / (np.sqrt(np.mean(np.square(x))) + 1e-12)


def bed(x, db):
    """A steady layer set `db` under full scale (its rms): what lies under a sound's strokes, whose peaks are at 1."""
    return rmsn(x) * 10 ** (db / 20.0)


def hiss(dur, rng, f_from=7000.0, f_to=3500.0, attack=0.01, t60=0.5, q=0.9):
    """Steam or air let go: a band of noise that comes on at once and sinks in pitch as the pressure falls."""
    n = n_of(dur)
    fc = f_to + (f_from - f_to) * np.exp(-np.arange(n) / SR / (t60 * 0.4))
    return svf(white(n, rng), fc, q, "bp") * env_ad(n, attack, t60)


def boxed(x, rng, lo=150.0, hi=900.0, count=4, q=(2.5, 5.0), amount=0.5, cut=5000.0):
    """Heard through a machine's sheet-metal case: the top rolled off, the case's own few low modes added."""
    y = lp(x, cut)
    out = y.copy()
    for _ in range(count):
        f = float(np.exp(rng.uniform(np.log(lo), np.log(hi))))
        out += amount / count * 2.0 * reson(y, f, rng.uniform(*q))
    return out


# ---------------------------------------------------------------- what the judges taught (tools/sounds/judge.py)
# A struck thing as a few clean partials is heard as an instrument (a wood block, a drum, "music"); a grain of
# crunching as one resonant ping is heard as a bubble; a low sine sliding down is a kick drum; a clean tone sliding
# up is a "boing"; a low rasp in bursts is not an electric arc. Real things are noisier and denser than that: these
# are the same ideas with the noise and the many modes left in.

WOOD = ((120.0, 450.0, 0.07, 1.0), (450.0, 1800.0, 0.035, 0.7), (1800.0, 6000.0, 0.012, 0.35))
STONE = ((180.0, 800.0, 0.035, 1.0), (800.0, 3500.0, 0.022, 0.8), (3500.0, 9000.0, 0.008, 0.4))
HEAVY = ((55.0, 180.0, 0.16, 1.0), (180.0, 700.0, 0.06, 0.55), (700.0, 3000.0, 0.016, 0.22))


def burst(dur, rng, bands, tilt=-2.0, attack=0.0004):
    """An unpitched blow: noise in a few bands, each dying at its own pace (the low ones longest), as a thing with
    too many modes to count answers a knock. bands is ((lo, hi, t60, gain), ...) - WOOD, STONE, HEAVY or one's own."""
    n = n_of(dur)
    out = np.zeros(n)
    for lo, hi, t60, g in bands:
        out += g * rmsn(bp(colored(n + 256, rng, tilt), lo, hi)[256:]) * exp_decay(n, t60)
    a = max(2, n_of(attack))
    out[:a] *= np.arange(a) / a
    return out


def dense_modes(rng, lo, hi, count, t60_lo, t60_hi, tilt=-1.5):
    """The many close modes of a real sheet, casting or frame, as [(f, t60, amp)]: pitches scattered between lo and
    hi, the low ones ringing longest (t60_lo down to t60_hi), none of them standing out as a note."""
    out = []
    for _ in range(count):
        u = rng.random()
        f = lo * (hi / lo) ** u
        out.append((f, t60_lo * (t60_hi / t60_lo) ** u * rng.uniform(0.6, 1.3), (f / lo) ** (tilt / 6.02) * rng.uniform(0.3, 1.0)))
    return out


def ring_of(dur, mode_list, rng):
    """modal() with each mode starting where it will (no two alike), for a ring of many modes."""
    n = n_of(dur)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for f, t60, a in mode_list:
        if f < SR * 0.45:
            out += a * np.exp(-6.908 * t / t60) * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi))
    k = n_of(0.0006)
    out[:k] *= np.arange(k) / k
    return out


def clang(dur, rng, lo, hi, t60, count=48, hard=0.6, t60_top=None):
    """Metal struck: the blow's own noise and a dense, inharmonic ring after it - a clank, not a note. lo..hi is where
    its modes lie, t60 how long the lowest ring (the top ones a fifth of that, or t60_top), hard how bright the blow."""
    ring = ring_of(dur, dense_modes(rng, lo, hi, count, t60, t60_top or t60 * 0.2), rng)
    blow = burst(min(dur, 0.15), rng, ((lo * 0.5, lo * 2.5, 0.05, 1.0), (lo * 2.5, min(hi * 1.5, 12000.0), 0.02, hard)))
    return place(norm(ring), norm(blow), 0.0, 0.7)


def thud(dur, rng, f=75.0, t60=0.14, tone=0.15, top=2500.0, snap=0.0):
    """A heavy thing landing: low noise that dies fast, a little of a low tone in it, and with `snap` the hard edge
    of the things that met. How much tone makes a tom-tom of it is not settled: the outside listener passed a door
    with 0.35 of it and called one with 0.1 and a snap a synthesiser's drum (2026-11-10, its second look) - give
    every call its own."""
    n = n_of(dur)
    low = burst(dur, rng, ((f * 0.7, f * 2.6, t60, 1.0), (f * 2.6, f * 9.0, t60 * 0.4, 0.5), (f * 9.0, top, t60 * 0.12, 0.25)))
    t = np.arange(n) / SR
    bend = f * (1 + 0.25 * np.exp(-t / 0.02))
    out = norm(low) + tone * np.sin(2 * np.pi * np.cumsum(bend) / SR) * env_ad(n, 0.003, t60 * 0.8)
    if snap:
        place(out, norm(burst(0.03, rng, ((1200.0, 7000.0, 0.006, 1.0),))), 0.0, snap)
    return out


def cracks(dur, rng, rate, lo=600.0, hi=5000.0, sigma=0.8, density=None, ms=(2.0, 18.0), ring=0.12):
    """Hard stuff breaking: each event a wide-band crack with a snap at its front (crunches() made each a ping, which
    is heard as a bubble). lo..hi is where a crack's weight lies; ring leaves a little of the piece's own pitch in."""
    def grain(r):
        length = r.uniform(*ms) / 1000.0
        n = max(32, n_of(length * 3))
        x = colored(n, r, r.uniform(-4.0, 0.0)) * exp_decay(n, length)
        fc = float(np.exp(r.uniform(np.log(lo), np.log(hi))))
        y = bp(x, fc * 0.45, min(fc * 2.2, 15000.0), 1)
        if ring:
            y = y + ring * reson(x, fc * r.uniform(0.8, 1.6), r.uniform(2.0, 5.0))
        k = min(n, n_of(0.0012))
        y[:k] += 0.8 * np.std(y) * white(k, r) * np.linspace(1.0, 0.0, k)
        return y
    return scatter(dur, rng, rate, grain, sigma, density, tail=0.2)


def whirr(n, rng, f_from, f_to, q=6.0, curve=1.0, tone=0.2):
    """A small motor or reel running up or down: a band of noise whose pitch moves, a little of a tone in it -
    a whirr (a clean tone moving the same way is heard as a "boing")."""
    n = int(n)
    f = f_from * (f_to / f_from) ** (np.linspace(0.0, 1.0, n) ** curve)
    y = rmsn(svf(white(n, rng), f, q, "bp")) + 0.5 * rmsn(svf(white(n, rng), np.minimum(f * 2.0, 16000.0), q, "bp"))
    if tone:
        y = y + tone * np.sqrt(2.0) * np.sin(2 * np.pi * np.cumsum(f) / SR) * (1 + 0.3 * wobble(n, rng, 60.0))
    return y


def buzz(n, rng, f0, bright=1200.0, jitter=0.004, rough=0.2):
    """A buzzing hum: every harmonic of f0 at once (a sawtooth, dulled above `bright`), its pitch trembling a hair
    and its level rough - coils and transformers, not an organ."""
    n = int(n)
    f = f0 * (1 + jitter * wobble(n, rng, 30.0))
    saw = 2.0 * ((np.cumsum(f) / SR + rng.random()) % 1.0) - 1.0
    return rmsn(lp(saw, bright, 2)) * (1 + rough * wobble(n, rng, 40.0))


def boil(dur, rng, rate, f_lo, f_hi, sigma=0.6, density=None, splash=0.25, rise=0.1):
    """bubbles() with what breaks the surface: each bubble ends in a speck of spray, so a boil is water and not a
    row of tones (low, bare bubbles are heard as a stomach). The outside listener hears a synthesiser in it and still
    knows it for boiling water, both times it was asked; breathy_boil below, made to cure that, it took for thunder
    and gravel."""
    def grain(r):
        f0 = float(np.exp(r.uniform(np.log(f_lo), np.log(f_hi))))
        b = bubble(r, f0, rise * r.uniform(0.4, 1.3), (f_lo / f0) ** 0.6)
        k = n_of(r.uniform(0.004, 0.012))
        spray = hp(white(k + 64, r), r.uniform(2500.0, 5000.0))[64:] * exp_decay(k, k / SR)
        return place(np.concatenate([b, np.zeros(k)]), spray, len(b) / SR * r.uniform(0.3, 0.7), splash * np.max(np.abs(b)) / (np.max(np.abs(spray)) + 1e-12))
    return scatter(dur, rng, rate, grain, sigma, density, tail=0.3)


def breathy_boil(dur, rng, rate, f_lo, f_hi, sigma=0.6, density=None, splash=0.4, rise=0.1, breath=0.45, bunch=0.6):
    """The third round's boil, which lost every pair it was in (2026-11-10: the outside listener heard far thunder,
    gravel stirred, a door creaking - no water): each bubble part tone and part breath (noise of the bubble's own
    pitch and length), a speck of spray at its end, bunches and lulls. Kept for the recipes that use it and wait
    held back; not the way to a better boil. breath is the share of noise in a bubble, bunch how unevenly they
    crowd."""
    n = n_of(dur)
    crowd = wobble(n, rng, 7.0, 0.0, 1.0) ** 2
    crowd = (1 - bunch) + bunch * crowd / (np.max(crowd) + 1e-12)
    thin = crowd if density is None else crowd * density[:n]

    def grain(r):
        f0 = float(np.exp(r.uniform(np.log(f_lo), np.log(f_hi))))
        tone = bubble(r, f0, rise * r.uniform(0.3, 1.6))
        k = len(tone)
        d = 0.13 * f0 + 0.0072 * f0 ** 1.5
        air = reson(white(k, r), f0 * r.uniform(0.9, 1.3), r.uniform(1.5, 3.0)) * np.exp(-d * np.arange(k) / SR)
        b = ((1 - breath) * tone + breath * norm(air)) * (f_lo / f0) ** 0.6
        m = n_of(r.uniform(0.004, 0.014))
        spray = hp(white(m + 64, r), r.uniform(2000.0, 4500.0))[64:] * exp_decay(m, m / SR)
        return place(np.concatenate([b, np.zeros(m)]), spray, k / SR * r.uniform(0.3, 0.7), splash * np.max(np.abs(b)) / (np.max(np.abs(spray)) + 1e-12))
    return scatter(dur, rng, rate * 1.6, grain, sigma, thin, tail=0.3)


def air_chord(n, rng, notes, width=0.025):
    """A soft chord of air: each note a band of noise about its pitch, coming and going on its own - a held sound
    for a thing that is no machine, with no oscillator in it. notes is [(f, amp), ...]."""
    n = int(n)
    out = np.zeros(n)
    for f, a in notes:
        out += a * band_tone(n, rng, f, width) * wobble(n, rng, 0.9, 0.3, 1.0)
    return rmsn(out)


def clicks(dur, rng, times, f_lo=3000.0, f_hi=6000.0, t60=0.006, gain=(0.5, 1.0)):
    """Small hard clicks at the given moments (a head seeking, a relay): each a speck of noise with a short ring."""
    out = np.zeros(n_of(dur))
    for at in times:
        f = float(rng.uniform(f_lo, f_hi))
        c = burst(0.03, rng, ((f * 0.6, min(f * 2.5, 12000.0), t60, 1.0),)) + 0.5 * ring_of(0.03, dense_modes(rng, f, f * 2.0, 4, t60 * 3, t60), rng)
        place(out, norm(c), at, float(rng.uniform(*gain)))
    return out


# ---------------------------------------------------------------- space

def room(x, rng, rt=0.22, wet=0.12, tone=3800.0, pre=0.006):
    """A little air round a dry sound - a workshop's walls, not a hall: a short, dull tail."""
    n = n_of(rt * 1.2)
    ir = white(n, rng) * exp_decay(n, rt)
    for k in range(5):  # a few early reflections
        at = n_of(pre + rng.uniform(0.003, 0.028))
        if at < n:
            ir[at] += rng.uniform(2.0, 5.0) * (0.8 ** k) * rng.choice([-1.0, 1.0])
    ir = hp(lp(ir, tone), 180.0)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-12
    tail = signal.fftconvolve(x, ir)
    out = np.zeros(len(tail))
    out[:len(x)] = x
    return out + wet * tail


def loopable(x, loop_len, cross=0.15):
    """x (longer than loop_len by cross seconds) folded into a loop: its tail faded under its head, so the end
    runs into the start with no seam."""
    n, c = n_of(loop_len), n_of(cross)
    x = x[:n + c]
    out = x[:n].copy()
    w = np.sin(0.5 * np.pi * np.arange(c) / c)  # equal power: the level holds through the join
    out[:c] = x[:c] * w + x[n:n + c] * np.cos(0.5 * np.pi * np.arange(c) / c)
    return out


class Loop:
    """A sound that goes round and round, made whole: everything in it fits the loop's length a whole number of times
    - what turns is whole turns, noise is made in the frequency domain (so it is periodic by construction), the slow
    changes are whole cycles a loop, filters, the case and the room run round it. The sample after its last is its
    first: there is no end to fold (loopable folds a longer sound), and nothing to crossfade. finish(loop=True) keeps
    it so; a hard start or stop of it clicks, so whatever plays it fades it in and out.

    What the judges that can hear taught about a motor (2026-11-12): tones laid over one another are an instrument or a
    spaceship - a foghorn, a singing bowl, a siren. A motor is one thing going round, giving a push at every pole or
    bar, no two bars quite alike (the same every turn - that is what puts the turning into the sound) and no two
    turns quite alike (that is its roughness), ringing in its own case: pulses(), then body()."""

    def __init__(self, seconds):
        self.seconds = float(seconds)
        self.n = n_of(seconds)
        self.t = np.arange(self.n) / SR
        self.f = np.fft.rfftfreq(self.n, 1.0 / SR)

    def fit(self, f):
        """f moved to the nearest pitch that fits the loop a whole number of times."""
        return round(f * self.seconds) / self.seconds

    def band(self, lo, hi, soft=0.3):
        """A band's shape over the spectrum: 1 inside lo..hi, rounded off over `soft` octaves at each end."""
        f = np.maximum(self.f, self.f[1])
        up = np.clip(np.log2(f / lo) / soft + 0.5, 0, 1)
        down = np.clip(np.log2(hi / f) / soft + 0.5, 0, 1)
        return (up * up * (3 - 2 * up)) * (down * down * (3 - 2 * down))

    def noise(self, rng, lo, hi, tilt=-3.0, soft=0.3):
        """Noise in a band, falling by tilt dB an octave, one loop long and periodic."""
        f = np.maximum(self.f, self.f[1])
        mag = (f / 1000.0) ** (tilt / 6.0206) * self.band(lo, hi, soft)
        return rmsn(np.fft.irfft(np.fft.rfft(rng.standard_normal(self.n)) * mag, self.n))

    def keep(self, x, lo, hi, soft=0.3):
        """x kept between lo and hi, the filter run round the loop (no start, no end)."""
        return np.fft.irfft(np.fft.rfft(x) * self.band(lo, hi, soft), self.n)

    def slow(self, rng, cycles, lo, hi):
        """A slow change between lo and hi that comes round with the loop: cycles = ((turns a loop, weight), ...)."""
        x = sum(w * np.sin(2 * np.pi * k * self.t / self.seconds + rng.uniform(0, 2 * np.pi)) for k, w in cycles)
        x = (x - x.min()) / (x.max() - x.min())
        return lo + (hi - lo) * x

    def lead(self, rng, depth=0.0012, cycles=((1, 1.0), (2, 0.7), (3, 0.5), (5, 0.3))):
        """The speed never quite still: how far ahead or behind the thing runs, in seconds, over the loop, its speed
        off by `depth` at most (a tenth of a percent is life, not a change of pitch). Everything that turns is read
        off it, so the whole machine wanders as one."""
        total = sum(w for _, w in cycles)
        lead = np.zeros(self.n)
        for k, w in cycles:
            lead += (depth * (w / total) * -np.cos(2 * np.pi * k * self.t / self.seconds + rng.uniform(0, 2 * np.pi))
                     * self.seconds / (2 * np.pi * k))
        return lead

    def turn(self, rng, rate, lead, depth):
        """Something going round `rate` times a second: stronger and weaker by `depth` each turn."""
        return 1 + depth * np.sin(2 * np.pi * self.fit(rate) * (self.t + lead) + rng.uniform(0, 2 * np.pi))

    def pulses(self, rng, shaft, pattern, lead, jitter=0.03, rough=0.1, lo=120.0, hi=4000.0, tilt=-3.0):
        """Pulses round the loop: len(pattern) to a turn of the shaft, their strengths the pattern's (the same every
        turn), each `jitter` of a gap early or late and `rough` stronger or weaker by chance; a pulse's own shape is
        the band lo..hi."""
        turns = int(round(shaft * self.seconds))
        count = turns * len(pattern)
        gap = self.seconds / count
        times = (np.arange(count) + rng.normal(0, jitter, count)) * gap
        times = times + np.interp(times % self.seconds, self.t, lead)
        amps = np.tile(np.asarray(pattern, dtype=float), turns) * (1 + rng.normal(0, rough, count))
        spec = np.zeros(len(self.f), dtype=complex)
        for i in range(0, count, 128):
            spec += (amps[None, i:i + 128] * np.exp(-2j * np.pi * self.f[:, None] * times[None, i:i + 128])).sum(axis=1)
        f = np.maximum(self.f, self.f[1])
        return rmsn(np.fft.irfft(spec * (f / 1000.0) ** (tilt / 6.0206) * self.band(lo, hi), self.n))

    def body(self, x, modes, dry=0.4):
        """x ringing in a case: modes = ((Hz, sharpness, gain), ...), run round the loop."""
        f = np.maximum(self.f, 1e-6)
        answer = np.full(len(self.f), dry, dtype=complex)
        for f0, sharp, gain in modes:
            r = f / f0
            answer += gain * (1j * r / sharp) / (1 - r * r + 1j * r / sharp)
        return np.fft.irfft(np.fft.rfft(x) * answer, self.n)

    def room(self, x, rng, rt=0.22, wet=0.1, tone=3200.0, pre=0.006):
        """A small room's answer, run round the loop."""
        k = n_of(min(self.seconds * 0.8, rt * 1.6))
        ir = lp(rng.standard_normal(k) * np.exp(-6.91 * np.arange(k) / SR / rt), tone)
        full = np.zeros(self.n)
        p = n_of(pre)
        full[p:p + k] = ir
        tail = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(full), self.n)
        return x + wet * tail * (np.std(x) / (np.std(tail) + 1e-12))


# ---------------------------------------------------------------- measuring, finishing, writing

def a_weighted(x):
    """x as the ear weighs it (A-weighting), for judging loudness."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / SR)
    f2 = f ** 2
    w = (12194.0 ** 2 * f2 ** 2) / ((f2 + 20.6 ** 2) * np.sqrt((f2 + 107.7 ** 2) * (f2 + 737.9 ** 2)) * (f2 + 12194.0 ** 2) + 1e-30)
    return np.fft.irfft(spec * w * 1.2589, len(x))


def loudness(x, gate_db=20.0):
    """How loud x is heard while it sounds, in dB: its A-weighted level over the 20 ms frames within gate_db of
    its loudest (so a click's silence after it does not count against it)."""
    if len(x) < 64:
        return -120.0
    y = a_weighted(np.asarray(x, dtype=float))
    frame = n_of(0.02)
    k = max(1, len(y) // frame)
    power = np.array([np.mean(y[i * frame:(i + 1) * frame] ** 2) for i in range(k)])
    top = power.max()
    if top <= 0:
        return -120.0
    return 10 * np.log10(np.mean(power[power >= top * 10 ** (-gate_db / 10)]))


def trim(x, floor_db=-66.0, keep=0.01):
    """Silence cut off the end (below floor_db of the peak), a breath kept."""
    peak = np.max(np.abs(x)) + 1e-12
    above = np.nonzero(np.abs(x) > peak * 10 ** (floor_db / 20))[0]
    if len(above) == 0:
        return x
    return x[:min(len(x), above[-1] + n_of(keep))]


def finish(x, level=-23.0, peak=-1.5, fin=0.0, fout=0.03, lowcut=40.0, highcut=14000.0, cut=True, loop=False):
    """A sound made ready: rumble and fizz beyond hearing's use taken off, its end trimmed and faded, set to one
    loudness (so sounds.json's volume alone says how loud each plays), its peaks held under `peak` dB by a soft knee.
    loop: a sound played round and round (see loopable) - nothing added before it, no trim, no fades, and the
    filters run in as if it had been playing already, so its end still meets its start."""
    x = np.asarray(x, dtype=float)
    if loop:
        x = lp(hp(np.concatenate([x, x]), lowcut, 2), highcut, 4)[len(x):]
    else:
        x = np.concatenate([np.zeros(n_of(0.002)), x])  # a breath before a stroke at its very start
        x = lp(hp(x, lowcut, 2), highcut, 4)
        if cut:
            x = trim(x)
        x = fade(x, fin, fout)
    cap = 10 ** (peak / 20)
    knee = cap * 0.7
    for _ in range(3):  # holding the peaks takes a little loudness off a sound of few, sharp strokes: set it again
        x = x * 10 ** ((level - loudness(x)) / 20)
        over = np.abs(x) > knee
        if not np.any(np.abs(x) > cap * 0.98):
            break
        x[over] = np.sign(x[over]) * (knee + (cap - knee) * np.tanh((np.abs(x[over]) - knee) / (cap - knee)))
    return x


# no tags, no encoder version, a fixed stream serial: the file comes out the same each run (as make_sounds.py's do)
EXACT = ["-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact"]


def write_wav(path, x):
    """16-bit mono, dithered (from a fixed seed)."""
    x = np.asarray(x, dtype=float)
    d = np.random.default_rng(1234)
    y = x * 32767.0 + (d.random(len(x)) - d.random(len(x)))
    y = np.clip(np.round(y), -32768, 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(y.tobytes())


def write_ogg(path, x, quality=4):
    """Mono Ogg Vorbis, as Minecraft wants a placed sound (a stereo one is heard everywhere at full volume)."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp.wav")
    write_wav(tmp, x)
    try:
        subprocess.run(["ffmpeg", "-hide_banner", "-y", "-loglevel", "error", "-i", str(tmp), "-ac", "1", "-ar", str(SR),
                        "-c:a", "libvorbis", "-q:a", str(quality), *EXACT, str(path)], check=True, capture_output=True)
    finally:
        tmp.unlink(missing_ok=True)


def read_audio(path):
    """Any sound file as mono samples at SR (through ffmpeg), for measuring."""
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32).astype(np.float64)
