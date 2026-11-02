package dev.leo.worldcraft.region;

import java.util.List;

/**
 * Spec 030 (sel poly): un polígono en el plano (x, z) con un alto. Una columna está dentro si la regla par-impar la deja
 * dentro o si está a medio bloque de una arista (las aristas quedan como líneas continuas, también con dos vértices).
 * Todo con enteros exactos.
 */
public final class Polygon extends Columns {
    final int[] xs, zs;
    final int minY, maxY;

    /** points: {x, z} de cada vértice, en orden. */
    public Polygon(List<int[]> points, int minY, int maxY) {
        super(bounds(points, minY, maxY));
        if (points.isEmpty()) throw new IllegalArgumentException("no points");
        xs = new int[points.size()];
        zs = new int[points.size()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = points.get(i)[0];
            zs[i] = points.get(i)[1];
        }
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
    }

    private static Box bounds(List<int[]> pts, int y1, int y2) {
        int ax = Integer.MAX_VALUE, az = Integer.MAX_VALUE, bx = Integer.MIN_VALUE, bz = Integer.MIN_VALUE;
        for (int[] p : pts) {
            ax = Math.min(ax, p[0]);
            az = Math.min(az, p[1]);
            bx = Math.max(bx, p[0]);
            bz = Math.max(bz, p[1]);
        }
        if (pts.isEmpty()) return new Box(0, 0, 0, 0, 0, 0);
        return Box.of(ax, y1, az, bx, y2, bz);
    }

    @Override
    protected int[] compute(int x, int z) {
        return inside(x, z) ? new int[]{minY, maxY} : null;
    }

    boolean inside(int x, int z) {
        int n = xs.length;
        boolean in = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            long ax = xs[j], az = zs[j], bx = xs[i], bz = zs[i];
            if (near(ax, az, bx, bz, x, z)) return true;
            if (n < 3) continue;
            if ((az > z) != (bz > z)) {
                // x < ax + (z - az)·(bx - ax)/(bz - az), sin dividir
                long lhs = (x - ax) * (bz - az), rhs = (z - az) * (bx - ax);
                if (bz - az > 0 ? lhs < rhs : lhs > rhs) in = !in;
            }
        }
        return in;
    }

    /** ¿Está (x, z) a medio bloque de la arista (a, b), dentro de su caja? */
    private static boolean near(long ax, long az, long bx, long bz, long x, long z) {
        if (x < Math.min(ax, bx) || x > Math.max(ax, bx) || z < Math.min(az, bz) || z > Math.max(az, bz)) return false;
        long dx = bx - ax, dz = bz - az;
        long cross = dx * (z - az) - dz * (x - ax);
        return 2 * Math.abs(cross) <= Math.max(Math.abs(dx), Math.abs(dz));
    }

    @Override
    public String kind() {
        return "poly";
    }
}
