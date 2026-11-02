package dev.leo.worldcraft.net;

/** Falla al bajar una imagen (spec 035 H4 CA4), con la clave de messages.yml y sus valores. Copiado de SkinStatues (ADR 0007). */
public final class LinkException extends Exception {
    private final String key;
    private final Object[] args;

    public LinkException(String key, Object... args) {
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
