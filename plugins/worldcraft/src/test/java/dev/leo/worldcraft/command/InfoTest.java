package dev.leo.worldcraft.command;

import dev.leo.worldcraft.region.Box;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 040 H4: chunkinfo, listchunks y searchitem. */
class InfoTest {
    @Test
    void H4_CA1_chunk_y_region_tambien_negativos() {
        assertEquals("r.0.0.mca", Info.region(0, 0));
        assertEquals("r.-1.0.mca", Info.region(-1, 31));
        assertEquals("r.1.-2.mca", Info.region(32, -33));
        assertArrayEquals(new int[]{-1, 0, 15, 64, 2}, Info.chunk(-1, 64, 2), "chunk x, z y lugar dentro (x, y, z)");
    }

    @Test
    void H4_CA2_los_chunks_de_la_seleccion_por_paginas() {
        List<int[]> all = Info.chunks(new Box(-1, 0, 0, 40, 10, 15));
        assertEquals(4, all.size(), "x de -1 a 2, z 0");
        assertArrayEquals(new int[]{-1, 0}, all.get(0));
        assertEquals(List.of(), Info.page(all, 3, 2));
        assertEquals(2, Info.page(all, 2, 2).size());
        assertEquals(2, Info.pages(all.size(), 2));
    }

    @Test
    void H4_CA3_buscar_por_nombre_primero_lo_exacto() {
        List<String> keys = List.of("minecraft:stone", "minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:redstone",
                "minecraft:diamond");
        assertEquals(List.of("minecraft:stone", "minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:redstone"),
                Info.search(keys, "stone"));
        assertEquals(List.of("minecraft:stone_bricks"), Info.search(keys, "stone brick"), "un espacio vale como _");
        assertEquals(List.of(), Info.search(keys, "zzz"));
    }
}
