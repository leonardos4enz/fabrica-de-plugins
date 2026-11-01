package dev.leo.skinstatues.config;

import dev.leo.skinstatues.color.BlockPalette;
import dev.leo.skinstatues.model.Pose;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;

/**
 * Los cuatro archivos de configuración ya validados y compilados (I.3, III.2). Si un archivo no se puede leer como
 * YAML, se conserva lo que había cargado antes (H9 CA2).
 */
public record LoadedConfig(Settings settings, Map<String, Pose> poses, BlockCatalog catalog,
                           Map<String, BlockPalette> palettes, Issues issues) {

    public static LoadedConfig load(File folder, BlockCatalog.BlockCheck check, LoadedConfig previous) {
        Issues issues = new Issues();
        ConfigurationSection cfg = yaml(new File(folder, "config.yml"), issues);
        Settings settings = cfg != null ? Settings.load(cfg, "config.yml", issues)
                : previous != null ? previous.settings : Settings.DEFAULTS;

        ConfigurationSection posesYml = yaml(new File(folder, "poses.yml"), issues);
        Map<String, Pose> poses = posesYml != null ? PoseLoader.load(posesYml, "poses.yml", issues)
                : previous != null ? previous.poses : Map.of(Pose.STANDING.name(), Pose.STANDING);

        ConfigurationSection colorsYml = yaml(new File(folder, "block-colors.yml"), issues);
        BlockCatalog catalog = colorsYml != null ? BlockCatalog.load(colorsYml, "block-colors.yml", check, issues)
                : previous != null ? previous.catalog : null;

        ConfigurationSection palettesYml = yaml(new File(folder, "palettes.yml"), issues);
        Map<String, BlockPalette> palettes = catalog != null && palettesYml != null
                ? catalog.palettes(palettesYml, "palettes.yml", issues)
                : previous != null ? previous.palettes : Map.of();
        if (palettes.isEmpty()) issues.error("palettes.yml", "", "no palette could be loaded: statues cannot be built");

        if (!poses.containsKey(settings.defaultPose())) {
            issues.error("config.yml", "default_pose", "pose '" + settings.defaultPose() + "' does not exist"
                    + Issues.hint(settings.defaultPose(), poses.keySet()) + ": using 'standing'");
        }
        if (!palettes.isEmpty() && !palettes.containsKey(settings.defaultPalette())) {
            issues.error("config.yml", "default_palette", "palette '" + settings.defaultPalette() + "' does not exist"
                    + Issues.hint(settings.defaultPalette(), palettes.keySet()) + ": using '" + palettes.keySet().iterator().next() + "'");
        }
        return new LoadedConfig(settings, poses, catalog, palettes, issues);
    }

    public String defaultPose() {
        return poses.containsKey(settings.defaultPose()) ? settings.defaultPose() : Pose.STANDING.name();
    }

    public String defaultPalette() {
        return palettes.containsKey(settings.defaultPalette()) || palettes.isEmpty()
                ? settings.defaultPalette() : palettes.keySet().iterator().next();
    }

    private static ConfigurationSection yaml(File f, Issues issues) {
        if (!f.exists()) {
            issues.error(f.getName(), "", "file is missing");
            return null;
        }
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.load(f);
            return y;
        } catch (Exception e) {
            String msg = String.valueOf(e.getMessage()).lines().findFirst().orElse("invalid YAML");
            issues.error(f.getName(), "", "invalid YAML (" + msg + "): keeping the previous version");
            return null;
        }
    }
}
