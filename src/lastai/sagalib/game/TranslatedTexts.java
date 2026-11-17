package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.loading.Description;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Content;
import lastai.sagalib.model.SystemDef;
import lastai.sagalib.systems.SystemBuilder;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * SagaLib 1.2: the parts of a translation that aren't read from SagaLib's own files.
 * <ul>
 *   <li>{@link #applySpecs()} (application load): names of the mod's items and its descriptions,
 *   which the game read from CSV files.</li>
 *   <li>{@link #applySave()} (every game load): faction names, and the names of the mod's systems,
 *   places and markets, which the save keeps from when they were made. A name is changed only
 *   while it is still the one SagaLib gave (remembered in the object's memory, or an original
 *   text), so a colony the player renamed keeps the player's name.</li>
 * </ul>
 * People's names need nothing here: SagaLib sets them on every load.
 */
public final class TranslatedTexts {
    private TranslatedTexts() {}

    /** Memory key: the name SagaLib last gave this object. */
    public static final String NAME_KEY = "$sagaforge_name";

    public static void applySpecs() {
        Content c = Saga.content();
        int n = 0;
        Map<String, Map<String, String>> items = c.translatedSpecs.get("items");
        if (items != null) for (Map.Entry<String, Map<String, String>> e : items.entrySet()) {
            String name = e.getValue().get("name");
            String desc = e.getValue().get("desc"); // a special item's own description (a commodity's is in descriptions)
            try {
                CommoditySpecAPI cs = Global.getSettings().getCommoditySpec(e.getKey());
                if (cs != null) { if (name != null) cs.setName(name); n++; continue; }
            } catch (Throwable ignored) { /* not a commodity */ }
            try {
                SpecialItemSpecAPI ss = Global.getSettings().getSpecialItemSpec(e.getKey());
                if (ss != null) {
                    if (name != null) ss.setName(name);
                    if (desc != null) ss.setDesc(desc);
                    n++;
                    continue;
                }
            } catch (Throwable ignored) { /* not a special item */ }
            SagaLog.warn("translation: item " + e.getKey() + " isn't a commodity or special item; its name stays");
        }
        Map<String, Map<String, String>> descs = c.translatedSpecs.get("descriptions");
        if (descs != null) for (Map.Entry<String, Map<String, String>> e : descs.entrySet()) {
            int bar = e.getKey().lastIndexOf('|');
            if (bar <= 0) continue;
            try {
                Description d = Global.getSettings().getDescription(e.getKey().substring(0, bar), Description.Type.valueOf(e.getKey().substring(bar + 1)));
                if (d == null) continue;
                Map<String, String> f = e.getValue();
                if (f.containsKey("text1")) d.setText1(f.get("text1"));
                if (f.containsKey("text2")) d.setText2(f.get("text2"));
                if (f.containsKey("text3")) d.setText3(f.get("text3"));
                n++;
            } catch (Throwable t) {
                SagaLog.warn("translation: description " + e.getKey() + " couldn't be changed (" + t + ")");
            }
        }
        if (n > 0) SagaLog.info("translation: " + n + " item names and descriptions");
    }

    public static void applySave() {
        Content c = Saga.content();
        Set<String> originals = new HashSet<String>(c.originals.values());
        int n = 0;
        // factions: the override is saved, so it is also taken back when the translation is gone
        for (Map.Entry<String, Map<String, String>> e : c.specTexts("factions").entrySet()) {
            FactionAPI f = Global.getSector().getFaction(e.getKey());
            if (f == null) continue;
            Map<String, String> tr = c.translatedSpecs.containsKey("factions") ? c.translatedSpecs.get("factions").get(e.getKey()) : null;
            MemoryAPI mem = f.getMemoryWithoutUpdate();
            n += override(mem, "displayName", tr == null ? null : tr.get("displayName"), f.getDisplayName(), new Setter() { public void set(String v) { f.setDisplayNameOverride(v); } });
            n += override(mem, "displayNameWithArticle", tr == null ? null : tr.get("displayNameWithArticle"), f.getDisplayNameWithArticle(), new Setter() { public void set(String v) { f.setDisplayNameWithArticleOverride(v); } });
        }
        for (SystemDef s : c.systems.values()) {
            if (!s.isNew()) {
                for (SystemDef.Entity e : s.entities) n += entity(e, originals);
                continue;
            }
            StarSystemAPI sys = SystemBuilder.find(s);
            if (sys == null) continue;
            String old = sys.getBaseName();
            if (rename(sys.getMemoryWithoutUpdate(), old, s.name, originals)) {
                sys.setBaseName(s.name);
                SectorEntityToken star = sys.getStar();
                if (star != null && old.equals(star.getName())) star.setName(s.name);
                n++;
            }
            for (SystemDef.Entity e : s.entities) n += entity(e, originals);
        }
        if (n > 0) SagaLog.info("translation: " + n + " names in this save changed to the current language");
    }

    private static int entity(SystemDef.Entity e, Set<String> originals) {
        int n = 0;
        SectorEntityToken t = Global.getSector().getEntityById(e.id);
        if (t != null && e.name != null && !e.name.isEmpty() && rename(t.getMemoryWithoutUpdate(), t.getName(), e.name, originals)) {
            t.setName(e.name);
            n++;
        }
        if (e.market != null && e.market.name != null) {
            MarketAPI m = Global.getSector().getEconomy().getMarket(e.market.id);
            if (m != null && rename(m.getMemoryWithoutUpdate(), m.getName(), e.market.name, originals)) {
                m.setName(e.market.name);
                n++;
            }
        }
        return n;
    }

    /** True if the name should become {@code wanted}; remembers it either way. */
    static boolean rename(MemoryAPI mem, String current, String wanted, Set<String> originals) {
        if (wanted == null || wanted.isEmpty() || current == null) return false;
        String given = mem.getString(NAME_KEY);
        boolean ours = given != null ? current.equals(given) : (current.equals(wanted) || originals.contains(current));
        if (!ours) return false; // the player (or another mod) renamed it
        mem.set(NAME_KEY, wanted);
        return !current.equals(wanted);
    }

    private interface Setter { void set(String v); }

    /** Sets or takes back a faction name override; only one SagaLib set itself is taken back. */
    private static int override(MemoryAPI mem, String field, String translated, String current, Setter s) {
        String key = NAME_KEY + "_" + field;
        String given = mem.getString(key);
        if (translated != null) {
            if (translated.equals(current) && translated.equals(given)) return 0;
            s.set(translated);
            mem.set(key, translated);
            return 1;
        }
        if (given != null && given.equals(current)) {
            s.set(null);
            mem.unset(key);
            return 1;
        }
        return 0;
    }
}
