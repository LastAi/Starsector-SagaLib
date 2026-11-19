package lastai.sagalib.rulecmd;

import lastai.sagalib.game.ModCondition;

/** SagaLib 1.3. Condition SF_HasAnyMod &lt;mod id&gt; [more ids…]: at least one of them counts (core.Mods). */
public class SF_HasAnyMod extends ModCondition {
    @Override
    protected String usage() { return "<mod id> [more mod ids]"; }
}
