package dev.leo.omnievents.expr;

/** Valor de enumeración o de registro (Material, GameMode, Sound…) que se compara con texto sin distinguir mayúsculas. */
public record Named(String name, Object raw) {
    @Override
    public String toString() {
        return name;
    }
}
