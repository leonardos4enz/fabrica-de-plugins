package dev.leo.worldcraft.schem;

import java.util.Map;

/**
 * Un esquema de Sponge leído o por escribir (plan 027 §1). blocks tiene un índice de la paleta por posición, en el orden
 * de la especificación: {@code x + z·width + y·width·length}. blockEntities va de ese índice a {@code {Id, Data}} (la
 * forma de v3, también para los de v1 y v2). offset es dónde va la esquina mínima respecto de quien pega.
 *
 * @param version          1, 2 o 3 (al escribir, 3)
 * @param dataVersion      la versión de datos del juego que lo escribió (-1 si el archivo no la dice)
 * @param entities         cuántas entidades traía (no se cargan: roadmap)
 * @param badBlockEntities datos de bloque que no se pudieron leer (posición fuera o mal formada)
 */
public record Schematic(int version, int dataVersion, int width, int height, int length, int[] offset, String[] palette,
                        int[] blocks, Map<Integer, Map<String, Object>> blockEntities, int entities, int badBlockEntities,
                        String name, String author, long date) {

    public long volume() {
        return (long) width * height * length;
    }

    public int index(int x, int y, int z) {
        return x + z * width + y * width * length;
    }
}
