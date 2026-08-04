# 7. Images, HUD Elements, and Item Icons

The current asset system has three layers:

- An **image asset** is raster and, when applicable, palette data built from PNG files.
- A **HUD element** is a small drawing script used by menus, battle UI, and other overlays.
- An **item entity** is a drawing script used for an item lying in the world.

An item record normally points to both a HUD element and an item entity. Those scripts, in turn, refer to image assets. Keeping the layers separate allows one image to be reused by several scripts and one script to animate through several images.

## 7.1. Editing an Existing Image

Open **Globals Editor → Images**, select the asset, and use **Choose** to locate its source PNG. Project images live under `$mod/image/assets/`; the paths stored in `ImageAssets.xml` are relative to that directory.

The image record controls its texture format, palette count, vertical flip, and whether the original ROM position is fixed. CI images may have several palette variants. For an asset named `item/Mushroom`, Star Rod recognizes variants such as:

```text
image/assets/item/Mushroom.png
image/assets/item/Mushroom_alt.png
image/assets/item/Mushroom_alt2.png
```

Do not silently change an existing asset's dimensions or texture format unless every script and renderer using it can accept the change. The preview tells you what Star Rod loaded, but the consuming HUD script still determines the displayed size.

If you place PNGs in `image/assets/` by hand, use **Actions → Import Missing** to create records for complete, unlisted image sets.

## 7.2. Adding an Image

Use **Add Image** in the Images tab, give the asset a unique name, choose its PNG, and select the correct format. CI-4 is a natural fit for the game's small paletted icons, but the image system also supports non-CI texture formats. Save the Globals Editor after the preview and palette count are correct.

Adding an image does not make it appear anywhere by itself. Reference it from a HUD element, an item entity, a message image list, or other patch data.

## 7.3. HUD Elements

HUD element sources live in `$mod/image/hudscripts/` and use the `.hs` extension. The Globals Editor divides them into four load groups:

| Group | Intended lifetime |
| --- | --- |
| Item Icons | HUD elements indexed through the item table. |
| Always Loaded | Elements available throughout normal gameplay. |
| During Battle Only | Elements loaded with the battle UI. |
| Pause and File Menus Only | Elements used by those menus. |

Use **Add Script** to create an element. A simple static 32×32 element looks like this:

```star-rod
SetVisible
SetTileSize ( .IconSize:32x32 )
Loop
    SetIcon ( 60` ~ImageIcon:item/Mushroom )
Restart
End
```

For an item HUD element named `Mushroom`, a second element named `Mushroom_disabled` is used for the disabled appearance when present. If it is absent, Star Rod uses the normal element for both states. A common arrangement is for the disabled script to use the asset's alternate, desaturated palette.

## 7.4. Item Entities

Item entity sources live in `$mod/image/itemscripts/` and use the `.is` extension. They control the icon drawn for an item in the world:

```star-rod
SetIcon ( 60` ~ImageIcon:item/Mushroom )
Restart
End
```

Use the **Item Entities** tab when you need animation or special timing. For an ordinary static item, the automatic graphics option described in the next chapter can generate both the HUD and item-entity scripts from one image asset.

Star Rod rebuilds and relocates the HUD and item-entity script tables during compilation. New entries are supported; they are not limited to replacing the vanilla icons.
