package dev.leo.worldcraft.terrain;

import dev.leo.worldcraft.pattern.Mask;

import java.util.function.IntPredicate;

/**
 * Qué es cada bloque para el terreno (plan 026 §2): suelo natural, cobertura (aire, plantas, agua: se tapa o se quita)
 * y enterrable (árboles: una montaña los puede tapar, el pincel no los toca). Lo demás es una construcción.
 */
public record Masks(Mask.Compiled ground, Mask.Compiled cover, Mask.Compiled buryable, IntPredicate air) {

    public boolean ground(int s) {
        return ground.test(s);
    }

    /** Aire o una planta: se puede poner encima. */
    public boolean open(int s) {
        return air.test(s) || cover.test(s);
    }

    /** Lo que una montaña o una isla puede llenar. */
    public boolean fillable(int s) {
        return open(s) || buryable.test(s);
    }
}
