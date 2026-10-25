package dev.leo.omnievents.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import dev.leo.omnievents.action.Action;
import dev.leo.omnievents.action.ActionParser;
import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.expr.Functions;
import dev.leo.omnievents.rule.Problems;
import dev.leo.omnievents.rule.RuleOptions;
import dev.leo.omnievents.trigger.TriggerType;
import dev.leo.omnievents.trigger.Triggers;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Triggers triggers = new Triggers();
    private final Actions actions = new Actions(null);
    private final JsonSchema schema = compile(SchemaGenerator.generate(triggers, actions));

    private static JsonSchema compile(String text) {
        try {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(JSON.readTree(text));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Set<ValidationMessage> validate(String yaml) {
        return validate(schema, yaml);
    }

    private static Set<ValidationMessage> validate(JsonSchema s, String yaml) {
        Object doc = new Yaml().load(yaml);
        JsonNode node = JSON.valueToTree(doc == null ? Map.of() : doc);
        return s.validate(node);
    }

    private static String dump(Set<ValidationMessage> errors) {
        return errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining("\n"));
    }

    private static String rule(String body) {
        return "r:\n" + body.lines().map(l -> "  " + l).collect(Collectors.joining("\n")) + "\n";
    }

    // ------------------------------------------------------------------ H2: el registro está documentado

    @Test
    void H2_CA2_todo_lo_registrado_tiene_descripcion() {
        List<String> missing = new ArrayList<>();
        for (TriggerType t : triggers.all()) if (t.description().isBlank()) missing.add("disparador " + t.name());
        for (String n : actions.names()) {
            Actions.Doc d = actions.doc(n);
            if (d == Actions.EXTERNAL || d.description().isBlank() || d.value().isBlank() || d.example().isBlank()) missing.add("acción " + n);
        }
        for (Functions.Def f : Functions.all()) if (f.description().equals(Functions.EXTERNAL)) missing.add("función " + f.name());
        for (RuleOptions.Option o : RuleOptions.all().values()) if (o.description().isBlank()) missing.add("opción " + o.name());
        assertTrue(missing.isEmpty(), "sin descripción: " + missing);
    }

    @Test
    void H2_CA2_cada_ejemplo_de_accion_carga_sin_problemas_y_usa_sus_claves() {
        for (String n : actions.names()) {
            Actions.Doc d = actions.doc(n);
            Object parsed = new Yaml().load(d.example());
            Problems p = new Problems().file("ejemplo");
            new ActionParser(actions, p).parseList(parsed, n);
            assertEquals(0, p.size(), n + ": " + p.all());
            for (String k : d.keys().keySet()) {
                assertTrue(parsed instanceof Map<?, ?> m && m.containsKey(k), "el ejemplo de " + n + " no usa su clave " + k);
            }
        }
    }

    @Test
    void H2_CA2_publicados_al_dia() throws IOException {
        Path docs = Path.of("../../docs/omnievents");
        Map<String, String> files = SchemaFiles.render(triggers, actions);
        for (Map.Entry<String, String> e : files.entrySet()) {
            Path f = docs.resolve(e.getKey());
            assertTrue(Files.exists(f), "falta " + f + ": corre ./gradlew :omnievents:schema");
            assertEquals(e.getValue(), Files.readString(f, StandardCharsets.UTF_8).replace("\r\n", "\n"),
                    f + " está desactualizado: corre ./gradlew :omnievents:schema");
        }
    }

    @Test
    void H2_CA1_la_referencia_lista_todo() {
        String ref = ReferenceGenerator.generate(triggers, actions);
        for (TriggerType t : triggers.all()) assertTrue(ref.contains("| `" + t.name() + "` |"), t.name());
        for (String n : actions.names()) assertTrue(ref.contains("#### `" + n + "`"), n);
        for (Functions.Def f : Functions.all()) assertTrue(ref.contains("`" + f.signature() + "`"), f.name());
        for (String o : RuleOptions.names()) assertTrue(ref.contains("| `" + o + "` |"), o);
        for (String a : ActionParser.ALIASES.keySet()) assertTrue(ref.contains("`" + a + "` →"), a);
    }

    // ------------------------------------------------------------------ H1: el esquema

    @Test
    void H1_CA7_los_ejemplos_del_plugin_validan() throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.walk(Path.of("src/main/resources/rules"))) {
            files = s.filter(f -> f.toString().endsWith(".yml")).sorted().toList();
        }
        assertTrue(files.size() >= 7, "se esperaban los 7 archivos de ejemplo");
        JsonSchema pack = compile(SchemaGenerator.generatePack(triggers, actions));
        for (Path f : files) {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            // Spec 011: un pack.yml tiene su propio esquema.
            boolean isPack = f.getFileName().toString().equals("pack.yml");
            assertTrue(text.startsWith("# yaml-language-server: $schema=" + (isPack ? SchemaGenerator.PACK_URL : SchemaGenerator.URL)), f + " no apunta al esquema");
            Set<ValidationMessage> errors = isPack ? validate(pack, text) : validate(text);
            assertTrue(errors.isEmpty(), f + ":\n" + dump(errors));
        }
    }

    @Test
    void S011_H5_CA1_plantillas_y_pack_yml_en_los_esquemas() {
        assertTrue(validate(rule("template: true\nparams: { cantidad: 1 }\nactions: [\"message: hola ${cantidad}\"]")).isEmpty());
        assertTrue(validate(rule("extends: base\nwith: { cantidad: 5 }")).isEmpty());
        assertFalse(validate(rule("actions: [\"message: sin trigger ni extends\"]")).isEmpty());
        assertFalse(validate(rule("extends: base\nwith: [1, 2]")).isEmpty());
        JsonSchema pack = compile(SchemaGenerator.generatePack(triggers, actions));
        assertTrue(validate(pack, "name: tienda\nversion: 1.2.0\nparams: { moneda: pvar.m }\nrequires: { omnievents: \">=1.6.0\", packs: { base: \">=1.0, <2.0\" } }\n"
                + "migrations:\n  \"1.2.0\":\n    - log: hola\n").isEmpty());
        assertFalse(validate(pack, "name: Tienda Grande\n").isEmpty());
        assertFalse(validate(pack, "version: uno\n").isEmpty());
        assertFalse(validate(pack, "requires: { omnievents: \"mayor que 1\" }\n").isEmpty());
        assertFalse(validate(pack, "migrations:\n  \"1.2.0\":\n    - no_existe: x\n").isEmpty());
        assertFalse(validate(pack, "nombre: tienda\n").isEmpty());
    }

    @Test
    void H1_CA7_lo_que_produce_el_importador_valida(@org.junit.jupiter.api.io.TempDir Path dir) throws IOException {
        Path ce = dir.resolve("ConditionalEvents");
        Files.createDirectories(ce);
        Files.writeString(ce.resolve("config.yml"), """
                Events:
                  ejemplo:
                    type: player_command
                    conditions:
                    - "%main_command% == /nivel"
                    - "%args_length% < 1 execute error1"
                    actions:
                      default:
                      - "cancel_event: true"
                      - "to_all: message: &a%player% usó /nivel"
                      error1:
                      - "message: &cUsa /nivel <jugador>"
                      - "firework: colors:RED;type:BALL"
                    ignore_with_permission: ce.ignorar
                    cooldown: 10
                  repetido:
                    type: repetitive
                    repetitive_time: 200
                    actions:
                      default:
                      - "playsound: ENTITY_PLAYER_LEVELUP;1;1"
                """, StandardCharsets.UTF_8);
        Path out = dir.resolve("rules");
        new dev.leo.omnievents.importer.ConditionalEventsImporter().importFolder(ce.toFile(), out.toFile());
        String yml = Files.readString(out.resolve("config.yml"), StandardCharsets.UTF_8);
        Set<ValidationMessage> errors = validate(yml);
        assertTrue(errors.isEmpty(), dump(errors) + "\n" + yml);
    }

    @Test
    void H1_CA3_el_ejemplo_de_cada_accion_y_su_forma_corta_validan() {
        for (String n : actions.names()) {
            String example = actions.doc(n).example().lines().map(l -> "    " + l).collect(Collectors.joining("\n")).replaceFirst("^    ", "  - ");
            Set<ValidationMessage> errors = validate("r:\n  trigger: call\n  actions:\n" + example + "\n");
            assertTrue(errors.isEmpty(), n + ":\n" + dump(errors));
            assertTrue(validate(rule("trigger: call\nactions: [\"" + n + ": x\"]")).isEmpty(), "forma corta de " + n);
        }
        for (String a : ActionParser.ALIASES.keySet()) assertTrue(validate(rule("trigger: call\nactions:\n  - " + a + ": x")).isEmpty(), "alias " + a);
        assertTrue(validate(rule("trigger: call\nactions:\n  - \"to_all: message: hola\"\n  - \"to_world: world: broadcast: hola\"\n  - stop")).isEmpty());
    }

    @Test
    void H1_CA4_sintaxis_de_la_006() {
        String ok = rule("""
                trigger: call
                budget: 200us
                conditions:
                  - "player != null"
                  - expr: "player.level >= 5"
                    deny: [{message: "nivel"}]
                  - any: 1
                    of:
                      - "player.op"
                      - any: 2
                        of: ["a", {expr: "b"}]
                    deny:
                      - "message: grupo"
                actions: [stop]""");
        Set<ValidationMessage> errors = validate(ok);
        assertTrue(errors.isEmpty(), dump(errors));
    }

    @Test
    void H1_CA5_marca_lo_que_el_cargador_rechaza() {
        Map<String, String> bad = new LinkedHashMap<>();
        bad.put("opción desconocida", rule("trigger: player_join\ncooldwon: 5s\nactions: [stop]"));
        bad.put("disparador desconocido", rule("trigger: blok_break\nactions: [stop]"));
        bad.put("sin trigger", rule("actions: [stop]"));
        bad.put("acción desconocida en mapa", rule("trigger: call\nactions: [{mesage: hola}]"));
        bad.put("acción desconocida en forma corta", rule("trigger: call\nactions: [\"mesage: hola\"]"));
        bad.put("clave que la acción no usa", rule("trigger: call\nactions: [{message: hola, do: [stop]}]"));
        bad.put("to en una acción de flujo", rule("trigger: call\nactions: [{stop: '', to: all}]"));
        bad.put("condición sin expr ni any", rule("trigger: call\nconditions: [{deny: [stop]}]\nactions: [stop]"));
        bad.put("any: 0", rule("trigger: call\nconditions: [{any: 0, of: [a]}]\nactions: [stop]"));
        bad.put("any sin of", rule("trigger: call\nconditions: [{any: 1}]\nactions: [stop]"));
        bad.put("budget sin unidad", rule("trigger: call\nbudget: 5\nactions: [stop]"));
        bad.put("duración inválida", rule("trigger: call\ncooldown: 5 minutos\nactions: [stop]"));
        bad.put("timer sin every", rule("trigger: timer\nactions: [stop]"));
        bad.put("clock con hora inválida", rule("trigger: clock\nat: \"25:00\"\nactions: [stop]"));
        bad.put("command sin nombre", rule("trigger: command\ncommand: {aliases: [x]}\nactions: [stop]"));
        bad.put("priority inválida", rule("trigger: call\npriority: urgente\nactions: [stop]"));
        bad.forEach((what, yaml) -> assertFalse(validate(yaml).isEmpty(), what + " debería marcarse"));
    }

    @Test
    void H1_CA6_las_expresiones_no_se_validan_y_listan_las_funciones() {
        assertTrue(validate(rule("trigger: call\nconditions: \"esto (no es && una expresión\"\nactions: [stop]")).isEmpty());
        String json = SchemaGenerator.generate(triggers, actions);
        for (Functions.Def f : Functions.all()) assertTrue(json.contains(f.signature()), f.name());
    }

    // ------------------------------------------------------------------ H3: lo de otros plugins

    @Test
    void H3_CA3_lo_que_registra_otro_plugin_aparece() {
        Actions withExternal = new Actions(null);
        withExternal.register("mi_accion", a -> Action.of(ctx -> { }));
        Functions.register("mi_funcion_007", 0, 1, (c, a) -> 1.0);
        try {
            JsonSchema s = compile(SchemaGenerator.generate(triggers, withExternal));
            assertTrue(validate(s, rule("trigger: call\nactions: [{mi_accion: hola}, \"mi_accion: chau\"]")).isEmpty());
            assertFalse(validate(rule("trigger: call\nactions: [{mi_accion: hola}]")).isEmpty(), "sin registrar no se acepta");
            String ref = ReferenceGenerator.generate(triggers, withExternal);
            assertTrue(ref.contains("### Otros plugins") && ref.contains("#### `mi_accion`") && ref.contains("Acción registrada por otro plugin."), ref);
            assertTrue(ref.contains("`mi_funcion_007(…)`"));
        } finally {
            Functions.unregister("mi_funcion_007"); // el registro de funciones es global: no ensuciar las otras pruebas
        }
    }
}
