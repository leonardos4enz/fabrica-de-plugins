package dev.leo.worldcraft.api;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.config.LoadedConfig;
import dev.leo.worldcraft.paper.Api;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 037: el contrato de la API (lo que se puede probar sin servidor; lo demás, en la compuerta 2 y la 3a). */
class ApiTest {
    @TempDir
    Path dir;

    @Test
    void H1_CA2_el_paquete_api_solo_usa_tipos_de_bukkit_y_del_jdk() throws Exception {
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/dev/leo/worldcraft/api"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = java.util.regex.Pattern.compile("dev\\.leo\\.worldcraft\\.(?!api\\b)[a-z]+").matcher(Files.readString(f));
                while (m.find()) bad.add(f.getFileName() + ": " + m.group());
            }
        }
        assertEquals(List.of(), bad, "el contrato no nombra clases internas (ADR 0008)");
        assertEquals(1, WorldcraftApi.API_VERSION);
    }

    @Test
    void H3_CA2_resultado_rechazado_y_hecho() {
        EditResult r = EditResult.refused("set", "You may not edit in nether.");
        assertEquals(-1, r.id());
        assertEquals(EditResult.Status.REFUSED, r.status());
        assertFalse(r.done());
        assertTrue(new EditResult(4, "set", 27, EditResult.Status.DONE, "").done());
    }

    @Test
    void H3_CA3_el_dueño_de_las_obras_de_un_plugin_es_fijo_y_propio() {
        assertEquals(Api.pluginOwner("Arena"), Api.pluginOwner("Arena"), "el mismo tras reiniciar: su historial sigue");
        assertNotEquals(Api.pluginOwner("Arena"), Api.pluginOwner("Islands"));
        assertNotEquals(new java.util.UUID(0, 0), Api.pluginOwner("CONSOLE"), "no es el de la consola");
    }

    @Test
    void H3_CA3_un_plugin_edita_con_default_y_los_grupos_de_plugin_groups() throws Exception {
        Files.copy(Path.of("src/main/resources/config.yml"), dir.resolve("config.yml"));
        LoadedConfig c = LoadedConfig.load(dir.toFile(), TestBlocks.REGISTRY, null);
        assertEquals(50_000, c.api().limits(c.limits()).maxBlocks(), "de fábrica: como un jugador común");
        assertEquals(2, c.api().limits(c.limits()).maxJobs());
        Files.writeString(dir.resolve("config.yml"), Files.readString(dir.resolve("config.yml"))
                .replace("plugin_groups: []", "plugin_groups: [builder]"));
        c = LoadedConfig.load(dir.toFile(), TestBlocks.REGISTRY, null);
        assertEquals(1_000_000, c.api().limits(c.limits()).maxBlocks());
        assertEquals(List.of("default", "builder"), c.api().limits(c.limits()).groups());
        assertTrue(c.api().limits(c.limits()).isBlocked("minecraft:bedrock", java.util.Map.of()),
                "builder no dice blocked_blocks: hereda los prohibidos de default");
    }
}
