package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;

import java.util.function.IntPredicate;

/**
 * Spec 041 H3 CA2, CA4: una obra con una máscara de origen (//gsmask, /smask): un bloque que la obra pondría y no cumple
 * la máscara se deja (con !air, pegar no pone el aire). La máscara mira el estado nuevo, no el del mundo.
 */
public final class SourceMaskedSource implements EditSource {
    private final EditSource inner;
    private final IntPredicate allowed;

    public SourceMaskedSource(EditSource inner, IntPredicate allowed) {
        this.inner = inner;
        this.allowed = allowed;
    }

    @Override
    public boolean next(int[] p) {
        return inner.next(p);
    }

    @Override
    public int target(int x, int y, int z, int current) {
        int t = inner.target(x, y, z, current);
        return t != KEEP && !allowed.test(t) ? KEEP : t;
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
