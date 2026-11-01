package dev.leo.blockwright.pattern;

import dev.leo.blockwright.block.StateTable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Qué bloques toca una operación (H2 CA2). Un bloque pasa si coincide con algún término positivo (o no hay ninguno) y
 * con ningún término negado: «*_wool, !red_wool» es toda la lana menos la roja; «!air» es todo menos el aire.
 */
public record Mask(List<Term> terms, String text) {

    /**
     * @param materials ids de bloque que coinciden (null = cualquiera)
     * @param props     propiedades que tienen que tener ese valor (las demás no importan)
     */
    public record Term(Set<String> materials, Map<String, String> props, boolean negated) {
        boolean matches(String material, Map<String, String> p) {
            if (materials != null && !materials.contains(material)) return false;
            for (Map.Entry<String, String> e : props.entrySet()) if (!e.getValue().equals(p.get(e.getKey()))) return false;
            return true;
        }
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

    public Compiled compile(StateTable table) {
        return new Compiled(this, table);
    }

    /** La máscara sobre números de estado: cada estado se decide una vez y se recuerda. */
    public static final class Compiled {
        private final Mask mask;
        private final StateTable table;
        private byte[] memo = new byte[64]; // 0 sin decidir, 1 no, 2 sí

        Compiled(Mask mask, StateTable table) {
            this.mask = mask;
            this.table = table;
        }

        public boolean test(int id) {
            if (mask.terms.isEmpty()) return true;
            if (id >= memo.length) memo = java.util.Arrays.copyOf(memo, Math.max(id + 1, memo.length * 2));
            byte m = memo[id];
            if (m == 0) {
                m = mask.test(table.material(id), table.properties(id)) ? (byte) 2 : 1;
                memo[id] = m;
            }
            return m == 2;
        }
    }
}
