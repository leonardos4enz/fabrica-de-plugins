package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;

/**
 * Una obra sin bloques (spec 031: {@code butcher}, {@code remove}): solo sus etapas de entidades. Tiene la caja para el
 * historial y para no cruzarse con otra obra, pero no pide ningún chunk (las etapas piden los suyos).
 */
public final class EmptySource implements EditSource {
    private final Box bounds;

    public EmptySource(Box bounds) {
        this.bounds = bounds;
    }

    @Override
    public boolean next(int[] p) {
        return false;
    }

    @Override
    public int target(int x, int y, int z, int current) {
        return KEEP;
    }

    @Override
    public long volume() {
        return 0;
    }

    @Override
    public Box bounds() {
        return bounds;
    }

    @Override
    public long[] chunks() {
        return new long[0];
    }
}
