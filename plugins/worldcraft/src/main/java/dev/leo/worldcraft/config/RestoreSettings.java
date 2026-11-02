package dev.leo.worldcraft.config;

import org.bukkit.configuration.ConfigurationSection;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/**
 * Las secciones regen y snapshots de config.yml (plan 036 §2). Un valor malo avisa y usa el de fábrica (constitución
 * I.3); una sección que falta, todo de fábrica (un config.yml de la 0.11.0 carga igual).
 *
 * @param snapshotsFolder la carpeta de las copias (relativa a la del servidor), o vacío = apagado
 */
public record RestoreSettings(int regenMaxChunks, int regenKeepSeconds, String snapshotsFolder, int snapshotsMaxChunks) {
    public static final RestoreSettings DEFAULTS = new RestoreSettings(256, 60, "", 1024);

    static final List<String> REGEN_KEYS = List.of("max_chunks", "keep_seconds");
    static final List<String> SNAPSHOT_KEYS = List.of("folder", "max_chunks");
    private static final String FILE = "config.yml";

    /** La carpeta de las copias, o null si está apagado. */
    public Path folder(Path serverFolder) {
        return snapshotsFolder.isEmpty() ? null : serverFolder.resolve(snapshotsFolder).normalize();
    }

    public static RestoreSettings load(ConfigurationSection config, Path serverFolder, Issues issues) {
        if (config == null) return DEFAULTS;
        for (String sec : List.of("regen", "snapshots")) {
            ConfigurationSection c = config.getConfigurationSection(sec);
            if (c == null) continue;
            List<String> keys = sec.equals("regen") ? REGEN_KEYS : SNAPSHOT_KEYS;
            for (String k : c.getKeys(false)) {
                if (!keys.contains(k)) issues.warn(FILE, sec + "." + k, "unknown setting" + Issues.hint(k, keys));
            }
        }
        RestoreSettings d = DEFAULTS;
        Settings.Reader r = new Settings.Reader(config, FILE, issues);
        String folder = String.valueOf(config.get("snapshots.folder", "")).trim();
        if (!folder.isEmpty()) {
            try {
                Path p = serverFolder.resolve(folder).normalize();
                if (!Files.isDirectory(p)) issues.warn(FILE, "snapshots.folder", "the folder '" + p + "' does not exist (yet): no backups to list");
            } catch (InvalidPathException e) {
                issues.error(FILE, "snapshots.folder", "'" + folder + "' is not a folder: backups are off");
                folder = "";
            }
        }
        return new RestoreSettings(r.integer("regen.max_chunks", d.regenMaxChunks, 1, 4096),
                r.integer("regen.keep_seconds", d.regenKeepSeconds, 0, 600), folder,
                r.integer("snapshots.max_chunks", d.snapshotsMaxChunks, 1, 16_384));
    }
}
