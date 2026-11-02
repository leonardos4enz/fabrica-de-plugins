package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.ChunkOrder;
import dev.leo.worldcraft.region.Region;
import dev.leo.worldcraft.region.Shape;

/** set, replace, walls y faces (H3 CA1): el patrón en la forma de la caja, donde la máscara deja. Spec 030: cualquier región. */
public final class RegionSource implements EditSource {
    private final Region box;
    private final Shape shape;
    private final Mask.Compiled mask;
    private final Pattern.Compiled pattern;
    private final long seed;
    private final ChunkOrder order;
    /** Spec 029: lo que había alrededor antes de la obra (para «above», «exposed»…); null si la máscara no lo mira. */
    private final Mask.Neighbors near;

    /** mask null = todo. */
    public RegionSource(Region box, Shape shape, Mask.Compiled mask, Pattern.Compiled pattern, long seed) {
        this(box, shape, mask, pattern, seed, null);
    }

    public RegionSource(Region box, Shape shape, Mask.Compiled mask, Pattern.Compiled pattern, long seed, Mask.Neighbors near) {
        this.box = box;
        this.shape = shape;
        this.mask = mask;
        this.pattern = pattern;
        this.seed = seed;
        this.near = near;
        this.order = new ChunkOrder(box, shape);
    }

    @Override
    public boolean next(int[] p) {
        return order.next(p);
    }

    @Override
    public int target(int x, int y, int z, int current) {
        if (mask != null && !mask.test(x, y, z, current, near, seed)) return KEEP;
        return pattern == null ? KEEP : pattern.apply(x, y, z, current, seed);
    }

    @Override
    public long volume() {
        return box.count(shape);
    }

    @Override
    public Box bounds() {
        return box.bounds();
    }
}
