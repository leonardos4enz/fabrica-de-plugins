package dev.leo.worldcraft.block;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Girar y voltear el estado de un bloque (H3 CA3: escaleras, troncos, puertas). En Paper los giros y espejos en x y z
 * los hace {@code BlockData.rotate/mirror}; el volteo de arriba abajo no existe en la API y se hace aquí sobre el texto.
 */
public interface StateOps {
    /** Un cuarto de vuelta en el sentido del reloj visto desde arriba. */
    int rotate90(int state);

    /** Espejo: 'x' cambia este por oeste, 'z' norte por sur, 'y' arriba por abajo. */
    int flip(int state, char axis);

    /** Arriba por abajo en las propiedades que lo dicen (half, type, face, facing, attachment…). */
    static String flipY(String state) {
        Map<String, String> p = StateTable.parseProperties(state);
        if (p.isEmpty()) return state;
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : p.entrySet()) {
            String k = e.getKey(), v = e.getValue();
            String nv = switch (k) {
                case "half", "type" -> v.equals("top") ? "bottom" : v.equals("bottom") ? "top"
                        : v.equals("upper") ? "lower" : v.equals("lower") ? "upper" : v;
                case "face", "attach_face" -> v.equals("floor") ? "ceiling" : v.equals("ceiling") ? "floor" : v;
                case "facing", "vertical_direction" -> v.equals("up") ? "down" : v.equals("down") ? "up" : v;
                case "attachment" -> v.equals("floor") ? "ceiling" : v.equals("ceiling") ? "floor" : v;
                case "up", "down" -> v; // se cambian juntas abajo
                default -> v;
            };
            out.put(k, nv);
        }
        if (p.containsKey("up") && p.containsKey("down")) {
            out.put("up", p.get("down"));
            out.put("down", p.get("up"));
        }
        int b = state.indexOf('[');
        return StateTable.withProperties(state.substring(0, b), out);
    }
}
