package dev.leo.omnievents.rule;

import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.action.ActionNode;
import dev.leo.omnievents.action.ExecContext;
import dev.leo.omnievents.action.Execution;
import dev.leo.omnievents.data.DataStore;
import dev.leo.omnievents.debug.Trace;
import dev.leo.omnievents.expr.ExprException;
import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.trigger.TriggerType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Reglas cargadas y el flujo de un disparo (plan 001 §2.2: orden de evaluación). */
public final class RuleManager {
    private final OmniEvents plugin;
    private volatile Map<String, Rule> rules = Map.of();

    public RuleManager(OmniEvents plugin) {
        this.plugin = plugin;
    }

    public void set(Map<String, Rule> loaded) {
        this.rules = Map.copyOf(loaded);
    }

    public Rule get(String name) {
        return name == null ? null : rules.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Rule> all() {
        return rules.values();
    }

    public List<Rule> byKind(TriggerType.Kind kind) {
        List<Rule> out = new ArrayList<>();
        for (Rule r : rules.values()) if (r.trigger().kind() == kind) out.add(r);
        return out;
    }

    public List<String> names() {
        List<String> out = new ArrayList<>();
        for (Rule r : rules.values()) out.add(r.name());
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    // ------------------------------------------------------------------ disparo

    /** Disparo desde un evento o temporizador. */
    public void fire(Rule rule, Map<String, Object> bindings, Event event) {
        if (!rule.enabled()) return;
        if (event != null) {
            if (!rule.accepts(event)) return;
            if (rule.ignoreCancelled() && event instanceof Cancellable c && c.isCancelled()) return;
        }
        run(rule, new ExecContext(plugin, rule, bindings), 0, null);
    }

    /** Ejecuta una regla (o la llama otra con call). Devuelve true si corrió sus acciones principales. */
    public boolean run(Rule rule, ExecContext ctx, int depth, Trace parentTrace) {
        long t0 = System.nanoTime();
        rule.stats().fired();
        Trace trace = parentTrace != null ? parentTrace : plugin.debugger().start(rule.name());
        String ind = parentTrace != null ? "  ↳ " : "";
        if (trace != null) trace.add(ind + "disparo de " + rule.trigger().name() + (ctx.playerOrNull() == null ? "" : " por " + ctx.playerOrNull().getName()));
        try {
            Player p = ctx.playerOrNull();
            if (!rule.worlds.isEmpty()) {
                String w = ctx.world() == null ? "" : ctx.world().getName().toLowerCase(Locale.ROOT);
                if (!rule.worlds.contains(w)) {
                    return skip(trace, ind + "✘ mundo '" + w + "' fuera de " + rule.worlds);
                }
            }
            if (rule.permission != null && (p == null || !p.hasPermission(rule.permission))) {
                return skip(trace, ind + "✘ sin el permiso " + rule.permission);
            }
            if (rule.bypassPermission != null && p != null && p.hasPermission(rule.bypassPermission)) {
                return skip(trace, ind + "✘ ignorado por " + rule.bypassPermission);
            }
            for (Expression c : rule.conditions) {
                boolean ok;
                try {
                    ok = c.test(ctx);
                    if (trace != null) trace.add(ind + (ok ? "✔ " : "✘ ") + c.explain(ctx));
                } catch (ExprException e) {
                    ok = false;
                    rule.stats().error();
                    plugin.getLogger().warning("Error en la condición «" + c.source() + "» de la regla '" + rule.name() + "': " + e.getMessage());
                    if (trace != null) trace.add(ind + "✘ error en «" + c.source() + "»: " + e.getMessage());
                }
                if (!ok) {
                    if (!rule.elseActions.isEmpty()) {
                        if (trace != null) trace.add(ind + "→ else");
                        execute(rule, rule.elseActions, ctx, depth, trace, null, parentTrace == null);
                    } else {
                        plugin.debugger().flush(parentTrace == null ? trace : null);
                    }
                    return false;
                }
            }
            DataStore.Holder limitHolder = holder(rule.limitGlobal, p);
            if (rule.limit > 0 && limitHolder != null) {
                long[] u = currentUses(rule, limitHolder);
                if (u[0] >= rule.limit) {
                    if (trace != null) trace.add(ind + "✘ límite: " + u[0] + "/" + rule.limit + " usos");
                    if (!rule.onLimit.isEmpty()) execute(rule, rule.onLimit, ctx, depth, trace, null, parentTrace == null);
                    else plugin.debugger().flush(parentTrace == null ? trace : null);
                    return false;
                }
            }
            DataStore.Holder cdHolder = holder(rule.cooldownGlobal, p);
            long now = System.currentTimeMillis();
            boolean bypassCd = p != null && plugin.settings().cooldownBypassOp() && p.isOp();
            if (rule.cooldownMs > 0 && !bypassCd && cdHolder != null) {
                Long until = cdHolder.cooldowns().get(rule.name());
                if (until != null && until > now) {
                    double left = Math.ceil((until - now) / 1000.0);
                    ctx.locals().put("cooldown_left", left);
                    if (trace != null) trace.add(ind + "✘ enfriamiento: faltan " + Values.formatNumber(left) + "s");
                    if (!rule.onCooldown.isEmpty()) execute(rule, rule.onCooldown, ctx, depth, trace, null, parentTrace == null);
                    else plugin.debugger().flush(parentTrace == null ? trace : null);
                    return false;
                }
            }
            Runnable revert = registerUsage(rule, cdHolder, limitHolder, now, bypassCd);
            rule.stats().passed();
            if (trace != null) trace.add(ind + "→ actions");
            execute(rule, rule.actions, ctx, depth, trace, revert, parentTrace == null);
            if (parentTrace != null) trace.add(ind + "(fin de " + rule.name() + ")");
            return true;
        } finally {
            rule.stats().time(System.nanoTime() - t0);
        }
    }

    private boolean skip(Trace trace, String line) {
        if (trace != null) {
            trace.add(line);
            plugin.debugger().flush(trace);
        }
        return false;
    }

    private void execute(Rule rule, List<ActionNode> nodes, ExecContext ctx, int depth, Trace trace, Runnable revert, boolean ownsTrace) {
        Execution exec = new Execution(plugin, rule, depth, trace, ownsTrace);
        if (revert != null) exec.revertUsage(revert);
        exec.start(nodes, ctx);
    }

    /**
     * Dónde se cuentan enfriamiento y usos. Por jugador y sin jugador (consola, temporizador del servidor) no se
     * aplican: contarlos como globales bloquearía a todos (Regresión, servidor real 001 F-05).
     */
    private DataStore.Holder holder(boolean global, Player p) {
        if (global) return plugin.data().global();
        return p == null ? null : plugin.data().player(p.getUniqueId());
    }

    /** Usos vigentes, aplicando el reinicio de la ventana (H8 CA2). */
    private long[] currentUses(Rule rule, DataStore.Holder h) {
        long[] u = h.uses().get(rule.name());
        if (u == null) return new long[]{0, 0};
        long now = System.currentTimeMillis();
        boolean expired = switch (rule.limitReset) {
            case NEVER -> false;
            case DURATION -> now - u[1] >= rule.limitResetMs;
            default -> u[1] < windowStart(rule.limitReset);
        };
        if (expired) {
            h.uses().remove(rule.name());
            h.markDirty();
            return new long[]{0, 0};
        }
        return u;
    }

    private static long windowStart(Rule.Reset reset) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate start = switch (reset) {
            case WEEKLY -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> today.withDayOfMonth(1);
            default -> today;
        };
        return start.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    private Runnable registerUsage(Rule rule, DataStore.Holder cd, DataStore.Holder lim, long now, boolean bypassCd) {
        Long prevCd = cd == null ? null : cd.cooldowns().get(rule.name());
        long[] prevUses = lim == null ? null : lim.uses().get(rule.name());
        boolean cdApplied = rule.cooldownMs > 0 && !bypassCd && cd != null;
        boolean limApplied = rule.limit > 0 && lim != null;
        boolean changed = false;
        if (cdApplied) {
            cd.cooldowns().put(rule.name(), now + rule.cooldownMs);
            cd.markDirty();
            changed = true;
        }
        if (limApplied) {
            long[] u = currentUses(rule, lim);
            lim.uses().put(rule.name(), new long[]{u[0] + 1, u[0] == 0 ? now : u[1]});
            lim.markDirty();
            changed = true;
        }
        if (!changed) return null;
        return () -> {
            if (cdApplied) {
                if (prevCd == null) cd.cooldowns().remove(rule.name());
                else cd.cooldowns().put(rule.name(), prevCd);
                cd.markDirty();
            }
            if (limApplied) {
                if (prevUses == null) lim.uses().remove(rule.name());
                else lim.uses().put(rule.name(), prevUses);
                lim.markDirty();
            }
        };
    }

    // ------------------------------------------------------------------ call, enfriamientos y usos

    /** Acción call y /omni call (H4 CA1). */
    public void call(String ruleName, ExecContext from, Map<String, Object> args, int depth, Trace trace) {
        Rule target = get(ruleName);
        if (target == null) throw new dev.leo.omnievents.action.ActionException("no existe la regla '" + ruleName + "' para call");
        if (depth > plugin.settings().maxCallDepth()) {
            throw new dev.leo.omnievents.action.ActionException("se pasó el tope de " + plugin.settings().maxCallDepth() + " call anidados (expressions.max-call-depth): ¿una regla que se llama a sí misma?");
        }
        if (!target.enabled()) return;
        Map<String, Object> locals = new LinkedHashMap<>(args);
        ExecContext ctx = from == null ? new ExecContext(plugin, target, new HashMap<>()) : from.forCall(target, locals);
        if (from == null) ctx.locals().putAll(locals);
        if (from != null && from.rule() != null) ctx.locals().putIfAbsent("caller", from.rule().name());
        run(target, ctx, depth, trace);
    }

    public double cooldownLeftSeconds(String ruleName, UUID player) {
        Rule r = get(ruleName);
        if (r == null) return 0;
        DataStore.Holder h = r.cooldownGlobal || player == null ? plugin.data().global() : plugin.data().player(player);
        Long until = h.cooldowns().get(r.name());
        long now = System.currentTimeMillis();
        return until == null || until <= now ? 0 : Math.ceil((until - now) / 1000.0);
    }

    public long uses(String ruleName, UUID player) {
        Rule r = get(ruleName);
        if (r == null) return 0;
        DataStore.Holder h = r.limitGlobal || player == null ? plugin.data().global() : plugin.data().player(player);
        return currentUses(r, h)[0];
    }

    public void resetCooldown(String ruleName, UUID player) {
        Rule r = get(ruleName);
        String key = r == null ? ruleName : r.name();
        DataStore.Holder h = player == null || r != null && r.cooldownGlobal ? plugin.data().global() : plugin.data().player(player);
        if (h.cooldowns().remove(key) != null) h.markDirty();
    }

    public void resetUses(String ruleName, UUID player) {
        Rule r = get(ruleName);
        String key = r == null ? ruleName : r.name();
        DataStore.Holder h = player == null || r != null && r.limitGlobal ? plugin.data().global() : plugin.data().player(player);
        if (h.uses().remove(key) != null) h.markDirty();
    }
}
