package dev.leo.worldcraft.tool;

import dev.leo.worldcraft.config.Issues;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Parser;
import dev.leo.worldcraft.pattern.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lo que hace un ítem en cada bloque para /wc brush apply|paint … item (spec 040 H2 CA6): item-uses.yml del jar más el
 * del dueño (que agrega ítems o reemplaza uno entero). Las reglas son máscaras y patrones de Worldcraft, revisados al
 * cargar. Java puro (el YAML de Bukkit no necesita el servidor).
 */
public final class ItemUses {
    public static final String FILE = "item-uses.yml";

    /** «máscara -> patrón» sobre el bloque, o con above en el aire de encima. */
    public record Rule(boolean above, Mask mask, Pattern pattern, String text) {
    }

    private final Map<String, List<Rule>> uses;

    private ItemUses(Map<String, List<Rule>> uses) {
        this.uses = uses;
    }

    public static ItemUses load(String builtin, String custom, Parser parser, Issues issues) {
        Map<String, List<Rule>> out = new LinkedHashMap<>();
        read(builtin, parser, issues, out);
        if (custom != null) read(custom, parser, issues, out);
        return new ItemUses(out);
    }

    private static void read(String yaml, Parser parser, Issues issues, Map<String, List<Rule>> out) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            issues.error(FILE, "", "could not be read: " + e.getMessage());
            return;
        }
        ConfigurationSection c = y.getConfigurationSection("uses");
        if (c == null) {
            if (!y.getKeys(false).isEmpty()) issues.warn(FILE, "", "nothing under 'uses:'");
            return;
        }
        for (String item : c.getKeys(false)) {
            String path = "uses." + item;
            if (!c.isList(item)) {
                issues.error(FILE, path, "needs a list of rules like \"grass_block -> farmland\"");
                continue;
            }
            List<Rule> rules = new ArrayList<>();
            for (Object o : c.getList(item)) {
                String text = String.valueOf(o).trim();
                try {
                    rules.add(rule(text, parser));
                } catch (IllegalArgumentException | ParseException e) {
                    issues.error(FILE, path, "'" + text + "': " + e.getMessage());
                }
            }
            out.put(key(item), rules);
        }
    }

    static Rule rule(String text, Parser parser) throws ParseException {
        int arrow = text.indexOf("->");
        if (arrow < 0) throw new IllegalArgumentException("write it as \"<mask> -> <pattern>\" (or \"above <mask> -> <pattern>\")");
        String left = text.substring(0, arrow).trim(), right = text.substring(arrow + 2).trim();
        boolean above = left.toLowerCase(Locale.ROOT).startsWith("above ");
        if (above) left = left.substring(6).trim();
        if (left.isEmpty() || right.isEmpty()) throw new IllegalArgumentException("a mask before -> and a pattern after it");
        return new Rule(above, parser.mask(left), parser.pattern(right), text);
    }

    private static String key(String item) {
        String k = item.toLowerCase(Locale.ROOT).trim();
        return k.startsWith("minecraft:") ? k.substring(10) : k;
    }

    /** Las reglas de un ítem («iron_hoe» o «minecraft:iron_hoe»): el nombre exacto o el primer comodín que lo cubre; null si no está. */
    public List<Rule> rules(String item) {
        String k = key(item);
        List<Rule> exact = uses.get(k);
        if (exact != null) return exact;
        for (var e : uses.entrySet()) if (e.getKey().contains("*") && glob(e.getKey(), k)) return e.getValue();
        return null;
    }

    private static boolean glob(String pattern, String s) {
        return s.matches(java.util.regex.Pattern.quote(pattern).replace("*", "\\E.*\\Q"));
    }

    /** Los ítems de la tabla (con sus comodines), para los errores y el autocompletado. */
    public List<String> items() {
        return List.copyOf(uses.keySet());
    }
}
