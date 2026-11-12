package lastai.sagalib.game;

import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.LabelAPI;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts the paragraphs in a dialog's text panel. TextPanelAPI can't read text back, but its
 * implementation is a UI panel with a public getChildrenCopy() (the same walk ReadAloud uses),
 * called through MethodHandles because java.lang.reflect is blocked for mods. -1 = can't tell.
 */
final class UiLabels {
    private UiLabels() {}

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodType RETURNS_LIST = MethodType.methodType(List.class);
    private static final int PUBLIC = 0x0001;
    private static final MethodHandle NONE = MethodHandles.constant(Object.class, null);
    private static final Map<Class<?>, MethodHandle> CHILDREN = new HashMap<Class<?>, MethodHandle>();

    static int count(Object root) {
        if (root == null || handle(root.getClass()) == NONE) return -1;
        int[] n = {0, 0};
        walk(root, 0, n);
        return n[0];
    }

    private static void walk(Object node, int depth, int[] n) {
        if (node == null || depth > 10 || n[1]++ > 4000) return;
        if (node instanceof ButtonAPI) return;
        if (node instanceof LabelAPI) {
            String t = ((LabelAPI) node).getText();
            if (t != null && !t.trim().isEmpty()) n[0]++;
            return;
        }
        MethodHandle h = handle(node.getClass());
        if (h == NONE) return;
        try {
            Object r = h.invoke(node);
            if (r instanceof List) for (Object c : (List<?>) r) walk(c, depth + 1, n);
        } catch (Throwable ignored) {
            // unreadable part of the tree: count what we have
        }
    }

    private static synchronized MethodHandle handle(Class<?> cls) {
        MethodHandle h = CHILDREN.get(cls);
        if (h != null) return h;
        h = NONE;
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            if ((c.getModifiers() & PUBLIC) == 0) continue;
            try {
                h = LOOKUP.findVirtual(c, "getChildrenCopy", RETURNS_LIST);
                break;
            } catch (NoSuchMethodException e) {
                break;
            } catch (IllegalAccessException e) {
                // try the next base class
            }
        }
        CHILDREN.put(cls, h);
        return h;
    }
}
