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
    /** Spec 029 (paste --origin): dónde estaba quien copió; null si vino de un esquema. */
    private int[] source;
    /** Spec 031: las entidades copiadas (con --entities o de un esquema), relativas a quien copió y sin transformar. */
    private java.util.List<dev.leo.worldcraft.entity.EntityCopy> entities = java.util.List.of();

    public Clipboard(Box box, int originX, int originY, int originZ) {
        this.source = new int[]{originX, originY, originZ};
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

    private Clipboard(int w, int h, int d, int offX, int offY, int offZ, int[] states, Map<Integer, byte[]> tiles) {
        if (states.length != (long) w * h * d) throw new IllegalArgumentException("size");
        this.w = w;
        this.h = h;
        this.d = d;
        this.offX = offX;
        this.offY = offY;
        this.offZ = offZ;
        this.states = states;
        this.tiles.putAll(tiles);
    }

    /**
     * Un portapapeles hecho de un esquema (spec 027): states en el orden (y·d + z)·w + x (el mismo del formato Sponge),
     * off la esquina mínima respecto de quien pega. Se puede armar fuera del hilo principal.
     */
    public static Clipboard of(int w, int h, int d, int offX, int offY, int offZ, int[] states, Map<Integer, byte[]> tiles) {
        return new Clipboard(w, h, d, offX, offY, offZ, states, tiles);
    }

    /** Spec 029: el lugar donde se copió (para pegar ahí mismo) o null si vino de un esquema. */
    public int[] source() {
        return source == null ? null : source.clone();
    }

    /**
     * Una copia con el giro y el volteo ya aplicados y sin transformación (spec 027 H1 CA1: se guarda girado). turn da
     * el estado girado de cada estado (calculado antes en el hilo principal); no toca Bukkit.
     */
    public Clipboard baked(java.util.function.IntUnaryOperator turn) {
        if (transform.steps().isEmpty()) return this;
        Box t = target(0, 0, 0);
        int nw = t.width(), nh = t.height(), nd = t.depth();
        int[] out = new int[states.length];
        Map<Integer, byte[]> nt = new HashMap<>();
        int[] v = new int[3];
        int i = 0;
        for (int y = 0; y < nh; y++) {
            for (int z = 0; z < nd; z++) {
                for (int x = 0; x < nw; x++, i++) {
                    transform.inverse(t.minX() + x, t.minY() + y, t.minZ() + z, v);
                    int s = at(v[0], v[1], v[2]);
                    out[i] = s < 0 ? s : turn.applyAsInt(s);
                    byte[] tile = tileAt(v[0], v[1], v[2]);
                    if (tile != null) nt.put(i, tile);
                }
            }
        }
        Clipboard c = new Clipboard(nw, nh, nd, t.minX(), t.minY(), t.minZ(), out, nt);
        c.entities = entities.stream().map(e -> e.transformed(transform)).toList();
        return c;
    }

    /** Spec 031: las entidades sin transformar, relativas a quien copió. */
    public java.util.List<dev.leo.worldcraft.entity.EntityCopy> entities() {
        return entities;
    }

    public void entities(java.util.List<dev.leo.worldcraft.entity.EntityCopy> list) {
        this.entities = java.util.List.copyOf(list);
    }

    /** Spec 031: las entidades donde quedan al pegar con el jugador en (px, py, pz), ya giradas. */
    public java.util.List<dev.leo.worldcraft.entity.EntityCopy> entitiesAt(int px, int py, int pz) {
        return entities.stream().map(e -> e.transformed(transform).shifted(px, py, pz)).toList();
    }

    /** Spec 031: los tipos que llevan sus entidades (para los prohibidos). */
    public java.util.Set<String> entityTypes() {
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (var e : entities) out.addAll(e.contains());
        return out;
    }

    public int width() {
        return w;
    }

    public int height() {
        return h;
    }

    public int depth() {
        return d;
    }

    /** La esquina mínima respecto de quien copió (sin transformar): {x, y, z}. */
    public int[] offset() {
        return new int[]{offX, offY, offZ};
    }

    /** Los estados sin transformar, en el orden (y·d + z)·w + x (-1 = sin leer). No se cambian. */
    public int[] rawStates() {
        return states;
    }

    /** Los datos de bloque por índice, sin transformar. No se cambian. */
    public Map<Integer, byte[]> rawTiles() {
        return java.util.Collections.unmodifiableMap(tiles);
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
