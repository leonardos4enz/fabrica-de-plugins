package dev.leo.worldcraft.formula;

import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Region;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Lo que calcula una fórmula sobre una selección (plan 034 §1.1), en el hilo de fórmulas: dónde poner (generate), de
 * dónde viene cada bloque (deform) y qué celdas de bioma (generatebiome). Java puro.
 */
public final class Shapes {
    private Shapes() {
    }

    /** Cuánto se evaluó (para decir hasta dónde llegó si se cortó) y el plazo. */
    public static final class Work {
        public final long deadline;
        public volatile long evaluated;
        /** La posición que se estaba evaluando (para el mensaje de las vueltas). */
        public volatile int x, y, z;

        public Work(long deadline) {
            this.deadline = deadline;
        }

        void at(int px, int py, int pz) {
            x = px;
            y = py;
            z = pz;
            evaluated++;
        }
    }

    /** generate: los bloques de la región donde la fórmula es verdadera; hollow, solo los que tocan algo de afuera. */
    public static long[] generate(Region region, Formula f, Space sp, boolean hollow, long seed, Work work) {
        Formula.Run run = f.run(seed).deadline(work.deadline);
        Box b = region.bounds();
        if (!hollow) {
            Longs out = new Longs();
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    int[] span = region.span(x, z);
                    if (span == null) continue;
                    for (int y = span[0]; y <= span[1]; y++) {
                        if (!region.contains(x, y, z)) continue;
                        work.at(x, y, z);
                        if (run.eval(sp.lx(x), sp.ly(y), sp.lz(z)) > 0) out.add(PointsSource.pack(x, y, z));
                    }
                }
            }
            return out.toArray();
        }
        if (b.volume() > Integer.MAX_VALUE - 8) throw new IllegalArgumentException("The selection is too big for a hollow shape");
        int w = b.width(), d = b.depth();
        BitSet in = new BitSet((int) b.volume());
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                int[] span = region.span(x, z);
                if (span == null) continue;
                for (int y = span[0]; y <= span[1]; y++) {
                    if (!region.contains(x, y, z)) continue;
                    work.at(x, y, z);
                    if (run.eval(sp.lx(x), sp.ly(y), sp.lz(z)) > 0) in.set(((y - b.minY()) * d + (z - b.minZ())) * w + (x - b.minX()));
                }
            }
        }
        Longs out = new Longs();
        for (int i = in.nextSetBit(0); i >= 0; i = in.nextSetBit(i + 1)) {
            int x = b.minX() + i % w, z = b.minZ() + (i / w) % d, y = b.minY() + i / w / d;
            if (!inside(in, b, x + 1, y, z) || !inside(in, b, x - 1, y, z) || !inside(in, b, x, y + 1, z) || !inside(in, b, x, y - 1, z)
                    || !inside(in, b, x, y, z + 1) || !inside(in, b, x, y, z - 1)) {
                out.add(PointsSource.pack(x, y, z));
            }
        }
        return out.toArray();
    }

    private static boolean inside(BitSet in, Box b, int x, int y, int z) {
        return b.contains(x, y, z) && in.get(((y - b.minY()) * b.depth() + (z - b.minZ())) * b.width() + (x - b.minX()));
    }

    /**
     * deform: para cada bloque de la región, a dónde mandó la fórmula x, y, z (el origen). Solo los que cambian de
     * lugar; un origen imposible (NaN, infinito) deja el bloque.
     *
     * @param targets destinos y orígenes empaquetados con {@link PointsSource#pack}; sources: la caja de los orígenes o null
     */
    public record Mapping(long[] targets, long[] sources, Box sourceBounds) {
        public int size() {
            return targets.length;
        }
    }

    public static Mapping deform(Region region, Formula f, Space sp, long seed, Work work) {
        Formula.Run run = f.run(seed).deadline(work.deadline);
        Box b = region.bounds();
        Longs to = new Longs(), from = new Longs();
        int a = Integer.MAX_VALUE, c = Integer.MAX_VALUE, e = Integer.MAX_VALUE, g = Integer.MIN_VALUE, h = Integer.MIN_VALUE, k = Integer.MIN_VALUE;
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                int[] span = region.span(x, z);
                if (span == null) continue;
                for (int y = span[0]; y <= span[1]; y++) {
                    if (!region.contains(x, y, z)) continue;
                    work.at(x, y, z);
                    run.eval(sp.lx(x), sp.ly(y), sp.lz(z));
                    int sx = sp.wx(run.input(0)), sy = sp.wy(run.input(1)), sz = sp.wz(run.input(2));
                    if (sx == Integer.MIN_VALUE || sy == Integer.MIN_VALUE || sz == Integer.MIN_VALUE) continue;
                    if (sx == x && sy == y && sz == z) continue;
                    if (sy < -2048 || sy > 2047) continue; // fuera de cualquier mundo: no hay qué leer
                    to.add(PointsSource.pack(x, y, z));
                    from.add(PointsSource.pack(sx, sy, sz));
                    a = Math.min(a, sx);
                    c = Math.min(c, sy);
                    e = Math.min(e, sz);
                    g = Math.max(g, sx);
                    h = Math.max(h, sy);
                    k = Math.max(k, sz);
                }
            }
        }
        return new Mapping(to.toArray(), from.toArray(), to.n == 0 ? null : new Box(a, c, e, g, h, k));
    }

    /** generatebiome: las celdas (esquina mínima) donde la fórmula es verdadera en su centro; hollow, las del borde. */
    public static long[] cells(long[] cells, Formula f, Space sp, boolean hollow, long seed, Work work) {
        Formula.Run run = f.run(seed).deadline(work.deadline);
        Longs out = new Longs();
        for (long cell : cells) {
            int x = PointsSource.unpackX(cell), y = PointsSource.unpackY(cell), z = PointsSource.unpackZ(cell);
            work.at(x, y, z);
            if (run.eval(sp.lx(x + 1.5), sp.ly(y + 1.5), sp.lz(z + 1.5)) > 0) out.add(cell);
        }
        long[] in = out.toArray();
        if (!hollow) return in;
        Set<Long> set = new HashSet<>();
        for (long c : in) set.add(c);
        Longs shell = new Longs();
        for (long c : in) {
            int x = PointsSource.unpackX(c), y = PointsSource.unpackY(c), z = PointsSource.unpackZ(c);
            if (!set.contains(PointsSource.pack(x + 4, y, z)) || !set.contains(PointsSource.pack(x - 4, y, z))
                    || !set.contains(PointsSource.pack(x, y + 4, z)) || !set.contains(PointsSource.pack(x, y - 4, z))
                    || !set.contains(PointsSource.pack(x, y, z + 4)) || !set.contains(PointsSource.pack(x, y, z - 4))) {
                shell.add(c);
            }
        }
        return shell.toArray();
    }

    /** Una lista de long que crece. */
    static final class Longs {
        long[] a = new long[64];
        int n;

        void add(long v) {
            if (n == a.length) a = Arrays.copyOf(a, n * 2);
            a[n++] = v;
        }

        long[] toArray() {
            return Arrays.copyOf(a, n);
        }
    }
}
