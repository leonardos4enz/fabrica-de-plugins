package dev.leo.worldcraft.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** config.yml (plan 025 §2) validado: un valor inválido avisa y se usa el de por defecto (constitución I.3). */
public record Settings(String wandItem, boolean showSelection, int showEveryTicks, int maxParticles,
                       int maxMsPerTick, int maxBlocksPerTick, boolean confirmEnabled, long confirmAbove, int confirmSeconds, int previewMaxBlocks,
                       int historyMaxPerPlayer, int historyKeepDays, Schematics schematics,
                       int shapesMaxRadius, int utilitiesMaxRadius, int maxPoints, int magicRadius,
                       int butcherRadius, int entitiesMaxRadius) {

    /** Spec 027: la sección schematics (plan 027 §2). */
    public record Schematics(int maxFileKb, int maxBlocks, int trashDays, List<String> extraFolders) {
        public static final Schematics DEFAULTS = new Schematics(10_240, 5_000_000, 7,
                List.of("plugins/WorldEdit/schematics", "plugins/FastAsyncWorldEdit/schematics"));
    }

    public static final Settings DEFAULTS = new Settings("minecraft:golden_axe", true, 10, 400,
            8, 200_000, true, 50_000, 120, 50_000, 30, 7, Schematics.DEFAULTS, 200, 64, 64, 16, 32, 128);

    private static final Map<String, List<String>> KEYS = new java.util.HashMap<>(Map.of(
            "", List.of("wand", "selection", "performance", "confirm", "preview", "history", "limits", "terrain", "schematics", "shapes", "utilities", "entities"),
            "wand", List.of("item"),
            "selection", List.of("show", "show_every_ticks", "max_particles", "max_points", "magic_radius"),
            "performance", List.of("max_ms_per_tick", "max_blocks_per_tick"),
            "confirm", List.of("enabled", "above_blocks", "expire_seconds"),
            "preview", List.of("max_blocks"),
            "history", List.of("max_per_player", "keep_days"),
            "schematics", List.of("max_file_kb", "max_blocks", "trash_days", "extra_folders"),
            "shapes", List.of("max_radius"),
            "utilities", List.of("max_radius")));

    static {
        KEYS.put("entities", List.of("butcher_radius", "max_radius"));
    }

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
        int entitiesMax = r.integer("entities.max_radius", d.entitiesMaxRadius, 1, 512);
        return new Settings(
                r.text("wand.item", d.wandItem),
                r.bool("selection.show", d.showSelection),
                r.integer("selection.show_every_ticks", d.showEveryTicks, 2, 200),
                r.integer("selection.max_particles", d.maxParticles, 12, 5000),
                r.integer("performance.max_ms_per_tick", d.maxMsPerTick, 1, 40),
                r.integer("performance.max_blocks_per_tick", d.maxBlocksPerTick, 100, 5_000_000),
                r.bool("confirm.enabled", d.confirmEnabled),
                r.integer("confirm.above_blocks", (int) d.confirmAbove, 0, Integer.MAX_VALUE),
                r.integer("confirm.expire_seconds", d.confirmSeconds, 10, 3600),
                r.integer("preview.max_blocks", d.previewMaxBlocks, 0, 1_000_000),
                r.integer("history.max_per_player", d.historyMaxPerPlayer, 1, 1000),
                r.integer("history.keep_days", d.historyKeepDays, 0, 3650),
                new Schematics(
                        r.integer("schematics.max_file_kb", d.schematics.maxFileKb(), 1, 1_048_576),
                        r.integer("schematics.max_blocks", d.schematics.maxBlocks(), 1, 100_000_000),
                        r.integer("schematics.trash_days", d.schematics.trashDays(), 0, 3650),
                        r.list("schematics.extra_folders", d.schematics.extraFolders())),
                // Spec 028: formas y utilidades alrededor del jugador.
                r.integer("shapes.max_radius", d.shapesMaxRadius, 1, 1000),
                r.integer("utilities.max_radius", d.utilitiesMaxRadius, 1, 256),
                // Spec 030: selecciones que no son cajas.
                r.integer("selection.max_points", d.maxPoints, 3, 256),
                r.integer("selection.magic_radius", d.magicRadius, 1, 64),
                // Spec 031: butcher y remove.
                Math.min(r.integer("entities.butcher_radius", d.butcherRadius, 1, 512), entitiesMax), entitiesMax);
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

        List<String> list(String path, List<String> def) {
            Object v = c.get(path);
            if (v == null) return def;
            if (!(v instanceof List<?> l)) {
                issues.error(file, path, "'" + v + "' is not a list: using the default");
                return def;
            }
            return l.stream().map(String::valueOf).map(String::trim).filter(x -> !x.isEmpty()).toList();
        }

        String text(String path, String def) {
            Object v = c.get(path);
            return v == null ? def : String.valueOf(v).toLowerCase(Locale.ROOT).trim();
        }
    }
}
