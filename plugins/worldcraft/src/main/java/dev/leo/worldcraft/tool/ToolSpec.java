package dev.leo.worldcraft.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Un pincel de construcción o una herramienta (spec 032 H1). Viaja en el ítem como texto
 * («kind=sphere;pattern=stone;radius=4;hollow=true»), como los ajustes del pincel de terreno (026): dos ítems de la
 * barra pueden tener pinceles distintos. Java puro.
 *
 * @param opts las opciones (pattern, radius, mask, hollow…) como texto; sin las que valen lo de fábrica
 */
public record ToolSpec(ToolKind kind, Map<String, String> opts) {
    /** Los topes del momento (config.yml): un ítem armado a mano no pasa de aquí (plan 032 §3). */
    public record Caps(int brushMaxRadius, int maxRange, int maxReach) {
        public static final Caps DEFAULTS = new Caps(16, 32, 200);

        public Caps(int brushMaxRadius, int maxRange) {
            this(brushMaxRadius, maxRange, DEFAULTS.maxReach);
        }
    }

    /** Las opciones de butcher que acepta el pincel (las de /wc butcher, spec 031). */
    public static final Set<String> BUTCHER_FLAGS = Set.of("pets", "npcs", "golems", "animals", "ambient", "water", "named",
            "armorstands", "friendly");

    public ToolSpec {
        opts = Collections.unmodifiableMap(new LinkedHashMap<>(opts));
    }

    // ------------------------------------------------------------------ valores, con los de fábrica de WorldEdit y FAWE

    public String text(String key) {
        return opts.get(key);
    }

    /** Spec 034: la fórmula de brush deform si no se escribe ninguna (la de WorldEdit). */
    public static final String DEFAULT_DEFORM = "y-=0.2";

    /** Spec 034: un texto que puede tener «;» (una fórmula) viaja en Base64 en el ítem. */
    public static String encodeText(String text) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** La fórmula de brush deform (o la de fábrica si el ítem no la trae o no se lee). */
    public String formula() {
        String v = opts.get("formula");
        if (v == null) return DEFAULT_DEFORM;
        try {
            return new String(java.util.Base64.getUrlDecoder().decode(v), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return DEFAULT_DEFORM;
        }
    }

    /** Spec 040 H3 CA4: la máscara de /wc tracemask (viaja en Base64: puede tener «;»), o null. */
    public String trace() {
        String v = opts.get("trace");
        if (v == null) return null;
        try {
            return new String(java.util.Base64.getUrlDecoder().decode(v), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String pattern() {
        return opts.get("pattern");
    }

    public String mask() {
        return opts.get("mask");
    }

    public boolean flag(String key) {
        return "true".equals(opts.get(key));
    }

    public int number(String key) {
        String v = opts.get(key);
        if (v != null) {
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException ignored) {
                // el de fábrica
            }
        }
        return defaultNumber(kind, key);
    }

    public int radius() {
        return number("radius");
    }

    static int defaultNumber(ToolKind kind, String key) {
        return switch (key) {
            case "radius" -> switch (kind) {
                case SPHERE, CYL, SET, SPLATTER -> 2;
                case ROCK -> 10;
                default -> 5;
            };
            // Spec 040: morph y dilate como WorldEdit; heightmap sube 5 de fábrica.
            case "erode_faces" -> 4;
            case "erode" -> kind == ToolKind.DILATE ? 0 : 1;
            case "dilate_faces" -> kind == ToolKind.DILATE ? 3 : 5;
            case "dilate", "iterations" -> 1;
            case "height" -> kind == ToolKind.HEIGHTMAP ? 5 : 1;
            case "decay", "distance" -> 1;
            case "points" -> 5;
            case "sphericity" -> 100;
            case "frequency" -> 30;
            case "amplitude" -> 50;
            case "range" -> kind == ToolKind.STACKER ? 10 : 5;
            case "density" -> kind == ToolKind.APPLY ? 100 : 5;
            default -> 0;
        };
    }

    /** Las opciones de butcher elegidas. */
    public Set<String> butcherFlags() {
        Set<String> out = new TreeSet<>();
        String v = opts.get("flags");
        if (v != null) for (String f : v.split(",")) if (BUTCHER_FLAGS.contains(f)) out.add(f);
        return out;
    }

    // ------------------------------------------------------------------ ítem

    public String encode() {
        StringBuilder sb = new StringBuilder("kind=").append(kind.id());
        for (Map.Entry<String, String> e : opts.entrySet()) {
            sb.append(';').append(e.getKey()).append('=').append(e.getValue().replace(";", ""));
        }
        return sb.toString();
    }

    /**
     * Lo guardado en un ítem, o null si no es un pincel ni una herramienta. Los números se acotan con los topes del
     * momento (un ítem con radio 9999 hecho a mano usa el tope); lo que no se entiende se deja con el de fábrica.
     */
    public static ToolSpec decode(String s, Caps caps) {
        if (s == null) return null;
        ToolKind kind = null;
        Map<String, String> opts = new LinkedHashMap<>();
        for (String part : s.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String k = part.substring(0, eq), v = part.substring(eq + 1);
            if (k.equals("kind")) {
                kind = parseAny(v);
                continue;
            }
            opts.put(k, v);
        }
        if (kind == null) return null;
        clamp(opts, "radius", 0, caps.brushMaxRadius());
        clamp(opts, "height", 1, 2 * caps.brushMaxRadius() + 1);
        clamp(opts, "range", 1, caps.maxRange());
        clamp(opts, "decay", 1, 10);
        clamp(opts, "points", 1, 500);
        clamp(opts, "distance", 0, 32);
        clamp(opts, "sphericity", 1, 100);
        clamp(opts, "frequency", 1, 100);
        clamp(opts, "amplitude", 0, 100);
        clamp(opts, "density", 0, 100);
        clamp(opts, "reach", 1, caps.maxReach());
        clamp(opts, "iterations", 1, 10);
        clamp(opts, "erode", 0, 10);
        clamp(opts, "dilate", 0, 10);
        clamp(opts, "erode_faces", 1, 6);
        clamp(opts, "dilate_faces", 1, 6);
        return new ToolSpec(kind, opts);
    }

    private static ToolKind parseAny(String id) {
        ToolKind k = ToolKind.parse(id, true);
        return k != null ? k : ToolKind.parse(id, false);
    }

    private static void clamp(Map<String, String> opts, String key, int lo, int hi) {
        String v = opts.get(key);
        if (v == null) return;
        try {
            opts.put(key, String.valueOf(Math.max(lo, Math.min(hi, Integer.parseInt(v)))));
        } catch (NumberFormatException e) {
            opts.remove(key);
        }
    }

    public ToolSpec with(String key, String value) {
        Map<String, String> m = new LinkedHashMap<>(opts);
        if (value == null) m.remove(key);
        else m.put(key, value);
        return new ToolSpec(kind, m);
    }

    /** «sphere · stone · radius 4 · hollow», para el nombre del ítem y los mensajes. */
    public String describe() {
        StringBuilder sb = new StringBuilder(kind.id());
        switch (kind) {
            case SET -> sb.append(" · ").append(opts.getOrDefault("shape", "sphere")).append(" · ").append(pattern());
            case LRBUILD -> sb.append(" · left ").append(opts.get("left")).append(" · right ").append(opts.get("right"));
            case CYCLER -> {
                if (opts.containsKey("property")) sb.append(" · ").append(opts.get("property"));
            }
            case TREE, FOREST -> sb.append(" · ").append(opts.getOrDefault("type", "oak"));
            case BIOME -> sb.append(" · ").append(opts.get("biome"));
            case FEATURE -> sb.append(" · ").append(opts.get("feature"));
            case HEIGHTMAP -> sb.append(" · ").append(opts.get("image"));
            case APPLY -> sb.append(" · ").append(opts.getOrDefault("shape", "sphere")).append(" · item ").append(opts.get("item"));
            case DEFORM -> sb.append(" · ").append(formula());
            default -> {
                if (pattern() != null) sb.append(" · ").append(pattern());
            }
        }
        if (kind.hasRadius()) sb.append(" · radius ").append(radius());
        switch (kind) {
            case CYL -> sb.append(" · height ").append(number("height"));
            case SPLATTER -> sb.append(" · decay ").append(number("decay"));
            case SCATTER -> sb.append(" · ").append(number("points")).append(" points, ").append(number("distance")).append(" apart");
            case ROCK -> sb.append(" · sphericity ").append(number("sphericity")).append(" · frequency ").append(number("frequency"))
                    .append(" · amplitude ").append(number("amplitude"));
            case FLOODFILL -> sb.append(" · range ").append(number("range"));
            case FOREST, FEATURE -> sb.append(" · density ").append(number("density")).append("%");
            case APPLY -> {
                if (number("density") < 100) sb.append(" · ").append(number("density")).append("% of the blocks");
            }
            case SNOWSMOOTH -> sb.append(" · ").append(number("iterations")).append(" passes");
            case MORPH, DILATE -> sb.append(" · erode ").append(number("erode_faces")).append(" faces x").append(number("erode"))
                    .append(" · dilate ").append(number("dilate_faces")).append(" faces x").append(number("dilate"));
            case HEIGHTMAP -> sb.append(" · height ").append(number("height"));
            case STACKER -> sb.append(" · range ").append(number("range"));
            default -> {
            }
        }
        for (String f : new String[]{"hollow", "air", "origin", "overlay", "full", "raw", "offset", "stack", "erase", "flatten"}) {
            if (flag(f)) sb.append(" · ").append(f.equals("full") ? "to the top" : f.equals("air") ? "with air" : f);
        }
        if (kind == ToolKind.BUTCHER && !butcherFlags().isEmpty()) sb.append(" · ").append(String.join(", ", butcherFlags()));
        if (mask() != null) sb.append(kind == ToolKind.STACKER ? " · into " : " · only on ").append(mask());
        // Spec 040 H3 CA3, CA4: /wc range y /wc tracemask.
        if (opts.containsKey("reach")) sb.append(" · reach ").append(number("reach"));
        if (trace() != null) sb.append(" · aims at ").append(trace());
        return sb.toString();
    }
}
