package dev.leo.worldcraft.region;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 028 H1: las formas (plan §1.1). */
class SolidTest {

    static long count(Solid s) {
        long n = 0;
        Box b = s.bounds();
        for (int x = b.minX(); x <= b.maxX(); x++)
            for (int y = b.minY(); y <= b.maxY(); y++)
                for (int z = b.minZ(); z <= b.maxZ(); z++) if (s.contains(x, y, z)) n++;
        return n;
    }

    @Test
    void H1_CA3_esfera_igual_a_la_formula_punto_por_punto_y_simetrica() {
        for (int r : new int[]{0, 1, 2, 5, 9}) {
            Solid s = Solid.sphere(100, 64, -40, r, r, r);
            double e = r + 0.5;
            long expected = 0;
            for (int dx = -r; dx <= r; dx++)
                for (int dy = -r; dy <= r; dy++)
                    for (int dz = -r; dz <= r; dz++) {
                        boolean in = (dx * dx + dy * dy + dz * dz) / (e * e) <= 1;
                        if (in) expected++;
                        assertEquals(in, s.contains(100 + dx, 64 + dy, -40 + dz), "r=" + r + " " + dx + "," + dy + "," + dz);
                    }
            assertEquals(expected, s.volume());
            assertEquals(expected, count(s));
            assertEquals(new Box(100 - r, 64 - r, -40 - r, 100 + r, 64 + r, -40 + r), s.bounds());
        }
        assertEquals(1, Solid.sphere(0, 0, 0, 0, 0, 0).volume());
        assertEquals(19, Solid.sphere(0, 0, 0, 1, 1, 1).volume(), "radio 1 como el de WorldEdit");
    }

    @Test
    void H1_CA1_elipsoide_con_radios_distintos() {
        Solid s = Solid.sphere(0, 0, 0, 6, 2, 3);
        assertEquals(new Box(-6, -2, -3, 6, 2, 3), s.bounds());
        assertTrue(s.contains(6, 0, 0));
        assertFalse(s.contains(0, 3, 0));
        assertFalse(s.contains(6, 0, 3));
    }

    @Test
    void H1_CA2_cilindro_piramide_y_cono() {
        Solid c = Solid.cylinder(0, 10, 0, 3, 3, 5);
        assertEquals(new Box(-3, 10, -3, 3, 14, 3), c.bounds());
        long base = 0;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) if ((dx * dx + dz * dz) / 12.25 <= 1) base++;
        assertEquals(base * 5, c.volume(), "cada capa es el mismo círculo");
        Solid down = Solid.cylinder(0, 10, 0, 2, 2, -4);
        assertEquals(7, down.bounds().minY(), "altura negativa: hacia abajo");
        assertEquals(10, down.bounds().maxY());

        Solid p = Solid.pyramid(0, 0, 0, 4);
        assertEquals(new Box(-3, 0, -3, 3, 3, 3), p.bounds());
        assertEquals(7 * 7 + 5 * 5 + 3 * 3 + 1, p.volume(), "capas cuadradas de 7, 5, 3 y 1");

        Solid cone = Solid.cone(0, 0, 0, 4, 8);
        assertEquals(7, cone.bounds().maxY(), "la punta en lo alto");
        assertEquals(0, cone.bounds().minY());
        assertTrue(cone.contains(0, 7, 0));
        assertFalse(cone.contains(4, 1, 0), "el borde es bajo");
        Solid inverted = Solid.cone(0, 0, 0, 4, -8);
        assertEquals(-7, inverted.bounds().minY());
        assertTrue(inverted.contains(0, -7, 0));
    }

    @Test
    void H1_CA3_hueca_sin_agujeros_vista_desde_fuera() {
        for (Solid full : new Solid[]{Solid.sphere(0, 0, 0, 7, 7, 7), Solid.sphere(0, 0, 0, 9, 4, 6),
                Solid.pyramid(0, 0, 0, 8), Solid.cone(0, 0, 0, 6, 10)}) {
            for (int t : new int[]{1, 2}) {
                Solid shell = full.hollow(t, false);
                assertTrue(shell.volume() < full.volume(), "tiene interior");
                Box b = full.bounds();
                // Una inundación desde fuera de la caja, sin pasar por la cáscara, no llega al interior.
                Set<Long> outside = flood(b, shell);
                for (int x = b.minX(); x <= b.maxX(); x++)
                    for (int y = b.minY(); y <= b.maxY(); y++)
                        for (int z = b.minZ(); z <= b.maxZ(); z++) {
                            if (full.contains(x, y, z) && !shell.contains(x, y, z)) {
                                assertFalse(outside.contains(key(x, y, z)), "hueco en " + x + "," + y + "," + z);
                            }
                        }
                // Grosor: todo bloque de la cáscara está a t o menos de un bloque de fuera.
                for (int x = b.minX(); x <= b.maxX(); x++)
                    for (int y = b.minY(); y <= b.maxY(); y++)
                        for (int z = b.minZ(); z <= b.maxZ(); z++) {
                            if (shell.contains(x, y, z)) assertTrue(near(full, x, y, z, t), "demasiado grueso en " + x + "," + y + "," + z);
                        }
            }
        }
    }

    @Test
    void H1_CA1_cilindro_hueco_sin_tapas_como_worldedit() {
        Solid c = Solid.cylinder(0, 0, 0, 5, 5, 6).hollow(1, true);
        assertFalse(c.contains(0, 0, 0), "sin piso");
        assertFalse(c.contains(0, 5, 0), "sin techo");
        assertTrue(c.contains(5, 3, 0));
        Solid closed = Solid.sphere(0, 0, 0, 1, 1, 1).hollow(1, false);
        assertEquals(18, closed.volume(), "solo el centro tiene sus 6 vecinos dentro");
        assertFalse(closed.contains(0, 0, 0));
    }

    @Test
    void H1_CA4_la_estimacion_esta_cerca_del_volumen() {
        assertEquals(Solid.sphere(0, 0, 0, 20, 20, 20).volume(), Solid.estimate("sphere", 20, 20, 20, 0), 0.03 * 34000);
        assertEquals(Solid.cylinder(0, 0, 0, 20, 20, 10).volume(), Solid.estimate("cyl", 20, 0, 20, 10), 0.05 * 13000);
        assertEquals(Solid.pyramid(0, 0, 0, 10).volume(), Solid.estimate("pyramid", 10, 0, 0, 0), 0.25 * 1330);
        assertEquals(Solid.cone(0, 0, 0, 15, 20).volume(), Solid.estimate("cone", 15, 0, 0, 20), 0.25 * 5000);
    }

    @Test
    void H2_CA6_linea_diagonal_sin_huecos() {
        long[] pts = Line.points(0, 0, 0, 10, 4, -7, 0, (x, y, z) -> key(x, y, z));
        assertEquals(11, pts.length);
        assertEquals(key(0, 0, 0), pts[0]);
        assertEquals(key(10, 4, -7), pts[10]);
        long[] thick = Line.points(0, 0, 0, 0, 0, 0, 1, (x, y, z) -> key(x, y, z));
        assertEquals(19, thick.length, "una esfera de radio 1");
    }

    // ------------------------------------------------------------------

    static long key(int x, int y, int z) {
        return ((long) (x + 1000) * 4000 + (y + 1000)) * 4000 + (z + 1000);
    }

    private static Set<Long> flood(Box b, Solid shell) {
        Box out = new Box(b.minX() - 1, b.minY() - 1, b.minZ() - 1, b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{out.minX(), out.minY(), out.minZ()});
        seen.add(key(out.minX(), out.minY(), out.minZ()));
        int[][] sides = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] o : sides) {
                int x = p[0] + o[0], y = p[1] + o[1], z = p[2] + o[2];
                if (!out.contains(x, y, z) || shell.contains(x, y, z) || !seen.add(key(x, y, z))) continue;
                q.add(new int[]{x, y, z});
            }
        }
        return seen;
    }

    /** ¿Hay algo fuera de la forma a t pasos (6 vecinos) o menos? */
    private static boolean near(Solid full, int x, int y, int z, int t) {
        for (int dx = -t; dx <= t; dx++)
            for (int dy = -t; dy <= t; dy++)
                for (int dz = -t; dz <= t; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > t) continue;
                    if (!full.contains(x + dx, y + dy, z + dz)) return true;
                }
        return false;
    }
}
