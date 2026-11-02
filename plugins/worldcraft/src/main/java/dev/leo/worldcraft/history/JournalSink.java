package dev.leo.worldcraft.history;

import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.edit.EditJob;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Lleva los cambios de una operación a su diario (plan 025 §4). Junta la tanda en el hilo principal (solo números a
 * texto, que la tabla ya tiene) y la escribe en el hilo de E/S, en orden, en el mismo tick en que se ponen los bloques.
 */
public final class JournalSink implements EditJob.Sink, dev.leo.worldcraft.entity.EntitySink {
    private final Path file;
    private final StateTable table;
    private final Executor io;
    private final Consumer<IOException> onError;
    private List<Journal.Change> batch = new ArrayList<>();
    private List<Journal.EntityChange> entities = new ArrayList<>();
    private int added, removed;
    private Journal journal;
    private boolean failed;

    public JournalSink(Path file, StateTable table, Executor io, Consumer<IOException> onError) {
        this.file = file;
        this.table = table;
        this.io = io;
        this.onError = onError;
    }

    @Override
    public void changed(int x, int y, int z, int before, int after, byte[] tileBefore, byte[] tileAfter) {
        batch.add(new Journal.Change(x, y, z, table.state(before), table.state(after), tileBefore, tileAfter));
    }

    @Override
    public void removed(java.util.UUID id, dev.leo.worldcraft.entity.EntityCopy copy) {
        entities.add(new Journal.EntityChange(true, id, copy));
        removed++;
    }

    @Override
    public void added(java.util.UUID id, dev.leo.worldcraft.entity.EntityCopy copy) {
        entities.add(new Journal.EntityChange(false, id, copy));
        added++;
    }

    /** Spec 031: entidades que la obra creó y quitó (para el mensaje). */
    public int added() {
        return added;
    }

    public int removed() {
        return removed;
    }

    @Override
    public void flush() {
        if (batch.isEmpty() && entities.isEmpty()) return;
        List<Journal.Change> b = batch;
        List<Journal.EntityChange> en = entities;
        batch = new ArrayList<>();
        entities = new ArrayList<>();
        io.execute(() -> {
            if (failed) return;
            try {
                if (journal == null) journal = Journal.create(file);
                journal.append(b);
                if (!en.isEmpty()) journal.appendEntities(en);
            } catch (IOException e) {
                failed = true;
                onError.accept(e);
            }
        });
    }

    @Override
    public void close(boolean complete) {
        flush();
        io.execute(() -> {
            try {
                if (journal == null && !failed) journal = Journal.create(file); // un diario vacío también existe
                if (journal != null) journal.close();
            } catch (IOException e) {
                onError.accept(e);
            }
        });
    }
}
