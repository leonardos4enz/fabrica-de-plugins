package dev.leo.skinstatues.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/** Los archivos que trae el plugin, cargados como en el servidor: las poses incorporadas del jar y la carpeta encima. */
public final class TestResources {
    public static final File RES = new File("src/main/resources");

    private TestResources() {
    }

    public static YamlConfiguration builtinPoses() {
        return YamlConfiguration.loadConfiguration(new File(RES, LoadedConfig.BUILTIN_POSES));
    }

    public static LoadedConfig load() {
        return LoadedConfig.load(RES, builtinPoses(), id -> null, null);
    }
}
