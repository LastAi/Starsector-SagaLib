package lastai.sagalib.core;

import lastai.sagalib.model.ProfileDef;
import lastai.sagalib.model.QuestDef;

import java.util.Map;

/** Applies a test profile (PLAN.md §4.10), the same way as play.ts applyProfile. */
public final class Profiles {
    private Profiles() {}

    /** The player's side of a profile. The game adapter changes the real fleet and character. */
    public interface Player {
        void setCredits(double credits);

        void setStoryPoints(int points);

        /** Takes a commission with that faction (vanilla's way). */
        void setCommission(String faction);

        /** Sets the relationship with a faction to {@code value} (-1..1). */
        void setRep(String faction, float value);

        void addCargo(String item, int qty);

        void setGender(String gender);

        /** Moves the player fleet there (the game only does it at new game). */
        void startAt(String entity);
    }

    /** A flag value as text, read the way Play mode does: true/false, a number, else the text. */
    public static Object flagValue(String v) {
        if ("true".equals(v)) return Boolean.TRUE;
        if ("false".equals(v)) return Boolean.FALSE;
        if (!v.isEmpty()) {
            Double d = Values.number(v);
            if (d != null) return Float.valueOf(d.floatValue());
        }
        return v;
    }

    public static void apply(Memory mem, ProfileDef prof, Map<String, QuestDef> quests, Player player) {
        for (Map.Entry<String, ProfileDef.QuestSetting> e : prof.quests.entrySet()) {
            String q = e.getKey();
            ProfileDef.QuestSetting s = e.getValue();
            if ("not-started".equals(s.state)) {
                mem.unset(QuestLogic.stateKey(q));
                mem.unset(QuestLogic.stepKey(q));
                continue;
            }
            mem.set(QuestLogic.stateKey(q), s.state);
            if (s.step != null) mem.set(QuestLogic.stepKey(q), s.step);
            QuestLogic.initVariables(mem, quests.get(q));
        }
        for (Map.Entry<String, String> e : prof.flags.entrySet()) {
            String k = e.getKey().startsWith("$") ? e.getKey() : "$" + e.getKey();
            mem.set(k, flagValue(e.getValue()));
        }
        if (prof.credits != null) player.setCredits(prof.credits);
        if (prof.storyPoints != null) player.setStoryPoints(prof.storyPoints);
        if (prof.commission != null) {
            player.setCommission(prof.commission);
            mem.set("$player.commissionFactionId", prof.commission);
            mem.set("$global.commissionFaction", prof.commission);
        }
        for (Map.Entry<String, Float> e : prof.rep.entrySet()) player.setRep(e.getKey(), e.getValue());
        for (ProfileDef.Cargo c : prof.cargo) player.addCargo(c.item, c.qty);
        if (prof.gender != null && !prof.gender.isEmpty()) player.setGender(prof.gender);
        if (prof.startAt != null) player.startAt(prof.startAt);
    }
}
