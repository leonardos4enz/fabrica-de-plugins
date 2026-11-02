package dev.leo.worldcraft.schem;

import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.region.Box;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 033 H4 CA3: los biomas en los esquemas Sponge v3 (por bloque) y v2 (por columna), armados desde la especificación. */
class SchemBiomesTest {
    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void H4_CA3_v3_con_biomas_ida_y_vuelta_por_el_portapapeles() throws Exception {
        Box box = Box.of(0, 0, 0, 1, 1, 0);
        Clipboard c = new Clipboard(box, 0, 0, 0);
        for (int x = 0; x <= 1; x++) for (int y = 0; y <= 1; y++) {
            c.put(box, x, y, 0, 7, null);
            c.putBiome(box, x, y, 0, x == 0 ? "minecraft:plains" : "minecraft:desert");
        }
        Schematic s = SchemConvert.fromClipboard(c, Map.of(7, "minecraft:stone"), "minecraft:air", 4189, "b", "Ana", 1);
        assertNotNull(s.biomes());
        byte[] file = SchemFormat.write(s, "0.9.0");
        Nbt.Root root = Nbt.read(Nbt.gunzip(file, 1 << 20));
        Map<String, Object> bio = Nbt.compound(Nbt.compound(root.tag(), "Schematic"), "Biomes");
        assertNotNull(bio, "v3 los guarda en Biomes");
        assertTrue(Nbt.compound(bio, "Palette").containsKey("minecraft:desert"));
        Schematic r = SchemFormat.read(file, 1000);
        SchemConvert.Loaded l = SchemConvert.toClipboard(r, new int[]{7}, st -> false, 4189, b -> !b.equals("minecraft:desert"));
        assertEquals(Set.of("minecraft:desert"), l.unknownBiomes(), "lo que el servidor no tiene se cuenta");
        assertTrue(l.clipboard().hasBiomes());
        assertEquals("minecraft:plains", l.clipboard().biomeFor(0, 1, 0, 0, 0, 0));
        assertNull(l.clipboard().biomeFor(1, 1, 0, 0, 0, 0), "el desconocido queda sin bioma");
    }

    @Test
    void H4_CA3_v2_por_columna_vale_para_toda_la_altura() throws Exception {
        // 2×3×1: la columna x=1 es desierto.
        Map<String, Object> s = map("Version", 2, "DataVersion", 3700, "Width", (short) 2, "Height", (short) 3, "Length", (short) 1,
                "Palette", map("minecraft:stone", 0), "BlockData", VarInts.encode(new int[6]),
                "BiomePalette", map("minecraft:plains", 0, "minecraft:desert", 1), "BiomeData", VarInts.encode(new int[]{0, 1}));
        byte[] file = Nbt.gzip(Nbt.write("Schematic", s));
        Schematic r = SchemFormat.read(file, 1000);
        assertEquals(6, r.biomes().length);
        SchemConvert.Loaded l = SchemConvert.toClipboard(r, new int[]{3}, st -> false, 3700, b -> true);
        assertEquals("minecraft:desert", l.clipboard().biomeFor(1, 2, 0, 0, 0, 0));
        assertEquals("minecraft:plains", l.clipboard().biomeFor(0, 2, 0, 0, 0, 0));
        assertEquals(2, l.biomes());
    }

    @Test
    void H4_CA3_una_paleta_de_biomas_danada_se_rechaza() {
        Map<String, Object> s = map("Version", 3, "DataVersion", 4189, "Width", (short) 1, "Height", (short) 1, "Length", (short) 1,
                "Blocks", map("Palette", map("minecraft:stone", 0), "Data", VarInts.encode(new int[1]), "BlockEntities", new Nbt.TagList(Nbt.COMPOUND, List.of())),
                "Biomes", map("Palette", map("minecraft:plains", 0), "Data", VarInts.encode(new int[]{5})));
        Nbt.NbtException e = assertThrows(Nbt.NbtException.class,
                () -> SchemFormat.read(Nbt.gzip(Nbt.write("", map("Schematic", s))), 1000));
        assertTrue(e.getMessage().contains("biome"), e.getMessage());
    }

    @Test
    void sin_biomas_no_escribe_Biomes() throws Exception {
        Box box = Box.of(0, 0, 0, 0, 0, 0);
        Clipboard c = new Clipboard(box, 0, 0, 0);
        c.put(box, 0, 0, 0, 7, null);
        Schematic s = SchemConvert.fromClipboard(c, new HashMap<>(Map.of(7, "minecraft:stone")), "minecraft:air", 4189, "b", "Ana", 1);
        Nbt.Root root = Nbt.read(Nbt.gunzip(SchemFormat.write(s, "0.9.0"), 1 << 20));
        assertNull(Nbt.compound(Nbt.compound(root.tag(), "Schematic"), "Biomes"));
    }
}
