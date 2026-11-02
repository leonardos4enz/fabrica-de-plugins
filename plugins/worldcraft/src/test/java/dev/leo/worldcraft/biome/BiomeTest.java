package dev.leo.worldcraft.biome;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.edit.EditJob;
import dev.leo.worldcraft.edit.EmptySource;
import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.edit.Transform;
import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.edit.UndoSource;
import dev.leo.worldcraft.entity.EntityStage;
import dev.leo.worldcraft.history.Journal;
import dev.leo.worldcraft.history.JournalSink;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Ellipsoid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 033: celdas de bioma, setbiome por el motor con diario, deshacer seguro y rehacer; biomas en el portapapeles. */
class BiomeTest {
    static final UUID ANA = new UUID(1, 1);
    final StateTable table = new StateTable();
    final TestBlocks.MemoryWorld world = new TestBlocks.MemoryWorld(table);
    final TestBlocks.MemoryBiomes biomes = new TestBlocks.MemoryBiomes();
    @TempDir
    Path dir;

    static void run(EditJob job) {
        for (int i = 0; i < 100_000 && !job.step(Long.MAX_VALUE, 7); i++) {
            // sigue
        }
        assertEquals(EditJob.State.DONE, job.state());
    }

    /** setbiome: una obra sin bloques con la etapa de biomas, como la arma Edits. */
    EditJob setbiome(Box box, long[] cells, String biome, Path journal) {
        JournalSink sink = new JournalSink(journal, table, Runnable::run, e -> fail(e));
        BiomeStage st = BiomeStage.apply(biomes, cells, (i, x, y, z, c) -> biome, sink);
        EditJob j = new EditJob(1, ANA, "setbiome", world, new EmptySource(box), EditJob.Mode.APPLY, sink, null,
                Long.MAX_VALUE, System::nanoTime).stages(null, EntityStage.chain(null, st));
        run(j);
        return j;
    }

    EditJob undo(Path journal, boolean redo, Box box) throws Exception {
        CompletableFuture<Journal.Data> data = CompletableFuture.completedFuture(Journal.read(journal));
        BiomeStage st = BiomeStage.undo(biomes, () -> data.join(), redo);
        EditJob j = new EditJob(2, ANA, "undo", world, new UndoSource(data, table, redo, box), EditJob.Mode.APPLY,
                new EditJob.Sink() {
                    @Override
                    public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
                    }
                }, null, Long.MAX_VALUE, System::nanoTime).stages(null, st);
        run(j);
        return j;
    }

    @Test
    void H2_CA1_las_celdas_de_una_caja_y_de_una_esfera() {
        long[] box = Cells.of(Box.of(0, 0, 0, 5, 5, 5));
        assertEquals(8, box.length, "x, y y z en {0, 4}");
        long[] neg = Cells.of(Box.of(-1, -61, -1, 0, -61, 0));
        assertEquals(4, neg.length);
        assertEquals(-64, PointsSource.unpackY(neg[0]), "la esquina mínima de -61 es -64");
        assertEquals(-4, PointsSource.unpackX(neg[0]));
        // Una esfera: las celdas son justo las que tienen algún bloque adentro (contado a mano).
        Ellipsoid e = new Ellipsoid(9, 9, 9, 2, 2, 2, true);
        java.util.Set<Long> want = new java.util.TreeSet<>();
        Box eb = e.bounds();
        for (int x = eb.minX(); x <= eb.maxX(); x++) for (int y = eb.minY(); y <= eb.maxY(); y++) for (int z = eb.minZ(); z <= eb.maxZ(); z++) {
            if (e.contains(x, y, z)) want.add(PointsSource.pack(x & ~3, y & ~3, z & ~3));
        }
        java.util.Set<Long> got = new java.util.TreeSet<>();
        for (long c : Cells.of(e)) got.add(c);
        assertEquals(want, got);
        assertTrue(got.size() < Cells.of(eb).length, "menos que las de su caja");
        // En orden de chunks: todo el chunk 0 antes del 1.
        long[] two = Cells.of(Box.of(12, 0, 0, 19, 0, 0));
        assertEquals(List.of(12, 16), List.of(PointsSource.unpackX(two[0]), PointsSource.unpackX(two[1])));
    }

    @Test
    void H2_CA2_setbiome_por_el_motor_se_deshace_seguro_y_se_rehace() throws Exception {
        Box box = Box.of(0, 0, 0, 7, 3, 3);
        Path j = dir.resolve("1.wcj");
        setbiome(box, Cells.of(box), "minecraft:desert", j);
        assertEquals("minecraft:desert", biomes.get(0, 0, 0));
        assertEquals("minecraft:desert", biomes.get(7, 3, 3));
        assertEquals("minecraft:plains", biomes.get(8, 0, 0));
        assertEquals(2, biomes.sets);
        assertTrue(biomes.refreshed.contains(dev.leo.worldcraft.region.ChunkOrder.pack(0, 0)), "el chunk se reenvía");
        Journal.Data d = Journal.read(j);
        assertEquals(2, d.biomes);
        assertEquals(0, d.size, "sin bloques");

        // Otro cambia una celda después: deshacer la deja y la cuenta.
        biomes.set(4, 0, 0, "minecraft:forest");
        BiomeStage[] seen = new BiomeStage[1];
        CompletableFuture<Journal.Data> data = CompletableFuture.completedFuture(Journal.read(j));
        seen[0] = BiomeStage.undo(biomes, data::join, false);
        while (!seen[0].step(Long.MAX_VALUE, System::nanoTime)) {
            // sigue
        }
        assertEquals("minecraft:plains", biomes.get(0, 0, 0));
        assertEquals("minecraft:forest", biomes.get(4, 0, 0), "lo de otro se deja");
        assertEquals(1, seen[0].skipped());
        assertEquals(1, seen[0].done());

        // Rehacer vuelve a poner el desierto donde estaba el llano.
        biomes.set(4, 0, 0, "minecraft:plains");
        undo(j, true, box);
        assertEquals("minecraft:desert", biomes.get(0, 0, 0));
        assertEquals("minecraft:desert", biomes.get(4, 0, 0));
    }

    @Test
    void H2_CA2_un_diario_sin_biomas_se_lee_igual() throws Exception {
        Path j = dir.resolve("2.wcj");
        Journal journal = Journal.create(j);
        journal.append(List.of(new Journal.Change(1, 2, 3, "minecraft:air", "minecraft:stone", null, null)));
        journal.close();
        Journal.Data d = Journal.read(j);
        assertEquals(1, d.size);
        assertEquals(0, d.biomes);
        assertTrue(Files.size(j) > 4);
    }

    @Test
    void H4_CA1_CA2_el_portapapeles_lleva_los_biomas_y_giran_con_su_bloque() {
        // Copia 3×1×1 desde (0,0,0) con el jugador en (0,0,0): el bloque x=2 es desierto.
        Box box = Box.of(0, 0, 0, 2, 0, 0);
        Clipboard c = new Clipboard(box, 0, 0, 0);
        for (int x = 0; x <= 2; x++) c.put(box, x, 0, 0, world.stone, null);
        assertFalse(c.hasBiomes());
        c.putBiome(box, 0, 0, 0, "minecraft:plains");
        c.putBiome(box, 1, 0, 0, "minecraft:plains");
        c.putBiome(box, 2, 0, 0, "minecraft:desert");
        assertTrue(c.hasBiomes());
        assertEquals("minecraft:desert", c.biomeFor(12, 5, 0, 10, 5, 0), "pegado con el jugador en (10, 5, 0)");
        // Girado 90°: lo que estaba en +x queda en +z (como lo gira Transform).
        c.transform(Transform.IDENTITY.rotate(90));
        int[] v = new int[3];
        c.transform().apply(2, 0, 0, v);
        assertEquals("minecraft:desert", c.biomeFor(v[0], v[1], v[2], 0, 0, 0));
        // baked() conserva los biomas en su lugar girado.
        Clipboard b = c.baked(s -> s);
        assertTrue(b.hasBiomes());
        assertEquals("minecraft:desert", b.biomeFor(v[0], v[1], v[2], 0, 0, 0));
    }

    @Test
    void H4_CA2_pegar_elige_cada_celda_por_su_centro() {
        // Un portapapeles de 8×1×1 mitad llano y mitad desierto, pegado en x = 0..7: dos celdas.
        Box box = Box.of(0, 0, 0, 7, 0, 0);
        Clipboard c = new Clipboard(box, 0, 0, 0);
        for (int x = 0; x <= 7; x++) {
            c.put(box, x, 0, 0, world.stone, null);
            c.putBiome(box, x, 0, 0, x < 4 ? "minecraft:plains" : "minecraft:desert");
        }
        Map<Long, String> put = new HashMap<>();
        Box target = c.target(0, 0, 0);
        for (long cell : Cells.of(target)) {
            int x = PointsSource.unpackX(cell), y = PointsSource.unpackY(cell), z = PointsSource.unpackZ(cell);
            int[] m = Cells.center(x, y, z, target);
            put.put(cell, c.biomeFor(m[0], m[1], m[2], 0, 0, 0));
        }
        List<String> got = new ArrayList<>();
        for (long cell : Cells.of(target)) got.add(put.get(cell));
        assertEquals(List.of("minecraft:plains", "minecraft:desert"), got);
    }
}
