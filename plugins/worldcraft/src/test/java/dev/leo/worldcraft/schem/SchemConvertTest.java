package dev.leo.worldcraft.schem;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.TestBlocks.MemoryWorld;
import dev.leo.worldcraft.block.StateOps;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.edit.EditJob;
import dev.leo.worldcraft.edit.EditSource;
import dev.leo.worldcraft.edit.PasteSource;
import dev.leo.worldcraft.edit.RegionSource;
import dev.leo.worldcraft.edit.Transform;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Shape;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SchemConvertTest {
    static final UUID ANA = new UUID(1, 1);
    final StateTable table = new StateTable();
    final MemoryWorld world = new MemoryWorld(table);
    final StateOps ops = TestBlocks.ops(table);
    static final String STAIRS_N = "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]";

    static final EditJob.Sink NOTHING = new EditJob.Sink() {
        @Override
        public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
        }
    };

    static void run(EditJob job) {
        for (int i = 0; i < 1_000_000 && !job.step(Long.MAX_VALUE, 41); i++) {
            // sigue
        }
    }

    Clipboard copy(Box box, int ox, int oy, int oz) {
        Clipboard clip = new Clipboard(box, ox, oy, oz);
        run(new EditJob(1, ANA, "copy", world, new RegionSource(box, Shape.FILL, null, null, 0), EditJob.Mode.READ, NOTHING,
                (x, y, z, s, tile) -> clip.put(box, x, y, z, s, tile), Long.MAX_VALUE, System::nanoTime));
        return clip;
    }

    void paste(Clipboard clip, int x, int y, int z) {
        EditSource src = new PasteSource(clip, x, y, z, false, s -> table.material(s).endsWith(":air"), ops);
        run(new EditJob(2, ANA, "paste", world, src, EditJob.Mode.APPLY, NOTHING, null, Long.MAX_VALUE, System::nanoTime));
    }

    /** Lo que hace el plugin al guardar (girar cada estado, el texto de la paleta) y al cargar, sin servidor. */
    Clipboard roundTrip(Clipboard clip, int dataVersion) throws Exception {
        Map<Integer, Integer> turned = new HashMap<>();
        Map<Integer, String> names = new HashMap<>();
        for (int s : clip.distinct()) {
            int t = s;
            for (Transform.Step step : clip.transform().steps()) {
                t = switch (step) {
                    case ROTATE_90 -> ops.rotate90(t);
                    case FLIP_X -> ops.flip(t, 'x');
                    case FLIP_Y -> ops.flip(t, 'y');
                    case FLIP_Z -> ops.flip(t, 'z');
                };
            }
            turned.put(s, t);
            names.put(t, table.state(t));
        }
        Clipboard baked = clip.baked(s -> turned.getOrDefault(s, s));
        byte[] file = SchemFormat.write(SchemConvert.fromClipboard(baked, names, "minecraft:air", dataVersion, "t", "Ana", 1), "test");
        Schematic s = SchemFormat.read(file, 1_000_000);
        SchemBlocks.Result res = SchemBlocks.resolve(s.palette(), SchemBlocks.counts(s), TestBlocks.REGISTRY, table);
        assertEquals(0, res.unknown());
        return SchemConvert.toClipboard(s, res.map(), id -> table.material(id).endsWith(":air")).clipboard();
    }

    static byte[] chest(int diamonds) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("Slot", (byte) 4);
        item.put("id", "minecraft:diamond");
        item.put("count", diamonds);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("Items", new Nbt.TagList(Nbt.COMPOUND, List.of(item)));
        return new TileData(4189, "minecraft:chest", data).encode();
    }

    @Test
    void H1_CA1_CA2_CA3_guardar_y_cargar_da_lo_mismo_en_el_mismo_lugar_tambien_girado() throws Exception {
        world.put(1, 10, 0, "minecraft:stone");
        world.put(2, 10, 0, "minecraft:glass");
        world.put(3, 10, 0, STAIRS_N);
        world.put(3, 11, 0, "minecraft:chest");
        world.restoreTile(3, 11, 0, chest(5));
        Clipboard clip = copy(Box.of(1, 10, 0, 3, 11, 0), 0, 10, 0);

        // Sin girar: el mismo lugar relativo (H1 CA2).
        paste(roundTrip(clip, 4189), 100, 10, 100);
        assertEquals("minecraft:stone", world.at(101, 10, 100));
        assertEquals("minecraft:glass", world.at(102, 10, 100));
        assertEquals(STAIRS_N, world.at(103, 10, 100));
        assertEquals("minecraft:chest", world.at(103, 11, 100));
        TileData t = TileData.decode(world.tile(103, 11, 100));
        assertEquals(TileData.Kind.CONTAINER, t.kind());
        assertEquals(5, Nbt.integer(SchemFormatTest.cast(Nbt.list(t.data(), "Items").items().get(0)), "count"));

        // Girado 90: se guarda ya girado, igual que pegar el portapapeles girado (H1 CA1).
        clip.transform(clip.transform().rotate(90));
        paste(clip, 200, 10, 200);
        paste(roundTrip(clip, 4189), 300, 10, 300);
        for (int dx = -3; dx <= 3; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -3; dz <= 3; dz++) {
            assertEquals(world.at(200 + dx, 10 + dy, 200 + dz), world.at(300 + dx, 10 + dy, 300 + dz), dx + " " + dy + " " + dz);
        }
        assertEquals("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", world.at(300, 10, 303));
        assertNotNull(world.tile(300, 11, 303), "el cofre girado sigue con sus datos");
    }

    @Test
    void H2_CA1_un_esquema_con_offset_se_pega_donde_dice() throws Exception {
        String[] palette = {"minecraft:air", "minecraft:gold_block"};
        int[] blocks = new int[2 * 1 * 3];
        blocks[1 + 2 * 2] = 1; // x = 1, z = 2
        Schematic s = new Schematic(3, 4189, 2, 1, 3, new int[]{-1, 0, 2}, palette, blocks, Map.of(), 0, 0, null, null, 0);
        Schematic r = SchemFormat.read(SchemFormat.write(s, "t"), 100);
        SchemBlocks.Result res = SchemBlocks.resolve(r.palette(), SchemBlocks.counts(r), TestBlocks.REGISTRY, table);
        paste(SchemConvert.toClipboard(r, res.map(), id -> table.material(id).endsWith(":air")).clipboard(), 100, 10, 100);
        assertEquals("minecraft:gold_block", world.at(100 - 1 + 1, 10, 100 + 2 + 2));
    }

    @Test
    void H2_CA4_desconocidos_a_aire_nombres_cambiados_y_propiedades_que_no_existen() {
        String[] palette = {"minecraft:stone", "minecraft:grass", "mymod:magic_block", "minecraft:oak_log[axis=x,glowing=true]",
                "minecraft:oak_stairs[facing=up]", "minecraft:copper_bulb[lit=true]"};
        long[] counts = {10, 4, 3, 2, 1, 7};
        SchemBlocks.Result r = SchemBlocks.resolve(palette, counts, TestBlocks.REGISTRY, table);
        assertEquals("minecraft:stone", table.state(r.map()[0]));
        assertEquals("minecraft:short_grass", table.state(r.map()[1]), "grass pasó a llamarse short_grass (1.20.3)");
        assertEquals("minecraft:air", table.state(r.map()[2]));
        assertEquals("minecraft:oak_log[axis=x]", table.state(r.map()[3]), "la propiedad que no existe se quita, axis queda");
        assertEquals("minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", table.state(r.map()[4]));
        assertEquals("minecraft:air", table.state(r.map()[5]));
        assertEquals(10, r.unknown());
        assertEquals(List.of("minecraft:copper_bulb", "mymod:magic_block"), List.copyOf(r.unknownByName().keySet()), "de más a menos");
        assertEquals(2, r.adjusted());
        assertEquals(1, r.renamed());
    }

    @Test
    void H2_CA3_los_datos_que_no_se_pegan_se_cuentan_y_se_conservan() throws Exception {
        String[] palette = {"minecraft:chest", "minecraft:spawner", "mymod:x"};
        int[] blocks = {0, 1, 2};
        Map<Integer, Map<String, Object>> bes = new HashMap<>();
        Map<String, Object> spawner = new LinkedHashMap<>();
        spawner.put("Delay", (short) 20);
        bes.put(0, Map.of("Id", "minecraft:chest", "Data", new LinkedHashMap<>(Map.of("Items", new Nbt.TagList(Nbt.END, List.of())))));
        bes.put(1, Map.of("Id", "minecraft:mob_spawner", "Data", spawner));
        bes.put(2, Map.of("Id", "mymod:x", "Data", new LinkedHashMap<>()));
        Schematic s = new Schematic(3, 3953, 3, 1, 1, new int[3], palette, blocks, bes, 2, 0, null, null, 0);
        SchemBlocks.Result res = SchemBlocks.resolve(s.palette(), SchemBlocks.counts(s), TestBlocks.REGISTRY, table);
        SchemConvert.Loaded l = SchemConvert.toClipboard(s, res.map(), id -> table.material(id).endsWith(":air"));
        assertEquals(1, l.placeable());
        assertEquals(Map.of("mob_spawner", 1), l.kept(), "el del bloque desconocido (aire) se suelta");
        // Al volver a guardar, el generador sigue con sus datos y su versión de datos.
        byte[] tile = l.clipboard().rawTiles().get(1);
        TileData t = TileData.decode(tile);
        assertEquals(3953, t.dataVersion());
        assertEquals((short) 20, t.data().get("Delay"));
    }

    @Test
    void H1_CA1_el_aire_que_falta_y_los_estados_sin_leer_se_guardan_como_aire() {
        Box box = Box.of(0, 0, 0, 1, 0, 0);
        Clipboard clip = new Clipboard(box, 0, 0, 0); // sin leer: -1
        clip.put(box, 0, 0, 0, table.intern("minecraft:stone"), null);
        Schematic s = SchemConvert.fromClipboard(clip, Map.of(table.intern("minecraft:stone"), "minecraft:stone"), "minecraft:air", 1, null, null, 0);
        Set<String> p = new HashSet<>(List.of(s.palette()));
        assertEquals(Set.of("minecraft:stone", "minecraft:air"), p);
    }
}
