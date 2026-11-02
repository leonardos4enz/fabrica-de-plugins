package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;

import java.util.function.IntPredicate;

/**
 * Inundaciones sobre una foto (plan 028 §1.2): lo conectado a un punto (drain, fixwater, fill) y el interior de un objeto
 * (hollow). Corre en el hilo de cálculo; Java puro. Los resultados son posiciones empaquetadas con
 * {@link PointsSource#pack}.
 */
public final class Flood {
    private Flood() {
    }

    /** Dónde se puede llegar: además de lo que deja pasar el estado, la posición. */
    public interface Bound {
        boolean test(int x, int y, int z);
    }

    private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** La posición más cercana a (cx, cy, cz) dentro del límite cuyo estado cumple match; null si no hay. */
    public static int[] nearest(Snapshot snap, int cx, int cy, int cz, Bound bound, IntPredicate match) {
        Box b = snap.box;
        long best = Long.MAX_VALUE;
        int[] out = null;
        for (int y = b.minY(); y <= b.maxY(); y++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int x = b.minX(); x <= b.maxX(); x++) {
                    long d = (long) (x - cx) * (x - cx) + (long) (y - cy) * (y - cy) + (long) (z - cz) * (z - cz);
                    if (d >= best || !bound.test(x, y, z)) continue;
                    int s = snap.get(x, y, z);
                    if (s >= 0 && match.test(s)) {
                        best = d;
                        out = new int[]{x, y, z};
                    }
                }
            }
        }
        return out;
    }

    /** Todo lo conectado (6 vecinos) a la posición de partida por estados que cumplen through, dentro del límite. */
    public static long[] connected(Snapshot snap, int sx, int sy, int sz, Bound bound, IntPredicate through) {
        Box b = snap.box;
        int s0 = snap.get(sx, sy, sz);
        if (s0 < 0 || !through.test(s0) || !bound.test(sx, sy, sz)) return new long[0];
        int w = b.width(), d = b.depth();
        boolean[] seen = new boolean[(int) b.volume()];
        int[] queue = new int[64];
        int head = 0, tail = 0;
        long[] out = new long[64];
        int n = 0;
        int start = snap.index(sx, sy, sz);
        seen[start] = true;
        queue[tail++] = start;
        while (head < tail) {
            int i = queue[head++];
            int x = b.minX() + i % w, z = b.minZ() + (i / w) % d, y = b.minY() + i / (w * d);
            if (n == out.length) out = java.util.Arrays.copyOf(out, n * 2);
            out[n++] = PointsSource.pack(x, y, z);
            for (int[] o : SIDES) {
                int nx = x + o[0], ny = y + o[1], nz = z + o[2];
                if (!b.contains(nx, ny, nz) || !bound.test(nx, ny, nz)) continue;
                int j = snap.index(nx, ny, nz);
                if (seen[j]) continue;
                int s = snap.get(nx, ny, nz);
                if (s < 0 || !through.test(s)) continue;
                seen[j] = true;
                if (tail == queue.length) queue = java.util.Arrays.copyOf(queue, tail * 2);
                queue[tail++] = j;
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    /**
     * hollow (H2 CA6): lo de afuera es el aire conectado al borde de la caja (y lo que está fuera de ella); el interior es
     * todo lo que está a más de t bloques de lo de afuera (también el aire encerrado).
     */
    public static long[] interior(Snapshot snap, IntPredicate air, int t) {
        Box b = snap.box;
        int w = b.width(), h = b.height(), d = b.depth();
        int n = (int) b.volume();
        // dist: 0 sin visitar; k + 1 = a k pasos de lo de afuera.
        int[] dist = new int[n];
        int[] queue = new int[n];
        int head = 0, tail = 0;
        // 1. El aire conectado al borde: distancia 0.
        for (int i = 0; i < n; i++) {
            int x = i % w, z = (i / w) % d, y = i / (w * d);
            boolean edge = x == 0 || z == 0 || y == 0 || x == w - 1 || z == d - 1 || y == h - 1;
            if (!edge) continue;
            int s = snap.get(b.minX() + x, b.minY() + y, b.minZ() + z);
            if (s >= 0 && air.test(s)) {
                dist[i] = 1;
                queue[tail++] = i;
            }
        }
        while (head < tail) {
            int i = queue[head++];
            int x = i % w, z = (i / w) % d, y = i / (w * d);
            for (int[] o : SIDES) {
                int nx = x + o[0], ny = y + o[1], nz = z + o[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= w || ny >= h || nz >= d) continue;
                int j = (ny * d + nz) * w + nx;
                if (dist[j] != 0) continue;
                int s = snap.get(b.minX() + nx, b.minY() + ny, b.minZ() + nz);
                if (s < 0 || !air.test(s)) continue;
                dist[j] = 1;
                queue[tail++] = j;
            }
        }
        // 2. Hacia adentro, por todo lo demás: los del borde de la caja tocan lo de afuera (distancia 1).
        head = 0;
        for (int i = 0; i < n; i++) {
            if (dist[i] != 0) continue;
            int x = i % w, z = (i / w) % d, y = i / (w * d);
            boolean edge = x == 0 || z == 0 || y == 0 || x == w - 1 || z == d - 1 || y == h - 1;
            if (edge) {
                dist[i] = 2;
                queue[tail++] = i;
            }
        }
        while (head < tail) {
            int i = queue[head++];
            if (dist[i] - 1 > t) continue; // ya es interior: lo de más adentro también
            int x = i % w, z = (i / w) % d, y = i / (w * d);
            for (int[] o : SIDES) {
                int nx = x + o[0], ny = y + o[1], nz = z + o[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= w || ny >= h || nz >= d) continue;
                int j = (ny * d + nz) * w + nx;
                if (dist[j] != 0) continue;
                dist[j] = dist[i] + 1;
                queue[tail++] = j;
            }
        }
        long[] out = new long[64];
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (dist[i] != 0 && dist[i] - 1 <= t) continue;
            int x = b.minX() + i % w, z = b.minZ() + (i / w) % d, y = b.minY() + i / (w * d);
            if (k == out.length) out = java.util.Arrays.copyOf(out, k * 2);
            out[k++] = PointsSource.pack(x, y, z);
        }
        return java.util.Arrays.copyOf(out, k);
    }
}
