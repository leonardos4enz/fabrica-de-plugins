package dev.leo.omnievents.expr;

import java.util.Set;

/** Lo que una expresión puede consultar al evaluarse. Las implementaciones deciden de dónde sale cada dato. */
public interface EvalContext {
    /** Valor de una variable raíz (player, event, block…), o null si no existe. */
    Object resolve(String name);

    /** Raíces disponibles (para sugerencias). */
    default Set<String> roots() {
        return Set.of();
    }

    default Object getVar(Scope scope, String name) {
        return null;
    }

    /** Placeholder %x% para un jugador (el del contexto si es null). */
    default Object placeholder(String raw, Object player) {
        return raw;
    }

    /** Jugador del contexto (puede ser null). */
    default Object player() {
        return resolve("player");
    }

    /** Lo que devuelve {@link #objectVar} cuando el objeto no admite variables (spec 009). */
    Object NOT_OBJECT = new Object();

    /** Variable de un bloque, entidad o ítem ({@code X.data.nombre}); {@link #NOT_OBJECT} si no es uno de esos. */
    default Object objectVar(Object target, String name) {
        return NOT_OBJECT;
    }

    /** Servicios del plugin para funciones (cooldown, uses, pvar_of…); null si no hay. */
    default Object service(String name, Object... args) {
        return null;
    }
}
