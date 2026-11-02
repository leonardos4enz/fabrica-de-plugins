package dev.leo.worldcraft.biome;

import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.entity.EntityStage;
import dev.leo.worldcraft.history.Journal;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Una etapa de biomas de una obra (plan 033 §1.1), por tandas dentro del presupuesto del tick: recorre celdas de 4×4×4 en
 * orden de chunks, mira el reloj en cada una (lección 025 F-01) y espera sin bloquear a que cargue el chunk. Al dejar un
 * chunk con celdas cambiadas, lo reenvía a los jugadores. Usa la interfaz de etapas de la 031 (genérica).
 */
public final class BiomeStage implements EntityStage {
    /** Qué poner en la celda i (esquina mínima x, y, z) sabiendo el bioma actual: null = dejarla; {@link #SKIP} = saltarla y contarla. */
    public interface Step {
        String at(int i, int x, int y, int z, String current);
    }

    /** Lo que se lee de cada celda (copiar, biomeinfo). */
    public interface Seen {
        void at(int x, int y, int z, String biome);
    }

    /** Las celdas a recorrer y qué hacer en cada una; null mientras no está listo (el diario se lee en otro hilo). */
    public record Plan(long[] cells, Step step) {
    }

    public static final String SKIP = "\0skip";

    private final BiomeWorld world;
    private final Supplier<Plan> plan;
    private final Seen seen;
    private final BiomeSink sink;
    private long[] cells;
    private Step step;
    private int next, waited, done, skipped;
    private long chunk = Long.MIN_VALUE;
    private boolean dirty, closed;

    private BiomeStage(BiomeWorld world, Supplier<Plan> plan, Seen seen, BiomeSink sink) {
        this.world = world;
        this.plan = plan;
        this.seen = seen;
        this.sink = sink;
    }

    /** Pone en cada celda lo que diga pick (null = dejarla). */
    public static BiomeStage apply(BiomeWorld world, long[] cells, Step pick, BiomeSink sink) {
        Plan p = new Plan(cells, pick);
        return new BiomeStage(world, () -> p, null, sink);
    }

    /** Lee el bioma de cada celda. */
    public static BiomeStage read(BiomeWorld world, long[] cells, Seen seen) {
        Plan p = new Plan(cells, (i, x, y, z, c) -> null);
        return new BiomeStage(world, () -> p, seen, BiomeSink.NONE);
    }

    /**
     * Deshacer o rehacer desde el diario: la celda vuelve a como estaba solo si sigue como la dejó la obra (al rehacer, si
     * sigue como estaba antes); si otro la cambió después, se deja y se cuenta.
     */
    public static BiomeStage undo(BiomeWorld world, Supplier<Journal.Data> data, boolean redo) {
        return new BiomeStage(world, () -> {
            Journal.Data d = data.get();
            if (d == null) return null;
            long[] cells = new long[d.biomes];
            for (int i = 0; i < d.biomes; i++) cells[i] = PointsSource.pack(d.bx[i], d.by[i], d.bz[i]);
            return new Plan(cells, (i, x, y, z, current) -> {
                String expect = d.biomeDict.get(redo ? d.bBefore[i] : d.bAfter[i]);
                String put = d.biomeDict.get(redo ? d.bAfter[i] : d.bBefore[i]);
                return expect.equals(current) ? put : SKIP;
            });
        }, null, BiomeSink.NONE);
    }

    @Override
    public boolean step(long deadlineNanos, LongSupplier clock) {
        if (closed) return true;
        if (cells == null) {
            Plan p = plan.get();
            if (p == null) return false;
            cells = p.cells();
            step = p.step();
        }
        while (next < cells.length) {
            long c = cells[next];
            int x = PointsSource.unpackX(c), y = PointsSource.unpackY(c), z = PointsSource.unpackZ(c);
            long key = ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
            if (key != chunk) {
                flushChunk();
                chunk = key;
            }
            if (!world.ready(x >> 4, z >> 4)) {
                if (++waited < MAX_WAIT) return false;
                // el chunk no cargó: se saltan sus celdas (y se cuentan)
                waited = 0;
                while (next < cells.length && sameChunk(cells[next], key)) {
                    next++;
                    skipped++;
                }
                continue;
            }
            waited = 0;
            int i = next++;
            if (y < world.minY() || y > world.maxY()) continue;
            String current = world.get(x, y, z);
            if (seen != null) seen.at(x, y, z, current);
            String put = step.at(i, x, y, z, current);
            if (put == SKIP) {
                skipped++;
            } else if (put != null && !put.equals(current)) {
                sink.biome(x, y, z, current, put);
                world.set(x, y, z, put);
                dirty = true;
                done++;
            }
            if (clock.getAsLong() >= deadlineNanos) return false;
        }
        flushChunk();
        if (!closed) {
            closed = true;
            world.close();
        }
        return true;
    }

    private static boolean sameChunk(long cell, long key) {
        int x = PointsSource.unpackX(cell), z = PointsSource.unpackZ(cell);
        return (((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL)) == key;
    }

    private void flushChunk() {
        if (dirty && chunk != Long.MIN_VALUE) world.refresh((int) (chunk >> 32), (int) chunk);
        dirty = false;
    }

    /** Celdas cambiadas. */
    @Override
    public int done() {
        return done;
    }

    /** Celdas que no se tocaron: otro las cambió después (deshacer seguro) o su chunk no cargó. */
    @Override
    public int skipped() {
        return skipped;
    }

    /** Cuántas celdas recorre (0 mientras no está listo). */
    public int size() {
        return cells == null ? 0 : cells.length;
    }
}
