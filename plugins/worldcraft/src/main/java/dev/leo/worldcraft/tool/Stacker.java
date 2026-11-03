package dev.leo.worldcraft.tool;

import dev.leo.worldcraft.edit.PointsSource;

import java.util.Arrays;
import java.util.function.IntPredicate;

/** /wc tool stacker (spec 040 H3 CA5): adónde se repite un bloque. Java puro. */
public final class Stacker {
    private Stacker() {
    }

    /** El estado en una posición. */
    public interface At {
        int state(int x, int y, int z);
    }

    /**
     * Desde (x, y, z) sin contarlo, paso a paso en (dx, dy, dz), hasta range lugares o el primero que no cumple ok.
     */
    public static long[] points(int x, int y, int z, int dx, int dy, int dz, int range, IntPredicate ok, At at) {
        long[] out = new long[Math.max(0, range)];
        int n = 0;
        for (int i = 1; i <= range; i++) {
            int px = x + dx * i, py = y + dy * i, pz = z + dz * i;
            if (!ok.test(at.state(px, py, pz))) break;
            out[n++] = PointsSource.pack(px, py, pz);
        }
        return Arrays.copyOf(out, n);
    }
}
