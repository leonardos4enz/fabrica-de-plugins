package dev.leo.omnievents.trigger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Estado de una regla state por jugador (spec 008, H1 y H3): quién está dentro, desde cuándo y si corrió su
 * on_enter (la pareja de on_exit). Solo memoria; puro, sin Bukkit.
 */
public final class StateTable {
    /** Clave del estado del servidor (per_player: false). */
    public static final UUID SERVER = new UUID(0, 0);

    /** Dentro del estado desde {@code since} (ms); {@code paired}: corrió su on_enter. */
    public record Entry(long since, boolean paired) {
    }

    public enum Kind { NONE, ENTER, ENTER_SILENT, EXIT }

    /** Qué hacer tras una revisión; {@code entry} es la entrada que se cierra en EXIT. */
    public record Change(Kind kind, Entry entry) {
        static final Change NONE = new Change(Kind.NONE, null);
    }

    private final Map<UUID, Entry> inside = new HashMap<>();
    private final Set<UUID> known = new HashSet<>();

    /**
     * Aplica el valor de una revisión. La primera de un jugador (al entrar al servidor, o al cargar la regla) entra
     * en silencio si {@code silentFirst}.
     */
    public Change update(UUID id, boolean value, long now, boolean silentFirst) {
        boolean first = known.add(id);
        boolean was = inside.containsKey(id);
        if (value == was) return Change.NONE;
        if (value) {
            inside.put(id, new Entry(now, false));
            return new Change(first && silentFirst ? Kind.ENTER_SILENT : Kind.ENTER, null);
        }
        return new Change(Kind.EXIT, inside.remove(id));
    }

    /** El on_enter de la entrada actual corrió: su on_exit correrá al salir (CLARIFY 3). */
    public void paired(UUID id) {
        inside.computeIfPresent(id, (k, e) -> new Entry(e.since(), true));
    }

    public boolean isIn(UUID id) {
        return inside.containsKey(id);
    }

    public boolean known(UUID id) {
        return known.contains(id);
    }

    public Entry entry(UUID id) {
        return inside.get(id);
    }

    /** El jugador salió del servidor: se olvida sin on_exit (H3 CA1). */
    public void forget(UUID id) {
        known.remove(id);
        inside.remove(id);
    }

    public Map<UUID, Entry> inside() {
        return new LinkedHashMap<>(inside);
    }

    public int size() {
        return inside.size();
    }
}
