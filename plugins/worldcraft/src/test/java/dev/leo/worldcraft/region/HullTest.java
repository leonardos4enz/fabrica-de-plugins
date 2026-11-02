package dev.leo.worldcraft.region;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 030 H2 CA7 (plan §1.2): la envolvente convexa sin casos degenerados y con enteros exactos. */
class HullTest {

    private static Convex convex(int[]... pts) {
        return new Convex(List.of(pts));
    }

    @Test
    void un_punto_es_un_bloque_y_dos_una_linea() {
        assertEquals(1, convex(new int[]{5, 64, 5}).volume());
        Convex line = convex(new int[]{0, 10, 0}, new int[]{5, 10, 0});
        assertEquals(6, line.volume());
        assertTrue(line.contains(3, 10, 0));
        assertFalse(line.contains(3, 11, 0));
        Convex diag = convex(new int[]{0, 0, 0}, new int[]{6, 3, 0});
        for (int x = 0; x <= 6; x++) {
            int n = 0;
            for (int y = -1; y <= 4; y++) if (diag.contains(x, y, 0)) n++;
            assertTrue(n >= 1, "la diagonal no tiene huecos en x = " + x);
        }
    }

    @Test
    void puntos_en_un_plano_son_una_lamina_de_un_bloque() {
        Convex sq = convex(new int[]{0, 10, 0}, new int[]{6, 10, 0}, new int[]{6, 10, 6}, new int[]{0, 10, 6});
        assertEquals(49, sq.volume());
        assertEquals(10, sq.bounds().minY());
        assertEquals(10, sq.bounds().maxY());
    }

    @Test
    void las_esquinas_de_un_cubo_son_el_cubo_y_un_tetraedro_corta_la_diagonal() {
        int[][] c = new int[8][];
        for (int i = 0; i < 8; i++) c[i] = new int[]{(i & 1) * 4, (i & 2) * 2, (i & 4)};
        Convex cube = convex(c);
        assertEquals(125, cube.volume());
        assertEquals(Box.of(0, 0, 0, 4, 4, 4), cube.bounds());
        assertEquals(6, cube.hull.planes(), "las caras repetidas se juntan");
        Convex t = convex(new int[]{0, 0, 0}, new int[]{4, 0, 0}, new int[]{0, 4, 0}, new int[]{0, 0, 4});
        assertTrue(t.contains(1, 1, 1));
        assertTrue(t.contains(2, 2, 1));
        assertFalse(t.contains(2, 2, 2));
        for (int[] v : new int[][]{{0, 0, 0}, {4, 0, 0}, {0, 4, 0}, {0, 0, 4}}) assertTrue(t.contains(v[0], v[1], v[2]), "vértice");
    }

    @Test
    void la_columna_y_contains_coinciden_tambien_lejos() {
        Convex far = convex(new int[]{29_999_000, 100, -29_999_000}, new int[]{29_999_040, 140, -29_998_990},
                new int[]{29_999_013, 60, -29_998_960}, new int[]{29_998_990, 120, -29_998_970}, new int[]{29_999_020, 100, -29_998_999});
        Box b = far.bounds();
        long n = 0;
        for (int x = b.minX(); x <= b.maxX(); x++) for (int y = b.minY(); y <= b.maxY(); y++) for (int z = b.minZ(); z <= b.maxZ(); z++) {
            boolean h = far.hull.contains(x, y, z);
            assertEquals(h, far.contains(x, y, z), x + " " + y + " " + z);
            if (h) n++;
        }
        assertEquals(n, far.volume());
        assertTrue(n > 1000);
    }

    @Test
    void demasiado_ancha_se_rechaza() {
        assertThrows(IllegalArgumentException.class, () -> convex(new int[]{0, 0, 0}, new int[]{70_000, 0, 0}));
    }
}
