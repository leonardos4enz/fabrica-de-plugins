package dev.leo.worldcraft.formula;

import dev.leo.worldcraft.region.Box;

import java.util.Locale;

/**
 * Cómo ve una fórmula las coordenadas (spec 034 H2 CA1), como WorldEdit: de −1 a 1 en la caja (de fábrica), las del
 * mundo (--raw), desde un punto (--offset) o desde el centro sin escalar (--center).
 */
public record Space(Mode mode, double zx, double zy, double zz, double ux, double uy, double uz) {
    public enum Mode {
        NORMAL, RAW, OFFSET, CENTER;

        public String flag() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * @param box  la caja de la selección (o de la esfera del pincel)
     * @param from el punto de --offset (los pies del jugador o el bloque que mira)
     */
    public static Space of(Mode mode, Box box, int[] from) {
        double cx = (box.minX() + box.maxX()) / 2.0, cy = (box.minY() + box.maxY()) / 2.0, cz = (box.minZ() + box.maxZ()) / 2.0;
        return switch (mode) {
            case RAW -> new Space(mode, 0, 0, 0, 1, 1, 1);
            case OFFSET -> new Space(mode, from[0], from[1], from[2], 1, 1, 1);
            case CENTER -> new Space(mode, cx, cy, cz, 1, 1, 1);
            case NORMAL -> new Space(mode, cx, cy, cz, unit(box.maxX() - cx), unit(box.maxY() - cy), unit(box.maxZ() - cz));
        };
    }

    private static double unit(double half) {
        return half == 0 ? 1 : half;
    }

    public double lx(double x) {
        return (x - zx) / ux;
    }

    public double ly(double y) {
        return (y - zy) / uy;
    }

    public double lz(double z) {
        return (z - zz) / uz;
    }

    /** De vuelta al bloque del mundo, redondeando (como WorldEdit). Fuera de lo razonable (NaN, infinito): MIN_VALUE. */
    public int wx(double v) {
        return world(v * ux + zx);
    }

    public int wy(double v) {
        return world(v * uy + zy);
    }

    public int wz(double v) {
        return world(v * uz + zz);
    }

    private static int world(double w) {
        if (!(Math.abs(w) < 30_000_000)) return Integer.MIN_VALUE;
        return (int) Math.floor(w + 0.5);
    }
}
