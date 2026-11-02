package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;

import java.util.HashMap;
import java.util.Map;

/**
 * Lo que había en una caja (plan 028 §1.2): la foto que lee un trabajo de lectura antes de calcular stack, move, hollow,
 * drain o fill. Estados en el orden (y·fondo + z)·ancho + x; -1 = sin leer.
 */
public final class Snapshot implements EditJob.Capture {
    public final Box box;
    private final int w, d;
    private final int[] states;
    private final Map<Integer, byte[]> tiles = new HashMap<>();

    public Snapshot(Box box) {
        if (box.volume() > Integer.MAX_VALUE - 8) throw new IllegalArgumentException("too big");
        this.box = box;
        this.w = box.width();
        this.d = box.depth();
        this.states = new int[(int) box.volume()];
        java.util.Arrays.fill(states, -1);
    }

    public int index(int x, int y, int z) {
        return ((y - box.minY()) * d + (z - box.minZ())) * w + (x - box.minX());
    }

    /** El estado en esa posición del mundo; -1 si cae fuera de la caja. */
    public int get(int x, int y, int z) {
        if (!box.contains(x, y, z)) return -1;
        return states[index(x, y, z)];
    }

    public byte[] tile(int x, int y, int z) {
        if (!box.contains(x, y, z)) return null;
        return tiles.get(index(x, y, z));
    }

    /** Para las pruebas: poner un estado a mano. */
    public void put(int x, int y, int z, int state) {
        states[index(x, y, z)] = state;
    }

    @Override
    public void take(int x, int y, int z, int state, byte[] tile) {
        int i = index(x, y, z);
        states[i] = state;
        if (tile != null) tiles.put(i, tile);
    }

    /** Los estados distintos (para revisar los bloques prohibidos). */
    public java.util.Set<Integer> distinct() {
        java.util.Set<Integer> out = new java.util.HashSet<>();
        for (int s : states) if (s >= 0) out.add(s);
        return out;
    }
}
