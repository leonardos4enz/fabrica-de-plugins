package dev.leo.worldcraft.pattern;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.block.StateTable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {
    private final Parser parser = new Parser(TestBlocks.REGISTRY,
            Map.of("castle", "60% stone_bricks, 25% cracked_stone_bricks, 15% mossy_stone_bricks"),
            Map.of("natural", "grass_block, dirt, stone"));

    private static String error(ThrowingRunnable r) {
        try {
            r.run();
        } catch (ParseException e) {
            return e.getMessage();
        }
        throw new AssertionError("no error");
    }

    interface ThrowingRunnable {
        void run() throws ParseException;
    }

    @Test
    void H2_CA1_un_bloque_con_o_sin_espacio_de_nombres_y_con_estado() throws ParseException {
        assertEquals(List.of("minecraft:stone"), parser.pattern("stone").states());
        assertEquals(List.of("minecraft:stone"), parser.pattern("minecraft:STONE").states());
        assertEquals(List.of("minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]"),
                parser.pattern("oak_stairs[half=top]").states());
    }

    @Test
    void H2_CA1_mezcla_con_porcentajes_o_sin_pesos() throws ParseException {
        Pattern p = parser.pattern("70% stone, 30%andesite");
        assertEquals(List.of("minecraft:stone", "minecraft:andesite"), p.states());
        assertEquals(List.of(70.0, 30.0), p.weights());
        Pattern q = parser.pattern("stone, andesite, diorite");
        assertEquals(List.of(1.0, 1.0, 1.0), q.weights());
        // Las comas dentro de un estado no separan.
        assertEquals(2, parser.pattern("oak_stairs[facing=east,half=top], stone").states().size());
    }

    @Test
    void H2_CA1_los_pesos_se_cumplen_en_proporcion() throws ParseException {
        StateTable t = new StateTable();
        Pattern.Compiled c = parser.pattern("75% stone, 25% andesite").compile(t);
        int stone = t.intern("minecraft:stone"), n = 0, total = 40_000;
        for (int i = 0; i < total; i++) if (c.apply(i % 200, i / 200, 7, 42L) == stone) n++;
        assertEquals(0.75, n / (double) total, 0.02);
    }

    @Test
    void H2_CA5_la_misma_semilla_da_el_mismo_bloque_en_cada_lugar() throws ParseException {
        StateTable t = new StateTable();
        Pattern.Compiled a = parser.pattern("50% stone, 50% andesite").compile(t);
        Pattern.Compiled b = parser.pattern("50% stone, 50% andesite").compile(t);
        int distinto = 0;
        for (int x = 0; x < 50; x++) {
            assertEquals(a.apply(x, 3, -x, 99L), b.apply(x, 3, -x, 99L));
            if (a.apply(x, 3, -x, 99L) != a.apply(x, 3, -x, 100L)) distinto++;
        }
        assertTrue(distinto > 5, "otra semilla debería dar otra mezcla");
    }

    @Test
    void H2_CA2_mascaras_lista_comodin_etiqueta_negacion_y_estado() throws ParseException {
        Mask wool = parser.mask("*_wool, !red_wool");
        assertTrue(wool.test("minecraft:white_wool", Map.of()));
        assertFalse(wool.test("minecraft:red_wool", Map.of()));
        assertFalse(wool.test("minecraft:stone", Map.of()));

        Mask notAir = parser.mask("!air");
        assertFalse(notAir.test("minecraft:cave_air", Map.of()), "air también es el aire de cueva");
        assertTrue(notAir.test("minecraft:stone", Map.of()));

        Mask logs = parser.mask("#logs");
        assertTrue(logs.test("minecraft:birch_log", Map.of("axis", "x")));

        Mask upright = parser.mask("oak_log[axis=y]");
        assertTrue(upright.test("minecraft:oak_log", Map.of("axis", "y")));
        assertFalse(upright.test("minecraft:oak_log", Map.of("axis", "x")));

        Mask all = parser.mask("*");
        assertTrue(all.test("minecraft:bedrock", Map.of()));

        Mask words = parser.mask("not stone, not dirt");
        assertFalse(words.test("minecraft:dirt", Map.of()), "«not» es lo mismo que «!»");
        assertTrue(words.test("minecraft:sand", Map.of()));
    }

    @Test
    void H2_CA2_la_mascara_compilada_recuerda_cada_estado() throws ParseException {
        StateTable t = new StateTable();
        Mask.Compiled c = parser.mask("stone, dirt").compile(t);
        assertTrue(c.test(t.intern("minecraft:dirt")));
        assertFalse(c.test(t.intern("minecraft:sand")));
        for (int i = 0; i < 200; i++) t.intern("minecraft:oak_log[axis=" + (i % 3 == 0 ? "y" : "x") + "]x" + i);
        assertTrue(c.test(t.intern("minecraft:stone")));
    }

    @Test
    void H2_CA3_errores_claros_con_sugerencia() {
        assertEquals("Unknown block 'stnoe' (did you mean 'stone'?)", error(() -> parser.pattern("stnoe")));
        assertTrue(error(() -> parser.pattern("50% stone, andesite")).startsWith("Give every block"));
        assertTrue(error(() -> parser.pattern("#logs")).contains("tags work in masks"));
        assertTrue(error(() -> parser.pattern("stone,,dirt")).contains("two commas"));
        assertTrue(error(() -> parser.pattern("oak_stairs[facing=up]")).contains("'up' is not a value of 'facing'"));
        assertTrue(error(() -> parser.pattern("oak_stairs[color=red]")).contains("has no property 'color'"));
        assertTrue(error(() -> parser.mask("#lgos")).contains("did you mean 'minecraft:logs'"));
        assertTrue(error(() -> parser.mask("*_concrete")).contains("matches no block"));
        assertTrue(error(() -> parser.mask("50% stone")).contains("weights (%) work in patterns"));
        assertTrue(error(() -> parser.pattern("")).startsWith("Missing pattern"));
    }

    /** F-02: en el servidor real «stnoe» sugería «snow» (empate a distancia 2); dos letras cambiadas de lugar cuentan 1. */
    @Test
    void H2_CA3_F02_letras_cambiadas_de_lugar_sugieren_la_palabra_correcta() {
        assertEquals("minecraft:stone", dev.leo.worldcraft.config.Issues.closest("minecraft:stnoe",
                List.of("minecraft:snow", "minecraft:stone")));
        assertEquals("andesite", dev.leo.worldcraft.config.Issues.closest("andesiet", List.of("andesite", "andesitf")));
    }

    @Test
    void H2_CA4_presets_por_nombre() throws ParseException {
        Pattern p = parser.pattern("$castle");
        assertEquals(3, p.states().size());
        assertEquals("$castle", p.text());
        assertTrue(parser.mask("$natural").test("minecraft:dirt", Map.of()));
        assertTrue(error(() -> parser.pattern("$castel")).contains("did you mean 'castle'"));
        Map<String, String> loop = new HashMap<>();
        loop.put("a", "$b");
        Parser p2 = new Parser(TestBlocks.REGISTRY, loop, Map.of());
        assertTrue(error(() -> p2.pattern("$a")).contains("points to another preset"));
    }
}
