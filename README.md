# Nookworks

A vanilla-style decoration and tech mod for Minecraft Java Edition on NeoForge: Thin Rope and Thin Chain with café
lights, posters from a Printing Press, furniture and a kitchen, Storage Barrels, Forge Energy machines, tree felling and
vein mining, and more.

- Download: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/nookworks)
- Player guide (English, 日本語, 简体中文, 繁體中文, 한국어): <https://riftwright.github.io/nookworks-guide/>
- Changes: [CHANGELOG.md](CHANGELOG.md)

This repository is the mod's source, updated at each release. Nookworks is a personal project:
bug reports are welcome (Issues, or the comments on CurseForge), but pull requests are not expected.

## Building

Requires a Java 25 JDK.

```
./gradlew build                # the jar in build/libs
./gradlew runGameTestServer    # the in-game test suite (fails on any failure)
./gradlew runClient            # a development client
```

On Windows, `tools/gradle.ps1 <task>` (or `tools/gradle.sh` from Git Bash or WSL) finds a Java 25 JDK for you.

The textures, sounds and much of the generated data are made by the scripts under `tools/` (Python 3,
`pip install -r tools/requirements.txt`): `tools/textures/`, `tools/sounds/`, `tools/gen_*.py`.

## How it is made

Nookworks is made with AI (Claude, by Anthropic) under the author's direction: the author decides what goes in, plays
it and judges it; the AI writes the code and most of the textures, drawn by the scripts kept here. The poster pictures
are AI-made too.

## Licence

MIT - see [LICENSE](LICENSE). All of the mod's textures are its own; no vanilla images are included. The mod's own
sounds are cut from Kenney's sound packs (kenney.nl, CC0): see `tools/sounds/make_sounds.py`.

---

自作のマイクラMod「Nookworks」のソースコードです（リリースごとに公開しています）。個人で作っているModなので、
不具合の報告は歓迎しますが（Issue か CurseForge のコメントへ）、プルリクエストは受け付けていません。
