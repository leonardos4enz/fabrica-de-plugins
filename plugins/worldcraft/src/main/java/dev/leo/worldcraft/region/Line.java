package dev.leo.worldcraft.region;

import java.util.Arrays;

/** line (spec 028 H2 CA6): los bloques entre dos puntos, sin huecos, con un grosor opcional. Java puro. */
public final class Line {
    private Line() {
    }

    /**
     * Las posiciones de la línea, empaquetadas con pack (x, y, z → long). Con radius &gt; 0, una esfera de ese radio en
     * cada punto.
     */
    public static long[] points(int x1, int y1, int z1, int x2, int y2, int z2, int radius, Packer pack) {
        int n = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        long[] out = new long[16];
        int k = 0;
        double er = radius + 0.5;
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : i / (double) n;
            int x = (int) Math.round(x1 + (x2 - x1) * t), y = (int) Math.round(y1 + (y2 - y1) * t), z = (int) Math.round(z1 + (z2 - z1) * t);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && dx * dx + dy * dy + dz * dz > er * er) continue;
                        if (k == out.length) out = Arrays.copyOf(out, k * 2);
                        out[k++] = pack.pack(x + dx, y + dy, z + dz);
                    }
                }
            }
        }
        return Arrays.copyOf(out, k);
    }

    public interface Packer {
        long pack(int x, int y, int z);
    }
}
