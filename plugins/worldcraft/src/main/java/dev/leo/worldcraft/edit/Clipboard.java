package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;

import java.util.HashMap;
import java.util.Map;

/**
 * Lo copiado (H3 CA2): los estados de toda la caja, con el aire, y los datos de cofres y carteles. Las posiciones son
 * relativas a donde estaba el jugador al copiar (como en los editores conocidos: se pega relativo a uno mismo). Vive en
 * memoria, uno por jugador.
 */
public final class Clipboard {
    private final int w, h, d;
    /** Desde el origen (el jugador al copiar) hasta la esquina mínima de la caja. */
    private final int offX, offY, offZ;
    private final int[] states;
    private final Map<Integer, byte[]> tiles = new HashMap<>();
    private Transform transform = Transform.IDENTITY;

    public Clipboard(Box box, int originX, int originY, int originZ) {
        long v = box.volume();
        if (v > Integer.MAX_VALUE - 8) throw new IllegalArgumentException("too big");
        w = box.width();
        h = box.height();
        d = box.depth();
        offX = box.minX() - originX;
        offY = box.minY() - originY;
        offZ = box.minZ() - originZ;
        states = new int[(int) v];
        java.util.Arrays.fill(states, -1);
    }

    private int index(int rx, int ry, int rz) {
        return (ry * d + rz) * w + rx;
    }

    /** Guarda lo que hay en una posición del mundo (con la caja original de la copia). */
    public void put(Box box, int x, int y, int z, int state, byte[] tile) {
        int i = index(x - box.minX(), y - box.minY(), z - box.minZ());
        states[i] = state;
        if (tile != null) tiles.put(i, tile);
    }

    /** El estado en esa posición relativa al origen, sin transformar; -1 si cae fuera de la caja. */
    int at(int vx, int vy, int vz) {
        int rx = vx - offX, ry = vy - offY, rz = vz - offZ;
        if (rx < 0 || ry < 0 || rz < 0 || rx >= w || ry >= h || rz >= d) return -1;
        return states[index(rx, ry, rz)];
    }

    byte[] tileAt(int vx, int vy, int vz) {
        int rx = vx - offX, ry = vy - offY, rz = vz - offZ;
        if (rx < 0 || ry < 0 || rz < 0 || rx >= w || ry >= h || rz >= d) return null;
        return tiles.get(index(rx, ry, rz));
    }

    /** Los estados distintos que tiene (para revisar los bloques prohibidos antes de pegar). */
    public java.util.Set<Integer> distinct() {
        java.util.Set<Integer> out = new java.util.HashSet<>();
        for (int s : states) if (s >= 0) out.add(s);
        return out;
    }

    public long volume() {
        return states.length;
    }

    public String size() {
        int[] o = new int[3];
        transform.apply(w, h, d, o);
        return Math.abs(o[0]) + "×" + Math.abs(o[1]) + "×" + Math.abs(o[2]);
    }

    public Transform transform() {
        return transform;
    }

    public void transform(Transform t) {
        this.transform = t;
    }

    /** La caja que ocupa al pegarla con el jugador en (px, py, pz), ya transformada. */
    public Box target(int px, int py, int pz) {
        int[] a = new int[3], b = new int[3];
        transform.apply(offX, offY, offZ, a);
        transform.apply(offX + w - 1, offY + h - 1, offZ + d - 1, b);
        return Box.of(px + a[0], py + a[1], pz + a[2], px + b[0], py + b[1], pz + b[2]);
    }
}
