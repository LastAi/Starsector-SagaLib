package lastai.sagalib.test;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The game loads mod classes through a class loader that refuses some JDK classes (0.98a):
 * java.io except a few stream classes, java.nio.file.File*, java.lang.reflect (but a few types),
 * javax.script and java.util.prefs. A mod referencing one fails in game when the class loads.
 * This test applies the same rule to every class we ship (the same check as ReadAloud's).
 */
public final class SandboxTests {
    static final Set<String> IO_ALLOWED = new HashSet<String>(Arrays.asList(
            "java.io.BufferedInputStream", "java.io.BufferedReader", "java.io.FilterInputStream", "java.io.InputStreamReader",
            "java.io.Reader", "java.io.Serializable", "java.io.InvalidClassException", "java.io.ObjectStreamException",
            "java.io.InputStream", "java.io.IOException", "java.io.PrintStream", "java.io.PrintWriter",
            "java.io.ByteArrayInputStream", "java.io.FilterOutputStream", "java.io.OutputStream", "java.io.Closeable",
            "java.io.Flushable", "java.io.StringReader", "java.io.FileReader"));
    static final Set<String> REFLECT_ALLOWED = new HashSet<String>(Arrays.asList(
            "java.lang.reflect.AnnotatedElement", "java.lang.reflect.InvocationTargetException",
            "java.lang.reflect.Type", "java.lang.reflect.GenericDeclaration"));

    static final Pattern CLASS_REF = Pattern.compile("(?:^|L|\\[)((?:java|javax|sun)/[A-Za-z0-9_/$]+)");

    static boolean blocked(String name) {
        if (name.startsWith("java.io") || name.startsWith("java.nio.file.File")) return !IO_ALLOWED.contains(name);
        if (name.startsWith("java.lang.reflect")) return !REFLECT_ALLOWED.contains(name);
        return name.startsWith("javax.script") || name.startsWith("java.util.prefs") || name.equals("sun.reflect.misc.MethodUtil");
    }

    public static void shippedClassesUseOnlyAllowedJdkClasses() throws Exception {
        Path root = Paths.get(System.getProperty("sagalib.classes"));
        List<Path> classes;
        try (Stream<Path> s = Files.walk(root)) {
            classes = s.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList());
        }
        T.yes(classes.size() > 20, "found the classes: " + classes.size());
        Set<String> bad = new TreeSet<String>();
        for (Path p : classes) {
            for (String utf : utf8Constants(Files.readAllBytes(p))) {
                Matcher m = CLASS_REF.matcher(utf);
                while (m.find()) {
                    String name = m.group(1).replace('/', '.');
                    if (blocked(name)) bad.add(root.relativize(p) + " -> " + name);
                }
            }
        }
        T.eq(new TreeSet<String>(), bad, "classes the game would refuse");
    }

    /** The pure core must stay testable without the game: no com.fs imports in lastai/sagalib/core or model. */
    public static void coreHasNoGameImports() throws Exception {
        Path root = Paths.get(System.getProperty("sagalib.classes"), "lastai", "sagalib");
        Set<String> bad = new TreeSet<String>();
        for (String pkg : new String[]{"core", "model"}) {
            try (Stream<Path> s = Files.walk(root.resolve(pkg))) {
                for (Path p : s.filter(x -> x.toString().endsWith(".class")).collect(Collectors.toList())) {
                    for (String utf : utf8Constants(Files.readAllBytes(p))) {
                        if (utf.contains("com/fs/")) bad.add(root.relativize(p) + " -> " + utf);
                    }
                }
            }
        }
        T.eq(new TreeSet<String>(), bad, "game classes used by the core");
    }

    /** Classes that live in saves keep their names (docs/FORMAT.md: never rename after 1.0). */
    public static void savedClassNamesAreStable() throws Exception {
        T.eq("lastai.sagalib.quest.SagaQuest", Class.forName("lastai.sagalib.quest.SagaQuest", false, SandboxTests.class.getClassLoader()).getName(), "SagaQuest");
        // SagaLib 1.2: world event news items are saved intel
        T.eq("lastai.sagalib.game.SagaNewsIntel", Class.forName("lastai.sagalib.game.SagaNewsIntel", false, SandboxTests.class.getClassLoader()).getName(), "SagaNewsIntel");
        // SagaLib 1.2: roaming fleet routes keep their spawner
        T.eq("lastai.sagalib.encounters.SagaRouteSpawner", Class.forName("lastai.sagalib.encounters.SagaRouteSpawner", false, SandboxTests.class.getClassLoader()).getName(), "SagaRouteSpawner");
        // SagaLib 1.2: progress events, their factors and stage slots are saved
        for (String c : new String[] { "lastai.sagalib.game.SagaProgressEvent", "lastai.sagalib.game.SagaMonthlyFactor", "lastai.sagalib.game.SagaOneTimeFactor" })
            T.eq(c, Class.forName(c, false, SandboxTests.class.getClassLoader()).getName(), c);
        List<String> slots = new ArrayList<String>();
        for (Object o : Class.forName("lastai.sagalib.game.SagaStage", false, SandboxTests.class.getClassLoader()).getEnumConstants()) slots.add(((Enum<?>) o).name());
        T.eq(21, slots.size(), "SagaStage S0..S20");
        T.eq("S0", slots.get(0), "S0 first");
        Class<?> stage = Class.forName("lastai.sagalib.quest.QuestStage", false, SandboxTests.class.getClassLoader());
        List<String> names = new ArrayList<String>();
        for (Object o : stage.getEnumConstants()) names.add(((Enum<?>) o).name());
        T.eq(Arrays.asList("ACTIVE", "COMPLETED", "FAILED", "ABANDONED"), names, "QuestStage values");
    }

    /**
     * Every sprite SagaLib asks the game for by name exists in vanilla's settings.json (a missing
     * one is fatal: events/stage_unknown crashed the intel screen, test 8d-3).
     */
    public static void spriteNamesExistInTheGame() throws Exception {
        java.nio.file.Path settings = java.nio.file.Paths.get(System.getProperty("sagalib.gameCore", ""), "data", "config", "settings.json");
        if (!java.nio.file.Files.exists(settings)) { System.out.println("       (no game settings.json; sprite check skipped)"); return; }
        String text = new String(java.nio.file.Files.readAllBytes(settings), java.nio.charset.StandardCharsets.UTF_8);
        java.util.regex.Pattern call = java.util.regex.Pattern.compile("getSpriteName\\(\"([^\"]+)\",\\s*([^;]*?)\\);");
        java.util.regex.Pattern lit = java.util.regex.Pattern.compile("\"([^\"]+)\"");
        java.nio.file.Path src = java.nio.file.Paths.get(System.getProperty("sagalib.modDir", "."), "src");
        List<java.nio.file.Path> files = new ArrayList<java.nio.file.Path>();
        try (java.util.stream.Stream<java.nio.file.Path> w = java.nio.file.Files.walk(src)) { w.filter(f -> f.toString().endsWith(".java")).forEach(files::add); }
        int n = 0;
        for (java.nio.file.Path f : files) {
            java.util.regex.Matcher m = call.matcher(new String(java.nio.file.Files.readAllBytes(f), java.nio.charset.StandardCharsets.UTF_8));
            while (m.find()) {
                java.util.regex.Matcher l = lit.matcher(m.group(2));
                while (l.find()) {
                    n++;
                    T.yes(text.contains("\"" + l.group(1) + "\""), f.getFileName() + ": sprite \"" + m.group(1) + "/" + l.group(1) + "\" exists in the game");
                }
            }
        }
        T.yes(n >= 3, "sprite names found: " + n);
    }

    /** All CONSTANT_Utf8 strings of a class file (class names and descriptors live there). */
    static List<String> utf8Constants(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(bytes));
        if (in.readInt() != 0xCAFEBABE) throw new IOException("not a class file");
        in.readUnsignedShort();
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        List<String> out = new ArrayList<String>();
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1: out.add(in.readUTF()); break;
                case 3: case 4: in.readInt(); break;
                case 5: case 6: in.readLong(); i++; break;
                case 7: case 8: case 16: case 19: case 20: in.readUnsignedShort(); break;
                case 9: case 10: case 11: case 12: case 17: case 18: in.readInt(); break;
                case 15: in.readUnsignedByte(); in.readUnsignedShort(); break;
                default: throw new IOException("unknown constant tag " + tag);
            }
        }
        return out;
    }
}
