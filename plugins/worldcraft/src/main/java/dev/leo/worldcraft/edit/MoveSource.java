package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.ChunkOrder;
import dev.leo.worldcraft.region.Shape;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * move (spec 028 H2 CA2, plan §1.2): primero la caja de destino con lo de la foto (aire incluido: el contenido se mueve
 * tal cual), después lo que queda del origen con el relleno. Recorre solo esas dos cajas: un move lejano no visita lo de
 * en medio.
 */
public final class MoveSource implements EditSource {
    private final Snapshot snap;
    private final int dx, dy, dz;
    private final Box dest, from;
    private final Pattern.Compiled fill;
    private final long seed;
    private final ChunkOrder first, second;
    private boolean inSecond;
    private byte[] tile;

    public MoveSource(Snapshot snap, int dx, int dy, int dz, Pattern.Compiled fill, long seed) {
        this.snap = snap;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.from = snap.box;
        this.dest = from.shift(dx, dy, dz);
        this.fill = fill;
        this.seed = seed;
        this.first = new ChunkOrder(dest, Shape.FILL);
        this.second = new ChunkOrder(from, Shape.FILL);
    }

    @Override
    public boolean next(int[] p) {
        if (!inSecond) {
            if (first.next(p)) return true;
            inSecond = true;
        }
        while (second.next(p)) if (!dest.contains(p[0], p[1], p[2])) return true;
        return false;
    }

    @Override
    public int target(int x, int y, int z, int current) {
        tile = null;
        if (dest.contains(x, y, z)) {
            int s = snap.get(x - dx, y - dy, z - dz);
            if (s < 0) return KEEP;
            tile = snap.tile(x - dx, y - dy, z - dz);
            return s;
        }
        return fill.apply(x, y, z, seed);
    }

    @Override
    public byte[] tile() {
        return tile;
    }

    @Override
    public long volume() {
        return dest.volume() + from.volume() - overlap();
    }

    private long overlap() {
        long w = Math.min(dest.maxX(), from.maxX()) - Math.max(dest.minX(), from.minX()) + 1;
        long h = Math.min(dest.maxY(), from.maxY()) - Math.max(dest.minY(), from.minY()) + 1;
        long d = Math.min(dest.maxZ(), from.maxZ()) - Math.max(dest.minZ(), from.minZ()) + 1;
        return w <= 0 || h <= 0 || d <= 0 ? 0 : w * h * d;
    }

    @Override
    public Box bounds() {
        return Box.of(Math.min(dest.minX(), from.minX()), Math.min(dest.minY(), from.minY()), Math.min(dest.minZ(), from.minZ()),
                Math.max(dest.maxX(), from.maxX()), Math.max(dest.maxY(), from.maxY()), Math.max(dest.maxZ(), from.maxZ()));
    }

    @Override
    public long[] chunks() {
        Set<Long> out = new LinkedHashSet<>();
        for (long c : ChunkOrder.chunks(dest)) out.add(c);
        for (long c : ChunkOrder.chunks(from)) out.add(c);
        return out.stream().mapToLong(Long::longValue).toArray();
    }

    /** La caja de destino (para --select). */
    public Box destination() {
        return dest;
    }
}
