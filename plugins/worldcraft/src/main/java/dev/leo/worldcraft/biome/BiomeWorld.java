package dev.leo.worldcraft.biome;

/**
 * Los biomas de un mundo tal como los ve el motor (plan 033 §1.1). El juego guarda un bioma por celda de 4×4×4: {@link #get}
 * y {@link #set} hablan de la celda que contiene esa posición. Los nombres son los ids completos («minecraft:plains»). En
 * Paper lo implementa {@code PaperBiomes}; en las unitarias, uno en memoria. Hilo principal.
 */
public interface BiomeWorld {
    int minY();

    /** El y más alto del mundo (incluido). */
    int maxY();

    String get(int x, int y, int z);

    void set(int x, int y, int z, String biome);

    /** El chunk está cargado: true. Si no, lo pide sin bloquear y devuelve false (la etapa espera al siguiente tick). */
    boolean ready(int chunkX, int chunkZ);

    /** Los jugadores que ven ese chunk reciben los biomas nuevos (sin salir y entrar). */
    default void refresh(int chunkX, int chunkZ) {
    }

    /** Suelta lo que sostiene (al terminar la etapa). */
    default void close() {
    }
}
