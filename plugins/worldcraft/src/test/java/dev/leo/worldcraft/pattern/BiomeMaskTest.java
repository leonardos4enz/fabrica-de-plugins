package dev.leo.worldcraft.pattern;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.block.StateTable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 033 H3: la máscara «$bioma» (como WorldEdit), combinada y negada; el preset gana si se llama igual. */
class BiomeMaskTest {
    private final Parser parser = new Parser(TestBlocks.REGISTRY, Map.of(), Map.of("forest", "#logs", "tops", "grass_block"));
    private final StateTable table = new StateTable();
    /** El desierto en x < 0, el llano en x >= 0. */
    private final Mask.BiomeLookup lookup = (x, y, z) -> x < 0 ? "minecraft:desert" : "minecraft:plains";

    private int id(String s) {
        return table.intern(TestBlocks.REGISTRY.canonical("minecraft:" + s));
    }

    private boolean test(String mask, int x, String block) throws ParseException {
        Mask m = parser.maskNear(mask);
        Mask.Compiled c = m.compile(table).biomes(lookup);
        return c.test(x, 0, 0, id(block), null, 1);
    }

    @Test
    void H3_CA1_bioma_solo_combinado_y_negado() throws ParseException {
        assertTrue(test("$desert", -5, "sand"));
        assertFalse(test("$desert", 5, "sand"));
        assertTrue(test("$desert and sand", -5, "sand"));
        assertFalse(test("$desert and sand", -5, "stone"));
        assertTrue(test("!$plains", -5, "stone"));
        assertFalse(test("!$plains", 5, "stone"));
        assertTrue(test("$desert, $plains", 5, "stone"), "la coma es «o»");
        assertTrue(test("$minecraft:desert", -1, "stone"));
        Mask m = parser.maskNear("$desert and sand");
        assertTrue(m.needsBiomes());
        assertFalse(m.needsNeighbors(), "no hace falta leer la foto: sirve en gmask");
        assertTrue(m.positional());
    }

    @Test
    void H3_CA1_sin_buscador_ningun_bioma_pasa() throws ParseException {
        Mask.Compiled c = parser.maskNear("$desert").compile(table);
        assertFalse(c.test(-5, 0, 0, id("sand"), null, 1));
    }

    @Test
    void H3_CA2_el_preset_gana_y_lo_desconocido_sugiere() throws ParseException {
        Mask forest = parser.maskNear("$forest");
        assertFalse(forest.needsBiomes(), "«forest» es un preset: #logs");
        assertEquals("$forest", forest.text());
        ParseException e = assertThrows(ParseException.class, () -> parser.maskNear("$desrt"));
        assertTrue(e.getMessage().contains("did you mean 'desert'"), e.getMessage());
        assertThrows(ParseException.class, () -> parser.mask("$desert"), "en el terreno y los pinceles no: mira la posición");
        assertEquals("minecraft:cherry_grove", parser.biome("cherry_grove"));
        assertNull(parser.biome("cherry"));
        assertTrue(parser.biomeHint("chery_grove").contains("cherry_grove"));
    }
}
