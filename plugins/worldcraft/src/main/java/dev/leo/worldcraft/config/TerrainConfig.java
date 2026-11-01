package dev.leo.worldcraft.config;

import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Parser;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.terrain.Options;
import dev.leo.worldcraft.terrain.Style;
import dev.leo.worldcraft.terrain.TerrainSettings;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lee y valida la sección {@code terrain} de config.yml y terrain.yml (plan 026 §2). Un valor malo avisa y usa el de
 * fábrica; una regla, capa o estilo malo se omite y se dice dónde (constitución I.3).
 */
public final class TerrainConfig {
    private TerrainConfig() {
    }

    public static final String GROUND = "#dirt, #sand, #base_stone_overworld, #base_stone_nether, #terracotta, #nylium, "
            + "gravel, clay, sandstone, red_sandstone, calcite, dripstone_block, end_stone, snow_block, powder_snow, ice, "
            + "packed_ice, blue_ice, magma_block, obsidian, #coal_ores, #iron_ores, #copper_ores, #gold_ores, "
            + "#redstone_ores, #lapis_ores, #diamond_ores, #emerald_ores";
    public static final String COVER = "air, #replaceable, #flowers, #saplings";
    public static final String BURYABLE = "#logs, #leaves";

    private static final List<String> SECTION = List.of("preview_first", "reach", "max_radius", "ground", "cover", "buryable", "brush");
    private static final List<String> BRUSH = List.of("item", "max_radius", "range", "show");
    private static final List<String> FILE = List.of("sizes", "defaults", "styles");
    private static final List<String> STYLE = List.of("description", "blend", "options", "layers", "hanging", "tree");
    private static final List<String> RULE = List.of("block", "height_above", "height_below", "slope_above", "slope_below", "y_above", "y_below");
    private static final List<String> TOOLS = List.of("mountain", "island");

    /** Un estilo mínimo si terrain.yml no deja ninguno válido. */
    static Style fallback(Parser parser) {
        try {
            return new Style("basic", "Grass, dirt and stone", 0, Map.of(), List.of(
                    new Style.Layer(1, List.of(Style.Rule.always(parser.pattern("grass_block")))),
                    new Style.Layer(3, List.of(Style.Rule.always(parser.pattern("dirt")))),
                    new Style.Layer(0, List.of(Style.Rule.always(parser.pattern("stone"))))), null, null, null);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @param section la sección terrain de config.yml (null si no está)
     * @param file    terrain.yml (null si no se pudo leer)
     */
    public static TerrainSettings load(ConfigurationSection section, ConfigurationSection file, Parser parser, Issues issues) {
        String cf = "config.yml", tf = "terrain.yml";
        ConfigurationSection s = section;
        if (s != null) {
            unknown(s, SECTION, cf, "terrain", issues);
            if (s.getConfigurationSection("brush") != null) unknown(s.getConfigurationSection("brush"), BRUSH, cf, "terrain.brush", issues);
        }
        // Desde la raíz, para que cada aviso diga la ruta completa («terrain.reach»).
        Settings.Reader r = new Settings.Reader(s == null ? new org.bukkit.configuration.MemoryConfiguration() : s.getRoot(), cf, issues);
        boolean preview = r.bool("terrain.preview_first", true);
        int reach = r.integer("terrain.reach", 200, 1, 512);
        int maxRadius = r.integer("terrain.max_radius", 160, 4, 512);
        Mask ground = mask(parser, s, "ground", GROUND, issues);
        Mask cover = mask(parser, s, "cover", COVER, issues);
        Mask buryable = mask(parser, s, "buryable", BURYABLE, issues);
        String item = r.text("terrain.brush.item", "minecraft:golden_shovel");
        int brushMax = r.integer("terrain.brush.max_radius", 16, 1, 64);
        int range = r.integer("terrain.brush.range", 200, 1, 512);
        boolean show = r.bool("terrain.brush.show", true);

        if (file != null) unknown(file, FILE, tf, "", issues);
        Map<String, Style> styles = new LinkedHashMap<>();
        ConfigurationSection st = file == null ? null : file.getConfigurationSection("styles");
        if (st != null) {
            for (String name : st.getKeys(false)) {
                if (!name.matches("[a-z0-9_-]+")) {
                    issues.error(tf, "styles." + name, "style names use lowercase letters, digits, _ and -: skipped");
                    continue;
                }
                Style style = style(name, st.getConfigurationSection(name), parser, tf, "styles." + name, issues);
                if (style != null) styles.put(name, style);
            }
        }
        if (styles.isEmpty()) {
            issues.error(tf, "styles", "no valid style: using a basic grass, dirt and stone one");
            styles.put("basic", fallback(parser));
        }
        Map<String, Map<String, Map<String, Double>>> sizes = new LinkedHashMap<>();
        Map<String, Map<String, Object>> defaults = new LinkedHashMap<>();
        for (String tool : TOOLS) {
            Options.Tool t = Options.Tool.valueOf(tool.toUpperCase(Locale.ROOT));
            Map<String, Options.Key> keys = Options.of(t);
            Map<String, Map<String, Double>> perSize = new LinkedHashMap<>();
            ConfigurationSection sz = file == null ? null : file.getConfigurationSection("sizes." + tool);
            if (sz != null) {
                for (String size : sz.getKeys(false)) {
                    if (!Options.SIZES.contains(size)) {
                        issues.warn(tf, "sizes." + tool + "." + size, "unknown size" + Issues.hint(size, Options.SIZES));
                        continue;
                    }
                    ConfigurationSection one = sz.getConfigurationSection(size);
                    if (one == null) continue;
                    Map<String, Double> m = numbers(one, keys, List.of("radius", "height"), tf, "sizes." + tool + "." + size, issues);
                    perSize.put(size, m);
                }
            }
            for (String size : Options.SIZES) {
                if (!perSize.containsKey(size)) issues.warn(tf, "sizes." + tool, "missing size '" + size + "'");
            }
            sizes.put(tool, perSize);
            Map<String, Object> def = new LinkedHashMap<>();
            ConfigurationSection d = file == null ? null : file.getConfigurationSection("defaults." + tool);
            if (d != null) {
                List<String> allowed = new ArrayList<>(keys.keySet());
                allowed.remove("seed");
                allowed.remove("radius");
                def.putAll(numbers(d, keys, allowed, tf, "defaults." + tool, issues));
                String styleName = d.getString("style");
                if (styleName != null) {
                    if (styles.containsKey(styleName)) def.put("style", styleName);
                    else issues.error(tf, "defaults." + tool + ".style", "unknown style '" + styleName + "'" + Issues.hint(styleName, styles.keySet()));
                }
            }
            for (String k : keys.keySet()) {
                if (!k.equals("seed") && !k.equals("radius") && !k.equals("height") && !def.containsKey(k)) {
                    issues.error(tf, "defaults." + tool, "missing '" + k + "'");
                }
            }
            defaults.put(tool, def);
        }
        return new TerrainSettings(preview, reach, maxRadius, ground, cover, buryable, item, brushMax, range, show,
                styles, sizes, defaults);
    }

    private static Mask mask(Parser parser, ConfigurationSection s, String key, String def, Issues issues) {
        String text = s == null ? null : s.getString(key);
        if (text != null) {
            try {
                return parser.mask(text);
            } catch (ParseException e) {
                issues.error("config.yml", "terrain." + key, e.getMessage() + ": using the default");
            }
        }
        try {
            return parser.mask(def);
        } catch (ParseException e) {
            // Un servidor sin alguna etiqueta (otra versión): lo que se pueda leer de la lista de fábrica.
            List<String> ok = new ArrayList<>();
            for (String part : def.split(",")) {
                try {
                    parser.mask(part.trim());
                    ok.add(part.trim());
                } catch (ParseException ignored) {
                    // sin esa parte
                }
            }
            try {
                return parser.mask(ok.isEmpty() ? "stone" : String.join(", ", ok));
            } catch (ParseException e2) {
                throw new IllegalStateException(e2);
            }
        }
    }

    private static Map<String, Double> numbers(ConfigurationSection sec, Map<String, Options.Key> keys, List<String> allowed,
                                               String file, String path, Issues issues) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String k : sec.getKeys(false)) {
            if (k.equals("style")) continue;
            if (!allowed.contains(k)) {
                issues.warn(file, path + "." + k, "unknown option" + Issues.hint(k, allowed));
                continue;
            }
            Object v = sec.get(k);
            Options.Key key = keys.get(k);
            if (!(v instanceof Number n)) {
                issues.error(file, path + "." + k, "'" + v + "' is not a number");
                continue;
            }
            double d = n.doubleValue();
            if (d < key.min() || d > key.max()) {
                issues.error(file, path + "." + k, Options.fmt(d) + " is out of range (" + Options.fmt(key.min()) + ".." + Options.fmt(key.max()) + ")");
                continue;
            }
            out.put(k, d);
        }
        return out;
    }

    static Style style(String name, ConfigurationSection s, Parser parser, String file, String path, Issues issues) {
        if (s == null) {
            issues.error(file, path, "a style is a section with layers: skipped");
            return null;
        }
        unknown(s, STYLE, file, path, issues);
        double blend = 0;
        Object b = s.get("blend");
        if (b != null) {
            if (b instanceof Number n && n.doubleValue() >= 0 && n.doubleValue() <= 0.3) blend = n.doubleValue();
            else issues.error(file, path + ".blend", "'" + b + "' must be a number from 0 to 0.3: using 0");
        }
        Map<String, Double> options = new LinkedHashMap<>();
        ConfigurationSection o = s.getConfigurationSection("options");
        if (o != null) {
            Map<String, Options.Key> all = new LinkedHashMap<>(Options.MOUNTAIN);
            Options.ISLAND.forEach(all::putIfAbsent);
            List<String> allowed = new ArrayList<>(all.keySet());
            allowed.remove("seed");
            options.putAll(numbers(o, all, allowed, file, path + ".options", issues));
        }
        List<Style.Layer> layers = new ArrayList<>();
        List<?> raw = s.getList("layers");
        if (raw == null || raw.isEmpty()) {
            issues.error(file, path + ".layers", "a style needs at least one layer: skipped");
            return null;
        }
        for (int li = 0; li < raw.size(); li++) {
            String lp = path + ".layers[" + li + "]";
            if (!(raw.get(li) instanceof Map<?, ?> lm)) {
                issues.error(file, lp, "a layer is {thickness: N, rules: [...]}: skipped");
                continue;
            }
            for (Object k : lm.keySet()) {
                if (!List.of("thickness", "rules").contains(String.valueOf(k))) {
                    issues.warn(file, lp + "." + k, "unknown key" + Issues.hint(String.valueOf(k), List.of("thickness", "rules")));
                }
            }
            int thickness = 0;
            Object t = lm.get("thickness");
            if (t != null) {
                if (t instanceof Integer n && n >= 1 && n <= 64) thickness = n;
                else issues.error(file, lp + ".thickness", "'" + t + "' must be a whole number from 1 to 64: this layer fills the rest");
            }
            List<Style.Rule> rules = new ArrayList<>();
            if (lm.get("rules") instanceof List<?> rl) {
                for (int ri = 0; ri < rl.size(); ri++) {
                    Style.Rule rule = rule(rl.get(ri), parser, file, lp + ".rules[" + ri + "]", issues);
                    if (rule != null) rules.add(rule);
                }
            }
            if (rules.isEmpty()) {
                issues.error(file, lp + ".rules", "no valid rule: layer skipped");
                continue;
            }
            layers.add(new Style.Layer(thickness, rules));
        }
        if (layers.isEmpty()) {
            issues.error(file, path, "no valid layer: style skipped");
            return null;
        }
        Pattern hanging = pattern(parser, s.getString("hanging"), file, path + ".hanging", issues);
        Pattern log = null, leaves = null;
        ConfigurationSection tree = s.getConfigurationSection("tree");
        if (tree != null) {
            unknown(tree, List.of("log", "leaves"), file, path + ".tree", issues);
            log = pattern(parser, tree.getString("log"), file, path + ".tree.log", issues);
            leaves = persistent(pattern(parser, tree.getString("leaves"), file, path + ".tree.leaves", issues));
            if (log == null || leaves == null) {
                issues.error(file, path + ".tree", "a tree needs log and leaves: no trees");
                log = leaves = null;
            }
        }
        return new Style(name, s.getString("description", ""), blend, options, layers, hanging, log, leaves);
    }

    /** Hojas que no se caen: las que tienen «persistent» la ponen en true. */
    static Pattern persistent(Pattern p) {
        if (p == null) return null;
        List<String> states = new ArrayList<>();
        for (String st : p.states()) states.add(st.replace("persistent=false", "persistent=true"));
        return new Pattern(states, p.weights(), p.text());
    }

    private static Pattern pattern(Parser parser, String text, String file, String path, Issues issues) {
        if (text == null) return null;
        try {
            return parser.pattern(text);
        } catch (ParseException e) {
            issues.error(file, path, e.getMessage());
            return null;
        }
    }

    private static Style.Rule rule(Object o, Parser parser, String file, String path, Issues issues) {
        if (!(o instanceof Map<?, ?> m)) {
            issues.error(file, path, "a rule is {block: ..., slope_above: ...}: skipped");
            return null;
        }
        for (Object k : m.keySet()) {
            if (!RULE.contains(String.valueOf(k))) issues.warn(file, path + "." + k, "unknown condition" + Issues.hint(String.valueOf(k), RULE));
        }
        Object block = m.get("block");
        if (block == null) {
            issues.error(file, path, "missing 'block': rule skipped");
            return null;
        }
        Pattern p = pattern(parser, String.valueOf(block), file, path + ".block", issues);
        if (p == null) return null;
        double[] v = new double[6];
        String[] names = {"height_above", "height_below", "slope_above", "slope_below", "y_above", "y_below"};
        double[][] range = {{0, 1}, {0, 1}, {0, 90}, {0, 90}, {-2048, 2048}, {-2048, 2048}};
        for (int i = 0; i < 6; i++) {
            Object x = m.get(names[i]);
            v[i] = Double.NaN;
            if (x == null) continue;
            if (!(x instanceof Number n) || n.doubleValue() < range[i][0] || n.doubleValue() > range[i][1]) {
                issues.error(file, path + "." + names[i], "'" + x + "' must be a number from " + Options.fmt(range[i][0])
                        + " to " + Options.fmt(range[i][1]) + ": rule skipped");
                return null;
            }
            v[i] = n.doubleValue();
        }
        return new Style.Rule(p, v[0], v[1], v[2], v[3], v[4], v[5]);
    }

    private static void unknown(ConfigurationSection s, List<String> keys, String file, String path, Issues issues) {
        for (String k : s.getKeys(false)) {
            if (!keys.contains(k)) issues.warn(file, path.isEmpty() ? k : path + "." + k, "unknown setting" + Issues.hint(k, keys));
        }
    }
}
