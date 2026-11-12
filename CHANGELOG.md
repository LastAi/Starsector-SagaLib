# Changelog

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

Tested: 56 Java tests (471 checks), the golden cases shared with Saga Forge's Play mode, and the
Vanguard Compact walkthrough in game (12 steps, 2026-11-11, with 126 mods enabled).
