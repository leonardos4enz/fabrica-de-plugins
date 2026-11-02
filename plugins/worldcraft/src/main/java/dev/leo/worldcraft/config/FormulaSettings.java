package dev.leo.worldcraft.config;

import dev.leo.worldcraft.formula.Formula;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/** La sección formulas de config.yml (plan 034 §2): tiempo de cálculo, vueltas, largo y megabuf. */
public record FormulaSettings(int timeoutMs, int maxTimeoutMs, int maxSteps, int maxLength, int megabufSize) {
    public static final FormulaSettings DEFAULTS = new FormulaSettings(2000, 10_000, 100_000, 1000, 65_536);

    static final List<String> KEYS = List.of("timeout_ms", "max_timeout_ms", "max_steps", "max_length", "megabuf_size");

    public Formula.Limits limits() {
        return new Formula.Limits(maxSteps, maxLength, megabufSize);
    }

    /** Un valor malo avisa y usa el de fábrica (constitución I.3); una sección que falta, todo de fábrica. */
    public static FormulaSettings load(ConfigurationSection config, Issues issues) {
        ConfigurationSection c = config == null ? null : config.getConfigurationSection("formulas");
        if (c == null) return DEFAULTS;
        String file = "config.yml";
        for (String k : c.getKeys(false)) {
            if (!KEYS.contains(k)) issues.warn(file, "formulas." + k, "unknown setting" + Issues.hint(k, KEYS));
        }
        FormulaSettings d = DEFAULTS;
        Settings.Reader r = new Settings.Reader(config, file, issues);
        int max = r.integer("formulas.max_timeout_ms", d.maxTimeoutMs, 100, 60_000);
        int timeout = r.integer("formulas.timeout_ms", d.timeoutMs, 1, 60_000);
        if (timeout > max) {
            issues.warn(file, "formulas.timeout_ms", timeout + " is more than max_timeout_ms (" + max + "): using " + max);
            timeout = max;
        }
        return new FormulaSettings(timeout, max, r.integer("formulas.max_steps", d.maxSteps, 1000, 10_000_000),
                r.integer("formulas.max_length", d.maxLength, 32, 10_000), r.integer("formulas.megabuf_size", d.megabufSize, 0, 10_000_000));
    }
}
