package dev.leo.worldcraft.pattern;

/** Un patrón o una máscara mal escritos (H2 CA3): el mensaje, en inglés, dice qué parte y «did you mean». */
public final class ParseException extends Exception {
    public ParseException(String message) {
        super(message);
    }
}
