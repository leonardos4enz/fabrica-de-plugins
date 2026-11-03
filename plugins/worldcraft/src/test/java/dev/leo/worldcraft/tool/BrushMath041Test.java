package dev.leo.worldcraft.tool;

import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.edit.Snapshot;
import dev.leo.worldcraft.region.Box;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 041 H1 CA6, H2 CA1–CA2 y H4 CA2: pull, shatter, stencil y //fall. */
class BrushMath041Test {
    final StateTable table = new StateTable();
    final int air = table.intern("minecraft:air"), stone = table.intern("minecraft:stone"), sand = table.intern("minecraft:sand"),
            chest = table.intern("minecraft:chest"), glass = table.intern("minecraft:glass");
    final IntPredicate isAir = s -> s == air;

    Snapshot empty(Box b) {
        Snapshot s = new Snapshot(b);
        for (int x = b.minX(); x <= b.maxX(); x++)
            for (int y = b.minY(); y <= b.maxY(); y++)
                for (int z = b.minZ(); z <= b.maxZ(); z++) s.put(x, y, z, air);
        return s;
    }

    int at(Map<Long, Integer> out, Snapshot s, int x, int y, int z) {
        Integer c = out.get(PointsSource.pack(x, y, z));
        return c != null ? c : s.get(x, y, z);
    }

    @Test
    void H1_CA6_pull_estira_el_bloque_hacia_el_jugador() {
        Snapshot s = empty(new Box(-4, -4, -4, 4, 4, 4));
        // Una pared en x = 2; el jugador está hacia −x: dir = (−1, 0, 0).
        for (int y = -4; y <= 4; y++) for (int z = -4; z <= 4; z++) s.put(2, y, z, stone);
        Map<Long, Integer> one = BrushMath.pull(s, 0, 0, 0, 3, new int[]{-1, 0, 0}, 1, isAir);
        assertEquals(stone, at(one, s, 1, 0, 0), "el aire delante de la pared se llena");
        assertEquals(air, at(one, s, 0, 0, 0), "una pasada: solo un bloque");
        Map<Long, Integer> two = BrushMath.pull(s, 0, 0, 0, 3, new int[]{-1, 0, 0}, 2, isAir);
        assertEquals(stone, at(two, s, 0, 0, 0), "dos pasadas: dos bloques");
        assertEquals(air, at(two, s, -2, 0, 0), "lo de más lejos no");
    }

    @Test
    void H2_CA1_shatter_parte_lo_solido_en_pedazos_con_bordes() {
        Snapshot s = empty(new Box(-6, -6, -6, 6, 6, 6));
        for (int x = -6; x <= 6; x++) for (int y = -6; y <= 6; y++) for (int z = -6; z <= 6; z++) s.put(x, y, z, stone);
        long[] cut = BrushMath.shatter(s, 0, 0, 0, 5, 6, isAir, 42L);
        assertTrue(cut.length > 10, "hay bordes");
        assertTrue(cut.length < 400, "no es todo: " + cut.length);
        assertArrayEquals(cut, BrushMath.shatter(s, 0, 0, 0, 5, 6, isAir, 42L), "la misma semilla da lo mismo");
        for (long p : cut) {
            int x = PointsSource.unpackX(p), y = PointsSource.unpackY(p), z = PointsSource.unpackZ(p);
            assertTrue(x * x + y * y + z * z <= 5.5 * 5.5, "dentro de la esfera");
        }
        assertEquals(0, BrushMath.shatter(s, 0, 0, 0, 5, 1, isAir, 42L).length, "un solo pedazo: sin bordes");
    }

    @Test
    void H2_CA2_stencil_pinta_la_cima_donde_la_imagen_es_clara() {
        Snapshot s = empty(new Box(-3, 0, -3, 3, 6, 3));
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) s.put(x, 2, z, stone);
        double[][] half = new double[7][7];
        for (int j = 0; j < 7; j++) for (int i = 0; i < 7; i++) half[j][i] = i >= 4 ? 1 : 0.2;
        long[] tops = BrushMath.stencil(s, 0, 0, 3, half, false, isAir);
        Set<Long> t = new HashSet<>();
        for (long p : tops) t.add(p);
        assertTrue(t.contains(PointsSource.pack(2, 2, 0)), "la mitad clara se pinta, en la cima");
        assertFalse(t.contains(PointsSource.pack(-2, 2, 0)), "la oscura no");
        double[][] cone = BrushMath.cone(7);
        assertEquals(1.0, cone[3][3], 1e-9, "el cono es blanco en el centro");
        assertEquals(0.0, cone[0][0], 1e-9, "y negro en la esquina");
        assertEquals(1, BrushMath.rotate(new double[][]{{1, 0}, {0, 0}}, 1)[0][1], 1e-9, "girar un cuarto");
    }

    @Test
    void H4_CA2_fall_hace_caer_lo_de_la_seleccion_y_deja_el_relleno() {
        Snapshot s = empty(new Box(0, 0, 0, 2, 9, 0));
        s.put(0, 0, 0, stone);          // el suelo (fuera de la selección)
        s.put(0, 5, 0, sand);
        s.put(0, 7, 0, sand);
        s.put(1, 6, 0, chest);          // con datos: no se mueve
        s.put(1, 9, 0, sand);
        s.put(2, 6, 0, sand);
        // Selección y de 3 a 9; en la columna 2 hay un vidrio en y = 2, por debajo de la selección.
        s.put(2, 2, 0, glass);
        Map<Long, Integer> out = BrushMath.fall(s, (x, y, z) -> y >= 3, isAir, st -> st == chest, air, 0);
        assertEquals(sand, at(out, s, 0, 1, 0), "cae sobre el suelo");
        assertEquals(sand, at(out, s, 0, 2, 0), "y la otra encima");
        assertEquals(air, at(out, s, 0, 5, 0));
        assertEquals(chest, at(out, s, 1, 6, 0), "el cofre no se mueve");
        assertEquals(sand, at(out, s, 1, 7, 0), "y hace de piso");
        assertEquals(sand, at(out, s, 2, 3, 0), "se apoya en lo que hay fuera de la selección");
        Map<Long, Integer> inside = BrushMath.fall(s, (x, y, z) -> y >= 3, isAir, st -> st == chest, air, 3);
        assertEquals(sand, at(inside, s, 0, 3, 0), "con --inside no baja de la selección");
        assertEquals(air, at(inside, s, 0, 1, 0));
        Map<Long, Integer> filled = BrushMath.fall(s, (x, y, z) -> y >= 3, isAir, st -> st == chest, glass, 0);
        assertEquals(glass, at(filled, s, 0, 5, 0), "lo que deja queda con el relleno");
        assertEquals(air, at(filled, s, 0, 4, 0), "el aire que ya era aire sigue igual");
    }
}
