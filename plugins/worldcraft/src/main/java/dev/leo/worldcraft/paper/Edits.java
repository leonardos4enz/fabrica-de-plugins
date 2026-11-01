package dev.leo.blockwright.paper;

import dev.leo.blockwright.Blockwright;
import dev.leo.blockwright.config.Limits;
import dev.leo.blockwright.config.Settings;
import dev.leo.blockwright.edit.Clipboard;
import dev.leo.blockwright.edit.EditJob;
import dev.leo.blockwright.edit.EditSource;
import dev.leo.blockwright.edit.Scheduler;
import dev.leo.blockwright.edit.UndoSource;
import dev.leo.blockwright.history.HistoryStore;
import dev.leo.blockwright.history.Journal;
import dev.leo.blockwright.history.JournalSink;
import dev.leo.blockwright.region.Box;
import dev.leo.blockwright.region.ChunkOrder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Une todo lo de una operación (plan 025 §1.5): límites, confirmación, vista previa, historial, diario y planificador,
 * con sus mensajes. Hilo principal.
 */
public final class Edits {
    public static final UUID CONSOLE = new UUID(0, 0);
    public static final UUID SELFTEST = new UUID(0, 1);

    /**
     * Una operación pedida.
     *
     * @param placed  los estados que puede poner (para los bloques prohibidos)
     * @param capture para cut: a dónde va lo que había (se crea al aplicar)
     */
    public record Op(String kind, String detail, World world, Box bounds, long volume, Supplier<EditSource> source,
                     Collection<Integer> placed, Supplier<EditJob.Capture> capture) {
    }

    /** Una operación esperando /bwr confirm, quizá con su vista previa. */
    public static final class Pending {
        final Op op;
        final String ownerName;
        long expires;
        PreviewSink preview;
        EditJob previewJob;

        Pending(Op op, String ownerName, long expires) {
            this.op = op;
            this.ownerName = ownerName;
            this.expires = expires;
        }
    }

    private final Blockwright plugin;
    private final PaperBlocks blocks;
    private final HistoryStore history;
    private final ExecutorService io;
    private final Scheduler scheduler;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<Integer, EditJob> jobs = new HashMap<>();
    private BukkitTask task;
    private int ticks, nextJob = 1;
    /** Bloques por milisegundo de hilo principal, medido en las operaciones terminadas (para estimar). */
    private double blocksPerMs = 300;

    public Edits(Blockwright plugin, PaperBlocks blocks, HistoryStore history, ExecutorService io) {
        this.plugin = plugin;
        this.blocks = blocks;
        this.history = history;
        this.io = io;
        this.scheduler = new Scheduler(System::nanoTime, () -> settings().maxMsPerTick(),
                () -> settings().maxBlocksPerTick(), (job, e) -> plugin.getLogger().log(Level.SEVERE,
                "Edit #" + job.id() + " stopped by an error", e));
    }

    private Settings settings() {
        return plugin.config().settings();
    }

    public PaperBlocks blocks() {
        return blocks;
    }

    public HistoryStore history() {
        return history;
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    public Session session(UUID owner) {
        return sessions.computeIfAbsent(owner, k -> new Session());
    }

    public Collection<Session> sessionsView() {
        return sessions.values();
    }

    public static UUID owner(CommandSender s) {
        return s instanceof Player p ? p.getUniqueId() : CONSOLE;
    }

    public Limits.Resolved limits(CommandSender s) {
        return plugin.config().limits().resolve(s::hasPermission);
    }

    /** Segundos aproximados para tantos bloques con el presupuesto actual. */
    public double estimateSeconds(long blocks) {
        double perTick = Math.max(1, blocksPerMs * settings().maxMsPerTick());
        perTick = Math.min(perTick, settings().maxBlocksPerTick());
        return blocks / perTick / 20.0;
    }

    // ------------------------------------------------------------------ pedir, confirmar, cancelar

    /** Pide una operación: revisa límites y, según el tamaño o --preview, la corre o la deja esperando confirmación. */
    public void request(CommandSender who, Op op, boolean preview) {
        UUID owner = owner(who);
        Limits.Resolved lim = limits(who);
        var msg = plugin.messages();
        if (!lim.world(op.world().getName())) {
            who.sendMessage(msg.get("limit-world", "world", op.world().getName()));
            return;
        }
        if (op.volume() > lim.maxBlocks()) {
            who.sendMessage(msg.get("limit-blocks", "blocks", fmt(op.volume()), "max", fmt(lim.maxBlocks()),
                    "groups", String.join(", ", lim.groups())));
            return;
        }
        for (int s : op.placed()) {
            if (lim.isBlocked(blocks.table().material(s), blocks.table().properties(s))) {
                who.sendMessage(msg.get("limit-blocked", "block", blocks.table().material(s).replace("minecraft:", "")));
                return;
            }
        }
        if (busy(owner) >= lim.maxJobs()) {
            who.sendMessage(msg.get("limit-jobs", "max", String.valueOf(lim.maxJobs())));
            return;
        }
        Session ses = session(owner);
        dropPending(ses);
        String name = who.getName();
        long expires = System.currentTimeMillis() + settings().confirmSeconds() * 1000L;
        if (preview && who instanceof Player p) {
            Pending pend = new Pending(op, name, expires);
            pend.preview = new PreviewSink(p, op.world(), blocks);
            PaperWorld w = world(op.world(), op.source().get());
            // La vista previa se arma con una fuente nueva: la de la obra será otra, con la misma semilla.
            pend.previewJob = new EditJob(nextJob++, owner, "preview", w, op.source().get(), EditJob.Mode.PREVIEW,
                    pend.preview, null, settings().previewMaxBlocks(), System::nanoTime);
            EditJob pj = pend.previewJob;
            pj.onEnd(() -> {
                w.close();
                jobs.remove(pj.id());
                if (pj.state() == EditJob.State.DONE) {
                    who.sendMessage(msg.get(pj.truncated() ? "preview-partial" : "preview-ready",
                            "shown", fmt(pend.preview.shown()), "blocks", fmt(op.volume()),
                            "seconds", String.valueOf(settings().confirmSeconds())));
                }
            });
            ses.pending = pend;
            jobs.put(pj.id(), pj);
            submit(pj);
            who.sendMessage(msg.get("preview-start", "kind", op.kind(), "blocks", fmt(op.volume())));
            return;
        }
        if (op.volume() > settings().confirmAbove()) {
            ses.pending = new Pending(op, name, expires);
            who.sendMessage(msg.get("confirm-needed", "kind", op.kind(), "blocks", fmt(op.volume()),
                    "time", seconds(estimateSeconds(op.volume())), "seconds", String.valueOf(settings().confirmSeconds())));
            ensureTask();
            return;
        }
        run(who, owner, name, op);
    }

    public void confirm(CommandSender who) {
        Session ses = session(owner(who));
        Pending p = ses.pending;
        if (p == null) {
            who.sendMessage(plugin.messages().get("nothing-to-confirm"));
            return;
        }
        dropPending(ses);
        run(who, owner(who), p.ownerName, p.op);
    }

    /** Cancela lo que espera confirmación o, si no hay, las operaciones en marcha de quien lo pide. */
    public void cancel(CommandSender who) {
        UUID owner = owner(who);
        Session ses = session(owner);
        if (ses.pending != null) {
            dropPending(ses);
            who.sendMessage(plugin.messages().get("pending-cancelled"));
            return;
        }
        int n = 0;
        for (Scheduler.Job j : scheduler.of(owner)) if (scheduler.cancel(j.id())) n++;
        who.sendMessage(plugin.messages().get(n == 0 ? "nothing-to-cancel" : "jobs-cancelled", "count", String.valueOf(n)));
    }

    public boolean cancelJob(int id) {
        return scheduler.cancel(id);
    }

    private void dropPending(Session ses) {
        Pending p = ses.pending;
        ses.pending = null;
        if (p == null) return;
        if (p.previewJob != null) {
            scheduler.cancel(p.previewJob.id());
            p.previewJob.cancel();
        }
        if (p.preview != null) p.preview.revert();
    }

    private int busy(UUID owner) {
        int n = 0;
        for (Scheduler.Job j : scheduler.of(owner)) if (j instanceof EditJob e && e.mode() != EditJob.Mode.PREVIEW) n++;
        return n;
    }

    // ------------------------------------------------------------------ correr

    private void run(CommandSender who, UUID owner, String ownerName, Op op) {
        var msg = plugin.messages();
        HistoryStore.Entry entry = history.begin(owner, ownerName, op.kind(), op.detail(), op.world().getName(), op.bounds(),
                System.currentTimeMillis());
        EditSource src = op.source().get();
        PaperWorld w = world(op.world(), src);
        JournalSink sink = new JournalSink(history.journal(owner, entry.id()), blocks.table(), io,
                e -> plugin.getLogger().log(Level.SEVERE, "Could not write the undo history of edit #" + entry.id(), e));
        EditJob job = new EditJob(nextJob++, owner, op.kind(), w, src, EditJob.Mode.APPLY, sink,
                op.capture() == null ? null : op.capture().get(), Long.MAX_VALUE, System::nanoTime);
        long started = System.nanoTime();
        job.onEnd(() -> {
            w.close();
            jobs.remove(job.id());
            boolean ok = job.state() == EditJob.State.DONE;
            history.finish(entry, job.changed(), ok);
            learn(job);
            String secs = seconds((System.nanoTime() - started) / 1e9);
            Component m = ok
                    ? msg.get("edit-done", "kind", op.kind(), "changed", fmt(job.changed()), "time", secs, "id", String.valueOf(entry.id()))
                    : msg.get("edit-cancelled", "kind", op.kind(), "changed", fmt(job.changed()));
            tell(owner, who, m);
            if (owner.equals(CONSOLE) || owner.equals(SELFTEST)) {
                plugin.getLogger().info(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(m));
            }
        });
        jobs.put(job.id(), job);
        submit(job);
        if (scheduler.of(owner).size() > 1) who.sendMessage(msg.get("edit-queued", "kind", op.kind()));
        else who.sendMessage(msg.get("edit-start", "kind", op.kind(), "blocks", fmt(op.volume()),
                "time", seconds(estimateSeconds(op.volume()))));
    }

    /** Lee una caja al portapapeles (copy): sin historial; el portapapeles queda al terminar. */
    public void copy(CommandSender who, World world, Box box, int ox, int oy, int oz, Runnable done) {
        UUID owner = owner(who);
        Clipboard clip = new Clipboard(box, ox, oy, oz);
        EditSource src = new dev.leo.blockwright.edit.RegionSource(box, dev.leo.blockwright.region.Shape.FILL, null, null, 0);
        PaperWorld w = world(world, src);
        EditJob job = new EditJob(nextJob++, owner, "copy", w, src, EditJob.Mode.READ, new EditJob.Sink() {
            @Override
            public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
            }
        }, (x, y, z, s, tile) -> clip.put(box, x, y, z, s, tile), Long.MAX_VALUE, System::nanoTime);
        job.onEnd(() -> {
            w.close();
            jobs.remove(job.id());
            if (job.state() == EditJob.State.DONE) {
                session(owner).clipboard(clip, world.getName());
                done.run();
            } else {
                tell(owner, who, plugin.messages().get("copy-cancelled"));
            }
        });
        jobs.put(job.id(), job);
        submit(job);
    }

    /** Deshace (o rehace) las últimas n operaciones de target; quien lo pide puede ser otro (con permiso). */
    public void undo(CommandSender who, UUID target, String targetName, int n, boolean redo) {
        var msg = plugin.messages();
        if (!scheduler.of(target).isEmpty() && target.equals(owner(who))) {
            who.sendMessage(msg.get("undo-busy"));
            return;
        }
        List<HistoryStore.Entry> picks = new ArrayList<>();
        List<HistoryStore.Entry> all = history.list(target);
        if (redo) {
            for (int i = all.size() - 1; i >= 0 && picks.size() < n; i--) {
                if (all.get(i).state() == HistoryStore.State.UNDONE) picks.add(all.get(i));
            }
        } else {
            for (HistoryStore.Entry e : all) {
                if (picks.size() >= n) break;
                if (e.state() == HistoryStore.State.DONE || e.state() == HistoryStore.State.CANCELLED) picks.add(e);
            }
        }
        if (picks.isEmpty()) {
            who.sendMessage(msg.get(redo ? "nothing-to-redo" : "nothing-to-undo", "player", targetName));
            return;
        }
        for (HistoryStore.Entry e : picks) {
            World world = Bukkit.getWorld(e.world());
            if (world == null) {
                who.sendMessage(msg.get("undo-no-world", "id", String.valueOf(e.id()), "world", e.world()));
                return;
            }
            var path = history.journal(target, e.id());
            CompletableFuture<Journal.Data> data = CompletableFuture.supplyAsync(() -> {
                try {
                    return Journal.read(path);
                } catch (IOException ex) {
                    throw new java.io.UncheckedIOException(ex);
                }
            }, io);
            UndoSource src = new UndoSource(data, blocks.table(), redo, e.bounds());
            long[] order = e.bounds() == null ? new long[0] : ChunkOrder.chunks(e.bounds());
            PaperWorld w = new PaperWorld(plugin, world, blocks, order);
            EditJob job = new EditJob(nextJob++, owner(who).equals(target) ? target : owner(who), redo ? "redo" : "undo",
                    w, src, EditJob.Mode.APPLY, new EditJob.Sink() {
                @Override
                public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
                }
            }, null, Long.MAX_VALUE, System::nanoTime);
            job.onEnd(() -> {
                w.close();
                jobs.remove(job.id());
                if (job.state() != EditJob.State.DONE) {
                    tell(owner(who), who, msg.get("undo-cancelled", "id", String.valueOf(e.id())));
                    return;
                }
                history.mark(e, redo ? HistoryStore.State.DONE : HistoryStore.State.UNDONE);
                Component m = msg.get(redo ? "redo-done" : "undo-done", "id", String.valueOf(e.id()), "kind", e.kind(),
                        "detail", e.detail(), "changed", fmt(job.changed()), "player", targetName);
                tell(owner(who), who, m);
                if (job.skipped() > 0) tell(owner(who), who, msg.get("undo-skipped", "skipped", fmt(job.skipped())));
            });
            jobs.put(job.id(), job);
            submit(job);
        }
    }

    // ------------------------------------------------------------------ tarea del tick

    /** Un trabajo armado por fuera (selftest), con el mismo planificador. */
    public void submitJob(EditJob job) {
        submit(job);
    }

    private void submit(EditJob job) {
        scheduler.submit(job);
        ensureTask();
    }

    private void ensureTask() {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        scheduler.tick();
        if (++ticks % 10 == 0) {
            progress();
            expire();
        }
        boolean anyPending = sessions.values().stream().anyMatch(s -> s.pending != null);
        if (scheduler.idle() && !anyPending && task != null) {
            task.cancel();
            task = null;
        }
    }

    /** El avance en la barra de acción de cada dueño (H5 CA4). */
    private void progress() {
        for (Scheduler.Job j : scheduler.all()) {
            if (!(j instanceof EditJob e) || e.state() != EditJob.State.RUNNING || e.mode() == EditJob.Mode.PREVIEW) continue;
            Player p = Bukkit.getPlayer(e.owner());
            if (p == null) continue;
            p.sendActionBar(plugin.messages().get("progress", "kind", e.label(),
                    "percent", String.valueOf((int) Math.floor(e.progress() * 100)), "done", fmt(e.visited()), "total", fmt(e.volume())));
        }
    }

    private void expire() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Session> en : sessions.entrySet()) {
            Session s = en.getValue();
            if (s.pending != null && now > s.pending.expires) {
                dropPending(s);
                tell(en.getKey(), null, plugin.messages().get("pending-expired"));
            }
        }
    }

    /** La última obra terminada (o cortada): su nombre, bloques, ticks y su tick más largo, para /bwr jobs. */
    private EditJob lastFinished;

    public EditJob lastFinished() {
        return lastFinished;
    }

    private void learn(EditJob job) {
        lastFinished = job;
        if (job.visited() < 5000 || job.nanos() <= 0) return;
        double rate = job.visited() / (job.nanos() / 1e6);
        blocksPerMs = blocksPerMs * 0.5 + rate * 0.5;
    }

    /** Un jugador se fue: su vista previa y lo que esperaba confirmación se descartan (sus operaciones siguen). */
    public void quit(Player p) {
        Session s = sessions.remove(p.getUniqueId());
        if (s != null) dropPending(s);
    }

    public void shutdown() {
        for (Session s : sessions.values()) dropPending(s);
        scheduler.shutdown();
        if (task != null) task.cancel();
        task = null;
    }

    public List<EditJob> jobs() {
        return new ArrayList<>(jobs.values());
    }

    // ------------------------------------------------------------------ utilidades

    private PaperWorld world(World world, EditSource src) {
        return new PaperWorld(plugin, world, blocks, ChunkOrder.chunks(src.bounds()));
    }

    private void tell(UUID owner, CommandSender fallback, Component m) {
        Player p = Bukkit.getPlayer(owner);
        if (p != null) p.sendMessage(m);
        else if (fallback != null && !(fallback instanceof Player)) fallback.sendMessage(m);
    }

    public static String fmt(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    public static String seconds(double s) {
        if (s < 1) return "less than a second";
        if (s < 90) return String.format(Locale.ROOT, "%.0f s", s);
        return String.format(Locale.ROOT, "%.1f min", s / 60);
    }

    public Optional<Pending> pending(UUID owner) {
        return Optional.ofNullable(session(owner).pending);
    }
}
