package dev.leo.worldcraft.config;

import dev.leo.worldcraft.TestBlocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    @TempDir
    Path dir;

    static final Path RESOURCES = Path.of("src/main/resources");

    LoadedConfig load(String config, String presets) throws Exception {
        Files.writeString(dir.resolve("config.yml"), config);
        Files.writeString(dir.resolve("presets.yml"), presets);
        return LoadedConfig.load(dir.toFile(), TestBlocks.REGISTRY, null);
    }

    @Test
    void H8_CA3_los_archivos_que_trae_el_plugin_cargan_sin_avisos() throws Exception {
        for (String f : new String[]{"config.yml", "presets.yml"}) Files.copy(RESOURCES.resolve(f), dir.resolve(f));
        LoadedConfig c = LoadedConfig.load(dir.toFile(), TestBlocks.REGISTRY, null);
        assertEquals("[]", c.issues().all().toString());
        assertEquals(Settings.DEFAULTS, c.settings());
        assertTrue(c.patternPresets().size() >= 2);
        assertTrue(c.limits().groups().keySet().containsAll(Set.of("default", "builder", "admin")));
    }

    @Test
    void H8_CA2_valores_invalidos_y_claves_desconocidas_con_sugerencia() throws Exception {
        LoadedConfig c = load("""
                performance:
                  max_ms_per_tik: 5
                  max_blocks_per_tick: muchos
                history:
                  keep_days: -4
                limits:
                  default: { max_blocks: 1000, blocked_blocks: [bedrock, stnoe] }
                  Big: { max_blocks: 5 }
                """, """
                patterns:
                  ok: "stone, dirt"
                  bad: "50% stone, dirt"
                masks:
                  m: "#nope"
                colors: {}
                """);
        String all = c.issues().all().toString();
        assertTrue(all.contains("config.yml › performance.max_ms_per_tik: unknown setting (did you mean 'max_ms_per_tick'?)"), all);
        assertTrue(all.contains("config.yml › performance.max_blocks_per_tick: 'muchos' is not a whole number"), all);
        assertTrue(all.contains("config.yml › history.keep_days: -4 is out of range"), all);
        assertTrue(all.contains("config.yml › limits.default.blocked_blocks[1]: Unknown block 'stnoe' (did you mean 'stone'?)"), all);
        assertTrue(all.contains("config.yml › limits.Big: group names use lowercase"), all);
        assertTrue(all.contains("presets.yml › patterns.bad: Give every block"), all);
        assertTrue(all.contains("presets.yml › masks.m: Unknown block tag"), all);
        assertTrue(all.contains("presets.yml › colors: unknown section"), all);
        assertEquals(Map.of("ok", "stone, dirt"), c.patternPresets());
        assertEquals(8, c.settings().maxMsPerTick());
        Limits.Resolved r = c.limits().resolve(p -> false);
        assertEquals(1000, r.maxBlocks());
        assertTrue(r.isBlocked("minecraft:bedrock", Map.of()), "lo válido de la lista sigue");
    }

    /** Decisión del dueño: la confirmación se apaga desde config.yml y de fábrica salta sobre 50 000 bloques. */
    @Test
    void H4_CA1_confirmacion_desactivable_y_de_fabrica_sobre_50000() throws Exception {
        assertTrue(Settings.DEFAULTS.confirmEnabled());
        assertEquals(50_000, Settings.DEFAULTS.confirmAbove());
        LoadedConfig c = load("confirm:\n  enabled: false\n  above_blocks: 10\n", "");
        assertFalse(c.settings().confirmEnabled());
        assertEquals(10, c.settings().confirmAbove());
        LoadedConfig bad = load("confirm:\n  enabled: maybe\n", "");
        assertTrue(bad.settings().confirmEnabled());
        assertTrue(bad.issues().all().toString().contains("confirm.enabled: 'maybe' is not true or false"));
    }

    @Test
    void H8_CA2_un_archivo_roto_conserva_lo_anterior() throws Exception {
        LoadedConfig first = load("performance: { max_ms_per_tick: 4 }\n", "patterns: { a: stone }\n");
        Files.writeString(dir.resolve("config.yml"), "performance: [\n");
        Files.writeString(dir.resolve("presets.yml"), "patterns: {\n");
        LoadedConfig second = LoadedConfig.load(dir.toFile(), TestBlocks.REGISTRY, first);
        assertEquals(4, second.settings().maxMsPerTick());
        assertEquals(Map.of("a", "stone"), second.patternPresets());
        assertTrue(second.issues().all().toString().contains("config.yml: not valid YAML"));
    }

    @Test
    void H7_CA1_con_varios_grupos_gana_lo_mas_generoso() throws Exception {
        LoadedConfig c = load("""
                limits:
                  default: { max_blocks: 100, max_jobs: 1, worlds: [world], blocked_blocks: [bedrock, barrier, command_block] }
                  builder: { max_blocks: 5000, worlds: [creative], blocked_blocks: [bedrock, barrier] }
                  mod: { max_jobs: 4, blocked_blocks: [bedrock] }
                  admin: { max_blocks: 900000, blocked_blocks: [] }
                """, "");
        Limits.Resolved none = c.limits().resolve(p -> false);
        assertEquals(100, none.maxBlocks());
        assertTrue(none.world("world"));
        assertFalse(none.world("creative"));
        assertTrue(none.isBlocked("minecraft:command_block", Map.of()));

        Limits.Resolved both = c.limits().resolve(p -> p.equals("worldcraft.limit.builder") || p.equals("worldcraft.limit.mod"));
        assertEquals(5000, both.maxBlocks());
        assertEquals(4, both.maxJobs());
        assertTrue(both.world("creative") && both.world("world"), "mod hereda los mundos de default");
        assertFalse(both.isBlocked("minecraft:barrier", Map.of()), "mod permite la barrera");
        assertTrue(both.isBlocked("minecraft:bedrock", Map.of()), "todos prohíben la roca madre");
        assertEquals(java.util.List.of("default", "builder", "mod"), both.groups());

        Limits.Resolved admin = c.limits().resolve(p -> p.equals("worldcraft.limit.admin"));
        assertFalse(admin.isBlocked("minecraft:bedrock", Map.of()));
        assertEquals(900000, admin.maxBlocks());
    }
}
