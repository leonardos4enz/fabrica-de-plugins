package dev.leo.worldcraft.structure;

import dev.leo.worldcraft.config.Issues;
import dev.leo.worldcraft.edit.Clipboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Las plantillas de estructuras del juego (spec 033 H7): el nombre que escribe alguien, la lista y la plantilla al
 * portapapeles. Java puro: los bloques llegan ya como números de estado del hilo principal.
 */
public final class Templates {
    private static final java.util.regex.Pattern KEY = java.util.regex.Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    private Templates() {
    }

    /** Un bloque de la plantilla, relativo a su esquina mínima. */
    public record Block(int x, int y, int z, int state, byte[] tile) {
    }

    /** «igloo/top» → «minecraft:igloo/top»; null si no es un nombre válido (nada de «..» ni mayúsculas raras). */
    public static String key(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (!n.contains(":")) n = "minecraft:" + n;
        if (!KEY.matcher(n).matches()) return null;
        String path = n.substring(n.indexOf(':') + 1);
        if (path.startsWith("/") || path.endsWith("/") || path.contains("//")) return null;
        for (String part : path.split("/")) if (part.equals("..") || part.equals(".")) return null;
        return n;
    }

    /** Los nombres que contienen el filtro (sin «minecraft:»), ordenados. */
    public static List<String> filter(Collection<String> all, String filter) {
        String f = filter == null ? "" : filter.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        List<String> out = new ArrayList<>();
        for (String k : all) {
            String shown = k.startsWith("minecraft:") ? k.substring(10) : k;
            if (shown.contains(f)) out.add(shown);
        }
        out.sort(null);
        return out;
    }

    /** «did you mean» entre las plantillas. */
    public static String hint(String name, Collection<String> all) {
        List<String> shown = filter(all, "");
        return Issues.hint(name.toLowerCase(Locale.ROOT).replace("minecraft:", ""), shown);
    }

    /**
     * H7 CA2, CA3: la plantilla al portapapeles con su esquina mínima a los pies de quien la carga. Lo que la plantilla no
     * trae (el «structure_void») queda sin leer y no se pega; un bloque de unión (jigsaw) queda como aire.
     */
    public static Clipboard toClipboard(int w, int h, int d, List<Block> blocks, IntPredicate jigsaw, int air) {
        int[] states = new int[w * h * d];
        java.util.Arrays.fill(states, -1);
        Map<Integer, byte[]> tiles = new HashMap<>();
        for (Block b : blocks) {
            if (b.x() < 0 || b.y() < 0 || b.z() < 0 || b.x() >= w || b.y() >= h || b.z() >= d) continue;
            int i = (b.y() * d + b.z()) * w + b.x();
            boolean j = jigsaw.test(b.state());
            states[i] = j ? air : b.state();
            if (!j && b.tile() != null) tiles.put(i, b.tile());
        }
        return Clipboard.of(w, h, d, 0, 0, 0, states, tiles);
    }
}
