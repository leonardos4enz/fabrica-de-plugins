package dev.leo.worldcraft;

import dev.leo.worldcraft.command.WorldcraftCommand;
import dev.leo.worldcraft.config.Issues;
import dev.leo.worldcraft.config.LoadedConfig;
import dev.leo.worldcraft.config.Messages;
import dev.leo.worldcraft.history.HistoryStore;
import dev.leo.worldcraft.paper.Edits;
import dev.leo.worldcraft.paper.PaperBlocks;
import dev.leo.worldcraft.paper.BrushTool;
import dev.leo.worldcraft.paper.Schematics;
import dev.leo.worldcraft.paper.Terrain;
import dev.leo.worldcraft.paper.Tools;
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
 * máscaras legibles y límites por permiso. Spec 026: montañas, islas flotantes y el pincel de terreno.
 */
public final class Worldcraft extends JavaPlugin {
    /** Archivos que se crean si faltan (H8 CA3). */
    private static final List<String> FILES = List.of("config.yml", "messages.yml", "presets.yml", "terrain.yml");

    private volatile LoadedConfig config;
    private Messages messages;
    private PaperBlocks blocks;
    private ExecutorService io;
    private Edits edits;
    private Wand wand;
    private WorldcraftCommand command;
    private ExecutorService terrainThread;
    private Terrain terrain;
    private BrushTool brush;
    private Tools tools;
    /** Spec 033: las plantillas de estructuras del juego. */
    private dev.leo.worldcraft.paper.Structures structures;
    private ExecutorService schematicThread;
    private Schematics schematics;
    /** Spec 034: las fórmulas se calculan en un hilo propio (una lenta no frena el terreno). */
    private ExecutorService formulaThread;
    private dev.leo.worldcraft.paper.Formulas formulas;
    /** Spec 035: las imágenes se leen y se bajan en un hilo propio (una descarga lenta no frena las fórmulas). */
    private ExecutorService imageThread;
    private dev.leo.worldcraft.paper.Images images;
    /** Spec 036: las copias de seguridad se leen en un hilo propio; regen usa un mundo temporal. */
    private ExecutorService snapshotThread;
    private dev.leo.worldcraft.paper.Regen regen;
    /** Spec 039: //set, /brush… como comandos de Worldcraft (desde el primer tick, cuando ya cargó WorldEdit). */
    private dev.leo.worldcraft.paper.SlashCommands slash;
    private boolean slashReady;

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
        // Spec 026: el ruido y las alturas se calculan en un hilo propio (Java puro, sin Bukkit).
        terrainThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Worldcraft-Terrain");
            t.setDaemon(true);
            return t;
        });
        terrain = new Terrain(this, terrainThread);
        brush = new BrushTool(this);
        getServer().getPluginManager().registerEvents(brush, this);
        brush.start();
        // Spec 032: pinceles de construcción, herramientas de un clic y superpico.
        tools = new Tools(this);
        getServer().getPluginManager().registerEvents(tools, this);
        tools.start();
        // Spec 033: la lista de plantillas del juego se lee en el hilo de E/S.
        structures = new dev.leo.worldcraft.paper.Structures(this);
        structures.scan();
        // Spec 027: el disco, la compresión y el NBT de los esquemas, en un hilo propio (no atrasa los diarios).
        schematicThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Worldcraft-Schematics");
            t.setDaemon(true);
            return t;
        });
        schematics = new Schematics(this, schematicThread);
        schematics.start();
        formulaThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Worldcraft-Formula");
            t.setDaemon(true);
            return t;
        });
        formulas = new dev.leo.worldcraft.paper.Formulas(this, formulaThread);
        imageThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Worldcraft-Images");
            t.setDaemon(true);
            return t;
        });
        images = new dev.leo.worldcraft.paper.Images(this, imageThread);
        snapshotThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Worldcraft-Snapshots");
            t.setDaemon(true);
            return t;
        });
        regen = new dev.leo.worldcraft.paper.Regen(this);
        regen.cleanLeftovers();
        // Spec 037: la API para otros plugins (Bukkit la quita sola al deshabilitar).
        getServer().getServicesManager().register(dev.leo.worldcraft.api.WorldcraftApi.class, new dev.leo.worldcraft.paper.Api(this), this,
                org.bukkit.plugin.ServicePriority.Normal);
        command = new WorldcraftCommand(this);
        PluginCommand cmd = getCommand("worldcraft");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }
        slash = new dev.leo.worldcraft.paper.SlashCommands(this);
        getServer().getPluginManager().registerEvents(slash, this);
        getServer().getScheduler().runTask(this, () -> {
            slashReady = true;
            slash.apply(config.slash());
        });
    }

    @Override
    public void onDisable() {
        try {
            // Spec 039: los de WorldEdit vuelven a WorldEdit.
            if (slash != null) slash.unregisterAll();
            if (wand != null) wand.stop();
            if (brush != null) brush.stop();
            if (tools != null) tools.stop();
            if (terrainThread != null) terrainThread.shutdownNow();
            if (formulaThread != null) formulaThread.shutdownNow();
            if (imageThread != null) imageThread.shutdownNow();
            if (snapshotThread != null) snapshotThread.shutdownNow();
            if (regen != null) regen.shutdown();
            if (schematicThread != null) {
                // Un esquema a medio escribir va a un temporal: cortar no deja un .schem roto.
                schematicThread.shutdown();
                try {
                    schematicThread.awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
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
        if (brush != null) brush.start();
        if (tools != null) tools.start();
        if (slash != null && slashReady) slash.apply(cfg.slash());
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

    public Terrain terrain() {
        return terrain;
    }

    public BrushTool brush() {
        return brush;
    }

    public Tools tools() {
        return tools;
    }

    public dev.leo.worldcraft.paper.Structures structures() {
        return structures;
    }

    public Schematics schematics() {
        return schematics;
    }

    public dev.leo.worldcraft.paper.Images images() {
        return images;
    }

    public dev.leo.worldcraft.paper.Regen regen() {
        return regen;
    }

    public ExecutorService snapshotThread() {
        return snapshotThread;
    }

    public dev.leo.worldcraft.paper.Formulas formulas() {
        return formulas;
    }

    public dev.leo.worldcraft.paper.SlashCommands slash() {
        return slash;
    }

    public WorldcraftCommand command() {
        return command;
    }

    public ExecutorService io() {
        return io;
    }

    /** El hilo de cálculo (terreno; spec 028: inundaciones e interiores). */
    public ExecutorService compute() {
        return terrainThread;
    }
}
