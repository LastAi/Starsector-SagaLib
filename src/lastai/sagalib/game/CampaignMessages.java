package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;
import lastai.sagalib.SagaLog;

import java.awt.Color;

/**
 * SagaLib 1.4 (quiet ops): what the player gets or loses outside a dialog, as vanilla's campaign
 * messages in the lower-left corner (a {@link MessageIntel}, as CoreReputationPlugin uses for
 * reputation without a text panel) instead of the dialog's "Gained …" lines. Never throws.
 */
public final class CampaignMessages {
    private CampaignMessages() {}

    /** One line; {@code %s} in the text is the highlighted value. */
    static void send(String text, String value, Color valueColor, String icon) {
        try {
            MessageIntel m = new MessageIntel(text, null, new String[]{value}, valueColor);
            if (icon != null && !icon.isEmpty()) m.setIcon(icon);
            Global.getSector().getCampaignUI().addMessage(m);
        } catch (Throwable t) {
            SagaLog.error("campaign message \"" + text.replace("%s", value) + "\" failed", t);
        }
    }

    static String intelIcon(String key) {
        try {
            return Global.getSettings().getSpriteName("intel", key);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void credits(long amount) {
        String v = Misc.getWithDGS(Math.abs(amount)) + Strings.C;
        send(amount >= 0 ? "Gained %s" : "Lost %s", v, Misc.getHighlightColor(), intelIcon("monthly_income_report"));
    }

    public static void item(String id, int qty) {
        if (qty == 0) return;
        String name = id, icon = null;
        try {
            Items.Kind k = Items.kindOf(id);
            if (k == Items.Kind.COMMODITY) {
                name = Global.getSettings().getCommoditySpec(id).getLowerCaseName();
                icon = Global.getSettings().getCommoditySpec(id).getIconName();
            } else if (k == Items.Kind.SPECIAL) {
                name = Global.getSettings().getSpecialItemSpec(id).getName();
                icon = Global.getSettings().getSpecialItemSpec(id).getIconName();
            } else if (k == Items.Kind.WEAPON) {
                name = Global.getSettings().getWeaponSpec(id).getWeaponName();
            } else if (k == Items.Kind.FIGHTER) {
                name = Global.getSettings().getFighterWingSpec(id).getWingName();
            }
        } catch (Throwable ignored) {
            // the id as the name
        }
        String v = Misc.getWithDGS(Math.abs(qty)) + Strings.X;
        send((qty > 0 ? "Gained %s " : "Lost %s ") + name, v, Misc.getHighlightColor(), icon);
    }

    public static void storyPoints(int points) {
        if (points == 0) return;
        int n = Math.abs(points);
        send((points > 0 ? "Gained %s " : "Lost %s ") + (n == 1 ? "Story Point" : "Story Points"), String.valueOf(n), Misc.getStoryOptionColor(),
                Global.getSector().getPlayerPerson().getPortraitSprite());
    }
}
