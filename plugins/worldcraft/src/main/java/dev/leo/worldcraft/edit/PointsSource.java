package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;

import java.util.Arrays;

/**
 * Una lista de posiciones calculadas (plan 028 §1.2: line, hollow, drain, fill…), recorridas en orden de chunks y, dentro
 * de cada chunk, por x, z e y como {@link dev.leo.worldcraft.region.ChunkOrder}. Qué poner lo decide {@link Pick} con
 * el estado actual del mundo.
 */
public final class PointsSource implements EditSource {
    /** Qué poner en una posición sabiendo lo que hay ahora; {@link EditSource#KEEP} para dejarla. */
    public interface Pick {
        int at(int x, int y, int z, int current);
    }

    private static final long FLIP = Long.MIN_VALUE;
    private final long[] keys;
    private final Pick pick;
    private final Box bounds;
    private int i;

    /** points: posiciones empaquetadas con {@link #pack}; se ordenan y se quitan las repetidas. */
    public PointsSource(long[] points, Pick pick) {
        long[] k = new long[points.length];
        for (int j = 0; j < points.length; j++) {
            long p = points[j];
            k[j] = order(unpackX(p), unpackY(p), unpackZ(p));
        }
        Arrays.sort(k);
        int n = 0;
        for (int j = 0; j < k.length; j++) if (j == 0 || k[j] != k[j - 1]) k[n++] = k[j];
        this.keys = Arrays.copyOf(k, n);
        this.pick = pick;
        int a = Integer.MAX_VALUE, b = Integer.MAX_VALUE, c = Integer.MAX_VALUE, d = Integer.MIN_VALUE, e = Integer.MIN_VALUE, f = Integer.MIN_VALUE;
        int[] p = new int[3];
        for (long key : keys) {
            decode(key, p);
            a = Math.min(a, p[0]);
            b = Math.min(b, p[1]);
            c = Math.min(c, p[2]);
            d = Math.max(d, p[0]);
            e = Math.max(e, p[1]);
            f = Math.max(f, p[2]);
        }
        this.bounds = keys.length == 0 ? new Box(0, 0, 0, 0, 0, 0) : new Box(a, b, c, d, e, f);
    }

    /** x y z en un long (26, 12 y 26 bits; y de −2048 a 2047). */
    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | ((y + 2048) & 0xFFF);
    }

    public static int unpackX(long p) {
        return (int) (p >> 38) << 6 >> 6;
    }

    public static int unpackZ(long p) {
        return (int) ((p >> 12) & 0x3FFFFFF) << 6 >> 6;
    }

    public static int unpackY(long p) {
        return (int) (p & 0xFFF) - 2048;
    }

    /** Clave que ordena por chunk x, chunk z, x, z, y (22 + 22 + 4 + 4 + 12 bits). */
    static long order(int x, int y, int z) {
        long cx = (x >> 4) + (1L << 21), cz = (z >> 4) + (1L << 21);
        long key = cx << 42 | cz << 20 | (long) (x & 15) << 16 | (long) (z & 15) << 12 | ((y + 2048) & 0xFFF);
        return key ^ FLIP;
    }

    static void decode(long key, int[] p) {
        key ^= FLIP;
        int cx = (int) ((key >>> 42) - (1L << 21)), cz = (int) (((key >>> 20) & 0x3FFFFF) - (1L << 21));
        p[0] = (cx << 4) | (int) ((key >>> 16) & 15);
        p[2] = (cz << 4) | (int) ((key >>> 12) & 15);
        p[1] = (int) (key & 0xFFF) - 2048;
    }

    @Override
    public boolean next(int[] p) {
        if (i >= keys.length) return false;
        decode(keys[i++], p);
        return true;
    }

    @Override
    public int target(int x, int y, int z, int current) {
        return pick.at(x, y, z, current);
    }

    @Override
    public long volume() {
        return keys.length;
    }

    @Override
    public Box bounds() {
        return bounds;
    }

    @Override
    public long[] chunks() {
        long[] out = new long[keys.length];
        int n = 0;
        int[] p = new int[3];
        long prev = Long.MIN_VALUE;
        for (long key : keys) {
            decode(key, p);
            long c = dev.leo.worldcraft.region.ChunkOrder.pack(p[0] >> 4, p[2] >> 4);
            if (n == 0 || c != prev) out[n++] = c;
            prev = c;
        }
        return Arrays.copyOf(out, n);
    }
}
