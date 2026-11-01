package dev.leo.skinstatues.statue;

import dev.leo.skinstatues.color.BlockPalette;
import dev.leo.skinstatues.model.VoxelGrid;

import java.util.Arrays;

/**
 * Lo que se va a poner (se arma fuera del hilo principal): posiciones relativas al bloque base, ordenadas de abajo
 * hacia arriba, y el índice del bloque de la paleta para cada una.
 */
public final class StatuePlan {
    final int[] dx, dy, dz, block;
    final String[] ids;
    final int height, width, depth;

    private StatuePlan(int[] dx, int[] dy, int[] dz, int[] block, String[] ids, int height, int width, int depth) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.block = block;
        this.ids = ids;
        this.height = height;
        this.width = width;
        this.depth = depth;
    }

    public static StatuePlan of(VoxelGrid g, BlockPalette palette) {
        int n = g.size();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> g.y(a) != g.y(b) ? Integer.compare(g.y(a), g.y(b))
                : g.x(a) != g.x(b) ? Integer.compare(g.x(a), g.x(b)) : Integer.compare(g.z(a), g.z(b)));
        int[] dx = new int[n], dy = new int[n], dz = new int[n], block = new int[n];
        for (int k = 0; k < n; k++) {
            int i = order[k];
            dx[k] = g.x(i);
            dy[k] = g.y(i);
            dz[k] = g.z(i);
            block[k] = palette.nearest(g.color(i));
        }
        String[] ids = palette.entries().stream().map(BlockPalette.Entry::id).toArray(String[]::new);
        return new StatuePlan(dx, dy, dz, block, ids, g.height(), g.maxX() - g.minX() + 1, g.maxZ() - g.minZ() + 1);
    }

    public int size() {
        return dx.length;
    }

    public int height() {
        return height;
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    /** Id de bloque de la posición k (para pruebas y para el selftest). */
    public String id(int k) {
        return ids[block[k]];
    }

    public int x(int k) {
        return dx[k];
    }

    public int y(int k) {
        return dy[k];
    }

    public int z(int k) {
        return dz[k];
    }
}
