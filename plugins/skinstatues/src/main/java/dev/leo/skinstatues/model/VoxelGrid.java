package dev.leo.skinstatues.model;

/**
 * Resultado de la voxelización: bloques relativos a la esquina del bloque base (los pies), con su color ARGB. La parte
 * más baja está en y = 0.
 */
public final class VoxelGrid {
    private final int[] xs, ys, zs, colors;
    private final int minX, maxX, maxY, minZ, maxZ;
    private final int shift;
    private final double scale;

    VoxelGrid(int[] xs, int[] ys, int[] zs, int[] colors, int shift, double scale) {
        this.shift = shift;
        this.scale = scale;
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
        this.colors = colors;
        int a = Integer.MAX_VALUE, b = Integer.MIN_VALUE, c = Integer.MIN_VALUE, d = Integer.MAX_VALUE, e = Integer.MIN_VALUE;
        for (int i = 0; i < xs.length; i++) {
            a = Math.min(a, xs[i]);
            b = Math.max(b, xs[i]);
            c = Math.max(c, ys[i]);
            d = Math.min(d, zs[i]);
            e = Math.max(e, zs[i]);
        }
        minX = a;
        maxX = b;
        maxY = c;
        minZ = d;
        maxZ = e;
    }

    /** Cuántos bloques se subió (o bajó, si es negativo) la figura para apoyarla en y = 0. */
    public int shift() {
        return shift;
    }

    /** Bloques por píxel de skin que se usaron. */
    public double scale() {
        return scale;
    }

    public int size() {
        return xs.length;
    }

    public int x(int i) {
        return xs[i];
    }

    public int y(int i) {
        return ys[i];
    }

    public int z(int i) {
        return zs[i];
    }

    public int color(int i) {
        return colors[i];
    }

    /** Alto en bloques (de y = 0 al bloque más alto, inclusive). */
    public int height() {
        return size() == 0 ? 0 : maxY + 1;
    }

    public int minX() {
        return minX;
    }

    public int maxX() {
        return maxX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxZ() {
        return maxZ;
    }

    public static long key(int x, int y, int z) {
        return ((long) (x + OFF) << 42) | ((long) (y + OFF) << 21) | (z + OFF);
    }

    static final int OFF = 1 << 20;
}
