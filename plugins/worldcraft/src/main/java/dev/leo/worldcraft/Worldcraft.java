package dev.leo.worldcraft;

import dev.leo.worldcraft.command.WorldcraftCommand;
import dev.leo.worldcraft.config.Issues;
import dev.leo.worldcraft.config.LoadedConfig;
import dev.leo.worldcraft.config.Messages;
import dev.leo.worldcraft.history.HistoryStore;
import dev.leo.worldcraft.paper.Edits;
import dev.leo.worldcraft.paper.PaperBlocks;
import dev.leo.worldcraft.paper.Wand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Worldcraft (spec 025): editar el mundo sin trabar el servidor, con vista previa, deshacer en disco, patrones y
 * máscaras legibles y límites por permiso.
 */
public final class Worldcraft extends JavaPlugin {
    /** Archivos que se crean si faltan (H8 CA3). */
    private static final List<String> FILES = List.of("config.yml", "messages.yml", "presets.yml");

    private volatile LoadedConfig config;
    private Messages messages;
    private PaperBlocks blocks;
    private ExecutorService io;
    private Edits edits;
    private Wand wand;
    private WorldcraftCommand command;

    @Override
    public void onEnable() {
        for (String f : FILES) if (!new File(getDataFolder(), f).exists()) saveResource(f, false);
        blocks = new PaperBlocks();
        reload();
        // Un solo hilo de E/S: los diarios y los índices se escriben en orden (y un deshacer lee después de escribir).
        io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Worldcraft-IO");
            t.setDaemon(true);
            return t;
        });
        HistoryStore history = new HistoryStore(getDataFolder().toPath().resolve("history"), io);
        try {
            int interrupted = history.load();
            if (interrupted > 0) {
                getLogger().warning(interrupted + " edit(s) were still running at shutdown: they can be undone like any other");
            }
            var s = config.settings();
            int pruned = history.prune(System.currentTimeMillis(), s.historyMaxPerPlayer(), s.historyKeepDays());
            if (pruned > 0) getLogger().info("Removed " + pruned + " old edit(s) from the undo history");
        } catch (Exception e) {
            // Sin historial no se puede deshacer nada: mejor no arrancar que pisar history/ (II.6).
            getLogger().severe("Could not read the undo history (" + e.getMessage() + "): Worldcraft is disabled to protect your data");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        edits = new Edits(this, blocks, history, io);
        try {
            edits.warmUp(); // F-04: el primer bloque de la primera obra ya no paga el código frío dentro de un tick
        } catch (RuntimeException e) {
            getLogger().warning("Warm-up skipped: " + e);
        }
        wand = new Wand(this);
        getServer().getPluginManager().registerEvents(wand, this);
        wand.startOutline();
        command = new WorldcraftCommand(this);
        PluginCommand cmd = getCommand("worldcraft");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }
    }

    @Override
    public void onDisable() {
        try {
            if (wand != null) wand.stop();
            if (edits != null) edits.shutdown();
        } finally {
            // Lo que quedó en la cola de E/S (diarios, índices) se escribe antes de salir (lección 001 F-04).
            if (io != null) {
                io.shutdown();
                try {
                    if (!io.awaitTermination(20, TimeUnit.SECONDS)) getLogger().severe("The undo history did not finish writing");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** Lee de nuevo los archivos; lo que no se pueda leer conserva lo anterior (H8 CA2). */
    public LoadedConfig reload() {
        messages = new Messages(new File(getDataFolder(), "messages.yml"), getResource("messages.yml"));
        LoadedConfig cfg = LoadedConfig.load(getDataFolder(), blocks, config);
        config = cfg;
        for (Issues.Issue i : cfg.issues().all()) {
            if (i.level() == Issues.Level.ERROR) getLogger().warning("Config error: " + i);
            else getLogger().info("Config note: " + i);
        }
        if (wand != null) wand.startOutline();
        return cfg;
    }

    public LoadedConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public Edits edits() {
        return edits;
    }

    public Wand wand() {
        return wand;
    }

    public WorldcraftCommand command() {
        return command;
    }

    public ExecutorService io() {
        return io;
    }
}
