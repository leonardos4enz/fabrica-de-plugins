package dev.leo.worldcraft.region;

/**
 * Dónde puede pararse un jugador (spec 028 H4, plan §1.4): lugar libre = los pies y la cabeza atravesables. Java puro;
 * el mundo llega como {@link World}. Devuelven la y (o la posición) nueva de los pies, o null si no hay.
 */
public final class Spots {
    private Spots() {
    }

    public interface World {
        /** ¿Se puede estar ahí (aire, plantas, agua)? */
        boolean passable(int x, int y, int z);

        int minY();

        int maxY();
    }

    private static boolean free(World w, int x, int y, int z) {
        return y >= w.minY() && y + 1 <= w.maxY() && w.passable(x, y, z) && w.passable(x, y + 1, z);
    }

    private static boolean floor(World w, int x, int y, int z) {
        return y - 1 >= w.minY() && !w.passable(x, y - 1, z);
    }

    /** ascend: el siguiente piso de arriba (un lugar libre con suelo); n veces. */
    public static Integer ascend(World w, int x, int y, int z, int n) {
        Integer at = null;
        int from = y;
        for (int k = 0; k < n; k++) {
            Integer next = null;
            for (int yy = from + 1; yy < w.maxY(); yy++) {
                if (free(w, x, yy, z) && floor(w, x, yy, z)) {
                    next = yy;
                    break;
                }
            }
            if (next == null) break;
            at = next;
            from = next;
        }
        return at;
    }

    /** descend: el siguiente piso de abajo; n veces. */
    public static Integer descend(World w, int x, int y, int z, int n) {
        Integer at = null;
        int from = y;
        for (int k = 0; k < n; k++) {
            Integer next = null;
            for (int yy = from - 1; yy > w.minY(); yy--) {
                // Por debajo del piso actual: tiene que haber un bloque entre medio.
                if (free(w, x, yy, z) && floor(w, x, yy, z) && blockedBetween(w, x, yy, from, z)) {
                    next = yy;
                    break;
                }
            }
            if (next == null) break;
            at = next;
            from = next;
        }
        return at;
    }

    private static boolean blockedBetween(World w, int x, int lowFeet, int highFeet, int z) {
        for (int yy = lowFeet + 2; yy < highFeet; yy++) if (!w.passable(x, yy, z)) return true;
        return false;
    }

    /** ceil: los pies justo bajo el techo (dejando clearance bloques libres arriba); null sin techo. */
    public static Integer ceil(World w, int x, int y, int z, int clearance) {
        for (int yy = y + 2; yy <= w.maxY(); yy++) {
            if (!w.passable(x, yy, z)) {
                int feet = yy - 2 - Math.max(0, clearance);
                return feet > y ? feet : null;
            }
        }
        return null;
    }

    /** up: n bloques más arriba si todo el camino está libre (no atraviesa techos). */
    public static Integer up(World w, int x, int y, int z, int n) {
        if (n < 1) return null;
        for (int yy = y + 1; yy <= y + n + 1; yy++) {
            if (yy > w.maxY() || !w.passable(x, yy, z)) return null;
        }
        return y + n;
    }

    /** unstuck: el primer lugar libre desde los pies hacia arriba. */
    public static Integer unstuck(World w, int x, int y, int z) {
        for (int yy = Math.max(y, w.minY()); yy < w.maxY(); yy++) if (free(w, x, yy, z)) return yy;
        return null;
    }

    /**
     * thru: atraviesa la pared que hay en la dirección (dx, dy, dz) desde los pies: primero un bloque que no se puede
     * atravesar, después el primer lugar libre (hasta max bloques). Devuelve {x, y, z} o null.
     */
    public static int[] thru(World w, int x, int y, int z, int dx, int dy, int dz, int max) {
        boolean wall = false;
        for (int k = 1; k <= max; k++) {
            int cx = x + dx * k, cy = y + dy * k, cz = z + dz * k;
            if (cy < w.minY() || cy > w.maxY()) return null;
            // De costado choca con los pies o la cabeza; hacia arriba, con la cabeza.
            boolean blocked = dy > 0 ? !w.passable(cx, cy + 1, cz)
                    : dy < 0 ? !w.passable(cx, cy, cz)
                    : !w.passable(cx, cy, cz) || !w.passable(cx, cy + 1, cz);
            if (!wall) {
                if (blocked) wall = true;
                continue;
            }
            if (free(w, cx, cy, cz)) return new int[]{cx, cy, cz};
        }
        return null;
    }

    /** jumpto: encima del bloque apuntado, en el primer lugar libre. */
    public static Integer onTop(World w, int x, int hitY, int z) {
        for (int yy = hitY + 1; yy < w.maxY(); yy++) if (free(w, x, yy, z)) return yy;
        return null;
    }
}
