# Hopeless Infinity

Version 0.29.8. By **David Mulkey (Hopeless Engineering)**.

A Minecraft Forge 1.20.1 mod. It replaces the overworld with a spiral galaxy of solar systems. Each planet is a round world with its own gravity, terrain, rivers, seas, towns, cities, roads and railroads.

## What is here

- `src/` - the mod source (Java) and its resources.
- `tools/` - Hopeless Lab (Minecraft): the test suite and the analysis tools. `tools/lab.sh list` shows each tool. WorldView is the one renderer: it draws the world from the generator or from a save, without the game.
- `docs/` - research notes and old test records.

## Build

Build with the javac build chain in [HopelessMCTool](https://github.com/ToothlessOneX/HopelessMCTool):

```
mcbuild.sh <this folder> hopelessinfinity src/main/java/com/hopeless/infinity/HopelessInfinity.java
```

Run the tests with `tools/run_tests.sh` (or `tools/lab.sh test`).

## Credit

Made by David Mulkey (Hopeless Engineering), with Claude (Anthropic) writing code under his direction.

Copyright (c) 2026 David Mulkey, Hopeless Engineering. All rights reserved. You can read and learn from this source. Ask before you reuse or republish it.
