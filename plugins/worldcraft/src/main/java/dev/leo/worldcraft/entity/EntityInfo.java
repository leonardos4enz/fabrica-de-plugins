package dev.leo.worldcraft.entity;

import java.util.Set;
import java.util.UUID;

/** Una entidad encontrada en un chunk, sin copiarla todavía: su uuid, tipo, posición y lo que es. */
public record EntityInfo(UUID id, String type, double x, double y, double z, Set<Kind> kinds) {
    public EntityInfo {
        kinds = Set.copyOf(kinds);
    }

    public boolean is(Kind k) {
        return kinds.contains(k);
    }
}
