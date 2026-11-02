package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.region.Box;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spec 029 (count, distr, trim): cuenta lo que se lee, sin guardar la foto. Cada bloque que pasa la máscara suma a la
 * cuenta, a la caja que los abarca (trim) y a su material o estado (distr). Sin Bukkit: se prueba en unitarias.
 */
public final class Census implements EditJob.Capture {
    /** Un renglón de distr: el nombre, cuántos y su parte del total (0–100). */
    public record Row(String name, long count, double percent) {
    }

    private final Mask.Compiled mask;
    private final Mask.Neighbors near;
    private final long seed;
    private final boolean byState;
    private final StateTable table;
    private final Map<Integer, long[]> perState = new HashMap<>();
    private long total;
    private int ax = Integer.MAX_VALUE, ay = Integer.MAX_VALUE, az = Integer.MAX_VALUE;
    private int bx = Integer.MIN_VALUE, by = Integer.MIN_VALUE, bz = Integer.MIN_VALUE;

    /**
     * @param mask    null = todo
     * @param near    la foto alrededor si la máscara mira vecinos (o null)
     * @param byState distr separado por estado (--states) en lugar de por material
     */
    public Census(Mask.Compiled mask, Mask.Neighbors near, long seed, boolean byState, StateTable table) {
        this.mask = mask;
        this.near = near;
        this.seed = seed;
        this.byState = byState;
        this.table = table;
    }

    @Override
    public void take(int x, int y, int z, int state, byte[] tile) {
        if (state < 0) return;
        if (mask != null && !mask.test(x, y, z, state, near, seed)) return;
        total++;
        perState.computeIfAbsent(state, k -> new long[1])[0]++;
        if (x < ax) ax = x;
        if (y < ay) ay = y;
        if (z < az) az = z;
        if (x > bx) bx = x;
        if (y > by) by = y;
        if (z > bz) bz = z;
    }

    /** Recorre una caja de una foto ya leída (cuando la máscara mira vecinos hubo que leer la caja + 1). */
    public Census over(Snapshot snap, Box box) {
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) take(x, y, z, snap.get(x, y, z), null);
            }
        }
        return this;
    }

    /** Recorre un portapapeles (distr --clipboard): sus estados en el orden de {@link Clipboard#rawStates()}. */
    public Census over(Clipboard clip) {
        int[] st = clip.rawStates();
        int w = clip.width(), d = clip.depth();
        for (int i = 0; i < st.length; i++) take(i % w, i / (w * d), (i / w) % d, st[i], null);
        return this;
    }

    public long total() {
        return total;
    }

    /** La caja más chica con todo lo que pasó la máscara (trim); null si no pasó nada. */
    public Box bounds() {
        return total == 0 ? null : new Box(ax, ay, az, bx, by, bz);
    }

    /** Los n más comunes (por material o por estado) con su porcentaje; empates por nombre. */
    public List<Row> top(int n) {
        Map<String, Long> grouped = new HashMap<>();
        for (Map.Entry<Integer, long[]> e : perState.entrySet()) {
            String name = byState ? table.state(e.getKey()) : table.material(e.getKey());
            grouped.merge(name.replace("minecraft:", ""), e.getValue()[0], Long::sum);
        }
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : grouped.entrySet()) rows.add(new Row(e.getKey(), e.getValue(), total == 0 ? 0 : 100.0 * e.getValue() / total));
        rows.sort((a, b) -> a.count() != b.count() ? Long.compare(b.count(), a.count()) : a.name().compareTo(b.name()));
        return rows.size() > n ? rows.subList(0, n) : rows;
    }

    /** Cuántos grupos distintos hay (para «… and N more»). */
    public int groups() {
        if (!byState) {
            java.util.Set<String> m = new java.util.HashSet<>();
            for (int s : perState.keySet()) m.add(table.material(s));
            return m.size();
        }
        return perState.size();
    }
}
