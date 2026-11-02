package dev.leo.worldcraft.history;

import dev.leo.worldcraft.edit.EditJob;
import dev.leo.worldcraft.entity.EntitySink;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Lo que anota un deshacer o rehacer (plan 031 §1.4): los bloques no (el diario ya los tiene), los uuid nuevos de las
 * entidades recreadas sí, al final del mismo diario y en el hilo de E/S.
 */
public final class RemapSink implements EditJob.Sink, EntitySink {
    private final Path file;
    private final Executor io;
    private final Consumer<IOException> onError;
    private List<UUID[]> batch = new ArrayList<>();

    public RemapSink(Path file, Executor io, Consumer<IOException> onError) {
        this.file = file;
        this.io = io;
        this.onError = onError;
    }

    @Override
    public void changed(int x, int y, int z, int before, int after, byte[] tileBefore, byte[] tileAfter) {
    }

    @Override
    public void remapped(UUID old, UUID now) {
        batch.add(new UUID[]{old, now});
    }

    @Override
    public void flush() {
        if (batch.isEmpty()) return;
        List<UUID[]> b = batch;
        batch = new ArrayList<>();
        io.execute(() -> {
            try (Journal j = Journal.reopen(file)) {
                for (UUID[] p : b) j.appendRemap(p[0], p[1]);
            } catch (IOException e) {
                onError.accept(e);
            }
        });
    }

    @Override
    public void close(boolean complete) {
        flush();
    }
}
