package dev.leo.worldcraft.pattern;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.block.StateTable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 035 H1: los patrones por color de FAWE, con la tabla limitada a los bloques del servidor (aquí, los de prueba). */
class ColorPatternTest {
    private final Parser parser = new Parser(TestBlocks.REGISTRY, Map.of("shade", "#darken"), Map.of());
    private final StateTable table = new StateTable();

    private int id(String s) {
        return table.intern(TestBlocks.REGISTRY.canonical("minecraft:" + s));
    }

    private String apply(String pattern, String current) throws ParseException {
        int r = parser.pattern(pattern).compile(table).apply(0, 0, 0, id(current), 1);
        return r < 0 ? null : table.state(r).replace("minecraft:", "");
    }

    private static double lum(dev.leo.worldcraft.color.BlockColors colors, String block) {
        return dev.leo.worldcraft.color.Lab.of(colors.colorOf("minecraft:" + block)).l();
    }

    @Test
    void H1_CA1_color_pone_el_bloque_mas_parecido() throws ParseException {
        assertEquals("red_wool", apply("#color[red]", "stone"));
        assertEquals("black_wool", apply("#color[#141519]", "air"));
        Pattern p = parser.pattern("#color[blue]");
        assertEquals(1, p.placedStates().size(), "un solo bloque, como un patrón normal");
        assertTrue(parser.colors().size() < 145, "solo los bloques que el servidor conoce");
    }

    @Test
    void spec041_H3_CA1_con_la_textura_del_jugador_elige_solo_entre_esos_bloques() throws ParseException {
        var all = parser.allColors();
        Map<String, Integer> two = new java.util.LinkedHashMap<>();
        two.put("minecraft:white_wool", all.colorOf("minecraft:white_wool"));
        two.put("minecraft:black_wool", all.colorOf("minecraft:black_wool"));
        Parser.TEXTURE.set(new dev.leo.worldcraft.color.BlockColors(two));
        try {
            String red = apply("#color[red]", "stone");
            assertTrue(red.equals("white_wool") || red.equals("black_wool"), "con la textura, rojo no está: " + red);
            assertEquals(all.size(), parser.allColors().size(), "allColors no mira la textura");
        } finally {
            Parser.TEXTURE.remove();
        }
        assertEquals("red_wool", apply("#color[red]", "stone"), "sin textura, toda la tabla otra vez");
    }

    @Test
    void H1_CA2_cambios_sobre_el_bloque_que_ya_esta() throws ParseException {
        var colors = parser.colors();
        String dark = apply("#darken", "light_gray_wool"), light = apply("#lighten", "gray_wool");
        assertNotNull(dark);
        assertNotNull(light);
        assertTrue(lum(colors, dark) < lum(colors, "light_gray_wool"), dark);
        assertTrue(lum(colors, light) > lum(colors, "gray_wool"), light);
        String sat = apply("#saturate[#ff0000]", "white_wool");
        assertTrue(sat != null && sat.startsWith("red"), "blanco teñido de rojo: " + sat);
        String avg = apply("#averagecolor[black]", "white_wool");
        assertNotNull(avg);
        assertTrue(lum(colors, avg) < lum(colors, "white_wool") - 20 && lum(colors, avg) > lum(colors, "black_wool") + 20,
                "el promedio de blanco y negro es un gris: " + avg);
        String des = apply("#desaturate", "red_wool");
        assertNotNull(des);
        assertFalse(des.startsWith("red"), "sin color: " + des);
        assertNull(apply("#darken", "oak_stairs"), "un bloque fuera de la tabla se deja");
        assertNull(apply("#desaturate[0]", "red_wool"), "0 %: el mismo bloque, nada que cambiar");
        // Sin saber qué hay (el pincel en el aire): se deja.
        assertEquals(-1, parser.pattern("#darken").compile(table).apply(0, 0, 0, 1));
    }

    @Test
    void H1_CA3_se_mezcla_y_sirve_en_presets() throws ParseException {
        Pattern p = parser.pattern("50% #darken, 50% stone");
        assertEquals(2, p.states().size());
        assertEquals(apply("#darken", "light_gray_wool"), apply("$shade", "light_gray_wool"));
        assertTrue(p.placedStates().contains(TestBlocks.REGISTRY.canonical("minecraft:stone")));
    }

    @Test
    void H1_CA4_los_prohibidos_ven_toda_la_tabla() throws ParseException {
        Pattern p = parser.pattern("#lighten");
        assertTrue(p.placedStates().contains("minecraft:diamond_block"), "un cambio por color puede poner cualquier bloque de la tabla");
        assertEquals(parser.colors().size(), p.placedStates().size());
        Pattern plain = parser.pattern("stone");
        assertEquals(1, plain.placedStates().size(), "sin colores no cambia");
    }

    @Test
    void H1_CA3_errores_con_did_you_mean() {
        String e = assertThrows(ParseException.class, () -> parser.pattern("#colr[red]")).getMessage();
        assertTrue(e.contains("did you mean '#color'"), e);
        e = assertThrows(ParseException.class, () -> parser.pattern("#color[rde]")).getMessage();
        assertTrue(e.contains("did you mean 'red'"), e);
        e = assertThrows(ParseException.class, () -> parser.pattern("#color")).getMessage();
        assertTrue(e.contains("needs a color"), e);
        e = assertThrows(ParseException.class, () -> parser.pattern("#desaturate[150]")).getMessage();
        assertTrue(e.contains("0 to 100"), e);
        e = assertThrows(ParseException.class, () -> parser.pattern("#logs")).getMessage();
        assertTrue(e.contains("is a tag") && e.contains("#color[red]"), e);
        assertThrows(ParseException.class, () -> parser.pattern("#darken[red]"));
        assertThrows(ParseException.class, () -> parser.pattern("#color[red"));
    }
}
