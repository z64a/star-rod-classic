# 10. Maps

## 10.1. Introduction

The game world is organized into areas and maps. An area determines which texture archive and special entity data are available. Each map has an area index and a map index; an encoded value such as `00030012` means map 12 in area 3.

Map variables and flags survive a battle transition because the game returns to the same world state afterward. They are cleared when a different map is entered. Enemy-defeat history is tracked separately for maps in the current area and is cleared when a different area is entered.

A normal map has three kinds of data:

- **Shape** contains the rendered model tree and geometry.
- **Hit** contains colliders, zones, and collision geometry.
- **Script data** contains the event scripts and supporting data for the map overlay.

The asset table stores shape, hit, and area texture resources. Shape and hit resources are normally Yay0-compressed; textures are normally uncompressed, though the Level Editor records compression per resource. Script data is listed in the map configuration table. Star Rod rebuilds and patches both tables from `$mod/map/MapTable.xml`.

By convention, a map named `mac_00` uses `mac_00_shape` and `mac_00_hit`, while the area's texture resource is named `mac_tex`. The Level Editor can share or rename resources, so these are useful defaults rather than hard engine requirements.

## 10.2. Adding a New Map

1. Open **Level Editor** and select the area which should own the map.
2. Click **Add New Map**. Give it an engine name of at most eight characters and check the Script Data, Shape Data, and Hit Data it will use.
3. Save the level table. The new entry is written to `$mod/map/MapTable.xml`.
4. Select the new map and click **Create** beside its missing map file. Start from the template or copy a mechanically similar map.
5. Open the new map in Map Editor, edit it, and save it. Editor saves live under `$mod/map/save/`.
6. Click **Create** beside the missing map patch. Add the entry script and any other map data to the new `.mpat` file under `$mod/map/patch/`.
7. Build the mod and enter the map from a known working exit while testing.

Use the map's **Friendly Name** for your own organization and its **Engine Name** for scripts and table lookup. A map with Shape or Hit unchecked may deliberately share or omit that resource; do not create dummy assets merely to silence an option you did not intend to enable.

The Level Editor also has a separate stage list for battle stages. Use **Add New Stage** there when the geometry is intended for a battle formation rather than the world map table.

## 10.3. Editing Maps

For a vanilla map, `$mod/map/src/` contains the initial copied source. Open it through Level Editor or Map Editor and save your working version; Star Rod looks in `map/save/` first and falls back to `map/src/` when no saved copy exists.

When you are done making changes in the Map Editor, save your changes and build Geometry and Collision the Map menu. The **Automatically Build Map Assets** project option is enabled by default, however, and compilation rebuilds missing or out-of-date shape and hit files for maps found under `map/save/`.

Script changes belong in the map's `.mpat` file under `$mod/map/patch/`. Reusable imports shared by several maps belong under `$mod/map/import/`. The copied `.mscr` and `.midx` sources in `map/src/` describe the vanilla overlay; keep your authored changes in the patch rather than editing generated or dumped binary data.

### 10.3.1. Model Render Modes

The common modes offered by Map Editor are:

| ID | Star Rod Name | Description and engine name |
| --- | --- | --- |
| 01 | `Surface_OPA` | Standard solid surface. `SURF_SOLID_AA_ZB` |
| 03 | `Surface_OPA_No_AA` | Solid surface without anti-aliasing. `SURF_SOLID_ZB` |
| 04 | `Surface_OPA_No_ZB` | Solid surface which ignores the Z-buffer. `SURF_SOLID_AA` |
| 11 | `Surface_XLU_Layer1` | Standard transparent surface, background layer. `SURF_XLU_AA_ZB_L1` |
| 16 | `Surface_XLU_Layer2` | Transparent surface, middle layer. `SURF_XLU_AA_ZB_L2` |
| 22 | `Surface_XLU_Layer3` | Transparent surface, foreground layer. `SURF_XLU_AA_ZB_L3` |
| 13 | `Surface_XLU_No_AA` | Transparent surface without anti-aliasing. `SURF_XLU_ZB` |
| 14 | `Surface_XLU_No_ZB` | Transparent surface which ignores the Z-buffer. `SURF_XLU_AA` |
| 0D | `AlphaTest` | Two-sided cutout surface; pixels pass or fail the alpha test. `ALPHA_TEST_AA_ZB_2SIDE` |
| 0F | `AlphaTest_OneSided` | One-sided alpha-tested surface with back-face culling. `ALPHA_TEST_AA_ZB_1SIDE` |
| 10 | `AlphaTest_No_ZB` | Alpha-tested surface which ignores the Z-buffer. `ALPHA_TEST_AA` |
| 05 | `Decal_OPA` | Solid surface drawn on top of another surface at the same depth. `DECAL_SOLID_AA_ZB` |
| 07 | `Decal_OPA_No_AA` | Opaque decal without anti-aliasing. `DECAL_SOLID_ZB` |
| 1A | `Decal_XLU` | Transparent decal used to mix coplanar models. `DECAL_XLU_AA_ZB` |
| 1C | `Decal_XLU_No_AA` | Transparent decal without anti-aliasing. `DECAL_XLU_ZB` |
| 09 | `Intersecting_OPA` | Solid surface intended to intersect another surface. `INTER_SOLID_AA_ZB` |
| 26 | `Intersecting_XLU` | Transparent intersecting surface. `INTER_XLU_AA_ZB` |
| 2E | `Cloud` | Special cloud surface with depth testing. `SURF_CLOUD_ZB` |
| 2F | `Cloud_No_ZB` | Special cloud surface without the Z-buffer. `SURF_CLOUD` |

