package lastai.sagalib.game;

import com.fs.starfarer.api.campaign.TextPanelAPI;

/**
 * SagaLib 1.3, task #65: what SagaLib asks MagicLib about achievements. Implemented only by the bridge
 * in src-optional/magiclib (compiled against MagicLib's jar, loaded by class name when MagicLib is
 * enabled: {@link Achievements}), so nothing here names a MagicLib class.
 */
public interface AchievementBridge {
    /**
     * Completes a MagicLib achievement (one in an enabled mod's magic_achievements.csv) for the player,
     * with MagicLib's own notice in {@code text} when not null. Already complete: nothing changes.
     * null = done (or already complete), else why not.
     */
    String complete(String id, TextPanelAPI text);
}
