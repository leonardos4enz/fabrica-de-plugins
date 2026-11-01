package dev.leo.worldcraft.terrain;

import dev.leo.worldcraft.edit.EditJob;
import dev.leo.worldcraft.edit.EditSource;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.ChunkOrder;
import dev.leo.worldcraft.region.Shape;

import java.util.function.IntPredicate;

/**
 * Lee el suelo de un rectángulo con el motor de la 025, como un trabajo de lectura por tandas (H5 CA2): columna por
 * columna (en orden de chunks), de arriba abajo, y deja la columna en cuanto encuentra el suelo y el bloque de debajo.
 * Lo que no es aire, suelo ni cobertura (plantas) queda marcado como obstáculo: árbol (enterrable) o construcción.
 */
public final class ColumnScan implements EditSource, EditJob.Capture {
    private final HeightField field;
    private final ChunkOrder columns;
    private final Mask.Compiled ground, cover, buryable;
    private final IntPredicate air;
    private final int[] col = new int[3];
    private int index = -1, y, phase; // phase 0 buscando suelo, 1 leyendo el de debajo, 2 columna terminada
    private boolean done;

    public ColumnScan(Box rect, int top, int bottom, Mask.Compiled ground, Mask.Compiled cover, Mask.Compiled buryable,
                      IntPredicate air) {
        this.field = new HeightField(rect, top, bottom);
        this.columns = new ChunkOrder(new Box(rect.minX(), 0, rect.minZ(), rect.maxX(), 0, rect.maxZ()), Shape.FILL);
        this.ground = ground;
        this.cover = cover;
        this.buryable = buryable;
        this.air = air;
        this.phase = 2;
    }

    /** El resultado (completo cuando el trabajo terminó). */
    public HeightField field() {
        return field;
    }

    @Override
    public boolean next(int[] p) {
        if (done) return false;
        if (phase == 2 || y < field.bottom) { // columna terminada, o sin suelo en la ventana (queda NONE)
            if (!columns.next(col)) {
                done = true;
                return false;
            }
            index = field.index(col[0], col[2]);
            y = field.top;
            phase = 0;
        }
        p[0] = col[0];
        p[1] = y;
        p[2] = col[2];
        y--;
        return true;
    }

    @Override
    public void take(int x, int py, int z, int state, byte[] tile) {
        int i = index;
        if (phase == 1) {
            field.under[i] = ground.test(state) ? state : field.surface[i];
            phase = 2;
            return;
        }
        if (air.test(state)) return;
        if (ground.test(state)) {
            field.ground[i] = py;
            field.surface[i] = state;
            field.under[i] = state;
            phase = py > field.bottom ? 1 : 2;
            if (phase == 1) y = py - 1; // el de debajo, aunque la ventana termine
            return;
        }
        if (cover.test(state)) field.cover(i, py);
        else field.mark(i, buryable.test(state) ? HeightField.BURYABLE : HeightField.HARD);
    }

    @Override
    public int target(int x, int y, int z, int current) {
        return KEEP;
    }

    @Override
    public long volume() {
        return (long) field.size() * (field.top - field.bottom + 1);
    }

    @Override
    public Box bounds() {
        return new Box(field.x0, field.bottom, field.z0, field.x0 + field.width - 1, field.top, field.z0 + field.depth - 1);
    }
}
