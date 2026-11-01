package dev.leo.skinstatues.config;

import dev.leo.skinstatues.color.BlockPalette;
import dev.leo.skinstatues.color.BlockPalette.Entry;
import dev.leo.skinstatues.color.Lab;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * block-colors.yml (el color de cada bloque) y palettes.yml (listas de bloques permitidos con comodines y
 * exclusiones, H6). La comprobación de que el bloque existe en el servidor se inyecta para probar sin Bukkit.
 */
public final class BlockCatalog {
    /** Devuelve null si el id es un bloque sólido de este servidor, o el motivo por el que no sirve. */
    public interface BlockCheck extends Function<String, String> {
    }

    private static final Pattern HEX = Pattern.compile("#?[0-9a-fA-F]{6}");

    private final Map<String, Integer> colors;

    private BlockCatalog(Map<String, Integer> colors) {
        this.colors = colors;
    }

    public Map<String, Integer> colors() {
        return colors;
    }

    public static String normalize(String id) {
        String s = id.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s;
    }

    public static BlockCatalog load(ConfigurationSection root, String file, BlockCheck check, Issues issues) {
        Map<String, Integer> out = new LinkedHashMap<>();
        int missing = 0;
        String firstMissing = null;
        for (String key : root.getKeys(false)) {
            String id = normalize(key);
            String hex = String.valueOf(root.get(key));
            if (!HEX.matcher(hex).matches()) {
                issues.error(file, key, "'" + hex + "' is not a color like \"#A1B2C3\"");
                continue;
            }
            String why = check.apply(id);
            if (why != null) {
                // Bloques de otra versión de Minecraft: un solo aviso con la cuenta, no uno por bloque.
                if (why.startsWith("unknown")) {
                    missing++;
                    if (firstMissing == null) firstMissing = id;
                } else {
                    issues.warn(file, key, why + ": skipped");
                }
                continue;
            }
            out.put(id, Integer.parseInt(hex.replace("#", ""), 16));
        }
        if (missing > 0) {
            issues.warn(file, "", missing + " block(s) do not exist in this server version (e.g. '" + firstMissing + "'): skipped");
        }
        return new BlockCatalog(out);
    }

    /** palettes.yml: cada paleta es una lista de ids o comodines (`*_wool`); `-id` excluye lo que ya se agregó. */
    public Map<String, BlockPalette> palettes(ConfigurationSection root, String file, Issues issues) {
        Map<String, BlockPalette> out = new LinkedHashMap<>();
        for (String name : root.getKeys(false)) {
            String pid = name.toLowerCase(Locale.ROOT);
            if (!pid.matches("[a-z0-9_-]{1,32}")) {
                issues.error(file, name, "palette names use letters, digits, '_' or '-' (up to 32)");
                continue;
            }
            List<?> list = root.getList(name);
            if (list == null) {
                issues.error(file, name, "a palette is a list of block ids, e.g. [\"*_wool\", \"-pink_wool\"]");
                continue;
            }
            Set<String> chosen = new LinkedHashSet<>();
            for (int i = 0; i < list.size(); i++) {
                String raw = String.valueOf(list.get(i)).trim();
                String path = name + "[" + i + "]";
                boolean exclude = raw.startsWith("-");
                String pat = normalize(exclude ? raw.substring(1) : raw);
                List<String> hits = match(pat);
                if (hits.isEmpty()) {
                    issues.error(file, path, pat.contains("*")
                            ? "'" + pat + "' matches no block in block-colors.yml"
                            : "'" + pat + "' is not in block-colors.yml (or not a solid block here)" + Issues.hint(pat, colors.keySet()));
                    continue;
                }
                if (exclude) hits.forEach(chosen::remove);
                else chosen.addAll(hits);
            }
            if (chosen.isEmpty()) {
                issues.error(file, name, "the palette has no blocks left: not loaded");
                continue;
            }
            List<Entry> entries = new ArrayList<>();
            for (String id : chosen) entries.add(new Entry(id, colors.get(id), Lab.of(colors.get(id))));
            out.put(pid, new BlockPalette(pid, entries));
        }
        return out;
    }

    private List<String> match(String pattern) {
        if (!pattern.contains("*")) return colors.containsKey(pattern) ? List.of(pattern) : List.of();
        Pattern re = Pattern.compile(Pattern.quote(pattern).replace("*", "\\E.*\\Q"));
        List<String> hits = new ArrayList<>();
        for (String id : colors.keySet()) if (re.matcher(id).matches()) hits.add(id);
        return hits;
    }
}
