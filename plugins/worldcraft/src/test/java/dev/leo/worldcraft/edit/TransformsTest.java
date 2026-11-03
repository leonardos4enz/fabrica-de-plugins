package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.region.Box;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 041 H3 CA2–CA4: //gtransform y /transform (dónde caen los bloques) y //gsmask y /smask (qué se pone). */
class TransformsTest {
    static final int STONE = 7, AIR = 0;

    /** Dos bloques de piedra en (0,0,0) y (2,0,0). */
    static EditSource two() {
        return new PointsSource(new long[]{PointsSource.pack(0, 0, 0), PointsSource.pack(2, 0, 0)}, (x, y, z, c) -> STONE);
    }

    static Map<Long, Integer> run(EditSource s) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        int[] p = new int[3];
        while (s.next(p)) {
            int t = s.target(p[0], p[1], p[2], AIR);
            if (t != EditSource.KEEP) out.put(PointsSource.pack(p[0], p[1], p[2]), t);
        }
        return out;
    }

    static boolean at(Map<Long, Integer> m, int x, int y, int z) {
        return m.containsKey(PointsSource.pack(x, y, z));
    }

    @Test
    void H3_CA3_offset_mueve_todo_y_la_caja_tambien() throws ParseException {
        Transforms t = Transforms.parse("#offset[0][5][1]");
        EditSource s = new TransformSource(two(), t, 1L);
        Map<Long, Integer> m = run(s);
        assertEquals(2, m.size());
        assertTrue(at(m, 0, 5, 1) && at(m, 2, 5, 1));
        assertEquals(new Box(0, 5, 1, 2, 5, 1), s.bounds());
        assertEquals(STONE, m.get(PointsSource.pack(2, 5, 1)));
        assertEquals("#offset[0][5][1]", Transforms.parse("#offset:0,5,1").text(), "también con dos puntos");
    }

    @Test
    void H3_CA3_rotate_y_flip_alrededor_del_centro() throws ParseException {
        // Centro (1,0,0): rotar 90° (como el reloj, visto desde arriba) lleva (0,0,0) a (1,0,−1) y (2,0,0) a (1,0,1).
        Map<Long, Integer> r = run(new TransformSource(two(), Transforms.parse("#rotate[90]"), 1L));
        assertTrue(at(r, 1, 0, -1) && at(r, 1, 0, 1), r.keySet().toString());
        Map<Long, Integer> f = run(new TransformSource(
                new PointsSource(new long[]{PointsSource.pack(0, 0, 0), PointsSource.pack(1, 0, 0), PointsSource.pack(3, 0, 0)}, (x, y, z, c) -> x),
                Transforms.parse("#flip[x]"), 1L));
        assertEquals(3, f.get(PointsSource.pack(0, 0, 0)), "el bloque de x = 3 pasa a x = 0");
        assertThrows(ParseException.class, () -> Transforms.parse("#rotate[45]"));
    }

    @Test
    void H3_CA3_scale_y_spread() throws ParseException {
        EditSource one = new PointsSource(new long[]{PointsSource.pack(0, 0, 0)}, (x, y, z, c) -> STONE);
        TransformSource s = new TransformSource(one, Transforms.parse("#scale[2]"), 1L);
        assertEquals(8, s.volume(), "un bloque es un cubo de 2");
        assertEquals(8, run(s).size());
        Map<Long, Integer> sp = run(new TransformSource(two(), Transforms.parse("#spread[2]"), 5L));
        for (long k : sp.keySet()) {
            assertTrue(Math.abs(PointsSource.unpackY(k)) <= 2 && Math.abs(PointsSource.unpackZ(k)) <= 2);
        }
        assertEquals(sp.keySet(), run(new TransformSource(two(), Transforms.parse("#spread[2]"), 5L)).keySet(), "la semilla decide");
        Transforms both = Transforms.parse("#offset[1][0][0]   #scale[2]");
        assertEquals("#offset[1][0][0] #scale[2]", both.text());
        assertThrows(ParseException.class, () -> Transforms.parse("#scale[9]"), "1 a 4");
        ParseException e = assertThrows(ParseException.class, () -> Transforms.parse("#rotat[90]"));
        assertTrue(e.getMessage().contains("rotate"), "did you mean: " + e.getMessage());
    }

    @Test
    void H3_CA2_la_mascara_de_origen_deja_lo_que_no_cumple() {
        EditSource src = new PointsSource(new long[]{PointsSource.pack(0, 0, 0), PointsSource.pack(1, 0, 0)}, (x, y, z, c) -> x == 0 ? AIR : STONE);
        Map<Long, Integer> m = run(new SourceMaskedSource(src, st -> st != AIR));
        assertEquals(1, m.size(), "el aire que pondría se deja (como //gsmask !air)");
        assertEquals(STONE, m.get(PointsSource.pack(1, 0, 0)));
    }
}
