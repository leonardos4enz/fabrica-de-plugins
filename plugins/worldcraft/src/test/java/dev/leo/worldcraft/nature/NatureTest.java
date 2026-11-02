package dev.leo.worldcraft.nature;

import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.edit.Snapshot;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.structure.Templates;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 033 H5–H7: dónde van los árboles, las plantas y las calabazas; los árboles juntos; los nombres; las plantillas. */
class NatureTest {
    final StateTable table = new StateTable();
    final int air = table.intern("minecraft:air"), grass = table.intern("minecraft:grass_block"), stone = table.intern("minecraft:stone"),
            sand = table.intern("minecraft:sand"), shortGrass = table.intern("minecraft:short_grass"),
            log = table.intern("minecraft:oak_log[axis=y]"), leaves = table.intern("minecraft:oak_leaves[distance=1,persistent=false,waterlogged=false]"),
            pumpkin = table.intern("minecraft:pumpkin"), jigsaw = table.intern("minecraft:jigsaw");
    final IntPredicate free = s -> s == air || s == shortGrass;

    /** Un suelo de 20×20 en y = 0: pasto en x < 10, arena en x >= 10, piedra en z = 0; aire arriba hasta y = 3. */
    Snapshot ground() {
        Snapshot snap = new Snapshot(Box.of(0, 0, 0, 19, 3, 19));
        for (int x = 0; x < 20; x++) for (int z = 0; z < 20; z++) {
            snap.put(x, 0, z, z == 0 ? stone : x < 10 ? grass : sand);
            for (int y = 1; y <= 3; y++) snap.put(x, y, z, air);
        }
        return snap;
    }

    @Test
    void H5_CA2_arboles_solo_sobre_el_suelo_con_la_densidad() {
        Snapshot snap = ground();
        Box region = Box.of(0, 0, 0, 19, 2, 19);
        long[] all = NatureMath.spots(snap, region, s -> s == grass, free, 100, 7, 1000);
        assertEquals(10 * 19, all.length, "toda la mitad de pasto menos la fila de piedra");
        for (long p : all) {
            assertEquals(1, PointsSource.unpackY(p), "en el aire de encima del suelo");
            assertTrue(PointsSource.unpackX(p) < 10 && PointsSource.unpackZ(p) > 0);
        }
        long[] some = NatureMath.spots(snap, region, s -> s == grass, free, 20, 7, 1000);
        assertTrue(some.length > 15 && some.length < 65, "alrededor del 20 %: " + some.length);
        assertArrayEquals(some, NatureMath.spots(snap, region, s -> s == grass, free, 20, 7, 1000), "la misma semilla da lo mismo");
        assertEquals(5, NatureMath.spots(snap, region, s -> s == grass, free, 100, 7, 5).length, "max_trees corta");
        // Una planta encima no tapa el suelo: sigue habiendo lugar.
        snap.put(1, 1, 1, shortGrass);
        assertTrue(java.util.Arrays.stream(NatureMath.spots(snap, region, s -> s == grass, free, 100, 7, 1000))
                .anyMatch(p -> p == PointsSource.pack(1, 1, 1)));
    }

    @Test
    void H6_CA1_flora_por_suelo_y_el_cactus_con_los_costados_libres() {
        Snapshot snap = ground();
        snap.put(15, 1, 15, stone); // un bloque al lado de (14, 1, 15): ahí no va cactus
        List<NatureMath.Plant> p = NatureMath.flora(snap, Box.of(0, 0, 0, 19, 2, 19), s -> s == grass, s -> s == sand, free, 100, 3);
        assertTrue(p.stream().anyMatch(NatureMath.Plant::onSand));
        assertTrue(p.stream().anyMatch(x -> !x.onSand()));
        assertTrue(p.stream().noneMatch(x -> x.z() == 0), "no sobre la piedra");
        NatureMath.Plant next = p.stream().filter(x -> x.x() == 14 && x.z() == 15).findFirst().orElseThrow();
        assertFalse(next.sidesFree());
        assertTrue(p.stream().filter(x -> x.x() == 12 && x.z() == 12).findFirst().orElseThrow().sidesFree());
    }

    @Test
    void H6_CA2_matas_de_calabazas_con_hojas_sobre_el_pasto() {
        Snapshot snap = ground();
        Map<Long, Integer> m = NatureMath.pumpkins(snap, Box.of(0, 0, 0, 19, 2, 19), s -> s == grass, free, 5, 11, pumpkin, leaves);
        long pumpkins = m.values().stream().filter(v -> v == pumpkin).count();
        assertTrue(pumpkins > 0);
        assertTrue(m.values().stream().filter(v -> v == leaves).count() >= 2 * pumpkins - 2, "de 2 a 4 hojas por calabaza (menos en el borde)");
        for (long k : m.keySet()) {
            assertEquals(1, PointsSource.unpackY(k));
            assertEquals(grass, snap.get(PointsSource.unpackX(k), 0, PointsSource.unpackZ(k)), "todo sobre el pasto");
        }
    }

    @Test
    void H5_CA1_los_arboles_juntos_el_tronco_gana_a_la_hoja() {
        Map<Long, Integer> a = new LinkedHashMap<>(), b = new LinkedHashMap<>();
        a.put(1L, leaves);
        a.put(2L, log);
        b.put(1L, log);
        b.put(2L, leaves);
        b.put(3L, leaves);
        Map<Long, Integer> m = NatureMath.merge(List.of(a, b), s -> s == log);
        assertEquals(log, m.get(1L));
        assertEquals(log, m.get(2L));
        assertEquals(leaves, m.get(3L));
    }

    @Test
    void H5_CA1_nombres_de_worldedit_alias_y_sugerencia() {
        assertEquals(TreeKind.SPRUCE, TreeKind.parse("redwood"));
        assertEquals(TreeKind.BIG_OAK, TreeKind.parse("bigtree"));
        assertEquals("TREE", TreeKind.parse("oak").paper);
        assertEquals("#nylium", TreeKind.parse("crimson").extraSoil());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> TreeKind.parse("brich"));
        assertTrue(e.getMessage().contains("did you mean 'birch'"), e.getMessage());
        TreeKind r = TreeKind.RANDOM.pick(new Random(1));
        assertNotEquals(TreeKind.RANDOM, r);
        assertNotNull(r.paper);
        for (TreeKind k : TreeKind.values()) if (k != TreeKind.RANDOM) assertNotNull(k.paper);
    }

    @Test
    void H7_nombres_lista_y_plantilla_al_portapapeles() {
        assertEquals("minecraft:igloo/top", Templates.key("igloo/top"));
        assertEquals("mypack:house", Templates.key("MyPack:House"));
        assertNull(Templates.key("../../x"));
        assertNull(Templates.key("igloo/../../x"));
        assertNull(Templates.key("igloo top"));
        List<String> all = List.of("minecraft:igloo/top", "minecraft:igloo/bottom", "minecraft:shipwreck/sideways_full", "mypack:house");
        assertEquals(List.of("igloo/bottom", "igloo/top"), Templates.filter(all, "igloo"));
        assertTrue(Templates.hint("iglo/top", all).contains("igloo/top"));
        // 2×1×2: un jigsaw (queda aire), una piedra con datos, y una posición que la plantilla no trae (structure_void).
        Clipboard c = Templates.toClipboard(2, 1, 2, List.of(new Templates.Block(0, 0, 0, jigsaw, null),
                new Templates.Block(1, 0, 0, stone, new byte[]{'L'}), new Templates.Block(0, 0, 1, stone, null),
                new Templates.Block(5, 0, 0, stone, null)), s -> s == jigsaw, air);
        int[] raw = c.rawStates();
        assertEquals(air, raw[0]);
        assertEquals(stone, raw[1]);
        assertEquals(stone, raw[2]);
        assertEquals(-1, raw[3], "lo que no trae no se pega");
        assertEquals(Set.of(1), c.rawTiles().keySet());
        assertArrayEquals(new int[]{0, 0, 0}, c.offset(), "la esquina mínima a los pies");
    }
}
