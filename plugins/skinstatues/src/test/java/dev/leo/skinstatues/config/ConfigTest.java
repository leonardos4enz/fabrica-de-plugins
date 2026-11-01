package dev.leo.skinstatues.config;

import dev.leo.skinstatues.color.BlockPalette;
import dev.leo.skinstatues.model.Pose;
import dev.leo.skinstatues.model.Pose.Angles;
import dev.leo.skinstatues.model.Pose.Part;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
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

    // ---------------------------------------------------------------- spec 022

    private static final String BASE = """
            base:
              head: { yaw: 10, roll: 4 }
              right_arm: { pitch: 20, yaw: 15, roll: 30 }
              right_leg: { pitch: -10, knee: 20 }
            """;

    @Test
    void H2_CA1_extends_cambia_angulo_por_angulo_y_el_padre_puede_ir_despues() throws Exception {
        Issues issues = new Issues();
        PoseLoader.Result r = PoseLoader.load(yaml(BASE), "builtin-poses.yml", yaml("""
                child:
                  extends: base
                  right_arm: { elbow: 90 }
                early:
                  extends: later
                  head: { pitch: 5 }
                later:
                  extends: base
                  head: { yaw: -20 }
                plain:
                  head: { pitch: 3 }
                """), "poses.yml", issues);
        assertTrue(issues.all().isEmpty(), issues.all().toString());
        Pose child = r.poses().get("child");
        assertEquals(new Angles(20, 15, 30, 90), child.of(Part.RIGHT_ARM), "el codo cambia y lo demás se hereda");
        assertEquals(new Angles(0, 10, 4, 0), child.of(Part.HEAD));
        assertEquals(new Angles(-10, 0, 0, 20), child.of(Part.RIGHT_LEG));
        assertEquals(new Angles(5, -20, 4, 0), r.poses().get("early").of(Part.HEAD), "dos niveles, el padre declarado después");
        assertEquals(Angles.NONE, r.poses().get("plain").of(Part.RIGHT_ARM), "sin extends parte de standing");
    }

    @Test
    void H2_CA2_mirror_voltea_la_pose_heredada_y_luego_aplica_lo_escrito() throws Exception {
        Issues issues = new Issues();
        PoseLoader.Result r = PoseLoader.load(yaml(BASE), "builtin-poses.yml", yaml("""
                flipped:
                  extends: base
                  mirror: true
                  head: { pitch: 7 }
                  right_arm: { pitch: 45 }
                back:
                  extends: flipped
                  mirror: true
                """), "poses.yml", issues);
        assertTrue(issues.all().isEmpty(), issues.all().toString());
        Pose f = r.poses().get("flipped");
        assertEquals(new Angles(20, -15, 30, 0), f.of(Part.LEFT_ARM), "el brazo pasa al otro lado: yaw cambia de signo, roll no");
        assertEquals(new Angles(45, 0, 0, 0), f.of(Part.RIGHT_ARM), "lo escrito se aplica después del espejo");
        assertEquals(new Angles(7, -10, -4, 0), f.of(Part.HEAD), "la cabeza: yaw y roll cambian de signo");
        assertEquals(new Angles(-10, 0, 0, 20), f.of(Part.LEFT_LEG));
        assertEquals(Angles.NONE, f.of(Part.RIGHT_LEG));
        Pose back = r.poses().get("back");
        assertEquals(new Angles(20, 15, 30, 0), back.of(Part.RIGHT_ARM), "dos espejos devuelven el brazo a su lado");
        assertEquals(new Angles(45, 0, 0, 0), back.of(Part.LEFT_ARM));
    }

    @Test
    void H2_CA3_herencias_que_no_se_resuelven_se_informan_y_las_demas_cargan() throws Exception {
        Issues issues = new Issues();
        StringBuilder chain = new StringBuilder();
        for (int i = 0; i < 10; i++) chain.append("deep").append(i).append(":\n  extends: deep").append(i + 1).append("\n");
        chain.append("deep10:\n  head: { pitch: 1 }\n");
        PoseLoader.Result r = PoseLoader.load(yaml(BASE), "builtin-poses.yml", yaml("""
                ok:
                  extends: base
                missing:
                  extends: bse
                loop_a:
                  extends: loop_b
                loop_b:
                  extends: loop_a
                self:
                  extends: self
                broken:
                  rigth_arm: { pitch: 1 }
                child_of_broken:
                  extends: broken
                lonely_mirror:
                  mirror: true
                  head: { pitch: 1 }
                mirror_text:
                  extends: base
                  mirror: "yes"
                extends_map:
                  extends: { a: 1 }
                """ + chain), "poses.yml", issues);
        String all = issues.all().toString();
        assertTrue(r.poses().containsKey("ok") && r.poses().containsKey("base") && r.poses().containsKey("standing"), all);
        for (String bad : List.of("missing", "loop_a", "loop_b", "self", "broken", "child_of_broken", "lonely_mirror",
                "mirror_text", "extends_map", "deep0", "deep1")) {
            assertFalse(r.poses().containsKey(bad), bad + " no carga: " + all);
        }
        assertTrue(all.contains("poses.yml › missing.extends: there is no pose 'bse' (did you mean 'base'?)"), all);
        assertTrue(all.contains("extends goes in a circle (loop_a → loop_b → loop_a)"), all);
        assertTrue(all.contains("extends goes in a circle (self → self)"), all);
        assertTrue(all.contains("child_of_broken.extends: it extends 'broken', which has errors"), all);
        assertTrue(all.contains("lonely_mirror.mirror: mirror flips the pose it extends"), all);
        assertTrue(all.contains("mirror_text.mirror: 'yes' is not true or false"), all);
        assertTrue(all.contains("extends_map.extends: expected the name of another pose"), all);
        assertTrue(all.contains("more than 8 levels of extends"), all);
        assertTrue(r.poses().containsKey("deep2"), "8 niveles sí se permiten: " + all);
    }

    @Test
    void H1_CA2_CA3_las_incorporadas_vienen_del_jar_y_poses_yml_agrega_o_reemplaza() throws Exception {
        Issues issues = new Issues();
        PoseLoader.Result r = PoseLoader.load(TestResources.builtinPoses(), "builtin-poses.yml", yaml("""
                t_pose:
                  right_arm: { roll: 80 }
                  left_arm: { roll: 80 }
                mine:
                  extends: t_pose
                  head: { pitch: 10 }
                """), "poses.yml", issues);
        assertTrue(issues.all().isEmpty(), issues.all().toString());
        assertEquals(PoseLoader.Origin.OVERRIDE, r.origins().get("t_pose"));
        assertEquals(PoseLoader.Origin.CUSTOM, r.origins().get("mine"));
        assertEquals(PoseLoader.Origin.BUILTIN, r.origins().get("running"));
        assertEquals(80, r.poses().get("mine").of(Part.RIGHT_ARM).roll(), "extends usa la pose ya reemplazada");
        assertEquals(List.copyOf(TestResources.builtinPoses().getKeys(false)),
                List.copyOf(r.poses().keySet()).subList(0, 28), "las incorporadas, en su orden");
    }

    @Test
    void H1_CA3_el_poses_yml_de_la_0_1_0_carga_sin_errores_ni_reemplazos() throws Exception {
        Issues issues = new Issues();
        YamlConfiguration old = YamlConfiguration.loadConfiguration(new File("src/test/resources/poses-0.1.0.yml"));
        assertEquals(6, old.getKeys(false).size());
        PoseLoader.Result r = PoseLoader.load(TestResources.builtinPoses(), "builtin-poses.yml", old, "poses.yml", issues);
        assertTrue(issues.all().isEmpty(), issues.all().toString());
        assertTrue(r.origins().values().stream().allMatch(o -> o == PoseLoader.Origin.BUILTIN), r.origins().toString());
        assertEquals(28, r.poses().size());
    }

    @Test
    void H4_CA3_y_H9_CA3_los_archivos_que_trae_el_plugin_cargan_sin_errores() {
        LoadedConfig cfg = TestResources.load();
        assertEquals(0, cfg.issues().errors(), cfg.issues().all().toString());
        assertEquals(0, cfg.issues().warnings(), cfg.issues().all().toString());
        List<String> names = List.copyOf(cfg.poses().keySet());
        assertEquals(List.of("standing", "waving", "walking", "victory", "pointing", "sitting"), names.subList(0, 6),
                "las 6 de la 0.1.0 primero");
        assertEquals(28, cfg.poseOrigins().values().stream().filter(o -> o == PoseLoader.Origin.BUILTIN).count(),
                "spec 022 H1 CA1: 22 incorporadas nuevas");
        assertEquals(List.of("my_wave", "my_point_left"), names.subList(28, 30), "los ejemplos de poses.yml (H2 CA4)");
        assertEquals(90, cfg.poses().get("my_wave").of(Part.RIGHT_ARM).bend());
        assertEquals(135, cfg.poses().get("my_wave").of(Part.RIGHT_ARM).roll(), "el resto del brazo, heredado");
        assertEquals(90, cfg.poses().get("my_point_left").of(Part.LEFT_ARM).pitch());
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
