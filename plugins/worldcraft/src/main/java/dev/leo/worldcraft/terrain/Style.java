package dev.leo.worldcraft.terrain;

import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.pattern.Pattern;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Un estilo de terreno de {@code terrain.yml} (H4 CA3): capas de material de la superficie hacia adentro, cada una con
 * reglas por altura relativa, pendiente y altura absoluta; y para las islas, raíces colgantes y árboles. También puede
 * traer opciones del generador (el volcán trae su cráter).
 *
 * @param blend   cuánto se desordena el borde entre reglas (0 = una línea recta de nieve)
 * @param hanging raíces bajo la isla, o null
 * @param log     tronco de los árboles de la isla, o null (sin árboles)
 * @param leaves  hojas (ya persistentes)
 */
public record Style(String name, String description, double blend, Map<String, Double> options, List<Layer> layers,
                    Pattern hanging, Pattern log, Pattern leaves) {

    /** @param thickness bloques de la capa; 0 = el resto (la última) */
    public record Layer(int thickness, List<Rule> rules) {
    }

    /** Una regla: el bloque si se cumplen sus condiciones (las que faltan, NaN, no cuentan). */
    public record Rule(Pattern block, double heightAbove, double heightBelow, double slopeAbove, double slopeBelow,
                       double yAbove, double yBelow) {
        public static Rule always(Pattern p) {
            return new Rule(p, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }

        boolean matches(double rel, double slope, int y) {
            return (Double.isNaN(heightAbove) || rel >= heightAbove) && (Double.isNaN(heightBelow) || rel < heightBelow)
                    && (Double.isNaN(slopeAbove) || slope >= slopeAbove) && (Double.isNaN(slopeBelow) || slope < slopeBelow)
                    && (Double.isNaN(yAbove) || y >= yAbove) && (Double.isNaN(yBelow) || y < yBelow);
        }
    }

    public Style {
        options = Map.copyOf(options);
        layers = List.copyOf(layers);
    }

    /** Un estilo de un solo patrón (pintar con un bloque). */
    public static Style of(Pattern p) {
        return new Style("pattern", p.text(), 0, Map.of(), List.of(new Layer(0, List.of(Rule.always(p)))), null, null, null);
    }

    /** Lo que suma el grosor de las capas con grosor (la parte de arriba que pinta el pincel). */
    public int paintDepth() {
        int d = 0;
        for (Layer l : layers) if (l.thickness > 0) d += l.thickness;
        return Math.max(1, d);
    }

    /** Todos los estados que puede poner (para los bloques prohibidos del grupo). */
    public Set<String> states() {
        Set<String> out = new LinkedHashSet<>();
        for (Layer l : layers) for (Rule r : l.rules) out.addAll(r.block.states());
        for (Pattern p : new Pattern[]{hanging, log, leaves}) if (p != null) out.addAll(p.states());
        return out;
    }

    public Painter painter(StateTable table, long seed) {
        return new Painter(this, table, seed);
    }

    /** El estilo con números de estado y su semilla: elige un bloque por posición, sin texto ni asignaciones. */
    public static final class Painter {
        private final int[] ends;
        private final Pattern.Compiled[][] blocks;
        private final Rule[][] rules;
        private final double blend;
        private final long seed;
        private final Noise jitter;
        public final Pattern.Compiled hanging, log, leaves;

        Painter(Style s, StateTable table, long seed) {
            this.seed = seed;
            this.blend = s.blend;
            this.jitter = new Noise(seed).derive(77);
            int n = s.layers.size();
            ends = new int[n];
            blocks = new Pattern.Compiled[n][];
            rules = new Rule[n][];
            int sum = 0;
            for (int i = 0; i < n; i++) {
                Layer l = s.layers.get(i);
                sum = l.thickness <= 0 || i == n - 1 ? Integer.MAX_VALUE : sum + l.thickness;
                ends[i] = sum;
                rules[i] = l.rules.toArray(new Rule[0]);
                List<Pattern.Compiled> c = new ArrayList<>();
                for (Rule r : l.rules) c.add(r.block.compile(table));
                blocks[i] = c.toArray(new Pattern.Compiled[0]);
            }
            hanging = s.hanging == null ? null : s.hanging.compile(table);
            log = s.log == null ? null : s.log.compile(table);
            leaves = s.leaves == null ? null : s.leaves.compile(table);
        }

        /**
         * El bloque para esa posición.
         *
         * @param depth bloques bajo la superficie (0 = la superficie)
         * @param rel   altura relativa (0 = pie, 1 = cima)
         * @param slope pendiente en grados
         */
        public int pick(int x, int y, int z, int depth, double rel, double slope) {
            int li = 0;
            while (li < ends.length - 1 && depth >= ends[li]) li++;
            if (blend > 0) {
                double j = jitter.at(x * 0.11, z * 0.11);
                rel += blend * j * 1.5;
                slope += blend * 60 * jitter.at(z * 0.17 + 40, x * 0.17);
            }
            Rule[] rs = rules[li];
            for (int k = 0; k < rs.length; k++) {
                if (rs[k].matches(rel, slope, y)) return blocks[li][k].apply(x, y, z, seed);
            }
            return blocks[li][rs.length - 1].apply(x, y, z, seed);
        }

        public long seed() {
            return seed;
        }
    }
}
