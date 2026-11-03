package dev.leo.worldcraft.tool;

import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.edit.Snapshot;
import dev.leo.worldcraft.region.Box;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 040 H2 CA4 y CA5: los pinceles morph, dilate y heightmap. */
class MorphHeightmapTest {
    final StateTable table = new StateTable();
    final int air = table.intern("minecraft:air"), stone = table.intern("minecraft:stone"), dirt = table.intern("minecraft:dirt"),
            grass = table.intern("minecraft:grass_block");
    final IntPredicate isAir = s -> s == air;

    Snapshot box(int r) {
        Snapshot s = new Snapshot(new Box(-r, -r, -r, r, r, r));
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) s.put(x, y, z, air);
        return s;
    }

    int at(Map<Long, Integer> out, Snapshot s, int x, int y, int z) {
        Integer c = out.get(PointsSource.pack(x, y, z));
        return c != null ? c : s.get(x, y, z);
    }

    @Test
    void H2_CA4_erosion_quita_lo_que_sobresale_y_dilatar_llena_los_pozos() {
        Snapshot s = box(4);
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) s.put(x, 0, z, stone);
        s.put(0, 1, 0, stone);       // una punta: 5 caras al aire
        s.put(2, 0, 2, air);         // un pozo: 5 caras de piedra (4 lados y abajo… abajo es aire aquí: 4)
        Map<Long, Integer> eroded = BrushMath.morph(s, 0, 0, 0, 3, 4, 1, 6, 0, isAir, air);
        assertEquals(air, at(eroded, s, 0, 1, 0), "la punta se va");
        assertEquals(stone, at(eroded, s, 1, 0, 0), "el suelo (2 caras al aire) queda");
        Map<Long, Integer> filled = BrushMath.morph(s, 0, 0, 0, 3, 6, 0, 4, 1, isAir, air);
        assertEquals(stone, at(filled, s, 2, 0, 2), "el pozo se llena con el bloque de alrededor");
        assertEquals(air, at(filled, s, 1, 1, 1), "el aire libre sobre el suelo no");
        assertFalse(filled.containsKey(PointsSource.pack(4, 0, 4)), "fuera de la esfera nada");
    }

    @Test
    void H2_CA4_dilatar_usa_el_bloque_mas_comun() {
        Snapshot s = box(2);
        s.put(1, 0, 0, dirt);
        s.put(-1, 0, 0, dirt);
        s.put(0, 1, 0, dirt);
        s.put(0, -1, 0, stone);
        Map<Long, Integer> out = BrushMath.morph(s, 0, 0, 0, 1, 6, 0, 3, 1, isAir, air);
        assertEquals(dirt, at(out, s, 0, 0, 0));
    }

    @Test
    void H2_CA5_heightmap_sube_en_lo_blanco_y_copia_la_cima() {
        Snapshot s = new Snapshot(new Box(-3, 0, -3, 3, 10, 3));
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++)
                for (int y = 0; y <= 10; y++) s.put(x, y, z, y < 2 ? dirt : y == 2 ? grass : air);
        double[][] img = new double[7][7];
        img[3][3] = 1;                       // el centro blanco, lo demás negro
        Map<Long, Integer> out = BrushMath.heightmap(s, 0, 2, 0, 3, img, 4, false, false, isAir, air);
        assertEquals(grass, at(out, s, 0, 6, 0), "el centro sube 4 con el pasto arriba");
        assertEquals(dirt, at(out, s, 0, 2, 0), "debajo, lo de abajo");
        assertTrue(out.keySet().stream().allMatch(p -> PointsSource.unpackX(p) == 0 && PointsSource.unpackZ(p) == 0), "lo negro no cambia");
        Map<Long, Integer> down = BrushMath.heightmap(s, 0, 2, 0, 3, img, 1, true, false, isAir, air);
        assertEquals(grass, at(down, s, 0, 1, 0), "--erase baja");
        assertEquals(air, at(down, s, 0, 2, 0));
    }

    @Test
    void H2_CA5_heightmap_flatten_deja_cada_columna_a_esa_altura() {
        Snapshot s = new Snapshot(new Box(-1, 0, -1, 1, 10, 1));
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                for (int y = 0; y <= 10; y++) s.put(x, y, z, y <= (x == 1 ? 7 : 2) ? stone : air);
        double[][] img = {{0.5, 0.5, 0.5}, {0.5, 0.5, 0.5}, {0.5, 0.5, 0.5}};
        Map<Long, Integer> out = BrushMath.heightmap(s, 0, 2, 0, 1, img, 4, false, true, isAir, air);
        assertEquals(stone, at(out, s, 0, 4, 0), "la base (2) + la mitad de 4");
        assertEquals(air, at(out, s, 0, 5, 0));
        assertEquals(air, at(out, s, 1, 5, 0), "la columna alta baja a 4");
        assertEquals(stone, at(out, s, 1, 4, 0));
    }
}
