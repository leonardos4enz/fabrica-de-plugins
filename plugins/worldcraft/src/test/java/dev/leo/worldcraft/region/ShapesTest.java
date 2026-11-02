package dev.leo.worldcraft.region;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 030 H1–H4 (plan §1.1): cada forma por dentro (contiene, volumen, cáscara) y su recorrido por chunks. */
class ShapesTest {

    /** Lo que la forma dice que contiene, mirando bloque a bloque la caja que la abarca (y un borde). */
    static Set<List<Integer>> brute(Region r, Shape shape) {
        Set<List<Integer>> out = new HashSet<>();
        Box b = r.bounds();
        for (int x = b.minX() - 1; x <= b.maxX() + 1; x++) {
            for (int y = b.minY() - 1; y <= b.maxY() + 1; y++) {
                for (int z = b.minZ() - 1; z <= b.maxZ() + 1; z++) {
                    if (!r.contains(x, y, z)) continue;
                    boolean side = !r.contains(x + 1, y, z) || !r.contains(x - 1, y, z) || !r.contains(x, y, z + 1) || !r.contains(x, y, z - 1);
                    boolean ends = !r.contains(x, y + 1, z) || !r.contains(x, y - 1, z);
                    boolean in = switch (shape) {
                        case FILL -> true;
                        case WALLS -> side;
                        case FACES -> side || ends;
                    };
                    if (in) out.add(List.of(x, y, z));
                }
            }
        }
        return out;
    }

    /** Lo que recorre ChunkOrder (cada posición una vez). */
    static Set<List<Integer>> walked(Region r, Shape shape) {
        Set<List<Integer>> out = new HashSet<>();
        ChunkOrder o = new ChunkOrder(r, shape);
        int[] p = new int[3];
        while (o.next(p)) assertTrue(out.add(List.of(p[0], p[1], p[2])), "dos veces " + List.of(p[0], p[1], p[2]));
        return out;
    }

    static void consistent(Region r) {
        for (Shape s : Shape.values()) {
            Set<List<Integer>> want = brute(r, s);
            assertEquals(want.size(), r.count(s), r.kind() + " " + s + ": count");
            assertEquals(want, walked(r, s), r.kind() + " " + s + ": recorrido");
        }
        assertEquals(brute(r, Shape.FILL).size(), r.volume(), r.kind() + ": volumen");
    }

    static List<Region> shapes() {
        return List.of(
                Box.of(-3, 60, 14, 5, 63, 20),
                new Polygon(List.of(new int[]{0, 0}, new int[]{12, 0}, new int[]{12, 4}, new int[]{4, 4}, new int[]{4, 12}, new int[]{0, 12}), 60, 64),
                new Polygon(List.of(new int[]{-5, 3}, new int[]{9, 20}), 10, 10),
                new Ellipsoid(10, 64, -7, 5, 3, 7, false),
                new Ellipsoid(17, 64, 17, 4, 4, 4, true),
                new Cylinder(-20, 30, 6, 3, 60, 66),
                new Convex(List.of(new int[]{0, 60, 0}, new int[]{10, 60, 2}, new int[]{3, 70, 8}, new int[]{5, 64, -6})),
                new PointSet(new long[]{PointSet.pack(0, 0, 0), PointSet.pack(1, 0, 0), PointSet.pack(2, 0, 0), PointSet.pack(2, 1, 0),
                        PointSet.pack(2, 1, 1), PointSet.pack(-17, -3, 31)}));
    }

    @Test
    void H4_CA2_cada_forma_cuenta_y_recorre_lo_mismo_que_contiene() {
        for (Region r : shapes()) consistent(r);
    }

    @Test
    void H4_CA2_en_una_caja_la_cascara_es_lo_de_siempre() {
        Box b = Box.of(0, 0, 0, 5, 3, 4);
        assertEquals(b.count(Shape.WALLS), brute(b, Shape.WALLS).size(), "walls de la 025");
        assertEquals(b.count(Shape.FACES), brute(b, Shape.FACES).size(), "faces de la 025");
        // Un polígono rectangular es la misma caja, también en la cáscara.
        Polygon p = new Polygon(List.of(new int[]{0, 0}, new int[]{5, 0}, new int[]{5, 4}, new int[]{0, 4}), 0, 3);
        for (Shape s : Shape.values()) assertEquals(brute(b, s), brute(p, s), s.name());
    }

    @Test
    void H2_CA3_poly_concavo_deja_fuera_el_hueco_de_la_L_y_dos_vertices_son_una_linea() {
        Polygon l = new Polygon(List.of(new int[]{0, 0}, new int[]{12, 0}, new int[]{12, 4}, new int[]{4, 4}, new int[]{4, 12}, new int[]{0, 12}), 60, 64);
        assertTrue(l.contains(2, 60, 10));
        assertTrue(l.contains(10, 64, 2));
        assertFalse(l.contains(8, 62, 8), "el hueco de la L");
        assertTrue(l.contains(12, 60, 4) && l.contains(4, 60, 12), "vértices");
        assertFalse(l.contains(2, 65, 2), "más alto que el vértice más alto");
        // Dos vértices: una línea sin huecos (cada paso a lo más en diagonal).
        Polygon line = new Polygon(List.of(new int[]{0, 0}, new int[]{9, 4}), 5, 5);
        int prev = -1;
        for (int x = 0; x <= 9; x++) {
            int z = -1;
            for (int k = 0; k <= 4; k++) if (line.contains(x, 5, k)) z = k;
            assertTrue(z >= 0, "columna " + x);
            if (prev >= 0) assertTrue(z - prev <= 1, "sin saltos");
            prev = z;
        }
    }

    @Test
    void H2_CA5_la_esfera_seleccionada_es_la_de_wc_sphere() {
        Ellipsoid e = new Ellipsoid(3, 50, -2, 6, 6, 6, true);
        Solid s = Solid.sphere(3, 50, -2, 6, 6, 6);
        assertEquals(s.volume(), e.volume());
        for (int x = -4; x <= 10; x++) for (int y = 43; y <= 57; y++) for (int z = -9; z <= 5; z++) {
            assertEquals(s.contains(x, y, z), e.contains(x, y, z), x + " " + y + " " + z);
        }
        assertEquals(19, new Ellipsoid(0, 0, 0, 1, 1, 1, true).volume(), "radio 1 = 19, como WorldEdit");
    }

    @Test
    void H2_CA6_cilindro_radio_0_es_una_columna_y_radio_1_un_cuadrado_de_9() {
        assertEquals(5, new Cylinder(0, 0, 0, 0, 10, 14).volume());
        assertEquals(9, new Cylinder(0, 0, 1, 1, 10, 10).volume());
        Cylinder c = new Cylinder(0, 0, 4, 4, 0, 9);
        assertFalse(c.contains(4, 0, 4), "la esquina de la caja no");
        Set<List<Integer>> tube = brute(c, Shape.WALLS);
        assertTrue(tube.contains(List.of(4, 5, 0)) && tube.contains(List.of(0, 0, -4)), "walls: el borde, de abajo arriba");
        assertFalse(tube.contains(List.of(0, 0, 0)), "walls: sin tapas");
        assertTrue(brute(c, Shape.FACES).contains(List.of(0, 0, 0)), "faces: con tapas");
    }

    @Test
    void H4_CA1_dentro_del_alto_del_mundo() {
        Region c = new Cylinder(0, 0, 2, 2, -70, -60).clampY(-64, 319);
        assertEquals(-64, c.bounds().minY());
        assertEquals(new Cylinder(0, 0, 2, 2, -64, -60).volume(), c.volume());
        consistent(c);
        assertNull(new Ellipsoid(0, -80, 0, 3, 3, 3, false).clampY(-64, 319));
        PointSet p = new PointSet(new long[]{PointSet.pack(0, -70, 0), PointSet.pack(0, -60, 0)});
        assertEquals(1, p.clampY(-64, 319).volume());
    }

    @Test
    void H2_CA8_puntos_empaquetados_con_negativos() {
        long k = PointSet.pack(-30_000_000, -64, 29_999_999);
        assertEquals(-30_000_000, PointSet.x(k));
        assertEquals(-64, PointSet.y(k));
        assertEquals(29_999_999, PointSet.z(k));
        PointSet a = new PointSet(new long[]{PointSet.pack(1, 2, 3)}), b = new PointSet(new long[]{PointSet.pack(1, 2, 3), PointSet.pack(-1, 2, 3)});
        assertEquals(2, a.union(b).volume(), "la unión no repite");
    }
}
