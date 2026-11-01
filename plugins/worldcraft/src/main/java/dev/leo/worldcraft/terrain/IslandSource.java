package dev.leo.worldcraft.terrain;

import java.util.Map;

/**
 * Lleva una isla al mundo (H2): de la base a la cima con las capas del estilo (profundidad desde la cima), la raíz bajo
 * la columna y los árboles. Solo pone donde hay aire, plantas o agua (H2 CA3); con {@code skin}, la cara de arriba, la
 * de abajo, los bordes, las raíces y los árboles.
 */
public final class IslandSource extends ColumnSource {
    private final IslandShape.Plan plan;
    private final Style.Painter painter;
    private final Masks masks;
    private final double span;

    public IslandSource(IslandShape.Plan plan, Style.Painter painter, Masks masks, boolean skin) {
        super(plan.rect(), skin);
        this.plan = plan;
        this.painter = painter;
        this.masks = masks;
        this.span = Math.max(1, plan.maxY() - plan.minY());
        int[] treeLo = new int[lo.length], treeHi = new int[lo.length];
        java.util.Arrays.fill(treeLo, Integer.MAX_VALUE);
        java.util.Arrays.fill(treeHi, Integer.MIN_VALUE);
        if (painter.log != null) {
            for (Long k : plan.trees().keySet()) {
                int x = (int) (k >> 38), z = (int) (k << 26 >> 38), y = (int) (k << 52 >> 52);
                if (x >= x0 + width || z >= z0 + depth || x < x0 || z < z0) continue;
                int i = index(x, z);
                treeLo[i] = Math.min(treeLo[i], y);
                treeHi[i] = Math.max(treeHi[i], y);
            }
        }
        for (int i = 0; i < lo.length; i++) {
            int a = Integer.MAX_VALUE, b = Integer.MIN_VALUE;
            if (plan.top()[i] != HeightField.NONE) {
                a = plan.bottom()[i];
                b = plan.top()[i];
                if (plan.root()[i] != HeightField.NONE && painter.hanging != null) a = Math.min(a, plan.root()[i]);
            }
            a = Math.min(a, treeLo[i]);
            b = Math.max(b, treeHi[i]);
            if (a <= b) {
                lo[i] = a;
                hi[i] = b;
            }
        }
    }

    @Override
    protected int cell(int i, int x, int y, int z, int current) {
        if (!masks.open(current)) return KEEP;
        int top = plan.top()[i];
        if (top != HeightField.NONE && y <= top && y >= plan.bottom()[i]) {
            return painter.pick(x, y, z, top - y, (y - plan.minY()) / span, plan.slope()[i]);
        }
        if (top != HeightField.NONE && y == plan.root()[i] && painter.hanging != null) {
            return painter.hanging.apply(x, y, z, painter.seed());
        }
        Byte t = painter.log == null ? null : plan.trees().get(IslandShape.key(x, y, z));
        if (t == null) return KEEP;
        return (t == IslandShape.LOG ? painter.log : painter.leaves).apply(x, y, z, painter.seed());
    }

    @Override
    protected int solidTop(int i) {
        return plan.top()[i];
    }

    @Override
    protected int solidBottom(int i) {
        return plan.bottom()[i];
    }

    /** Los bloques de árbol (para contarlos en pruebas). */
    public Map<Long, Byte> trees() {
        return plan.trees();
    }
}
