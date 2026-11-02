package dev.leo.worldcraft.config;

import dev.leo.worldcraft.block.BlockRegistry;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Parser;
import dev.leo.worldcraft.terrain.TerrainSettings;
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
 * config.yml, presets.yml y terrain.yml (spec 026) leídos y validados juntos (H8 CA2). Un archivo que no se puede leer conserva lo que ya
 * funcionaba (si había algo) y dice archivo, ruta y causa.
 */
public record LoadedConfig(Settings settings, Limits limits, Parser parser, Map<String, String> patternPresets,
                           Map<String, String> maskPresets, TerrainSettings terrain, Issues issues, ToolSettings tools,
                           NatureSettings nature, FormulaSettings formulas, CreateSettings create,
                           RestoreSettings restore, ApiSettings api) {

    public static LoadedConfig load(File folder, BlockRegistry registry, LoadedConfig previous) {
        Issues issues = new Issues();
        YamlConfiguration config = read(new File(folder, "config.yml"), issues);
        YamlConfiguration presets = read(new File(folder, "presets.yml"), issues);
        YamlConfiguration terrainFile = read(new File(folder, "terrain.yml"), issues);
        if (config == null && previous != null) {
            issues.error("config.yml", "", "kept the previous configuration");
        }
        Settings settings = config == null ? (previous != null ? previous.settings : Settings.DEFAULTS)
                : Settings.load(config, "config.yml", issues);

        // Spec 035: la tabla de colores va antes que los presets (un preset puede ser «#darken»).
        dev.leo.worldcraft.color.BlockColors colors = config == null && previous != null ? previous.parser.colors()
                : CreateSettings.colors(config, registry, issues);
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
            Parser plain = new Parser(registry, Map.of(), Map.of()).colors(colors);
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
                    plain.maskNear(e.getValue()); // spec 029: un preset puede mirar alrededor (solo sirve donde se admite)
                    return false;
                } catch (ParseException ex) {
                    issues.error("presets.yml", "masks." + e.getKey(), ex.getMessage() + ": skipped");
                    return true;
                }
            });
        }
        // Spec 033 H3 CA2: un preset de máscara que se llama como un bioma tapa «$bioma».
        for (String k : msk.keySet()) {
            if (registry.biomes().contains(k.contains(":") ? k : "minecraft:" + k)) {
                issues.warn("presets.yml", "masks." + k, "has the name of a biome: $" + k + " is this preset, not the biome");
            }
        }
        // Spec 034: los topes de las fórmulas, antes del analizador (las máscaras y patrones «=…» los usan).
        FormulaSettings formulas = config == null && previous != null ? previous.formulas : FormulaSettings.load(config, issues);
        Parser parser = new Parser(registry, Map.copyOf(pat), Map.copyOf(msk)).formulaLimits(formulas.limits()).colors(colors);
        Limits limits = config == null && previous != null ? previous.limits
                : Limits.load(config == null ? null : config.getConfigurationSection("limits"), parser, "config.yml", issues, registry.entityTypes());
        // Spec 026: un terrain.yml ilegible conserva el anterior; una sección terrain mala, sus valores de fábrica.
        TerrainSettings terrain = terrainFile == null && previous != null ? previous.terrain
                : TerrainConfig.load(config == null ? null : config.getConfigurationSection("terrain"), terrainFile, parser, issues);
        // Spec 032: pinceles de construcción y herramientas (un config.yml ilegible conserva los anteriores).
        ToolSettings tools = config == null && previous != null ? previous.tools : ToolSettings.load(config, parser, issues);
        // Spec 033: árboles, plantas y calabazas.
        NatureSettings nature = config == null && previous != null ? previous.nature : NatureSettings.load(config, parser, issues);
        // Spec 035: minerales, cuevas e imágenes.
        CreateSettings create = config == null && previous != null ? previous.create : CreateSettings.load(config, parser, issues);
        // Spec 036: regen y copias de seguridad (la carpeta, relativa a la del servidor).
        RestoreSettings restore = config == null && previous != null ? previous.restore
                : RestoreSettings.load(config, java.nio.file.Path.of("").toAbsolutePath(), issues);
        // Spec 037: la API para otros plugins (los grupos de un plugin se revisan contra los de limits).
        ApiSettings api = config == null && previous != null ? previous.api : ApiSettings.load(config, limits, issues);
        return new LoadedConfig(settings, limits, parser, Map.copyOf(pat), Map.copyOf(msk), terrain, issues, tools, nature, formulas, create,
                restore, api);
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
