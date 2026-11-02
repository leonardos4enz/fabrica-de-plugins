package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.config.FormulaSettings;
import dev.leo.worldcraft.config.Limits;
import dev.leo.worldcraft.edit.EditSource;
import dev.leo.worldcraft.edit.MapSource;
import dev.leo.worldcraft.edit.Snapshot;
import dev.leo.worldcraft.formula.Formula;
import dev.leo.worldcraft.formula.Shapes;
import dev.leo.worldcraft.formula.Space;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Region;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Spec 034 (plan §1.3): el cálculo de las fórmulas en su hilo, con el plazo de /wc timeout, y lo que comparten los
 * comandos y el pincel (deform). Lo que se pone va después por el motor de la 025.
 */
public final class Formulas {
    private final Worldcraft plugin;
    private final ExecutorService thread;

    public Formulas(Worldcraft plugin, ExecutorService thread) {
        this.plugin = plugin;
        this.thread = thread;
    }

    private FormulaSettings settings() {
        return plugin.config().formulas();
    }

    /** Los milisegundos que puede calcular quien lo pide (H6 CA2). */
    public int timeoutMs(CommandSender s) {
        int t = plugin.edits().session(Edits.owner(s)).formulaTimeout;
        return t < 0 ? settings().timeoutMs() : Math.min(t, settings().maxTimeoutMs());
    }

    /** Un trabajo con el plazo de quien lo pide, que empieza ahora. */
    public Shapes.Work work(CommandSender s) {
        return new Shapes.Work(System.nanoTime() + timeoutMs(s) * 1_000_000L);
    }

    /**
     * Calcula en el hilo de fórmulas y sigue en el principal. Si se corta (plazo, vueltas) lo dice con hasta dónde llegó
     * y corre failed; total es lo que se iba a evaluar.
     */
    public <T> void compute(CommandSender s, Shapes.Work work, long total, Supplier<T> job, Consumer<T> then, Runnable failed) {
        CompletableFuture.supplyAsync(job, thread).whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err == null) {
                then.accept(v);
                return;
            }
            Throwable e = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
            var msg = plugin.messages();
            if (e instanceof Formula.TimedOut) {
                s.sendMessage(msg.get("formula-timeout", "ms", String.valueOf(timeoutMs(s)), "done", Edits.fmt(work.evaluated),
                        "total", Edits.fmt(total), "max", String.valueOf(settings().maxTimeoutMs())));
            } else if (e instanceof Formula.TooManySteps) {
                s.sendMessage(msg.get("formula-steps", "max", Edits.fmt(settings().maxSteps()), "pos", work.x + " " + work.y + " " + work.z));
            } else if (e instanceof IllegalArgumentException) {
                s.sendMessage(msg.get("error", "message", e.getMessage()));
            } else {
                plugin.getLogger().log(Level.SEVERE, "Formula calculation failed", e);
                s.sendMessage(msg.get("compute-error"));
            }
            if (failed != null) failed.run();
        }));
    }

    /**
     * Una deformación lista para pedir (H3): la fuente, su caja, cuántos bloques cambian y los estados que pone (los
     * leídos, para los bloques prohibidos).
     */
    public record Prepared(Supplier<EditSource> source, Box bounds, long volume, Collection<Integer> placed) {
    }

    /**
     * deform (H3, H7): calcula de dónde viene cada bloque, lee esa caja por tandas (también lo de fuera de la región) y da
     * la obra. Si nada cambia o se pasa del tope, lo dice y corre failed.
     */
    public void deform(CommandSender s, World w, Region region, Formula f, Space sp, String label, Consumer<Prepared> then, Runnable failed) {
        Shapes.Work work = work(s);
        long seed = ThreadLocalRandom.current().nextLong();
        long total = region.volume();
        compute(s, work, total, () -> Shapes.deform(region, f, sp, seed, work), m -> {
            var msg = plugin.messages();
            if (m.size() == 0) {
                s.sendMessage(msg.get("deform-nothing"));
                if (failed != null) failed.run();
                return;
            }
            PaperBlocks blocks = plugin.edits().blocks();
            int air = blocks.air();
            Box from = m.sourceBounds().clampY(w.getMinHeight(), w.getMaxHeight() - 1);
            if (from == null) {
                // todo viene de fuera del alto del mundo: aire
                MapSource probe = new MapSource(m.targets(), m.sources(), null, air);
                then.accept(new Prepared(probe::fresh, probe.bounds(), m.size(), Set.of(air)));
                return;
            }
            Limits.Resolved lim = plugin.edits().limits(s);
            if (from.volume() > lim.maxBlocks() || from.volume() > Integer.MAX_VALUE - 8) {
                s.sendMessage(msg.get("deform-too-far", "blocks", Edits.fmt(from.volume()), "max", Edits.fmt(lim.maxBlocks()),
                        "groups", String.join(", ", lim.groups())));
                if (failed != null) failed.run();
                return;
            }
            Snapshot snap = new Snapshot(from);
            plugin.edits().readOrCancel(s, w, from, label, snap, () -> {
                Set<Integer> placed = new HashSet<>(snap.distinct());
                placed.add(air);
                MapSource probe = new MapSource(m.targets(), m.sources(), snap, air);
                then.accept(new Prepared(probe::fresh, probe.bounds(), m.size(), placed));
            }, failed == null ? () -> {
            } : failed);
        }, failed);
    }
}
