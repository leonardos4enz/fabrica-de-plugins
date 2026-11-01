package dev.leo.worldcraft.terrain;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.command.WorldcraftCommand;
import dev.leo.worldcraft.config.Issues;
import dev.leo.worldcraft.config.TerrainConfig;
import dev.leo.worldcraft.pattern.Parser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** terrain.yml, la sección terrain, las opciones clave=valor y los ajustes del pincel (T5, T6). */
class TerrainConfigTest {
    final TerrainLab lab = new TerrainLab();

    @Test
    void H6_CA1_lo_de_fabrica_carga_sin_avisos_con_los_siete_estilos() {
        assertEquals("[]", lab.issues.all().toString());
        assertEquals(List.of("alpine", "hills", "desert", "volcano", "snowy", "meadow", "end"), List.copyOf(lab.cfg.styles().keySet()));
        assertTrue(lab.cfg.previewFirst());
        assertEquals(16, lab.cfg.brushMaxRadius());
        assertNotNull(lab.cfg.styles().get("meadow").log(), "la pradera trae árboles");
        assertTrue(lab.cfg.styles().get("meadow").leaves().states().get(0).contains("persistent=true"));
    }

    TerrainSettings load(String section, String file, Issues issues) throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(section);
        YamlConfiguration t = new YamlConfiguration();
        t.loadFromString(file);
        return TerrainConfig.load(c.getConfigurationSection("terrain"), t, new Parser(TestBlocks.REGISTRY, Map.of(), Map.of()), issues);
    }

    @Test
    void H4_CA3_errores_con_ruta_y_sugerencia_y_lo_malo_se_omite() throws Exception {
        Issues is = new Issues();
        TerrainSettings s = load("""
                terrain:
                  reach: 9999
                  brsh: {}
                  ground: "#nope"
                """, """
                sizes:
                  mountain: { small: {radius: 10, height: 12}, medim: {radius: 3} }
                defaults:
                  mountain: { style: alpnie, peaks: 3, roughness: 0.5, ridges: 0.5, sharpness: 1, crater: 0 }
                  island: { style: ok, depth: 0.9, relief: 0.1, spikes: 0.5, roots: 0.5, trees: 2 }
                styles:
                  ok:
                    blend: 0.05
                    layers:
                      - thickness: 1
                        rules:
                          - { block: stnoe }
                          - { block: grass_block, slope_abve: 30 }
                          - { block: dirt, height_above: 2 }
                      - rules: [ { block: stone } ]
                  Bad: { layers: [] }
                  empty: { layers: [ { rules: [ { block: nope } ] } ] }
                """, is);
        String all = is.all().toString();
        assertTrue(all.contains("config.yml › terrain.reach: 9999 is out of range"), all);
        assertTrue(all.contains("config.yml › terrain.brsh: unknown setting (did you mean 'brush'?)"), all);
        assertTrue(all.contains("config.yml › terrain.ground: Unknown block tag"), all);
        assertTrue(all.contains("terrain.yml › sizes.mountain.medim: unknown size (did you mean 'medium'?)"), all);
        assertTrue(all.contains("terrain.yml › defaults.mountain.style: unknown style 'alpnie'"), all);
        assertTrue(all.contains("terrain.yml › styles.ok.layers[0].rules[0].block: Unknown block 'stnoe' (did you mean 'stone'?)"), all);
        assertTrue(all.contains("terrain.yml › styles.ok.layers[0].rules[1].slope_abve: unknown condition (did you mean 'slope_above'?)"), all);
        assertTrue(all.contains("terrain.yml › styles.ok.layers[0].rules[2].height_above: '2' must be a number from 0 to 1"), all);
        assertTrue(all.contains("terrain.yml › styles.Bad: style names use lowercase"), all);
        assertTrue(all.contains("terrain.yml › styles.empty.layers[0].rules: no valid rule"), all);
        assertEquals(Set.of("ok"), s.styles().keySet(), "lo malo se omite");
        assertEquals(1, s.styles().get("ok").layers().get(0).rules().size(), "queda la regla de pasto");
        assertEquals(200, s.reach(), "un valor fuera de rango usa el de fábrica");
    }

    @Test
    void H4_CA1_opciones_con_rangos_tamanos_estilos_y_sugerencias() {
        Options.Resolved o = lab.options(Options.Tool.MOUNTAIN, "large", "volcano", "peaks=2", "seed=77");
        assertEquals(56, o.integer("radius"));
        assertEquals(70, o.integer("height"));
        assertEquals("volcano", o.style().name());
        assertEquals(2, o.integer("peaks"), "lo escrito gana al estilo");
        assertEquals(0.3, o.number("crater"), 1e-9, "el estilo trae su cráter");
        assertEquals(77, o.seed());
        Options.Resolved n = lab.options(Options.Tool.MOUNTAIN, "64");
        assertEquals(64, n.integer("radius"));
        assertEquals(80, n.integer("height"), "un radio solo: la altura sigue la proporción de medium");
        assertEquals(1234, n.seed(), "sin seed= se usa la nueva");
        assertEquals("Unknown option 'peeks='" + " (did you mean 'peaks'?)", err(Options.Tool.MOUNTAIN, "peeks=3"));
        assertTrue(err(Options.Tool.MOUNTAIN, "peaks=9").startsWith("peaks 9 is out of range (1..7)"));
        assertTrue(err(Options.Tool.MOUNTAIN, "radius=900").contains("out of range (4..160)"), "el tope de config.yml");
        assertTrue(err(Options.Tool.ISLAND, "lagre").startsWith("'lagre' is not a size or a style (did you mean 'large'?)"));
        assertTrue(err(Options.Tool.ISLAND, "style=medow").startsWith("Unknown style 'medow' (did you mean 'meadow'?)"));
        assertTrue(err(Options.Tool.ISLAND, "trees=lots").contains("needs a number"));
    }

    String err(Options.Tool t, String... w) {
        return assertThrows(IllegalArgumentException.class, () -> lab.options(t, w)).getMessage();
    }

    @Test
    void H3_CA1_H4_CA4_ajustes_del_pincel_por_palabras_y_claves_y_de_ida_y_vuelta_en_el_item() {
        List<String> styles = List.copyOf(lab.cfg.styles().keySet());
        BrushSettings b = parse(BrushSettings.DEFAULT, styles, "smooth", "8", "70%");
        assertEquals(BrushSettings.Mode.SMOOTH, b.mode());
        assertEquals(8, b.radius());
        assertEquals(0.7, b.strength(), 1e-9);
        BrushSettings p = parse(b, styles, "70%", "grass_block,", "30%", "podzol", "mask=grass_block,dirt");
        assertEquals(BrushSettings.Mode.PAINT, p.mode(), "unos bloques pasan a pintar");
        assertEquals("70% grass_block, 30% podzol", p.pattern());
        assertEquals("grass_block,dirt", p.mask());
        assertEquals(8, p.radius(), "lo no dicho se conserva");
        BrushSettings s = parse(p, styles, "paint", "style=desert");
        assertEquals("desert", s.style());
        assertNull(s.pattern());
        assertEquals(s, BrushSettings.decode(s.encode()), "el ítem guarda y devuelve lo mismo");
        assertEquals(BrushSettings.DEFAULT, BrushSettings.decode("mode=nope;radius=x"), "un ítem raro no rompe");
        assertTrue(perr(styles, "smoth").startsWith("Unknown brush mode 'smoth' (did you mean 'smooth'?)"));
        assertTrue(perr(styles, "raise", "99").contains("out of range (1..16)"));
        assertTrue(perr(styles, "raise", "4", "3").contains("strength 3 is out of range"));
        assertTrue(perr(styles, "raise", "4", "0.5", "stone").startsWith("Only the paint mode takes blocks"));
        assertTrue(perr(styles, "streng=1").startsWith("Unknown option 'streng=' (did you mean 'strength'?)"));
        assertEquals("smooth · radius 8 · strength 70%", b.describe());
    }

    BrushSettings parse(BrushSettings base, List<String> styles, String... args) {
        List<String> words = new java.util.ArrayList<>();
        Map<String, String> kv = WorldcraftCommandAccess.keyValues(List.of(args), words);
        return WorldcraftCommandAccess.brushSettings(base, words, kv, 16, styles);
    }

    String perr(List<String> styles, String... args) {
        return assertThrows(IllegalArgumentException.class, () -> parse(BrushSettings.DEFAULT, styles, args)).getMessage();
    }

    @Test
    void H4_CA1_at_con_coordenadas_relativas() {
        assertArrayEquals(new int[]{10, 70, -5}, dev.leo.worldcraft.paper.Terrain.parseAt("~,~5,-5", new int[]{10, 65, 3}));
        assertArrayEquals(new int[]{1, 2, 3}, dev.leo.worldcraft.paper.Terrain.parseAt("1,2,3", null));
        assertThrows(IllegalArgumentException.class, () -> dev.leo.worldcraft.paper.Terrain.parseAt("1,2", null));
        assertThrows(IllegalArgumentException.class, () -> dev.leo.worldcraft.paper.Terrain.parseAt("a,2,3", null));
    }

    /** Acceso a lo estático del comando (paquete command). */
    static final class WorldcraftCommandAccess {
        static Map<String, String> keyValues(List<String> p, List<String> w) {
            return WorldcraftCommand.keyValues(p, w);
        }

        static BrushSettings brushSettings(BrushSettings b, List<String> w, Map<String, String> kv, int max, List<String> styles) {
            return WorldcraftCommand.brushSettings(b, w, kv, max, styles);
        }
    }
}
