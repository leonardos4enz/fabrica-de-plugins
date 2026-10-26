package dev.leo.omnievents.action;

import dev.leo.omnievents.util.Suggest;

import java.util.List;
import java.util.Locale;

/**
 * Casillas del inventario de un jugador con los nombres de ConditionalEvents (spec 015): HAND, OFF_HAND, HELMET,
 * CHESTPLATE, LEGGINGS, BOOTS o un número de 0 a 40 (36–39 la armadura, 40 la mano secundaria).
 */
public final class Slots {
    /** La casilla de la mano principal, que depende de lo que tenga seleccionado el jugador. */
    public static final int HAND = -1;
    public static final List<String> NAMES = List.of("HAND", "OFF_HAND", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");

    private Slots() {
    }

    /** Índice de la casilla (HAND = {@link #HAND}); IllegalArgumentException si no es una casilla. */
    public static int parse(String text) {
        String s = text.trim().toUpperCase(Locale.ROOT);
        switch (s) {
            case "HAND", "MAIN_HAND" -> {
                return HAND;
            }
            case "OFF_HAND", "OFFHAND" -> {
                return 40;
            }
            case "HELMET", "HEAD" -> {
                return 39;
            }
            case "CHESTPLATE", "CHEST" -> {
                return 38;
            }
            case "LEGGINGS", "LEGS" -> {
                return 37;
            }
            case "BOOTS", "FEET" -> {
                return 36;
            }
            default -> {
            }
        }
        try {
            int n = Integer.parseInt(s);
            if (n >= 0 && n <= 40) return n;
        } catch (NumberFormatException ignored) {
            // abajo
        }
        throw new IllegalArgumentException("casilla desconocida '" + text.trim() + "' (usa 0 a 40 o " + String.join(", ", NAMES) + ")"
                + Suggest.hint(s, NAMES));
    }
}
