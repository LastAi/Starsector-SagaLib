package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseMissionHub;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lastai.sagalib.SagaLog;
import lastai.sagalib.model.PersonDef;

/**
 * Creates and places the content mods' people (docs/FORMAT.md, "People") on new game and on
 * every load. A person who already exists (from the save) is updated, never duplicated.
 */
public final class People {
    private People() {}

    /** Creates or updates everyone. Markets that don't exist yet (systems built later) are retried on the next call. */
    public static void applyAll() {
        int made = 0, waiting = 0;
        for (PersonDef d : Saga.content().people.values()) {
            try {
                boolean isNew = Global.getSector().getImportantPeople().getPerson(d.id) == null;
                if (!apply(d)) waiting++;
                if (isNew) made++;
            } catch (Throwable t) {
                SagaLog.error("person " + d.id + " (" + d.source + ") could not be set up", t);
            }
        }
        if (made > 0 || waiting > 0) SagaLog.info("people: " + made + " created, " + waiting + " waiting for their market");
    }

    /** False if the person's market doesn't exist yet. */
    static boolean apply(PersonDef d) {
        ImportantPeopleAPI ip = Global.getSector().getImportantPeople();
        PersonAPI p = ip.getPerson(d.id);
        boolean isNew = p == null;
        if (isNew) {
            p = Global.getFactory().createPerson();
            p.setId(d.id);
        }
        Gender g = "MALE".equals(d.gender) ? Gender.MALE : "FEMALE".equals(d.gender) ? Gender.FEMALE
                : (isNew ? ((d.id.hashCode() & 1) == 0 ? Gender.FEMALE : Gender.MALE) : p.getGender());
        p.setGender(g);
        p.setName(new FullName(d.firstName, d.lastName, g));
        if (Global.getSector().getFaction(d.faction) == null) SagaLog.error("person " + d.id + ": unknown faction \"" + d.faction + "\"");
        else p.setFaction(d.faction);
        if (!d.rank.isEmpty()) p.setRankId(d.rank);
        if (!d.post.isEmpty()) p.setPostId(d.post);
        if (!d.portrait.isEmpty() && Textures.ensure(d.portrait)) p.setPortraitSprite(d.portrait);
        if (!d.voice.isEmpty()) p.setVoice(d.voice);
        if (!d.personality.isEmpty()) p.setPersonality(d.personality);
        for (String t : d.tags) p.addTag(t);
        if (d.isContact()) {
            // contact types are vanilla's person tags (Tags.CONTACT_MILITARY = "military", ...)
            for (String t : d.contactTypes) p.addTag(t);
            p.setImportance(importance(d));
        }
        if (isNew) ip.addPerson(p); // so ShowPersonVisual false <id> and getPerson(id) find them

        boolean placed = true;
        if ("market".equals(d.placementAt)) {
            MarketAPI m = Global.getSector().getEconomy().getMarket(d.market);
            if (m == null) {
                placed = false;
                SagaLog.warnOnce("nomarket:" + d.id + ":" + d.market, "person " + d.id + ": market \"" + d.market
                        + "\" doesn't exist yet; placing them is retried on the next load");
            } else {
                place(p, m, d.inCommDirectory);
            }
        }
        if (d.isContact() && BaseMissionHub.get(p) == null) {
            // like vanilla's Sebestyen: a mission hub lets them offer their person_missions.csv quests right away;
            // finishing one of their quests makes them a potential contact (SagaQuest.create)
            BaseMissionHub.set(p, new BaseMissionHub(p));
        }
        return placed;
    }

    /**
     * A person listed in a built market's "people": one of the mods' people (created now if
     * needed), or an existing important person. Placed there in the comm directory.
     */
    public static void placeAt(String personId, MarketAPI m) {
        PersonDef d = Saga.content().people.get(personId);
        PersonAPI p = Global.getSector().getImportantPeople().getPerson(personId);
        if (d != null) {
            if (p == null || !"market".equals(d.placementAt)) {
                apply(d);
                p = Global.getSector().getImportantPeople().getPerson(personId);
            }
            if (p != null && !"market".equals(d.placementAt)) place(p, m, true);
            else if (p != null && !m.getId().equals(d.market)) {
                SagaLog.warn("person " + personId + " is listed in market " + m.getId() + " but placed at " + d.market + " by the people file");
            }
            return;
        }
        if (p == null) {
            SagaLog.error("market " + m.getId() + ": unknown person \"" + personId + "\" in its people list");
            return;
        }
        place(p, m, true);
    }

    private static void place(PersonAPI p, MarketAPI m, boolean inCommDirectory) {
        MarketAPI old = p.getMarket();
        if (old != null && old != m) {
            old.removePerson(p);
            if (old.getCommDirectory() != null) old.getCommDirectory().removePerson(p);
        }
        if (!m.getPeopleCopy().contains(p)) m.addPerson(p);
        p.setMarket(m);
        if (m.getCommDirectory() != null) {
            boolean listed = m.getCommDirectory().getEntryForPerson(p) != null;
            if (inCommDirectory && !listed) m.getCommDirectory().addPerson(p);
            else if (!inCommDirectory && listed) m.getCommDirectory().removePerson(p);
        }
        Global.getSector().getImportantPeople().getData(p).getLocation().setMarket(m);
        replaceGeneratedStaff(p, m);
    }

    /** Posts vanilla fills with a generated person at every market (CoreLifecyclePluginImpl.createInitialPeople). */
    static final Set<String> STAFF_POSTS = new HashSet<String>(Arrays.asList(
            Ranks.POST_BASE_COMMANDER, Ranks.POST_STATION_COMMANDER, Ranks.POST_OUTPOST_COMMANDER,
            Ranks.POST_PORTMASTER, Ranks.POST_SUPPLY_OFFICER, Ranks.POST_ADMINISTRATOR));

    /**
     * A content person who holds one of those posts takes it over: vanilla's generated person
     * with the same post at that market leaves (Haven Station had two station commanders, found
     * in game 2026-11-11). Only people vanilla checked out as nothing but "permanent_staff" are
     * removed, so contacts, mission givers and other mods' people stay. If the one removed ran
     * the market, the content person does now.
     */
    static void replaceGeneratedStaff(PersonAPI p, MarketAPI m) {
        String post = p.getPostId();
        if (post == null || !STAFF_POSTS.contains(post)) return;
        ImportantPeopleAPI ip = Global.getSector().getImportantPeople();
        for (PersonAPI other : m.getPeopleCopy()) {
            if (other == p || !post.equals(other.getPostId()) || Saga.content().people.containsKey(other.getId())) continue;
            ImportantPeopleAPI.PersonDataAPI data = ip.getData(other);
            if (data == null) continue;
            Set<String> out = data.getCheckedOutFor();
            if (out == null || out.size() != 1 || !out.contains("permanent_staff")) continue;
            m.removePerson(other);
            if (m.getCommDirectory() != null) m.getCommDirectory().removePerson(other);
            if (m.getAdmin() == other) m.setAdmin(p);
            ip.removePerson(other);
            SagaLog.info("person " + p.getId() + " takes over the post " + post + " at " + m.getId() + " from vanilla's generated "
                    + other.getNameString());
        }
    }

    private static PersonImportance importance(PersonDef d) {
        try {
            return PersonImportance.valueOf(d.importance);
        } catch (Throwable t) {
            SagaLog.warn("person " + d.id + ": unknown importance \"" + d.importance + "\"; using MEDIUM");
            return PersonImportance.MEDIUM;
        }
    }
}
