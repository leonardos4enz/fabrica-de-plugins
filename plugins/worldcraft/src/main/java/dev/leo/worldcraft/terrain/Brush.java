package dev.leo.worldcraft.terrain;

import java.util.function.IntPredicate;

/**
 * Los modos del pincel sobre el mapa de alturas (H3 CA3, investigación §2.3–§2.4). Solo cambian columnas con suelo y sin
 * obstáculos (ni casas ni árboles) dentro del círculo, y que pasen la máscara; el resto queda NONE. La fuerza de cada
 * columna baja hacia el borde. Java puro.
 */
public final class Brush {
    private Brush() {
    }

    /** Las alturas nuevas, y el pie y lo alto de la zona (para la altura relativa al pintar). */
    public record Result(int[] newTop, int base, int span) {
    }

    /** Cuántos bloques más allá del círculo hay que leer (el suavizado y la erosión miran a los vecinos). */
    public static int margin(BrushSettings.Mode mode, int radius) {
        return switch (mode) {
            case SMOOTH -> (int) Math.ceil(2 * sigma(radius)) + 1;
            case ERODE -> 2;
            default -> 0;
        };
    }

    private static double sigma(int radius) {
        return Math.max(1, radius / 2.5);
    }

    public static Result apply(BrushSettings b, HeightField f, int cx, int cz, int centerY, IntPredicate surfaceAllowed, long seed) {
        int n = f.size();
        int[] out = new int[n];
        java.util.Arrays.fill(out, HeightField.NONE);
        boolean[] in = new boolean[n];
        double[] fall = new double[n], core = new double[n];
        int r = b.radius();
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            double d = Math.hypot(f.x(i) - cx, f.z(i) - cz) / (r + 0.5);
            if (d >= 1 || f.ground(i) == HeightField.NONE || f.obstacle(i) != HeightField.CLEAR) continue;
            if (surfaceAllowed != null && !surfaceAllowed.test(f.surface(i))) continue;
            in[i] = true;
            fall[i] = (1 - d * d) * (1 - d * d);
            core[i] = MountainShape.smooth((1 - d) / 0.6);
            lo = Math.min(lo, f.ground(i));
            hi = Math.max(hi, f.ground(i));
        }
        double s = b.strength();
        int ci = f.contains(cx, cz) ? f.index(cx, cz) : -1;
        switch (b.mode()) {
            case RAISE, LOWER -> {
                double amount = Math.max(1, Math.round(s * r * 0.6)) * (b.mode() == BrushSettings.Mode.RAISE ? 1 : -1);
                for (int i = 0; i < n; i++) if (in[i]) out[i] = f.ground(i) + (int) Math.round(amount * fall[i]);
            }
            case FLATTEN -> {
                int target = ci >= 0 && f.ground(ci) != HeightField.NONE ? f.ground(ci) : centerY;
                for (int i = 0; i < n; i++) if (in[i]) out[i] = (int) Math.round(f.ground(i) + (target - f.ground(i)) * s * core[i]);
            }
            case SMOOTH -> {
                double[] blur = blur(f, sigma(r));
                double k = Math.min(1, s * 1.25);
                for (int i = 0; i < n; i++) if (in[i]) out[i] = (int) Math.round(f.ground(i) + (blur[i] - f.ground(i)) * k * core[i]);
            }
            case ERODE -> {
                double[] h = new double[n];
                for (int i = 0; i < n; i++) h[i] = f.ground(i) == HeightField.NONE ? Double.NaN : f.ground(i);
                int passes = 2 + (int) Math.round(s * 8);
                for (int p = 0; p < passes; p++) erodePass(f, h, in, fall);
                for (int i = 0; i < n; i++) if (in[i]) out[i] = (int) Math.round(h[i]);
            }
            case PAINT -> {
                for (int i = 0; i < n; i++) {
                    if (in[i] && (s >= 0.999 || Noise.hash01(f.x(i), f.z(i), seed) < s * (0.5 + 0.5 * core[i]))) out[i] = f.ground(i);
                }
            }
        }
        if (b.mode() != BrushSettings.Mode.PAINT) {
            for (int i = 0; i < n; i++) {
                if (out[i] == HeightField.NONE) continue;
                out[i] = Math.max(f.bottom + 1, Math.min(f.top, out[i])); // dentro de lo leído
                if (out[i] == f.ground(i)) out[i] = HeightField.NONE;
            }
        }
        if (lo > hi) lo = hi = centerY;
        return new Result(out, lo, Math.max(1, hi - lo));
    }

    /** Media gaussiana del suelo (las columnas sin suelo u ocupadas no cuentan). */
    static double[] blur(HeightField f, double sigma) {
        int k = (int) Math.ceil(2 * sigma);
        double[] w = new double[k + 1];
        for (int i = 0; i <= k; i++) w[i] = Math.exp(-(i * i) / (2 * sigma * sigma));
        int n = f.size();
        double[] tmp = new double[n], wt = new double[n], out = new double[n];
        // Dos pasadas (x y luego z), llevando el peso para no contar lo que no es suelo.
        for (int i = 0; i < n; i++) {
            int x = i % f.width, z = i / f.width;
            double sum = 0, ws = 0;
            for (int d = -k; d <= k; d++) {
                int xx = x + d;
                if (xx < 0 || xx >= f.width) continue;
                int j = xx + z * f.width;
                if (!usable(f, j)) continue;
                sum += w[Math.abs(d)] * f.ground(j);
                ws += w[Math.abs(d)];
            }
            tmp[i] = sum;
            wt[i] = ws;
        }
        for (int i = 0; i < n; i++) {
            int x = i % f.width, z = i / f.width;
            double sum = 0, ws = 0;
            for (int d = -k; d <= k; d++) {
                int zz = z + d;
                if (zz < 0 || zz >= f.depth) continue;
                int j = x + zz * f.width;
                sum += w[Math.abs(d)] * tmp[j];
                ws += w[Math.abs(d)] * wt[j];
            }
            out[i] = ws > 0 ? sum / ws : f.ground(i);
        }
        return out;
    }

    private static boolean usable(HeightField f, int i) {
        return f.ground(i) != HeightField.NONE && f.obstacle(i) == HeightField.CLEAR;
    }

    /** Erosión térmica: lo que pasa de un bloque de diferencia con un vecino se reparte hacia abajo. */
    private static void erodePass(HeightField f, double[] h, boolean[] in, double[] fall) {
        double[] delta = new double[h.length];
        for (int i = 0; i < h.length; i++) {
            if (!in[i]) continue;
            int x = i % f.width, z = i / f.width;
            int[][] nb = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
            for (int[] c : nb) {
                if (c[0] < 0 || c[1] < 0 || c[0] >= f.width || c[1] >= f.depth) continue;
                int j = c[0] + c[1] * f.width;
                if (!in[j]) continue;
                double diff = h[i] - h[j];
                if (diff > 1) {
                    double move = (diff - 1) * 0.2 * Math.min(fall[i], fall[j] + 0.2);
                    delta[i] -= move;
                    delta[j] += move;
                }
            }
        }
        for (int i = 0; i < h.length; i++) if (in[i]) h[i] += delta[i];
    }
}
