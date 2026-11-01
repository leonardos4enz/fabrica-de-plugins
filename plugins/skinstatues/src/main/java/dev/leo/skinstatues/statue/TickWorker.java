package dev.leo.skinstatues.statue;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * Cola de trabajos en el hilo principal (H7): un trabajo a la vez, con un tope de bloques y de milisegundos por tick.
 * La tarea solo existe mientras hay trabajo (constitución III.1).
 */
public final class TickWorker {
    public static final int MAX_QUEUE = 20;

    public interface Job {
        /** Avanza; devuelve true cuando terminó. */
        boolean step(long deadlineNanos, int maxBlocks);

        /** Se detiene sin terminar (cancelar o apagar el servidor). */
        void cancel();

        int statueId();

        UUID requester();

        /** Se llama una vez al pasar a ser el trabajo actual. */
        default void start() {
        }
    }

    private final Plugin plugin;
    private final IntSupplier blocksPerTick, msPerTick;
    private final Deque<Job> queue = new ArrayDeque<>();
    private Job current;
    private BukkitTask task;

    public TickWorker(Plugin plugin, IntSupplier blocksPerTick, IntSupplier msPerTick) {
        this.plugin = plugin;
        this.blocksPerTick = blocksPerTick;
        this.msPerTick = msPerTick;
    }

    public boolean full() {
        return queue.size() >= MAX_QUEUE;
    }

    public int waiting() {
        return queue.size();
    }

    public void submit(Job job) {
        queue.add(job);
        if (task == null) task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        long deadline = System.nanoTime() + msPerTick.getAsInt() * 1_000_000L;
        int budget = blocksPerTick.getAsInt();
        while (true) {
            if (current == null) {
                current = queue.poll();
                if (current == null) {
                    task.cancel();
                    task = null;
                    return;
                }
                current.start();
            }
            boolean done;
            try {
                done = current.step(deadline, budget);
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Statue #" + current.statueId() + " stopped by an error: " + e);
                current.cancel();
                done = true;
            }
            if (!done) return;
            current = null;
            if (System.nanoTime() >= deadline) return;
        }
    }

    /** El trabajo actual o en espera de esa estatua, o null. */
    public Job find(int statueId) {
        if (current != null && current.statueId() == statueId) return current;
        for (Job j : queue) if (j.statueId() == statueId) return j;
        return null;
    }

    /** Cancela el trabajo de esa estatua (actual o en espera). */
    public boolean cancel(int statueId) {
        Job j = find(statueId);
        if (j == null) return false;
        if (j == current) current = null;
        else queue.remove(j);
        j.cancel();
        return true;
    }

    /** Los trabajos de un jugador, el actual primero. */
    public List<Job> of(UUID requester) {
        List<Job> out = new ArrayList<>();
        if (current != null && current.requester().equals(requester)) out.add(current);
        for (Job j : queue) if (j.requester().equals(requester)) out.add(j);
        return out;
    }

    /** Al apagar: todo lo pendiente queda cancelado (y deshacible). */
    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        if (current != null) current.cancel();
        current = null;
        for (Job j : queue) j.cancel();
        queue.clear();
    }
}
