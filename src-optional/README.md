# Optional bridges

Support for other mods lives here, one folder per mod (PLAN.md §3.1, §10).

| Folder | Mod | What it does |
|---|---|---|
| `magiclib/` | MagicLib (checked against 1.5.6; needs LazyLib) | `SF_StartBounty` accepts a MagicLib bounty for the player; `SF_BountyState` asks whether MagicLib has a bounty and its memory key (task #59, docs/FORMAT.md "MagicLib bounties") |
| `magiclib/` (achievements) | MagicLib 1.5.6 | `SF_Achievement` unlocks an achievement of a mod's `magic_achievements.csv` (`MagicAchievementsBridge`, through `game/Achievements`; task #65, docs/FORMAT.md "MagicLib achievements") |
| `nexerelin/` | Nexerelin (checked against 0.12.2d; needs LazyLib) | `SagaQuestSkipPlugin`, the plugin of the quest chains a mod's `data/config/exerelin/questSkip.json` lists: Nexerelin sets the skipped quests' flags through it, and it gives their rewards (`game/QuestSkips`; task #65, docs/FORMAT.md "Nexerelin quest skip"). Only Nexerelin loads it (by the name in that file), so SagaLib itself never does |
| `lunalib/` | LunaLib (checked against 2.0.5; needs LazyLib) | The player's values of each content mod's settings (`LunaSettings` getters) and changes saved in LunaLib's menu (`LunaSettingsListener`), into `$global.<fieldID>`; without it the defaults from the mod's LunaSettings.csv (task #62, `game/ModSettings`, docs/FORMAT.md "Mod settings (LunaLib)") |

How a bridge works (the same as ReadAloud's LunaLib bridge):

- Its classes sit in `src-optional/<name>/` and are compiled against the other mod's jar. Add a
  line to `BRIDGES` in `build.sh`: `"<name>|<jar glob under the mods folder>[|more jars]"`. If the
  jar isn't installed, the build skips the bridge and says so ("skip <name>"): that jar then has no
  bridge, and the feature says "built without its <mod> bridge". Release builds must show
  "built <name>".
- The bridge implements a small interface from `src/` (MagicLib: `game/BountyBridge`) and is
  compiled into the same `jars/SagaLib.jar`.
- SagaLib loads it by class name only when `Global.getSettings().getModManager().isModEnabled(<id>)`
  is true (MagicLib: `game/MagicBounties`), so the library never needs the other mod. Only the
  bridge's own classes name the other mod's classes (`jdeps` on the jar shows it).
