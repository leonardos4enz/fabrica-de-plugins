package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.ChunkOrder;

import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Una operación en marcha (H5): avanza por tandas dentro del tiempo que le da el {@link Scheduler}, chunk por chunk, y
 * espera sin bloquear si el chunk no está cargado. Las tres formas: poner (con diario), vista previa (bloques falsos para
 * un jugador) y leer (copiar).
 */
public final class EditJob implements Scheduler.Job {
    public enum Mode { APPLY, PREVIEW, READ }

    public enum State { WAITING, RUNNING, DONE, CANCELLED }

    /** A dónde va cada cambio: el diario (poner) o la vista previa. */
    public interface Sink {
        void changed(int x, int y, int z, int before, int after, byte[] tileBefore, byte[] tileAfter);

        /** Fin de una tanda (el diario la manda al disco, la vista previa al jugador). */
        default void flush() {
        }

        /** Fin del trabajo; complete = false si se canceló o falló. */
        default void close(boolean complete) {
        }
    }

    /** Lo que se copia al portapapeles al pasar (copy, cut). */
    public interface Capture {
        void take(int x, int y, int z, int state, byte[] tile);
    }

    private static final long NO_CHUNK = Long.MIN_VALUE;

    private final int id;
    private final UUID owner;
    private final String label;
    private final EditWorld world;
    private final EditSource source;
    private final Mode mode;
    private final Sink sink;
    private final Capture capture;
    private final long maxChanges;
    private final LongSupplier clock;
    private final int[] p = new int[3];
    private boolean pending;
    private long chunk = NO_CHUNK;
    private long visited, changed, nanos, maxStepNanos;
    private boolean truncated;
    private State state = State.WAITING;
    /** Spec 031: lo que se hace con las entidades antes de los bloques (quitar) y después (crear); o null. */
    private dev.leo.worldcraft.entity.EntityStage before, after;
    private boolean beforeDone, blocksDone;
    private Runnable onEnd = () -> {
    };

    /**
     * @param maxChanges tope de cambios (la vista previa se corta ahí y lo dice); Long.MAX_VALUE sin tope
     */
    public EditJob(int id, UUID owner, String label, EditWorld world, EditSource source, Mode mode, Sink sink,
                   Capture capture, long maxChanges, LongSupplier clock) {
        this.id = id;
        this.owner = owner;
        this.label = label;
        this.world = world;
        this.source = source;
        this.mode = mode;
        this.sink = sink;
        this.capture = capture;
        this.maxChanges = maxChanges;
        this.clock = clock;
    }

    /**
     * Spec 031 (plan §1.3): las etapas de entidades. Lo que se quita va antes de tocar los bloques (un marco sin pared se
     * cae) y lo que se crea, después (ya tiene pared). La vista previa no las usa.
     */
    public EditJob stages(dev.leo.worldcraft.entity.EntityStage before, dev.leo.worldcraft.entity.EntityStage after) {
        this.before = before;
        this.after = after;
        return this;
    }

    public dev.leo.worldcraft.entity.EntityStage before() {
        return before;
    }

    public dev.leo.worldcraft.entity.EntityStage after() {
        return after;
    }

    /** Se llama una vez al terminar o cancelarse (en el hilo principal). */
    public EditJob onEnd(Runnable r) {
        this.onEnd = r;
        return this;
    }

    @Override
    public boolean step(long deadlineNanos, int maxBlocks) {
        long t0 = clock.getAsLong();
        try {
            return doStep(deadlineNanos, maxBlocks);
        } finally {
            long dt = clock.getAsLong() - t0;
            nanos += dt;
            maxStepNanos = Math.max(maxStepNanos, dt);
        }
    }

    /** Tiempo de hilo principal que usó (para estimar cuánto tardará la próxima). */
    public long nanos() {
        return nanos;
    }

    /** El tick más largo de este trabajo (H5 CA1: lo muestra /wc jobs para la última obra). */
    public long maxStepNanos() {
        return maxStepNanos;
    }

    private boolean doStep(long deadlineNanos, int maxBlocks) {
        if (state == State.DONE || state == State.CANCELLED) return true;
        state = State.RUNNING;
        if (!source.ready()) return false;
        if (before != null && !beforeDone) {
            if (!before.step(deadlineNanos, clock)) {
                sink.flush();
                return false;
            }
            beforeDone = true;
        }
        int n = 0;
        while (!blocksDone && n < maxBlocks) {
            if (!pending) {
                if (!source.next(p)) {
                    blocksDone = true;
                    break;
                }
                pending = true;
            }
            int x = p[0], y = p[1], z = p[2];
            long key = ChunkOrder.pack(x >> 4, z >> 4);
            if (key != chunk) {
                if (chunk != NO_CHUNK) world.release(ChunkOrder.chunkX(chunk), ChunkOrder.chunkZ(chunk));
                chunk = key;
            }
            if (!world.ready(x >> 4, z >> 4)) {
                sink.flush();
                return false;
            }
            pending = false;
            n++;
            visited++;
            if (y >= world.minY() && y <= world.maxY()) process(x, y, z);
            if (changed >= maxChanges) {
                truncated = true;
                end(State.DONE, true);
                return true;
            }
            // F-01: el reloj se mira en cada bloque (cuesta unos 25 ns; poner uno, microsegundos). Mirarlo cada 64
            // dejaba pasar el plazo por decenas de milisegundos cuando los bloques eran lentos.
            if (clock.getAsLong() >= deadlineNanos) break;
        }
        if (blocksDone && (after == null || after.step(deadlineNanos, clock))) {
            end(State.DONE, true);
            return true;
        }
        sink.flush();
        return false;
    }

    private void process(int x, int y, int z) {
        int current = world.get(x, y, z);
        if (capture != null) capture.take(x, y, z, current, world.tile(x, y, z));
        if (mode == Mode.READ) return;
        int t = source.target(x, y, z, current);
        if (t == EditSource.KEEP) return;
        byte[] tileAfter = source.tile();
        if (t == current && tileAfter == null) return;
        if (mode == Mode.APPLY) {
            byte[] tileBefore = world.tile(x, y, z);
            sink.changed(x, y, z, current, t, tileBefore, tileAfter);
            world.set(x, y, z, t);
            if (tileAfter != null) world.restoreTile(x, y, z, tileAfter);
        } else {
            sink.changed(x, y, z, current, t, null, null);
        }
        changed++;
    }

    private void end(State s, boolean complete) {
        if (chunk != NO_CHUNK) world.release(ChunkOrder.chunkX(chunk), ChunkOrder.chunkZ(chunk));
        chunk = NO_CHUNK;
        state = s;
        try {
            sink.flush();
            sink.close(complete);
        } finally {
            onEnd.run();
        }
    }

    @Override
    public void cancel() {
        if (state == State.DONE || state == State.CANCELLED) return;
        end(State.CANCELLED, false);
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
        return world.name();
    }

    @Override
    public Box bounds() {
        return source.bounds();
    }

    public String label() {
        return label;
    }

    public Mode mode() {
        return mode;
    }

    public State state() {
        return state;
    }

    public long visited() {
        return visited;
    }

    public long changed() {
        return changed;
    }

    public long skipped() {
        return source.skipped();
    }

    public long volume() {
        return source.volume();
    }

    /** La vista previa llegó al tope y no muestra todo. */
    public boolean truncated() {
        return truncated;
    }

    /** 0..1 */
    public double progress() {
        long v = source.volume();
        return v <= 0 ? (state == State.DONE ? 1 : 0) : Math.min(1, visited / (double) v);
    }
}
