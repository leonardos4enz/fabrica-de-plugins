package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;

import java.util.Arrays;

/**
 * Spec 034 (deform, plan §1.3): cada destino toma el estado y los datos de bloque de su origen en la foto leída antes
 * de la obra. Los destinos se recorren en orden de chunks como {@link PointsSource}; un origen que no se leyó (fuera del
 * alto del mundo, o snap null) es aire.
 */
public final class MapSource implements EditSource {
    private final long[] keys, from;
    private final Snapshot snap;
    private final int air;
    private final Box bounds;
    private int i;
    private byte[] tile;

    /** targets y sources empaquetados con {@link PointsSource#pack}, uno a uno. */
    public MapSource(long[] targets, long[] sources, Snapshot snap, int air) {
        this.snap = snap;
        this.air = air;
        int n = targets.length;
        long[] k = new long[n];
        Integer[] idx = new Integer[n];
        for (int j = 0; j < n; j++) {
            long p = targets[j];
            k[j] = PointsSource.order(PointsSource.unpackX(p), PointsSource.unpackY(p), PointsSource.unpackZ(p));
            idx[j] = j;
        }
        Arrays.sort(idx, (a, b) -> Long.compare(k[a], k[b]));
        this.keys = new long[n];
        this.from = new long[n];
        for (int j = 0; j < n; j++) {
            keys[j] = k[idx[j]];
            from[j] = sources[idx[j]];
        }
        int a = Integer.MAX_VALUE, b = Integer.MAX_VALUE, c = Integer.MAX_VALUE, d = Integer.MIN_VALUE, e = Integer.MIN_VALUE, f = Integer.MIN_VALUE;
        for (long t : targets) {
            int x = PointsSource.unpackX(t), y = PointsSource.unpackY(t), z = PointsSource.unpackZ(t);
            a = Math.min(a, x);
            b = Math.min(b, y);
            c = Math.min(c, z);
            d = Math.max(d, x);
            e = Math.max(e, y);
            f = Math.max(f, z);
        }
        this.bounds = n == 0 ? new Box(0, 0, 0, 0, 0, 0) : new Box(a, b, c, d, e, f);
    }

    private MapSource(MapSource o) {
        this.keys = o.keys;
        this.from = o.from;
        this.snap = o.snap;
        this.air = o.air;
        this.bounds = o.bounds;
    }

    /** Otra recorrida de lo mismo (la vista previa y la obra), sin volver a ordenar. */
    public MapSource fresh() {
        return new MapSource(this);
    }

    @Override
    public boolean next(int[] p) {
        if (i >= keys.length) return false;
        PointsSource.decode(keys[i++], p);
        return true;
    }

    @Override
    public int target(int x, int y, int z, int current) {
        long s = from[i - 1];
        int sx = PointsSource.unpackX(s), sy = PointsSource.unpackY(s), sz = PointsSource.unpackZ(s);
        int st = snap == null ? -1 : snap.get(sx, sy, sz);
        tile = st < 0 ? null : snap.tile(sx, sy, sz);
        return st < 0 ? air : st;
    }

    @Override
    public byte[] tile() {
        return tile;
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
            PointsSource.decode(key, p);
            long c = dev.leo.worldcraft.region.ChunkOrder.pack(p[0] >> 4, p[2] >> 4);
            if (n == 0 || c != prev) out[n++] = c;
            prev = c;
        }
        return Arrays.copyOf(out, n);
    }
}
