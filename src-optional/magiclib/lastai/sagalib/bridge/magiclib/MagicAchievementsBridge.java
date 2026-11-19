package lastai.sagalib.bridge.magiclib;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import lastai.sagalib.game.AchievementBridge;
import org.magiclib.achievements.MagicAchievement;
import org.magiclib.achievements.MagicAchievementManager;

/**
 * SagaLib's MagicLib achievements bridge (task #65), checked against MagicLib 1.5.6 (Saga Forge
 * FORMATS.md, "MagicLib 1.5.6 achievements"). Loaded by name only when MagicLib is enabled
 * (game/Achievements). Does what MagicAchievementManager.completeAchievement(id) does, with the
 * player and the dialog's text panel so MagicLib shows its notice in the conversation.
 */
public class MagicAchievementsBridge implements AchievementBridge {
    @Override
    public String complete(String id, TextPanelAPI text) {
        MagicAchievementManager m = MagicAchievementManager.getInstance();
        if (!m.areAchievementsEnabled()) return "achievements are turned off in MagicLib's settings";
        MagicAchievement a = m.getAchievement(id);
        if (a == null) return "MagicLib has no achievement \"" + id + "\" (not in an enabled mod's data/config/magic_achievements.csv, or the row couldn't be read: see MagicLib's lines in starsector.log)";
        if (a.isComplete()) return null;
        a.completeAchievement(Global.getSector() == null ? null : Global.getSector().getPlayerPerson(), text);
        a.saveChanges();
        return null;
    }
}
