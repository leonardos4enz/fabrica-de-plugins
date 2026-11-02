package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.config.Limits;
import dev.leo.worldcraft.config.Settings;
import dev.leo.worldcraft.api.EditResult;
import dev.leo.worldcraft.api.event.WorldcraftEditDoneEvent;
import dev.leo.worldcraft.api.event.WorldcraftEditEvent;
import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.edit.EditJob;
import dev.leo.worldcraft.edit.EditSource;
import dev.leo.worldcraft.edit.Scheduler;
import dev.leo.worldcraft.edit.UndoSource;
import dev.leo.worldcraft.history.HistoryStore;
import dev.leo.worldcraft.history.Journal;
import dev.leo.worldcraft.history.JournalSink;
import dev.leo.worldcraft.history.RemapSink;
import dev.leo.worldcraft.entity.EntityCopy;
import dev.leo.worldcraft.entity.EntitySink;
import dev.leo.worldcraft.entity.EntityStage;
import dev.leo.worldcraft.entity.EntityWorld;
import dev.leo.worldcraft.entity.UndoEntities;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.ChunkOrder;
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
     * @param capture    para cut: a dónde va lo que había (se crea al aplicar)
     * @param preview    la fuente de la vista previa (spec 026: la piel del terreno); null = la misma que la obra
     * @param note       lo que se agrega al mensaje de la vista previa (semilla, medidas); vacío si nada
     * @param rerollable la vista previa ofrece [Another] (/wc reroll)
     * @param after      spec 028: lo que se hace cuando la obra termina bien (stack --select, el vidrio de up); o null
     */
    public record Op(String kind, String detail, World world, Box bounds, long volume, Supplier<EditSource> source,
                     Collection<Integer> placed, Supplier<EditJob.Capture> capture, Supplier<EditSource> preview,
                     String note, boolean rerollable, Runnable after, EntityWork entities, BiomeWork biomes) {
        public Op(String kind, String detail, World world, Box bounds, long volume, Supplier<EditSource> source,
                  Collection<Integer> placed, Supplier<EditJob.Capture> capture, Supplier<EditSource> preview,
                  String note, boolean rerollable, Runnable after, EntityWork entities) {
            this(kind, detail, world, bounds, volume, source, placed, capture, preview, note, rerollable, after, entities, null);
        }

        public Op(String kind, String detail, World world, Box bounds, long volume, Supplier<EditSource> source,
                  Collection<Integer> placed, Supplier<EditJob.Capture> capture, Supplier<EditSource> preview,
                  String note, boolean rerollable, Runnable after) {
            this(kind, detail, world, bounds, volume, source, placed, capture, preview, note, rerollable, after, null);
        }

        public Op(String kind, String detail, World world, Box bounds, long volume, Supplier<EditSource> source,
                  Collection<Integer> placed, Supplier<EditJob.Capture> capture, Supplier<EditSource> preview,
                  String note, boolean rerollable) {
            this(kind, detail, world, bounds, volume, source, placed, capture, preview, note, rerollable, null);
        }

        public Op(String kind, String detail, World world, Box bounds, long volume, Supplier<EditSource> source,
                  Collection<Integer> placed, Supplier<EditJob.Capture> capture) {
            this(kind, detail, world, bounds, volume, source, placed, capture, null, "", false, null);
        }

        /** La misma operación con algo que hacer al terminar. */
        public Op then(Runnable r) {
            return new Op(kind, detail, world, bounds, volume, source, placed, capture, preview, note, rerollable, r, entities, biomes);
        }

        /** Spec 031: la misma operación con entidades. */
        public Op with(EntityWork w) {
            return new Op(kind, detail, world, bounds, volume, source, placed, capture, preview, note, rerollable, after, w, biomes);
        }

        /** Spec 033: la misma operación con biomas. */
        public Op with(BiomeWork b) {
            return new Op(kind, detail, world, bounds, volume, source, placed, capture, preview, note, rerollable, after, entities, b);
        }
    }

    /** Spec 033: arma la etapa de biomas con el mundo de Paper y el diario de la obra. */
    public interface BiomeMaker {
        EntityStage make(dev.leo.worldcraft.biome.BiomeWorld world, dev.leo.worldcraft.biome.BiomeSink sink);
    }

    /**
     * Spec 033 (plan §1.1): lo que una obra hace con los biomas, después de los bloques y las entidades.
     *
     * @param note lo que se dice en la vista previa («biomes are not shown»), o vacío
     */
    public record BiomeWork(BiomeMaker maker, String note) {
    }

    /** Spec 031: arma una etapa de entidades con el mundo, el diario, el tope del grupo y la etapa anterior (o null). */
    public interface StageMaker {
        EntityStage make(EntityWorld world, EntitySink sink, int max, EntityStage before);
    }

    /**
     * Spec 031: lo que una obra hace con las entidades (plan §1.3).
     *
     * @param creates cuántas va a crear (para max_entities); las que quita se cortan en el tope
     * @param types   los tipos que va a crear, con lo montado (para blocked_entities)
     * @param note    lo que se dice en la vista previa («3 entities will be added»), o vacío
     */
    public record EntityWork(StageMaker before, StageMaker after, int creates, java.util.Set<String> types, String note) {
    }

    /** Una operación esperando /wc confirm, quizá con su vista previa. */
    public static final class Pending {
        final Op op;
        final String ownerName;
        long expires;
        PreviewSink preview;
        EditJob previewJob;
        /** Spec 029: algo que no es una obra y también pide /wc confirm (clearhistory); op es null. */
        Runnable action;

        Pending(Op op, String ownerName, long expires) {
            this.op = op;
            this.ownerName = ownerName;
            this.expires = expires;
        }
    }

    private final Worldcraft plugin;
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

    public Edits(Worldcraft plugin, PaperBlocks blocks, HistoryStore history, ExecutorService io) {
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
    public void request(CommandSender who, Op requested, boolean preview) {
        UUID owner = owner(who);
        Limits.Resolved lim = limits(who);
        var msg = plugin.messages();
        // Spec 029 H4: la máscara global deja fuera lo que no pasa, en la obra y en su vista previa; se dice siempre.
        Mask gmask = session(owner).gmask;
        Op op = gmask == null ? requested : masked(requested, gmask);
        if (gmask != null) who.sendMessage(msg.get("gmask-applies", "mask", gmask.text()));
        Component refused = refusal(op, lim, owner);
        if (refused != null) {
            who.sendMessage(refused);
            return;
        }
        // Spec 037 H4 CA1: otro plugin puede detenerla, después de los límites y antes de la vista previa.
        WorldcraftEditEvent ev = fire(who, owner, who.getName(), null, op.kind(), op.detail(), op.world(), op.bounds(), op.volume());
        if (ev.isCancelled()) {
            who.sendMessage(stopped(ev));
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
            pend.previewJob = new EditJob(nextJob++, owner, "preview", w, (op.preview() != null ? op.preview() : op.source()).get(), EditJob.Mode.PREVIEW,
                    pend.preview, null, settings().previewMaxBlocks(), System::nanoTime);
            EditJob pj = pend.previewJob;
            pj.onEnd(() -> {
                w.close();
                jobs.remove(pj.id());
                if (pj.state() == EditJob.State.DONE) {
                    if (op.preview() != null) {
                        // Terreno (spec 026 H1 CA4): la piel, con la semilla y los botones.
                        who.sendMessage(msg.get(pj.truncated() ? "terrain-preview-partial" : "terrain-preview-ready",
                                "kind", op.kind(), "blocks", fmt(op.volume()), "note", op.note(),
                                "seconds", String.valueOf(settings().confirmSeconds())));
                        who.sendMessage(msg.get(op.rerollable() ? "terrain-buttons" : "terrain-buttons-no-reroll"));
                    } else {
                        who.sendMessage(msg.get(pj.truncated() ? "preview-partial" : "preview-ready",
                                "shown", fmt(pend.preview.shown()), "blocks", fmt(op.volume()),
                                "seconds", String.valueOf(settings().confirmSeconds())));
                    }
                }
            });
            ses.pending = pend;
            jobs.put(pj.id(), pj);
            submit(pj);
            who.sendMessage(msg.get("preview-start", "kind", op.kind(), "blocks", fmt(op.volume())));
            if (op.entities() != null && !op.entities().note().isEmpty()) who.sendMessage(msg.get("preview-entities", "note", op.entities().note()));
            if (op.biomes() != null && !op.biomes().note().isEmpty()) who.sendMessage(msg.get("preview-entities", "note", op.biomes().note()));
            return;
        }
        // El dueño puede apagar la confirmación (confirm.enabled); --preview sigue esperando su confirm.
        if (settings().confirmEnabled() && op.volume() > settings().confirmAbove()) {
            ses.pending = new Pending(op, name, expires);
            who.sendMessage(msg.get(op.preview() != null ? "terrain-confirm-needed" : "confirm-needed", "kind", op.kind(), "blocks", fmt(op.volume()),
                    "time", seconds(estimateSeconds(op.volume())), "seconds", String.valueOf(settings().confirmSeconds())));
            ensureTask();
            return;
        }
        run(who, owner, name, op, lim, null, null);
    }

    /**
     * Spec 037: quién pide una obra por la API. who recibe los mensajes (el jugador de as(), o null: un plugin a su
     * nombre no habla en el chat); limits ya resueltos (los del jugador o los de api.plugin_groups).
     */
    public record Caller(UUID owner, String name, CommandSender who, Limits.Resolved limits, org.bukkit.plugin.Plugin plugin) {
    }

    /**
     * Spec 037 H3: una obra de la API. Las mismas revisiones y el mismo evento que un comando, sin vista previa ni
     * confirmación (la pidió un plugin); el resultado llega a done al terminar (o al rechazarla).
     */
    public void submit(Caller c, Op op, java.util.function.Consumer<EditResult> done) {
        Component refused = refusal(op, c.limits(), c.owner());
        if (refused != null) {
            if (c.who() != null) c.who().sendMessage(refused);
            done.accept(EditResult.refused(op.kind(), plain(refused)));
            return;
        }
        WorldcraftEditEvent ev = fire(c.who(), c.owner(), c.name(), c.plugin(), op.kind(), op.detail(), op.world(), op.bounds(), op.volume());
        if (ev.isCancelled()) {
            Component m = stopped(ev);
            if (c.who() != null) c.who().sendMessage(m);
            done.accept(EditResult.refused(op.kind(), plain(m)));
            return;
        }
        run(c.who(), c.owner(), c.name(), op, c.limits(), c.plugin(), done);
    }

    /** Los límites del grupo (mundo, tope, bloques y entidades prohibidos, obras a la vez): el mensaje, o null si pasa. */
    private Component refusal(Op op, Limits.Resolved lim, UUID owner) {
        var msg = plugin.messages();
        if (!lim.world(op.world().getName())) return msg.get("limit-world", "world", op.world().getName());
        if (op.volume() > lim.maxBlocks()) {
            return msg.get("limit-blocks", "blocks", fmt(op.volume()), "max", fmt(lim.maxBlocks()), "groups", String.join(", ", lim.groups()));
        }
        for (int s : op.placed()) {
            if (lim.isBlocked(blocks.table().material(s), blocks.table().properties(s))) {
                return msg.get("limit-blocked", "block", blocks.table().material(s).replace("minecraft:", ""));
            }
        }
        if (op.entities() != null) {
            Component e = entityRefusal(lim, op.entities().creates(), op.entities().types());
            if (e != null) return e;
        }
        if (busy(owner) >= lim.maxJobs()) return msg.get("limit-jobs", "max", String.valueOf(lim.maxJobs()));
        return null;
    }

    /** Spec 037 H4 CA1: WorldcraftEditEvent con lo que la obra puede tocar (box null = sin bloques: una caja vacía en 0). */
    private WorldcraftEditEvent fire(CommandSender who, UUID owner, String name, org.bukkit.plugin.Plugin from, String kind, String detail,
                                     World world, Box box, long volume) {
        WorldcraftEditEvent ev = new WorldcraftEditEvent(who, owner, name, from, kind, detail, world, min(box), max(box), volume);
        Bukkit.getPluginManager().callEvent(ev);
        return ev;
    }

    private void fireDone(CommandSender who, UUID owner, String name, org.bukkit.plugin.Plugin from, String kind, String detail,
                          World world, Box box, long volume, int id, long changed, boolean ok) {
        Bukkit.getPluginManager().callEvent(new WorldcraftEditDoneEvent(who, owner, name, from, kind, detail, world, min(box), max(box),
                volume, id, changed, ok));
    }

    private static int[] min(Box b) {
        return b == null ? new int[3] : new int[]{b.minX(), b.minY(), b.minZ()};
    }

    private static int[] max(Box b) {
        return b == null ? new int[3] : new int[]{b.maxX(), b.maxY(), b.maxZ()};
    }

    private Component stopped(WorldcraftEditEvent ev) {
        String r = ev.getCancelReason();
        return plugin.messages().get("edit-stopped", "reason", r.isBlank() ? "" : ": " + r);
    }

    /** El texto de un mensaje sin el prefijo «Worldcraft »» (el motivo de un rechazo de la API). */
    private String plain(Component c) {
        var ser = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        String s = ser.serialize(c), pre = ser.serialize(plugin.messages().get("prefix"));
        return !pre.isEmpty() && s.startsWith(pre) ? s.substring(pre.length()) : s;
    }

    /**
     * Spec 037 F-01: el fin de una obra (evento y resultado de la API) va al tick siguiente. onEnd corre dentro del paso
     * del planificador, con la obra todavía en su cola: un plugin que pedía otra obra o un undo al recibir el resultado
     * recibía «Wait for your running edit to finish» (y contaba para max_jobs).
     */
    private void later(Runnable r) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, r);
        else r.run();
    }

    private static void say(CommandSender who, Component m) {
        if (who != null) who.sendMessage(m);
    }

    /**
     * Spec 031 H7: el tope de entidades que se crean y los tipos prohibidos (también lo montado y el bloque de un
     * falling_block, que pasa por blocked_blocks). Lo dice y devuelve false si no se puede.
     */
    public boolean entitiesAllowed(CommandSender who, Limits.Resolved lim, int creates, Collection<String> types) {
        Component m = entityRefusal(lim, creates, types);
        if (m != null) who.sendMessage(m);
        return m == null;
    }

    private Component entityRefusal(Limits.Resolved lim, int creates, Collection<String> types) {
        var msg = plugin.messages();
        if (creates > lim.maxEntities()) {
            return msg.get("limit-entities", "count", fmt(creates), "max", fmt(lim.maxEntities()), "groups", String.join(", ", lim.groups()));
        }
        for (String t : types) {
            boolean blocked = t.startsWith("block:") ? lim.isBlocked(t.substring(6), Map.of()) : lim.isEntityBlocked(t);
            if (blocked) return msg.get("limit-entity-blocked", "entity", t.replace("block:", "").replace("minecraft:", ""));
        }
        return null;
    }

    public void confirm(CommandSender who) {
        Session ses = session(owner(who));
        Pending p = ses.pending;
        if (p == null) {
            who.sendMessage(plugin.messages().get("nothing-to-confirm"));
            return;
        }
        dropPending(ses);
        if (p.op == null) {
            p.action.run();
            return;
        }
        run(who, owner(who), p.ownerName, p.op, limits(who), null, null);
    }

    /** Spec 029: algo que no es una obra espera /wc confirm (con el mismo plazo que una obra). */
    public void ask(CommandSender who, Runnable action) {
        Session ses = session(owner(who));
        dropPending(ses);
        Pending p = new Pending(null, who.getName(), System.currentTimeMillis() + settings().confirmSeconds() * 1000L);
        p.action = action;
        ses.pending = p;
        ensureTask();
    }

    /** La misma obra con la máscara global: deja (KEEP) lo que no pasa; el azar usa una semilla fija por obra. */
    private Op masked(Op op, Mask gmask) {
        Mask.Compiled mc = compile(gmask, op.world());
        long seed = java.util.concurrent.ThreadLocalRandom.current().nextLong();
        java.util.function.Function<Supplier<EditSource>, Supplier<EditSource>> wrap = sup -> sup == null ? null
                : () -> new dev.leo.worldcraft.edit.MaskedSource(sup.get(), mc, seed);
        return new Op(op.kind(), op.detail() + " (gmask " + gmask.text() + ")", op.world(), op.bounds(), op.volume(),
                wrap.apply(op.source()), op.placed(), op.capture(), wrap.apply(op.preview()), op.note(), op.rerollable(), op.after(), op.entities(), op.biomes());
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

    /**
     * Corre la obra. who recibe los mensajes (null = nadie: un plugin a su nombre); from es el plugin que la pidió por la
     * API (o null) y done recibe el resultado al terminar (o null).
     */
    private void run(CommandSender who, UUID owner, String ownerName, Op op, Limits.Resolved lim, org.bukkit.plugin.Plugin from,
                     java.util.function.Consumer<EditResult> done) {
        var msg = plugin.messages();
        HistoryStore.Entry entry = history.begin(owner, ownerName, op.kind(), op.detail(), op.world().getName(), op.bounds(),
                System.currentTimeMillis());
        EditSource src = op.source().get();
        PaperWorld w = world(op.world(), src);
        JournalSink sink = new JournalSink(history.journal(owner, entry.id()), blocks.table(), io,
                e -> plugin.getLogger().log(Level.SEVERE, "Could not write the undo history of edit #" + entry.id(), e));
        EditJob job = new EditJob(nextJob++, owner, op.kind(), w, src, EditJob.Mode.APPLY, sink,
                op.capture() == null ? null : op.capture().get(), Long.MAX_VALUE, System::nanoTime);
        EntityStage b = null, a = null;
        if (op.entities() != null) {
            PaperEntities ew = new PaperEntities(plugin, op.world());
            int max = lim.maxEntities();
            b = op.entities().before() == null ? null : op.entities().before().make(ew, sink, max, null);
            a = op.entities().after() == null ? null : op.entities().after().make(ew, sink, max, b);
        }
        // Spec 033: los biomas, después de los bloques y las entidades.
        PaperBiomes bw = op.biomes() == null ? null : new PaperBiomes(plugin, op.world());
        if (bw != null) a = EntityStage.chain(a, op.biomes().maker().make(bw, sink));
        if (b != null || a != null) job.stages(b, a);
        long started = System.nanoTime();
        job.onEnd(() -> {
            w.close();
            if (bw != null) bw.close();
            jobs.remove(job.id());
            boolean ok = job.state() == EditJob.State.DONE;
            history.finish(entry, job.changed(), ok);
            learn(job);
            String secs = seconds((System.nanoTime() - started) / 1e9);
            // Spec 031: una obra solo de entidades (butcher, remove) no habla de bloques.
            String doneKey = op.volume() == 0 && op.entities() != null ? "edit-done-entities" : "edit-done";
            // Spec 033: una obra solo de biomas (setbiome) habla de celdas, no de bloques.
            boolean onlyBiomes = op.biomes() != null && job.visited() == 0 && op.entities() == null;
            if (onlyBiomes) doneKey = "edit-done-biomes";
            Component m = ok
                    ? msg.get(doneKey, "kind", op.kind(), "changed", fmt(onlyBiomes ? sink.biomeCells() : job.changed()), "time", secs, "id", String.valueOf(entry.id()))
                    : msg.get("edit-cancelled", "kind", op.kind(), "changed", fmt(job.changed()));
            tell(owner, who, m);
            if (ok && !onlyBiomes && sink.biomeCells() > 0) {
                Component bm = msg.get("edit-biomes", "cells", fmt(sink.biomeCells()));
                tell(owner, who, bm);
                if (owner.equals(CONSOLE) || owner.equals(SELFTEST)) plugin.getLogger().info(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(bm));
            }
            Component em = entityNote(job, sink.added(), sink.removed());
            if (em != null) tell(owner, who, em);
            if (owner.equals(CONSOLE) || owner.equals(SELFTEST)) {
                plugin.getLogger().info(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(m));
                if (em != null) plugin.getLogger().info(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(em));
            }
            if (ok && op.after() != null) op.after().run();
            // Spec 037 H4 CA2 y H3 CA2: el evento de fin y el resultado de la API, ya fuera de la cola (F-01).
            long changed = job.changed();
            later(() -> {
                fireDone(who, owner, ownerName, from, op.kind(), op.detail(), op.world(), op.bounds(), op.volume(), entry.id(), changed, ok);
                if (done != null) done.accept(new EditResult(entry.id(), op.kind(), changed, ok ? EditResult.Status.DONE : EditResult.Status.CANCELLED, ""));
            });
        });
        jobs.put(job.id(), job);
        submit(job);
        if (who == null) return;
        if (scheduler.of(owner).size() > 1) who.sendMessage(msg.get("edit-queued", "kind", op.kind()));
        else who.sendMessage(msg.get("edit-start", "kind", op.kind(), "blocks", fmt(op.volume()),
                "time", seconds(estimateSeconds(op.volume()))));
    }

    /** Spec 031: «3 entities added, 5 removed (3 zombie, 2 skeleton)» y si se cortó en el tope; null si no tocó ninguna. */
    private Component entityNote(EditJob job, int added, int removed) {
        EntityStage.Scan scan = job.before() instanceof EntityStage.Scan s ? s : null;
        if (added == 0 && removed == 0 && (scan == null || !scan.truncated())) return null;
        String types = scan == null || scan.types().isEmpty() ? "" : " (" + typeList(scan.types()) + ")";
        String key = scan != null && scan.truncated() ? "edit-entities-limit" : "edit-entities";
        return plugin.messages().get(key, "added", fmt(added), "removed", fmt(removed), "types", types,
                "max", fmt(scan == null ? 0 : scan.done()));
    }

    /** «3 zombie, 2 skeleton», los más primero. */
    public static String typeList(Map<String, Integer> types) {
        return types.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(6)
                .map(e -> e.getValue() + " " + e.getKey()).collect(java.util.stream.Collectors.joining(", "))
                + (types.size() > 6 ? ", …" : "");
    }

    /** Lee una caja al portapapeles (copy): sin historial; el portapapeles queda al terminar. */
    public void copy(CommandSender who, World world, dev.leo.worldcraft.region.Region region, int ox, int oy, int oz, Runnable done) {
        copy(who, world, region, ox, oy, oz, false, c -> done.run());
    }

    /** Spec 031: con entities, también las entidades de la región (relativas a quien copió); done recibe el portapapeles. */
    public void copy(CommandSender who, World world, dev.leo.worldcraft.region.Region region, int ox, int oy, int oz,
                     boolean entities, java.util.function.Consumer<Clipboard> done) {
        copy(who, world, region, ox, oy, oz, entities, false, done);
    }

    /** Spec 033: con biomes, también el bioma de cada bloque (leído por celdas después de los bloques). */
    public void copy(CommandSender who, World world, dev.leo.worldcraft.region.Region region, int ox, int oy, int oz,
                     boolean entities, boolean biomes, java.util.function.Consumer<Clipboard> done) {
        UUID owner = owner(who);
        // Spec 030: de una forma solo se leen sus bloques; lo de afuera queda «sin leer» y paste lo salta.
        Box box = region.bounds();
        Clipboard clip = new Clipboard(box, ox, oy, oz);
        EntityStage.Scan scan = entities ? scan(who, world, region) : null;
        PaperBiomes bw = biomes ? new PaperBiomes(plugin, world) : null;
        EntityStage after = bw == null ? null : readBiomes(bw, region, clip);
        readWith(who, world, new dev.leo.worldcraft.edit.RegionSource(region, dev.leo.worldcraft.region.Shape.FILL, null, null, 0), "copy",
                (x, y, z, s, tile) -> clip.put(box, x, y, z, s, tile), scan, after, () -> {
            if (bw != null) bw.close();
            if (scan != null) {
                clip.entities(relative(scan.result(), ox, oy, oz));
                if (scan.truncated()) who.sendMessage(plugin.messages().get("copy-entities-limit", "max", fmt(scan.done())));
            }
            session(owner).clipboard(clip, world.getName());
            done.accept(clip);
        });
    }

    /**
     * Spec 036: lee la región de un mundo (el temporal de regen) a un portapapeles nuevo con el origen en la esquina mínima,
     * sin tocar el del jugador; con biomes, también los biomas. done recibe el portapapeles; si se cancela, cancelled.
     */
    public void readClip(CommandSender who, World world, dev.leo.worldcraft.region.Region region, String label, boolean biomes,
                         java.util.function.Consumer<Clipboard> done, Runnable cancelled) {
        Box box = region.bounds();
        Clipboard clip = new Clipboard(box, box.minX(), box.minY(), box.minZ());
        PaperBiomes bw = biomes ? new PaperBiomes(plugin, world) : null;
        EntityStage after = bw == null ? null : readBiomes(bw, region, clip);
        readWith(who, world, new dev.leo.worldcraft.edit.RegionSource(region, dev.leo.worldcraft.region.Shape.FILL, null, null, 0), label,
                (x, y, z, s, tile) -> clip.put(box, x, y, z, s, tile), null, after, () -> {
                    if (bw != null) bw.close();
                    done.accept(clip);
                }, () -> {
                    if (bw != null) bw.close();
                    cancelled.run();
                });
    }

    /** Spec 033: lee los biomas de las celdas de la región al portapapeles (cada bloque de la región toma el de su celda). */
    public static EntityStage readBiomes(dev.leo.worldcraft.biome.BiomeWorld bw, dev.leo.worldcraft.region.Region region, Clipboard clip) {
        Box box = region.bounds();
        return dev.leo.worldcraft.biome.BiomeStage.read(bw, dev.leo.worldcraft.biome.Cells.of(region), (x, y, z, biome) -> {
            for (int dx = 0; dx < 4; dx++) for (int dy = 0; dy < 4; dy++) for (int dz = 0; dz < 4; dz++) {
                int px = x + dx, py = y + dy, pz = z + dz;
                if (box.contains(px, py, pz) && region.contains(px, py, pz)) clip.putBiome(box, px, py, pz, biome);
            }
        });
    }

    /** Spec 033: una máscara compilada que lee el bioma del mundo si lo pide («$desert»). */
    public Mask.Compiled compile(Mask mask, World world) {
        Mask.Compiled c = mask.compile(blocks.table());
        if (mask.needsBiomes()) c.biomes(PaperBiomes.lookup(world));
        return c;
    }

    /** Spec 031: una búsqueda de entidades (sin quitar) para copiar, hasta el tope del grupo. */
    public EntityStage.Scan scan(CommandSender who, World world, dev.leo.worldcraft.region.Region region) {
        return new EntityStage.Scan(new PaperEntities(plugin, world), region, e -> true, false,
                limits(who).maxEntities(), EntitySink.NONE);
    }

    /** Spec 031: de posiciones del mundo a relativas a quien copió. */
    public static List<EntityCopy> relative(List<EntityCopy> list, int ox, int oy, int oz) {
        return list.stream().map(e -> e.shifted(-ox, -oy, -oz)).toList();
    }

    /**
     * Lee una caja por tandas (spec 028 §1.2: la foto de stack, move, hollow, drain…), sin historial. done corre en el
     * hilo principal si terminó; si se canceló, se avisa.
     */
    public void read(CommandSender who, World world, dev.leo.worldcraft.region.Region box, String label, EditJob.Capture into, Runnable done) {
        read(who, world, box, label, into, null, done);
    }

    /** Spec 031: la misma lectura y, antes, una búsqueda de entidades (o null). */
    public void read(CommandSender who, World world, dev.leo.worldcraft.region.Region box, String label, EditJob.Capture into,
                     EntityStage.Scan scan, Runnable done) {
        EditSource src = new dev.leo.worldcraft.edit.RegionSource(box, dev.leo.worldcraft.region.Shape.FILL, null, null, 0);
        readWith(who, world, src, label, into, scan, done);
    }

    /** Lo mismo con una fuente de lectura propia (spec 028: la cima de cada columna). */
    public void readWith(CommandSender who, World world, EditSource src, String label, EditJob.Capture into, Runnable done) {
        readWith(who, world, src, label, into, null, done);
    }

    /** Spec 032: la misma lectura de una caja y, si se cancela, cancelled (el pincel deja de esperar). */
    public void readOrCancel(CommandSender who, World world, dev.leo.worldcraft.region.Region box, String label, EditJob.Capture into,
                     Runnable done, Runnable cancelled) {
        readWith(who, world, new dev.leo.worldcraft.edit.RegionSource(box, dev.leo.worldcraft.region.Shape.FILL, null, null, 0),
                label, into, null, done, cancelled);
    }

    private void readWith(CommandSender who, World world, EditSource src, String label, EditJob.Capture into,
                          EntityStage.Scan scan, Runnable done) {
        readWith(who, world, src, label, into, scan, done, null);
    }

    private void readWith(CommandSender who, World world, EditSource src, String label, EditJob.Capture into,
                          EntityStage.Scan scan, Runnable done, Runnable cancelled) {
        readWith(who, world, src, label, into, scan, null, done, cancelled);
    }

    private void readWith(CommandSender who, World world, EditSource src, String label, EditJob.Capture into,
                          EntityStage.Scan scan, EntityStage after, Runnable done) {
        readWith(who, world, src, label, into, scan, after, done, null);
    }

    /** Spec 033: una lectura con una etapa después de los bloques (biomeinfo); src puede no tener bloques. */
    public void readStage(CommandSender who, World world, EditSource src, String label, EntityStage after, Runnable done) {
        readWith(who, world, src, label, (x, y, z, s, t) -> {
        }, null, after, done, null);
    }

    private void readWith(CommandSender who, World world, EditSource src, String label, EditJob.Capture into,
                          EntityStage.Scan scan, EntityStage after, Runnable done, Runnable cancelled) {
        UUID owner = owner(who);
        PaperWorld w = world(world, src);
        EditJob job = new EditJob(nextJob++, owner, label, w, src, EditJob.Mode.READ, new EditJob.Sink() {
            @Override
            public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
            }
        }, into, Long.MAX_VALUE, System::nanoTime);
        if (scan != null || after != null) job.stages(scan, after);
        job.onEnd(() -> {
            w.close();
            jobs.remove(job.id());
            // Spec 028: lo que sigue (pedir la obra) corre en el tick siguiente, cuando la lectura ya salió del planificador;
            // si no, la obra decía «waiting for your previous edit» por la lectura que ya había terminado.
            if (job.state() == EditJob.State.DONE) Bukkit.getScheduler().runTask(plugin, done);
            else {
                if (cancelled != null) cancelled.run();
                tell(owner, who, plugin.messages().get(label.equals("copy") ? "copy-cancelled" : "read-cancelled", "kind", label));
            }
        });
        jobs.put(job.id(), job);
        submit(job);
    }

    /**
     * Lee el suelo para el terreno (spec 026 H5 CA2): un trabajo de lectura por tandas, sin historial, como copy. done
     * corre en el hilo principal si terminó; si se canceló, cancelled y se avisa.
     */
    public void scan(CommandSender who, World world, dev.leo.worldcraft.terrain.ColumnScan scan, Runnable done, Runnable cancelled) {
        UUID owner = owner(who);
        PaperWorld w = world(world, scan);
        EditJob job = new EditJob(nextJob++, owner, "reading the ground", w, scan, EditJob.Mode.READ, new EditJob.Sink() {
            @Override
            public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
            }
        }, scan, Long.MAX_VALUE, System::nanoTime);
        job.onEnd(() -> {
            w.close();
            jobs.remove(job.id());
            if (job.state() == EditJob.State.DONE) done.run();
            else {
                cancelled.run();
                tell(owner, who, plugin.messages().get("terrain-scan-cancelled"));
            }
        });
        jobs.put(job.id(), job);
        submit(job);
    }

    /** Las obras de un dueño (en marcha o esperando), sin contar vistas previas. */
    public int running(UUID owner) {
        return busy(owner);
    }

    /** Deshace (o rehace) las últimas n operaciones de target; quien lo pide puede ser otro (con permiso). */
    public void undo(CommandSender who, UUID target, String targetName, int n, boolean redo) {
        undo(who, owner(who), who.getName(), target, targetName, n, redo, null, null);
    }

    /**
     * Spec 037: deshacer con quien lo pide aparte (actor; who recibe los mensajes o es null) y el resultado en done: los
     * bloques de todas, el número de la última. Antes se dispara WorldcraftEditEvent por cada obra: si otro plugin
     * cancela una, no se deshace ninguna.
     */
    public void undo(CommandSender who, UUID actor, String actorName, UUID target, String targetName, int n, boolean redo,
                     org.bukkit.plugin.Plugin from, java.util.function.Consumer<EditResult> done) {
        var msg = plugin.messages();
        String kind = redo ? "redo" : "undo";
        if (!scheduler.of(target).isEmpty() && target.equals(actor)) {
            Component m = msg.get("undo-busy");
            say(who, m);
            if (done != null) done.accept(EditResult.refused(kind, plain(m)));
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
            Component m = msg.get(redo ? "nothing-to-redo" : "nothing-to-undo", "player", targetName);
            say(who, m);
            if (done != null) done.accept(EditResult.refused(kind, plain(m)));
            return;
        }
        for (HistoryStore.Entry e : picks) {
            World world = Bukkit.getWorld(e.world());
            if (world == null) {
                Component m = msg.get("undo-no-world", "id", String.valueOf(e.id()), "world", e.world());
                say(who, m);
                if (done != null) done.accept(EditResult.refused(kind, plain(m)));
                return;
            }
            // Spec 037 H4 CA1: deshacer también es una obra que un plugin de protección puede detener.
            WorldcraftEditEvent ev = fire(who, actor, actorName, from, kind, "#" + e.id() + " " + e.kind() + " " + e.detail(), world,
                    e.bounds(), e.bounds() == null ? 0 : e.bounds().volume());
            if (ev.isCancelled()) {
                Component m = stopped(ev);
                say(who, m);
                if (done != null) done.accept(EditResult.refused(kind, plain(m)));
                return;
            }
        }
        long[] total = new long[1];
        int[] left = {picks.size()};
        boolean[] allOk = {true};
        for (HistoryStore.Entry e : picks) {
            World world = Bukkit.getWorld(e.world());
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
            // Spec 031 (plan §1.4): deshacer quita lo que la obra creó, devuelve los bloques y recrea lo que quitó;
            // rehacer, al revés. Los uuid nuevos van al final del mismo diario.
            RemapSink remaps = new RemapSink(path, io,
                    ex -> plugin.getLogger().log(Level.SEVERE, "Could not write the undo history of edit #" + e.id(), ex));
            EntityStage.Kill kill = UndoEntities.kill(new PaperEntities(plugin, world), data, redo);
            EntityStage.Spawn spawn = UndoEntities.spawn(new PaperEntities(plugin, world), data, redo, remaps);
            // Spec 033: los biomas vuelven después (deshacer seguro, celda por celda).
            PaperBiomes bw = new PaperBiomes(plugin, world);
            dev.leo.worldcraft.biome.BiomeStage biomes = dev.leo.worldcraft.biome.BiomeStage.undo(bw,
                    () -> data.isDone() && !data.isCompletedExceptionally() ? data.join() : null, redo);
            EditJob job = new EditJob(nextJob++, actor.equals(target) ? target : actor, redo ? "redo" : "undo",
                    w, src, EditJob.Mode.APPLY, remaps, null, Long.MAX_VALUE, System::nanoTime).stages(kill, EntityStage.chain(spawn, biomes));
            job.onEnd(() -> {
                w.close();
                bw.close();
                jobs.remove(job.id());
                boolean ok = job.state() == EditJob.State.DONE;
                long changed = job.changed();
                later(() -> { // F-01: fuera de la cola del planificador
                    fireDone(who, actor, actorName, from, kind, "#" + e.id() + " " + e.kind() + " " + e.detail(), world, e.bounds(),
                            e.bounds() == null ? 0 : e.bounds().volume(), e.id(), changed, ok);
                    total[0] += changed;
                    allOk[0] &= ok;
                    if (--left[0] == 0 && done != null) done.accept(new EditResult(e.id(), kind, total[0],
                            allOk[0] ? EditResult.Status.DONE : EditResult.Status.CANCELLED, ""));
                });
                if (!ok) {
                    tell(actor, who, msg.get("undo-cancelled", "id", String.valueOf(e.id())));
                    return;
                }
                history.mark(e, redo ? HistoryStore.State.DONE : HistoryStore.State.UNDONE);
                // Spec 031: una obra solo de entidades (butcher, remove) no habla de bloques.
                boolean onlyEntities = job.changed() == 0 && kill.done() + spawn.done() > 0;
                boolean onlyBiomes = job.changed() == 0 && !onlyEntities && biomes.done() + biomes.skipped() > 0;
                String key = onlyEntities ? (redo ? "redo-done-entities" : "undo-done-entities")
                        : onlyBiomes ? (redo ? "redo-done-biomes" : "undo-done-biomes") : (redo ? "redo-done" : "undo-done");
                Component m = msg.get(key, "id", String.valueOf(e.id()), "kind", e.kind(),
                        "detail", e.detail(), "changed", fmt(job.changed()), "player", targetName);
                tell(actor, who, m);
                if (job.skipped() > 0) tell(actor, who, msg.get("undo-skipped", "skipped", fmt(job.skipped())));
                if (kill.done() + spawn.done() > 0) tell(actor, who, msg.get("undo-entities",
                        "back", fmt(spawn.done()), "removed", fmt(kill.done())));
                if (kill.skipped() + spawn.skipped() > 0) tell(actor, who, msg.get("undo-entities-skipped",
                        "skipped", fmt(kill.skipped() + spawn.skipped())));
                if (biomes.done() > 0 && !onlyBiomes) tell(actor, who, msg.get("undo-biomes", "cells", fmt(biomes.done())));
                if (biomes.skipped() > 0) tell(actor, who, msg.get("undo-biomes-skipped", "skipped", fmt(biomes.skipped())));
            });
            jobs.put(job.id(), job);
            submit(job);
        }
    }

    // ------------------------------------------------------------------ tarea del tick

    /**
     * F-04: el primer bloque de la primera obra tras arrancar tardaba ~30 ms (código que corre por primera vez: leer,
     * convertir y poner un estado, ver si guarda datos), todo dentro de un tick. Al habilitar el plugin se pasa una vez
     * por esos caminos en el bloque de aparición del mundo principal, poniéndole su mismo estado (no cambia nada).
     * Devuelve los milisegundos que tardó.
     */
    public double warmUp() {
        long t0 = System.nanoTime();
        World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (w == null) return 0;
        var spawn = w.getSpawnLocation();
        int x = spawn.getBlockX(), y = Math.max(w.getMinHeight(), Math.min(spawn.getBlockY(), w.getMaxHeight() - 1)), z = spawn.getBlockZ();
        PaperWorld pw = new PaperWorld(plugin, w, blocks, new long[]{ChunkOrder.pack(x >> 4, z >> 4)});
        try {
            if (!pw.ready(x >> 4, z >> 4)) return 0; // el chunk no está cargado: se calienta con la primera obra
            int s = pw.get(x, y, z);
            pw.tile(x, y, z);
            pw.set(x, y, z, s);
            // Poner el mismo estado no llega al código que cambia un bloque (luz, mapas de altura): en el techo del
            // mundo, si ahí hay aire, se pone piedra y se vuelve a aire en el mismo tick.
            int top = w.getMaxHeight() - 1;
            if (blocks.isAir(pw.get(x, top, z))) {
                int air = pw.get(x, top, z);
                pw.set(x, top, z, blocks.id(org.bukkit.Material.STONE.createBlockData()));
                pw.set(x, top, z, air);
            }
            blocks.table().state(s);
            // Spec 027 F-01: los datos de un cofre (copiar, pegar, guardar un esquema) también pagan código frío.
            try {
                org.bukkit.inventory.ItemStack one = new org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE);
                byte[] raw = org.bukkit.inventory.ItemStack.serializeItemsAsBytes(new org.bukkit.inventory.ItemStack[]{one});
                byte[] tile = new byte[raw.length + 1];
                tile[0] = 'C';
                System.arraycopy(raw, 0, tile, 1, raw.length);
                // De la forma de la 025 a la estándar y de vuelta a un ítem: lo que hacen guardar y pegar un esquema.
                byte[] std = TileNbt.standard(tile, "minecraft:chest");
                TileNbt.items(dev.leo.worldcraft.schem.TileData.decode(std), 27);
            } catch (java.io.IOException | RuntimeException ignored) {
                // solo es calentar
            }
            blocks.data(blocks.air());
            blocks.hasTile(blocks.air());
            // Spec 033: el primer árbol capturado paga el código frío del generador (sin tocar el mundo: se captura).
            try {
                Trees.capture(w, blocks, x, Math.min(top - 20, y + 30), z, dev.leo.worldcraft.nature.TreeKind.OAK, new java.util.Random(1));
                PaperBiomes.names();
                w.getBiome(x, y, z);
            } catch (RuntimeException ignored) {
                // solo es calentar
            }
            plugin.messages().text("edit-done", "kind", "set", "changed", "0", "time", "0", "id", "0");
            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(plugin.messages().text("progress",
                    "kind", "set", "percent", "0", "done", "0", "total", "0"));
            new org.bukkit.configuration.file.YamlConfiguration().saveToString();
        } finally {
            pw.close();
        }
        return (System.nanoTime() - t0) / 1e6;
    }

    /** Un trabajo armado por fuera (selftest), con el mismo planificador. */
    public void submitJob(EditJob job) {
        submit(job);
    }

    /** Spec 027: un trabajo de pasos sueltos (TaskJob) dentro del mismo presupuesto por tick; make recibe su número. */
    public void submitTask(java.util.function.IntFunction<Scheduler.Job> make) {
        scheduler.submit(make.apply(nextJob++));
        ensureTask();
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

    /** La última obra terminada (o cortada): su nombre, bloques, ticks y su tick más largo, para /wc jobs. */
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
        return new PaperWorld(plugin, world, blocks, src.chunks());
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

    /** Descarta lo que espera confirmación (sin mensaje). */
    public void dropPending(UUID owner) {
        dropPending(session(owner));
    }

    public Optional<Pending> pending(UUID owner) {
        return Optional.ofNullable(session(owner).pending);
    }
}
