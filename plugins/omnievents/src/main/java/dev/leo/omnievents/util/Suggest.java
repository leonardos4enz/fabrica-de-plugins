package dev.leo.omnievents.util;

import java.util.Collection;
import java.util.Locale;

/** «¿Quisiste decir…?» por distancia de edición. */
public final class Suggest {
    private Suggest() {
    }

    public static String closest(String input, Collection<String> options) {
        if (input == null) return null;
        String in = input.toLowerCase(Locale.ROOT);
        String best = null;
        int bestD = Integer.MAX_VALUE;
        for (String o : options) {
            int d = distance(in, o.toLowerCase(Locale.ROOT));
            if (d < bestD) {
                bestD = d;
                best = o;
            }
        }
        int max = Math.max(2, in.length() / 3);
        return bestD <= max ? best : null;
    }

    public static String hint(String input, Collection<String> options) {
        String s = closest(input, options);
        return s == null ? "" : " (¿quisiste decir '" + s + "'?)";
    }

    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
