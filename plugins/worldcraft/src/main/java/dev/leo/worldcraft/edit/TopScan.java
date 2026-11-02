package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.ChunkOrder;
import dev.leo.worldcraft.region.Shape;

import java.util.function.IntPredicate;

/**
 * La cima de cada columna (plan 028 §1.3: overlay, snow): un trabajo de lectura que baja cada columna desde arriba y la
 * deja en el primer bloque que cumple stop. Las columnas fuera de la forma (columns = false) no se leen.
 */
public final class TopScan implements EditSource, EditJob.Capture {
    public static final int NONE = Integer.MIN_VALUE;

    public interface Columns {
        boolean test(int x, int z);
    }

    private final Box box;
    private final IntPredicate stop;
    private final Columns columns;
    private final ChunkOrder order;
    private final int[] top, topState;
    private final int[] col = new int[3];
    private int index = -1, y;
    private boolean found = true, done;

    public TopScan(Box box, IntPredicate stop, Columns columns) {
        this.box = box;
        this.stop = stop;
        this.columns = columns;
        this.order = new ChunkOrder(new Box(box.minX(), 0, box.minZ(), box.maxX(), 0, box.maxZ()), Shape.FILL);
        int n = box.width() * box.depth();
        this.top = new int[n];
        this.topState = new int[n];
        java.util.Arrays.fill(top, NONE);
    }

    public int index(int x, int z) {
        return (x - box.minX()) + (z - box.minZ()) * box.width();
    }

    /** La altura del primer bloque que cumple stop en la columna, o NONE. */
    public int top(int x, int z) {
        return top[index(x, z)];
    }

    public int topState(int x, int z) {
        return topState[index(x, z)];
    }

    @Override
    public boolean next(int[] p) {
        if (done) return false;
        while (found || y < box.minY()) {
            if (!order.next(col)) {
                done = true;
                return false;
            }
            if (!columns.test(col[0], col[2])) continue;
            index = index(col[0], col[2]);
            y = box.maxY();
            found = false;
        }
        p[0] = col[0];
        p[1] = y--;
        p[2] = col[2];
        return true;
    }

    @Override
    public void take(int x, int py, int z, int state, byte[] tile) {
        if (found || !stop.test(state)) return;
        top[index] = py;
        topState[index] = state;
        found = true;
    }

    @Override
    public int target(int x, int y, int z, int current) {
        return KEEP;
    }

    @Override
    public long volume() {
        return box.volume();
    }

    @Override
    public Box bounds() {
        return box;
    }
}
