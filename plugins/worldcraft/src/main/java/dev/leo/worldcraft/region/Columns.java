package dev.leo.worldcraft.region;

/**
 * Spec 030 (plan §1.1): una forma en la que cada columna (x, z) es un solo intervalo de y (polígono, elipsoide, esfera,
 * cilindro, convexa). La subclase calcula el intervalo; aquí se guarda en una tabla (una vez, al primer uso) y de ella
 * salen contains, el volumen, el recorrido y la cáscara, así nunca discrepan.
 */
public abstract class Columns implements Region {
    /** Más columnas que esto no se tabulan (16 millones = 128 MB); la forma se rechaza al armarla. */
    public static final long MAX_COLUMNS = 16_000_000;

    protected final Box bounds;
    private int[] lo, hi;

    protected Columns(Box bounds) {
        if ((long) bounds.width() * bounds.depth() > MAX_COLUMNS) throw new IllegalArgumentException("the selection is too wide");
        this.bounds = bounds;
    }

    /** [lo, hi] de la columna (dentro de bounds), o null si está vacía. Se llama una vez por columna. */
    protected abstract int[] compute(int x, int z);

    private void table() {
        if (lo != null) return;
        int w = bounds.width(), d = bounds.depth();
        int[] a = new int[w * d], b = new int[w * d];
        for (int dz = 0; dz < d; dz++) {
            for (int dx = 0; dx < w; dx++) {
                int i = dx + dz * w;
                int[] c = compute(bounds.minX() + dx, bounds.minZ() + dz);
                if (c == null || c[1] < c[0]) {
                    a[i] = 1;
                    b[i] = 0;
                } else {
                    a[i] = c[0];
                    b[i] = c[1];
                }
            }
        }
        hi = b;
        lo = a;
    }

    private int index(int x, int z) {
        int dx = x - bounds.minX(), dz = z - bounds.minZ();
        if (dx < 0 || dz < 0 || dx >= bounds.width() || dz >= bounds.depth()) return -1;
        return dx + dz * bounds.width();
    }

    /** El intervalo de la columna o null. */
    @Override
    public int[] span(int x, int z) {
        table();
        int i = index(x, z);
        if (i < 0 || hi[i] < lo[i]) return null;
        return new int[]{lo[i], hi[i]};
    }

    @Override
    public Box bounds() {
        return bounds;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        table();
        int i = index(x, z);
        return i >= 0 && y >= lo[i] && y <= hi[i];
    }

    @Override
    public long volume() {
        return count(Shape.FILL);
    }

    /**
     * El interior de una columna para esa cáscara: [a, b] = intervalo ∩ los de los 4 vecinos (FACES además sin el primero
     * ni el último). Lo que queda fuera del interior es la cáscara (plan §1.1).
     */
    private int[] inner(int i, int x, int z, Shape shape) {
        int a = lo[i], b = hi[i];
        if (shape == Shape.FACES) {
            a++;
            b--;
        }
        int[][] nb = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
        for (int[] c : nb) {
            int j = index(c[0], c[1]);
            if (j < 0) return null;
            a = Math.max(a, lo[j]);
            b = Math.min(b, hi[j]);
        }
        return b < a ? null : new int[]{a, b};
    }

    @Override
    public long count(Shape shape) {
        table();
        long n = 0;
        int w = bounds.width();
        for (int i = 0; i < lo.length; i++) {
            if (hi[i] < lo[i]) continue;
            n += hi[i] - lo[i] + 1;
            if (shape == Shape.FILL) continue;
            int[] in = inner(i, bounds.minX() + i % w, bounds.minZ() + i / w, shape);
            if (in != null) n -= in[1] - in[0] + 1;
        }
        return n;
    }

    @Override
    public int[] ys(int x, int z, Shape shape) {
        table();
        int i = index(x, z);
        if (i < 0 || hi[i] < lo[i]) return new int[0];
        int[] in = shape == Shape.FILL ? null : inner(i, x, z, shape);
        if (in == null) return Box.range(lo[i], hi[i]);
        int[] out = new int[(hi[i] - lo[i] + 1) - (in[1] - in[0] + 1)];
        int k = 0;
        for (int y = lo[i]; y < in[0]; y++) out[k++] = y;
        for (int y = in[1] + 1; y <= hi[i]; y++) out[k++] = y;
        return out;
    }

    @Override
    public Region clampY(int min, int max) {
        if (min <= bounds.minY() && max >= bounds.maxY()) return this;
        Columns inner = this;
        Box b = bounds.clampY(min, max);
        if (b == null) return null;
        Columns c = new Columns(b) {
            @Override
            protected int[] compute(int x, int z) {
                int[] s = inner.span(x, z);
                if (s == null) return null;
                int a = Math.max(s[0], min), h = Math.min(s[1], max);
                return h < a ? null : new int[]{a, h};
            }

            @Override
            public String kind() {
                return inner.kind();
            }
        };
        return c.volume() == 0 ? null : c;
    }
}
