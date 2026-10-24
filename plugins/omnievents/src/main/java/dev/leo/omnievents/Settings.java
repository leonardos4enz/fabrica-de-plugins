package dev.leo.omnievents;

import org.bukkit.configuration.file.FileConfiguration;

/** Opciones de config.yml (plan 001 §2.1). */
public record Settings(boolean cooldownBypassOp, boolean allowUnsafeMethods, int maxLoopIterations, int maxCallDepth,
                       int autosaveMinutes, boolean placeholderApi) {

    public static Settings from(FileConfiguration c) {
        return new Settings(
                c.getBoolean("cooldown-bypass-op", false),
                c.getBoolean("expressions.allow-unsafe-methods", false),
                Math.max(1, c.getInt("expressions.max-loop-iterations", 1000)),
                Math.max(1, c.getInt("expressions.max-call-depth", 16)),
                Math.max(1, c.getInt("storage.autosave-minutes", 5)),
                c.getBoolean("placeholderapi", true));
    }
}
