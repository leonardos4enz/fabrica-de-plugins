package dev.leo.blockwright.history;

import dev.leo.blockwright.block.StateTable;
import dev.leo.blockwright.edit.EditJob;

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
public final class JournalSink implements EditJob.Sink {
    private final Path file;
    private final StateTable table;
    private final Executor io;
    private final Consumer<IOException> onError;
    private List<Journal.Change> batch = new ArrayList<>();
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
    public void flush() {
        if (batch.isEmpty()) return;
        List<Journal.Change> b = batch;
        batch = new ArrayList<>();
        io.execute(() -> {
            if (failed) return;
            try {
                if (journal == null) journal = Journal.create(file);
                journal.append(b);
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
