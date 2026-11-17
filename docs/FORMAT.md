# SagaLib runtime format (format 1)

SagaLib (mod id `lastai_sagalib`, MIT) runs content made with Saga Forge
(`tools/saga-forge`). This file is the contract between Saga Forge's Export and SagaLib.
The TypeScript copy of the behaviour is `tools/saga-forge/src/shared/play.ts` (Play mode). The
same golden cases in `test/golden/*.json` run on both sides, so the two can't drift apart.

## Files a content mod ships

| File | Read how | What |
|---|---|---|
| `data/config/sagaforge/content.csv` | `Global.getSettings().getMergedSpreadsheetDataForMod("id", path, "lastai_sagalib")`: rows from every enabled mod | `id,type,path,mod`: one row per runtime file. type ∈ `quest`, `people`, `factions`, `system`, `encounter`, `profiles`, `pool` (1.2) |
| `data/sagaforge/quests/<questId>.json` | `Global.getSettings().loadJSON(path, mod)` | one quest (below) |
| `data/sagaforge/people/<mod>_people.json` | same | `{format, people: [Person], phrases: [Phrase]}` (`phrases` = the mod-wide phrases) |
| `data/sagaforge/factions.json` | same | `{format, relations: {factionId: {otherId: -1..1}}, scripted: {factionId: [{op:"remove", path:"knownShips.hulls", value:"colossus"}]}}` |
| `data/sagaforge/systems/<id>.json` | same | a star system (M6; see that section) |
| `data/sagaforge/encounters/<id>.json` | same | an encounter (M7; see that section) |
| `data/sagaforge/pools/<id>.json` | same | SagaLib 1.2: a random pool (see "Pools") |
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
  "timers": [{"node": "wait1", "trigger": "vgc_lost_convoy_wait1", "days": 7}],
  "encounters": [{"node": "ambush", "encounter": "vgc_belt_ambush", "defeated": "vgc_lost_convoy_ambush_defeated", "avoided": "vgc_lost_convoy_ambush_avoided"}],
  "arrivals": ["vgc_convoy_wreck"],
  "phrases": [Phrase],
  "flags": ["$global.vgc_lost_convoy_catch_asked"]
}
```

- `kind` is `quest` (a vanilla hub mission: journal, markers, time limit) or `dialog` (a file only
  because it has inline phrases; no mission).
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

## Factions runtime

On a **new game** only, set the starting relations
(`Global.getSector().getFaction(a).setRelationship(b, value)`). Scripted changes are applied on
**every** load: remove the value from the faction's known lists (`FactionAPI.removeKnownShip`,
`removeKnownWeapon`, …, by path).

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
    Saga Forge should write `stage` = the stage set just before the Wait node.
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
  phrases, profiles) from disk without restarting. Reload only re-reads definitions; built
  systems and spawned fleets are not rebuilt.
- Applying a profile writes the quests' state/step/variables, flags, credits, story points,
  commission, reputation, cargo, and moves the player fleet to `startAt` (an entity or market id;
  before 1.2 only at a new game; from the dev menu, the menu then closes). The menu says it
  changes the running save. Test mods use this to take the player to their testing area.

## Logging

Prefix `[SagaLib]`. Errors name the mod, the file and the JSON path. The game version is checked
on load; an unknown one logs a warning.
