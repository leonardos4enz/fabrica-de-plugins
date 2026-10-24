package dev.leo.omnievents.action;

import dev.leo.omnievents.expr.ExprException;
import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.rule.Problems;
import dev.leo.omnievents.util.Suggest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Convierte las listas de acciones del YAML en {@link ActionNode}s, validando todo al cargar. */
public final class ActionParser {
    private static final Set<String> MODIFIERS = Set.of("to", "where", "chance", "when");
    /** Nombres de ConditionalEvents y sinónimos → nombre propio. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("cancel_event", "cancel"), Map.entry("console_command", "console"), Map.entry("player_command", "command"),
            Map.entry("playsound", "sound"), Map.entry("give_potion_effect", "effect"), Map.entry("remove_potion_effect", "remove_effect"),
            Map.entry("wait", "delay"), Map.entry("player_send_chat", "chat"), Map.entry("centered_message", "message"),
            Map.entry("mini_message", "message"), Map.entry("json_message", "message"), Map.entry("set_on_fire", "fire"),
            Map.entry("call_event", "call"), Map.entry("lightning_strike", "lightning"), Map.entry("stopsound", "stop_sound"),
            Map.entry("execute", "call"), Map.entry("tp", "teleport"), Map.entry("msg", "message"), Map.entry("bc", "broadcast"),
            Map.entry("prevent_join", "deny_join"), Map.entry("potion", "effect"));
    private static final Map<String, String> CE_TARGETS = Map.of(
            "to_all", "all", "to_target", "target", "to_world", "world", "to_player", "player", "to_range", "radius");

    private final Actions catalog;
    private final Problems problems;
    private final Set<String> declared = new java.util.HashSet<>();
    private final Set<String> usedTypes = new java.util.HashSet<>();

    /** Nombres que una acción declara (as: de for_each/repeat) y que las expresiones pueden usar como raíz. */
    public void declare(String name) {
        declared.add(name);
    }

    public Set<String> declared() {
        return declared;
    }

    /** Tipos de acción usados en todo lo analizado (para validaciones de la regla). */
    public Set<String> usedTypes() {
        return usedTypes;
    }

    public ActionParser(Actions catalog, Problems problems) {
        this.catalog = catalog;
        this.problems = problems;
    }

    public Problems problems() {
        return problems;
    }

    public List<ActionNode> parseList(Object raw, String path) {
        List<ActionNode> out = new ArrayList<>();
        if (raw == null) return out;
        if (!(raw instanceof List<?> list)) {
            ActionNode n = parseOne(raw, path);
            if (n != null) out.add(n);
            return out;
        }
        for (int i = 0; i < list.size(); i++) {
            ActionNode n = parseOne(list.get(i), path + "[" + i + "]");
            if (n != null) out.add(n);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private ActionNode parseOne(Object item, String path) {
        try {
            if (item instanceof Map<?, ?> m) return parseMap(stringKeys((Map<Object, Object>) m), path);
            if (item instanceof String s) return parseString(s, path, null);
            problems.error(path, "una acción debe ser un texto ('message: hola') o un mapa (message: hola)");
        } catch (ExprException e) {
            problems.error(path, e.getMessage());
        } catch (IllegalArgumentException e) {
            problems.error(path, e.getMessage());
        }
        return null;
    }

    private static Map<String, Object> stringKeys(Map<Object, Object> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> e : m.entrySet()) out.put(String.valueOf(e.getKey()), e.getValue());
        return out;
    }

    /** Forma de ConditionalEvents: "tipo: valor", con prefijos to_all:, to_target:… */
    private ActionNode parseString(String s, String path, TargetSpec forced) {
        String t = s.trim();
        int colon = t.indexOf(':');
        String type = (colon < 0 ? t : t.substring(0, colon)).trim().toLowerCase(Locale.ROOT);
        String value = colon < 0 ? "" : t.substring(colon + 1).trim();
        if (CE_TARGETS.containsKey(type)) {
            String target = CE_TARGETS.get(type);
            if (type.equals("to_world") || type.equals("to_player") || type.equals("to_range")) {
                int c2 = value.indexOf(':');
                if (c2 < 0) throw new IllegalArgumentException("'" + type + "' necesita 'parámetro: acción'");
                String param = value.substring(0, c2).trim();
                if (type.equals("to_range")) param = param.split(";")[0];
                target = target + ":" + param;
                value = value.substring(c2 + 1).trim();
            }
            return parseString(value, path, TargetSpec.parse(target));
        }
        Map<String, Object> node = new LinkedHashMap<>();
        node.put(type, value);
        ActionNode n = parseMap(node, path);
        if (n != null && forced != null) n = new ActionNode(n.type(), n.path(), n.action(), forced, n.where(), n.chance(), n.when());
        return n;
    }

    private ActionNode parseMap(Map<String, Object> node, String path) {
        String type = null;
        for (String k : node.keySet()) {
            String key = canonical(k);
            if (MODIFIERS.contains(key)) continue;
            if (catalog.has(key)) {
                type = k;
                break;
            }
        }
        if (type == null) {
            String first = node.keySet().stream().filter(k -> !MODIFIERS.contains(k)).findFirst().orElse(null);
            if (first == null) throw new IllegalArgumentException("el nodo solo tiene modificadores; falta la acción");
            throw new IllegalArgumentException("acción desconocida '" + first + "'" + Suggest.hint(canonical(first), catalog.names()));
        }
        String canonical = canonical(type);
        Map<String, Object> extra = new LinkedHashMap<>(node);
        Object value = extra.remove(type);
        Object to = extra.remove("to"), where = extra.remove("where"), chance = extra.remove("chance"), when = extra.remove("when");
        Args args = new Args(canonical, value, extra, path, this);
        usedTypes.add(canonical);
        Action action = catalog.create(canonical, args);
        for (String k : args.unusedExtras()) problems.warn(path, "la clave '" + k + "' no la usa la acción '" + canonical + "' y se ignora");
        TargetSpec spec = to == null ? null : TargetSpec.parse(Values.str(to));
        if (spec != null && catalog.isFlow(canonical)) throw new IllegalArgumentException("'to' no se usa con '" + canonical + "': usa for_each");
        double ch = 100;
        if (chance != null) {
            Double d = Values.toNumberOrNull(String.valueOf(chance).replace("%", ""));
            if (d == null || d < 0 || d > 100) throw new IllegalArgumentException("chance debe ser un porcentaje entre 0 y 100");
            ch = d;
        }
        return new ActionNode(canonical, path, action, spec,
                where == null ? null : Expression.compile(Values.str(where)), ch,
                when == null ? null : Expression.compile(Values.str(when)));
    }

    static String canonical(String k) {
        String l = k.trim().toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(l, l);
    }
}
