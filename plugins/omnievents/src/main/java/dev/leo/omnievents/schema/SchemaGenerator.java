package dev.leo.omnievents.schema;

import com.google.gson.GsonBuilder;
import dev.leo.omnievents.action.ActionParser;
import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.expr.Functions;
import dev.leo.omnievents.rule.RuleLoader;
import dev.leo.omnievents.rule.RuleOptions;
import dev.leo.omnievents.trigger.TriggerType;
import dev.leo.omnievents.trigger.Triggers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Esquema JSON (draft-07) de los archivos de reglas, para el autocompletado y los errores del editor (spec 007, H1).
 * Sale del registro: disparadores, acciones, funciones y opciones de regla. Determinista: misma entrada, mismo texto.
 */
public final class SchemaGenerator {
    public static final String URL = "https://raw.githubusercontent.com/leonardos4enz/fabrica-de-plugins/main/docs/omnievents/omnievents.schema.json";
    /** Acciones cuyas claves hermanas son listas de acciones. */
    private static final Set<String> ACTION_LIST_KEYS = Set.of("then", "else", "do");
    private static final String DURATION = "^\\s*(\\d+(\\.\\d+)?|(\\d+(\\.\\d+)?\\s*([mM][sS]|[tTsSmMhHdDwW])\\s*)+)$";
    private static final String BUDGET = "^\\s*(\\d+(\\.\\d+)?|\\.\\d+)\\s*([mM][sS]|[uUµ][sS])\\s*$";
    private static final String TIME = "^([01]?\\d|2[0-3]):[0-5]\\d$";

    private SchemaGenerator() {
    }

    public static String generate(Triggers triggers, Actions actions) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("$schema", "http://json-schema.org/draft-07/schema#");
        root.put("$id", URL);
        root.put("title", "Reglas de OmniEvents");
        root.put("description", "Cada clave de primer nivel es una regla. Generado del código con ./gradlew :omnievents:schema o /omni schema; las expresiones se revisan con /omni validate.");
        root.put("type", "object");
        root.put("additionalProperties", ref("rule"));
        Map<String, Object> defs = new LinkedHashMap<>();
        defs.put("rule", rule(triggers));
        defs.put("expression", expression());
        defs.put("duration", map("description", "Duración: 10s, 5m, 1h30m, 2d, 1w, 20t (ticks), 500ms, o un número de segundos.",
                "anyOf", List.of(map("type", "number", "minimum", 0), map("type", "string", "pattern", DURATION))));
        defs.put("budget", map("type", "string", "pattern", BUDGET, "description", "Presupuesto de tiempo: 0.5ms, 200us o 200µs."));
        defs.put("texts", map("anyOf", List.of(map("type", "string"), map("type", "array", "items", map("type", "string")))));
        defs.put("conditions", map("description", RuleOptions.all().get("conditions").description(),
                "anyOf", List.of(ref("condition"), map("type", "array", "items", ref("condition")))));
        defs.put("condition", condition());
        defs.put("actions", map("description", "Una acción o una lista de acciones.",
                "anyOf", List.of(ref("action"), map("type", "array", "items", ref("action")), map("type", "null"))));
        defs.put("action", action(actions));
        ActionParser.MODIFIER_DOCS.forEach((m, desc) -> {
            Map<String, Object> p = switch (m) {
                case "where", "when" -> with(ref("expression"), desc);
                case "chance" -> map("anyOf", List.of(map("type", "number", "minimum", 0, "maximum", 100), map("type", "string", "pattern", "^\\s*\\d+(\\.\\d+)?\\s*%?\\s*$")));
                default -> map("type", "string");
            };
            p.put("description", desc);
            defs.put("modifier_" + m, p);
        });
        root.put("definitions", defs);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
    }

    // ------------------------------------------------------------------ reglas

    private static Map<String, Object> rule(Triggers triggers) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (RuleOptions.Option o : RuleOptions.all().values()) {
            String desc = o.description() + (o.trigger() == null ? "" : " (para trigger: " + o.trigger() + ")");
            props.put(o.name(), with(option(o, triggers), desc));
        }
        List<Object> conditional = new ArrayList<>();
        conditional.add(requires(List.of("timer", "repetitive", "repetitive_server"), map("anyOf", List.of(map("required", List.of("every")), map("required", List.of("repetitive_time"))))));
        conditional.add(requires(List.of("clock"), map("required", List.of("at"))));
        conditional.add(requires(List.of("command"), map("required", List.of("command"))));
        conditional.add(requires(List.of("custom"), map("required", List.of("event"))));
        return map("type", "object", "description", "Una regla: cuando pase trigger, si se cumplen conditions, corre actions.",
                "properties", props, "additionalProperties", false,
                "anyOf", List.of(map("required", List.of("trigger")), map("required", List.of("type"))),
                "allOf", conditional);
    }

    private static Map<String, Object> requires(List<String> triggerNames, Map<String, Object> then) {
        return map("if", map("properties", map("trigger", map("enum", triggerNames)), "required", List.of("trigger")), "then", then);
    }

    private static Map<String, Object> option(RuleOptions.Option o, Triggers triggers) {
        return switch (o.kind()) {
            case TRIGGER -> {
                List<String> names = new ArrayList<>(), descs = new ArrayList<>();
                for (TriggerType t : triggers.all()) {
                    names.add(t.name());
                    descs.add(t.description());
                }
                RuleLoader.TRIGGER_ALIASES.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                    names.add(e.getKey());
                    descs.add("Alias de " + e.getValue() + " (ConditionalEvents).");
                });
                yield map("type", "string", "enum", names, "enumDescriptions", descs, "markdownEnumDescriptions", descs);
            }
            case BOOLEAN -> map("type", "boolean");
            case INTEGER -> map("type", "integer", "minimum", 0);
            case TEXT -> map("type", "string");
            case TEXTS -> ref("texts");
            case ENUM -> map("type", "string", "enum", bothCases(o.values()));
            case DURATION -> ref("duration");
            case RESET -> map("anyOf", List.of(map("type", "string", "enum", bothCases(o.values())), ref("duration")));
            case TIMES -> {
                Map<String, Object> t = map("type", "string", "pattern", TIME);
                yield map("anyOf", List.of(t, map("type", "array", "items", t)));
            }
            case CONDITIONS -> ref("conditions");
            case ACTIONS -> ref("actions");
            case COMMAND -> {
                Map<String, Object> keys = new LinkedHashMap<>();
                RuleOptions.COMMAND_KEYS.forEach((k, d) -> {
                    Map<String, Object> p = switch (k) {
                        case "name" -> map("type", "string", "pattern", "^[A-Za-z0-9_\\-]{1,32}$");
                        case "aliases" -> map("type", "array", "items", map("type", "string"));
                        case "tab" -> map("type", "array");
                        default -> map("type", "string");
                    };
                    p.put("description", d);
                    keys.put(k, p);
                });
                yield map("anyOf", List.of(map("type", "string", "pattern", "^[A-Za-z0-9_\\-]{1,32}$"),
                        map("type", "object", "properties", keys, "required", List.of("name"), "additionalProperties", false)));
            }
            case BUDGET -> ref("budget");
            case LIST -> map("type", "array", "items", map("type", "string"));
        };
    }

    private static List<String> bothCases(List<String> values) {
        List<String> out = new ArrayList<>(values);
        for (String v : values) out.add(v.toUpperCase(Locale.ROOT));
        return out;
    }

    // ------------------------------------------------------------------ condiciones y expresiones

    static String expressionHelp() {
        List<String> sigs = new ArrayList<>();
        for (Functions.Def d : Functions.all()) sigs.add(d.signature());
        return "Expresión de OmniEvents (el editor no la revisa: usa /omni validate). Operadores: && || ! == != < <= > >= ~= contains"
                + " startsWith endsWith matches in, + - * / %, c ? a : b. Variables: var.x, pvar.x, gvar.x. Funciones: " + String.join(", ", sigs) + ".";
    }

    private static Map<String, Object> expression() {
        return map("type", List.of("string", "number", "boolean"), "description", expressionHelp());
    }

    private static Map<String, Object> condition() {
        Map<String, String> k = RuleOptions.CONDITION_KEYS;
        Map<String, Object> expr = map("type", "object", "properties", map(
                "expr", with(ref("expression"), k.get("expr")), "deny", with(ref("actions"), k.get("deny"))),
                "required", List.of("expr"), "additionalProperties", false);
        Map<String, Object> any = map("type", "object", "properties", map(
                "any", map("type", "integer", "minimum", 1, "description", k.get("any")),
                "of", map("type", "array", "minItems", 1, "items", ref("condition"), "description", k.get("of")),
                "deny", with(ref("actions"), k.get("deny"))),
                "required", List.of("any", "of"), "additionalProperties", false);
        return map("description", "Una condición: una expresión, o un mapa con expr o any + of, y deny opcional.",
                "anyOf", List.of(ref("expression"), expr, any));
    }

    // ------------------------------------------------------------------ acciones

    private static Map<String, Object> action(Actions actions) {
        List<Object> branches = new ArrayList<>();
        branches.add(map("type", "string", "pattern", shortForm(actions),
                "description", "Forma corta: \"tipo: valor\" (p. ej. \"message: hola\" o \"cancel\")."));
        for (String name : actions.names()) branches.add(actionObject(name, name, actions, actions.doc(name).description()));
        ActionParser.ALIASES.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .filter(e -> actions.has(e.getValue()))
                .forEach(e -> branches.add(actionObject(e.getKey(), e.getValue(), actions, "Alias de " + e.getValue() + " (ConditionalEvents). " + actions.doc(e.getValue()).description())));
        return map("description", "Una acción: un texto \"tipo: valor\" o un mapa con la acción y sus modificadores (to, where, chance, when).",
                "anyOf", branches);
    }

    private static String shortForm(Actions actions) {
        List<String> names = new ArrayList<>(actions.names());
        ActionParser.ALIASES.keySet().stream().sorted().forEach(names::add);
        List<String> pre = new ArrayList<>(ActionParser.CE_TARGET_PREFIXES);
        pre.sort(null);
        List<String> simple = new ArrayList<>(), param = new ArrayList<>();
        for (String p : pre) (p.equals("to_all") || p.equals("to_target") ? simple : param).add(p);
        return "^\\s*(?:(?:" + String.join("|", simple) + ")\\s*:\\s*|(?:" + String.join("|", param) + ")\\s*:[^:]*:\\s*)?(?:"
                + String.join("|", names) + ")\\s*(?::[\\s\\S]*)?$";
    }

    private static Map<String, Object> actionObject(String key, String canonical, Actions actions, String description) {
        Actions.Doc d = actions.doc(canonical);
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> value = value(d);
        value.put("description", description + (d.value().isEmpty() ? "" : " Valor: " + d.value() + "."));
        props.put(key, value);
        d.keys().forEach((k, desc) -> props.put(k, with(ACTION_LIST_KEYS.contains(k) ? ref("actions") : map("type", List.of("string", "number")), desc)));
        for (String m : ActionParser.MODIFIER_DOCS.keySet()) {
            if (!(m.equals("to") && actions.isFlow(canonical))) props.put(m, ref("modifier_" + m));
        }
        return map("type", "object", "properties", props, "required", List.of(key), "additionalProperties", false);
    }

    private static Map<String, Object> value(Actions.Doc d) {
        Map<String, Object> scalar = map("type", List.of("string", "number", "boolean", "null"));
        return switch (d.shape()) {
            case SCALAR -> scalar;
            case ITEM, CALL -> {
                Map<String, Object> keys = new LinkedHashMap<>();
                d.valueKeys().forEach((k, desc) -> keys.put(k, with(k.equals("args") ? map("type", "object", "additionalProperties", ref("expression")) : map(), desc)));
                yield map("anyOf", List.of(scalar, map("type", "object", "properties", keys, "additionalProperties", false)));
            }
            case BRANCHES -> {
                Map<String, Object> keys = new LinkedHashMap<>();
                d.valueKeys().forEach((k, desc) -> keys.put(k, with(k.equals("do") ? ref("actions") : map("type", "number", "minimum", 0), desc)));
                yield map("type", "array", "minItems", 1, "items", map("type", "object", "properties", keys, "additionalProperties", false));
            }
        };
    }

    // ------------------------------------------------------------------ utilidades

    private static Map<String, Object> ref(String def) {
        return map("$ref", "#/definitions/" + def);
    }

    /** Agrega una descripción. Con $ref, draft-07 ignora lo hermano: se envuelve en allOf. */
    private static Map<String, Object> with(Map<String, Object> schema, String description) {
        Map<String, Object> m = schema.containsKey("$ref") ? map("allOf", List.of(schema)) : new LinkedHashMap<>(schema);
        m.put("description", description);
        return m;
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
