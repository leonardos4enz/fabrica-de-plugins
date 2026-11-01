package dev.leo.skinstatues.config;

import dev.leo.skinstatues.color.BlockPalette;
import dev.leo.skinstatues.model.Pose;
import dev.leo.skinstatues.model.Pose.Part;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    static final File RES = new File("src/main/resources");

    static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return y;
    }

    static BlockCatalog catalog(Issues issues) throws Exception {
        return BlockCatalog.load(yaml("white_wool: \"#E9ECEC\"\npink_wool: \"#ED8DAC\"\nred_wool: \"#A12722\"\n"
                + "terracotta: \"#985E43\"\nred_terracotta: \"#8F3D2E\"\nstone: \"#7E7E7E\"\nsand: \"#DBCFA3\"\n"),
                "block-colors.yml", id -> id.equals("sand") ? "falls (gravity)" : id.equals("future_block") ? "unknown block" : null, issues);
    }

    @Test
    void H6_CA2_comodines_exclusiones_e_ids() throws Exception {
        Issues issues = new Issues();
        BlockCatalog cat = catalog(issues);
        Map<String, BlockPalette> p = cat.palettes(yaml("""
                lana: ["*_wool", "-pink_wool"]
                mezcla: ["minecraft:stone", "*terracotta"]
                todo: ["*"]
                """), "palettes.yml", issues);
        assertEquals(java.util.List.of("white_wool", "red_wool"), p.get("lana").entries().stream().map(BlockPalette.Entry::id).toList());
        assertEquals(3, p.get("mezcla").entries().size());
        assertEquals(6, p.get("todo").entries().size(), "la arena (cae) no entra al catálogo");
        assertEquals(1, issues.warnings(), issues.all().toString());
        assertEquals(0, issues.errors());
    }

    @Test
    void H6_CA4_elige_entre_los_permitidos_como_lo_ve_el_ojo() throws Exception {
        Issues issues = new Issues();
        Map<String, BlockPalette> p = catalog(issues).palettes(yaml("lana: [\"*_wool\"]\nterra: [\"*terracotta\"]\n"), "palettes.yml", issues);
        assertEquals("red_wool", p.get("lana").nearestId(0xFFB02A20));
        assertEquals("white_wool", p.get("lana").nearestId(0xFFF0F0F0));
        assertEquals("red_terracotta", p.get("terra").nearestId(0xFFB02A20), "solo terracota si la paleta solo tiene terracota");
        // Piel: más cerca de la terracota naranja apagada que de la lana roja.
        assertEquals("terracotta", p.get("terra").nearestId(0xFFB07050));
    }

    @Test
    void H6_CA5_ids_desconocidos_con_sugerencia_y_paleta_vacia() throws Exception {
        Issues issues = new Issues();
        Map<String, BlockPalette> p = catalog(issues).palettes(yaml("""
                mal: ["whte_wool"]
                nada: ["*_glass"]
                vacia: ["*_wool", "-*_wool"]
                buena: ["stone"]
                """), "palettes.yml", issues);
        assertEquals(java.util.Set.of("buena"), p.keySet());
        String all = issues.all().toString();
        assertTrue(all.contains("did you mean 'white_wool'"), all);
        assertTrue(all.contains("matches no block"), all);
        assertTrue(all.contains("no blocks left"), all);
        assertTrue(all.contains("palettes.yml › mal[0]"), "archivo y ruta: " + all);
    }

    @Test
    void H6_colores_invalidos_y_bloques_de_otra_version() throws Exception {
        Issues issues = new Issues();
        BlockCatalog c = BlockCatalog.load(yaml("stone: \"rojo\"\nfuture_block: \"#123456\"\nwhite_wool: \"#FFFFFF\"\n"),
                "block-colors.yml", id -> id.equals("future_block") ? "unknown block" : null, issues);
        assertEquals(1, c.colors().size());
        assertEquals(1, issues.errors());
        assertTrue(issues.all().toString().contains("1 block(s) do not exist in this server version"));
    }

    @Test
    void H4_CA4_poses_con_errores_se_omiten_y_las_demas_cargan() throws Exception {
        Issues issues = new Issues();
        Map<String, Pose> poses = PoseLoader.load(yaml("""
                buena:
                  right_arm: { pitch: 20, elbow: 30 }
                brazo_mal:
                  rigth_arm: { pitch: 10 }
                angulo:
                  head: { pitch: 200 }
                texto:
                  body: { yaw: "mucho" }
                codo_en_pierna:
                  left_leg: { elbow: 10 }
                doblez:
                  left_arm: { elbow: 170 }
                """), "poses.yml", issues);
        assertEquals(java.util.Set.of("buena", "standing"), poses.keySet(), issues.all().toString());
        String all = issues.all().toString();
        assertTrue(all.contains("did you mean 'right_arm'"), all);
        assertTrue(all.contains("out of range (-180..180)"), all);
        assertTrue(all.contains("not a number"), all);
        assertTrue(all.contains("left_leg bends with 'knee'"), all);
        assertTrue(all.contains("out of range (0..150)"), all);
        assertEquals(20, poses.get("buena").of(Part.RIGHT_ARM).pitch());
        assertEquals(30, poses.get("buena").of(Part.RIGHT_ARM).bend());
    }

    @Test
    void H4_CA3_y_H9_CA3_los_archivos_que_trae_el_plugin_cargan_sin_errores() {
        LoadedConfig cfg = LoadedConfig.load(RES, id -> null, null);
        assertEquals(0, cfg.issues().errors(), cfg.issues().all().toString());
        assertEquals(0, cfg.issues().warnings(), cfg.issues().all().toString());
        assertEquals(java.util.List.of("standing", "waving", "walking", "victory", "pointing", "sitting"),
                java.util.List.copyOf(cfg.poses().keySet()));
        assertTrue(cfg.palettes().keySet().containsAll(java.util.List.of("all", "wool", "concrete", "terracotta")));
        assertEquals(16, cfg.palettes().get("wool").entries().size());
        assertEquals(17, cfg.palettes().get("terracotta").entries().size());
        assertTrue(cfg.catalog().colors().size() > 100);
        assertEquals("standing", cfg.defaultPose());
        assertEquals("all", cfg.defaultPalette());
    }

    @Test
    void H9_CA2_config_con_valores_invalidos_avisa_y_usa_los_de_por_defecto() throws Exception {
        Issues issues = new Issues();
        Settings s = Settings.load(yaml("""
                max_height: "alto"
                min_height: 8
                ofset_up: 3
                overlay: { depth: 9 }
                network: { mojang: "si" }
                """), "config.yml", issues);
        assertEquals(128, s.maxHeight());
        assertEquals(0.5, s.overlayDepth());
        assertFalse(s.mojang(), "la red sigue apagada si el valor no es true");
        String all = issues.all().toString();
        assertTrue(all.contains("did you mean 'offset_up'"), all);
        assertEquals(3, issues.errors(), all);
    }
}
