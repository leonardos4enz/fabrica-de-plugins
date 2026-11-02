package dev.leo.worldcraft.entity;

import dev.leo.worldcraft.history.Journal;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Las etapas de entidades de un deshacer o rehacer (plan 031 §1.4). Deshacer: quitar lo que la obra creó ({@code IN}) y
 * recrear lo que quitó ({@code OUT}); rehacer, al revés. Los uuid se siguen por los {@code REMAP} del diario.
 */
public final class UndoEntities {
    private UndoEntities() {
    }

    public static EntityStage.Kill kill(EntityWorld world, CompletableFuture<Journal.Data> data, boolean redo) {
        return new EntityStage.Kill(world, () -> {
            Journal.Data d = loaded(data);
            if (d == null) return null;
            List<Journal.EntityChange> list = redo ? d.entitiesOut : d.entitiesIn;
            return list.stream().map(c -> new EntityStage.Target(d.current(c.id()), c.copy().type())).toList();
        });
    }

    public static EntityStage.Spawn spawn(EntityWorld world, CompletableFuture<Journal.Data> data, boolean redo, EntitySink sink) {
        return new EntityStage.Spawn(world, () -> {
            Journal.Data d = loaded(data);
            if (d == null) return null;
            List<Journal.EntityChange> list = redo ? d.entitiesIn : d.entitiesOut;
            return list.stream().map(c -> new EntityStage.Item(c.id(), c.copy())).toList();
        }, sink);
    }

    private static Journal.Data loaded(CompletableFuture<Journal.Data> data) {
        return data.isDone() && !data.isCompletedExceptionally() ? data.join() : null;
    }
}
