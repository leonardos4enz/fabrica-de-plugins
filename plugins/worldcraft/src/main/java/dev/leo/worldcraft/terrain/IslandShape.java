package dev.leo.worldcraft.terrain;

import dev.leo.worldcraft.region.Box;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * La isla flotante (H2, investigación §2.2): dos caras por columna. Arriba, casi plana con relieve suave y un borde que
 * baja un poco; abajo, un cono invertido rugoso, más hondo en el centro; el contorno irregular. Debajo cuelgan
 * estalactitas y, en el borde delgado, raíces; arriba, árboles pequeños. Java puro: se calcula fuera del hilo principal.
 *
 * @param radius radio en bloques
 * @param depth  hondura del cono respecto al radio
 * @param relief relieve de la cima respecto al radio
 * @param spikes cuántas estalactitas (0..1)
 * @param roots  cuántas raíces en el borde (0..1)
 * @param trees  árboles
 */
public record IslandShape(int radius, double depth, double relief, double spikes, double roots, int trees, long seed) {
    public static final byte LOG = 1, LEAVES = 2;

    /**
     * La isla calculada: el rectángulo, la cima y la base de cada columna (NONE = vacía), la raíz (NONE = sin raíz), los
     * bloques de los árboles (posición → LOG o LEAVES) y la pendiente de la cima.
     */
    public record Plan(Box rect, int[] top, int[] bottom, int[] root, Map<Long, Byte> trees, float[] slope, int minY, int maxY) {
        public int index(int x, int z) {
            return (x - rect.minX()) + (z - rect.minZ()) * rect.width();
        }
    }

    public static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }

    /** La isla con la cima centrada en (cx, cy, cz). */
    public Plan plan(int cx, int cy, int cz, int worldMin, int worldMax) {
        int half = (int) Math.ceil(radius * 1.3) + 3;
        Box rect = new Box(cx - half, 0, cz - half, cx + half, 0, cz + half);
        int w = rect.width(), d = rect.depth(), n = w * d;
        int[] top = new int[n], bottom = new int[n], root = new int[n];
        java.util.Arrays.fill(top, HeightField.NONE);
        java.util.Arrays.fill(bottom, HeightField.NONE);
        java.util.Arrays.fill(root, HeightField.NONE);
        Noise noise = new Noise(seed);
        Noise rid = noise.derive(3), relief2 = noise.derive(4), outline = noise.derive(5);
        double[] dd = new double[n];
        for (int i = 0; i < n; i++) {
            int x = rect.minX() + i % w, z = rect.minZ() + i / w;
            double u = (x - cx) / (double) radius, v = (z - cz) / (double) radius;
            double r = Math.sqrt(u * u + v * v) / (1 + 0.28 * outline.fbm(u * 1.3 + 5, v * 1.3 - 2, 3));
            dd[i] = r;
            if (r >= 1) continue;
            double rise = radius * relief * (0.6 * (1 - r * r) + 0.5 * relief2.fbm(u * 2.4, v * 2.4, 3));
            int t = cy + (int) Math.round(rise - 2 * MountainShape.smooth((r - 0.82) / 0.18));
            double cone = depth * radius * (1 - Math.pow(r, 1.7)) * (0.8 + 0.5 * rid.ridged(u * 3, v * 3, 4));
            int thick = Math.max(r > 0.9 ? 1 : 2, (int) Math.round(cone));
            top[i] = Math.min(t, worldMax);
            bottom[i] = Math.max(t - thick + 1, worldMin);
        }
        Random rnd = new Random(seed ^ 0x2545F4914F6CDD1DL);
        // Estalactitas: columnas que cuelgan más, afiladas hacia la punta.
        int count = (int) Math.round(spikes * (3 + radius * radius / 40.0));
        for (int k = 0; k < count; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * 0.7;
            double sx = cx + Math.cos(a) * rr * radius, sz = cz + Math.sin(a) * rr * radius;
            double rs = 1 + rnd.nextDouble() * 1.6, len = (0.25 + 0.45 * rnd.nextDouble()) * depth * radius;
            for (int x = (int) Math.floor(sx - rs); x <= (int) Math.ceil(sx + rs); x++) {
                for (int z = (int) Math.floor(sz - rs); z <= (int) Math.ceil(sz + rs); z++) {
                    int i = (x - rect.minX()) + (z - rect.minZ()) * w;
                    if (x < rect.minX() || z < rect.minZ() || x > rect.maxX() || z > rect.maxZ() || top[i] == HeightField.NONE) continue;
                    double dist = Math.hypot(x + 0.5 - sx, z + 0.5 - sz);
                    if (dist >= rs) continue;
                    int extra = (int) Math.round(len * Math.pow(1 - dist / rs, 1.5));
                    bottom[i] = Math.max(worldMin, Math.min(bottom[i], bottom[i] - extra));
                }
            }
        }
        // Raíces: bajo las columnas delgadas del borde (donde la base es tierra).
        for (int i = 0; i < n; i++) {
            if (top[i] == HeightField.NONE || top[i] - bottom[i] > 3 || dd[i] < 0.55) continue;
            if (Noise.hash01(rect.minX() + i % w, rect.minZ() + i / w, seed + 91) < roots * 0.6 && bottom[i] - 1 >= worldMin) {
                root[i] = bottom[i] - 1;
            }
        }
        float[] slope = HeightField.slopes(top, w, d);
        Map<Long, Byte> treeBlocks = new HashMap<>();
        List<int[]> placed = new ArrayList<>();
        for (int tries = 0; placed.size() < trees && tries < trees * 40; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * 0.55 * radius;
            int tx = cx + (int) Math.round(Math.cos(a) * rr), tz = cz + (int) Math.round(Math.sin(a) * rr);
            int i = (tx - rect.minX()) + (tz - rect.minZ()) * w;
            if (top[i] == HeightField.NONE || slope[i] > 35) continue;
            boolean near = false;
            for (int[] p : placed) if (Math.abs(p[0] - tx) + Math.abs(p[1] - tz) < 5) near = true;
            if (near) continue;
            placed.add(new int[]{tx, tz});
            tree(treeBlocks, tx, top[i] + 1, tz, 4 + rnd.nextInt(3), rnd, worldMax);
        }
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            if (top[i] == HeightField.NONE) continue;
            minY = Math.min(minY, Math.min(bottom[i], root[i] == HeightField.NONE ? bottom[i] : root[i]));
            maxY = Math.max(maxY, top[i]);
        }
        return new Plan(rect, top, bottom, root, treeBlocks, slope, minY, maxY);
    }

    /** Un árbol pequeño: tronco y una copa redondeada (las hojas no pisan el tronco). */
    private static void tree(Map<Long, Byte> out, int x, int y, int z, int height, Random rnd, int worldMax) {
        int crown = y + height - 1;
        if (crown + 1 > worldMax) return;
        for (int dy = -2; dy <= 1; dy++) {
            int r = dy <= -1 ? 2 : 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                    if (corner && (dy == 1 || rnd.nextBoolean())) continue;
                    out.putIfAbsent(key(x + dx, crown + dy, z + dz), LEAVES);
                }
            }
        }
        for (int yy = y; yy <= crown; yy++) out.put(key(x, yy, z), LOG);
    }
}
