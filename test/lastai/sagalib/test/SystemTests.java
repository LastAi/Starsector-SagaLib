package lastai.sagalib.test;

import lastai.sagalib.core.Content;
import lastai.sagalib.core.JsonReader;
import lastai.sagalib.core.MapMemory;
import lastai.sagalib.core.Problems;
import lastai.sagalib.core.SystemPlan;
import lastai.sagalib.core.WorldChanges;
import lastai.sagalib.model.SystemDef;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Star systems: reading, focus-first ordering, additions to existing systems, the example mod's files. */
public final class SystemTests {
    static SystemDef system(String singleQuoted) throws Exception {
        Problems p = new Problems();
        SystemDef s = SystemDef.parse(Fx.json(singleQuoted), new JsonReader("sys.json", p));
        T.yes(p.isEmpty(), "fixture: " + p.all());
        return s;
    }

    static String ent(String id, String kind, String focus) {
        return "{'id':'" + id + "','kind':'" + kind + "','type':'t','orbit':{'focus':'" + focus + "','radius':100,'angle':0,'days':10}}";
    }

    static List<String> ids(SystemPlan.Order o) {
        List<String> out = new ArrayList<String>();
        for (SystemDef.Entity e : o.entities) out.add(e.id);
        return out;
    }

    static final SystemPlan.Exists NOTHING = new SystemPlan.Exists() {
        @Override
        public boolean exists(String id) { return false; }
    };

    static SystemPlan.Exists existing(final String... ids) {
        final Set<String> set = new HashSet<String>(Arrays.asList(ids));
        return new SystemPlan.Exists() {
            @Override
            public boolean exists(String id) { return set.contains(id); }
        };
    }

    /** SagaLib 1.1: no star, deferred entities, replacements, removals and planet conditions are read; deferred ones aren't built with the system. */
    public static void worldChangesFormat() throws Exception {
        SystemDef s = system("{'format':1,'id':'s','kind':'new','hyperspace':[0,0],'star':null,'light':[200,180,255],'entities':["
                + ent("planet", "planet", "star") + ","
                + "{'id':'probe','kind':'derelict','type':'wreck','appears':{'quest':'q','stage':'find'},'orbit':{'focus':'planet','radius':300,'angle':0,'days':10}},"
                + "{'id':'ruin','kind':'station','type':'t','appears':{'quest':'q','stage':'boom'},'replaces':'outpost','orbit':{'focus':'planet','radius':300,'angle':0,'days':10}},"
                + "{'id':'later','kind':'custom','type':'t','appears':{},'orbit':{'focus':'star','radius':900,'angle':0,'days':10}},"
                + "{'id':'moon','kind':'moon','type':'barren','conditions':['ruins_scattered','no_atmosphere'],'surveyed':'full','orbit':{'focus':'planet','radius':500,'angle':0,'days':10}}"
                + "],'removals':[{'target':'outpost','quest':'q','stage':'boom','market':'decivilize'},{'target':'jangala_relay','quest':'q','stage':'boom'}]}");
        T.eq(null, s.starType, "no star");
        T.eq(Arrays.asList(200, 180, 255), Arrays.asList(s.light[0], s.light[1], s.light[2]), "light colour");
        T.yes(s.entity("probe").deferred(), "appears = deferred");
        T.yes(s.entity("later").deferred() && s.entity("later").appears.quest == null, "appears {} = by SF_PlaceEntity only");
        T.eq("outpost", s.entity("ruin").replaces, "replaces");
        T.eq(Arrays.asList("ruins_scattered", "no_atmosphere"), s.entity("moon").conditions, "conditions");
        T.eq("full", s.entity("moon").surveyed, "surveyed");
        T.eq(2, s.removals.size(), "removals");
        T.eq("keep", s.removals.get(1).market, "market defaults to keep");
        T.eq(Arrays.asList("planet", "moon"), ids(SystemPlan.buildOrder(WorldChanges.initial(s.entities), NOTHING)), "deferred entities aren't built with the system");
        T.eq(Arrays.asList(), ids(SystemPlan.additions(s, existing("planet", "moon"), new MapMemory())), "nor added to a save");
    }

    /** SagaLib 1.1: what to place and remove when a quest reaches a stage, once. */
    public static void worldChangesDecide() throws Exception {
        SystemDef s = system("{'format':1,'id':'s','kind':'existing','entities':["
                + "{'id':'probe','kind':'derelict','type':'wreck','appears':{'quest':'q','stage':'find'},'orbit':{'focus':'star','radius':300,'angle':0,'days':10}}"
                + "],'removals':[{'target':'relay','quest':'q','stage':'boom'}]}");
        List<SystemDef> all = Arrays.asList(s);
        MapMemory m = new MapMemory();
        T.eq(0, WorldChanges.toPlace(all, m).size(), "quest not started");
        m.set(lastai.sagalib.core.QuestLogic.stateKey("q"), "active");
        m.set(lastai.sagalib.core.QuestLogic.stepKey("q"), "find");
        T.eq(1, WorldChanges.toPlace(all, m).size(), "at the stage: place");
        m.set(WorldChanges.placedKey("probe"), Boolean.TRUE);
        T.eq(0, WorldChanges.toPlace(all, m).size(), "placed once");
        T.eq(0, WorldChanges.toRemove(all, m).size(), "removal waits for its stage");
        m.set(lastai.sagalib.core.QuestLogic.stepKey("q"), "boom");
        T.eq("relay", WorldChanges.toRemove(all, m).get(0).target, "at its stage: remove");
        m.set(WorldChanges.removedKey("relay"), Boolean.TRUE);
        T.eq(0, WorldChanges.toRemove(all, m).size(), "removed once");
    }

    /** SagaLib 1.1: a quest's entity flags are on only while it is active at one of their steps. */
    public static void entityFlags() throws Exception {
        Problems p = new Problems();
        lastai.sagalib.model.QuestDef q = lastai.sagalib.model.QuestDef.parse(Fx.json("{'format':1,'id':'q','title':'Q','kind':'quest','start':{'kind':'manual'},"
                + "'stages':[{'id':'find','title':'F','journal':'','kind':'normal','renamedFrom':[]},{'id':'back','title':'B','journal':'','kind':'normal','renamedFrom':[]}],"
                + "'entityFlags':[{'entity':'probe','flag':'$sagaforge_q_arrive','steps':['find'],'important':true}]}"), new JsonReader("q.json", p));
        T.yes(p.isEmpty(), "fixture: " + p.all());
        T.eq(1, q.entityFlags.size(), "read");
        T.yes(q.entityFlags.get(0).important, "important");
        MapMemory m = new MapMemory();
        List<lastai.sagalib.model.QuestDef> qs = Arrays.asList(q);
        T.no(WorldChanges.entityFlags(qs, m).get(0).on, "quest not started: off");
        m.set(lastai.sagalib.core.QuestLogic.stateKey("q"), "active");
        m.set(lastai.sagalib.core.QuestLogic.stepKey("q"), "find");
        T.yes(WorldChanges.entityFlags(qs, m).get(0).on, "at its step: on");
        m.set(lastai.sagalib.core.QuestLogic.stepKey("q"), "back");
        T.no(WorldChanges.entityFlags(qs, m).get(0).on, "another step: off");
    }

    public static void focusComesFirst() throws Exception {
        SystemDef s = system("{'format':1,'id':'s','kind':'new','hyperspace':[0,0],'star':{'type':'star_yellow'},'entities':["
                + ent("station", "station", "moon") + "," + ent("moon", "moon", "planet") + "," + ent("planet", "planet", "star") + ","
                + ent("belt", "asteroidBelt", "star") + "]}");
        SystemPlan.Order o = SystemPlan.buildOrder(s.entities, NOTHING);
        T.eq(Arrays.asList("planet", "moon", "station", "belt"), ids(o), "focus before what orbits it; JSON order otherwise");
        T.eq(0, o.problems.size(), "no problems");
    }

    public static void badFocusIsReported() throws Exception {
        SystemDef s = system("{'format':1,'id':'s','kind':'new','hyperspace':[0,0],'star':{'type':'star_yellow'},'entities':["
                + ent("a", "planet", "b") + "," + ent("b", "planet", "a") + "," + ent("c", "planet", "nowhere") + "," + ent("d", "planet", "star") + "]}");
        SystemPlan.Order o = SystemPlan.buildOrder(s.entities, NOTHING);
        T.eq(Arrays.asList("d"), ids(o), "only d can be built");
        T.eq(3, o.problems.size(), "a, b and c reported");
        T.yes(o.problems.get(0).contains("focus loop"), "loop: " + o.problems);
        T.yes(o.problems.get(2).contains("\"nowhere\" doesn't exist"), "missing focus");
    }

    public static void additionsToExistingSystems() throws Exception {
        SystemDef s = system("{'format':1,'id':'corvus','kind':'existing','entities':["
                + ent("vgc_outpost", "station", "jangala") + "," + ent("vgc_relay", "commRelay", "star") + "," + ent("vgc_moon", "moon", "vgc_rock") + ","
                + ent("vgc_rock", "planet", "star") + "]}");
        T.no(s.isNew(), "existing");
        // jangala is vanilla's; vgc_relay was added by an earlier load; the rest are missing
        SystemPlan.Order o = SystemPlan.additions(s, existing("jangala", "vgc_relay"));
        T.eq(Arrays.asList("vgc_outpost", "vgc_rock", "vgc_moon"), ids(o), "only missing entities, focus first");
        o = SystemPlan.additions(s, existing("jangala", "vgc_relay", "vgc_outpost", "vgc_rock", "vgc_moon"));
        T.eq(0, o.entities.size(), "nothing left to add on the next load");
        o = SystemPlan.additions(s, existing("vgc_relay", "vgc_rock"));
        T.eq(Arrays.asList("vgc_moon"), ids(o), "a missing entity around an existing one is added");
        T.eq(1, o.problems.size(), "the outpost's focus (jangala) doesn't exist here: reported");

        // with the save's memory: an addition built once and gone now (a salvaged wreck) stays gone
        MapMemory mem = new MapMemory();
        o = SystemPlan.additions(s, existing("jangala", "vgc_relay", "vgc_outpost", "vgc_rock", "vgc_moon"), mem);
        T.eq(0, o.entities.size(), "all there");
        T.eq(Boolean.TRUE, mem.get(SystemPlan.addedKey("vgc_relay")), "existing additions are recorded (older saves)");
        o = SystemPlan.additions(s, existing("jangala", "vgc_outpost", "vgc_rock", "vgc_moon"), mem);
        T.eq(0, o.entities.size(), "the relay was removed in game: not built again");
        o = SystemPlan.additions(s, existing("jangala", "vgc_rock"), new MapMemory());
        T.eq(Arrays.asList("vgc_outpost", "vgc_relay", "vgc_moon"), ids(o), "a save that never had them gets them");
    }

    public static void readingErrors() throws Exception {
        Problems p = new Problems();
        T.eq(null, SystemDef.parse(Fx.json("{'format':1,'id':'s','kind':'new','star':{'type':'x'}}"), new JsonReader("f", p)), "no hyperspace");
        T.yes(p.has("$.hyperspace"), "hyperspace path: " + p.all());
        SystemDef s = SystemDef.parse(Fx.json("{'format':1,'id':'s','kind':'existing','entities':[{'id':'a','kind':'castle','orbit':{}},"
                + "{'id':'b','kind':'planet'},{'id':'c','kind':'planet','orbit':{'focus':'star'},'market':{'id':'m'}}," + ent("c", "planet", "star") + "]}"),
                new JsonReader("f", p));
        T.eq(1, s.entities.size(), "only c is kept");
        T.yes(p.has("$.entities[0].kind: unknown entity kind \"castle\""), "bad kind");
        T.yes(p.has("$.entities[1].orbit: missing"), "missing orbit");
        T.yes(p.has("$.entities[2].market.faction: missing"), "market without faction");
        T.eq(null, s.entities.get(0).market, "the bad market is dropped, the planet kept");
        T.yes(p.has("$.entities[3].id: entity \"c\" is listed twice"), "duplicate");
    }

    static final Path VGC = Paths.get(System.getProperty("sagalib.modDir", "."), "..", "VanguardCompact");

    /** Loads the example mod's whole content index from disk, as the game would. */
    static Content vanguard(final boolean dev) throws Exception {
        return Content.load(new Content.Source() {
            @Override
            public JSONArray index() throws Exception {
                List<String> lines = Files.readAllLines(VGC.resolve("data/config/sagaforge/content.csv"), StandardCharsets.UTF_8);
                JSONArray rows = new JSONArray();
                String[] head = lines.get(0).trim().split(",");
                for (String l : lines.subList(1, lines.size())) {
                    if (l.trim().isEmpty() || l.startsWith("#")) continue;
                    String[] cells = l.trim().split(",", -1);
                    JSONObject r = new JSONObject();
                    for (int i = 0; i < head.length && i < cells.length; i++) r.put(head[i], cells[i]);
                    rows.put(r);
                }
                return rows;
            }

            @Override
            public JSONObject load(String path, String mod) throws Exception {
                return new JSONObject(new String(Files.readAllBytes(VGC.resolve(path)), StandardCharsets.UTF_8));
            }

            @Override
            public boolean devMode() { return dev; }
        });
    }

    public static void vanguardKestrelLoads() throws Exception {
        Path file = VGC.resolve("data/sagaforge/systems/vgc_kestrel.json");
        T.yes(Files.exists(file), "example file exists: " + file);
        Problems p = new Problems();
        SystemDef s = SystemDef.parse(new JSONObject(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)), new JsonReader("vgc_kestrel.json", p));
        T.yes(p.isEmpty(), "no problems: " + p.all());
        T.yes(s.isNew(), "a new system");
        T.yes(s.addToExistingSaves, "addToExistingSaves");
        T.eq("sagaforge:vgc_kestrel", s.tag(), "tag");
        T.yes(s.hasJumpPoint(), "it lists a jump point");
        SystemDef.Entity haven = s.entity("vgc_haven");
        T.eq("vgc_vanguard", haven.market.faction, "market faction");
        T.eq(Arrays.asList("vgc_reyes"), haven.market.people, "market people");
        SystemPlan.Order o = SystemPlan.buildOrder(s.entities, NOTHING);
        T.eq(0, o.problems.size(), "every focus resolves: " + o.problems);
        T.yes(ids(o).indexOf("vgc_kestrel_i") < ids(o).indexOf("vgc_haven"), "the planet before its station");
        T.eq(s.entities.size(), o.entities.size(), "all entities built");
    }

    public static void vanguardWholeModLoads() throws Exception {
        Content c = vanguard(true);
        T.eq(0, c.problems.errors(), "no errors loading VanguardCompact: " + c.problems.all());
        T.yes(c.systems.containsKey("vgc_kestrel"), "system");
        T.yes(c.quests.containsKey("vgc_lost_convoy"), "quest");
        T.yes(c.people.containsKey("vgc_reyes"), "person");
        T.yes(c.profiles.size() > 0, "profiles in dev mode");
        T.eq(1, c.encounters.size(), "encounter file");
    }
}
