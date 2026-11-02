package dev.leo.worldcraft.biome;

/** A dónde va cada celda que una obra cambió de bioma (el diario, plan 033 §1.1). */
public interface BiomeSink {
    BiomeSink NONE = (x, y, z, before, after) -> {
    };

    /** La celda con esquina mínima (x, y, z) pasó de before a after. */
    void biome(int x, int y, int z, String before, String after);
}
