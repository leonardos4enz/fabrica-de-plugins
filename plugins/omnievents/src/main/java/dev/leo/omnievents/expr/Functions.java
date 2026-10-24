package dev.leo.omnievents.expr;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/** Funciones del lenguaje. Otros plugins agregan las suyas con {@link #register}. */
public final class Functions {
    @FunctionalInterface
    public interface Impl {
        /** args son valores ya evaluados; si la función es perezosa, son {@link Node}. */
        Object call(EvalContext ctx, Object[] args);
    }

    public record Def(String name, int min, int max, boolean lazy, Impl impl) {
    }

    private static final Map<String, Def> DEFS = new TreeMap<>();

    private Functions() {
    }

    public static void register(String name, int min, int max, Impl impl) {
        DEFS.put(name.toLowerCase(Locale.ROOT), new Def(name.toLowerCase(Locale.ROOT), min, max, false, impl));
    }

    private static void lazy(String name, int min, int max, Impl impl) {
        DEFS.put(name, new Def(name, min, max, true, impl));
    }

    public static Def find(String name) {
        return DEFS.get(name.toLowerCase(Locale.ROOT));
    }

    public static Set<String> names() {
        return DEFS.keySet();
    }

    private static double n(Object[] a, int i) {
        return Values.num(a[i]);
    }

    private static String s(Object[] a, int i) {
        return Values.str(a[i]);
    }

    static {
        // Matemáticas
        register("random", 2, 2, (c, a) -> (double) ThreadLocalRandom.current().nextLong((long) Math.min(n(a, 0), n(a, 1)), (long) Math.max(n(a, 0), n(a, 1)) + 1));
        register("randomf", 2, 2, (c, a) -> ThreadLocalRandom.current().nextDouble(Math.min(n(a, 0), n(a, 1)), Math.nextUp(Math.max(n(a, 0), n(a, 1)))));
        register("chance", 1, 1, (c, a) -> ThreadLocalRandom.current().nextDouble(100) < n(a, 0));
        register("min", 1, Integer.MAX_VALUE, (c, a) -> fold(a, Math::min));
        register("max", 1, Integer.MAX_VALUE, (c, a) -> fold(a, Math::max));
        register("abs", 1, 1, (c, a) -> Math.abs(n(a, 0)));
        register("round", 1, 2, (c, a) -> Values.round(n(a, 0), a.length > 1 ? (int) n(a, 1) : 0));
        register("floor", 1, 1, (c, a) -> Math.floor(n(a, 0)));
        register("ceil", 1, 1, (c, a) -> Math.ceil(n(a, 0)));
        register("sqrt", 1, 1, (c, a) -> Math.sqrt(n(a, 0)));
        register("pow", 2, 2, (c, a) -> Math.pow(n(a, 0), n(a, 1)));
        register("clamp", 3, 3, (c, a) -> Math.max(n(a, 1), Math.min(n(a, 2), n(a, 0))));
        // Texto y conversión
        register("len", 1, 1, (c, a) -> (double) size(a[0]));
        register("size", 1, 1, (c, a) -> (double) size(a[0]));
        register("lower", 1, 1, (c, a) -> s(a, 0).toLowerCase(Locale.ROOT));
        register("upper", 1, 1, (c, a) -> s(a, 0).toUpperCase(Locale.ROOT));
        register("trim", 1, 1, (c, a) -> s(a, 0).trim());
        register("replace", 3, 3, (c, a) -> s(a, 0).replace(s(a, 1), s(a, 2)));
        register("substr", 2, 3, (c, a) -> {
            String str = s(a, 0);
            int from = Math.max(0, Math.min(str.length(), (int) n(a, 1)));
            int to = a.length > 2 ? Math.max(from, Math.min(str.length(), (int) n(a, 2))) : str.length();
            return str.substring(from, to);
        });
        register("split", 2, 2, (c, a) -> new ArrayList<Object>(List.of(s(a, 0).split(java.util.regex.Pattern.quote(s(a, 1)), -1))));
        register("join", 1, 2, (c, a) -> {
            StringBuilder sb = new StringBuilder();
            String sep = a.length > 1 ? s(a, 1) : ", ";
            for (Object o : Values.list(a[0])) {
                if (!sb.isEmpty()) sb.append(sep);
                sb.append(Values.str(o));
            }
            return sb.toString();
        });
        register("str", 1, 1, (c, a) -> s(a, 0));
        register("num", 1, 1, (c, a) -> {
            Double d = Values.toNumberOrNull(a[0]);
            return d == null ? 0.0 : d;
        });
        register("int", 1, 1, (c, a) -> {
            Double d = Values.toNumberOrNull(a[0]);
            return d == null ? 0.0 : (double) d.longValue();
        });
        register("bool", 1, 1, (c, a) -> Values.truthy(a[0]));
        register("strip", 1, 1, (c, a) -> s(a, 0).replaceAll("(?i)[&§][0-9a-fk-orx]", "").replaceAll("<[^<>]*>", ""));
        register("format", 1, 2, (c, a) -> String.format(Locale.ROOT, "%,." + (a.length > 1 ? (int) n(a, 1) : 0) + "f", n(a, 0)));
        register("default", 2, 2, (c, a) -> a[0] == null || Values.str(a[0]).isEmpty() ? a[1] : a[0]);
        lazy("if", 3, 3, (c, a) -> Values.truthy(((Node) a[0]).eval(c)) ? ((Node) a[1]).eval(c) : ((Node) a[2]).eval(c));
        // Tiempo
        register("now", 0, 0, (c, a) -> (double) System.currentTimeMillis());
        register("hour", 0, 0, (c, a) -> (double) LocalDateTime.now().getHour());
        register("minute", 0, 0, (c, a) -> (double) LocalDateTime.now().getMinute());
        register("weekday", 0, 0, (c, a) -> (double) LocalDateTime.now().getDayOfWeek().getValue());
        register("date", 0, 1, (c, a) -> LocalDateTime.now().format(DateTimeFormatter.ofPattern(a.length > 0 ? s(a, 0) : "yyyy-MM-dd")));
        // Servidor y jugadores
        register("online", 1, 1, (c, a) -> Bukkit.getPlayerExact(s(a, 0)) != null);
        register("player", 1, 1, (c, a) -> Bukkit.getPlayerExact(s(a, 0)));
        register("players", 0, 0, (c, a) -> new ArrayList<Object>(Bukkit.getOnlinePlayers()));
        register("perm", 1, 2, (c, a) -> {
            Object p = a.length > 1 ? a[1] : c.player();
            if (p instanceof String name) p = Bukkit.getPlayerExact(name);
            return p instanceof Player pl && pl.hasPermission(s(a, 0));
        });
        register("papi", 1, 2, (c, a) -> {
            Object p = a.length > 1 ? a[1] : null;
            if (p instanceof String name) p = Bukkit.getPlayerExact(name);
            Object v = c.placeholder(s(a, 0), p);
            Double d = Values.toNumberOrNull(v);
            return d != null ? d : v;
        });
        register("distance", 2, 2, (c, a) -> {
            Location x = loc(a[0]), y = loc(a[1]);
            if (x == null || y == null || x.getWorld() != y.getWorld()) return -1.0;
            return x.distance(y);
        });
        register("in_area", 7, 7, (c, a) -> {
            Location l = loc(c.player());
            if (l == null || l.getWorld() == null || !l.getWorld().getName().equalsIgnoreCase(s(a, 0))) return false;
            return between(l.getX(), n(a, 1), n(a, 4)) && between(l.getY(), n(a, 2), n(a, 5)) && between(l.getZ(), n(a, 3), n(a, 6));
        });
        register("has_item", 1, 2, (c, a) -> {
            if (!(c.player() instanceof Player p)) return false;
            Material m = Material.matchMaterial(s(a, 0));
            return m != null && p.getInventory().containsAtLeast(new org.bukkit.inventory.ItemStack(m), a.length > 1 ? (int) n(a, 1) : 1);
        });
        // Datos de OmniEvents
        register("cooldown", 1, 1, (c, a) -> c.service("cooldown", s(a, 0)));
        register("uses", 1, 1, (c, a) -> c.service("uses", s(a, 0)));
        register("pvar_of", 2, 2, (c, a) -> c.service("pvar_of", a[0], s(a, 1)));
    }

    private static boolean between(double v, double a, double b) {
        return v >= Math.min(a, b) && v <= Math.max(a, b) + 1;
    }

    private static Location loc(Object o) {
        o = Values.raw(o);
        if (o instanceof Location l) return l;
        if (o instanceof Entity e) return e.getLocation();
        if (o instanceof org.bukkit.block.Block b) return b.getLocation();
        return null;
    }

    private static int size(Object o) {
        if (o == null) return 0;
        if (o instanceof Collection<?> c) return c.size();
        if (o instanceof Map<?, ?> m) return m.size();
        return Values.str(o).length();
    }

    private static double fold(Object[] a, java.util.function.DoubleBinaryOperator op) {
        List<Object> items = a.length == 1 ? Values.list(a[0]) : java.util.Arrays.asList(a);
        if (items.isEmpty()) return 0;
        double r = Values.num(items.get(0));
        for (int i = 1; i < items.size(); i++) r = op.applyAsDouble(r, Values.num(items.get(i)));
        return r;
    }
}
