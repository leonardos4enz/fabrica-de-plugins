package dev.leo.worldcraft.terrain;

import java.util.Random;

/**
 * La montaña (H1, plan 026 §1.1): uno o varios picos con perfil de campana, el contorno alabeado con ruido para que no sea
 * un círculo, crestas que tallan las laderas (más donde ya es alto) y rugosidad fina; se normaliza para que la cima mida
 * {@code height}. Opcional: un cráter. Java puro: se calcula fuera del hilo principal.
 *
 * @param radius    radio en bloques
 * @param height    altura de la cima sobre el pie
 * @param peaks     picos (1 = uno solo)
 * @param roughness rugosidad fina (0..1)
 * @param ridges    cuánto tallan las crestas (0..1)
 * @param sharpness exponente del perfil (&gt;1 = laderas más cóncavas y cima más aguda)
 * @param crater    radio del cráter respecto al de la montaña (0 = sin cráter)
 */
public record MountainShape(int radius, int height, int peaks, double roughness, double ridges, double sharpness,
                            double crater, long seed) {

    /** La montaña ya calculada sobre un suelo: cima nueva por columna (NONE = no se toca), pendiente y el pie. */
    public record Plan(int[] newTop, float[] slope, int base, int peak) {
    }

    /** Las columnas donde hay montaña de verdad, sin el suelo: alturas (0..height) sobre el pie. Para pruebas y la vista PNG. */
    public double[] heights(int width, int depth, int cx0, int cz0) {
        double[] out = new double[width * depth];
        double[][] pk = peakList();
        Noise n = new Noise(seed);
        double max = 1e-9;
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                double e = raw(n, pk, (x - cx0) / (double) radius, (z - cz0) / (double) radius);
                out[x + z * width] = e;
                max = Math.max(max, e);
            }
        }
        for (int i = 0; i < out.length; i++) {
            double h = height * out[i] / max;
            if (crater > 0) {
                double u = ((i % width) - cx0) / (double) radius, v = ((i / width) - cz0) / (double) radius;
                double d = Math.sqrt(u * u + v * v);
                if (d < crater) h = Math.min(h, height * (1 - 0.5 * (1 - (d / crater) * (d / crater))));
            }
            out[i] = h;
        }
        return out;
    }

    /** La montaña sobre el suelo leído, con el centro en (cx, cz) y el pie en base0 si el centro no tiene suelo. */
    public Plan plan(HeightField f, int cx, int cz, int base0, int maxY) {
        int ci = f.contains(cx, cz) ? f.index(cx, cz) : -1;
        int base = ci >= 0 && f.ground(ci) != HeightField.NONE ? f.ground(ci) : base0;
        double[] h = heights(f.width, f.depth, cx - f.x0, cz - f.z0);
        int[] top = new int[f.size()];
        int[] surface = new int[f.size()];
        int peak = base;
        for (int i = 0; i < top.length; i++) {
            int g = f.ground(i);
            int t = base + (int) Math.round(h[i]);
            t = Math.min(t, maxY);
            boolean grows = h[i] >= 0.5 && f.obstacle(i) != HeightField.HARD && (g == HeightField.NONE || t > g);
            top[i] = grows ? t : HeightField.NONE;
            surface[i] = grows ? t : g;
            if (grows) peak = Math.max(peak, t);
        }
        return new Plan(top, HeightField.slopes(surface, f.width, f.depth), base, peak);
    }

    /** Los picos: {u, v, altura, radio} en unidades del radio; el primero en el centro. */
    double[][] peakList() {
        Random r = new Random(seed ^ 0x5DEECE66DL);
        double[][] out = new double[Math.max(1, peaks)][];
        out[0] = new double[]{0, 0, 1, 1};
        double a0 = r.nextDouble() * Math.PI * 2;
        for (int k = 1; k < out.length; k++) {
            double a = a0 + k * (Math.PI * 2 / (out.length - 1)) + (r.nextDouble() - 0.5) * 0.9;
            double d = 0.3 + r.nextDouble() * 0.3;
            out[k] = new double[]{Math.cos(a) * d, Math.sin(a) * d, 0.5 + r.nextDouble() * 0.3, 0.45 + r.nextDouble() * 0.2};
        }
        return out;
    }

    private double raw(Noise n, double[][] pk, double u, double v) {
        if (u * u + v * v > 2.2) return 0;
        double wu = u + 0.22 * n.fbm(u * 1.7 + 3.1, v * 1.7, 3);
        double wv = v + 0.22 * n.fbm(u * 1.7 - 4.2, v * 1.7 - 5.3, 3);
        double d = Math.sqrt(wu * wu + wv * wv);
        if (d >= 1) return 0;
        double e = 0;
        for (double[] p : pk) {
            double du = wu - p[0], dv = wv - p[1];
            double s = (du * du + dv * dv) / (p[3] * p[3]);
            if (s < 1) e = Math.max(e, p[2] * (1 - s) * (1 - s));
        }
        double edge = smooth(Math.min(1, (1 - d) / 0.3));
        e *= edge;
        if (e <= 0) return 0;
        double r = n.derive(1).ridged(wu * 2.3 + 11, wv * 2.3 - 7, 5);
        e *= 1 + ridges * (0.75 * r - 0.5);
        e += roughness * 0.1 * Math.sqrt(e) * n.derive(2).fbm(u * 7, v * 7, 4);
        return Math.pow(Math.max(0, e), sharpness);
    }

    static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
