package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.edit.Scheduler;
import dev.leo.worldcraft.nature.NatureMath;
import dev.leo.worldcraft.nature.TreeKind;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.ChunkOrder;
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Los árboles del juego capturados para el motor (plan 033 §1.4). Paper genera un árbol en una lista de estados y pregunta
 * por cada uno si lo pone: devolviendo false no pone nada y la lista queda para la obra (vista previa, límites, deshacer).
 */
public final class Trees {
    private Trees() {
    }

    /** El TreeType de Paper de un tipo (null si esta versión no lo tiene). */
    static TreeType type(TreeKind k) {
        try {
            return k.paper == null ? null : TreeType.valueOf(k.paper);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Un árbol en (x, y, z) sin tocar el mundo: posición → estado, o null si no cabe. Hilo principal. */
    public static Map<Long, Integer> capture(World w, PaperBlocks blocks, int x, int y, int z, TreeKind kind, Random r) {
        TreeType t = type(kind);
        if (t == null) return null;
        Map<Long, Integer> out = new LinkedHashMap<>();
        Predicate<BlockState> keep = bs -> {
            out.put(PointsSource.pack(bs.getX(), bs.getY(), bs.getZ()), blocks.id(bs.getBlockData()));
            return false;
        };
        boolean ok = w.generateTree(new Location(w, x, y, z), r, t, keep);
        return ok && !out.isEmpty() ? out : null;
    }

    /** Lo capturado: los bloques de todos los árboles juntos, cuántos se plantaron y cuántos no cupieron. */
    public record Result(Map<Long, Integer> blocks, int planted, int failed) {
    }

    /**
     * Captura los árboles de una lista de lugares por tandas (H5): espera sin bloquear a que estén cargados los 3×3 chunks
     * de alrededor de cada uno (un árbol grande se sale del suyo) y mira el reloj después de cada árbol.
     */
    public static final class Planter implements Scheduler.Job {
        private static final int MAX_WAIT = 200;
        private final int id;
        private final UUID owner;
        private final Plugin plugin;
        private final World world;
        private final PaperBlocks blocks;
        private final long[] spots;
        private final TreeKind kind;
        private final long seed;
        private final IntPredicate isLog;
        private final java.util.function.Consumer<Result> done;
        private final Runnable onCancel;
        private final Set<Long> held = new HashSet<>(), loading = new HashSet<>();
        private final List<Map<Long, Integer>> trees = new ArrayList<>();
        private int next, waited, failed;
        private boolean finished, cancelled;

        public Planter(int id, UUID owner, Plugin plugin, World world, PaperBlocks blocks, long[] spots, TreeKind kind, long seed,
                       IntPredicate isLog, java.util.function.Consumer<Result> done, Runnable onCancel) {
            this.id = id;
            this.owner = owner;
            this.plugin = plugin;
            this.world = world;
            this.blocks = blocks;
            this.spots = spots;
            this.kind = kind;
            this.seed = seed;
            this.isLog = isLog;
            this.done = done;
            this.onCancel = onCancel;
        }

        @Override
        public boolean step(long deadlineNanos, int maxBlocks) {
            if (finished || cancelled) return true;
            while (next < spots.length) {
                long p = spots[next];
                int x = PointsSource.unpackX(p), y = PointsSource.unpackY(p), z = PointsSource.unpackZ(p);
                if (!around(x >> 4, z >> 4)) {
                    if (++waited < MAX_WAIT) return false;
                    waited = 0;
                    next++;
                    failed++; // sin los chunks de alrededor no se genera (los cargaría de golpe)
                    continue;
                }
                waited = 0;
                Random r = new Random(seed ^ (next * 0x9E3779B97F4A7C15L));
                Map<Long, Integer> t = capture(world, blocks, x, y, z, kind.pick(r), r);
                next++;
                if (t == null) failed++;
                else trees.add(t);
                if (System.nanoTime() >= deadlineNanos) return false;
            }
            finished = true;
            release();
            done.accept(new Result(NatureMath.merge(trees, isLog), trees.size(), failed));
            return true;
        }

        /** Los 3×3 chunks de alrededor, cargados y sostenidos; si falta alguno, se pide sin bloquear. */
        private boolean around(int cx, int cz) {
            boolean all = true;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int ax = cx + dx, az = cz + dz;
                    long key = ChunkOrder.pack(ax, az);
                    if (held.contains(key)) continue;
                    if (world.isChunkLoaded(ax, az)) {
                        held.add(key);
                        world.addPluginChunkTicket(ax, az, plugin);
                        continue;
                    }
                    all = false;
                    if (loading.add(key)) {
                        world.getChunkAtAsync(ax, az).whenComplete((c, err) -> {
                            loading.remove(key);
                            if (err == null && !finished && !cancelled && held.add(key)) world.addPluginChunkTicket(ax, az, plugin);
                        });
                    }
                }
            }
            return all;
        }

        private void release() {
            for (long k : held) world.removePluginChunkTicket(ChunkOrder.chunkX(k), ChunkOrder.chunkZ(k), plugin);
            held.clear();
        }

        @Override
        public void cancel() {
            if (finished || cancelled) return;
            cancelled = true;
            release();
            onCancel.run();
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
            return world.getName();
        }

        @Override
        public Box bounds() {
            return null;
        }
    }
}
