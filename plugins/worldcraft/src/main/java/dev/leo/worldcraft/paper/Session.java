package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.region.Box;

/** Lo de cada jugador (o la consola) mientras está conectado: selección, portapapeles y la operación por confirmar. */
public final class Session {
    private String world;
    private int[] pos1, pos2;
    private Clipboard clipboard;
    private String clipboardWorld;
    private boolean show = true;
    Edits.Pending pending;

    /** La caja seleccionada o null si falta una esquina. */
    public Box box() {
        if (pos1 == null || pos2 == null) return null;
        return Box.of(pos1[0], pos1[1], pos1[2], pos2[0], pos2[1], pos2[2]);
    }

    public String world() {
        return world;
    }

    public int[] corner1() {
        return pos1 == null ? null : pos1.clone();
    }

    public int[] corner2() {
        return pos2 == null ? null : pos2.clone();
    }

    /** Pone una esquina; si es de otro mundo, la otra se olvida (H1 CA5). */
    public void corner(int which, String worldName, int x, int y, int z) {
        if (world != null && !world.equals(worldName)) {
            pos1 = null;
            pos2 = null;
        }
        world = worldName;
        if (which == 1) pos1 = new int[]{x, y, z};
        else pos2 = new int[]{x, y, z};
    }

    public void selectBox(String worldName, Box b) {
        world = worldName;
        pos1 = new int[]{b.minX(), b.minY(), b.minZ()};
        pos2 = new int[]{b.maxX(), b.maxY(), b.maxZ()};
    }

    public void clearSelection() {
        pos1 = null;
        pos2 = null;
    }

    public boolean show() {
        return show;
    }

    public void show(boolean v) {
        show = v;
    }

    public Clipboard clipboard() {
        return clipboard;
    }

    public String clipboardWorld() {
        return clipboardWorld;
    }

    public void clipboard(Clipboard c, String worldName) {
        clipboard = c;
        clipboardWorld = worldName;
    }
}
