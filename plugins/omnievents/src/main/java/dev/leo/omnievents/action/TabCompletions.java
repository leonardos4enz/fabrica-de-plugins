package dev.leo.omnievents.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** La acción tab_complete sobre una lista de sugerencias (spec 015, H5 CA3), como la de ConditionalEvents. Pura. */
public final class TabCompletions {
    private TabCompletions() {
    }

    /** La palabra a medio escribir: lo que va después del último espacio del texto del chat. */
    public static String last(String buffer) {
        int sp = buffer.lastIndexOf(' ');
        return sp < 0 ? "" : buffer.substring(sp + 1);
    }

    /**
     * set: solo las de items que empiezan por last, ordenadas; add: las suma a las actuales (las que empiezan por last
     * y no estaban); remove: quita esas; clear: ninguna.
     */
    public static List<String> apply(String mode, List<String> current, List<String> items, String last) {
        String prefix = last.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>(current);
        switch (mode) {
            case "clear" -> out.clear();
            case "remove" -> out.removeIf(s -> items.stream().anyMatch(i -> i.equalsIgnoreCase(s)));
            case "set" -> {
                out.clear();
                for (String i : items) if (i.toLowerCase(Locale.ROOT).startsWith(prefix) && !out.contains(i)) out.add(i);
                out.sort(String.CASE_INSENSITIVE_ORDER);
            }
            case "add" -> {
                for (String i : items) if (i.toLowerCase(Locale.ROOT).startsWith(prefix) && !out.contains(i)) out.add(i);
            }
            default -> throw new IllegalArgumentException("modo de tab_complete desconocido '" + mode + "'");
        }
        return out;
    }
}
