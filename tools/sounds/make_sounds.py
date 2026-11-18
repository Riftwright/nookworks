"""One of the mod's own sound files, cut out of Kenney's CC0 sound packs (kenney.nl - free to use, change and pass on, no credit
needed): mono (Minecraft places only a mono sound where it plays; a stereo one is heard everywhere at full volume),
faded out, levelled to one loudness, the same bytes every run.

The pack is not kept here. Download it from kenney.nl into a folder of its own (as it comes, zipped):
    Impact Sounds   kenney_impact-sounds.zip
then, from the repo root (needs ffmpeg on the PATH):
    python tools/sounds/make_sounds.py <that folder>

Picked by the user from three candidates (2026-10-11, the listening page): the Trash Can's tin knock. sounds.json sets
how loud it plays. Three more were picked then, from the Sci-fi Sounds pack, and are computed by gen_sounds.py now:
the Drive Wheel's hum (engineCircular_002, slowed to 0.85), from 2026-11-10, when a listener heard a mains hum in it;
the Spawner Controller's swell (forceField_001, slowed to 0.8), the same night, by the user's own ear beside the
computed one; and the Motor's low running hum (spaceEngineLow_000, 1.3 seconds from its first second), from
2026-11-12, when the user found it little like a motor and odd for sounding now and then - a loop of ours since.
"""
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/nookworks/sounds"

# file: (pack, path in the pack, start s, length s, fade in s, fade out s, speed - the pitch, as a record played faster)
SOUNDS = {
    "block/trash_can_swallow": ("kenney_impact-sounds.zip", "Audio/impactTin_medium_000.ogg", 0.0, 0.16, 0.0, 0.05, 1.0),
}
TARGET_MEAN = -20.0  # dB, each sound's mean loudness; its peak is held under -1 dB
# no tags, no encoder version, a fixed stream serial: the file comes out the same each run
EXACT = ["-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact"]


def ffmpeg(*args):
    return subprocess.run(["ffmpeg", "-hide_banner", "-y", "-loglevel", "error", *args], capture_output=True, text=True,
                          check=True)


def mean_volume(path):
    ran = subprocess.run(["ffmpeg", "-hide_banner", "-i", str(path), "-af", "volumedetect", "-f", "null", "-"],
                         capture_output=True, text=True)
    return float(re.search(r"mean_volume: ([-0-9.]+) dB", ran.stderr).group(1))


def main(packs):
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        for name, (pack, inner, start, length, fade_in, fade_out, speed) in SOUNDS.items():
            source = tmp / Path(inner).name
            with zipfile.ZipFile(packs / pack) as z:
                source.write_bytes(z.read(inner))
            chain = [f"atrim=start={start}:duration={length}", "asetpts=PTS-STARTPTS"]
            if speed != 1.0:
                chain += [f"asetrate=44100*{speed}", "aresample=44100"]
            chain += ["aformat=channel_layouts=mono"]
            if fade_in:
                chain += [f"afade=t=in:d={fade_in}"]
            chain += [f"afade=t=out:st={max(0.0, length / speed - fade_out):.3f}:d={fade_out}"]
            raw = tmp / "raw.wav"
            ffmpeg("-i", str(source), "-af", ",".join(chain), "-ar", "44100", "-ac", "1", *EXACT, str(raw))
            gain = TARGET_MEAN - mean_volume(raw)
            out = OUT / f"{name}.ogg"
            out.parent.mkdir(parents=True, exist_ok=True)
            ffmpeg("-i", str(raw), "-af", f"volume={gain:.2f}dB,alimiter=limit=0.89:level=false", "-c:a", "libvorbis",
                   "-q:a", "5", *EXACT, str(out))
            print(f"{out.relative_to(ROOT).as_posix()}  {length / speed:.2f} s")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(Path(sys.argv[1]))
