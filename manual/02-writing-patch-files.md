# 2. Writing Patch Files

## 2.1. Introduction

Since the memory of the N64 is limited, sections of the ROM containing assets, scripts, and code are loaded into memory as they are needed. For example, data is loaded whenever the player enters a new map, starts a battle, uses a move, and so on. These chunks of data are referred to as **data sections**. Each map has its own map data section while battles are grouped together by area (ex: battles from Dry Dry Desert or battles from Koopa Bros Fortress). Each boss usually has their own battle data section. Each player move has its own data section, as do different actions in the overworld.

Star Rod rips these data sections from the ROM and recursively scans through them to detect scripts and data structures. Most data structures have been identified and reverse engineered. Star Rod's automated system will find the vast majority of them and write them to text files: `mscr` for maps and `bscr` for battles. Much of the game logic is implemented through a custom event and scripting engine, which is available to us as **script bytecode**. This bytecode is sufficiently high-level for us to create new enemies, setup complex map logic, and make new cutscenes without having to write any (or perhaps just a little) assembly.

The scripts are dumped along with all the other data structures in each data section. The dumped data structures can be **patched** by editing their text in patch files: `mpat` for map-related data and `bpat` for battle-related data. These patches are converted from their various text formats to binary and written over the original structures. If the patched structure is larger than the original, it will be moved to a free location in memory and all pointers to it are automatically updated. You can also declare new data structures in your patch files. Since the text formats for structures in source and patch files are identical, you can copy structures from one source file into a patch file. For example, this can be used to copy enemies from one battle section to another or copy NPCs from one map to another.

The distinction between map and battle data serves two important purposes. First, the set of "library" functions and scripts that are available to call from scripts is different in these two environments. This is because "library" or "common" data is loaded into and out of memory whenever a battle begins or ends. The list of available library functions and scripts can be found in the database folder. Second, different data structures are used in battles and maps. For example, NPCs are only relevant to map scripts and Actors are only relevant to battle scripts. Keeping track of which functions and data structures are available to which scripts is something Star Rod does automatically.

To really understand how these files are structured, you should inspect the dumped sources and study how the game logic is structured. The best way to implement a new feature is often to find a map or battle that already does something similar to what you want and see how the developers did it. For example, the overworld AI for Boo enemies in Pro Mode was created by duplicating and modifying the Ember AI.

## 2.2. Basic Patch File Syntax

Patches are written like this:

```star-rod
@ $DataTable_80241C00 {
    002A0000 002B0010 002C0015 002D0002
}
```

This tells Star Rod to look for a data structure called `DataTable_80241C00` and overwrite it with the 16 bytes that appear on the following line. Patches are contained within curly brackets. Let's try something more advanced. We can choose to only overwrite the third value by adding an offset to the start of our patch:

```star-rod
@ $DataTable_80241C00 { [8] 002C0015 }
```

By default, each token in a patch is treated as a hexadecimal 32-bit integer. So the value "1" writes "00000001". This can be changed by adding a suffix identifying the token as a decimal number (`` ` ``), and as either a 16-bit short (`s`) or an 8-bit byte (`b`). You can use the minus sign for negative numbers in any format. Float values will be treated as IEEE floats, but leading decimal places are not allowed. Here are some examples of numbers:

```text
128  = 00000128
100` = 00000064
12b  = 12
12`b = 0C
1FFs = 01FF
-10s = FFF0
3.2  = 404CCCCD
-2.5 = C0200000
.5   = error
```

More sophisticated patches are available for certain data structures. Function patches expect MIPS assembly code, script patches expect lines of Paper Mario's scripting language, strings expect Star Rod's string markup language, and so on.

You can also use constant values defined locally or globally, enumerated values from the database directory, or special expressions like `~Vec3d:LocationMarker` that produce specially formatted data or read data from editable map files.

### 2.2.1. Patch Syntax Reference

| Syntax | Description |
| --- | --- |
| `% comment` | Single line comment |
| `/%`<br>`...`<br>`%/` | Multi-line comment. Can begin or end at any point in a line. Everything between these will be replaced with a single space and will not begin a new line. |
| `@ $Pointer {`<br>`... }` | Starts a new patch overwriting the data structure at `$Pointer`. The `$` indicates this is the address of some named data structure. |
| `@ $Pointer {`<br>`[40]`<br>`... }` | Add an offset to the patch location. |
| `@ $Pointer {`<br>`[40] ...`<br>`[60] ... }` | Alternate way to create patches with offsets. More than one can be used, but each must start on a new line. |
| `@ $Pointer [40]{`<br>`[10] ...`<br>`[20] ... }` | Set the *base offset* for a patch. Other patch offsets for this struct will be relative to this value. Hence, the `[10]` will begin at `$Pointer[50]` and the `[20]` will begin at `$Pointer[60]`. |
| `.Constant` | Local/global constants, enum values from in `/database/types/`, or struct-specific constant values. |
| `*ScriptVariable` | Special pointers used for both local variables in scripts and global game data. See "Script Variables" for more information. |
| `~Expression` | Converts the enclosed statement into some complicated binary expression. Used by some scripts to reference data from maps and other source files. |
| `#directive` | Used for imports, creating new structs, renaming them, and more. |

### 2.2.2. Directives

`#new:StructType $StructName`

Creates a new struct and immediately begins reading a patch for it. You can add extra patches to the struct just like any other.

`#import OtherFile.mpat`

Imports all patches from another patch file in the relevant `/import/` directory. For battle scripts, the `/enemy/` directory may also be used. You can add patches to imported structs if you need to modify their properties.

Imports are only valid in local map, battle, move, and similar overlay patches. Global patches are already compiled together and cannot use `#import`.

`#import MyFile.bpat FileNamespace`

The second example shows an import with a qualifying namespace. Structures imported in this way will have names like: `$FileNamespace:PointerName`. If the imported file has imports of its own, the resulting import will have a nested namespace, ie: `$Outer:Inner:OriginalName`.

`#define .Name Value`

Creates a new constant. The constant may be referenced anywhere in the current script. Global constants must be defined in either a global patch file or an enum file.

`#alias $ComplicatedStructName $SimpleName`

Adds another name to reference an existing struct.

`#delete $StructName`

Clears the memory associated with an existing struct. Useful if you need to create more room in an existing map or battle section.

`#reserve 80231000 80238000`

Reserves an area of RAM. No new structures will be placed there. You can use it for whatever you like. General Guy uses this area to load each wave of minions. Only one region of RAM may be reserved per patch file.

### 2.2.3. Expressions

`~String:StringName`

Replaced with the string index assigned to a named custom string (see "Editing Strings").

`~SizeOf:Type`

Replaced with an int token equal to the size of a struct type, e.g. `~Sizeof:Actor`. Only works for structs that have a fixed size, i.e. not Function, Script, etc.

`~Index:VariableName`

Replaced by the index of a variable. Example: `~Index:*DojoRank` resolves to `1C` since `*DojoRank = *GameByte[01C]`.

`~Func:FunctionName`

Used in function structs to identify calls to known engine functions. Function names are read from `/database/*.lib`.

`~FX:EffectName`

Used in script calls to the `PlayEffect` function, assigning meaningful names to a pair of function arguments. Values are read from `/database/types/effects.txt`.

```text
~Flags:FlagType:FlagValues
~Flags:FlagType:FlagValues:Constant
~Flags:FlagType::Constant
```

Flag type is a name from a *flags* file in `$database/types/`. Flag values is a series of flag names separated by `|`. This value can be blank, but must always be present. Constant is just a constant value to OR with the combination of flag values (intended for undocumented flags). The constant field may be omitted.

Example: `~Flags:DamageType:NoContact|Fire` for a fire-type move that doesn't count as direct contact.

`~Short:ConstName`

Resolves a constant and writes it to the patch as a 16-bit short.

`~Byte:ConstName`

Resolves a constant and writes it to the patch as an 8-bit byte.

```text
~Anim:SpriteName:AnimationName
~Anim:SpriteName:AnimationName:PaletteName
~PlayerAnim:SpriteName:AnimationName
~PlayerAnim:SpriteName:AnimationName:PaletteName
```

Expands to an NPC or player sprite animation ID. The optional palette name selects a particular palette; without it, the palette field is zero. Names of animations and palettes can be changed using the Sprite Editor.

```text
~RasterFile:Format:Filename
~PaletteFile:Format:Filename
```

Copies the contents of an image or palette file from the `$mod/res` directory according to a specified image format, e.g. CI-4, IA-8, etc. The filename is relative to `$mod/res`.

Supported formats are: I-4, I-8, IA-4, IA-8, IA-16, CI-4, CI-8, RGBA-16, RGBA-32.

```text
~TileFormat:Format
~TileDepth:Format
```

Equals the fmt or depth of an image format.

Supported formats are: I-4, I-8, IA-4, IA-8, IA-16, CI-4, CI-8, RGBA-16, RGBA-32.

`~BinaryFile:Filename`

Copies the contents of a binary file from the `$mod/res` directory as a series of byte tokens. The file will not be padded. The filename is relative to `$mod/res`.

### 2.2.4. Map Expressions

Expressions can be used to reference map object data, like ID values or positions from names given in the map editor. When used in a map patch file (`.mpat`), these expressions may omit the `MapName` field when referencing the current map.

```text
~Model:MapName:ModelName
~ModelShort:MapName:ModelName
```

Replaced by the ID (32 or 16 bit) of the first model in the model tree with a given name based on a breadth first traversal. Avoid issues by using unique model names.

```text
~Collider:MapName:ColliderName
~ColliderShort:MapName:ColliderName
~Zone:MapName:ZoneName
~ZoneShort:MapName:ZoneName
~Entry:MapName:EntryName
~EntryShort:MapName:EntryName
```

These work like Model and ModelShort, returning the corresponding collider, zone, or entry ID.

`~XXX:MapName:MarkerName`

Marker location expression with valid values for the location XXX are:

| Values | Meaning |
| --- | --- |
| `Vec2d`, `Vec2f` | 2D planar position |
| `VecXZd`, `VecXZf` | Alternative name for 2D planar position |
| `Vec3d`, `Vec3f` | 3D position |
| `PosXd`, `PosXf` | X coordinate value |
| `PosYd`, `PosYf` | Y coordinate value |
| `PosZd`, `PosZf` | Z coordinate value |
| `Angle`, `AngleF` | Yaw angle |
| `Vec4d`, `Vec4f` | 3D position followed by yaw angle |

In each case, the expression will be replaced by either floating point (`f`) or 32-bit int (`d`) values for the given location.

```text
~PathXZd:MapName:MarkerName:PointIndex
~PathXZf:MapName:MarkerName:PointIndex
~Path3d:MapName:MarkerName
~Path3f:MapName:MarkerName
```

`Path3d` and `Path3f` are replaced by the array of 3D points assigned to the marker. `PathXZd` and `PathXZf` return the X/Z coordinates of one point in the path.

`~PushGrid:MapName:MarkerName`

Used by `CreatePushBlockGrid` to specify a grid for blue pushable blocks.

### 2.2.5. Constant Offsets

You can get a relative address within a struct using an offset, which may either be a number or a constant value:

```star-rod
$MyPointer[offset]
$MyPointer[.Constant]
```

Functions allow using one of their internal labels as offsets:

```star-rod
$MyFunction[.o150]
```

Offsets may also be applied to constants, in which case the value of the constants are added together. You may nest offsets in this way:

```star-rod
#define .ConstA 10
#define ConstB 3
.ConstA[.ConstB] % == 13
$MyPointer[.ConstA[.ConstB[2]]] % address of MyPointer + 13
```

### 2.2.6. Script Variables

There are a set of special values which are used in scripts to denote variables. If one of these variables is passed to a function, it will usually read the value from that variable instead of the literal value of the argument. Each set of script variables can be understood as an array with a certain scope and for a certain purpose.

| Name | Description | Maximum | First Value |
| --- | --- | ---: | --- |
| `*GameByte[i]` | Global saved byte. | `0x200` | `F5DE0180` (-170m) |
| `*GameFlag[i]` | Global saved flag. | `0x800` | `F8405B80` (-130m) |
| `*ModByte[i]` | Extra saved byte for mods. | `0x1000` | --- |
| `*ModFlag[i]` | Extra saved flag for mods. | `0x8000` | --- |
| `*AreaByte[i]` | Cleared on area change. | `0x10` | `F70F2E80` (-150m) |
| `*AreaFlag[i]` | Cleared on area change. | `0x100` | `F9718880` (-110m) |
| `*MapVar[i]` | Cleared on map change. | `0x10` | `FD050F80` (-50m) |
| `*MapFlag[i]` | Cleared on map change. | `0x60` | `FAA2B580` (-90m) |
| `*Fixed[0.3]` | Fixed point real.<br>Precision = 1/1024 | ±19531 | `F24A7A80` (-230m) |
| `*Array[i]` | From allocated script array. | varies | `F4ACD480` (-190m) |
| `*FlagArray[i]` | From allocated script array. | varies | `F37BA780` (-210m) |
| `*Var[i]` | Script variables. | `0x10` | `FE363C80` (-30m) |
| `*Flag[i]` | Script flags. | `0x60` | `FBD3E280` (-70m) |

*Note*: Flags are packed into 32-bit words from LSB to MSB, `1F 1E 1D ... 02 01 00`, `3F 3E 3D ... 22 21 20`, etc.

Just like with other offsets, the array offsets into variables can either be numeric, e.g. `*Var[4]`, `*GameByte[80]`, etc; or constant `*GameByte[.SomeConstant]`.

#### Map Variables

`*MapVar` and `*MapFlag` use the current world or battle map-variable storage selected by the script engine. They are cleared when a new map is entered. This means they survive a battle fought from the same world map, but should not be used for state that must survive travel to another map.

#### Area Variables

The area byte/flag variables are cleared whenever the player leaves the area. They have a limited capacity, so don't overuse them. This is for persistent, but temporary data. For example, an NPC that says different things when you talk to them repeatedly.

#### Utility Functions

Functions read from and write to these variables with a set of helper functions, which you must familiarize yourself with. Each takes an argument pointing to the "script context" -- the data structure holding the state of the script which called the function. For more on that, read section [3.1. Script Bytecode](03-writing-scripts.md#31-script-bytecode). These functions can be called with a NULL (0) script context, in which case `*Var[x]` and `*Flag[x]` will not be resolvable. Whether variables are treated as integers or floats depends on which set of functions you use.

##### `evt_get_variable (802C7ABC)`

| Register | Value |
| --- | --- |
| A0 | Script context pointer. |
| A1 | "Variable" to resolve. Could be constant or a script variable value like `FE363C80`. |
| V0 (return) | Integer value of variable. |

##### `evt_set_variable (802C8098)`

| Register | Value |
| --- | --- |
| A0 | Script context pointer. |
| A1 | "Variable" to resolve. Could be constant or a script variable value like `FE363C80`. |
| A2 | New value to set. |
| V0 (return) | Previous integer value of variable. |

##### `evt_get_float_variable (802C842C)`

| Register | Value |
| --- | --- |
| A0 | Script context pointer. |
| A1 | "Variable" to resolve. Could be constant or a script variable value like `FE363C80`. |
| F0 (return) | Floating-point value of variable. |

##### `evt_set_float_variable (802C8640)`

| Register | Value |
| --- | --- |
| A0 | Script context pointer. |
| A1 | "Variable" to resolve. Could be constant or a script variable value like `FE363C80`. |
| A2 | New value to set. |
| F0 (return) | Previous floating-point value of variable. |

##### `evt_fixed_var_to_float (802C4920)`

| Register | Value |
| --- | --- |
| A0 | `*Fixed` script variable. If out of range, assumes value is a "reasonably-sized" float. |
| F0 | Floating point value. |

##### `evt_float_to_fixed_var (802C496C)`

| Register | Value |
| --- | --- |
| F12 | `*Fixed` script variable |
| V0 | `*Fixed` representation. Not range-checked! Ensure arguments no larger than ±19531. |

##### `set_global_flag (80145450)`

Sets `GlobalFlag[i]`, accepts either indices or encoded values, returns the old value.

##### `get_global_flag (801454BC)`

Returns `GlobalFlag[i]`, accepts either indices or encoded values.

##### `set_global_byte (80145520)`

Sets `GameByte[i]`, accepts either a raw index or an encoded `*GameByte` value, and returns the old value.

##### `get_global_byte (80145538)`

Returns `GameByte[i]`, accepting either a raw index or an encoded `*GameByte` value.

##### `set_area_byte (80145638)`

Sets `AreaByte[i]` at `800DBF90`, accepts ONLY indices, returns the old value.

##### `get_area_byte (80145650)`

Returns `AreaByte[i]` from `800DBF90`, accepts ONLY indices.

##### `set_area_flag (801455A0)`

Sets `AreaFlag[i]` at `800DBF70`, accepts ONLY indices, returns the old value.

##### `get_area_flag (801455F0)`

Returns `AreaFlag[i]` from `800DBF70`, accepts ONLY indices.

## 2.3. Global Patches

Patches in `$mod/globals/patch/` are applied directly to the ROM, allowing for global modifications.

### 2.3.1. Modifying Existing Data

```star-rod
@Data XXXX
@Function XXXX
@Hook XXXX
@Script:Global XXXX
@Script:Map XXXX
@Script:Battle XXXX
```

```star-rod
@Fill XXXX YYYY
```

Fills the region of the ROM from `XXXX` to `YYYY` with whatever pattern is supplied as the patch. The pattern will repeat to fill the region and be truncated if it goes beyond the end.

### 2.3.2. Creating New Global Structs

`#new:Data $Name`

Use this to patch arbitrary data to the ROM.

`#new:Function $Name`

Use this to patch a function written in MIPS assembly to the ROM.

```star-rod
#new:Script:Global $Name
#new:Script:Map $Name
#new:Script:Battle $Name
```

Use these to create new scripts using particular function APIs.

`#reserve XXXX $Name`

Reserves XXXX bytes in RAM and stores the address in the `$Name` pointer, which can be referenced elsewhere in global patches.

| Directive | Description |
| --- | --- |
| `#export:Function $GlobalFunction` | Create a struct as global. |
| `#export .NewConstName XXXX` | Create a constant as global. |
| `#export $GlobalPointer` | Make an existing struct global. |
| `#export .ExistingConstName` | Make an existing constant global. |

Export makes things global. It works with both pointers and constants. You can also use `#export:` instead of `#new:` as a shorthand for creating global structs.

## 2.4. Hint Files

The automatic recursive dumping system is sometimes unable to find all data structures in a data section, and generally unable to give descriptive names to the structures it does find. Fortunately, a hint system allows mod authors to supply hints to the dumping process. Hint files are located in `/database/hints/` with names that match the source files they apply to. You’ll find a large variety of default hints there. Within each file, the following hints are supported:

| Hint | Description and example |
| --- | --- |
| `add` | Identifies a new data structure of a given type.<br>`add 80240210 Function` |
| `name` | Assign a unique name to a data structure.<br>`name 80240210 Function_FadeScreenToBlack` |
| `size` | Forces a certain size for a data structure. Rarely needed.<br>`size 80240210 800` |
| `newline` | Sets how many 32-bit words should be printed on each line. Default = 8.<br>`newline 80240210 6` |
