package dev.leo.worldcraft.pattern;

import dev.leo.worldcraft.block.StateTable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Qué bloques toca una operación (H2 CA2). Un bloque pasa si coincide con algún término positivo (o no hay ninguno) y
 * con ningún término negado: «*_wool, !red_wool» es toda la lana menos la roja; «!air» es todo menos el aire.
 * Spec 029: un término puede pedir además cosas de la posición («stone above grass_block», «exposed», «30% chance»),
 * unidas con «and».
 */
public record Mask(List<Term> terms, String text) {

    /**
     * @param materials ids de bloque que coinciden (null = cualquiera)
     * @param props     propiedades que tienen que tener ese valor (las demás no importan)
     * @param near      spec 029: lo que además tiene que cumplir la posición (vacío = solo el estado)
     * @param not       spec 029 (F-01): partes de estado negadas dentro de un «and» («#logs and !birch_log»)
     */
    public record Term(Set<String> materials, Map<String, String> props, boolean negated, List<Near> near, List<Term> not) {
        public Term(Set<String> materials, Map<String, String> props, boolean negated) {
            this(materials, props, negated, List.of(), List.of());
        }

        public Term(Set<String> materials, Map<String, String> props, boolean negated, List<Near> near) {
            this(materials, props, negated, near, List.of());
        }

        public Term {
            near = List.copyOf(near);
            not = List.copyOf(not);
        }

        boolean matches(String material, Map<String, String> p) {
            if (materials != null && !materials.contains(material)) return false;
            for (Map.Entry<String, String> e : props.entrySet()) if (!e.getValue().equals(p.get(e.getKey()))) return false;
            for (Term n : not) if (n.matches(material, p)) return false;
            return true;
        }
    }

    /** Spec 029: lo que mira la posición del bloque, no solo su estado. */
    public sealed interface Near {
        /** El bloque de abajo (dy = -1, «above») o el de arriba (dy = 1, «below») cumple esa máscara de estado. */
        record Neighbor(int dy, Mask of) implements Near {
        }

        /** Toca aire por alguna de sus 6 caras (y no es aire). */
        record Exposed() implements Near {
        }

        /** No es aire y tiene aire justo encima. */
        record Surface() implements Near {
        }

        /** Al azar con esa probabilidad (0–1), con la semilla de la obra: la vista previa y la obra eligen lo mismo. */
        record Chance(double p) implements Near {
        }

        /** Spec 033: el bioma de la posición es uno de esos («$desert», ids completos). */
        record Biome(Set<String> biomes) implements Near {
        }

        /** F-01: lo contrario («stone and !surface»). */
        record Not(Near of) implements Near {
        }

        /** Spec 034 H5 CA1: la fórmula es verdadera en x, y, z del mundo («=y&lt;64»). */
        record Formula(dev.leo.worldcraft.formula.Formula formula) implements Near {
        }
    }

    /** Spec 033: el bioma de una posición («minecraft:plains»); en Paper lee el mundo en el hilo principal. */
    @FunctionalInterface
    public interface BiomeLookup {
        String at(int x, int y, int z);
    }

    /** Lo que hay alrededor: la foto leída antes de la obra (H2 CA2). -1 = no se sabe (fuera de la foto). */
    @FunctionalInterface
    public interface Neighbors {
        int at(int x, int y, int z);
    }

    public Mask {
        terms = List.copyOf(terms);
    }

    /** La máscara que deja pasar todo. */
    public static final Mask ANY = new Mask(List.of(), "*");

    public boolean test(String material, Map<String, String> props) {
        boolean anyPositive = false, positive = false;
        for (Term t : terms) {
            if (t.negated) {
                if (t.matches(material, props)) return false;
            } else {
                anyPositive = true;
                if (!positive && t.matches(material, props)) positive = true;
            }
        }
        return positive || !anyPositive;
    }

    /** Spec 029: mira algo más que el estado (vecinos o azar). */
    public boolean positional() {
        for (Term t : terms) if (!t.near.isEmpty()) return true;
        return false;
    }

    /** Spec 029: mira los bloques de al lado (hace falta leer una foto antes de la obra). */
    public boolean needsNeighbors() {
        for (Term t : terms) {
            for (Near n : t.near) {
                Near in = n instanceof Near.Not no ? no.of() : n;
                if (!(in instanceof Near.Chance) && !(in instanceof Near.Biome) && !(in instanceof Near.Formula)) return true;
            }
        }
        return false;
    }

    /** Spec 033: mira el bioma (hace falta un {@link BiomeLookup} al compilar). */
    public boolean needsBiomes() {
        for (Term t : terms) for (Near n : t.near) if ((n instanceof Near.Not no ? no.of() : n) instanceof Near.Biome) return true;
        return false;
    }

    public Compiled compile(StateTable table) {
        return new Compiled(this, table);
    }

    /** La máscara sobre números de estado: cada estado se decide una vez y se recuerda. */
    public static final class Compiled {
        private final Mask mask;
        private final StateTable table;
        private final boolean positional;
        private byte[] memo = new byte[64]; // 0 sin decidir, 1 no, 2 sí
        /** Spec 029: por término, si el estado coincide (memo[término][estado]) y las máscaras de vecino compiladas. */
        private final byte[][] termMemo;
        private final Compiled[][] inner;
        private byte[] air = new byte[64];
        /** Spec 033: de dónde sale el bioma; null = ningún bioma pasa. */
        private BiomeLookup biomes;

        Compiled(Mask mask, StateTable table) {
            this.mask = mask;
            this.table = table;
            this.positional = mask.positional();
            this.termMemo = new byte[mask.terms.size()][];
            this.inner = new Compiled[mask.terms.size()][];
            for (int i = 0; i < mask.terms.size(); i++) {
                termMemo[i] = new byte[64];
                List<Near> near = mask.terms.get(i).near;
                inner[i] = new Compiled[near.size()];
                for (int k = 0; k < near.size(); k++) {
                    Near n = near.get(k) instanceof Near.Not no ? no.of() : near.get(k);
                    if (n instanceof Near.Neighbor nb) inner[i][k] = nb.of().compile(table);
                }
            }
        }

        public Mask mask() {
            return mask;
        }

        /** Spec 033: con qué se lee el bioma de una posición (también en las máscaras de «above»/«below»). */
        public Compiled biomes(BiomeLookup lookup) {
            this.biomes = lookup;
            for (Compiled[] row : inner) for (Compiled c : row) if (c != null) c.biomes(lookup);
            return this;
        }

        /** Solo el estado: para las máscaras sin posición (las de hoy, el terreno, el pincel). */
        public boolean test(int id) {
            if (mask.terms.isEmpty()) return true;
            if (id < 0) return false;
            if (id >= memo.length) memo = java.util.Arrays.copyOf(memo, Math.max(id + 1, memo.length * 2));
            byte m = memo[id];
            if (m == 0) {
                m = mask.test(table.material(id), table.properties(id)) ? (byte) 2 : 1;
                memo[id] = m;
            }
            return m == 2;
        }

        /**
         * Spec 029: el estado y, si hace falta, la posición. near puede ser null si la máscara no mira vecinos (H4: la
         * máscara global solo admite el azar).
         */
        public boolean test(int x, int y, int z, int id, Neighbors near, long seed) {
            if (!positional) return test(id);
            if (id < 0) return false;
            boolean anyPositive = false, positive = false;
            for (int i = 0; i < mask.terms.size(); i++) {
                Term t = mask.terms.get(i);
                if (!t.negated) {
                    anyPositive = true;
                    if (positive) continue;
                }
                boolean hit = stateHit(i, t, id) && nearHit(i, t, x, y, z, id, near, seed);
                if (t.negated && hit) return false;
                if (!t.negated && hit) positive = true;
            }
            return positive || !anyPositive;
        }

        private boolean stateHit(int i, Term t, int id) {
            byte[] tm = termMemo[i];
            if (id >= tm.length) termMemo[i] = tm = java.util.Arrays.copyOf(tm, Math.max(id + 1, tm.length * 2));
            if (tm[id] == 0) tm[id] = t.matches(table.material(id), table.properties(id)) ? (byte) 2 : 1;
            return tm[id] == 2;
        }

        private boolean nearHit(int i, Term t, int x, int y, int z, int id, Neighbors near, long seed) {
            for (int k = 0; k < t.near.size(); k++) {
                Near n = t.near.get(k);
                boolean flip = n instanceof Near.Not;
                if (n instanceof Near.Not no) n = no.of();
                boolean ok = switch (n) {
                    case Near.Chance c -> (Pattern.mix(x, y, z, seed ^ 0x5DEECE66DL) >>> 11) * 0x1.0p-53 < c.p();
                    case Near.Neighbor nb -> near != null && inner[i][k].test(near.at(x, y + nb.dy(), z));
                    case Near.Surface s -> near != null && !isAir(id) && isAir(near.at(x, y + 1, z));
                    case Near.Exposed e -> near != null && !isAir(id) && (isAir(near.at(x + 1, y, z)) || isAir(near.at(x - 1, y, z))
                            || isAir(near.at(x, y + 1, z)) || isAir(near.at(x, y - 1, z))
                            || isAir(near.at(x, y, z + 1)) || isAir(near.at(x, y, z - 1)));
                    case Near.Biome b -> biomes != null && b.biomes().contains(biomes.at(x, y, z));
                    case Near.Formula f -> formula(f.formula(), x, y, z);
                    case Near.Not no -> throw new IllegalStateException("not inside not");
                };
                if (ok == flip) return false;
            }
            return true;
        }

        /** Spec 034: una evaluación cortada por las vueltas (plan §1.2) cuenta como falsa. */
        static boolean formula(dev.leo.worldcraft.formula.Formula f, int x, int y, int z) {
            try {
                return f.local().eval(x, y, z) > 0;
            } catch (dev.leo.worldcraft.formula.Formula.Stop e) {
                return false;
            }
        }

        private boolean isAir(int id) {
            if (id < 0) return false;
            if (id >= air.length) air = java.util.Arrays.copyOf(air, Math.max(id + 1, air.length * 2));
            if (air[id] == 0) air[id] = dev.leo.worldcraft.block.BlockRegistry.AIR.contains(table.material(id)) ? (byte) 2 : 1;
            return air[id] == 2;
        }
    }
}
