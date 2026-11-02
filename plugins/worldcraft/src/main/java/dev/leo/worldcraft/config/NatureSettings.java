package dev.leo.worldcraft.config;

import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Parser;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/** La sección nature de config.yml (plan 033 §2): árboles, plantas y calabazas. */
public record NatureSettings(int maxTrees, int maxSize, String soil, String floraGrass, String floraSand) {
    public static final NatureSettings DEFAULTS = new NatureSettings(300, 32,
            "grass_block, dirt, podzol, coarse_dirt, rooted_dirt, mycelium, moss_block, mud",
            "80% short_grass, 5% dandelion, 5% poppy, 4% cornflower, 3% oxeye_daisy, 3% azure_bluet",
            "90% dead_bush, 10% cactus");

    static final List<String> KEYS = List.of("max_trees", "max_size", "soil", "flora_grass", "flora_sand");

    /** Un valor malo avisa y usa el de fábrica (constitución I.3); una sección que falta, todo de fábrica. */
    public static NatureSettings load(ConfigurationSection config, Parser parser, Issues issues) {
        ConfigurationSection c = config == null ? null : config.getConfigurationSection("nature");
        if (c == null) return DEFAULTS;
        String file = "config.yml";
        for (String k : c.getKeys(false)) {
            if (!KEYS.contains(k)) issues.warn(file, "nature." + k, "unknown setting" + Issues.hint(k, KEYS));
        }
        NatureSettings d = DEFAULTS;
        Settings.Reader r = new Settings.Reader(config, file, issues);
        return new NatureSettings(r.integer("nature.max_trees", d.maxTrees, 1, 5000), r.integer("nature.max_size", d.maxSize, 1, 64),
                text(c, parser, issues, "soil", d.soil, true), text(c, parser, issues, "flora_grass", d.floraGrass, false),
                text(c, parser, issues, "flora_sand", d.floraSand, false));
    }

    private static String text(ConfigurationSection c, Parser parser, Issues issues, String key, String def, boolean mask) {
        Object v = c.get(key);
        if (v == null) return def;
        String text = v instanceof List<?> l ? String.join(", ", l.stream().map(String::valueOf).toList()) : String.valueOf(v);
        try {
            if (mask) parser.mask(text);
            else parser.pattern(text);
            return text;
        } catch (ParseException e) {
            issues.error("config.yml", "nature." + key, e.getMessage() + ": using the default");
            return def;
        }
    }
}
