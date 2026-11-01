package dev.leo.blockwright.history;

import dev.leo.blockwright.region.Box;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HistoryTest {
    @TempDir
    Path dir;
    final UUID ana = new UUID(7, 7), beto = new UUID(8, 8);
    final Box box = Box.of(0, 0, 0, 3, 3, 3);

    @Test
    void H6_CA2_el_diario_va_y_vuelve_con_los_datos_de_cofres() throws Exception {
        Path f = dir.resolve("1.bwj");
        try (Journal j = Journal.create(f)) {
            j.append(List.of(new Journal.Change(1, -60, 3, "minecraft:chest[facing=north,type=single,waterlogged=false]",
                    "minecraft:stone", new byte[]{1, 2, 3}, null)));
            j.append(List.of(new Journal.Change(2, 70, -9, "minecraft:air", "minecraft:stone", null, new byte[]{9})));
        }
        Journal.Data d = Journal.read(f);
        assertEquals(2, d.size);
        assertEquals("minecraft:chest[facing=north,type=single,waterlogged=false]", d.dict.get(d.before[0]));
        assertEquals(d.after[0], d.after[1], "el diccionario no repite estados");
        assertArrayEquals(new byte[]{1, 2, 3}, d.tileBefore.get(0));
        assertArrayEquals(new byte[]{9}, d.tileAfter.get(1));
        assertEquals(-9, d.z[1]);
    }

    @Test
    void H6_CA2_una_cola_cortada_se_ignora() throws Exception {
        Path f = dir.resolve("2.bwj");
        try (Journal j = Journal.create(f)) {
            for (int i = 0; i < 10; i++) j.append(List.of(new Journal.Change(i, 0, 0, "minecraft:air", "minecraft:stone", null, null)));
        }
        try (RandomAccessFile raf = new RandomAccessFile(f.toFile(), "rw")) {
            raf.setLength(raf.length() - 7); // el último registro, a medias
        }
        assertEquals(9, Journal.read(f).size);
        Files.write(dir.resolve("x.bwj"), new byte[]{1, 2, 3, 4, 5});
        assertThrows(java.io.IOException.class, () -> Journal.read(dir.resolve("x.bwj")));
    }

    @Test
    void H6_CA1_y_CA7_deshacer_rehacer_y_una_operacion_nueva_borra_lo_rehacible() throws Exception {
        HistoryStore h = new HistoryStore(dir, Runnable::run);
        HistoryStore.Entry e1 = h.finish(h.begin(ana, "Ana", "set", "stone", "world", box, 1000), 64, true);
        HistoryStore.Entry e2 = h.finish(h.begin(ana, "Ana", "replace", "dirt -> glass", "world", box, 2000), 10, true);
        assertEquals(e2.id(), h.lastUndoable(ana).orElseThrow().id());
        h.mark(e2, HistoryStore.State.UNDONE);
        assertEquals(e1.id(), h.lastUndoable(ana).orElseThrow().id());
        h.mark(h.lastUndoable(ana).orElseThrow(), HistoryStore.State.UNDONE);
        assertEquals(e1.id(), h.nextRedo(ana).orElseThrow().id(), "se rehace primero lo último deshecho");
        h.mark(e1, HistoryStore.State.DONE);
        Files.writeString(h.journal(ana, e2.id()), "x");
        h.begin(ana, "Ana", "set", "glass", "world", box, 3000);
        assertTrue(h.nextRedo(ana).isEmpty());
        assertFalse(Files.exists(h.journal(ana, e2.id())), "su diario se borra");
        assertEquals(List.of(3, 1), h.list(ana).stream().map(HistoryStore.Entry::id).toList());
        assertTrue(h.lastUndoable(beto).isEmpty());
    }

    @Test
    void H6_CA2_y_CA7_sobrevive_al_reinicio_y_lo_que_estaba_en_marcha_queda_cortado() throws Exception {
        HistoryStore h = new HistoryStore(dir, Runnable::run);
        h.finish(h.begin(ana, "Ana", "set", "stone", "world", box, 1000), 64, true);
        h.begin(ana, "Ana", "set", "glass", "world_nether", box, 2000); // el servidor se cae aquí
        h.finish(h.begin(beto, "Beto", "paste", "", "world", null, 3000), 5, false);

        HistoryStore again = new HistoryStore(dir, Runnable::run);
        assertEquals(1, again.load());
        List<HistoryStore.Entry> anas = again.list(ana);
        assertEquals(2, anas.size());
        assertEquals(HistoryStore.State.CANCELLED, anas.get(0).state());
        assertEquals("world_nether", anas.get(0).world());
        assertEquals(box, anas.get(1).bounds());
        assertEquals(64, anas.get(1).changed());
        assertEquals(beto, again.ownerByName("beto").orElseThrow());
        assertEquals(3, again.begin(ana, "Ana", "set", "x", "world", box, 4000).id(), "los números siguen");
    }

    @Test
    void H6_CA5_limpieza_por_cantidad_y_por_dias() throws Exception {
        HistoryStore h = new HistoryStore(dir, Runnable::run);
        long day = 86_400_000L, now = 100 * day;
        for (int i = 0; i < 8; i++) {
            HistoryStore.Entry e = h.finish(h.begin(ana, "Ana", "set", "s" + i, "world", box, now - (8 - i) * day), 1, true);
            Files.writeString(h.journal(ana, e.id()), "x");
        }
        assertEquals(3, h.prune(now, 5, 30));
        assertEquals(5, h.list(ana).size());
        assertFalse(Files.exists(h.journal(ana, 1)));
        assertTrue(Files.exists(h.journal(ana, 8)));
        assertEquals(2, h.prune(now, 5, 3), "las de más de 3 días (la de 3 justos se queda)");
        assertEquals(List.of(8, 7, 6), h.list(ana).stream().map(HistoryStore.Entry::id).toList());
    }
}
