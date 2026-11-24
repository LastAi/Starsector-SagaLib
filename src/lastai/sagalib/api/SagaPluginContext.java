package lastai.sagalib.api;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.Map;

/**
 * What a {@link SagaPlugin} gets to know about its call (SagaLib 1.4, task #79). SagaLib creates one
 * per call; a plugin only reads it. Part of SagaLib's stable API: methods may be added, none removed
 * within SagaLib 1.x.
 */
public final class SagaPluginContext {
    private final String questId;
    private final String nodeId;
    private final String ruleId;
    private final InteractionDialogAPI dialog;
    private final Map<String, MemoryAPI> memoryMap;

    /** SagaLib makes these (SF_RunPlugin); a mod's own tests may too. Any argument may be null. */
    public SagaPluginContext(String questId, String nodeId, String ruleId, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        this.questId = questId;
        this.nodeId = nodeId;
        this.ruleId = ruleId;
        this.dialog = dialog;
        this.memoryMap = memoryMap;
    }

    /** The quest the call comes from (the line's {@code quest=}); null for a world event, a progress stage or a hand-written line without it. */
    public String getQuestId() { return questId; }

    /** The quest's node the call comes from (the line's {@code node=}): the Plugin or Action node; null when not given. */
    public String getNodeId() { return nodeId; }

    /** The rules.csv rule that runs the line (for log messages); null outside a rule. */
    public String getRuleId() { return ruleId; }

    /**
     * The dialog the rule runs in, or null. A quest's dialog, or (a timer, an encounter's outcome, a
     * world event, a progress stage) the small dialog SagaLib opens on the player's fleet for the rule.
     */
    public InteractionDialogAPI getDialog() { return dialog; }

    /** The rule's memory map (MemKeys.LOCAL, GLOBAL, PLAYER, …, as rule commands get it), or null. */
    public Map<String, MemoryAPI> getMemoryMap() { return memoryMap; }

    /** There is a dialog with a text panel, so {@link #addText} shows something. */
    public boolean hasDialog() { return dialog != null && dialog.getTextPanel() != null; }

    /** Adds a paragraph to the dialog. False (nothing shown) when there is no dialog. */
    public boolean addText(String text) {
        TextPanelAPI p = panel();
        if (p == null || text == null) return false;
        p.addPara(text);
        return true;
    }

    /** Adds a paragraph in a color (e.g. Misc.getPositiveHighlightColor()). False when there is no dialog. */
    public boolean addText(String text, Color color) {
        TextPanelAPI p = panel();
        if (p == null || text == null) return false;
        if (color == null) p.addPara(text);
        else p.addPara(text, color);
        return true;
    }

    /** Adds a paragraph with these parts of it in the highlight color. False when there is no dialog. */
    public boolean addTextHighlighted(String text, String... highlights) {
        TextPanelAPI p = panel();
        if (p == null || text == null) return false;
        p.addPara(text);
        if (highlights != null && highlights.length > 0) p.highlightInLastPara(Misc.getHighlightColor(), highlights);
        return true;
    }

    private TextPanelAPI panel() { return dialog == null ? null : dialog.getTextPanel(); }

    @Override
    public String toString() {
        return "SagaPluginContext{quest=" + questId + ", node=" + nodeId + ", rule=" + ruleId + ", dialog=" + (dialog != null) + "}";
    }
}
