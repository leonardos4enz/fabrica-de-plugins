package dev.leo.worldcraft.tool;

import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.region.Line;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Spec 041 H1: lo que calculan los pinceles de líneas y curvas de FAWE (line, catenary, circle, sweep). Las posiciones
 * salen empaquetadas con {@link PointsSource#pack}. Java puro.
 */
public final class LineMath {
    private LineMath() {
    }

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** H1 CA1: la línea de a a b con ese grosor; flat la traza a la altura de a; shell deja la cáscara. */
    public static long[] line(int[] a, int[] b, int radius, boolean shell, boolean flat) {
        int by = flat ? a[1] : b[1];
        long[] pts = Line.points(a[0], a[1], a[2], b[0], by, b[2], radius, PointsSource::pack);
        return shell && radius > 0 ? shell(pts) : dedup(pts);
    }

    /** Los puntos del conjunto con un vecino de las 6 caras fuera de él. */
    public static long[] shell(long[] pts) {
        Set<Long> all = new LinkedHashSet<>();
        for (long p : pts) all.add(p);
        List<Long> out = new ArrayList<>();
        for (long p : all) {
            int x = PointsSource.unpackX(p), y = PointsSource.unpackY(p), z = PointsSource.unpackZ(p);
            for (int[] f : FACES) {
                if (!all.contains(PointsSource.pack(x + f[0], y + f[1], z + f[2]))) {
                    out.add(p);
                    break;
                }
            }
        }
        return out.stream().mapToLong(Long::longValue).toArray();
    }

    private static long[] dedup(long[] pts) {
        return Arrays.stream(pts).distinct().toArray();
    }

    /**
     * H1 CA4: una cuerda colgante de a a b de largo factor × distancia, que cuelga hacia down (un vector unitario; de
     * fábrica, hacia abajo). Es y = k·cosh((u − u0)/k) + v0 en el plano de la cuerda y down; k por bisección de
     * 2k·sinh(D/2k) = √(L² − H²). Con factor 1 (o la cuerda a lo largo de down), una recta.
     */
    public static long[] catenary(int[] a, int[] b, double factor, double[] down, int radius, boolean shell) {
        double[] c = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        double len = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
        double[] up = {-down[0], -down[1], -down[2]};
        double h = c[0] * up[0] + c[1] * up[1] + c[2] * up[2];
        double[] e = {c[0] - h * up[0], c[1] - h * up[1], c[2] - h * up[2]};
        double d = Math.sqrt(e[0] * e[0] + e[1] * e[1] + e[2] * e[2]);
        double total = Math.max(len, factor * len);
        if (d < 1 || total - len < 1e-6) return line(a, b, radius, shell, false);
        for (int i = 0; i < 3; i++) e[i] /= d;
        double target = Math.sqrt(total * total - h * h);
        double lo = 1e-3, hi = 1e7;
        for (int i = 0; i < 200; i++) {
            double k = Math.sqrt(lo * hi);
            double f = 2 * k * Math.sinh(d / (2 * k));
            if (Double.isInfinite(f) || f > target) lo = k;
            else hi = k;
        }
        double k = Math.sqrt(lo * hi);
        double m = d / 2 - k * asinh(h / (2 * k * Math.sinh(d / (2 * k))));
        double v0 = -k * Math.cosh(-m / k);
        int steps = Math.max(2, (int) Math.ceil(total * 4));
        Set<Long> out = new LinkedHashSet<>();
        double er = (radius + 0.5) * (radius + 0.5);
        int[] last = null;
        for (int s = 0; s <= steps; s++) {
            double u = d * s / steps;
            double v = k * Math.cosh((u - m) / k) + v0;
            int x = (int) Math.round(a[0] + e[0] * u + up[0] * v), y = (int) Math.round(a[1] + e[1] * u + up[1] * v),
                    z = (int) Math.round(a[2] + e[2] * u + up[2] * v);
            if (s == steps) {
                x = b[0];
                y = b[1];
                z = b[2];
            }
            int[] here = {x, y, z};
            // Entre dos muestras lejanas (la parte empinada) se une con una recta: sin huecos.
            long[] seg = last == null ? new long[]{PointsSource.pack(x, y, z)} : Line.points(last[0], last[1], last[2], x, y, z, 0, PointsSource::pack);
            for (long p : seg) {
                int px = PointsSource.unpackX(p), py = PointsSource.unpackY(p), pz = PointsSource.unpackZ(p);
                for (int dx = -radius; dx <= radius; dx++)
                    for (int dy = -radius; dy <= radius; dy++)
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (radius > 0 && dx * dx + dy * dy + dz * dz > er) continue;
                            out.add(PointsSource.pack(px + dx, py + dy, pz + dz));
                        }
            }
            last = here;
        }
        long[] pts = out.stream().mapToLong(Long::longValue).toArray();
        return shell && radius > 0 ? shell(pts) : pts;
    }

    private static double asinh(double v) {
        return Math.log(v + Math.sqrt(v * v + 1));
    }

    /**
     * H1 CA5: un círculo de radio r alrededor de (cx, cy, cz), perpendicular a la mirada n; fill = el disco. La regla de
     * la esfera de la 028 (r + ½).
     */
    public static long[] circle(int cx, int cy, int cz, double nx, double ny, double nz, int r, boolean fill) {
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-9) {
            nx = 0;
            ny = 1;
            nz = 0;
            nl = 1;
        }
        nx /= nl;
        ny /= nl;
        nz /= nl;
        double rr = (r + 0.5) * (r + 0.5), in = (r - 0.5) * (r - 0.5);
        List<Long> out = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double along = dx * nx + dy * ny + dz * nz;
                    if (Math.abs(along) > 0.5) continue;
                    // La distancia al centro medida en el plano del círculo.
                    double q = dx * dx + dy * dy + dz * dz - along * along;
                    if (q > rr) continue;
                    if (!fill && q < in) continue;
                    // Un punto con dos muestras sobre el plano (|along| = ½ exacto) se queda solo del lado de adelante.
                    if (Math.abs(along) == 0.5 && along < 0) continue;
                    out.add(PointsSource.pack(cx + dx, cy + dy, cz + dz));
                }
            }
        }
        return out.stream().mapToLong(Long::longValue).toArray();
    }

    /** Dónde va una copia del barrido y hacia dónde va la curva ahí (el eje más cercano en x o z: dx, dz de −1 a 1). */
    public record Placement(int x, int y, int z, int dx, int dz) {
    }

    /**
     * H1 CA3: las copias del portapapeles a lo largo de la curva por los nodos: con copies &gt; 0, esa cantidad repartida
     * pareja (la primera al principio, la última al final); si no, una cada length bloques de curva.
     */
    public static List<Placement> sweep(List<int[]> nodes, int length, int copies) {
        List<double[]> pts = new ArrayList<>();
        int n = nodes.size();
        for (int i = 0; i < Math.max(1, n - 1); i++) {
            int[] p0 = nodes.get(Math.max(0, i - 1)), p1 = nodes.get(i), p2 = nodes.get(Math.min(n - 1, i + 1)), p3 = nodes.get(Math.min(n - 1, i + 2));
            double seg = Math.sqrt(sq(p2[0] - p1[0]) + sq(p2[1] - p1[1]) + sq(p2[2] - p1[2]));
            int steps = Math.max(1, (int) Math.ceil(seg * 4));
            for (int s = i == 0 ? 0 : 1; s <= steps; s++) {
                double t = s / (double) steps;
                pts.add(new double[]{cr(p0[0], p1[0], p2[0], p3[0], t), cr(p0[1], p1[1], p2[1], p3[1], t), cr(p0[2], p1[2], p2[2], p3[2], t)});
            }
        }
        double[] arc = new double[pts.size()];
        for (int i = 1; i < pts.size(); i++) {
            double[] u = pts.get(i - 1), v = pts.get(i);
            arc[i] = arc[i - 1] + Math.sqrt(sq(v[0] - u[0]) + sq(v[1] - u[1]) + sq(v[2] - u[2]));
        }
        double total = arc[arc.length - 1];
        List<Double> at = new ArrayList<>();
        if (copies > 0) {
            for (int i = 0; i < copies; i++) at.add(copies == 1 ? 0 : total * i / (copies - 1));
        } else {
            double step = Math.max(1, length);
            for (double s = 0; s <= total + 1e-9; s += step) at.add(s);
        }
        List<Placement> out = new ArrayList<>();
        int j = 0;
        for (double s : at) {
            while (j < arc.length - 1 && arc[j + 1] < s) j++;
            int k = Math.min(arc.length - 1, j + (s >= arc[j] && j + 1 < arc.length ? 1 : 0));
            double[] p = pts.get(Math.abs(arc[k] - s) < Math.abs(arc[j] - s) ? k : j);
            // La dirección: de la muestra de antes a la de después.
            int a = Math.max(0, j - 2), b = Math.min(pts.size() - 1, j + 3);
            double tx = pts.get(b)[0] - pts.get(a)[0], tz = pts.get(b)[2] - pts.get(a)[2];
            int dx = 0, dz = 0;
            if (Math.abs(tx) >= Math.abs(tz)) dx = tx >= 0 ? 1 : -1;
            else dz = tz >= 0 ? 1 : -1;
            out.add(new Placement((int) Math.round(p[0]), (int) Math.round(p[1]), (int) Math.round(p[2]), dx, dz));
        }
        return out;
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Catmull-Rom uniforme entre p1 (t = 0) y p2 (t = 1), como {@code region.Curve}. */
    private static double cr(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }
}
