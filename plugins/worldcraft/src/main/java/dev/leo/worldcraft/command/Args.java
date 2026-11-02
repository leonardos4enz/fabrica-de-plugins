package dev.leo.worldcraft.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Los argumentos de un subcomando: lo posicional y las opciones «--x» que pueden ir en cualquier lugar. Las opciones
 * con valor (--player, --world) toman el siguiente argumento.
 */
public final class Args {
    public static final Set<String> WITH_VALUE = Set.of("player", "world", "at", "thickness");

    public final List<String> positional = new ArrayList<>();
    public final Set<String> flags = new HashSet<>();
    public final Map<String, String> values = new HashMap<>();
    /** Una opción que no se conoce, o una con valor que se quedó sin él. */
    public String problem;

    public static Args parse(String[] args, int from, Set<String> known) {
        Args a = new Args();
        for (int i = from; i < args.length; i++) {
            String s = args[i];
            if (s.startsWith("--") && s.length() > 2) {
                String name = s.substring(2).toLowerCase(Locale.ROOT);
                if (!known.contains(name)) {
                    if (a.problem == null) a.problem = "Unknown option '" + s + "'" + hint(name, known);
                    continue;
                }
                if (WITH_VALUE.contains(name)) {
                    if (i + 1 >= args.length) {
                        if (a.problem == null) a.problem = "'" + s + "' needs a value";
                        continue;
                    }
                    a.values.put(name, args[++i]);
                } else {
                    a.flags.add(name);
                }
            } else {
                a.positional.add(s);
            }
        }
        return a;
    }

    private static String hint(String name, Set<String> known) {
        String c = dev.leo.worldcraft.config.Issues.closest(name, known);
        return c == null ? "" : " (did you mean '--" + c + "'?)";
    }

    /** Lo posicional desde i, unido con espacios (un patrón «70% stone, 30% dirt» llega en varios argumentos). */
    public String join(int i) {
        return i >= positional.size() ? "" : String.join(" ", positional.subList(i, positional.size()));
    }

    /**
     * replace: «&lt;máscara&gt; with &lt;patrón&gt;» o, sin «with», el primer argumento es la máscara. Devuelve {máscara, patrón}
     * o null si falta algo.
     */
    public String[] maskAndPattern() {
        int with = -1;
        for (int i = 0; i < positional.size(); i++) if (positional.get(i).equalsIgnoreCase("with")) with = i;
        if (with > 0) {
            String m = String.join(" ", positional.subList(0, with)), p = join(with + 1);
            return p.isBlank() ? null : new String[]{m, p};
        }
        if (positional.size() < 2) return null;
        return new String[]{positional.get(0), join(1)};
    }
}
