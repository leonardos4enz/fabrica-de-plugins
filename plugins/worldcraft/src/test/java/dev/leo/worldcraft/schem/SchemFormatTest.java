package dev.leo.worldcraft.schem;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Los formatos Sponge v1, v2 y v3, armados aquí a partir de la especificación pública (no con archivos de WorldEdit). */
class SchemFormatTest {

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void H1_CA1_v3_escrito_y_leido_igual() throws Exception {
        String[] palette = {"minecraft:air", "minecraft:stone", "minecraft:chest[facing=north,type=single,waterlogged=false]"};
        int[] blocks = new int[2 * 3 * 4];
        for (int i = 0; i < blocks.length; i++) blocks[i] = i % 2;
        blocks[5] = 2;
        Map<Integer, Map<String, Object>> bes = new HashMap<>();
        bes.put(5, map("Id", "minecraft:chest", "Data", map("Items", new Nbt.TagList(Nbt.COMPOUND,
                List.of(map("Slot", (byte) 3, "id", "minecraft:diamond", "count", 5))))));
        Schematic s = new Schematic(3, 4189, 2, 3, 4, new int[]{-1, 0, 2}, palette, blocks, bes, 0, 0, "casa", "Ana", 123L);
        byte[] file = SchemFormat.write(s, "0.3.0");
        // La raíz de v3 es un compuesto sin nombre con «Schematic» adentro.
        Nbt.Root root = Nbt.read(Nbt.gunzip(file, 1 << 20));
        assertEquals("", root.name());
        assertEquals(3, Nbt.compound(root.tag(), "Schematic").get("Version"));
        Schematic r = SchemFormat.read(file, 1000);
        assertEquals(3, r.version());
        assertEquals(4189, r.dataVersion());
        assertEquals(List.of(2, 3, 4), List.of(r.width(), r.height(), r.length()));
        assertArrayEquals(new int[]{-1, 0, 2}, r.offset());
        assertArrayEquals(palette, r.palette());
        assertArrayEquals(blocks, r.blocks());
        assertEquals("casa", r.name());
        assertEquals("Ana", r.author());
        assertEquals(123L, r.date());
        // La posición del cofre: el índice 5 es x = 1, z = 2, y = 0 en 2×3×4.
        Map<String, Object> be = Nbt.list(Nbt.compound(Nbt.compound(root.tag(), "Schematic"), "Blocks"), "BlockEntities").items()
                .stream().map(o -> (Map<String, Object>) cast(o)).findFirst().orElseThrow();
        assertArrayEquals(new int[]{1, 0, 2}, (int[]) be.get("Pos"));
        assertEquals("minecraft:diamond", Nbt.string(cast(Nbt.list(Nbt.compound(r.blockEntities().get(5), "Data"), "Items").items().get(0)), "id"));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> cast(Object o) {
        return (Map<String, Object>) o;
    }

    @Test
    void H2_CA2_v2_de_worldedit_usa_WEOffset_y_los_datos_de_bloque_sueltos() throws Exception {
        // Como lo guarda WorldEdit 7.0-7.2: Offset es la posición en el mundo; lo relativo, en Metadata.WEOffsetX/Y/Z.
        Map<String, Object> palette = map("minecraft:air", 0, "minecraft:oak_sign[rotation=0,waterlogged=false]", 1);
        Map<String, Object> s = map("Version", 2, "DataVersion", 3700, "Width", (short) 2, "Height", (short) 1, "Length", (short) 1,
                "Offset", new int[]{1000, 64, -2000},
                "Metadata", map("WEOffsetX", -3, "WEOffsetY", 1, "WEOffsetZ", 0),
                "PaletteMax", 2, "Palette", palette, "BlockData", VarInts.encode(new int[]{0, 1}),
                "BlockEntities", new Nbt.TagList(Nbt.COMPOUND, List.of(map("Pos", new int[]{1, 0, 0}, "Id", "minecraft:sign",
                        "front_text", map("messages", new Nbt.TagList(Nbt.STRING, List.of("\"hola\"", "\"\"", "\"\"", "\"\"")))))));
        Schematic r = SchemFormat.read(Nbt.gzip(Nbt.write("Schematic", s)), 1000);
        assertEquals(2, r.version());
        assertArrayEquals(new int[]{-3, 1, 0}, r.offset());
        assertEquals("minecraft:sign", Nbt.string(r.blockEntities().get(1), "Id"));
        Map<String, Object> d = Nbt.compound(r.blockEntities().get(1), "Data");
        assertNotNull(Nbt.compound(d, "front_text"));
        assertFalse(d.containsKey("Pos"), "Pos e Id no son parte de los datos");
        assertEquals(TileData.Kind.SIGN, new TileData(3700, "minecraft:sign", d).kind());
    }

    @Test
    void H2_CA2_v1_con_TileEntities_y_sin_DataVersion() throws Exception {
        Map<String, Object> s = map("Version", 1, "Width", (short) 1, "Height", (short) 1, "Length", (short) 1,
                "Offset", new int[]{0, -1, 0}, "Palette", map("minecraft:chest", 0), "BlockData", new byte[]{0},
                "TileEntities", new Nbt.TagList(Nbt.COMPOUND, List.of(map("ContentVersion", 1, "Pos", new int[]{0, 0, 0}, "Id", "minecraft:chest",
                        "Items", new Nbt.TagList(Nbt.END, List.of())))));
        Schematic r = SchemFormat.read(Nbt.gzip(Nbt.write("Schematic", s)), 1000);
        assertEquals(-1, r.dataVersion());
        assertArrayEquals(new int[]{0, -1, 0}, r.offset(), "sin WEOffset: el Offset del archivo");
        assertEquals(1, r.blockEntities().size());
        assertFalse(Nbt.compound(r.blockEntities().get(0), "Data").containsKey("ContentVersion"));
    }

    @Test
    void paleta_de_mas_de_127_usa_varint_de_dos_bytes() throws Exception {
        String[] palette = new String[300];
        for (int i = 0; i < 300; i++) palette[i] = "minecraft:block_" + i;
        int[] blocks = new int[300];
        for (int i = 0; i < 300; i++) blocks[i] = 299 - i;
        Schematic s = new Schematic(3, 1, 300, 1, 1, new int[3], palette, blocks, Map.of(), 0, 0, null, null, 0);
        Schematic r = SchemFormat.read(SchemFormat.write(s, "x"), 1000);
        assertArrayEquals(blocks, r.blocks());
        assertEquals("minecraft:block_299", r.palette()[r.blocks()[0]]);
    }

    @Test
    void H5_CA2_una_cabecera_enorme_se_rechaza_antes_de_reservar() {
        // 65 535³ bloques con un arreglo de datos vacío: si reservara, serían 1 125 TB.
        Map<String, Object> sc = map("Version", 3, "DataVersion", 4189, "Width", (short) -1, "Height", (short) -1, "Length", (short) -1,
                "Blocks", map("Palette", map("minecraft:air", 0), "Data", new byte[0]));
        Nbt.NbtException e = assertThrows(Nbt.NbtException.class,
                () -> SchemFormat.read(Nbt.gzip(Nbt.write("", map("Schematic", sc))), 5_000_000));
        assertTrue(e.getMessage().contains("65535×65535×65535") && e.getMessage().contains("schematics.max_blocks"), e.getMessage());
    }

    @Test
    void H5_CA3_archivos_malos_dicen_por_que() {
        byte[] noEsEsquema = Nbt.gzip(Nbt.write("", map("hola", 1)));
        assertEquals("this is not a Sponge schematic (.schem): it has no Version",
                assertThrows(Nbt.NbtException.class, () -> SchemFormat.read(noEsEsquema, 1000)).getMessage());
        byte[] v4 = Nbt.gzip(Nbt.write("", map("Schematic", map("Version", 4))));
        assertTrue(assertThrows(Nbt.NbtException.class, () -> SchemFormat.read(v4, 1000)).getMessage().contains("version 4"));
        // Datos que no alcanzan para el tamaño.
        Map<String, Object> corto = map("Version", 3, "Width", (short) 2, "Height", (short) 2, "Length", (short) 2,
                "Blocks", map("Palette", map("minecraft:air", 0), "Data", new byte[3]));
        assertTrue(assertThrows(Nbt.NbtException.class, () -> SchemFormat.read(Nbt.gzip(Nbt.write("", map("Schematic", corto))), 1000))
                .getMessage().contains("has 3 blocks but the size says 8"));
        // Un índice que la paleta no tiene.
        Map<String, Object> malo = map("Version", 3, "Width", (short) 1, "Height", (short) 1, "Length", (short) 1,
                "Blocks", map("Palette", map("minecraft:air", 0), "Data", new byte[]{5}));
        assertTrue(assertThrows(Nbt.NbtException.class, () -> SchemFormat.read(Nbt.gzip(Nbt.write("", map("Schematic", malo))), 1000))
                .getMessage().contains("palette entry that does not exist"));
        // Un dato de bloque fuera de la caja se salta y se cuenta.
        Map<String, Object> fuera = map("Version", 3, "Width", (short) 1, "Height", (short) 1, "Length", (short) 1,
                "Blocks", map("Palette", map("minecraft:air", 0), "Data", new byte[]{0},
                        "BlockEntities", new Nbt.TagList(Nbt.COMPOUND, List.of(map("Pos", new int[]{4, 0, 0}, "Id", "minecraft:chest")))));
        assertDoesNotThrow(() -> assertEquals(1, SchemFormat.read(Nbt.gzip(Nbt.write("", map("Schematic", fuera))), 1000).badBlockEntities()));
    }
}
