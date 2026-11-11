"""The mod's own sound effects, computed (tools/sounds/sfx.py): no recording, no other mod's or Mojang's sound in them.

    python tools/sounds/gen_sounds.py [name-part ...] [--wav DIR]

writes assets/nookworks/sounds/<file>_<n>.ogg for every sound below (or only those whose event name holds one of
the name parts) and sets each one's entry in assets/nookworks/sounds.json to its files. --wav also leaves the
uncompressed sounds in DIR, for looking at (tools/sounds/look.py). The same bytes every run (needs ffmpeg on the PATH).

How they are meant (DECISIONS.md, "Sound is left to me"):
- A machine's work sound is heard now and then, a second or two of it, never a loop: each is a short phrase of what the
  machine is doing, faded in and out, in several variants so two in a row are not the same sound.
- Soft and dull rather than bright, as vanilla's are: heard through a machine's case, in a workshop's air.
- What turns keeps one pitch in every variant (a pitch that wanders reads as a motor changing speed).
- No creaking wood, and no steady tone on the mains' 50/60 Hz or their doubles.
The Motor, the Spawner Controller and the Trash Can keep the sounds the user picked (make_sounds.py); the Drive
Wheel's is ours since the outside listener's second look.

A script cannot listen, and nobody else is asked to (the user plays with the sound off and has no one to ask): the
recipes are worked on against judges that can hear. tools/sounds/hear.py puts a recipe before two of them at once
(what AudioSet's labels call it, what kind of sound CLAP takes it for), judge.py puts every sound before them beside
what it replaced and beside vanilla's own, with a third judge held back, and bench.py measures ours against vanilla's
whole library. What they taught is written at the head of that part of sfx.py; the short of it: a struck thing of a
few clean partials is an instrument, a ping is a bubble, a low sine sliding down is a drum, a clean glide is a "boing",
low noise that swells and fades is an explosion, plain high noise is static - real things are noisier, denser and
less even. The judges are no truth either (judge.py's control says how far each is right about vanilla's own sounds).
"""
import json
import sys
from pathlib import Path

import numpy as np
from scipy import signal

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sfx  # noqa: E402
from sfx import (SR, n_of, white, colored, lp, hp, bp, reson, reson_bank, svf, exp_decay, env_ad, env_points, fade, wobble,  # noqa: E402
                 place, mix, drive, modes, modal, click, strike, thump, bubble, bubbles, crackles, crunches,
                 harmonics, pulses, room, norm, bed, hiss, boxed, PLATE, BAR, CUP,
                 burst, clang, thud, cracks, whirr, buzz, boil, ring_of, dense_modes, WOOD, STONE, HEAVY,
                 air_chord, clicks, band_tone, rmsn, loopable, breathy_boil)

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
MODID = "nookworks"


# ------------------------------------------------------------------ the machines' work sounds

def generator_run(rng, v):
    """A firebox: wood snapping, a log cracking and settling, the flames' flutter - nothing booming under it."""
    dur = 2.3
    n = n_of(dur)
    snaps = cracks(dur, rng, 16, 800, 3500, sigma=0.8, density=wobble(n, rng, 3.0, 0.15, 1.0), ms=(1.5, 7), ring=0.2)[:n]
    logs = cracks(dur, rng, 2.5, 300, 1200, sigma=0.5, ms=(8, 22), ring=0.0)[:n]
    flame = bp(colored(n, rng, -4), 200, 1200) * wobble(n, rng, 9.0, 0.3, 1.0) * wobble(n, rng, 2.0, 0.6, 1.0)
    x = mix(norm(snaps), 0.5 * norm(logs), bed(flame, -30))
    x = fade(lp(hp(x, 150), 5000), 0.1, 0.4)
    return room(x, rng, 0.18, 0.08)


def diesel_generator_run(rng, v):
    """A small diesel at its idle: one firing after another, each ringing in the exhaust and the block, run together
    into a low, loping note; the exhaust's puffs and the valves' clatter on top of it."""
    dur = 1.9
    n = n_of(dur)
    rate = 22.0  # firings a second, the same in every variant (its harmonics keep clear of the mains' 50/60 Hz lines)
    fire = np.zeros(n)
    at, i = rng.uniform(0, 1.0 / rate), 0
    while at < dur:
        k = n_of(rng.uniform(0.003, 0.006))  # no two firings quite alike: a push, and the roughness of the burn in it
        shot = np.hanning(k) * (1 + 0.3 * white(k, rng))
        place(fire, shot, at, (1.0, 0.72, 0.9, 0.65)[i % 4] * (1 + rng.uniform(-0.15, 0.15)))  # a four-stroke's uneven round
        at += (1.0 / rate) * (1 + rng.uniform(-0.03, 0.03))
        i += 1
    block = reson_bank(fire, [(88.0, 2.5, 1.0), (176.0, 2.5, 0.8), (310.0, 3.0, 0.6), (540.0, 3.0, 0.4), (900.0, 2.5, 0.25)]) + 0.3 * lp(fire, 1500)
    strokes = lp(np.abs(fire), 40.0)
    puffs = bp(colored(n, rng, -2), 200, 2400) * strokes / (np.max(strokes) + 1e-12)
    clatter = cracks(dur, rng, 44, 1200, 4000, sigma=0.5, ms=(1.0, 3.0), ring=0.2)[:n]
    x = mix(norm(block), 0.45 * norm(puffs), 0.08 * norm(clatter))
    x = fade(drive(lp(x, 3200) * 1.2, 1.5), 0.12, 0.4)
    return room(x, rng, 0.2, 0.07)


def crusher_run(rng, v):
    """Jaws on rock: two or three bites of stone cracking and crumbling, a big piece giving way, a grumble under it."""
    dur = 1.35
    n = n_of(dur)
    t = np.arange(n) / SR
    pace, phase = 2.3 + 0.2 * rng.uniform(-1, 1), rng.uniform(0, np.pi)
    bite = np.sin(np.pi * pace * t + phase) ** 2
    density = 0.1 + 0.9 * bite
    rock = cracks(dur, rng, 85, 500, 4500, sigma=0.95, density=density, ms=(3, 28))[:n]
    x = norm(rock)
    for k in range(3):  # where a bite closes, a bigger piece breaks
        at = ((k + 0.5) * np.pi - phase) / (np.pi * pace)
        if 0.03 < at < dur - 0.15:
            place(x, norm(burst(0.12, rng, STONE, tilt=-3.0)), at + rng.uniform(-0.03, 0.03), rng.uniform(0.5, 0.8))
    grit = bp(white(n, rng), 1300, 5000) * density ** 2 * wobble(n, rng, 18.0, 0.3, 1.0)
    grumble = bp(colored(n, rng, -6), 70, 210) * (0.55 + 0.45 * bite)
    x = mix(x, bed(grit, -27), bed(grumble, -31))
    x = fade(boxed(x, rng, 220, 800, 4, amount=0.25, cut=6500), 0.05, 0.25)
    return room(drive(x * 1.4, 1.4), rng, 0.2, 0.09)


def compressor_run(rng, v):
    """A press: air drawn, the ram coming down on its load, the air let go."""
    x = np.zeros(n_of(1.15))
    n = n_of(0.3)
    draw = bp(white(n, rng), 1800, 5200) * env_points(n, [(0, 0), (0.24, 1.0), (0.29, 0.2)]) ** 2
    place(x, norm(draw), 0.0, 0.2)
    ram = thump(0.4, 118, 56, 0.17, rng, noise=0.35, noise_cut=600)
    frame = strike(0.5, modes(196 * (1 + rng.uniform(-0.03, 0.03)), 0.42, PLATE, 8, rng, bright=0.76), rng, 0.35, 3.0, 3500)
    squeeze = crunches(0.14, rng, 90, 300, 1800, sigma=0.7, ms=(4, 16))
    place(x, norm(ram), 0.29, 0.9)
    place(x, norm(frame), 0.292, 0.5)
    place(x, norm(squeeze), 0.3, 0.3)
    place(x, norm(hiss(0.6, rng, 7500, 3000, 0.012, 0.42)), 0.5 + rng.uniform(-0.03, 0.03), 0.3)
    x = boxed(x, rng, 220, 700, 3, amount=0.25, cut=6500)
    return room(x, rng, 0.22, 0.1)


def recycler_run(rng, v):
    """Toothed rollers tearing things up: metal ripped and crumpled, a piece clattering through now and then."""
    dur = 1.45
    n = n_of(dur)
    t = np.arange(n) / SR
    turn = 0.5 + 0.5 * np.sin(2 * np.pi * 4.3 * t + rng.uniform(0, 6.28))  # the rollers' teeth coming round
    tear = cracks(dur, rng, 70, 900, 6500, sigma=0.9, density=0.25 + 0.75 * turn, ms=(2, 16), ring=0.3)[:n]
    x = norm(tear)
    for _ in range(2 + (v % 2)):
        piece = clang(0.3, rng, float(rng.uniform(600, 1000)), 7500.0, 0.16, count=30, hard=0.7)
        place(x, norm(piece), rng.uniform(0.1, dur - 0.4), rng.uniform(0.35, 0.55))
    grind = bp(colored(n, rng, -5), 90, 400) * (0.6 + 0.4 * turn)
    scrape = bp(white(n, rng), 1500, 5500) * turn * wobble(n, rng, 25.0, 0.3, 1.0)
    x = mix(x, bed(grind, -29), bed(scrape, -27))
    x = fade(boxed(x, rng, 220, 900, 4, amount=0.25, cut=7000), 0.06, 0.28)
    return room(drive(x * 1.3, 1.3), rng, 0.2, 0.09)


def stone_maker_run(rng, v):
    """Lava meeting water: steam coming up and hissing on, sinking slowly, spitting as it goes, the new stone's knock."""
    dur = 1.4
    n = n_of(dur)
    shape = env_points(n, [(0, 0), (0.07, 1.0), (0.45, 0.7), (1.4, 0.0)])
    steam = svf(white(n, rng), 3600 + 4600 * np.exp(-np.arange(n) / SR / 0.5), 0.9, "bp") * shape * wobble(n, rng, 14.0, 0.75, 1.0)
    spit = crackles(dur, rng, 110, 2500, 8000, sigma=0.8, density=shape, ms=(0.4, 2.0))[:n]
    x = mix(norm(steam), 0.22 * norm(spit))
    place(x, norm(burst(0.1, rng, STONE)), 0.2 + rng.uniform(0, 0.05), 0.25)
    return room(lp(x, 9500), rng, 0.2, 0.08)


def electric_furnace_run(rng, v):
    """Heating coils at work: hot air moving, soft, the metal ticking as it swells - no hum in it."""
    dur = 1.9
    n = n_of(dur)
    air = bp(colored(n, rng, -3), 250, 2200) * wobble(n, rng, 1.2, 0.8, 1.0) * wobble(n, rng, 9.0, 0.85, 1.0)
    x = bed(air, -18)
    at = rng.uniform(0.15, 0.5)
    for k in range(3 + v % 3):
        f = float(rng.uniform(2200, 4200))
        tick = norm(ring_of(0.08, dense_modes(rng, f, f * 2.2, 5, 0.035, 0.015), rng))  # the metal's own small ping
        place(x, tick, at, rng.uniform(0.1, 0.22))
        place(x, norm(burst(0.03, rng, ((f * 0.5, f * 2.0, 0.005, 1.0),))), at, 0.1)
        at += rng.uniform(0.18, 0.55)
    return fade(lp(hp(x, 180), 5000), 0.3, 0.6)


def laser_run(rng, v):
    """A beam held on: a thin, steady whine with a shimmer in it, the air singeing."""
    dur = 1.6
    n = n_of(dur)
    f0 = 740.0  # the same in every variant
    whine = harmonics(n, rng, f0, [1.0, 0.5, 0.33, 0.12], width=0.006, beat=0.0045, twin=0.3, steady=0.7)
    singe = hp(white(n, rng), 4500) * wobble(n, rng, 26.0, 0.0, 1.0) ** 2
    x = bed(whine, -14) * wobble(n, rng, 2.0, 0.85, 1.0) * wobble(n, rng, 11.0, 0.85, 1.0) + bed(singe, -36)
    return fade(lp(x, 6500), 0.2, 0.5)


def retort_run(rng, v):
    """A sealed iron vessel over its fire: the boil heard through the wall, dull and close; the fire under it."""
    dur = 1.9
    n = n_of(dur)
    bub = boil(dur, rng, 75, 260, 1300, sigma=0.6, density=wobble(n, rng, 2.5, 0.4, 1.0), splash=0.15)[:n]
    roil = bp(colored(n, rng, -3), 200, 1800) * wobble(n, rng, 6.0, 0.4, 1.0)
    inside = lp(mix(norm(bub), bed(roil, -22)), 1700, 4)
    inside = inside + 0.5 * reson_bank(inside, [(330 * (1 + rng.uniform(-0.04, 0.04)), 5.0, 1.0), (720, 4.0, 0.6)])
    fire = crackles(dur, rng, 7, 800, 4000, sigma=0.9)[:n]
    x = fade(mix(norm(inside), 0.14 * norm(fire)), 0.12, 0.4)
    return room(x, rng, 0.18, 0.07)


def stirring_tank_run(rng, v):
    """A paddle going round in a full tank: the liquid thrown up and falling back, bubbles carried under."""
    dur = 1.8
    n = n_of(dur)
    t = np.arange(n) / SR
    turn = 1.55  # paddle strokes a second, the same in every variant
    swing = 0.5 - 0.5 * np.cos(2 * np.pi * turn * t + rng.uniform(0, 2 * np.pi))
    fall = boil(dur, rng, 110, 380, 2600, sigma=0.6, density=0.12 + 0.88 * swing ** 2, splash=0.6)[:n]
    glug = bubbles(dur, rng, 6, 180, 420, sigma=0.4, density=swing)[:n]
    wash = lp(svf(colored(n, rng, -3), 500 + 900 * swing ** 1.5, 1.2, "bp") * (0.3 + 0.7 * swing), 3000, 4)
    x = mix(norm(fall), 0.35 * norm(glug), bed(wash, -27))
    x = fade(boxed(x, rng, 250, 600, 2, amount=0.2, cut=5000), 0.14, 0.42)
    return room(x, rng, 0.2, 0.08)


def distiller_run(rng, v):
    """Glassware at work: a quick, light boil in the flask, a drop falling into the receiver."""
    dur = 1.55
    n = n_of(dur)
    boil = bubbles(dur, rng, 75, 650, 2700, rise=0.12, sigma=0.55, density=wobble(n, rng, 3.0, 0.35, 1.0))[:n]
    glass = reson_bank(boil, [(2150 * (1 + rng.uniform(-0.05, 0.05)), 14.0, 1.0), (3480, 12.0, 0.5)])
    x = mix(norm(boil), 0.3 * norm(glass))
    for _ in range(2 + v % 2):
        place(x, norm(bubble(rng, float(rng.uniform(1300, 2100)), rise=0.3)), rng.uniform(0.15, dur - 0.3), 0.8)
    x = fade(x + bed(hp(white(n, rng), 5000), -52), 0.1, 0.38)
    return room(x, rng, 0.16, 0.07, tone=5000)


def electrolytic_cell_run(rng, v):
    """Gas coming off the plates: a fine fizz like soda water, a bigger bubble breaking through it now and then."""
    dur = 1.45
    n = n_of(dur)
    grain_ = wobble(n, rng, 55.0, 0.35, 1.0) * wobble(n, rng, 4.0, 0.6, 1.0)  # the fizz is made of specks, never even
    fizz = bp(colored(n, rng, -2), 1800, 6000) * grain_
    specks = cracks(dur, rng, 90, 2500, 6000, sigma=0.6, ms=(0.4, 1.5), ring=0.0)[:n]
    big = breathy_boil(dur, rng, 4, 450, 900, sigma=0.4, splash=0.5, breath=0.5)[:n]
    x = fade(mix(bed(fizz, -19), 0.3 * norm(specks), 0.35 * norm(big)), 0.1, 0.38)
    return room(lp(x, 6500), rng, 0.15, 0.06, tone=5000)


def water_pump_run(rng, v):
    """Two strokes of a pump: the valve's dry clack, the water drawn up and falling through."""
    x = np.zeros(n_of(1.55))
    for k, at in enumerate((0.03, 0.74 + rng.uniform(-0.04, 0.04))):
        valve = burst(0.1, rng, ((250.0, 900.0, 0.03, 1.0), (900.0, 3500.0, 0.015, 0.8)))
        water = breathy_boil(0.45, rng, 60, 300, 1600, sigma=0.6, density=env_points(n_of(0.45), [(0, 0.3), (0.12, 1.0), (0.45, 0.1)]), splash=0.7, breath=0.6)
        n = n_of(0.45)
        wash = bp(colored(n, rng, -3), 400, 2500) * env_points(n, [(0, 0), (0.08, 1), (0.25, 0.5), (0.45, 0)]) * wobble(n, rng, 16.0, 0.4, 1.0)
        g = 1.0 if k == 0 else 0.85
        place(x, norm(valve), at, 0.7 * g)
        place(x, norm(water), at + 0.06, 0.7 * g)
        place(x, norm(wash), at + 0.05, 0.2 * g)
    return room(lp(x, 5000), rng, 0.2, 0.08)


def ventilation_fan_run(rng, v):
    """A fan: the air through its blades, soft and even - a sigh of air, nothing heavy under it."""
    dur = 1.9
    n = n_of(dur)
    t = np.arange(n) / SR
    turns = 23.0  # turns a second, the same in every variant; four blades
    rush = bp(colored(n, rng, -3), 400, 3200) * (1 + 0.15 * np.sin(2 * np.pi * turns * 4 * t + wobble(n, rng, 2.0)))
    x = bed(rush, -17) * wobble(n, rng, 1.5, 0.85, 1.0)
    return fade(lp(hp(x, 250), 4500, 4), 0.25, 0.5)


def printing_press_run(rng, v):
    """A platen press: the lever thrown, the platen's thump on the bed, the sheet drawn off, the lever home."""
    x = np.zeros(n_of(0.95))
    place(x, norm(clang(0.08, rng, float(rng.uniform(900, 1200)), 6000.0, 0.035, count=16, hard=0.7)), 0.0, 0.3)
    place(x, norm(thud(0.3, rng, 85.0, 0.1, tone=0.3)), 0.1, 0.8)
    place(x, norm(burst(0.2, rng, WOOD)), 0.1, 0.5)
    place(x, norm(clang(0.3, rng, 230.0, 3000.0, 0.2, count=30, hard=0.3)), 0.102, 0.25)
    n = n_of(0.3)
    sheet = bp(white(n, rng), 2400, 7000) * env_points(n, [(0, 0), (0.07, 1), (0.2, 0.5), (0.3, 0)]) * wobble(n, rng, 40.0, 0.4, 1.0)
    place(x, norm(sheet), 0.34 + rng.uniform(-0.02, 0.02), 0.12)
    place(x, norm(clang(0.07, rng, float(rng.uniform(800, 1000)), 5000.0, 0.03, count=14, hard=0.6)), 0.66 + rng.uniform(-0.02, 0.02), 0.25)
    return room(lp(x, 7500), rng, 0.22, 0.1)


# ------------------------------------------------------------------ hand work, the kitchen and small things

def rice_polisher_turn(rng, v):
    """Grain rubbing on grain: a short dry rustle (a fifth of a second - a Motor turns it every half second)."""
    dur = 0.2
    n = n_of(dur)
    shape = env_points(n, [(0, 0), (0.025, 1.0), (0.09, 0.6), (0.2, 0.0)])
    grains = crackles(dur, rng, 520, 2400, 8000, sigma=0.6, density=shape, ms=(0.25, 1.2))[:n]
    rub = bp(white(n, rng), 2200, 7200) * shape * wobble(n, rng, 45.0, 0.5, 1.0)
    return hp(mix(norm(grains), bed(rub, -22)), 900)


def hand_quern_turn(rng, v):
    """A millstone taken round on its bed: stone dragging on stone, rough and dry, the grain cracking between them."""
    dur = 0.5
    n = n_of(dur)
    shape = env_points(n, [(0, 0), (0.14, 1.0), (0.32, 0.85), (0.5, 0.0)])
    grit = cracks(dur, rng, 260, 700, 5000, sigma=0.6, density=shape * wobble(n, rng, 30.0, 0.35, 1.0), ms=(0.6, 3.5), ring=0.0)[:n]
    drag = bp(colored(n, rng, -2), 500, 4500) * shape * wobble(n, rng, 30.0, 0.3, 1.0)
    stone = reson_bank(grit + 0.3 * drag, [(float(rng.uniform(480, 560)), 2.0, 1.0), (float(rng.uniform(950, 1150)), 2.0, 0.8)])
    x = mix(norm(grit), bed(drag, -24), 0.35 * norm(stone))
    return room(lp(hp(x, 300), 6500), rng, 0.16, 0.07)


def oil_press_turn(rng, v):
    """The press screwed down a turn: seeds giving under it, hull by hull, with a dry crunch."""
    dur = 0.5
    n = n_of(dur)
    shape = env_points(n, [(0, 0), (0.06, 1.0), (0.28, 0.7), (0.5, 0.0)])
    hulls = cracks(dur, rng, 28, 1000, 6000, sigma=0.9, density=shape, ms=(4, 20), ring=0.0)[:n]
    husks = cracks(dur, rng, 120, 1500, 7000, sigma=0.6, density=shape, ms=(0.6, 3), ring=0.0)[:n]
    x = mix(norm(hulls), 0.35 * norm(husks))
    return room(lp(hp(x, 300), 7500), rng, 0.16, 0.07)


def iron_pot_fry(rng, v):
    """Hot oil: the fat spitting and popping, never evenly, a low seethe under it."""
    dur = 1.7
    n = n_of(dur)
    fits = wobble(n, rng, 5.0, 0.3, 1.0) * wobble(n, rng, 17.0, 0.6, 1.0)
    pops = cracks(dur, rng, 34, 1200, 5000, sigma=0.9, density=fits, ms=(1.2, 6), ring=0.15)[:n]
    fine = cracks(dur, rng, 160, 2000, 6000, sigma=0.7, density=fits, ms=(0.4, 1.8), ring=0.0)[:n]
    seethe = bp(colored(n, rng, -2), 1500, 5000) * fits
    x = fade(mix(norm(pops), 0.5 * norm(fine), bed(seethe, -31)), 0.1, 0.4)
    return room(lp(x, 6500), rng, 0.14, 0.05, tone=5000)


def iron_pot_boil(rng, v):
    """A pot on the boil: bubbles rolling up and breaking at the top, round and close in the iron."""
    dur = 1.7
    n = n_of(dur)
    roll = boil(dur, rng, 70, 380, 2300, sigma=0.6, density=wobble(n, rng, 3.0, 0.4, 1.0), splash=0.35)[:n]
    pot = roll + 0.4 * reson_bank(roll, [(float(rng.uniform(520, 600)), 4.0, 1.0), (float(rng.uniform(980, 1080)), 4.0, 0.5)])
    steam = hp(white(n, rng), 4000) * wobble(n, rng, 2.0, 0.3, 1.0)
    x = fade(mix(norm(pot), bed(steam, -40)), 0.1, 0.4)
    return room(lp(x, 7000), rng, 0.14, 0.06)


def grill_sizzle(rng, v):
    """Fat falling on the coals: it spits, flares into a soft hiss and crackles on."""
    dur = 1.5
    n = n_of(dur)
    x = np.zeros(n)
    at = rng.uniform(0.0, 0.08)
    for k in range(2 + v % 2):
        m = n_of(0.8)
        flare = env_points(m, [(0, 0), (0.06, 1.0), (0.25, 0.45), (0.8, 0.0)]) * wobble(m, rng, 24.0, 0.3, 1.0)
        place(x, bed(bp(colored(m, rng, -2), 1500, 5000) * flare, -21), at, (1.0, 0.6, 0.75)[k])
        place(x, norm(cracks(0.5, rng, 40, 1500, 5000, sigma=0.8, ms=(0.8, 4), ring=0.1)), at, 0.5 * (1.0, 0.6, 0.75)[k])
        at += rng.uniform(0.3, 0.5)
    x = fade(x, 0.02, 0.35)
    return room(lp(x, 6500), rng, 0.14, 0.05, tone=5000)


def repair_box_mend(rng, v):
    """A small hammer on a small anvil: two or three light taps."""
    x = np.zeros(n_of(0.6))
    f0 = float(rng.uniform(1750, 2050))
    at = 0.0
    for k in range(2 + v % 2):
        tap = strike(0.22, modes(f0 * (1 + rng.uniform(-0.012, 0.012)), 0.13, BAR, 4, rng, bright=0.6, damp=0.6), rng, 0.6, 1.2, 7500)
        low = reson(click(rng, 2.0, 1500), 520, 3.0)
        place(x, norm(tap), at, (1.0, 0.6, 0.8)[k])
        place(x, norm(low), at, 0.25 * (1.0, 0.6, 0.8)[k])
        at += rng.uniform(0.13, 0.17)
    return room(x, rng, 0.2, 0.08)


def repair_box_done(rng, v):
    """Done: one clear, light ring of the anvil, heard across a workshop."""
    f0 = float(rng.uniform(1560, 1640))
    ring = strike(1.1, modes(f0, 0.95, BAR, 4, rng, bright=0.6, damp=0.8), rng, 0.5, 1.2, 8000)
    twin = modal(1.1, [(f0 * 1.004, 0.9, 0.5)])  # a hair off: the ring beats slowly, as a real one does
    x = norm(ring) + 0.3 * twin
    place(x, norm(reson(click(rng, 2.0, 1500), 480, 3.0)), 0.0, 0.3)
    return room(x, rng, 0.25, 0.1)


def dice_roll(rng, v):
    """A die thrown on a table: it skips, each bounce sooner and softer, and rattles to rest."""
    x = np.zeros(n_of(0.62))
    at, gap, gain = 0.0, rng.uniform(0.1, 0.13), 1.0
    for k in range(7):
        f = float(rng.uniform(1000, 1500))
        knock = strike(0.05, [(f, 0.02, 1.0), (f * rng.uniform(1.8, 2.3), 0.013, 0.6), (f * rng.uniform(3.0, 3.6), 0.008, 0.3)], rng, 0.8, 0.8, 5500)
        table = reson(click(rng, 1.5, 1200), float(rng.uniform(210, 270)), 3.5)
        place(x, norm(knock), at, gain)
        place(x, norm(table), at, 0.4 * gain)
        at += gap
        gap *= rng.uniform(0.6, 0.72)
        gain *= rng.uniform(0.68, 0.8)
        if gap < 0.012:
            break
    return room(x, rng, 0.15, 0.06)


def camera_shutter(rng, v):
    """A brass camera's shutter: the release, the blades running across, and shut with a clack."""
    x = np.zeros(n_of(0.3))
    release = burst(0.03, rng, ((1500.0, 6500.0, 0.006, 1.0), (500.0, 1500.0, 0.01, 0.6)))
    gap = 0.05 + rng.uniform(-0.006, 0.006)
    n = n_of(gap)
    blades = bp(white(n, rng), 2500, 9000) * env_points(n, [(0, 0.2), (gap * 0.5, 1.0), (gap, 0.3)]) * wobble(n, rng, 300.0, 0.4, 1.0)
    shut = burst(0.08, rng, ((350.0, 1300.0, 0.022, 1.0), (1300.0, 5000.0, 0.014, 0.9), (5000.0, 10000.0, 0.006, 0.4)))
    spring = ring_of(0.08, dense_modes(rng, 2500.0, 7000.0, 8, 0.04, 0.015), rng)
    place(x, norm(release), 0.0, 0.6)
    place(x, norm(blades), 0.004, 0.22)
    place(x, norm(shut), gap, 1.0)
    place(x, norm(spring), gap + 0.004, 0.08)
    return room(x, rng, 0.12, 0.05, tone=5000)


def wrench_turn(rng, v):
    """A ratchet wrench taken round: its pawl over a few teeth, the last one seating."""
    x = np.zeros(n_of(0.36))
    at, count = 0.0, 4 + v % 2
    for k in range(count):
        last = k == count - 1
        tooth = burst(0.04, rng, ((1800.0, 7500.0, 0.008, 1.0), (700.0, 1800.0, 0.012, 0.7 if last else 0.4)))
        ping = ring_of(0.05, dense_modes(rng, 2200.0, 6000.0, 6, 0.03, 0.012), rng)
        place(x, norm(tooth), at, 1.0 if last else rng.uniform(0.5, 0.7))
        place(x, norm(ping), at, 0.2)
        at += rng.uniform(0.042, 0.052) * (1.0 + 0.1 * k)
    return room(x, rng, 0.12, 0.05, tone=5000)


def oil_can_squirt(rng, v):
    """A thumb oiler: its tin bottom pressed in and springing back - pok, pok - a drop of oil out of the spout."""
    x = np.zeros(n_of(0.32))
    f = float(rng.uniform(640, 760))
    for k, (at, up) in enumerate(((0.0, 1.0), (0.105 + rng.uniform(-0.01, 0.01), 1.22))):
        tin = strike(0.12, modes(f * up, 0.055, PLATE, 5, rng, bright=0.7, damp=0.5), rng, 0.5, 1.5, 5000)
        place(x, norm(tin), at, 1.0 if k == 0 else 0.75)
    n = n_of(0.05)
    drop = bp(white(n, rng), 2500, 6500) * env_points(n, [(0, 0), (0.008, 1.0), (0.05, 0.0)])
    place(x, norm(drop), 0.012, 0.1)
    return room(x, rng, 0.12, 0.05)


def motor_run(rng, v):
    """A small motor running: a whirr in the middle of the ear, the armature's poles fluttering by, the brushes
    ticking - a thing going round, and no drone under it."""
    dur = 1.4
    n = n_of(dur)
    t = np.arange(n) / SR
    flutter = 1 + 0.4 * np.sin(2 * np.pi * 37.0 * t + 0.8 * wobble(n, rng, 2.0))  # the armature's poles going by
    air = bp(colored(n, rng, -3), 450, 2200) * flutter
    whine = band_tone(n, rng, 520.0, 0.05) + 0.5 * band_tone(n, rng, 1040.0, 0.05)  # the same pitch in every variant
    brushes = cracks(dur, rng, 37, 1500, 4500, sigma=0.4, ms=(0.6, 2.0), ring=0.2)[:n]
    x = bed(air, -21) * wobble(n, rng, 1.5, 0.9, 1.0) + bed(whine, -21) * flutter + 0.15 * norm(brushes)
    return fade(lp(hp(x, 300), 4500), 0.12, 0.45)


def drive_wheel_turn(rng, v):
    """A belted wheel going round: the belt's seam knocking over the rim each turn, the belt's dry rub between -
    koto, shu, koto."""
    dur = 1.5
    n = n_of(dur)
    x = np.zeros(n)
    turn = 0.26  # seconds a turn, the same in every variant
    at = rng.uniform(0.02, 0.1)
    while at < dur - 0.12:
        knock = burst(0.08, rng, ((180.0, 700.0, 0.03, 1.0), (700.0, 2600.0, 0.014, 0.6)))
        place(x, norm(knock), at, rng.uniform(0.7, 1.0))
        k = n_of(turn * 0.6)
        rub = bp(colored(k + 64, rng, -2), 900, 3500)[64:] * env_points(k, [(0, 0), (turn * 0.2, 1.0), (turn * 0.6, 0.0)]) * wobble(k, rng, 40.0, 0.5, 1.0)
        place(x, bed(rub, -24), at + turn * 0.3)
        at += turn * (1 + rng.uniform(-0.07, 0.07))
    roll = bp(colored(n, rng, -4), 200, 900) * wobble(n, rng, 4.0, 0.6, 1.0)
    x = fade(x + bed(roll, -31), 0.08, 0.3)
    return room(x, rng, 0.16, 0.06)


def spawner_controller_run(rng, v):
    """Something not quite of this world at work: a soft, clear chord of air that swells, wavers and goes - no buzz."""
    dur = 1.9
    n = n_of(dur)
    notes = [(196.0, 1.0), (293.7, 0.8), (440.0, 0.6), (587.3, 0.35)]  # open fifths: neither glad nor sad; the same in every variant
    chord = air_chord(n, rng, notes)
    breath = bp(colored(n, rng, -3), 400, 2500) * wobble(n, rng, 0.7, 0.3, 1.0)
    shape = env_points(n, [(0, 0), (0.5, 1.0), (1.0, 0.8), (1.9, 0.0)])
    x = (bed(chord, -16) + bed(breath, -33)) * shape
    return room(lp(x, 3500), rng, 0.6, 0.25, tone=3000)


# ------------------------------------------------------------------ a machine stopping, its bell, and small things about the house

def machine_stop(rng, v):
    """A machine come to a stop: its contactor dropping out, then the drive running down in two clunks inside its
    steel case, the second lower, softer and late. Nothing rings and nothing beeps: it is the machine that is heard,
    not an alarm. (A breath of air let go under it, as first made, was all our judges heard - a hiss, a slap - so
    only a trace is left; and three bare knocks were a knock at a door, so there are two, with the case in them.)"""
    x = np.zeros(n_of(0.6))
    clack = burst(0.05, rng, ((600.0, 2400.0, 0.016, 1.0), (2400.0, 6000.0, 0.006, 0.3), (250.0, 600.0, 0.024, 0.6)))
    place(x, norm(clack), 0.0, 0.55)
    at, gap, low, gain = 0.09 + rng.uniform(-0.008, 0.008), 0.16, 1.0, 1.0
    for _ in range(2):
        knock = burst(0.22, rng, ((150.0 * low, 560.0 * low, 0.08, 1.0), (560.0 * low, 1900.0 * low, 0.035, 0.6),
                                  (1900.0 * low, 4500.0, 0.01, 0.15)), attack=0.001)
        case = ring_of(0.2, dense_modes(rng, 320.0 * low, 1700.0 * low, 12, 0.11, 0.04), rng)
        place(x, norm(knock), at, gain)
        place(x, norm(case), at, 0.3 * gain)
        at += gap * (1 + rng.uniform(-0.06, 0.06))
        gap, low, gain = gap * 1.3, low * 0.76, gain * 0.6
    n = n_of(0.12)
    air = bp(colored(n + 64, rng, -4)[64:], 700, 2600) * env_points(n, [(0, 0), (0.015, 1.0), (0.12, 0.0)]) ** 2
    place(x, bed(air, -30), 0.02)
    return room(x, rng, 0.18, 0.07)


def machine_bell_ring(rng, v):
    """A small electric bell on a machine's side: its hammer trembling on a steel gong for most of a second, the gong
    ringing out after. The same bell at every ring - the gong's notes are fixed - only the hammer's dance differs."""
    dur = 1.5
    f0 = 1510.0
    gong = [(f0, 0.55, 1.0), (f0 * 1.0035, 0.5, 0.6),  # a pair a hair apart, as a real gong's are: it beats
            (f0 * 2.32, 0.4, 0.7), (f0 * 2.335, 0.36, 0.35), (f0 * 3.9, 0.26, 0.3), (f0 * 5.75, 0.16, 0.1)]
    cast = sfx.rng_for(MODID, "machine_bell", "gong")  # the many small modes of the casting: this bell's, every time
    gong += [(f, t60, a * 0.2) for f, t60, a in dense_modes(cast, 2600.0, 9000.0, 14, 0.2, 0.06, tilt=-3.0)]
    x = np.zeros(n_of(dur))
    at, until = 0.0, 0.7 + 0.05 * (v % 3)
    while at < until:
        blow = rng.uniform(0.7, 1.0) * (0.6 if at < 0.03 else 1.0)  # the first touch is the lightest
        place(x, ring_of(dur - at, gong, rng), at, blow)
        place(x, click(rng, 0.8, 9000.0, 1500.0), at, 0.5 * blow)  # the hammer's own tick
        at += (1.0 / 23.0) * (1 + rng.uniform(-0.07, 0.07))
    return room(lp(x, 9000), rng, 0.3, 0.1, tone=5000)


def sorter_sort(rng, v):
    """A sorter's gate letting one thing through: a light flap falling shut, and its small bounce."""
    x = np.zeros(n_of(0.14))
    flap = burst(0.05, rng, ((500.0, 1900.0, 0.016, 1.0), (1900.0, 5200.0, 0.007, 0.5), (220.0, 500.0, 0.022, 0.4)), attack=0.0008)
    place(x, norm(flap), 0.0, 1.0)
    bounce = burst(0.03, rng, ((700.0, 2600.0, 0.009, 1.0), (2600.0, 6000.0, 0.005, 0.4)))
    place(x, norm(bounce), rng.uniform(0.028, 0.04), rng.uniform(0.22, 0.34))
    return room(x, rng, 0.1, 0.04)


def seat_sit(rng, v):
    """Sitting down on a wooden seat: cloth brushing it, the weight landing dull and soft, a leg settling on the
    floor. No creak in it. (The cloth is kept faint and smooth: louder, or fluttering, it was a zip or a scrape to
    our judges, and the landing without the seat's own wood in it something ruder.)"""
    x = np.zeros(n_of(0.42))
    n = n_of(0.1)
    cloth = bp(colored(n + 64, rng, -3)[64:], 500, 2500) * env_points(n, [(0, 0), (0.045, 1.0), (0.1, 0.0)]) ** 1.5
    place(x, bed(cloth, -27), 0.0)
    at = 0.05 + rng.uniform(-0.005, 0.005)
    land = burst(0.22, rng, ((110.0, 400.0, 0.085, 1.0), (400.0, 1500.0, 0.035, 0.5), (1500.0, 5000.0, 0.012, 0.12)), attack=0.003)
    place(x, norm(land), at, 1.0)
    place(x, norm(burst(0.08, rng, WOOD)), at + 0.004, 0.22)  # the seat's boards answering
    place(x, norm(burst(0.06, rng, WOOD)), at + 0.14 + rng.uniform(-0.02, 0.03), rng.uniform(0.14, 0.22))
    return room(x, rng, 0.15, 0.06)


# a wind chime's six tubes: G5 A5 B5 D6 E6 G6, the five notes of G major's pentatonic - any two of them agree, so
# two chimes heard at once, or two variants one after the other, never quarrel
CHIME_NOTES = (783.99, 880.0, 987.77, 1174.66, 1318.51, 1567.98)


def chime_tube(rng, f, dur):
    """One tube of a wind chime touched by its wooden clapper: a free tube's partials (the first rings on, the upper
    ones are soon gone), each a close pair that beats slowly, as no tube is quite round. The touch is soft: the ring
    comes up over a few milliseconds and the clapper is barely heard."""
    f *= 1 + rng.uniform(-0.002, 0.002)
    tube = []
    for ratio, t60, amp in ((1.0, 1.25, 1.0), (2.756, 0.6, 0.5), (5.404, 0.28, 0.16), (8.933, 0.12, 0.04)):
        t60 *= rng.uniform(0.85, 1.1)
        amp *= rng.uniform(0.75, 1.0)
        tube.append((f * ratio, t60, amp))
        tube.append((f * ratio * (1 + rng.uniform(0.0012, 0.003)), t60 * 0.9, amp * rng.uniform(0.3, 0.6)))
    ring = fade(norm(ring_of(dur, tube, rng)), 0.003, 0.0)
    return place(ring, click(rng, 2.5, 2500.0, 300.0), 0.0, 0.08)


def wind_chime_chime(rng, v):
    """A garden wind chime in a breath of wind: a few tubes touched one after another, some of them twice, each
    ringing on over the next - a second or two of it. Out of doors: almost no room about it."""
    dur = 2.0
    x = np.zeros(n_of(dur))
    count = (2, 3, 3, 4, 4, 5)[v % 6]
    at = 0.0
    for k, i in enumerate(rng.choice(len(CHIME_NOTES), size=count, replace=True)):
        place(x, chime_tube(rng, CHIME_NOTES[int(i)], dur - at), at, rng.uniform(0.7, 1.0) if k == 0 else rng.uniform(0.3, 0.9))
        at += rng.uniform(0.05, 0.2)
    return room(x, rng, 0.3, 0.04, tone=6000)


# ------------------------------------------------------------------ the table

# event: (maker, variants, volume in sounds.json, extra) - extra: pitch = the pitch the code plays it at when that is
# not about 1 (the file is stored slowed or quickened by it, so it is heard as made), and finish()'s settings.
# Every file is finished to one loudness (-23 dB as sfx.loudness hears it), so the volume alone says how loud a sound
# plays: a machine's work about -34 to -40 dB (0.28 to 0.14; the Motor's hum the user picked is at -38), a stroke of
# hand work about -30, a thing meant to be noticed (the Repair Box's ring, the shutter) -25 and up.
SOUNDS = {
    "block.generator.run": (generator_run, 3, 0.18, {}),
    "block.diesel_generator.run": (diesel_generator_run, 3, 0.2, {}),
    "block.crusher.run": (crusher_run, 3, 0.35, {}),
    "block.compressor.run": (compressor_run, 3, 0.36, dict(level=-25.0)),
    "block.recycler.run": (recycler_run, 3, 0.35, {}),
    "block.stone_maker.run": (stone_maker_run, 3, 0.18, {}),
    "block.electric_furnace.run": (electric_furnace_run, 3, 0.14, dict(fout=0.3)),
    "block.laser.run": (laser_run, 2, 0.14, dict(fout=0.3)),
    "block.retort.run": (retort_run, 3, 0.22, {}),
    "block.stirring_tank.run": (stirring_tank_run, 3, 0.18, {}),
    "block.distiller.run": (distiller_run, 3, 0.28, {}),
    "block.electrolytic_cell.run": (electrolytic_cell_run, 3, 0.28, {}),
    "block.water_pump.run": (water_pump_run, 3, 0.18, {}),
    "block.ventilation_fan.run": (ventilation_fan_run, 2, 0.11, dict(fout=0.3)),
    "block.printing_press.run": (printing_press_run, 3, 0.32, {}),
    "block.rice_polisher.turn": (rice_polisher_turn, 4, 0.32, {}),
    "block.hand_quern.turn": (hand_quern_turn, 4, 1.0, {}),
    "block.oil_press.turn": (oil_press_turn, 4, 1.0, {}),
    "block.iron_pot.fry": (iron_pot_fry, 3, 0.45, {}),
    "block.iron_pot.boil": (iron_pot_boil, 3, 0.6, {}),
    "block.grill.sizzle": (grill_sizzle, 3, 0.5, {}),
    "block.repair_box.mend": (repair_box_mend, 3, 0.45, {}),
    "block.repair_box.done": (repair_box_done, 2, 0.8, {}),
    "block.dice.roll": (dice_roll, 4, 0.55, dict(pitch=1.6)),
    "item.camera.shutter": (camera_shutter, 3, 1.0, {}),
    "item.wrench.turn": (wrench_turn, 4, 1.0, {}),
    "item.oil_can.squirt": (oil_can_squirt, 3, 0.7, {}),
    # recipes for three of the four the user picked from Kenney's packs (make_sounds.py), which the outside listener
    # called a rumble, a mains hum and a buzzer: the user, 2026-11-10, of those picks - asked to choose, they chose, and
    # cannot tell good from bad; if better can be made, replace them. Heard blind beside Kenney's, the Drive Wheel's
    # was taken (Kenney's a mains hum to it both times); the other two it called as unfit as what they would replace
    "block.motor.run": (motor_run, 3, 0.17, dict(fout=0.3)),
    "block.drive_wheel.turn": (drive_wheel_turn, 3, 0.2, {}),
    "block.spawner_controller.run": (spawner_controller_run, 3, 0.17, {}),
    # for the features of 2026-11-11 (the user: 「できそうなことはぜんぶやっていいよ」): a machine's stop heard, the bell that
    # rings for it, a sorter's gate, sitting down, the wind chime. A cue meant to be noticed about -27 dB, the bell
    # at full, what is only there to be felt (the gate) under a machine's work. All five taken by the user's ear, 2026-11-11.
    "block.machine.stop": (machine_stop, 3, 0.6, {}),
    "block.machine_bell.ring": (machine_bell_ring, 3, 1.0, {}),
    "block.sorter.sort": (sorter_sort, 4, 0.22, {}),
    "block.seat.sit": (seat_sit, 3, 0.4, {}),
    "block.wind_chime.chime": (wind_chime_chime, 6, 0.45, dict(fout=0.25)),
}


# Held back: their recipes stay above to be worked on; their events go on playing what they played (the vanilla event,
# the volume and the pitch written here, or Kenney's file), and no file of theirs ships. Decided by the user's own ear
# (2026-11-10 night: 「実際に聞き比べて比較したい」): every sound heard beside what it would replace, both as the game plays
# them (AI_review/sounds/compare in the main checkout, made by compare_tool/compare_page.py there), and these five kept as
# they were - with the Oil Can's, which the judgings had let in. The judgings before it (tools/sounds/judge.py, and
# Gemini in Antigravity twice, DECISIONS.md "Sound is left to me") had held back sixteen; the user took eleven of them.
HELD_BACK = {
    "block.diesel_generator.run": [("minecraft:entity.minecart.riding", 0.25, 0.55)],
    "block.water_pump.run": [("minecraft:block.water.ambient", 0.25, 1.3)],
    "block.electric_furnace.run": [("minecraft:block.beacon.ambient", 0.25, 1.4)],
    "block.laser.run": [("minecraft:block.beacon.ambient", 0.3, 1.8)],
    # the honey block's slide it played before, at the pitch the code gave it then (the code plays it at 1.0 now)
    "item.oil_can.squirt": [("minecraft:block.honey_block.slide", 1.0, 1.4)],
    # Kenney's file, the user's pick of 2026-10-11, kept by their ear
    "block.motor.run": [("nookworks:block/motor_run", 0.5, None)],
    # A new sound waits here for the user's ear as None: its event plays the placeholder sounds.json has for it, left as
    # it is there. The five of 2026-11-11 (a machine's stop, the Machine Bell, the sorter, sitting down, the Wind Chime)
    # waited so, and were all taken over their vanilla stand-ins the same day (AI_review/sounds/compare_new).
}


def file_of(event):
    kind, thing, what = event.split(".")
    return f"{kind}/{thing}_{what}"


def render(event, variant):
    make, _, _, extra = SOUNDS[event]
    extra = dict(extra)
    pitch = extra.pop("pitch", 1.0)
    x = sfx.finish(make(sfx.rng_for(MODID, event, variant), variant), **extra)
    if pitch != 1.0:  # the code plays it at this pitch: stored the other way, so it is heard as made
        x = sfx.finish(signal.resample_poly(x, int(round(pitch * 100)), 100), **{**extra, "lowcut": 20.0, "highcut": 20000.0})
    return x


def main(args):
    wav = None
    if "--wav" in args:
        wav = Path(args[args.index("--wav") + 1])
        wav.mkdir(parents=True, exist_ok=True)
        args = [a for a in args if a != "--wav" and a != str(wav)]
    table = json.loads((ASSETS / "sounds.json").read_bytes())
    for event, (make, variants, volume, extra) in SOUNDS.items():
        if args and not any(a in event for a in args):
            continue
        if event in HELD_BACK:
            for stale in (ASSETS / "sounds").glob(f"{file_of(event)}_*.ogg"):
                stale.unlink()
            if HELD_BACK[event] is None:
                print(f"{event}: held back, plays the placeholder sounds.json has for it")
                continue
            table[event] = {"subtitle": f"subtitles.{MODID}.{event}", "sounds": [
                {"name": name, "volume": vol} if pitch is None else {"name": name, "type": "event", "volume": vol, "pitch": pitch}
                for name, vol, pitch in HELD_BACK[event]]}
            print(f"{event}: held back, plays {', '.join(name for name, _, _ in HELD_BACK[event])}")
            continue
        entry = []
        for v in range(variants):
            x = render(event, v)
            name = f"{file_of(event)}_{v + 1}"
            sfx.write_ogg(ASSETS / "sounds" / f"{name}.ogg", x)
            if wav:
                sfx.write_wav(wav / f"{name.replace('/', '-')}.wav", x)
            entry.append({"name": f"{MODID}:{name}", "volume": volume})
            print(f"{name}.ogg  {len(x) / SR:.2f} s  {sfx.loudness(x):6.1f} dB  peak {20 * np.log10(np.max(np.abs(x))):5.1f}")
        table[event] = {"subtitle": f"subtitles.{MODID}.{event}", "sounds": entry}
    (ASSETS / "sounds.json").write_bytes((json.dumps(table, indent=2) + "\n").encode("utf-8"))


if __name__ == "__main__":
    main(sys.argv[1:])
