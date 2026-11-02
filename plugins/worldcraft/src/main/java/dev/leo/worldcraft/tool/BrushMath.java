package dev.leo.worldcraft.tool;

import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.edit.Snapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntPredicate;

/**
 * Lo que calculan los pinceles y herramientas que miran el mundo (plan 032 §1.1), sobre la foto de la 028. Las posiciones
 * salen empaquetadas con {@link PointsSource#pack}. Java puro; el azar sale de la semilla (la misma semilla da lo mismo).
 */
public final class BrushMath {
    private BrushMath() {
    }

    private static double dist(int dx, int dy, int dz) {
        return Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
    }

    /**
     * splatter (H3 CA1): los bloques de la superficie (ground con open encima) dentro de la esfera, cada uno con
     * probabilidad (1 − d/(r+½))^(decay/2): decay 1 casi parejo, 10 solo el centro.
     */
    public static long[] splatter(Snapshot snap, int cx, int cy, int cz, int r, int decay, IntPredicate ground, IntPredicate open, long seed) {
        List<Long> out = new ArrayList<>();
        double e = r + 0.5;
        for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    double d = dist(dx, dy, dz);
                    if (d > e) continue;
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    if (!surface(snap, x, y, z, ground, open)) continue;
                    double p = Math.pow(Math.max(0, 1 - d / e), decay / 2.0);
                    if (hash01(x, y, z, seed) < p) out.add(PointsSource.pack(x, y, z));
                }
            }
        }
        return toArray(out);
    }

    private static boolean surface(Snapshot snap, int x, int y, int z, IntPredicate ground, IntPredicate open) {
        int s = snap.get(x, y, z), up = snap.get(x, y + 1, z);
        return s >= 0 && up >= 0 && ground.test(s) && open.test(up);
    }

    /**
     * scatter (H3 CA2, FAWE): la cima de cada columna del círculo (de cy+r hacia abajo hasta cy−r), al azar, hasta
     * points posiciones separadas al menos distance en el plano; con overlay, la de encima.
     */
    public static long[] scatter(Snapshot snap, int cx, int cy, int cz, int r, int points, int distance, boolean overlay,
                                 IntPredicate ground, IntPredicate open, long seed) {
        List<int[]> cand = new ArrayList<>();
        double e = (r + 0.5) * (r + 0.5);
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dz * dz > e) continue;
                int x = cx + dx, z = cz + dz;
                for (int y = cy + r; y >= cy - r; y--) {
                    if (surface(snap, x, y, z, ground, open)) {
                        cand.add(new int[]{x, overlay ? y + 1 : y, z});
                        break;
                    }
                }
            }
        }
        java.util.Collections.shuffle(cand, new Random(seed));
        List<int[]> picked = new ArrayList<>();
        long d2 = (long) distance * distance;
        for (int[] c : cand) {
            if (picked.size() >= points) break;
            boolean ok = true;
            for (int[] q : picked) {
                long ddx = c[0] - q[0], ddz = c[2] - q[2];
                if (ddx * ddx + ddz * ddz < d2) {
                    ok = false;
                    break;
                }
            }
            if (ok) picked.add(c);
        }
        long[] out = new long[picked.size()];
        for (int i = 0; i < out.length; i++) out[i] = PointsSource.pack(picked.get(i)[0], picked.get(i)[1], picked.get(i)[2]);
        return out;
    }

    /**
     * rock / blob (H3 CA3, FAWE): una esfera deformada. Cada eje mide entre sphericity % y 100 % del radio (al azar); el
     * borde se mueve con un ruido de valor 3D de frecuencia frequency/200 por bloque y amplitude/200 del radio.
     */
    public static long[] rock(int cx, int cy, int cz, int r, int sphericity, int frequency, int amplitude, long seed) {
        Random rnd = new Random(seed);
        double s = sphericity / 100.0;
        double ax = r * (s + (1 - s) * rnd.nextDouble()) + 0.5, ay = r * (s + (1 - s) * rnd.nextDouble()) + 0.5,
                az = r * (s + (1 - s) * rnd.nextDouble()) + 0.5;
        double amp = amplitude / 200.0, f = frequency / 200.0;
        int m = (int) Math.ceil(r * (1 + amp)) + 1;
        long noiseSeed = rnd.nextLong();
        List<Long> out = new ArrayList<>();
        for (int dy = -m; dy <= m; dy++) {
            for (int dz = -m; dz <= m; dz++) {
                for (int dx = -m; dx <= m; dx++) {
                    double q = Math.sqrt((dx / ax) * (dx / ax) + (dy / ay) * (dy / ay) + (dz / az) * (dz / az));
                    double limit = 1 + (amp == 0 ? 0 : amp * noise3(dx * f, dy * f, dz * f, noiseSeed));
                    if (q <= limit) out.add(PointsSource.pack(cx + dx, cy + dy, cz + dz));
                }
            }
        }
        return toArray(out);
    }

    /**
     * gravity (H4 CA1, como WorldEdit): en cada columna del círculo, de yLo a yHi, lo que no es aire cae hasta apoyarse;
     * lo fijo (bloques con datos) no se mueve y hace de piso. Devuelve posición → estado nuevo (solo lo que cambia).
     */
    public static Map<Long, Integer> gravity(Snapshot snap, int cx, int cz, int r, int yLo, int yHi, IntPredicate air,
                                            IntPredicate fixed, int airState) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        double e = (r + 0.5) * (r + 0.5);
        int n = yHi - yLo + 1;
        int[] old = new int[n], neu = new int[n];
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dz * dz > e) continue;
                int x = cx + dx, z = cz + dz;
                boolean any = false;
                for (int i = 0; i < n; i++) {
                    old[i] = snap.get(x, yLo + i, z);
                    if (old[i] >= 0) any = true;
                }
                if (!any) continue;
                int free = 0;
                for (int i = 0; i < n; i++) {
                    int s = old[i];
                    if (s < 0 || fixed.test(s)) {
                        // Lo fijo (o sin leer) se queda: lo de arriba se apoya encima.
                        for (int j = free; j < i; j++) neu[j] = keepAir(old[j], air, airState);
                        neu[i] = s;
                        free = i + 1;
                    } else if (!air.test(s)) {
                        neu[free++] = s;
                    }
                }
                for (int j = free; j < n; j++) neu[j] = keepAir(old[j], air, airState);
                for (int i = 0; i < n; i++) {
                    if (old[i] >= 0 && neu[i] != old[i]) out.put(PointsSource.pack(x, yLo + i, z), neu[i]);
                }
            }
        }
        return out;
    }

    private static int keepAir(int old, IntPredicate air, int airState) {
        return old >= 0 && air.test(old) ? old : airState;
    }

    /** Lo que encontró deltree. */
    public enum TreeStatus { OK, NOT_TREE, GROUNDED, TOO_BIG }

    public record Tree(TreeStatus status, long[] points) {
    }

    /**
     * deltree (H5 CA5): lo conectado (6 vecinos) al bloque de partida que es árbol; si algo de eso toca un bloque que no
     * es árbol ni libre (aire, plantas, agua), está plantado; si llega al borde de la foto, es muy grande.
     */
    public static Tree deltree(Snapshot snap, int sx, int sy, int sz, IntPredicate tree, IntPredicate free) {
        int s0 = snap.get(sx, sy, sz);
        if (s0 < 0 || !tree.test(s0)) return new Tree(TreeStatus.NOT_TREE, new long[0]);
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        List<Long> out = new ArrayList<>();
        seen.add(PointsSource.pack(sx, sy, sz));
        queue.add(new int[]{sx, sy, sz});
        int[][] sides = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            out.add(PointsSource.pack(c[0], c[1], c[2]));
            for (int[] d : sides) {
                int x = c[0] + d[0], y = c[1] + d[1], z = c[2] + d[2];
                int s = snap.get(x, y, z);
                if (s < 0) return new Tree(TreeStatus.TOO_BIG, new long[0]);
                if (tree.test(s)) {
                    if (seen.add(PointsSource.pack(x, y, z))) queue.add(new int[]{x, y, z});
                } else if (!free.test(s)) {
                    return new Tree(TreeStatus.GROUNDED, new long[0]);
                }
            }
        }
        return new Tree(TreeStatus.OK, toArray(out));
    }

    /** superpico area (H6 CA1): los bloques que cumplen same en el cubo de radio r. */
    public static long[] cube(Snapshot snap, int cx, int cy, int cz, int r, IntPredicate same) {
        List<Long> out = new ArrayList<>();
        for (int y = cy - r; y <= cy + r; y++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int x = cx - r; x <= cx + r; x++) {
                    int s = snap.get(x, y, z);
                    if (s >= 0 && same.test(s)) out.add(PointsSource.pack(x, y, z));
                }
            }
        }
        return toArray(out);
    }

    // ------------------------------------------------------------------ azar

    /** Un número de 0 a 1 que depende solo de la posición y la semilla. */
    public static double hash01(int x, int y, int z, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Ruido de valor 3D de −1 a 1, suave (interpolación con smoothstep entre los puntos enteros). */
    static double noise3(double x, double y, double z, long seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double fx = smooth(x - x0), fy = smooth(y - y0), fz = smooth(z - z0);
        double v = 0;
        for (int i = 0; i < 8; i++) {
            int ix = i & 1, iy = (i >> 1) & 1, iz = (i >> 2) & 1;
            double w = (ix == 1 ? fx : 1 - fx) * (iy == 1 ? fy : 1 - fy) * (iz == 1 ? fz : 1 - fz);
            v += w * hash01(x0 + ix, y0 + iy, z0 + iz, seed);
        }
        return v * 2 - 1;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static long[] toArray(List<Long> l) {
        long[] out = new long[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }
}
