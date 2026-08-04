# 8. Editing Items

The item table controls names, descriptions, graphics, usage flags, targeting, value, badge associations, and a few small effect parameters. It does not contain the full behavior of every item: battle-use scripts and map scripts still do the actual work.

Open **Globals Editor → Items** to edit the table. Changes are saved to `$mod/globals/Items.xml`.

## 8.1. Item Fields

| Field | Meaning |
| --- | --- |
| Name | The identifier used by Star Rod's item enum. It must be unique. |
| Name Message | The message shown as the item's display name. |
| Short Desc | The message used in shops. |
| Full Desc | The message used in most menus. |
| Type Flags | Where the item may be used and whether it is a badge, key item, food, gear, and so on. |
| Target Flags | Which battle targets may be selected. Copy a similar vanilla item when the unknown bits matter. |
| Graphics | Either one image used to generate ordinary scripts, or explicit item-entity and HUD-element scripts. |
| Move | The move associated with a badge. |
| Menu Order | Sort priority among badges; larger values appear farther down. |
| Potency A | HP gain for food, or power for a battle-usable item. |
| Potency B | FP gain for food. |
| Sell Value | Default shop and Refund value. Shops may override it. `-1` represents no ordinary value. |

The type-flag editor currently names these bits:

| Value | Meaning |
| --- | --- |
| `0001` | Usable in the world. |
| `0002` | Usable in battle. |
| `0004` | Consumable. |
| `0008` | Key item. |
| `0020` | Gear: boots or hammers. |
| `0040` | Badge. |
| `0080` | Food or drink. |
| `0100` | Use the drink animation. |
| `0200` | Collectible entity, such as a coin or heart. This bit has no direct use effect. |
| `1000` | Use a full-size 32×32 world entity instead of 24×24. |

These bits describe categories; they do not implement an effect. For example, setting "Usable in battle" does not create a `UseItem` script.

## 8.2. Editing an Existing Item

Select an item, make the changes, and save the Globals Editor. The message chooser is safer than typing a raw message ID because it records a resolvable string identifier. If you rename a move, image, item entity, or item HUD element from within the editor, dependent item records are updated where supported.

Be conservative with the flags of engine-special items such as boots, hammers, key items, coins, and recovery pickups. Their category bits are often checked by code outside the item table.

## 8.3. Adding a New Item

1. Create its name and description messages under `$mod/strings/src/`.
2. Add or choose an image in **Globals Editor → Images**.
3. Return to **Items** and click **Add Item**. Be careful not to shift the IDs of existing items.
4. Give it a unique identifier and choose the three messages.
5. Set the type and target flags by copying the closest working vanilla item, then change only the properties you understand.
6. Choose **Create them automatically from an image** for a normal static icon, or select explicit item-entity and HUD-element scripts for custom animation.
7. Fill in the fields relevant to its type: HP/FP gain for food, power for a battle item, or move and menu order for a badge.
8. Save the Globals Editor and compile the mod. Resolve every unknown reference reported by the build before testing in game.

Automatic graphics creates a world item-entity script and a menu HUD element from the selected image. If that image has a second palette, it also creates the disabled HUD variant. With manual graphics, the normal HUD element may live in either the Item Icons or Always Loaded group; a sibling named `_disabled` supplies its disabled state.

### Battle-usable items

A battle item also needs an entry in `$mod/battle/item/Items.txt`. That entry associates the item ID with a battle item source from `$mod/battle/item/patch/` or one of the dumped vanilla sources. The source must export `$Script_UseItem`.

The simplest way to start is to copy the entry and `.bpat` source for a mechanically similar vanilla item, rename the source, and then replace its effect logic. The item table potency fields are available to conventional item code, but the `UseItem` script decides how targeting, animation, damage, healing, status, and inventory consumption actually proceed.

### Badges

A badge record points to a move from the Globals Editor's Moves tab. That move supplies the BP cost and battle ability association. Adding the badge item alone does not create a new command or attack; use an existing move when only the badge's presentation is new, or add the corresponding move and battle patches as well.

## 8.4. Item IDs

Star Rod rebuilds the item enum from the ordered `Items.xml` list. In patches, prefer `.Item:Name` or another context which Star Rod knows is an `itemID` over a handwritten number. Appending new records preserves vanilla IDs, while reordering or removing records changes every later item ID and can break scripts, inventories, shops, and save data.
