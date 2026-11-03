package dev.leo.worldcraft.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * La sección slash_commands de config.yml (plan 039 §2). Un valor malo avisa y usa el de fábrica; una sección que falta,
 * todo de fábrica (un config.yml de la 0.13.0 carga igual).
 *
 * @param takeFromWorldEdit worldedit: take (Worldcraft se queda con los nombres de WorldEdit y FAWE) o leave
 * @param skip              los nombres que Worldcraft deja, sin la primera barra («//up» → «/up»)
 */
public record SlashSettings(boolean enabled, boolean takeFromWorldEdit, Set<String> skip) {
    public static final SlashSettings DEFAULTS = new SlashSettings(true, true, Set.of());

    static final List<String> KEYS = List.of("enabled", "worldedit", "skip");
    private static final List<String> MODES = List.of("take", "leave");
    private static final String FILE = "config.yml";

    /** @param known los nombres de la tabla (sin la primera barra), para avisar de uno que no existe */
    public static SlashSettings load(ConfigurationSection config, Collection<String> known, Issues issues) {
        if (config == null) return DEFAULTS;
        ConfigurationSection c = config.getConfigurationSection("slash_commands");
        if (c == null) return DEFAULTS;
        for (String k : c.getKeys(false)) {
            if (!KEYS.contains(k)) issues.warn(FILE, "slash_commands." + k, "unknown setting" + Issues.hint(k, KEYS));
        }
        Settings.Reader r = new Settings.Reader(config, FILE, issues);
        String mode = r.text("slash_commands.worldedit", "take");
        if (!MODES.contains(mode)) {
            issues.error(FILE, "slash_commands.worldedit", "'" + mode + "' is not take or leave" + Issues.hint(mode, MODES) + ": using take");
            mode = "take";
        }
        Set<String> skip = new LinkedHashSet<>();
        for (String s : r.list("slash_commands.skip", List.of())) {
            if (!s.startsWith("/")) {
                issues.warn(FILE, "slash_commands.skip", "'" + s + "' needs its slash, as you type it (/" + s + " or //" + s + "): skipped");
                continue;
            }
            String label = s.substring(1).toLowerCase(java.util.Locale.ROOT);
            if (!known.contains(label)) {
                issues.warn(FILE, "slash_commands.skip", "'" + s + "' is not one of Worldcraft's commands: skipped");
                continue;
            }
            skip.add(label);
        }
        return new SlashSettings(r.bool("slash_commands.enabled", DEFAULTS.enabled), mode.equals("take"), Set.copyOf(skip));
    }
}
