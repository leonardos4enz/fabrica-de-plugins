package dev.leo.worldcraft.region;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * curve (spec 040 H1 CA1): una curva suave que pasa por los puntos en orden, como //curve de WorldEdit (Kochanek-Bartels
 * con tensión, sesgo y continuidad 0, que es Catmull-Rom uniforme). Java puro.
 */
public final class Curve {
    private Curve() {
    }

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /**
     * Las posiciones de la curva, empaquetadas con pack. Con radius &gt; 0, una esfera de ese radio en cada punto; hollow
     * deja solo la cáscara (los que tienen una cara hacia afuera).
     */
    public static long[] points(List<int[]> nodes, int radius, boolean hollow, Line.Packer pack) {
        // Coordenadas → empaquetado, en orden (pack no se puede invertir aquí).
        Map<Key, Long> out = new LinkedHashMap<>();
        Set<Key> centers = new HashSet<>();
        int n = nodes.size();
        double er = (radius + 0.5) * (radius + 0.5);
        for (int i = 0; i < Math.max(1, n - 1); i++) {
            int[] p0 = nodes.get(Math.max(0, i - 1)), p1 = nodes.get(i), p2 = nodes.get(Math.min(n - 1, i + 1)), p3 = nodes.get(Math.min(n - 1, i + 2));
            double len = Math.sqrt(sq(p2[0] - p1[0]) + sq(p2[1] - p1[1]) + sq(p2[2] - p1[2]));
            int steps = Math.max(1, (int) Math.ceil(len * 4));
            for (int s = 0; s <= steps; s++) {
                double t = s / (double) steps;
                Key c = new Key((int) Math.round(cr(p0[0], p1[0], p2[0], p3[0], t)), (int) Math.round(cr(p0[1], p1[1], p2[1], p3[1], t)),
                        (int) Math.round(cr(p0[2], p1[2], p2[2], p3[2], t)));
                if (!centers.add(c)) continue;
                for (int dx = -radius; dx <= radius; dx++)
                    for (int dy = -radius; dy <= radius; dy++)
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (radius > 0 && dx * dx + dy * dy + dz * dz > er) continue;
                            Key k = new Key(c.x + dx, c.y + dy, c.z + dz);
                            out.computeIfAbsent(k, q -> pack.pack(q.x, q.y, q.z));
                        }
            }
        }
        if (!hollow || radius == 0) return out.values().stream().mapToLong(Long::longValue).toArray();
        return out.entrySet().stream().filter(e -> {
            Key q = e.getKey();
            for (int[] f : FACES) if (!out.containsKey(new Key(q.x + f[0], q.y + f[1], q.z + f[2]))) return true;
            return false;
        }).mapToLong(Map.Entry::getValue).toArray();
    }

    private record Key(int x, int y, int z) {
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Catmull-Rom uniforme entre p1 (t = 0) y p2 (t = 1). */
    static double cr(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }
}
