package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.TestBlocks.MemoryWorld;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.pattern.Parser;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Direction;
import dev.leo.worldcraft.region.Line;
import dev.leo.worldcraft.region.Shape;
import dev.leo.worldcraft.region.Solid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 028 H1–H3: las fuentes nuevas sobre el mundo en memoria (plan §1.1–§1.3). */
class OpsTest {
    static final UUID ANA = new UUID(1, 1);
    final StateTable table = new StateTable();
    final MemoryWorld world = new MemoryWorld(table);
    final Parser parser = new Parser(TestBlocks.REGISTRY, Map.of(), Map.of());
    final IntPredicate air = s -> table.material(s).endsWith("air");

    EditJob apply(EditSource src) {
        EditJob j = new EditJob(1, ANA, "test", world, src, EditJob.Mode.APPLY, new EditJob.Sink() {
            @Override
            public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
            }
        }, null, Long.MAX_VALUE, System::nanoTime);
        EditTest.run(j);
        return j;
    }

    void read(EditSource src, EditJob.Capture into) {
        EditJob j = new EditJob(2, ANA, "read", world, src, EditJob.Mode.READ, new EditJob.Sink() {
            @Override
            public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
            }
        }, into, Long.MAX_VALUE, System::nanoTime);
        EditTest.run(j);
    }

    Snapshot snapshot(Box box) {
        Snapshot s = new Snapshot(box);
        read(new RegionSource(box, Shape.FILL, null, null, 0), s);
        return s;
    }

    Pattern.Compiled pattern(String p) throws Exception {
        return parser.pattern(p).compile(table);
    }

    int id(String s) {
        return table.intern(s);
    }

    @Test
    void H1_forma_puesta_bloque_por_bloque_en_orden_de_chunks() throws Exception {
        Solid s = Solid.sphere(8, 40, 8, 6, 6, 6).hollow(1, false);
        EditJob j = apply(new SolidSource(s, pattern("glass"), 1));
        assertEquals(s.volume(), j.visited());
        assertEquals(s.volume(), j.changed());
        assertEquals("minecraft:glass", world.at(14, 40, 8));
        assertEquals("minecraft:air", world.at(8, 40, 8), "hueca");
        assertEquals(0, world.loaded.size());
    }

    @Test
    void H2_CA1_stack_repite_sin_aire_y_con_aire() throws Exception {
        Box box = Box.of(0, 10, 0, 2, 11, 1);
        world.put(0, 10, 0, "minecraft:stone_bricks");
        world.put(2, 11, 1, "minecraft:chest");
        world.tiles.put(MemoryWorld.pos(2, 11, 1), new byte[]{'C', 1});
        world.put(10, 10, 0, "minecraft:dirt"); // en la segunda copia, donde la foto tiene aire
        Snapshot snap = snapshot(box);
        StackSource src = new StackSource(snap, Direction.EAST, 3, false, air);
        assertEquals(Box.of(3, 10, 0, 11, 11, 1), src.bounds());
        assertEquals(3 * box.volume(), src.volume());
        apply(src);
        for (int k = 1; k <= 3; k++) {
            assertEquals("minecraft:stone_bricks", world.at(3 * k, 10, 0));
            assertEquals("minecraft:chest", world.at(2 + 3 * k, 11, 1));
            assertArrayEquals(new byte[]{'C', 1}, world.tiles.get(MemoryWorld.pos(2 + 3 * k, 11, 1)), "el cofre con lo de adentro");
        }
        assertEquals("minecraft:dirt", world.at(10, 10, 0), "el aire copiado no borra");
        apply(new StackSource(snap, Direction.EAST, 3, true, air));
        assertEquals("minecraft:air", world.at(10, 10, 0), "con --air sí");
        assertEquals(Box.of(0, 10, -6, 2, 11, -5), StackSource.last(box, Direction.NORTH, 3));
    }

    @Test
    void H2_CA2_move_lejos_solo_visita_las_dos_cajas() throws Exception {
        Box box = Box.of(0, 10, 0, 1, 10, 1);
        world.put(0, 10, 0, "minecraft:gold_block");
        world.put(1, 10, 1, "minecraft:oak_sign");
        world.tiles.put(MemoryWorld.pos(1, 10, 1), new byte[]{'S', 2});
        Snapshot snap = snapshot(box);
        MoveSource src = new MoveSource(snap, 500, 0, 0, pattern("air"), 1);
        assertEquals(8, src.volume());
        assertEquals(2, src.chunks().length, "el chunk de origen y el de destino, nada en medio");
        EditJob j = apply(src);
        assertEquals(8, j.visited());
        assertEquals("minecraft:gold_block", world.at(500, 10, 0));
        assertEquals("minecraft:oak_sign", world.at(501, 10, 1));
        assertArrayEquals(new byte[]{'S', 2}, world.tiles.get(MemoryWorld.pos(501, 10, 1)));
        assertEquals("minecraft:air", world.at(0, 10, 0), "lo que queda atrás, vacío");
    }

    @Test
    void H2_CA2_move_que_se_pisa_con_relleno() throws Exception {
        Box box = Box.of(0, 10, 0, 3, 10, 0);
        for (int x = 0; x <= 3; x++) world.put(x, 10, 0, "minecraft:white_wool");
        world.put(0, 10, 0, "minecraft:red_wool");
        MoveSource src = new MoveSource(snapshot(box), 2, 0, 0, pattern("glass"), 1);
        assertEquals(6, src.volume(), "4 de destino + 2 que quedan atrás");
        apply(src);
        assertEquals("minecraft:red_wool", world.at(2, 10, 0));
        assertEquals("minecraft:white_wool", world.at(5, 10, 0));
        assertEquals("minecraft:glass", world.at(0, 10, 0));
        assertEquals("minecraft:glass", world.at(1, 10, 0));
    }

    @Test
    void H2_CA6_hollow_vacia_lo_cerrado_y_deja_la_cascara() throws Exception {
        Box cube = Box.of(0, 10, 0, 6, 16, 6);
        apply(new RegionSource(cube, Shape.FILL, null, pattern("stone"), 1));
        Box sel = Box.of(-1, 9, -1, 7, 17, 7);
        long[] inside = Flood.interior(snapshot(sel), air, 1);
        assertEquals(5L * 5 * 5, inside.length);
        apply(new PointsSource(inside, (x, y, z, c) -> table.intern("minecraft:air")));
        assertEquals("minecraft:air", world.at(3, 13, 3));
        assertEquals("minecraft:stone", world.at(0, 13, 3));
        assertEquals(27, Flood.interior(snapshot(sel), air, 2).length, "con grosor 2, lo de más adentro: un cubo de 3");
    }

    @Test
    void H2_CA6_hollow_no_vacia_lo_abierto() throws Exception {
        // Una U abierta arriba: el aire de adentro está conectado con el borde, no hay interior.
        Box u = Box.of(0, 10, 0, 4, 12, 0);
        apply(new RegionSource(u, Shape.FILL, null, pattern("stone"), 1));
        world.put(2, 11, 0, "minecraft:air");
        world.put(2, 12, 0, "minecraft:air");
        assertEquals(0, Flood.interior(snapshot(u), air, 1).length);
    }

    @Test
    void H2_CA6_line_y_center() throws Exception {
        long[] pts = Line.points(0, 20, 0, 6, 23, 9, 0, PointsSource::pack);
        apply(new PointsSource(pts, (x, y, z, c) -> id("minecraft:glass")));
        assertEquals("minecraft:glass", world.at(0, 20, 0));
        assertEquals("minecraft:glass", world.at(6, 23, 9));
        assertEquals(10, pts.length);
    }

    @Test
    void H2_CA4_naturalize_por_capas_solo_en_lo_natural() throws Exception {
        Box sel = Box.of(0, -10, 0, 1, 5, 0);
        world.put(0, 5, 0, "minecraft:oak_planks"); // una construcción encima: no se toca
        world.put(1, -2, 0, "minecraft:diamond_ore");
        int grass = id("minecraft:grass_block"), dirt = id("minecraft:dirt"), stone = id("minecraft:stone");
        IntPredicate natural = s -> s == grass || s == dirt || s == stone;
        apply(new ColumnEdit(sel, (x, z) -> new int[]{sel.minY(), sel.maxY()}, ColumnEdit.naturalize(natural, grass, dirt, stone)));
        assertEquals("minecraft:oak_planks", world.at(0, 5, 0));
        assertEquals("minecraft:grass_block", world.at(0, -1, 0), "la piedra de arriba pasa a pasto");
        assertEquals("minecraft:dirt", world.at(0, -2, 0));
        assertEquals("minecraft:dirt", world.at(0, -4, 0));
        assertEquals("minecraft:stone", world.at(0, -5, 0));
        assertEquals("minecraft:diamond_ore", world.at(1, -2, 0), "un mineral no es natural");
        assertEquals("minecraft:dirt", world.at(1, -3, 0), "la profundidad sigue debajo del mineral");
    }

    @Test
    void H2_CA3_overlay_encima_del_suelo_no_de_un_tronco() throws Exception {
        Box sel = Box.of(0, -3, 0, 2, 10, 0);
        world.put(1, 0, 0, "minecraft:oak_log");
        world.put(1, 1, 0, "minecraft:oak_log");
        world.put(2, 0, 0, "minecraft:poppy");
        int log = id("minecraft:oak_log"), poppy = id("minecraft:poppy");
        IntPredicate skip = s -> air.test(s) || s == poppy || s == log;
        TopScan scan = new TopScan(sel, skip.negate(), (x, z) -> true);
        read(scan, scan);
        assertEquals(-1, scan.top(0, 0));
        assertEquals(-1, scan.top(1, 0), "el tronco no es la cima");
        IntPredicate open = s -> air.test(s) || s == poppy;
        apply(new ColumnEdit(sel, ColumnEdit.aboveTop(scan, 1), ColumnEdit.overlay(open, pattern("sand"), 1)));
        assertEquals("minecraft:sand", world.at(0, 0, 0));
        assertEquals("minecraft:oak_log", world.at(1, 0, 0), "no reemplaza el tronco");
        assertEquals("minecraft:sand", world.at(2, 0, 0), "la flor sí");
    }

    @Test
    void H3_CA4_snow_thaw_y_green() throws Exception {
        Box sel = Box.of(0, -3, 0, 2, 5, 0);
        world.put(1, -1, 0, "minecraft:water");
        world.put(2, -1, 0, "minecraft:dirt");
        int water = id("minecraft:water"), ice = id("minecraft:ice"), snow = id("minecraft:snow_block");
        TopScan scan = new TopScan(sel, air.negate(), (x, z) -> true);
        read(scan, scan);
        apply(new ColumnEdit(sel, ColumnEdit.aboveTop(scan, 0), ColumnEdit.snow(scan, s -> s == water, s -> true, air, ice, snow)));
        assertEquals("minecraft:snow_block", world.at(0, 0, 0));
        assertEquals("minecraft:ice", world.at(1, -1, 0));
        assertEquals("minecraft:air", world.at(1, 0, 0), "sobre el hielo nuevo no nieva");
        apply(new ColumnEdit(sel, (x, z) -> new int[]{sel.minY(), sel.maxY()}, ColumnEdit.firstSolid(air,
                s -> s == snow ? table.intern("minecraft:air") : s == ice ? water : s)));
        assertEquals("minecraft:air", world.at(0, 0, 0));
        assertEquals("minecraft:water", world.at(1, -1, 0));
        int dirt = id("minecraft:dirt");
        apply(new ColumnEdit(sel, (x, z) -> new int[]{sel.minY(), sel.maxY()}, ColumnEdit.firstSolid(air,
                s -> s == dirt ? id("minecraft:grass_block") : s)));
        assertEquals("minecraft:grass_block", world.at(2, -1, 0));
    }

    @Test
    void H3_CA1_drain_solo_lo_conectado() throws Exception {
        // Un lago de 3×1×3 y un charco aparte.
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) world.put(x, -1, z, "minecraft:water");
        world.put(6, -1, 0, "minecraft:water");
        world.put(4, -1, 0, "minecraft:oak_stairs[waterlogged=true]");
        Box box = Box.of(-5, -6, -5, 9, 4, 5);
        Snapshot snap = snapshot(box);
        int water = id("minecraft:water");
        Flood.Bound sphere = (x, y, z) -> (x - 1) * (x - 1) + y * y + (z - 1) * (z - 1) <= 64;
        int[] start = Flood.nearest(snap, 1, 0, 1, sphere, s -> s == water);
        assertArrayEquals(new int[]{1, -1, 1}, start);
        long[] lake = Flood.connected(snap, start[0], start[1], start[2], sphere, s -> s == water);
        assertEquals(9, lake.length);
        apply(new PointsSource(lake, (x, y, z, c) -> c == water ? table.intern("minecraft:air") : EditSource.KEEP));
        assertEquals("minecraft:air", world.at(1, -1, 1));
        assertEquals("minecraft:water", world.at(6, -1, 0), "el charco aparte queda");
        // Con lo anegado: la escalera une el lago con el charco.
        world.put(3, -1, 0, "minecraft:water");
        for (int x = 0; x < 3; x++) world.put(x, -1, 0, "minecraft:water");
        world.put(5, -1, 0, "minecraft:water");
        Snapshot again = snapshot(box);
        IntPredicate wet = s -> s == water || "true".equals(table.properties(s).get("waterlogged"));
        long[] all = Flood.connected(again, 0, -1, 0, sphere, wet);
        assertEquals(7, all.length, "0..6 en la fila, con la escalera");
    }

    @Test
    void H3_CA2_fill_no_sube_de_los_pies() throws Exception {
        // Un pozo de 2×3×1 en la piedra.
        for (int y = -3; y <= -1; y++) for (int x = 0; x <= 1; x++) world.put(x, y, 0, "minecraft:air");
        Box box = Box.of(-4, -6, -4, 5, 3, 4);
        Snapshot snap = snapshot(box);
        int feetY = -1;
        Flood.Bound b = (x, y, z) -> y <= feetY && Math.hypot(x, z) <= 4.5;
        long[] hole = Flood.connected(snap, 0, feetY, 0, b, air);
        assertEquals(6, hole.length);
        List<Long> sorted = new ArrayList<>();
        for (long p : hole) sorted.add(p);
        assertTrue(sorted.contains(PointsSource.pack(1, -3, 0)));
        assertFalse(sorted.contains(PointsSource.pack(0, 0, 0)), "nada por encima de los pies");
    }

    @Test
    void puntos_en_orden_de_chunks_sin_repetidos_y_con_negativos() {
        long[] pts = {PointsSource.pack(-17, 5, 3), PointsSource.pack(20, -64, -33), PointsSource.pack(-17, 5, 3),
                PointsSource.pack(0, 319, 0), PointsSource.pack(1, 2, 3)};
        PointsSource src = new PointsSource(pts, (x, y, z, c) -> EditSource.KEEP);
        assertEquals(4, src.volume());
        int[] p = new int[3];
        List<String> seen = new ArrayList<>();
        while (src.next(p)) seen.add(p[0] + "," + p[1] + "," + p[2]);
        assertEquals(List.of("-17,5,3", "0,319,0", "1,2,3", "20,-64,-33"), seen);
        assertEquals(3, src.chunks().length);
        assertEquals(new Box(-17, -64, -33, 20, 319, 3), src.bounds());
    }
}
