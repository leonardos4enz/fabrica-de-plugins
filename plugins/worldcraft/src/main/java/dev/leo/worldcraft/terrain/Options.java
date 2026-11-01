package dev.leo.worldcraft.terrain;

import dev.leo.worldcraft.config.Issues;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Las opciones de montañas e islas (H4 CA1): qué claves hay, sus rangos, y cómo se juntan lo de fábrica, lo del estilo,
 * el tamaño y lo que escribe el jugador (en ese orden; gana lo último). Las palabras sueltas son un tamaño
 * (`small`…), un número (el radio) o un estilo.
 */
public final class Options {
    private Options() {
    }

    public enum Tool {
        MOUNTAIN, ISLAND;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Una clave numérica con su rango. */
    public record Key(String name, double min, double max, boolean integer, String help) {
    }

    public static final Map<String, Key> MOUNTAIN = keys(
            new Key("radius", 4, 512, true, "radius in blocks"),
            new Key("height", 4, 384, true, "height of the top above the foot"),
            new Key("peaks", 1, 7, true, "how many peaks"),
            new Key("roughness", 0, 1, false, "small bumps"),
            new Key("ridges", 0, 1, false, "how deep the ridges cut"),
            new Key("sharpness", 0.5, 3, false, "above 1: sharper top, steeper slopes"),
            new Key("crater", 0, 0.6, false, "crater size (0 = none)"),
            new Key("seed", -Long.MAX_VALUE, Long.MAX_VALUE, true, "the same seed gives the same result"));

    public static final Map<String, Key> ISLAND = keys(
            new Key("radius", 4, 512, true, "radius in blocks"),
            new Key("depth", 0.2, 2, false, "how deep the underside hangs, compared to the radius"),
            new Key("relief", 0, 0.3, false, "hills on top, compared to the radius"),
            new Key("spikes", 0, 1, false, "how many stalactites hang below"),
            new Key("roots", 0, 1, false, "hanging roots at the edge"),
            new Key("trees", 0, 12, true, "small trees on top"),
            new Key("seed", -Long.MAX_VALUE, Long.MAX_VALUE, true, "the same seed gives the same result"));

    /** Claves de texto de los dos. */
    public static final List<String> TEXT_KEYS = List.of("style", "at", "world");

    public static final List<String> SIZES = List.of("small", "medium", "large", "huge");

    public static Map<String, Key> of(Tool t) {
        return t == Tool.MOUNTAIN ? MOUNTAIN : ISLAND;
    }

    private static Map<String, Key> keys(Key... ks) {
        Map<String, Key> m = new LinkedHashMap<>();
        for (Key k : ks) m.put(k.name, k);
        return m;
    }

    /** Todas las claves que se pueden escribir (para sugerencias). */
    public static List<String> names(Tool t) {
        List<String> out = new ArrayList<>(of(t).keySet());
        out.addAll(TEXT_KEYS);
        return out;
    }

    /** Lo pedido, ya resuelto. */
    public record Resolved(Map<String, Double> values, long seed, Style style, String size, String at, String world) {
        public int integer(String k) {
            return (int) Math.round(values.get(k));
        }

        public double number(String k) {
            return values.get(k);
        }

    }

    /**
     * Junta todo. Errores en inglés con «did you mean» (H4 CA1).
     *
     * @param words     las palabras sin «=» (tamaño, radio o estilo)
     * @param given     lo escrito como clave=valor
     * @param maxRadius el tope de config.yml
     * @param seed      la semilla nueva si no se da una
     */
    public static Resolved resolve(Tool tool, List<String> words, Map<String, String> given, TerrainSettings cfg,
                                   int maxRadius, long seed) throws IllegalArgumentException {
        Map<String, Key> keys = of(tool);
        for (String k : given.keySet()) {
            if (!keys.containsKey(k) && !TEXT_KEYS.contains(k)) {
                throw new IllegalArgumentException("Unknown option '" + k + "='" + Issues.hint(k, names(tool)));
            }
        }
        String size = null, styleName = given.get("style");
        Double radius = null;
        for (String w : words) {
            String lw = w.toLowerCase(Locale.ROOT);
            if (SIZES.contains(lw)) size = lw;
            else if (lw.matches("\\d+")) radius = Double.parseDouble(lw);
            else if (cfg.styles().containsKey(lw)) styleName = lw;
            else {
                List<String> options = new ArrayList<>(SIZES);
                options.addAll(cfg.styles().keySet());
                throw new IllegalArgumentException("'" + w + "' is not a size or a style" + Issues.hint(lw, options)
                        + ". Sizes: " + String.join(", ", SIZES) + "; see /wc styles");
            }
        }
        Map<String, Object> defaults = cfg.defaults().getOrDefault(tool.id(), Map.of());
        if (styleName == null) styleName = String.valueOf(defaults.getOrDefault("style", ""));
        Style style = cfg.styles().get(styleName.toLowerCase(Locale.ROOT));
        if (style == null) {
            if (given.containsKey("style")) {
                throw new IllegalArgumentException("Unknown style '" + styleName + "'" + Issues.hint(styleName, cfg.styles().keySet()) + "; see /wc styles");
            }
            style = cfg.styles().values().iterator().next();
        }
        Map<String, Double> v = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : defaults.entrySet()) if (e.getValue() instanceof Number n) v.put(e.getKey(), n.doubleValue());
        for (Map.Entry<String, Double> e : style.options().entrySet()) if (keys.containsKey(e.getKey())) v.put(e.getKey(), e.getValue());
        Map<String, Double> sz = cfg.sizes().getOrDefault(tool.id(), Map.of()).get(size == null ? "medium" : size);
        if (sz != null) v.putAll(sz);
        if (radius != null) {
            // Un número es el radio; la altura de la montaña lo sigue (como en «medium»).
            Map<String, Double> med = cfg.sizes().getOrDefault(tool.id(), Map.of()).get("medium");
            if (tool == Tool.MOUNTAIN && med != null && med.containsKey("height") && med.containsKey("radius")) {
                v.put("height", (double) Math.max(4, Math.round(radius * med.get("height") / med.get("radius"))));
            }
            v.put("radius", radius);
        }
        long useSeed = seed;
        for (Map.Entry<String, String> e : given.entrySet()) {
            Key k = keys.get(e.getKey());
            if (k == null) continue;
            if (k.name.equals("seed")) {
                try {
                    useSeed = Long.parseLong(e.getValue());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("'seed=" + e.getValue() + "' needs a whole number");
                }
                continue;
            }
            double d;
            try {
                d = Double.parseDouble(e.getValue());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("'" + e.getKey() + "=" + e.getValue() + "' needs a number" + (k.integer ? " (whole)" : ""));
            }
            v.put(k.name, d);
        }
        v.remove("seed");
        for (Key k : keys.values()) {
            Double d = v.get(k.name);
            if (d == null) continue;
            double max = k.name.equals("radius") ? Math.min(k.max, maxRadius) : k.max;
            if (d < k.min || d > max) {
                throw new IllegalArgumentException(k.name + " " + fmt(d) + " is out of range (" + fmt(k.min) + ".." + fmt(max) + ")");
            }
            if (k.integer) v.put(k.name, (double) Math.round(d));
        }
        for (Key k : keys.values()) {
            if (!k.name.equals("seed") && !v.containsKey(k.name)) throw new IllegalArgumentException("Missing '" + k.name + "' in terrain.yml defaults." + tool.id());
        }
        return new Resolved(v, useSeed, style, size, given.get("at"), given.get("world"));
    }

    public static String fmt(double d) {
        return d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.2f", d);
    }
}
