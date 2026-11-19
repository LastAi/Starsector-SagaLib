package lastai.sagalib.bridge.nexerelin;

import exerelin.campaign.questskip.BaseQuestSkipPlugin;
import exerelin.campaign.questskip.QuestSkipEntry;
import lastai.sagalib.game.QuestSkips;

/**
 * SagaLib's Nexerelin bridge (task #65), checked against Nexerelin 0.12.2d (Saga Forge FORMATS.md,
 * "Quest skip"). Saga Forge names it as the "plugin" of each quest chain it writes into the mod's
 * data/config/exerelin/questSkip.json, so only Nexerelin ever loads it.
 * <p>
 * A plugin is needed for the flags too: Nexerelin applies an entry's sectorMemFlags and playerMemFlags
 * only through a chain's or quest's plugin (applyMemKeys, here BaseQuestSkipPlugin's: every enabled
 * quest of the chain, in onNewGameAfterEconomyLoad). After the time pass, this gives each skipped
 * quest's rewards (game.QuestSkips). Nexerelin keeps plugins as singletons across games: no state here.
 */
public class SagaQuestSkipPlugin extends BaseQuestSkipPlugin {
    @Override
    public void onNewGameAfterTimePass() {
        if (chain == null) return;
        for (QuestSkipEntry q : chain.quests) {
            if (q.isEnabled) QuestSkips.skipped(q.id);
        }
    }
}
