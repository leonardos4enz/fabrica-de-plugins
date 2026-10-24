package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.ActionNode;
import dev.leo.omnievents.debug.Stats;
import dev.leo.omnievents.expr.Expression;
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
    final List<Expression> conditions;
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
    private final Stats stats = new Stats();

    Rule(String name, String file, TriggerType trigger, boolean enabled, EventPriority priority, boolean ignoreCancelled,
         Set<String> worlds, String permission, String bypassPermission, List<Expression> conditions, long cooldownMs,
         boolean cooldownGlobal, int limit, boolean limitGlobal, Reset limitReset, long limitResetMs,
         List<ActionNode> actions, List<ActionNode> elseActions, List<ActionNode> onCooldown, List<ActionNode> onLimit,
         Predicate<Event> filter, ConfigurationSection section, List<String> declaredArgs) {
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

    public List<Expression> conditions() {
        return conditions;
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
        return actions.size() + elseActions.size() + onCooldown.size() + onLimit.size();
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
}
