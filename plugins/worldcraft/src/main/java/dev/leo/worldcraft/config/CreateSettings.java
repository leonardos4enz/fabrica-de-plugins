package dev.leo.worldcraft.config;

import dev.leo.worldcraft.block.BlockRegistry;
import dev.leo.worldcraft.color.BlockColors;
import dev.leo.worldcraft.net.LinkPolicy;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Parser;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Las secciones colors, ores, caves e images de config.yml (plan 035 §2). Un valor malo avisa y usa el de fábrica
 * (constitución I.3); una sección que falta, todo de fábrica (un config.yml de la 0.10.0 carga igual).
 */
public record CreateSettings(String oresMask, String deepslateIn, int maxVein, List<Ore> ores, String cavesMask, int lavaY,
                             String imagesFolder, int imagesMaxSize, int maxFileKb, boolean links, List<String> allowedHosts) {

    /** Un mineral de /wc ores: estados canónicos, bloques por veta, intentos por chunk y alturas. */
    public record Ore(String name, String block, String deepslate, int size, int perChunk, int minY, int maxY) {
    }

    static final List<String> COLOR_KEYS = List.of("remove", "add");
    static final List<String> ORE_KEYS = List.of("mask", "deepslate_in", "max_size", "list");
    static final List<String> ORE_FIELDS = List.of("block", "deepslate", "size", "per_chunk", "min_y", "max_y");
    static final List<String> CAVE_KEYS = List.of("mask", "lava_y");
    static final List<String> IMAGE_KEYS = List.of("folder", "max_size", "max_file_kb", "links", "allowed_hosts");

    static final String ORES_MASK = "stone, deepslate, andesite, diorite, granite, tuff";
    static final String DEEPSLATE_IN = "deepslate, tuff";
    static final String CAVES_MASK = "#base_stone_overworld, #dirt, gravel, sand, sandstone, calcite, *_ore";
    /** name, block, deepslate, size, per_chunk, min_y, max_y (los de config.yml de fábrica). */
    static final Object[][] ORES = {
            {"coal", "coal_ore", "deepslate_coal_ore", 17, 20, 0, 192},
            {"iron", "iron_ore", "deepslate_iron_ore", 9, 10, -24, 56},
            {"copper", "copper_ore", "deepslate_copper_ore", 10, 16, -16, 112},
            {"gold", "gold_ore", "deepslate_gold_ore", 9, 4, -64, 32},
            {"redstone", "redstone_ore", "deepslate_redstone_ore", 8, 4, -64, 15},
            {"lapis", "lapis_ore", "deepslate_lapis_ore", 7, 2, -64, 64},
            {"diamond", "diamond_ore", "deepslate_diamond_ore", 8, 7, -64, 16},
            {"emerald", "emerald_ore", "deepslate_emerald_ore", 3, 3, -16, 320}};

    private static final String FILE = "config.yml";

    /**
     * La tabla de colores: la del jar con los bloques que el servidor conoce, menos colors.remove, más colors.add. Va
     * antes que el analizador (los patrones #color la usan).
     */
    public static BlockColors colors(ConfigurationSection config, BlockRegistry registry, Issues issues) {
        Map<String, Integer> table = new LinkedHashMap<>(BlockColors.builtin());
        java.util.Set<String> known = new java.util.HashSet<>(registry.blocks());
        table.keySet().retainAll(known);
        ConfigurationSection c = config == null ? null : config.getConfigurationSection("colors");
        if (c != null) {
            for (String k : c.getKeys(false)) {
                if (!COLOR_KEYS.contains(k)) issues.warn(FILE, "colors." + k, "unknown setting" + Issues.hint(k, COLOR_KEYS));
            }
            Object rm = c.get("remove");
            if (rm != null && !(rm instanceof List<?>)) issues.error(FILE, "colors.remove", "'" + rm + "' is not a list: nothing removed");
            if (rm instanceof List<?> l) {
                for (Object o : l) {
                    String id = id(String.valueOf(o));
                    if (table.remove(id) == null) {
                        issues.warn(FILE, "colors.remove", "'" + o + "' is not in the color table" + hintBlock(id, table.keySet()));
                    }
                }
            }
            ConfigurationSection add = c.getConfigurationSection("add");
            if (c.get("add") != null && add == null) issues.error(FILE, "colors.add", "write one block per line, like 'gold_block: \"#F9D849\"'");
            if (add != null) {
                for (String k : add.getKeys(false)) {
                    String id = id(k);
                    if (!known.contains(id)) {
                        issues.error(FILE, "colors.add." + k, "unknown block" + hintBlock(id, known) + ": skipped");
                        continue;
                    }
                    try {
                        table.put(id, BlockColors.parse(String.valueOf(add.get(k))));
                    } catch (IllegalArgumentException e) {
                        issues.error(FILE, "colors.add." + k, e.getMessage() + ": skipped");
                    }
                }
            }
        }
        if (table.isEmpty()) {
            issues.error(FILE, "colors", "the color table is empty: using the built-in one");
            table = new LinkedHashMap<>(BlockColors.builtin());
            table.keySet().retainAll(known);
        }
        return new BlockColors(table);
    }

    private static String id(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        return n.contains(":") ? n : "minecraft:" + n;
    }

    private static String hintBlock(String id, java.util.Collection<String> options) {
        String h = Issues.closest(id, options);
        return h == null ? "" : " (did you mean '" + h.replace("minecraft:", "") + "'?)";
    }

    public static CreateSettings defaults(Parser parser) {
        return load(null, parser, new Issues());
    }

    public static CreateSettings load(ConfigurationSection config, Parser parser, Issues issues) {
        Settings.Reader r = new Settings.Reader(config == null ? new org.bukkit.configuration.MemoryConfiguration() : config, FILE, issues);
        unknown(config, "ores", ORE_KEYS, issues);
        unknown(config, "caves", CAVE_KEYS, issues);
        unknown(config, "images", IMAGE_KEYS, issues);
        String oresMask = mask(config, parser, issues, "ores.mask", ORES_MASK);
        String deepslateIn = mask(config, parser, issues, "ores.deepslate_in", DEEPSLATE_IN);
        int maxVein = r.integer("ores.max_size", 64, 1, 256);
        List<Ore> ores = ores(config, parser, issues);
        String cavesMask = mask(config, parser, issues, "caves.mask", CAVES_MASK);
        int lavaY = r.integer("caves.lava_y", -55, -2048, 2047);
        String folder = config == null ? "images" : String.valueOf(config.get("images.folder", "images")).trim();
        if (!folder.matches("[A-Za-z0-9._-]+") || folder.contains("..")) {
            issues.error(FILE, "images.folder", "'" + folder + "' is not a folder name (letters, numbers, . _ -): using images");
            folder = "images";
        }
        int maxSize = r.integer("images.max_size", 256, 1, 1024);
        int maxKb = r.integer("images.max_file_kb", 2048, 1, 20_480);
        boolean links = r.bool("images.links", false);
        List<String> hosts = new ArrayList<>();
        for (String h : r.list("images.allowed_hosts", List.of())) {
            String v = h.toLowerCase(Locale.ROOT);
            if (LinkPolicy.HOST_PATTERN.matcher(v).matches()) hosts.add(v);
            else issues.error(FILE, "images.allowed_hosts", "'" + h + "' is not a site (like i.imgur.com or *.imgur.com): skipped");
        }
        return new CreateSettings(oresMask, deepslateIn, maxVein, List.copyOf(ores), cavesMask, lavaY, folder, maxSize, maxKb, links,
                List.copyOf(hosts));
    }

    private static void unknown(ConfigurationSection config, String section, List<String> keys, Issues issues) {
        ConfigurationSection s = config == null ? null : config.getConfigurationSection(section);
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            if (!keys.contains(k)) issues.warn(FILE, section + "." + k, "unknown setting" + Issues.hint(k, keys));
        }
    }

    private static String mask(ConfigurationSection config, Parser parser, Issues issues, String path, String def) {
        Object v = config == null ? null : config.get(path);
        if (v == null) return def;
        String text = v instanceof List<?> l ? String.join(", ", l.stream().map(String::valueOf).toList()) : String.valueOf(v);
        try {
            parser.mask(text);
            return text;
        } catch (ParseException e) {
            issues.error(FILE, path, e.getMessage() + ": using the default");
            return def;
        }
    }

    private static List<Ore> ores(ConfigurationSection config, Parser parser, Issues issues) {
        List<Ore> out = new ArrayList<>();
        ConfigurationSection list = config == null ? null : config.getConfigurationSection("ores.list");
        if (list == null) {
            if (config != null && config.get("ores.list") != null) issues.error(FILE, "ores.list", "write one ore per line: using the default list");
            for (Object[] o : ORES) {
                Ore ore = ore(parser, issues, "ores.list." + o[0], (String) o[0], (String) o[1], (String) o[2],
                        (int) o[3], (int) o[4], (int) o[5], (int) o[6]);
                if (ore != null) out.add(ore);
            }
            return out;
        }
        for (String name : list.getKeys(false)) {
            String path = "ores.list." + name;
            ConfigurationSection o = list.getConfigurationSection(name);
            if (o == null) {
                issues.error(FILE, path, "write { block: ..., size: ..., per_chunk: ..., min_y: ..., max_y: ... }: skipped");
                continue;
            }
            for (String k : o.getKeys(false)) {
                if (!ORE_FIELDS.contains(k)) issues.warn(FILE, path + "." + k, "unknown setting" + Issues.hint(k, ORE_FIELDS));
            }
            Object block = o.get("block");
            if (block == null) {
                issues.error(FILE, path + ".block", "missing: skipped");
                continue;
            }
            Settings.Reader r = new Settings.Reader(o, FILE + " (" + path + ")", issues);
            Ore ore = ore(parser, issues, path, name, String.valueOf(block), o.get("deepslate") == null ? null : String.valueOf(o.get("deepslate")),
                    r.integer("size", 8, 1, 256), r.integer("per_chunk", 4, 1, 1000), r.integer("min_y", -64, -2048, 2047),
                    r.integer("max_y", 320, -2048, 2047));
            if (ore != null) out.add(ore);
        }
        return out;
    }

    private static Ore ore(Parser parser, Issues issues, String path, String name, String block, String deepslate,
                           int size, int perChunk, int minY, int maxY) {
        String b = state(parser, issues, path + ".block", block);
        if (b == null) return null;
        String d = deepslate == null ? b : state(parser, issues, path + ".deepslate", deepslate);
        if (d == null) d = b;
        if (minY > maxY) {
            issues.error(FILE, path, "min_y (" + minY + ") is above max_y (" + maxY + "): skipped");
            return null;
        }
        return new Ore(name, b, d, size, perChunk, minY, maxY);
    }

    private static String state(Parser parser, Issues issues, String path, String text) {
        try {
            var p = parser.pattern(text);
            if (p.states().size() != 1 || p.placedStates().size() != 1) throw new ParseException("write one block, like 'coal_ore'");
            return p.placedStates().get(0);
        } catch (ParseException e) {
            issues.error(FILE, path, e.getMessage() + ": skipped");
            return null;
        }
    }
}
