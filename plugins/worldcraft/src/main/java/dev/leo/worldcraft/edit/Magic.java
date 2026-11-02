package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.PointSet;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.function.IntPredicate;

/**
 * Spec 030 (sel magic, plan §1.4): los bloques conectados por las caras al del clic que cumplen same (el mismo
 * material), dentro de la foto leída alrededor. Java puro: corre en el hilo de cálculo.
 */
public final class Magic {
    private Magic() {
    }

    /** null si el bloque del clic no está en la foto o no cumple. */
    public static PointSet flood(Snapshot snap, int x, int y, int z, IntPredicate same) {
        var b = snap.box;
        if (!b.contains(x, y, z) || !same.test(snap.get(x, y, z))) return null;
        int w = b.width(), h = b.height(), d = b.depth();
        BitSet seen = new BitSet((int) b.volume());
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        long[] out = new long[64];
        int n = 0;
        queue.add(new int[]{x, y, z});
        seen.set(snap.index(x, y, z));
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            if (n == out.length) out = java.util.Arrays.copyOf(out, n * 2);
            out[n++] = PointSet.pack(p[0], p[1], p[2]);
            for (int[] dd : dirs) {
                int nx = p[0] + dd[0], ny = p[1] + dd[1], nz = p[2] + dd[2];
                if (nx < b.minX() || ny < b.minY() || nz < b.minZ() || nx >= b.minX() + w || ny >= b.minY() + h || nz >= b.minZ() + d) continue;
                int i = snap.index(nx, ny, nz);
                if (seen.get(i)) continue;
                seen.set(i);
                if (same.test(snap.get(nx, ny, nz))) queue.add(new int[]{nx, ny, nz});
            }
        }
        return new PointSet(java.util.Arrays.copyOf(out, n));
    }
}
