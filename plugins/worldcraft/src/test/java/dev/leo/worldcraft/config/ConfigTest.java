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
        for (String f : new String[]{"config.yml", "presets.yml", "terrain.yml"}) Files.copy(RESOURCES.resolve(f), dir.resolve(f));
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

    /** Spec 027 H6 CA1: la sección schematics, con rangos; un config.yml de la 0.2.0 sin ella usa los de fábrica. */
    @Test
    void spec027_H6_CA1_seccion_schematics_validada() throws Exception {
        assertEquals(Settings.Schematics.DEFAULTS, load("history: { keep_days: 3 }\n", "").settings().schematics());
        assertEquals(java.util.List.of("plugins/WorldEdit/schematics", "plugins/FastAsyncWorldEdit/schematics"),
                Settings.Schematics.DEFAULTS.extraFolders());
        LoadedConfig c = load("schematics:\n  max_file_kb: 0\n  max_blocks: 1000\n  trash_dayz: 3\n  extra_folders: [a/b]\n", "");
        Settings.Schematics s = c.settings().schematics();
        assertEquals(10_240, s.maxFileKb());
        assertEquals(1000, s.maxBlocks());
        assertEquals(java.util.List.of("a/b"), s.extraFolders());
        String all = c.issues().all().toString();
        assertTrue(all.contains("schematics.max_file_kb: 0 is out of range"), all);
        assertTrue(all.contains("schematics.trash_dayz: unknown setting (did you mean 'trash_days'?)"), all);
    }

    /** Spec 028 plan §2: shapes y utilities con rangos; un config.yml de la 0.3.0 sin ellas usa 200 y 64. */
    @Test
    void spec028_secciones_shapes_y_utilities() throws Exception {
        Settings old = load("history: { keep_days: 3 }\n", "").settings();
        assertEquals(200, old.shapesMaxRadius());
        assertEquals(64, old.utilitiesMaxRadius());
        LoadedConfig c = load("shapes:\n  max_radius: 5000\nutilities:\n  max_radius: 32\n  max_radios: 3\n", "");
        assertEquals(200, c.settings().shapesMaxRadius());
        assertEquals(32, c.settings().utilitiesMaxRadius());
        String all = c.issues().all().toString();
        assertTrue(all.contains("shapes.max_radius: 5000 is out of range"), all);
        assertTrue(all.contains("utilities.max_radios: unknown setting (did you mean 'max_radius'?)"), all);
    }

    /** Spec 031: entities.* y, por grupo, max_entities y blocked_entities (prohibido si lo prohíben todos). */
    @Test
    void spec031_entidades_en_la_configuracion_y_los_limites() throws Exception {
        Settings old = load("history: { keep_days: 3 }\n", "").settings();
        assertEquals(32, old.butcherRadius());
        assertEquals(128, old.entitiesMaxRadius());
        LoadedConfig c = load("""
                entities: { butcher_radius: 400, max_radius: 64 }
                limits:
                  default: { max_entities: 10, blocked_entities: [tnt, wither] }
                  builder: { max_entities: 500, blocked_entities: [wither, Minecraft:Ender_Dragon] }
                  bad: { max_entities: -1 }
                """, "");
        assertEquals(64, c.settings().butcherRadius(), "no más que max_radius");
        Limits.Resolved none = c.limits().resolve(p -> false);
        assertEquals(10, none.maxEntities());
        assertTrue(none.isEntityBlocked("minecraft:tnt"));
        Limits.Resolved b = c.limits().resolve(p -> p.equals("worldcraft.limit.builder"));
        assertEquals(500, b.maxEntities());
        assertFalse(b.isEntityBlocked("minecraft:tnt"), "builder no la prohíbe");
        assertTrue(b.isEntityBlocked("minecraft:wither"));
        assertTrue(b.isEntityBlocked("minecraft:ender_dragon"), "la lista del grupo reemplaza la de default (como los bloques)");
        Limits.Resolved two = c.limits().resolve(p -> p.equals("worldcraft.limit.builder") || p.equals("worldcraft.limit.bad"));
        assertTrue(two.isEntityBlocked("minecraft:wither"), "bad hereda la de default y builder también la prohíbe");
        assertFalse(two.isEntityBlocked("minecraft:ender_dragon"), "default (heredada por bad) no lo prohíbe");
        assertTrue(c.issues().all().toString().contains("limits.bad.max_entities"), c.issues().all().toString());
        Limits.Resolved d = load("history: { keep_days: 3 }\n", "").limits().resolve(p -> false);
        assertEquals(200, d.maxEntities(), "de fábrica");
        assertTrue(d.isEntityBlocked("minecraft:command_block_minecart"));
    }

    @Test
    void spec032_H7_CA3_seccion_tools_validada() throws Exception {
        assertEquals(ToolSettings.DEFAULTS, load("history: { keep_days: 3 }\n", "").tools(), "un config.yml de la 0.7.0 usa lo de fábrica");
        LoadedConfig c = load("""
                tools:
                  range: 9000
                  brush_max_radius: 8
                  never_break: "bedrock, stonne"
                  tree_blocks: [oak_log, oak_leaves]
                  rnage: 3
                """, "");
        ToolSettings t = c.tools();
        assertEquals(200, t.range(), "fuera de rango: el de fábrica");
        assertEquals(8, t.brushMaxRadius());
        assertEquals(ToolSettings.DEFAULTS.neverBreak(), t.neverBreak(), "una máscara que no se entiende: la de fábrica");
        assertEquals("oak_log, oak_leaves", t.treeBlocks(), "también como lista");
        String all = c.issues().all().toString();
        assertTrue(all.contains("tools.range"), all);
        assertTrue(all.contains("tools.never_break"), all);
        assertTrue(all.contains("tools.rnage") && all.contains("did you mean 'range'"), all);
    }

    @Test
    void spec033_H8_CA1_seccion_nature_validada() throws Exception {
        assertEquals(NatureSettings.DEFAULTS, load("history: { keep_days: 3 }\n", "").nature(), "un config.yml de la 0.8.0 usa lo de fábrica");
        LoadedConfig c = load("""
                nature:
                  max_trees: 0
                  max_size: 10
                  soil: [grass_block, sand]
                  flora_grass: "50% short_grass, 50% popy"
                  flora_snad: dead_bush
                """, "masks: { desert: sand }\n");
        NatureSettings n = c.nature();
        assertEquals(300, n.maxTrees(), "fuera de rango: el de fábrica");
        assertEquals(10, n.maxSize());
        assertEquals("grass_block, sand", n.soil(), "también como lista");
        assertEquals(NatureSettings.DEFAULTS.floraGrass(), n.floraGrass(), "un patrón que no se entiende: el de fábrica");
        String all = c.issues().all().toString();
        assertTrue(all.contains("nature.max_trees"), all);
        assertTrue(all.contains("nature.flora_grass") && all.contains("poppy"), all);
        assertTrue(all.contains("nature.flora_snad") && all.contains("did you mean 'flora_sand'"), all);
        assertTrue(all.contains("masks.desert") && all.contains("biome"), "un preset con nombre de bioma avisa: " + all);
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

    @Test
    void spec034_H8_CA1_seccion_formulas_validada() throws Exception {
        assertEquals(FormulaSettings.DEFAULTS, load("history: { keep_days: 3 }\n", "").formulas(), "un config.yml de la 0.9.0 usa lo de fábrica");
        LoadedConfig c = load("""
                formulas:
                  timeout_ms: 20000
                  max_timeout_ms: 5000
                  max_steps: 5
                  max_length: 200
                  megabuf_sise: 10
                """, "");
        FormulaSettings f = c.formulas();
        assertEquals(5000, f.maxTimeoutMs());
        assertEquals(5000, f.timeoutMs(), "más que el tope: se baja al tope");
        assertEquals(FormulaSettings.DEFAULTS.maxSteps(), f.maxSteps(), "fuera de rango: el de fábrica");
        assertEquals(200, f.maxLength());
        assertEquals(200, c.parser().formulaLimits().maxLength(), "el analizador usa los topes de config.yml");
        String all = c.issues().all().toString();
        assertTrue(all.contains("formulas.timeout_ms"), all);
        assertTrue(all.contains("formulas.max_steps"), all);
        assertTrue(all.contains("formulas.megabuf_sise") && all.contains("did you mean 'megabuf_size'"), all);
        String text = "=" + "1+".repeat(150) + "1";
        assertThrows(dev.leo.worldcraft.pattern.ParseException.class, () -> c.parser().maskNear(text), "más largo que max_length");
    }

    @Test
    void spec035_H5_CA1_colores_minerales_cuevas_e_imagenes() throws Exception {
        LoadedConfig old = load("history: { keep_days: 3 }\n", "");
        assertEquals("[]", old.issues().all().stream().filter(i -> i.file().equals("config.yml")).toList().toString(), "un config.yml de la 0.10.0 carga sin avisos");
        CreateSettings d = old.create();
        assertEquals(8, d.ores().size());
        assertFalse(d.links(), "los links vienen apagados");
        assertEquals("minecraft:deepslate_coal_ore", d.ores().get(0).deepslate());
        assertEquals(-55, d.lavaY());
        LoadedConfig c = load("""
                colors:
                  remove: [red_wool, yelow_wool]
                  add: { stone: "#FF0000", stnoe: "#00FF00", glass: "verde" }
                ores:
                  mask: "stone, nada_de_esto"
                  max_size: 999
                  list:
                    gold: { block: gold_ore, size: 9, per_chunk: 4, min_y: -64, max_y: 32 }
                    bad: { block: gold_ore, min_y: 10, max_y: 0 }
                    worse: { block: oro, per_chunk: 3 }
                    nope: { size: 3 }
                caves:
                  lava_y: -60
                  maks: x
                images:
                  folder: "../afuera"
                  links: true
                  allowed_hosts: ["*.imgur.com", "no es un sitio"]
                """, "");
        CreateSettings s = c.create();
        var colors = c.parser().colors();
        assertEquals(-1, colors.colorOf("minecraft:red_wool"), "quitado");
        assertEquals(0xFF0000, colors.colorOf("minecraft:stone"), "color cambiado");
        assertEquals("minecraft:stone", colors.nearestId(0xFF0000));
        assertEquals(1, s.ores().size(), "solo el oro es válido");
        assertEquals("minecraft:gold_ore", s.ores().get(0).deepslate(), "sin deepslate, el mismo bloque");
        assertEquals(CreateSettings.ORES_MASK, s.oresMask());
        assertEquals(64, s.maxVein());
        assertEquals(-60, s.lavaY());
        assertEquals("images", s.imagesFolder(), "una carpeta con .. no sale");
        assertTrue(s.links());
        assertEquals(java.util.List.of("*.imgur.com"), s.allowedHosts());
        String all = c.issues().all().toString();
        for (String path : new String[]{"colors.remove", "colors.add.stnoe", "colors.add.glass", "ores.mask", "ores.max_size",
                "ores.list.bad", "ores.list.worse.block", "ores.list.nope.block", "caves.maks", "images.folder", "images.allowed_hosts"}) {
            assertTrue(all.contains(path), path + " en " + all);
        }
        assertTrue(all.contains("did you mean 'yellow_wool'"), all);
        assertTrue(all.contains("did you mean 'stone'"), all);
        assertTrue(all.contains("did you mean 'mask'"), all);
        // Un preset puede ser un patrón por color (la tabla va antes que los presets).
        LoadedConfig p = load("", "patterns:\n  shade: \"#darken\"\n");
        assertEquals("[]", p.issues().all().stream().filter(i -> !i.file().equals("terrain.yml")).toList().toString());
        assertEquals("$shade", p.parser().pattern("$shade").text());
    }
}
