package dev.leo.omnievents.action;

import dev.leo.omnievents.expr.EvalContext;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.text.Template;
import dev.leo.omnievents.util.Suggest;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * La acción firework (spec 014, H2) en la forma de ConditionalEvents: pares clave:valor separados por espacios
 * ({@code colors:RED,#FFAA00 type:STAR power:1 fade:WHITE flicker:true trail:true}). Lo constante se valida al cargar;
 * lo que tiene {expresiones}, al correr.
 */
public final class Firework {
    public static final List<String> KEYS = List.of("colors", "type", "power", "fade", "flicker", "trail", "location", "damage", "shot_direction");

    static final Map<String, Color> COLORS = new LinkedHashMap<>();

    static {
        COLORS.put("AQUA", Color.AQUA);
        COLORS.put("BLACK", Color.BLACK);
        COLORS.put("BLUE", Color.BLUE);
        COLORS.put("FUCHSIA", Color.FUCHSIA);
        COLORS.put("GRAY", Color.GRAY);
        COLORS.put("GREEN", Color.GREEN);
        COLORS.put("LIME", Color.LIME);
        COLORS.put("MAROON", Color.MAROON);
        COLORS.put("NAVY", Color.NAVY);
        COLORS.put("OLIVE", Color.OLIVE);
        COLORS.put("ORANGE", Color.ORANGE);
        COLORS.put("PURPLE", Color.PURPLE);
        COLORS.put("RED", Color.RED);
        COLORS.put("SILVER", Color.SILVER);
        COLORS.put("TEAL", Color.TEAL);
        COLORS.put("WHITE", Color.WHITE);
        COLORS.put("YELLOW", Color.YELLOW);
    }

    private static final List<String> TYPES = List.of("BALL", "BALL_LARGE", "STAR", "BURST", "CREEPER");

    private final Map<String, Template> values;

    private Firework(Map<String, Template> values) {
        this.values = values;
    }

    /** Lee la forma de texto; un error dice qué está mal (con «¿quisiste decir?»). */
    public static Firework parse(String text) {
        Map<String, Template> v = new LinkedHashMap<>();
        for (String token : Args.split(text.trim(), ' ')) {
            if (token.isBlank()) continue;
            int c = token.indexOf(':');
            if (c <= 0) throw new IllegalArgumentException("'" + token + "' no es clave:valor (p. ej. colors:RED,BLUE type:STAR power:1)");
            String key = token.substring(0, c).toLowerCase(Locale.ROOT);
            if (!KEYS.contains(key)) throw new IllegalArgumentException("clave desconocida '" + key + "' en firework" + Suggest.hint(key, KEYS) + "; claves: " + String.join(", ", KEYS));
            v.put(key, Template.compile(token.substring(c + 1)));
        }
        if (!v.containsKey("colors")) throw new IllegalArgumentException("firework necesita colors (p. ej. colors:RED,#FFAA00)");
        Firework f = new Firework(v);
        // Lo constante se comprueba ya: un color o un tipo mal escrito es un error de carga, no de ejecución.
        for (String k : List.of("colors", "fade", "type", "power", "shot_direction")) {
            Template t = v.get(k);
            if (t == null || !t.isConstant()) continue;
            try {
                switch (k) {
                    case "colors", "fade" -> colors(t.source());
                    case "type" -> type(t.source());
                    case "shot_direction" -> ShotDirection.parse(t.source());
                    default -> power(t.source());
                }
            } catch (ActionException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        }
        return f;
    }

    public FireworkEffect effect(EvalContext ctx) {
        FireworkEffect.Builder b = FireworkEffect.builder().withColor(colors(raw("colors", ctx)));
        String fade = raw("fade", ctx);
        if (fade != null && !fade.isEmpty()) b.withFade(colors(fade));
        String type = raw("type", ctx);
        b.with(type == null || type.isEmpty() ? FireworkEffect.Type.BALL : type(type));
        b.flicker(Values.truthy(raw("flicker", ctx)));
        b.trail(Values.truthy(raw("trail", ctx)));
        return b.build();
    }

    /** La velocidad del cohete con shot_direction (al azar en sus rangos), o null si sube recto. */
    public org.bukkit.util.Vector shot(EvalContext ctx) {
        String s = raw("shot_direction", ctx);
        return s == null || s.isEmpty() ? null : ShotDirection.parse(s).roll();
    }

    public int power(EvalContext ctx) {
        String p = raw("power", ctx);
        return p == null || p.isEmpty() ? 1 : power(p);
    }

    public boolean damage(EvalContext ctx) {
        return Values.truthy(raw("damage", ctx));
    }

    /** La ubicación tal como se escribió (mundo;x;y;z o, como en CE, x;y;z;mundo), o null. */
    public Template location() {
        return values.get("location");
    }

    private String raw(String key, EvalContext ctx) {
        Template t = values.get(key);
        return t == null ? null : t.render(ctx, Template.Mode.RAW).trim();
    }

    static List<Color> colors(String list) {
        List<Color> out = new ArrayList<>();
        for (String s : list.split(",")) {
            String c = s.trim();
            if (c.isEmpty()) continue;
            if (c.startsWith("#")) {
                if (!c.matches("#[0-9A-Fa-f]{6}")) throw new ActionException("color '" + c + "' inválido (usa #RRGGBB)");
                out.add(Color.fromRGB(Integer.parseInt(c.substring(1), 16)));
                continue;
            }
            Color col = COLORS.get(c.toUpperCase(Locale.ROOT));
            if (col == null) throw new ActionException("color desconocido '" + c + "'" + Suggest.hint(c, COLORS.keySet()) + " (o usa #RRGGBB)");
            out.add(col);
        }
        if (out.isEmpty()) throw new ActionException("firework necesita al menos un color");
        return out;
    }

    static FireworkEffect.Type type(String s) {
        String t = s.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(t)) throw new ActionException("tipo de fuego artificial desconocido '" + s + "'" + Suggest.hint(t, TYPES) + "; tipos: " + String.join(", ", TYPES));
        return FireworkEffect.Type.valueOf(t);
    }

    static int power(String s) {
        if (!Values.isNumeric(s.trim())) throw new ActionException("power debe ser un número de 0 a 3, no '" + s + "'");
        int p = (int) Values.num(s.trim());
        if (p < 0 || p > 3) throw new ActionException("power va de 0 a 3 (dado: " + p + ")");
        return p;
    }

    /**
     * shot_direction de ConditionalEvents (spec 016): xmin,xmax;ymin,ymax;zmin,zmax[;vmin-vmax]. Cada eje es un número o
     * un rango min,max; la velocidad, un número o min-max (1 por defecto). El vector sale al azar en esos rangos.
     */
    public record ShotDirection(double[] x, double[] y, double[] z, double[] speed) {
        public static ShotDirection parse(String text) {
            String[] p = text.trim().split(";");
            if (p.length < 3 || p.length > 4) {
                throw new ActionException("shot_direction es xmin,xmax;ymin,ymax;zmin,zmax[;vmin-vmax] (p. ej. -1,1;1,1;-1,1;0.5-1), no '" + text + "'");
            }
            return new ShotDirection(range(p[0], ","), range(p[1], ","), range(p[2], ","), p.length > 3 ? range(p[3], "-") : new double[]{1, 1});
        }

        private static double[] range(String s, String sep) {
            String t = s.trim();
            // Un número negativo solo (-0.5) no es un rango con «-».
            int at = sep.equals("-") ? t.indexOf(sep, 1) : t.indexOf(sep);
            String a = at < 0 ? t : t.substring(0, at).trim(), b = at < 0 ? t : t.substring(at + 1).trim();
            if (!Values.isNumeric(a) || !Values.isNumeric(b)) throw new ActionException("shot_direction: '" + s + "' no es un número ni un rango min" + sep + "max");
            double x = Values.num(a), y = Values.num(b);
            return new double[]{Math.min(x, y), Math.max(x, y)};
        }

        private static double roll(double[] r) {
            return r[0] == r[1] ? r[0] : java.util.concurrent.ThreadLocalRandom.current().nextDouble(r[0], r[1]);
        }

        public org.bukkit.util.Vector roll() {
            return new org.bukkit.util.Vector(roll(x), roll(y), roll(z)).multiply(roll(speed));
        }
    }
}
