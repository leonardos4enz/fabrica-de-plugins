package dev.leo.blockwright.edit;

/**
 * El mundo tal como lo ve el motor (plan 025 §1.4): estados como números de la {@link dev.leo.blockwright.block.StateTable}.
 * En Paper lo implementa {@code PaperWorld}; en las unitarias, un mundo en memoria. Solo se usa en el hilo principal.
 */
public interface EditWorld {
    String name();

    int minY();

    /** El y más alto que se puede poner (incluido). */
    int maxY();

    int get(int x, int y, int z);

    /** Pone el estado sin física: la arena no cae ni el agua corre a mitad de una obra (H5 CA5). */
    void set(int x, int y, int z, int state);

    /** Los datos del bloque que no están en su estado (lo que hay en un cofre, el texto de un cartel); null si no hay. */
    byte[] tile(int x, int y, int z);

    /** Devuelve esos datos a un bloque recién puesto. */
    void restoreTile(int x, int y, int z, byte[] data);

    /**
     * El chunk está cargado y sostenido: true. Si no, lo pide sin bloquear y devuelve false (el trabajo espera al
     * siguiente tick).
     */
    boolean ready(int chunkX, int chunkZ);

    /** El trabajo terminó con ese chunk: se puede soltar. */
    void release(int chunkX, int chunkZ);
}
