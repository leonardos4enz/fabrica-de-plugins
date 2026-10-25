package dev.leo.omnievents.trigger;

import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Node;
import dev.leo.omnievents.expr.Scope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * De qué depende la expresión de un disparador state (spec 008, H2): qué eventos pueden cambiarla, qué variables y
 * qué otros estados lee, y qué partes no se pueden deducir (piden check_every). Puro: no toca Bukkit (plan 008 §1.2).
 */
public final class StateDeps {
    /** Fuentes de cambio; cada una se traduce a eventos de Bukkit en {@link StateService}. */
    public enum Source {
        HEALTH("vida"), FOOD("hambre"), MOVE("ubicación"), XP("experiencia"), GAMEMODE("modo de juego"),
        SNEAK("agacharse"), SPRINT("correr"), FLIGHT("volar"), ARMOR("armadura"), INVENTORY("inventario"),
        ONLINE("conectados"), WEATHER("clima");

        public final String label;

        Source(String label) {
            this.label = label;
        }
    }

    /** Primera propiedad de player (normalizada como en Properties: sin _ y en minúsculas) → fuente. */
    private static final Map<String, Source> PLAYER_PROPS = Map.ofEntries(
            Map.entry("health", Source.HEALTH), Map.entry("maxhealth", Source.HEALTH), Map.entry("absorptionamount", Source.HEALTH),
            Map.entry("alive", Source.HEALTH), Map.entry("dead", Source.HEALTH),
            Map.entry("food", Source.FOOD), Map.entry("foodlevel", Source.FOOD), Map.entry("saturation", Source.FOOD), Map.entry("exhaustion", Source.FOOD),
            Map.entry("location", Source.MOVE), Map.entry("world", Source.MOVE), Map.entry("x", Source.MOVE), Map.entry("y", Source.MOVE),
            Map.entry("z", Source.MOVE), Map.entry("blockx", Source.MOVE), Map.entry("blocky", Source.MOVE), Map.entry("blockz", Source.MOVE),
            Map.entry("inwater", Source.MOVE), Map.entry("swimming", Source.MOVE), Map.entry("onground", Source.MOVE),
            Map.entry("gliding", Source.MOVE), Map.entry("eyelocation", Source.MOVE),
            Map.entry("level", Source.XP), Map.entry("exp", Source.XP), Map.entry("totalexperience", Source.XP),
            Map.entry("gamemode", Source.GAMEMODE),
            Map.entry("sneaking", Source.SNEAK), Map.entry("sprinting", Source.SPRINT),
            Map.entry("flying", Source.FLIGHT), Map.entry("allowflight", Source.FLIGHT),
            Map.entry("helmet", Source.ARMOR), Map.entry("chestplate", Source.ARMOR), Map.entry("leggings", Source.ARMOR), Map.entry("boots", Source.ARMOR),
            Map.entry("item", Source.INVENTORY), Map.entry("offhand", Source.INVENTORY), Map.entry("inventory", Source.INVENTORY),
            Map.entry("emptyslots", Source.INVENTORY), Map.entry("iteminhand", Source.INVENTORY));
    /** Propiedades de player que no cambian mientras está conectado. */
    private static final Set<String> PLAYER_CONSTANT = Set.of("name", "uuid", "uniqueid", "firstjoin", "locale", "isplayer", "type", "displayname");
    /** Funciones del lenguaje sin estado propio: solo cuentan sus argumentos. */
    private static final Set<String> PURE = Set.of("min", "max", "abs", "round", "floor", "ceil", "sqrt", "pow", "clamp", "len", "size",
            "lower", "upper", "trim", "replace", "substr", "split", "join", "str", "num", "int", "bool", "strip", "format", "default", "if");
    private static final Set<String> RANDOM = Set.of("random", "randomf", "chance");

    private final EnumSet<Source> sources = EnumSet.noneOf(Source.class);
    private final Set<String> pvars = new LinkedHashSet<>();
    private final Set<String> gvars = new LinkedHashSet<>();
    private final Set<String> states = new LinkedHashSet<>();
    private final Set<String> unknown = new LinkedHashSet<>();
    private final Set<String> random = new LinkedHashSet<>();
    private final Set<String> playerUses = new LinkedHashSet<>();

    private StateDeps() {
    }

    /** Analiza las expresiones de when. */
    public static StateDeps analyze(Collection<Expression> exprs) {
        StateDeps d = new StateDeps();
        for (Expression e : exprs) d.walk(e.root());
        return d;
    }

    public Set<Source> sources() {
        return sources;
    }

    public Set<String> pvars() {
        return pvars;
    }

    public Set<String> gvars() {
        return gvars;
    }

    /** Otros estados que lee con in_state('regla') (en minúsculas). */
    public Set<String> states() {
        return states;
    }

    /** Partes cuya dependencia no se pudo deducir (piden check_every). */
    public Set<String> unknown() {
        return unknown;
    }

    /** Funciones de azar usadas (un estado no puede ser aleatorio). */
    public Set<String> random() {
        return random;
    }

    /** Partes que necesitan un jugador (error en un estado del servidor). */
    public Set<String> playerUses() {
        return playerUses;
    }

    /** Agrega una fuente a mano (worlds de la regla → MOVE). */
    public void add(Source s) {
        sources.add(s);
    }

    /** true si nada puede cambiar el valor (ni eventos, ni variables, ni otros estados, ni partes sin deducir). */
    public boolean constant() {
        return sources.isEmpty() && pvars.isEmpty() && gvars.isEmpty() && states.isEmpty() && unknown.isEmpty();
    }

    /** Texto para /omni info: «vida, ubicación, pvar.racha, in_state('x')». */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Source s : sources) out.add(s.label);
        for (String v : pvars) out.add("pvar." + v);
        for (String v : gvars) out.add("gvar." + v);
        for (String s : states) out.add("in_state('" + s + "')");
        return out;
    }

    private static String key(String name) {
        return name.replace("_", "").toLowerCase(Locale.ROOT);
    }

    private void walk(Node n) {
        switch (n) {
            case Node.Literal l -> { }
            case Node.ListNode l -> l.items().forEach(this::walk);
            case Node.Ident i -> root(i.name(), null, i.name());
            case Node.VarRef v -> {
                if (v.scope() == Scope.PLAYER) {
                    pvars.add(v.name());
                    playerUses.add("pvar." + v.name());
                } else if (v.scope() == Scope.GLOBAL) {
                    gvars.add(v.name());
                }
            }
            case Node.Placeholder p -> unknown.add("el placeholder " + p.raw());
            case Node.DataRef d -> {
                // Spec 009: nadie avisa cuando cambia una variable de un objeto.
                unknown.add("la variable de objeto " + d.text());
                walk(d.target());
            }
            case Node.Prop p -> prop(p);
            case Node.Index i -> {
                if (i.target() instanceof Node.Ident id && i.index() instanceof Node.Literal lit && lit.value() instanceof String s) {
                    root(id.name(), s, n.text());
                } else {
                    walk(i.target());
                    walk(i.index());
                }
            }
            case Node.Call c -> call(c);
            case Node.MethodCall m -> {
                unknown.add("el método " + m.name() + "()");
                walk(m.target());
                m.args().forEach(this::walk);
            }
            case Node.Not x -> walk(x.operand());
            case Node.Neg x -> walk(x.operand());
            case Node.And a -> {
                walk(a.left());
                walk(a.right());
            }
            case Node.Or o -> {
                walk(o.left());
                walk(o.right());
            }
            case Node.Ternary t -> {
                walk(t.cond());
                walk(t.then());
                walk(t.otherwise());
            }
            case Node.Binary b -> {
                walk(b.left());
                walk(b.right());
            }
        }
    }

    /** Cadena de propiedades: decide la primera propiedad tras la raíz. */
    private void prop(Node.Prop p) {
        Node cur = p;
        String first = null;
        while (cur instanceof Node.Prop q) {
            first = q.name();
            cur = q.target();
        }
        if (cur instanceof Node.Ident id) root(id.name(), first, p.text());
        else walk(cur);
    }

    /** Una raíz con su primera propiedad (o null si va sola). */
    private void root(String root, String first, String text) {
        String k = first == null ? null : key(first);
        switch (root) {
            case "player" -> {
                playerUses.add(text);
                if (k == null) {
                    unknown.add("player (sin propiedad)");
                    return;
                }
                Source s = PLAYER_PROPS.get(k);
                if (s != null) sources.add(s);
                else if (!PLAYER_CONSTANT.contains(k)) unknown.add(text);
            }
            case "location" -> {
                playerUses.add(text);
                sources.add(Source.MOVE);
            }
            case "item" -> {
                playerUses.add(text);
                sources.add(Source.INVENTORY);
            }
            case "world" -> {
                if (k == null || k.equals("name") || k.equals("uid")) {
                    playerUses.add(text);
                    sources.add(Source.MOVE);
                } else if (k.equals("storm") || k.equals("thunder")) {
                    sources.add(Source.WEATHER);
                    sources.add(Source.MOVE);
                } else if (k.equals("players")) {
                    sources.add(Source.ONLINE);
                    sources.add(Source.MOVE);
                } else {
                    unknown.add(text);
                }
            }
            case "online" -> sources.add(Source.ONLINE);
            case "rule" -> { }
            default -> unknown.add(text);
        }
    }

    private void call(Node.Call c) {
        String name = c.name().toLowerCase(Locale.ROOT);
        if (RANDOM.contains(name)) {
            random.add(name + "()");
            return;
        }
        switch (name) {
            case "in_state" -> {
                if (c.args().size() == 1 && c.args().get(0) instanceof Node.Literal l && l.value() instanceof String s) {
                    states.add(s.toLowerCase(Locale.ROOT));
                } else {
                    unknown.add("in_state() con un nombre que no es un texto fijo");
                }
                return;
            }
            case "in_area", "distance" -> {
                if (name.equals("in_area")) playerUses.add("in_area()");
                sources.add(Source.MOVE);
                // distance(player, …): el jugador como lugar es movimiento, no «player sin propiedad».
                for (Node a : c.args()) {
                    if (a instanceof Node.Ident id && id.name().equals("player")) playerUses.add("player");
                    else walk(a);
                }
                return;
            }
            case "has_item" -> {
                playerUses.add("has_item()");
                sources.add(Source.INVENTORY);
            }
            case "online", "players" -> sources.add(Source.ONLINE);
            case "ttl" -> unknown.add("ttl() (cambia con el reloj)");
            default -> {
                if (!PURE.contains(name)) {
                    unknown.add("la función " + name + "()");
                    return;
                }
            }
        }
        c.args().forEach(this::walk);
    }
}
