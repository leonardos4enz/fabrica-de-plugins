package dev.leo.worldcraft.tool;

import dev.leo.worldcraft.config.Issues;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lo escrito tras /wc brush &lt;tipo&gt; o /wc tool &lt;tipo&gt; (spec 032), en el orden de WorldEdit: el patrón y luego los
 * números («sphere stone 4», «cyl stone 3 5», «set cube 3 stone», «lrbuild air stone»). Los números también van como
 * clave=valor (points=10). Los patrones y máscaras se revisan después con el parser del plugin. Java puro.
 */
public final class ToolParser {
    private ToolParser() {
    }

    /** Las claves=valor que acepta cada uno (además de mask= en los que ponen bloques). */
    static List<String> keys(ToolKind k) {
        List<String> out = new ArrayList<>(switch (k) {
            case SPHERE -> List.of("pattern", "radius");
            case CYL -> List.of("pattern", "radius", "height");
            case SET -> List.of("pattern", "radius", "shape");
            case SPLATTER -> List.of("pattern", "radius", "decay");
            case SCATTER -> List.of("pattern", "radius", "points", "distance");
            case ROCK -> List.of("pattern", "radius", "sphericity", "frequency", "amplitude");
            case GRAVITY, EXTINGUISH, BUTCHER -> List.of("radius");
            case REPL -> List.of("pattern");
            case FLOODFILL -> List.of("pattern", "range");
            case LRBUILD -> List.of("left", "right");
            case FOREST -> List.of("type", "radius", "density");
            case DEFORM -> List.of("radius");
            case TREE -> List.of("type");
            default -> List.<String>of();
        });
        if (k.placesBlocks()) out.add("mask");
        return out;
    }

    /** Las opciones --x que acepta cada uno. */
    public static List<String> flags(ToolKind k) {
        return switch (k) {
            case SPHERE, CYL -> List.of("hollow");
            case CLIPBOARD -> List.of("air", "origin");
            case SCATTER -> List.of("overlay");
            case GRAVITY -> List.of("height");
            case BUTCHER -> List.copyOf(new TreeSet<>(ToolSpec.BUTCHER_FLAGS));
            case DEFORM -> List.of("raw", "offset");
            default -> List.of();
        };
    }

    /** Los números del final, en orden (radio, alto…). */
    private static List<String> numbers(ToolKind k) {
        return switch (k) {
            case SPHERE, SPLATTER, GRAVITY, EXTINGUISH, BUTCHER -> List.of("radius", "decay").subList(0, k == ToolKind.SPLATTER ? 2 : 1);
            case CYL -> List.of("radius", "height");
            case SCATTER -> List.of("radius", "points", "distance");
            case ROCK -> List.of("radius", "sphericity", "frequency", "amplitude");
            case FLOODFILL -> List.of("range");
            case FOREST -> List.of("radius", "density");
            default -> List.of();
        };
    }

    public static final List<String> SET_SHAPES = List.of("cube", "sphere", "cyl");

    public static ToolSpec parse(boolean brush, String type, List<String> words, Set<String> flags, Map<String, String> kv,
                                 ToolSpec.Caps caps) {
        ToolKind kind = ToolKind.parse(type, brush);
        if (kind == null) {
            List<String> ids = ToolKind.ids(brush);
            throw new IllegalArgumentException("Unknown " + (brush ? "brush" : "tool") + " '" + type + "'" + Issues.hint(type.toLowerCase(Locale.ROOT), ids)
                    + ". " + (brush ? "Brushes" : "Tools") + ": " + String.join(", ", ids));
        }
        List<String> allowed = keys(kind);
        for (String k : kv.keySet()) {
            if (!allowed.contains(k)) {
                throw new IllegalArgumentException(allowed.isEmpty() ? kind.id() + " takes no options"
                        : "Unknown option '" + k + "=' for " + kind.id() + Issues.hint(k, allowed) + ". Options: " + String.join(", ", allowed));
            }
        }
        List<String> fl = flags(kind);
        for (String f : flags) {
            if (!fl.contains(f)) {
                throw new IllegalArgumentException(fl.isEmpty() ? kind.id() + " takes no --options"
                        : "Unknown option '--" + f + "' for " + kind.id() + Issues.hint(f, fl) + ". Options: --" + String.join(", --", fl));
            }
        }
        Map<String, String> o = new LinkedHashMap<>();
        List<String> rest = new ArrayList<>(words);
        switch (kind) {
            case SET -> {
                if (rest.isEmpty() && !kv.containsKey("shape")) throw new IllegalArgumentException("Which shape? /wc brush set <cube|sphere|cyl> [radius] <pattern>");
                String shape = kv.containsKey("shape") ? kv.get("shape") : rest.remove(0);
                shape = shape.toLowerCase(Locale.ROOT).equals("cylinder") ? "cyl" : shape.toLowerCase(Locale.ROOT);
                if (!SET_SHAPES.contains(shape)) {
                    throw new IllegalArgumentException("'" + shape + "' is not a shape" + Issues.hint(shape, SET_SHAPES) + ": cube, sphere or cyl");
                }
                o.put("shape", shape);
                if (!rest.isEmpty() && rest.get(0).matches("\\d+")) o.put("radius", rest.remove(0));
            }
            case LRBUILD -> {
                String l = kv.containsKey("left") ? kv.get("left") : rest.isEmpty() ? null : rest.remove(0);
                String r = kv.containsKey("right") ? kv.get("right") : rest.isEmpty() ? null : rest.remove(0);
                if (l == null || r == null || !rest.isEmpty()) {
                    throw new IllegalArgumentException("lrbuild takes two blocks: /wc tool lrbuild <left click> <right click>, like /wc tool lrbuild air stone");
                }
                o.put("left", l);
                o.put("right", r);
            }
            case DEFORM -> {
                // Spec 034 H7: «deform [radio] [fórmula]», de fábrica 5 e «y-=0.2» (como WorldEdit); la fórmula se revisa aquí.
                if (!rest.isEmpty() && rest.get(0).matches("\\d+")) o.put("radius", rest.remove(0));
                else if (!kv.containsKey("radius")) o.put("radius", "5");
                String text = rest.isEmpty() ? ToolSpec.DEFAULT_DEFORM : String.join(" ", rest);
                try {
                    dev.leo.worldcraft.formula.Formula.parse(text, List.of("x", "y", "z"), dev.leo.worldcraft.formula.Formula.Limits.DEFAULTS);
                } catch (dev.leo.worldcraft.formula.FormulaException e) {
                    throw new IllegalArgumentException(e.getMessage());
                }
                o.put("formula", ToolSpec.encodeText(text));
                rest = new ArrayList<>();
            }
            case TREE, FOREST -> {
                // Spec 033: el tipo de árbol en vez de bloques («forest birch 6 10», «tree cherry»).
                List<String> nums = numbers(kind);
                int n = 0;
                while (n < rest.size() && rest.get(rest.size() - 1 - n).matches("-?\\d+")) n++;
                if (n > nums.size()) {
                    throw new IllegalArgumentException(nums.isEmpty() ? kind.id() + " takes no numbers: " + example(kind)
                            : kind.id() + " takes at most " + nums.size() + " numbers (" + String.join(", ", nums) + ")");
                }
                List<String> tail = new ArrayList<>(rest.subList(rest.size() - n, rest.size()));
                for (int i = 0; i < tail.size(); i++) o.put(nums.get(i), tail.get(i));
                List<String> names = rest.subList(0, rest.size() - n);
                if (names.size() > 1) throw new IllegalArgumentException(kind.id() + " takes one tree type: " + example(kind));
                String tree = kv.containsKey("type") ? kv.get("type") : names.isEmpty() ? "oak" : names.get(0);
                o.put("type", dev.leo.worldcraft.nature.TreeKind.parse(tree).id());
                rest = new ArrayList<>();
            }
            default -> {
                List<String> nums = numbers(kind);
                int n = 0;
                while (n < rest.size() && rest.get(rest.size() - 1 - n).matches("-?\\d+")) n++;
                if (n > nums.size()) {
                    throw new IllegalArgumentException(nums.isEmpty() ? kind.id() + " takes no numbers"
                            : kind.id() + " takes at most " + nums.size() + " number" + (nums.size() == 1 ? "" : "s") + " (" + String.join(", ", nums) + ")");
                }
                List<String> tail = new ArrayList<>(rest.subList(rest.size() - n, rest.size()));
                rest = new ArrayList<>(rest.subList(0, rest.size() - n));
                for (int i = 0; i < tail.size(); i++) o.put(nums.get(i), tail.get(i));
            }
        }
        for (Map.Entry<String, String> e : kv.entrySet()) {
            if (!e.getKey().equals("mask") && !e.getKey().equals("shape") && !e.getKey().equals("left") && !e.getKey().equals("right")
                    && !e.getKey().equals("type")) o.put(e.getKey(), e.getValue());
        }
        boolean takesPattern = allowed.contains("pattern");
        String pattern = kv.containsKey("pattern") ? kv.get("pattern") : String.join(" ", rest);
        if (takesPattern) {
            if (pattern.isBlank()) throw new IllegalArgumentException(kind.id() + " needs blocks: " + example(kind));
            o.put("pattern", pattern.trim());
        } else if (!rest.isEmpty()) {
            throw new IllegalArgumentException(kind.id() + " takes no blocks ('" + String.join(" ", rest) + "'): " + example(kind));
        }
        range(o, "radius", 0, caps.brushMaxRadius());
        range(o, "height", 1, 2 * caps.brushMaxRadius() + 1);
        range(o, "decay", 1, 10);
        range(o, "points", 1, 500);
        range(o, "distance", 0, 32);
        range(o, "sphericity", 1, 100);
        range(o, "frequency", 1, 100);
        range(o, "amplitude", 0, 100);
        range(o, "range", 1, caps.maxRange());
        range(o, "density", 0, 100);
        if (kv.containsKey("mask")) {
            String m = kv.get("mask");
            if (!m.isBlank() && !m.equals("*")) o.put("mask", m);
        }
        for (String f : flags) {
            if (kind == ToolKind.BUTCHER) continue;
            o.put(f.equals("height") ? "full" : f, "true");
        }
        if (kind == ToolKind.BUTCHER && !flags.isEmpty()) o.put("flags", String.join(",", new TreeSet<>(flags)));
        return new ToolSpec(kind, o);
    }

    /** /wc brush radius=6 o mask=stone sobre el pincel de la mano: cambia solo eso. */
    public static ToolSpec update(ToolSpec spec, Map<String, String> kv, ToolSpec.Caps caps) {
        ToolSpec out = spec;
        for (Map.Entry<String, String> e : kv.entrySet()) {
            String k = e.getKey(), v = e.getValue();
            switch (k) {
                case "radius" -> {
                    if (!spec.kind().hasRadius()) throw new IllegalArgumentException(spec.kind().id() + " has no radius");
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("radius", v);
                    range(m, "radius", 0, caps.brushMaxRadius());
                    out = out.with("radius", m.get("radius"));
                }
                case "mask" -> {
                    if (!spec.kind().placesBlocks()) throw new IllegalArgumentException(spec.kind().id() + " takes no mask");
                    out = out.with("mask", v.isBlank() || v.equals("*") ? null : v);
                }
                default -> throw new IllegalArgumentException("Only radius= and mask= change the brush in your hand"
                        + Issues.hint(k, List.of("radius", "mask")) + "; write the whole brush to change the rest");
            }
        }
        return out;
    }

    private static void range(Map<String, String> o, String key, int lo, int hi) {
        String v = o.get(key);
        if (v == null) return;
        int n;
        try {
            n = Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + key + "=" + v + "' needs a whole number");
        }
        if (n < lo || n > hi) throw new IllegalArgumentException(key + " " + n + " is out of range (" + lo + ".." + hi + ")");
    }

    /** Un ejemplo de cada uno, para los errores. */
    public static String example(ToolKind k) {
        return switch (k) {
            case SPHERE -> "/wc brush sphere stone 4";
            case CYL -> "/wc brush cyl stone 3 5";
            case SET -> "/wc brush set cube 3 stone";
            case CLIPBOARD -> "/wc brush clipboard";
            case SPLATTER -> "/wc brush splatter moss_block 4 3";
            case SCATTER -> "/wc brush scatter poppy 6 10 2 --overlay";
            case ROCK -> "/wc brush rock stone, andesite 6";
            case GRAVITY -> "/wc brush gravity 5";
            case EXTINGUISH -> "/wc brush extinguish 8";
            case BUTCHER -> "/wc brush butcher 8 --animals";
            case REPL -> "/wc tool repl oak_planks";
            case FLOODFILL -> "/wc tool floodfill water 10";
            case INFO -> "/wc tool info";
            case CYCLER -> "/wc tool cycler";
            case DELTREE -> "/wc tool deltree";
            case FARWAND -> "/wc tool farwand";
            case LRBUILD -> "/wc tool lrbuild air stone";
            case NAVWAND -> "/wc tool navwand";
            case FOREST -> "/wc brush forest birch 6 10";
            case DEFORM -> "/wc brush deform 6 y-=0.2";
            case TREE -> "/wc tool tree cherry";
        };
    }
}
