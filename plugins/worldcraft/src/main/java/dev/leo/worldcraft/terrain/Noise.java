package dev.leo.worldcraft.terrain;

/**
 * Ruido de gradiente 2D con semilla (plan 026 §1), escrito desde cero: cada esquina de la rejilla tiene un gradiente que
 * sale de un hash de (x, z, semilla) y la mezcla usa la curva quíntica. Encima, fBm (octavas que suman), crestas (octavas
 * de «1 − |ruido|» multiplicadas por la anterior: detalle solo donde ya es alto) y alabeo de dominio. Inmutable: se puede
 * usar desde cualquier hilo.
 */
public final class Noise {
    private final long seed;

    public Noise(long seed) {
        this.seed = seed;
    }

    /** Otra semilla derivada (para descorrelacionar usos del mismo plan). */
    public Noise derive(int salt) {
        return new Noise(mix(seed + salt * 0x632BE59BD9B4E019L));
    }

    /** Ruido en [-1, 1] (en la práctica casi siempre en [-0.75, 0.75]). */
    public double at(double x, double z) {
        int x0 = floor(x), z0 = floor(z);
        double fx = x - x0, fz = z - z0;
        double a = corner(x0, z0, fx, fz), b = corner(x0 + 1, z0, fx - 1, fz);
        double c = corner(x0, z0 + 1, fx, fz - 1), d = corner(x0 + 1, z0 + 1, fx - 1, fz - 1);
        double u = fade(fx), v = fade(fz);
        return 1.4142 * lerp(lerp(a, b, u), lerp(c, d, u), v);
    }

    /** Octavas con frecuencia ×2 y amplitud ×0.5, normalizado a [-1, 1]. */
    public double fbm(double x, double z, int octaves) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += amp * at(x * f + i * 17.31, z * f - i * 9.17);
            norm += amp;
            amp *= 0.5;
            f *= 2;
        }
        return sum / norm;
    }

    /** Crestas en [0, 1]: «1 − |ruido|» al cuadrado, cada octava pesada por la anterior. */
    public double ridged(double x, double z, int octaves) {
        double sum = 0, amp = 1, norm = 0, f = 1, weight = 1;
        for (int i = 0; i < octaves; i++) {
            double n = 1 - Math.abs(at(x * f + i * 31.7, z * f + i * 11.3));
            n *= n;
            n *= weight;
            weight = Math.min(1, Math.max(0, n * 2));
            sum += n * amp;
            norm += amp;
            amp *= 0.5;
            f *= 2;
        }
        return Math.min(1, sum / norm * 1.6);
    }

    private double corner(int ix, int iz, double dx, double dz) {
        long h = mix(seed ^ (ix * 0x9E3779B97F4A7C15L) ^ (iz * 0xC2B2AE3D27D4EB4FL));
        double angle = (h >>> 11) * 0x1.0p-53 * Math.PI * 2;
        return Math.cos(angle) * dx + Math.sin(angle) * dz;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    /** SplitMix64: un número bien repartido de otro. */
    public static long mix(long h) {
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /** Un número en [0, 1) fijo para (x, z, semilla): para decisiones por columna (pintar con fuerza, raíces). */
    public static double hash01(int x, int z, long seed) {
        return (mix(seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0x165667B19E3779F9L)) >>> 11) * 0x1.0p-53;
    }
}
