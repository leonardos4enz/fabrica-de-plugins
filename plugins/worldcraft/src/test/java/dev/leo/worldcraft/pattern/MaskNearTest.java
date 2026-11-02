package dev.leo.worldcraft.pattern;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.block.StateTable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 029 H2: máscaras que miran alrededor o eligen al azar, y patrones que conservan el estado (H3). */
class MaskNearTest {
    private final Parser parser = new Parser(TestBlocks.REGISTRY, Map.of(), Map.of("tops", "grass_block and exposed"));
    private final StateTable table = new StateTable();
    /** La «foto»: lo que había antes de la obra. */
    private final Map<Long, Integer> photo = new HashMap<>();
    private final Mask.Neighbors near = (x, y, z) -> photo.getOrDefault(key(x, y, z), id("minecraft:air"));

    private static long key(int x, int y, int z) {
        return ((long) x & 0xFFFFF) | (((long) y & 0xFFFFF) << 20) | (((long) z & 0xFFFFF) << 40);
    }

    private int id(String s) {
        return table.intern(TestBlocks.REGISTRY.canonical(s.contains(":") ? s : "minecraft:" + s));
    }

    private void put(int x, int y, int z, String s) {
        photo.put(key(x, y, z), id(s));
    }

    private boolean test(String mask, int x, int y, int z) throws ParseException {
        Mask.Compiled m = parser.maskNear(mask).compile(table);
        return m.test(x, y, z, near.at(x, y, z), near, 42);
    }

    private String error(String mask) {
        try {
            parser.maskNear(mask);
        } catch (ParseException e) {
            return e.getMessage();
        }
        throw new AssertionError("sin error: " + mask);
    }

    @Test
    void H2_CA1_above_y_below_miran_el_bloque_de_abajo_y_el_de_arriba() throws ParseException {
        put(0, 0, 0, "grass_block");
        put(0, 1, 0, "stone");
        put(0, 2, 0, "oak_log");
        assertTrue(test("stone above grass_block", 0, 1, 0));
        assertTrue(test("above grass_block", 0, 1, 0));
        assertFalse(test("stone above dirt", 0, 1, 0));
        assertTrue(test("stone below #logs", 0, 1, 0), "below: el de arriba es un tronco");
        assertFalse(test("stone above grass_block", 0, 2, 0), "el tronco no es piedra");
        assertTrue(test("!above grass_block", 0, 2, 0));
        assertTrue(test("stone and above grass_block and below oak_log", 0, 1, 0));
    }

    @Test
    void H2_CA1_exposed_surface_y_solid() throws ParseException {
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) put(x, y, z, "stone");
        assertFalse(test("exposed", 0, 0, 0), "rodeado de piedra");
        assertTrue(test("exposed", 0, 1, 0), "la de arriba toca el aire");
        assertTrue(test("surface", 0, 1, 0));
        assertFalse(test("surface", 1, 0, 0), "tiene piedra encima");
        assertTrue(test("exposed", 1, 0, 0), "pero toca aire al costado");
        assertFalse(test("exposed", 0, 2, 0), "el aire no está expuesto");
        put(0, 2, 0, "short_grass");
        assertTrue(test("solid", 0, 1, 0));
        assertFalse(test("solid", 0, 2, 0), "el pasto corto no es sólido");
        assertTrue(test("stone and exposed, short_grass", 0, 2, 0), "la coma es «o»");
        assertTrue(test("$tops", -9, -9, -9) == false);
    }

    @Test
    void H2_CA1_chance_es_al_azar_pero_igual_para_la_vista_previa_y_la_obra() throws ParseException {
        Mask.Compiled half = parser.maskNear("50% chance").compile(table);
        int stone = id("stone"), hits = 0;
        for (int x = 0; x < 100; x++) for (int z = 0; z < 100; z++) {
            boolean a = half.test(x, 0, z, stone, null, 7), b = half.test(x, 0, z, stone, null, 7);
            assertEquals(a, b, "la misma semilla elige lo mismo");
            if (a) hits++;
        }
        assertTrue(hits > 4500 && hits < 5500, "cerca de la mitad: " + hits);
        assertEquals(10_000, count("100% chance"));
        assertTrue(count("stone and 10% chance") < 1300);
    }

    private int count(String m) throws ParseException {
        Mask.Compiled c = parser.maskNear(m).compile(table);
        int n = 0, stone = id("stone");
        for (int x = 0; x < 100; x++) for (int z = 0; z < 100; z++) if (c.test(x, 0, z, stone, null, 3)) n++;
        return n;
    }

    @Test
    void H2_CA2_decide_la_foto_no_el_mundo_a_medio_escribir() throws ParseException {
        // Una columna de piedra: «surface» es solo la de arriba en la foto, aunque la obra ya haya quitado esa.
        for (int y = 0; y < 3; y++) put(0, y, 0, "stone");
        Mask.Compiled m = parser.maskNear("surface").compile(table);
        Map<Long, Integer> world = new HashMap<>(photo);
        int changed = 0;
        for (int y = 2; y >= 0; y--) {
            if (m.test(0, y, 0, world.get(key(0, y, 0)), near, 1)) {
                world.put(key(0, y, 0), id("air"));
                changed++;
            }
        }
        assertEquals(1, changed, "solo la de arriba: la foto no cambia mientras se escribe");
    }

    @Test
    void H2_CA3_estado_sin_tipo() throws ParseException {
        put(0, 0, 0, "oak_stairs[waterlogged=true]");
        put(1, 0, 0, "oak_leaves[waterlogged=true]");
        put(2, 0, 0, "oak_stairs");
        assertTrue(test("^[waterlogged=true]", 0, 0, 0));
        assertTrue(test("^[waterlogged=true]", 1, 0, 0));
        assertFalse(test("^[waterlogged=true]", 2, 0, 0));
        assertFalse(parser.maskNear("^[waterlogged=true]").positional(), "es solo de estado");
        assertTrue(parser.mask("^[waterlogged=true]").compile(table).test(id("oak_leaves[waterlogged=true]")));
    }

    @Test
    void H2_CA4_errores_claros_antes_de_tocar_nada() {
        assertTrue(error("exposd").contains("did you mean 'exposed'"), error("exposd"));
        assertTrue(error("stone above grass_blok").contains("did you mean 'grass_block'"), error("stone above grass_blok"));
        assertTrue(error("above exposed").contains("after 'above' write blocks"), error("above exposed"));
        assertTrue(error("stone and").contains("'and' needs something"), error("stone and"));
        assertTrue(error("150% chance").contains("at most 100%"), error("150% chance"));
        assertTrue(error("30% stone").contains("30% chance"), error("30% stone"));
        assertTrue(error("stone and dirt").contains("nothing in common"), error("stone and dirt"));
        // Donde la máscara solo mira bloques (terreno, pinceles), lo de alrededor se rechaza diciendo dónde sí.
        try {
            parser.mask("stone above grass_block");
            fail();
        } catch (ParseException e) {
            assertTrue(e.getMessage().contains("replace"), e.getMessage());
        }
    }

    /**
     * F-01 (compuerta 2): «stone above grass_block and !surface» decía «'!surface' is not a block name». Una parte de un
     * «and» también se puede negar.
     */
    @Test
    void spec029_F01_negar_una_parte_del_and() throws ParseException {
        for (int y = 0; y < 3; y++) put(0, y, 0, "stone");
        put(0, -1, 0, "grass_block");
        assertTrue(test("stone and !surface", 0, 1, 0), "tiene piedra encima");
        assertFalse(test("stone and !surface", 0, 2, 0), "la de arriba es superficie");
        assertTrue(test("stone above grass_block and not surface", 0, 0, 0));
        assertTrue(test("stone and !above grass_block", 0, 1, 0));
        assertFalse(test("stone and !above grass_block", 0, 0, 0));
        assertTrue(test("#base_stone_overworld and !andesite", 0, 1, 0));
        assertFalse(parser.maskNear("#base_stone_overworld and !andesite").positional(), "negar bloques sigue siendo de estado");
        assertTrue(parser.mask("#logs and !birch_log").compile(table).test(id("oak_log")));
        assertFalse(parser.mask("#logs and !birch_log").compile(table).test(id("birch_log")));
    }

    /** F-02 (compuerta 2): «stone above» decía «'stone above' is not a block name» en vez de qué falta. */
    @Test
    void spec029_F02_above_sin_bloque_dice_que_falta() {
        assertTrue(error("stone above").contains("after 'above' write blocks"), error("stone above"));
        assertTrue(error("below").contains("after 'below' write blocks"), error("below"));
    }

    // ------------------------------------------------------------------ H3 patrones que conservan

    private int apply(String pattern, String current) throws ParseException {
        return parser.pattern(pattern).compile(table).apply(0, 0, 0, current == null ? -1 : id(current), 9);
    }

    private String state(int id) {
        return id < 0 ? null : table.state(id);
    }

    @Test
    void H3_CA1_tipo_nuevo_conserva_lo_que_acepta() throws ParseException {
        assertEquals("minecraft:stone_stairs[facing=west,half=top,shape=straight,waterlogged=true]",
                state(apply("^stone_stairs", "oak_stairs[facing=west,half=top,waterlogged=true]")));
        assertEquals("minecraft:stone", state(apply("^stone", "oak_stairs[facing=west]")), "la piedra no tiene facing");
        assertEquals("minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
                state(apply("^oak_stairs", "stone")), "lo que no tiene estado queda de fábrica");
        assertNull(state(apply("^stone_stairs", null)), "sin saber qué hay, se deja");
        assertEquals(List.of("minecraft:stone_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
                parser.pattern("^stone_stairs").placedStates(), "los bloques prohibidos se revisan con el tipo");
    }

    @Test
    void H3_CA2_solo_el_estado() throws ParseException {
        assertEquals("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]",
                state(apply("^[waterlogged=false]", "oak_stairs[facing=east,waterlogged=true]")));
        assertNull(state(apply("^[waterlogged=false]", "stone")), "la piedra no tiene esa propiedad: se deja");
        assertTrue(parser.pattern("^[waterlogged=false]").placedStates().isEmpty());
    }

    @Test
    void H3_CA3_mezcla_con_porcentajes() throws ParseException {
        Pattern.Compiled c = parser.pattern("50% ^stone_stairs, 50% ^cobblestone_stairs").compile(table);
        int cur = id("oak_stairs[facing=south]");
        int stone = 0, cobble = 0;
        for (int x = 0; x < 200; x++) {
            String s = table.state(c.apply(x, 0, 0, cur, 5));
            assertTrue(s.contains("facing=south"), s);
            if (s.startsWith("minecraft:stone_stairs")) stone++;
            else cobble++;
        }
        assertTrue(stone > 60 && cobble > 60, stone + " / " + cobble);
    }

    @Test
    void H3_errores() {
        for (String bad : List.of("^", "^stne_stairs", "^[waterlogged=false", "^oak_stairs[facing=north]")) {
            try {
                parser.pattern(bad);
                fail(bad);
            } catch (ParseException e) {
                assertFalse(e.getMessage().isBlank());
            }
        }
    }
}
