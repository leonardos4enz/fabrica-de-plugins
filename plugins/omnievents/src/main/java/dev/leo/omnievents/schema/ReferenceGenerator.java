package dev.leo.omnievents.schema;

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
import java.util.Map;
import java.util.TreeMap;

/** Referencia en Markdown de todo lo que entiende OmniEvents, generada del registro (spec 007, H2). */
public final class ReferenceGenerator {
    private ReferenceGenerator() {
    }

    public static String generate(Triggers triggers, Actions actions) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Referencia de OmniEvents\n\n");
        sb.append("> Generada del código con `./gradlew :omnievents:schema` (o `/omni schema` en el servidor): no se edita a mano.\n");
        sb.append("> Una prueba falla si queda desactualizada. Guía y ejemplos: [README](../../plugins/omnievents/README.md).\n\n");
        sb.append("Autocompletado y errores en VS Code (extensión YAML de Red Hat): pon esta línea al inicio de cada archivo de reglas.\n\n");
        sb.append("```yaml\n# yaml-language-server: $schema=").append(SchemaGenerator.URL).append("\n```\n\n");
        sb.append("El editor revisa opciones, disparadores, acciones y la forma de las condiciones; las expresiones, los materiales, ")
                .append("los sonidos y los efectos se revisan con `/omni validate`. Los nombres van en minúsculas.\n\n");
        sb.append("Contenido: [opciones de una regla](#opciones-de-una-regla) · [condiciones](#condiciones) · ")
                .append("[disparadores](#disparadores) · [acciones](#acciones) · [funciones](#funciones)\n\n");

        sb.append("## Opciones de una regla\n| Opción | Valor | Disparador | Qué hace |\n|---|---|---|---|\n");
        for (RuleOptions.Option o : RuleOptions.all().values()) {
            sb.append("| `").append(o.name()).append("` | ").append(kind(o)).append(" | ")
                    .append(o.trigger() == null ? "todos" : "`" + o.trigger() + "`").append(" | ").append(cell(o.description())).append(" |\n");
        }
        sb.append("\nBloque `command` (para `trigger: command`):\n\n| Clave | Qué hace |\n|---|---|\n");
        RuleOptions.COMMAND_KEYS.forEach((k, d) -> sb.append("| `").append(k).append("` | ").append(cell(d)).append(" |\n"));
        sb.append("\nCada caso de `tests` (spec 010; corren con `/omni test` y al cargar):\n\n| Clave | Qué hace |\n|---|---|\n");
        RuleOptions.TEST_KEYS.forEach((k, d) -> sb.append("| `").append(k).append("` | ").append(cell(d)).append(" |\n"));
        sb.append("\nClaves de `expect` (además, variables: `pvar.x`, `gvar.x`, `var.x`, `block.data.x`; `null` = que no exista):\n\n| Clave | Qué hace |\n|---|---|\n");
        RuleOptions.EXPECT_KEYS.forEach((k, d) -> sb.append("| `").append(k).append("` | ").append(cell(d)).append(" |\n"));

        sb.append("\n## Condiciones\nCada elemento de `conditions` es una expresión, o un mapa con estas claves:\n\n| Clave | Qué hace |\n|---|---|\n");
        RuleOptions.CONDITION_KEYS.forEach((k, d) -> sb.append("| `").append(k).append("` | ").append(cell(d)).append(" |\n"));
        sb.append("""

                ```yaml
                conditions:
                  - "player != null"
                  - expr: "player.level >= 5"
                    deny:
                      - message: "<red>Necesitas nivel 5."
                  - any: 2
                    of: ["perm('omnievents.vip')", "has_item('EMERALD', 1)", "player.world.time > 12000"]
                ```
                """);

        sb.append("\n## Disparadores\nRaíces que todos ofrecen además de las suyas: ");
        sb.append(code(Triggers.COMMON_ROOTS)).append(".\n");
        Map<String, List<TriggerType>> byGroup = new LinkedHashMap<>();
        for (TriggerType t : triggers.all()) byGroup.computeIfAbsent(t.group(), g -> new ArrayList<>()).add(t);
        byGroup.forEach((g, list) -> {
            sb.append("\n### ").append(capital(g)).append("\n| Disparador | Raíces | Qué es |\n|---|---|---|\n");
            for (TriggerType t : list) {
                List<String> notes = new ArrayList<>();
                if (t.cancellable()) notes.add("se puede cancelar");
                if (t.async()) notes.add("asíncrono");
                sb.append("| `").append(t.name()).append("` | ").append(code(new ArrayList<>(t.roots()))).append(" | ")
                        .append(cell(t.description())).append(notes.isEmpty() ? "" : " *(" + String.join(", ", notes) + ")*").append(" |\n");
            }
        });
        sb.append("\nAlias: ");
        List<String> ta = new ArrayList<>();
        new TreeMap<>(RuleLoader.TRIGGER_ALIASES).forEach((k, v) -> ta.add("`" + k + "` → `" + v + "`"));
        sb.append(String.join(", ", ta)).append(".\n");

        sb.append("\n## Acciones\nDos formas equivalentes: `- message: hola` (mapa) o `- \"message: hola\"` (texto). Modificadores de cualquier acción ")
                .append("(salvo `to` en las de flujo):\n\n| Modificador | Qué hace |\n|---|---|\n");
        ActionParser.MODIFIER_DOCS.forEach((k, d) -> sb.append("| `").append(k).append("` | ").append(cell(d)).append(" |\n"));
        Map<String, List<String>> actionsByGroup = new TreeMap<>((a, b) -> a.equals(b) ? 0 : groupOrder(a) != groupOrder(b) ? groupOrder(a) - groupOrder(b) : a.compareTo(b));
        for (String n : actions.names()) actionsByGroup.computeIfAbsent(actions.doc(n).group(), g -> new ArrayList<>()).add(n);
        actionsByGroup.forEach((g, names) -> {
            sb.append("\n### ").append(capital(g)).append("\n");
            for (String n : names) {
                Actions.Doc d = actions.doc(n);
                sb.append("\n#### `").append(n).append("`\n").append(md(d.description())).append("\n\n");
                if (!d.value().isEmpty()) sb.append("Valor: ").append(md(d.value())).append(".\n");
                if (!d.valueKeys().isEmpty()) {
                    sb.append(d.shape() == Actions.Shape.BRANCHES ? "\nCada rama:\n" : "\nEn forma de mapa:\n");
                    d.valueKeys().forEach((k, v) -> sb.append("- `").append(k).append("`: ").append(md(v)).append('\n'));
                }
                if (!d.keys().isEmpty()) {
                    sb.append("\nClaves:\n");
                    d.keys().forEach((k, v) -> sb.append("- `").append(k).append("`: ").append(md(v)).append('\n'));
                }
                if (!d.example().isEmpty()) sb.append("\n```yaml\n").append(listItem(d.example())).append("```\n");
            }
        });
        sb.append("\nAlias (de ConditionalEvents y sinónimos): ");
        List<String> aa = new ArrayList<>();
        new TreeMap<>(ActionParser.ALIASES).forEach((k, v) -> aa.add("`" + k + "` → `" + v + "`"));
        sb.append(String.join(", ", aa)).append(".\n");

        sb.append("\n## Funciones\nOperadores: `&&` `||` `!` (`and` `or` `not`), `==` `!=` `<` `<=` `>` `>=`, `~=` (igual sin mayúsculas), ")
                .append("`contains` `startsWith` `endsWith` `matches` `in`, `+ - * / %`, `c ? a : b`. Variables: `var.x` (de la ejecución), ")
                .append("`pvar.x` (del jugador), `gvar.x` (global).\n\n| Función | Qué hace |\n|---|---|\n");
        for (Functions.Def d : Functions.all()) sb.append("| `").append(d.signature()).append("` | ").append(cell(d.description())).append(" |\n");
        return sb.toString();
    }

    /** Los grupos de acciones en el orden del registro; los de otros plugins al final. */
    private static final List<String> GROUPS = List.of("flujo", "mensajes", "comandos", "jugador", "mundo", "evento");

    private static int groupOrder(String g) {
        int i = GROUPS.indexOf(g);
        return i < 0 ? GROUPS.size() : i;
    }

    private static String kind(RuleOptions.Option o) {
        String k = switch (o.kind()) {
            case TRIGGER -> "disparador";
            case BOOLEAN -> "`true` / `false`";
            case INTEGER -> "entero";
            case TEXT -> "texto";
            case TEXTS -> "texto o lista";
            case ENUM, RESET -> String.join(", ", o.values().stream().map(v -> "`" + v + "`").toList()) + (o.kind() == RuleOptions.Kind.RESET ? " o una duración" : "");
            case DURATION -> "duración";
            case TIMES -> "`HH:mm` o lista";
            case CONDITIONS -> "condiciones";
            case ACTIONS -> "acciones";
            case COMMAND -> "nombre o mapa";
            case BUDGET -> "`0.5ms`, `200us`";
            case LIST -> "lista de nombres";
            case TESTS -> "lista de casos";
        };
        return o.aliasOf() == null ? k : k + " (sinónimo de `" + o.aliasOf() + "`)";
    }

    private static String listItem(String yaml) {
        StringBuilder sb = new StringBuilder();
        String[] lines = yaml.split("\n");
        for (int i = 0; i < lines.length; i++) sb.append(i == 0 ? "- " : "  ").append(lines[i]).append('\n');
        return sb.toString();
    }

    private static String code(List<String> items) {
        return items.isEmpty() ? "—" : String.join(" ", items.stream().map(s -> "`" + s + "`").toList());
    }

    private static String cell(String s) {
        return md(s).replace("|", "\\|");
    }

    /** Texto suelto en Markdown: un <nombre> se vería como etiqueta HTML. */
    private static String md(String s) {
        return s.replace("<", "&lt;");
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
