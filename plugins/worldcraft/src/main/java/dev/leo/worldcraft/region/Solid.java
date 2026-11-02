package dev.leo.worldcraft.region;

/**
 * Una forma como intervalos de y por columna (plan 028 §1.1): esfera, cilindro, pirámide y cono, llenos o huecos. Cada
 * columna (x, z) del rectángulo ocupa [lo, hi] (vacía si hi &lt; lo) menos su interior [innerLo, innerHi] (vacío si no es
 * hueca). Inmutable salvo al armarla; Java puro.
 */
public final class Solid {
    public final int x0, z0, width, depth;
    final int[] lo, hi, innerLo, innerHi;

    private Solid(int x0, int z0, int width, int depth) {
        long n = (long) width * depth;
        if (n > 50_000_000) throw new IllegalArgumentException("too big");
        this.x0 = x0;
        this.z0 = z0;
        this.width = width;
        this.depth = depth;
        lo = new int[(int) n];
        hi = new int[(int) n];
        innerLo = new int[(int) n];
        innerHi = new int[(int) n];
        java.util.Arrays.fill(lo, 1);
        java.util.Arrays.fill(innerLo, 1);
    }

    private static Solid around(int cx, int cz, int rx, int rz) {
        return new Solid(cx - rx, cz - rz, 2 * rx + 1, 2 * rz + 1);
    }

    // ------------------------------------------------------------------ formas

    /** Elipsoide de radios (a, b, c) con centro en (cx, cy, cz); radio 0 en un eje = un bloque de grueso. */
    public static Solid sphere(int cx, int cy, int cz, int a, int b, int c) {
        Solid s = around(cx, cz, a, c);
        double ea = a + 0.5, eb = b + 0.5, ec = c + 0.5;
        for (int dz = -c; dz <= c; dz++) {
            for (int dx = -a; dx <= a; dx++) {
                double q = 1 - (dx / ea) * (dx / ea) - (dz / ec) * (dz / ec);
                if (q < 0) continue;
                int h = (int) Math.floor(eb * Math.sqrt(q));
                h = Math.min(h, b);
                s.set(cx + dx, cz + dz, cy - h, cy + h);
            }
        }
        return s;
    }

    /** Cilindro de radios (a, c) y alto h desde y (h &lt; 0: hacia abajo); h = 0 se trata como 1. */
    public static Solid cylinder(int cx, int y, int cz, int a, int c, int h) {
        Solid s = around(cx, cz, a, c);
        int[] ys = span(y, h);
        double ea = a + 0.5, ec = c + 0.5;
        for (int dz = -c; dz <= c; dz++) {
            for (int dx = -a; dx <= a; dx++) {
                if ((dx / ea) * (dx / ea) + (dz / ec) * (dz / ec) > 1) continue;
                s.set(cx + dx, cz + dz, ys[0], ys[1]);
            }
        }
        return s;
    }

    /** Pirámide de base cuadrada (lado 2·size − 1) y alto size desde y. */
    public static Solid pyramid(int cx, int y, int cz, int size) {
        int r = Math.max(0, size - 1);
        Solid s = around(cx, cz, r, r);
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int d = Math.max(Math.abs(dx), Math.abs(dz));
                s.set(cx + dx, cz + dz, y, y + (r - d));
            }
        }
        return s;
    }

    /** Cono de radio r y alto h desde y (h &lt; 0: con la punta hacia abajo). */
    public static Solid cone(int cx, int y, int cz, int r, int h) {
        Solid s = around(cx, cz, r, r);
        int len = Math.max(1, Math.abs(h));
        double er = r + 0.5;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                double d = Math.hypot(dx, dz);
                if (d > er) continue;
                int tall = Math.max(1, (int) Math.floor(len * (1 - d / er) + 1e-9));
                tall = Math.min(tall, len);
                if (h >= 0) s.set(cx + dx, cz + dz, y, y + tall - 1);
                else s.set(cx + dx, cz + dz, y - tall + 1, y);
            }
        }
        return s;
    }

    /** [desde, hasta] para un alto que puede ser negativo. */
    static int[] span(int y, int h) {
        int len = Math.max(1, Math.abs(h));
        return h >= 0 ? new int[]{y, y + len - 1} : new int[]{y - len + 1, y};
    }

    /**
     * La cáscara de grosor t (plan 028 §1.1): el interior es lo que queda tras erosionar t veces con los 6 vecinos.
     * sides = solo de costado (el cilindro hueco de WorldEdit no tiene tapas).
     */
    public Solid hollow(int t, boolean sides) {
        int n = lo.length;
        int[] a = lo.clone(), b = hi.clone();
        for (int k = 0; k < Math.max(1, t); k++) {
            int[] na = new int[n], nb = new int[n];
            for (int i = 0; i < n; i++) {
                int l = a[i], h = b[i];
                if (h < l) {
                    na[i] = 1;
                    nb[i] = 0;
                    continue;
                }
                if (!sides) {
                    l++;
                    h--;
                }
                int x = i % width, z = i / width;
                int[][] nbrs = {{x + 1, z}, {x - 1, z}, {x, z + 1}, {x, z - 1}};
                for (int[] c : nbrs) {
                    if (c[0] < 0 || c[1] < 0 || c[0] >= width || c[1] >= depth) {
                        h = l - 1;
                        break;
                    }
                    int j = c[0] + c[1] * width;
                    l = Math.max(l, a[j]);
                    h = Math.min(h, b[j]);
                }
                na[i] = l;
                nb[i] = h;
            }
            a = na;
            b = nb;
        }
        Solid out = new Solid(x0, z0, width, depth);
        System.arraycopy(lo, 0, out.lo, 0, n);
        System.arraycopy(hi, 0, out.hi, 0, n);
        System.arraycopy(a, 0, out.innerLo, 0, n);
        System.arraycopy(b, 0, out.innerHi, 0, n);
        return out;
    }

    private void set(int x, int z, int a, int b) {
        int i = (x - x0) + (z - z0) * width;
        lo[i] = a;
        hi[i] = b;
    }

    // ------------------------------------------------------------------ consultas

    public int size() {
        return lo.length;
    }

    public int x(int i) {
        return x0 + i % width;
    }

    public int z(int i) {
        return z0 + i / width;
    }

    public int index(int x, int z) {
        return (x - x0) + (z - z0) * width;
    }

    public int lo(int i) {
        return lo[i];
    }

    public int hi(int i) {
        return hi[i];
    }

    /** ¿Está y de la columna i en la forma (cáscara si es hueca)? */
    public boolean contains(int i, int y) {
        if (y < lo[i] || y > hi[i]) return false;
        return innerHi[i] < innerLo[i] || y < innerLo[i] || y > innerHi[i];
    }

    public boolean contains(int x, int y, int z) {
        int rx = x - x0, rz = z - z0;
        if (rx < 0 || rz < 0 || rx >= width || rz >= depth) return false;
        return contains(rx + rz * width, y);
    }

    /** La siguiente y de la columna i a partir de y (hacia arriba) que está en la forma, o hi + 1 si no hay más. */
    public int nextY(int i, int y) {
        if (y < lo[i]) y = lo[i];
        if (innerHi[i] >= innerLo[i] && y >= innerLo[i] && y <= innerHi[i]) y = innerHi[i] + 1;
        return y;
    }

    /** Bloques que ocupa. */
    public long volume() {
        long v = 0;
        for (int i = 0; i < lo.length; i++) {
            if (hi[i] < lo[i]) continue;
            v += hi[i] - lo[i] + 1;
            int a = Math.max(lo[i], innerLo[i]), b = Math.min(hi[i], innerHi[i]);
            if (b >= a) v -= b - a + 1;
        }
        return v;
    }

    /** La caja que la contiene (null si está vacía). */
    public Box bounds() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < lo.length; i++) {
            if (hi[i] < lo[i]) continue;
            minX = Math.min(minX, x(i));
            maxX = Math.max(maxX, x(i));
            minZ = Math.min(minZ, z(i));
            maxZ = Math.max(maxZ, z(i));
            minY = Math.min(minY, lo[i]);
            maxY = Math.max(maxY, hi[i]);
        }
        return minX > maxX ? null : new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // ------------------------------------------------------------------ estimaciones (antes de calcular)

    /** Volumen aproximado de una forma llena, para rechazarla antes de reservar memoria. */
    public static double estimate(String kind, int a, int b, int c, int h) {
        double ea = a + 0.5, eb = b + 0.5, ec = c + 0.5;
        int len = Math.max(1, Math.abs(h));
        return switch (kind) {
            case "sphere" -> 4.0 / 3 * Math.PI * ea * eb * ec;
            case "cyl" -> Math.PI * ea * ec * len;
            case "pyramid" -> {
                double s = Math.max(1, a);
                yield (2 * s - 1) * (2 * s - 1) * s / 3 + s * s;
            }
            case "cone" -> Math.PI * ea * ea * len / 3;
            default -> throw new IllegalArgumentException(kind);
        };
    }
}
