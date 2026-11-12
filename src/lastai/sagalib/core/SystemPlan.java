package lastai.sagalib.core;

import lastai.sagalib.model.SystemDef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The pure part of building a star system: in which order entities are created, and which are added. */
public final class SystemPlan {
    private SystemPlan() {}

    /** Answers whether an entity id already exists in the game. */
    public interface Exists {
        boolean exists(String id);
    }

    /** The order and what couldn't be placed. */
    public static final class Order {
        public final List<SystemDef.Entity> entities = new ArrayList<SystemDef.Entity>();
        /** One line per entity left out (focus missing, or a focus loop). */
        public final List<String> problems = new ArrayList<String>();
    }

    /**
     * Focus first: an entity comes after the entity it orbits. "star" and ids that already exist
     * in the game (an existing system's planets) are always available. JSON order is kept otherwise.
     * Entities whose focus never becomes available are left out and reported.
     */
    public static Order buildOrder(List<SystemDef.Entity> entities, Exists external) {
        Order o = new Order();
        Set<String> placed = new HashSet<String>();
        List<SystemDef.Entity> left = new ArrayList<SystemDef.Entity>(entities);
        boolean progress = true;
        while (!left.isEmpty() && progress) {
            progress = false;
            for (int i = 0; i < left.size(); i++) {
                SystemDef.Entity e = left.get(i);
                String f = e.orbit.focus;
                if ("star".equals(f) || placed.contains(f) || (!isListed(entities, f) && external != null && external.exists(f))) {
                    o.entities.add(e);
                    placed.add(e.id);
                    left.remove(i);
                    progress = true;
                    break; // restart, so JSON order wins among the ones that are ready
                }
            }
        }
        for (SystemDef.Entity e : left) {
            String f = e.orbit.focus;
            o.problems.add(e.id + ": its orbit focus \"" + f + "\" " + (isListed(entities, f) ? "is part of a focus loop" : "doesn't exist") + "; not built");
        }
        return o;
    }

    private static boolean isListed(List<SystemDef.Entity> entities, String id) {
        for (SystemDef.Entity e : entities) if (e.id.equals(id)) return true;
        return false;
    }

    /** For an existing system (or a re-check): the entities still missing, focus first. Existing ones are never touched. */
    public static Order additions(SystemDef s, Exists game) {
        return additions(s, game, new MapMemory());
    }

    /** Memory key recording that an addition was built in this save (so a salvaged wreck stays gone). */
    public static String addedKey(String entityId) { return "$global.sagaforge_added_" + entityId; }

    /**
     * Entities to add to an existing system: those not in the game that this save never had.
     * One that was built once and is gone now (salvaged, destroyed) isn't built again
     * (found while planning the salvage quest, 2026-11-11). Entities that exist get recorded too,
     * for saves from before this was tracked.
     */
    public static Order additions(SystemDef s, Exists game, Memory mem) {
        List<SystemDef.Entity> missing = new ArrayList<SystemDef.Entity>();
        for (SystemDef.Entity e : s.entities) {
            if (game.exists(e.id)) mem.set(addedKey(e.id), Boolean.TRUE);
            else if (!Boolean.TRUE.equals(mem.get(addedKey(e.id)))) missing.add(e);
        }
        return buildOrder(missing, game);
    }
}
