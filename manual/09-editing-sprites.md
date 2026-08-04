# 9. Editing Sprites

## 9.1. Adding a new NPC sprite

To add a new NPC sprite, duplicate one of the folders in `$mod/sprite/npc/src/`, give it a new name, and add the new sprite to `$mod/sprite/SpriteTable.xml`. The new sprite will now appear in the Sprite Editor and will be built when you compile your mod.

## 9.2. Palettes

Sprites only used in the world may have whatever palettes you like, but CI-4 sprites which are used in battle are expected to have a certain number of palettes in a particular order to properly display status ailments. A world-only sheet with `N` color variations has `N` palettes. A battle sheet has `4N + 1`, in the following order:

**Partner Actors**

| Index | Palette | Appearance |
| ---: | --- | --- |
| 0 | Normal | Unchanged |
| 1 | Poisoned | Green tint |
| 2 | Turn Finished | Darker tint |
| 3 | Shocked | Yellow tint |
| 4 | Burned | Blackened |

**Enemy Actors**

| Index | Palette | Appearance |
| ---: | --- | --- |
| 0 | Normal | Unchanged |
| 1 | Poisoned | Green tint |
| 2 | Dizzy / Sick | Purple tint |
| 3 | Shocked | Yellow tint |
| 4 | Burned | Blackened |

Enemies with different color-variants (palette-swaps) have a separate set of palettes for each color variant (see sprite 31). In the sprite editor, the number of color variants is controlled by the "Groups" field of the "Spritesheet" tab. The palettes are expected in the following order:

```text
Normal 1, Normal 2, …, Normal N
Poisoned 1, Poisoned 2, …, Poisoned N
Dizzy 1, Dizzy 2, …, Dizzy N
Shocked 1, Shocked 2, … Shocked N
Burned (only one)
```

Additional palettes for certain "accessories" appear after these (see sprite 68).
