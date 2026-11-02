package dev.leo.worldcraft.config;

import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Parser;
import dev.leo.worldcraft.tool.ToolSpec;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/** La sección tools de config.yml (plan 032 §2): pinceles de construcción, herramientas y superpico. */
public record ToolSettings(String item, String navwandItem, int range, boolean show, int brushMaxRadius, int maxRange,
                           int deltreeRange, int superpickaxeMaxRadius, String treeBlocks, String neverBreak) {
    public static final ToolSettings DEFAULTS = new ToolSettings("minecraft:blaze_rod", "minecraft:compass", 200, true, 16, 32,
            24, 5, "#logs, #leaves, vine, cocoa, bee_nest, mangrove_roots",
            "bedrock, barrier, end_portal_frame, end_portal, end_gateway, nether_portal, command_block, chain_command_block, "
                    + "repeating_command_block, structure_block, jigsaw");

    static final List<String> KEYS = List.of("item", "navwand_item", "range", "show", "brush_max_radius", "max_range",
            "deltree_range", "superpickaxe_max_radius", "tree_blocks", "never_break");

    public ToolSpec.Caps caps() {
        return new ToolSpec.Caps(brushMaxRadius, maxRange);
    }

    /** Un valor malo avisa y usa el de fábrica (constitución I.3); una sección que falta, todo de fábrica. */
    public static ToolSettings load(ConfigurationSection config, Parser parser, Issues issues) {
        ConfigurationSection c = config == null ? null : config.getConfigurationSection("tools");
        if (c == null) return DEFAULTS;
        String file = "config.yml";
        for (String k : c.getKeys(false)) {
            if (!KEYS.contains(k)) issues.warn(file, "tools." + k, "unknown setting" + Issues.hint(k, KEYS));
        }
        ToolSettings d = DEFAULTS;
        // Desde la raíz con la ruta completa: los avisos dicen «tools.range» (lección 026 F-02).
        Settings.Reader r = new Settings.Reader(config, file, issues);
        return new ToolSettings(r.text("tools.item", d.item), r.text("tools.navwand_item", d.navwandItem),
                r.integer("tools.range", d.range, 1, 512), r.bool("tools.show", d.show),
                r.integer("tools.brush_max_radius", d.brushMaxRadius, 0, 64), r.integer("tools.max_range", d.maxRange, 1, 64),
                r.integer("tools.deltree_range", d.deltreeRange, 4, 64),
                r.integer("tools.superpickaxe_max_radius", d.superpickaxeMaxRadius, 1, 16),
                mask(c, parser, issues, "tree_blocks", d.treeBlocks), mask(c, parser, issues, "never_break", d.neverBreak));
    }

    private static String mask(ConfigurationSection c, Parser parser, Issues issues, String key, String def) {
        Object v = c.get(key);
        if (v == null) return def;
        String text = v instanceof List<?> l ? String.join(", ", l.stream().map(String::valueOf).toList()) : String.valueOf(v);
        try {
            parser.mask(text);
            return text;
        } catch (ParseException e) {
            issues.error("config.yml", "tools." + key, e.getMessage() + ": using the default");
            return def;
        }
    }
}
