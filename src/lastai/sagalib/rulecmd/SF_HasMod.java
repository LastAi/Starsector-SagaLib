package lastai.sagalib.rulecmd;

import lastai.sagalib.game.ModCondition;

/**
 * SagaLib 1.3. Condition SF_HasMod &lt;mod id&gt; [more ids…]: every listed mod counts: enabled
 * when this game started and still enabled (core.Mods). Negate with "!SF_HasMod &lt;id&gt;".
 */
public class SF_HasMod extends ModCondition {
    @Override
    protected String usage() { return "<mod id> [more mod ids]"; }
}
