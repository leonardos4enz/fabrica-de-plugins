package dev.leo.worldcraft.region;

import java.util.ArrayList;
import java.util.List;

/**
 * Spec 030 H3: el dibujo del contorno de cada forma, en coordenadas del mundo (un bloque ocupa [x, x + 1]): segmentos
 * {x1, y1, z1, x2, y2, z2} y puntos sueltos {x, y, z}. La varita los dibuja con partículas; aquí es Java puro.
 */
public record Outline(List<double[]> segments, List<double[]> points) {
    private static final int ROUND = 48;

    public static Outline of(Region r) {
        List<double[]> seg = new ArrayList<>(), pts = new ArrayList<>();
        if (r instanceof Box b) {
            box(seg, b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
        } else if (r instanceof Polygon p) {
            int n = p.xs.length;
            for (int i = 0; i < n; i++) {
                double ax = p.xs[i] + 0.5, az = p.zs[i] + 0.5;
                seg.add(new double[]{ax, p.minY, az, ax, p.maxY + 1, az});
                if (n == 1 || (n == 2 && i == 1)) continue;
                int j = (i + 1) % n;
                double bx = p.xs[j] + 0.5, bz = p.zs[j] + 0.5;
                seg.add(new double[]{ax, p.minY, az, bx, p.minY, bz});
                seg.add(new double[]{ax, p.maxY + 1, az, bx, p.maxY + 1, bz});
            }
        } else if (r instanceof Ellipsoid e) {
            double x = e.cx + 0.5, y = e.cy + 0.5, z = e.cz + 0.5, a = e.rx + 0.5, b = e.ry + 0.5, c = e.rz + 0.5;
            ring(seg, x, y, z, a, 0, 0, 0, 0, c); // el ecuador (x, z)
            ring(seg, x, y, z, a, 0, 0, 0, b, 0); // (x, y)
            ring(seg, x, y, z, 0, 0, c, 0, b, 0); // (z, y)
        } else if (r instanceof Cylinder c) {
            double x = c.cx + 0.5, z = c.cz + 0.5, a = c.rx + 0.5, b = c.rz + 0.5;
            ring(seg, x, c.minY, z, a, 0, 0, 0, 0, b);
            ring(seg, x, c.maxY + 1, z, a, 0, 0, 0, 0, b);
            seg.add(new double[]{x + a, c.minY, z, x + a, c.maxY + 1, z});
            seg.add(new double[]{x - a, c.minY, z, x - a, c.maxY + 1, z});
            seg.add(new double[]{x, c.minY, z + b, x, c.maxY + 1, z + b});
            seg.add(new double[]{x, c.minY, z - b, x, c.maxY + 1, z - b});
        } else if (r instanceof Convex v) {
            seg.addAll(v.hull.edges());
        } else if (r instanceof PointSet s) {
            // la mágica: el centro de cada bloque del borde
            for (long k : s.packed()) {
                int x = PointSet.x(k), y = PointSet.y(k), z = PointSet.z(k);
                if (!(s.contains(x + 1, y, z) && s.contains(x - 1, y, z) && s.contains(x, y + 1, z)
                        && s.contains(x, y - 1, z) && s.contains(x, y, z + 1) && s.contains(x, y, z - 1))) {
                    pts.add(new double[]{x + 0.5, y + 0.5, z + 0.5});
                }
            }
        } else {
            Box b = r.bounds();
            box(seg, b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
        }
        return new Outline(seg, pts);
    }

    /** Las 12 aristas de una caja. */
    static void box(List<double[]> seg, double x0, double y0, double z0, double x1, double y1, double z1) {
        double[][] e = {
                {x0, y0, z0, x1, y0, z0}, {x0, y1, z0, x1, y1, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z1, x1, y1, z1},
                {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
                {x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}};
        seg.addAll(List.of(e));
    }

    /** Una elipse alrededor de (x, y, z) con dos semiejes u y v, en ROUND segmentos. */
    private static void ring(List<double[]> seg, double x, double y, double z, double ux, double uy, double uz,
                             double vx, double vy, double vz) {
        for (int i = 0; i < ROUND; i++) {
            double a = 2 * Math.PI * i / ROUND, b = 2 * Math.PI * (i + 1) / ROUND;
            seg.add(new double[]{x + ux * Math.cos(a) + vx * Math.sin(a), y + uy * Math.cos(a) + vy * Math.sin(a), z + uz * Math.cos(a) + vz * Math.sin(a),
                    x + ux * Math.cos(b) + vx * Math.sin(b), y + uy * Math.cos(b) + vy * Math.sin(b), z + uz * Math.cos(b) + vz * Math.sin(b)});
        }
    }

    /** El largo total de los segmentos (para repartir las partículas). */
    public double length() {
        double t = 0;
        for (double[] s : segments) t += Math.sqrt(Math.pow(s[3] - s[0], 2) + Math.pow(s[4] - s[1], 2) + Math.pow(s[5] - s[2], 2));
        return t;
    }
}
