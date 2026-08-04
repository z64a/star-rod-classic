# 6. Globals

Star Rod uses "Globals" to describe project-wide data such as items, moves, and enums. The data for these are always loaded and can be referenced in any context.

## 6.1. Enums and Flags

Enum files let you use a readable name in place of a number. Project copies live under `$mod/globals/enum/` and use the extensions `.enum` and `.flags`. Files in this directory extend or override Star Rod's built-in type database.

An enum begins with its patch namespace, its library name, and whether its lookup is reversed. The remaining lines map hexadecimal values to names:

```text
Npc       % patch namespace
npcID     % library name
false     % reversed

FFFFFFFF = Self
FFFFFFFE = Player
FFFFFFFC = Partner
```

The patch form is `.Namespace:Name`, so the entry above may be written as `.Npc:Self`. Script-library argument information uses the second name to tell Star Rod when a numeric argument should be decoded through this enum.

A `.flags` file uses the same layout, but each entry describes a bit rather than an exclusive value:

```text
DamageType
damageType
false

00000002 = Fire
00000008 = Ice
08000000 = IgnoreDefense
```

Comments begin with `%`. Keep the three header lines even in a small project override; Star Rod uses the namespace to decide which built-in table is being extended.

## 6.2. Named Script Variables

The four text files below assign names to save data storage:

| File | Storage |
| --- | --- |
| `$mod/globals/GameBytes.txt` | The game's 512 saved bytes. |
| `$mod/globals/GameFlags.txt` | The game's 2,048 saved flags. |
| `$mod/globals/ModBytes.txt` | Star Rod's additional saved bytes for mods. |
| `$mod/globals/ModFlags.txt` | Star Rod's additional saved flags for mods. |

Each line begins with a hexadecimal index. It may then provide the default name, a more descriptive name, and a comment:

```text
01C = Byte_DojoRank = DojoRank % current dojo promotion
000 = ModByte_Example = ExampleCounter
000 = ModFlag_Example = OpenedExampleChest
```

Star Rod adds the leading `*` when these are used as script variables. The examples above become `*DojoRank`, `*ExampleCounter`, and `*OpenedExampleChest`.

Game bytes and flags are part of the normal save data. Mod bytes and flags are extra storage installed by Star Rod's global patches. Do not assign the same name to two different indices, and keep every index within the range of its variable type.

## 6.3. The Globals Editor

Open **Globals Editor** from the main window to edit the project-wide tables. It covers:

| Tab | Project data |
| --- | --- |
| Items | `$mod/globals/Items.xml` |
| Moves | `$mod/globals/Moves.xml` |
| Images | `$mod/image/ImageAssets.xml` and PNG files under `$mod/image/assets/` |
| Item Entities | `$mod/image/ItemEntities.xml` and `.is` files under `$mod/image/itemscripts/` |
| HUD Elements | `$mod/image/HudElements.xml` and `.hs` files under `$mod/image/hudscripts/` |

Use **Save** or **Save All** in the editor instead of hand-editing several related IDs. The editor updates references when supported, rebuilds its name indexes, and validates the relationships during compilation.

The files are still ordinary project sources, which can be edited by hand.

## 6.4. Global Patches

Patch files in `$mod/globals/patch/` are compiled for every build. This is the right place for reusable functions, hooks, new global data, and engine changes which are not owned by a particular map or battle overlay.

`$mod/globals/system/` is used by Star Rod's project machinery and generated system patches. Very advanced users can override certain built-in patches here.
