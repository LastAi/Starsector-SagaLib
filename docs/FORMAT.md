# SagaLib runtime format (format 1)

SagaLib (mod id `lastai_sagalib`, MIT) runs content made with Saga Forge
(`tools/saga-forge`). This file is the contract between Saga Forge's Export and SagaLib.
The TypeScript copy of the behaviour is `tools/saga-forge/src/shared/play.ts` (Play mode). The
same golden cases in `test/golden/*.json` run on both sides, so the two can't drift apart.

## Files a content mod ships

| File | Read how | What |
|---|---|---|
| `data/config/sagaforge/content.csv` | `Global.getSettings().getMergedSpreadsheetDataForMod("id", path, "lastai_sagalib")`: rows from every enabled mod | `id,type,path,mod`: one row per runtime file. type ∈ `quest`, `people`, `factions`, `system`, `encounter`, `profiles`, `pool` (1.2), `mods` (1.3), `settings` (1.3, "Mod settings (LunaLib)"). SagaLib 1.3: quest, people (per person), factions, system, encounter, pool, events, progress, scatter and strings files may have `versions` ("Versions by installed mods") |
| `data/sagaforge/quests/<questId>.json` | `Global.getSettings().loadJSON(path, mod)` | one quest (below) |
| `data/sagaforge/people/<mod>_people.json` | same | `{format, people: [Person], phrases: [Phrase]}` (`phrases` = the mod-wide phrases) |
| `data/sagaforge/factions.json` | same | `{format, relations: {factionId: {otherId: -1..1}}, scripted: {factionId: [{op:"remove", path:"knownShips.hulls", value:"colossus"}]}}`; SagaLib 1.3: a relation may be `{"value": v, "modsWith": [...]}`, a change may carry mod conditions, ops `hideFromIntelTab`, `showInIntelTab`, `add` and `set` ("Versions by installed mods"), the file may have `versions`, and `nexerelin` lists factions whose relations are left to Nexerelin ("Factions runtime") |
| `data/sagaforge/systems/<id>.json` | same | a star system (M6; see that section) |
| `data/sagaforge/encounters/<id>.json` | same | an encounter (M7; see that section) |
| `data/sagaforge/pools/<id>.json` | same | SagaLib 1.2: a random pool (see "Pools") |
| `data/sagaforge/mods.json` | same, type `mods` | SagaLib 1.3: the mod's list of other mods (see "Other mods and mod conditions") |
| `data/sagaforge/dev/test_profiles.json` | same, **only in dev mode** | `{format, profiles: [TestProfile]}` |
| `data/campaign/person_missions.csv`, `bar_events.csv` | the game itself | rows with `plugin` = `lastai.sagalib.quest.SagaQuest` and `mission id` / `bar event id` = the quest id |
| `data/campaign/rules.csv` | the game itself | the dialog; it uses the rule commands below |

Each JSON file starts with `"_sagaforge": {"generated": "...", "hash": "..."}`. SagaLib ignores
that header. It must accept every `format` it has ever supported (only 1 so far), and log a clear
error with the file and the JSON path for anything malformed, then skip that object without
crashing the game.

Mod paths are relative to the mod that ships them (the `mod` column). Two mods may use the same
path (for example both ship `data/sagaforge/factions.json`): they are different files.

## Quest JSON

```json
{
  "format": 1, "id": "vgc_lost_convoy", "title": "The Lost Convoy", "kind": "quest",
  "icon": "graphics/icons/missions/data_retrieval.png", "repeatable": false, "timeLimitDays": 0,
  "start": {"kind": "contact", "person": "vgc_reyes", "freq": 10, "minRep": "FAVORABLE", "offerText": "...", "optionText": "..."},
  "requires": ["SF_QuestDone vgc_other success", "$global.vgc_flag"],
  "stages": [{"id": "find", "title": "Find the convoy", "journal": "Find the missing convoy in Kestrel.",
              "nextStep": "", "marker": "vgc_kestrel_belt", "kind": "normal|success|fail", "renamedFrom": ["search"]}],
  "variables": [{"name": "pay", "type": "number|string|bool|entity|person", "initial": "1"}],
  "targets": [],
  "rewards": [{"id": "done", "credits": 60000, "rep": [{"faction": "vgc_vanguard", "amount": 5}],
               "personRep": [{"person": "vgc_reyes", "amount": 5}], "items": [{"item": "vgc_black_box", "qty": 1}],
               "ships": ["buffalo_Standard"], "xp": 1000, "storyPoints": 1}],
  "timers": [{"node": "wait1", "trigger": "vgc_lost_convoy_wait1", "days": 7, "stage": "find"}],
  "encounters": [{"node": "ambush", "encounter": "vgc_belt_ambush", "defeated": "vgc_lost_convoy_ambush_defeated", "avoided": "vgc_lost_convoy_ambush_avoided"}],
  "bountyWaits": [{"node": "w", "bounty": "vgc_b_raid", "stage": "hunt", "succeeded": "vgc_lost_convoy_w_succeeded",
                   "failed": "", "expired": "vgc_lost_convoy_w_expired", "dismissed": ""}],
  "arrivals": ["vgc_convoy_wreck"],
  "phrases": [Phrase],
  "flags": ["$global.vgc_lost_convoy_catch_asked"]
}
```

- `kind` is `quest` (a vanilla hub mission: journal, markers, time limit) or `dialog` (a file only
  because it has inline phrases; no mission).
- `bountyWaits` (SagaLib 1.3, optional): Wait nodes on a MagicLib bounty's end, see "MagicLib bounties".
- `targets` (SagaLib 1.2): random targets, see "Random targets". `[]` or missing: none. A stage
  `marker`, an `arrivals` entry or an `entityFlags` entity may be `$global.<quest>_<var>`: the
  picked id.
- `requires` are condition lines (the rules.csv syntax, see "Conditions"). `create()` aborts if
  one is false.
- Reputation `amount` is in points (5 = +0.05), like vanilla's `AdjustRep <faction> 5`.

## Memory keys (the save state)

Quest progress lives only in sector memory, so saves survive updates:

| Key | Value |
|---|---|
| `$global.<quest>_state` | `active`, `success` or `fail` (unset = not started) |
| `$global.<quest>_step` | the current stage id (a string) |
| `$global.<quest>_<var>` | quest variables (set to `initial` when the mission is created, if unset) |
| vanilla's `$<quest>_missionCompleted` | set by `setCompletedKey()` on success, as vanilla does |
| `$global.sagaforge_success_<quest>` / `_fail_<quest>` | SagaLib 1.3: true once the quest ended that way (true/false keys MagicLib's bounty triggers can read; see "MagicLib bounties") |
| `$global.sagaforge_skipped_<quest>` | SagaLib 1.3: true once SagaLib gave the rewards of a quest the player skipped on Nexerelin's new-game screen (see "Nexerelin quest skip") |
| `$global.sagaforge_away_<person>` | SagaLib 1.3: true while a person with a `presence` rule is away, false while present (unset until the first check; see "Presence") |
| `$global.sagaforge_bwait_<quest>_<node>_done` | SagaLib 1.3: the step a Wait on a MagicLib bounty fired at (`*` = once per run); unset while it watches (see "MagicLib bounties") |

The generic mission's vanilla stage is an enum with only `ACTIVE`, `COMPLETED`, `FAILED` and
`ABANDONED` (PLAN.md §3.2, option A). Designer steps are strings in `$global.<quest>_step`.
Saved objects (the `SagaQuest`, its intel, scripts) store **ids only** and look the definition up
in the loaded JSON. **Never rename or remove a saved field or class after 1.0.**

## Rule commands (package `lastai.sagalib.rulecmd`, added through `ruleCommandPackages`)

| Command | Use | Effect |
|---|---|---|
| `SF_Start <quest>` | script | Accept the quest. If it is the mission being offered (`$missionId == <quest>`), accept it through vanilla's hub: contact `Call $mHub accept $missionId`, bar `BarCMD accept $missionId`. Otherwise (a quest started by another quest) create and start it directly. Sets state `active` and initializes the variables. |
| `SF_Step <quest> <step>` | script | `SF_Start` if not active yet, then set `$global.<quest>_step`, update the intel (journal text, next step, map marker of that stage, intel update notification). A stage of kind `success`/`fail` also ends the quest that way. |
| `SF_End <quest> success\|fail` | script | End the mission: vanilla stage `COMPLETED`/`FAILED`, state key set, completed key on success. |
| `SF_Reward <quest> <rewardId>` | script | Give that reward from the quest JSON: credits, rep, items, ships, XP, story points, each with vanilla's "Gained …" text in the dialog's text panel. |
| `SF_Spawn <encounter>` / `SF_Despawn <encounter>` | script | Encounters (M7). |
| `SF_PlaceEntity <entity>` | script | SagaLib 1.1: place a deferred entity (`appears`) now, once. |
| `SF_RemoveEntity <entity> [decivilize]` | script | SagaLib 1.1: remove an entity (fades out), once; one with a market only with `decivilize`. |
| `SF_RemovePerson <person>` | script | SagaLib 1.1: the person is gone for good (`$global.sagaforge_gone_<person>`): removed from their market, comm directory and the important people; never recreated, never placed, never a fleet's captain again. |
| `SF_IsTarget <quest> <var> [person|spawned]` | condition | SagaLib 1.2: the dialog is at the quest's random target: its entity, its market, or (a system target) anything in that system; `person`: talking to its person; `spawned`: at what SF_SpawnAtTarget placed. |
| `SF_SpawnAtTarget <quest> <var> <entity|derelict> <type> [where]` | script | SagaLib 1.2: vanilla's mission spawning near the target (see "Random targets"). |
| `SF_RollPool <pool> [object|global]` | both | SagaLib 1.2: pick one entry of a random pool by weight into `$sagaforge_pick` (see "Pools"). As a condition: true when an entry, not `nothing`, was picked. |
| `SF_HasMod <mod id> [more…]` | condition | SagaLib 1.3: every listed mod **counts**: enabled when this game started and still enabled (see "Other mods and mod conditions"). `!SF_HasMod <id>`: it doesn't count. |
| `SF_HasAnyMod <mod id> [more…]` | condition | SagaLib 1.3: at least one of them counts. |
| `SF_StartBounty <bounty>` | script | SagaLib 1.3: the player takes a MagicLib bounty, through the guarded bridge (see "MagicLib bounties"); without MagicLib it does nothing. |
| `SF_BountyState <bounty> <state> [more…]` | condition | SagaLib 1.3: the MagicLib bounty is `unavailable`, `offered`, `active`, `succeeded`, `failed`, `expired`, `dismissed` or `ended` (see "MagicLib bounties"). |
| `SF_Achievement <id>` | script | SagaLib 1.3: unlocks a MagicLib achievement through the guarded bridge (see "MagicLib achievements"); without MagicLib it does nothing. |
| `SF_FactionRep <factionA> <factionB> <level>` | condition | SagaLib 1.3: faction A is at least `<level>` with faction B (`VENGEFUL` … `COOPERATIVE`, or `notHostile` = `INHOSPITABLE` or better). Either faction may be a memory key, e.g. `$faction.id` (the owner of the market the dialog is at). A faction with itself is `COOPERATIVE`; a faction that doesn't exist makes it false (logged once). Vanilla's `RepIsAtWorst` is the player's standing; this one is between two factions (see "Presence"). |
| `SF_BackToMenu` | script | The end of a conversation branch: clear the options and `FireAll PopulateOptions`; if no rule adds an option (a wreck or other custom entity has no menu) the dialog closes. |
| `SF_AtStep <quest> <step>` | condition | true if state is `active` and the step is `<step>` |
| `SF_QuestDone <quest> [success\|fail\|any]` | condition | true if the quest ended that way (default any) |
| `SF_QuestState <quest> notStarted\|active\|success\|fail` | condition | true if the quest is in that state |
| `SF_AddShip <variant>` / `SF_RemoveShip <variant>` | script | add or remove a ship, with the vanilla "Gained"/"Lost" text |
| `SF_ShowPlanetInfo <entity>` | script | visual panel: planet info (`showPlanetInfo`) |
| `SF_ShowFleets <encounter>` | script | visual panel: the player's and the encounter's fleets (`showFleetInfo`) |
| `SF_ShowShip <variant>` | script | visual panel: one ship (`showFleetMemberInfo`) |
| `SF_Show image\|faction\|ships\|cargo\|heading\|skills <args…>` | script | widgets in the text panel through `TextPanelAPI.addImage` / `beginTooltip()` + `TooltipMakerAPI`: `image <category> <key>`, `faction <id>` (crest, name, relationship bar), `ships <variant…>`, `cargo <item:qty…>`, `heading "<text>"`, `skills <person>` |

A command that gets a missing or unknown id logs an error with the rule id and does nothing.

## Conditions

Phrase cases and `requires` use rules.csv condition lines. SagaLib evaluates them the way the game
does:

- `$key`, `!$key`, `$key == value`, `!=`, `>`, `>=`, `<`, `<=`, with memory keys resolved through
  the memory map (`$global.`, `$player.`, `$faction.`, `$entity.`, `$market.`, `$person.`, local `$x`).
- Anything else is a rule command. Instantiate `com.fs.starfarer.api.impl.campaign.rulecmd.<Name>`
  (or SagaLib's own) and call `execute(ruleId, dialog, Misc.tokenize(args), memoryMap)`. A
  leading `!` negates the line.

## Phrases (conditional text, PLAN.md §4.3)

```json
{"token": "$vgc_reyes_title", "owner": "vgc_reyes", "mode": "first-match|random",
 "cases": [{"when": ["$player.commissionFactionId == vgc_vanguard"], "text": "Commander $playerLastName"}],
 "otherwise": "Captain"}
```

- SagaLib registers one `RuleTokenReplacementGeneratorPlugin` with
  `Global.getSector().getRules().addTokenReplacementGenerator(...)` in **every** `onGameLoad`
  (the list is not saved; docs/FORMATS.md).
- For every known token it returns the text of the first case whose conditions all hold
  (`first-match`), or a random case or otherwise (`random`), or `otherwise`, or `""`.
- Phrase texts may contain other tokens; the game fills those afterwards.
- Tokens come from every quest's `phrases`, people's `phrases` and the mod-wide `phrases`.

## People

```json
{"id": "vgc_reyes", "name": {"first": "Ilse", "last": "Reyes"}, "gender": "FEMALE", "faction": "vgc_vanguard",
 "rank": "spaceCommander", "post": "stationCommander", "portrait": "graphics/portraits/x.png", "voice": "soldier",
 "personality": "steady", "placement": {"at": "market", "market": "vgc_haven", "inCommDirectory": true},
 "contact": {"types": ["military"], "importance": "HIGH"}, "tags": [], "phrases": [Phrase], "description": ""}
```

- On new game and on load, create each person once (`Global.getFactory().createPerson()`, the id
  set with `setId`) and register them with `ImportantPeople` (`addPerson`, so
  `ShowPersonVisual false <id>` works).
- Placement `market`: add the person to the market (`market.addPerson`, comm directory if
  `inCommDirectory`; `getCommDirectory().addPerson`). If the market doesn't exist yet (a new system
  built later), retry after systems are built.
- A person placed at a market with one of vanilla's staff posts (`baseCommander`,
  `stationCommander`, `outpostCommander`, `portmaster`, `supplyOfficer`, `administrator`) takes
  that post over. Vanilla's generated person with the same post at that market is removed, but
  only if vanilla checked them out for nothing but `permanent_staff`, so contacts and other mods'
  people stay. If that person was the market's administrator, the content person becomes it.
- `contact`: make them a potential contact the vanilla way (`ContactIntel.addPotentialContact`
  style, `Tags.CONTACT_*`, importance), so their quests from `person_missions.csv` are offered.
- An existing person with that id (from a save) is updated, not duplicated.
- SagaLib 1.3: `"presence": {"faction": "hegemony", "atLeast": "NEUTRAL", "elsewhere": "vgc_refuge"}`
  (only with placement `market`): present only while the market's owner gets on with a faction.
  See "Presence".

## Factions runtime

On a **new game** only, set the starting relations
(`Global.getSector().getFaction(a).setRelationship(b, value)`). Scripted changes are applied on
**every** load: remove the value from the faction's known lists (`FactionAPI.removeKnownShip`,
`removeKnownWeapon`, …, by path).

SagaLib 1.3 (additive):

- A relation with mod conditions is an object: `"relations": {"vgc_f": {"hegemony": 0.5, "HMI": {"value": -0.5, "modsWith": ["HMI"]}}}`. When its conditions don't hold it is skipped quietly (one info line), so a relation with an optional mod's faction no longer logs "unknown faction".
- A scripted change may carry `modsWith` / `modsWithout` / `modsAny`.
- `{"op": "hideFromIntelTab"}` (no `path` or `value`): `FactionAPI.setShowInIntelTab(false)` on every load, for a faction that only matters with an optional mod (usually with `modsWithout`). The `.faction` file's own `showInIntelTab` can't depend on mods.
- More ops (`showInIntelTab`, `add`, `set` for names, logo, crest, secondary colour and doctrine): see "Versions by installed mods", "Scripted faction changes".
- **`"nexerelin": ["vgc_f", …]`** (task #63): factions whose starting relations the mod also ships in a
  Nexerelin faction config, `data/config/exerelinFactionConfig/<id>.json` (listed in its
  `mod_factions.csv`, with `startRelationships` and `useConfigRelationshipsInNonRandomSector: true`).
  When the mod `nexerelin` is enabled, SagaLib doesn't set those factions' relations **without mod
  conditions**: Nexerelin applies them (in the hand-made and the random sector) and randomizes them
  only when the player picks random relations, unless the config has `noRandomizeRelations`.
  Mods' ModPlugins run in mod order, so SagaLib's `onNewGameAfterEconomyLoad` runs after Nexerelin's
  and would otherwise overwrite them. SagaLib first checks that the mod ships the config file
  (`SettingsAPI.loadText(path, mod)`); if it doesn't, it logs a warning and sets the relations as
  before. Relations with mod conditions aren't in the config and are set by SagaLib as always (after
  Nexerelin, never randomized). Without Nexerelin nothing changes. Logged: "left N starting relations
  of <faction> to Nexerelin (its faction config)". The config itself is data only: SagaLib never reads
  or changes it.

## Save reconciliation (on every load)

For every quest with `$global.<quest>_state == active`:

- step found in the JSON → nothing to do;
- step listed in a stage's `renamedFrom` → set the step to that stage's id;
- step gone → set the step to `__changed`; the intel says "This quest was changed by an update"
  and offers to abandon it;
- quest gone from every mod → end its intel quietly, keep its memory keys.

Log one line per change. Never crash.

## Star systems (`data/sagaforge/systems/<id>.json`)

```json
{"format": 1, "id": "vgc_kestrel", "name": "Kestrel", "kind": "new|existing", "hyperspace": [-5200, -7600],
 "star": {"type": "star_yellow", "radius": 600, "corona": 300}, "addToExistingSaves": false, "tags": [], "music": "",
 "entities": [
   {"id": "vgc_kestrel_i", "kind": "planet|moon", "type": "barren", "name": "Kestrel I", "radius": 120,
    "orbit": {"focus": "star", "radius": 2500, "angle": 30, "days": 160}},
   {"id": "vgc_haven", "kind": "station", "type": "station_side02", "name": "Haven Station",
    "orbit": {"focus": "vgc_kestrel_i", "radius": 400, "angle": 0, "days": 30},
    "market": {"id": "vgc_haven", "name": "Haven Station", "faction": "vgc_vanguard", "size": 5,
               "conditions": ["population_5"], "industries": ["population", "spaceport"],
               "submarkets": ["open_market", "storage"], "freePort": false, "people": ["vgc_reyes"]}},
   {"id": "vgc_kestrel_belt", "kind": "asteroidBelt", "type": "asteroid_belt", "name": "Kestrel Belt", "width": 300, "count": 100,
    "orbit": {"focus": "star", "radius": 4200, "angle": 0, "days": 300}},
   {"id": "vgc_wreck", "kind": "derelict|commRelay|navBuoy|sensorArray|custom|jumpPoint|ring", "type": "wreck", "name": "Convoy wreck", "orbit": {...}}
 ]}
```

- `orbit.focus` is `star` or another entity's id. Angles are in degrees.
- **New system**: built on a new game (in `onNewGame`, like a `SectorGeneratorPlugin`), and on load
  in a save that doesn't have it when `addToExistingSaves` is true.
  - Create the system, initialize the star, place it at `hyperspace` (x, y in game units), and
    tag it `sagaforge:<id>` so it is found again.
  - Add entities in order (focus first): `addPlanet`, `addCustomEntity` with a circular orbit,
    `addAsteroidBelt`, `addRingBand`, jump points. Each entity gets `setCustomDescriptionId(id)`
    so `descriptions.csv` PLANET/CUSTOM rows apply.
  - Then `autogenerateHyperspaceJumpPoints(true, fringe)`: always, because listed jump points get
    their hyperspace side from it too (vanilla does the same); `fringe` is true only when no jump
    point is listed.
  - SagaLib 0.1 details: an asteroid belt or ring is terrain, so its terrain entity gets the JSON id
    with `setId` (markers and spawns find it with `getEntityById`). A ring's `type` is a `misc`
    graphics key (default `rings_dust0`). A derelict's `type` is a variant id, or `wreck` for a
    random independent hull. `commRelay` / `navBuoy` / `sensorArray` default to the vanilla
    `comm_relay` / `nav_buoy` / `sensor_array` types. A custom entity belongs to its `faction`,
    else its market's faction, else neutral. Systems are built in `onNewGame`; people are placed in
    `onNewGameAfterEconomyLoad` and on every load (also those whose market didn't exist before).
- **Existing system** (`kind: existing`, id = the game's system id): add each entity only if
  `getEntityById(id)` is null. Never change what is there. Done on new game and on every load.
- **Markets**: `createMarket(id, name, size)`, then the faction, the primary entity, conditions,
  industries, submarkets and free port. Add it to the economy and set it as the entity's
  market. People listed in `people` are placed there (as in People).
- `music`: the system's music id (memory key `$musicSetId`), if set.

### SagaLib 1.1 additions (all optional; format stays 1)

- **No star**: `"star": null` and `"light": [r, g, b]` on a new system. `initNonStarCenter()`
  and `setLightColor`, as vanilla's black site; `orbit.focus: "star"` is the empty centre.
- **Planet conditions**: a planet or moon without a market may list `"conditions": [...]` and
  `"surveyed": "none|preliminary|full"` (`Misc.initConditionMarket`, as vanilla's uncolonised
  planets).
- **Objects that appear**: an entity with `"appears": {"quest": q, "stage": s}` isn't built with
  its system. It is placed once, when quest `q` is active at stage `s` (checked after `SF_Step`,
  on load and in the manager tick), or by `SF_PlaceEntity <id>`. `"appears": {}` means "only by
  `SF_PlaceEntity`".
  - With `"replaces": "<id>"` it takes that entity's place (focus, current angle, radius, days),
    and that entity is removed.
- **Objects that disappear**: `"removals": [{"target": id, "quest": q, "stage": s,
  "market": "keep|decivilize"}]` on any system file; also `SF_RemoveEntity <id> [decivilize]`.
  - Any entity can be a target (the mod's own, vanilla's, another mod's); it fades out
    (`Misc.fadeAndExpire`).
  - **Guardrails**: a missing target is logged and skipped. An entity with a market is only
    removed with `decivilize` (`DecivTracker.decivilize(market, true, false)`), otherwise it stays
    and an error is logged.
- Memory keys: `$global.sagaforge_placed_<id>`, `$global.sagaforge_removed_<id>`. A placed entity
  is never placed again; a removed one never comes back (additions to existing systems also keep
  `$global.sagaforge_added_<id>`).
- `"loot": []` on entities and rewards is reserved for random content; SagaLib ignores it.
- **Entity flags** (quest JSON): `"entityFlags": [{"entity": id, "flag": "$sagaforge_<quest>_<node>",
  "steps": [stage…], "important": true}]`.
  - While the quest is active at one of `steps`, the entity's memory has `flag = true`; otherwise
    it is unset. With `important`, `Misc.makeImportant(entity, flag)` makes it stand out on the
    map, as vanilla's missions do (`makeUnimportant` when it ends).
  - Recomputed after `SF_Step`, on load and in the manager tick.
  - Saga Forge writes one for each Arrive node: the place the quest waits at.
- A quest `start` of kind `interject` (Saga Forge's "interjection") needs nothing from SagaLib.
  Its rules answer `$option == <option>` before the option's own rule, and hand back to it.

## Encounters (`data/sagaforge/encounters/<id>.json`)

```json
{"format": 1, "id": "vgc_belt_ambush", "name": "Belt ambush",
 "fleet": {"faction": "pirates", "type": "patrolMedium", "size": "fp|explicit", "points": 80, "variants": ["hound_Standard"],
           "quality": 0.5, "flagshipCommander": "vgc_corsair", "name": "Belt Raiders"},
 "spawn": {"at": "vgc_kestrel_belt", "when": "quest-stage|new-game|periodic", "quest": "vgc_lost_convoy", "stage": "find", "despawnOnStageEnd": true, "days": 0},
 "behavior": "patrol|guard|intercept-player|travel|ambush", "travelTo": "",
 "flags": {"hostile": true, "noRepImpact": false, "important": true, "allowDisengage": true},
 "commDialog": "vgc_belt_ambush_comm", "outcomes": {"defeated": "...", "avoided": "..."}}
```

- **Spawn** with `FleetFactoryV3.createFleet(new FleetParamsV3(...))`: faction, fleet type,
  combat points, quality. `size: explicit` builds an empty fleet of the faction and adds the
  listed variants. A `flagshipCommander` (one of the mod's people) commands the flagship. Place
  the fleet at `spawn.at`.
- Set fleet memory `$sagaforge_encounter = <id>`. Comm-link dialog rules are scoped to the fleet
  with the condition `$entity.sagaforge_encounter == <id>`, so other fleets keep vanilla's
  greetings.
- **Flags** map to vanilla's `MemFlags`: hostile = make hostile / aggressive, `noRepImpact`,
  `important` = `Misc.makeImportant`, `allowDisengage`.
- **Behavior** maps to fleet assignments:
  - patrol: patrol the system;
  - guard: defend / orbit `at`;
  - intercept-player: intercept the player's fleet;
  - travel: go to `travelTo`, then despawn;
  - ambush: orbit `at` aggressively.
- **When**:
  - `quest-stage`: spawned by `SF_Spawn`, or when the quest reaches `stage`;
  - `new-game`: spawned once;
  - `periodic`: a manager script keeps one alive, respawning `days` after it is gone.
  - **Named fleets** (SagaLib 1.1): an explicit fleet may carry `shipNames` (parallel to
    `variants`; `""` = the game's), `flagship` (index into `variants`), `template` (an id: each
    defeat adds 1 to `$global.sagaforge_fleet_<template>_defeats`) and `captainDiesOnDefeat`
    (when the fleet is defeated, `flagshipCommander` is retired as by `SF_RemovePerson`). A
    retired (gone) commander isn't used; the fleet's own officer commands.
  - `player-near` (SagaLib 1.1): spawned once when the player's fleet is in the spawn place's
    system and within `spawn.range` map units of it (0 = anywhere in that system), checked four
    times a day. With `spawn.quest` / `spawn.stage`, only while the quest is at that stage, and
    again on a later visit to it.
- `despawnOnStageEnd`: when the quest leaves that stage, the fleet leaves peacefully
  (`SF_Despawn`, outcome *avoided*). A stage renamed since the fleet spawned (the old id is in
  the new stage's `renamedFrom`) counts as the same stage: the fleet stays, and its record moves
  to the new id.
- **Outcomes**. Quest encounters list `defeated` / `avoided` triggers in the quest JSON's
  `encounters`.
  - When the fleet is destroyed or beaten in battle, SagaLib opens a rule-based dialog with the
    `defeated` trigger: `showInteractionDialog(new RuleBasedInteractionDialogPluginImpl(trigger), playerFleet)`,
    or fires it in the post-battle dialog if one is open.
  - When it despawns without being beaten, the same with the `avoided` trigger.
- Quest timers (`timers`, wait N days) use the same mechanism: after `days` at that point, fire
  the trigger. **When a timer starts** (SagaLib 0.1's rule):
  - A timer entry may name the step it waits at: `{"node": "wait1", "trigger": "...", "days": 7,
    "stage": "find"}`. It starts when the active quest is at that step and fires if the quest is
    still there `days` later. Leaving the step first cancels it; coming back starts it again.
    Saga Forge writes `stage` = the stage set just before the Wait node (since task #69; before,
    it wrote none, and the flow also jumped into the timer's rule at once).
  - Without `stage`, it starts at the step the quest is at when SagaLib first sees it active
    (in practice the step set just before the Wait node), runs once per quest run, and is
    cancelled if the step changes first.
  - A quest that isn't active forgets its timers. Days are counted by SagaLib's campaign script
    while the game runs (`$global.sagaforge_days`), checked four times a day.
- Outcome and timer triggers are queued (`$global.sagaforge_pending`) and fired when no dialog is
  open, so a battle's `defeated` trigger opens its own dialog after the post-battle dialog
  closes, and `SF_Despawn` in a comm dialog fires `avoided` after that dialog closes.
- Encounter state is kept in memory keys only: `$global.sagaforge_enc_<id>_state`
  (`spawned|defeated|avoided`), `_quest` / `_stage` (the quest and step it spawned for, used by
  `despawnOnStageEnd`; for `SF_Spawn` that is the quest listing the encounter and its current
  step), `_gone` (day), `_once`. A `quest-stage` encounter with `spawn.quest` spawns again when the
  quest comes back to the stage after leaving it. A fleet beaten in a battle the player won (or
  `wasFleetDefeated`) is `defeated`; any other despawn is `avoided`.
- When SagaLib fires a trigger this way:
  - If no rule matches it, don't open a dialog at all.
  - If the dialog has no options after the rules ran, and they added no text, dismiss it at once.
  - If they added text but no options, add one option "Continue" that closes the dialog.
  - This also covers rules that only run script lines (an Action chain).

## Pools (`data/sagaforge/pools/<id>.json`, SagaLib 1.2)

A weighted list to pick one thing from: a story at a wreck, which fleet waits, a loot table later.
Rules can't pick exactly one by weight (`FireBest` takes the most specific rule,
`RollProbability` rolls each rule on its own), so `SF_RollPool` does.

```json
{
  "format": 1, "id": "vgc_stories", "draw": "pickOne", "nothing": 2,
  "entries": [
    { "id": "pirates", "weight": 3, "conditions": [] },
    { "id": "survivor", "weight": 2, "conditions": [], "once": "playthrough" },
    { "id": "rich", "weight": 4, "conditions": ["$player.credits >= 100000"], "cooldownDays": 30, "max": 3 }
  ]
}
```

| Field | Meaning |
|---|---|
| `draw` | `pickOne` (exactly one by weight, or nothing). `rollEach` is reserved: Saga Forge exports those as plain rules. |
| `nothing` | The weight of "nothing happens" (default 0). |
| `entries[].weight` | Relative weight (3 against 1 is three times as likely); 0 = never. `nothing` is reserved as an id. |
| `entries[].conditions` | Condition lines (as in rules.csv); all must hold for the entry to be eligible. |
| `once` | `playthrough`, or `object`: once per object rolled for (once per playthrough when rolled for the world). |
| `cooldownDays` / `max` | At least that many days between two picks; at most that many picks per playthrough. |

**`SF_RollPool <pool> [object|global]`:**
- Rolled for the dialog's object by default (its salvage seed, `Misc.getSalvageSeed`), else for
  the world (a seed stored once in `$global.sagaforge_seed`). The same object and pool give the
  same pick after a reload.
- Only eligible entries count; if none is (or the roll lands on the nothing share) the pick is
  `nothing`.
- The seed of one draw is `SplitMix64(base ^ (poolId.hashCode() * 0x9E3779B97F4A7C15) + draws *
  0x2545F4914F6CDD1D)`, then `new java.util.Random(seed).nextFloat() * total`, entries in order.
  Saga Forge's Play mode does exactly the same (golden case `test/golden/pools.json`).
- In dev mode, `$global.sagaforge_pool_<pool>_force = <entry>` forces the next pick (test
  profiles).

Memory keys it writes (S = `$entity.` when rolled for an object, else `$global.`):

| Key | What |
|---|---|
| `$sagaforge_pick` | The pick, for the rules that follow (local memory). |
| `$global.sagaforge_pool_<pool>_last` | The last pick. |
| `S.sagaforge_pool_<pool>_draws` | Draws so far (the seed's counter). |
| `$global.sagaforge_pool_<pool>_<entry>_count` | Picks per playthrough (`once`, `max`). |
| `$entity.sagaforge_pool_<pool>_<entry>_done` | Picked for this object (`once: object`). |
| `$global.sagaforge_pool_<pool>_<entry>_day` | The day it was last picked (`$global.sagaforge_days`, for `cooldownDays`). |

Saga Forge's pattern (a Random branch): one rule runs `SF_RollPool <pool>` and `FireBest
<node>_pick`; one rule per entry on that trigger answers `$sagaforge_pick == <entry>`, and one
answers `nothing`.

A mod that uses pools requires SagaLib 1.2 (an older SagaLib doesn't know the command or the
`pool` type).

## Random targets (quest `targets`, SagaLib 1.2)

```json
"targets": [{"var": "ruin", "pick": "system|planet|entity|market", "systemFirst": true,
             "require": [{"c": "plRuins", "args": []}, {"c": "sysWithin", "args": ["giver", 12]}],
             "prefer": [{"c": "plConditions", "args": ["ANY", ["decivilized"]]}]}]
```

- Each criterion `c` is one of vanilla's `HubMissionWithSearch` methods (`core/Targets.CRITERIA`,
  the same ids as Saga Forge's `shared/targets.ts`): `plRuins` → `requirePlanetWithRuins` /
  `preferPlanetWithRuins`. Argument kinds: a ReqMode (`ALL`, `ANY`, `NOT_ALL`, `NOT_ANY`), a list
  of ids, one id, a number, or `giver`/`player` (where a range is measured from). An unknown
  criterion or pick is a load error. `sysSafeStars`, `sysNotUsedForStory` and `mkNotPlayer` exist
  only as require (as prefer they are skipped with a warning).
- **When**: in `SagaQuest.create()`: a contact or bar runs it when offering the quest; any other
  start when the quest starts (its first `SF_Start`/`SF_Step`). Targets are searched in order
  with vanilla's semantics: requires all hold; prefers are applied in order, each dropped when it
  would leave nothing; `systemFirst` = `searchMakeSystemPreferencesMoreImportant(true)`.
  **Nothing found: the mission isn't created** (not offered, or SF_Start/SF_Step doesn't start
  it), and the log says which target found nothing.
- **Stored** (permanent, ``): `<quest>_<var>` = the id (system id, planet or entity id,
  market id); `_name`, `_system` (its star system's name, "" in hyperspace), `_dist`
  (light-years from the giver's market, one decimal; empty without a giver), `_faction` (its
  faction's display name, "" for none). Texts use them as tokens (`$global.<quest>_<var>_name`).
- **Details** (RC-R3, Booleans unless noted): `_deciv`, `_hasRuins`, `_ruins` ("" or
  scattered/widespread/extensive/vast), `_unexploredRuins`, `_populated`, `_gasGiant`, `_theme` (the
  system's theme_* tags, comma-separated, for texts), `_ruinsTheme`, `_derelictTheme`,
  `_remnantTheme`, `_miscTheme`, `_blackHole`, `_pulsar`, `_nebula`, `_hostile` (its faction is
  hostile to the player), `_unexplored` (the player never entered its system); `_factionId`.
- **A person there** (RC-R4): `"person": {"posts": ["portmaster"], "rank": "citizen", "faction": ""}`
  on a market, planet or entity pick: vanilla's `findOrCreatePerson` at its colony (someone with
  one of the posts, else a new person of the faction or the colony's, removed when the mission
  ends). `_person` = their id, `_personName`. A pick without a colony isn't offered.
- **Spawned there** (RC-R2): `SF_SpawnAtTarget <quest> <var> <entity|derelict> <type> [where]` uses
  vanilla's mission spawning (`spawnEntity` / `spawnDerelict` with `LocData`, removed when the
  mission ends): a custom entity type or a faction's derelict ship, `where` = vanilla's
  `EntityLocationType` (default `ORBITING_PARAM`: around the target). Once per quest; its id is
  `_spawned`, marked on the map.
- A stage `marker`, `arrivals` and `entityFlags` may also be `$global.<quest>_<var>_person` or
  `_spawned`. An encounter's `spawn.at` may be `$global.<quest>_<var>` (the fleet appears at the
  pick).
- A re-offer searches again (and forgets the last person and spawn); an active quest keeps its
  pick.

A mod that uses targets requires SagaLib 1.2.

## Phase 7e additions (SagaLib 1.2)

- **Encounter pools**: a pool with `"use": "encounters"`, `"points": ["slipstream", "outside_system"]`
  and `"frequency": 10`. Its entries are encounter ids. SagaLib adds one creator to vanilla's
  `EncounterManager.CREATORS` (an `EPEncounterCreator`): at vanilla's encounter points of those
  types, its frequency is the sum of the frequencies of pools with an eligible entry (vanilla's own
  creators use about 10). When vanilla picks it, a pool is picked by frequency, then an entry
  (`core/Pools`, with its conditions and limits), and that encounter's fleet lies in wait near the
  point (`ORBIT_AGGRESSIVE`, vanilla's `MissionFleetAutoDespawn`). Its comm dialog works; it has no
  quest outcome. Its own `spawn` settings are ignored. `"use": "branch"` (the default) is
  `SF_RollPool`'s.
- **Salvage loot and defenders** on a system entity: `"loot": [{"group": "<drop group>", "chances": 3}]`
  (`addDropRandom`) or `{"group": …, "value": 5000}` (`addDropValue`); `"defenders": {"faction":
  "pirates", "prob": 1, "minStr": 10, "maxStr": 30, "maxSize": 4}` (`Misc.setDefenderOverride` with a
  `DefenderDataOverride`). Drop groups are vanilla's `drop_groups.csv` (Saga Forge writes the mod's
  own groups there).
- **`"ruinsUnexplored": true`** on a planet with ruins: at new game (after vanilla marked ruins in a
  system with a colony explored, `onNewGameAfterTimePass`) its `$ruinsExplored` is cleared and its
  own `surveyed` level put back.
- **Reward loot** (Phase 8a): a quest reward may have `"loot": [{"group": "rare_tech", "chances": 3}]`
  or `{"group": …, "value": 3000}`: vanilla's salvage roller (`SalvageEntity.generateSalvage`) on
  that drop group; the items go to the player with vanilla's "Gained …" lines (after `items`).
- **`"protectFromDeciv": true`** on a market: vanilla's `$core_noDeciv` (as pirate and Pather
  bases), so the colony never decivilises.
- **Decivilised colonies**: a transient `ColonyDecivListener` sets
  `$global.sagaforge_deciv_<market id>` = true when any colony decivilises.
- **Scatter** (`data/sagaforge/scatter/<id>.json`, content type `scatter`):
  `{"format": 1, "id", "count", "addToExistingSaves", "target": {pick, require, prefer, systemFirst},
  "entity": {"type", "name", "faction"}, "loot", "defenders"}`. Placed once per game after the
  economy loads (an existing save on load, with `addToExistingSaves`): `count` times vanilla's
  search (a throwaway `SagaQuest` as the search only; ranges measured from `"from"`, an entity or
  market id, else from the player), each copy
  in a different system, as a custom entity `<id>_<n>` orbiting the pick (a system pick: its
  centre), tagged `NOT_RANDOM_MISSION_TARGET`, with the loot and defenders.
  `$global.sagaforge_scatter_<id>_done` = how many were placed.

## Progress events (SagaLib 1.2)

`data/sagaforge/progress/<id>.json`, type `progress`: the mod's own event bar, like vanilla's colony
crises and Hyperspace Topography (vanilla's BaseEventIntel: progress bar, stage markers, monthly and
one-time factors, an intel update per stage).

```json
{ "format": 1, "id": "vgc_unrest", "name": "Unrest in Kestrel", "icon": "", "tag": "", "max": 1000,
  "negative": true, "startConditions": ["$global.vgc_war"], "restartAt": 0,
  "stages": [ { "id": "rumours", "progress": 300, "title": "Rumours", "text": "…", "icon": "" },
              { "id": "riots", "progress": 600, "title": "Riots", "text": "…",
                "outcomes": [ { "id": "calm", "weight": 2 }, { "id": "burn", "weight": 1 } ] } ],
  "factors": [ { "id": "war", "desc": "The war", "points": 100, "conditions": ["$global.vgc_war"] } ] }
```

- It **starts** when its `startConditions` hold (checked four times a day; empty: at once) or at the
  first **`SF_Progress <event> <points> ["text"]`** (one-time points, listed with the text for 30
  days like vanilla's; negative lowers it). One instance per event, in sector memory.
- **Monthly factors**: `points` per month while their conditions hold (vanilla's economy ticks).
- **Stages** (at most 20, each above the one before): when the bar reaches one, the rules on
  **`SF_progress_<event>_<stage>`** run (quietly, like world events); with `outcomes` one is picked by
  weight and **`SF_progress_<event>_<stage>_<outcome>`** runs instead. Memory:
  `$global.<event>_stage` (the last stage reached), `$global.<event>_<stage>_outcome`.
- **After the last stage**: with `restartAt` the bar goes back to it (the stages repeat); without,
  the event ends and isn't started again (`$global.sagaforge_pe_<event>_done`).
- `negative`: a red bar, as for crises. `tag`: another intel tab besides "Major events".
- Saved classes (names never change): `lastai.sagalib.game.SagaProgressEvent` (keeps the event id;
  the definition is read again after loading, so changed thresholds and new factors apply),
  `SagaMonthlyFactor`, `SagaOneTimeFactor`, and the stage slots `SagaStage` S0..S20 (vanilla needs
  enum stage ids; S0 is the start at 0).

## Roaming fleets (SagaLib 1.2)

An encounter with `"spawn": { "at": "<from>", "when": "roaming", "to": "<entity or market>", "days": 30,
"max": 2, "stayDays": 3, "roundTrip": true }` travels on **vanilla's routes** (RouteManager), like
traders and patrols: a route per departure (source `sagalib_roam_<encounter>`) moves on its own
(getting ready 1 day, travel, a stay of `stayDays`, and back with `roundTrip`), and a real fleet
(the encounter's fleet and flags, moved by vanilla's RouteFleetAssignmentAI) exists only while the
player is near it (vanilla: within about 1.6 LY; gone when far). A new one leaves about every
`days` (×0.75–1.25; the first one spread over one interval), never while `max` are on the way
(`$global.sagaforge_roam_<encounter>_next`). Behavior and outcomes don't apply. Routes keep
`lastai.sagalib.encounters.SagaRouteSpawner` (saved: the name never changes; no fields) and the
encounter id; a route whose encounter is gone ends.

## World events (SagaLib 1.2)

`data/sagaforge/events/<id>.json`, type `events`: an **event scheduler**.

```json
{ "format": 1, "id": "vgc_news", "name": "Sector news", "min": 0, "max": 2, "intervalDays": 30, "firstAfterDays": 10,
  "entries": [
    { "id": "vgc_boom", "weight": 2, "conditions": ["$global.vgc_peace"], "cooldownDays": 60, "durationDays": 20,
      "news": { "title": "A boom at Haven", "text": "…", "icon": "", "tag": "Major events", "at": "vgc_haven", "days": 30 } },
    { "id": "vgc_raid", "weight": 1, "once": "playthrough", "durationDays": 10 } ] }
```

- **entries** are pool entries (weight, conditions, `once`, `max`, `cooldownDays`: the picker of
  SF_RollPool, with the pool keys of "Pools" under the scheduler's id; a scheduler and a pool must
  not share an id) plus `durationDays` (0: starts and ends at once), `stack` and an optional `news`
  item. **One run at a time** by default: a running event isn't picked, and when it comes back its
  news item replaces the older one still listed. `"stack": true` lets it start while it runs (each run
  counts toward max, has its own news item and ends on its own; `.<event>_active` stays true
  while any runs; the end days are kept as a list in `.sagaforge_ev_<event>_until`).
- **Each tick** (four times a day) SagaLib ends the events whose days are over, and when a check
  is due starts one (weighted pick among the events whose conditions hold and that aren't
  running or used up) if fewer than `max` run. Next check: `intervalDays` ×0.75–1.25 later, or a
  day later while fewer than `min` run. The first check is `firstAfterDays` (or one interval) after
  SagaLib first sees the scheduler.
- **State, all in global memory** (no saved scheduler class): `$global.sagaforge_ev_<scheduler>_next`
  (day of the next check), `$global.<event>_active` (true while it runs: use it in conditions and
  dialog), `$global.sagaforge_ev_<event>_until`.
- **Start and end**: the rules on the triggers **`SF_event_<event>_start`** and
  **`SF_event_<event>_end`** run through the trigger queue outside any dialog (like quest timers;
  Saga Forge writes them from the event's actions). They run **quietly**: text without options
  ("Gained 100¢") closes at once; a rule that adds options opens its dialog. The **news item** is a
  `lastai.sagalib.game.SagaNewsIntel` (saved; strings only): title, text, icon, intel tag (default
  "Major events"), a place on the map, removed after `days`. Its title and text are translatable.
- **Dev mode**: `$global.sagaforge_ev_<scheduler>_force = <event>` starts that event at the next
  tick (set by a test profile).

## Translations (SagaLib 1.2)

A translation is a mod of its own that ships one CSV; the translated mod is never copied or changed.

- **The mod's texts** (`data/sagaforge/text.json`, type `strings`): `{"format": 1, "rules": {<rule id>:
  {<part>: text}}, "items": {<id>: {"name", "desc"}}, "descriptions": {"<id>|<TYPE>": {"text1", "text2",
  "text3"}}, "factions": {<id>: {"displayName", "displayNameWithArticle"}}}`. Saga Forge exports a
  rule's player-visible texts as tokens **`$sft-<rule id>-<part>-`** in rules.csv (parts: `t` or
  `t1`, `t2`… for OR variants, `o_<option id>`, `tip_<option id>`, `a<n>` AddText/AddTextSmall/
  heading, `h<n>` highlights, `bar_o`/`bar_b`, `raid<n>`); the token generator **TextTokens** fills
  them: a rule's text and script lines for that rule only (the game passes its id); **option labels,
  tooltips, bar scene options and `g_` parts for every rule** (the game passes the calling rule's id,
  or none, for options, and another id for raid objective tooltips and story option log texts). Tokens inside a text are filled through the game's own replacement, and every value is
  quoted (`Matcher.quoteReplacement`): the game uses it as a regex replacement. Items' names and
  descriptions are set on application load, faction names on every game load, **only where a
  translation changed them** (the CSV and .faction files hold the originals).
- **A translation**: a content.csv row `<mod id>/<language>,translation,<path>,<translation mod id>`
  and a CSV with columns `key, context, original, translation, source` (only key, translation and
  source are read). **Keys**: the content id and the path in the file, with an array element's
  `id` (else its `token`, else its position): `vgc_lost_convoy/stages[find].journal`,
  `vgc_text/rules.vgc_lost_convoy_offer.t`. **source**: the FNV-1a fingerprint (32 bit, UTF-16 code
  units, 8 hex digits) of the original the line was made for. A line is used only when its
  translation isn't empty and its source matches the current original (empty source: always), so a
  changed original shows itself instead of an outdated translation. Keys and fingerprints are pinned
  by `test/textkeys.json`, shared with Saga Forge.
- **Applied when content loads**: the translation replaces texts in the mod's JSON before it is
  read, so quests, people, phrases, systems, encounters and scatter see the translated text.
  One translation per mod: with two enabled, the first listed is used and the other is reported.
- **Names the save keeps** (every load, `TranslatedTexts.applySave`): the mod's new systems (and
  their star), places and markets get the current language's name while the name is still the one
  SagaLib gave (`$sagaforge_name` in the object's memory, or an original text); a colony the player
  renamed keeps its name. People are renamed on every load anyway. Faction name overrides are set,
  and taken back when the translation is disabled. Not renamed: scatter objects, jump points named
  after a system, vanilla systems.

## Dev tools (only when `Global.getSettings().isDevMode()`)

- **New game**: after the sector is generated, if any profile exists, show a dialog listing them
  (and "none") and apply the chosen one.
- **Dev menu** (a `CampaignInputListener`, re-added on load, default key F9 with Ctrl). It
  lists, applies a profile, jumps a quest to a step, and **reloads** the JSON (quests, people,
  phrases, profiles) from disk without restarting. SagaLib 1.3: **Mods** shows each content mod's
  list with the start record and what is enabled now, and every item skipped for its mod
  conditions, with why. **Versions** and **Write sector snapshot**: see "Versions by installed
  mods". Reload only re-reads definitions; built
  systems and spawned fleets are not rebuilt.
- Applying a profile writes the quests' state/step/variables, flags, credits, story points,
  commission, reputation, cargo, and moves the player fleet to `startAt` (an entity or market id;
  before 1.2 only at a new game; from the dev menu, the menu then closes). The menu says it
  changes the running save. Test mods use this to take the player to their testing area.

## Other mods and mod conditions (SagaLib 1.3)

The game has no optional dependency (Saga Forge FORMATS.md, "mod_info.json and dependencies"), so a
content mod lists the other mods it has content for in `data/sagaforge/mods.json` (type `mods` in
content.csv, one per mod):

```json
{ "format": 1, "mod": "vgc", "name": "Vanguard Compact", "prefix": "vgc_",
  "mods": [ { "id": "HMI", "name": "Hazard Mining Incorporated", "use": "optional" },
            { "id": "crewr", "name": "Crew Replacer", "use": "incompatible", "reason": "both change crew" },
            { "id": "lw_lazylib", "name": "LazyLib", "use": "required", "minVersion": "3.0" } ] }
```

`use` is `required` (in `mod_info.json` dependencies too; SagaLib only shows it in the dev menu),
`optional` or `incompatible`. `mod` must be the mod that ships the file. `prefix` is the mod's id
prefix: `SF_HasMod` in a rule whose id starts with it reads that mod's start record.

**The start record** (saved): the first time SagaLib sees a content mod in a save (at a new game,
in `onNewGame` before anything is built; or when the mod is added to a running game; or when an
update lists a new mod), it records for each of its optional and incompatible mods whether it is
enabled: `$global.sagaforge_mod_<owner>_<mod>` = `"on"` | `"off"`. It never changes afterwards.

**A mod counts** for a content mod when it is enabled now **and** its record isn't `"off"`. A mod
the record doesn't know (not listed as optional or incompatible, e.g. a required one) counts when
it is enabled. Without an owner (a condition in a phrase case or a pool entry), a mod counts when
it is enabled and no list that records it says `"off"`. So a mod added to a running game changes
nothing, and a removed one stops what depends on it (what already exists stays; SagaLib doesn't
clean up).

**Mod conditions** on content (all additive, all optional):

| Key | Meaning |
|---|---|
| `modsWith` | every one of these mods counts |
| `modsWithout` | none of these mods counts |
| `modsAny` | a list of lists: in each, at least one mod counts |

They are read on quests (never offered or started otherwise: `SagaQuest.create` and `SF_Start`
return false), people (not created or updated; also not when a market's `people` lists them),
systems and each of their entities (not built: for an existing system, its additions), encounters
(not spawned, also from encounter pools), roaming fleets, event schedulers, progress events,
pools (`SF_RollPool` picks `nothing`; an encounter pool isn't rolled), scatter, relations and
scripted faction changes. A skipped item logs one info line per game: `skipped quest vgc_x of vgc:
needs HMI (not enabled when this game started)`. Single world events and pool entries use condition
lines (`SF_HasMod` …) in their `conditions`, and dialog uses the rule commands.

**Notices.** On every load, after the record, SagaLib compares it with the enabled mods and logs
each difference as a warning (`[SagaLib] mods: …`). A difference not told in this save before is
shown once in a small dialog when the campaign is on screen; `$global.sagaforge_modtold_<owner>_<mod>`
remembers what was told (`added`, `removed`, `incompatible`; unset again when it no longer
applies, so a later change is told again):

- an incompatible mod enabled: "<mod>: the author marks <other> as possibly conflicting: <reason>"
- an optional mod added since the start: "<mod> was set up for the mods this game started with.
  <other> was added since, so <mod> plays on without its <other> content. Start a new game to get it."
- an optional mod removed since the start: "<other> was removed after this game started. The author
  of <mod> didn't plan for that, so things may break."

The shared golden case `test/golden/mods.json` (also run by Saga Forge's Play mode) covers the
record, `SF_HasMod` / `SF_HasAnyMod`, the gates and the notices. Its extra keys: `modLists`
(mods.json contents), `enabled` (the enabled mods), and the steps `{"enabled": [...]}`,
`{"record": true, "notices": ["owner:mod:what", …]}` (the fresh notices) and
`{"gate": {...}, "owner": "<mod>", "is": true|false}` (whether the conditions hold); a `check`
step may name its `owner`.

## Versions by installed mods (SagaLib 1.3)

(Saga Forge PLAN.md §10.6.) One item can have **versions** for combinations of the optional
mods: the same id, with some fields changed when those mods count. SagaLib picks each item's
version **once per save**, from the start record (above), and keeps it.

**Where.** A `versions` list may be on: a quest file, each person of a people file, a system
file, an encounter file, a pool file, an event scheduler file, a progress event file, a scatter
file and a `factions.json` file (the whole file: starting relations and scripted changes).

```json
{ "id": "sfa_reyes", "name": { "first": "Ilse", "last": "Reyes" }, "portrait": "graphics/a.png",
  "versions": [
    { "id": "mod1", "modsWith": ["sfj"], "set": { "placement.market": "sfj_port" } },
    { "id": "mod2", "modsWith": ["sfb"], "set": { "name.last": "Okafor", "portrait": "graphics/b.png" } }
  ] }
```

- A version holds when its `modsWith` / `modsWithout` / `modsAny` hold (the same keys and meaning
  as mod conditions). `id` is a label (the dev menu, translation keys); it should be unique in the
  list. `set` maps a **path** to a value.
- **Paths**: segments joined by `.`; a segment is an object key, or `key[x]`: the element of the
  array `key` whose `"id"` (else, for an element without an id, `"token"`) is `x` (the same as
  translation keys; an element with neither isn't reachable). Setting a path creates missing
  objects on the way. A value `null` **removes** the key (or the array element); removing
  something that isn't there does nothing. A path that can't be followed (not an object, no
  element with that id) is an error for that path only (logged once, at load), and the rest of
  the version still applies. The path `id` can't be set. The paths of one version are applied in
  sorted order (Java `String` order, so a parent before its children): the game's JSON keeps no
  key order.
- **Resolution** for a set of mods that count: take the versions that hold, sort them by
  specificity (`modsWith` + `modsWithout` + `modsAny` entries; stable, so equal ones keep their
  order in the file), and apply each one's `set` in that order to a copy of the item without
  `versions`. So the most specific version wins, field by field, and a field no version sets
  keeps the base value.
- **Conflict**: two versions that hold with the same specificity set the same path to different
  values, and no more specific version that holds sets it. The later one in the file wins, and
  SagaLib logs one warning per item and path (at load, for any combination). (Saga Forge asks
  for the value in the combined version, so its exports have none.)
- **Mods an item mentions** = every mod named by its versions. At most 6 (an item with more
  logs an error and uses its base). At load SagaLib parses every combination (at most 64) as a
  normal item. A problem that only a combination has is logged once, ending in
  `(in version <key>)`; a combination that can't be read at all uses the base.
- **Base** = the item resolved with no mod counting, so a `modsWithout` version holds in it.
- **Choosing** (new game: in `onNewGame`, right after the start record and before systems are
  built; every load: right after the record): for each item not chosen yet in this save, its
  version = the mods it mentions that count for its owner (`Mods.counts`, the start record), stored
  as `$global.sagaforge_ver_<kind>_<id>` = `"base"` or the mod ids sorted (Java `String`
  order) and joined with `+` (`"sfb+sfj"`). `<kind>` ∈ `quest`, `person`, `system`, `encounter`,
  `pool`, `events`, `progress`, `scatter`, `factions` (`<id>` = the owner mod's id). **A stored
  choice never changes**: a mod added or removed later, or an update that adds versions for
  another mod, doesn't switch it (a stored mod the item no longer mentions is dropped when the
  version is used; the stored key itself isn't rewritten). An item first seen in a running save
  (added by an update) is chosen from the same record. The dev menu's reload chooses and applies
  again too.
- For **quests** SagaLib also sets `$global.sfv_<questId>_<mod>` = `true` for each mod of the
  chosen version (characters of a mod id other than letters, digits and `_` become `_`). The
  exported dialog tests these: a node a version changes gets an extra rule with these conditions,
  which beats the base rule by having more conditions.
- **Using it**: after choosing, every definition SagaLib uses (quests, people and their phrases,
  systems, encounters, pools, events, progress, scatter, factions) is the chosen version, for the
  whole session of that save. Another save loaded later is chosen and applied again (every item
  is put back from its parsed versions, so nothing carries over). Progress events already in the
  save take their chosen definition right after.

**Item names, descriptions, icons and prices** (`data/sagaforge/text.json`) follow the **installed**
mods instead: they are set at application load, before any save. The file may have a `versions`
list over its own paths: `items.<id>.name`, `items.<id>.desc`, `items.<id>.icon`,
`items.<id>.basePrice` (a number), `descriptions.<id>|<TYPE>.text1` / `text2` / `text3`. It is
resolved with the mods **enabled now** and applied like a translation (`setName`, `setDesc`,
`setIconName`, `setBasePrice` on the commodity or special item spec; `Description.setText1…3`).
Only values that differ from the file's base are set; an icon is loaded first. `desc` is only
for special items (a commodity's text is `descriptions.<id>|RESOURCE.text1`). Another path, or a
`basePrice` that isn't a number, is an error at load and never applied. A translation of the mod
translates the versions' texts too (their keys are `<id>/versions[<version id>].set.<path>`).

**Scripted faction changes** (SagaLib 1.3 additions, applied on every load like the others):

| Change | What SagaLib calls |
|---|---|
| `{"op": "set", "path": "displayName", "value": "…"}` | `setDisplayNameOverride` (also `displayNameWithArticle` → `setDisplayNameWithArticleOverride`) |
| `{"op": "set", "path": "logo" \| "crest", "value": "<sprite path>"}` | `setFactionLogoOverride` / `setFactionCrestOverride` (the sprite is loaded first) |
| `{"op": "set", "path": "secondaryColor", "value": "r,g,b,a"}` | `setSecondaryColorOverride` (0–255 each; alpha optional, default 255) |
| `{"op": "set", "path": "doctrine.<field>", "value": "3"}` | the faction's `FactionDoctrineAPI` setter: `warships`, `carriers`, `phaseShips`, `officerQuality`, `shipQuality` (0–5), `numShips`, `shipSize`, `aggression` (1–5), all whole numbers; `combatFreighterProbability` (0–1) |
| `{"op": "add", "path": "knownShips.hulls", "value": "<hull>"}` | `addKnownShip(hull, false)` if not known; `knownWeapons.weapons`, `knownFighters.fighters` likewise |
| `{"op": "add" \| "remove", "path": "illegalCommodities", "value": "<commodity>"}` | `makeCommodityIllegal` (if not illegal) / `makeCommodityLegal` |
| `{"op": "showInIntelTab"}` | `setShowInIntelTab(true)` (the opposite of `hideFromIntelTab`) |

There is no `displayNameLong`: the game (0.98a) has no setter for it, so that path is an error.
Every op, path and value is checked at load; a bad one is an error with its JSON path and the
change is skipped. Since 1.3 this includes `remove` with a list SagaLib can't change (before, it
was an error on every load).

**Sector snapshot** (dev mode only): the dev menu's **Write sector snapshot** writes
`Global.getSettings().writeTextFileToCommon("sagaforge_snapshot.json", …)` (the game adds `.data`
to the file name in `saves/common/`). Saga Forge imports it as a source "seen in game", so places,
markets and people that only exist in a mod's code become pickable and checked:

```json
{ "format": 1, "sagalib": "1.3.0", "gameVersion": "0.98a-RC8", "day": "c.206.3.15",
  "mods": [ { "id": "sfj", "name": "Saga Forge 64 Java stand-in", "version": "1.0.0" } ],
  "systems": [ { "id": "sfj_haven", "name": "Haven", "x": -4000, "y": 2500 } ],
  "places":  [ { "id": "sfj_port", "name": "Haven Port", "system": "sfj_haven", "kind": "station", "type": "station_side00", "faction": "independent", "market": "sfj_port_market" } ],
  "markets": [ { "id": "sfj_port_market", "entity": "sfj_port", "name": "Haven Port", "faction": "independent", "size": 4, "system": "sfj_haven" } ],
  "people":  [ { "id": "sfj_harbor", "first": "Mara", "last": "Quell", "faction": "independent", "market": "sfj_port_market", "post": "portmaster", "rank": "citizen", "portrait": "graphics/portraits/portrait_mercenary02.png" } ] }
```

Places are the systems' entities that have an id and aren't fleets (planets, stars, stations,
custom entities, jump points; asteroids, terrain and rings are left out). People are everyone in
`ImportantPeopleAPI` plus each market's people list, by id. `kind` is `star`, `planet`,
`station` (a custom entity tagged `station`), `jumpPoint` or `custom`. `type` is the planet type
or the custom entity type (none for jump points). A place's `market` is set only for a real
market (not a planet's condition-only market). A person's `market` is the first market that
lists them, else `PersonAPI.getMarket()`. Systems' `x`/`y` are their hyperspace location,
rounded. Key order in the file isn't kept (the game's JSON).

**Dev menu**: **Versions** lists every versioned item with its kind, owner, the mods it
mentions, the chosen version (or "not chosen yet") and the version in use, plus each text.json
with versions and the version used for the mods enabled at startup.

The shared golden case `test/golden/versions.json` covers resolution (2 optional mods, all 4
combinations, inheritance, a conflict), choosing at new game, and that a mod added later changes
nothing. Its extra keys: `versioned` (`[{ "kind", "owner", "item" }]`: items in their file
format with `versions`; a `factions` item is the whole file) and the steps `{"newGame": true}`
(an empty save), `{"choose": true}` (choose every item not chosen yet, after a `record`),
`{"resolve": "<kind>:<id>", "on": [...], "is": {…}, "conflicts": ["<path>", …]}` (the item
resolved for those mods, without `versions`, and its conflicting paths, sorted) and
`{"chosen": "<kind>:<id>", "is": {…}}` (the item as the save uses it).

## Places a quest needs, markets changing owner (SagaLib 1.3, task #61)

No bridge: Nexerelin is never loaded or named in code. Everything below works the same without it
(owners then change only through other events and mods). Nexerelin's random sector is not supported
yet (on hold, its own task).

**Places a quest needs.** A quest is never offered or started (`SagaQuest.create` and `SF_Start`
return false, as vanilla's missions do when their search finds nothing) while one of these doesn't
exist: its contact (`start.person` of a `contact` or `bar` start; an important person), a place
named by a `$id == <id>` line of `start.conditions`, a stage `marker`, or an `arrivals` entry (an
entity, a market or a star system). Random targets (`$global.…` keys) and the mod's own objects
that appear during a quest (`appears`) are left out. It logs once per game: `skipped quest vgc_x:
needs place jangala, which doesn't exist in this game`. So a quest can't break halfway when a mod it
names is gone, or a place it needs was removed.

**Markets changing owner.** A quest may say what happens when one of its markets changes owner
(an invasion, a rebellion, a sale, any mod):

```json
"ownerChange": { "do": "stage", "stage": "lost", "trigger": "vgc_q_ownerChanged",
                 "markets": ["jangala", "$global.vgc_q_target"] }
```

- `do`: `keep` (keeps going), `fail` (the quest fails) or `stage` (the quest moves to `stage`, which
  must be one of its stages; a success or fail stage ends it). `trigger` (optional, with `stage`):
  fired afterwards (queued until no dialog is open, like a timer), so the rules can run what follows.
- `markets`: entity or market ids, or a random target's key (its picked id is read). When the quest
  starts, SagaLib remembers each one's owner, plus the owner of the mission giver's market:
  `$global.sagaforge_owners_<quest>` = `"jangala=hegemony;vgc_station=independent"`. A target picked
  later is remembered the first time it is seen.
- Once a day (the runtime script), for every active quest with `ownerChange`: the first market
  whose faction differs from the remembered one sets `$global.<quest>_ownerChanged` (the market id)
  and `$global.<quest>_newOwner` (the faction id), the new owner is remembered, and `do` happens.
  A market that no longer exists is left as remembered. Every `do` sets the two keys, so dialog and
  a Branch can react even with `keep`.

**Protection from invasion.** A market of a system file may say `"protectFromInvasion": true`: SagaLib
sets `$nex_uninvadable` = true in its memory when it builds it and on every load (it never unsets
it). Nexerelin's `NexUtilsMarket.canBeInvaded` is false while it is true, for every invasion, the
player's too. Without Nexerelin it does nothing.

**Dev menu › Change a market's owner**: the markets active quests watch (with their remembered
owners) and the content mods' own markets; pick one, then a faction. It sets the market's and its
entities' faction (without Nexerelin's side effects: no fleets, people or reputation) and checks
the quests at once instead of waiting a day.

Golden case `test/golden/places.json` (also run by Saga Forge's Play mode). Its steps:
`{"world": {"places": [...], "people": [...], "owners": {market: faction}}}` (the sector from then
on; without a world step everything exists), `{"needs": "<quest>", "is": {"places": [...], "people":
[...]}}`, `{"offer": "<quest>", "is": true|false, "missing": "place x"}` (the mod and sector gate,
not active, nothing missing), and `{"day": true, "changes": ["<quest>:<market>-><owner>:<do>[:<stage>:<trigger>]"]}`.

## Presence: people present only while their market's owner gets on with a faction (SagaLib 1.3, task #70)

A person placed at a market may be loyal to a faction, for example a Hegemony loyalist at a
station who leaves when the station is held by someone worse than Neutral toward the Hegemony:

```json
"presence": { "faction": "hegemony", "atLeast": "NEUTRAL", "elsewhere": "vgc_refuge" }
```

- `faction`: the faction they're loyal to (default: their own `faction`). `atLeast`: a RepLevel name
  (`VENGEFUL`, `HOSTILE`, `INHOSPITABLE`, `SUSPICIOUS`, `NEUTRAL`, `FAVORABLE`, `WELCOMING`,
  `FRIENDLY`, `COOPERATIVE`; default `NEUTRAL`) or `notHostile` (`INHOSPITABLE` or better, the
  opposite of `FactionAPI.isHostileTo`). `elsewhere` (optional): a market they go to while away.
- They are present while the owner of their market (`MarketAPI.getFactionId`) is at least
  `atLeast` with `faction` (`FactionAPI.getRelationshipLevel`). A faction with itself counts as
  `COOPERATIVE`. So both a new owner and a change of relations count: a war breaking out (a
  Nexerelin war, a vanilla event, a mod) makes them leave, peace brings them back.
- Checked once a day (the runtime script, with "Markets changing owner"), on every load and when
  the dev menu changes an owner or a relation. It sets `$global.sagaforge_away_<person>` to true
  or false. A person whose market doesn't exist (any more) keeps their flag. Each leave and return
  is logged: `person vgc_x leaves vgc_station: its owner pirates is below NEUTRAL with hegemony`.
- **Away** means: their entry in the market's comm directory is hidden
  (`CommDirectoryEntryAPI.setHidden(true)`; they stay at the market and in the important people,
  so saves are safe and it is reversible), quests with them as contact (`contact` or `bar` start
  with that `person`) are not offered (`SagaQuest.create` returns false, logged once per game:
  `skipped quest vgc_q: its contact vgc_x is away`), and dialog can test the flag. With
  `elsewhere`, they are listed in that market's comm directory while away (only a comm-directory
  entry: their own market stays their home), but their quests still aren't offered. When a contact
  leaves, their mission hub is replaced by a fresh `BaseMissionHub` (vanilla keeps offers it already
  created until its next update), so an offer seen before they left isn't shown elsewhere.
- An active quest isn't touched when its contact leaves. Use "Markets changing owner" to fail it or
  move it to a stage, or test the flag in its dialog.
- A `presence` with an unknown level, or on a person not placed at a market, is an error and ignored.

**Dev menu**: "Change a market's owner" also lists the markets of people with a presence rule and
checks presence at once. "War or peace" (shown when a content mod has a presence rule) sets the
relation between such a person's market owner and their faction to Hostile (-0.6) or Neutral (0).

Golden case `test/golden/presence.json` (also run by Saga Forge's Play mode). Besides the steps of
`places.json`, it has top-level `"people"` (in this file's format), a world may give `"factions"`
(the ones that exist; others make `SF_FactionRep` false) and `"relations": {"a|b": "LEVEL"}` (both
ways; unset = `NEUTRAL`), `{"presence": true, "moves": ["<person>:leaves|back:<owner>"]}` runs the
check, and `"offer"` steps may say `"away": "<person>"`.

## MagicLib bounties (SagaLib 1.3, task #59)

A quest can start a MagicLib bounty and test its state. MagicLib is never a dependency of SagaLib:
the bridge (`src-optional/magiclib`, compiled against MagicLib 1.5.6's jar into the same
`jars/SagaLib.jar`) is loaded by class name only when MagicLib is enabled (`game/MagicBounties`),
and nothing SagaLib always loads names a MagicLib class. Without MagicLib every bounty is
`unavailable` and `SF_StartBounty` does nothing but log why. A content mod that uses these
commands lists MagicLib in its `mods.json` (optional by default; Saga Forge adds it only then).

| Command | Use | Effect |
|---|---|---|
| `SF_StartBounty <bounty>` | script | The player takes the bounty, as if accepted on MagicLib's board: MagicLib creates it if its board hasn't yet (`createActiveBounty`: the place is picked and the fleet spawned), then `acceptBounty` with the scaled credit reward, `job_reputation_reward` and `job_forFaction`; the dialog gets MagicLib's own "Accepted job: <name>" line (`mb_accepted`). Only when the bounty is `offered`; a bounty already taken or ended stays as it is. |
| `SF_BountyState <bounty> <state> [more states…]` | condition | The bounty is in one of these states (below). Negate with `!`. |

**States**, read from the memory keys MagicLib itself sets (the bounty's `job_memKey`, `$<id>` by
default, is `$global.<id>` in rules): `unavailable` (MagicLib doesn't count for the content mod,
the bridge is missing, or MagicLib doesn't have the bounty: not in any enabled mod's file, invalid,
or its `required_mods_id` are missing), `offered` (MagicLib has it and nobody took it; whether the
board lists it right now depends on where the player is), `active` (the key is false: accepted),
`succeeded` (`_succeeded`), `failed` (`_failed`: the flagship was salvaged), `expired`
(`_expired`: its time ran out, or it ended without the player), `dismissed` (the key is true
without an outcome key), and the word `ended` = any of the last four.

MagicLib "counts" as in "Other mods and mod conditions": enabled when the game started (the start
record of the content mod whose prefix the rule id has) and still enabled. A bounty already in
the save's memory keeps its state either way.

**Quest-ended keys.** When a quest ends, SagaLib also sets `$global.sagaforge_success_<quest>` or
`$global.sagaforge_fail_<quest>` to true (it stays true when a repeatable quest runs again; a
quest that ended before 1.3 gets it on the next load). These are true/false keys in the sector's
memory, so MagicLib's `trigger_memKeys_*` can read them as `$sagaforge_success_<quest>`.

Golden case `test/golden/bounties.json`: its `bounties` key says what MagicLib knows
(`{"known": [ids], "memKeys": {id: key}}`), `enabled` lists `MagicLib` or not, and
`SF_StartBounty` in a `do` step stands for the bridge's accept (MagicLib sets the key false).

**Waiting for a bounty's end** (quest `bountyWaits`, Saga Forge task #69). A Wait node that goes on
by itself when the bounty ends, instead of a later dialog that tests its state. Each entry:
`node`, `bounty` (any mod's bounty id), `stage` (the step it waits at; Saga Forge writes the Stage
before the node) and one trigger per end state: `succeeded`, `failed`, `expired`, `dismissed`
(`""` = that end fires nothing, the quest stays there). The campaign script checks it four times
a day, with the same state as `SF_BountyState` (MagicLib's memory keys; no MagicLib class):

- While the active quest is at `stage` and the bounty has ended, it fires that end's trigger once
  through the trigger queue (`$global.sagaforge_pending`), so it opens its own dialog on the
  player's fleet when no other dialog is open, after MagicLib's own messages, like an encounter
  outcome. A bounty that ended **before** the quest got to the step fires as soon as it does.
- It records the step it fired at in `$global.sagaforge_bwait_<quest>_<node>_done`. Leaving the
  step and coming back watches again. Without `stage` it watches the whole run and fires once.
- A quest that isn't active forgets its waits. Without MagicLib (or when it doesn't count for the
  content mod) every bounty is `unavailable`, so nothing fires and the quest waits.
- `dismissed` comes only from MagicLib's old bar board (switched off in MagicLib 1.5.6).
- Not used: MagicLib's `job_conclusion_script` (one trigger per bounty, only for a mod's own
  bounties, fired in whatever dialog is open, and missed when the quest isn't there yet).

Golden case `test/golden/bountywait.json`: a `watch` step runs this check for every quest and
compares the triggers with `fired`.

## Mod settings (LunaLib) (SagaLib 1.3, task #62)

A content mod can have a settings page in LunaLib's menu. Its `data/config/LunaSettings.csv` is
LunaLib's own format (columns `fieldID`, `fieldName`, `fieldType`, `defaultValue`,
`secondaryValue`, `fieldDescription`, `minValue`, `maxValue`, `tab`), and content.csv lists it:

```
<modId>_settings,settings,data/config/LunaSettings.csv,<modId>
```

SagaLib reads the file itself (`Global.getSettings().loadCSV(path, mod)`, no LunaLib needed; a
listed file that is missing is an error). Every **value field** becomes **`$global.<fieldID>`**,
set on every game load (new games too), right after the start record and before anything reads
quests:

| `fieldType` | Memory value | Notes |
|---|---|---|
| `Boolean` | Boolean | `true` / `false`, any case |
| `Int` | Float (a whole number) | `1.5` isn't an Int |
| `Double` | Float | |
| `Radio` | String | `secondaryValue` = the choices, comma separated; the player's value must be one of them |
| `String` | String | may be empty |
| `Header`, `Text` | none | layout only, skipped silently |
| `Keycode`, `Color` | none | skipped with a warning (Saga Forge doesn't make them) |

Rows without a `fieldID` are skipped (as LunaLib does). An unknown type is a warning; a default
that doesn't read as its type is an error and the field is skipped. A field id used twice (in one
mod or across mods) is a warning: the first one wins. `minValue` / `maxValue` are LunaLib's menu
limits; SagaLib doesn't clamp.

**Values.** Without LunaLib (or with a SagaLib built without the bridge) every field gets its
default. With LunaLib enabled, the optional bridge (`src-optional/lunalib`, compiled against
LunaLib 2.0.5's jar, loaded by class name only when `lunalib` is enabled: `game/ModSettings`) reads
the player's value with `LunaSettings.getBoolean` / `getInt` / `getDouble` / `getString` (Radio and
String) and uses it when it reads as the field's type, else the default. Every apply sets every
field again, so a value LunaLib no longer has goes back to the default. A `LunaSettingsListener`
(registered once per game start) applies a mod's settings at once when the player saves them in
LunaLib's menu during a campaign, and logs what changed; saved from the main menu, they apply on
the next load. **LunaLib's values are shared by every save** (they live in `saves/common`). SagaLib
saves nothing about settings: the `$global` keys are ordinary memory, written again on every load.
Log: one line per content mod, e.g. `settings of sst_mod: 4 values (LunaLib)` or
`(defaults; LunaLib isn't enabled)`. Dev menu › Mod settings lists each value now and where it came
from.

Settings work as conditions everywhere (`$global.sst_enable`, `$global.sst_difficulty == Hard`,
`$global.sst_bonus >= 100`): quest `requires`, Branch cases, phrases.

**Scaling by a setting.** A reward's **`creditsScale`** and an encounter's **`fleet.sizeScale`**:

```json
{ "setting": "<fieldID>", "per": 100, "factors": { "Easy": 0.5, "Normal": 1, "Hard": 2 } }
```

`per` is optional (default 1; 0 is replaced by 1 with a warning), `factors` optional. The factor is
read when the reward is given / the fleet is built, from `$global.<setting>`: unset = 1. With
`factors`: the value's text (Boolean `true`/`false`, a number without `.0`, a String as is) →
its factor, not listed = 1. Without: a number (or a String that reads as one) divided by `per`,
anything else 1. NaN or infinite = 1; then clamped to 0..100. Credits = `round(credits × factor)`
(half up). Fleet points (`size: fp` only; explicit and named fleets are unchanged) =
`points × factor`, but at least 1 when the points are above 0; a scaled spawn logs one info line.

Golden case `test/golden/settings.json`: top-level `settings` (`{"mod", "rows"}`: the CSV rows as
loadCSV gives them) and `encounters` (encounter files), and steps `{"settings": {fieldID: "value
text"}}` (an apply with these player values; `{}` = no LunaLib), `{"credits": [quest, reward],
"is": n}`, `{"fleetPoints": encounter, "is": n}` and `{"unset": [keys]}`.

## MagicLib achievements (SagaLib 1.3, task #65)

A content mod's achievements are rows of its own `data/config/magic_achievements.csv`, MagicLib's
format (`id,name,description,tooltip,script,image,spoilerLevel,rarity`; `script` =
`org.magiclib.achievements.MagicAchievement`, the generic class for achievements code completes).
SagaLib doesn't read the file; MagicLib does, for every enabled mod.

| Command | Use | Effect |
|---|---|---|
| `SF_Achievement <id>` | script | Unlocks the achievement for the player (`MagicAchievement.completeAchievement(player, textPanel)` + `saveChanges()`), with MagicLib's notice in the dialog. Already unlocked: nothing changes. |

Through the MagicLib bridge (`src-optional/magiclib`, `MagicAchievementsBridge`, loaded by class name
by `game/Achievements` only when MagicLib is enabled). Without MagicLib, with its achievements turned
off in MagicLib's settings, or for an id MagicLib doesn't have, it does nothing and logs why once
per id. MagicLib keeps achievements in the game's common data (`magic_achievements.json`), not in a
save: one unlocked stays unlocked in every playthrough, so unlike the other commands it doesn't
depend on the start record.

## Nexerelin quest skip (SagaLib 1.3, task #65)

Nexerelin's new-game "skip quests" screen lists quest chains from every mod's
`data/config/exerelin/questSkip.json` (merged JSON; Saga Forge FORMATS.md, "Quest skip"). A content
mod's chains name **`lastai.sagalib.bridge.nexerelin.SagaQuestSkipPlugin`** as their `plugin`
(`src-optional/nexerelin`, compiled against Nexerelin 0.12.2d's jar into `jars/SagaLib.jar`). Only
Nexerelin instantiates it, by that name, so SagaLib never loads anything of Nexerelin's itself.

- Each quest entry's `sectorMemFlags` (and `playerMemFlags`) hold the chosen ending's memory, as
  this file defines it: `$<quest>_state`, `$<quest>_step`, vanilla's `$<quest>_missionCompleted`,
  `$sagaforge_success_<quest>` / `_fail_`, the variables and the keys set on the way (sector memory
  is `$global.` in rules). **Nexerelin applies them only through a plugin** (its `applyMemKeys`, in
  its `onNewGameAfterEconomyLoad`): the plugin's base class applies every skipped quest of the chain.
- After the time pass (`onNewGameAfterTimePass`), the plugin calls `game.QuestSkips.skipped(id)` for
  each skipped quest: SagaLib gives the quest's `skip.rewards` (as `SF_Reward` does, without a
  dialog), shows `skip.unhide` people in their market's comm directory (one without an entry is only
  logged), and sets **`$global.sagaforge_skipped_<quest>`** to true. That key makes it happen once;
  it isn't one of the flags Nexerelin sets (it would come first and stop the rewards).

The quest JSON's optional **`skip`** section (only for a quest in a chain):

```json
"skip": {"rewards": [{"id": "pay", "credits": 5000}, {"id": "skip_won", "xp": 500}], "unhide": ["vgc_reyes"]}
```

`rewards` are Reward objects (as in `rewards`, `creditsScale` included); Saga Forge copies those on
the way to the chosen ending and adds one for the direct credits, reputation, items, XP and story
points there. Missing or empty: nothing is given.

## Logging

Prefix `[SagaLib]`. Errors name the mod, the file and the JSON path. The game version is checked
on load; an unknown one logs a warning.
