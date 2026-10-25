package dev.leo.omnievents.action;

import dev.leo.omnievents.debug.Trace;
import dev.leo.omnievents.expr.Scope;
import dev.leo.omnievents.expr.Values;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Estado falso de un caso de prueba (spec 010, plan §1.1). Colgado de un {@link ExecContext}, las variables, los
 * datos de objetos y los servicios salen de aquí y no del servidor; las acciones con efecto se registran como
 * {@link Effect} en lugar de aplicarse.
 */
public final class Sandbox {
    /** Una acción que la prueba registró sin aplicarla, con su texto ya armado. */
    public record Effect(String type, String path, String text) {
    }

    /** Lo que hace call dentro de una prueba: recorre la regla llamada con este mismo Sandbox. */
    @FunctionalInterface
    public interface Caller {
        void call(String rule, ExecContext from, Map<String, Object> args, int depth, Trace trace);
    }

    /** Pasos (acciones y vueltas) por caso: un caso no puede congelar el hilo (plan §1.3). */
    public static final int MAX_STEPS = 10_000;

    private final Map<String, Object> pvars = new HashMap<>();
    private final Map<String, Object> gvars = new HashMap<>();
    private final Map<String, Long> expires = new HashMap<>();
    private final Map<Object, Map<String, Object>> objectVars = new IdentityHashMap<>();
    private final Map<String, Object> placeholders = new HashMap<>();
    private final List<Effect> effects = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final Random random;
    private final int maxLoops;
    private final int maxCallDepth;
    private final long now = System.currentTimeMillis();
    private Boolean cancelled;
    private Caller caller;
    private int steps;

    public Sandbox(long seed, int maxLoops, int maxCallDepth) {
        this.random = new Random(seed);
        this.maxLoops = maxLoops;
        this.maxCallDepth = maxCallDepth;
    }

    public void caller(Caller c) {
        this.caller = c;
    }

    public void call(String rule, ExecContext from, Map<String, Object> args, int depth, Trace trace) {
        if (depth > maxCallDepth) {
            throw new ActionException("se pasó el tope de " + maxCallDepth + " call anidados (expressions.max-call-depth): ¿una regla que se llama a sí misma?");
        }
        if (caller == null) throw new ActionException("call no está disponible en esta prueba");
        caller.call(rule, from, args, depth, trace);
    }

    // ----- variables

    private Map<String, Object> scope(Scope s) {
        return s == Scope.GLOBAL ? gvars : pvars;
    }

    public Object get(Scope s, String name) {
        return scope(s).get(name);
    }

    /** Asigna una pvar o gvar del caso (value null = borra); expireAt en ms de época, 0 = no caduca. */
    public void put(Scope s, String name, Object value, long expireAt) {
        String key = s.prefix + "." + name;
        if (value == null) {
            scope(s).remove(name);
            expires.remove(key);
            return;
        }
        scope(s).put(name, value);
        if (expireAt > 0) expires.put(key, expireAt);
        else expires.remove(key);
    }

    /** Segundos que le quedan (spec 009, ttl()), -1 si no caduca, null si no existe. */
    public Double ttl(Scope s, String name) {
        if (!scope(s).containsKey(name)) return null;
        Long at = expires.get(s.prefix + "." + name);
        return at == null ? -1.0 : Math.max(0, (at - now) / 1000.0);
    }

    // ----- variables de objetos (los objetos de given son mapas)

    public static boolean isObject(Object o) {
        return o instanceof Map<?, ?>;
    }

    public Object objectGet(Object target, String name) {
        Map<String, Object> m = objectVars.get(target);
        return m == null ? null : m.get(name);
    }

    public void objectSet(Object target, String name, Object value) {
        if (value == null) {
            Map<String, Object> m = objectVars.get(target);
            if (m != null) m.remove(name);
            return;
        }
        objectVars.computeIfAbsent(target, k -> new HashMap<>()).put(name, value);
    }

    // ----- placeholders y reloj

    public void placeholder(String raw, Object value) {
        placeholders.put(raw, value);
    }

    public Object placeholder(String raw) {
        return placeholders.containsKey(raw) ? placeholders.get(raw) : raw;
    }

    public long now() {
        return now;
    }

    // ----- lo que la regla habría hecho

    public void effect(String type, String path, String text) {
        effects.add(new Effect(type, path, text));
    }

    public List<Effect> effects() {
        return effects;
    }

    public void cancel(boolean value) {
        cancelled = value;
    }

    /** null si la regla no usó cancel. */
    public Boolean cancelled() {
        return cancelled;
    }

    public void error(String msg) {
        errors.add(msg);
    }

    public List<String> errors() {
        return errors;
    }

    public Random random() {
        return random;
    }

    public int maxLoops() {
        return maxLoops;
    }

    /** Cuenta un paso del caso; pasado el tope, corta con un error. */
    public void step() {
        if (++steps > MAX_STEPS) throw new ActionException("la prueba pasó el tope de " + MAX_STEPS + " pasos");
    }

    @Override
    public String toString() {
        return "pvar=" + pvars + " gvar=" + gvars + " efectos=" + effects.size() + (cancelled == null ? "" : " cancelado=" + Values.str(cancelled));
    }
}
