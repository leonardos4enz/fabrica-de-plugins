package dev.leo.omnievents.action;

import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.debug.Trace;
import dev.leo.omnievents.expr.ExprException;
import dev.leo.omnievents.rule.Rule;
import org.bukkit.Bukkit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/**
 * Ejecución de una lista de acciones como pila de marcos (plan 001 §3): un delay suspende la ejecución y la reanuda
 * con el scheduler; un evento asíncrono pasa al hilo principal en la primera acción que no es segura.
 */
public final class Execution {
    /** Un marco produce la siguiente acción, {@link #PUSHED} si apiló otro marco, o null si terminó. */
    public interface Frame {
        Object step(Execution exec);
    }

    public static final Object PUSHED = new Object();

    private final OmniEvents plugin;
    private final Rule rule;
    private final int depth;
    private final Deque<Frame> stack = new ArrayDeque<>();
    private final Trace trace;
    private final boolean ownsTrace;
    private final boolean sampled;
    private ActionNode pending;
    private ExecContext pendingCtx;
    private boolean stopped;
    private boolean finished;
    private int iterations;
    private Runnable revertUsage;
    private Runnable onFinish;

    public Execution(OmniEvents plugin, Rule rule, int depth, Trace trace, boolean ownsTrace, boolean sampled) {
        this.ownsTrace = ownsTrace;
        this.sampled = sampled;
        this.plugin = plugin;
        this.rule = rule;
        this.depth = depth;
        this.trace = trace;
    }

    public OmniEvents plugin() {
        return plugin;
    }

    public Rule rule() {
        return rule;
    }

    public int depth() {
        return depth;
    }

    public Trace traceObj() {
        return trace;
    }

    /** ¿Es una simulación (spec 006, H4)? Entonces solo corren las acciones de control; las demás se listan. */
    public boolean simulating() {
        return trace != null && trace.simulation();
    }

    public void trace(String line) {
        if (trace != null) trace.add(line);
    }

    public void onFinish(Runnable r) {
        this.onFinish = r;
    }

    public void revertUsage(Runnable r) {
        this.revertUsage = r;
    }

    /** Acción no_cooldown: esta ejecución no cuenta como uso ni activa el enfriamiento (H8 CA4). */
    public void cancelUsage() {
        if (revertUsage != null) {
            revertUsage.run();
            revertUsage = null;
            trace("  no_cooldown: no cuenta el uso ni el enfriamiento");
        }
    }

    public void stop() {
        stopped = true;
    }

    public boolean finished() {
        return finished;
    }

    /** Cuenta una vuelta de un bucle y corta la ejecución al pasar el tope (constitución III.4). */
    public void countIteration() {
        if (++iterations > plugin.settings().maxLoopIterations()) {
            throw new ActionException("se pasó el tope de " + plugin.settings().maxLoopIterations()
                    + " iteraciones (expressions.max-loop-iterations): ¿un while que nunca termina?");
        }
    }

    // ----- marcos -----

    public void pushBlock(List<ActionNode> nodes, ExecContext ctx) {
        if (nodes == null || nodes.isEmpty()) return;
        stack.push(new BlockFrame(nodes, ctx));
    }

    public void push(Frame f) {
        stack.push(f);
    }

    private final class BlockFrame implements Frame {
        private final List<ActionNode> nodes;
        private final ExecContext ctx;
        private int i;

        BlockFrame(List<ActionNode> nodes, ExecContext ctx) {
            this.nodes = nodes;
            this.ctx = ctx;
        }

        @Override
        public Object step(Execution exec) {
            if (i >= nodes.size()) return null;
            pendingCtx = ctx;
            return nodes.get(i++);
        }
    }

    /** Marco de bucle genérico: next(i) devuelve el contexto de la vuelta i, o null para terminar. */
    public Frame loop(List<ActionNode> body, Function<Integer, ExecContext> next) {
        return new Frame() {
            private int i;

            @Override
            public Object step(Execution exec) {
                ExecContext c = next.apply(i);
                if (c == null) return null;
                i++;
                countIteration();
                pushBlock(body, c);
                return PUSHED;
            }
        };
    }

    // ----- motor -----

    public void start(List<ActionNode> nodes, ExecContext ctx) {
        pushBlock(nodes, ctx);
        resume();
    }

    public void resume() {
        if (!plugin.isEnabled() && !plugin.disabling()) {
            finish();
            return;
        }
        while (!stopped) {
            ActionNode node;
            ExecContext ctx;
            if (pending != null) {
                node = pending;
                ctx = pendingCtx;
                pending = null;
            } else {
                Frame f = stack.peek();
                if (f == null) break;
                Object s;
                try {
                    s = f.step(this);
                } catch (RuntimeException e) {
                    fail(null, e);
                    stopped = true;
                    break;
                }
                if (s == null) {
                    stack.pop();
                    continue;
                }
                if (s == PUSHED) continue;
                node = (ActionNode) s;
                ctx = pendingCtx;
            }
            if (!Bukkit.isPrimaryThread() && !node.asyncSafe()) {
                pending = node;
                pendingCtx = ctx;
                trace("  » pasa al hilo principal para '" + node.type() + "'");
                Bukkit.getScheduler().runTask(plugin, this::resume);
                return;
            }
            long wait;
            long t0 = sampled ? System.nanoTime() : 0;
            try {
                wait = node.execute(ctx, this);
            } catch (ActionException | ExprException e) {
                fail(node, e);
                continue;
            } catch (RuntimeException e) {
                fail(node, e);
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Detalle del error", e);
                continue;
            }
            if (sampled && rule != null) {
                plugin.profiler().recordPart(rule.name(), rule.trigger().name(), rule.trigger().async(),
                        dev.leo.omnievents.rule.RuleManager.part(rule, node.path()), System.nanoTime() - t0);
            }
            if (wait > 0 && !stopped) {
                if (simulating()) {
                    trace("    (espera " + wait + " ticks: en la simulación no se espera)");
                    continue;
                }
                if (plugin.disabling()) {
                    trace("  delay ignorado: el servidor se está apagando");
                    continue;
                }
                Bukkit.getScheduler().runTaskLater(plugin, this::resume, wait);
                return;
            }
        }
        finish();
    }

    private void fail(ActionNode node, RuntimeException e) {
        String where = "regla '" + (rule == null ? "?" : rule.name()) + "'" + (node == null ? "" : ", " + node.path() + " (" + node.type() + ")");
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        plugin.getLogger().warning("Error en " + where + ": " + msg);
        trace("  ✘ " + (node == null ? "" : node.type() + ": ") + msg);
        if (rule != null) rule.stats().error();
        if (e instanceof ActionException ae && ae.getMessage() != null && ae.getMessage().contains("tope")) stopped = true;
    }

    private void finish() {
        if (finished) return;
        finished = true;
        if (trace != null && ownsTrace) plugin.debugger().flush(trace);
        if (onFinish != null) onFinish.run();
    }
}
