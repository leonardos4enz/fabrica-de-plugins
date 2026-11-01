package dev.leo.skinstatues.model;

import dev.leo.skinstatues.skin.Skin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Voxelización inversa (plan 021 §1.1): para cada bloque dentro de la caja envolvente de cada segmento, lleva su centro
 * de vuelta al espacio de la parte y decide si es cáscara de la primera capa, segunda capa o nada, y qué color le toca.
 * Java puro: corre fuera del hilo principal.
 */
public final class Voxelizer {
    /** Prioridades cuando dos segmentos caen en el mismo bloque: la primera capa tapa a la capa y esta a la segunda. */
    private static final int BASE = 3, CAPE = 2, OVERLAY = 1;

    /**
     * @param height       alto pedido en bloques (32 píxeles de skin)
     * @param facingYaw    hacia dónde mira la estatua, en grados de Bukkit (0 = sur, 90 = oeste…), ya redondeado a 90
     * @param overlayDepth píxeles de la segunda capa por fuera de la primera
     * @param alpha        un píxel de la segunda capa cuenta desde esta opacidad (0–255)
     */
    public record Options(double height, double facingYaw, boolean overlay, double overlayDepth, int alpha, boolean cape) {
        public double scale() {
            return height / 32.0;
        }
    }

    private Voxelizer() {
    }

    private static final class Cell {
        int argb, prio;
        double depth;
    }

    public static VoxelGrid run(Skin skin, Pose pose, Options o) {
        double s = o.scale();
        // H3 CA3: con poco espacio la segunda capa no tiene bloques propios y pinta la primera. Se decide con la escala
        // pedida, antes de descontar el sombrero (F-03: a 32 de alto el descuento la dejaba justo bajo el umbral).
        boolean extrude = o.overlay() && o.overlayDepth() * s >= 0.5;
        // El sombrero sobresale de la cabeza: se descuenta para que el alto total sea el pedido (H1 CA1).
        if (extrude && skin.hasHat(o.alpha())) {
            double top = Math.max(o.overlayDepth() * s, 1);
            if (o.height() - top >= 8) s = (o.height() - top) / 32.0;
        }
        Mat3 facing = Mat3.rotY(-o.facingYaw());
        Mat3 unfacing = facing.transpose();
        boolean paint = o.overlay() && !extrude;
        double thick = extrude ? Math.max(o.overlayDepth(), 1 / s) : 0;
        Map<Long, Cell> cells = new HashMap<>();

        for (Segment seg : PlayerModel.segments(skin.slim(), pose, o.cape() && skin.hasCape())) {
            double[] min = seg.min.clone(), max = seg.max.clone();
            // Lo que mide menos de un bloque (la capa a escala chica) se ensancha a un bloque para no desaparecer.
            for (int a = 0; a < 3; a++) {
                double need = 1 / s;
                if (max[a] - min[a] < need) {
                    double c = (min[a] + max[a]) / 2;
                    min[a] = c - need / 2;
                    max[a] = c + need / 2;
                }
            }
            double t = seg.hasOverlay() ? thick : 0;
            double[] lo = new double[3], hi = new double[3];
            for (int a = 0; a < 3; a++) {
                lo[a] = min[a] - (seg.inflate[2 * a] ? t : 0);
                hi[a] = max[a] + (seg.inflate[2 * a + 1] ? t : 0);
            }
            Transform inv = seg.toModel.inverse();
            // Cáscara de un bloque: un camino de 6 vecinos avanza a lo sumo un bloque por paso en la dirección de la
            // normal, así que no puede saltarse una franja de un bloque de grueso, aunque el segmento esté girado.
            double shell = 1 / s;
            // La capa de un solo bloque de grueso se ve desde atrás: su color sale siempre de la cara de afuera.
            boolean outerOnly = seg.cape && (max[2] - min[2]) * s < 2;

            int[] box = bounds(seg.toModel, facing, s, lo, hi);
            for (int i = box[0]; i <= box[3]; i++) {
                for (int j = box[1]; j <= box[4]; j++) {
                    for (int k = box[2]; k <= box[5]; k++) {
                        Vec3 c = new Vec3(i + 0.5, j + 0.5, k + 0.5);
                        Vec3 p = inv.apply(unfacing.mul(c).scale(1 / s));
                        if (!within(p, lo, hi) || !seg.onMiterSide(p)) continue;
                        if (within(p, min, max)) {
                            int face = -1;
                            double depth = Double.MAX_VALUE;
                            for (int q = 0; q < 6; q++) {
                                // En la capa un empate lo gana la cara de afuera (+z local).
                                int f = seg.cape ? 5 - q : q;
                                if (!seg.real[f]) continue;
                                double d = inside(f, p, min, max);
                                if (d < depth) {
                                    depth = d;
                                    face = f;
                                }
                            }
                            // Por dentro: hueca (H1 CA4). Inclusiva: con una escala no entera un centro cae justo en el plano de una cara (queda
                            // fuera por el intervalo semiabierto) y el siguiente a un bloque exacto; sin el igual, la cara se abría.
                            if (depth > shell + 1e-9) continue;
                            if (outerOnly && face == Segment.NEG_Z) face = Segment.POS_Z;
                            int color = base(skin, seg, face, p, s, paint, o.alpha());
                            if (color != 0) put(cells, i, j, k, color, seg.cape ? CAPE : BASE, depth);
                        } else if (t > 0) {
                            overlay(cells, skin, seg, p, i, j, k, s, o.alpha(), min, max);
                        }
                    }
                }
            }
        }
        return grid(cells, s);
    }

    /** Segunda capa en relieve: el píxel de la cara de la que más sale el punto; si es transparente, la siguiente. */
    private static void overlay(Map<Long, Cell> cells, Skin skin, Segment seg, Vec3 p, int i, int j, int k, double s,
                                int alpha, double[] min, double[] max) {
        int[] order = new int[6];
        double[] out = new double[6];
        int n = 0;
        for (int f = 0; f < 6; f++) {
            double d = -inside(f, p, min, max);
            if (seg.inflate[f] && d > 0) {
                order[n] = f;
                out[n++] = d;
            }
        }
        // Orden por cuánto sale, de mayor a menor (como mucho tres caras).
        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                if (out[b] > out[a]) {
                    double td = out[a]; out[a] = out[b]; out[b] = td;
                    int tf = order[a]; order[a] = order[b]; order[b] = tf;
                }
            }
        }
        for (int a = 0; a < n; a++) {
            int color = sample(skin, seg, order[a], p, s, seg.overU, seg.overV, alpha);
            if (color != 0) {
                put(cells, i, j, k, color, OVERLAY, out[a]);
                return;
            }
        }
    }

    /** Color de la primera capa (o de la capa); con la segunda capa pintada encima si no tiene bloques propios. */
    private static int base(Skin skin, Segment seg, int face, Vec3 p, double s, boolean paint, int alpha) {
        double[] r = seg.uv(face, p, seg.baseU, seg.baseV);
        int dx = seg.overU - seg.baseU, dy = seg.overV - seg.baseV;
        boolean withOverlay = paint && seg.hasOverlay();
        long sr = 0, sg = 0, sb = 0;
        int count = 0;
        int steps = s >= 1 ? 1 : (int) Math.ceil(1 / s);
        double span = s >= 1 ? 0 : 1 / s;
        for (int a = 0; a < steps; a++) {
            for (int b = 0; b < steps; b++) {
                int x = texel(r[0], r[2] + offset(a, steps, span), r[4]);
                int y = texel(r[1], r[3] + offset(b, steps, span), r[5]);
                int c = seg.cape ? skin.capePixel(x, y) : skin.pixel(x, y);
                if (withOverlay) {
                    int over = skin.pixel(x + dx, y + dy);
                    if ((over >>> 24) >= alpha) c = over;
                }
                if ((c >>> 24) < 128) continue; // solo pasa en la capa: un hueco transparente no pone bloque
                sr += (c >> 16) & 0xFF;
                sg += (c >> 8) & 0xFF;
                sb += c & 0xFF;
                count++;
            }
        }
        if (count == 0 || count * 2 < steps * steps && seg.cape) return 0;
        return 0xFF000000 | (int) (sr / count) << 16 | (int) (sg / count) << 8 | (int) (sb / count);
    }

    /** Píxel de la segunda capa (con la huella del bloque si es más grande que un píxel): 0 si es transparente. */
    private static int sample(Skin skin, Segment seg, int face, Vec3 p, double s, int u0, int v0, int alpha) {
        double[] r = seg.uv(face, p, u0, v0);
        int steps = s >= 1 ? 1 : (int) Math.ceil(1 / s);
        double span = s >= 1 ? 0 : 1 / s;
        long sr = 0, sg = 0, sb = 0;
        int count = 0;
        for (int a = 0; a < steps; a++) {
            for (int b = 0; b < steps; b++) {
                int c = skin.pixel(texel(r[0], r[2] + offset(a, steps, span), r[4]),
                        texel(r[1], r[3] + offset(b, steps, span), r[5]));
                if ((c >>> 24) < alpha) continue;
                sr += (c >> 16) & 0xFF;
                sg += (c >> 8) & 0xFF;
                sb += c & 0xFF;
                count++;
            }
        }
        if (count == 0 || count * 2 < steps * steps) return 0;
        return 0xFF000000 | (int) (sr / count) << 16 | (int) (sg / count) << 8 | (int) (sb / count);
    }

    private static double offset(int a, int steps, double span) {
        return steps == 1 ? 0 : ((a + 0.5) / steps - 0.5) * span;
    }

    private static int texel(double origin, double u, double size) {
        int k = (int) Math.floor(u);
        if (k < 0) k = 0;
        if (k > size - 1) k = (int) size - 1;
        return (int) origin + k;
    }

    private static double inside(int face, Vec3 p, double[] min, double[] max) {
        int axis = face / 2;
        double v = p.get(axis);
        return face % 2 == 0 ? v - min[axis] : max[axis] - v;
    }

    private static boolean within(Vec3 p, double[] min, double[] max) {
        return p.x() >= min[0] && p.x() < max[0] && p.y() >= min[1] && p.y() < max[1] && p.z() >= min[2] && p.z() < max[2];
    }

    /** Caja envolvente, en bloques, del segmento ya girado y escalado: [x0, y0, z0, x1, y1, z1]. */
    private static int[] bounds(Transform t, Mat3 facing, double s, double[] lo, double[] hi) {
        double[] bmin = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] bmax = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int c = 0; c < 8; c++) {
            Vec3 corner = new Vec3((c & 1) == 0 ? lo[0] : hi[0], (c & 2) == 0 ? lo[1] : hi[1], (c & 4) == 0 ? lo[2] : hi[2]);
            Vec3 w = facing.mul(t.apply(corner)).scale(s);
            for (int a = 0; a < 3; a++) {
                bmin[a] = Math.min(bmin[a], w.get(a));
                bmax[a] = Math.max(bmax[a], w.get(a));
            }
        }
        return new int[]{(int) Math.floor(bmin[0]) - 1, (int) Math.floor(bmin[1]) - 1, (int) Math.floor(bmin[2]) - 1,
                (int) Math.ceil(bmax[0]), (int) Math.ceil(bmax[1]), (int) Math.ceil(bmax[2])};
    }

    private static void put(Map<Long, Cell> cells, int x, int y, int z, int argb, int prio, double depth) {
        Cell c = cells.computeIfAbsent(VoxelGrid.key(x, y, z), key -> new Cell());
        if (c.prio > prio || (c.prio == prio && c.depth <= depth)) return;
        c.argb = argb;
        c.prio = prio;
        c.depth = depth;
    }

    /** Pasa el mapa a arreglos y sube o baja todo para que lo más bajo quede a la altura de los pies (H4 CA6). */
    private static VoxelGrid grid(Map<Long, Cell> cells, double scale) {
        int n = cells.size();
        int[] xs = new int[n], ys = new int[n], zs = new int[n], colors = new int[n];
        int i = 0, minY = n == 0 ? 0 : Integer.MAX_VALUE;
        for (Map.Entry<Long, Cell> e : cells.entrySet()) {
            long key = e.getKey();
            xs[i] = (int) ((key >>> 42) & 0x1FFFFF) - VoxelGrid.OFF;
            ys[i] = (int) ((key >>> 21) & 0x1FFFFF) - VoxelGrid.OFF;
            zs[i] = (int) (key & 0x1FFFFF) - VoxelGrid.OFF;
            colors[i] = e.getValue().argb;
            minY = Math.min(minY, ys[i]);
            i++;
        }
        for (int k = 0; k < n; k++) ys[k] -= minY;
        return new VoxelGrid(xs, ys, zs, colors, -minY, scale);
    }

    /** Lista de segmentos (para pruebas). */
    static List<Segment> segmentsFor(Skin skin, Pose pose, boolean cape) {
        return PlayerModel.segments(skin.slim(), pose, cape && skin.hasCape());
    }
}
