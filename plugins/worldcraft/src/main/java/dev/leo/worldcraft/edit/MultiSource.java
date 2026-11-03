package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;

import java.util.List;

/**
 * Spec 041: varias fuentes en una sola obra, una tras otra (el barrido y populateschematic pegan varias copias): un solo
 * deshacer. Donde se pisan, gana la última.
 */
public final class MultiSource implements EditSource {
    private final List<EditSource> parts;
    private final Box bounds;
    private int i;

    public MultiSource(List<EditSource> parts) {
        if (parts.isEmpty()) throw new IllegalArgumentException("nothing to do");
        this.parts = List.copyOf(parts);
        Box b = null;
        for (EditSource s : parts) {
            Box o = s.bounds();
            b = b == null ? o : new Box(Math.min(b.minX(), o.minX()), Math.min(b.minY(), o.minY()), Math.min(b.minZ(), o.minZ()),
                    Math.max(b.maxX(), o.maxX()), Math.max(b.maxY(), o.maxY()), Math.max(b.maxZ(), o.maxZ()));
        }
        this.bounds = b;
    }

    @Override
    public boolean next(int[] p) {
        while (i < parts.size()) {
            if (parts.get(i).next(p)) return true;
            i++;
        }
        return false;
    }

    @Override
    public int target(int x, int y, int z, int current) {
        return parts.get(i).target(x, y, z, current);
    }

    @Override
    public byte[] tile() {
        return i < parts.size() ? parts.get(i).tile() : null;
    }

    @Override
    public long volume() {
        long v = 0;
        for (EditSource s : parts) v += s.volume();
        return v;
    }

    @Override
    public Box bounds() {
        return bounds;
    }

    @Override
    public long[] chunks() {
        java.util.LinkedHashSet<Long> all = new java.util.LinkedHashSet<>();
        for (EditSource s : parts) for (long c : s.chunks()) all.add(c);
        return all.stream().mapToLong(Long::longValue).toArray();
    }

    @Override
    public boolean ready() {
        for (EditSource s : parts) if (!s.ready()) return false;
        return true;
    }
}
