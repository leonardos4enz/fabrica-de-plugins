package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.region.Box;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 029 H6: fixwater, fixlava y drain sobre la foto (en la 028 solo se habían probado en el servidor). */
class LiquidsTest {
    private final StateTable t = new StateTable();
    private final Snapshot snap = new Snapshot(Box.of(-12, -6, -12, 12, 6, 12));
    private final int air = id("air"), still = id("water"), lavaStill = id("lava");
    private final Liquids liquids = new Liquids(t, air, still, lavaStill);

    LiquidsTest() {
        for (int x = -12; x <= 12; x++) for (int y = -6; y <= 6; y++) for (int z = -12; z <= 12; z++) snap.put(x, y, z, id("stone"));
    }

    private int id(String s) {
        return t.intern(TestBlocks.REGISTRY.canonical("minecraft:" + s));
    }

    private Set<Long> set(long[] pts) {
        Set<Long> out = new HashSet<>();
        for (long p : pts) out.add(p);
        return out;
    }

    /** Lo que quedaría en cada punto: el cambio de pick sobre la foto. */
    private int after(long[] pts, Liquids.Kind kind, int x, int y, int z) {
        long k = PointsSource.pack(x, y, z);
        for (long p : pts) if (p == k) {
            int r = liquids.pick(kind).at(x, y, z, snap.get(x, y, z));
            return r == EditSource.KEEP ? snap.get(x, y, z) : r;
        }
        return snap.get(x, y, z);
    }

    @Test
    void H6_fixwater_deja_quieta_el_agua_que_corre_conectada() {
        // Un canal en y = 0 de x = 0..5: fuente, agua que corre y otra fuente; aparte, un charco que corre en x = 9.
        snap.put(0, 0, 0, still);
        for (int x = 1; x <= 4; x++) snap.put(x, 0, 0, id("water[level=" + x + "]"));
        snap.put(5, 0, 0, still);
        snap.put(9, 0, 0, id("water[level=3]"));
        // Una cascada por encima del nivel donde se la encontró: no se toca.
        snap.put(0, 1, 0, id("water[level=8]"));
        long[] pts = liquids.find(snap, Liquids.Kind.FIXWATER, 2, 0, 0, 10, false);
        Set<Long> s = set(pts);
        assertTrue(s.contains(PointsSource.pack(4, 0, 0)));
        assertFalse(s.contains(PointsSource.pack(9, 0, 0)), "otro charco, sin conexión");
        assertFalse(s.contains(PointsSource.pack(0, 1, 0)), "no sube del nivel encontrado");
        for (int x = 0; x <= 5; x++) assertEquals(still, after(pts, Liquids.Kind.FIXWATER, x, 0, 0), "x=" + x);
        assertEquals(id("water[level=8]"), after(pts, Liquids.Kind.FIXWATER, 0, 1, 0));
    }

    @Test
    void H6_fixlava_no_toca_el_agua() {
        snap.put(0, 0, 0, id("lava[level=2]"));
        snap.put(1, 0, 0, id("lava[level=4]"));
        snap.put(2, 0, 0, id("water[level=2]"));
        long[] pts = liquids.find(snap, Liquids.Kind.FIXLAVA, 0, 0, 0, 5, false);
        assertEquals(2, pts.length);
        assertEquals(lavaStill, after(pts, Liquids.Kind.FIXLAVA, 1, 0, 0));
        assertEquals(id("water[level=2]"), after(pts, Liquids.Kind.FIXLAVA, 2, 0, 0));
        assertEquals(0, liquids.find(snap, Liquids.Kind.FIXWATER, 9, 5, 9, 1, false).length, "nada cerca");
    }

    @Test
    void H6_drain_con_y_sin_lo_anegado() {
        snap.put(0, 0, 0, still);
        snap.put(1, 0, 0, id("water[level=1]"));
        snap.put(2, 0, 0, id("oak_stairs[waterlogged=true]"));
        snap.put(3, 0, 0, still);
        long[] dry = liquids.find(snap, Liquids.Kind.DRAIN, 0, 0, 0, 8, false);
        assertEquals(2, dry.length, "la escalera corta el paso sin --waterlogged");
        long[] wet = liquids.find(snap, Liquids.Kind.DRAIN, 0, 0, 0, 8, true);
        assertEquals(4, wet.length);
        assertEquals(air, after(wet, Liquids.Kind.DRAIN, 3, 0, 0));
        assertEquals(t.state(id("oak_stairs[waterlogged=false]")), t.state(after(wet, Liquids.Kind.DRAIN, 2, 0, 0)));
    }
}
