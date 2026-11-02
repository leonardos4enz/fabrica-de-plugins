package dev.leo.worldcraft.color;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 035 H1: la tabla de colores de SkinStatues, el bloque más parecido, más oscuro y más claro, y las mezclas de FAWE. */
class ColorTest {
    static final BlockColors ALL = new BlockColors(BlockColors.builtin());

    @Test
    void H1_CA4_la_tabla_del_jar_son_los_145_bloques_de_skinstatues() {
        Map<String, Integer> t = BlockColors.builtin();
        assertEquals(145, t.size());
        assertEquals(0xE9ECEC, t.get("minecraft:white_wool"));
        assertTrue(t.keySet().stream().allMatch(k -> k.startsWith("minecraft:")));
    }

    @Test
    void H1_CA1_el_mas_parecido_como_lo_ve_el_ojo() {
        // Un color medido es su propio bloque.
        for (BlockColors.Entry e : ALL.entries()) assertEquals(e.rgb(), ALL.entries().get(ALL.nearest(e.rgb())).rgb(), e.id());
        assertEquals("minecraft:red_wool", ALL.nearestId(BlockColors.parse("red")));
        assertEquals("minecraft:black_wool", ALL.nearestId(BlockColors.parse("black")));
        String white = ALL.nearestId(0xFFFFFF);
        assertTrue(white.contains("white") || white.contains("snow") || white.contains("quartz"), white);
        assertEquals(ALL.nearest(0x123456), ALL.nearest(0xFF123456), "el alfa no cuenta");
    }

    @Test
    void H1_CA1_colores_escritos_y_did_you_mean() {
        assertEquals(0x3A7BD5, BlockColors.parse("#3a7bd5"));
        assertEquals(0x3A7BD5, BlockColors.parse("3A7BD5"));
        assertEquals(0xFFAA00, BlockColors.parse("#fa0"));
        assertEquals(0x3AAFD9, BlockColors.parse("light_blue"));
        String e = assertThrows(IllegalArgumentException.class, () -> BlockColors.parse("rde")).getMessage();
        assertTrue(e.contains("did you mean 'red'"), e);
        assertThrows(IllegalArgumentException.class, () -> BlockColors.parse("#12345"));
        assertEquals("#3A7BD5", BlockColors.hex(0x3A7BD5));
    }

    @Test
    void H1_CA2_mas_oscuro_y_mas_claro_del_mismo_tono() {
        String dark = ALL.darker("minecraft:light_gray_wool");
        assertNotNull(dark);
        assertTrue(Lab.of(ALL.colorOf(dark)).l() < Lab.of(ALL.colorOf("minecraft:light_gray_wool")).l(), dark);
        String light = ALL.lighter("minecraft:gray_wool");
        assertNotNull(light);
        assertTrue(Lab.of(ALL.colorOf(light)).l() > Lab.of(ALL.colorOf("minecraft:gray_wool")).l(), light);
        // El tono se conserva: lo más oscuro de una lana azul no es roja.
        Lab blue = Lab.of(ALL.colorOf("minecraft:light_blue_wool")), d = Lab.of(ALL.colorOf(ALL.darker("minecraft:light_blue_wool")));
        assertTrue(d.b() < 0, "sigue siendo azul: " + ALL.darker("minecraft:light_blue_wool"));
        assertTrue(Math.abs(d.b() - blue.b()) < 40);
        assertNull(ALL.darker("minecraft:dirt_path_x"), "fuera de la tabla");
        // En una tabla de dos negros no hay nada más oscuro que el más oscuro.
        Map<String, Integer> two = new LinkedHashMap<>();
        two.put("minecraft:a", 0x101010);
        two.put("minecraft:b", 0x202020);
        assertNull(new BlockColors(two).darker("minecraft:a"));
        assertEquals("minecraft:b", new BlockColors(two).lighter("minecraft:a"));
    }

    @Test
    void H1_CA2_mezclas_como_fawe() {
        assertEquals(0x808000, BlockColors.multiply(0xFFFF00, 0x808080));
        assertEquals(0x7F007F, BlockColors.average(0xFF0000, 0x0000FF));
        int gray = BlockColors.desaturate(0xFF0000, 100);
        assertEquals((gray >> 16) & 0xFF, (gray >> 8) & 0xFF, "100 % = gris");
        assertEquals((gray >> 8) & 0xFF, gray & 0xFF);
        assertEquals(0xFF0000, BlockColors.desaturate(0xFF0000, 0));
    }
}
