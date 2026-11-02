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
    public record Caps(int brushMaxRadius, int maxRange) {
        public static final Caps DEFAULTS = new Caps(16, 32);
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
            case "height", "decay", "distance" -> 1;
            case "points" -> 5;
            case "sphericity" -> 100;
            case "frequency" -> 30;
            case "amplitude" -> 50;
            case "range" -> 5;
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
            default -> {
            }
        }
        for (String f : new String[]{"hollow", "air", "origin", "overlay", "full"}) {
            if (flag(f)) sb.append(" · ").append(f.equals("full") ? "to the top" : f.equals("air") ? "with air" : f);
        }
        if (kind == ToolKind.BUTCHER && !butcherFlags().isEmpty()) sb.append(" · ").append(String.join(", ", butcherFlags()));
        if (mask() != null) sb.append(" · only on ").append(mask());
        return sb.toString();
    }
}
