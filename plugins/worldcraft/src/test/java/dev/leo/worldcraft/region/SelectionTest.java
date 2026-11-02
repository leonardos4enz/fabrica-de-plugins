package dev.leo.worldcraft.region;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.edit.Census;
import dev.leo.worldcraft.edit.Snapshot;
import dev.leo.worldcraft.history.HistoryStore;
import dev.leo.worldcraft.pattern.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 029 H1 (selección e información) y H6 (center, deuda de la 028). */
class SelectionTest {
    private final StateTable table = new StateTable();
    private final Parser parser = new Parser(TestBlocks.REGISTRY, Map.of(), Map.of());

    private int id(String s) {
        return table.intern(TestBlocks.REGISTRY.canonical("minecraft:" + s));
    }

    @Test
    void H1_CA3_outset_e_inset_hacia_todos_los_lados_o_solo_un_eje() {
        Box b = Box.of(0, 0, 0, 4, 4, 4);
        assertEquals(new Box(-2, -2, -2, 6, 6, 6), b.outset(2, false, false));
        assertEquals(new Box(-2, 0, -2, 6, 4, 6), b.outset(2, true, false), "--horizontal");
        assertEquals(new Box(0, -2, 0, 4, 6, 4), b.outset(2, false, true), "--vertical");
        assertEquals(new Box(2, 2, 2, 2, 2, 2), b.outset(-2, false, false), "inset 2 deja el centro");
        assertThrows(IllegalArgumentException.class, () -> b.outset(-3, false, false), "achicar de más");
        assertEquals(new Box(1, 0, 1, 3, 4, 3), b.outset(-1, true, false));
    }

    @Test
    void H1_CA2_chunk_entero_y_union() {
        assertEquals(new Box(16, -64, -32, 31, 319, -17), Box.chunk(1, -2, -64, 319));
        assertEquals(new Box(0, -64, 0, 31, 319, 15), Box.chunk(0, 0, -64, 319).union(Box.chunk(1, 0, -64, 319)));
    }

    @Test
    void H6_center_de_1_2_4_u_8_bloques() {
        assertEquals(1, Box.of(0, 0, 0, 4, 4, 4).center().volume());
        assertEquals(new Box(2, 2, 2, 2, 2, 2), Box.of(0, 0, 0, 4, 4, 4).center());
        assertEquals(new Box(1, 2, 2, 2, 2, 2), Box.of(0, 0, 0, 3, 4, 4).center(), "ancho par: 2 bloques");
        assertEquals(4, Box.of(0, 0, 0, 3, 4, 3).center().volume());
        assertEquals(new Box(1, 1, 1, 2, 2, 2), Box.of(0, 0, 0, 3, 3, 3).center(), "todo par: 8");
        assertEquals(new Box(5, 5, 5, 5, 5, 5), Box.of(5, 5, 5, 5, 5, 5).center(), "un bloque es su propio centro");
        assertEquals(new Box(-1, 0, 7, 0, 0, 7), Box.of(-3, 0, 7, 2, 0, 7).center(), "con negativos");
    }

    /** Una foto de 4×2×1: abajo piedra, piedra, tierra, aire; arriba aire salvo una piedra. */
    private Snapshot photo() {
        Snapshot s = new Snapshot(Box.of(-1, -1, -1, 4, 2, 1)); // con un borde de aire para los vecinos
        for (int x = -1; x <= 4; x++) for (int y = -1; y <= 2; y++) for (int z = -1; z <= 1; z++) s.put(x, y, z, id("air"));
        s.put(0, 0, 0, id("stone"));
        s.put(1, 0, 0, id("stone"));
        s.put(2, 0, 0, id("dirt"));
        s.put(1, 1, 0, id("stone"));
        return s;
    }

    @Test
    void H1_CA5_count_y_distr_por_material_y_por_estado() throws Exception {
        Box sel = Box.of(0, 0, 0, 3, 1, 0);
        Census c = new Census(parser.mask("stone").compile(table), null, 0, false, table).over(photo(), sel);
        assertEquals(3, c.total());
        Census all = new Census(null, null, 0, false, table).over(photo(), sel);
        assertEquals(8, all.total());
        List<Census.Row> top = all.top(10);
        assertEquals("air", top.get(0).name());
        assertEquals(4, top.get(0).count());
        assertEquals(50.0, top.get(0).percent(), 1e-9);
        assertEquals(List.of("air", "stone", "dirt"), top.stream().map(Census.Row::name).toList());
        assertEquals(2, all.top(2).size());
        assertEquals(3, all.groups());
        Snapshot logs = new Snapshot(Box.of(0, 0, 0, 1, 0, 0));
        logs.put(0, 0, 0, table.intern(TestBlocks.REGISTRY.canonical("minecraft:oak_log[axis=x]")));
        logs.put(1, 0, 0, table.intern(TestBlocks.REGISTRY.canonical("minecraft:oak_log[axis=y]")));
        assertEquals(1, new Census(null, null, 0, false, table).over(logs, logs.box).groups(), "por material: un tronco");
        assertEquals(2, new Census(null, null, 0, true, table).over(logs, logs.box).groups(), "--states: dos estados");
    }

    @Test
    void H1_CA5_count_con_vecinos_lee_la_foto_con_borde() throws Exception {
        Snapshot p = photo();
        Census c = new Census(parser.maskNear("surface").compile(table), p::get, 0, false, table).over(p, Box.of(0, 0, 0, 3, 1, 0));
        assertEquals(3, c.total(), "las dos de abajo con aire encima (x=0, x=2) y la de arriba");
    }

    @Test
    void H1_CA4_trim_a_lo_que_cumple() throws Exception {
        Census c = new Census(parser.mask("!air").compile(table), null, 0, false, table).over(photo(), Box.of(-1, -1, -1, 4, 2, 1));
        assertEquals(new Box(0, 0, 0, 2, 1, 0), c.bounds());
        assertNull(new Census(parser.mask("gold_block").compile(table), null, 0, false, table).over(photo(), Box.of(0, 0, 0, 3, 1, 0)).bounds());
    }

    @TempDir
    Path dir;

    @Test
    void H1_CA6_clearhistory_borra_lo_propio_menos_lo_que_esta_en_marcha() throws Exception {
        UUID ana = new UUID(1, 1), beto = new UUID(2, 2);
        HistoryStore h = new HistoryStore(dir, Runnable::run);
        Box b = Box.of(0, 0, 0, 1, 1, 1);
        HistoryStore.Entry e1 = h.finish(h.begin(ana, "Ana", "set", "stone", "world", b, 1), 8, true);
        Files.createDirectories(h.journal(ana, e1.id()).getParent());
        Files.write(h.journal(ana, e1.id()), new byte[]{1});
        h.finish(h.begin(ana, "Ana", "set", "dirt", "world", b, 2), 8, true);
        h.begin(ana, "Ana", "set", "glass", "world", b, 3); // en marcha
        h.finish(h.begin(beto, "Beto", "set", "stone", "world", b, 4), 8, true);
        assertEquals(2, h.clear(ana));
        assertEquals(1, h.list(ana).size(), "queda la que está en marcha");
        assertTrue(h.lastUndoable(ana).isEmpty());
        assertFalse(Files.exists(h.journal(ana, e1.id())), "el diario se borra del disco");
        assertEquals(1, h.list(beto).size(), "lo de Beto no se toca");
        assertEquals(0, h.clear(new UUID(9, 9)));
    }
}
