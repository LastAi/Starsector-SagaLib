package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CommDirectoryAPI;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseMissionHub;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Memory;
import lastai.sagalib.core.Places;
import lastai.sagalib.core.Presence;
import lastai.sagalib.model.PersonDef;

import java.util.ArrayList;
import java.util.List;

/**
 * SagaLib 1.3 (task #70): people present only while their market's owner gets on with a faction
 * (core.Presence). Once a day (RuntimeScript, with #61's owner check), on load, and after the dev
 * menu changes an owner: the away flags are set, and each person's comm-directory entry at their
 * market is hidden while away (setHidden: they stay at the market, so it is save-safe and
 * reversible). With "elsewhere" they are listed in that market's comm directory while away.
 */
public final class PresenceRuntime {
    private PresenceRuntime() {}

    private static List<PersonDef> people() {
        List<PersonDef> out = new ArrayList<PersonDef>();
        for (PersonDef d : Saga.content().people.values()) {
            if (d.presence == null || People.isGone(d.id)) continue;
            if (!lastai.sagalib.core.Mods.allowed(GameMemory.global(), d.gate, "person", d.id)) continue;
            out.add(d);
        }
        return out;
    }

    /** The daily check: log who leaves or comes back, and show it in the comm directories. Returns how many moved. */
    public static int tick(Memory mem) {
        List<PersonDef> ps = people();
        if (ps.isEmpty()) return 0;
        int n = 0;
        for (Presence.Move mv : Presence.check(mem, ps, Places.world())) {
            PersonDef d = Saga.content().people.get(mv.person);
            String owner = mv.owner;
            SagaLog.info("person " + mv.person + (mv.away ? " leaves " : " is back at ") + d.market + ": its owner " + owner + " is "
                    + (mv.away ? "below " : "at least ") + d.presence.atLeast + " with " + d.presence.faction
                    + (mv.away && d.presence.elsewhere != null ? "; they go to " + d.presence.elsewhere : "")
                    + (mv.changed ? "" : " (first check in this save)"));
            if (mv.away) forgetOffers(d);
            n++;
        }
        for (PersonDef d : ps) show(d, Presence.isAway(mem, d.id));
        return n;
    }

    /** On load (after People.applyAll): the same check, so the comm directories match the save. */
    public static void onLoad() {
        tick(GameMemory.global());
    }

    /**
     * Vanilla's mission hub keeps the offers it created (a transient list) until its next update, so
     * an offer seen before they left could still show where they went. A fresh hub (vanilla's class,
     * as People.apply sets it) lists offers anew, and SagaQuest.create refuses theirs while away.
     */
    static void forgetOffers(PersonDef d) {
        if (!d.isContact()) return;
        PersonAPI p = Global.getSector().getImportantPeople().getPerson(d.id);
        if (p == null || BaseMissionHub.get(p) == null) return;
        BaseMissionHub.set(p, new BaseMissionHub(p));
    }

    /** Hides or shows their entry at their market; lists them at "elsewhere" while away. */
    static void show(PersonDef d, boolean away) {
        PersonAPI p = Global.getSector().getImportantPeople().getPerson(d.id);
        if (p == null) return;
        MarketAPI home = Global.getSector().getEconomy().getMarket(d.market);
        if (home != null && home.getCommDirectory() != null) {
            CommDirectoryEntryAPI e = home.getCommDirectory().getEntryForPerson(p);
            if (e != null && e.isHidden() != away) e.setHidden(away);
        }
        if (d.presence.elsewhere == null) return;
        MarketAPI other = SectorPlaces.market(d.presence.elsewhere);
        if (other == null || other == home) {
            if (away) SagaLog.warnOnce("elsewhere:" + d.id, "person " + d.id + ": market \"" + d.presence.elsewhere + "\" (where they go while away) doesn't exist; they are nowhere");
            return;
        }
        // only a comm-directory entry: their market (getMarket) stays their home, so nothing else moves
        CommDirectoryAPI cd = other.getCommDirectory();
        if (cd == null) return;
        boolean listed = cd.getEntryForPerson(p) != null;
        if (away && !listed) cd.addPerson(p);
        else if (!away && listed) cd.removePerson(p);
    }
}
