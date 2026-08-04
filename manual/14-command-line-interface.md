# 14. Command Line Interface

Star Rod runs command-line tasks when it is launched with arguments. The packaged launchers use the bundled Java runtime:

```text
StarRod.bat -Version                    % Windows
./StarRod -Version                     % Linux and macOS
```

The active project and base ROM are read from `cfg/main.cfg` beside the Star Rod executable. `ModPath` selects the project; relative paths beginning with `.` are resolved from the application directory. Build options still come from the selected project's `mod.cfg`.

Tasks are processed from left to right and may be chained in one invocation:

```text
StarRod.bat -CompileTextures -CompileMaps -CompileMod
```

## 14.1. Project and ROM Tasks

| Task | Description |
| --- | --- |
| `-Version` | Prints `VERSION=<version>` to standard output. |
| `-DumpAssets` | Runs the configured asset dump if the current dump is not already present. |
| `-CopyAssets` | Copies missing dumped sources into the active project. |
| `-CompileMod` | Builds the active project using its `mod.cfg` options. |
| `-DumpMaps ROMfile` | Validates a big-endian Paper Mario ROM and dumps its maps. |

`-DumpMaps` takes its ROM from the command line. The other dump and build tasks use the ROM and project selected in `cfg/main.cfg`.

## 14.2. Map Tasks

| Task | Description |
| --- | --- |
| `-CompileShape map` | Builds the map's shape asset. |
| `-CompileHit map` | Builds the map's collision asset. |
| `-GenerateScript map` | Generates the Classic script source for the map. |
| `-CompileMap map` | Builds shape and collision, then generates the appropriate Classic or decomp script for the active project. |
| `-CompileMaps` | Performs all three operations for every saved Classic map, or every configured decomp map. |

The `map` argument may be an asset name such as `mac_00` or a project-relative path ending in `.xml`. A bare Classic name is searched under `map/save/` first and then `map/src/`.

```text
StarRod.bat -CompileMap mac_00
StarRod.bat -CompileShape map/save/custom/my_map.xml
```

Standalone `-GenerateScript` currently always invokes the Classic script generator. For a decomp project, use `-CompileMap` or `-CompileMaps` when you need decomp output.

## 14.3. Image Tasks

| Task | Description |
| --- | --- |
| `-CompileTextures` | Builds the project's map texture archives. |
| `-CompileBackgrounds` | Builds the project's map background images. |

These tasks build intermediate assets; they do not patch a ROM by themselves. Chain `-CompileMod` afterward when you want a complete mod build in the same invocation.
