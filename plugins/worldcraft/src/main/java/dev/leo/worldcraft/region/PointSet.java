package dev.leo.worldcraft.region;

import java.util.Arrays;

/**
 * Spec 030 (sel magic): bloques sueltos, empaquetados y ordenados; contains por búsqueda binaria. La cáscara se decide
 * mirando los vecinos (como en {@link Columns}, pero bloque a bloque).
 */
public final class PointSet implements Region {
    private final long[] points;
    private final Box bounds;

    /** packed: posiciones de {@link #pack}; se ordenan y se quitan las repetidas. */
    public PointSet(long[] packed) {
        long[] p = packed.clone();
        Arrays.sort(p);
        int n = 0;
        for (int i = 0; i < p.length; i++) if (i == 0 || p[i] != p[i - 1]) p[n++] = p[i];
        points = Arrays.copyOf(p, n);
        if (n == 0) throw new IllegalArgumentException("no blocks");
        int ax = Integer.MAX_VALUE, ay = Integer.MAX_VALUE, az = Integer.MAX_VALUE;
        int bx = Integer.MIN_VALUE, by = Integer.MIN_VALUE, bz = Integer.MIN_VALUE;
        for (long k : points) {
            int x = x(k), y = y(k), z = z(k);
            ax = Math.min(ax, x);
            ay = Math.min(ay, y);
            az = Math.min(az, z);
            bx = Math.max(bx, x);
            by = Math.max(by, y);
            bz = Math.max(bz, z);
        }
        bounds = new Box(ax, ay, az, bx, by, bz);
    }

    /** x y z en 26 + 12 + 26 bits (el mismo orden que la posición de bloque de Minecraft). */
    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public static int x(long k) {
        return (int) (k >> 38);
    }

    public static int y(long k) {
        return (int) (k << 52 >> 52);
    }

    public static int z(long k) {
        return (int) (k << 26 >> 38);
    }

    public long[] packed() {
        return points.clone();
    }

    /** Esta y otra juntas (clic derecho de la mágica). */
    public PointSet union(PointSet o) {
        long[] all = Arrays.copyOf(points, points.length + o.points.length);
        System.arraycopy(o.points, 0, all, points.length, o.points.length);
        return new PointSet(all);
    }

    @Override
    public Box bounds() {
        return bounds;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return bounds.contains(x, y, z) && Arrays.binarySearch(points, pack(x, y, z)) >= 0;
    }

    @Override
    public long volume() {
        return points.length;
    }

    private boolean shell(int x, int y, int z, Shape shape) {
        if (shape == Shape.FILL) return true;
        if (!contains(x + 1, y, z) || !contains(x - 1, y, z) || !contains(x, y, z + 1) || !contains(x, y, z - 1)) return true;
        return shape == Shape.FACES && (!contains(x, y + 1, z) || !contains(x, y - 1, z));
    }

    @Override
    public long count(Shape shape) {
        if (shape == Shape.FILL) return points.length;
        long n = 0;
        for (long k : points) if (shell(x(k), y(k), z(k), shape)) n++;
        return n;
    }

    @Override
    public int[] ys(int x, int z, Shape shape) {
        if (x < bounds.minX() || x > bounds.maxX() || z < bounds.minZ() || z > bounds.maxZ()) return new int[0];
        int[] tmp = new int[bounds.height()];
        int n = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) if (contains(x, y, z) && shell(x, y, z, shape)) tmp[n++] = y;
        return Arrays.copyOf(tmp, n);
    }

    @Override
    public int[] span(int x, int z) {
        int[] ys = ys(x, z, Shape.FILL);
        return ys.length == 0 ? null : new int[]{ys[0], ys[ys.length - 1]};
    }

    @Override
    public Region clampY(int min, int max) {
        if (min <= bounds.minY() && max >= bounds.maxY()) return this;
        long[] keep = Arrays.stream(points).filter(k -> y(k) >= min && y(k) <= max).toArray();
        return keep.length == 0 ? null : new PointSet(keep);
    }

    @Override
    public String kind() {
        return "magic";
    }
}
