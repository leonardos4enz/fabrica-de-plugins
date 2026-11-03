package dev.leo.worldcraft.tool;

import java.util.List;

/**
 * Spec 041 H3 CA6: lo que cambia la rueda con un pincel en la mano (/wc scroll), como las acciones de FAWE. clipboard lo
 * resuelve quien llama (los esquemas de schem loadall están en la sesión). Java puro.
 */
public final class Scroll {
    private Scroll() {
    }

    public static final List<String> ACTIONS = List.of("none", "size", "range", "targetoffset", "target", "mask", "pattern", "clipboard");

    /** De la casilla from a la to: +1 o −1 si es la de al lado (la rueda, con la vuelta 8 ↔ 0), 0 si saltó (los números). */
    public static int delta(int from, int to) {
        int d = Math.floorMod(to - from, 9);
        return d == 1 ? 1 : d == 8 ? -1 : 0;
    }

    /** El pincel con el valor de su acción movido delta pasos. */
    public static ToolSpec apply(ToolSpec s, int delta, ToolSpec.Caps caps) {
        String action = s.text("scroll");
        if (action == null) return s;
        return switch (action) {
            case "size" -> s.with("radius", String.valueOf(clamp(s.radius() + delta, 0, caps.brushMaxRadius())));
            case "range" -> {
                int now = s.text("reach") == null ? caps.maxRange() : s.number("reach");
                yield s.with("reach", String.valueOf(clamp(now + delta, 1, caps.maxRange())));
            }
            case "targetoffset" -> s.with("toffset", String.valueOf(clamp(s.number("toffset") + delta, -caps.maxTargetOffset(), caps.maxTargetOffset())));
            case "target" -> {
                List<String> t = ToolSpec.TARGETS;
                yield s.with("target", t.get(Math.floorMod(t.indexOf(s.target()) + delta, t.size())));
            }
            case "mask", "pattern" -> {
                String all = s.decodedText("scroll_values");
                if (all == null || all.isBlank()) yield s;
                String[] values = all.split("\\|");
                int i = Math.floorMod(s.number("scroll_i") + delta, values.length);
                yield s.with("scroll_i", String.valueOf(i)).with(action, values[i].trim());
            }
            default -> s;
        };
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
