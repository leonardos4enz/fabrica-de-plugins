package dev.leo.worldcraft.region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spec 030 (sel convex, plan §1.2): la envolvente convexa de los cubos de los bloques marcados. Inflar cada vértice a su
 * cubo (esquinas a ±½) hace que nunca sea plana ni una recta, así el algoritmo incremental no tiene casos degenerados.
 * Las coordenadas van al doble y relativas al primer vértice: todo es entero ({@code long}), sin redondeo.
 */
public final class Hull {
    /** El ancho máximo de los vértices (para que los productos quepan en un long). */
    public static final int MAX_EXTENT = 60_000;

    private final int ox, oy, oz;
    /** Semiespacios {nx, ny, nz, d}: dentro si nx·X + ny·Y + nz·Z ≤ d (X = 2·(x − ox)). Sin repetidos. */
    private final List<long[]> planes = new ArrayList<>();
    /** Las aristas que se ven (sin las diagonales entre caras del mismo plano), en coordenadas al doble. */
    private final List<long[]> edges = new ArrayList<>();
    private final Box bounds;

    private Hull(int ox, int oy, int oz, Box bounds) {
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.bounds = bounds;
    }

    /** points: {x, y, z} de cada vértice marcado (al menos uno). */
    public static Hull of(List<int[]> points) {
        if (points.isEmpty()) throw new IllegalArgumentException("no points");
        int ax = Integer.MAX_VALUE, ay = Integer.MAX_VALUE, az = Integer.MAX_VALUE;
        int bx = Integer.MIN_VALUE, by = Integer.MIN_VALUE, bz = Integer.MIN_VALUE;
        for (int[] p : points) {
            ax = Math.min(ax, p[0]);
            ay = Math.min(ay, p[1]);
            az = Math.min(az, p[2]);
            bx = Math.max(bx, p[0]);
            by = Math.max(by, p[1]);
            bz = Math.max(bz, p[2]);
        }
        if ((long) bx - ax > MAX_EXTENT || (long) by - ay > MAX_EXTENT || (long) bz - az > MAX_EXTENT) {
            throw new IllegalArgumentException("the selection is too wide");
        }
        int[] o = points.get(0);
        Hull h = new Hull(o[0], o[1], o[2], new Box(ax, ay, az, bx, by, bz));
        Set<List<Long>> seen = new LinkedHashSet<>();
        for (int[] p : points) {
            long X = 2L * (p[0] - o[0]), Y = 2L * (p[1] - o[1]), Z = 2L * (p[2] - o[2]);
            for (int c = 0; c < 8; c++) {
                seen.add(List.of(X + ((c & 1) == 0 ? -1 : 1), Y + ((c & 2) == 0 ? -1 : 1), Z + ((c & 4) == 0 ? -1 : 1)));
            }
        }
        List<long[]> pts = new ArrayList<>();
        for (List<Long> p : seen) pts.add(new long[]{p.get(0), p.get(1), p.get(2)});
        h.build(pts);
        return h;
    }

    // ------------------------------------------------------------------ aritmética exacta

    private static long[] sub(long[] a, long[] b) {
        return new long[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static long[] cross(long[] a, long[] b) {
        return new long[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static long dot(long[] a, long[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** &gt; 0 si d está del lado de afuera (la normal) de la cara (a, b, c). */
    private static long orient(long[] a, long[] b, long[] c, long[] d) {
        return dot(cross(sub(b, a), sub(c, a)), sub(d, a));
    }

    // ------------------------------------------------------------------ envolvente incremental

    private void build(List<long[]> p) {
        int a = 0, b = -1, c = -1, d = -1;
        long best = -1;
        for (int i = 1; i < p.size(); i++) {
            long[] v = sub(p.get(i), p.get(a));
            if (dot(v, v) > best) {
                best = dot(v, v);
                b = i;
            }
        }
        best = -1;
        for (int i = 0; i < p.size(); i++) {
            long[] n = cross(sub(p.get(b), p.get(a)), sub(p.get(i), p.get(a)));
            if (dot(n, n) > best) {
                best = dot(n, n);
                c = i;
            }
        }
        best = -1;
        for (int i = 0; i < p.size(); i++) {
            long o = Math.abs(orient(p.get(a), p.get(b), p.get(c), p.get(i)));
            if (o > best) {
                best = o;
                d = i;
            }
        }
        List<int[]> faces = new ArrayList<>();
        int[][] tetra = {{a, b, c, d}, {a, b, d, c}, {a, c, d, b}, {b, c, d, a}};
        for (int[] t : tetra) {
            // la cara mira hacia afuera: el cuarto vértice queda del lado de adentro
            faces.add(orient(p.get(t[0]), p.get(t[1]), p.get(t[2]), p.get(t[3])) > 0 ? new int[]{t[0], t[2], t[1]} : new int[]{t[0], t[1], t[2]});
        }
        for (int i = 0; i < p.size(); i++) {
            if (i == a || i == b || i == c || i == d) continue;
            long[] q = p.get(i);
            List<int[]> visible = new ArrayList<>(), keep = new ArrayList<>();
            for (int[] f : faces) (orient(p.get(f[0]), p.get(f[1]), p.get(f[2]), q) > 0 ? visible : keep).add(f);
            if (visible.isEmpty()) continue;
            Set<Long> directed = new HashSet<>();
            for (int[] f : visible) for (int k = 0; k < 3; k++) directed.add(edge(f[k], f[(k + 1) % 3]));
            for (int[] f : visible) {
                for (int k = 0; k < 3; k++) {
                    int u = f[k], v = f[(k + 1) % 3];
                    if (!directed.contains(edge(v, u))) keep.add(new int[]{u, v, i});
                }
            }
            faces = keep;
        }
        Map<List<Long>, long[]> unique = new HashMap<>();
        Map<Long, List<List<Long>>> byEdge = new HashMap<>();
        for (int[] f : faces) {
            long[] n = cross(sub(p.get(f[1]), p.get(f[0])), sub(p.get(f[2]), p.get(f[0])));
            long dd = dot(n, p.get(f[0]));
            long g = gcd(gcd(Math.abs(n[0]), Math.abs(n[1])), gcd(Math.abs(n[2]), Math.abs(dd)));
            if (g == 0) continue;
            long[] plane = {n[0] / g, n[1] / g, n[2] / g, dd / g};
            List<Long> key = List.of(plane[0], plane[1], plane[2], plane[3]);
            unique.putIfAbsent(key, plane);
            for (int k = 0; k < 3; k++) {
                int u = f[k], v = f[(k + 1) % 3];
                byEdge.computeIfAbsent(edge(Math.min(u, v), Math.max(u, v)), x -> new ArrayList<>()).add(key);
            }
        }
        planes.addAll(unique.values());
        for (Map.Entry<Long, List<List<Long>>> e : byEdge.entrySet()) {
            List<List<Long>> ps = e.getValue();
            if (ps.size() == 2 && ps.get(0).equals(ps.get(1))) continue; // diagonal dentro de una cara plana
            long[] u = p.get((int) (e.getKey() >> 32)), v = p.get((int) (long) e.getKey());
            edges.add(new long[]{u[0], u[1], u[2], v[0], v[1], v[2]});
        }
    }

    private static long edge(int u, int v) {
        return ((long) u << 32) | (v & 0xFFFFFFFFL);
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    // ------------------------------------------------------------------ consultas

    public Box bounds() {
        return bounds;
    }

    public int planes() {
        return planes.size();
    }

    /** ¿Está el centro del bloque dentro (con el borde)? */
    public boolean contains(int x, int y, int z) {
        long X = 2L * (x - ox), Y = 2L * (y - oy), Z = 2L * (z - oz);
        for (long[] pl : planes) if (pl[0] * X + pl[1] * Y + pl[2] * Z > pl[3]) return false;
        return true;
    }

    /** [lo, hi] de la columna (la recta vertical cortada por los semiespacios), o null. */
    public int[] column(int x, int z) {
        long X = 2L * (x - ox), Z = 2L * (z - oz);
        long lo = Long.MIN_VALUE, hi = Long.MAX_VALUE;
        for (long[] pl : planes) {
            long rhs = pl[3] - pl[0] * X - pl[2] * Z;
            if (pl[1] > 0) hi = Math.min(hi, Math.floorDiv(rhs, 2 * pl[1]));
            else if (pl[1] < 0) lo = Math.max(lo, Math.ceilDiv(rhs, 2 * pl[1]));
            else if (rhs < 0) return null;
        }
        if (hi < lo) return null;
        return new int[]{(int) (oy + lo), (int) (oy + hi)};
    }

    /** Las aristas en coordenadas del mundo (para partículas): {x1, y1, z1, x2, y2, z2}, ya con el ½ del centro. */
    public List<double[]> edges() {
        List<double[]> out = new ArrayList<>();
        for (long[] e : edges) {
            out.add(new double[]{ox + e[0] / 2.0 + 0.5, oy + e[1] / 2.0 + 0.5, oz + e[2] / 2.0 + 0.5,
                    ox + e[3] / 2.0 + 0.5, oy + e[4] / 2.0 + 0.5, oz + e[5] / 2.0 + 0.5});
        }
        return out;
    }
}
