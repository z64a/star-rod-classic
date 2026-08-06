# Engine Limits

This page collects important hard capacities and supported gameplay limits in the original engine and Star Rod's current compilers. It is not an exhaustive list. A field may be wide enough to store a larger number while normal engine code deliberately clamps or indexes it more narrowly.

## Player and Inventory

| Limit | Value | Reason |
| --- | ---: | --- |
| Badges owned | 128 | `PlayerData` has 128 acquired-badge slots. |
| Badges equipped | 64 | `PlayerData` has 64 equipped-badge slots. |
| Star Pieces held | 222 | The field is one byte, but ordinary inventory and pickup code clamp it to 222. |

## Maps and Collision

| Limit | Value | Reason |
| --- | ---: | --- |
| Models in a loaded map | 256 | The engine model list has 256 entries. |
| Unique collision vertices | 1,024 | Each triangle stores three 10-bit vertex indices; Star Rod rejects larger tables. |
| Maps with encounter-defeat history in one area | 60 | `EncounterStatus` has `defeatFlags[60][12]`. |

## NPCs, Items, and Battle Actors

| Limit | Value | Reason |
| --- | ---: | --- |
| Ordinary NPC slots | 64 | The world NPC list uses `MAX_NPCS = 64`. |
| Ordinary NPC ID storage | Signed byte | A live `Npc` stores its ID as `s8`; negative values are reserved for special lookups. |
| World or battle item entities | 256 | Each item-entity pool has 256 entries. |
| Enemy actors in one battle | 24 | Enemy actor IDs run from `0200` through `0217`. |

An API which accepts an `s32 npcID` does not enlarge the ID stored by an NPC. It allows the call to accept special negative IDs as well as ordinary list indices.

## Event Scripts and Messages

| Limit | Value |
| --- | ---: |
| Labels in one event script | 16 |
| Nested event-script loops | 8 |
| Nested switches | 8 |
| Local variables | 16 |
| Local flags | 96 |
| Message-variable buffers | 3 |
| Encoded characters in one message-variable buffer | 31 plus terminator |

The local variable and flag capacities apply to each `Evt` context. Child and parallel scripts may share or copy state depending on how they were started.
