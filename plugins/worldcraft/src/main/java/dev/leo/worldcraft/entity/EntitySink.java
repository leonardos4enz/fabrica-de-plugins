package dev.leo.worldcraft.entity;

import java.util.UUID;

/** A dónde va lo que una etapa hace con las entidades (el diario de la obra). */
public interface EntitySink {
    EntitySink NONE = new EntitySink() {
    };

    /** La obra quitó esa entidad (copiada antes de quitarla). */
    default void removed(UUID id, EntityCopy copy) {
    }

    /** La obra creó esa entidad (copiada después de crearla). */
    default void added(UUID id, EntityCopy copy) {
    }

    /** Deshacer o rehacer recreó con otro uuid la entidad que el diario conoce como old. */
    default void remapped(UUID old, UUID now) {
    }
}
