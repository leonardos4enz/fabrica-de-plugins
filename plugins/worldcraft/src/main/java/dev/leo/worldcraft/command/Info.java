package dev.leo.worldcraft.command;

import dev.leo.worldcraft.region.Box;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** chunkinfo, listchunks y searchitem (spec 040 H4): las cuentas, sin el servidor. */
public final class Info {
    private Info() {
    }

    /** El archivo de región de un chunk (32 × 32 chunks por archivo). */
    public static String region(int cx, int cz) {
        return "r." + (cx >> 5) + "." + (cz >> 5) + ".mca";
    }

    /** {chunk x, chunk z, x dentro, y, z dentro} de un bloque. */
    public static int[] chunk(int x, int y, int z) {
        return new int[]{x >> 4, z >> 4, x & 15, y, z & 15};
    }

    /** Los chunks que toca una caja, de norte a sur y de oeste a este: {cx, cz}. */
    public static List<int[]> chunks(Box b) {
        List<int[]> out = new ArrayList<>();
        for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) {
            for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) out.add(new int[]{cx, cz});
        }
        return out;
    }

    public static int pages(int count, int perPage) {
        return Math.max(1, (count + perPage - 1) / perPage);
    }

    /** La página (desde 1); vacía si se pasa. */
    public static <T> List<T> page(List<T> all, int page, int perPage) {
        int from = (page - 1) * perPage;
        if (page < 1 || from >= all.size()) return List.of();
        return all.subList(from, Math.min(all.size(), from + perPage));
    }

    /**
     * Los nombres que contienen el texto (un espacio vale como «_»): primero el exacto, después los que empiezan con él,
     * después el resto, cada grupo en orden alfabético.
     */
    public static List<String> search(Collection<String> keys, String text) {
        String q = text.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        if (q.startsWith("minecraft:")) q = q.substring(10);
        List<String> exact = new ArrayList<>(), start = new ArrayList<>(), rest = new ArrayList<>();
        for (String k : keys) {
            String n = k.startsWith("minecraft:") ? k.substring(10) : k;
            if (n.equals(q)) exact.add(k);
            else if (n.startsWith(q)) start.add(k);
            else if (n.contains(q)) rest.add(k);
        }
        start.sort(null);
        rest.sort(null);
        List<String> out = new ArrayList<>(exact);
        out.addAll(start);
        out.addAll(rest);
        return out;
    }
}
