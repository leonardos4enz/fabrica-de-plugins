package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.block.StateTable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * drain, fixwater y fixlava (spec 028 H3 CA1) sobre la foto leída antes, sin Bukkit (spec 029 H6: antes vivía dentro de
 * BuildCommands y no tenía prueba). Busca el líquido más cercano dentro de la esfera y lo conectado a él.
 */
public final class Liquids {
    public enum Kind {
        DRAIN, FIXWATER, FIXLAVA
    }

    private final StateTable t;
    private final IntPredicate water, lava, wet;
    private final int air, waterSource, lavaSource;

    /**
     * @param waterSource el estado del agua quieta (en Paper, «minecraft:water[level=0]»)
     * @param lavaSource  el de la lava quieta
     */
    public Liquids(StateTable t, int air, int waterSource, int lavaSource) {
        this.t = t;
        this.air = air;
        this.waterSource = waterSource;
        this.lavaSource = lavaSource;
        this.water = st -> t.material(st).equals("minecraft:water");
        this.lava = st -> t.material(st).equals("minecraft:lava");
        this.wet = st -> "true".equals(t.properties(st).get("waterlogged"));
    }

    /** La esfera de radio r (+ ½, como WorldEdit) alrededor de un punto. */
    public static Flood.Bound sphere(int cx, int cy, int cz, int r) {
        double e = (r + 0.5) * (r + 0.5);
        return (x, y, z) -> (double) (x - cx) * (x - cx) + (double) (y - cy) * (y - cy) + (double) (z - cz) * (z - cz) <= e;
    }

    /**
     * Lo que se va a cambiar: el líquido conectado al más cercano. drain sigue el agua (y lo anegado con waterlogged) o
     * la lava, la que encontró primero; fixwater y fixlava no suben del nivel donde lo encontraron (si no, «arreglar» un
     * río dejaría quieta una cascada de arriba).
     */
    public long[] find(Snapshot snap, Kind kind, int x, int y, int z, int r, boolean waterlogged) {
        Flood.Bound inSphere = sphere(x, y, z, r);
        IntPredicate match = switch (kind) {
            case FIXWATER -> water;
            case FIXLAVA -> lava;
            case DRAIN -> waterlogged ? water.or(lava).or(wet) : water.or(lava);
        };
        int[] start = Flood.nearest(snap, x, y, z, inSphere, match);
        if (start == null) return new long[0];
        IntPredicate through;
        Flood.Bound bound = inSphere;
        if (kind == Kind.DRAIN) {
            int s0 = snap.get(start[0], start[1], start[2]);
            // El agua de la escalera anegada se cuenta como agua; la lava no se mezcla con el agua.
            through = lava.test(s0) ? lava : waterlogged ? water.or(wet) : water;
        } else {
            through = kind == Kind.FIXWATER ? water : lava;
            int top = start[1];
            bound = (bx, by, bz) -> by <= top && inSphere.test(bx, by, bz);
        }
        return Flood.connected(snap, start[0], start[1], start[2], bound, through);
    }

    /** Qué poner en cada punto encontrado. */
    public PointsSource.Pick pick(Kind kind) {
        return switch (kind) {
            case DRAIN -> (x, y, z, c) -> water.test(c) || lava.test(c) ? air
                    : wet.test(c) ? t.intern(StateTable.withProperties(t.material(c), with(t.properties(c), "waterlogged", "false")))
                    : EditSource.KEEP;
            case FIXWATER -> (x, y, z, c) -> water.test(c) && c != waterSource ? waterSource : EditSource.KEEP;
            case FIXLAVA -> (x, y, z, c) -> lava.test(c) && c != lavaSource ? lavaSource : EditSource.KEEP;
        };
    }

    /** El estado que pone (para los bloques prohibidos). */
    public int placed(Kind kind) {
        return kind == Kind.DRAIN ? air : kind == Kind.FIXWATER ? waterSource : lavaSource;
    }

    private static Map<String, String> with(Map<String, String> props, String k, String v) {
        Map<String, String> m = new LinkedHashMap<>(props);
        m.put(k, v);
        return m;
    }
}
