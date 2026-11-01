package dev.leo.skinstatues.model;

/** Matriz de rotación 3×3 (fila por fila). */
public record Mat3(double a, double b, double c, double d, double e, double f, double g, double h, double i) {
    public static final Mat3 IDENTITY = new Mat3(1, 0, 0, 0, 1, 0, 0, 0, 1);

    public static Mat3 rotX(double deg) {
        double r = Math.toRadians(deg), co = cos(r), si = sin(r);
        return new Mat3(1, 0, 0, 0, co, -si, 0, si, co);
    }

    public static Mat3 rotY(double deg) {
        double r = Math.toRadians(deg), co = cos(r), si = sin(r);
        return new Mat3(co, 0, si, 0, 1, 0, -si, 0, co);
    }

    public static Mat3 rotZ(double deg) {
        double r = Math.toRadians(deg), co = cos(r), si = sin(r);
        return new Mat3(co, -si, 0, si, co, 0, 0, 0, 1);
    }

    /** Seno y coseno sin ruido en los ángulos rectos: 90° da exactamente 0 y 1 (la estatua sin girar queda alineada). */
    private static double cos(double r) {
        return snap(Math.cos(r));
    }

    private static double sin(double r) {
        return snap(Math.sin(r));
    }

    private static double snap(double v) {
        double k = Math.rint(v);
        return Math.abs(v - k) < 1e-12 ? k : v;
    }

    public Vec3 mul(Vec3 v) {
        return new Vec3(a * v.x() + b * v.y() + c * v.z(), d * v.x() + e * v.y() + f * v.z(), g * v.x() + h * v.y() + i * v.z());
    }

    public Mat3 mul(Mat3 m) {
        return new Mat3(
                a * m.a + b * m.d + c * m.g, a * m.b + b * m.e + c * m.h, a * m.c + b * m.f + c * m.i,
                d * m.a + e * m.d + f * m.g, d * m.b + e * m.e + f * m.h, d * m.c + e * m.f + f * m.i,
                g * m.a + h * m.d + i * m.g, g * m.b + h * m.e + i * m.h, g * m.c + h * m.f + i * m.i);
    }

    public Mat3 transpose() {
        return new Mat3(a, d, g, b, e, h, c, f, i);
    }

    /** Verdadero si solo permuta ejes (giros de 0/90/180/270°): la cáscara puede ser de un bloque. */
    public boolean axisAligned() {
        for (double v : new double[]{a, b, c, d, e, f, g, h, i}) {
            if (Math.abs(v) > 1e-9 && Math.abs(Math.abs(v) - 1) > 1e-9) return false;
        }
        return true;
    }
}
