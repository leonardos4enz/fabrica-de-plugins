package dev.leo.worldcraft.region;

import dev.leo.worldcraft.edit.PointsSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 040 H1 CA1: //curve, una curva suave que pasa por los puntos de la selección, en orden. */
class CurveTest {
    static Set<Long> set(long[] pts) {
        Set<Long> s = new HashSet<>();
        for (long p : pts) s.add(p);
        return s;
    }

    @Test
    void pasa_por_cada_punto_en_orden_y_sin_huecos() {
        List<int[]> nodes = List.of(new int[]{0, 0, 0}, new int[]{10, 5, 0}, new int[]{20, 0, 8});
        long[] pts = Curve.points(nodes, 0, false, PointsSource::pack);
        Set<Long> s = set(pts);
        for (int[] n : nodes) assertTrue(s.contains(PointsSource.pack(n[0], n[1], n[2])), "pasa por " + n[0] + "," + n[1] + "," + n[2]);
        // Sin huecos: cada bloque tiene otro a distancia de rey (26 vecinos), salvo que sea uno solo.
        for (long p : s) {
            int x = PointsSource.unpackX(p), y = PointsSource.unpackY(p), z = PointsSource.unpackZ(p);
            boolean near = false;
            for (int dx = -1; dx <= 1 && !near; dx++)
                for (int dy = -1; dy <= 1 && !near; dy++)
                    for (int dz = -1; dz <= 1 && !near; dz++)
                        if ((dx | dy | dz) != 0 && s.contains(PointsSource.pack(x + dx, y + dy, z + dz))) near = true;
            assertTrue(near, "bloque suelto en " + x + "," + y + "," + z);
        }
        assertTrue(s.size() >= 21, "al menos tan larga como el eje x: " + s.size());
    }

    @Test
    void es_suave_no_dos_lineas_rectas() {
        // Con tres puntos en L, la curva se aparta del vértice: pasa por (10, 0, 10) pero antes no sigue la línea recta.
        List<int[]> nodes = List.of(new int[]{0, 0, 0}, new int[]{10, 0, 10}, new int[]{20, 0, 0});
        Set<Long> curve = set(Curve.points(nodes, 0, false, PointsSource::pack));
        Set<Long> straight = set(Line.points(0, 0, 0, 10, 0, 10, 0, PointsSource::pack));
        straight.addAll(set(Line.points(10, 0, 10, 20, 0, 0, 0, PointsSource::pack)));
        assertNotEquals(straight, curve);
        // Catmull-Rom uniforme: a mitad del primer tramo, (5 − 10/16·…): la curva va por encima de la recta x = z.
        boolean above = curve.stream().anyMatch(p -> PointsSource.unpackX(p) == 5 && PointsSource.unpackZ(p) > 5);
        assertTrue(above, "la curva se abre hacia afuera del vértice");
    }

    @Test
    void con_grosor_y_hueca() {
        List<int[]> nodes = List.of(new int[]{0, 0, 0}, new int[]{12, 0, 0});
        Set<Long> full = set(Curve.points(nodes, 2, false, PointsSource::pack));
        Set<Long> hollow = set(Curve.points(nodes, 2, true, PointsSource::pack));
        assertTrue(full.contains(PointsSource.pack(6, 2, 0)), "radio 2 alrededor");
        assertTrue(full.contains(PointsSource.pack(6, 0, 0)));
        assertFalse(hollow.contains(PointsSource.pack(6, 0, 0)), "la hueca no tiene el centro");
        assertTrue(hollow.contains(PointsSource.pack(6, 2, 0)), "la hueca tiene la cáscara");
        assertTrue(full.containsAll(hollow));
        assertTrue(hollow.size() < full.size());
    }

    @Test
    void un_solo_punto_es_un_bloque() {
        long[] pts = Curve.points(List.of(new int[]{3, 4, 5}), 0, false, PointsSource::pack);
        assertArrayEquals(new long[]{PointsSource.pack(3, 4, 5)}, pts);
    }
}
