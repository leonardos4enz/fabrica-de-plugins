package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.trigger.Triggers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 019: el paquete de ejemplos en inglés (rules/examples-en) y sus regiones de WorldGuard. */
class EjemplosEnInglesTest {
    private static final File RULES = new File("src/main/resources/rules");

    private static String dump(Problems p) {
        return p.all().stream().map(Problems.Problem::toString).collect(Collectors.joining("\n"));
    }

    /** Solo las reglas del paquete (las demás de rules/ también se cargan: un pack.yml no puede estar en la raíz). */
    private static Map<String, Rule> examples(Map<String, Rule> all) {
        Map<String, Rule> out = new java.util.LinkedHashMap<>();
        all.forEach((k, r) -> { if (k.startsWith("examples:")) out.put(k, r); });
        return out;
    }

    /** Los problemas de los archivos del paquete (los ejemplos en español sí avisan sin Vault, WorldGuard ni Citizens). */
    private static String dumpExamples(Problems p) {
        return p.all().stream().filter(x -> x.file().startsWith("examples-en/")).map(Problems.Problem::toString).collect(Collectors.joining("\n"));
    }

    private static RuleLoader sinIntegraciones() {
        return new RuleLoader(new Actions(null), new Triggers(), "1.11.0").integrations(Set.of());
    }

    @Test
    void H1_CA1_H3_CA3_viene_apagado_y_sin_worldguard_carga_sin_avisos() {
        Problems p = new Problems();
        RuleLoader loader = sinIntegraciones();
        Map<String, Rule> rules = examples(loader.loadFolder(RULES, p));
        assertEquals("", dumpExamples(p));
        Pack pack = loader.packs().stream().filter(x -> x.name().equals("examples")).findFirst().orElseThrow();
        assertFalse(pack.enabled());
        assertTrue(rules.values().stream().noneMatch(Rule::enabled), "todo el paquete viene apagado");
        assertTrue(rules.keySet().containsAll(List.of("examples:welcome", "examples:daily_login", "examples:daily", "examples:spawn_zone",
                "examples:safe_spawn", "examples:chat_filter", "examples:region_welcome", "examples:vip_only", "examples:where", "examples:sell_wheat", "examples:npc_guide",
                "examples:npc_quest", "examples:stats", "examples:bedrock_hint", "examples:hide_spam")), rules.keySet().toString());
    }

    @Test
    void H1_CA4_cada_regla_trae_pruebas_y_pasan() {
        Problems p = new Problems();
        Map<String, Rule> rules = examples(new RuleLoader(new Actions(null), new Triggers()).loadFolder(RULES, p));
        assertEquals(0, p.size(), dump(p));
        assertTrue(rules.size() >= 20, rules.keySet().toString());
        // Un menú se compila en reglas (apertura, cierre y cada clic); el clic de «Close» solo cierra: no necesitan prueba.
        List<String> sinPruebas = rules.values().stream().filter(r -> r.tests().isEmpty()).map(Rule::name).toList();
        assertEquals(List.of("examples:shop.menu", "examples:shop.menu.on_close", "examples:shop.menu.items.close.click"), sinPruebas);
        RuleTester.Summary s = new RuleTester(n -> rules.get(n.toLowerCase()), 1000, 16).runAll(rules.values());
        String failed = s.results().stream().filter(r -> !r.ok())
                .map(r -> r.rule().name() + " «" + r.test().name() + "»: " + r.failures() + "\n  " + String.join("\n  ", r.trace()))
                .collect(Collectors.joining("\n"));
        assertEquals(0, s.failed(), failed);
        assertEquals(53, s.results().size(), "casos de tests: del paquete");
    }

    @Test
    void F02_una_regla_apagada_no_avisa_de_la_integracion_que_le_falta(@TempDir File dir) throws IOException {
        String region = "r:\n  trigger: region_enter\n  actions: [stop]\n";
        Files.createDirectories(dir.toPath().resolve("apagado"));
        Files.writeString(dir.toPath().resolve("apagado/pack.yml"), "name: apagado\nenabled: false\n", StandardCharsets.UTF_8);
        Files.writeString(dir.toPath().resolve("apagado/r.yml"), region, StandardCharsets.UTF_8);
        Files.writeString(dir.toPath().resolve("suelta.yml"), "s:\n  trigger: region_enter\n  enabled: false\n  actions: [stop]\n", StandardCharsets.UTF_8);
        Files.createDirectories(dir.toPath().resolve("prendido"));
        Files.writeString(dir.toPath().resolve("prendido/pack.yml"), "name: prendido\n", StandardCharsets.UTF_8);
        Files.writeString(dir.toPath().resolve("prendido/r.yml"), region, StandardCharsets.UTF_8);
        Problems p = new Problems();
        sinIntegraciones().loadFolder(dir, p);
        assertEquals(1, p.warnings(), dump(p));
        assertTrue(dump(p).contains("prendido:r: las regiones (region_enter) necesita WorldGuard"), dump(p));
    }
}
