package dev.leo.worldcraft.terrain;

import dev.leo.worldcraft.region.Box;

import java.util.Arrays;

/**
 * El suelo de un rectángulo, columna por columna (plan 026 §1): la altura del suelo natural, su bloque de superficie y el
 * de debajo, la planta más alta encima y si hay algo que no es suelo ni planta (un árbol o una construcción). Lo llena
 * {@link ColumnScan}; los generadores y el pincel lo leen en otro hilo (solo números, sin Bukkit).
 */
public final class HeightField {
    /** Columna sin suelo dentro de la ventana leída. */
    public static final int NONE = Integer.MIN_VALUE;
    /** Qué hay sobre el suelo, además de aire y plantas. */
    public static final byte CLEAR = 0, BURYABLE = 1, HARD = 2;

    public final int x0, z0, width, depth;
    /** La ventana de y que se leyó (incluida). */
    public final int top, bottom;
    final int[] ground, surface, under, cover;
    final byte[] obstacle;

    public HeightField(Box rect, int top, int bottom) {
        this.x0 = rect.minX();
        this.z0 = rect.minZ();
        this.width = rect.width();
        this.depth = rect.depth();
        this.top = top;
        this.bottom = bottom;
        int n = width * depth;
        ground = new int[n];
        surface = new int[n];
        under = new int[n];
        cover = new int[n];
        obstacle = new byte[n];
        Arrays.fill(ground, NONE);
        Arrays.fill(cover, NONE);
    }

    public Box rect() {
        return new Box(x0, 0, z0, x0 + width - 1, 0, z0 + depth - 1);
    }

    public int size() {
        return width * depth;
    }

    public boolean contains(int x, int z) {
        return x >= x0 && z >= z0 && x < x0 + width && z < z0 + depth;
    }

    public int index(int x, int z) {
        return (x - x0) + (z - z0) * width;
    }

    public int x(int i) {
        return x0 + i % width;
    }

    public int z(int i) {
        return z0 + i / width;
    }

    /** Altura del suelo o {@link #NONE}. */
    public int ground(int i) {
        return ground[i];
    }

    public int surface(int i) {
        return surface[i];
    }

    public int under(int i) {
        return under[i];
    }

    /** La planta (u otra cobertura) más alta sobre el suelo, o NONE. */
    public int cover(int i) {
        return cover[i];
    }

    public byte obstacle(int i) {
        return obstacle[i];
    }

    /** Para pruebas y para llenar a mano. */
    public void set(int i, int groundY, int surfaceState, int underState) {
        ground[i] = groundY;
        surface[i] = surfaceState;
        under[i] = underState;
    }

    void mark(int i, byte kind) {
        if (kind > obstacle[i]) obstacle[i] = kind;
    }

    void cover(int i, int y) {
        if (y > cover[i]) cover[i] = y;
    }

    /**
     * La pendiente en grados de una superficie dada por alturas (plan 026 §1.4): diferencias centrales a dos columnas, así
     * un escalón suelto en una ladera suave no cuenta como 45°. Las columnas sin altura (NONE) toman la del centro.
     */
    public static float[] slopes(int[] h, int width, int depth) {
        float[] out = new float[h.length];
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                int i = x + z * width;
                if (h[i] == NONE) continue;
                double dx = (at(h, width, depth, x + 2, z, h[i]) - at(h, width, depth, x - 2, z, h[i])) / 4.0;
                double dz = (at(h, width, depth, x, z + 2, h[i]) - at(h, width, depth, x, z - 2, h[i])) / 4.0;
                out[i] = (float) Math.toDegrees(Math.atan(Math.sqrt(dx * dx + dz * dz)));
            }
        }
        return out;
    }

    private static int at(int[] h, int width, int depth, int x, int z, int fallback) {
        if (x < 0 || z < 0 || x >= width || z >= depth) return fallback;
        int v = h[x + z * width];
        return v == NONE ? fallback : v;
    }
}
