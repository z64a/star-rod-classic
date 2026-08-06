# Project Layout

## Application Database

Star Rod's `database/` directory describes the original ROM. It contains function libraries, structure hints, enum and flag names, render modes, and other information used while dumping and compiling data. Consult it when you need the exact name Star Rod expects. Project-specific additions generally belong under `$mod/globals/`.

## Dump Directory

The dump is a read-only working reference produced from the clean ROM. It contains decoded maps, battles, strings, sprites, images, and libraries. Copy an original asset from the dump when you need a clean starting point; do not edit the dump in place.

## Mod Directory

| Directory | Contents |
| --- | --- |
| `map/src/` | Maps copied from the dump; fallback when no saved editor copy exists. |
| `map/save/` | Maps saved by Map Editor; these take priority over `map/src/`. |
| `map/patch/` | Hand-written patches applied to individual maps. |
| `map/import/` | Reusable data imported by map patches. |
| `battle/formation/src/` | Battle section sources copied from the dump. |
| `battle/formation/patch/` | Battle section patches. |
| `battle/formation/import/` | Reusable data imported into battle sections. |
| `battle/formation/import/enemy/` | Reusable enemy data imported into battle sections. |
| `battle/item/patch/` | Battle-item patch sources. |
| `globals/` | Project enums, global tables, system data, and global patches. |
| `strings/src/` | Main string source files. |
| `strings/patch/` | New and modified string sources. |
| `image/assets/` | PNG sources used by image scripts. |
| `image/hudscripts/` | HUD-element scripts. |
| `image/itemscripts/` | Item-entity image scripts. |
| `sprite/npc/src/` | NPC and battle sprite sources. |
| `sprite/player/src/` | Player sprite sources. |
| `res/` | Arbitrary files included with expressions such as `~BinaryFile`. |
| `out/` | Compiled ROMs and distributable patches. |

Directories named `build`, `cache`, `gen`, and `temp` contain generated data. Do not use them as the only home for hand-written work.
