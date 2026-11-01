package dev.leo.blockwright.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Los estados de bloque que vio una sesión del plugin, cada uno con un número (plan 025 §1.2): el motor trabaja con
 * enteros y solo pasa a texto para el diario. El texto es la forma canónica de Minecraft
 * («minecraft:oak_stairs[facing=north,half=top,...]»). Solo se usa en el hilo principal.
 */
public final class StateTable {
    private final List<String> states = new ArrayList<>();
    private final List<String> materials = new ArrayList<>();
    private final List<Map<String, String>> properties = new ArrayList<>();
    private final Map<String, Integer> ids = new HashMap<>();

    /** El número de ese estado canónico (lo agrega si es nuevo). */
    public int intern(String canonical) {
        Integer id = ids.get(canonical);
        if (id != null) return id;
        int n = states.size();
        states.add(canonical);
        int b = canonical.indexOf('[');
        materials.add(b < 0 ? canonical : canonical.substring(0, b));
        properties.add(parseProperties(canonical));
        ids.put(canonical, n);
        return n;
    }

    public String state(int id) {
        return states.get(id);
    }

    /** «minecraft:oak_stairs». */
    public String material(int id) {
        return materials.get(id);
    }

    public Map<String, String> properties(int id) {
        return properties.get(id);
    }

    public int size() {
        return states.size();
    }

    /** Las propiedades de «a:b[k=v,k2=v2]», en orden. */
    public static Map<String, String> parseProperties(String state) {
        int b = state.indexOf('[');
        if (b < 0 || !state.endsWith("]")) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        for (String kv : state.substring(b + 1, state.length() - 1).split(",")) {
            int eq = kv.indexOf('=');
            if (eq > 0) out.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
        }
        return out;
    }

    /** El mismo estado con otras propiedades (para voltear de arriba abajo, plan §1.5). */
    public static String withProperties(String material, Map<String, String> props) {
        if (props.isEmpty()) return material;
        StringBuilder sb = new StringBuilder(material).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : props.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.append(']').toString();
    }
}
