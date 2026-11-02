package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.region.Box;

/**
 * Spec 029 H4: una obra con la máscara global del jugador (/wc gmask): donde el bloque actual no pasa, se deja. Solo
 * máscaras sin vecinos (el azar sí: decide con la posición y la semilla).
 */
public final class MaskedSource implements EditSource {
    private final EditSource inner;
    private final Mask.Compiled mask;
    private final long seed;

    public MaskedSource(EditSource inner, Mask.Compiled mask, long seed) {
        this.inner = inner;
        this.mask = mask;
        this.seed = seed;
    }

    @Override
    public boolean next(int[] p) {
        return inner.next(p);
    }

    @Override
    public int target(int x, int y, int z, int current) {
        if (!mask.test(x, y, z, current, null, seed)) return KEEP;
        return inner.target(x, y, z, current);
    }

    @Override
    public byte[] tile() {
        return inner.tile();
    }

    @Override
    public long volume() {
        return inner.volume();
    }

    @Override
    public Box bounds() {
        return inner.bounds();
    }

    @Override
    public long[] chunks() {
        return inner.chunks();
    }

    @Override
    public boolean ready() {
        return inner.ready();
    }

    @Override
    public long skipped() {
        return inner.skipped();
    }
}
