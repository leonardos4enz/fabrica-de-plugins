package dev.leo.skinstatues.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** config.yml (plan 021 §2) validado: un valor inválido avisa y se usa el de por defecto. */
public record Settings(int minHeight, int maxHeight, int maxBlocks, int offsetUp, String defaultPose, String defaultPalette,
                       boolean overlay, double overlayDepth, int alphaThreshold, boolean cape,
                       int blocksPerTick, int maxMsPerTick, boolean mojang, int cacheHours) {

    public static final Settings DEFAULTS = new Settings(8, 128, 400_000, 2, "standing", "all", true, 0.5, 128, true,
            4000, 10, false, 24);

    private static final Map<String, List<String>> KEYS = Map.of(
            "", List.of("min_height", "max_height", "max_blocks", "offset_up", "default_pose", "default_palette",
                    "overlay", "cape", "build", "network"),
            "overlay", List.of("enabled", "depth", "alpha_threshold"),
            "build", List.of("blocks_per_tick", "max_ms_per_tick"),
            "network", List.of("mojang", "cache_hours"));

    public static Settings load(ConfigurationSection c, String file, Issues issues) {
        for (Map.Entry<String, List<String>> e : KEYS.entrySet()) {
            ConfigurationSection sec = e.getKey().isEmpty() ? c : c.getConfigurationSection(e.getKey());
            if (sec == null) continue;
            for (String k : sec.getKeys(false)) {
                if (!e.getValue().contains(k)) {
                    String path = e.getKey().isEmpty() ? k : e.getKey() + "." + k;
                    issues.warn(file, path, "unknown setting" + Issues.hint(k, e.getValue()));
                }
            }
        }
        Settings d = DEFAULTS;
        Reader r = new Reader(c, file, issues);
        int min = r.integer("min_height", d.minHeight, 1, 4096);
        int max = r.integer("max_height", d.maxHeight, 1, 4096);
        if (max < min) {
            issues.error(file, "max_height", "max_height (" + max + ") is lower than min_height (" + min + "): using " + min);
            max = min;
        }
        return new Settings(min, max,
                r.integer("max_blocks", d.maxBlocks, 1, 50_000_000),
                r.integer("offset_up", d.offsetUp, -64, 64),
                r.text("default_pose", d.defaultPose),
                r.text("default_palette", d.defaultPalette),
                r.bool("overlay.enabled", d.overlay),
                r.decimal("overlay.depth", d.overlayDepth, 0, 4),
                r.integer("overlay.alpha_threshold", d.alphaThreshold, 1, 255),
                r.bool("cape", d.cape),
                r.integer("build.blocks_per_tick", d.blocksPerTick, 1, 1_000_000),
                r.integer("build.max_ms_per_tick", d.maxMsPerTick, 1, 45),
                r.bool("network.mojang", d.mojang),
                r.integer("network.cache_hours", d.cacheHours, 0, 24 * 365));
    }

    private record Reader(ConfigurationSection c, String file, Issues issues) {
        int integer(String path, int def, int lo, int hi) {
            Object v = c.get(path);
            if (v == null) return def;
            if (!(v instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
                issues.error(file, path, "'" + v + "' is not a whole number: using " + def);
                return def;
            }
            int i = n.intValue();
            if (i < lo || i > hi) {
                issues.error(file, path, i + " is out of range (" + lo + ".." + hi + "): using " + def);
                return def;
            }
            return i;
        }

        double decimal(String path, double def, double lo, double hi) {
            Object v = c.get(path);
            if (v == null) return def;
            if (!(v instanceof Number n)) {
                issues.error(file, path, "'" + v + "' is not a number: using " + def);
                return def;
            }
            double x = n.doubleValue();
            if (x < lo || x > hi) {
                issues.error(file, path, x + " is out of range (" + lo + ".." + hi + "): using " + def);
                return def;
            }
            return x;
        }

        boolean bool(String path, boolean def) {
            Object v = c.get(path);
            if (v == null) return def;
            if (!(v instanceof Boolean b)) {
                issues.error(file, path, "'" + v + "' is not true or false: using " + def);
                return def;
            }
            return b;
        }

        String text(String path, String def) {
            Object v = c.get(path);
            return v == null ? def : String.valueOf(v).toLowerCase(Locale.ROOT);
        }
    }
}
