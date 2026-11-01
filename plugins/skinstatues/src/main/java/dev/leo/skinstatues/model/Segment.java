package dev.leo.skinstatues.model;

/**
 * Un trozo rígido del modelo en su espacio de reposo (píxeles): la cabeza, el cuerpo, la capa o una mitad de un brazo o
 * pierna. Sabe qué caras son superficie de verdad, dónde lo corta el inglete del codo y de qué parte de la textura sale
 * cada cara.
 */
final class Segment {
    /** Caras en el orden −x, +x, −y, +y, −z, +z. */
    static final int NEG_X = 0, POS_X = 1, NEG_Y = 2, POS_Y = 3, NEG_Z = 4, POS_Z = 5;

    final String name;
    /** Caja geométrica (con la extensión hacia la articulación si es una mitad). */
    final double[] min, max;
    /** Caja de la textura: el miembro entero, de donde salen las UV. */
    final double[] tmin, tmax;
    /** Qué caras son superficie (la de la articulación no lo es). */
    final boolean[] real;
    /** Qué caras llevan segunda capa por fuera: las reales menos la suela del pie (si no, la estatua crece por abajo). */
    final boolean[] inflate;
    /** Rango de y (en reposo) de la mitad, para recortar la v de la textura. */
    final double yLo, yHi;
    /** Inglete: el punto pertenece al segmento si (p − miterPoint)·miterNormal ≥ 0. Null si no hay. */
    final Vec3 miterPoint, miterNormal;
    /** Origen de la textura de la primera y de la segunda capa (−1 si no tiene). */
    final int baseU, baseV, overU, overV;
    /** La capa usa la textura de la capa (64×32) en vez de la skin. */
    final boolean cape;
    /** Reposo → modelo (píxeles, origen entre los pies). */
    final Transform toModel;

    Segment(String name, double[] min, double[] max, double[] tmin, double[] tmax, boolean[] real, double yLo, double yHi,
            Vec3 miterPoint, Vec3 miterNormal, int baseU, int baseV, int overU, int overV, boolean cape, boolean sole, Transform toModel) {
        this.name = name;
        this.min = min;
        this.max = max;
        this.tmin = tmin;
        this.tmax = tmax;
        this.real = real;
        this.inflate = real.clone();
        if (sole) inflate[NEG_Y] = false;
        this.yLo = yLo;
        this.yHi = yHi;
        this.miterPoint = miterPoint;
        this.miterNormal = miterNormal;
        this.baseU = baseU;
        this.baseV = baseV;
        this.overU = overU;
        this.overV = overV;
        this.cape = cape;
        this.toModel = toModel;
    }

    boolean hasOverlay() {
        return overU >= 0;
    }

    boolean onMiterSide(Vec3 p) {
        return miterNormal == null || p.sub(miterPoint).dot(miterNormal) >= -1e-9;
    }

    /**
     * Coordenadas (u, v) en píxeles de la textura para el punto proyectado en la cara, con la convención de Minecraft:
     * de frente u crece hacia +x; los lados, la espalda, arriba y abajo siguen el despliegue de la caja.
     */
    double[] uv(int face, Vec3 p, int u0, int v0) {
        double w = tmax[0] - tmin[0], h = tmax[1] - tmin[1], d = tmax[2] - tmin[2];
        double x = clamp(p.x(), tmin[0], tmax[0]);
        double y = clamp(p.y(), Math.max(tmin[1], yLo), Math.min(tmax[1], yHi));
        double z = clamp(p.z(), tmin[2], tmax[2]);
        double fu, fv, u, v, fw, fh;
        switch (face) {
            case POS_Z -> { fu = u0 + d; fv = v0 + d; u = x - tmin[0]; v = tmax[1] - y; fw = w; fh = h; }
            case NEG_Z -> { fu = u0 + 2 * d + w; fv = v0 + d; u = tmax[0] - x; v = tmax[1] - y; fw = w; fh = h; }
            case NEG_X -> { fu = u0; fv = v0 + d; u = z - tmin[2]; v = tmax[1] - y; fw = d; fh = h; }
            case POS_X -> { fu = u0 + d + w; fv = v0 + d; u = tmax[2] - z; v = tmax[1] - y; fw = d; fh = h; }
            case POS_Y -> { fu = u0 + d; fv = v0; u = x - tmin[0]; v = z - tmin[2]; fw = w; fh = d; }
            default -> { fu = u0 + d + w; fv = v0; u = x - tmin[0]; v = tmax[2] - z; fw = w; fh = d; }
        }
        return new double[]{fu, fv, u, v, fw, fh};
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
