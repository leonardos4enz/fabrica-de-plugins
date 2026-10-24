package dev.leo.omnievents.expr;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Acceso a propiedades: primero alias legibles por tipo ({@code player.level}), luego métodos de lectura de la API
 * ({@code event.final_damage} → {@code getFinalDamage()}). Los métodos se resuelven una vez y se cachean.
 * Solo se exponen métodos sin efectos (constitución II.1, ADR-0003).
 */
public final class Properties {
    private static final Properties INSTANCE = new Properties();
    private static final Object MISSING = new Object();

    private final Map<Class<?>, Map<String, Function<Object, Object>>> aliases = new LinkedHashMap<>();
    private final Map<Class<?>, Map<String, Method>> getterIndex = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> aliasCache = new ConcurrentHashMap<>();
    private volatile boolean allowUnsafe;

    private Properties() {
    }

    public static Properties get() {
        return INSTANCE;
    }

    public void setAllowUnsafe(boolean allow) {
        this.allowUnsafe = allow;
    }

    @SuppressWarnings("unchecked")
    public synchronized <T> void alias(Class<T> type, String name, Function<T, Object> getter) {
        aliases.computeIfAbsent(type, k -> new HashMap<>()).put(key(name), (Function<Object, Object>) getter);
        aliasCache.clear();
    }

    /** Nombres de alias disponibles para un tipo (para /omni y documentación). */
    public List<String> aliasNames(Class<?> type) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Class<?>, Map<String, Function<Object, Object>>> e : aliases.entrySet()) {
            if (e.getKey().isAssignableFrom(type)) out.addAll(e.getValue().keySet());
        }
        return out;
    }

    public Object read(Object target, String name) {
        if (target == null) return null;
        Object raw = Values.raw(target);
        String k = key(name);
        if (raw instanceof Map<?, ?> m) {
            if (m.containsKey(name)) return Values.normalize(m.get(name));
            for (Map.Entry<?, ?> e : m.entrySet()) if (key(String.valueOf(e.getKey())).equals(k)) return Values.normalize(e.getValue());
            return null;
        }
        if (target instanceof Named n && (k.equals("name") || k.equals("key"))) return n.name();
        Function<Object, Object> alias = findAlias(raw.getClass(), k);
        if (alias != null) return Values.normalize(alias.apply(raw));
        if (raw instanceof List<?> l && (k.equals("size") || k.equals("length"))) return (double) l.size();
        if (raw instanceof String s) {
            return switch (k) {
                case "length", "size" -> (double) s.length();
                case "upper" -> s.toUpperCase(Locale.ROOT);
                case "lower" -> s.toLowerCase(Locale.ROOT);
                default -> null;
            };
        }
        Method m = getterIndex.computeIfAbsent(raw.getClass(), this::indexGetters).get(k);
        if (m == null) return null;
        try {
            return Values.normalize(m.invoke(raw));
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new ExprException("No se pudo leer '" + name + "' de " + raw.getClass().getSimpleName() + ": " + rootMessage(e));
        }
    }

    /** ¿Existe la propiedad en el tipo? (validación al cargar) */
    public boolean has(Class<?> type, String name) {
        String k = key(name);
        return findAlias(type, k) != null || getterIndex.computeIfAbsent(type, this::indexGetters).containsKey(k);
    }

    public Object invoke(Object target, String name, Object[] args) {
        if (target == null) return null;
        Object raw = Values.raw(target);
        String lower = name.toLowerCase(Locale.ROOT);
        if (BLOCKED.contains(name)) throw new ExprException("El método '" + name + "' no está disponible en expresiones");
        if (!allowUnsafe &&!(lower.startsWith("get") || lower.startsWith("is") || lower.startsWith("has") || lower.startsWith("can"))) {
            throw new ExprException("El método '" + name + "' no es de lectura: solo se permiten get*, is*, has* y can* (expressions.allow-unsafe-methods)");
        }
        for (Method m : raw.getClass().getMethods()) {
            if (!m.getName().equalsIgnoreCase(name) || m.getParameterCount() != args.length || Modifier.isStatic(m.getModifiers())) continue;
            Object[] coerced = coerce(m.getParameterTypes(), args);
            if (coerced == null) continue;
            Method pub = publicVersion(m);
            if (pub == null) continue;
            try {
                return Values.normalize(pub.invoke(raw, coerced));
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new ExprException("Falló " + name + "(): " + rootMessage(e));
            }
        }
        throw new ExprException("No existe el método '" + name + "' con " + args.length + " argumentos en " + raw.getClass().getSimpleName());
    }

    /** Asigna una propiedad con su setter (acción set_event: el administrador lo pide explícitamente). */
    public void write(Object target, String name, Object value) {
        Object raw = Values.raw(target);
        String k = key(name);
        Object v = Values.raw(value);
        for (Method m : raw.getClass().getMethods()) {
            if (m.getParameterCount() != 1 || Modifier.isStatic(m.getModifiers())) continue;
            String n = m.getName();
            boolean setter = n.startsWith("set") && key(n.substring(3)).equals(k);
            boolean fluent = key(n).equals(k); // estilo Adventure: message(Component)
            if (!setter && !fluent) continue;
            Class<?> type = m.getParameterTypes()[0];
            Object arg;
            if (type == net.kyori.adventure.text.Component.class) {
                arg = v == null ? null : dev.leo.omnievents.text.TextFormat.parse(Values.str(v));
            } else {
                Object[] c = coerce(new Class<?>[]{type}, new Object[]{v});
                if (c == null) continue;
                arg = c[0];
            }
            Method pub = publicVersion(m);
            if (pub == null) continue;
            try {
                pub.invoke(raw, arg);
                return;
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new ExprException("No se pudo asignar '" + name + "': " + rootMessage(e));
            }
        }
        throw new ExprException(raw.getClass().getSimpleName() + " no tiene una propiedad '" + name + "' que se pueda asignar con ese valor");
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object[] coerce(Class<?>[] types, Object[] args) {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Class<?> t = types[i];
            Object a = Values.raw(args[i]);
            if (a == null) {
                if (t.isPrimitive()) return null;
                out[i] = null;
            } else if (t.isInstance(a)) {
                out[i] = a;
            } else if (t == String.class) {
                out[i] = Values.str(a);
            } else if ((t == int.class || t == Integer.class) && Values.isNumeric(a)) {
                out[i] = (int) Values.num(a);
            } else if ((t == double.class || t == Double.class) && Values.isNumeric(a)) {
                out[i] = Values.num(a);
            } else if ((t == float.class || t == Float.class) && Values.isNumeric(a)) {
                out[i] = (float) Values.num(a);
            } else if ((t == long.class || t == Long.class) && Values.isNumeric(a)) {
                out[i] = (long) Values.num(a);
            } else if (t == boolean.class || t == Boolean.class) {
                out[i] = Values.truthy(a);
            } else if (t.isEnum()) {
                try {
                    out[i] = Enum.valueOf((Class<Enum>) t, Values.str(a).toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return out;
    }

    private Function<Object, Object> findAlias(Class<?> type, String k) {
        Object cached = aliasCache.computeIfAbsent(type, t -> {
            Map<String, Function<Object, Object>> merged = new HashMap<>();
            synchronized (this) {
                for (Map.Entry<Class<?>, Map<String, Function<Object, Object>>> e : aliases.entrySet()) {
                    if (e.getKey().isAssignableFrom(t)) {
                        for (Map.Entry<String, Function<Object, Object>> a : e.getValue().entrySet()) merged.putIfAbsent(a.getKey(), a.getValue());
                    }
                }
            }
            return merged.isEmpty() ? MISSING : merged;
        });
        if (cached == MISSING) return null;
        @SuppressWarnings("unchecked")
        Map<String, Function<Object, Object>> map = (Map<String, Function<Object, Object>>) cached;
        return map.get(k);
    }

    /** Clave normalizada: sin guiones bajos y en minúsculas ("final_damage" y "finalDamage" son lo mismo). */
    static String key(String name) {
        return name.replace("_", "").toLowerCase(Locale.ROOT);
    }

    private Map<String, Method> indexGetters(Class<?> type) {
        Map<String, Method> out = new HashMap<>();
        Map<String, Integer> rank = new HashMap<>();
        for (Method m : type.getMethods()) {
            if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) continue;
            Class<?> r = m.getReturnType();
            if (r == void.class || m.getDeclaringClass() == Object.class) continue;
            String n = m.getName();
            if (BLOCKED.contains(n)) continue;
            String prop;
            int prio;
            if (n.startsWith("get") && n.length() > 3) {
                prop = n.substring(3);
                prio = 0;
            } else if (n.startsWith("is") && n.length() > 2) {
                prop = n.substring(2);
                prio = 1;
            } else if ((n.startsWith("has") || n.startsWith("can")) && n.length() > 3) {
                prop = n;
                prio = 2;
            } else {
                // Accesores estilo Adventure (displayName(), message()). Los que devuelven boolean suelen ser
                // acciones (eject, leaveVehicle, breakNaturally): se excluyen.
                if (r == boolean.class || r == Boolean.class || BLOCKED.contains(n)) continue;
                prop = n;
                prio = 3;
            }
            Method pub = publicVersion(m);
            if (pub == null) continue;
            String k = key(prop);
            Integer prev = rank.get(k);
            if (prev == null || prio < prev) {
                out.put(k, pub);
                rank.put(k, prio);
            }
            if (prio == 2) out.putIfAbsent(key(n), pub);
        }
        return out;
    }

    private static final java.util.Set<String> BLOCKED = java.util.Set.of(
            "clone", "hashCode", "toString", "spigot", "remove", "damage", "teleport", "zero", "normalize", "update",
            "launchProjectile", "getHandle", "wait", "notify", "notifyAll", "getClass");

    /** Versión del método declarada en un tipo público (las clases de CraftBukkit no siempre lo son). */
    private static Method publicVersion(Method m) {
        if (Modifier.isPublic(m.getDeclaringClass().getModifiers())) return m;
        for (Class<?> c = m.getDeclaringClass(); c != null; c = c.getSuperclass()) {
            Method found = searchInterfaces(c, m);
            if (found != null) return found;
        }
        return null;
    }

    private static Method searchInterfaces(Class<?> c, Method m) {
        for (Class<?> i : c.getInterfaces()) {
            if (Modifier.isPublic(i.getModifiers())) {
                try {
                    return i.getMethod(m.getName(), m.getParameterTypes());
                } catch (NoSuchMethodException ignored) {
                    // seguir buscando
                }
            }
            Method deeper = searchInterfaces(i, m);
            if (deeper != null) return deeper;
        }
        return null;
    }
}
