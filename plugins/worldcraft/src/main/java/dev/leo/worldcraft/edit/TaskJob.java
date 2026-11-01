package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;

import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

/**
 * Pasos sueltos dentro del presupuesto por tick del {@link Scheduler} (spec 027 plan §1.2): por ejemplo, convertir los
 * datos de miles de cofres con la API de Paper sin trabar un tick. Mira el reloj en cada paso (lección 025 F-01). No
 * tiene caja: nunca espera a otra obra ni la hace esperar.
 */
public final class TaskJob implements Scheduler.Job {
    private final int id;
    private final UUID owner;
    private final int count;
    private final IntConsumer step;
    private final LongSupplier clock;
    private final Runnable done, onCancel;
    private int next;
    private boolean cancelled, finished;

    /** step(i) para i de 0 a count - 1; done al terminar, onCancel si se cancela antes (con /wc cancel o al apagar). */
    public TaskJob(int id, UUID owner, int count, IntConsumer step, LongSupplier clock, Runnable done, Runnable onCancel) {
        this.id = id;
        this.owner = owner;
        this.count = count;
        this.step = step;
        this.clock = clock;
        this.done = done;
        this.onCancel = onCancel;
    }

    @Override
    public boolean step(long deadlineNanos, int maxBlocks) {
        if (cancelled) return true;
        int n = 0;
        while (next < count) {
            step.accept(next++);
            n++;
            if (n >= maxBlocks || clock.getAsLong() >= deadlineNanos) break;
        }
        if (next >= count && !finished) {
            finished = true;
            done.run();
        }
        return next >= count;
    }

    public int done() {
        return next;
    }

    @Override
    public void cancel() {
        if (cancelled || finished) return;
        cancelled = true;
        onCancel.run();
    }

    public boolean cancelled() {
        return cancelled;
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public UUID owner() {
        return owner;
    }

    @Override
    public String world() {
        return "";
    }

    @Override
    public Box bounds() {
        return null;
    }
}
