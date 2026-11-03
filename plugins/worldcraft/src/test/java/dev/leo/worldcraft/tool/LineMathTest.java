package dev.leo.worldcraft.tool;

import dev.leo.worldcraft.edit.PointsSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 041 H1: las líneas, la catenaria, el círculo y el barrido de los pinceles de FAWE. */
class LineMathTest {
    static Set<Long> set(long[] pts) {
        Set<Long> s = new HashSet<>();
        for (long p : pts) s.add(p);
        return s;
    }

    static boolean has(long[] pts, int x, int y, int z) {
        return set(pts).contains(PointsSource.pack(x, y, z));
    }

    static int minY(long[] pts, int x) {
        int m = Integer.MAX_VALUE;
        for (long p : pts) if (PointsSource.unpackX(p) == x) m = Math.min(m, PointsSource.unpackY(p));
        return m;
    }

    @Test
    void H1_CA1_la_linea_plana_queda_a_la_altura_del_primer_punto_y_la_cascara_deja_el_centro() {
        long[] flat = LineMath.line(new int[]{0, 5, 0}, new int[]{8, 9, 0}, 0, false, true);
        for (long p : flat) assertEquals(5, PointsSource.unpackY(p), "plana: todo en y = 5");
        assertTrue(has(flat, 8, 5, 0), "llega a la x del segundo punto");
        long[] thick = LineMath.line(new int[]{0, 0, 0}, new int[]{10, 0, 0}, 2, false, false);
        long[] shell = LineMath.line(new int[]{0, 0, 0}, new int[]{10, 0, 0}, 2, true, false);
        assertTrue(has(thick, 5, 0, 0));
        assertFalse(has(shell, 5, 0, 0), "el centro de la línea gruesa queda vacío");
        assertTrue(has(shell, 5, 2, 0), "la cáscara sí");
        assertTrue(shell.length < thick.length);
    }

    @Test
    void H1_CA4_la_catenaria_pasa_por_los_extremos_cuelga_y_con_factor_1_es_recta() {
        int[] a = {0, 10, 0}, b = {20, 10, 0};
        long[] hang = LineMath.catenary(a, b, 1.2, new double[]{0, -1, 0}, 0, false);
        assertTrue(has(hang, 0, 10, 0) && has(hang, 20, 10, 0), "pasa por los dos puntos");
        assertTrue(minY(hang, 10) < 10 - 3, "en el medio cuelga varios bloques (largo 1,2 × 20)");
        assertTrue(minY(hang, 10) <= minY(hang, 3), "lo más bajo está en el medio");
        long[] straight = LineMath.catenary(a, b, 1.0, new double[]{0, -1, 0}, 0, false);
        for (long p : straight) assertEquals(10, PointsSource.unpackY(p), "factor 1: recta");
        // Colgando hacia +z (--facing): el medio se corre en z, no en y.
        long[] side = LineMath.catenary(a, b, 1.2, new double[]{0, 0, 1}, 0, false);
        boolean movedZ = false;
        for (long p : side) {
            if (PointsSource.unpackX(p) == 10) {
                assertEquals(10, PointsSource.unpackY(p));
                if (PointsSource.unpackZ(p) > 3) movedZ = true;
            }
        }
        assertTrue(movedZ, "cuelga hacia donde mira");
        // Desnivel: un extremo más alto, sigue pasando por los dos.
        long[] steep = LineMath.catenary(new int[]{0, 0, 0}, new int[]{10, 8, 0}, 1.3, new double[]{0, -1, 0}, 0, false);
        assertTrue(has(steep, 0, 0, 0) && has(steep, 10, 8, 0));
    }

    @Test
    void H1_CA5_el_circulo_es_perpendicular_a_la_mirada() {
        // Mirando hacia +x: el círculo queda en el plano y-z (x constante).
        long[] ring = LineMath.circle(0, 64, 0, 1, 0, 0, 4, false);
        for (long p : ring) assertEquals(0, PointsSource.unpackX(p));
        assertTrue(has(ring, 0, 68, 0) && has(ring, 0, 64, 4), "radio 4 hacia arriba y hacia z");
        assertFalse(has(ring, 0, 64, 0), "sin --fill el centro queda vacío");
        long[] disc = LineMath.circle(0, 64, 0, 1, 0, 0, 4, true);
        assertTrue(has(disc, 0, 64, 0) && disc.length > ring.length);
        // Mirando hacia abajo: un anillo horizontal.
        long[] down = LineMath.circle(0, 64, 0, 0, -1, 0, 3, false);
        for (long p : down) assertEquals(64, PointsSource.unpackY(p));
    }

    @Test
    void H1_CA3_el_barrido_reparte_las_copias_y_las_gira_segun_la_curva() {
        List<int[]> nodes = List.of(new int[]{0, 0, 0}, new int[]{20, 0, 0}, new int[]{20, 0, 20});
        List<LineMath.Placement> three = LineMath.sweep(nodes, 4, 3);
        assertEquals(3, three.size());
        assertEquals(0, three.get(0).x());
        assertEquals(20, three.get(2).z(), "la última al final de la curva");
        assertEquals(1, three.get(0).dx(), "al principio va hacia +x");
        assertEquals(1, three.get(2).dz(), "al final va hacia +z");
        List<LineMath.Placement> every = LineMath.sweep(nodes, 5, 0);
        assertTrue(every.size() >= 7 && every.size() <= 9, "una cada 5 bloques en ~40 de curva: " + every.size());
    }
}
