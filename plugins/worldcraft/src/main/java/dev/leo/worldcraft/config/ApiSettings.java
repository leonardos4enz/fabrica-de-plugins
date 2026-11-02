package dev.leo.worldcraft.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * La sección api de config.yml (plan 037 §2). Un valor malo avisa y usa el de fábrica; una sección que falta, todo de
 * fábrica (un config.yml de la 0.12.0 carga igual).
 *
 * @param pluginGroups los grupos de límites de una obra que un plugin hace a su nombre (además de default)
 */
public record ApiSettings(boolean enabled, List<String> pluginGroups) {
    public static final ApiSettings DEFAULTS = new ApiSettings(true, List.of());

    static final List<String> KEYS = List.of("enabled", "plugin_groups");
    private static final String FILE = "config.yml";

    /** Los límites de un plugin: default y los grupos de plugin_groups (como si tuviera worldcraft.limit.&lt;grupo&gt;). */
    public Limits.Resolved limits(Limits limits) {
        Set<String> perms = Set.copyOf(pluginGroups.stream().map(g -> Limits.PERMISSION_PREFIX + g).toList());
        return limits.resolve(perms::contains);
    }

    public static ApiSettings load(ConfigurationSection config, Limits limits, Issues issues) {
        if (config == null) return DEFAULTS;
        ConfigurationSection c = config.getConfigurationSection("api");
        if (c == null) return DEFAULTS;
        for (String k : c.getKeys(false)) {
            if (!KEYS.contains(k)) issues.warn(FILE, "api." + k, "unknown setting" + Issues.hint(k, KEYS));
        }
        Settings.Reader r = new Settings.Reader(config, FILE, issues);
        List<String> groups = new ArrayList<>();
        for (String g : r.list("api.plugin_groups", List.of())) {
            String name = g.toLowerCase(Locale.ROOT);
            if (limits != null && !limits.groups().containsKey(name)) {
                issues.warn(FILE, "api.plugin_groups", "there is no limits group '" + g + "'" + Issues.hint(name, limits.groups().keySet()) + ": skipped");
                continue;
            }
            if (!name.equals("default") && !groups.contains(name)) groups.add(name);
        }
        return new ApiSettings(r.bool("api.enabled", DEFAULTS.enabled), List.copyOf(groups));
    }
}
