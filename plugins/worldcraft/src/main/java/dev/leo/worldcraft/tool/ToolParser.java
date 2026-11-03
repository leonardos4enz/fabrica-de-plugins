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
            // Spec 040
            case SNOW -> List.of("radius");
            case SNOWSMOOTH -> List.of("radius", "iterations");
            case BIOME -> List.of("radius", "biome");
            case MORPH -> List.of("radius", "erode_faces", "erode", "dilate_faces", "dilate");
            case DILATE -> List.of("radius", "dilate_faces", "dilate");
            case HEIGHTMAP -> List.of("image", "radius", "height");
            case FEATURE -> List.of("feature", "radius", "density");
            case APPLY -> List.of("shape", "radius", "density", "item");
            case STACKER -> List.of("range", "mask");
            // Spec 041
            case LINE, SPLINE, SURFACESPLINE, CIRCLE, RECURSIVE -> List.of("pattern", "radius");
            case CATENARY -> List.of("pattern", "length", "radius");
            case SWEEP -> List.of("copies");
            case PULL -> List.of("radius", "iterations");
            case SHATTER -> List.of("pattern", "radius", "count");
            case STENCIL -> List.of("pattern", "radius", "image");
            case IMAGE -> List.of("image", "radius");
            case HEIGHT -> List.of("radius", "image", "scale");
            case POPULATE -> List.of("on", "schem", "radius", "density");
            case COPYPASTE -> List.of("radius");
            case FEATUREPLACER -> List.of("feature");
            case STRUCTUREPLACER -> List.of("structure");
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
            case SNOW -> List.of("stack");
            case HEIGHTMAP -> List.of("erase", "flatten");
            // Spec 041 (las banderas de FAWE, con nombre largo)
            case LINE -> List.of("shell", "select", "flat");
            case CATENARY -> List.of("shell", "select", "facing");
            case CIRCLE -> List.of("fill");
            case RECURSIVE -> List.of("depth-first");
            case STENCIL -> List.of("white", "random-rotate");
            case IMAGE -> List.of("alpha", "fade");
            case HEIGHT -> List.of("random-rotate");
            case POPULATE -> List.of("rotate");
            case COPYPASTE -> List.of("random-rotate", "auto-rotate");
            default -> List.of();
        };
    }

    /** Spec 041: una palabra que es una imagen de la carpeta images/ (stencil, image, height). */
    static boolean isImage(String w) {
        return w.toLowerCase(Locale.ROOT).matches(".+\\.(png|jpe?g|gif|bmp)");
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
            case SNOW -> List.of("radius");
            case SNOWSMOOTH -> List.of("radius", "iterations");
            case MORPH -> List.of("radius", "erode_faces", "erode", "dilate_faces", "dilate");
            case DILATE -> List.of("radius", "dilate_faces", "dilate");
            case HEIGHTMAP -> List.of("radius", "height");
            case FEATURE -> List.of("radius", "density");
            case LINE, SPLINE, SURFACESPLINE, CIRCLE, RECURSIVE, COPYPASTE -> List.of("radius");
            case SWEEP -> List.of("copies");
            case PULL -> List.of("radius", "iterations");
            case SHATTER -> List.of("radius", "count");
            default -> List.of();
        };
    }

    public static final List<String> SET_SHAPES = List.of("cube", "sphere", "cyl");

    public static ToolSpec parse(boolean brush, String type, List<String> words, Set<String> flags, Map<String, String> kv,
                                 ToolSpec.Caps caps) {
        // Spec 040: /wc brush paint … item <ítem> es apply con densidad (sin item, paint es del pincel de terreno).
        boolean paint = brush && type.equalsIgnoreCase("paint");
        ToolKind kind = paint ? ToolKind.APPLY : ToolKind.parse(type, brush);
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
            case BIOME -> {
                // «biome [radio] <bioma>», como WorldEdit; el nombre se revisa con el registro del servidor.
                if (!rest.isEmpty() && rest.get(0).matches("\\d+")) o.put("radius", rest.remove(0));
                String b = kv.containsKey("biome") ? kv.get("biome") : rest.size() == 1 ? rest.remove(0) : null;
                if (b == null || !rest.isEmpty()) throw new IllegalArgumentException("biome takes one biome: " + example(kind));
                o.put("biome", b.toLowerCase(Locale.ROOT).replace("minecraft:", ""));
                rest = new ArrayList<>();
            }
            case HEIGHTMAP, FEATURE -> {
                // «heightmap <imagen> [radio] [alto]», «feature <nombre> [radio] [densidad]».
                String key = kind == ToolKind.HEIGHTMAP ? "image" : "feature";
                List<String> nums = numbers(kind);
                int n = 0;
                while (n < rest.size() && rest.get(rest.size() - 1 - n).matches("-?\\d+")) n++;
                if (n > nums.size()) throw new IllegalArgumentException(kind.id() + " takes at most " + nums.size() + " numbers (" + String.join(", ", nums) + ")");
                List<String> tail = new ArrayList<>(rest.subList(rest.size() - n, rest.size()));
                for (int i = 0; i < tail.size(); i++) o.put(nums.get(i), tail.get(i));
                List<String> names = rest.subList(0, rest.size() - n);
                String name = kv.containsKey(key) ? kv.get(key) : names.size() == 1 ? names.get(0) : null;
                if (name == null || names.size() > 1) throw new IllegalArgumentException(kind.id() + " needs one " + key + ": " + example(kind));
                o.put(key, key.equals("feature") ? name.toLowerCase(Locale.ROOT).replace("minecraft:", "") : name);
                rest = new ArrayList<>();
            }
            case APPLY -> {
                // «apply [sphere|cube|cyl] [radio] item <ítem>», «paint [forma] [radio] [densidad] item <ítem>» (WorldEdit).
                int at = -1;
                for (int i = 0; i < rest.size(); i++) if (rest.get(i).equalsIgnoreCase("item")) at = i;
                if (at < 0 || at != rest.size() - 2) {
                    throw new IllegalArgumentException("Write the item after the word item: " + (paint ? "/wc brush paint sphere 5 20 item bone_meal" : example(kind)));
                }
                o.put("item", rest.get(at + 1).toLowerCase(Locale.ROOT).replace("minecraft:", ""));
                List<String> head = new ArrayList<>(rest.subList(0, at));
                String shape = "sphere";
                if (!head.isEmpty() && !head.get(0).matches("\\d+")) {
                    shape = head.remove(0).toLowerCase(Locale.ROOT);
                    if (shape.equals("cylinder")) shape = "cyl";
                    if (!SET_SHAPES.contains(shape)) throw new IllegalArgumentException("'" + shape + "' is not a shape" + Issues.hint(shape, SET_SHAPES) + ": cube, sphere or cyl");
                }
                o.put("shape", shape);
                List<String> nums = paint ? List.of("radius", "density") : List.of("radius");
                if (head.size() > nums.size() || !head.stream().allMatch(w -> w.matches("\\d+"))) {
                    throw new IllegalArgumentException((paint ? "paint" : "apply") + " takes " + String.join(" and ", nums) + " before item");
                }
                for (int i = 0; i < head.size(); i++) o.put(nums.get(i), head.get(i));
                o.put("density", o.getOrDefault("density", paint ? "10" : "100"));
                rest = new ArrayList<>();
            }
            case STACKER -> {
                // «stacker [alcance] [máscara]», como WorldEdit (de fábrica 10 y el aire).
                if (!rest.isEmpty() && rest.get(0).matches("\\d+")) o.put("range", rest.remove(0));
                if (!rest.isEmpty()) o.put("mask", String.join(" ", rest));
                rest = new ArrayList<>();
            }
            // Spec 041: los de FAWE que no son «patrón y números».
            case CATENARY -> {
                // «catenary <patrón> [largo] [grosor]», como FAWE (el largo con decimales, de fábrica 1,2).
                int n = 0;
                while (n < rest.size() && n < 2 && rest.get(rest.size() - 1 - n).matches("-?\\d+(\\.\\d+)?")) n++;
                List<String> tail = new ArrayList<>(rest.subList(rest.size() - n, rest.size()));
                rest = new ArrayList<>(rest.subList(0, rest.size() - n));
                if (!tail.isEmpty()) o.put("length", tail.get(0));
                if (tail.size() > 1) o.put("radius", tail.get(1));
                if (kv.containsKey("length")) o.put("length", kv.get("length"));
                String len = o.get("length");
                if (len != null) {
                    double v;
                    try {
                        v = Double.parseDouble(len);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("'length=" + len + "' needs a number, like 1.2");
                    }
                    if (v < 1 || v > 4) throw new IllegalArgumentException("The length is 1 to 4 times the distance (1 = straight, 1.2 hangs a little)");
                }
            }
            case STENCIL, IMAGE, HEIGHT -> {
                // «stencil <patrón> [radio] [imagen]», «image <imagen> [radio]», «height [radio] [imagen] [escala]».
                String image = kv.get("image");
                List<String> keep = new ArrayList<>();
                for (String w : rest) {
                    if (image == null && isImage(w)) image = w;
                    else keep.add(w);
                }
                if (kind == ToolKind.IMAGE && image == null && !keep.isEmpty() && !keep.get(0).matches("-?\\d+")) image = keep.remove(0);
                if (kind == ToolKind.IMAGE && image == null) throw new IllegalArgumentException("image needs a picture from the images folder: " + example(kind));
                if (image != null) o.put("image", image);
                List<String> nums = kind == ToolKind.HEIGHT ? List.of("radius", "scale") : List.of("radius");
                int n = 0;
                while (n < keep.size() && keep.get(keep.size() - 1 - n).matches("-?\\d+")) n++;
                if (n > nums.size()) throw new IllegalArgumentException(kind.id() + " takes at most " + nums.size() + " numbers (" + String.join(", ", nums) + ")");
                List<String> tail = new ArrayList<>(keep.subList(keep.size() - n, keep.size()));
                for (int i = 0; i < tail.size(); i++) o.put(nums.get(i), tail.get(i));
                rest = new ArrayList<>(keep.subList(0, keep.size() - n));
            }
            case POPULATE -> {
                // «populateschematic <máscara> <esquema|carpeta> [radio] [densidad]».
                String on = kv.containsKey("on") ? kv.get("on") : rest.isEmpty() ? null : rest.remove(0);
                String schem = kv.containsKey("schem") ? kv.get("schem") : rest.isEmpty() ? null : rest.remove(0);
                if (on == null || schem == null) throw new IllegalArgumentException("populateschematic takes what to put them on and a schematic or folder: " + example(kind));
                o.put("on", on);
                o.put("schem", schem);
                List<String> nums = List.of("radius", "density");
                if (rest.size() > 2 || !rest.stream().allMatch(w -> w.matches("\\d+"))) {
                    throw new IllegalArgumentException("populateschematic takes radius and density after the schematic: " + example(kind));
                }
                for (int i = 0; i < rest.size(); i++) o.put(nums.get(i), rest.get(i));
                rest = new ArrayList<>();
            }
            case FEATUREPLACER, STRUCTUREPLACER -> {
                String key = kind == ToolKind.FEATUREPLACER ? "feature" : "structure";
                String name = kv.containsKey(key) ? kv.get(key) : rest.size() == 1 ? rest.get(0) : null;
                if (name == null) throw new IllegalArgumentException(kind.id() + " needs one " + key + ": " + example(kind));
                o.put(key, kind == ToolKind.FEATUREPLACER ? name.toLowerCase(Locale.ROOT).replace("minecraft:", "") : name);
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
                    && !e.getKey().equals("type") && !e.getKey().equals("biome") && !e.getKey().equals("image") && !e.getKey().equals("feature")
                    && !e.getKey().equals("item") && !(kind == ToolKind.APPLY && e.getKey().equals("shape"))) o.put(e.getKey(), e.getValue());
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
        range(o, "iterations", 1, 10);
        range(o, "erode", 0, 10);
        range(o, "dilate", 0, 10);
        range(o, "erode_faces", 1, 6);
        range(o, "dilate_faces", 1, 6);
        range(o, "count", 1, 64);
        range(o, "copies", 0, 256);
        range(o, "scale", -64, 64);
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
            case SNOW -> "/wc brush snow 6 --stack";
            case SNOWSMOOTH -> "/wc brush snowsmooth 6 2";
            case BIOME -> "/wc brush biome 6 desert";
            case MORPH -> "/wc brush morph 5 4 1 5 1";
            case DILATE -> "/wc brush dilate 5";
            case HEIGHTMAP -> "/wc brush heightmap hill.png 10 8";
            case FEATURE -> "/wc brush feature mossy_rock 8 5";
            case APPLY -> "/wc brush apply sphere 4 item iron_hoe";
            case STACKER -> "/wc tool stacker 10 air";
            case LINE -> "/wc brush line stone 1 --select";
            case SPLINE -> "/wc brush spline oak_planks 1";
            case SURFACESPLINE -> "/wc brush surfacespline dirt_path 1";
            case SWEEP -> "/wc brush sweep 5";
            case CATENARY -> "/wc brush catenary chain 1.2";
            case CIRCLE -> "/wc brush circle glass 6 --fill";
            case PULL -> "/wc brush pull 5 1";
            case RECURSIVE -> "/wc brush recursive gold_block 6";
            case SHATTER -> "/wc brush shatter air 10 10";
            case STENCIL -> "/wc brush stencil moss_block 6 spots.png";
            case IMAGE -> "/wc brush image logo.png 8";
            case HEIGHT -> "/wc brush height 6 hill.png 5";
            case POPULATE -> "/wc brush populateschematic grass_block trees/ 12 10 --rotate";
            case COPYPASTE -> "/wc brush copypaste 6 --auto-rotate";
            case INSPECT -> "/wc tool inspect";
            case FEATUREPLACER -> "/wc tool featureplacer mossy_rock";
            case STRUCTUREPLACER -> "/wc tool structureplacer igloo/top";
        };
    }
}
