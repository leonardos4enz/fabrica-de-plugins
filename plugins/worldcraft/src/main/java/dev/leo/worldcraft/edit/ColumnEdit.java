package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.terrain.ColumnSource;
import dev.leo.worldcraft.terrain.HeightField;

import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/**
 * Una obra columna por columna, cada una de arriba abajo de una vez (plan 028 §1.3): naturalize, thaw, green, y lo que
 * va encima de la cima leída (overlay, snow). La regla ve las posiciones en ese orden, así puede llevar la profundidad
 * desde la superficie sin leer antes.
 */
public final class ColumnEdit extends ColumnSource {
    /** Qué poner; {@link EditSource#KEEP} para dejarlo. */
    public interface Rule {
        int at(int x, int y, int z, int current);
    }

    /** El rango de y de una columna; hi &lt; lo = no tocarla. */
    public interface Range {
        int[] of(int x, int z);
    }

    private final Rule rule;

    public ColumnEdit(Box rect, Range range, Rule rule) {
        super(rect, false);
        this.rule = rule;
        for (int z = rect.minZ(); z <= rect.maxZ(); z++) {
            for (int x = rect.minX(); x <= rect.maxX(); x++) {
                int i = index(x, z);
                int[] r = range.of(x, z);
                if (r == null || r[1] < r[0]) empty(i);
                else {
                    lo[i] = r[0];
                    hi[i] = r[1];
                }
            }
        }
    }

    @Override
    protected int cell(int i, int x, int y, int z, int current) {
        return rule.at(x, y, z, current);
    }

    @Override
    protected int solidTop(int i) {
        return HeightField.NONE;
    }

    // ------------------------------------------------------------------ reglas

    /**
     * naturalize (H2 CA4): desde el primer bloque natural de la columna, profundidad 0 pasto, 1–3 tierra, más abajo
     * piedra; solo en bloques naturales. La profundidad sigue aunque haya una cueva.
     */
    public static Rule naturalize(IntPredicate natural, int grass, int dirt, int stone) {
        return new Rule() {
            int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE, start = Integer.MIN_VALUE;

            @Override
            public int at(int x, int y, int z, int current) {
                if (x != lastX || z != lastZ) {
                    lastX = x;
                    lastZ = z;
                    start = Integer.MIN_VALUE;
                }
                if (!natural.test(current)) return KEEP;
                if (start == Integer.MIN_VALUE) start = y;
                int depth = start - y;
                return depth == 0 ? grass : depth <= 3 ? dirt : stone;
            }
        };
    }

    /** Sobre la cima leída: de la cima (from = 0) o del bloque de encima (from = 1) hasta el de encima. */
    public static Range aboveTop(TopScan scan, int from) {
        return (x, z) -> {
            int t = scan.top(x, z);
            return t == TopScan.NONE ? null : new int[]{t + from, t + 1};
        };
    }

    /** overlay (H2 CA3): el patrón encima de la cima, solo donde hay aire o cobertura (no sobre un tronco). */
    public static Rule overlay(IntPredicate open, dev.leo.worldcraft.pattern.Pattern.Compiled pattern, long seed) {
        return (x, y, z, current) -> open.test(current) ? pattern.apply(x, y, z, current, seed) : KEEP;
    }

    /** snow (H3 CA4): el agua quieta de la cima se congela; sobre un bloque firme, una capa de nieve si hay aire. */
    public static Rule snow(TopScan scan, IntPredicate waterSource, IntPredicate sturdy, IntPredicate air, int ice, int snow) {
        return (x, y, z, current) -> {
            int t = scan.top(x, z);
            if (y == t) return waterSource.test(current) ? ice : KEEP;
            return air.test(current) && sturdy.test(scan.topState(x, z)) && !waterSource.test(scan.topState(x, z)) ? snow : KEEP;
        };
    }

    /** thaw y green (H3 CA4): cambia solo el primer bloque que no es aire de cada columna. */
    public static Rule firstSolid(IntPredicate air, IntUnaryOperator change) {
        return new Rule() {
            int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
            boolean done;

            @Override
            public int at(int x, int y, int z, int current) {
                if (x != lastX || z != lastZ) {
                    lastX = x;
                    lastZ = z;
                    done = false;
                }
                if (done || air.test(current)) return KEEP;
                done = true;
                int t = change.applyAsInt(current);
                return t == current ? KEEP : t;
            }
        };
    }
}
