package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.TestBlocks.MemoryWorld;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.history.Journal;
import dev.leo.worldcraft.history.JournalSink;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Parser;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Shape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class EditTest {
    static final UUID ANA = new UUID(1, 1);
    final StateTable table = new StateTable();
    final MemoryWorld world = new MemoryWorld(table);
    final Parser parser = new Parser(TestBlocks.REGISTRY, Map.of(), Map.of());
    @TempDir
    Path dir;

    /** Corre un trabajo hasta el final, de a pocos bloques por paso, como lo haría el planificador. */
    static void run(EditJob job) {
        for (int i = 0; i < 1_000_000 && !job.step(Long.MAX_VALUE, 37); i++) {
            // sigue
        }
        assertNotEquals(EditJob.State.RUNNING, job.state());
    }

    EditJob apply(EditSource src, Path journal) {
        JournalSink sink = new JournalSink(journal, table, Runnable::run, e -> fail(e));
        EditJob j = new EditJob(1, ANA, "test", world, src, EditJob.Mode.APPLY, sink, null, Long.MAX_VALUE, System::nanoTime);
        run(j);
        return j;
    }

    RegionSource region(Box box, Shape shape, String mask, String pattern, long seed) throws ParseException {
        return new RegionSource(box, shape, mask == null ? null : parser.mask(mask).compile(table),
                parser.pattern(pattern).compile(table), seed);
    }

    EditJob undo(Path journal, boolean redo, Box box) throws IOException {
        Journal.Data d = Journal.read(journal);
        EditJob j = new EditJob(2, ANA, redo ? "redo" : "undo", world,
                new UndoSource(CompletableFuture.completedFuture(d), table, redo, box), EditJob.Mode.APPLY,
                new EditJob.Sink() {
                    @Override
                    public void changed(int x, int y, int z, int before, int after, byte[] tb, byte[] ta) {
                    }
                }, null, Long.MAX_VALUE, System::nanoTime);
        run(j);
        return j;
    }

    Map<String, String> picture(Box b) {
        Map<String, String> m = new HashMap<>();
        for (int x = b.minX(); x <= b.maxX(); x++) for (int y = b.minY(); y <= b.maxY(); y++)
            for (int z = b.minZ(); z <= b.maxZ(); z++) m.put(x + "," + y + "," + z, world.at(x, y, z));
        return m;
    }

    @Test
    void H3_CA1_set_llena_la_caja_y_cuenta_lo_cambiado() throws Exception {
        Box box = Box.of(-3, -2, 5, 20, 3, 40); // cruza chunks y el suelo (y < 0 es piedra)
        EditJob j = apply(region(box, Shape.FILL, null, "stone", 1), dir.resolve("a.wcj"));
        assertEquals(box.volume(), j.visited());
        assertEquals(24L * 4 * 36, j.changed(), "lo que ya era piedra no cuenta");
        for (String s : picture(box).values()) assertEquals("minecraft:stone", s);
        assertEquals(0, world.loaded.size(), "suelta cada chunk al terminar");
    }

    @Test
    void H3_CA1_replace_solo_donde_la_mascara_deja() throws Exception {
        world.put(0, 5, 0, "minecraft:dirt");
        world.put(1, 5, 0, "minecraft:grass_block");
        world.put(2, 5, 0, "minecraft:sand");
        apply(region(Box.of(0, 5, 0, 2, 5, 0), Shape.FILL, "dirt, grass_block", "glass", 1), dir.resolve("r.wcj"));
        assertEquals("minecraft:glass", world.at(0, 5, 0));
        assertEquals("minecraft:glass", world.at(1, 5, 0));
        assertEquals("minecraft:sand", world.at(2, 5, 0));
    }

    @Test
    void H3_CA1_walls_y_faces_dejan_el_interior() throws Exception {
        Box box = Box.of(0, 10, 0, 9, 14, 6);
        EditJob w = apply(region(box, Shape.WALLS, null, "stone", 1), dir.resolve("w.wcj"));
        assertEquals(box.count(Shape.WALLS), w.changed());
        assertEquals("minecraft:air", world.at(4, 12, 3), "el interior de walls queda vacío");
        assertEquals("minecraft:air", world.at(4, 14, 3), "walls no pone techo");
        assertEquals("minecraft:stone", world.at(0, 12, 3));
        EditJob f = apply(region(box, Shape.FACES, null, "glass", 1), dir.resolve("f.wcj"));
        assertEquals(box.count(Shape.FACES), f.visited());
        assertEquals("minecraft:glass", world.at(4, 14, 3), "faces pone techo");
        assertEquals("minecraft:glass", world.at(4, 10, 3), "y piso");
        assertEquals("minecraft:air", world.at(4, 12, 3), "y sigue hueca");
    }

    @Test
    void H5_CA3_espera_un_chunk_que_no_esta_cargado_sin_saltarse_bloques() throws Exception {
        Box box = Box.of(0, 1, 0, 40, 1, 3);
        world.slow.add(dev.leo.worldcraft.region.ChunkOrder.pack(1, 0));
        world.slow.add(dev.leo.worldcraft.region.ChunkOrder.pack(2, 0));
        EditJob j = apply(region(box, Shape.FILL, null, "stone", 1), dir.resolve("c.wcj"));
        assertEquals(box.volume(), j.changed());
        assertTrue(world.releases >= 3);
    }

    /**
     * F-01: con el reloj revisado cada 64 bloques, unos bloques lentos (código frío, luz) pasaban el plazo por mucho:
     * la compuerta 2 midió 23,5 ms en un tick con 8 de presupuesto. Ahora se pasa a lo sumo por un bloque.
     */
    @Test
    void H5_CA1_F01_no_se_pasa_del_plazo_aunque_cada_bloque_sea_lento() throws Exception {
        long[] now = {0};
        EditWorld slow = new MemoryWorld(table) {
            @Override
            public void set(int x, int y, int z, int state) {
                super.set(x, y, z, state);
                now[0] += 500_000; // medio milisegundo por bloque
            }
        };
        EditJob j = new EditJob(1, ANA, "set", slow, region(Box.of(0, 0, 0, 15, 15, 15), Shape.FILL, null, "glass", 1),
                EditJob.Mode.APPLY, new EditJob.Sink() {
            @Override
            public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
            }
        }, null, Long.MAX_VALUE, () -> now[0]);
        long deadline = 8_000_000;
        j.step(deadline, 100_000);
        assertTrue(now[0] <= deadline + 500_000, "se pasó hasta " + now[0] / 1e6 + " ms");
        assertEquals(16, j.changed());
    }

    @Test
    void H4_CA3_la_vista_previa_muestra_exactamente_lo_que_hace_la_obra() throws Exception {
        Box box = Box.of(0, 2, 0, 30, 6, 30);
        Map<String, Integer> preview = new HashMap<>();
        EditJob p = new EditJob(3, ANA, "preview", world, region(box, Shape.FILL, null, "40% stone, 35% andesite, 25% glass", 77),
                EditJob.Mode.PREVIEW, new EditJob.Sink() {
            @Override
            public void changed(int x, int y, int z, int before, int after, byte[] tb, byte[] ta) {
                preview.put(x + "," + y + "," + z, after);
            }
        }, null, Long.MAX_VALUE, System::nanoTime);
        run(p);
        assertEquals(0, world.sets, "la vista previa no toca el mundo");
        apply(region(box, Shape.FILL, null, "40% stone, 35% andesite, 25% glass", 77), dir.resolve("p.wcj"));
        assertEquals(box.volume(), preview.size());
        for (Map.Entry<String, Integer> e : preview.entrySet()) {
            String[] c = e.getKey().split(",");
            assertEquals(table.state(e.getValue()), world.at(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])));
        }
    }

    @Test
    void H4_CA2_la_vista_previa_se_corta_en_el_tope_y_lo_dice() throws Exception {
        List<int[]> seen = new ArrayList<>();
        EditJob p = new EditJob(3, ANA, "preview", world, region(Box.of(0, 0, 0, 99, 9, 9), Shape.FILL, null, "glass", 1),
                EditJob.Mode.PREVIEW, new EditJob.Sink() {
            @Override
            public void changed(int x, int y, int z, int before, int after, byte[] tb, byte[] ta) {
                seen.add(new int[]{x, y, z});
            }
        }, null, 500, System::nanoTime);
        run(p);
        assertEquals(500, seen.size());
        assertTrue(p.truncated());
    }

    @Test
    void H6_CA1_deshacer_devuelve_todo_y_rehacer_lo_vuelve_a_poner() throws Exception {
        world.put(3, 4, 3, "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]");
        world.put(5, 4, 5, "minecraft:gold_block");
        Box box = Box.of(0, 3, 0, 20, 6, 20);
        Map<String, String> before = picture(box);
        Path jn = dir.resolve("u.wcj");
        apply(region(box, Shape.FILL, null, "70% stone, 30% dirt", 5), jn);
        Map<String, String> after = picture(box);
        assertNotEquals(before, after);
        EditJob u = undo(jn, false, box);
        assertEquals(before, picture(box), "el terreno vuelve exacto, con su estado");
        assertEquals(0, u.skipped());
        undo(jn, true, box);
        assertEquals(after, picture(box));
    }

    @Test
    void H6_CA3_deshacer_no_pisa_lo_que_otro_cambio_despues() throws Exception {
        Box box = Box.of(0, 3, 0, 9, 3, 9);
        Path jn = dir.resolve("s.wcj");
        apply(region(box, Shape.FILL, null, "stone", 1), jn);
        world.put(4, 3, 4, "minecraft:gold_block"); // otro jugador construye encima
        world.put(5, 3, 5, "minecraft:dirt");
        EditJob u = undo(jn, false, box);
        assertEquals(2, u.skipped());
        assertEquals("minecraft:gold_block", world.at(4, 3, 4));
        assertEquals("minecraft:dirt", world.at(5, 3, 5));
        assertEquals("minecraft:air", world.at(0, 3, 0));
    }

    @Test
    void H6_CA4_deshacer_devuelve_lo_que_habia_en_un_cofre() throws Exception {
        world.put(2, 2, 2, "minecraft:chest");
        byte[] items = {7, 7, 7};
        world.restoreTile(2, 2, 2, items);
        Box box = Box.of(0, 2, 0, 4, 2, 4);
        Path jn = dir.resolve("t.wcj");
        apply(region(box, Shape.FILL, null, "stone", 1), jn);
        assertNull(world.tile(2, 2, 2));
        undo(jn, false, box);
        assertEquals("minecraft:chest", world.at(2, 2, 2));
        assertArrayEquals(items, world.tile(2, 2, 2));
    }

    @Test
    void H5_CA4_cancelar_deja_lo_hecho_deshacible() throws Exception {
        Box box = Box.of(0, 3, 0, 63, 3, 63);
        Path jn = dir.resolve("x.wcj");
        JournalSink sink = new JournalSink(jn, table, Runnable::run, e -> fail(e));
        EditJob j = new EditJob(1, ANA, "set", world, region(box, Shape.FILL, null, "stone", 1), EditJob.Mode.APPLY, sink,
                null, Long.MAX_VALUE, System::nanoTime);
        j.step(Long.MAX_VALUE, 1000);
        j.cancel();
        assertEquals(EditJob.State.CANCELLED, j.state());
        assertEquals(1000, j.changed());
        assertEquals(1000, Journal.read(jn).size);
        undo(jn, false, box);
        assertEquals(0, picture(box).values().stream().filter(s -> !s.equals("minecraft:air")).count());
    }

    @Test
    void H3_CA2_copiar_y_pegar_relativo_al_jugador_sin_el_aire() throws Exception {
        world.put(10, 5, 10, "minecraft:stone");
        world.put(11, 5, 10, "minecraft:glass");
        world.put(10, 6, 10, "minecraft:chest");
        world.restoreTile(10, 6, 10, new byte[]{1, 2});
        Box box = Box.of(10, 5, 10, 11, 6, 11);
        Clipboard clip = copy(box, 10, 4, 10);
        // Donde se pega hay tierra: el aire copiado no la borra.
        world.put(51, 6, 50, "minecraft:dirt");
        apply(paste(clip, 50, 4, 50, false), dir.resolve("p1.wcj"));
        assertEquals("minecraft:stone", world.at(50, 5, 50));
        assertEquals("minecraft:glass", world.at(51, 5, 50));
        assertEquals("minecraft:chest", world.at(50, 6, 50));
        assertArrayEquals(new byte[]{1, 2}, world.tile(50, 6, 50));
        assertEquals("minecraft:dirt", world.at(51, 6, 50));
        apply(paste(clip, 50, 4, 50, true), dir.resolve("p2.wcj"));
        assertEquals("minecraft:air", world.at(51, 6, 50), "con --air sí");
    }

    @Test
    void H3_CA3_girar_mueve_los_bloques_y_gira_su_estado() throws Exception {
        // Una fila de 3 hacia el este desde el jugador, con una escalera que mira al norte en la punta.
        world.put(1, 0 + 10, 0, "minecraft:stone");
        world.put(2, 10, 0, "minecraft:glass");
        world.put(3, 10, 0, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
        Clipboard clip = copy(Box.of(1, 10, 0, 3, 10, 0), 0, 10, 0);
        clip.transform(clip.transform().rotate(90)); // el este pasa al sur
        assertEquals("1×1×3", clip.size());
        apply(paste(clip, 100, 10, 100, false), dir.resolve("g.wcj"));
        assertEquals("minecraft:stone", world.at(100, 10, 101));
        assertEquals("minecraft:glass", world.at(100, 10, 102));
        assertEquals("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", world.at(100, 10, 103));

        clip.transform(clip.transform().flip('y'));
        apply(paste(clip, 200, 50, 200, false), dir.resolve("g2.wcj"));
        assertEquals("minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]", world.at(200, 50, 203),
                "voltear de arriba abajo pone la escalera al revés");
        // Los cuatro giros vuelven al principio.
        Transform t = Transform.IDENTITY.rotate(90).rotate(90).rotate(-90).rotate(270);
        int[] o = new int[3];
        t.apply(4, 5, 6, o);
        assertArrayEquals(new int[]{4, 5, 6}, o);
    }

    @Test
    void H3_CA2_cortar_copia_y_deja_aire() throws Exception {
        world.put(0, 3, 0, "minecraft:gold_block");
        Box box = Box.of(0, 3, 0, 1, 3, 1);
        Clipboard clip = new Clipboard(box, 0, 3, 0);
        JournalSink sink = new JournalSink(dir.resolve("cut.wcj"), table, Runnable::run, e -> fail(e));
        RegionSource air = new RegionSource(box, Shape.FILL, null, parser.pattern("air").compile(table), 1);
        EditJob j = new EditJob(1, ANA, "cut", world, air, EditJob.Mode.APPLY, sink,
                (x, y, z, s, tile) -> clip.put(box, x, y, z, s, tile), Long.MAX_VALUE, System::nanoTime);
        run(j);
        assertEquals("minecraft:air", world.at(0, 3, 0));
        apply(paste(clip, 10, 3, 10, false), dir.resolve("cut2.wcj"));
        assertEquals("minecraft:gold_block", world.at(10, 3, 10));
    }

    Clipboard copy(Box box, int ox, int oy, int oz) {
        Clipboard clip = new Clipboard(box, ox, oy, oz);
        EditJob j = new EditJob(1, ANA, "copy", world, new RegionSource(box, Shape.FILL, null, null, 0), EditJob.Mode.READ,
                new EditJob.Sink() {
                    @Override
                    public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
                    }
                }, (x, y, z, s, tile) -> clip.put(box, x, y, z, s, tile), Long.MAX_VALUE, System::nanoTime);
        run(j);
        assertEquals(0, world.sets);
        return clip;
    }

    PasteSource paste(Clipboard clip, int x, int y, int z, boolean air) {
        return new PasteSource(clip, x, y, z, air, s -> table.material(s).endsWith(":air"), TestBlocks.ops(table));
    }
}
