package lastai.sagalib.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import lastai.sagalib.SagaLog;
import lastai.sagalib.game.Items;
import lastai.sagalib.game.SagaCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SF_Show image|faction|ships|cargo|heading|skills &lt;args...&gt;: widgets in the dialog's text
 * (PLAN.md §4.9), drawn through TextPanelAPI.addImage or beginTooltip() + TooltipMakerAPI.
 * <pre>
 *   SF_Show image &lt;category&gt; &lt;key&gt;     an image (one argument: an illustration key)
 *   SF_Show faction &lt;id&gt;                crest, name and relationship bar
 *   SF_Show ships &lt;variant...&gt;          a row of ship icons
 *   SF_Show cargo &lt;item:qty...&gt;         a cargo grid
 *   SF_Show heading "&lt;text&gt;"            a section heading
 *   SF_Show skills &lt;person&gt;             a person's skill panel
 * </pre>
 */
public class SF_Show extends SagaCommand {
    @Override
    protected int minArgs() { return 2; }

    @Override
    protected String usage() { return "image|faction|ships|cargo|heading|skills <args...>"; }

    @Override
    protected boolean run(String ruleId, InteractionDialogAPI dialog, List<String> args, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        TextPanelAPI text = dialog.getTextPanel();
        String what = args.get(0);
        List<String> rest = args.subList(1, args.size());
        if ("image".equals(what)) {
            if (rest.size() >= 2) text.addImage(rest.get(0), rest.get(1));
            else text.addImage("illustrations", rest.get(0));
            return true;
        }
        if ("faction".equals(what)) return faction(ruleId, text, rest.get(0));
        if ("ships".equals(what)) return ships(ruleId, text, rest);
        if ("cargo".equals(what)) return cargo(ruleId, text, rest);
        if ("heading".equals(what)) {
            StringBuilder b = new StringBuilder();
            for (int i = 1; i < params.size(); i++) {
                if (b.length() > 0) b.append(' ');
                b.append(params.get(i).getStringWithTokenReplacement(ruleId, dialog, memoryMap));
            }
            TooltipMakerAPI t = text.beginTooltip();
            t.addSectionHeading(b.toString(), Alignment.MID, 0f);
            text.addTooltip();
            return true;
        }
        if ("skills".equals(what)) {
            PersonAPI p = Global.getSector().getImportantPeople().getPerson(rest.get(0));
            if (p == null && dialog.getInteractionTarget() != null && "active".equals(rest.get(0))) p = dialog.getInteractionTarget().getActivePerson();
            if (p == null) {
                SagaLog.error("SF_Show skills in rule " + ruleId + ": unknown person \"" + rest.get(0) + "\"");
                return false;
            }
            TooltipMakerAPI t = text.beginTooltip();
            t.addSkillPanel(p, 0f);
            text.addTooltip();
            return true;
        }
        SagaLog.error("SF_Show in rule " + ruleId + ": unknown display \"" + what + "\" (" + usage() + ")");
        return false;
    }

    private boolean faction(String ruleId, TextPanelAPI text, String id) {
        FactionAPI f = Global.getSector().getFaction(id);
        if (f == null) {
            SagaLog.error("SF_Show faction in rule " + ruleId + ": unknown faction \"" + id + "\"");
            return false;
        }
        TooltipMakerAPI t = text.beginTooltip();
        TooltipMakerAPI img = t.beginImageWithText(f.getCrest(), 48f);
        img.addPara(f.getDisplayName(), f.getBaseUIColor(), 0f);
        img.addRelationshipBar(f, 3f);
        t.addImageWithText(0f);
        text.addTooltip();
        return true;
    }

    private boolean ships(String ruleId, TextPanelAPI text, List<String> variants) {
        List<FleetMemberAPI> ships = new ArrayList<FleetMemberAPI>();
        for (String v : variants) {
            FleetMemberAPI m = Items.ship(v);
            if (m != null) ships.add(m);
        }
        if (ships.isEmpty()) {
            SagaLog.error("SF_Show ships in rule " + ruleId + ": no known variant in " + variants);
            return false;
        }
        int cols = Math.min(ships.size(), 8);
        int rows = (ships.size() + cols - 1) / cols;
        TooltipMakerAPI t = text.beginTooltip();
        t.addShipList(cols, rows, 48f, Misc.getBasePlayerColor(), ships, 0f);
        text.addTooltip();
        return true;
    }

    private boolean cargo(String ruleId, TextPanelAPI text, List<String> entries) {
        CargoAPI cargo = Global.getFactory().createCargo(true);
        for (String e : entries) {
            int colon = e.lastIndexOf(':');
            String item = colon > 0 ? e.substring(0, colon) : e;
            int qty = 1;
            if (colon > 0) {
                try {
                    qty = Integer.parseInt(e.substring(colon + 1).trim());
                } catch (NumberFormatException ex) {
                    SagaLog.error("SF_Show cargo in rule " + ruleId + ": \"" + e + "\" is not item:qty");
                    continue;
                }
            }
            Items.change(cargo, item, qty, null);
        }
        if (cargo.isEmpty()) return false;
        TooltipMakerAPI t = text.beginTooltip();
        t.showCargo(cargo, 20, true, 0f);
        text.addTooltip();
        return true;
    }
}
