package dev.leo.omnievents;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.api.OmniEventsAPI;
import dev.leo.omnievents.command.OmniCommand;
import dev.leo.omnievents.data.BlockDataCleaner;
import dev.leo.omnievents.data.DataStore;
import dev.leo.omnievents.data.ObjectData;
import dev.leo.omnievents.data.PackVersions;
import dev.leo.omnievents.rule.Pack;
import dev.leo.omnievents.data.SavedItems;
import dev.leo.omnievents.debug.Debugger;
import dev.leo.omnievents.debug.Profiler;
import dev.leo.omnievents.expr.BukkitProperties;
import dev.leo.omnievents.expr.Properties;
import dev.leo.omnievents.hook.Economy;
import dev.leo.omnievents.hook.PapiHook;
import dev.leo.omnievents.hook.Placeholders;
import dev.leo.omnievents.hook.Regions;
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
            "rules/ejemplos/herramientas.yml", "rules/ejemplos/estados.yml", "rules/ejemplos/variables.yml", "rules/ejemplos/integraciones.yml", "rules/ejemplos/menus.yml",
            "rules/ejemplos/recompensas/pack.yml", "rules/ejemplos/recompensas/reglas.yml");

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
    private dev.leo.omnievents.trigger.RegionService regionService;
    private Economy economy = Economy.NONE;
    private Regions regions = Regions.NONE;
    /** Spec 013: menús abiertos y formularios de Bedrock (Floodgate). */
    private dev.leo.omnievents.menu.MenuService menuService;
    private dev.leo.omnievents.hook.Forms forms = dev.leo.omnievents.hook.Forms.NONE;
    /** Spec 012: Vault, WorldGuard y Citizens, si están instalados. */
    private java.util.Set<String> integrations = java.util.Set.of();
    private BukkitTask autosave;
    private Problems lastProblems = new Problems();
    private PackVersions packVersions;
    private boolean migrationScheduled;
    private volatile boolean disabling;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) saveResource("messages.yml", false);
        if (!new File(getDataFolder(), "rules").exists()) for (String ex : EXAMPLES) saveResource(ex, false);

        BukkitProperties.register(Properties.get());
        data = new DataStore(getDataFolder(), getLogger());
        packVersions = new PackVersions(getDataFolder(), getLogger());
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
        regionService = new dev.leo.omnievents.trigger.RegionService(this);
        menuService = new dev.leo.omnievents.menu.MenuService(this);
        loadSettings();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            PapiHook.install(this);
            getLogger().info("PlaceholderAPI detectado: %placeholders% y %omni_...% activos");
        }
        detectIntegrations();
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
            step("regiones", () -> {
                if (regionService != null) regionService.unregister();
            });
            step("menús", () -> {
                if (menuService != null) menuService.unregister();
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
        RuleLoader loader = new RuleLoader(actions, triggers, getPluginMeta().getVersion()).integrations(integrations);
        Map<String, Rule> loaded = loader.loadFolder(folder, p);
        // Cada servicio por separado: si uno falla, los demás quedan activos (H1 CA3; regresión 001 F-03).
        listeners.unregister();
        timers.stop();
        // Los comandos no se quitan antes: register() concilia y solo registra si cambiaron (spec 005).
        rules.set(loaded, loader.packs());
        guarded(p, "oyentes", () -> listeners.register(rules.all(), p));
        guarded(p, "temporizadores", timers::start);
        guarded(p, "estados", () -> states.register(rules.all()));
        guarded(p, "regiones", () -> regionService.register(rules.all()));
        guarded(p, "menús", () -> menuService.set(loader.menus()));
        guarded(p, "comandos", ruleCommands::register);
        guarded(p, "datos de bloques", () -> blockCleaner.update(rules.all()));
        lastProblems = p;
        // Spec 011: las migraciones de los paquetes corren en el tick siguiente (al arrancar, antes que server_start).
        packVersions.load();
        if (!migrationScheduled && !packVersions.plan(rules.packs()).isEmpty()) {
            migrationScheduled = true;
            Bukkit.getScheduler().runTask(this, this::migratePacks);
        }
        if (settings.testsOnLoad()) {
            try {
                logTests(tester().runAll(rules.all()));
            } catch (RuntimeException e) {
                getLogger().log(java.util.logging.Level.WARNING, "Error al correr las pruebas de las reglas", e);
            }
        }
        return p;
    }

    /**
     * Spec 011, H3 CA2–CA3: por cada paquete que cambió de versión, sus migraciones en orden y la versión nueva en
     * data/packs.yml. Se vuelve a planear aquí: dos reload seguidos no corren nada dos veces.
     */
    private void migratePacks() {
        migrationScheduled = false;
        packVersions.load();
        List<PackVersions.Step> steps = packVersions.plan(rules.packs());
        if (steps.isEmpty()) return;
        for (PackVersions.Step st : steps) {
            String name = st.pack().name();
            if (st.from() == null) {
                getLogger().info("Paquete " + name + ": primera carga, versión " + st.to());
            } else if (dev.leo.omnievents.util.Versions.compare(st.to(), st.from()) < 0) {
                getLogger().warning("Paquete " + name + ": la versión " + st.to() + " es menor que la última cargada (" + st.from() + "); no corre ninguna migración");
                continue;
            } else {
                for (Pack.Migration m : st.migrations()) {
                    Map<String, Object> b = new HashMap<>();
                    b.put("world", Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
                    dev.leo.omnievents.action.ExecContext ctx = new dev.leo.omnievents.action.ExecContext(this, m.rule(), b);
                    ctx.locals().put("from", st.from());
                    ctx.locals().put("to", st.to());
                    try {
                        rules.run(m.rule(), ctx, 0, null);
                    } catch (RuntimeException e) {
                        getLogger().log(java.util.logging.Level.WARNING, "Error en la migración " + m.version() + " del paquete " + name, e);
                    }
                }
                getLogger().info("Paquete " + name + ": " + st.from() + " → " + st.to() + " (" + st.migrations().size() + " migraciones)");
            }
            packVersions.record(st);
        }
        try {
            packVersions.save();
        } catch (java.io.IOException e) {
            getLogger().log(java.util.logging.Level.WARNING, "No se pudo guardar data/packs.yml", e);
        }
    }

    public PackVersions packVersions() {
        return packVersions;
    }

    /** Corredor de las pruebas de las reglas (spec 010) con los topes de config.yml. */
    public dev.leo.omnievents.rule.RuleTester tester() {
        return new dev.leo.omnievents.rule.RuleTester(rules::get, settings.maxLoopIterations(), settings.maxCallDepth());
    }

    /** H4 CA1: una línea si todas pasan; un aviso por caso que falla. Sin pruebas, nada. */
    private void logTests(dev.leo.omnievents.rule.RuleTester.Summary sum) {
        if (sum.results().isEmpty()) return;
        if (sum.failed() == 0) {
            getLogger().info("Pruebas de reglas: " + sum.passed() + "/" + sum.results().size() + " pasan");
            return;
        }
        for (var r : sum.results()) {
            if (r.ok()) continue;
            getLogger().warning("La prueba «" + r.test().name() + "» de la regla '" + r.rule().name() + "' falla: "
                    + String.join("; ", r.failures()) + " (detalle: /omni test " + r.rule().name() + ")");
        }
        getLogger().warning("Pruebas de reglas: " + sum.failed() + " de " + sum.results().size() + " fallan");
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
        new RuleLoader(actions, triggers, getPluginMeta().getVersion()).integrations(integrations).loadFolder(new File(getDataFolder(), "rules"), p);
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

    /**
     * Spec 012 (constitución VI.2): cada integración solo se instancia si su plugin está; la economía de Vault se busca
     * en cada uso, así que una que se registra después también sirve.
     */
    private void detectIntegrations() {
        var pm = Bukkit.getPluginManager();
        java.util.Set<String> found = new java.util.LinkedHashSet<>();
        if (pm.getPlugin("Vault") != null) {
            economy = new dev.leo.omnievents.hook.VaultEconomy();
            found.add("Vault");
        }
        if (pm.getPlugin("WorldGuard") != null) {
            try {
                regions = new dev.leo.omnievents.hook.WorldGuardRegions();
                found.add("WorldGuard");
            } catch (LinkageError e) {
                getLogger().warning("WorldGuard está pero no se pudo usar su API: " + e);
            }
        }
        if (pm.getPlugin("Citizens") != null) found.add("Citizens");
        // Spec 013: formularios de Bedrock con Floodgate (por reflexión: sin dependencia de compilación).
        org.bukkit.plugin.Plugin floodgate = pm.getPlugin("floodgate");
        if (floodgate != null) {
            try {
                forms = new dev.leo.omnievents.hook.FloodgateForms(floodgate);
                found.add("Floodgate");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                getLogger().warning("Floodgate está pero no se pudo usar su API de formularios: " + e);
            }
        }
        integrations = java.util.Collections.unmodifiableSet(found);
        if (!found.isEmpty()) getLogger().info("Integraciones detectadas: " + String.join(", ", found));
    }

    public Economy economy() {
        return economy;
    }

    public Regions regions() {
        return regions;
    }

    public dev.leo.omnievents.menu.MenuService menus() {
        return menuService;
    }

    public dev.leo.omnievents.hook.Forms forms() {
        return forms;
    }

    public dev.leo.omnievents.trigger.RegionService regionService() {
        return regionService;
    }

    /** /omni stats: lo detectado y, con Vault, el plugin de economía de ahora. */
    public String integrationsLine() {
        if (integrations.isEmpty()) return "ninguna (Vault, WorldGuard, Citizens y Floodgate son opcionales)";
        List<String> out = new java.util.ArrayList<>();
        for (String i : integrations) {
            if (i.equals("Vault")) out.add("Vault (" + (economy.available() ? economy.provider() : "sin economía") + ")");
            else out.add(i);
        }
        return String.join(", ", out);
    }

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
