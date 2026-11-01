package dev.leo.skinstatues.statue;

import dev.leo.skinstatues.statue.StatueStore.State;
import dev.leo.skinstatues.statue.StatueStore.Statue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StoreTest {
    @Test
    void H8_CA1_el_diario_guarda_lo_que_habia_con_su_estado(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("statues/1.journal");
        try (Journal j = Journal.create(f)) {
            j.append(List.of(new Journal.Entry(0, 0, 0, "minecraft:grass_block[snowy=false]", "minecraft:white_wool"),
                    new Journal.Entry(1, 2, -3, "minecraft:water[level=0]", "minecraft:white_wool")));
            j.append(List.of(new Journal.Entry(-5, 7, 2, "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=true]",
                    "minecraft:red_wool")));
        }
        List<Journal.Entry> read = Journal.read(f);
        assertEquals(3, read.size());
        assertEquals("minecraft:water[level=0]", read.get(1).before());
        assertEquals(new Journal.Entry(-5, 7, 2, "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=true]",
                "minecraft:red_wool"), read.get(2));
    }

    @Test
    void H8_CA4_una_cola_truncada_no_pierde_lo_anterior(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("2.journal");
        try (Journal j = Journal.create(f)) {
            j.append(List.of(new Journal.Entry(0, 0, 0, "minecraft:stone", "minecraft:white_wool")));
            j.append(List.of(new Journal.Entry(0, 1, 0, "minecraft:dirt", "minecraft:white_wool")));
        }
        try (RandomAccessFile raf = new RandomAccessFile(f.toFile(), "rw")) {
            raf.setLength(raf.length() - 7); // el servidor se cayó a mitad del último registro
        }
        List<Journal.Entry> read = Journal.read(f);
        assertEquals(1, read.size());
        assertEquals("minecraft:stone", read.get(0).before());
    }

    @Test
    void H8_CA4_el_indice_sobrevive_y_lo_interrumpido_queda_cancelado(@TempDir Path dir) throws Exception {
        StatueStore a = new StatueStore(dir);
        UUID ana = UUID.randomUUID();
        int id1 = a.allocateId(), id2 = a.allocateId();
        a.put(new Statue(id1, "Example", 32, "standing", "all", "world", 1, 2, 3, 90, ana, "Ana", 1000, 500, State.DONE));
        a.put(new Statue(id2, "Example", 64, "waving", "wool", "world", 4, 5, 6, 0, ana, "Ana", 2000, 900, State.BUILDING));
        a.write(a.snapshot());
        assertFalse(Files.exists(dir.resolve("statues.yml.tmp")), "escritura atómica: no queda el temporal");

        StatueStore b = new StatueStore(dir);
        assertEquals(1, b.load(), "una estatua seguía construyéndose al apagar");
        assertEquals(State.CANCELLED, b.get(id2).state());
        assertEquals(90, b.get(id1).facing());
        assertEquals(id2, b.lastBy(ana).id(), "undo quita la última");
        assertEquals(3, b.allocateId(), "los números no se repiten");
        assertNull(b.lastBy(UUID.randomUUID()));
    }
}
