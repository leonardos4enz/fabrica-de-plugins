package dev.leo.worldcraft.region;

/**
 * Recorre una forma dentro de una caja chunk por chunk (columna de 16×16), para que una operación cargue cada chunk una
 * sola vez y lo suelte al terminar con él (H5 CA3). Dentro de la columna va por (x, z) y luego y. Las columnas que una
 * forma hueca no toca (el interior de walls) se saltan sin recorrerlas.
 */
public final class ChunkOrder {
    private final Box box;
    private final Shape shape;
    private final int cx0, cz0, cx1, cz1;
    private int cx, cz;
    private int x, z, colX0, colX1, colZ0, colZ1;
    private int yIndex = -1;
    private int[] ys = new int[0];
    private boolean started, done;

    public ChunkOrder(Box box, Shape shape) {
        this.box = box;
        this.shape = shape;
        cx0 = box.minX() >> 4;
        cz0 = box.minZ() >> 4;
        cx1 = box.maxX() >> 4;
        cz1 = box.maxZ() >> 4;
    }

    /** Las columnas de chunks en el orden en que se recorren (cx, cz empaquetados con {@link #pack}). */
    public static long[] chunks(Box box) {
        int a = box.minX() >> 4, b = box.minZ() >> 4, c = box.maxX() >> 4, d = box.maxZ() >> 4;
        long[] out = new long[(c - a + 1) * (d - b + 1)];
        int i = 0;
        for (int x = a; x <= c; x++) for (int zz = b; zz <= d; zz++) out[i++] = pack(x, zz);
        return out;
    }

    public static long pack(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public static int chunkX(long key) {
        return (int) (key >> 32);
    }

    public static int chunkZ(long key) {
        return (int) key;
    }

    /** La siguiente posición en p; false si ya no hay más. */
    public boolean next(int[] p) {
        if (done) return false;
        while (true) {
            if (yIndex >= 0 && yIndex < ys.length) {
                p[0] = x;
                p[1] = ys[yIndex++];
                p[2] = z;
                return true;
            }
            if (!advanceColumn()) {
                done = true;
                return false;
            }
        }
    }

    /** Pasa a la siguiente columna (x, z) de la forma; prepara sus alturas. */
    private boolean advanceColumn() {
        while (true) {
            if (!started) {
                started = true;
                cx = cx0;
                cz = cz0;
                enterChunk();
            } else if (++z > colZ1) {
                z = colZ0;
                if (++x > colX1) {
                    if (++cz > cz1) {
                        cz = cz0;
                        if (++cx > cx1) return false;
                    }
                    enterChunk();
                }
            }
            if (prepareHeights()) return true;
        }
    }

    private void enterChunk() {
        colX0 = Math.max(box.minX(), cx << 4);
        colX1 = Math.min(box.maxX(), (cx << 4) + 15);
        colZ0 = Math.max(box.minZ(), cz << 4);
        colZ1 = Math.min(box.maxZ(), (cz << 4) + 15);
        x = colX0;
        z = colZ0;
    }

    private boolean prepareHeights() {
        boolean edge = x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ();
        yIndex = 0;
        switch (shape) {
            case FILL -> ys = range(box.minY(), box.maxY());
            case WALLS -> ys = edge ? range(box.minY(), box.maxY()) : new int[0];
            case FACES -> ys = edge ? range(box.minY(), box.maxY())
                    : box.minY() == box.maxY() ? new int[]{box.minY()} : new int[]{box.minY(), box.maxY()};
        }
        return ys.length > 0;
    }

    private int[] cachedRange;

    private int[] range(int a, int b) {
        if (cachedRange == null) {
            cachedRange = new int[b - a + 1];
            for (int i = 0; i < cachedRange.length; i++) cachedRange[i] = a + i;
        }
        return cachedRange;
    }
}
