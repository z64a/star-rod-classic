# 11. Battles

## 11.1. Introduction

Characters which participate in battle are called **actors**: Mario, the active partner, and enemies. The maps where battles take place are called **stages**. Actors use event scripts for turn logic, attacks, and event handling; player and partner moves, Star Powers, and battle items are implemented with scripts as well.

Battle events tell an actor what just happened. An actor's `HandleEvent` script may respond to an ordinary hit, death, burn, flip, fall, shock contact, recovery, Lucky result, or another engine-defined event. The flags supplied to the damage call determine which of these events may be generated.

Not all battle data is loaded at once. Enemy **formations** are divided among more than forty battle sections corresponding to different parts of the game. One section at a time may be loaded to `80218000–80238000`, while stage data uses `80210000–80218000`. An enemy imported from another section needs its scripts, tables, and referenced functions. Star Rod's battle `#import` machinery copies those structures and relocates the references it can identify; the imported data still has to fit in the active section.

An overworld encounter uses the packed ID `AABBCCCC`:

| Field | Meaning |
| --- | --- |
| `AA` | Battle section index. |
| `BB` | Formation index within that section. |
| `CCCC` | One-based index in the stage table. Zero uses the formation's default stage. |

For example, `12080005` selects section 12, formation 8, and stage-table entry 5.

## 11.2. Actors

Every actor in a battle has a unique ID. The player is `0000`, the partner is `0100`, and enemy actors range from `0200` through `0217`, allowing 24 enemy slots.

### 11.2.1. Parts

An actor is made from one or more **parts**. Each part may have its own sprite animation, position, defense table, event flags, target flags, and render flags. A multi-part boss may expose one part as the normal target while retaining other parts for decorations, weapons, or special attacks.

Actor flags apply to the whole actor. Part flags apply only to one part. Part **target flags** are a third field which rejects jump, smash, or all damage; do not confuse them with either set of render/state flags.

### 11.2.2. Defense Tables

A defense table maps an element key to the amount subtracted from incoming damage. Positive defense reduces damage; negative defense increases it. A value of 99 means immune. `IgnoreDefense` reduces positive defense to zero, but preserves 99 as immunity.

When several element bits are present, the engine checks every corresponding table entry and uses the lowest defense. If no recognized element bit is present, it uses `Normal`. Missing entries also fall back to `Normal`.

| Key | Star Rod name | Damage bit | Typical attacks |
| --- | --- | --- | --- |
| 01 | `Normal` | none | Default for an attack with no recognized element. |
| 02 | `Fire` | `00000002` | Fire Flower, Egg Missile. |
| 03 | `Water` | `00000004` | Squirt. |
| 04 | `Ice` | `00000008` | Snowman Doll, Ice Power. |
| 05 | `Mystery` | none | Present as a table key, but no ordinary damage bit selects it. |
| 07 | `Magic` | `00000010` | Magikoopa and magical Jr. Troopa attacks. |
| 08 | `Hammer` | `00000040` | Hammer and smash attacks; the engine calls this element `Smash`. |
| 09 | `Jump` | `00000080` | Jump, Headbonk, Belly Flop. |
| 0A | `Cosmic` | `00000100` | Shooting Star, Star Storm. |
| 0B | `Blast` | `00000200` | Power Bomb and other blasts. |
| 0C | `Shock` | `00000020` | Thunder Rage, Thunder Bolt, Mega Shock. |
| 0D | `Quake` | `00000800` | Quake Hammer, Earthquake Jump. |
| 0F | `Throw` | `00040000` | Hammer Throw. |

**Example: Lava Bubble**

```star-rod
.Element:Normal    0
.Element:Water    -2   % two extra damage from water
.Element:Ice      -2   % two extra damage from ice
.Element:Fire     99`  % immune to fire
.Element:Blast    -1   % one extra damage from blasts
.Element:End
```

**Example: Cleft**

```star-rod
.Element:Normal    2   % two less damage from ordinary attacks
.Element:Fire     99`  % immune to fire
.Element:Magic     0   % normal damage from magic
.Element:End
```

## 11.3. Damage Types

Damage calls accept a bitfield combining an element with behavioral modifiers. Star Rod exposes these through the `DamageType` flag database.

### Element and specialized low bits

| Value | Name | Meaning |
| --- | --- | --- |
| `00000002` | `Fire` | Fire element. |
| `00000004` | `Water` | Water element. |
| `00000008` | `Ice` | Ice element. |
| `00000010` | `Magic` | Magic element. |
| `00000020` | `Electric` | Shock element. |
| `00000040` | `Smash` | Hammer/smash element. |
| `00000080` | `Jump` | Jump element. |
| `00000100` | `Cosmic` | Cosmic element. |
| `00000200` | `Blast` | Blast element. |
| `00000400` | `POW` | POW Block behavior. |
| `00000800` | `Quake` | Quake element. |
| `00001000` | `Fear` | Fear/spook behavior. |
| `00002000` | `Death` | Specialized instant-death item behavior; named by the engine, not the Classic flag file. |
| `00008000` | `AirLift` | Air Lift behavior. |
| `00010000` | `SpinySurge` | Spiny Surge behavior. |
| `00020000` | `ShellCrack` | Converts certain hit events to Shell Crack. |
| `00040000` | `Throw` | Hammer Throw element and charge behavior. |

### High-bit modifiers

| Value | Star Rod name | Meaning |
| --- | --- | --- |
| `00100000` | `PowerBounce` | Marks a Power Bounce hit. |
| `00200000` | `QuakeHammer` | Marks a Quake Hammer hit. |
| `00400000` | `RemoveBuffs` | Removes applicable buffs. |
| `00800000` | `PeachBeam` | Marks Peach Beam behavior. |
| `01000000` | `MultiBounce` | Marks a Multibounce hit. |
| `02000000` | `Unblockable` | Prevents normal blocking. |
| `04000000` | `SpinSmash` | Converts applicable hit and death events to Spin Smash variants. |
| `08000000` | `IgnoreDefense` | Ignores positive defense; a defense of 99 remains immune. |
| `10000000` | `NoContact` | The attacker does not touch the target, so contact hazards do not trigger. |
| `20000000` | `NoOtherDamagePopups` | Used by attacks with several targets or hit popups. The engine source calls this `MultiplePopups`. |
| `40000000` | `StatusAlwaysHits` | Makes the associated status attempt bypass its normal hit restriction. |
| `80000000` | `TriggerLucky` | Allows a failed hit test to produce Lucky. |

Use the same combinations as a working attack with similar contact, targeting, and popup behavior.

## 11.4. Battle-State Flags

The battle state has two 32-bit flag fields, conventionally called `flags1` and `flags2`. The following are the most useful named bits; several other bits are transient state owned by the battle engine.

### `flags1`

| Value | Meaning |
| --- | --- |
| `00000001` | Actors are visible. |
| `00000002` | A battle menu is open. |
| `00000004` | The Tattle window is open. |
| `00000008` | Show player decorations such as Frozen, Water Block, and Cloud Nine. |
| `00000010` | Include Power Plus, Merlee, and other attack power-ups. |
| `00000020` | Allow the current hit to trigger special target events such as flip, fall, and burn. |
| `00000040` | Nice action-command result. |
| `00000080` | Suppress the action-command rating message. |
| `00000100` | A move is executing. |
| `00000200` | Super action-command result for partner and item hits. |
| `00000800` | Force an immune hit result. |
| `00001000` | Automatically succeed the action command. |
| `00008000` | Free action command. |
| `00020000` | Disable the normal celebration. |
| `00040000` | Player or enemy fled. |
| `00080000` | Partner is acting. |
| `00100000` | Player is in back. |
| `00200000` | End the current move or turn when set by `YieldTurn`. |
| `00400000` | Player is defending. |
| `00800000` | Do not game over on a loss. |
| `01000000` | Star points have been dropped. |
| `02000000` | Tutorial battle; partner switching is restricted. |
| `04000000` | Hustled state. |
| `08000000` | Sort enemy turns by X position. |
| `10000000` | Hammer charge is present. |
| `20000000` | Jump charge is present. |
| `40000000` | Goombario charge is present. |
| `80000000` | Current attack was blocked. |

### `flags2`

| Value | Meaning |
| --- | --- |
| `00000001` | Star points are being awarded. |
| `00000002` | Player turn has been used. |
| `00000004` | Partner turn has been used. |
| `00000008` | Override inactive player presentation. |
| `00000010` | Override inactive partner presentation. |
| `00000020` | Running away is allowed. |
| `00000040` | Peach battle. |
| `00000100` | A stored Turbo Charge turn must not decrement at the start of the player turn. |
| `00000200` | Jump tutorial is active. |
| `00000400` | Final Bowser, part 1; no other understood use. |
| `00001000` | No target is available. |
| `00004000` | Ignore darkness. |
| `00010000` | Hide partner buff counters. |
| `00100000` | Do not apply the player's palette adjustment. |
| `01000000` | The battle began with a first strike. |
| `02000000` | Do not stop the current music when battle ends. |
| `04000000` | HP Drain has already applied. |
| `08000000` | A Rush badge condition is active. |
| `10000000` | Drop a Whacka's Bump after battle. |

## 11.5. Actor and Part Flags

### Actor flags

| Value | Meaning |
| --- | --- |
| `00000001` | Invisible. |
| `00000004` | Hide the actor's shadow. |
| `00000010` | Low-priority target when combined with Target Only. |
| `00000040` | Minor target; ignored by Primary Only targeting. |
| `00000080` | Cannot be tattled. |
| `00000200` | Flying; rejected by Not Flying and unaffected by Quake Hammer. |
| `00000400` | Flipped. |
| `00000800` | Upside down or attached to the ceiling. |
| `00001000` | Actor type changed; refresh Tattle/HP-bar state. |
| `00002000` | Immune to items, Chill Out, and Up & Away. |
| `00004000` | Target only: has no turn and does not keep the battle alive. |
| `00008000` | Half height. |
| `00010000` | Skip the current turn. |
| `00040000` | Never show a health bar. |
| `00080000` | Health bar is temporarily hidden. |
| `00200000` | Do not attack. |
| `00400000` | Do not apply damage to HP. |
| `02000000` | Hide damage popups. |
| `04000000` | Actor is using its idle animation. |
| `08000000` | Show status icons. |
| `10000000` | Blur effect is enabled. |
| `20000000` | Do not use an inactive animation; player only. |

### Part flags

| Value | Meaning |
| --- | --- |
| `00000001` | Invisible. |
| `00000002` | Do not accept decorations. |
| `00000004` | Hide the part's shadow. |
| `00000008` | Default part selected for this actor. |
| `00000020` | Ignore the "target below" check. |
| `00000040` | Minor target; ignored by Primary Only targeting. |
| `00000080` | Cannot be tattled. |
| `00000100` | Transparent/illusory part. |
| `00002000` | Damage immune. |
| `00020000` | Cannot be targeted. |
| `00100000` | Position is absolute rather than relative to the actor. |
| `00800000` | Primary target for attacks which filter secondary parts. |
| `01000000` | Has a palette effect. |
| `20000000` | Do not change idle animation for status. |
| `40000000` | Do not apply the shock visual effect to this part. |
| `80000000` | Do not allocate movement state for this part. |

Part target flags are `01 = NoJump`, `02 = NoSmash`, and `04 = NoDamage`. They are consumed by target selection and damage/status routines, not by rendering.

## 11.6. Target Flags

Items and moves use the same target-list filter bits:

| Value | Meaning |
| --- | --- |
| `00000001` | Player selects one target. |
| `00000002` | Engine flag 2; widely used for ordinary enemy targeting, but its independent meaning is not named. |
| `00000004` | Ground row only. |
| `00000008` | Target the player instead of enemies. |
| `00000010` | Reject high targets; unused by vanilla moves. |
| `00000020` | Reject actors marked Flying. |
| `00000040` | Reject ground-row targets. |
| `00000080` | Unread compatibility bit used by jump-like moves. |
| `00000100` | Target the partner instead of enemies. |
| `00000400` | Air Lift filter; rejects ceiling targets. |
| `00000800` | Jump-like move; rejects parts marked NoJump. |
| `00001000` | Smash-like move; rejects parts marked NoSmash. |
| `00002000` | Reject targets behind another eligible target. |
| `00004000` | Reject targets below another eligible target. |
| `00008000` | Primary parts only. |
| `00010000` | Allow actors marked Target Only. |
| `00020000` | Tattle filter; rejects actors or parts marked NoTattle. |
| `00040000` | Reject ceiling actors. |
| `00100000` | Direction Right filter. Bugged and unused. |
| `00200000` | Direction Left filter. Bugged and unused. |
| `00400000` | Direction Below filter. Bugged and unused. |
| `00800000` | Direction Above filter. Bugged and unused. |
| `80000000` | Override target selection and proceed without choosing a target. |

Target construction also inspects actor rows, columns, whole-actor flags, part flags, and part target flags. Copying a bitfield without the corresponding actor-part setup is a common reason for an empty target list.
