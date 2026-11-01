package dev.leo.worldcraft.terrain;

import dev.leo.worldcraft.edit.EditSource;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.ChunkOrder;
import dev.leo.worldcraft.region.Shape;

/**
 * Una obra de terreno para el motor de la 025 (plan 026 §1.2–§1.3): por cada columna de un rectángulo, un rango de y a
 * recorrer y qué poner en cada y. Las columnas van en orden de chunks y cada una de arriba abajo. Con {@code skin}
 * (la vista previa) solo se recorren los bloques que se ven desde fuera: la cima, la base si se ve, y lo que no tiene
 * vecino a esa altura; la obra recorre todo con las mismas reglas, así lo que se ve es lo que se pone.
 */
public abstract class ColumnSource implements EditSource {
    protected final int x0, z0, width, depth;
    protected final int[] lo, hi;
    private final boolean skin;
    private final ChunkOrder columns;
    private final int[] col = new int[3];
    private int index = -1, y, yLo;
    private boolean started;
    private long volume = -1;
    private Box bounds;

    protected ColumnSource(Box rect, boolean skin) {
        this.x0 = rect.minX();
        this.z0 = rect.minZ();
        this.width = rect.width();
        this.depth = rect.depth();
        this.skin = skin;
        this.lo = new int[width * depth];
        this.hi = new int[width * depth];
        java.util.Arrays.fill(lo, 1); // todas vacías (hi < lo) hasta que la subclase diga su rango
        this.columns = new ChunkOrder(new Box(rect.minX(), 0, rect.minZ(), rect.maxX(), 0, rect.maxZ()), Shape.FILL);
    }

    /** Qué poner en (x, y, z) de la columna i sabiendo el bloque actual; {@link #KEEP} para dejarlo. */
    protected abstract int cell(int i, int x, int y, int z, int current);

    /** La cima del sólido de la columna después de la obra (para la piel), o {@link HeightField#NONE}. */
    protected abstract int solidTop(int i);

    /** La base del sólido después de la obra (para la piel de las islas), o NONE si no se ve desde abajo. */
    protected int solidBottom(int i) {
        return HeightField.NONE;
    }

    protected final int index(int x, int z) {
        return (x - x0) + (z - z0) * width;
    }

    /** ¿Se ve (x, y) de la columna i desde fuera? */
    protected boolean visible(int i, int y) {
        int top = solidTop(i), bottom = solidBottom(i);
        if (top == HeightField.NONE || y > top || y == top) return true;
        if (bottom != HeightField.NONE && y <= bottom) return true;
        int x = i % width, z = i / width;
        int[][] n = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
        for (int[] c : n) {
            if (c[0] < 0 || c[1] < 0 || c[0] >= width || c[1] >= depth) return true;
            int j = c[0] + c[1] * width;
            int t = solidTop(j);
            if (t == HeightField.NONE || y > t) return true;
            int b = solidBottom(j);
            if (bottom != HeightField.NONE && b != HeightField.NONE && y < b) return true;
        }
        return false;
    }

    @Override
    public final boolean next(int[] p) {
        while (true) {
            if (started && index >= 0 && y >= yLo) {
                int cy = y--;
                if (skin && !visible(index, cy)) continue;
                p[0] = col[0];
                p[1] = cy;
                p[2] = col[2];
                return true;
            }
            started = true;
            if (!columns.next(col)) {
                index = -1;
                return false;
            }
            index = index(col[0], col[2]);
            y = hi[index];
            yLo = lo[index];
        }
    }

    @Override
    public final int target(int x, int y, int z, int current) {
        return cell(index(x, z), x, y, z, current);
    }

    @Override
    public long volume() {
        if (volume < 0) {
            long v = 0;
            for (int i = 0; i < lo.length; i++) {
                if (hi[i] < lo[i]) continue;
                if (!skin) v += hi[i] - lo[i] + 1;
                else for (int yy = lo[i]; yy <= hi[i]; yy++) if (visible(i, yy)) v++;
            }
            volume = v;
        }
        return volume;
    }

    @Override
    public Box bounds() {
        if (bounds == null) {
            int a = Integer.MAX_VALUE, b = Integer.MIN_VALUE;
            for (int i = 0; i < lo.length; i++) {
                if (hi[i] < lo[i]) continue;
                a = Math.min(a, lo[i]);
                b = Math.max(b, hi[i]);
            }
            if (a > b) a = b = 0;
            bounds = new Box(x0, a, z0, x0 + width - 1, b, z0 + depth - 1);
        }
        return bounds;
    }

    /** Las columnas vacías tienen hi &lt; lo. */
    protected final void empty(int i) {
        lo[i] = 1;
        hi[i] = 0;
    }
}
