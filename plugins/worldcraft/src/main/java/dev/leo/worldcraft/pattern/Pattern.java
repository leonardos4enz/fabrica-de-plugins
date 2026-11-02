package dev.leo.worldcraft.pattern;

import dev.leo.worldcraft.block.StateTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Qué bloque poner en cada lugar (H2 CA1): uno solo o una mezcla con pesos. La mezcla elige con un hash de la posición
 * y la semilla de la operación, así la vista previa y la obra ponen lo mismo (H2 CA5).
 *
 * @param states  estados canónicos; spec 029: para un cambio, el estado de fábrica de su tipo (^tipo) o null (^[estado])
 * @param weights pesos relativos (no hace falta que sumen 100)
 * @param text    lo que escribió el jugador, para mensajes y el historial
 * @param changes spec 029: por entrada, null (poner el estado) o el cambio sobre el bloque actual («^stone_stairs»,
 *                «^[waterlogged=false]»): del estado canónico actual al nuevo, o null si no se puede (el bloque se deja)
 */
public record Pattern(List<String> states, List<Double> weights, String text, List<UnaryOperator<String>> changes) {

    public Pattern(List<String> states, List<Double> weights, String text) {
        this(states, weights, text, Collections.nCopies(states.size(), null));
    }

    public Pattern {
        if (states.isEmpty() || states.size() != weights.size() || states.size() != changes.size()) {
            throw new IllegalArgumentException("empty pattern");
        }
        states = Collections.unmodifiableList(new ArrayList<>(states));
        weights = List.copyOf(weights);
        changes = Collections.unmodifiableList(new ArrayList<>(changes));
    }

    public static Pattern of(String state) {
        return new Pattern(List.of(state), List.of(1.0), state);
    }

    /** Los estados que puede poner (para los bloques prohibidos): sin los ^[estado], que no cambian el material. */
    public List<String> placedStates() {
        List<String> out = new ArrayList<>();
        for (String s : states) if (s != null) out.add(s);
        return out;
    }

    /** Spec 029: tiene algún cambio que depende del bloque actual. */
    public boolean changesCurrent() {
        for (UnaryOperator<String> c : changes) if (c != null) return true;
        return false;
    }

    public Compiled compile(StateTable table) {
        int[] ids = new int[states.size()];
        double[] cumulative = new double[states.size()];
        double sum = 0;
        for (int i = 0; i < ids.length; i++) {
            ids[i] = changes.get(i) != null ? -1 : table.intern(states.get(i));
            sum += weights.get(i);
            cumulative[i] = sum;
        }
        for (int i = 0; i < cumulative.length; i++) cumulative[i] /= sum;
        return new Compiled(ids, cumulative, changes, table);
    }

    /** El patrón ya con números de estado: se evalúa por bloque sin texto ni asignaciones. */
    public static final class Compiled {
        private final int[] ids;
        private final double[] cumulative;
        private final List<UnaryOperator<String>> changes;
        private final StateTable table;
        /** Spec 029: por entrada con cambio, estado actual → estado nuevo + 1 (0 sin decidir; LEAVE = dejarlo). */
        private final int[][] memo;

        Compiled(int[] ids, double[] cumulative, List<UnaryOperator<String>> changes, StateTable table) {
            this.ids = ids;
            this.cumulative = cumulative;
            this.changes = changes;
            this.table = table;
            this.memo = new int[ids.length][];
            for (int i = 0; i < ids.length; i++) if (changes.get(i) != null) memo[i] = new int[64];
        }

        /** Sin saber qué hay: un cambio (^tipo) deja el bloque como está. */
        public int apply(int x, int y, int z, long seed) {
            return apply(x, y, z, -1, seed);
        }

        /** El estado nuevo sabiendo el actual; -1 (dejar) si la entrada elegida es un cambio que no aplica. */
        public int apply(int x, int y, int z, int current, long seed) {
            int i = 0;
            if (ids.length > 1) {
                double r = (mix(x, y, z, seed) >>> 11) * 0x1.0p-53;
                i = ids.length - 1;
                for (int k = 0; k < cumulative.length - 1; k++) {
                    if (r < cumulative[k]) {
                        i = k;
                        break;
                    }
                }
            }
            if (ids[i] >= 0) return ids[i];
            if (current < 0) return -1;
            int[] m = memo[i];
            if (current >= m.length) memo[i] = m = java.util.Arrays.copyOf(m, Math.max(current + 1, m.length * 2));
            if (m[current] == 0) {
                String next = changes.get(i).apply(table.state(current));
                m[current] = next == null ? LEAVE : table.intern(next) + 1;
            }
            return m[current] == LEAVE ? -1 : m[current] - 1;
        }

        private static final int LEAVE = Integer.MIN_VALUE;
    }

    /** SplitMix64 de la posición y la semilla: el mismo lugar con la misma semilla da siempre el mismo número. */
    static long mix(int x, int y, int z, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
