package dev.leo.worldcraft.create;

import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.formula.Formula;
import dev.leo.worldcraft.formula.Noise3;
import dev.leo.worldcraft.formula.Shapes;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Region;

/**
 * Cuevas con ruido 3D (spec 035 H3, plan §1.3), como las del juego desde 1.18: un túnel es donde dos ruidos valen casi
 * cero a la vez (dos superficies que se cruzan en una línea: «espagueti»); una sala, donde un ruido lento pasa de un
 * umbral («queso»). Java puro, en el hilo de cálculo; qué se talla (la máscara, la lava) lo decide la obra.
 */
public final class Caves {
    private Caves() {
    }

    /** Bloques por unidad de ruido con la frecuencia de fábrica (40): un túnel cambia de rumbo cada ~25 bloques. */
    static final double WAVELENGTH = 24;
    /** Los túneles van más bien de lado: el ruido cambia 1,5 veces más rápido hacia arriba. */
    static final double SQUASH = 1.5;
    /** Una sala donde el ruido lento (un cuarto de la frecuencia) pasa de esto. */
    static final double ROOM = 0.45;

    /**
     * @param size      ancho de los túneles en bloques (1–10)
     * @param frequency cuántos túneles (1–100; 40 de fábrica)
     */
    public static long[] carve(Region region, int size, int frequency, boolean rooms, long seed, Shapes.Work work) {
        Noise3 a = new Noise3(seed), b = new Noise3(seed * 31 + 7), c = new Noise3(seed * 17 + 3);
        double k = frequency / 40.0 / WAVELENGTH;
        // Cerca de un cero el ruido cambia ~k por bloque: medio ancho de size/2 bloques es t ≈ k·size/2 (con el gradiente
        // medio de Perlin, algo menor que 1, el factor 0,9 lo ajusta; ver CavesTest).
        double t = k * size * 0.45, t2 = t * t;
        double kr = k / 4;
        Box box = region.bounds();
        Shapes.Longs out = new Shapes.Longs();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                if (System.nanoTime() > work.deadline) throw new Formula.TimedOut();
                int[] span = region.span(x, z);
                if (span == null) continue;
                for (int y = span[0]; y <= span[1]; y++) {
                    if (!region.contains(x, y, z)) continue;
                    work.at(x, y, z);
                    double nx = x * k, ny = y * k * SQUASH, nz = z * k;
                    double n1 = a.at(nx + 0.31, ny + 0.17, nz + 0.53); // fuera de la rejilla: Perlin vale 0 en sus puntos
                    boolean cut = false;
                    if (n1 * n1 < t2) {
                        double n2 = b.at(nx + 101.3, ny, nz - 57.9);
                        cut = n1 * n1 + n2 * n2 < t2;
                    }
                    if (!cut && rooms) cut = c.at(x * kr, y * kr * SQUASH, z * kr) > ROOM;
                    if (cut) out.add(PointsSource.pack(x, y, z));
                }
            }
        }
        return out.toArray();
    }
}
