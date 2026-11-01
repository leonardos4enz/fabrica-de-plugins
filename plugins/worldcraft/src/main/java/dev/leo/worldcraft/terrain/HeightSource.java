package dev.leo.worldcraft.terrain;

/**
 * Lleva al mundo un mapa de alturas nuevo (plan 026 §1.2).
 * <ul>
 * <li>{@link Mode#GENERATE} (montaña): llena de la vieja superficie a la nueva con el estilo, solo donde hay aire,
 * plantas, agua o árboles.</li>
 * <li>{@link Mode#PRESERVE} (pincel): sube o baja conservando los materiales de la columna; al bajar quita también las
 * plantas de encima.</li>
 * <li>{@link Mode#PAINT} (pincel): cambia las capas de arriba donde hay suelo.</li>
 * </ul>
 * Las columnas con {@code newTop} NONE no se tocan (obstáculos, sin suelo, fuera del pincel).
 */
public final class HeightSource extends ColumnSource {
    public enum Mode { GENERATE, PRESERVE, PAINT }

    private final HeightField field;
    private final int[] newTop;
    private final Mode mode;
    private final Style.Painter painter;
    private final float[] slope;
    private final int base;
    private final double height;
    private final Masks masks;
    private final int paintDepth, air;

    /**
     * @param newTop la cima nueva de cada columna del campo (NONE = no tocar); en PAINT, la altura que se pinta
     * @param slope  pendiente de cada columna (para el estilo)
     * @param air    el estado de aire (lo que queda al bajar)
     * @param base   el pie (altura relativa 0) y {@code height} lo que mide hasta la cima (altura relativa 1)
     */
    public HeightSource(HeightField field, int[] newTop, Mode mode, Style.Painter painter, float[] slope, int base,
                        double height, Masks masks, int paintDepth, int air, boolean skin) {
        super(field.rect(), skin);
        this.field = field;
        this.newTop = newTop;
        this.mode = mode;
        this.painter = painter;
        this.slope = slope;
        this.base = base;
        this.height = Math.max(1, height);
        this.masks = masks;
        this.paintDepth = Math.max(1, paintDepth);
        this.air = air;
        for (int i = 0; i < newTop.length; i++) {
            int t = newTop[i], g = field.ground(i);
            if (t == HeightField.NONE) continue;
            switch (mode) {
                case GENERATE -> {
                    int from = g == HeightField.NONE ? field.bottom : g + 1;
                    if (t >= from) {
                        lo[i] = from;
                        hi[i] = t;
                    }
                }
                case PRESERVE -> {
                    if (g == HeightField.NONE || t == g) continue;
                    if (t > g) {
                        lo[i] = g;
                        hi[i] = t;
                    } else {
                        lo[i] = t - 1;
                        hi[i] = Math.max(g, field.cover(i));
                    }
                }
                case PAINT -> {
                    if (g == HeightField.NONE) continue;
                    lo[i] = g - this.paintDepth + 1;
                    hi[i] = g;
                }
            }
        }
    }

    @Override
    protected int cell(int i, int x, int y, int z, int current) {
        int t = newTop[i], g = field.ground(i);
        return switch (mode) {
            case GENERATE -> masks.fillable(current)
                    ? painter.pick(x, y, z, t - y, (y - base) / height, slope[i]) : KEEP;
            case PAINT -> masks.ground(current)
                    ? painter.pick(x, y, z, g - y, (y - base) / height, slope[i]) : KEEP;
            case PRESERVE -> t > g ? raise(i, y, t, g, current) : lower(i, y, t, g, current);
        };
    }

    private int raise(int i, int y, int t, int g, int current) {
        if (y == g) return masks.ground(current) ? field.under(i) : KEEP; // la vieja superficie queda enterrada
        if (!masks.open(current)) return KEEP;
        return y == t ? field.surface(i) : field.under(i);
    }

    private int lower(int i, int y, int t, int g, int current) {
        if (y > g) return masks.cover().test(current) && !masks.air().test(current) ? air : KEEP;
        if (y > t) return masks.ground(current) || masks.cover().test(current) ? air : KEEP;
        if (!masks.ground(current)) return KEEP;
        return y == t ? field.surface(i) : field.under(i);
    }

    @Override
    protected int solidTop(int i) {
        int t = newTop[i];
        if (t != HeightField.NONE && mode != Mode.PAINT) return t;
        return field.ground(i);
    }

}
