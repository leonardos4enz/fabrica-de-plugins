package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * Reparte el tiempo de cada tick entre las operaciones (H5, plan 025 §3). Un tope de milisegundos y de bloques para
 * todo el plugin, repartido en partes iguales entre los jugadores que tienen algo en marcha (por turnos: una operación
 * enorme no deja esperando a una chica de otro). Cada jugador hace sus operaciones en orden. Dos operaciones cuyas
 * cajas se cruzan en el mismo mundo no corren a la vez: la segunda espera (sin bloques fantasma).
 * No conoce Bukkit: la tarea de Paper llama a {@link #tick()} y la detiene cuando {@link #idle()}.
 */
public final class Scheduler {
    public interface Job {
        /** Avanza hasta el plazo o el tope de bloques; true si terminó. */
        boolean step(long deadlineNanos, int maxBlocks);

        void cancel();

        int id();

        UUID owner();

        String world();

        Box bounds();
    }

    private final LongSupplier clock;
    private final IntSupplier maxMsPerTick, maxBlocksPerTick;
    private final BiConsumer<Job, RuntimeException> onError;
    private final Map<UUID, Deque<Job>> queues = new LinkedHashMap<>();
    private int turn;
    private long lastTickNanos, maxTickNanos;

    public Scheduler(LongSupplier clock, IntSupplier maxMsPerTick, IntSupplier maxBlocksPerTick, BiConsumer<Job, RuntimeException> onError) {
        this.clock = clock;
        this.maxMsPerTick = maxMsPerTick;
        this.maxBlocksPerTick = maxBlocksPerTick;
        this.onError = onError;
    }

    public void submit(Job job) {
        queues.computeIfAbsent(job.owner(), k -> new ArrayDeque<>()).add(job);
    }

    public boolean idle() {
        return queues.isEmpty();
    }

    /** Un tick: cada jugador con trabajo, por turnos, avanza el primero de los suyos. */
    public void tick() {
        long start = clock.getAsLong();
        long deadline = start + maxMsPerTick.getAsInt() * 1_000_000L;
        List<UUID> owners = new ArrayList<>(queues.keySet());
        if (owners.isEmpty()) return;
        int blocks = maxBlocksPerTick.getAsInt();
        List<Job> running = new ArrayList<>();
        int k = owners.size();
        turn = Math.floorMod(turn, k);
        for (int i = 0; i < k; i++) {
            UUID o = owners.get((turn + i) % k);
            Deque<Job> q = queues.get(o);
            Job j = q.peek();
            if (j == null) continue;
            if (blocked(j, running)) continue;
            running.add(j);
            long now = clock.getAsLong();
            if (now >= deadline) break;
            int left = k - i;
            long slice = now + (deadline - now) / left;
            boolean done;
            try {
                done = j.step(slice, Math.max(1, blocks / k));
            } catch (RuntimeException e) {
                j.cancel();
                onError.accept(j, e);
                done = true;
            }
            if (done) {
                q.poll();
                running.remove(j);
                if (q.isEmpty()) queues.remove(o);
            }
        }
        turn++;
        lastTickNanos = clock.getAsLong() - start;
        maxTickNanos = Math.max(maxTickNanos, lastTickNanos);
    }

    /** Se cruza con una operación que va antes en este tick o con la primera de otro jugador que ya empezó. */
    private boolean blocked(Job j, List<Job> running) {
        for (Job r : running) if (overlaps(j, r)) return true;
        for (Deque<Job> q : queues.values()) {
            Job other = q.peek();
            if (other == null || other == j || other.owner().equals(j.owner())) continue;
            if (other.id() < j.id() && overlaps(j, other)) return true;
        }
        return false;
    }

    private static boolean overlaps(Job a, Job b) {
        if (!a.world().equals(b.world())) return false;
        Box x = a.bounds(), y = b.bounds();
        if (x == null || y == null) return false;
        return x.minX() <= y.maxX() && y.minX() <= x.maxX() && x.minY() <= y.maxY() && y.minY() <= x.maxY()
                && x.minZ() <= y.maxZ() && y.minZ() <= x.maxZ();
    }

    /** Las operaciones de un jugador (la que corre primero). */
    public List<Job> of(UUID owner) {
        Deque<Job> q = queues.get(owner);
        return q == null ? List.of() : List.copyOf(q);
    }

    public List<Job> all() {
        List<Job> out = new ArrayList<>();
        for (Deque<Job> q : queues.values()) out.addAll(q);
        return out;
    }

    /** Cancela esa operación (en marcha o en espera). */
    public boolean cancel(int jobId) {
        for (Iterator<Map.Entry<UUID, Deque<Job>>> it = queues.entrySet().iterator(); it.hasNext(); ) {
            Deque<Job> q = it.next().getValue();
            for (Job j : q) {
                if (j.id() == jobId) {
                    q.remove(j);
                    if (q.isEmpty()) it.remove();
                    j.cancel();
                    return true;
                }
            }
        }
        return false;
    }

    /** Al apagar: todo queda cancelado (lo hecho, deshacible). */
    public void shutdown() {
        for (Deque<Job> q : queues.values()) for (Job j : q) j.cancel();
        queues.clear();
    }

    public long lastTickNanos() {
        return lastTickNanos;
    }

    public long maxTickNanos() {
        return maxTickNanos;
    }
}
