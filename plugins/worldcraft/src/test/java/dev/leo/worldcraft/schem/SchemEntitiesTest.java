package dev.leo.worldcraft.schem;

import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.edit.Transform;
import dev.leo.worldcraft.entity.EntityCopy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.leo.worldcraft.schem.SchemFormatTest.map;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 031 H6: entidades en los esquemas (Sponge v3 al guardar; v2 y v3 al cargar). */
class SchemEntitiesTest {
    static byte[] frame() {
        Map<String, Object> nbt = map("id", "minecraft:item_frame", "DataVersion", 4189,
                "Pos", new Nbt.TagList(Nbt.DOUBLE, List.of(1000.5, 64.5, -2002.5)), "UUID", new int[]{1, 2, 3, 4},
                "Facing", (byte) 2, "TileX", 1000, "TileY", 64, "TileZ", -2003, "Rotation", new Nbt.TagList(Nbt.FLOAT, List.of(180f, 0f)),
                "Item", map("id", "minecraft:diamond", "count", 1));
        return Nbt.gzip(Nbt.write("", nbt));
    }

    @Test
    void H6_CA1_guardar_y_cargar_una_entidad_girada_con_su_pared_y_su_item() throws Exception {
        Clipboard c = Clipboard.of(1, 1, 3, 0, 0, -3, new int[]{-1, -1, -1}, Map.of());
        c.entities(List.of(new EntityCopy("minecraft:item_frame", 0.5, 0.5, -2.5, 180, 0, frame(), List.of("minecraft:item_frame"))));
        c.transform(Transform.IDENTITY.rotate90());
        Clipboard baked = c.baked(s -> s);
        Schematic s = SchemConvert.fromClipboard(baked, Map.of(), "minecraft:air", 4189, "marco", "Ana", 1);
        Schematic r = SchemFormat.read(SchemFormat.write(s, "t"), 100);
        assertEquals(1, r.entities());
        Map<String, Object> e = r.entityList().get(0);
        assertEquals("minecraft:item_frame", e.get("Id"));
        double[] pos = (double[]) e.get("Pos");
        // girado: 3 bloques al este del origen; la esquina mínima del esquema queda en x = 1 (Pos es relativa a ella).
        assertArrayEquals(new int[]{1, 0, 0}, r.offset());
        assertArrayEquals(new double[]{2.5, 0.5, 0.5}, pos, 1e-9);
        Map<String, Object> data = Nbt.compound(e, "Data");
        assertFalse(data.containsKey("UUID"), "sin uuid: cada pegado tiene el suyo");
        assertEquals(List.of(3.5, 0.5, 0.5), Nbt.list(data, "Pos").items(), "F-01: Pos y TileX coherentes (WorldEdit usa la diferencia)");
        assertEquals((byte) 5, data.get("Facing"), "la pared norte pasó a ser la este");
        assertEquals(List.of(3, 0, 0), List.of(data.get("TileX"), data.get("TileY"), data.get("TileZ")),
                "F-01: el bloque del marco relativo a quien copió (como WorldEdit), no del mundo");
        assertEquals("minecraft:diamond", Nbt.string(Nbt.compound(data, "Item"), "id"));

        EntityCopy back = SchemConvert.toClipboard(r, new int[]{0}, id -> true, 4189).clipboard().entities().get(0);
        assertEquals(3.5, back.x(), 1e-9);
        assertEquals(0.5, back.z(), 1e-9);
        assertEquals(-90, back.yaw(), 1e-3);
        assertTrue(back.turn().isIdentity(), "lo de un esquema ya viene girado");
        Map<String, Object> nbt = Nbt.read(Nbt.gunzip(back.nbt(), 1 << 20)).tag();
        assertEquals("minecraft:item_frame", nbt.get("id"));
        assertEquals(4189, nbt.get("DataVersion"), "Paper lo actualiza desde la versión del esquema");
        assertFalse(nbt.containsKey("UUID"));
        assertEquals(List.of(3, 0, 0), List.of(nbt.get("TileX"), nbt.get("TileY"), nbt.get("TileZ")));
    }

    @Test
    void H6_CA2_v2_de_worldedit_con_los_datos_sueltos_lo_montado_y_las_malas_contadas() throws Exception {
        Map<String, Object> rider = map("id", "minecraft:command_block_minecart", "Command", "op Ana");
        Map<String, Object> s = map("Version", 2, "DataVersion", 3700, "Width", (short) 1, "Height", (short) 1, "Length", (short) 1,
                "Offset", new int[]{-2, 0, 1}, "PaletteMax", 1, "Palette", map("minecraft:air", 0), "BlockData", new byte[]{0},
                "Entities", new Nbt.TagList(Nbt.COMPOUND, List.of(
                        map("Pos", new Nbt.TagList(Nbt.DOUBLE, List.of(0.5, 0.0, 0.5)), "Id", "minecraft:cow", "Health", 5f,
                                "UUID", new int[]{9, 9, 9, 9}, "TileX", 1000, "TileY", 64, "TileZ", 1000, "Passengers", new Nbt.TagList(Nbt.COMPOUND, List.of(rider))),
                        map("Id", "minecraft:pig"))));
        Schematic r = SchemFormat.read(Nbt.gzip(Nbt.write("Schematic", s)), 10);
        assertEquals(2, r.entities());
        assertEquals(1, r.entityList().size(), "la que no tiene Pos se cuenta y se deja");
        EntityCopy cow = SchemConvert.toClipboard(r, new int[]{0}, id -> true, 4189).clipboard().entities().get(0);
        assertEquals(-1.5, cow.x(), 1e-9);
        assertEquals(1.5, cow.z(), 1e-9);
        assertEquals(List.of("minecraft:cow", "minecraft:command_block_minecart"), cow.contains(),
                "lo montado se revisa con los prohibidos");
        Map<String, Object> nbt = Nbt.read(Nbt.gunzip(cow.nbt(), 1 << 20)).tag();
        assertEquals(5f, nbt.get("Health"));
        assertEquals(3700, nbt.get("DataVersion"));
        assertFalse(nbt.containsKey("UUID"));
        assertFalse(nbt.containsKey("Id"), "en v2 Pos e Id no son parte de los datos");
        assertEquals(List.of(-2, 0, 1), List.of(nbt.get("TileX"), nbt.get("TileY"), nbt.get("TileZ")),
                "F-02: el bloque junto a la Pos nueva, no donde estaba en el mundo de WorldEdit");
    }
}
