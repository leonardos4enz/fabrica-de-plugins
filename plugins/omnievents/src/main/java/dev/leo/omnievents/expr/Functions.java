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

    /** Una función; {@code signature} y {@code description} van al esquema y la referencia (spec 007). */
    public record Def(String name, int min, int max, boolean lazy, Impl impl, String signature, String description) {
    }

    /** Descripción de las funciones que registra otro plugin sin documentarlas (spec 007, H3 CA3). */
    public static final String EXTERNAL = "Función registrada por otro plugin.";

    private static final Map<String, Def> DEFS = new TreeMap<>();

    private Functions() {
    }

    public static void register(String name, int min, int max, Impl impl) {
        String n = name.toLowerCase(Locale.ROOT);
        DEFS.put(n, new Def(n, min, max, false, impl, n + "(" + (max == 0 ? "" : "…") + ")", EXTERNAL));
    }

    /** Registra una función con su firma («round(x[, decimales])», de la que sale el nombre) y su descripción. */
    public static void register(String signature, String description, int min, int max, Impl impl) {
        String n = signature.substring(0, signature.indexOf('(')).toLowerCase(Locale.ROOT);
        DEFS.put(n, new Def(n, min, max, false, impl, signature, description));
    }

    private static void doc(String signature, String description, int min, int max, Impl impl) {
        register(signature, description, min, max, impl);
    }

    private static void lazyDoc(String signature, String description, int min, int max, Impl impl) {
        String n = signature.substring(0, signature.indexOf('('));
        DEFS.put(n, new Def(n, min, max, true, impl, signature, description));
    }

    /** Quita una función registrada (p. ej. al desactivarse el plugin que la agregó). */
    public static void unregister(String name) {
        DEFS.remove(name.toLowerCase(Locale.ROOT));
    }

    public static Def find(String name) {
        return DEFS.get(name.toLowerCase(Locale.ROOT));
    }

    public static Set<String> names() {
        return DEFS.keySet();
    }

    public static java.util.Collection<Def> all() {
        return DEFS.values();
    }

    private static double n(Object[] a, int i) {
        return Values.num(a[i]);
    }

    private static String s(Object[] a, int i) {
        return Values.str(a[i]);
    }

    static {
        // Matemáticas
        doc("random(a, b)", "Entero al azar entre a y b, ambos incluidos.", 2, 2, (c, a) -> (double) ThreadLocalRandom.current().nextLong((long) Math.min(n(a, 0), n(a, 1)), (long) Math.max(n(a, 0), n(a, 1)) + 1));
        doc("randomf(a, b)", "Decimal al azar entre a y b.", 2, 2, (c, a) -> ThreadLocalRandom.current().nextDouble(Math.min(n(a, 0), n(a, 1)), Math.nextUp(Math.max(n(a, 0), n(a, 1)))));
        doc("chance(p)", "true con una probabilidad de p por ciento.", 1, 1, (c, a) -> ThreadLocalRandom.current().nextDouble(100) < n(a, 0));
        doc("min(a, b, …)", "El menor de los números (o de una lista).", 1, Integer.MAX_VALUE, (c, a) -> fold(a, Math::min));
        doc("max(a, b, …)", "El mayor de los números (o de una lista).", 1, Integer.MAX_VALUE, (c, a) -> fold(a, Math::max));
        doc("abs(x)", "Valor absoluto.", 1, 1, (c, a) -> Math.abs(n(a, 0)));
        doc("round(x[, decimales])", "Redondea (a 0 decimales por defecto).", 1, 2, (c, a) -> Values.round(n(a, 0), a.length > 1 ? (int) n(a, 1) : 0));
        doc("floor(x)", "Redondea hacia abajo.", 1, 1, (c, a) -> Math.floor(n(a, 0)));
        doc("ceil(x)", "Redondea hacia arriba.", 1, 1, (c, a) -> Math.ceil(n(a, 0)));
        doc("sqrt(x)", "Raíz cuadrada.", 1, 1, (c, a) -> Math.sqrt(n(a, 0)));
        doc("pow(x, y)", "x elevado a y.", 2, 2, (c, a) -> Math.pow(n(a, 0), n(a, 1)));
        doc("clamp(x, min, max)", "x limitado al rango [min, max].", 3, 3, (c, a) -> Math.max(n(a, 1), Math.min(n(a, 2), n(a, 0))));
        // Texto y conversión
        doc("len(x)", "Largo de un texto, o tamaño de una lista o un mapa.", 1, 1, (c, a) -> (double) size(a[0]));
        doc("size(x)", "Igual que len.", 1, 1, (c, a) -> (double) size(a[0]));
        doc("lower(t)", "Texto en minúsculas.", 1, 1, (c, a) -> s(a, 0).toLowerCase(Locale.ROOT));
        doc("upper(t)", "Texto en mayúsculas.", 1, 1, (c, a) -> s(a, 0).toUpperCase(Locale.ROOT));
        doc("trim(t)", "Quita los espacios de los extremos.", 1, 1, (c, a) -> s(a, 0).trim());
        doc("replace(t, buscar, poner)", "Reemplaza cada aparición de buscar.", 3, 3, (c, a) -> s(a, 0).replace(s(a, 1), s(a, 2)));
        doc("substr(t, desde[, hasta])", "Parte de un texto (posiciones desde 0).", 2, 3, (c, a) -> {
            String str = s(a, 0);
            int from = Math.max(0, Math.min(str.length(), (int) n(a, 1)));
            int to = a.length > 2 ? Math.max(from, Math.min(str.length(), (int) n(a, 2))) : str.length();
            return str.substring(from, to);
        });
        doc("split(t, separador)", "Lista con las partes del texto.", 2, 2, (c, a) -> new ArrayList<Object>(List.of(s(a, 0).split(java.util.regex.Pattern.quote(s(a, 1)), -1))));
        doc("join(lista[, separador])", "Une una lista en un texto (\", \" por defecto).", 1, 2, (c, a) -> {
            StringBuilder sb = new StringBuilder();
            String sep = a.length > 1 ? s(a, 1) : ", ";
            for (Object o : Values.list(a[0])) {
                if (!sb.isEmpty()) sb.append(sep);
                sb.append(Values.str(o));
            }
            return sb.toString();
        });
        doc("str(x)", "Convierte a texto.", 1, 1, (c, a) -> s(a, 0));
        doc("num(x)", "Convierte a número (0 si no se puede).", 1, 1, (c, a) -> {
            Double d = Values.toNumberOrNull(a[0]);
            return d == null ? 0.0 : d;
        });
        doc("int(x)", "Convierte a entero, sin decimales (0 si no se puede).", 1, 1, (c, a) -> {
            Double d = Values.toNumberOrNull(a[0]);
            return d == null ? 0.0 : (double) d.longValue();
        });
        doc("bool(x)", "Convierte a booleano.", 1, 1, (c, a) -> Values.truthy(a[0]));
        doc("strip(t)", "Quita los colores & y § y las etiquetas MiniMessage.", 1, 1, (c, a) -> s(a, 0).replaceAll("(?i)[&§][0-9a-fk-orx]", "").replaceAll("<[^<>]*>", ""));
        doc("format(x[, decimales])", "Número con separador de miles (1,234.5).", 1, 2, (c, a) -> String.format(Locale.ROOT, "%,." + (a.length > 1 ? (int) n(a, 1) : 0) + "f", n(a, 0)));
        doc("default(x, otro)", "x, o otro si x es nulo o vacío.", 2, 2, (c, a) -> a[0] == null || Values.str(a[0]).isEmpty() ? a[1] : a[0]);
        lazyDoc("if(condición, a, b)", "a si la condición se cumple, si no b (solo evalúa la rama elegida).", 3, 3, (c, a) -> Values.truthy(((Node) a[0]).eval(c)) ? ((Node) a[1]).eval(c) : ((Node) a[2]).eval(c));
        // Tiempo
        doc("now()", "Milisegundos desde 1970 (hora del servidor).", 0, 0, (c, a) -> (double) System.currentTimeMillis());
        doc("hour()", "Hora del día del servidor (0 a 23).", 0, 0, (c, a) -> (double) LocalDateTime.now().getHour());
        doc("minute()", "Minuto de la hora (0 a 59).", 0, 0, (c, a) -> (double) LocalDateTime.now().getMinute());
        doc("weekday()", "Día de la semana: 1 es lunes, 7 es domingo.", 0, 0, (c, a) -> (double) LocalDateTime.now().getDayOfWeek().getValue());
        doc("date([patrón])", "Fecha de hoy con un patrón de Java (yyyy-MM-dd por defecto).", 0, 1, (c, a) -> LocalDateTime.now().format(DateTimeFormatter.ofPattern(a.length > 0 ? s(a, 0) : "yyyy-MM-dd")));
        // Servidor y jugadores
        doc("online(nombre)", "true si ese jugador está conectado.", 1, 1, (c, a) -> Bukkit.getPlayerExact(s(a, 0)) != null);
        doc("player(nombre)", "El jugador conectado con ese nombre, o nulo.", 1, 1, (c, a) -> Bukkit.getPlayerExact(s(a, 0)));
        doc("players()", "Lista de jugadores conectados.", 0, 0, (c, a) -> new ArrayList<Object>(Bukkit.getOnlinePlayers()));
        doc("perm(permiso[, jugador])", "true si el jugador (el del evento por defecto) tiene el permiso.", 1, 2, (c, a) -> {
            Object p = a.length > 1 ? a[1] : c.player();
            if (p instanceof String name) p = Bukkit.getPlayerExact(name);
            return p instanceof Player pl && pl.hasPermission(s(a, 0));
        });
        doc("papi(texto[, jugador])", "Resuelve placeholders de PlaceholderAPI; si el resultado es un número, devuelve número.", 1, 2, (c, a) -> {
            Object p = a.length > 1 ? a[1] : null;
            if (p instanceof String name) p = Bukkit.getPlayerExact(name);
            Object v = c.placeholder(s(a, 0), p);
            Double d = Values.toNumberOrNull(v);
            return d != null ? d : v;
        });
        doc("distance(a, b)", "Distancia entre dos ubicaciones, entidades o bloques (-1 si están en mundos distintos).", 2, 2, (c, a) -> {
            Location x = loc(a[0]), y = loc(a[1]);
            if (x == null || y == null || x.getWorld() != y.getWorld()) return -1.0;
            return x.distance(y);
        });
        doc("in_area(mundo, x1, y1, z1, x2, y2, z2)", "true si el jugador está dentro del cubo.", 7, 7, (c, a) -> {
            Location l = loc(c.player());
            if (l == null || l.getWorld() == null || !l.getWorld().getName().equalsIgnoreCase(s(a, 0))) return false;
            return between(l.getX(), n(a, 1), n(a, 4)) && between(l.getY(), n(a, 2), n(a, 5)) && between(l.getZ(), n(a, 3), n(a, 6));
        });
        doc("has_item(material[, n])", "true si el jugador lleva al menos n (1 por defecto) del material.", 1, 2, (c, a) -> {
            if (!(c.player() instanceof Player p)) return false;
            Material m = Material.matchMaterial(s(a, 0));
            return m != null && p.getInventory().containsAtLeast(new org.bukkit.inventory.ItemStack(m), a.length > 1 ? (int) n(a, 1) : 1);
        });
        // Datos de OmniEvents
        doc("cooldown(regla)", "Segundos de enfriamiento que le quedan al jugador en esa regla.", 1, 1, (c, a) -> c.service("cooldown", s(a, 0)));
        doc("uses(regla)", "Usos que lleva el jugador en esa regla.", 1, 1, (c, a) -> c.service("uses", s(a, 0)));
        doc("in_state(regla)", "true si el jugador está en el estado de esa regla state (o si el estado del servidor está activo).", 1, 1, (c, a) -> c.service("in_state", s(a, 0)));
        lazyDoc("ttl(variable)", "Segundos que le quedan a una variable con expire (pvar.x, gvar.x u objeto.data.x); -1 si no caduca; nulo si no existe.", 1, 1, (c, a) -> {
            if (a[0] instanceof Node.VarRef v) return c.service("ttl", v.scope(), v.name());
            Node.DataRef d = (Node.DataRef) a[0];
            Object target = d.target().eval(c);
            return target == null ? null : c.service("ttl_data", target, d.name());
        });
        doc("pvar_of(jugador, nombre)", "Variable pvar de otro jugador (por nombre).", 2, 2, (c, a) -> c.service("pvar_of", a[0], s(a, 1)));
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
