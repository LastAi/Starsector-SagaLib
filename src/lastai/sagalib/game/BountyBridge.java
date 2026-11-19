package lastai.sagalib.game;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;

/**
 * SagaLib 1.3, task #59: what SagaLib asks MagicLib about bounties. Implemented only by the bridge
 * in src-optional/magiclib (compiled against MagicLib's jar, loaded by class name when MagicLib is
 * enabled: {@link MagicBounties}), so nothing here names a MagicLib class.
 */
public interface BountyBridge {
    /** MagicLib has this bounty: read from an enabled mod's file and valid, or already created. */
    boolean known(String id);

    /** The bounty's memory key (its job_memKey, "$" + id by default); null if MagicLib doesn't have it. */
    String memKey(String id);

    /**
     * Creates the bounty if MagicLib hasn't yet (its place is picked and the fleet spawned then) and
     * accepts it for the player, as MagicLib's own board does. null = accepted, else why not.
     */
    String accept(String id, InteractionDialogAPI dialog);
}
