package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.ChunkOrder;
import dev.leo.worldcraft.region.Shape;
import dev.leo.worldcraft.region.Solid;

/** Una forma (spec 028 H1): el patrón en cada bloque de la forma, columna por columna en orden de chunks. */
public final class SolidSource implements EditSource {
    private final Solid solid;
    private final Pattern.Compiled pattern;
    private final long seed;
    private final ChunkOrder columns;
    private final Box bounds;
    private final long volume;
    private final int[] col = new int[3];
    private int index = -1, y;

    public SolidSource(Solid solid, Pattern.Compiled pattern, long seed) {
        this.solid = solid;
        this.pattern = pattern;
        this.seed = seed;
        this.columns = new ChunkOrder(new Box(solid.x0, 0, solid.z0, solid.x0 + solid.width - 1, 0, solid.z0 + solid.depth - 1), Shape.FILL);
        Box b = solid.bounds();
        this.bounds = b == null ? new Box(solid.x0, 0, solid.z0, solid.x0, 0, solid.z0) : b;
        this.volume = solid.volume();
    }

    @Override
    public boolean next(int[] p) {
        while (true) {
            if (index >= 0) {
                y = solid.nextY(index, y);
                if (y <= solid.hi(index)) {
                    p[0] = col[0];
                    p[1] = y++;
                    p[2] = col[2];
                    return true;
                }
            }
            if (!columns.next(col)) return false;
            index = solid.index(col[0], col[2]);
            y = solid.lo(index);
        }
    }

    @Override
    public int target(int x, int y, int z, int current) {
        return pattern.apply(x, y, z, seed);
    }

    @Override
    public long volume() {
        return volume;
    }

    @Override
    public Box bounds() {
        return bounds;
    }
}
