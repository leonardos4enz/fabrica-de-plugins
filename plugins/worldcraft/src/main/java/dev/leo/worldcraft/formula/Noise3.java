package dev.leo.worldcraft.formula;

/**
 * Ruido 3D para las fórmulas (spec 034 H1 CA2): Perlin mejorado con la permutación de la semilla, sus octavas
 * (perlin), crestas (ridgedmulti) y celdas (voronoi). Se usa como el de WorldEdit; los números son otros.
 */
final class Noise3 {
    private final int[] p = new int[512];
    private final long seed;

    Noise3(long seed) {
        this.seed = seed;
        int[] perm = new int[256];
        for (int i = 0; i < 256; i++) perm[i] = i;
        long s = seed;
        for (int i = 255; i > 0; i--) {
            s = mix(s + i);
            int j = (int) Long.remainderUnsigned(s, i + 1);
            int t = perm[i];
            perm[i] = perm[j];
            perm[j] = t;
        }
        for (int i = 0; i < 512; i++) p[i] = perm[i & 255];
    }

    static long mix(long h) {
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /** Un valor de −1 a 1 (aprox.). */
    double at(double x, double y, double z) {
        int X = (int) Math.floor(x) & 255, Y = (int) Math.floor(y) & 255, Z = (int) Math.floor(z) & 255;
        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);
        double u = fade(x), v = fade(y), w = fade(z);
        int A = p[X] + Y, AA = p[A] + Z, AB = p[A + 1] + Z, B = p[X + 1] + Y, BA = p[B] + Z, BB = p[B + 1] + Z;
        return lerp(w, lerp(v, lerp(u, grad(p[AA], x, y, z), grad(p[BA], x - 1, y, z)),
                        lerp(u, grad(p[AB], x, y - 1, z), grad(p[BB], x - 1, y - 1, z))),
                lerp(v, lerp(u, grad(p[AA + 1], x, y, z - 1), grad(p[BA + 1], x - 1, y, z - 1)),
                        lerp(u, grad(p[AB + 1], x, y - 1, z - 1), grad(p[BB + 1], x - 1, y - 1, z - 1))));
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y, v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    /** Octavas que suman (frecuencia × 2 y amplitud × persistencia en cada una). */
    double perlin(double x, double y, double z, double frequency, int octaves, double persistence) {
        octaves = Math.max(1, Math.min(30, octaves));
        double sum = 0, amp = 1, f = frequency;
        for (int i = 0; i < octaves; i++) {
            sum += at(x * f + i * 31.7, y * f + i * 17.3, z * f + i * 11.1) * amp;
            f *= 2;
            amp *= persistence;
        }
        return sum;
    }

    /** Crestas: 1 − |ruido|, al cuadrado, cada octava pesada por la anterior (de −1 a 1, aprox.). */
    double ridged(double x, double y, double z, double frequency, int octaves) {
        octaves = Math.max(1, Math.min(30, octaves));
        double sum = 0, weight = 1, f = frequency, amp = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            double s = 1 - Math.abs(at(x * f + i * 31.7, y * f + i * 17.3, z * f + i * 11.1));
            s *= s * weight;
            weight = Math.max(0, Math.min(1, s * 2));
            sum += s * amp;
            norm += amp;
            f *= 2;
            amp *= 0.5;
        }
        return sum / norm * 2 - 1;
    }

    /** El valor (de −1 a 1) de la celda con el punto más cercano; cada celda entera tiene un punto al azar. */
    double voronoi(double x, double y, double z, double frequency) {
        x *= frequency;
        y *= frequency;
        z *= frequency;
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y), cz = (int) Math.floor(z);
        double best = Double.MAX_VALUE;
        long pick = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            int ix = cx + dx, iy = cy + dy, iz = cz + dz;
            long h = mix(seed ^ (ix * 0x9E3779B97F4A7C15L) ^ (iy * 0xC2B2AE3D27D4EB4FL) ^ (iz * 0x165667B19E3779F9L));
            double px = ix + (h & 0xFFFF) / 65536.0, py = iy + ((h >>> 16) & 0xFFFF) / 65536.0, pz = iz + ((h >>> 32) & 0xFFFF) / 65536.0;
            double d = (px - x) * (px - x) + (py - y) * (py - y) + (pz - z) * (pz - z);
            if (d < best) {
                best = d;
                pick = h;
            }
        }
        return ((pick >>> 48) & 0xFFFF) / 32767.5 - 1;
    }
}
