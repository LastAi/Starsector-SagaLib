package lastai.sagalib.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** A plain map of full keys ("$global.x") to values. Used by the tests. */
public final class MapMemory implements Memory {
    public final Map<String, Object> values = new LinkedHashMap<String, Object>();

    @Override
    public Object get(String key) { return values.get(key); }

    @Override
    public void set(String key, Object value) {
        if (value == null) values.remove(key);
        else values.put(key, value);
    }

    @Override
    public void unset(String key) { values.remove(key); }
}
