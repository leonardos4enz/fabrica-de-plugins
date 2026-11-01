package dev.leo.worldcraft.schem;

import dev.leo.worldcraft.block.BlockRegistry;
import dev.leo.worldcraft.block.StateTable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * La paleta de un esquema a estados de este servidor (spec 027 H2 CA4, plan §1.5): tal cual; si el bloque no existe, por
 * un nombre cambiado conocido; si una propiedad no existe o su valor no vale, sin ella; y si nada sirve, aire. Se llama
 * en el hilo principal (el registro de Paper y la tabla de estados), una vez por entrada de la paleta.
 */
public final class SchemBlocks {
    /** Nombres cambiados entre versiones del juego (los dos sentidos): solo se prueban si el original no existe. */
    static final Map<String, String> RENAMED = Map.of(
            "minecraft:grass", "minecraft:short_grass", "minecraft:short_grass", "minecraft:grass",
            "minecraft:grass_path", "minecraft:dirt_path", "minecraft:dirt_path", "minecraft:grass_path",
            "minecraft:chain", "minecraft:iron_chain", "minecraft:iron_chain", "minecraft:chain");

    /**
     * @param map         de índice de la paleta a número de estado
     * @param unknown     bloques de esquema que se cargaron como aire
     * @param unknownByName esos bloques por nombre, de más a menos
     * @param adjusted    entradas de la paleta a las que se les quitó una propiedad
     * @param renamed     entradas traducidas por un nombre cambiado
     */
    public record Result(int[] map, long unknown, Map<String, Long> unknownByName, int adjusted, int renamed) {
    }

    private SchemBlocks() {
    }

    /** counts: cuántos bloques del esquema usan cada entrada (para el informe). */
    public static Result resolve(String[] palette, long[] counts, BlockRegistry registry, StateTable table) {
        Set<String> known = new HashSet<>(registry.blocks());
        int air = table.intern(registry.canonical("minecraft:air"));
        int[] map = new int[palette.length];
        Map<String, Long> unknown = new TreeMap<>();
        long unknownBlocks = 0;
        int adjusted = 0, renamed = 0;
        for (int i = 0; i < palette.length; i++) {
            if (palette[i] == null) {
                map[i] = air;
                continue;
            }
            String state = palette[i].trim();
            int b = state.indexOf('[');
            String mat = (b < 0 ? state : state.substring(0, b)).toLowerCase(Locale.ROOT);
            if (!mat.contains(":")) mat = "minecraft:" + mat;
            Map<String, String> props = StateTable.parseProperties(state);
            String use = mat;
            if (!known.contains(mat)) {
                String other = RENAMED.get(mat);
                use = other != null && known.contains(other) ? other : null;
                if (use != null) renamed++;
            }
            String canonical = use == null ? null : canonical(registry, use, props);
            if (canonical == null && use != null) {
                // Una propiedad que este servidor no tiene (o un valor): se arma con las que sí valen.
                Map<String, String> ok = new LinkedHashMap<>();
                canonical = canonical(registry, use, ok);
                if (canonical != null) {
                    for (Map.Entry<String, String> e : props.entrySet()) {
                        ok.put(e.getKey(), e.getValue());
                        String c = canonical(registry, use, ok);
                        if (c == null) ok.remove(e.getKey());
                        else canonical = c;
                    }
                    adjusted++;
                }
            }
            if (canonical == null) {
                map[i] = air;
                long n = counts == null ? 0 : counts[i];
                unknownBlocks += n;
                unknown.merge(mat, n, Long::sum);
            } else {
                map[i] = table.intern(canonical);
            }
        }
        Map<String, Long> sorted = new LinkedHashMap<>();
        unknown.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue(), x.getValue())).forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return new Result(map, unknownBlocks, sorted, adjusted, renamed);
    }

    private static String canonical(BlockRegistry registry, String material, Map<String, String> props) {
        try {
            return registry.canonical(StateTable.withProperties(material, props));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Cuántos bloques usan cada entrada de la paleta (fuera del hilo principal). */
    public static long[] counts(Schematic s) {
        long[] c = new long[s.palette().length];
        for (int b : s.blocks()) c[b]++;
        return c;
    }
}
