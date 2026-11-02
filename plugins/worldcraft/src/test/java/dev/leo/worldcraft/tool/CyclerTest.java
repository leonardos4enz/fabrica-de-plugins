package dev.leo.worldcraft.tool;

import dev.leo.worldcraft.block.StateTable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 032 H5 CA4: el cycler con los valores que acepta el «servidor» (aquí, una tabla; en Paper, createBlockData). */
class CyclerTest {
    static final Map<String, List<String>> STAIRS = Map.of(
            "facing", List.of("north", "south", "west", "east"),
            "half", List.of("top", "bottom"),
            "shape", List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right"),
            "waterlogged", List.of("true", "false"));
    static final Map<String, List<String>> CROP = Map.of("age", List.of("0", "1", "2", "3", "4", "5", "6", "7"));

    /** Acepta un estado si cada propiedad tiene un valor de la tabla de su bloque. */
    static final Predicate<String> VALID = state -> {
        Map<String, List<String>> table = state.startsWith("minecraft:oak_stairs") ? STAIRS : CROP;
        for (Map.Entry<String, String> e : StateTable.parseProperties(state).entrySet()) {
            List<String> ok = table.get(e.getKey());
            if (ok == null || !ok.contains(e.getValue())) return false;
        }
        return true;
    };

    static final String STAIR = "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]";

    @Test
    void H5_CA4_valores_siguiente_y_anterior_de_cada_propiedad() {
        assertEquals(List.of("north", "east", "south", "west"), Cycler.values(STAIR, "facing", VALID), "en el orden de Minecraft");
        assertEquals("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", Cycler.cycle(STAIR, "facing", false, VALID));
        assertEquals("minecraft:oak_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]", Cycler.cycle(STAIR, "facing", true, VALID),
                "agachado, el anterior (y da la vuelta)");
        assertEquals(List.of("bottom", "top"), Cycler.values(STAIR, "half", VALID));
        assertEquals(5, Cycler.values(STAIR, "shape", VALID).size());
        assertTrue(Cycler.cycle(STAIR, "waterlogged", false, VALID).contains("waterlogged=true"));
        assertEquals(List.of("0", "1", "2", "3", "4", "5", "6", "7"), Cycler.values("minecraft:wheat[age=3]", "age", VALID), "números");
        assertEquals("minecraft:wheat[age=0]", Cycler.cycle("minecraft:wheat[age=7]", "age", false, VALID));
    }

    @Test
    void H5_CA4_propiedad_siguiente_y_un_bloque_sin_estado() {
        assertEquals("facing", Cycler.nextProperty(STAIR, null));
        assertEquals("half", Cycler.nextProperty(STAIR, "facing"));
        assertEquals("facing", Cycler.nextProperty(STAIR, "waterlogged"), "da la vuelta");
        assertEquals("facing", Cycler.nextProperty(STAIR, "age"), "una propiedad de otro bloque: la primera");
        assertNull(Cycler.nextProperty("minecraft:stone", null));
        assertNull(Cycler.cycle("minecraft:stone", "facing", false, VALID));
    }
}
