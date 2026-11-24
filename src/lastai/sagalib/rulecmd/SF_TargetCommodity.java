package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Commodities;
import lastai.sagalib.core.Values;
import lastai.sagalib.game.GameEffects;
import lastai.sagalib.game.GameMemory;
import lastai.sagalib.game.SagaCommand;

import java.util.List;
import java.util.Map;

/**
 * SF_TargetCommodity &lt;quest&gt; &lt;var&gt; has|take|give|credits [mult] (SagaLib 1.4, task #75): a
 * commodity target's goods (its amount of its commodity, core/Commodities):
 * <ul>
 * <li>has (a condition): the player's cargo holds the amount;</li>
 * <li>take / give: removes it from / adds it to the player's cargo, with vanilla's "Lost" / "Gained" line;</li>
 * <li>credits [mult]: pays mult (default 1) x base price x amount.</li>
 * </ul>
 * Nothing picked (the quest wasn't offered with a commodity): has is false, the others do nothing and log once.
 */
public class SF_TargetCommodity extends SagaCommand {
    @Override
    protected int minArgs() { return 3; }

    @Override
    protected String usage() { return "<quest> <var> has|take|give|credits [mult]"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        String q = args.get(0), v = args.get(1), what = args.get(2);
        GameMemory mem = GameMemory.global();
        String id = Commodities.commodityId(mem, q, v);
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        if ("has".equals(what)) return id != null && Commodities.has(mem, q, v, cargo.getCommodityQuantity(id));
        if (id == null) {
            SagaLog.warnOnce("tgtcom:" + q + ":" + v, "SF_TargetCommodity in rule " + ruleId + ": target " + v + " of " + q + " has no commodity picked; nothing done");
            return false;
        }
        GameEffects fx = new GameEffects(dialog == null ? null : dialog.getTextPanel());
        if ("credits".equals(what)) {
            Double mult = args.size() > 3 ? Values.number(args.get(3)) : Double.valueOf(1);
            long c = Commodities.credits(mem, q, v, mult == null ? 1 : mult);
            if (c != 0) fx.credits(c);
            return true;
        }
        int n = Commodities.amount(mem, q, v);
        if ("take".equals(what)) { fx.item(id, -n); return true; }
        if ("give".equals(what)) { fx.item(id, n); return true; }
        SagaLog.error("SF_TargetCommodity in rule " + ruleId + ": unknown \"" + what + "\" (has, take, give or credits)");
        return false;
    }
}
