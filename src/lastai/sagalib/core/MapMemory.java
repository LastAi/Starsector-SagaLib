package lastai.sagalib.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** A plain map of full keys ("$global.x") to values. Used by the tests. */
public final class MapMemory implements Memory {
    public final Map<String, Object> values = new LinkedHashMap<String, Object>();
    /** SagaLib 1.4: the days a key was set to expire after (the tests check it; nothing expires here). */
    public final Map<String, Float> expires = new LinkedHashMap<String, Float>();

    @Override
    public Object get(String key) { return values.get(key); }

    @Override
    public void set(String key, Object value) {
        expires.remove(key);
        if (value == null) values.remove(key);
        else values.put(key, value);
    }

    @Override
    public void set(String key, Object value, float days) {
        set(key, value);
        if (value != null) expires.put(key, days);
    }

    @Override
    public void unset(String key) {
        values.remove(key);
        expires.remove(key);
    }
}
