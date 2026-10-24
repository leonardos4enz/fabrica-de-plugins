package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.trigger.Triggers;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleLoaderTest {
    private final RuleLoader loader = new RuleLoader(new Actions(null), new Triggers());

    private static String dump(Problems p) {
        return p.all().stream().map(Problems.Problem::toString).collect(Collectors.joining("\n"));
    }

    private Rule parse(String yaml, Problems p) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(yaml);
        String name = y.getKeys(false).iterator().next();
        return loader.parse(name, "prueba.yml", y.getConfigurationSection(name), p.file("prueba.yml"));
    }

    @Test
    void H1_CA4_los_ejemplos_cargan_sin_errores_ni_avisos() {
        Problems p = new Problems();
        Map<String, Rule> rules = loader.loadFolder(new File("src/main/resources/rules"), p);
        assertEquals(0, p.size(), dump(p));
        assertTrue(rules.size() >= 15, "se esperaban al menos 15 reglas de ejemplo y hay " + rules.size());
    }

    @Test
    void H1_CA1_nombre_repetido_nombra_los_dos_archivos(@TempDir File dir) throws IOException {
        Files.writeString(new File(dir, "a.yml").toPath(), "uno:\n  trigger: player_join\n  actions: [\"message: a\"]\n", StandardCharsets.UTF_8);
        Files.writeString(new File(dir, "b.yml").toPath(), "Uno:\n  trigger: player_join\n  actions: [\"message: b\"]\n", StandardCharsets.UTF_8);
        Problems p = new Problems();
        Map<String, Rule> rules = loader.loadFolder(dir, p);
        assertEquals(1, rules.size());
        assertEquals(1, p.errors());
        assertTrue(p.all().get(0).message().contains("a.yml"), dump(p));
        assertEquals("b.yml", p.all().get(0).file());
    }

    @Test
    void H1_CA3_un_archivo_roto_no_tumba_a_los_demas(@TempDir File dir) throws IOException {
        Files.writeString(new File(dir, "bueno.yml").toPath(), "bien:\n  trigger: player_join\n  actions: [\"message: hola\"]\n", StandardCharsets.UTF_8);
        Files.writeString(new File(dir, "roto.yml").toPath(), "mal:\n  trigger: [\n", StandardCharsets.UTF_8);
        Problems p = new Problems();
        Map<String, Rule> rules = loader.loadFolder(dir, p);
        assertNotNull(rules.get("bien"));
        assertEquals(1, p.errors());
        assertEquals("roto.yml", p.all().get(0).file());
    }

    @Test
    void H2_CA5_error_de_sintaxis_con_ruta_y_columna() throws Exception {
        Problems p = new Problems();
        Rule r = parse("r:\n  trigger: block_break\n  conditions:\n    - \"block.type == 'STONE'\"\n    - \"player.level >= \"\n  actions: [cancel]\n", p);
        assertNull(r);
        Problems.Problem e = p.all().get(0);
        assertEquals("r.conditions[1]", e.path());
        assertTrue(e.message().contains("columna"), e.message());
    }

    @Test
    void H5_CA6_accion_desconocida_sugiere() throws Exception {
        Problems p = new Problems();
        assertNull(parse("r:\n  trigger: player_join\n  actions:\n    - mesage: hola\n", p));
        assertTrue(p.all().get(0).message().contains("message"), dump(p));
        assertEquals("r.actions[0]", p.all().get(0).path());
    }

    @Test
    void disparador_desconocido_sugiere() throws Exception {
        Problems p = new Problems();
        assertNull(parse("r:\n  trigger: blok_break\n  actions: [cancel]\n", p));
        assertTrue(p.all().get(0).message().contains("block_break"), dump(p));
    }

    @Test
    void H3_CA4_raiz_inexistente_es_aviso_con_sugerencia() throws Exception {
        Problems p = new Problems();
        Rule r = parse("r:\n  trigger: block_break\n  conditions: \"blok.type == 'STONE'\"\n  actions: [cancel]\n", p);
        assertNotNull(r, "un aviso no impide cargar la regla");
        assertEquals(0, p.errors());
        assertTrue(dump(p).contains("block"), dump(p));
    }

    @Test
    void H4_CA4_las_dos_formas_de_accion_y_prefijos_de_ConditionalEvents() throws Exception {
        Problems p = new Problems();
        Rule r = parse("r:\n  trigger: player_attack\n  actions:\n    - \"message: hola\"\n    - message: hola\n    - \"to_target: message: te golpearon\"\n"
                + "    - \"cancel_event: true\"\n    - message: a todos\n      to: all\n      where: \"player.level > 3\"\n      chance: 50\n", p);
        assertNotNull(r, dump(p));
        assertEquals(0, p.size(), dump(p));
    }

    @Test
    void H4_CA1_control_de_flujo_valida_lo_anidado() throws Exception {
        Problems p = new Problems();
        assertNull(parse("r:\n  trigger: player_join\n  actions:\n    - if: \"true\"\n      then:\n        - repeat: 3\n          do:\n            - mesage: x\n", p));
        assertEquals("r.actions[0].then[0].do[0]", p.all().get(0).path(), dump(p));
    }

    @Test
    void to_no_se_usa_con_control_de_flujo() throws Exception {
        Problems p = new Problems();
        assertNull(parse("r:\n  trigger: player_join\n  actions:\n    - if: \"true\"\n      to: all\n      then: [stop]\n", p));
    }

    @Test
    void execute_de_ConditionalEvents_orienta_al_importador() throws Exception {
        Problems p = new Problems();
        assertNull(parse("r:\n  trigger: player_command\n  conditions: [\"%args_length% < 1 execute error1\"]\n  actions: [cancel]\n", p));
        assertTrue(p.all().get(0).message().contains("import"), dump(p));
    }

    @Test
    void H8_opciones_de_enfriamiento_y_limite() throws Exception {
        Problems p = new Problems();
        Rule r = parse("r:\n  trigger: player_join\n  cooldown: 1h30m\n  cooldown_scope: global\n  limit: 3\n  limit_reset: 12h\n  actions: [\"message: x\"]\n", p);
        assertNotNull(r, dump(p));
        assertEquals(5_400_000, r.cooldownMs());
        assertTrue(r.cooldownGlobal());
        assertEquals(3, r.limit());
        assertEquals(Rule.Reset.DURATION, r.limitReset());
        Problems q = new Problems();
        assertNull(parse("r:\n  trigger: player_join\n  cooldown: 5 minutos\n  actions: [\"message: x\"]\n", q));
        assertTrue(dump(q).contains("duración"), dump(q));
    }

    @Test
    void opcion_desconocida_es_aviso() throws Exception {
        Problems p = new Problems();
        assertNotNull(parse("r:\n  trigger: player_join\n  cooldwn: 5s\n  actions: [\"message: x\"]\n", p));
        assertTrue(dump(p).contains("cooldown"), dump(p));
        assertFalse(p.errors() > 0);
    }

    @Test
    void cancel_en_evento_no_cancelable_avisa() throws Exception {
        Problems p = new Problems();
        assertNotNull(parse("r:\n  trigger: player_join\n  actions: [cancel]\n", p));
        assertTrue(dump(p).contains("no se puede cancelar"), dump(p));
    }
}
