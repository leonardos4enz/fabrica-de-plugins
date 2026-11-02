package dev.leo.worldcraft.region;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 030 H1, H2 y H4 CA5 (plan §1.3): los clics de cada forma, como WorldEdit, y cómo se cambia cada una. */
class SelectorTest {
    private final Selector s = new Selector();

    @Test
    void H1_CA1_nombres_de_worldedit_y_fawe_y_cambiar_borra_los_puntos() {
        assertEquals(Selector.Kind.CUBOID, s.kind(), "de fábrica");
        assertEquals(Selector.Kind.CUBOID, Selector.NAMES.get("box"));
        assertEquals(Selector.Kind.MAGIC, Selector.NAMES.get("fuzzy"));
        assertEquals(Selector.Kind.CONVEX, Selector.NAMES.get("hull"));
        assertEquals(Selector.Kind.CONVEX, Selector.NAMES.get("polyhedral"));
        s.primary(0, 0, 0);
        s.secondary(2, 2, 2, 64);
        assertNotNull(s.region());
        s.kind(Selector.Kind.SPHERE);
        assertNull(s.region());
        assertTrue(s.empty());
    }

    @Test
    void H2_CA1_cuboid_como_hoy() {
        assertEquals("Corner 1", s.primary(5, 64, 5));
        assertNull(s.region());
        assertEquals("Corner 2", s.secondary(1, 60, 9, 64));
        assertEquals(Box.of(1, 60, 5, 5, 64, 9), s.region());
        assertArrayEquals(new int[]{5, 64, 5}, s.corner1());
        assertArrayEquals(new int[]{1, 60, 9}, s.corner2());
    }

    @Test
    void H2_CA2_extend_crece_con_cada_clic_derecho() {
        s.kind(Selector.Kind.EXTEND);
        s.primary(0, 64, 0);
        assertEquals(new Box(0, 64, 0, 0, 64, 0), s.region());
        s.secondary(3, 66, -2, 64);
        s.secondary(-1, 64, 1, 64);
        assertEquals(new Box(-1, 64, -2, 3, 66, 1), s.region());
        s.primary(10, 10, 10);
        assertEquals(1, s.region().volume(), "el izquierdo empieza de nuevo");
        s.select(Box.of(0, 0, 0, 1, 1, 1));
        assertEquals(Selector.Kind.EXTEND, s.kind(), "una caja nueva (chunk, trim) sigue en extend");
    }

    @Test
    void H2_CA3_y_CA9_poly_alto_de_los_puntos_y_tope_de_vertices() {
        s.kind(Selector.Kind.POLY);
        s.primary(0, 60, 0);
        s.secondary(10, 64, 0, 3);
        assertEquals("Point 3", s.secondary(5, 62, 8, 3));
        Region r = s.region();
        assertEquals(60, r.bounds().minY());
        assertEquals(64, r.bounds().maxY());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> s.secondary(1, 1, 1, 3));
        assertTrue(e.getMessage().contains("at most 3 points"));
        assertEquals("3 points: 0 0, 10 0, 5 8; y 60 to 64", s.describe());
        s.secondary(0, 70, 0, 3);
        assertEquals(70, s.region().bounds().maxY(), "el mismo (x, z) más alto solo cambia el alto");
    }

    @Test
    void H2_CA4_a_CA6_centro_y_radios() {
        s.kind(Selector.Kind.ELLIPSOID);
        assertEquals("Center", s.primary(0, 64, 0));
        s.secondary(3, 65, 0, 64);
        s.secondary(1, 64, -5, 64);
        Ellipsoid e = (Ellipsoid) s.region();
        assertEquals(3, e.rx);
        assertEquals(1, e.ry);
        assertEquals(5, e.rz);

        s.kind(Selector.Kind.SPHERE);
        s.primary(0, 64, 0);
        assertEquals("Radius 5", s.secondary(3, 68, 0, 64));
        assertEquals("Radius 2", s.secondary(1, 65, 1, 64), "la distancia redondeada hacia arriba (√3 → 2)");
        assertEquals("sphere", s.region().kind());

        s.kind(Selector.Kind.CYL);
        s.primary(0, 64, 0);
        assertEquals(1, s.region().volume(), "solo el centro");
        s.secondary(3, 70, -2, 64);
        Cylinder c = (Cylinder) s.region();
        assertEquals(3, c.rx);
        assertEquals(2, c.rz);
        assertEquals(64, c.minY);
        assertEquals(70, c.maxY);
        assertEquals("center 0 0, radii 3, 2; y 64 to 70", s.describe());
    }

    @Test
    void H2_CA7_convex_con_un_punto_ya_es_un_bloque() {
        s.kind(Selector.Kind.CONVEX);
        s.primary(1, 2, 3);
        assertEquals(1, s.region().volume());
        s.secondary(4, 2, 3, 64);
        assertEquals(4, s.region().volume());
        assertEquals("2 points", s.describe());
    }

    @Test
    void H2_CA8_la_magica_suma_con_el_clic_derecho() {
        PointSet a = new PointSet(new long[]{PointSet.pack(0, 0, 0), PointSet.pack(1, 0, 0)});
        PointSet b = new PointSet(new long[]{PointSet.pack(5, 0, 0)});
        s.magic(a, false);
        assertEquals(Selector.Kind.MAGIC, s.kind());
        s.magic(b, true);
        assertEquals(3, s.region().volume());
        s.magic(b, false);
        assertEquals(1, s.region().volume());
        assertThrows(IllegalStateException.class, () -> s.primary(0, 0, 0), "la mágica lee el mundo: la hace el comando");
    }

    @Test
    void H4_CA5_shift_mueve_cualquier_forma_y_alto_y_radios_por_forma() {
        s.kind(Selector.Kind.SPHERE);
        s.primary(0, 64, 0);
        s.secondary(4, 64, 0, 64);
        long v = s.region().volume();
        s.shift(10, -4, 0);
        Ellipsoid e = (Ellipsoid) s.region();
        assertEquals(10, e.cx);
        assertEquals(60, e.cy);
        assertEquals(v, e.volume());
        s.outset(2, false, false);
        assertEquals(6, ((Ellipsoid) s.region()).rx);
        assertThrows(IllegalArgumentException.class, () -> s.outset(-7, false, false), "achicar de más");
        assertThrows(IllegalStateException.class, () -> s.height(1, 1), "una esfera no tiene alto");
        s.outset(1, false, true);
        assertEquals(Selector.Kind.ELLIPSOID, s.kind(), "solo en vertical deja de ser esfera");
        assertEquals(7, ((Ellipsoid) s.region()).ry);

        s.kind(Selector.Kind.CYL);
        s.primary(0, 64, 0);
        s.secondary(2, 66, 0, 64);
        s.height(1, 3);
        assertEquals(63, s.region().bounds().minY());
        assertEquals(69, s.region().bounds().maxY());
        s.outset(1, true, false);
        assertEquals(3, ((Cylinder) s.region()).rx);
        assertEquals(63, s.region().bounds().minY(), "--horizontal no cambia el alto");

        s.kind(Selector.Kind.POLY);
        s.primary(0, 64, 0);
        s.secondary(5, 64, 5, 64);
        assertThrows(IllegalStateException.class, () -> s.outset(1, false, false), "poly solo en vertical");
        s.outset(2, false, true);
        assertEquals(62, s.region().bounds().minY());
        s.heightTo(-64, 319);
        assertEquals(384, s.region().bounds().height());

        s.kind(Selector.Kind.MAGIC);
        s.magic(new PointSet(new long[]{PointSet.pack(0, 0, 0)}), false);
        s.shift(0, 5, 0);
        assertTrue(s.region().contains(0, 5, 0));
        assertThrows(IllegalStateException.class, () -> s.outset(1, false, false));
    }
}
