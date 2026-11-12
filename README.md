# SagaLib

A Starsector library (mod id `lastai_sagalib`) that runs quests, characters and conversations
made with the **Saga Forge** editor ([Starsector-SagaForge](https://github.com/LastAi/Starsector-SagaForge)).
Version 1.0, for Starsector 0.98a. From 1.0 on the save format is frozen: saved class names and
fields, and the memory keys in docs/FORMAT.md, never change.
License: [MIT](LICENSE). Changes: [CHANGELOG.md](CHANGELOG.md).

An example mod that uses it, Vanguard Compact, is in the Saga Forge repo (`example/`).

> **Tested, but it needs more testing over time.** SagaLib passes its automated tests and a full
> in-game walkthrough of the example mod (with 126 other mods enabled), but it is new, and edge
> cases may have been overlooked. Modders: keep that in mind when you build on it, and test your
> quests in game. If something behaves oddly, please
> [open an issue](https://github.com/LastAi/Starsector-SagaLib/issues) with your `starsector.log`
> (SagaLib's lines start with `[SagaLib]`) and, if you can, the mod's JSON.

> **Linux: available but untested.** SagaLib is plain Java and has nothing Windows-specific, so it
> should work on Linux (and macOS), but so far it has only been tested on Windows.

## For players

Install it like any mod when another mod says "made with Saga Forge, needs SagaLib". It does
nothing by itself. Quests made with it are ordinary missions: they are offered by contacts or at
the bar, show in the intel screen's mission list with markers on the map, can be abandoned, and
keep their progress when the mod that ships them is updated. If an update removes the step you
were at, the quest says "This quest was changed by an update" and you can abandon it.

## For modders

You normally don't write anything for SagaLib by hand: Saga Forge exports it. The contract
between the two is [docs/FORMAT.md](docs/FORMAT.md). In short, a content mod ships:

- `data/config/sagaforge/content.csv` (`id,type,path,mod`): one row per runtime file; SagaLib
  reads the merged rows of every enabled mod.
- `data/sagaforge/quests/<quest>.json`, `people/<mod>_people.json`, `factions.json`, and in dev
  mode `dev/test_profiles.json`.
- `person_missions.csv` / `bar_events.csv` rows with plugin `lastai.sagalib.quest.SagaQuest`
  (one class for every quest; the mission id is the quest id).
- `rules.csv` dialog that uses SagaLib's rule commands: `SF_Start`, `SF_Step`, `SF_End`,
  `SF_Reward`, `SF_AddShip`, `SF_RemoveShip`, the displays `SF_ShowPlanetInfo`, `SF_ShowFleets`,
  `SF_ShowShip`, `SF_Show image|faction|ships|cargo|heading|skills`, and the conditions
  `SF_AtStep`, `SF_QuestDone`, `SF_QuestState`, and `SF_Spawn` / `SF_Despawn` for encounters
  (`data/sagaforge/encounters/*.json`, with defeated/avoided outcomes and quest timers). Star systems (`data/sagaforge/systems/*.json`) are built on new game, and
  in existing saves when the file says `addToExistingSaves`.

Quest progress is kept in memory keys only (`$global.<quest>_state`, `$global.<quest>_step`,
`$global.<quest>_<variable>`), so rules.csv can test them directly too.

Errors in the JSON are logged in `starsector.log` with the prefix `[SagaLib]`, the file and the
JSON path; the bad object is skipped and the game keeps running.

**Dev mode** (`devMode` in the game's settings): after a new sector is generated SagaLib asks
which test profile to apply, and **Ctrl+F9** on the campaign map opens a menu to apply a profile,
jump a quest to a step, or reload the JSON from disk. None of this exists outside dev mode.

### License

MIT: use, bundle, copy or change it freely; keep the `LICENSE` file (copyright notice) with any
copy. Mods that only list SagaLib as a dependency ship none of its files and owe nothing. A credit
is welcome but not required, for example:

> Uses SagaLib by LastAi - https://github.com/LastAi/Starsector-SagaLib

## Building

```
bash build.sh            # compile jars/SagaLib.jar, then run the tests
bash build.sh --no-test  # compile only
```

Needs a JDK 17+ and the game in `D:\Starsector` (or set `STARSECTOR_CORE` to its
`starsector-core` folder). Class files go to the workspace's `.cache\sagalib-build` when there is
one, else to `../.build/sagalib-build` next to this folder. The tests (`test/`) cover JSON
reading and errors, quest logic, conditions, phrases, save reconciliation, test profiles, a check
that shipped classes use nothing the game's class loader refuses, and the **golden cases** in
`test/golden/*.json`, which Saga Forge's Play mode runs too (its `test/golden.test.ts`), so the
Java library and the editor can't drift apart.

Code layout: `lastai.sagalib.core` and `.model` are pure Java (no game classes, unit-tested);
`.game`, `.quest`, `.rulecmd` and `.dev` are the thin layer on the game's API; `.systems` and
`.encounters` build star systems and run encounter fleets. Classes that end up in saves
(`quest.SagaQuest`, `quest.QuestStage`) store ids only and must never be renamed.
