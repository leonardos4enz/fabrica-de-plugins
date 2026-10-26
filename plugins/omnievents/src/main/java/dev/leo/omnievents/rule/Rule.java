package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.ActionNode;
import dev.leo.omnievents.debug.Budget;
import dev.leo.omnievents.debug.Stats;
import dev.leo.omnievents.trigger.TriggerType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Una regla compilada (plan 001 §2.2). Inmutable salvo enabled y las estadísticas. */
public final class Rule {
    public enum Reset { NEVER, DAILY, WEEKLY, MONTHLY, DURATION }

    final String name;
    final String file;
    final TriggerType trigger;
    volatile boolean enabled;
    final EventPriority priority;
    final boolean ignoreCancelled;
    final Set<String> worlds;
    final String permission;
    final String bypassPermission;
    final List<Condition> conditions;
    final long cooldownMs;
    final boolean cooldownGlobal;
    final int limit;
    final boolean limitGlobal;
    final Reset limitReset;
    final long limitResetMs;
    final List<ActionNode> actions;
    final List<ActionNode> elseActions;
    final List<ActionNode> onCooldown;
    final List<ActionNode> onLimit;
    final Predicate<Event> filter;
    final ConfigurationSection section;
    final List<String> declaredArgs;
    final long budgetNanos;
    final StateSpec state;
    /** Spec 009: alguna acción escribe X.data.y (puesto por el cargador). */
    boolean writesObjectData;
    /** Spec 009: variables que acepta una regla var_expire (vacío = todas). */
    Set<String> expireVariables = Set.of();
    /** Spec 010: los casos de tests (los inválidos ya se descartaron al cargar). */
    List<RuleTest> tests = List.of();
    /** Spec 011: el paquete de la regla (null si es global). */
    String pack;
    /** Spec 014, H4: permiso que salta el enfriamiento (cooldown_bypass u omnievents.bypasscooldown.<regla>). */
    String cooldownBypass;
    /** Spec 012: el costo (null si no tiene) y lo que corre si no alcanza. */
    Cost cost;
    List<ActionNode> onCostFail = List.of();
    private final Stats stats = new Stats();
    private final Budget budget = new Budget();

    Rule(String name, String file, TriggerType trigger, boolean enabled, EventPriority priority, boolean ignoreCancelled,
         Set<String> worlds, String permission, String bypassPermission, List<Condition> conditions, long cooldownMs,
         boolean cooldownGlobal, int limit, boolean limitGlobal, Reset limitReset, long limitResetMs,
         List<ActionNode> actions, List<ActionNode> elseActions, List<ActionNode> onCooldown, List<ActionNode> onLimit,
         Predicate<Event> filter, ConfigurationSection section, List<String> declaredArgs, long budgetNanos, StateSpec state) {
        this.name = name;
        this.file = file;
        this.trigger = trigger;
        this.enabled = enabled;
        this.priority = priority;
        this.ignoreCancelled = ignoreCancelled;
        this.worlds = worlds;
        this.permission = permission;
        this.bypassPermission = bypassPermission;
        this.conditions = conditions;
        this.cooldownMs = cooldownMs;
        this.cooldownGlobal = cooldownGlobal;
        this.limit = limit;
        this.limitGlobal = limitGlobal;
        this.limitReset = limitReset;
        this.limitResetMs = limitResetMs;
        this.actions = actions;
        this.elseActions = elseActions;
        this.onCooldown = onCooldown;
        this.onLimit = onLimit;
        this.filter = filter;
        this.section = section;
        this.declaredArgs = declaredArgs;
        this.budgetNanos = budgetNanos;
        this.state = state;
    }

    public String name() {
        return name;
    }

    public String file() {
        return file;
    }

    public TriggerType trigger() {
        return trigger;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean e) {
        enabled = e;
    }

    public EventPriority priority() {
        return priority;
    }

    public boolean ignoreCancelled() {
        return ignoreCancelled;
    }

    /** Mundos de la regla, en minúsculas (vacío = todos). */
    public Set<String> worlds() {
        return worlds;
    }

    public List<Condition> conditions() {
        return conditions;
    }

    /** Spec 014, H4: el permiso que salta el enfriamiento. */
    public String cooldownBypass() {
        return cooldownBypass;
    }

    public long cooldownMs() {
        return cooldownMs;
    }

    public int limit() {
        return limit;
    }

    public boolean cooldownGlobal() {
        return cooldownGlobal;
    }

    public boolean limitGlobal() {
        return limitGlobal;
    }

    public Reset limitReset() {
        return limitReset;
    }

    public int actionCount() {
        int n = actions.size() + elseActions.size() + onCooldown.size() + onLimit.size() + onCostFail.size();
        for (Condition c : conditions) n += c.deny().size();
        if (state != null) n += state.onExit().size();
        return n;
    }

    public boolean accepts(Event e) {
        return filter == null || filter.test(e);
    }

    public ConfigurationSection section() {
        return section;
    }

    public Stats stats() {
        return stats;
    }

    /** Presupuesto declarado con budget (spec 006, H7), en nanosegundos; 0 = sin presupuesto. */
    public long budgetNanos() {
        return budgetNanos;
    }

    /** Lo propio de una regla state (spec 008); null en las demás. */
    public StateSpec state() {
        return state;
    }

    public boolean writesObjectData() {
        return writesObjectData;
    }

    public Set<String> expireVariables() {
        return expireVariables;
    }

    public List<RuleTest> tests() {
        return tests;
    }

    public Budget budget() {
        return budget;
    }

    /** Costo de la regla (spec 012), o null. */
    public Cost cost() {
        return cost;
    }

    public List<ActionNode> onCostFail() {
        return onCostFail;
    }

    /** Paquete de la regla (spec 011), o null si es global. */
    public String pack() {
        return pack;
    }

    /**
     * Busca una regla por nombre desde otra (spec 011, H1 CA2): paquete:regla es absoluto; un nombre sin paquete,
     * desde una regla de un paquete, prueba primero en ese paquete y luego entre las globales.
     */
    public static Rule resolve(String name, Rule from, java.util.function.Function<String, Rule> lookup) {
        if (name == null) return null;
        String n = name.trim();
        if (from != null && from.pack != null && n.indexOf(':') < 0) {
            Rule r = lookup.apply(from.pack + ":" + n);
            if (r != null) return r;
        }
        return lookup.apply(n);
    }

    public java.util.List<ActionNode> actions() {
        return actions;
    }

    public java.util.List<ActionNode> elseActions() {
        return elseActions;
    }
}
