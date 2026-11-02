package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.TestBlocks.MemoryWorld;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.formula.Formula;
import dev.leo.worldcraft.formula.Shapes;
import dev.leo.worldcraft.formula.Space;
import dev.leo.worldcraft.region.Box;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 034 H3 CA2: deform pone en cada destino lo que había en su origen en la foto (con los datos de bloque). */
class MapSourceTest {
    final StateTable table = new StateTable();
    final MemoryWorld world = new MemoryWorld(table);

    int id(String s) {
        return table.intern(TestBlocks.REGISTRY.canonical("minecraft:" + s));
    }

    @Test
    void H3_CA2_la_foto_se_lee_antes_y_lo_movido_no_se_vuelve_a_mover() throws Exception {
        // Una columna: piedra en y = 0, tierra en y = 1, vidrio en y = 2; deform «y -= 1» (crudo) en y = 1..3.
        world.set(0, 0, 0, id("stone"));
        world.set(0, 1, 0, id("dirt"));
        world.set(0, 2, 0, id("glass"));
        world.tiles.put(MemoryWorld.pos(0, 0, 0), new byte[]{'C', 1});
        world.set(0, 3, 0, id("air"));
        Box sel = new Box(0, 1, 0, 0, 3, 0);
        Formula f = Formula.parse("y -= 1", List.of("x", "y", "z"), Formula.Limits.DEFAULTS);
        Shapes.Mapping m = Shapes.deform(sel, f, Space.of(Space.Mode.RAW, sel, new int[3]), 1, new Shapes.Work(Long.MAX_VALUE));
        Snapshot snap = new Snapshot(m.sourceBounds());
        for (int y = 0; y <= 2; y++) snap.take(0, y, 0, world.get(0, y, 0), world.tile(0, y, 0));
        MapSource src = new MapSource(m.targets(), m.sources(), snap, id("air"));
        assertEquals(3, src.volume());
        EditJob j = new EditJob(1, new UUID(0, 9), "deform", world, src.fresh(), EditJob.Mode.APPLY, new EditJob.Sink() {
            @Override
            public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
            }
        }, null, Long.MAX_VALUE, System::nanoTime);
        EditTest.run(j);
        assertEquals(id("stone"), world.get(0, 1, 0), "subió la piedra");
        assertEquals(id("dirt"), world.get(0, 2, 0), "y la tierra, la de antes (no la piedra recién movida)");
        assertEquals(id("glass"), world.get(0, 3, 0));
        assertArrayEquals(new byte[]{'C', 1}, world.tile(0, 1, 0), "el cofre viaja con su contenido");
        assertEquals(id("stone"), world.get(0, 0, 0), "lo de fuera de la selección solo se lee");
    }

    @Test
    void H3_CA1_sin_foto_es_aire() {
        long[] t = {dev.leo.worldcraft.edit.PointsSource.pack(1, 2, 3)}, s = {dev.leo.worldcraft.edit.PointsSource.pack(1, 5000 % 2048, 3)};
        MapSource src = new MapSource(t, s, null, id("air"));
        int[] p = new int[3];
        assertTrue(src.next(p));
        assertEquals(id("air"), src.target(p[0], p[1], p[2], id("stone")));
        assertEquals(new Box(1, 2, 3, 1, 2, 3), src.bounds());
    }
}
