package dev.leo.skinstatues.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Problemas de configuración (constitución I.3): archivo, ruta dentro del archivo, qué está mal y «did you mean». Un
 * aviso no impide cargar; un error omite lo inválido.
 */
public final class Issues {
    public enum Level { ERROR, WARNING }

    public record Issue(Level level, String file, String path, String message) {
        @Override
        public String toString() {
            return file + (path.isEmpty() ? "" : " › " + path) + ": " + message;
        }
    }

    private final List<Issue> list = new ArrayList<>();

    public void error(String file, String path, String message) {
        list.add(new Issue(Level.ERROR, file, path, message));
    }

    public void warn(String file, String path, String message) {
        list.add(new Issue(Level.WARNING, file, path, message));
    }

    public List<Issue> all() {
        return List.copyOf(list);
    }

    public long errors() {
        return list.stream().filter(i -> i.level() == Level.ERROR).count();
    }

    public long warnings() {
        return list.stream().filter(i -> i.level() == Level.WARNING).count();
    }

    /** « (did you mean 'x'?)» si hay una opción parecida; vacío si no. */
    public static String hint(String input, Collection<String> options) {
        String s = closest(input, options);
        return s == null ? "" : " (did you mean '" + s + "'?)";
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
        return bestD <= Math.max(2, in.length() / 3) ? best : null;
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
