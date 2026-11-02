package dev.leo.worldcraft.snapshot;

import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Cylinder;
import dev.leo.worldcraft.schem.Nbt;
import dev.leo.worldcraft.schem.TileData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 036 H2, H3: el lector de copias del juego (.mca en carpetas y .zip), armadas desde la descripción del formato. */
class SnapshotTest {
    static final ZoneId UTC = ZoneId.of("UTC");
    static final int DV = 4189; // 1.21.4

    /** Una sección de piedra con una columna de 17 estados distintos en x = 0 (5 bits) y biomas: la mitad desierto. */
    static Map<String, Object> mixed() {
        String[] pal = new String[17];
        pal[0] = "minecraft:stone";
        for (int i = 1; i < 17; i++) pal[i] = "minecraft:oak_log[axis=" + (i % 2 == 0 ? "x" : "z") + "]#" + i;
        // nombres distintos para 17 entradas: los de verdad no importan aquí
        for (int i = 1; i < 17; i++) pal[i] = "minecraft:wool_" + i;
        int[] blocks = new int[4096];
        for (int y = 0; y < 16; y++) blocks[(y << 8)] = y + 1;
        blocks[(5 << 8) | (3 << 4) | 2] = 16;
        int[] bio = new int[64];
        for (int i = 0; i < 64; i++) bio[i] = (i & 3) >= 2 ? 1 : 0; // x de celda 2 y 3 = desierto
        return McaWriter.section(pal, blocks, new String[]{"minecraft:plains", "minecraft:desert"}, bio);
    }

    static Map<String, Object> chest(int x, int y, int z) {
        Map<String, Object> be = new LinkedHashMap<>();
        be.put("id", "minecraft:chest");
        be.put("x", x);
        be.put("y", y);
        be.put("z", z);
        be.put("keepPacked", (byte) 0);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("Slot", (byte) 0);
        item.put("id", "minecraft:diamond");
        item.put("count", 3);
        be.put("Items", new Nbt.TagList(Nbt.COMPOUND, new ArrayList<>(List.of(item))));
        return be;
    }

    /** Una región con el chunk (0, 0) completo, el (1, 0) sin terminar y el (2, 0) en un .mcc; el (3, 0) no está. */
    static byte[] region(Map<String, byte[]> external, int compression) throws IOException {
        Map<Integer, Map<String, Object>> secs = new HashMap<>();
        secs.put(0, mixed());
        secs.put(1, McaWriter.section(new String[]{"minecraft:air"}, null, new String[]{"minecraft:plains"}, null));
        secs.put(-1, McaWriter.section(new String[]{"minecraft:bedrock"}, null, null, null));
        byte[] full = McaWriter.nbt(McaWriter.chunk(DV, "minecraft:full", secs, List.of(chest(1, 2, 3))));
        byte[] proto = McaWriter.nbt(McaWriter.chunk(DV, "minecraft:features", secs, List.of()));
        Map<Integer, Map<String, Object>> stone = Map.of(0, McaWriter.section(new String[]{"minecraft:stone"}, null, null, null));
        byte[] big = McaWriter.nbt(McaWriter.chunk(DV, "full", stone, List.of()));
        return McaWriter.region(List.of(new McaWriter.Entry(0, 0, full, compression), new McaWriter.Entry(1, 0, proto, compression),
                new McaWriter.Entry(2, 0, big, 128 + 2)), external);
    }

    static SnapshotRepo.Chunks memory(byte[] mca, Map<String, byte[]> ext) {
        return new SnapshotRepo.Chunks() {
            @Override
            public RegionFile.Chunk chunk(int cx, int cz) throws IOException {
                if ((cx >> 5) != 0 || (cz >> 5) != 0) return new RegionFile.Chunk(RegionFile.Kind.MISSING, null, "");
                return RegionFile.chunk(RegionFile.of(mca), (x, z, max) -> ext.get("c." + x + "." + z + ".mcc"), cx, cz);
            }

            @Override
            public void close() {
            }
        };
    }

    @Test
    void H3_CA1_la_paleta_empaquetada_los_biomas_y_los_datos_de_bloque() throws Exception {
        for (int comp : new int[]{1, 2, 3}) {
            Map<String, byte[]> ext = new HashMap<>();
            byte[] mca = region(ext, comp);
            RegionFile.Chunk c = RegionFile.chunk(RegionFile.of(mca), null, 0, 0);
            assertEquals(RegionFile.Kind.OK, c.kind(), "compresión " + comp);
            ChunkNbt.Data d = ChunkNbt.decode(c.nbt());
            assertEquals(ChunkNbt.Kind.OK, d.kind());
            ChunkNbt.Section s = d.sections().get(0);
            assertEquals("minecraft:wool_1", s.block(0, 0, 0));
            assertEquals("minecraft:wool_16", s.block(0, 15, 0), "17 entradas = 5 bits, 12 por long");
            assertEquals("minecraft:wool_16", s.block(2, 5, 3));
            assertEquals("minecraft:stone", s.block(1, 5, 3));
            assertEquals("minecraft:plains", s.biome(0, 0, 0));
            assertEquals("minecraft:desert", s.biome(12, 0, 0), "celda x = 3");
            assertEquals("minecraft:bedrock", d.sections().get(-1).block(7, 7, 7), "sección de un estado sin data");
            assertEquals(1, d.blockEntities().size());
        }
    }

    @Test
    void H3_CA2_sin_terminar_viejo_LZ4_mcc_y_roto() throws Exception {
        Map<String, byte[]> ext = new HashMap<>();
        byte[] mca = region(ext, 2);
        assertEquals(ChunkNbt.Kind.UNFINISHED, ChunkNbt.decode(RegionFile.chunk(RegionFile.of(mca), null, 1, 0).nbt()).kind());
        RegionFile.Chunk big = RegionFile.chunk(RegionFile.of(mca), (x, z, max) -> ext.get("c." + x + "." + z + ".mcc"), 2, 0);
        assertEquals(RegionFile.Kind.OK, big.kind(), "el .mcc de al lado");
        assertEquals("minecraft:stone", ChunkNbt.decode(big.nbt()).sections().get(0).block(0, 0, 0));
        assertEquals(RegionFile.Kind.BROKEN, RegionFile.chunk(RegionFile.of(mca), (x, z, max) -> null, 2, 0).kind(), "sin el .mcc");
        assertEquals(RegionFile.Kind.MISSING, RegionFile.chunk(RegionFile.of(mca), null, 3, 0).kind());
        // Antes de 1.18 (DataVersion 2730 = 1.17.1): viejo.
        byte[] old = McaWriter.nbt(McaWriter.chunk(2730, "full", Map.of(), List.of()));
        assertEquals(ChunkNbt.Kind.OLD, ChunkNbt.decode(old).kind());
        Map<String, Object> level = new LinkedHashMap<>();
        level.put("DataVersion", 2586);
        level.put("Level", new LinkedHashMap<>());
        assertEquals(ChunkNbt.Kind.OLD, ChunkNbt.decode(Nbt.write("", level)).kind());
        // LZ4 (1.20.5+, opción del servidor): no se soporta, se dice.
        byte[] lz4 = McaWriter.region(List.of(new McaWriter.Entry(0, 0, old, 4)), new HashMap<>());
        RegionFile.Chunk l = RegionFile.chunk(RegionFile.of(lz4), null, 0, 0);
        assertEquals(RegionFile.Kind.UNSUPPORTED, l.kind());
        assertEquals("LZ4", l.problem());
        // Un largo empaquetado equivocado o un índice fuera de la paleta: roto, no revienta.
        Map<String, Object> bad = McaWriter.section(new String[]{"minecraft:stone", "minecraft:dirt"}, new int[4096], null, null);
        Nbt.compound(bad, "block_states").put("data", new long[3]);
        assertEquals(ChunkNbt.Kind.BROKEN, ChunkNbt.decode(McaWriter.nbt(McaWriter.chunk(DV, "full", Map.of(0, bad), List.of()))).kind());
        long[] outside = ChunkNbt.pack(new int[4096], 4);
        outside[0] = 5; // el índice 5 con una paleta de 2
        Nbt.compound(bad, "block_states").put("data", outside);
        assertEquals(ChunkNbt.Kind.BROKEN, ChunkNbt.decode(McaWriter.nbt(McaWriter.chunk(DV, "full", Map.of(0, bad), List.of()))).kind());
        assertEquals(ChunkNbt.Kind.BROKEN, ChunkNbt.decode(new byte[]{10, 0}).kind(), "NBT cortado");
        // Una cabecera cortada o un chunk que apunta fuera del archivo.
        assertEquals(RegionFile.Kind.BROKEN, RegionFile.chunk(RegionFile.of(new byte[5000]), null, 0, 0).kind());
        byte[] lying = mca.clone();
        lying[0] = 0x7f; // posición enorme
        RegionFile.Chunk far = RegionFile.chunk(RegionFile.of(lying), null, 0, 0);
        assertEquals(RegionFile.Kind.BROKEN, far.kind());
    }

    @Test
    void H3_CA4_una_bomba_de_compresion_se_corta() throws Exception {
        byte[] huge = new byte[(int) RegionFile.MAX_CHUNK + 1024];
        byte[] mca = McaWriter.region(List.of(new McaWriter.Entry(0, 0, huge, 2)), new HashMap<>());
        assertTrue(mca.length < 200_000, "comprimida es chica");
        RegionFile.Chunk c = RegionFile.chunk(RegionFile.of(mca), null, 0, 0);
        assertEquals(RegionFile.Kind.BROKEN, c.kind());
        assertTrue(c.problem().contains("too big"), c.problem());
    }

    @Test
    void H3_CA1_restore_solo_dentro_de_la_region_y_los_chunks_que_faltan_quedan_sin_leer() throws Exception {
        Map<String, byte[]> ext = new HashMap<>();
        byte[] mca = region(ext, 2);
        // Un cilindro de radio 3 alrededor de (4, 3) de y = -2 a 6: toca el chunk (0, 0); la caja de x = 1 a 7.
        Cylinder cyl = new Cylinder(4, 3, 3, 3, -2, 6);
        Restore.Raw raw = Restore.read(cyl, memory(mca, ext), true);
        Box b = raw.box;
        assertEquals(1, raw.chunks);
        String[] pal = raw.palette;
        int inside = 0;
        for (int y = b.minY(); y <= b.maxY(); y++) for (int z = b.minZ(); z <= b.maxZ(); z++) for (int x = b.minX(); x <= b.maxX(); x++) {
            int s = raw.states[((y - b.minY()) * b.depth() + (z - b.minZ())) * b.width() + (x - b.minX())];
            if (!cyl.contains(x, y, z)) {
                assertEquals(-1, s, "fuera del cilindro no se restaura");
                continue;
            }
            inside++;
            String want = y < 0 ? "minecraft:bedrock" : (x == 2 && y == 5 && z == 3) ? "minecraft:wool_16" : "minecraft:stone";
            assertEquals(want, pal[s], x + " " + y + " " + z);
        }
        assertEquals(cyl.volume(), inside);
        assertEquals(inside, java.util.Arrays.stream(raw.counts).sum());
        // El cofre (1, 2, 3) está en el cilindro: sus datos sin la posición.
        int ci = ((2 - b.minY()) * b.depth() + (3 - b.minZ())) * b.width() + (1 - b.minX());
        TileData t = raw.tiles.get(ci);
        assertNotNull(t);
        assertEquals("minecraft:chest", t.id());
        assertEquals(DV, t.dataVersion());
        assertFalse(t.data().containsKey("x"));
        assertEquals(TileData.Kind.CONTAINER, t.kind());
        // Una caja que cruza a los chunks sin terminar (1, 0), en un .mcc (2, 0) y que falta (3, 0).
        Restore.Raw wide = Restore.read(Box.of(10, 0, 0, 60, 0, 0), memory(mca, ext), false);
        assertEquals(4, wide.chunks);
        assertEquals(1, wide.unfinished);
        assertEquals(1, wide.missing);
        assertEquals(-1, wide.states[20 - 10], "el chunk sin terminar se deja");
        assertEquals("minecraft:stone", wide.palette[wide.states[40 - 10]], "el del .mcc se restaura");
        assertEquals(-1, wide.states[50 - 10]);
    }

    @Test
    void H3_CA2_al_portapapeles_lo_desconocido_se_deja_y_los_biomas_que_no_hay_quedan_sin_bioma() throws Exception {
        Map<String, byte[]> ext = new HashMap<>();
        Box box = Box.of(0, 0, 0, 15, 0, 0);
        Restore.Raw raw = Restore.read(box, memory(region(ext, 1), ext), true);
        int[] map = new int[raw.palette.length];
        for (int i = 0; i < map.length; i++) map[i] = raw.palette[i].equals("minecraft:wool_1") ? -1 : 10 + i;
        Clipboard c = Restore.toClipboard(raw, map, s -> false, b -> !b.equals("minecraft:desert"));
        int[] st = c.rawStates();
        assertEquals(-1, st[0], "wool_1 no existe en el servidor: se deja");
        assertTrue(st[1] >= 10);
        assertEquals(1, Restore.unknownBlocks(raw, map));
        assertEquals("minecraft:plains", c.biomeFor(0, 0, 0, 0, 0, 0));
        assertNull(c.biomeFor(15, 0, 0, 0, 0, 0), "el desierto no está en el servidor");
        // El cofre de (1, 2, 3) no entra; uno en una posición que quedó aire se suelta.
        Restore.Raw r2 = Restore.read(Box.of(1, 2, 3, 1, 2, 3), memory(region(ext, 1), ext), false);
        assertEquals(1, r2.tiles.size());
        assertEquals(1, Restore.toClipboard(r2, new int[]{5}, s -> false, b -> true).rawTiles().size());
        assertEquals(0, Restore.toClipboard(r2, new int[]{5}, s -> s == 5, b -> true).rawTiles().size());
    }

    @Test
    void H2_CA1_carpetas_y_zip_con_y_sin_carpeta_de_mas(@TempDir Path dir) throws Exception {
        Map<String, byte[]> ext = new HashMap<>();
        byte[] mca = region(ext, 2);
        Path f = dir.resolve("2026-11-01-08-00").resolve("world").resolve("region");
        Files.createDirectories(f);
        Files.write(f.resolve("r.0.0.mca"), mca);
        for (Map.Entry<String, byte[]> e : ext.entrySet()) Files.write(f.resolve(e.getKey()), e.getValue());
        zip(dir.resolve("2026-11-02_10-30-00.zip"), "world/region/", mca, ext);
        zip(dir.resolve("nightly.zip"), "backup/world/region/", mca, ext);
        Files.setLastModifiedTime(dir.resolve("nightly.zip"), FileTime.fromMillis(SnapshotRepo.parseDate("2026-10-30 12:00", UTC)));
        zip(dir.resolve("other-world.zip"), "creative/region/", mca, ext);
        zip(dir.resolve("deep.zip"), "a/b/world/region/", mca, ext);
        Files.writeString(dir.resolve("2026-11-03.zip"), "not a zip");
        Files.createDirectories(dir.resolve("2026-11-04").resolve("creative").resolve("region"));
        List<SnapshotRepo.Entry> list = SnapshotRepo.list(dir, "world", UTC);
        assertEquals(List.of("2026-11-02_10-30-00.zip", "2026-11-01-08-00", "nightly.zip"), list.stream().map(SnapshotRepo.Entry::name).toList(),
                "la más nueva primero; sin el mundo, roto o muy hondo, no está");
        assertEquals("2026-11-02 10:30", list.get(0).date(UTC));
        assertTrue(list.get(0).dateFromName());
        assertFalse(list.get(2).dateFromName(), "sin fecha en el nombre, la del archivo");
        assertEquals("backup/world/", list.get(2).zipPrefix());
        for (SnapshotRepo.Entry e : list) {
            try (SnapshotRepo.Chunks c = SnapshotRepo.open(e, "world")) {
                ChunkNbt.Data d = ChunkNbt.decode(c.chunk(0, 0).nbt());
                assertEquals("minecraft:wool_16", d.sections().get(0).block(2, 5, 3), e.name());
                assertEquals(RegionFile.Kind.OK, c.chunk(2, 0).kind(), "el .mcc al lado: " + e.name());
                assertEquals(RegionFile.Kind.MISSING, c.chunk(40, 0).kind(), "otra región que no está");
            }
        }
        assertEquals(java.util.Set.of("2026-11-04", "other-world.zip"), java.util.Set.copyOf(SnapshotRepo.list(dir, "creative", UTC).stream()
                .map(SnapshotRepo.Entry::name).toList()), "creative en carpeta y en un .zip");
    }

    @Test
    void H3_CA4_una_region_enorme_dentro_del_zip_se_corta(@TempDir Path dir) throws Exception {
        Path z = dir.resolve("big.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(z))) {
            out.putNextEntry(new ZipEntry("world/region/r.0.0.mca"));
            byte[] zeros = new byte[1 << 20];
            for (int i = 0; i <= RegionFile.MAX_REGION >> 20; i++) out.write(zeros);
            out.closeEntry();
        }
        SnapshotRepo.Entry e = SnapshotRepo.list(dir, "world", UTC).get(0);
        try (SnapshotRepo.Chunks c = SnapshotRepo.open(e, "world")) {
            IOException ex = assertThrows(IOException.class, () -> c.chunk(0, 0));
            assertTrue(ex.getMessage().contains("too big"), ex.getMessage());
        }
    }

    @Test
    void H2_CA2_fechas_before_y_after() {
        assertEquals(SnapshotRepo.parseDate("2026-11-02 14:30", UTC), SnapshotRepo.dateFromName("backup-2026-11-02-14-30", UTC));
        assertEquals(SnapshotRepo.parseDate("2026-11-02", UTC), SnapshotRepo.dateFromName("2026-11-02", UTC));
        assertEquals(SnapshotRepo.parseDate("2026-11-02T14:30:15", UTC), SnapshotRepo.dateFromName("2026-11-02_14-30-15.zip", UTC));
        assertNull(SnapshotRepo.dateFromName("2026-13-40", UTC), "una fecha que no existe");
        assertNull(SnapshotRepo.dateFromName("nightly", UTC));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SnapshotRepo.parseDate("yesterday", UTC));
        assertTrue(e.getMessage().contains("2026-11-02 14:30"));
        assertThrows(IllegalArgumentException.class, () -> SnapshotRepo.parseDate("2026-02-30", UTC));
        long d1 = SnapshotRepo.parseDate("2026-11-01", UTC), d2 = SnapshotRepo.parseDate("2026-11-02", UTC), d3 = SnapshotRepo.parseDate("2026-11-03", UTC);
        List<SnapshotRepo.Entry> list = List.of(entry("c", d3), entry("b", d2), entry("a", d1));
        assertEquals("b", SnapshotRepo.before(list, d3).name());
        assertEquals("a", SnapshotRepo.before(list, d2 - 1).name());
        assertNull(SnapshotRepo.before(list, d1));
        assertEquals("b", SnapshotRepo.after(list, d1).name());
        assertEquals("c", SnapshotRepo.after(list, d2).name());
        assertNull(SnapshotRepo.after(list, d3));
    }

    @Test
    void STRIDE_nombres_con_rutas_no_valen() {
        for (String bad : List.of("../world", "..", "a/b", "a\\b", ".hidden", "")) assertFalse(SnapshotRepo.safeName(bad), bad);
        assertTrue(SnapshotRepo.safeName("2026-11-02 backup.zip"));
    }

    @Test
    void H3_CA3_los_chunks_de_la_caja_region_por_region() {
        long[] cs = Restore.chunks(Box.of(-20, 0, 0, 520, 0, 20));
        assertEquals(Restore.chunkCount(Box.of(-20, 0, 0, 520, 0, 20)), cs.length);
        // Primero todos los de la región -1, después los de la 0, después la 1.
        int lastRegion = Integer.MIN_VALUE;
        for (long k : cs) {
            int rx = ((int) (k >> 32)) >> 5;
            assertTrue(rx >= lastRegion);
            lastRegion = rx;
        }
    }

    @Test
    void H1_CA2_semillas_y_carpetas_temporales(@TempDir Path dir) throws Exception {
        Random r = new Random(1);
        assertEquals(42, TempWorlds.seed(null, 42, r));
        assertEquals(-7, TempWorlds.seed("-7", 42, r));
        assertEquals("abc".hashCode(), TempWorlds.seed("abc", 42, r), "una palabra como el juego");
        assertNotEquals(42, TempWorlds.seed("random", 42, new Random(5)));
        assertTrue(TempWorlds.looksLikeSeed("123"));
        assertTrue(TempWorlds.looksLikeSeed("random"));
        assertFalse(TempWorlds.looksLikeSeed("desert"));
        Path mine = dir.resolve(TempWorlds.PREFIX + "1");
        TempWorlds.mark(mine, "world");
        Files.createDirectories(mine.resolve("region"));
        Files.writeString(mine.resolve("region").resolve("r.0.0.mca"), "x");
        Path theirs = dir.resolve(TempWorlds.PREFIX + "2"); // sin la marca: del dueño
        Files.createDirectories(theirs);
        Path loaded = dir.resolve(TempWorlds.PREFIX + "3");
        TempWorlds.mark(loaded, "world");
        Path world = dir.resolve("world");
        Files.createDirectories(world);
        Files.writeString(world.resolve(TempWorlds.MARK), "a mark in the wrong folder");
        assertEquals(List.of(mine), TempWorlds.leftovers(dir, java.util.Set.of(TempWorlds.PREFIX + "3")));
        assertFalse(TempWorlds.delete(theirs));
        assertFalse(TempWorlds.delete(world), "el nombre no es de Worldcraft");
        assertTrue(TempWorlds.delete(mine));
        assertFalse(Files.exists(mine));
        assertTrue(Files.exists(theirs));
        assertTrue(Files.exists(world));
    }

    static SnapshotRepo.Entry entry(String name, long t) {
        return new SnapshotRepo.Entry(name, Path.of(name), false, null, t, true);
    }

    static void zip(Path z, String dir, byte[] mca, Map<String, byte[]> ext) throws IOException {
        try (OutputStream o = Files.newOutputStream(z); ZipOutputStream out = new ZipOutputStream(o)) {
            out.putNextEntry(new ZipEntry(dir + "r.0.0.mca"));
            out.write(mca);
            out.closeEntry();
            for (Map.Entry<String, byte[]> e : ext.entrySet()) {
                out.putNextEntry(new ZipEntry(dir + e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
    }
}
