package dev.leo.skinstatues.color;

/** Color en CIELAB (D65): la distancia entre dos colores se parece a la que ve el ojo (H6 CA4). */
public record Lab(double l, double a, double b) {
    public static Lab of(int rgb) {
        double r = linear((rgb >> 16) & 0xFF), g = linear((rgb >> 8) & 0xFF), bl = linear(rgb & 0xFF);
        double x = (0.4124 * r + 0.3576 * g + 0.1805 * bl) / 0.95047;
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * bl;
        double z = (0.0193 * r + 0.1192 * g + 0.9505 * bl) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new Lab(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz));
    }

    /** ΔE76 al cuadrado (basta para comparar). */
    public double distance2(Lab o) {
        double dl = l - o.l, da = a - o.a, db = b - o.b;
        return dl * dl + da * da + db * db;
    }

    private static double linear(int c) {
        double v = c / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
    }
}
