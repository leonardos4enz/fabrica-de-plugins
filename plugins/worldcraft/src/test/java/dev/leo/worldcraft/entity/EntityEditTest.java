package dev.leo.worldcraft.entity;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.edit.EditJob;
import dev.leo.worldcraft.edit.EmptySource;
import dev.leo.worldcraft.edit.RegionSource;
import dev.leo.worldcraft.edit.Transform;
import dev.leo.worldcraft.edit.UndoSource;
import dev.leo.worldcraft.history.Journal;
import dev.leo.worldcraft.history.JournalSink;
import dev.leo.worldcraft.history.RemapSink;
import dev.leo.worldcraft.pattern.Parser;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Shape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 031: el motor con etapas de entidades, el diario y deshacer/rehacer. */
class EntityEditTest {
    static final UUID ANA = new UUID(1, 1);
    final StateTable table = new StateTable();
    final TestBlocks.MemoryWorld world = new TestBlocks.MemoryWorld(table);
    final MemoryEntities ents = new MemoryEntities();
    final Parser parser = new Parser(TestBlocks.REGISTRY, Map.of(), Map.of());
    @TempDir
    Path dir;

    static void run(EditJob job) {
        for (int i = 0; i < 100_000 && !job.step(Long.MAX_VALUE, 7); i++) {
            // sigue
        }
        assertEquals(EditJob.State.DONE, job.state());
    }

    /** «Cortar» la caja a aire moviendo sus entidades d bloques: quitar antes de los bloques, crear después. */
    EditJob moveEntities(Box box, Path journal, int dx) throws Exception {
        JournalSink sink = new JournalSink(journal, table, Runnable::run, e -> fail(e));
        EntityStage.Scan scan = new EntityStage.Scan(ents, box, e -> true, true, 100, sink);
        EntityStage.Spawn spawn = new EntityStage.Spawn(ents, () -> scan.result().stream()
                .map(e -> new EntityStage.Item(null, e.shifted(dx, 0, 0))).toList(), sink);
        RegionSource src = new RegionSource(box, Shape.FILL, null, parser.pattern("air").compile(table), 1);
        EditJob j = new EditJob(1, ANA, "move", world, src, EditJob.Mode.APPLY, sink, null, Long.MAX_VALUE, System::nanoTime)
                .stages(scan, spawn);
        run(j);
        return j;
    }

    EditJob undo(Path journal, boolean redo, Box box) throws Exception {
        CompletableFuture<Journal.Data> data = CompletableFuture.completedFuture(Journal.read(journal));
        RemapSink remaps = new RemapSink(journal, Runnable::run, e -> fail(e));
        EditJob j = new EditJob(2, ANA, "undo", world, new UndoSource(data, table, redo, box), EditJob.Mode.APPLY, remaps, null,
                Long.MAX_VALUE, System::nanoTime).stages(UndoEntities.kill(ents, data, redo), UndoEntities.spawn(ents, data, redo, remaps));
        run(j);
        return j;
    }

    @Test
    void H2_CA2_mover_quita_antes_de_los_bloques_crea_despues_y_deshacer_las_devuelve() throws Exception {
        Box box = Box.of(0, 1, 0, 3, 3, 3);
        for (int x = 0; x <= 3; x++) for (int z = 0; z <= 3; z++) world.blocks.put(TestBlocks.MemoryWorld.pos(x, 1, z), world.stone);
        MemoryEntities.E stand = ents.add("armor_stand", 1.5, 2, 1.5, "Bob");
        ents.add("cow", 40.5, 2, 1.5, "afuera"); // fuera de la caja: no se toca
        List<Integer> seen = new ArrayList<>();
        ents.onRemove = e -> seen.add(world.get(1, 1, 1));
        Path j = dir.resolve("1.wcj");
        EditJob job = moveEntities(box, j, 10);
        assertEquals(List.of(world.stone), seen, "la entidad se quita antes de que se toquen los bloques");
        assertEquals(world.air, world.get(1, 1, 1));
        assertNull(ents.all.get(stand.id));
        MemoryEntities.E moved = ents.at("armor_stand").get(0);
        assertEquals(11.5, moved.x, 1e-9);
        assertEquals("Bob", moved.name, "la misma, con lo suyo");
        assertEquals(2, ents.all.size());
        assertEquals(1, job.before().done());

        Journal.Data d = Journal.read(j);
        assertEquals(1, d.entitiesOut.size());
        assertEquals(1, d.entitiesIn.size());
        assertEquals(stand.id, d.entitiesOut.get(0).id());
        assertEquals(moved.id, d.entitiesIn.get(0).id());

        // Deshacer: quita la movida, devuelve los bloques y recrea la original (con otro uuid, anotado en el diario).
        undo(j, false, Box.of(0, 1, 0, 14, 3, 3));
        assertEquals(world.stone, world.get(1, 1, 1));
        List<MemoryEntities.E> back = ents.at("armor_stand");
        assertEquals(1, back.size());
        assertEquals(1.5, back.get(0).x, 1e-9);
        assertEquals("Bob", back.get(0).name);
        assertEquals(back.get(0).id, Journal.read(j).current(stand.id), "REMAP: el uuid viejo lleva al nuevo");

        // Rehacer quita la recreada (seguida por REMAP) y vuelve a crear la movida; deshacer otra vez sigue la cadena.
        undo(j, true, Box.of(0, 1, 0, 14, 3, 3));
        assertEquals(11.5, ents.at("armor_stand").get(0).x, 1e-9);
        assertEquals(1, ents.at("armor_stand").size());
        undo(j, false, Box.of(0, 1, 0, 14, 3, 3));
        assertEquals(1, ents.at("armor_stand").size());
        assertEquals(1.5, ents.at("armor_stand").get(0).x, 1e-9);
    }

    @Test
    void H3_CA2_deshacer_salta_y_cuenta_la_creada_que_ya_no_esta() throws Exception {
        Box box = Box.of(0, 1, 0, 3, 3, 3);
        ents.add("pig", 1.5, 2, 1.5, "Pepa");
        Path j = dir.resolve("2.wcj");
        moveEntities(box, j, 10);
        ents.all.clear(); // alguien mató al cerdo movido
        EditJob u = undo(j, false, Box.of(0, 1, 0, 14, 3, 3));
        assertEquals(1, u.before().skipped(), "la que ya no está se salta y se cuenta");
        assertEquals(1, u.after().done(), "la original vuelve igual");
    }

    @Test
    void H4_CA1_butcher_sin_bloques_respeta_la_region_el_filtro_el_tope_y_espera_chunks() throws Exception {
        Box box = Box.of(-20, 0, -20, 20, 10, 20);
        for (int i = 0; i < 5; i++) ents.add("zombie", 0.5 + i, 2, 0.5, "z" + i, Kind.LIVING, Kind.MONSTER);
        ents.add("cow", 3.5, 2, 3.5, "vaca", Kind.LIVING, Kind.ANIMAL);
        ents.add("player", 1.5, 2, 1.5, "Ana", Kind.LIVING, Kind.PLAYER);
        ents.add("zombie", 100.5, 2, 0.5, "lejos", Kind.LIVING, Kind.MONSTER);
        ents.slow.add(0L); // el chunk (0, 0) tarda en cargar
        Butcher b = Butcher.of(Set.of());
        JournalSink sink = new JournalSink(dir.resolve("3.wcj"), table, Runnable::run, e -> fail(e));
        EntityStage.Scan scan = new EntityStage.Scan(ents, box, e -> b.kills(e.kinds()), true, 3, sink);
        EditJob job = new EditJob(1, ANA, "butcher", world, new EmptySource(box), EditJob.Mode.APPLY, sink, null,
                Long.MAX_VALUE, System::nanoTime).stages(scan, null);
        run(job);
        assertEquals(1, ents.waits, "esperó al chunk sin trabar");
        assertEquals(3, scan.done());
        assertTrue(scan.truncated(), "se cortó en el tope");
        assertEquals(Map.of("zombie", 3), scan.types());
        assertEquals(1, ents.at("cow").size());
        assertEquals(1, ents.at("player").size());
        assertEquals(3, ents.at("zombie").size(), "quedan 2 de la caja y el de lejos");
        assertEquals(3, Journal.read(dir.resolve("3.wcj")).entitiesOut.size());
    }

    @Test
    void H3_CA1_un_diario_sin_entidades_de_la_030_se_sigue_leyendo() throws Exception {
        Path j = dir.resolve("4.wcj");
        try (Journal w = Journal.create(j)) {
            w.append(List.of(new Journal.Change(1, 2, 3, "minecraft:stone", "minecraft:air", null, null)));
        }
        Journal.Data d = Journal.read(j);
        assertEquals(1, d.size);
        assertTrue(d.entitiesOut.isEmpty() && d.entitiesIn.isEmpty() && d.remap.isEmpty());
        // reopen agrega al final sin tocar lo de antes
        try (Journal w = Journal.reopen(j)) {
            w.appendRemap(new UUID(1, 2), new UUID(3, 4));
        }
        d = Journal.read(j);
        assertEquals(1, d.size);
        assertEquals(new UUID(3, 4), d.current(new UUID(1, 2)));
    }

    @Test
    void H1_CA3_el_portapapeles_gira_sus_entidades_al_pegar_y_al_hornearse() {
        Clipboard c = new Clipboard(Box.of(0, 0, -3, 0, 0, 0), 0, 0, 0);
        c.entities(List.of(new EntityCopy("minecraft:item_frame", 0.5, 0.5, -2.5, 180, 0, new byte[0], List.of("minecraft:item_frame"))));
        c.transform(Transform.IDENTITY.rotate90());
        EntityCopy placed = c.entitiesAt(100, 64, 100).get(0);
        assertEquals(103.5, placed.x(), 1e-9);
        assertEquals(100.5, placed.z(), 1e-9);
        assertArrayEquals(new int[]{1, 0, 0}, placed.face(0, 0, -1));
        Clipboard baked = c.baked(s -> s);
        assertEquals(3.5, baked.entities().get(0).x(), 1e-9);
        assertEquals(Set.of("minecraft:item_frame"), c.entityTypes());
    }
}
