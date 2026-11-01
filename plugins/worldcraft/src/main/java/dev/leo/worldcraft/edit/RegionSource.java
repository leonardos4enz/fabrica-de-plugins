package dev.leo.blockwright.edit;

import dev.leo.blockwright.pattern.Mask;
import dev.leo.blockwright.pattern.Pattern;
import dev.leo.blockwright.region.Box;
import dev.leo.blockwright.region.ChunkOrder;
import dev.leo.blockwright.region.Shape;

/** set, replace, walls y faces (H3 CA1): el patrón en la forma de la caja, donde la máscara deja. */
public final class RegionSource implements EditSource {
    private final Box box;
    private final Shape shape;
    private final Mask.Compiled mask;
    private final Pattern.Compiled pattern;
    private final long seed;
    private final ChunkOrder order;

    /** mask null = todo. */
    public RegionSource(Box box, Shape shape, Mask.Compiled mask, Pattern.Compiled pattern, long seed) {
        this.box = box;
        this.shape = shape;
        this.mask = mask;
        this.pattern = pattern;
        this.seed = seed;
        this.order = new ChunkOrder(box, shape);
    }

    @Override
    public boolean next(int[] p) {
        return order.next(p);
    }

    @Override
    public int target(int x, int y, int z, int current) {
        if (mask != null && !mask.test(current)) return KEEP;
        return pattern == null ? KEEP : pattern.apply(x, y, z, seed);
    }

    @Override
    public long volume() {
        return box.count(shape);
    }

    @Override
    public Box bounds() {
        return box;
    }
}
