package dev.leo.worldcraft.entity;

import java.util.List;
import java.util.UUID;

/**
 * Las entidades de un mundo tal como las ve el motor (plan 031 §1.2), como {@code EditWorld} para los bloques. En Paper
 * lo implementa {@code PaperEntities}; en las unitarias, un mundo en memoria. Solo en el hilo principal.
 */
public interface EntityWorld {
    /** El chunk y sus entidades están cargados: true. Si no, los pide sin bloquear y devuelve false. */
    boolean ready(int chunkX, int chunkZ);

    /** Las entidades del chunk que no van montadas en otra (las montadas viajan con ella); también jugadores. */
    List<EntityInfo> list(int chunkX, int chunkZ);

    /** La entidad copiada (posición del mundo), o null si ya no está o no se puede copiar. */
    EntityCopy copy(UUID id);

    /** El tipo de la entidad con ese uuid, o null si ya no está. */
    String type(UUID id);

    /** La quita (con lo que lleva montado) sin soltar nada; false si ya no estaba. */
    boolean remove(UUID id);

    /** La crea (posición del mundo); el uuid nuevo, o null si no se pudo. */
    UUID spawn(EntityCopy copy);
}
