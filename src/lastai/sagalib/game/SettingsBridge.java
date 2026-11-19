package lastai.sagalib.game;

/**
 * SagaLib 1.3 (task #62): what SagaLib asks LunaLib about mod settings. Implemented only by the
 * bridge in src-optional/lunalib (compiled against LunaLib's jar, loaded by class name when LunaLib
 * is enabled: {@link ModSettings}), so nothing here names a LunaLib class.
 */
public interface SettingsBridge {
    /** Called when the player saves a mod's settings in LunaLib's menu. */
    interface Listener {
        void changed(String modId);
    }

    /**
     * The player's value of a field (Boolean, Integer, Double or String by the LunaLib type:
     * Boolean, Int, Double, Radio/String), or null when LunaLib doesn't know it.
     */
    Object value(String modId, String fieldId, String type);

    /** Registers a listener for saved settings (LunaSettingsListener). */
    void listen(Listener l);
}
