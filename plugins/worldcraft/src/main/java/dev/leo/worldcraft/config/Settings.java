package dev.leo.blockwright.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** config.yml (plan 025 §2) validado: un valor inválido avisa y se usa el de por defecto (constitución I.3). */
public record Settings(String wandItem, boolean showSelection, int showEveryTicks, int maxParticles,
                       int maxMsPerTick, int maxBlocksPerTick, long confirmAbove, int confirmSeconds, int previewMaxBlocks,
                       int historyMaxPerPlayer, int historyKeepDays) {

    public static final Settings DEFAULTS = new Settings("minecraft:golden_axe", true, 10, 400,
            8, 200_000, 20_000, 120, 50_000, 30, 7);

    private static final Map<String, List<String>> KEYS = Map.of(
            "", List.of("wand", "selection", "performance", "confirm", "preview", "history", "limits"),
            "wand", List.of("item"),
            "selection", List.of("show", "show_every_ticks", "max_particles"),
            "performance", List.of("max_ms_per_tick", "max_blocks_per_tick"),
            "confirm", List.of("above_blocks", "expire_seconds"),
            "preview", List.of("max_blocks"),
            "history", List.of("max_per_player", "keep_days"));

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
        return new Settings(
                r.text("wand.item", d.wandItem),
                r.bool("selection.show", d.showSelection),
                r.integer("selection.show_every_ticks", d.showEveryTicks, 2, 200),
                r.integer("selection.max_particles", d.maxParticles, 12, 5000),
                r.integer("performance.max_ms_per_tick", d.maxMsPerTick, 1, 40),
                r.integer("performance.max_blocks_per_tick", d.maxBlocksPerTick, 100, 5_000_000),
                r.integer("confirm.above_blocks", (int) d.confirmAbove, 0, Integer.MAX_VALUE),
                r.integer("confirm.expire_seconds", d.confirmSeconds, 10, 3600),
                r.integer("preview.max_blocks", d.previewMaxBlocks, 0, 1_000_000),
                r.integer("history.max_per_player", d.historyMaxPerPlayer, 1, 1000),
                r.integer("history.keep_days", d.historyKeepDays, 0, 3650));
    }

    record Reader(ConfigurationSection c, String file, Issues issues) {
        int integer(String path, int def, int lo, int hi) {
            Object v = c.get(path);
            if (v == null) return def;
            if (!(v instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
                issues.error(file, path, "'" + v + "' is not a whole number: using " + def);
                return def;
            }
            long i = n.longValue();
            if (i < lo || i > hi) {
                issues.error(file, path, i + " is out of range (" + lo + ".." + hi + "): using " + def);
                return def;
            }
            return (int) i;
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
            return v == null ? def : String.valueOf(v).toLowerCase(Locale.ROOT).trim();
        }
    }
}
