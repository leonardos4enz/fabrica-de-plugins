package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.ChunkOrder;
import dev.leo.worldcraft.region.Direction;
import dev.leo.worldcraft.region.Shape;

import java.util.function.IntPredicate;

/**
 * stack (spec 028 H2 CA1): n copias de la foto seguidas hacia una dirección. Cada posición de la caja de las copias mira
 * la foto en «posición mod tamaño». El aire copiado no se pone salvo con withAir (no borra lo que hay alrededor).
 */
public final class StackSource implements EditSource {
    private final Snapshot snap;
    private final boolean withAir;
    private final IntPredicate isAir;
    private final Box box;
    private final ChunkOrder order;
    private byte[] tile;

    public StackSource(Snapshot snap, Direction d, int n, boolean withAir, IntPredicate isAir) {
        this.snap = snap;
        this.withAir = withAir;
        this.isAir = isAir;
        this.box = copies(snap.box, d, n);
        this.order = new ChunkOrder(box, Shape.FILL);
    }

    /** La caja de las n copias (sin la original). */
    public static Box copies(Box b, Direction d, int n) {
        Box first = b.shift(d.dx * b.width(), d.dy * b.height(), d.dz * b.depth());
        Box last = b.shift(d.dx * b.width() * n, d.dy * b.height() * n, d.dz * b.depth() * n);
        return Box.of(Math.min(first.minX(), last.minX()), Math.min(first.minY(), last.minY()), Math.min(first.minZ(), last.minZ()),
                Math.max(first.maxX(), last.maxX()), Math.max(first.maxY(), last.maxY()), Math.max(first.maxZ(), last.maxZ()));
    }

    /** La última copia (para --select). */
    public static Box last(Box b, Direction d, int n) {
        return b.shift(d.dx * b.width() * n, d.dy * b.height() * n, d.dz * b.depth() * n);
    }

    @Override
    public boolean next(int[] p) {
        return order.next(p);
    }

    @Override
    public int target(int x, int y, int z, int current) {
        Box b = snap.box;
        int sx = b.minX() + Math.floorMod(x - b.minX(), b.width());
        int sy = b.minY() + Math.floorMod(y - b.minY(), b.height());
        int sz = b.minZ() + Math.floorMod(z - b.minZ(), b.depth());
        tile = null;
        int s = snap.get(sx, sy, sz);
        if (s < 0 || (!withAir && isAir.test(s))) return KEEP;
        tile = snap.tile(sx, sy, sz);
        return s;
    }

    @Override
    public byte[] tile() {
        return tile;
    }

    @Override
    public long volume() {
        return box.volume();
    }

    @Override
    public Box bounds() {
        return box;
    }
}
