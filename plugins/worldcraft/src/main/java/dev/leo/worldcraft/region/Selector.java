package dev.leo.worldcraft.region;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Spec 030 (plan §1.3): la forma que se está seleccionando y sus puntos. primary es el clic izquierdo (pos1, hpos1) y
 * secondary el derecho (pos2, hpos2), como en WorldEdit: el izquierdo empieza (primer punto o centro), el derecho agrega
 * un punto o agranda. Java puro: la sesión lo guarda y los comandos lo usan.
 */
public final class Selector {
    public enum Kind {
        CUBOID, EXTEND, POLY, ELLIPSOID, SPHERE, CYL, CONVEX, MAGIC;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Los nombres de /wc sel (los de WorldEdit y los de FAWE). */
    public static final Map<String, Kind> NAMES = Map.ofEntries(
            Map.entry("cuboid", Kind.CUBOID), Map.entry("box", Kind.CUBOID), Map.entry("extend", Kind.EXTEND),
            Map.entry("poly", Kind.POLY), Map.entry("ellipsoid", Kind.ELLIPSOID), Map.entry("sphere", Kind.SPHERE),
            Map.entry("cyl", Kind.CYL), Map.entry("convex", Kind.CONVEX), Map.entry("hull", Kind.CONVEX),
            Map.entry("polyhedral", Kind.CONVEX), Map.entry("magic", Kind.MAGIC), Map.entry("fuzzy", Kind.MAGIC));

    private Kind kind = Kind.CUBOID;
    /** cuboid y extend: las esquinas. */
    private int[] p1, p2;
    /** poly y convex: los vértices ({x, y, z}); ellipsoid, sphere y cyl: el centro en el primero. */
    private final List<int[]> points = new ArrayList<>();
    private int rx, ry, rz, yLo, yHi;
    /** magic: lo seleccionado y el último clic. */
    private PointSet magic;
    /** La última región armada (las tablas de columnas se calculan una vez); null tras cualquier cambio. */
    private Region cached;

    public Kind kind() {
        return kind;
    }

    /** Cambia la forma y borra los puntos (H1 CA1). */
    public void kind(Kind k) {
        cached = null;
        kind = k;
        clear();
    }

    public void clear() {
        cached = null;
        p1 = null;
        p2 = null;
        points.clear();
        rx = ry = rz = 0;
        magic = null;
    }

    public boolean empty() {
        return p1 == null && p2 == null && points.isEmpty() && magic == null;
    }

    // ------------------------------------------------------------------ clics

    /**
     * El clic izquierdo; devuelve qué quedó («Corner 1», «Center»…). La mágica no pasa por aquí (necesita leer el mundo):
     * ver {@link #magic(PointSet, boolean)}.
     */
    public String primary(int x, int y, int z) {
        cached = null;
        switch (kind) {
            case CUBOID -> {
                p1 = new int[]{x, y, z};
                return "Corner 1";
            }
            case EXTEND -> {
                p1 = new int[]{x, y, z};
                p2 = new int[]{x, y, z};
                return "Start";
            }
            case POLY, CONVEX -> {
                points.clear();
                points.add(new int[]{x, y, z});
                yLo = yHi = y;
                return "Point 1";
            }
            case ELLIPSOID, SPHERE, CYL -> {
                points.clear();
                points.add(new int[]{x, y, z});
                rx = ry = rz = 0;
                yLo = yHi = y;
                return "Center";
            }
            default -> throw new IllegalStateException("magic");
        }
    }

    /** El clic derecho; maxPoints es el tope de vértices de poly y convex (H2 CA9). */
    public String secondary(int x, int y, int z, int maxPoints) {
        cached = null;
        switch (kind) {
            case CUBOID -> {
                p2 = new int[]{x, y, z};
                return "Corner 2";
            }
            case EXTEND -> {
                if (p1 == null) return primary(x, y, z);
                Box b = Box.of(p1[0], p1[1], p1[2], p2[0], p2[1], p2[2]).union(new Box(x, y, z, x, y, z));
                p1 = new int[]{b.minX(), b.minY(), b.minZ()};
                p2 = new int[]{b.maxX(), b.maxY(), b.maxZ()};
                return "Extended";
            }
            case POLY, CONVEX -> {
                if (points.isEmpty()) return primary(x, y, z);
                for (int[] p : points) {
                    // un punto que ya está (en poly, el mismo x, z): no suma un vértice, solo el alto
                    if (p[0] == x && p[2] == z && (kind == Kind.POLY || p[1] == y)) {
                        yLo = Math.min(yLo, y);
                        yHi = Math.max(yHi, y);
                        return "Point " + points.size();
                    }
                }
                if (points.size() >= maxPoints) {
                    throw new IllegalArgumentException("A " + kind.id() + " selection has at most " + maxPoints + " points");
                }
                points.add(new int[]{x, y, z});
                yLo = Math.min(yLo, y);
                yHi = Math.max(yHi, y);
                return "Point " + points.size();
            }
            case ELLIPSOID -> {
                if (points.isEmpty()) return primary(x, y, z);
                int[] c = points.get(0);
                rx = Math.max(rx, Math.abs(x - c[0]));
                ry = Math.max(ry, Math.abs(y - c[1]));
                rz = Math.max(rz, Math.abs(z - c[2]));
                return "Radii " + rx + ", " + ry + ", " + rz;
            }
            case SPHERE -> {
                if (points.isEmpty()) return primary(x, y, z);
                int[] c = points.get(0);
                double dist = Math.sqrt(Math.pow(x - c[0], 2) + Math.pow(y - c[1], 2) + Math.pow(z - c[2], 2));
                rx = ry = rz = (int) Math.ceil(dist - 1e-9);
                return "Radius " + rx;
            }
            case CYL -> {
                if (points.isEmpty()) return primary(x, y, z);
                int[] c = points.get(0);
                rx = Math.max(rx, Math.abs(x - c[0]));
                rz = Math.max(rz, Math.abs(z - c[2]));
                yLo = Math.min(yLo, y);
                yHi = Math.max(yHi, y);
                return "Radii " + rx + ", " + rz;
            }
            default -> throw new IllegalStateException("magic");
        }
    }

    /** La mágica (H2 CA8): lo que inundó el comando; add = clic derecho (se suma a lo que había). */
    public void magic(PointSet set, boolean add) {
        cached = null;
        kind = Kind.MAGIC;
        magic = add && magic != null ? magic.union(set) : set;
    }

    public PointSet magicPoints() {
        return magic;
    }

    // ------------------------------------------------------------------ la región

    /** La forma seleccionada, o null si falta un punto. */
    public Region region() {
        if (cached == null) cached = build();
        return cached;
    }

    private Region build() {
        return switch (kind) {
            case CUBOID, EXTEND -> p1 == null || p2 == null ? null : Box.of(p1[0], p1[1], p1[2], p2[0], p2[1], p2[2]);
            case POLY -> points.isEmpty() ? null : new Polygon(xz(), yLo, yHi);
            case CONVEX -> points.isEmpty() ? null : new Convex(points);
            case ELLIPSOID, SPHERE -> {
                if (points.isEmpty()) yield null;
                int[] c = points.get(0);
                yield new Ellipsoid(c[0], c[1], c[2], rx, ry, rz, kind == Kind.SPHERE);
            }
            case CYL -> {
                if (points.isEmpty()) yield null;
                int[] c = points.get(0);
                yield new Cylinder(c[0], c[2], rx, rz, yLo, yHi);
            }
            case MAGIC -> magic;
        };
    }

    private List<int[]> xz() {
        List<int[]> out = new ArrayList<>();
        for (int[] p : points) out.add(new int[]{p[0], p[2]});
        return out;
    }

    /** La primera esquina, el primer vértice o el centro (line, y el origen de la consola). */
    public int[] corner1() {
        if (kind == Kind.CUBOID || kind == Kind.EXTEND) return p1 == null ? null : p1.clone();
        if (kind == Kind.MAGIC) return magic == null ? null : new int[]{magic.bounds().minX(), magic.bounds().minY(), magic.bounds().minZ()};
        return points.isEmpty() ? null : points.get(0).clone();
    }

    /** La segunda esquina (solo cuboid y extend). */
    public int[] corner2() {
        return kind == Kind.CUBOID || kind == Kind.EXTEND ? (p2 == null ? null : p2.clone()) : null;
    }

    /** Una caja (chunk, trim, paste --select): extend sigue siendo extend; lo demás vuelve a cuboid. */
    public void select(Box b) {
        cached = null;
        if (kind != Kind.EXTEND) kind = Kind.CUBOID;
        clear();
        p1 = new int[]{b.minX(), b.minY(), b.minZ()};
        p2 = new int[]{b.maxX(), b.maxY(), b.maxZ()};
    }

    // ------------------------------------------------------------------ cambiar la forma (H4 CA5)

    /** Mueve cualquier forma. */
    public void shift(int dx, int dy, int dz) {
        cached = null;
        if (p1 != null) p1 = new int[]{p1[0] + dx, p1[1] + dy, p1[2] + dz};
        if (p2 != null) p2 = new int[]{p2[0] + dx, p2[1] + dy, p2[2] + dz};
        points.replaceAll(p -> new int[]{p[0] + dx, p[1] + dy, p[2] + dz});
        yLo += dy;
        yHi += dy;
        if (magic != null) {
            long[] k = magic.packed();
            for (int i = 0; i < k.length; i++) k[i] = PointSet.pack(PointSet.x(k[i]) + dx, PointSet.y(k[i]) + dy, PointSet.z(k[i]) + dz);
            magic = new PointSet(k);
        }
    }

    /** ¿Se agranda como una caja (expand, contract, outset de lado)? */
    /** Spec 040 H1 CA1: los puntos de poly y convex en el orden en que se marcaron (vacío en las otras formas). */
    public List<int[]> points() {
        if (kind != Kind.POLY && kind != Kind.CONVEX) return List.of();
        List<int[]> out = new ArrayList<>();
        for (int[] p : points) out.add(p.clone());
        return out;
    }

    public boolean boxLike() {
        return kind == Kind.CUBOID || kind == Kind.EXTEND;
    }

    /** poly y cyl: el alto (expand/contract up o down, expand vert). down y up pueden ser negativos (achicar). */
    public void height(int down, int up) {
        cached = null;
        if (kind != Kind.POLY && kind != Kind.CYL) throw new IllegalStateException(kind.id());
        int a = yLo - down, b = yHi + up;
        if (a > b) throw new IllegalArgumentException("the selection would be empty");
        yLo = a;
        yHi = b;
    }

    /** poly y cyl: el alto exacto (expand vert). */
    public void heightTo(int min, int max) {
        cached = null;
        if (kind != Kind.POLY && kind != Kind.CYL) throw new IllegalStateException(kind.id());
        yLo = min;
        yHi = max;
    }

    /**
     * outset/inset en las formas redondas: los radios (horizontal: x y z; vertical: y o el alto del cilindro); poly solo
     * el alto. n negativo achica.
     */
    public void outset(int n, boolean horizontal, boolean vertical) {
        cached = null;
        boolean h = horizontal || !vertical, v = vertical || !horizontal;
        switch (kind) {
            case ELLIPSOID, SPHERE -> {
                int a = h ? rx + n : rx, b = v ? ry + n : ry, c = h ? rz + n : rz;
                if (a < 0 || b < 0 || c < 0) throw new IllegalArgumentException("the selection would be empty");
                if (kind == Kind.SPHERE && !(h && v)) kind = Kind.ELLIPSOID;
                rx = a;
                ry = b;
                rz = c;
            }
            case CYL -> {
                int a = h ? rx + n : rx, c = h ? rz + n : rz, lo = v ? yLo - n : yLo, hi = v ? yHi + n : yHi;
                if (a < 0 || c < 0 || lo > hi) throw new IllegalArgumentException("the selection would be empty");
                rx = a;
                rz = c;
                yLo = lo;
                yHi = hi;
            }
            case POLY -> {
                if (horizontal || !vertical) throw new IllegalStateException(kind.id());
                height(n, n);
            }
            default -> throw new IllegalStateException(kind.id());
        }
    }

    // ------------------------------------------------------------------ texto

    /** Los puntos de la forma, para /wc sel (H1 CA2). */
    public String describe() {
        return switch (kind) {
            case CUBOID, EXTEND -> p1 == null || p2 == null ? "" : "from " + xyz(Box.of(p1[0], p1[1], p1[2], p2[0], p2[1], p2[2]), true)
                    + " to " + xyz(Box.of(p1[0], p1[1], p1[2], p2[0], p2[1], p2[2]), false);
            case POLY -> {
                StringBuilder sb = new StringBuilder(points.size() + (points.size() == 1 ? " point: " : " points: "));
                for (int i = 0; i < points.size(); i++) sb.append(i == 0 ? "" : ", ").append(points.get(i)[0]).append(' ').append(points.get(i)[2]);
                yield sb.append("; y ").append(yLo).append(" to ").append(yHi).toString();
            }
            case CONVEX -> points.size() + (points.size() == 1 ? " point" : " points");
            case ELLIPSOID -> points.isEmpty() ? "" : "center " + xyz(points.get(0)) + ", radii " + rx + ", " + ry + ", " + rz;
            case SPHERE -> points.isEmpty() ? "" : "center " + xyz(points.get(0)) + ", radius " + rx;
            case CYL -> points.isEmpty() ? "" : "center " + points.get(0)[0] + " " + points.get(0)[2] + ", radii " + rx + ", " + rz
                    + "; y " + yLo + " to " + yHi;
            case MAGIC -> magic == null ? "" : magic.volume() + " connected blocks";
        };
    }

    private static String xyz(int[] p) {
        return p[0] + " " + p[1] + " " + p[2];
    }

    private static String xyz(Box b, boolean min) {
        return min ? b.minX() + " " + b.minY() + " " + b.minZ() : b.maxX() + " " + b.maxY() + " " + b.maxZ();
    }
}
