package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;

/**
 * Qué cambia una operación, posición por posición (plan 025 §1.4). Las posiciones salen en orden de chunks; para cada
 * una el trabajo lee el estado actual y pregunta el nuevo. Así la máscara se evalúa contra el mundo real en el momento
 * de poner el bloque, no contra una foto vieja.
 */
public interface EditSource {
    /** No tocar esa posición. */
    int KEEP = -1;

    /** La siguiente posición en p = {x, y, z}; false si no hay más. */
    boolean next(int[] p);

    /** El estado nuevo para la última posición dada por {@link #next}, sabiendo el actual; {@link #KEEP} para dejarla. */
    int target(int x, int y, int z, int current);

    /** Datos de bloque (cofre, cartel) para el último estado devuelto por {@link #target}, o null. */
    default byte[] tile() {
        return null;
    }

    /** Las posiciones que va a recorrer (para el progreso y los límites). */
    long volume();

    Box bounds();

    /** Lista para empezar (un deshacer espera a leer su diario del disco). */
    default boolean ready() {
        return true;
    }

    /** Las posiciones que no se tocaron porque alguien las cambió después (deshacer seguro, H6 CA3). */
    default long skipped() {
        return 0;
    }
}
