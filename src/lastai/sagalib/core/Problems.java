package lastai.sagalib.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Errors and warnings found while reading content, each with the file and the JSON path. */
public final class Problems {
    public static final class Problem {
        public final boolean error;
        /** "data/sagaforge/quests/x.json (mod vgc)", or another place such as "content.csv row 3". */
        public final String file;
        /** "$.stages[1].id", or "" for the whole file. */
        public final String path;
        public final String message;

        Problem(boolean error, String file, String path, String message) {
            this.error = error;
            this.file = file;
            this.path = path;
            this.message = message;
        }

        @Override
        public String toString() {
            return (error ? "error" : "warning") + " in " + file + (path == null || path.isEmpty() ? "" : " at " + path) + ": " + message;
        }
    }

    private final List<Problem> list = new ArrayList<Problem>();

    public void error(String file, String path, String message) { list.add(new Problem(true, file, path, message)); }

    public void warn(String file, String path, String message) { list.add(new Problem(false, file, path, message)); }

    public List<Problem> all() { return Collections.unmodifiableList(list); }

    public int errors() {
        int n = 0;
        for (Problem p : list) if (p.error) n++;
        return n;
    }

    public boolean isEmpty() { return list.isEmpty(); }

    /** Problems whose text contains {@code part} (for tests). */
    public boolean has(String part) {
        for (Problem p : list) if (p.toString().contains(part)) return true;
        return false;
    }
}
