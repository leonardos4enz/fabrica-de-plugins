package dev.leo.omnievents;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.api.OmniEventsAPI;
import dev.leo.omnievents.command.OmniCommand;
import dev.leo.omnievents.data.BlockDataCleaner;
import dev.leo.omnievents.data.DataStore;
import dev.leo.omnievents.data.ObjectData;
import dev.leo.omnievents.data.SavedItems;
import dev.leo.omnievents.debug.Debugger;
import dev.leo.omnievents.debug.Profiler;
import dev.leo.omnievents.expr.BukkitProperties;
import dev.leo.omnievents.expr.Properties;
import dev.leo.omnievents.hook.PapiHook;
import dev.leo.omnievents.hook.Placeholders;
import dev.leo.omnievents.rule.Problems;
import dev.leo.omnievents.rule.Rule;
import dev.leo.omnievents.rule.RuleLoader;
import dev.leo.omnievents.rule.RuleManager;
import dev.leo.omnievents.trigger.ListenerManager;
import dev.leo.omnievents.trigger.RuleCommands;
import dev.leo.omnievents.trigger.StateService;
import dev.leo.omnievents.trigger.TimerService;
import dev.leo.omnievents.trigger.TriggerType;
import dev.leo.omnievents.trigger.Triggers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OmniEvents extends JavaPlugin implements Listener {
    private static final List<String> EXAMPLES = List.of(
            "rules/ejemplos/bienvenida.yml", "rules/ejemplos/mineria.yml", "rules/ejemplos/comandos.yml",
            "rules/ejemplos/combate.yml", "rules/ejemplos/chat.yml", "rules/ejemplos/temporizadores.yml",
            "rules/ejemplos/herramientas.yml", "rules/ejemplos/estados.yml", "rules/ejemplos/variables.yml");

    private Settings settings;
    private Messages messages;
    private DataStore data;
    private ObjectData objectData;
    private BlockDataCleaner blockCleaner;
    private BukkitTask expirySweep;
    private SavedItems savedItems;
    private final Placeholders placeholders = new Placeholders();
    private Debugger debugger;
    private final Profiler profiler = new Profiler();
    private Actions actions;
    private Triggers triggers;
    private RuleManager rules;
    private ListenerManager listeners;
    private TimerService timers;
    private StateService states;
    private RuleCommands ruleCommands;
    private BukkitTask autosave;
    private Problems lastProblems = new Problems();
    private volatile boolean disabling;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) saveResource("messages.yml", false);
        if (!new File(getDataFolder(), "rules").exists()) for (String ex : EXAMPLES) saveResource(ex, false);

        BukkitProperties.register(Properties.get());
        data = new DataStore(getDataFolder(), getLogger());
        objectData = new ObjectData(data::now, () -> settings.objectMaxText(), () -> settings.objectMaxVars());
        blockCleaner = new BlockDataCleaner(this);
        savedItems = new SavedItems(getDataFolder());
        debugger = new Debugger(this);
        actions = new Actions(this);
        triggers = new Triggers();
        rules = new RuleManager(this);
        listeners = new ListenerManager(this);
        timers = new TimerService(this);
        states = new StateService(this);
        ruleCommands = new RuleCommands(this);
        loadSettings();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            PapiHook.install(this);
            getLogger().info("PlaceholderAPI detectado: %placeholders% y %omni_...% activos");
        }
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) data.preload(p.getUniqueId());

        OmniCommand cmd = new OmniCommand(this);
        var c = getCommand("omni");
        if (c != null) {
            c.setExecutor(cmd);
            c.setTabCompleter(cmd);
        }
        OmniEventsAPI.init(this);

        Problems p = reload();
        logProblems(p);
        // Spec 009: caducidad de pvar y gvar, una vez por segundo (sin nada vencido cuesta un peek).
        expirySweep = Bukkit.getScheduler().runTaskTimer(this, this::sweepExpired, 20, 20);
        // server_start corre en el primer tick, con todos los plugins y mundos cargados.
        Bukkit.getScheduler().runTask(this, () -> fireAll(TriggerType.Kind.SERVER_START));
    }

    @Override
    public void onDisable() {
        disabling = true;
        // Cada paso por separado: un fallo en uno no puede impedir que se guarden los datos
        // (Regresión, servidor real 001 F-04: el error al quitar comandos dejaba sin guardar global.yml).
        try {
            step("server_stop", () -> {
                if (rules != null) fireAll(TriggerType.Kind.SERVER_STOP);
            });
            step("oyentes", () -> {
                if (listeners != null) listeners.unregister();
            });
            step("temporizadores", () -> {
                if (timers != null) timers.stop();
            });
            step("estados", () -> {
                if (states != null) states.unregister();
            });
            step("comandos", () -> {
                if (ruleCommands != null) ruleCommands.unregister();
            });
            step("autoguardado", () -> {
                if (autosave != null) autosave.cancel();
            });
            step("caducidad", () -> {
                if (expirySweep != null) expirySweep.cancel();
                if (blockCleaner != null) blockCleaner.unregister();
            });
        } finally {
            if (data != null) data.saveAllSync();
        }
    }

    private void step(String what, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            getLogger().log(java.util.logging.Level.WARNING, "Error al apagar (" + what + ")", e);
        }
    }

    private void loadSettings() {
        reloadConfig();
        settings = Settings.from(getConfig());
        profiler.sampleEvery(settings.sampleEvery());
        messages = new Messages(this);
        Properties.get().setAllowUnsafe(settings.allowUnsafeMethods());
        if (autosave != null) autosave.cancel();
        long period = settings.autosaveMinutes() * 60L * 20L;
        autosave = Bukkit.getScheduler().runTaskTimer(this, this::saveAsync, period, period);
    }

    /** Recarga config, mensajes y reglas (H1 CA3). Las reglas con errores se omiten; los datos no se tocan. */
    public Problems reload() {
        loadSettings();
        File folder = new File(getDataFolder(), "rules");
        folder.mkdirs();
        Problems p = new Problems();
        Map<String, Rule> loaded = new RuleLoader(actions, triggers).loadFolder(folder, p);
        // Cada servicio por separado: si uno falla, los demás quedan activos (H1 CA3; regresión 001 F-03).
        listeners.unregister();
        timers.stop();
        // Los comandos no se quitan antes: register() concilia y solo registra si cambiaron (spec 005).
        rules.set(loaded);
        guarded(p, "oyentes", () -> listeners.register(rules.all(), p));
        guarded(p, "temporizadores", timers::start);
        guarded(p, "estados", () -> states.register(rules.all()));
        guarded(p, "comandos", ruleCommands::register);
        guarded(p, "datos de bloques", () -> blockCleaner.update(rules.all()));
        lastProblems = p;
        return p;
    }

    private void guarded(Problems p, String what, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            p.file("(recarga)").error(what, "falló al registrar " + what + ": " + e);
            getLogger().log(java.util.logging.Level.WARNING, "Error al recargar (" + what + ")", e);
        }
    }

    /** Valida sin aplicar (H9 CA4). */
    public Problems validate() {
        Problems p = new Problems();
        new RuleLoader(actions, triggers).loadFolder(new File(getDataFolder(), "rules"), p);
        return p;
    }

    public void logProblems(Problems p) {
        for (Problems.Problem pr : p.all()) {
            if (pr.error()) getLogger().severe(pr.toString());
            else getLogger().warning(pr.toString());
        }
        getLogger().info(rules.all().size() + " reglas cargadas, " + listeners.count() + " oyentes, " + timers.count()
                + " temporizadores, " + states.listenerCount() + " oyentes de estado, " + ruleCommands.count() + " comandos (" + p.errors() + " errores, " + p.warnings() + " avisos)");
    }

    private void fireAll(TriggerType.Kind kind) {
        for (Rule r : rules.byKind(kind)) {
            Map<String, Object> b = new HashMap<>();
            b.put("world", Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
            rules.fire(r, b, null);
        }
    }

    /** Spec 009, H2: lo que caducó avisa a los estados que lo leen y corre las reglas var_expire. */
    private void sweepExpired() {
        for (DataStore.Expired x : data.sweep()) {
            states.varChanged(x.scope(), x.player(), x.name());
            Player p = x.player() == null ? null : Bukkit.getPlayer(x.player());
            if (x.player() != null && p == null) continue;
            for (Rule r : rules.byKind(TriggerType.Kind.VAR_EXPIRE)) {
                if (!r.expireVariables().isEmpty() && !r.expireVariables().contains(x.name())) continue;
                Map<String, Object> b = new HashMap<>();
                b.put("variable", x.name());
                b.put("value", x.value());
                b.put("scope", x.scope().prefix);
                if (p != null) {
                    b.put("player", p);
                    b.put("world", p.getWorld());
                    b.put("location", p.getLocation());
                } else {
                    b.put("world", Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
                }
                rules.fire(r, b, null);
            }
        }
    }

    public void saveAsync() {
        List<DataStore.Snapshot> snaps = data.dirtySnapshots();
        if (snaps.isEmpty()) return;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> snaps.forEach(data::write));
    }

    // ------------------------------------------------------------------ carga de datos por jugador

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) data.preload(e.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        // Un tick después: las reglas de player_quit aún pueden usar pvar.
        var id = e.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (Bukkit.getPlayer(id) == null) data.unload(id, r -> Bukkit.getScheduler().runTaskAsynchronously(this, r));
        }, 1);
    }

    // ------------------------------------------------------------------ accesores

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public DataStore data() {
        return data;
    }

    public ObjectData objectData() {
        return objectData;
    }

    public BlockDataCleaner blockCleaner() {
        return blockCleaner;
    }

    public SavedItems savedItems() {
        return savedItems;
    }

    public Placeholders placeholders() {
        return placeholders;
    }

    public Debugger debugger() {
        return debugger;
    }

    public Profiler profiler() {
        return profiler;
    }

    public Actions actions() {
        return actions;
    }

    public Triggers triggers() {
        return triggers;
    }

    public RuleManager rules() {
        return rules;
    }

    public ListenerManager listeners() {
        return listeners;
    }

    public TimerService timers() {
        return timers;
    }

    public StateService states() {
        return states;
    }

    public RuleCommands ruleCommands() {
        return ruleCommands;
    }

    public Problems lastProblems() {
        return lastProblems;
    }

    public boolean disabling() {
        return disabling;
    }
}
