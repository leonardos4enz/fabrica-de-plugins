package dev.leo.worldcraft.region;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 030 H3: el contorno de cada forma. */
class OutlineTest {

    @Test
    void H3_cada_forma_su_dibujo() {
        assertEquals(12, Outline.of(Box.of(0, 0, 0, 3, 3, 3)).segments().size(), "caja: 12 aristas");
        Outline tri = Outline.of(new Polygon(List.of(new int[]{0, 0}, new int[]{8, 0}, new int[]{4, 6}), 60, 64));
        assertEquals(9, tri.segments().size(), "3 verticales + 3 abajo + 3 arriba");
        assertEquals(4, Outline.of(new Polygon(List.of(new int[]{0, 0}, new int[]{8, 0}), 60, 60)).segments().size(), "2 vértices: una pared");
        assertEquals(3 * 48, Outline.of(new Ellipsoid(0, 64, 0, 5, 3, 5, false)).segments().size(), "tres círculos");
        assertEquals(2 * 48 + 4, Outline.of(new Cylinder(0, 0, 4, 4, 60, 70)).segments().size(), "dos círculos y cuatro líneas");
        int[][] c = new int[8][];
        for (int i = 0; i < 8; i++) c[i] = new int[]{(i & 1) * 4, (i & 2) * 2, (i & 4)};
        // Convexa de un cubo de 5: las 12 aristas (algunas partidas en tramos) y ninguna diagonal de las caras.
        assertEquals(12 * 5, Outline.of(new Convex(List.of(c))).length(), 1e-9);
    }

    @Test
    void H3_la_magica_dibuja_solo_el_borde() {
        long[] k = new long[27];
        int n = 0;
        for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++) k[n++] = PointSet.pack(x, y, z);
        Outline o = Outline.of(new PointSet(k));
        assertEquals(26, o.points().size(), "el del medio no se ve");
        assertTrue(o.segments().isEmpty());
    }

    @Test
    void H3_el_circulo_pasa_por_el_borde_de_la_esfera() {
        Outline o = Outline.of(new Ellipsoid(10, 64, 10, 4, 4, 4, true));
        double[] first = o.segments().get(0);
        assertEquals(10.5 + 4.5, first[0], 1e-9, "radio + ½ desde el centro del bloque");
        assertEquals(64.5, first[1], 1e-9);
    }
}
