package dev.leo.worldcraft.block;

import java.util.Collection;
import java.util.Set;

/**
 * Lo que el analizador de patrones y máscaras necesita saber del servidor (plan 025 §1.3). En Paper lo da el registro
 * de materiales y etiquetas; en las unitarias, una lista fija (los materiales necesitan el registro del servidor y no
 * corren sin él: lección de la 021).
 */
public interface BlockRegistry {
    /** Los ids de todos los bloques que se pueden poner («minecraft:stone»…). */
    Collection<String> blocks();

    /**
     * La forma canónica de un estado escrito por alguien («oak_stairs[facing=north]» →
     * «minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]»).
     *
     * @throws IllegalArgumentException con el motivo en inglés si el bloque o una propiedad no existen.
     */
    String canonical(String state);

    /** Los ids de bloque de una etiqueta («logs» o «minecraft:logs»); null si la etiqueta no existe. */
    Set<String> tag(String name);

    /** Los nombres de las etiquetas de bloques («minecraft:logs»…), para sugerencias. */
    Collection<String> tags();

    /**
     * Spec 029 (máscara «solid»): si el bloque es sólido. En Paper, {@code Material.isSolid}; de fábrica, todo lo que no es
     * aire ni líquido.
     */
    default boolean solid(String id) {
        return !AIR.contains(id) && !id.equals("minecraft:water") && !id.equals("minecraft:lava");
    }

    /** Los ids de aire (aire, aire de cueva, aire del vacío). */
    Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");
}
