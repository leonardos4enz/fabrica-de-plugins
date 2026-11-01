package dev.leo.skinstatues;

import dev.leo.skinstatues.statue.StatueStore;
import dev.leo.skinstatues.statue.Statues;
import dev.leo.skinstatues.command.StatueCommand;
import dev.leo.skinstatues.config.Issues;
import dev.leo.skinstatues.config.LoadedConfig;
import dev.leo.skinstatues.config.Messages;
import dev.leo.skinstatues.skin.MojangClient;
import dev.leo.skinstatues.skin.SkinRepository;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Clock;
import java.util.List;

/** SkinStatues (spec 021): estatuas de bloques con la skin de un jugador, a la altura pedida y en la pose elegida. */
public final class SkinStatues extends JavaPlugin {
    /** Archivos que se crean si faltan (H9 CA3). */
    private static final List<String> FILES = List.of("config.yml", "messages.yml", "poses.yml", "palettes.yml", "block-colors.yml");
    /** La skin de ejemplo (original, no de Mojang): solo si la carpeta skins/ no existe. */
    private static final List<String> EXAMPLE_SKINS = List.of("skins/Example.png", "skins/Example.yml", "skins/Example-cape.png");

    private volatile LoadedConfig config;
    private Messages messages;
    private SkinRepository skins;
    private Statues statues;

    @Override
    public void onEnable() {
        boolean firstSkins = !new File(getDataFolder(), "skins").exists();
        for (String f : FILES) if (!new File(getDataFolder(), f).exists()) saveResource(f, false);
        if (firstSkins) for (String f : EXAMPLE_SKINS) saveResource(f, false);

        reload();
        skins = new SkinRepository(getDataFolder().toPath().resolve("skins"), getDataFolder().toPath().resolve("cache"),
                new MojangClient(MojangClient.realHttp()), Clock.systemUTC());
        StatueStore store = new StatueStore(getDataFolder().toPath().resolve("data"));
        try {
            int interrupted = store.load();
            if (interrupted > 0) {
                getLogger().warning(interrupted + " statue(s) were still building at shutdown: marked as cancelled (use /statue remove to undo them)");
            }
        } catch (Exception e) {
            // Sin índice no se puede deshacer nada: mejor no arrancar que pisar data/ (II.6).
            getLogger().severe("Could not read data/statues.yml (" + e.getMessage() + "): SkinStatues is disabled to protect your data");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        statues = new Statues(this, store);

        PluginCommand cmd = getCommand("statue");
        if (cmd != null) {
            StatueCommand handler = new StatueCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
    }

    @Override
    public void onDisable() {
        if (statues != null) statues.shutdown();
    }

    /** Lee de nuevo los archivos; lo que no se pueda leer conserva lo anterior (H9 CA2). */
    public LoadedConfig reload() {
        messages = new Messages(new File(getDataFolder(), "messages.yml"), getResource("messages.yml"));
        LoadedConfig cfg = LoadedConfig.load(getDataFolder(), SkinStatues::checkBlock, config);
        config = cfg;
        for (Issues.Issue i : cfg.issues().all()) {
            if (i.level() == Issues.Level.ERROR) getLogger().warning("Config error: " + i);
            else getLogger().info("Config note: " + i);
        }
        if (cfg.settings().mojang()) getLogger().info("network.mojang is on: skins not found in skins/ are downloaded from Mojang");
        return cfg;
    }

    /** Un bloque sirve si existe en este servidor, es sólido, opaco y no cae (H6 CA5). */
    static String checkBlock(String id) {
        Material m = Material.matchMaterial(id);
        if (m == null || !m.isBlock()) return "unknown block";
        if (!m.isSolid() || !m.isOccluding()) return "not a solid full block";
        if (m.hasGravity()) return "falls (gravity)";
        return null;
    }

    public LoadedConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public SkinRepository skins() {
        return skins;
    }

    public Statues statues() {
        return statues;
    }
}
