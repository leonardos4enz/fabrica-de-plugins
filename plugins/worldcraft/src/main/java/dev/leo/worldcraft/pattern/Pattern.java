package dev.leo.worldcraft.pattern;

import dev.leo.worldcraft.block.StateTable;

import java.util.List;

/**
 * Qué bloque poner en cada lugar (H2 CA1): uno solo o una mezcla con pesos. La mezcla elige con un hash de la posición
 * y la semilla de la operación, así la vista previa y la obra ponen lo mismo (H2 CA5).
 *
 * @param states  estados canónicos
 * @param weights pesos relativos (no hace falta que sumen 100)
 * @param text    lo que escribió el jugador, para mensajes y el historial
 */
public record Pattern(List<String> states, List<Double> weights, String text) {

    public Pattern {
        if (states.isEmpty() || states.size() != weights.size()) throw new IllegalArgumentException("empty pattern");
        states = List.copyOf(states);
        weights = List.copyOf(weights);
    }

    public static Pattern of(String state) {
        return new Pattern(List.of(state), List.of(1.0), state);
    }

    public Compiled compile(StateTable table) {
        int[] ids = new int[states.size()];
        double[] cumulative = new double[states.size()];
        double sum = 0;
        for (int i = 0; i < ids.length; i++) {
            ids[i] = table.intern(states.get(i));
            sum += weights.get(i);
            cumulative[i] = sum;
        }
        for (int i = 0; i < cumulative.length; i++) cumulative[i] /= sum;
        return new Compiled(ids, cumulative);
    }

    /** El patrón ya con números de estado: se evalúa por bloque sin texto ni asignaciones. */
    public static final class Compiled {
        private final int[] ids;
        private final double[] cumulative;

        Compiled(int[] ids, double[] cumulative) {
            this.ids = ids;
            this.cumulative = cumulative;
        }

        public int apply(int x, int y, int z, long seed) {
            if (ids.length == 1) return ids[0];
            double r = (mix(x, y, z, seed) >>> 11) * 0x1.0p-53;
            for (int i = 0; i < cumulative.length - 1; i++) if (r < cumulative[i]) return ids[i];
            return ids[ids.length - 1];
        }
    }

    /** SplitMix64 de la posición y la semilla: el mismo lugar con la misma semilla da siempre el mismo número. */
    static long mix(int x, int y, int z, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
