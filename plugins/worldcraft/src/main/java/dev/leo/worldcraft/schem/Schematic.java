package dev.leo.worldcraft.schem;

import java.util.List;
import java.util.Map;

/**
 * Un esquema de Sponge leído o por escribir (plan 027 §1). blocks tiene un índice de la paleta por posición, en el orden
 * de la especificación: {@code x + z·width + y·width·length}. blockEntities va de ese índice a {@code {Id, Data}} (la
 * forma de v3, también para los de v1 y v2). offset es dónde va la esquina mínima respecto de quien pega.
 *
 * @param version          1, 2 o 3 (al escribir, 3)
 * @param dataVersion      la versión de datos del juego que lo escribió (-1 si el archivo no la dice)
 * @param entities         cuántas entidades traía el archivo
 * @param entityList       spec 031: las que se pudieron leer, en la forma de v3 ({Pos double[3], Id, Data}; ver SchemEntities)
 * @param badBlockEntities datos de bloque que no se pudieron leer (posición fuera o mal formada)
 * @param biomePalette     spec 033: los nombres de los biomas, o null si no trae
 * @param biomes           spec 033: un índice de biomePalette por bloque (el mismo orden que blocks), o null
 */
public record Schematic(int version, int dataVersion, int width, int height, int length, int[] offset, String[] palette,
                        int[] blocks, Map<Integer, Map<String, Object>> blockEntities, int entities, int badBlockEntities,
                        String name, String author, long date, List<Map<String, Object>> entityList,
                        String[] biomePalette, int[] biomes) {
    public Schematic(int version, int dataVersion, int width, int height, int length, int[] offset, String[] palette,
                     int[] blocks, Map<Integer, Map<String, Object>> blockEntities, int entities, int badBlockEntities,
                     String name, String author, long date, List<Map<String, Object>> entityList) {
        this(version, dataVersion, width, height, length, offset, palette, blocks, blockEntities, entities, badBlockEntities,
                name, author, date, entityList, null, null);
    }

    /** Spec 033: el mismo esquema con biomas. */
    public Schematic withBiomes(String[] palette, int[] perBlock) {
        return new Schematic(version, dataVersion, width, height, length, offset, this.palette, blocks, blockEntities, entities,
                badBlockEntities, name, author, date, entityList, palette, perBlock);
    }

    public Schematic(int version, int dataVersion, int width, int height, int length, int[] offset, String[] palette,
                     int[] blocks, Map<Integer, Map<String, Object>> blockEntities, int entities, int badBlockEntities,
                     String name, String author, long date) {
        this(version, dataVersion, width, height, length, offset, palette, blocks, blockEntities, entities, badBlockEntities,
                name, author, date, List.of());
    }

    public long volume() {
        return (long) width * height * length;
    }

    public int index(int x, int y, int z) {
        return x + z * width + y * width * length;
    }
}
