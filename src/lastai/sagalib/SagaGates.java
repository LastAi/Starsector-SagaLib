package lastai.sagalib;

import lastai.sagalib.core.Content;
import lastai.sagalib.core.ModGate;
import lastai.sagalib.model.EncounterDef;
import lastai.sagalib.model.EventsDef;
import lastai.sagalib.model.FactionsDef;
import lastai.sagalib.model.PersonDef;
import lastai.sagalib.model.PoolDef;
import lastai.sagalib.model.ProgressDef;
import lastai.sagalib.model.QuestDef;
import lastai.sagalib.model.ScatterDef;
import lastai.sagalib.model.SystemDef;

import java.util.LinkedHashMap;
import java.util.Map;

/** SagaLib 1.3: every content item that has mod conditions ("quest vgc_x" → its gate), for the dev menu and the tests. */
public final class SagaGates {
    private SagaGates() {}

    public static Map<String, ModGate> all(Content c) {
        Map<String, ModGate> out = new LinkedHashMap<String, ModGate>();
        for (QuestDef q : c.quests.values()) put(out, "quest " + q.id, q.gate);
        for (PersonDef p : c.people.values()) put(out, "person " + p.id, p.gate);
        for (SystemDef s : c.systems.values()) {
            put(out, "system " + s.id, s.gate);
            for (SystemDef.Entity e : s.entities) put(out, "object " + e.id, e.gate);
        }
        for (EncounterDef e : c.encounters.values()) put(out, "encounter " + e.id, e.gate);
        for (PoolDef p : c.pools.values()) put(out, "pool " + p.id, p.gate);
        for (EventsDef d : c.events.values()) put(out, "event scheduler " + d.id, d.gate);
        for (ProgressDef d : c.progress.values()) put(out, "progress event " + d.id, d.gate);
        for (ScatterDef s : c.scatters.values()) put(out, "scatter " + s.id, s.gate);
        for (FactionsDef f : c.factions) {
            for (FactionsDef.Relation r : f.relations) put(out, "relation " + r.faction + "/" + r.other, r.gate);
            for (FactionsDef.Change ch : f.scripted) put(out, "faction change " + ch.faction + " " + ch.op + (ch.value != null ? " " + ch.value : ""), ch.gate);
        }
        return out;
    }

    private static void put(Map<String, ModGate> out, String key, ModGate g) {
        if (g != null && !g.isEmpty()) out.put(key, g);
    }
}
