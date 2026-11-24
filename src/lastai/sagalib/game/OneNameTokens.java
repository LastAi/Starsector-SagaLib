package lastai.sagalib.game;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.rules.RuleTokenReplacementGeneratorPlugin;
import com.fs.starfarer.api.campaign.rules.RulesAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import lastai.sagalib.SagaLog;
import lastai.sagalib.core.Names;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * SagaLib 1.4 (task #79): $personFirstName and $PersonFirstName for a one-name active person (first
 * name blank, last name not: any mod's, not only Saga Forge's) are the one name instead of vanilla's
 * empty first name (core.Names; docs/FORMAT.md, "One-name characters").
 * <p>
 * The engine merges every generator's map in list order ({@code putAll}: a later generator's value
 * wins for the same token) before it replaces anything (javap, 0.98a-RC8: CampaignEngine$3). Vanilla
 * adds its CoreRuleTokenReplacementGeneratorImpl in CoreLifecyclePluginImpl.onGameLoad, and the core
 * plugin's onGameLoad runs before every mod's (ModManager.getEnabledModPlugins puts it first). So this
 * one is registered in SagaLib's onGameLoad only (not with the phrases on a new game, which would put
 * it before vanilla's): it comes after vanilla's and its value wins. The person is found as vanilla
 * finds it: the dialog target's active person, else a fleet's commander.
 */
public final class OneNameTokens implements RuleTokenReplacementGeneratorPlugin {
    private static WeakReference<RulesAPI> registeredOn = new WeakReference<RulesAPI>(null);

    /** Adds the generator to the current sector's rules once (call from onGameLoad, after vanilla's). */
    public static void register() {
        RulesAPI rules = Global.getSector().getRules();
        if (rules == null || registeredOn.get() == rules) return;
        rules.addTokenReplacementGenerator(new OneNameTokens());
        registeredOn = new WeakReference<RulesAPI>(rules);
    }

    /** The person vanilla's generator fills $person… tokens for. */
    static PersonAPI activePerson(Object entity) {
        PersonAPI person = null;
        if (entity instanceof SectorEntityToken) person = ((SectorEntityToken) entity).getActivePerson();
        if (person == null) {
            if (entity instanceof CampaignFleetAPI) person = ((CampaignFleetAPI) entity).getCommander();
            else if (entity instanceof PersonAPI) person = (PersonAPI) entity;
        }
        return person;
    }

    @Override
    public Map<String, String> getTokenReplacements(String ruleId, Object entity, Map<String, MemoryAPI> memoryMap) {
        try {
            PersonAPI p = activePerson(entity);
            FullName n = p == null ? null : p.getName();
            if (n == null) return Collections.emptyMap();
            Map<String, String> tokens = Names.personTokens(n.getFirst(), n.getLast());
            if (tokens.isEmpty()) return tokens;
            // the game uses each value as a regex replacement (docs/FORMATS.md, Token replacement)
            Map<String, String> out = new HashMap<String, String>();
            for (Map.Entry<String, String> e : tokens.entrySet()) out.put(e.getKey(), java.util.regex.Matcher.quoteReplacement(e.getValue()));
            return out;
        } catch (Throwable t) {
            SagaLog.errorOnce("one-name-tokens", "filling one-name first-name tokens failed; vanilla's stay", t);
            return Collections.emptyMap();
        }
    }
}
