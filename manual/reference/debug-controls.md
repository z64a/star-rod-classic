# Debug Controls

Star Rod Classic can add an in-game debug overlay and menu to a compiled mod. In the main window, click **Options** beside **Compile Mod**, then open the **Debug** tab. Changes take effect the next time the mod is compiled.

## Debug Build Options

| Option | Description |
| --- | --- |
| **Enable Debug Information** | Installs the debug overlay, menu, controller shortcuts, and watch list. The bottom of the screen shows Mario's position and the current map while exploring, or the current formation and stage during battle. |
| **Enable Variable Logging** | Displays recent writes to game and mod bytes and flags. This requires **Enable Debug Information**. Named globals are shown by name when possible. |
| **Quick Launch** | Skips the normal startup sequence and immediately loads the last saved file. This option does not require the debug patch. |
| **Debug Battle** | Sets the four-digit hexadecimal formation ID initially shown by **Battle Select** in the debug menu. |

These settings are stored in the project's `mod.cfg`. The corresponding keys are `EnableDebugCode`, `EnableVarLogging`, `QuickLaunch`, and `DebugBattleID`.

## Watch List

The watch list displays up to eight live values. Configure its entries under **Watch List 0** through **Watch List 7** on the **Debug** tab, then recompile the mod. Press D-pad Right in-game to show the detailed overlay. In addition to the watch list, this overlay shows heap usage and the number of running scripts.

Click **Edit** beside a slot and choose one of the following categories:

| Category | Use |
| --- | --- |
| **Memory** | Read a byte, short, word, float, or double from a RAM address. Enter a short display name, choose the value's size, and enter the hexadecimal address. |
| **Variable** | Watch a map, area, game, or mod script variable by entering its expression without the leading `*`, such as `GameByte[0]`. Local variables and arrays cannot be watched because the overlay has no `Evt` context from which to resolve them. |
| **Clear** | Remove the entry from this slot. |

Memory values are shown in hexadecimal, except for floats and doubles. Display names are limited to 15 characters.

The same entries may be written directly in `mod.cfg`. A variable entry contains only the variable expression. A memory entry uses `address,size,name`, where the size is `1`, `2`, `4`, `F`, or `D`:

```text
DebugWatch0 = GameByte[0]
DebugWatch1 = 8010F07C,1,ActionState
```

The examples above watch a script variable and an unsigned byte at `8010F07C`, respectively. Editing the entries through Star Rod is generally less error-prone.

## In-Game Controls

| Control | Action |
| --- | --- |
| D-pad Left | Open the debug menu. Use the D-pad to navigate, R to select, and L to return or close the menu. |
| D-pad Right | Toggle the detailed overlay, including the watch list, heap usage, and running-script count. |
| D-pad Down | Toggle god mode. Mario and his partner deal 99 damage, while Mario ignores most incoming damage. |
| D-pad Up | Toggle turbo mode. This increases Mario's walk and run speeds and prevents enemy encounters. |

The debug menu also provides map and battle selection, save and load controls, editors for common game state, a sound player, collision visualization, and a full restore command.