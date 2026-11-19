package lastai.sagalib.dev;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.QuestLogic;
import lastai.sagalib.game.GameMemory;
import lastai.sagalib.game.QuestRuntime;
import lastai.sagalib.game.Saga;
import lastai.sagalib.model.ProfileDef;
import lastai.sagalib.model.QuestDef;
import org.lwjgl.input.Keyboard;

import java.util.Map;

/**
 * SagaLib's dev menu (dev mode only): apply a test profile, jump a quest to a step, reload the
 * JSON. With {@code newGame} it is the profile picker shown after a new sector is generated.
 */
public final class DevMenu implements InteractionDialogPlugin {
    private static final class Opt {
        final String kind;
        final String a;
        final String b;

        Opt(String kind, String a, String b) {
            this.kind = kind;
            this.a = a;
            this.b = b;
        }
    }

    private final boolean newGame;
    private InteractionDialogAPI dialog;

    public DevMenu(boolean newGame) { this.newGame = newGame; }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.getVisualPanel().fadeVisualOut();
        TextPanelAPI text = dialog.getTextPanel();
        if (newGame) {
            text.addPara("SagaLib (dev mode): apply a test profile to this new game?");
            profiles();
        } else {
            text.addPara("SagaLib dev menu. Profiles and jumps change the running save: use a throwaway save.",
                    Misc.getHighlightColor(), "change the running save");
            main();
        }
    }

    private OptionPanelAPI options() { return dialog.getOptionPanel(); }

    private void leaveOption(String label) {
        Opt leave = new Opt("leave", null, null);
        options().addOption(label, leave);
        options().setShortcut(leave, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    private void main() {
        options().clearOptions();
        options().addOption("Apply a test profile", new Opt("profiles", null, null));
        options().addOption("Jump a quest to a step", new Opt("quests", null, null));
        options().addOption("Mods: the start record and what was skipped", new Opt("mods", null, null));
        options().addOption("Change a market's owner (quests react at once)", new Opt("owners", null, null));
        boolean presence = false;
        for (lastai.sagalib.model.PersonDef d : Saga.content().people.values()) presence |= d.presence != null;
        if (presence) options().addOption("War or peace: a presence person's market owner and their faction", new Opt("relations", null, null));
        options().addOption("Versions: each item's version in this game", new Opt("versions", null, null));
        if (!Saga.content().settings.isEmpty()) options().addOption("Mod settings: each value now and where it came from", new Opt("settings", null, null));
        options().addOption("Write sector snapshot (for Saga Forge)", new Opt("snapshot", null, null));
        options().addOption("Reload the JSON from disk", new Opt("reload", null, null));
        leaveOption("Leave");
    }

    private void profiles() {
        options().clearOptions();
        for (ProfileDef p : Saga.content().profileList()) options().addOption(p.name + " (" + p.id + ")", new Opt("profile", p.id, null));
        if (newGame) {
            leaveOption("None");
        } else {
            if (Saga.content().profiles.isEmpty()) dialog.getTextPanel().addPara("No test profiles are loaded (data/sagaforge/dev/test_profiles.json).");
            options().addOption("Back", new Opt("main", null, null));
        }
    }

    /** SagaLib 1.3: each content mod's optional and incompatible mods (start record, now) and what was skipped. */
    private void mods(TextPanelAPI text) {
        GameMemory mem = GameMemory.global();
        // SagaLib 1.3 (task #59): the guarded MagicLib bridge
        String bridge = lastai.sagalib.game.MagicBounties.status();
        text.addPara("MagicLib bounties: " + (bridge.isEmpty() ? "bridge ready (quests can start and check MagicLib bounties)" : bridge + " (quests see every bounty as unavailable)"));
        if (lastai.sagalib.core.Mods.lists().isEmpty()) text.addPara("No content mod lists other mods (data/sagaforge/mods.json).");
        for (lastai.sagalib.core.Mods.ModList l : lastai.sagalib.core.Mods.lists().values()) {
            StringBuilder b = new StringBuilder(l.name + " (" + l.owner + "):");
            for (lastai.sagalib.core.Mods.Entry e : l.entries) {
                Object rec = mem.get(lastai.sagalib.core.Mods.recordKey(l.owner, e.id));
                b.append("\n  ").append(e.name).append(" (").append(e.id).append("), ").append(e.use)
                        .append(e.recorded() ? ": at start " + (rec == null ? "not recorded" : rec) : "")
                        .append(", now ").append(lastai.sagalib.core.Mods.enabledNow(e.id) ? "on" : "off")
                        .append(e.recorded() ? (lastai.sagalib.core.Mods.counts(mem, l.owner, e.id) ? ": counts" : ": doesn't count") : "");
            }
            text.addPara(b.toString());
        }
        // every item with mod conditions that doesn't hold in this game, plus what was skipped so far
        java.util.Map<String, String> sk = new java.util.LinkedHashMap<String, String>();
        for (java.util.Map.Entry<String, lastai.sagalib.core.ModGate> g : lastai.sagalib.SagaGates.all(lastai.sagalib.game.Saga.content()).entrySet()) {
            String why = lastai.sagalib.core.Mods.blocked(mem, g.getValue());
            if (why != null) sk.put(g.getKey(), why);
        }
        sk.putAll(lastai.sagalib.core.Mods.skipped());
        if (sk.isEmpty()) text.addPara("Nothing is skipped for mod conditions in this game.");
        else {
            StringBuilder b = new StringBuilder("Skipped (only with / only without mods):");
            for (java.util.Map.Entry<String, String> e : sk.entrySet()) b.append("\n  ").append(e.getKey()).append(": ").append(e.getValue());
            text.addPara(b.toString());
        }
    }

    /** SagaLib 1.3 (task #62): each content mod's settings: the value now and where it came from. Read only. */
    private void settings(TextPanelAPI text) {
        String bridge = lastai.sagalib.game.ModSettings.status();
        text.addPara("LunaLib: " + (bridge.isEmpty() ? "bridge ready (the player's values; menu changes apply at once)" : bridge + " (every setting uses its default)"));
        GameMemory mem = GameMemory.global();
        for (java.util.Map.Entry<String, java.util.List<lastai.sagalib.core.Settings.Field>> e : lastai.sagalib.game.ModSettings.byMod().entrySet()) {
            StringBuilder b = new StringBuilder("Settings of " + e.getKey() + ":");
            for (lastai.sagalib.core.Settings.Field f : e.getValue()) {
                lastai.sagalib.core.Settings.Applied a = lastai.sagalib.game.ModSettings.last(f.id);
                b.append("\n  ").append(f.key()).append(" (").append(f.type).append(") = ").append(lastai.sagalib.core.Values.str(mem.get(f.key())))
                        .append(a == null ? ", not applied yet" : a.fromPlayer ? ", from LunaLib" : ", default")
                        .append(" (default ").append(lastai.sagalib.core.Values.str(f.def)).append(')');
            }
            text.addPara(b.toString());
        }
    }

    /** SagaLib 1.3: every versioned item (kind, owner, the mods it mentions, the version this game uses). */
    private void versions(TextPanelAPI text) {
        GameMemory mem = GameMemory.global();
        lastai.sagalib.core.Content c = Saga.content();
        if (c.versioned.isEmpty()) text.addPara("No content item has versions by installed mods.");
        else {
            StringBuilder b = new StringBuilder("Items with versions (" + c.versioned.size() + "):");
            for (lastai.sagalib.core.Versions.Item it : c.versioned.values()) {
                Object stored = mem.get(lastai.sagalib.core.Versions.memKey(it.kind, it.id));
                b.append("\n  ").append(it.kind).append(' ').append(it.id).append(" (").append(it.owner).append("), mentions ")
                        .append(it.mentioned.isEmpty() ? "no mod" : lastai.sagalib.core.Versions.key(it.mentioned).replace("+", ", "))
                        .append(": ").append(stored == null ? "not chosen yet" : "version " + stored)
                        .append(", in use ").append(lastai.sagalib.core.Content.activeKey(it));
            }
            text.addPara(b.toString());
        }
        for (lastai.sagalib.core.Content.TextVersions tv : c.textVersions) {
            java.util.List<String> on = new java.util.ArrayList<String>();
            for (String m : tv.mentioned) if (lastai.sagalib.core.Mods.enabledNow(m)) on.add(m);
            text.addPara("Item texts of " + tv.mod + " (by the mods enabled at startup): version " + lastai.sagalib.core.Versions.key(on));
        }
    }

    /** SagaLib 1.3 (task #61): factions offered as a new owner (those in this game). */
    private static final String[] OWNERS = {"pirates", "independent", "hegemony", "tritachyon", "luddic_church", "luddic_path", "persean", "sindrian_diktat"};

    /** SagaLib 1.3 (task #61): the markets active quests watch, then the mod's own markets (at most 12). */
    private void ownerMarkets(TextPanelAPI text) {
        options().clearOptions();
        GameMemory mem = GameMemory.global();
        java.util.LinkedHashMap<String, String> list = new java.util.LinkedHashMap<String, String>();
        StringBuilder b = new StringBuilder();
        for (QuestDef q : Saga.content().quests.values()) {
            if (q.ownerChange == null || !QuestLogic.isActive(mem, q.id)) continue;
            b.append("\n  ").append(q.title).append(" (").append(q.ownerChange.action)
                    .append(q.ownerChange.stage != null && !q.ownerChange.stage.isEmpty() && "stage".equals(q.ownerChange.action) ? " " + q.ownerChange.stage : "").append("): ");
            for (String[] r : lastai.sagalib.core.Places.remembered(mem, q.id)) {
                b.append(r[0]).append('=').append(r[1]).append(' ');
                if (!list.containsKey(r[0])) list.put(r[0], q.title);
            }
        }
        text.addPara(b.length() == 0 ? "No active quest watches its markets." : "Watched by active quests (market=owner when it started or at the last check):" + b);
        // SagaLib 1.3 (task #70): the markets of people present only while the owner gets on with a faction
        StringBuilder pb = new StringBuilder();
        for (lastai.sagalib.model.PersonDef d : Saga.content().people.values()) {
            if (d.presence == null) continue;
            pb.append("\n  ").append(d.id).append(" at ").append(d.market).append(": owner at least ").append(d.presence.atLeast).append(" with ")
                    .append(d.presence.faction).append(lastai.sagalib.core.Presence.isAway(mem, d.id) ? " (away now)" : " (present now)");
            if (!list.containsKey(d.market)) list.put(d.market, "presence of " + d.id);
        }
        if (pb.length() > 0) text.addPara("People with a presence rule:" + pb);
        for (lastai.sagalib.model.SystemDef sd : Saga.content().systems.values()) {
            for (lastai.sagalib.model.SystemDef.Entity e : sd.entities) if (e.market != null && !list.containsKey(e.id)) list.put(e.id, null);
        }
        int n = 0;
        for (java.util.Map.Entry<String, String> e : list.entrySet()) {
            com.fs.starfarer.api.campaign.econ.MarketAPI m = lastai.sagalib.game.SectorPlaces.market(e.getKey());
            if (m == null || n >= 12) continue;
            n++;
            options().addOption(m.getName() + " (" + m.getFactionId() + ")" + (e.getValue() != null ? ", " + e.getValue() : ""), new Opt("ownerMarket", e.getKey(), null));
        }
        options().addOption("Back", new Opt("main", null, null));
    }

    private void ownerFactions(String market) {
        options().clearOptions();
        com.fs.starfarer.api.campaign.econ.MarketAPI m = lastai.sagalib.game.SectorPlaces.market(market);
        for (String f : OWNERS) {
            if (m != null && f.equals(m.getFactionId())) continue;
            com.fs.starfarer.api.campaign.FactionAPI fa = com.fs.starfarer.api.Global.getSector().getFaction(f);
            if (fa == null) continue;
            options().addOption(fa.getDisplayName() + " (" + f + ")", new Opt("ownerSet", market, f));
        }
        options().addOption("Back", new Opt("owners", null, null));
    }

    /** Like a capture, without Nexerelin's side effects: the market's and its entities' faction. Then the quests check at once. */
    private void setOwner(String market, String faction, TextPanelAPI text) {
        com.fs.starfarer.api.campaign.econ.MarketAPI m = lastai.sagalib.game.SectorPlaces.market(market);
        if (m == null) {
            text.addPara("No market " + market + " in this game.");
            return;
        }
        String old = m.getFactionId();
        m.setFactionId(faction);
        if (m.getPrimaryEntity() != null) m.getPrimaryEntity().setFaction(faction);
        for (com.fs.starfarer.api.campaign.SectorEntityToken t : m.getConnectedEntities()) t.setFaction(faction);
        SagaLog.info("dev menu: market " + m.getId() + " changed owner from " + old + " to " + faction);
        int n = lastai.sagalib.game.OwnerChanges.checkNow(GameMemory.global());
        int pn = lastai.sagalib.game.PresenceRuntime.tick(GameMemory.global()); // SagaLib 1.3 (task #70)
        text.addPara(m.getName() + ": " + old + " -> " + faction + ". " + (n == 0 ? "No quest reacted." : n + " quest" + (n == 1 ? "" : "s") + " reacted (see the journal and starsector.log).")
                + (pn > 0 ? " " + pn + " person" + (pn == 1 ? "" : "s") + " left or came back (starsector.log)." : ""));
    }

    /**
     * SagaLib 1.3 (task #70): for each person with a presence rule, war (Hostile) or peace (Neutral)
     * between their market's owner and the faction they're loyal to; then presence is checked at once.
     */
    private void relations(TextPanelAPI text) {
        options().clearOptions();
        com.fs.starfarer.api.campaign.SectorAPI s = com.fs.starfarer.api.Global.getSector();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (lastai.sagalib.model.PersonDef d : Saga.content().people.values()) {
            if (d.presence == null) continue;
            com.fs.starfarer.api.campaign.econ.MarketAPI m = lastai.sagalib.game.SectorPlaces.market(d.market);
            if (m == null || m.getFactionId().equals(d.presence.faction) || !seen.add(m.getFactionId() + "|" + d.presence.faction)) continue;
            com.fs.starfarer.api.campaign.FactionAPI a = s.getFaction(m.getFactionId());
            if (a == null || s.getFaction(d.presence.faction) == null) continue;
            String pair = m.getFactionId() + "|" + d.presence.faction;
            text.addPara(m.getFactionId() + " and " + d.presence.faction + " (" + d.id + "): " + a.getRelationshipLevel(d.presence.faction).name());
            options().addOption("War: " + m.getFactionId() + " and " + d.presence.faction + " hostile", new Opt("relationSet", pair, "-0.6"));
            options().addOption("Peace: " + m.getFactionId() + " and " + d.presence.faction + " neutral", new Opt("relationSet", pair, "0"));
        }
        options().addOption("Back", new Opt("main", null, null));
    }

    private void setRelation(String pair, String value, TextPanelAPI text) {
        String[] ab = pair.split("\\|");
        com.fs.starfarer.api.campaign.FactionAPI a = com.fs.starfarer.api.Global.getSector().getFaction(ab[0]);
        if (a == null) return;
        a.setRelationship(ab[1], Float.parseFloat(value));
        SagaLog.info("dev menu: relation " + ab[0] + " / " + ab[1] + " set to " + value);
        int pn = lastai.sagalib.game.PresenceRuntime.tick(GameMemory.global());
        text.addPara(ab[0] + " / " + ab[1] + ": now " + a.getRelationshipLevel(ab[1]).name() + ". "
                + (pn == 0 ? "No one left or came back." : pn + " person" + (pn == 1 ? "" : "s") + " left or came back (starsector.log)."));
    }

    private void quests() {
        options().clearOptions();
        GameMemory mem = GameMemory.global();
        for (QuestDef q : Saga.content().quests.values()) {
            if (!q.isMission()) continue;
            String st = QuestLogic.state(mem, q.id);
            String step = QuestLogic.step(mem, q.id);
            options().addOption(q.title + " (" + st + (step != null ? ", at " + step : "") + ")", new Opt("quest", q.id, null));
        }
        options().addOption("Back", new Opt("main", null, null));
    }

    private void steps(String questId) {
        QuestDef q = Saga.content().quest(questId);
        options().clearOptions();
        if (q != null) for (QuestDef.Stage s : q.stages) options().addOption(s.id + ": " + s.title, new Opt("step", q.id, s.id));
        options().addOption("Back", new Opt("quests", null, null));
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (!(optionData instanceof Opt)) return;
        Opt o = (Opt) optionData;
        TextPanelAPI text = dialog.getTextPanel();
        try {
            if ("leave".equals(o.kind)) {
                dialog.dismiss();
            } else if ("main".equals(o.kind)) {
                main();
            } else if ("profiles".equals(o.kind)) {
                profiles();
            } else if ("quests".equals(o.kind)) {
                quests();
            } else if ("quest".equals(o.kind)) {
                text.addPara("Steps of " + o.a + ":");
                steps(o.a);
            } else if ("owners".equals(o.kind)) {
                ownerMarkets(text);
            } else if ("ownerMarket".equals(o.kind)) {
                text.addPara("New owner of " + o.a + ":");
                ownerFactions(o.a);
            } else if ("ownerSet".equals(o.kind)) {
                setOwner(o.a, o.b, text);
                main();
            } else if ("relations".equals(o.kind)) {
                relations(text);
            } else if ("relationSet".equals(o.kind)) {
                setRelation(o.a, o.b, text);
                main();
            } else if ("mods".equals(o.kind)) {
                mods(text);
                main();
            } else if ("settings".equals(o.kind)) {
                settings(text);
                main();
            } else if ("versions".equals(o.kind)) {
                versions(text);
                main();
            } else if ("snapshot".equals(o.kind)) {
                try {
                    text.addPara("Sector snapshot: " + SectorSnapshot.write() + ".");
                } catch (Throwable t) {
                    SagaLog.error("dev: writing the sector snapshot failed", t);
                    text.addPara("The sector snapshot couldn't be written: " + t.getMessage() + ". See starsector.log.", Misc.getNegativeHighlightColor());
                }
                main();
            } else if ("reload".equals(o.kind)) {
                String s = DevTools.reload();
                text.addPara("Reloaded: " + s + ". Systems and fleets already in the game are not rebuilt.");
                if (Saga.content().problems.errors() > 0) text.addPara("There are errors: see starsector.log ([SagaLib]).", Misc.getNegativeHighlightColor());
                main();
            } else if ("profile".equals(o.kind)) {
                ProfileDef p = Saga.content().profiles.get(o.a);
                if (p != null) DevTools.applyProfile(p, newGame, text);
                // a profile that moves the fleet closes the menu, so the map shows the new place (1.2)
                if (newGame || (p != null && p.startAt != null)) dialog.dismiss();
                else main();
            } else if ("step".equals(o.kind)) {
                QuestRuntime.step(o.a, o.b, "SagaLib dev menu", dialog, null, true);
                text.addPara("Quest " + o.a + " is now at step " + o.b + ".");
                QuestRuntime.syncJournal();
                quests();
            }
        } catch (Throwable t) {
            SagaLog.error("dev menu action " + o.kind + " failed", t);
            text.addPara("That failed: see starsector.log.", Misc.getNegativeHighlightColor());
        }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {}

    @Override
    public void advance(float amount) {}

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {}

    @Override
    public Object getContext() { return null; }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() { return null; }
}
