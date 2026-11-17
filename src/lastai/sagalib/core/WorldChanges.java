package lastai.sagalib.core;

import lastai.sagalib.model.SystemDef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * SagaLib 1.1 (docs/FORMAT.md, "Objects that appear and disappear"): which deferred entities to
 * place and which entities to remove now, from the quests' memory keys. Pure: the game side builds
 * and removes. Recorded in memory keys only:
 * <pre>
 *   $global.sagaforge_placed_&lt;id&gt;   true once a deferred entity was placed
 *   $global.sagaforge_removed_&lt;id&gt;  true once an entity was removed (or a removal was given up)
 * </pre>
 */
public final class WorldChanges {
    private WorldChanges() {}

    public static String placedKey(String id) { return "$global.sagaforge_placed_" + id; }

    public static String removedKey(String id) { return "$global.sagaforge_removed_" + id; }

    /** A deferred entity and the system it belongs to. */
    public static final class Placement {
        public final SystemDef system;
        public final SystemDef.Entity entity;

        Placement(SystemDef system, SystemDef.Entity entity) { this.system = system; this.entity = entity; }
    }

    /** True while the quest is active at that stage. */
    public static boolean at(Memory m, SystemDef.StageRef r) {
        return r != null && r.quest != null && r.stage != null && QuestLogic.isActive(m, r.quest) && r.stage.equals(QuestLogic.step(m, r.quest));
    }

    public static boolean placed(Memory m, String id) { return Boolean.TRUE.equals(m.get(placedKey(id))); }

    public static boolean removed(Memory m, String id) { return Boolean.TRUE.equals(m.get(removedKey(id))); }

    /** Deferred entities whose quest is at their stage now, not placed (or removed) before. */
    public static List<Placement> toPlace(Collection<SystemDef> systems, Memory m) {
        List<Placement> out = new ArrayList<Placement>();
        for (SystemDef s : systems) {
            for (SystemDef.Entity e : s.entities) {
                if (e.deferred() && !placed(m, e.id) && !removed(m, e.id) && at(m, e.appears)) out.add(new Placement(s, e));
            }
        }
        return out;
    }

    /** Removals whose quest is at their stage now, not done before. */
    public static List<SystemDef.Removal> toRemove(Collection<SystemDef> systems, Memory m) {
        List<SystemDef.Removal> out = new ArrayList<SystemDef.Removal>();
        for (SystemDef s : systems) for (SystemDef.Removal r : s.removals) if (!removed(m, r.target) && at(m, r.at)) out.add(r);
        return out;
    }

    /** The deferred entity with this id in any system (for SF_PlaceEntity), or null. */
    public static Placement find(Collection<SystemDef> systems, String id) {
        for (SystemDef s : systems) {
            SystemDef.Entity e = s.entity(id);
            if (e != null) return new Placement(s, e);
        }
        return null;
    }

    /** The removal of this target listed in any system (its market rule), or null. */
    public static SystemDef.Removal removal(Collection<SystemDef> systems, String target) {
        for (SystemDef s : systems) for (SystemDef.Removal r : s.removals) if (r.target.equals(target)) return r;
        return null;
    }

    /** An entity flag and whether it should be set now. */
    public static final class FlagState {
        public final lastai.sagalib.model.QuestDef quest;
        public final lastai.sagalib.model.QuestDef.EntityFlag flag;
        public final boolean on;

        FlagState(lastai.sagalib.model.QuestDef quest, lastai.sagalib.model.QuestDef.EntityFlag flag, boolean on) { this.quest = quest; this.flag = flag; this.on = on; }
    }

    /** Every quest's entity flags (SagaLib 1.1): on while the quest is active at one of the flag's steps, off otherwise. */
    public static List<FlagState> entityFlags(Collection<lastai.sagalib.model.QuestDef> quests, Memory m) {
        List<FlagState> out = new ArrayList<FlagState>();
        for (lastai.sagalib.model.QuestDef q : quests) {
            if (q.entityFlags.isEmpty()) continue;
            boolean active = QuestLogic.isActive(m, q.id);
            String step = QuestLogic.step(m, q.id);
            for (lastai.sagalib.model.QuestDef.EntityFlag f : q.entityFlags) out.add(new FlagState(q, f, active && step != null && f.steps.contains(step)));
        }
        return out;
    }

    /** Entities built with the system: everything except the deferred ones. */
    public static List<SystemDef.Entity> initial(List<SystemDef.Entity> entities) {
        List<SystemDef.Entity> out = new ArrayList<SystemDef.Entity>();
        for (SystemDef.Entity e : entities) if (!e.deferred()) out.add(e);
        return out;
    }
}
