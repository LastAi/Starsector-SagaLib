# Optional bridges

Support for other mods lives here, one folder per mod (PLAN.md §3.1, §10). None exist yet; the
first planned one is MagicLib bounties.

How a bridge works (the same as ReadAloud's LunaLib bridge):

- Its classes sit in `src-optional/<name>/` and are compiled against the other mod's jar. Add a
  line to `BRIDGES` in `build.sh`: `"<name>|<jar glob under the mods folder>[|more jars]"`. If the
  jar isn't installed, the build skips the bridge and says so.
- The bridge implements a small interface from `src/` and is compiled into the same
  `jars/SagaLib.jar`.
- SagaLib loads it by class name only when `Global.getSettings().getModManager().isModEnabled(<id>)`
  is true, so the library never needs the other mod. Without it, features that need it log
  "needs <mod>".
