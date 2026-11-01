package dev.leo.worldcraft.schem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchemFilesTest {
    @TempDir
    Path dir;

    SchemFiles files() throws Exception {
        Path own = dir.resolve("plugins/Worldcraft/schematics");
        Files.createDirectories(own);
        return new SchemFiles(own, List.of(new SchemFiles.Extra(dir.resolve("plugins/WorldEdit/schematics"), "WorldEdit"),
                new SchemFiles.Extra(dir.resolve("plugins/FastAsyncWorldEdit/schematics"), "FastAsyncWorldEdit")));
    }

    @Test
    void H5_CA1_nombres_seguros_y_peligrosos() throws Exception {
        assertEquals("tower", SchemFiles.clean("tower"));
        assertEquals("tower", SchemFiles.clean("tower.schem"));
        assertEquals("castles/Tower_2-b", SchemFiles.clean("castles/Tower_2-b"));
        assertEquals("v1.2", SchemFiles.clean("v1.2"));
        for (String bad : List.of("", "../server", "a/../../b", "..", "/etc/passwd", "C:/x", "a\\b", ".hidden", "a/.b", "a//b",
                "a/", "con espacio", "a/b/c/d/e", "x".repeat(65), "a..b", "ñ")) {
            assertThrows(SchemFiles.SchemFileException.class, () -> SchemFiles.clean(bad), bad);
        }
    }

    @Test
    void H3_CA1_CA4_la_carpeta_propia_primero_y_las_de_worldedit_con_subcarpetas() throws Exception {
        SchemFiles f = files();
        Path we = dir.resolve("plugins/WorldEdit/schematics");
        Files.createDirectories(we.resolve("castles"));
        Files.write(we.resolve("castles/tower.schem"), new byte[]{1});
        Files.write(we.resolve("house.schem"), new byte[]{2});
        Files.write(we.resolve("notes.txt"), new byte[]{3});
        Files.write(we.resolve("old.schematic"), new byte[]{4});
        f.write("house", new byte[]{9, 9}, false);
        SchemFiles.Entry h = f.find("house", true);
        assertNull(h.folder(), "la propia tapa a la de WorldEdit");
        assertEquals(2, h.bytes());
        SchemFiles.Entry t = f.find("castles/tower", true);
        assertEquals("WorldEdit", t.folder());
        assertNull(f.find("castles/tower", false), "sin las de solo lectura no está");
        assertNull(f.find("nada", true));
        List<SchemFiles.Entry> all = f.list();
        assertEquals(List.of("castles/tower", "house"), all.stream().map(SchemFiles.Entry::name).toList());
        assertEquals("WorldEdit", all.get(0).folder());
        assertNull(all.get(1).folder());
    }

    @Test
    void H3_CA3_sin_carpeta_de_worldedit_no_pasa_nada() throws Exception {
        SchemFiles f = files();
        assertEquals(List.of(), f.list());
        assertNull(f.find("x", true));
    }

    @Test
    void H1_CA4_no_pisa_sin_overwrite_y_escribe_de_forma_atomica() throws Exception {
        SchemFiles f = files();
        Path p = f.write("a/b", new byte[]{1}, false);
        assertTrue(p.endsWith(Path.of("a", "b.schem")));
        assertThrows(FileAlreadyExistsException.class, () -> f.write("a/b", new byte[]{2}, false));
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(p));
        f.write("a/b", new byte[]{3}, true);
        assertArrayEquals(new byte[]{3}, Files.readAllBytes(p));
        try (var s = Files.list(p.getParent())) {
            assertEquals(1, s.count(), "no quedan temporales");
        }
    }

    @Test
    void H4_CA2_borrar_va_a_la_papelera_y_se_vacia_despues() throws Exception {
        SchemFiles f = files();
        f.write("vieja", new byte[]{1}, false);
        Path we = dir.resolve("plugins/WorldEdit/schematics");
        Files.createDirectories(we);
        Files.write(we.resolve("ajena.schem"), new byte[]{1});
        long now = 1_700_000_000_000L;
        assertTrue(f.trash("vieja", now));
        assertNull(f.find("vieja", true));
        assertFalse(f.trash("ajena", now), "la de WorldEdit no se borra");
        assertTrue(Files.exists(we.resolve("ajena.schem")));
        Path trash = f.own().resolve(".trash");
        try (var s = Files.list(trash)) {
            assertEquals(1, s.count());
        }
        assertEquals(List.of("ajena"), f.list().stream().map(SchemFiles.Entry::name).toList(), "la papelera no se lista");
        assertEquals(0, f.purgeTrash(now + 6 * 86_400_000L, 7));
        assertEquals(1, f.purgeTrash(now + 8 * 86_400_000L, 7));
        // Un archivo viejo que se borra hoy cuenta desde hoy.
        f.write("otra", new byte[]{1}, false);
        Files.setLastModifiedTime(f.own().resolve("otra.schem"), FileTime.fromMillis(0));
        f.trash("otra", now);
        assertEquals(0, f.purgeTrash(now + 86_400_000L, 7));
    }

    @Test
    void H5_CA1_un_enlace_que_sale_de_la_carpeta_se_rechaza() throws Exception {
        SchemFiles f = files();
        Path outside = dir.resolve("secreto.schem");
        Files.write(outside, new byte[]{1});
        try {
            Files.createSymbolicLink(f.own().resolve("link.schem"), outside);
        } catch (Exception e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "este sistema no deja crear enlaces: " + e);
        }
        SchemFiles.SchemFileException e = assertThrows(SchemFiles.SchemFileException.class, () -> f.find("link", true));
        assertTrue(e.getMessage().contains("outside"));
        assertEquals(List.of(), f.list(), "los enlaces no se listan");
    }
}
