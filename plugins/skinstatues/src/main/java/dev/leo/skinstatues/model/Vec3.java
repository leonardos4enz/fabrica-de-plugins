package dev.leo.skinstatues.model;

/** Vector de tres componentes (píxeles de skin o bloques, según el espacio). */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 add(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 sub(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    public Vec3 scale(double k) {
        return new Vec3(x * k, y * k, z * k);
    }

    public double dot(Vec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public double length() {
        return Math.sqrt(dot(this));
    }

    public Vec3 normalize() {
        double l = length();
        return l < 1e-12 ? this : scale(1 / l);
    }

    public double get(int axis) {
        return axis == 0 ? x : axis == 1 ? y : z;
    }
}
