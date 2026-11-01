package dev.leo.worldcraft.terrain;

import dev.leo.worldcraft.edit.EditJob;
import dev.leo.worldcraft.edit.UndoSource;
import dev.leo.worldcraft.history.Journal;
import dev.leo.worldcraft.region.Box;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/** Leer el suelo, montañas y pinceles corridos por el motor sobre el mundo en memoria (T4). */
class TerrainEditTest {
    final TerrainLab lab = new TerrainLab();
    @TempDir
    Path dir;

    void put(int x, int y, int z, String s) {
        lab.world.put(x, y, z, "minecraft:" + s);
    }

    /** Una casita de tablas (2×3×2) en (5, 0, 5) y un árbol en (-5, 0, -5) con una flor al lado. */
    void scenery() {
        for (int x = 5; x <= 6; x++) for (int z = 5; z <= 6; z++) for (int y = 0; y <= 2; y++) put(x, y, z, "oak_planks");
        for (int y = 0; y <= 3; y++) put(-5, y, -5, "oak_log");
        put(-5, 4, -5, "oak_leaves");
        put(-4, 0, -5, "poppy");
    }

    @Test
    void H5_CA2_leer_el_suelo_por_tandas_distingue_suelo_plantas_arboles_y_construcciones() {
        scenery();
        put(0, -1, 0, "stone");
        HeightField f = lab.scan(new Box(-8, 0, -8, 8, 0, 8), 20, -20);
        int i = f.index(1, 1);
        assertEquals(-1, f.ground(i));
        assertEquals("minecraft:grass_block", lab.table.state(f.surface(i)));
        assertEquals("minecraft:dirt", lab.table.state(f.under(i)));
        assertEquals(HeightField.CLEAR, f.obstacle(i));
        assertEquals("minecraft:stone", lab.table.state(f.surface(f.index(0, 0))));
        assertEquals(HeightField.HARD, f.obstacle(f.index(5, 5)), "la casa es una construcción");
        assertEquals(-1, f.ground(f.index(5, 5)), "el suelo bajo la casa");
        assertEquals(HeightField.BURYABLE, f.obstacle(f.index(-5, -5)), "el árbol se puede enterrar");
        assertEquals(0, f.cover(f.index(-4, -5)), "la flor es cobertura");
        assertEquals(HeightField.CLEAR, f.obstacle(f.index(-4, -5)));
    }

    @Test
    void H1_CA3_H5_CA1_la_montana_solo_agrega_respeta_casas_y_se_deshace_exacta() throws Exception {
        scenery();
        Map<Long, Integer> before = new HashMap<>(lab.world.blocks);
        TerrainLab.Mountain m = lab.planMountain(0, 0, -1, "small", "seed=4");
        Path j = dir.resolve("m.wcj");
        EditJob job = lab.run(lab.mountainSource(m, false), EditJob.Mode.APPLY, null, j);
        assertEquals(EditJob.State.DONE, job.state());
        assertTrue(job.changed() > 3000, "puso la montaña: " + job.changed());
        for (int y = 0; y <= 2; y++) assertEquals("minecraft:oak_planks", lab.world.at(5, y, 5), "la casa sigue");
        assertEquals("minecraft:air", lab.world.at(5, 3, 5), "y no se entierra: su columna no se toca");
        assertEquals("minecraft:grass_block", lab.world.at(0, -1, 0), "el suelo que había no se pisa");
        int top = m.plan().peak();
        assertEquals("minecraft:snow_block", lab.world.at(m.field().x(argPeak(m)), top, m.field().z(argPeak(m))), "nieve en la cima");
        // Deshacer con el diario deja todo como estaba.
        Journal.Data d = Journal.read(j);
        lab.run(new UndoSource(CompletableFuture.completedFuture(d), lab.table, false, job.bounds()), EditJob.Mode.APPLY, null, null);
        Map<Long, Integer> after = new HashMap<>(lab.world.blocks);
        after.values().removeIf(v -> v == lab.world.air);
        before.values().removeIf(v -> v == lab.world.air);
        assertEquals(before, after);
    }

    int argPeak(TerrainLab.Mountain m) {
        int best = 0;
        for (int i = 0; i < m.plan().newTop().length; i++) if (m.plan().newTop()[i] > m.plan().newTop()[best]) best = i;
        return best;
    }

    @Test
    void H1_CA5_lo_que_se_construye_es_lo_que_mostro_la_vista_previa() {
        TerrainLab.Mountain m = lab.planMountain(0, 0, -1, "small", "seed=21");
        Map<Long, Integer> skin = lab.preview(lab.mountainSource(m, true));
        lab.run(lab.mountainSource(m, false), EditJob.Mode.APPLY, null, null);
        for (Map.Entry<Long, Integer> e : skin.entrySet()) {
            long k = e.getKey();
            int x = (int) (k >> 38), z = (int) (k << 26 >> 38), y = (int) (k << 52 >> 52);
            assertEquals(e.getValue(), lab.world.get(x, y, z));
        }
    }

    HeightSource brush(BrushSettings b, int cx, int cz, java.util.function.IntPredicate mask) {
        int m = Brush.margin(b.mode(), b.radius()) + b.radius();
        HeightField f = lab.scan(new Box(cx - m, 0, cz - m, cx + m, 0, cz + m), 30, -30);
        Brush.Result r = Brush.apply(b, f, cx, cz, -1, mask, 7);
        boolean paint = b.mode() == BrushSettings.Mode.PAINT;
        Style style = paint ? (b.pattern() != null ? Style.of(pattern(b.pattern())) : lab.cfg.styles().get(b.style())) : null;
        int[] g = new int[f.size()];
        for (int i = 0; i < g.length; i++) g[i] = f.ground(i);
        return new HeightSource(f, r.newTop(), paint ? HeightSource.Mode.PAINT : HeightSource.Mode.PRESERVE,
                style == null ? null : style.painter(lab.table, 7), HeightField.slopes(g, f.width, f.depth), r.base(), r.span(),
                lab.masks, paint ? style.paintDepth() : 1, lab.world.air, false);
    }

    dev.leo.worldcraft.pattern.Pattern pattern(String s) {
        try {
            return lab.parser.pattern(s);
        } catch (dev.leo.worldcraft.pattern.ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    int ground(int x, int z) {
        for (int y = 40; y > -40; y--) if (lab.masks.ground(lab.world.get(x, y, z))) return y;
        return Integer.MIN_VALUE;
    }

    @Test
    void H3_CA4_subir_y_bajar_conservan_el_pasto_arriba_y_quitan_las_plantas() {
        put(2, 0, 0, "poppy");
        lab.run(brush(new BrushSettings(BrushSettings.Mode.RAISE, 5, 1, null, null, null), 0, 0, null), EditJob.Mode.APPLY, null, null);
        int g = ground(0, 0);
        assertTrue(g >= 1, "subió: " + g);
        assertEquals("minecraft:grass_block", lab.world.at(0, g, 0), "pasto arriba");
        assertEquals("minecraft:dirt", lab.world.at(0, g - 1, 0), "tierra debajo");
        assertEquals("minecraft:dirt", lab.world.at(0, -1, 0), "el viejo pasto quedó enterrado como tierra");
        assertTrue(ground(4, 0) < g, "baja hacia el borde");
        lab.run(brush(new BrushSettings(BrushSettings.Mode.LOWER, 6, 1, null, null, null), 20, 0, null), EditJob.Mode.APPLY, null, null);
        int low = ground(20, 0);
        assertTrue(low <= -3, "bajó: " + low);
        assertEquals("minecraft:grass_block", lab.world.at(20, low, 0), "sigue con pasto arriba");
        assertEquals("minecraft:air", lab.world.at(20, -1, 0));
        put(30, 0, 0, "poppy");
        lab.run(brush(new BrushSettings(BrushSettings.Mode.LOWER, 3, 1, null, null, null), 30, 0, null), EditJob.Mode.APPLY, null, null);
        assertEquals("minecraft:air", lab.world.at(30, 0, 0), "la flor no queda flotando");
    }

    @Test
    void H3_CA3_suavizar_no_toca_arboles_ni_casas_y_baja_un_pico() {
        scenery();
        for (int y = 0; y <= 6; y++) put(1, y, 1, "stone"); // un pico de piedra de 7 bloques
        lab.run(brush(new BrushSettings(BrushSettings.Mode.SMOOTH, 8, 1, null, null, null), 0, 0, null), EditJob.Mode.APPLY, null, null);
        assertTrue(ground(1, 1) < 5, "el pico bajó: " + ground(1, 1));
        for (int y = 0; y <= 3; y++) assertEquals("minecraft:oak_log", lab.world.at(-5, y, -5), "el árbol sigue");
        assertEquals("minecraft:oak_planks", lab.world.at(5, 0, 5), "la casa sigue");
        assertEquals("minecraft:grass_block", lab.world.at(-5, -1, -5), "bajo el árbol no se tocó");
    }

    @Test
    void H3_CA3_aplanar_y_erosionar() {
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = 0; y < 3 + (x + 3); y++) put(x, y, z, "stone");
        lab.run(brush(new BrushSettings(BrushSettings.Mode.FLATTEN, 6, 1, null, null, null), 0, 0, null), EditJob.Mode.APPLY, null, null);
        int a = ground(-2, 0), b = ground(2, 0);
        assertTrue(Math.abs(a - b) <= 1, "aplanado cerca del centro: " + a + " y " + b);

        TerrainLab other = new TerrainLab();
        // Un escalón de 8 bloques: la erosión lo derrumba hacia abajo.
        for (int x = 0; x <= 4; x++) for (int z = -4; z <= 4; z++) for (int y = 0; y < 8; y++) other.world.put(x, y, z, "minecraft:stone");
        int before = maxStep(other);
        int m = Brush.margin(BrushSettings.Mode.ERODE, 6) + 6;
        HeightField f = other.scan(new Box(-m, 0, -m, m, 0, m), 30, -30);
        Brush.Result r = Brush.apply(new BrushSettings(BrushSettings.Mode.ERODE, 6, 1, null, null, null), f, 0, 0, 7, null, 1);
        other.run(new HeightSource(f, r.newTop(), HeightSource.Mode.PRESERVE, null, new float[f.size()],
                r.base(), r.span(), other.masks, 1, other.world.air, false), EditJob.Mode.APPLY, null, null);
        int after = maxStep(other);
        assertTrue(after < before, "el escalón se derrumbó: " + before + " → " + after);
    }

    int maxStep(TerrainLab l) {
        int max = 0;
        for (int x = -3; x <= 3; x++) {
            int a = top(l, x, 0), b = top(l, x + 1, 0);
            max = Math.max(max, Math.abs(a - b));
        }
        return max;
    }

    int top(TerrainLab l, int x, int z) {
        for (int y = 40; y > -40; y--) if (l.masks.ground(l.world.get(x, y, z))) return y;
        return 0;
    }

    @Test
    void H4_CA4_pintar_por_pendiente_con_estilo_y_con_mascara() {
        for (int x = 0; x <= 2; x++) for (int y = 0; y < 8; y++) for (int z = -6; z <= 6; z++) put(x, y, z, "dirt");
        lab.run(brush(new BrushSettings(BrushSettings.Mode.PAINT, 7, 1, "alpine", null, null), 0, 0, null), EditJob.Mode.APPLY, null, null);
        assertEquals("minecraft:grass_block", lab.world.at(-5, -1, 0), "lo plano y bajo, pasto");
        String wall = lab.world.at(0, 7, 0);
        assertTrue(wall.contains("stone") || wall.contains("andesite") || wall.contains("gravel") || wall.contains("snow"),
                "lo empinado o alto no es pasto: " + wall);
        // Con máscara: solo donde la superficie es pasto.
        TerrainLab l2 = new TerrainLab();
        l2.world.put(1, -1, 0, "minecraft:sand");
        int sand = l2.table.intern("minecraft:sand");
        int m = 4;
        HeightField f = l2.scan(new Box(-m, 0, -m, m, 0, m), 10, -10);
        BrushSettings b = new BrushSettings(BrushSettings.Mode.PAINT, 3, 1, null, "snow_block", null);
        Brush.Result r = Brush.apply(b, f, 0, 0, -1, s -> s != sand, 1);
        l2.run(new HeightSource(f, r.newTop(), HeightSource.Mode.PAINT, Style.of(pattern("snow_block")).painter(l2.table, 1),
                new float[f.size()], r.base(), r.span(), l2.masks, 1, l2.world.air, false), EditJob.Mode.APPLY, null, null);
        assertEquals("minecraft:snow_block", l2.world.at(0, -1, 0));
        assertEquals("minecraft:sand", l2.world.at(1, -1, 0), "la máscara dejó la arena");
    }
}
