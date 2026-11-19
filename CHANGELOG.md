# Changelog

## 1.3.0 (2026-11-19)

For Starsector **0.98a and 0.98.5a**. Format 1, all additive (docs/FORMAT.md, "Other mods and mod
conditions"): content without the new keys behaves exactly as with 1.2. Tested in game on 0.98a
(Saga Forge `docs/INGAME-CHECKS.md`, "Task #60", "Task #64", "Task #59" and "Task #61", 2026-11-17/18;
"Task #70", 2026-11-18; "Task #69", 2026-11-19).

- **The mod's list of other mods** (`data/sagaforge/mods.json`, type `mods`): required, optional
  and incompatible mods, with reasons.
- **The start record**: the first time SagaLib sees a content mod in a save, it records which of
  its optional and incompatible mods are enabled. Every mod condition uses it: a mod counts only
  if it was enabled when the game started and still is.
- **`SF_HasMod <id…>`** (all of them count) and **`SF_HasAnyMod <id…>`** (one does): rule
  commands, and conditions in phrase cases, `requires` and pool entries.
- **`modsWith` / `modsWithout` / `modsAny`** on quests, people, systems and their entities,
  encounters, roaming fleets, event schedulers, progress events, pools, scatter, starting
  relations and scripted faction changes. A skipped item logs one info line per game.
- **Notices**: an incompatible mod enabled, or an optional mod added or removed since the save
  started: a log warning on every load and a one-time dialog per save and change.
- **Scripted faction change `hideFromIntelTab`** (`FactionAPI.setShowInIntelTab(false)`), for a
  faction that only matters with an optional mod.
- **Dev menu › Mods**: each content mod's list, its start record and what is enabled now, and
  everything skipped for its mod conditions, with why.
- A person listed in a market's `people` is no longer created there when their own conditions
  don't hold.
- **Versions by installed mods** (docs/FORMAT.md, "Versions by installed mods"; task #64): a
  quest, person, system, encounter, pool, event scheduler, progress event, scatter or
  `factions.json` may have `versions`: fields changed when some optional mods count. Each item's
  version is chosen once per save from the start record (`$global.sagaforge_ver_<kind>_<id>`;
  quests also set `$global.sfv_<quest>_<mod>`) and never switches. Every combination is parsed at
  load (at most 6 mods per item). Golden case `test/golden/versions.json`.
- **text.json versions**: item names, descriptions, icons and prices follow the mods enabled at
  startup.
- **Faction setters** (scripted changes): `set` the display names, logo, crest, secondary colour
  and doctrine fields; `add` known ships, weapons and fighters; `add` / `remove` illegal
  commodities; `showInIntelTab`. Ops and paths are checked at load (also `remove`'s lists).
- **Sector snapshot** (dev mode): Dev menu › Write sector snapshot writes the sector's systems,
  places, markets and people to `saves/common/sagaforge_snapshot.json.data` for Saga Forge.
- **Dev menu › Versions**: each versioned item, the mods it mentions and its version in this game.
- **MagicLib bounties from quests** (docs/FORMAT.md, "MagicLib bounties"; task #59): the first
  optional bridge (`src-optional/magiclib`, checked against MagicLib 1.5.6), loaded only when
  MagicLib is enabled; SagaLib still needs nothing but the game. **`SF_StartBounty <bounty>`**: the
  player takes a MagicLib bounty, as if accepted on its board. **`SF_BountyState <bounty> <state…>`**:
  `unavailable`, `offered`, `active`, `succeeded`, `failed`, `expired`, `dismissed` or `ended`,
  read from MagicLib's own memory keys. Without MagicLib every bounty is unavailable and starting
  one only logs why. Dev menu › Mods says whether the bridge is ready. Golden case
  `test/golden/bounties.json`.
- **Waiting for a MagicLib bounty's end** (quest `bountyWaits`; task #69): while the quest is at
  the step before a Wait node, SagaLib watches the bounty's memory keys and fires
  `<quest>_<node>_succeeded` / `_failed` / `_expired` / `_dismissed` in its own dialog, also when
  the bounty ended before the quest got there. Works for any mod's bounty, no MagicLib class.
  Golden case `test/golden/bountywait.json`.
- **Quest-ended keys**: `$global.sagaforge_success_<quest>` / `$global.sagaforge_fail_<quest>`
  (true/false), set when a quest ends (and on load for quests that ended before), for what can't
  test the state string, such as MagicLib's bounty triggers.
- **Places a quest needs, markets changing owner** (docs/FORMAT.md, same title; task #61; no
  bridge, works with Nexerelin's invasions):
  - **A quest isn't offered or started while a place or person it needs doesn't exist** (its
    contact, `$id ==` in its start conditions, stage markers, Arrive places): logged once.
  - **`ownerChange`** on quests: the owners of their markets are remembered at the start and
    compared once a day; `keep`, `fail` or `stage` (with a trigger), always with
    `$global.<quest>_ownerChanged` and `$global.<quest>_newOwner`.
  - **`protectFromInvasion`** on markets: `$nex_uninvadable` in the market's memory.
  - **Dev menu › Change a market's owner**.
  - Golden case `test/golden/places.json`.
- **Presence by the market's owner** (docs/FORMAT.md, "Presence"; task #70): a person's
  `presence` (`faction`, `atLeast` a RepLevel or `notHostile`, optional `elsewhere`) makes them
  present only while their market's owner gets on with that faction. Checked once a day, on load
  and after the dev menu's owner or relation change, so a new owner, a war or peace all count:
  `$global.sagaforge_away_<person>` true/false, their comm-directory entry hidden while away
  (`setHidden`, save-safe), listed at `elsewhere` if set, their quests not offered (logged once).
  - **`SF_FactionRep <factionA> <factionB> <level>`**: a condition between two factions (either may
    be `$faction.id`), for dialog, Branch cases, phrase cases and `requires`.
  - **Dev menu › War or peace** for presence people's market owners and factions.
  - Golden case `test/golden/presence.json`.
- **Mod settings (LunaLib)** (docs/FORMAT.md, "Mod settings (LunaLib)"; task #62): a content mod's
  `data/config/LunaSettings.csv` (content.csv type `settings`) is read without LunaLib, and every
  value field (Boolean, Int, Double, Radio, String) becomes `$global.<fieldID>` on every load: its
  default, or the player's value from LunaLib's menu through the second optional bridge
  (`src-optional/lunalib`, checked against LunaLib 2.0.5), loaded only when LunaLib is enabled. A
  change saved in the menu during a campaign applies at once. Values are shared by every save;
  nothing is saved by SagaLib.
  - **`creditsScale`** on a reward and **`fleet.sizeScale`** on an encounter (`size: fp`): scale by a
    setting (`per`, or `factors` by value), clamped to 0..100; credits rounded, a fleet keeps at
    least 1 point.
  - **Dev menu › Mod settings**: each value now, LunaLib or default, and the bridge's status.
- **Starting relations left to Nexerelin** (docs/FORMAT.md, "Factions runtime"; task #63):
  `factions.json` may list `nexerelin` factions, whose relations the mod also ships in a Nexerelin
  faction config. With Nexerelin enabled (and the config file really in the mod), SagaLib leaves
  their relations without mod conditions to Nexerelin, which applies them in both sectors and
  randomizes them only when the player asks; SagaLib no longer overwrites them (its new-game hook runs
  after Nexerelin's). Relations with mod conditions, and everything without Nexerelin, are set as
  before.
  - Golden case `test/golden/settings.json`.
- **Nexerelin quest skip** (docs/FORMAT.md, "Nexerelin quest skip"; task #65): the third optional
  bridge (`src-optional/nexerelin`, checked against Nexerelin 0.12.2d), **`SagaQuestSkipPlugin`**,
  named as the plugin of the quest chains a content mod writes into
  `data/config/exerelin/questSkip.json`, so only Nexerelin loads it. Nexerelin sets a skipped quest's
  memory flags only through a plugin; this one also gives the skipped ending's rewards after the new
  game's time pass (a quest's new **`skip`** section: `rewards`, `unhide`), once, and sets
  **`$global.sagaforge_skipped_<quest>`**.
- **MagicLib achievements** (docs/FORMAT.md, "MagicLib achievements"; task #65):
  **`SF_Achievement <id>`** unlocks an achievement of an enabled mod's `magic_achievements.csv`
  through the MagicLib bridge (`MagicAchievementsBridge`), with MagicLib's notice in the dialog.
  Without MagicLib (or with its achievements turned off) it does nothing and logs why once.
- **A quest's own icon is loaded** before the game draws it (the intel list, the dialog's journal
  lines): the game only draws loaded textures and nothing loaded a mod's own icon file. A file that
  can't be loaded is logged once and falls back to the giver's portrait (or the default icon).

## 1.2.0 (2026-11-17)

For Starsector **0.98a and 0.98.5a** (one jar; the release has a zip for each, differing only in
`mod_info.json` `gameVersion`). Includes 1.1.0, which wasn't released on its own. Every part was
tested in game on 0.98.5a with throwaway test mods (Saga Forge `docs/INGAME-CHECKS.md`, Phases 7–8).


Format 1, with a new content type and command (docs/FORMAT.md, "Pools"). Content without them
behaves exactly as with 1.1.

- **Random pools** (`data/sagaforge/pools/<id>.json`, type `pool`) and **`SF_RollPool`**: pick
  one entry by weight (or nothing), with conditions and limits (once per playthrough or per
  object, cooldown days, at most N times). Stable: the same object and pool give the same pick
  after a reload. In dev mode a test profile can force a pick.
- `core/Pools.java`: the picker every later random feature uses (loot, random encounters,
  scatter), pure and tested; the editor's Play mode rolls exactly the same numbers (golden case).
- **Random targets** (quest `targets`): a system, planet, entity or market picked with vanilla's
  own `HubMissionWithSearch` (56 of its require/prefer criteria) when the quest is offered (or
  starts); nothing found = not offered. The pick and its name, system, distance and faction are
  in `$global` memory. Stage markers, arrivals and entity flags can name the target; new condition
  **`SF_IsTarget <quest> <var>`**.
- **At the target**: details for conditions (decivilised, ruins and their size, themes, black
  hole, hostile, …), a **person** at a picked colony (vanilla's `findOrCreatePerson`), and
  **`SF_SpawnAtTarget`** (vanilla's mission spawning: a cache, probe or derelict near the target,
  removed when the quest ends). Encounters can spawn at a target; `SF_IsTarget` also takes
  `person` and `spawned`; a system target is marked by its centre.
- **Phase 7e**: encounter pools (random encounters through vanilla's `EncounterManager`, at its
  encounter points), salvage `loot` and `defenders` on system entities, `protectFromDeciv` on
  markets, `$global.sagaforge_deciv_<market>` when a colony decivilises, and **scatter** (objects
  placed at new game, or in an existing save, in places picked with vanilla's search).
- **Progress events** (`data/sagaforge/progress/<id>.json`, type `progress`): vanilla's event bar
  (BaseEventIntel) for a mod's own event: start conditions, monthly factors, stages that run rules
  (`SF_progress_<event>_<stage>`, or a random outcome's), repeat or end; **`SF_Progress`** adds
  one-time points. Saved classes SagaProgressEvent, SagaMonthlyFactor, SagaOneTimeFactor, SagaStage.
  Their stage actions run quietly, like world events'. docs/FORMAT.md "Progress events".
- **Roaming fleets**: `spawn.when: "roaming"` sends an encounter's fleet from `at` to `to` (and back)
  on vanilla's routes, every `days` on average, at most `max` at once; a real fleet only near the
  player (`SagaRouteSpawner`, a saved class). docs/FORMAT.md "Roaming fleets".
- **World events**: event schedulers (`data/sagaforge/events/<id>.json`, type `events`) keep
  between min and max of their events running, start one by weighted pick (the pool picker:
  conditions, once, max, cooldown) every interval, end it after its days; start and end run the
  rules on `SF_event_<event>_start` / `_end`; an optional news item in the intel screen
  (`SagaNewsIntel`, a saved class). State in global memory. docs/FORMAT.md "World events".
- **Translations**: a translation mod ships one CSV (content type `translation`) that replaces
  the mod's texts at runtime: dialog through tokens (`$sft-<rule>-<part>-`, new token generator
  TextTokens, texts in `data/sagaforge/text.json`, type `strings`), quest/people/system texts
  before they are read, item names and descriptions on application load, faction names and the
  names the save keeps (systems, places, markets) on every load. Lines made for an older original
  are skipped (fingerprints). docs/FORMAT.md "Translations".
- Fixed: phrase texts with a literal `$` or `\` broke or lost characters (the game uses token
  values as regex replacements); values are quoted now.
- Quest rewards can roll drop groups (`loot`), with vanilla's salvage roller and "Gained" lines.
- Planets can keep their ruins unexplored (`ruinsUnexplored`) in a system with a colony, which
  vanilla otherwise marks explored at new game.
- **Fix:** new systems are cleared of hyperspace's storm clouds around them, as vanilla's own systems
  are (its sector generator runs before mods build theirs); systems an older SagaLib built are
  cleared once on the next load.
- **Fix:** a quest fleet beaten but not destroyed breaks off and leaves instead of chasing the player.
- Dev tools: a test profile's `startAt` also works from the Ctrl+F9 dev menu mid-game (it moved
  the fleet only at a new game before); the menu closes after the move. Test mods use it to take
  the player to their testing area.

Tested: 94 Java tests and the golden cases (including `pools.json`, shared with Saga Forge).

## 1.1.0 (2026-11-15, released as part of 1.2.0)

Format 1, with optional new fields (docs/FORMAT.md, "SagaLib 1.1 additions"). Content without
them behaves exactly as with 1.0; saves keep working (new memory keys only).

- **Systems without a star**: an empty centre with a light colour.
- **Planet conditions** on uncolonised planets and moons, and their survey level.
- **Objects that appear when a quest reaches a stage** (`appears`, optionally `replaces` another
  object), and `SF_PlaceEntity`.
- **Objects removed during a quest** (`removals`, any entity), and `SF_RemoveEntity`. An object
  with a market is only removed with `decivilize`.
- **Encounters that spawn when the player is near** (`spawn.when: player-near`, `spawn.range`).
- `loot` reserved on entities and rewards.
- **Entity flags** during quest steps (`entityFlags`): the place a quest waits at is marked and
  stands out on the map while the quest is there.

- **Named fleets**: ship names and the flagship of an explicit fleet; defeats counted per fleet
  template; a captain may die with the fleet.
- **People who are gone** (`SF_RemovePerson`, or a captain who died): removed for good, never
  recreated or placed, never a captain again.

- **Starsector 0.98.5a**: the same jar runs on 0.98a and 0.98.5a; `0.98.5a-RC5` joins the game
  versions SagaLib was checked against (no "not one SagaLib was checked against" warning there).

Tested: 61 Java tests (511 checks) and the golden cases. In game (2026-11-15, user): a quest mod
built by hand in Saga Forge, played on 0.98a-RC8 and, migrated, on a new 0.98.5a-RC5 game
(including Survival Mode). The 1.1-only features not used by that mod (starless systems, objects
that appear or are removed, named fleets, player-near spawns) are still untested in game.

## 1.0.0 (2026-11-11)

First release, for Starsector 0.98a. Runtime format 1 (docs/FORMAT.md). From 1.0 on the save
format is frozen: saved class names and fields, and the memory keys, never change.

- **Quests**: one generic mission class for every quest (contacts' `person_missions.csv` and
  `bar_events.csv`), steps kept as string ids in memory, journal text and map markers per step,
  time limits, abandon, rewards (`SF_Reward`: credits, reputation, items, XP, story points).
- **Rule commands**: `SF_Start`, `SF_Step`, `SF_End`, `SF_Reward`, `SF_AddShip`,
  `SF_RemoveShip`, `SF_BackToMenu`, the displays `SF_Show…`, the conditions `SF_AtStep`,
  `SF_QuestDone`, `SF_QuestState`, and `SF_Spawn` / `SF_Despawn`.
- **People and phrases**: characters placed at markets (replacing the generated holder of the
  same post), contacts, and phrase tokens whose wording follows the situation.
- **Factions**: starting relations and scripted changes.
- **Star systems**: built on new game, and once in existing saves when the file asks for it;
  additions to existing systems with existence checks; entities removed by play (e.g. salvaged)
  stay removed.
- **Encounters**: quest-stage fleets with their own comm dialog, defeated/avoided outcomes,
  quest timers.
- **Save safety**: reconciliation on load (renamed steps via `renamedFrom`, removed steps marked
  "changed by an update").
- **Dev mode**: test profiles on new game, and Ctrl+F9 to apply a profile, jump a quest to a
  step, or reload the JSON.
- JSON errors are logged with the prefix `[SagaLib]`, the file and the JSON path; the bad object
  is skipped.

License: MIT (changed from CC BY 4.0 on 2026-11-11, the release day).

Tested: 56 Java tests (471 checks), the golden cases shared with Saga Forge's Play mode, and the
Vanguard Compact walkthrough in game (12 steps, 2026-11-11, with 126 mods enabled).
