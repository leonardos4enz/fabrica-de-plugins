package dev.leo.worldcraft.region;

import java.util.List;

/** Spec 030 (sel convex): la envolvente convexa de los vértices marcados (ver {@link Hull}). */
public final class Convex extends Columns {
    public final Hull hull;

    public Convex(List<int[]> points) {
        this(Hull.of(points));
    }

    private Convex(Hull hull) {
        super(hull.bounds());
        this.hull = hull;
    }

    @Override
    protected int[] compute(int x, int z) {
        int[] c = hull.column(x, z);
        if (c == null) return null;
        int a = Math.max(c[0], bounds.minY()), b = Math.min(c[1], bounds.maxY());
        return b < a ? null : new int[]{a, b};
    }

    @Override
    public String kind() {
        return "convex";
    }
}
