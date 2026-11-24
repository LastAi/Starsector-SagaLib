package lastai.sagalib.core;

import lastai.sagalib.model.QuestDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * SagaLib 1.4 (task #75): commodity targets (docs/FORMAT.md "Commodity targets"). Pure part, shared
 * with Saga Forge's Play mode (shared/targets.ts) and the golden case commodity.json: the allowed
 * commodities, the amount in cargo units, the facts stored when one is picked, and credits for the
 * goods. The search is vanilla's pickCommodity, run by quest/TargetSearch in the game.
 * <pre>
 *   $global.&lt;quest&gt;_&lt;var&gt;              the market's id (plus the market's facts, as a market target)
 *   $global.&lt;quest&gt;_&lt;var&gt;_commodity    its name for texts (vanilla's getLowerCaseName)
 *   $global.&lt;quest&gt;_&lt;var&gt;_commodityId  its id
 *   $global.&lt;quest&gt;_&lt;var&gt;_amount       cargo units (Integer)
 *   $global.&lt;quest&gt;_&lt;var&gt;_basePrice    its base price (Integer, rounded)
 *   $global.&lt;quest&gt;_&lt;var&gt;_value        base price x amount (Integer, credits)
 * </pre>
 */
public final class Commodities {
    private Commodities() {}

    /** The 18 tradeable vanilla economy commodities (commodities.csv: not nonecon, not meta). */
    public static final List<String> VANILLA_GOODS = Collections.unmodifiableList(Arrays.asList(
            "supplies", "fuel", "crew", "marines", "food", "organics", "volatiles", "ore", "rare_ore", "metals",
            "rare_metals", "heavy_machinery", "domestic_goods", "organs", "drugs", "hand_weapons", "luxury_goods", "lobster"));

    public static final String[] FACTS = { "commodity", "commodityId", "amount", "basePrice", "value" };

    /** The commodity ids a pick may be, or null for any economy commodity. */
    public static List<String> allowed(QuestDef.CommodityPick c) {
        if (c == null || "vanilla".equals(c.which)) return VANILLA_GOODS;
        if ("any".equals(c.which)) return null;
        return new ArrayList<String>(c.ids);
    }

    /** Round half up (as the editor's Math.floor(x + 0.5)). */
    static long round(double x) { return (long) Math.floor(x + 0.5); }

    /** Cargo units: econ = n x econUnit (x the market's size with perSize), cargo = n; at least 1. */
    public static int cargoUnits(QuestDef.CommodityPick c, double econUnit, int size) {
        double n = c == null ? 0.1 : c.n;
        if (Double.isNaN(n) || Double.isInfinite(n)) n = 0;
        double raw = c != null && "cargo".equals(c.unit) ? n : n * (econUnit > 0 ? econUnit : 1) * (c != null && c.perSize ? Math.max(1, size) : 1);
        return (int) Math.max(1, round(raw));
    }

    /** Credits worth of the goods: base price x amount, rounded. */
    public static long value(double basePrice, int amount) { return round(Math.max(0, basePrice) * amount); }

    /** Stores a pick's commodity facts (the market's are stored as for a market target). */
    public static void store(Memory m, String quest, String var, QuestDef.CommodityPick c, String id, String name, double econUnit, double basePrice, int size) {
        int amount = cargoUnits(c, econUnit, size);
        m.set(Targets.factKey(quest, var, "commodity"), name);
        m.set(Targets.factKey(quest, var, "commodityId"), id);
        m.set(Targets.factKey(quest, var, "amount"), Integer.valueOf(amount));
        m.set(Targets.factKey(quest, var, "basePrice"), Integer.valueOf((int) round(Math.max(0, basePrice))));
        m.set(Targets.factKey(quest, var, "value"), Integer.valueOf((int) value(basePrice, amount)));
    }

    /** The picked commodity id, or null when nothing is picked. */
    public static String commodityId(Memory m, String quest, String var) {
        Object v = m.get(Targets.factKey(quest, var, "commodityId"));
        return v == null || String.valueOf(v).isEmpty() ? null : String.valueOf(v);
    }

    /** The picked amount in cargo units (0 when nothing is picked). */
    public static int amount(Memory m, String quest, String var) {
        Double n = Values.number(m.get(Targets.factKey(quest, var, "amount")));
        return n == null ? 0 : (int) Math.round(n);
    }

    /** SF_TargetCommodity … credits &lt;mult&gt; and a reward's targetCredits: mult x the stored value, rounded (0 when nothing is picked). */
    public static long credits(Memory m, String quest, String var, double mult) {
        Double v = Values.number(m.get(Targets.factKey(quest, var, "value")));
        if (v == null) return 0;
        return round(v * (Double.isNaN(mult) ? 1 : mult));
    }

    /** SF_TargetCommodity … has: the player's cargo holds the amount (false when nothing is picked). */
    public static boolean has(Memory m, String quest, String var, double inCargo) {
        return commodityId(m, quest, var) != null && inCargo >= amount(m, quest, var);
    }
}
