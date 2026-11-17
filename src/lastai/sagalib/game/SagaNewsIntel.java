package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.misc.FleetLogIntel;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.Set;

/**
 * A news item of a world event (SagaLib 1.2): a titled intel entry with text, an icon, a chosen
 * intel tab and a place on the map, removed after its days (vanilla's FleetLogIntel does that).
 * Saved in the player's save: this class name must never change, and it holds strings only.
 */
public class SagaNewsIntel extends FleetLogIntel {
    private final String title;
    private final String text;
    private final String tag;
    private final String at;
    /** The world event it belongs to (null in news items of saves from before it was kept). */
    private final String event;

    public SagaNewsIntel(String event, String title, String text, String icon, String tag, String at, float days) {
        super(days);
        this.event = event;
        this.title = title == null ? "" : title;
        this.text = text == null ? "" : text;
        this.tag = tag == null || tag.isEmpty() ? Tags.INTEL_MAJOR_EVENT : tag;
        this.at = at == null ? "" : at;
        if (icon != null && !icon.isEmpty() && Textures.ensure(icon)) setIcon(icon);
    }

    public String getEvent() { return event; }

    @Override
    public String getName() { return title; }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        Color c = getTitleColor(mode);
        info.addPara(getName(), c, 0f);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addPara(text, 10f);
        addLogTimestamp(info, Misc.getGrayColor(), 10f);
        addDeleteButton(info, width);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.remove(Tags.INTEL_FLEET_LOG);
        tags.add(tag);
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (at.isEmpty()) return null;
        SectorEntityToken e = Global.getSector().getEntityById(at);
        if (e != null) return e;
        MarketAPI m = Global.getSector().getEconomy().getMarket(at);
        return m != null ? m.getPrimaryEntity() : null;
    }
}
