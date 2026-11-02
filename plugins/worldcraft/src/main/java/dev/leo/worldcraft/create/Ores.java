package dev.leo.worldcraft.create;

import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.formula.Formula;
import dev.leo.worldcraft.formula.Shapes;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Region;

import java.util.SplittableRandom;

/**
 * Vetas de mineral como las del juego (spec 035 H2, plan §1.2): FAWE //ore y //ores. Java puro: se calcula en el hilo de
 * cálculo; qué se pone en cada punto (la máscara, el deepslate) lo decide la obra con el bloque que hay.
 */
public final class Ores {
    private Ores() {
    }

    /**
     * @param size     bloques por veta (como el juego: más o menos los que salen)
     * @param perChunk intentos por chunk de 16×16
     * @param rarity   % de que cada intento salga (100 = todos)
     */
    public record Spec(int size, int perChunk, double rarity, int minY, int maxY) {
    }

    /** Los puntos de las vetas (dentro de la región) y cuántas vetas dejaron algún punto. */
    public record Result(long[] points, int veins) {
    }

    public static Result place(Region region, Spec spec, long seed, Shapes.Work work) {
        Box b = region.bounds();
        int lo = Math.max(spec.minY, b.minY()), hi = Math.min(spec.maxY, b.maxY());
        Shapes.Longs out = new Shapes.Longs();
        if (lo > hi || spec.size <= 0 || spec.perChunk <= 0 || spec.rarity <= 0) return new Result(new long[0], 0);
        int veins = 0;
        Shapes.Longs one = new Shapes.Longs();
        for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) {
            for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) {
                if (System.nanoTime() > work.deadline) throw new Formula.TimedOut();
                // Cada chunk con su semilla: la misma semilla da las mismas vetas, se calcule en el orden que sea.
                SplittableRandom r = new SplittableRandom(seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL));
                for (int i = 0; i < spec.perChunk; i++) {
                    double roll = r.nextDouble() * 100;
                    int x = (cx << 4) + r.nextInt(16), z = (cz << 4) + r.nextInt(16), y = lo + r.nextInt(hi - lo + 1);
                    if (roll >= spec.rarity) continue;
                    one.n = 0;
                    vein(x, y, z, spec.size, r, region, one);
                    work.at(x, y, z);
                    if (one.n > 0) veins++;
                    for (int k = 0; k < one.n; k++) out.add(one.a[k]);
                }
            }
        }
        return new Result(out.toArray(), veins);
    }

    /**
     * La veta del juego (OreFeature): un segmento de largo size/8 con un ángulo al azar y esferas a lo largo, más gruesas
     * en el medio. Solo los puntos de la región.
     */
    static void vein(int cx, int cy, int cz, int size, SplittableRandom r, Region region, Shapes.Longs out) {
        double angle = r.nextDouble() * Math.PI, len = size / 8.0;
        double x0 = cx + 0.5 + Math.sin(angle) * len, x1 = cx + 0.5 - Math.sin(angle) * len;
        double z0 = cz + 0.5 + Math.cos(angle) * len, z1 = cz + 0.5 - Math.cos(angle) * len;
        double y0 = cy + r.nextInt(3) - 1 + 0.5, y1 = cy + r.nextInt(3) - 1 + 0.5;
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        int steps = Math.max(1, size);
        for (int i = 0; i < steps; i++) {
            double t = steps == 1 ? 0.5 : (double) i / (steps - 1);
            double px = x0 + (x1 - x0) * t, py = y0 + (y1 - y0) * t, pz = z0 + (z1 - z0) * t;
            double rad = ((Math.sin(Math.PI * t) + 1) * r.nextDouble() * size / 16.0 + 1) / 2;
            int ax = (int) Math.floor(px - rad), bx = (int) Math.floor(px + rad);
            int ay = (int) Math.floor(py - rad), by = (int) Math.floor(py + rad);
            int az = (int) Math.floor(pz - rad), bz = (int) Math.floor(pz + rad);
            for (int x = ax; x <= bx; x++) {
                double dx = (x + 0.5 - px) / rad;
                if (dx * dx >= 1) continue;
                for (int y = ay; y <= by; y++) {
                    double dy = (y + 0.5 - py) / rad;
                    if (dx * dx + dy * dy >= 1) continue;
                    for (int z = az; z <= bz; z++) {
                        double dz = (z + 0.5 - pz) / rad;
                        if (dx * dx + dy * dy + dz * dz >= 1 || !region.contains(x, y, z)) continue;
                        long p = PointsSource.pack(x, y, z);
                        if (seen.add(p)) out.add(p);
                    }
                }
            }
        }
        // Una veta chica con el centro entre bloques puede no tocar ninguno: al menos el del centro.
        if (seen.isEmpty() && region.contains(cx, cy, cz)) out.add(PointsSource.pack(cx, cy, cz));
    }
}
