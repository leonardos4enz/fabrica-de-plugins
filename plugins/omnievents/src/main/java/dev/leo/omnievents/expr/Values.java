package dev.leo.omnievents.expr;

import net.kyori.adventure.key.Keyed;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Coerciones y comparaciones del lenguaje. Todo valor que entra al lenguaje pasa por {@link #normalize}. */
public final class Values {
    private Values() {
    }

    public static Object normalize(Object v) {
        if (v == null) return null;
        if (v instanceof Optional<?> o) return normalize(o.orElse(null));
        if (v instanceof Component c) return PlainTextComponentSerializer.plainText().serialize(c);
        if (v instanceof Enum<?> e) return new Named(e.name(), e);
        // Entidades y mundos son Keyed en Paper, pero no son valores de registro: se dejan como objetos.
        if (v instanceof org.bukkit.entity.Entity || v instanceof org.bukkit.World) return v;
        if (v instanceof org.bukkit.Keyed k) return new Named(k.getKey().getKey().toUpperCase(Locale.ROOT), v);
        if (v instanceof Keyed k) return new Named(k.key().value().toUpperCase(Locale.ROOT), v);
        if (v instanceof UUID || v instanceof Character) return v.toString();
        if (v instanceof CharSequence cs && !(v instanceof String)) return cs.toString();
        if (v instanceof Object[] arr) return new ArrayList<>(Arrays.asList(arr));
        if (v instanceof int[] arr) return new ArrayList<Object>(Arrays.stream(arr).boxed().toList());
        return v;
    }

    /** El objeto real detrás de un {@link Named}. */
    public static Object raw(Object v) {
        return v instanceof Named n ? n.raw() : v;
    }

    public static boolean isNumeric(Object v) {
        return toNumberOrNull(v) != null;
    }

    public static Double toNumberOrNull(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1.0 : 0.0;
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) return null;
            char c = t.charAt(0);
            if (!(Character.isDigit(c) || c == '-' || c == '+' || c == '.')) return null;
            try {
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static double num(Object v) {
        Double d = toNumberOrNull(v);
        if (d == null) {
            if (v == null) return 0;
            throw new ExprException("Se esperaba un número y llegó '" + str(v) + "'");
        }
        return d;
    }

    public static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        if (v instanceof Collection<?> c) return !c.isEmpty();
        if (v instanceof Map<?, ?> m) return !m.isEmpty();
        String s = str(v).trim().toLowerCase(Locale.ROOT);
        return !(s.isEmpty() || s.equals("false") || s.equals("no") || s.equals("0"));
    }

    public static String str(Object v) {
        if (v == null) return "";
        if (v instanceof String s) return s;
        if (v instanceof Double d) return formatNumber(d);
        if (v instanceof Float f) return formatNumber(f.doubleValue());
        if (v instanceof Location l) {
            return (l.getWorld() == null ? "" : l.getWorld().getName()) + ";" + formatNumber(round(l.getX(), 3)) + ";"
                    + formatNumber(round(l.getY(), 3)) + ";" + formatNumber(round(l.getZ(), 3)) + ";"
                    + formatNumber(round(l.getYaw(), 2)) + ";" + formatNumber(round(l.getPitch(), 2));
        }
        if (v instanceof org.bukkit.entity.Player p) return p.getName();
        if (v instanceof org.bukkit.World w) return w.getName();
        if (v instanceof Collection<?> c) {
            StringBuilder sb = new StringBuilder();
            for (Object o : c) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(str(o));
            }
            return sb.toString();
        }
        Object n = normalize(v);
        return n == v ? v.toString() : str(n);
    }

    public static String formatNumber(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return String.valueOf(d);
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
        String s = String.format(Locale.ROOT, "%.6f", d);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    public static double round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(v * f) / f;
    }

    public static boolean looseEquals(Object a, Object b) {
        if (a == null || b == null) return a == null && b == null;
        if (a instanceof Named || b instanceof Named) return str(a).equalsIgnoreCase(str(b));
        if (a instanceof Boolean || b instanceof Boolean) {
            return isBoolLike(a) && isBoolLike(b) && truthy(a) == truthy(b);
        }
        Double x = toNumberOrNull(a), y = toNumberOrNull(b);
        if (x != null && y != null) return x.doubleValue() == y.doubleValue();
        if (a instanceof String || b instanceof String || a instanceof Number || b instanceof Number) {
            return str(a).equals(str(b));
        }
        return a.equals(b);
    }

    private static boolean isBoolLike(Object o) {
        if (o instanceof Boolean || o instanceof Number) return true;
        String s = str(o).toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("false") || s.equals("yes") || s.equals("no");
    }

    /** Compara para &lt; y &gt;; devuelve null si alguno es nulo (la comparación es falsa). */
    public static Integer compare(Object a, Object b) {
        if (a == null || b == null) return null;
        Double x = toNumberOrNull(a), y = toNumberOrNull(b);
        if (x != null && y != null) return Double.compare(x, y);
        return str(a).compareToIgnoreCase(str(b));
    }

    /** Convierte a lista para iterar o para 'in'. */
    public static List<Object> list(Object v) {
        if (v == null) return new ArrayList<>();
        if (v instanceof Collection<?> c) return new ArrayList<>(c);
        if (v instanceof Map<?, ?> m) return new ArrayList<>(m.keySet());
        if (v instanceof Object[] arr) return new ArrayList<>(Arrays.asList(arr));
        List<Object> one = new ArrayList<>();
        one.add(v);
        return one;
    }

    public static String typeName(Object v) {
        if (v == null) return "nulo";
        if (v instanceof Number) return "número";
        if (v instanceof String) return "texto";
        if (v instanceof Boolean) return "booleano";
        if (v instanceof Collection<?>) return "lista";
        return v.getClass().getSimpleName();
    }
}
