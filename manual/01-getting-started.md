# 1. Getting Started

## 1.1. Introduction

Star Rod is a collection of modding tools for Paper Mario 64 (US version 1.0). These tools allow modders to make sweeping changes to the game, from tweaking and rebalancing existing content to adding multiple chapters worth of new content. Paired with any text or code editor, you'll be able to add or edit maps, battles, enemies, badges, items, strings, and more. The tool suite includes editors for maps, levels, sprites, strings, images, globals, and the world map, along with an assembler and the tools needed to build and package a mod.

## 1.2. Recommended Software

Star Rod dumps many game assets to text files, which you may edit and compile back to the ROM. For this reason, a large part of modding Paper Mario will involve editing these text files. Context-sensitive color coding and highlighting help to read these files and prevent basic syntax errors. Use either *Notepad++* or *VS Code*. *Notepad++* user-defined language files are provided with Star Rod for script files and string files. A plugin for *VS Code* is available [here](https://marketplace.visualstudio.com/items?itemName=nanaian.vscode-star-rod).

Sprites and textures can be edited in your favorite image editor and converted to the proper format using Star Rod's image editor. Sprite sheets textures use CI-4 images with a strict limitr of 16 colors per palette. Other textures may use CI-4, CI-8, intensity, intensity-alpha, or RGBA formats, so always check the format of the asset you are replacing. Many artists prefer using [Aseprite](https://www.aseprite.org/) for pixel art which can be exported as a color-indexed PNG or imported directly into Star Rod's Sprite Editor.

Star Rod is written in Java 17. New releases contain their own Java runtime, so you do not need to install Java to use it.

## 1.3. Creating a Mod

The first time you launch Star Rod, you will be prompted to select a valid Paper Mario US v1.0 ROM and a directory for your mod. There are a variety of tools you can use to dump a backup from your own cartridge. Please ensure your cartridge matches the required version. Star Rod will perform validation before proceeding. Your mod directory will contain all the source files you'll be creating to make your Paper Mario mod.

Once the new project is set up, you must first dump the ROM. A folder will be created in the same directory as the ROM with all the modifiable files extracted. This process will take several minutes the first time as assets are repackaged and converted to human-readable formats.

After dumping is complete, use **Copy Assets to Mod** to populate your mod directory with files from the dump directory. You only need to do this *ONCE*, the first time you set up a new mod.

Once your edits are ready, use **Compile Mod** to produce a modded Paper Mario ROM you can test in a compatible emulator. The output will be found in `$mod/out/`. When your mod is done and you're ready to share it with the world, use **Package Mod** to create a patch which users can combine with their own Paper Mario US v1.0 ROM. Do not distribute patched ROMs.

## 1.4. Directories

### 1.4.1. Database

Star Rod's `database/` directory describes the original ROM. It contains function libraries, structure hints, enum and flag names, render modes, and other information used while dumping and compiling data. You may consult it when you need to see the exact name Star Rod expects, but project-specific additions generally belong in your mod's `globals/` directory.

### 1.4.2. Dump Directory

The dump directory is a read-only working reference produced from your clean ROM. It contains decoded maps, battles, strings, sprites, images, and libraries. If you need a clean copy of an original asset, copy it from the dump rather than editing the dump in place.

### 1.4.3. Mod Directory

The mod directory contains your project. The most commonly used locations are:

| Directory | Contents |
| --- | --- |
| `map/src/` | Maps copied from the dump. Used as a fallback when no saved editor copy exists. |
| `map/save/` | Maps saved by Map Editor. These take priority over `map/src/`. |
| `map/patch/` | Hand-written patches applied to individual maps. |
| `map/import/` | Reusable data imported by map patches. |
| `battle/formation/src/` | Original battle-area sources. |
| `battle/formation/patch/` | Formation patches. |
| `battle/formation/import/enemy/` | Reusable enemy data imported into formations. |
| `globals/` | Project enums, global tables, system data, and global patches. |
| `strings/src/` | String source files. |
| `strings/patch/` | New and modified string source files. |
| `image/assets/` | PNG sources used by image scripts. |
| `image/hudscripts/` | HUD element scripts. |
| `image/itemscripts/` | Item-entity image scripts. |
| `sprite/npc/src/` | NPC and battle sprite sources. |
| `sprite/player/src/` | Player sprite sources. |
| `res/` | Arbitrary files included from patches with expressions such as `~BinaryFile`. |
| `out/` | Compiled ROMs and distributable patches. |

Directories named `build`, `cache`, `gen`, and `temp` contain generated data. Do not use them as the only home for hand-written work.

## 1.5. Notation Used in this Guide

`$mod` refers to the root of the active mod project. `$dump` refers to the asset dump produced from the clean ROM, and `$database` refers to Star Rod's application database.

Numbers in patch files are hexadecimal unless they end with a backtick. For example, `10` is hexadecimal 0x10 while `` 10` `` is decimal ten. Addresses are written without a leading `0x` because that is the form accepted by the patch language.

Names beginning with `$` are pointers, names beginning with `.` are constants or enum values, and names beginning with `*` are script variables or temporary register aliases. The exact meaning depends on the context in which the name appears.
