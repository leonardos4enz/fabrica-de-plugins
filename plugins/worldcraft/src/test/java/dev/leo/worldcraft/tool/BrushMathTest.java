package dev.leo.worldcraft.tool;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.TestBlocks.MemoryWorld;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.edit.EditJob;
import dev.leo.worldcraft.edit.EditSource;
import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.edit.RegionSource;
import dev.leo.worldcraft.edit.Snapshot;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Shape;
import dev.leo.worldcraft.region.Solid;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 032 H3–H6: lo que calculan los pinceles y herramientas que miran el mundo (plan §1.1), sobre el mundo en memoria. */
class BrushMathTest {
    final StateTable table = new StateTable();
    final MemoryWorld world = new MemoryWorld(table);
    final IntPredicate air = s -> table.material(s).endsWith("air");
    final IntPredicate ground = air.negate();

    int id(String s) {
        return table.intern("minecraft:" + s);
    }

    void put(int x, int y, int z, String s) {
        world.blocks.put(MemoryWorld.pos(x, y, z), id(s));
    }

    String at(int x, int y, int z) {
        Integer s = world.blocks.get(MemoryWorld.pos(x, y, z));
        return s == null ? "air" : table.material(s).replace("minecraft:", "");
    }

    /** Un suelo de piedra en y = 0 de -20..20 (y aire encima). */
    void floor() {
        for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) put(x, 0, z, "stone");
    }

    Snapshot snapshot(Box box) {
        for (int x = box.minX(); x <= box.maxX(); x++)
            for (int y = box.minY(); y <= box.maxY(); y++)
                for (int z = box.minZ(); z <= box.maxZ(); z++) world.blocks.putIfAbsent(MemoryWorld.pos(x, y, z), world.air);
        Snapshot s = new Snapshot(box);
        EditJob j = new EditJob(2, new UUID(1, 1), "read", world, new RegionSource(box, Shape.FILL, null, null, 0), EditJob.Mode.READ,
                new EditJob.Sink() {
                    @Override
                    public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
                    }
                }, s, Long.MAX_VALUE, System::nanoTime);
        run(j);
        return s;
    }

    void apply(EditSource src) {
        EditJob j = new EditJob(1, new UUID(1, 1), "test", world, src, EditJob.Mode.APPLY, new EditJob.Sink() {
            @Override
            public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
            }
        }, null, Long.MAX_VALUE, System::nanoTime);
        run(j);
    }

    static void run(EditJob job) {
        for (int i = 0; i < 1_000_000 && !job.step(Long.MAX_VALUE, 37); i++) {
            // sigue
        }
        assertNotEquals(EditJob.State.RUNNING, job.state());
    }

    static Set<Long> set(long[] pts) {
        Set<Long> out = new HashSet<>();
        for (long p : pts) out.add(p);
        return out;
    }

    @Test
    void H3_CA1_splatter_solo_la_superficie_y_ralea_hacia_el_borde() {
        floor();
        put(0, 1, 0, "stone"); // un bloque encima: tapa el del suelo
        Snapshot snap = snapshot(new Box(-6, -5, -6, 6, 6, 6));
        long[] all = BrushMath.splatter(snap, 0, 0, 0, 5, 1, ground, air, 7);
        for (long p : all) {
            int x = PointsSource.unpackX(p), y = PointsSource.unpackY(p), z = PointsSource.unpackZ(p);
            assertTrue(y == 0 || (x == 0 && y == 1 && z == 0), "solo la superficie");
            assertFalse(x == 0 && y == 0 && z == 0, "el tapado no es superficie");
        }
        assertArrayEquals(all, BrushMath.splatter(snap, 0, 0, 0, 5, 1, ground, air, 7), "la misma semilla da lo mismo");
        long sparse = BrushMath.splatter(snap, 0, 0, 0, 5, 10, ground, air, 7).length;
        assertTrue(sparse < all.length, "decay 10 deja menos: " + sparse + " de " + all.length);
    }

    @Test
    void H3_CA2_scatter_separadas_y_encima_con_overlay() {
        floor();
        Snapshot snap = snapshot(new Box(-8, -7, -8, 8, 8, 8));
        long[] pts = BrushMath.scatter(snap, 0, 0, 0, 6, 8, 3, true, ground, air, 11);
        assertEquals(8, pts.length);
        for (int i = 0; i < pts.length; i++) {
            assertEquals(1, PointsSource.unpackY(pts[i]), "encima del suelo");
            for (int j = 0; j < i; j++) {
                int dx = PointsSource.unpackX(pts[i]) - PointsSource.unpackX(pts[j]), dz = PointsSource.unpackZ(pts[i]) - PointsSource.unpackZ(pts[j]);
                assertTrue(dx * dx + dz * dz >= 9, "al menos 3 de distancia");
            }
        }
        assertEquals(0, PointsSource.unpackY(BrushMath.scatter(snap, 0, 0, 0, 6, 1, 1, false, ground, air, 11)[0]), "sin overlay, la cima misma");
    }

    @Test
    void H3_CA3_rock_redonda_con_sphericity_100_y_deformada_con_amplitud() {
        Solid sphere = Solid.sphere(0, 0, 0, 5, 5, 5);
        long[] round = BrushMath.rock(0, 0, 0, 5, 100, 30, 0, 3);
        assertEquals(sphere.volume(), round.length, "sin amplitud es la esfera de /wc sphere");
        for (long p : round) assertTrue(sphere.contains(PointsSource.unpackX(p), PointsSource.unpackY(p), PointsSource.unpackZ(p)));
        long[] rough = BrushMath.rock(0, 0, 0, 5, 60, 40, 80, 3);
        assertNotEquals(set(round), set(rough));
        for (long p : rough) assertTrue(Math.abs(PointsSource.unpackX(p)) <= 9 && Math.abs(PointsSource.unpackY(p)) <= 9);
        assertArrayEquals(rough, BrushMath.rock(0, 0, 0, 5, 60, 40, 80, 3));
    }

    @Test
    void H4_CA1_gravity_hace_caer_y_lo_fijo_hace_de_piso_por_el_motor() {
        floor();
        put(0, 5, 0, "sand");
        put(0, 7, 0, "dirt");
        put(2, 4, 0, "chest");
        put(2, 6, 0, "glass");
        Snapshot snap = snapshot(new Box(-3, 0, -3, 3, 8, 3));
        IntPredicate fixed = s -> table.material(s).equals("minecraft:chest");
        Map<Long, Integer> ch = BrushMath.gravity(snap, 0, 0, 3, 0, 8, air, fixed, world.air);
        apply(new PointsSource(ch.keySet().stream().mapToLong(Long::longValue).toArray(), (x, y, z, c) -> ch.get(PointsSource.pack(x, y, z))));
        assertEquals("sand", at(0, 1, 0));
        assertEquals("dirt", at(0, 2, 0));
        assertEquals("air", at(0, 5, 0));
        assertEquals("air", at(0, 7, 0));
        assertEquals("chest", at(2, 4, 0), "el cofre no se mueve");
        assertEquals("glass", at(2, 5, 0), "se apoya sobre el cofre");
        assertTrue(BrushMath.gravity(snapshot(new Box(-3, 0, -3, 3, 8, 3)), 0, 0, 3, 0, 8, air, fixed, world.air).isEmpty(),
                "una segunda pasada no cambia nada");
    }

    @Test
    void H5_CA5_deltree_flotante_plantado_y_muy_grande() {
        floor();
        IntPredicate tree = s -> table.material(s).endsWith("_log") || table.material(s).endsWith("_leaves");
        IntPredicate free = s -> air.test(s) || table.material(s).equals("minecraft:short_grass");
        // Un árbol plantado en x = 0 y uno flotante en x = 6 (troncos de y = 3 a 5 y hojas arriba).
        for (int y = 1; y <= 4; y++) put(0, y, 0, "oak_log");
        put(0, 5, 0, "oak_leaves");
        for (int y = 3; y <= 5; y++) put(6, y, 0, "oak_log");
        put(6, 6, 0, "oak_leaves");
        put(7, 6, 0, "oak_leaves");
        put(6, 2, 0, "short_grass");
        Snapshot snap = snapshot(new Box(-4, 0, -4, 10, 10, 4));
        BrushMath.Tree t = BrushMath.deltree(snap, 6, 4, 0, tree, free);
        assertEquals(BrushMath.TreeStatus.OK, t.status());
        assertEquals(5, t.points().length);
        assertEquals(BrushMath.TreeStatus.GROUNDED, BrushMath.deltree(snap, 0, 3, 0, tree, free).status());
        assertEquals(BrushMath.TreeStatus.NOT_TREE, BrushMath.deltree(snap, 3, 0, 0, tree, free).status());
        Snapshot small = snapshot(new Box(4, 3, -1, 8, 5, 1));
        assertEquals(BrushMath.TreeStatus.TOO_BIG, BrushMath.deltree(small, 6, 4, 0, tree, free).status(), "las hojas siguen fuera de la foto");
    }

    @Test
    void H6_CA1_superpico_area_y_recursivo_del_mismo_tipo_sin_never_break() {
        floor();
        put(0, 0, 0, "bedrock");
        put(1, 1, 0, "stone");
        Snapshot snap = snapshot(new Box(-3, -2, -3, 3, 2, 3));
        int stone = id("stone");
        IntPredicate same = s -> s == stone;
        long[] area = BrushMath.cube(snap, 1, 0, 0, 1, same);
        assertEquals(9 - 1 + 1, area.length, "el cubo de 3×3×3: 8 del suelo (sin la roca madre) y la de encima");
        assertFalse(set(area).contains(PointsSource.pack(0, 0, 0)));
    }
}
