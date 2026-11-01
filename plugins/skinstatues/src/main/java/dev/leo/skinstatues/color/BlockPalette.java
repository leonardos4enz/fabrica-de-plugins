package dev.leo.skinstatues.color;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paleta compilada (H6): los bloques permitidos con su color. Elige el más parecido como lo ve el ojo y recuerda la
 * respuesta por color de 24 bits (una estatua repite muchos colores). Se usa desde el hilo de cálculo.
 */
public final class BlockPalette {
    public record Entry(String id, int rgb, Lab lab) {
    }

    private final String name;
    private final List<Entry> entries;
    private final ConcurrentHashMap<Integer, Integer> cache = new ConcurrentHashMap<>();

    public BlockPalette(String name, List<Entry> entries) {
        if (entries.isEmpty()) throw new IllegalArgumentException("empty palette");
        this.name = name;
        this.entries = List.copyOf(entries);
    }

    public String name() {
        return name;
    }

    public List<Entry> entries() {
        return entries;
    }

    /** Índice del bloque más parecido al color (se ignora el alfa). */
    public int nearest(int argb) {
        int rgb = argb & 0xFFFFFF;
        return cache.computeIfAbsent(rgb, c -> {
            Lab lab = Lab.of(c);
            int best = 0;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < entries.size(); i++) {
                double d = entries.get(i).lab().distance2(lab);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            return best;
        });
    }

    public String nearestId(int argb) {
        return entries.get(nearest(argb)).id();
    }
}
