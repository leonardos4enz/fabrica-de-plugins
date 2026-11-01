package dev.leo.worldcraft.terrain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Los ajustes de un pincel de terreno (H3, H4 CA4). Viajan en el ítem como texto («mode=smooth;radius=6;strength=0.5»):
 * dos pinceles en la barra pueden tener modos distintos.
 *
 * @param style   estilo para pintar (null = el de fábrica)
 * @param pattern patrón para pintar solo la cima (gana sobre el estilo), o null
 * @param mask    solo las columnas cuya superficie pasa esta máscara, o null
 */
public record BrushSettings(Mode mode, int radius, double strength, String style, String pattern, String mask) {
    public enum Mode {
        RAISE, LOWER, SMOOTH, FLATTEN, ERODE, PAINT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Mode parse(String s) {
            for (Mode m : values()) if (m.id().equalsIgnoreCase(s)) return m;
            return null;
        }

        public static List<String> ids() {
            return java.util.Arrays.stream(values()).map(Mode::id).toList();
        }
    }

    public static final BrushSettings DEFAULT = new BrushSettings(Mode.RAISE, 5, 0.5, null, null, null);

    public BrushSettings {
        strength = Math.max(0.1, Math.min(1, strength));
        radius = Math.max(1, radius);
    }

    public String encode() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("mode", mode.id());
        m.put("radius", String.valueOf(radius));
        m.put("strength", String.format(Locale.ROOT, "%.2f", strength));
        if (style != null) m.put("style", style);
        if (pattern != null) m.put("pattern", pattern);
        if (mask != null) m.put("mask", mask);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (!sb.isEmpty()) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue().replace(";", ""));
        }
        return sb.toString();
    }

    /** Lo guardado en un ítem; lo que no se entiende se deja con el valor de fábrica (un ítem viejo sigue sirviendo). */
    public static BrushSettings decode(String s) {
        BrushSettings b = DEFAULT;
        if (s == null) return b;
        Mode mode = b.mode;
        int radius = b.radius;
        double strength = b.strength;
        String style = null, pattern = null, mask = null;
        for (String part : s.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String k = part.substring(0, eq), v = part.substring(eq + 1);
            try {
                switch (k) {
                    case "mode" -> mode = Mode.parse(v) != null ? Mode.parse(v) : mode;
                    case "radius" -> radius = Integer.parseInt(v);
                    case "strength" -> strength = Double.parseDouble(v);
                    case "style" -> style = v;
                    case "pattern" -> pattern = v;
                    case "mask" -> mask = v;
                    default -> {
                    }
                }
            } catch (NumberFormatException ignored) {
                // se queda el de fábrica
            }
        }
        return new BrushSettings(mode, radius, strength, style, pattern, mask);
    }

    public BrushSettings withMode(Mode m) {
        return new BrushSettings(m, radius, strength, style, pattern, mask);
    }

    /** «smooth · radius 6 · strength 50%» (más lo que tenga). */
    public String describe() {
        StringBuilder sb = new StringBuilder(mode.id()).append(" · radius ").append(radius)
                .append(" · strength ").append(Math.round(strength * 100)).append('%');
        if (mode == Mode.PAINT) sb.append(" · ").append(pattern != null ? pattern : style != null ? "style " + style : "default style");
        if (mask != null) sb.append(" · only on ").append(mask);
        return sb.toString();
    }
}
