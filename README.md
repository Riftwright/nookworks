# Nookworks for Minecraft 1.21.1

This branch (`mc1.21.1`) is Nookworks for **Minecraft 1.21.1 on NeoForge 21.1** (21.1.219 or newer): the same mod as
the `main` branch, which follows the newest Minecraft, brought back to 1.21.1 so that it can be played beside the mods
that live there.

Nookworks is a vanilla-style decoration and tech mod: Thin Rope and Thin Chain with café lights, posters from a
Printing Press, furniture and a kitchen, Storage Barrels, Forge Energy machines, tree felling and vein mining, and more.

- Download: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/nookworks) (the files marked 1.21.1)
- Changes: [CHANGELOG.md](CHANGELOG.md) ("For Minecraft 1.21.1" at the top)

## Beside other mods

Nookworks is meant as one mod among many in a pack, and stays out of the others' way:

- **Create** (6.0.10 or newer, optional): a Create Shaft set against a hand-worked machine or a Drive Wheel turns it,
  and a Nookworks Motor turns Create's Shafts - nothing in between either way, stress and speed in Create's own terms,
  Engineer's Goggles and Ponder included. Like machines do each other's work.
- **Farmer's Delight** (optional): one kitchen - each mod's pots cook on the other's heat and make the other's dishes.
- **JEI** and **Jade** (optional) show Nookworks' recipes and machines.

What differs from the edition for the newest Minecraft (woods and blocks 1.21.1 does not have, and the like) is small
and listed at the top of the changelog.

This repository is the mod's source, updated at each release. Nookworks is a personal project:
bug reports are welcome (Issues, or the comments on CurseForge), but pull requests are not expected.

## Building

Gradle runs on a Java 21 or newer JDK and builds the mod for Java 21 (it fetches that toolchain itself if needed).

```
./gradlew build                # the jar in build/libs (nookworks-1.21.1-<version>.jar)
./gradlew runGameTestServer    # the in-game test suite (fails on any failure)
./gradlew runClient            # a development client
```

Other mods are tried by putting their jars into the run folder's `mods/` (`run/mods`, `run-gametest/mods`).

The textures, sounds and much of the generated data are made by the scripts under `tools/` (Python 3,
`pip install -r tools/requirements.txt`): `tools/textures/`, `tools/sounds/`, `tools/gen_*.py`, `tools/port1211/gen_*.py`.

## How it is made

Nookworks is made with AI (Claude, by Anthropic) under the author's direction: the author decides what goes in, plays
it and judges it; the AI writes the code and most of the textures, drawn by the scripts kept here. The poster pictures
are AI-made too.

## Licence

MIT - see [LICENSE](LICENSE). All of the mod's textures are its own; no vanilla images are included, and no file of
another mod's. The mod's own sounds are computed (`tools/sounds/gen_sounds.py`), but for two cut from Kenney's sound
packs (kenney.nl, CC0): see `tools/sounds/make_sounds.py`.

---

自作のマイクラMod「Nookworks」の Minecraft 1.21.1（NeoForge 21.1）版のソースコードです。最新バージョン向けは `main`
ブランチにあります。個人で作っているModなので、不具合の報告は歓迎しますが（Issue か CurseForge のコメントへ）、
プルリクエストは受け付けていません。
