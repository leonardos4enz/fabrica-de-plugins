package dev.leo.omnievents.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Duraciones legibles: 10s, 5m, 1h30m, 2d, 1w, 20t (ticks), 500ms; un número solo son segundos. */
public final class Durations {
    private static final Pattern PART = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(ms|t|s|m|h|d|w)");

    private Durations() {
    }

    /** Milisegundos, o lanza IllegalArgumentException con un mensaje en español. */
    public static long parseMillis(Object value) {
        if (value instanceof Number n) return Math.round(n.doubleValue() * 1000);
        String s = String.valueOf(value).trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (s.isEmpty()) throw new IllegalArgumentException("duración vacía");
        if (s.matches("\\d+(\\.\\d+)?")) return Math.round(Double.parseDouble(s) * 1000);
        Matcher m = PART.matcher(s);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) break;
            double n = Double.parseDouble(m.group(1));
            total += Math.round(n * switch (m.group(2)) {
                case "ms" -> 1;
                case "t" -> 50;
                case "s" -> 1000;
                case "m" -> 60_000;
                case "h" -> 3_600_000;
                case "d" -> 86_400_000;
                default -> 604_800_000;
            });
            end = m.end();
        }
        if (end != s.length() || end == 0) {
            throw new IllegalArgumentException("duración inválida '" + value + "' (usa por ejemplo 10s, 5m, 1h30m, 2d o 20t)");
        }
        return total;
    }

    public static long toTicks(long millis) {
        return Math.max(0, Math.round(millis / 50.0));
    }

    /** 1h 2m 3s, para mensajes. */
    public static String format(long millis) {
        long s = Math.max(0, (millis + 999) / 1000);
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60, sec = s % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (sec > 0 || sb.isEmpty()) sb.append(sec).append("s");
        return sb.toString().trim();
    }
}
