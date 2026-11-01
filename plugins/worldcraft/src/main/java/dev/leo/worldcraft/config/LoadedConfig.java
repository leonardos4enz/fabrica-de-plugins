package dev.leo.blockwright.config;

import dev.leo.blockwright.block.BlockRegistry;
import dev.leo.blockwright.pattern.ParseException;
import dev.leo.blockwright.pattern.Parser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * config.yml y presets.yml leídos y validados juntos (H8 CA2). Un archivo que no se puede leer conserva lo que ya
 * funcionaba (si había algo) y dice archivo, ruta y causa.
 */
public record LoadedConfig(Settings settings, Limits limits, Parser parser, Map<String, String> patternPresets,
                           Map<String, String> maskPresets, Issues issues) {

    public static LoadedConfig load(File folder, BlockRegistry registry, LoadedConfig previous) {
        Issues issues = new Issues();
        YamlConfiguration config = read(new File(folder, "config.yml"), issues);
        YamlConfiguration presets = read(new File(folder, "presets.yml"), issues);
        if (config == null && previous != null) {
            issues.error("config.yml", "", "kept the previous configuration");
        }
        Settings settings = config == null ? (previous != null ? previous.settings : Settings.DEFAULTS)
                : Settings.load(config, "config.yml", issues);

        Map<String, String> pat, msk;
        if (presets == null && previous != null) {
            pat = previous.patternPresets;
            msk = previous.maskPresets;
        } else {
            pat = section(presets, "patterns");
            msk = section(presets, "masks");
            if (presets != null) {
                for (String k : presets.getKeys(false)) {
                    if (!k.equals("patterns") && !k.equals("masks")) {
                        issues.warn("presets.yml", k, "unknown section" + Issues.hint(k, java.util.List.of("patterns", "masks")));
                    }
                }
            }
            // Cada preset se comprueba con el analizador (sin presets: uno no puede nombrar a otro).
            Parser plain = new Parser(registry, Map.of(), Map.of());
            pat.entrySet().removeIf(e -> {
                try {
                    plain.pattern(e.getValue());
                    return false;
                } catch (ParseException ex) {
                    issues.error("presets.yml", "patterns." + e.getKey(), ex.getMessage() + ": skipped");
                    return true;
                }
            });
            msk.entrySet().removeIf(e -> {
                try {
                    plain.mask(e.getValue());
                    return false;
                } catch (ParseException ex) {
                    issues.error("presets.yml", "masks." + e.getKey(), ex.getMessage() + ": skipped");
                    return true;
                }
            });
        }
        Parser parser = new Parser(registry, Map.copyOf(pat), Map.copyOf(msk));
        Limits limits = config == null && previous != null ? previous.limits
                : Limits.load(config == null ? null : config.getConfigurationSection("limits"), parser, "config.yml", issues);
        return new LoadedConfig(settings, limits, parser, Map.copyOf(pat), Map.copyOf(msk), issues);
    }

    private static Map<String, String> section(YamlConfiguration y, String name) {
        Map<String, String> out = new LinkedHashMap<>();
        if (y == null) return out;
        ConfigurationSection s = y.getConfigurationSection(name);
        if (s == null) return out;
        for (String k : s.getKeys(false)) out.put(k.toLowerCase(Locale.ROOT), String.valueOf(s.get(k)));
        return out;
    }

    /** null si no existe o no es YAML válido (y lo informa). */
    static YamlConfiguration read(File f, Issues issues) {
        if (!f.exists()) return new YamlConfiguration();
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            return y;
        } catch (IOException | InvalidConfigurationException e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage().lines().findFirst().orElse("");
            issues.error(f.getName(), "", "not valid YAML: " + msg);
            return null;
        }
    }
}
