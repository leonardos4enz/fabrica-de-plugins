package dev.leo.omnievents.rule;

import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.action.ActionNode;
import dev.leo.omnievents.action.ExecContext;
import dev.leo.omnievents.action.Execution;
import dev.leo.omnievents.data.DataStore;
import dev.leo.omnievents.debug.Trace;
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
    private volatile List<Pack> packs = List.of();

    public RuleManager(OmniEvents plugin) {
        this.plugin = plugin;
    }

    public void set(Map<String, Rule> loaded) {
        set(loaded, List.of());
    }

    public void set(Map<String, Rule> loaded, List<Pack> packs) {
        this.rules = Map.copyOf(loaded);
        this.packs = List.copyOf(packs);
    }

    /** Ejecuciones que esperan un delay (spec 015, /omni interrupt). Concurrente: un delay puede programarse desde player_chat. */
    private final java.util.Set<dev.leo.omnievents.action.Execution> waiting = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** La ejecución empieza (true) o deja (false) de esperar un delay. */
    public void waiting(dev.leo.omnievents.action.Execution exec, boolean on) {
        if (on) waiting.add(exec);
        else waiting.remove(exec);
    }

    /**
     * Corta las ejecuciones que esperan un delay de una regla (o de todas con *), del jugador o de todos (player null).
     * Devuelve cuántas cortó. Hilo principal.
     */
    public int interrupt(String rule, java.util.UUID player) {
        int n = 0;
        for (dev.leo.omnievents.action.Execution e : java.util.List.copyOf(waiting)) {
            if (e.rule() == null || !rule.equals("*") && !e.rule().name().equalsIgnoreCase(rule)) continue;
            if (player != null && !player.equals(e.playerId())) continue;
            waiting.remove(e);
            if (e.interrupt()) n++;
        }
        return n;
    }

    public Rule get(String name) {
        return name == null ? null : rules.get(name.toLowerCase(Locale.ROOT));
    }

    /** Un nombre visto desde una regla (spec 011, H1 CA2): primero en su paquete, luego entre las globales. */
    public Rule resolve(String name, Rule from) {
        return Rule.resolve(name, from, this::get);
    }

    /** Nombre completo de la regla a la que apunta name desde from, o name si no existe. */
    public String resolveName(String name, Rule from) {
        Rule r = resolve(name, from);
        return r == null ? name : r.name();
    }

    public List<Pack> packs() {
        return packs;
    }

    public Pack pack(String name) {
        if (name == null) return null;
        for (Pack p : packs) if (p.name().equalsIgnoreCase(name.trim())) return p;
        return null;
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
        return run(rule, ctx, depth, parentTrace, null);
    }

    /**
     * on_enter de una regla state (spec 008): un disparo normal (permiso, límite, enfriamiento, estadísticas) que
     * sigue la traza que empezó la revisión. Devuelve true si corrió on_enter.
     */
    public boolean runState(Rule rule, ExecContext ctx, Trace own) {
        return run(rule, ctx, 0, null, own);
    }

    /** on_exit de una regla state (spec 008): solo las acciones, con la traza de la revisión. */
    public void runActions(Rule rule, List<ActionNode> nodes, ExecContext ctx, Trace trace) {
        if (nodes.isEmpty()) plugin.debugger().flush(trace);
        else execute(rule, nodes, ctx, 0, trace, null, true, false);
    }

    private boolean run(Rule rule, ExecContext ctx, int depth, Trace parentTrace, Trace own) {
        long t0 = System.nanoTime();
        // Una simulación (spec 006, H4) recorre la regla sin efectos: sin estadísticas, usos ni enfriamientos.
        boolean sim = parentTrace != null && parentTrace.simulation();
        boolean nested = parentTrace != null && depth > 0;
        boolean sampled = !sim && plugin.profiler().sample(rule.name(), rule.trigger().name(), rule.trigger().async());
        if (!sim) rule.stats().fired();
        Trace trace = parentTrace != null ? parentTrace : own != null ? own : plugin.debugger().start(rule.name());
        String ind = nested ? "  ↳ " : "";
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
            for (int i = 0; i < rule.conditions.size(); i++) {
                Condition c = rule.conditions.get(i);
                long tc = sampled ? System.nanoTime() : 0;
                boolean ok = c.test(ctx, trace == null ? null : trace::add, ind, false, (single, e) -> {
                    if (sim) return;
                    rule.stats().error();
                    plugin.getLogger().warning("Error en la condición «" + single.source() + "» de la regla '" + rule.name() + "': " + e.getMessage());
                });
                if (sampled) plugin.profiler().recordPart(rule.name(), rule.trigger().name(), rule.trigger().async(), part(rule, c.path()), System.nanoTime() - tc);
                if (!ok) {
                    // H1 CA1-2: el deny de la condición que falla reemplaza al else; sin deny, corre el else.
                    List<ActionNode> branch = c.deny().isEmpty() ? rule.elseActions : c.deny();
                    if (!branch.isEmpty()) {
                        if (trace != null) trace.add(ind + (c.deny().isEmpty() ? "→ else" : "→ deny de " + part(rule, c.path())));
                        execute(rule, branch, ctx, depth, trace, null, parentTrace == null, sampled);
                    } else {
                        plugin.debugger().flush(parentTrace == null ? trace : null);
                    }
                    return false;
                }
            }
            DataStore.Holder limitHolder = holder(rule.limitGlobal, p);
            if (rule.limit > 0 && limitHolder != null) {
                long[] u = currentUses(rule, limitHolder, !sim);
                if (u[0] >= rule.limit) {
                    if (trace != null) trace.add(ind + "✘ límite: " + u[0] + "/" + rule.limit + " usos");
                    if (!rule.onLimit.isEmpty()) execute(rule, rule.onLimit, ctx, depth, trace, null, parentTrace == null, sampled);
                    else plugin.debugger().flush(parentTrace == null ? trace : null);
                    return false;
                }
            }
            DataStore.Holder cdHolder = holder(rule.cooldownGlobal, p);
            long now = System.currentTimeMillis();
            String bypassWhy = rule.cooldownMs > 0 ? cooldownBypass(rule, p) : null;
            boolean bypassCd = bypassWhy != null;
            if (bypassCd && trace != null) trace.add(ind + "✔ enfriamiento: libre (" + bypassWhy + ")");
            if (rule.cooldownMs > 0 && !bypassCd && cdHolder != null) {
                Long until = cdHolder.cooldowns().get(rule.name());
                if (until != null && until > now) {
                    double left = Math.ceil((until - now) / 1000.0);
                    ctx.locals().put("cooldown_left", left);
                    if (trace != null) trace.add(ind + "✘ enfriamiento: faltan " + Values.formatNumber(left) + "s");
                    if (!rule.onCooldown.isEmpty()) execute(rule, rule.onCooldown, ctx, depth, trace, null, parentTrace == null, sampled);
                    else plugin.debugger().flush(parentTrace == null ? trace : null);
                    return false;
                }
            }
            // Spec 012, H2: el costo, todo o nada, antes de contar el uso.
            if (rule.cost != null && !pay(rule, ctx, trace, ind, sim)) {
                if (!rule.onCostFail.isEmpty()) execute(rule, rule.onCostFail, ctx, depth, trace, null, parentTrace == null, sampled);
                else plugin.debugger().flush(parentTrace == null ? trace : null);
                return false;
            }
            Runnable revert = sim ? null : registerUsage(rule, cdHolder, limitHolder, now, bypassCd);
            if (!sim) rule.stats().passed();
            if (trace != null) trace.add(ind + "→ actions");
            execute(rule, rule.actions, ctx, depth, trace, revert, parentTrace == null, sampled);
            if (nested) trace.add(ind + "(fin de " + rule.name() + ")");
            return true;
        } finally {
            if (!sim) finished(rule, System.nanoTime() - t0, sampled);
        }
    }

    /**
     * Cotiza y cobra el costo (plan 012 §1.3). Devuelve true si se cobró todo (o, en una simulación, si alcanzaría);
     * si no, deja var.cost_missing y var.cost_missing_list para on_cost_fail. Nunca cobra fuera del hilo principal.
     */
    private boolean pay(Rule rule, ExecContext ctx, Trace trace, String ind, boolean sim) {
        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("La regla '" + rule.name() + "' tiene cost y corrió fuera del hilo principal: no se cobra (llámala desde un disparador síncrono)");
            return fail(ctx, trace, ind, "el cobro necesita el hilo principal", List.of("thread"));
        }
        if (rule.cost.needsPlayer() && ctx.playerOrNull() == null) return fail(ctx, trace, ind, "un jugador que pague", List.of("player"));
        dev.leo.omnievents.action.Purse purse = ctx.purse();
        Cost.Quote q;
        try {
            q = rule.cost.quote(ctx, purse);
        } catch (dev.leo.omnievents.action.ActionException | dev.leo.omnievents.expr.ExprException e) {
            if (!sim) rule.stats().error();
            plugin.getLogger().warning("Error en el cost de la regla '" + rule.name() + "': " + e.getMessage());
            return fail(ctx, trace, ind, "(error: " + e.getMessage() + ")", List.of("error"));
        }
        if (!q.ok()) {
            RuleTester.costFailed(ctx, q, null, purse);
            if (trace != null) trace.add(ind + "✘ costo: falta " + ctx.locals().get("cost_missing") + (rule.onCostFail.isEmpty() ? "" : " → on_cost_fail"));
            return false;
        }
        if (sim) {
            if (trace != null) trace.add(ind + "✔ costo: se cobraría " + String.join(", ", q.lines().stream().map(l -> Cost.describe(l, l.amount(), purse)).toList()));
            return true;
        }
        Cost.Line failed = rule.cost.charge(q, purse, (l, why) -> plugin.getLogger().severe("No se pudo devolver " + Cost.describe(l, l.amount(), purse)
                + " a " + purse.who() + " (regla '" + rule.name() + "'): " + why));
        if (failed != null) {
            RuleTester.costFailed(ctx, q, failed, purse);
            if (trace != null) trace.add(ind + "✘ costo: no se pudo cobrar " + failed.key() + "; se devolvió lo cobrado");
            return false;
        }
        if (trace != null) trace.add(ind + "✔ costo cobrado: " + String.join(", ", q.lines().stream().map(l -> Cost.describe(l, l.amount(), purse)).toList()));
        return true;
    }

    private static boolean fail(ExecContext ctx, Trace trace, String ind, String missing, List<Object> keys) {
        ctx.locals().put("cost_missing", missing);
        ctx.locals().put("cost_missing_list", new ArrayList<>(keys));
        if (trace != null) trace.add(ind + "✘ costo: falta " + missing);
        return false;
    }

    /** Tiempo del disparo: estadísticas, perfilador (si se midió) y presupuesto (spec 006, H6 y H7). */
    private void finished(Rule rule, long nanos, boolean sampled) {
        rule.stats().time(nanos);
        if (sampled) plugin.profiler().recordRule(rule.name(), rule.trigger().name(), rule.trigger().async(), nanos);
        if (rule.budgetNanos > 0 && rule.budget().record(nanos, rule.budgetNanos, System.currentTimeMillis(), plugin.settings().budgetWarnEveryMs())) {
            plugin.getLogger().warning("La regla '" + rule.name() + "' promedia " + Values.formatNumber(Values.round(rule.budget().recentMillis(), 3))
                    + " ms por disparo, más que su budget de " + Values.formatNumber(rule.budgetNanos / 1e6) + " ms (detalle: /omni cpu on)");
        }
    }

    /** Ruta de una condición o acción sin el nombre de la regla: conditions[1], actions[0].then[2]. */
    public static String part(Rule rule, String path) {
        String prefix = rule.name() + ".";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    // ------------------------------------------------------------------ revisión y simulación (spec 006)

    /** Simula un disparo (H4): recorre la regla y lista las acciones sin aplicarlas. */
    public Trace simulate(Rule rule, ExecContext ctx) {
        Trace t = new Trace(rule.name(), true);
        Player p = ctx.playerOrNull();
        t.add("simulación de " + rule.name() + (p == null ? "" : " para " + p.getName()) + ": no se aplica ninguna acción");
        run(rule, ctx, 0, t);
        plugin.debugger().remember(t);
        return t;
    }

    /**
     * Revisa la regla ahora (H3): mundo, permisos, todas las condiciones con sus valores, usos y enfriamiento, y la
     * rama que correría. No ejecuta acciones ni cambia datos o estadísticas.
     */
    public List<String> check(Rule rule, ExecContext ctx) {
        List<String> out = new ArrayList<>();
        String branch = null;
        Player p = ctx.playerOrNull();
        if (!rule.worlds.isEmpty()) {
            String w = ctx.world() == null ? "" : ctx.world().getName().toLowerCase(Locale.ROOT);
            boolean ok = rule.worlds.contains(w);
            out.add((ok ? "✔ mundo '" + w + "' en " : "✘ mundo '" + w + "' fuera de ") + rule.worlds);
            if (!ok) branch = "nada (fuera de sus mundos)";
        }
        if (rule.permission != null) {
            boolean ok = p != null && p.hasPermission(rule.permission);
            out.add((ok ? "✔ tiene el permiso " : "✘ sin el permiso ") + rule.permission);
            if (!ok && branch == null) branch = "nada (sin el permiso)";
        }
        if (rule.bypassPermission != null && p != null && p.hasPermission(rule.bypassPermission)) {
            out.add("✘ tiene " + rule.bypassPermission + ": la regla lo ignora");
            if (branch == null) branch = "nada (permiso de bypass)";
        }
        for (Condition c : rule.conditions) {
            boolean ok = c.test(ctx, out::add, "", true, null);
            if (!ok && branch == null) {
                String where = part(rule, c.path());
                branch = !c.deny().isEmpty() ? "deny de " + where + " (" + c.deny().size() + " acciones)"
                        : !rule.elseActions.isEmpty() ? "else (" + rule.elseActions.size() + " acciones), falla " + where
                        : "nada (falla " + where + " y no hay else)";
            }
        }
        DataStore.Holder limitHolder = holder(rule.limitGlobal, p);
        if (rule.limit > 0 && limitHolder != null) {
            long used = currentUses(rule, limitHolder, false)[0];
            boolean ok = used < rule.limit;
            out.add((ok ? "✔ " : "✘ ") + "límite: " + used + "/" + rule.limit + " usos");
            if (!ok && branch == null) branch = rule.onLimit.isEmpty() ? "nada (límite alcanzado)" : "on_limit (" + rule.onLimit.size() + " acciones)";
        }
        DataStore.Holder cdHolder = holder(rule.cooldownGlobal, p);
        if (rule.cooldownMs > 0 && cdHolder != null) {
            String bypassWhy = cooldownBypass(rule, p);
            boolean bypassCd = bypassWhy != null;
            Long until = cdHolder.cooldowns().get(rule.name());
            long left = until == null || bypassCd ? 0 : Math.max(0, until - System.currentTimeMillis());
            out.add((left == 0 ? "✔ " : "✘ ") + "enfriamiento: " + (left == 0 ? "libre" + (bypassCd ? " (" + bypassWhy + ")" : "") : "faltan " + Values.formatNumber(Math.ceil(left / 1000.0)) + "s"));
            if (left > 0 && branch == null) branch = rule.onCooldown.isEmpty() ? "nada (en enfriamiento)" : "on_cooldown (" + rule.onCooldown.size() + " acciones)";
        }
        // Spec 012, H2 CA5: cada parte del costo con lo que tiene y lo que pide.
        if (rule.cost != null) {
            if (rule.cost.needsPlayer() && p == null) {
                out.add("✘ costo: sin jugador que pague");
                if (branch == null) branch = "on_cost_fail (" + rule.onCostFail.size() + " acciones)";
            } else {
                try {
                    Cost.Quote q = rule.cost.quote(ctx, ctx.purse());
                    if (q.noEconomy()) out.add("✘ costo money: no hay economía (Vault)");
                    for (Cost.Line l : q.lines()) if (!(q.noEconomy() && l.key().equals("money"))) out.add(Cost.checkLine(l));
                    if (!q.ok() && branch == null) branch = "on_cost_fail (" + rule.onCostFail.size() + " acciones)";
                } catch (dev.leo.omnievents.action.ActionException | dev.leo.omnievents.expr.ExprException e) {
                    out.add("✘ costo: " + e.getMessage());
                    if (branch == null) branch = "nada (error en cost)";
                }
            }
        }
        out.add("→ correría: " + (branch == null ? "actions (" + rule.actions.size() + " acciones)" : branch));
        java.util.Set<String> used = new java.util.TreeSet<>();
        for (Condition c : rule.conditions) roots(c, used);
        out.addAll(missingNotes(used, rule.trigger().roots(), r -> ctx.resolve(r) != null));
        return out;
    }

    /** H3 CA3: avisa qué raíces del disparador que usan las condiciones no tienen valor en la revisión. */
    static List<String> missingNotes(java.util.Set<String> used, java.util.Set<String> triggerRoots, java.util.function.Predicate<String> bound) {
        java.util.Set<String> missing = new java.util.TreeSet<>(used);
        missing.retainAll(triggerRoots);
        missing.removeIf(bound);
        List<String> out = new ArrayList<>();
        if (missing.remove("player")) out.add("• sin jugador: player vale nulo (usa /omni check <regla> <jugador>)");
        if (!missing.isEmpty()) {
            out.add("• sin evento: " + String.join(", ", missing) + (missing.size() == 1 ? " vale" : " valen") + " nulo en esta revisión");
        }
        return out;
    }

    private static void roots(Condition c, java.util.Set<String> out) {
        switch (c) {
            case Condition.Single s -> out.addAll(s.expr().roots());
            case Condition.AnyOf a -> a.of().forEach(x -> roots(x, out));
        }
    }

    private boolean skip(Trace trace, String line) {
        if (trace != null) {
            trace.add(line);
            plugin.debugger().flush(trace);
        }
        return false;
    }

    private void execute(Rule rule, List<ActionNode> nodes, ExecContext ctx, int depth, Trace trace, Runnable revert, boolean ownsTrace, boolean sampled) {
        Execution exec = new Execution(plugin, rule, depth, trace, ownsTrace, sampled);
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

    /** Usos vigentes, aplicando el reinicio de la ventana (H8 CA2). Con write = false no borra una ventana vencida. */
    private long[] currentUses(Rule rule, DataStore.Holder h, boolean write) {
        long[] u = h.uses().get(rule.name());
        if (u == null) return new long[]{0, 0};
        long now = System.currentTimeMillis();
        boolean expired = switch (rule.limitReset) {
            case NEVER -> false;
            case DURATION -> now - u[1] >= rule.limitResetMs;
            default -> u[1] < windowStart(rule.limitReset);
        };
        if (expired) {
            if (!write) return new long[]{0, 0};
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

    /**
     * Por qué el jugador salta el enfriamiento de la regla («OP» o «permiso …»), o null. Spec 014, H4 CA1: el permiso
     * cuenta solo si está dado (isPermissionSet); un permiso no declarado vale para los OP por defecto en Bukkit.
     */
    String cooldownBypass(Rule rule, Player p) {
        return bypassReason(rule.cooldownBypass, plugin.settings().cooldownBypassOp(), p);
    }

    static String bypassReason(String perm, boolean opBypass, Player p) {
        if (p == null) return null;
        if (opBypass && p.isOp()) return "OP";
        return perm != null && p.isPermissionSet(perm) && p.hasPermission(perm) ? "permiso " + perm : null;
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
            long[] u = currentUses(rule, lim, true);
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
        Rule target = resolve(ruleName, from == null ? null : from.rule());
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
        return currentUses(r, h, true)[0];
    }

    public void resetCooldown(String ruleName, UUID player) {
        Rule r = get(ruleName);
        String key = r == null ? ruleName : r.name();
        DataStore.Holder h = player == null || r != null && r.cooldownGlobal ? plugin.data().global() : plugin.data().player(player);
        if (h.cooldowns().remove(key) != null) h.markDirty();
    }

    /**
     * Spec 014, H4 CA3: reinicia el enfriamiento (o los usos) de esas reglas para todos: el global en las de alcance
     * global y, en las demás, cada jugador cargado (aquí, en el hilo principal) y cada desconectado con archivo (en
     * otro hilo; {@code done} recibe cuántos jugadores cambiaron en total, en el hilo principal).
     */
    public void resetEveryone(Collection<Rule> rules, boolean cooldown, java.util.function.IntConsumer done) {
        List<String> perPlayer = new ArrayList<>();
        List<String> global = new ArrayList<>();
        for (Rule r : rules) ((cooldown ? r.cooldownGlobal : r.limitGlobal) ? global : perPlayer).add(r.name());
        DataStore.removeUsage(plugin.data().global(), global, cooldown);
        int loaded = 0;
        for (UUID id : plugin.data().loadedPlayers()) if (DataStore.removeUsage(plugin.data().player(id), perPlayer, cooldown)) loaded++;
        int already = loaded;
        if (perPlayer.isEmpty()) {
            done.accept(already);
            return;
        }
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int offline = plugin.data().removeUsageOffline(perPlayer, cooldown);
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> done.accept(already + offline));
        });
    }

    public void resetUses(String ruleName, UUID player) {
        Rule r = get(ruleName);
        String key = r == null ? ruleName : r.name();
        DataStore.Holder h = player == null || r != null && r.limitGlobal ? plugin.data().global() : plugin.data().player(player);
        if (h.uses().remove(key) != null) h.markDirty();
    }
}
