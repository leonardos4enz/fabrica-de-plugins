package dev.leo.worldcraft.biome;

import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Region;

import java.util.Arrays;

/**
 * Las celdas de bioma (4×4×4, plan 033 §1.1) que toca una región, en orden de chunks: chunk x, chunk z y dentro del chunk
 * x, z, y. Cada celda es su esquina mínima (múltiplos de 4), empaquetada con {@link PointsSource#pack}. Java puro.
 */
public final class Cells {
    private Cells() {
    }

    /** La esquina mínima de la celda que contiene esa coordenada. */
    public static int floor(int v) {
        return v & ~3;
    }

    /** Las celdas que tienen algún bloque de la región (para una caja, todas las de su caja). */
    public static long[] of(Region region) {
        Box b = region.bounds();
        boolean box = region instanceof Box;
        long[] out = new long[16];
        int n = 0;
        int y0 = floor(b.minY()), y1 = floor(b.maxY());
        for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) {
            for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) {
                int x0 = Math.max(floor(b.minX()), cx << 4), x1 = Math.min(floor(b.maxX()), (cx << 4) + 12);
                int z0 = Math.max(floor(b.minZ()), cz << 4), z1 = Math.min(floor(b.maxZ()), (cz << 4) + 12);
                for (int x = x0; x <= x1; x += 4) {
                    for (int z = z0; z <= z1; z += 4) {
                        for (int y = y0; y <= y1; y += 4) {
                            if (!box && !touches(region, x, y, z)) continue;
                            if (n == out.length) out = Arrays.copyOf(out, n * 2);
                            out[n++] = PointsSource.pack(x, y, z);
                        }
                    }
                }
            }
        }
        return Arrays.copyOf(out, n);
    }

    /** ¿Algún bloque de la celda (x..x+3, y..y+3, z..z+3) está en la región? Mira el tramo de cada una de sus 16 columnas. */
    static boolean touches(Region region, int x, int y, int z) {
        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                int[] span = region.span(x + dx, z + dz);
                if (span == null || span[1] < y || span[0] > y + 3) continue;
                int[] ys = region.ys(x + dx, z + dz, dev.leo.worldcraft.region.Shape.FILL);
                for (int yy : ys) if (yy >= y && yy <= y + 3) return true;
            }
        }
        return false;
    }

    /** El centro de la celda que más cerca queda dentro de la caja (para elegir el bloque que decide la celda). */
    public static int[] center(int x, int y, int z, Box within) {
        return new int[]{clamp(x + 2, within.minX(), within.maxX()), clamp(y + 2, within.minY(), within.maxY()),
                clamp(z + 2, within.minZ(), within.maxZ())};
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
