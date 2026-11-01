package dev.leo.blockwright.edit;

import dev.leo.blockwright.block.StateTable;
import dev.leo.blockwright.history.Journal;
import dev.leo.blockwright.region.Box;

import java.util.concurrent.CompletableFuture;

/**
 * Deshacer y rehacer desde el diario (H6). Seguro: solo se devuelve un bloque si sigue como lo dejó la operación (al
 * deshacer) o como estaba antes (al rehacer); lo que otro cambió después se deja y se cuenta (H6 CA3).
 */
public final class UndoSource implements EditSource {
    private final CompletableFuture<Journal.Data> loading;
    private final StateTable table;
    private final boolean redo;
    private final Box bounds;
    private Journal.Data data;
    private int[] ids;
    private int i = -1;
    private long skipped;
    private byte[] tile;

    /** El diario se lee en otro hilo (constitución III.2); bounds es la caja guardada en el historial. */
    public UndoSource(CompletableFuture<Journal.Data> loading, StateTable table, boolean redo, Box bounds) {
        this.loading = loading;
        this.table = table;
        this.redo = redo;
        this.bounds = bounds;
    }

    @Override
    public boolean ready() {
        if (data != null) return true;
        if (!loading.isDone()) return false;
        data = loading.join(); // un error de lectura sale como excepción y el planificador cancela el trabajo
        ids = new int[data.dict.size()];
        for (int k = 0; k < ids.length; k++) ids[k] = table.intern(data.dict.get(k));
        return true;
    }

    @Override
    public boolean next(int[] p) {
        if (++i >= data.size) return false;
        p[0] = data.x[i];
        p[1] = data.y[i];
        p[2] = data.z[i];
        return true;
    }

    @Override
    public int target(int x, int y, int z, int current) {
        int expect = ids[redo ? data.before[i] : data.after[i]];
        int put = ids[redo ? data.after[i] : data.before[i]];
        tile = null;
        if (current != expect) {
            skipped++;
            return KEEP;
        }
        tile = (redo ? data.tileAfter : data.tileBefore).get(i);
        return put;
    }

    @Override
    public byte[] tile() {
        return tile;
    }

    @Override
    public long volume() {
        return data == null ? 0 : data.size;
    }

    @Override
    public Box bounds() {
        return bounds;
    }

    @Override
    public long skipped() {
        return skipped;
    }
}
