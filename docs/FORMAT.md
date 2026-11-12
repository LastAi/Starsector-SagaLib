# SagaLib runtime format (format 1)

SagaLib (mod id `lastai_sagalib`, CC BY 4.0) runs content made with Saga Forge
(`tools/saga-forge`). This file is the contract between Saga Forge's Export and SagaLib.
The TypeScript copy of the behaviour is `tools/saga-forge/src/shared/play.ts` (Play mode). The
same golden cases in `test/golden/*.json` run on both sides, so the two can't drift apart.

## Files a content mod ships

| File | Read how | What |
|---|---|---|
| `data/config/sagaforge/content.csv` | `Global.getSettings().getMergedSpreadsheetDataForMod("id", path, "lastai_sagalib")`: rows from every enabled mod | `id,type,path,mod`: one row per runtime file. type ∈ `quest`, `people`, `factions`, `system`, `encounter`, `profiles` |
| `data/sagaforge/quests/<questId>.json` | `Global.getSettings().loadJSON(path, mod)` | one quest (below) |
| `data/sagaforge/people/<mod>_people.json` | same | `{format, people: [Person], phrases: [Phrase]}` (`phrases` = the mod-wide phrases) |
| `data/sagaforge/factions.json` | same | `{format, relations: {factionId: {otherId: -1..1}}, scripted: {factionId: [{op:"remove", path:"knownShips.hulls", value:"colossus"}]}}` |
| `data/sagaforge/systems/<id>.json` | same | a star system (M6; see that section) |
| `data/sagaforge/encounters/<id>.json` | same | an encounter (M7; see that section) |
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
- `targets` is reserved for random targets (after 1.0). Format 1 always writes `[]`; SagaLib must
  ignore it.
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

## Dev tools (only when `Global.getSettings().isDevMode()`)

- **New game**: after the sector is generated, if any profile exists, show a dialog listing them
  (and "none") and apply the chosen one.
- **Dev menu** (a `CampaignInputListener`, re-added on load, default key F9 with Ctrl). It
  lists, applies a profile, jumps a quest to a step, and **reloads** the JSON (quests, people,
  phrases, profiles) from disk without restarting. Reload only re-reads definitions; built
  systems and spawned fleets are not rebuilt.
- Applying a profile writes the quests' state/step/variables, flags, credits, story points,
  commission, reputation, cargo, and (new game only) moves the player fleet to `startAt`. The
  menu says it changes the running save.

## Logging

Prefix `[SagaLib]`. Errors name the mod, the file and the JSON path. The game version is checked
on load; an unknown one logs a warning.
