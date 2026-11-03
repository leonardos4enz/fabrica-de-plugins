package dev.leo.worldcraft.edit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/**
 * La nieve en capas (spec 040): snowsmooth alisa la nieve de una zona contando cada capa como un octavo de bloque (como
 * el mapa de alturas de nieve de WorldEdit), y el pincel snow la hace caer. Solo cambian capas de nieve (o el agua que se
 * congela), nunca el suelo. Java puro; las posiciones salen con {@link PointsSource#pack}.
 */
public final class SnowMath {
    private SnowMath() {
    }

    /**
     * Lo que se pregunta a los estados (en el hilo principal se arma como conjuntos: lección 032).
     *
     * @param layers capas de nieve de un estado (0 si no es nieve en capas)
     * @param holds  ¿aguanta una capa de nieve encima? (la cara de arriba entera)
     * @param snow   el estado de nieve con 1..8 capas (índice 0 sin usar)
     */
    public record Rules(IntPredicate air, IntUnaryOperator layers, IntPredicate holds, int[] snow, int airState, IntPredicate water, int ice) {
    }

    /** El núcleo gaussiano de 5×5 de WorldEdit (1 4 6 4 1 por 1 4 6 4 1). */
    private static final int[] K = {1, 4, 6, 4, 1};

    /**
     * snowsmooth (H1 CA2, H2 CA2): las columnas de la caja de la foto que pasan inside, alisadas passes veces. Una columna
     * cuenta si tiene suelo dentro de la caja que aguanta nieve y lugar encima.
     */
    public static Map<Long, Integer> smooth(Snapshot snap, TopScan.Columns inside, int passes, Rules r) {
        var b = snap.box;
        int w = b.width(), d = b.depth();
        int[] ground = new int[w * d], old = new int[w * d];
        boolean[] ok = new boolean[w * d];
        double[] h = new double[w * d];
        for (int z = b.minZ(); z <= b.maxZ(); z++) {
            for (int x = b.minX(); x <= b.maxX(); x++) {
                int i = (z - b.minZ()) * w + (x - b.minX());
                if (!inside.test(x, z)) continue;
                for (int y = b.maxY(); y >= b.minY(); y--) {
                    int s = snap.get(x, y, z);
                    if (s < 0 || r.air().test(s)) continue;
                    int l = r.layers().applyAsInt(s);
                    if (l > 0) {
                        old[i] = l;
                        continue;
                    }
                    // y es el suelo: tiene que aguantar nieve y tener lugar encima dentro de la caja
                    if (r.holds().test(s) && y < b.maxY()) {
                        ok[i] = true;
                        ground[i] = y;
                        h[i] = (double) y * 8 + old[i];
                    }
                    break;
                }
                if (!ok[i]) old[i] = 0;
            }
        }
        for (int p = 0; p < passes; p++) {
            double[] next = h.clone();
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    int i = z * w + x;
                    if (!ok[i]) continue;
                    double sum = 0, weight = 0;
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            int xx = x + dx, zz = z + dz;
                            if (xx < 0 || zz < 0 || xx >= w || zz >= d || !ok[zz * w + xx]) continue;
                            double k = K[dx + 2] * K[dz + 2];
                            sum += h[zz * w + xx] * k;
                            weight += k;
                        }
                    }
                    next[i] = sum / weight;
                }
            }
            h = next;
        }
        Map<Long, Integer> out = new LinkedHashMap<>();
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int i = z * w + x;
                if (!ok[i]) continue;
                int layers = (int) Math.max(0, Math.min(8, Math.round(h[i] - ground[i] * 8.0)));
                if (layers == old[i]) continue;
                out.put(PointsSource.pack(b.minX() + x, ground[i] + 1, b.minZ() + z), layers == 0 ? r.airState() : r.snow()[layers]);
            }
        }
        return out;
    }

    /**
     * El pincel snow (H2 CA1): en cada columna del círculo, de cy + radio hacia abajo hasta cy − radio, el primer bloque
     * que no es aire: el agua quieta se congela, sobre un suelo que aguanta va una capa, sobre nieve se suma una con stack.
     */
    public static Map<Long, Integer> fall(Snapshot snap, int cx, int cy, int cz, int radius, boolean stack, Rules r) {
        Map<Long, Integer> out = new LinkedHashMap<>();
        double e = (radius + 0.5) * (radius + 0.5);
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dz * dz > e) continue;
                int x = cx + dx, z = cz + dz;
                for (int y = cy + radius; y >= cy - radius; y--) {
                    int s = snap.get(x, y, z);
                    if (s < 0 || r.air().test(s)) continue;
                    int l = r.layers().applyAsInt(s);
                    if (l > 0) {
                        if (stack && l < 8) out.put(PointsSource.pack(x, y, z), r.snow()[l + 1]);
                    } else if (r.water().test(s)) {
                        out.put(PointsSource.pack(x, y, z), r.ice());
                    } else if (r.holds().test(s)) {
                        int up = snap.get(x, y + 1, z);
                        if (up >= 0 && r.air().test(up)) out.put(PointsSource.pack(x, y + 1, z), r.snow()[1]);
                    }
                    break;
                }
            }
        }
        return out;
    }
}
