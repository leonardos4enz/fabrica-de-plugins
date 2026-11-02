package dev.leo.worldcraft.pattern;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.formula.Formula;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 034 H5: la máscara «=fórmula» y el patrón «=fórmula -> a, b, c», con x, y, z del mundo. */
class FormulaMaskTest {
    private final Parser parser = new Parser(TestBlocks.REGISTRY, Map.of("checker", "=(x+z)%2 -> white_wool, blue_wool"),
            Map.of("low", "=y<64"));
    private final StateTable table = new StateTable();

    private int id(String s) {
        return table.intern(TestBlocks.REGISTRY.canonical("minecraft:" + s));
    }

    private boolean test(String mask, int x, int y, int z, String block) throws ParseException {
        return parser.maskNear(mask).compile(table).test(x, y, z, id(block), null, 1);
    }

    @Test
    void H5_CA1_la_mascara_mira_la_posicion() throws ParseException {
        assertTrue(test("=y<64", 0, 10, 0, "stone"));
        assertFalse(test("=y<64", 0, 70, 0, "stone"));
        assertTrue(test("!=y<64", 0, 70, 0, "stone"), "negada");
        assertTrue(test("=y<64 and stone", 0, 10, 0, "stone"));
        assertFalse(test("=y<64 and stone", 0, 10, 0, "dirt"));
        assertTrue(test("=max(x, z) < 5, dirt", 0, 70, 9, "dirt"), "la coma de la función no parte la máscara");
        assertTrue(test("=max(x, z) < 5, dirt", 1, 70, 2, "stone"));
        assertFalse(test("=max(x, z) < 5, dirt", 9, 70, 2, "stone"));
        assertTrue(test("$low", 0, 10, 0, "stone"), "en un preset");
        Mask m = parser.maskNear("=y<64");
        assertTrue(m.positional());
        assertFalse(m.needsNeighbors(), "no hace falta leer alrededor: sirve en gmask");
        assertFalse(m.needsBiomes());
    }

    @Test
    void H5_CA1_errores_y_donde_no_se_puede() {
        String e = assertThrows(ParseException.class, () -> parser.maskNear("=sinn(x)>0")).getMessage();
        assertTrue(e.contains("did you mean 'sin'"), e);
        assertTrue(assertThrows(ParseException.class, () -> parser.maskNear("=")).getMessage().contains("write a formula"));
        String where = assertThrows(ParseException.class, () -> parser.mask("=y<64")).getMessage();
        assertTrue(where.contains("formula") && where.contains("replace"), where);
    }

    @Test
    void H5_CA1_una_evaluacion_cortada_es_falsa() throws ParseException {
        Parser tight = new Parser(TestBlocks.REGISTRY, Map.of(), Map.of()).formulaLimits(new Formula.Limits(1000, 1000, 16));
        Mask.Compiled c = tight.maskNear("=while (1) { a++ }; 1").compile(table);
        assertFalse(c.test(0, 0, 0, id("stone"), null, 1));
    }

    @Test
    void H5_CA2_el_patron_elige_por_el_numero() throws ParseException {
        Pattern p = parser.pattern("=(x+z)%2 -> white_wool, blue_wool");
        assertNotNull(p.pick());
        assertEquals(List.of("minecraft:white_wool", "minecraft:blue_wool"), p.placedStates());
        Pattern.Compiled c = p.compile(table);
        assertEquals(id("white_wool"), c.apply(0, 0, 0, 1));
        assertEquals(id("blue_wool"), c.apply(1, 0, 0, 1));
        assertEquals(id("white_wool"), c.apply(1, 0, 1, 1));
        Pattern.Compiled steps = parser.pattern("=y/2 -> stone, dirt, glass").compile(table);
        assertEquals(id("stone"), steps.apply(0, -10, 0, 1), "menos de 0: el primero");
        assertEquals(id("dirt"), steps.apply(0, 3, 0, 1), "1.5 es el segundo");
        assertEquals(id("glass"), steps.apply(0, 100, 0, 1), "más del último: el último");
        Pattern preset = parser.pattern("$checker");
        assertNotNull(preset.pick(), "el preset conserva la fórmula");
        assertEquals(id("blue_wool"), preset.compile(table).apply(0, 0, 1, 1));
        assertEquals(id("stone"), parser.pattern("=0->stone").compile(table).apply(5, 5, 5, 1), "sin espacios también");
    }

    @Test
    void H5_CA2_errores_del_patron() {
        assertTrue(assertThrows(ParseException.class, () -> parser.pattern("=x%2")).getMessage().contains("'->'"));
        assertTrue(assertThrows(ParseException.class, () -> parser.pattern("=x%2 ->")).getMessage().contains("write the blocks"));
        assertTrue(assertThrows(ParseException.class, () -> parser.pattern("=x%2 -> 50% stone, 50% dirt")).getMessage().contains("no weights"));
        assertTrue(assertThrows(ParseException.class, () -> parser.pattern("=x%2 -> stnoe")).getMessage().contains("did you mean 'stone'"));
    }

    @Test
    void split_respeta_parentesis_y_corchetes() {
        assertEquals(List.of("=max(x,z)<5", " oak_log[axis=y,x=1]", " dirt"), Parser.split("=max(x,z)<5, oak_log[axis=y,x=1], dirt"));
    }
}
