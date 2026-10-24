package dev.leo.omnievents.expr;

/** Ámbito de una variable: de la ejecución, del jugador o del servidor. */
public enum Scope {
    LOCAL("var"), PLAYER("pvar"), GLOBAL("gvar");

    public final String prefix;

    Scope(String prefix) {
        this.prefix = prefix;
    }

    public static Scope byPrefix(String p) {
        for (Scope s : values()) if (s.prefix.equalsIgnoreCase(p)) return s;
        return null;
    }
}
