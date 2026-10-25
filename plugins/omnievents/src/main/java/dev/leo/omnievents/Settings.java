package dev.leo.omnievents;

import dev.leo.omnievents.util.Durations;
import org.bukkit.configuration.file.FileConfiguration;

/** Opciones de config.yml (plan 001 §2.1; profiler y debug: spec 006; object-data: spec 009). */
public record Settings(boolean cooldownBypassOp, boolean allowUnsafeMethods, int maxLoopIterations, int maxCallDepth,
                       int autosaveMinutes, boolean placeholderApi, int sampleEvery, long budgetWarnEveryMs, int exportBuffer,
                       int objectMaxText, int objectMaxVars) {

    public static Settings from(FileConfiguration c) {
        long warnEvery;
        try {
            warnEvery = Durations.parseMillis(c.getString("profiler.budget-warn-every", "5m"));
        } catch (IllegalArgumentException e) {
            warnEvery = 300_000;
        }
        return new Settings(
                c.getBoolean("cooldown-bypass-op", false),
                c.getBoolean("expressions.allow-unsafe-methods", false),
                Math.max(1, c.getInt("expressions.max-loop-iterations", 1000)),
                Math.max(1, c.getInt("expressions.max-call-depth", 16)),
                Math.max(1, c.getInt("storage.autosave-minutes", 5)),
                c.getBoolean("placeholderapi", true),
                Math.max(1, c.getInt("profiler.sample-every", 10)),
                Math.max(0, warnEvery),
                Math.max(1, Math.min(5000, c.getInt("debug.export-buffer", 200))),
                Math.max(1, Math.min(65536, c.getInt("object-data.max-text-length", 256))),
                Math.max(1, Math.min(1024, c.getInt("object-data.max-vars", 32))));
    }
}
