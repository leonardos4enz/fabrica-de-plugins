package dev.leo.worldcraft.formula;

import dev.leo.worldcraft.biome.Cells;
import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Cylinder;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 034 H2–H4: lo que calcula una fórmula sobre una selección (plan §1.1: Space y Shapes). */
class ShapesTest {
    static final List<String> XYZ = List.of("x", "y", "z");
    static final Shapes.Work FREE = new Shapes.Work(Long.MAX_VALUE);

    static Formula f(String text) throws FormulaException {
        return Formula.parse(text, XYZ, Formula.Limits.DEFAULTS);
    }

    static Set<Long> set(long[] pts) {
        Set<Long> out = new HashSet<>();
        for (long p : pts) out.add(p);
        return out;
    }

    static long p(int x, int y, int z) {
        return PointsSource.pack(x, y, z);
    }

    @Test
    void H2_CA1_coordenadas_de_menos_uno_a_uno_como_WorldEdit() {
        Box box = new Box(10, 0, 20, 20, 10, 30);
        Space sp = Space.of(Space.Mode.NORMAL, box, new int[3]);
        assertEquals(-1, sp.lx(10), 1e-12);
        assertEquals(1, sp.lx(20), 1e-12);
        assertEquals(0, sp.lz(25), 1e-12);
        assertEquals(20, sp.wx(1));
        assertEquals(15, sp.wx(0));
        Space flat = Space.of(Space.Mode.NORMAL, new Box(0, 5, 0, 4, 5, 4), new int[3]);
        assertEquals(0, flat.ly(5), 1e-12, "un eje de un bloque: unidad 1, no 0");
        Space raw = Space.of(Space.Mode.RAW, box, new int[3]);
        assertEquals(12, raw.lx(12), 1e-12);
        Space off = Space.of(Space.Mode.OFFSET, box, new int[]{12, 3, 22});
        assertEquals(-2, off.lx(10), 1e-12);
        assertEquals(-3, off.ly(0), 1e-12);
        Space center = Space.of(Space.Mode.CENTER, box, new int[3]);
        assertEquals(5, center.lx(20), 1e-12);
        assertEquals(Integer.MIN_VALUE, sp.wx(Double.NaN), "NaN no es un lugar");
    }

    @Test
    void H2_CA1_la_esfera_llena_la_seleccion() throws Exception {
        Box box = new Box(0, 0, 0, 10, 10, 10);
        long[] pts = Shapes.generate(box, f("x^2+y^2+z^2<1"), Space.of(Space.Mode.NORMAL, box, new int[3]), false, 1, FREE);
        Set<Long> s = set(pts);
        assertTrue(s.contains(p(5, 5, 5)));
        assertTrue(s.contains(p(1, 5, 5)), "cerca del borde, adentro");
        assertFalse(s.contains(p(0, 5, 5)), "en el borde (x = -1) no: < 1");
        assertFalse(s.contains(p(0, 0, 0)));
        // La misma cuenta a mano, con las coordenadas de −1 a 1 (centro 5, unidad 5).
        int n = 0;
        for (int x = 0; x <= 10; x++) for (int y = 0; y <= 10; y++) for (int z = 0; z <= 10; z++) {
            if (Math.pow((x - 5) / 5.0, 2) + Math.pow((y - 5) / 5.0, 2) + Math.pow((z - 5) / 5.0, 2) < 1) n++;
        }
        assertEquals(n, pts.length);
    }

    @Test
    void H2_CA1_raw_y_una_seleccion_que_no_es_caja() throws Exception {
        Cylinder cyl = new Cylinder(0, 0, 3, 3, 0, 4);
        long[] pts = Shapes.generate(cyl, f("y < 2"), Space.of(Space.Mode.RAW, cyl.bounds(), new int[3]), false, 1, FREE);
        for (long q : pts) {
            int x = PointsSource.unpackX(q), y = PointsSource.unpackY(q), z = PointsSource.unpackZ(q);
            assertTrue(y < 2 && cyl.contains(x, y, z));
        }
        assertEquals(cyl.volume() * 2 / 5, pts.length, "las dos capas de abajo del cilindro");
    }

    @Test
    void H2_CA2_hueca_solo_la_cascara() throws Exception {
        Box box = new Box(0, 0, 0, 10, 10, 10);
        Space sp = Space.of(Space.Mode.NORMAL, box, new int[3]);
        Set<Long> full = set(Shapes.generate(box, f("x^2+y^2+z^2<1"), sp, false, 1, FREE));
        Set<Long> shell = set(Shapes.generate(box, f("x^2+y^2+z^2<1"), sp, true, 1, FREE));
        assertTrue(full.containsAll(shell));
        assertFalse(shell.contains(p(5, 5, 5)), "el centro no");
        assertTrue(shell.contains(p(1, 5, 5)), "el borde sí");
        assertTrue(shell.size() < full.size() / 2);
        Set<Long> cube = set(Shapes.generate(box, f("1"), sp, true, 1, FREE));
        assertEquals(11 * 11 * 11 - 9 * 9 * 9, cube.size(), "lo de fuera de la selección cuenta como fuera: una caja hueca");
    }

    @Test
    void H3_CA1_deform_dice_de_donde_viene_cada_bloque() throws Exception {
        Box box = new Box(0, 0, 0, 4, 4, 4);
        Shapes.Mapping m = Shapes.deform(box, f("y -= 1"), Space.of(Space.Mode.RAW, box, new int[3]), 1, FREE);
        assertEquals(125, m.size(), "todos cambian de lugar");
        for (int i = 0; i < m.size(); i++) {
            long t = m.targets()[i], s = m.sources()[i];
            assertEquals(PointsSource.unpackX(t), PointsSource.unpackX(s));
            assertEquals(PointsSource.unpackY(t) - 1, PointsSource.unpackY(s));
        }
        assertEquals(new Box(0, -1, 0, 4, 3, 4), m.sourceBounds(), "también se lee lo de abajo de la selección");
        Shapes.Mapping none = Shapes.deform(box, f("x = x"), Space.of(Space.Mode.NORMAL, box, new int[3]), 1, FREE);
        assertEquals(0, none.size());
        assertNull(none.sourceBounds());
        Shapes.Mapping nan = Shapes.deform(box, f("y = 0/0"), Space.of(Space.Mode.NORMAL, box, new int[3]), 1, FREE);
        assertEquals(0, nan.size(), "un origen imposible deja el bloque");
        // Normalizado: y -= 0.5 en una caja de alto 5 (unidad 2) baja un bloque el origen.
        Shapes.Mapping half = Shapes.deform(box, f("y -= 0.5"), Space.of(Space.Mode.NORMAL, box, new int[3]), 1, FREE);
        assertEquals(PointsSource.unpackY(half.targets()[0]) - 1, PointsSource.unpackY(half.sources()[0]));
    }

    @Test
    void H4_CA1_celdas_en_su_centro_y_huecas() throws Exception {
        Box box = new Box(0, 0, 0, 15, 15, 15);
        long[] cells = Cells.of(box);
        assertEquals(64, cells.length);
        Space sp = Space.of(Space.Mode.NORMAL, box, new int[3]);
        long[] half = Shapes.cells(cells, f("x < 0"), sp, false, 1, FREE);
        assertEquals(32, half.length);
        for (long c : half) assertTrue(PointsSource.unpackX(c) < 8);
        long[] shell = Shapes.cells(cells, f("1"), sp, true, 1, FREE);
        assertEquals(64 - 8, shell.length, "las de adentro (2×2×2) no");
    }

    @Test
    void H1_CA4_el_plazo_corta_y_dice_hasta_donde_llego() throws Exception {
        Box box = new Box(0, 0, 0, 99, 99, 99);
        Shapes.Work w = new Shapes.Work(System.nanoTime() + 30_000_000L);
        Formula slow = Formula.parse("for (i = 0; i < 2000; i++) { a = sin(i) }; 1", XYZ, Formula.Limits.DEFAULTS);
        assertThrows(Formula.TimedOut.class, () -> Shapes.generate(box, slow, Space.of(Space.Mode.NORMAL, box, new int[3]), false, 1, w));
        assertTrue(w.evaluated > 0 && w.evaluated < box.volume(), "evaluó " + w.evaluated);
        Shapes.Work w2 = new Shapes.Work(Long.MAX_VALUE);
        Formula loop = Formula.parse("x > 0 ? 1 : 0; while (x > 0.5) { }", XYZ, new Formula.Limits(1000, 1000, 16));
        assertThrows(Formula.TooManySteps.class, () -> Shapes.generate(box, loop, Space.of(Space.Mode.NORMAL, box, new int[3]), false, 1, w2));
        assertTrue(w2.x > 49, "dice dónde: " + w2.x);
    }
}
