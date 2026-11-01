package dev.leo.worldcraft.region;

/** Qué parte de la caja toca una operación (H3 CA1). */
public enum Shape {
    /** Toda la caja (set, replace). */
    FILL,
    /** Las cuatro paredes de los costados, de abajo hasta arriba (walls). */
    WALLS,
    /** Las seis caras: paredes, piso y techo; hueca por dentro (faces). */
    FACES
}
