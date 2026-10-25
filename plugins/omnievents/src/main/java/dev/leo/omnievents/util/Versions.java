package dev.leo.omnievents.util;

import java.util.ArrayList;
import java.util.List;

/** Versiones x.y.z y restricciones como ">=1.2.0, <2.0.0" (spec 011, H3). */
public final class Versions {
    private static final java.util.regex.Pattern VERSION = java.util.regex.Pattern.compile("^(\\d{1,6})\\.(\\d{1,6})(?:\\.(\\d{1,6}))?$");
    private static final java.util.regex.Pattern PART = java.util.regex.Pattern.compile("^(>=|<=|>|<|=)?\\s*(\\S+)$");

    private Versions() {
    }

    /** [mayor, menor, parche]; IllegalArgumentException si no es x.y.z o x.y. */
    public static int[] parse(String v) {
        java.util.regex.Matcher m = v == null ? null : VERSION.matcher(v.trim());
        if (m == null || !m.matches()) throw new IllegalArgumentException("versión inválida '" + v + "' (usa x.y.z, p. ej. 1.2.0)");
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), m.group(3) == null ? 0 : Integer.parseInt(m.group(3))};
    }

    public static boolean valid(String v) {
        return v != null && VERSION.matcher(v.trim()).matches();
    }

    public static int compare(String a, String b) {
        int[] x = parse(a), y = parse(b);
        for (int i = 0; i < 3; i++) if (x[i] != y[i]) return Integer.compare(x[i], y[i]);
        return 0;
    }

    /** Las partes de una restricción, separadas por coma; IllegalArgumentException si alguna es inválida. */
    public static List<String[]> constraint(String c) {
        if (c == null || c.isBlank()) throw new IllegalArgumentException("restricción vacía (p. ej. \">=1.0.0\")");
        List<String[]> out = new ArrayList<>();
        for (String raw : c.split(",")) {
            java.util.regex.Matcher m = PART.matcher(raw.trim());
            if (!m.matches() || !valid(m.group(2))) {
                throw new IllegalArgumentException("restricción inválida '" + raw.trim() + "' (usa >=, >, <=, <, = y x.y.z, p. ej. \">=1.2.0, <2.0.0\")");
            }
            out.add(new String[]{m.group(1) == null ? "=" : m.group(1), m.group(2)});
        }
        return out;
    }

    public static boolean satisfies(String version, String constraint) {
        for (String[] p : constraint(constraint)) {
            int c = compare(version, p[1]);
            boolean ok = switch (p[0]) {
                case ">=" -> c >= 0;
                case ">" -> c > 0;
                case "<=" -> c <= 0;
                case "<" -> c < 0;
                default -> c == 0;
            };
            if (!ok) return false;
        }
        return true;
    }
}
