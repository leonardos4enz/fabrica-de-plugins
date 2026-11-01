package dev.leo.skinstatues.skin;

/** Falla al conseguir una skin, con la clave de messages.yml y sus valores (H2 CA3, CA4). */
public final class SkinException extends Exception {
    private final String key;
    private final Object[] args;

    public SkinException(String key, Object... args) {
        super(key);
        this.key = key;
        this.args = args;
    }

    public String key() {
        return key;
    }

    /** Pares clave, valor para el mensaje. */
    public Object[] args() {
        return args;
    }
}
