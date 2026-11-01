package dev.leo.worldcraft.terrain;

import dev.leo.worldcraft.edit.EditJob;
import dev.leo.worldcraft.region.Box;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** El ruido, la montaña y la isla como formas (T1–T3). */
class ShapeTest {
    final TerrainLab lab = new TerrainLab();

    @Test
    void T1_ruido_determinista_acotado_y_continuo() {
        Noise a = new Noise(42), b = new Noise(42), c = new Noise(43);
        double diff = 0, min = 9, max = -9;
        for (int i = 0; i < 2000; i++) {
            double x = i * 0.137, z = i * 0.071;
            assertEquals(a.at(x, z), b.at(x, z), "misma semilla, mismo valor");
            diff += Math.abs(a.at(x, z) - c.at(x, z));
            min = Math.min(min, a.fbm(x, z, 5));
            max = Math.max(max, a.fbm(x, z, 5));
            double r = a.ridged(x, z, 5);
            assertTrue(r >= 0 && r <= 1, "crestas en [0, 1]: " + r);
            assertTrue(Math.abs(a.at(x, z) - a.at(x + 0.001, z)) < 0.01, "continuo");
        }
        assertTrue(diff / 2000 > 0.1, "otra semilla, otro ruido");
        assertTrue(min >= -1 && max <= 1 && max - min > 0.5, "fbm en [-1, 1] y variado: " + min + ".." + max);
        assertEquals(0, a.at(3, 7), 1e-9, "en las esquinas de la rejilla vale 0");
    }

    @Test
    void H1_CA2_la_montana_mide_lo_pedido_cae_a_cero_en_el_borde_y_no_es_un_cono() {
        TerrainLab.Mountain m = lab.planMountain(0, 0, -1, "medium");
        int r = m.options().integer("radius"), h = m.options().integer("height");
        assertEquals(-1, m.plan().base(), "el pie es el suelo del centro");
        assertEquals(-1 + h, m.plan().peak(), "la cima mide height");
        HeightField f = m.field();
        int[] top = m.plan().newTop();
        for (int i = 0; i < f.size(); i++) {
            double d = Math.hypot(f.x(i), f.z(i));
            if (d > r * 1.25) assertEquals(HeightField.NONE, top[i], "nada lejos del radio");
        }
        // Crestas: en un anillo a media ladera la altura no es pareja (un cono liso daría casi la misma).
        double sum = 0, sq = 0;
        int n = 0;
        for (int k = 0; k < 64; k++) {
            double a = Math.PI * 2 * k / 64;
            int i = f.index((int) Math.round(Math.cos(a) * r * 0.4), (int) Math.round(Math.sin(a) * r * 0.4));
            int t = top[i] == HeightField.NONE ? -1 : top[i];
            sum += t;
            sq += (double) t * t;
            n++;
        }
        double sd = Math.sqrt(sq / n - (sum / n) * (sum / n));
        assertTrue(sd > 2, "laderas irregulares: desviación " + sd);
    }

    @Test
    void H4_CA2_la_misma_semilla_da_lo_mismo_y_otra_da_otra() {
        int[] a = lab.planMountain(0, 0, -1, "small", "seed=99").plan().newTop();
        int[] b = new TerrainLab().planMountain(0, 0, -1, "small", "seed=99").plan().newTop();
        int[] c = lab.planMountain(0, 0, -1, "small", "seed=100").plan().newTop();
        assertArrayEquals(a, b);
        assertFalse(java.util.Arrays.equals(a, c));
    }

    @Test
    void H4_CA1_crater_y_picos() {
        TerrainLab.Mountain v = lab.planMountain(0, 0, -1, "style=volcano", "seed=5");
        assertTrue(v.options().number("crater") > 0, "el volcán trae su cráter");
        int center = v.plan().newTop()[v.field().index(0, 0)];
        assertTrue(center < v.plan().peak() - 5, "el centro del cráter está hundido: " + center + " vs " + v.plan().peak());
        MountainShape one = TerrainLab.mountain(lab.options(Options.Tool.MOUNTAIN, "peaks=1"));
        MountainShape five = TerrainLab.mountain(lab.options(Options.Tool.MOUNTAIN, "peaks=5"));
        assertEquals(1, one.peakList().length);
        assertEquals(5, five.peakList().length);
    }

    @Test
    void H5_CA3_medium_cabe_en_el_grupo_default() {
        TerrainLab.Mountain m = lab.planMountain(0, 0, -1, "medium", "seed=1");
        long v = lab.mountainSource(m, false).volume();
        assertTrue(v > 10_000 && v < 50_000, "medium: " + v + " bloques");
        Options.Resolved o = lab.options(Options.Tool.ISLAND, "medium", "seed=1");
        long iv = lab.islandSource(TerrainLab.island(o).plan(0, 80, 0, -64, 319), o, false).volume();
        assertTrue(iv > 1_000 && iv < 50_000, "isla medium: " + iv);
    }

    @Test
    void H2_CA2_la_isla_tiene_cima_casi_plana_base_honda_al_centro_estalactitas_raices_y_arboles() {
        Options.Resolved o = lab.options(Options.Tool.ISLAND, "large", "seed=8", "trees=4", "spikes=1", "roots=1");
        IslandShape.Plan p = TerrainLab.island(o).plan(0, 80, 0, -64, 319);
        int r = o.integer("radius");
        int ci = p.index(0, 0);
        int topMin = Integer.MAX_VALUE, topMax = Integer.MIN_VALUE, roots = 0, cols = 0;
        for (int i = 0; i < p.top().length; i++) {
            if (p.top()[i] == HeightField.NONE) continue;
            cols++;
            topMin = Math.min(topMin, p.top()[i]);
            topMax = Math.max(topMax, p.top()[i]);
            if (p.root()[i] != HeightField.NONE) roots++;
            int x = p.rect().minX() + i % p.rect().width(), z = p.rect().minZ() + i / p.rect().width();
            assertTrue(Math.hypot(x, z) < r * 1.35, "nada fuera del contorno");
        }
        assertTrue(topMax - topMin <= Math.max(6, r * 0.3), "cima casi plana: " + topMin + ".." + topMax);
        int centerDepth = p.top()[ci] - p.bottom()[ci];
        assertTrue(centerDepth > r * 0.5, "honda en el centro: " + centerDepth);
        int edge = p.index((int) (r * 0.85), 0);
        if (p.top()[edge] != HeightField.NONE) assertTrue(p.top()[edge] - p.bottom()[edge] < centerDepth, "más delgada en el borde");
        assertTrue(roots > 0, "raíces en el borde");
        assertTrue(cols > Math.PI * r * r * 0.5, "es una isla, no unas columnas: " + cols);
        long logs = p.trees().values().stream().filter(t -> t == IslandShape.LOG).count();
        assertTrue(logs >= 4 * 4, "cuatro árboles con tronco: " + logs);
        assertTrue(p.minY() < 80 - r * 0.9, "las estalactitas cuelgan más abajo que la base: " + p.minY());
    }

    @Test
    void H2_CA3_la_isla_solo_pone_en_el_aire_y_sus_hojas_no_se_caen() {
        Options.Resolved o = lab.options(Options.Tool.ISLAND, "small", "seed=3", "trees=2");
        IslandShape.Plan p = TerrainLab.island(o).plan(0, 6, 0, -64, 319);
        // La base de la isla baja hasta el suelo (y = -1): el pasto y la tierra no se tocan.
        lab.run(lab.islandSource(p, o, false), EditJob.Mode.APPLY, null, null);
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            assertEquals("minecraft:grass_block", lab.world.at(x, -1, z));
            assertEquals("minecraft:dirt", lab.world.at(x, -2, z));
        }
        boolean leaves = false;
        for (Map.Entry<Long, Integer> e : lab.world.blocks.entrySet()) {
            String st = lab.table.state(e.getValue());
            if (st.contains("leaves")) {
                leaves = true;
                assertTrue(st.contains("persistent=true"), st);
            }
        }
        assertTrue(leaves);
    }

    @Test
    void H1_CA4_la_piel_es_parte_de_la_obra_y_tapa_lo_que_se_ve() {
        TerrainLab.Mountain m = lab.planMountain(0, 0, -1, "small", "seed=11");
        Map<Long, Integer> skin = lab.preview(lab.mountainSource(m, true));
        Map<Long, Integer> full = lab.preview(lab.mountainSource(m, false));
        assertTrue(full.entrySet().containsAll(skin.entrySet()), "la piel pone lo mismo que la obra en esos lugares");
        assertTrue(skin.size() < full.size() / 2, "la piel es mucho menos: " + skin.size() + " de " + full.size());
        // Todo bloque de la obra con aire encima (lo que se ve desde arriba) está en la piel.
        for (Long k : full.keySet()) {
            int x = (int) (k >> 38), z = (int) (k << 26 >> 38), y = (int) (k << 52 >> 52);
            if (!full.containsKey(IslandShape.key(x, y + 1, z))) assertTrue(skin.containsKey(k), "cima en la piel");
        }
    }

}
