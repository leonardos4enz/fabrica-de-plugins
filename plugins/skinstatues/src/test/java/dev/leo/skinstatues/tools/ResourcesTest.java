package dev.leo.skinstatues.tools;

import dev.leo.skinstatues.color.BlockPalette;
import dev.leo.skinstatues.config.LoadedConfig;
import dev.leo.skinstatues.model.Pose;
import dev.leo.skinstatues.model.VoxelGrid;
import dev.leo.skinstatues.model.Voxelizer;
import dev.leo.skinstatues.skin.Skin;
import dev.leo.skinstatues.statue.StatuePlan;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Lo que trae el jar está al día con sus generadores y funciona tal cual (H9 CA3, constitución V.2). */
class ResourcesTest {
    static final File RES = new File("src/main/resources");

    @Test
    void H9_CA3_la_skin_de_ejemplo_es_la_que_dibuja_el_generador() throws Exception {
        assertSame(ExampleSkinMain.skin(), ImageIO.read(new File(RES, "skins/Example.png")), "Example.png");
        assertSame(ExampleSkinMain.cape(), ImageIO.read(new File(RES, "skins/Example-cape.png")), "Example-cape.png");
    }

    private static void assertSame(BufferedImage want, BufferedImage got, String what) {
        assertEquals(want.getWidth(), got.getWidth(), what);
        assertEquals(want.getHeight(), got.getHeight(), what);
        for (int y = 0; y < want.getHeight(); y++) {
            for (int x = 0; x < want.getWidth(); x++) {
                assertEquals(want.getRGB(x, y), got.getRGB(x, y), what + " desactualizada en " + x + "," + y
                        + ": ./gradlew :skinstatues:exampleSkin");
            }
        }
    }

    @Test
    void block_colors_tiene_todos_los_bloques_del_generador() {
        LoadedConfig cfg = LoadedConfig.load(RES, id -> null, null);
        assertEquals(BlockColorsMain.blocks().keySet(), cfg.catalog().colors().keySet(),
                "./gradlew :skinstatues:blockColors -Pmc=<jar del cliente>");
    }

    @Test
    void H1_la_estatua_de_ejemplo_usa_la_paleta_con_colores_reconocibles() throws Exception {
        LoadedConfig cfg = LoadedConfig.load(RES, id -> null, null);
        Skin skin = Skin.of(ImageIO.read(new File(RES, "skins/Example.png")), false,
                ImageIO.read(new File(RES, "skins/Example-cape.png")));
        VoxelGrid g = Voxelizer.run(skin, Pose.STANDING, new Voxelizer.Options(32, 0, true, 0.5, 128, true));
        BlockPalette wool = cfg.palettes().get("wool");
        StatuePlan plan = StatuePlan.of(g, wool);
        Set<String> used = new HashSet<>();
        for (int k = 0; k < plan.size(); k++) used.add(plan.id(k));
        assertTrue(used.contains("orange_wool"), "el chaleco: " + used);
        assertTrue(used.contains("yellow_wool"), "el casco: " + used);
        assertTrue(used.contains("blue_wool") || used.contains("light_blue_wool") || used.contains("cyan_wool"), "los jeans: " + used);
        assertTrue(used.stream().allMatch(id -> id.endsWith("_wool")), "solo lana con la paleta wool");
        for (int k = 1; k < plan.size(); k++) assertTrue(plan.y(k) >= plan.y(k - 1), "de abajo hacia arriba");
    }
}
