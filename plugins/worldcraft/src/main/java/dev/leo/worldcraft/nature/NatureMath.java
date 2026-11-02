package dev.leo.worldcraft.nature;

import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.edit.Snapshot;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Dónde van los árboles, las plantas y las calabazas (spec 033 H5, H6), sobre la foto leída antes ({@link Snapshot}).
 * Todo al azar con la semilla de la obra: la vista previa y la obra eligen lo mismo. Java puro (corre en el hilo de
 * cálculo: lo que pregunta al registro llega como conjuntos ya armados).
 */
public final class NatureMath {
    private NatureMath() {
    }

    /** Un número de 0 a 1 para esa columna y esa semilla. */
    static double chance(int x, int y, int z, long seed) {
        return (Pattern.mix(x, y, z, seed) >>> 11) * 0x1.0p-53;
    }

    /**
     * La superficie de cada columna de la región: el bloque más alto que no es libre (aire o plantas) con uno libre encima,
     * dentro de la región y de la foto; -1 en y si no hay. Devuelve {x, y, z} por columna con superficie.
     */
    static List<int[]> surface(Snapshot snap, Region region, IntPredicate free) {
        List<int[]> out = new ArrayList<>();
        Box b = region.bounds();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                int[] span = region.span(x, z);
                if (span == null) continue;
                for (int y = Math.min(span[1], snap.box.maxY() - 1); y >= Math.max(span[0], snap.box.minY()); y--) {
                    int s = snap.get(x, y, z);
                    if (s < 0 || free.test(s)) continue;
                    int above = snap.get(x, y + 1, z);
                    if (above >= 0 && free.test(above)) out.add(new int[]{x, y, z});
                    break;
                }
            }
        }
        return out;
    }

    /**
     * H5 CA2: dónde se planta un árbol: sobre el suelo (soil) de la superficie, en el bloque libre de encima, con
     * probabilidad density % por columna; a lo sumo max (los primeros en orden de columnas).
     */
    public static long[] spots(Snapshot snap, Region region, IntPredicate soil, IntPredicate free, double density, long seed, int max) {
        List<Long> out = new ArrayList<>();
        for (int[] p : surface(snap, region, free)) {
            if (!soil.test(snap.get(p[0], p[1], p[2]))) continue;
            if (chance(p[0], p[1], p[2], seed) >= density / 100.0) continue;
            out.add(PointsSource.pack(p[0], p[1] + 1, p[2]));
            if (out.size() >= max) break;
        }
        return out.stream().mapToLong(Long::longValue).toArray();
    }

    /** H6 CA1: una planta encima de la superficie; onSand si va sobre arena; sidesFree si sus 4 costados están libres (cactus). */
    public record Plant(int x, int y, int z, boolean onSand, boolean sidesFree) {
    }

    public static List<Plant> flora(Snapshot snap, Region region, IntPredicate grass, IntPredicate sand, IntPredicate free,
                                    double density, long seed) {
        List<Plant> out = new ArrayList<>();
        for (int[] p : surface(snap, region, free)) {
            int ground = snap.get(p[0], p[1], p[2]);
            boolean onGrass = grass.test(ground), onSand = sand.test(ground);
            if (!onGrass && !onSand) continue;
            int y = p[1] + 1;
            // Solo sobre aire de verdad (no encima de otra planta) para no apilar.
            if (snap.get(p[0], y, p[2]) < 0) continue;
            if (chance(p[0], p[1], p[2], seed ^ 0x9E3779B97F4A7C15L) >= density / 100.0) continue;
            boolean sides = isFree(snap, free, p[0] + 1, y, p[2]) && isFree(snap, free, p[0] - 1, y, p[2])
                    && isFree(snap, free, p[0], y, p[2] + 1) && isFree(snap, free, p[0], y, p[2] - 1);
            out.add(new Plant(p[0], y, p[2], onSand, sides));
        }
        return out;
    }

    private static boolean isFree(Snapshot snap, IntPredicate free, int x, int y, int z) {
        int s = snap.get(x, y, z);
        return s < 0 || free.test(s);
    }

    /**
     * H6 CA2: matas de calabazas: sobre el pasto de la superficie, con probabilidad density %, una calabaza y de 2 a 4 hojas en las columnas vecinas que también sean pasto con aire encima a la misma altura.
     */
    public static Map<Long, Integer> pumpkins(Snapshot snap, Region region, IntPredicate grass, IntPredicate free, double density,
                                              long seed, int pumpkin, int leaves) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        Map<Long, Integer> top = new HashMap<>();
        List<int[]> surf = surface(snap, region, free);
        for (int[] p : surf) top.put(PointsSource.pack(p[0], 0, p[2]), p[1]);
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] p : surf) {
            if (!grass.test(snap.get(p[0], p[1], p[2]))) continue;
            if (chance(p[0], p[1], p[2], seed ^ 0x51ED27L) >= density / 100.0) continue;
            long at = PointsSource.pack(p[0], p[1] + 1, p[2]);
            if (out.containsKey(at)) continue;
            long h = Pattern.mix(p[0], p[1], p[2], seed);
            out.put(at, pumpkin);
            int want = 2 + (int) Math.floorMod(h >>> 8, 3L);
            for (int k = 0, put = 0; k < around.length && put < want; k++) {
                int[] d = around[(int) Math.floorMod((h >>> 16) + k, (long) around.length)];
                int nx = p[0] + d[0], nz = p[2] + d[1];
                Integer ny = top.get(PointsSource.pack(nx, 0, nz));
                if (ny == null || ny != p[1] || !grass.test(snap.get(nx, ny, nz))) continue;
                long l = PointsSource.pack(nx, ny + 1, nz);
                if (out.containsKey(l)) continue;
                out.put(l, leaves);
                put++;
            }
        }
        return out;
    }

    /**
     * Los árboles capturados, juntos (plan 033 §1.4): cada uno vio el mundo de antes, así que dos que se tocan pueden
     * querer la misma posición; un tronco gana a lo que no es tronco y, si no, queda el primero.
     */
    public static Map<Long, Integer> merge(List<Map<Long, Integer>> trees, IntPredicate isLog) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        for (Map<Long, Integer> t : trees) {
            for (Map.Entry<Long, Integer> e : t.entrySet()) {
                Integer prev = out.get(e.getKey());
                if (prev == null || (isLog.test(e.getValue()) && !isLog.test(prev))) out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }
}
