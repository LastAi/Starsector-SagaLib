package lastai.sagalib.bridge.lunalib;

import lastai.sagalib.game.SettingsBridge;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

/**
 * SagaLib's LunaLib bridge (task #62), checked against LunaLib 2.0.5 (Saga Forge FORMATS.md,
 * "LunaLib 2.0.5"). Compiled against LunaLib's jar by build.sh and loaded by name only when LunaLib
 * is enabled (game/ModSettings). Reads the player's values with LunaSettings' static getters (null
 * when LunaLib doesn't know the mod or field) and listens for settings saved in LunaLib's menu.
 */
public class LunaLibBridge implements SettingsBridge {
    @Override
    public Object value(String modId, String fieldId, String type) {
        if ("Boolean".equals(type)) return LunaSettings.getBoolean(modId, fieldId);
        if ("Int".equals(type)) return LunaSettings.getInt(modId, fieldId);
        if ("Double".equals(type)) return LunaSettings.getDouble(modId, fieldId);
        if ("Radio".equals(type) || "String".equals(type)) return LunaSettings.getString(modId, fieldId);
        return null;
    }

    @Override
    public void listen(final Listener l) {
        LunaSettings.addSettingsListener(new LunaSettingsListener() {
            @Override
            public void settingsChanged(String modId) { l.changed(modId); }
        });
    }
}
