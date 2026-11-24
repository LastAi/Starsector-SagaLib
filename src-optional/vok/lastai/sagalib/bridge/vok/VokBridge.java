package lastai.sagalib.bridge.vok;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import data.kaysaar.aotd.vok.campaign.econ.listeners.AoTDListenerUtilis;
import data.kaysaar.aotd.vok.scripts.research.AoTDFactionResearchManager;
import data.kaysaar.aotd.vok.scripts.research.AoTDMainResearchManager;
import data.kaysaar.aotd.vok.scripts.research.models.ResearchOption;
import lastai.sagalib.game.ResearchBridge;

/**
 * SagaLib's AoTD Vaults of Knowledge bridge (task #77), checked against VoK 5.0.7 (Saga Forge
 * FORMATS.md, "AoTD - Vaults of Knowledge 5.0.7"). Loaded by name only when VoK is enabled
 * (game/VokResearch). Granting does what VoK's console command research_tech does: the player's
 * faction's research option is set researched, the research focus cleared if it was this tech, and
 * VoK's finished-research listeners run.
 */
public class VokBridge implements ResearchBridge {
    private static AoTDFactionResearchManager player() {
        AoTDMainResearchManager m = AoTDMainResearchManager.getInstance();
        return m == null ? null : m.getManagerForPlayer();
    }

    @Override
    public boolean known(String techId) {
        AoTDFactionResearchManager p = player();
        return p != null && p.getResearchOptionFromRepo(techId) != null;
    }

    @Override
    public boolean researched(String techId, String factionId) {
        AoTDMainResearchManager m = AoTDMainResearchManager.getInstance();
        if (m == null) return false;
        if (factionId == null) return m.isResearchedForPlayer(techId);
        FactionAPI f = Global.getSector().getFaction(factionId);
        return f != null && m.haveFactionResearchedCertainTech(f, techId);
    }

    @Override
    public String grant(String techId) {
        AoTDFactionResearchManager p = player();
        ResearchOption o = p == null ? null : p.getResearchOptionFromRepo(techId);
        if (o == null) throw new IllegalStateException("Vaults of Knowledge has no research option " + techId + " for the player's faction");
        if (o.isResearched()) return null;
        o.setResearched(true);
        ResearchOption focus = p.getCurrentFocus();
        if (focus != null && focus.getSpec() != null && techId.equals(focus.getSpec().getId())) p.setCurrentFocus(null);
        AoTDListenerUtilis.finishedResearch(techId, p.getFaction());
        return o.Name == null || o.Name.isEmpty() ? techId : o.Name;
    }
}
