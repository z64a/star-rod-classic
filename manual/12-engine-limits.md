# 12. Engine Limits

These are hard capacities or supported gameplay limits in the original engine and Star Rod's current compilers. A field may be physically wide enough to store a larger number while normal engine code deliberately clamps or indexes it more narrowly.

## 12.1. Player and Inventory

| Limit | Value | Reason |
| --- | ---: | --- |
| Badges owned | 128 | `PlayerData` contains 128 acquired-badge slots. |
| Badges equipped | 64 | `PlayerData` contains 64 equipped-badge slots. |
| Star Pieces held | 222 | The field is one byte, but normal inventory and pickup code clamp it to 222. |

## 12.2. Maps and Collision

| Limit | Value | Reason |
| --- | ---: | --- |
| Models in a loaded map | 256 | The engine model list has 256 entries. |
| Unique collision vertices | 1,024 | Each collision triangle stores three 10-bit vertex indices; Star Rod rejects larger vertex tables. |
| Maps with encounter-defeat history in one area | 60 | `EncounterStatus` has `defeatFlags[60][12]`. |

## 12.3. NPCs, Items, and Battle Actors

| Limit | Value | Reason |
| --- | ---: | --- |
| Ordinary NPC slots | 64 | The world NPC list is `MAX_NPCS = 64`. |
| Ordinary NPC ID storage | Signed byte | A live `Npc` stores its ID as `s8`; several negative values are reserved for special lookups. |
| World or battle item entities | 256 | Each item-entity pool has 256 entries. |
| Enemy actors in one battle | 24 | Enemy actor IDs run from `0200` through `0217`. |

An API taking an `s32 npcID` does not enlarge the ID stored on the NPC. It only allows the call to accept special negative IDs as well as ordinary list indices.

## 12.4. Event Scripts and Messages

| Limit | Value |
| --- | ---: |
| Labels in one event script | 16 |
| Nested loops | 8 |
| Nested switches | 8 |
| Local variables | 16 |
| Local flags | 96 |
| Message-variable buffers | 3 |
| Encoded characters in one message-variable buffer | 31 plus the terminator |

The local-variable and flag counts apply to each event-script context. Child and parallel scripts may share or copy context depending on how they were started, as described in Chapter 3.
