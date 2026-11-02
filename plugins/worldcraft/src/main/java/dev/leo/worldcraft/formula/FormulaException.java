package dev.leo.worldcraft.formula;

/** Una fórmula mal escrita (spec 034 H1 CA3): el mensaje dice qué y dónde, antes de tocar el mundo. */
public final class FormulaException extends Exception {
    public FormulaException(String message) {
        super(message);
    }
}
