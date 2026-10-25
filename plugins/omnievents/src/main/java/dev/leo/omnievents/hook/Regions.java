package dev.leo.omnievents.hook;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Regiones de WorldGuard (spec 012, H3); sin WorldGuard, {@link #NONE}. */
public interface Regions {
    boolean available();

    /** Las regiones en ese lugar (sin __global__), de mayor a menor prioridad. */
    List<Region> at(Location l);

    /** Una región tal como la ven las reglas: region.id, region.priority y region.world. */
    record Region(String id, int priority, String world) {
        public Map<String, Object> asMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("priority", (double) priority);
            m.put("world", world);
            return m;
        }
    }

    Regions NONE = new Regions() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public List<Region> at(Location l) {
            return List.of();
        }
    };
}
