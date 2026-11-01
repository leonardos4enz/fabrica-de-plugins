package dev.leo.skinstatues.statue;

import dev.leo.skinstatues.SkinStatues;
import dev.leo.skinstatues.statue.StatueStore.State;
import dev.leo.skinstatues.statue.StatueStore.Statue;
import dev.leo.skinstatues.color.BlockPalette;
import dev.leo.skinstatues.config.Settings;
import dev.leo.skinstatues.model.Pose;
import dev.leo.skinstatues.model.VoxelGrid;
import dev.leo.skinstatues.model.Voxelizer;
import dev.leo.skinstatues.skin.SkinException;
import dev.leo.skinstatues.skin.SkinRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Construir, quitar y comprobar estatuas (H1, H7, H8). El cálculo y la E/S van fuera del hilo principal; poner y
 * quitar bloques, en él, por tandas (TickWorker).
 */
public final class Statues {
    public static final UUID CONSOLE = new UUID(0, 0);

    private final SkinStatues plugin;
    private final StatueStore store;
    private final TickWorker worker;
    /** Un solo hilo de E/S: el diario y el índice se escriben en orden. */
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SkinStatues-IO");
        t.setDaemon(true);
        return t;
    });

    public Statues(SkinStatues plugin, StatueStore store) {
        this.plugin = plugin;
        this.store = store;
        this.worker = new TickWorker(plugin, () -> plugin.config().settings().blocksPerTick(),
                () -> plugin.config().settings().maxMsPerTick());
    }

    public StatueStore store() {
        return store;
    }

    public TickWorker worker() {
        return worker;
    }

    // ---------------------------------------------------------------- construir

    public record Request(String name, int height, Pose pose, BlockPalette palette, boolean overlay, boolean cape) {
    }

    /** H1: los pies 2 bloques arriba del jugador (offset_up), mirando hacia donde mira, redondeado a N/S/E/O. */
    public void build(Player player, Request req) {
        Settings st = plugin.config().settings();
        Location loc = player.getLocation();
        World world = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY() + st.offsetUp(), bz = loc.getBlockZ();
        int facing = Math.floorMod(Math.round(loc.getYaw() / 90f), 4) * 90;
        UUID who = player.getUniqueId();
        String whoName = player.getName();
        player.sendMessage(plugin.messages().get("loading-skin", "name", req.name()));

        SkinRepository repo = plugin.skins();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            StatuePlan plan;
            String source;
            try {
                SkinRepository.Result r = repo.load(req.name(), st.mojang(), st.cacheHours());
                source = r.source();
                VoxelGrid g = Voxelizer.run(r.skin(), req.pose(), new Voxelizer.Options(req.height(), facing,
                        req.overlay(), st.overlayDepth(), st.alphaThreshold(), req.cape()));
                plan = StatuePlan.of(g, req.palette());
            } catch (SkinException e) {
                Bukkit.getScheduler().runTask(plugin, () -> tell(who, e.key(), e.args()));
                return;
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not plan the statue of " + req.name(), e);
                Bukkit.getScheduler().runTask(plugin, () -> tell(who, "internal-error"));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> enqueue(who, whoName, world, bx, by, bz, facing, req, plan, source));
        });
    }

    private void enqueue(UUID who, String whoName, World world, int bx, int by, int bz, int facing, Request req,
                         StatuePlan plan, String source) {
        Settings st = plugin.config().settings();
        int top = by + plan.height() - 1;
        if (by < world.getMinHeight() || top >= world.getMaxHeight()) {
            int room = world.getMaxHeight() - Math.max(by, world.getMinHeight());
            int fit = Math.max(0, (int) Math.floor(req.height() * (double) room / plan.height()));
            tell(who, "too-tall-world", "height", req.height(), "max", fit);
            return;
        }
        if (plan.size() > st.maxBlocks()) {
            tell(who, "too-many-blocks", "blocks", plan.size(), "max", st.maxBlocks());
            return;
        }
        if (worker.full()) {
            tell(who, "queue-full", "max", TickWorker.MAX_QUEUE);
            return;
        }
        BlockData[] datas = new BlockData[plan.ids.length];
        for (int i = 0; i < datas.length; i++) {
            Material m = Material.matchMaterial(plan.ids[i]);
            datas[i] = m == null ? Material.STONE.createBlockData() : m.createBlockData();
        }
        int id = store.allocateId();
        Statue statue = new Statue(id, req.name(), req.height(), req.pose().name(), req.palette().name(), world.getName(),
                bx, by, bz, facing, who, whoName, System.currentTimeMillis(), plan.size(), State.BUILDING);
        store.put(statue);
        saveIndex();
        int seconds = (int) Math.ceil(plan.size() / (double) st.blocksPerTick() / 20.0);
        tell(who, "planned", "id", id, "name", req.name(), "blocks", plan.size(), "seconds", Math.max(1, seconds),
                "width", plan.width(), "height", plan.height(), "depth", plan.depth(), "source", source,
                "waiting", worker.waiting());
        worker.submit(new BuildJob(statue, world, plan, datas));
    }

    private final class BuildJob implements TickWorker.Job {
        private final Statue statue;
        private final World world;
        private final StatuePlan plan;
        private final BlockData[] datas;
        private final Journal[] journal = new Journal[1]; // solo se toca desde el hilo de E/S
        private int next, placed, kept, steps;

        BuildJob(Statue statue, World world, StatuePlan plan, BlockData[] datas) {
            this.statue = statue;
            this.world = world;
            this.plan = plan;
            this.datas = datas;
        }

        @Override
        public void start() {
            io.execute(() -> {
                try {
                    journal[0] = Journal.create(store.journal(statue.id()));
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not create the journal of statue #" + statue.id(), e);
                }
            });
        }

        @Override
        public boolean step(long deadline, int maxBlocks) {
            List<Journal.Entry> batch = new ArrayList<>();
            int done = 0;
            while (next < plan.size() && done < maxBlocks) {
                if ((done & 255) == 255 && System.nanoTime() >= deadline) break;
                int k = next++;
                Block b = world.getBlockAt(statue.x() + plan.dx[k], statue.y() + plan.dy[k], statue.z() + plan.dz[k]);
                BlockData target = datas[plan.block[k]];
                Journal.Entry e = place(b, target, plan.dx[k], plan.dy[k], plan.dz[k]);
                if (e == null) kept++;
                else {
                    batch.add(e);
                    placed++;
                }
                done++;
            }
            if (!batch.isEmpty()) io.execute(() -> append(batch));
            if (++steps % 10 == 0) {
                Player p = Bukkit.getPlayer(statue.owner());
                if (p != null) {
                    int pct = (int) (100L * next / Math.max(1, plan.size()));
                    p.sendActionBar(plugin.messages().get("progress", "id", statue.id(), "percent", pct,
                            "done", next, "blocks", plan.size()));
                }
            }
            if (next < plan.size()) return false;
            finish(State.DONE);
            tell(statue.owner(), "done", "id", statue.id(), "name", statue.skin(), "placed", placed);
            if (kept > 0) tell(statue.owner(), "kept-containers", "count", kept);
            return true;
        }

        private void append(List<Journal.Entry> batch) {
            try {
                if (journal[0] != null) journal[0].append(batch);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not write the journal of statue #" + statue.id(), e);
            }
        }

        private void finish(State state) {
            io.execute(() -> {
                try {
                    if (journal[0] != null) journal[0].close();
                } catch (IOException ignored) {
                    // el diario ya está escrito por tandas
                }
            });
            store.put(statue.withState(state, placed));
            saveIndex();
        }

        @Override
        public void cancel() {
            finish(State.CANCELLED);
            tell(statue.owner(), "cancelled", "id", statue.id(), "placed", placed);
        }

        @Override
        public int statueId() {
            return statue.id();
        }

        @Override
        public UUID requester() {
            return statue.owner();
        }
    }

    /**
     * Pone el bloque si hace falta y devuelve lo que había (H8 CA1), o null si no se tocó: ya era ese bloque o tiene
     * contenido (cofres, carteles, hornos…), que se deja para no perder lo que guarda.
     */
    static Journal.Entry place(Block b, BlockData target, int dx, int dy, int dz) {
        BlockData cur = b.getBlockData();
        if (cur.equals(target)) return null;
        if (!cur.getMaterial().isAir() && b.getState(false) instanceof TileState) return null;
        b.setBlockData(target, false); // sin física: el agua o la arena vecinas no se mueven a mitad de obra
        return new Journal.Entry(dx, dy, dz, cur.getAsString(), target.getAsString());
    }

    // ---------------------------------------------------------------- quitar

    /** H8 CA2: quita la estatua (cancelando su construcción si sigue) y devuelve lo que había. */
    public void remove(CommandSender sender, Statue statue) {
        UUID who = sender instanceof Player p ? p.getUniqueId() : CONSOLE;
        TickWorker.Job running = worker.find(statue.id());
        if (running instanceof RemoveJob) {
            tell(who, "busy", "id", statue.id());
            return;
        }
        if (running != null) worker.cancel(statue.id());
        World world = Bukkit.getWorld(statue.world());
        if (world == null) {
            tell(who, "world-missing", "id", statue.id(), "world", statue.world());
            return;
        }
        tell(who, "removing", "id", statue.id());
        io.execute(() -> {
            List<Journal.Entry> entries;
            try {
                entries = Journal.read(store.journal(statue.id()));
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not read the journal of statue #" + statue.id(), e);
                Bukkit.getScheduler().runTask(plugin, () -> tell(who, "internal-error"));
                return;
            }
            Collections.reverse(entries); // de arriba hacia abajo
            Bukkit.getScheduler().runTask(plugin, () -> worker.submit(new RemoveJob(statue, world, entries, who)));
        });
    }

    private final class RemoveJob implements TickWorker.Job {
        private final Statue statue;
        private final World world;
        private final List<Journal.Entry> entries;
        private final UUID who;
        private final Map<String, BlockData> parsed = new HashMap<>();
        private int next, restored, changed;

        RemoveJob(Statue statue, World world, List<Journal.Entry> entries, UUID who) {
            this.statue = statue;
            this.world = world;
            this.entries = entries;
            this.who = who;
        }

        @Override
        public boolean step(long deadline, int maxBlocks) {
            int done = 0;
            while (next < entries.size() && done < maxBlocks) {
                if ((done & 255) == 255 && System.nanoTime() >= deadline) break;
                Journal.Entry e = entries.get(next++);
                Block b = world.getBlockAt(statue.x() + e.dx(), statue.y() + e.dy(), statue.z() + e.dz());
                if (restore(b, data(e.placed()), data(e.before()))) restored++;
                else changed++;
                done++;
            }
            if (next < entries.size()) return false;
            store.remove(statue.id());
            saveIndex();
            io.execute(() -> {
                try {
                    Files.deleteIfExists(store.journal(statue.id()));
                } catch (IOException ignored) {
                    // queda un archivo huérfano, sin efecto
                }
            });
            tell(who, "removed", "id", statue.id(), "restored", restored, "changed", changed);
            return true;
        }

        private BlockData data(String s) {
            return parsed.computeIfAbsent(s, Bukkit::createBlockData);
        }

        @Override
        public void cancel() {
            // Al apagar: lo que falta se quita la próxima vez (el diario sigue ahí).
        }

        @Override
        public int statueId() {
            return statue.id();
        }

        @Override
        public UUID requester() {
            return who;
        }
    }

    /** H8 CA3: solo se devuelve lo que había si el bloque sigue siendo el de la estatua. */
    static boolean restore(Block b, BlockData placed, BlockData before) {
        if (!b.getBlockData().equals(placed)) return false;
        b.setBlockData(before, false);
        return true;
    }

    // ---------------------------------------------------------------- autoprueba

    /**
     * H9 CA5: la estatua de la skin de ejemplo a altura 16 junto al punto de aparición, hundida un bloque en el suelo
     * (así reemplaza terreno); comprueba cada bloque, la quita y comprueba que el terreno quedó igual.
     */
    public void selftest(CommandSender sender, BlockPalette palette) {
        World world = Bukkit.getWorlds().get(0);
        Location spawn = world.getSpawnLocation();
        int bx = spawn.getBlockX() + 24, bz = spawn.getBlockZ() + 24;
        int by = world.getHighestBlockYAt(bx, bz) - 1;
        SkinRepository repo = plugin.skins();
        Settings st = plugin.config().settings();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            StatuePlan plan;
            try {
                SkinRepository.Result r = repo.load("Example", false, 0);
                plan = StatuePlan.of(Voxelizer.run(r.skin(), Pose.STANDING, new Voxelizer.Options(16, 0, true,
                        st.overlayDepth(), st.alphaThreshold(), true)), palette);
            } catch (SkinException e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(plugin.messages().get(e.key(), e.args()));
                    sender.sendMessage(plugin.messages().get("selftest-fail", "reason", "the example skin could not be read"));
                });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(runSelftest(world, bx, by, bz, plan)));
        });
    }

    private net.kyori.adventure.text.Component runSelftest(World world, int bx, int by, int bz, StatuePlan plan) {
        Map<Integer, BlockData> before = new HashMap<>();
        for (int k = 0; k < plan.size(); k++) {
            before.put(k, world.getBlockAt(bx + plan.dx[k], by + plan.dy[k], bz + plan.dz[k]).getBlockData());
        }
        List<Journal.Entry> entries = new ArrayList<>();
        Map<String, BlockData> cache = new HashMap<>();
        int wrong = 0;
        for (int k = 0; k < plan.size(); k++) {
            Block b = world.getBlockAt(bx + plan.dx[k], by + plan.dy[k], bz + plan.dz[k]);
            BlockData target = cache.computeIfAbsent(plan.id(k), id -> Material.matchMaterial(id).createBlockData());
            Journal.Entry e = place(b, target, plan.dx[k], plan.dy[k], plan.dz[k]);
            if (e != null) entries.add(e);
            if (!b.getBlockData().equals(target) && !(b.getState(false) instanceof TileState)) wrong++;
        }
        Collections.reverse(entries);
        for (Journal.Entry e : entries) {
            Block b = world.getBlockAt(bx + e.dx(), by + e.dy(), bz + e.dz());
            restore(b, Bukkit.createBlockData(e.placed()), Bukkit.createBlockData(e.before()));
        }
        int differs = 0;
        for (int k = 0; k < plan.size(); k++) {
            if (!world.getBlockAt(bx + plan.dx[k], by + plan.dy[k], bz + plan.dz[k]).getBlockData().equals(before.get(k))) differs++;
        }
        if (wrong == 0 && differs == 0) {
            return plugin.messages().get("selftest-ok", "blocks", plan.size(), "replaced", entries.size(),
                    "x", bx, "y", by, "z", bz);
        }
        return plugin.messages().get("selftest-fail", "reason", wrong + " block(s) not placed as planned, "
                + differs + " block(s) of terrain not restored");
    }

    // ---------------------------------------------------------------- apoyo

    public void saveIndex() {
        String snap = store.snapshot();
        io.execute(() -> {
            try {
                store.write(snap);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save data/statues.yml", e);
            }
        });
    }

    void tell(UUID who, String key, Object... args) {
        CommandSender s = who.equals(CONSOLE) ? Bukkit.getConsoleSender() : Bukkit.getPlayer(who);
        if (s != null) s.sendMessage(plugin.messages().get(key, args));
    }

    /** onDisable: cancela lo pendiente (queda deshacible), escribe el índice y espera al hilo de E/S. */
    public void shutdown() {
        try {
            worker.shutdown();
        } finally {
            String snap = store.snapshot();
            io.execute(() -> {
                try {
                    store.write(snap);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not save data/statues.yml", e);
                }
            });
            io.shutdown();
            try {
                if (!io.awaitTermination(10, TimeUnit.SECONDS)) plugin.getLogger().warning("I/O did not finish in 10 s");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
