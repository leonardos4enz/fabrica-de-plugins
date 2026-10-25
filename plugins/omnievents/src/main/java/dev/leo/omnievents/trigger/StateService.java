package dev.leo.omnievents.trigger;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.action.ExecContext;
import dev.leo.omnievents.debug.Trace;
import dev.leo.omnievents.expr.Scope;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.rule.Condition;
import dev.leo.omnievents.rule.Rule;
import dev.leo.omnievents.rule.StateSpec;
import dev.leo.omnievents.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Disparador state (spec 008, plan §1.3): escucha solo los eventos de los que dependen las reglas state, marca
 * (regla, jugador) y revisa cada par una vez al final del tick, cuando el cambio ya se aplicó. Sin sondeo salvo el
 * check_every que declare la regla.
 */
public final class StateService implements Listener {
    private static final Map<StateDeps.Source, List<Class<? extends Event>>> EVENTS = new EnumMap<>(StateDeps.Source.class);

    static {
        EVENTS.put(StateDeps.Source.HEALTH, List.of(EntityDamageEvent.class, EntityRegainHealthEvent.class, PlayerRespawnEvent.class));
        EVENTS.put(StateDeps.Source.FOOD, List.of(FoodLevelChangeEvent.class, PlayerRespawnEvent.class));
        EVENTS.put(StateDeps.Source.MOVE, List.of(PlayerMoveEvent.class, PlayerTeleportEvent.class, PlayerChangedWorldEvent.class, PlayerRespawnEvent.class));
        EVENTS.put(StateDeps.Source.XP, List.of(PlayerExpChangeEvent.class, PlayerLevelChangeEvent.class));
        EVENTS.put(StateDeps.Source.GAMEMODE, List.of(PlayerGameModeChangeEvent.class));
        EVENTS.put(StateDeps.Source.SNEAK, List.of(PlayerToggleSneakEvent.class));
        EVENTS.put(StateDeps.Source.SPRINT, List.of(PlayerToggleSprintEvent.class));
        EVENTS.put(StateDeps.Source.FLIGHT, List.of(PlayerToggleFlightEvent.class, PlayerGameModeChangeEvent.class));
        EVENTS.put(StateDeps.Source.ARMOR, List.of(PlayerArmorChangeEvent.class));
        EVENTS.put(StateDeps.Source.INVENTORY, List.of(PlayerItemHeldEvent.class, PlayerSwapHandItemsEvent.class, InventoryClickEvent.class,
                InventoryDragEvent.class, InventoryCloseEvent.class, EntityPickupItemEvent.class, PlayerDropItemEvent.class,
                PlayerItemConsumeEvent.class, PlayerItemBreakEvent.class, BlockPlaceEvent.class, PlayerRespawnEvent.class));
        EVENTS.put(StateDeps.Source.ONLINE, List.of(PlayerJoinEvent.class, PlayerQuitEvent.class));
        EVENTS.put(StateDeps.Source.WEATHER, List.of(WeatherChangeEvent.class, ThunderChangeEvent.class));
    }

    /** Fuentes que no son de un jugador: su evento revisa a todos (y al estado del servidor). */
    private static final Set<StateDeps.Source> WORLD_SOURCES = Set.of(StateDeps.Source.ONLINE, StateDeps.Source.WEATHER);

    /** Una regla state viva: su tabla y sus contadores. Se conserva al recargar si no cambió la huella (H3 CA2). */
    private static final class Tracked {
        Rule rule;
        final String fingerprint;
        final StateTable table = new StateTable();
        long checks;
        long changes;
        long lastWarn;

        Tracked(Rule rule) {
            this.rule = rule;
            this.fingerprint = rule.state().fingerprint();
        }

        StateSpec spec() {
            return rule.state();
        }

        String key() {
            return rule.name().toLowerCase(Locale.ROOT);
        }
    }

    /** Qué reglas revisa un tipo de evento. */
    private static final class Plan {
        final Set<String> perPlayer = new LinkedHashSet<>();
        final Set<String> everyone = new LinkedHashSet<>();
        final Map<TriggerType, Set<String>> watch = new LinkedHashMap<>();
        boolean join;
        boolean quit;
        boolean blockMove;
    }

    private final OmniEvents plugin;
    private Map<String, Tracked> tracked = new LinkedHashMap<>();
    private final Map<String, List<Tracked>> byPvar = new HashMap<>();
    private final Map<String, List<Tracked>> byGvar = new HashMap<>();
    private final Map<String, List<Tracked>> dependents = new HashMap<>();
    private final Map<Class<? extends Event>, Plan> plans = new LinkedHashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private Map<String, Set<UUID>> dirty = new LinkedHashMap<>();
    private boolean scheduled;

    public StateService(OmniEvents plugin) {
        this.plugin = plugin;
    }

    /** Eventos de Bukkit que siguen una fuente (para /omni info). */
    public static List<String> eventNames(StateDeps.Source s) {
        List<String> out = new ArrayList<>();
        for (Class<? extends Event> c : EVENTS.getOrDefault(s, List.of())) out.add(c.getSimpleName());
        return out;
    }

    // ------------------------------------------------------------------ registro

    /** Al cargar, recargar o con /omni enable|disable: conserva las tablas de las reglas que no cambiaron. */
    public void register(Collection<Rule> rules) {
        unregister();
        Map<String, Tracked> old = tracked;
        Map<String, Tracked> now = new LinkedHashMap<>();
        List<Tracked> fresh = new ArrayList<>();
        for (Rule r : rules) {
            if (!r.enabled() || r.state() == null) continue;
            String k = r.name().toLowerCase(Locale.ROOT);
            Tracked prev = old.get(k);
            Tracked t;
            if (prev != null && prev.fingerprint.equals(r.state().fingerprint())) {
                t = prev;
                t.rule = r;
            } else {
                t = new Tracked(r);
                fresh.add(t);
            }
            now.put(k, t);
        }
        tracked = now;
        if (now.isEmpty()) return;
        for (Tracked t : now.values()) index(t);
        for (Map.Entry<Class<? extends Event>, Plan> e : plans.entrySet()) {
            Plan p = e.getValue();
            Bukkit.getPluginManager().registerEvent(e.getKey(), this, EventPriority.MONITOR, (l, ev) -> onEvent(e.getKey(), p, ev), plugin, true);
        }
        for (Tracked t : now.values()) {
            long every = t.spec().checkEveryTicks();
            if (every > 0) tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> markAll(t), every, every));
        }
        // H3 CA2 y H6 CA3: una regla nueva o cambiada toma el estado actual, sin acciones.
        long ms = System.currentTimeMillis();
        for (Tracked t : fresh) {
            if (!t.spec().perPlayer()) evaluate(t, StateTable.SERVER, ms, true);
            else for (Player p : Bukkit.getOnlinePlayers()) evaluate(t, p.getUniqueId(), ms, true);
        }
    }

    private void index(Tracked t) {
        StateSpec s = t.spec();
        String k = t.key();
        for (String v : s.deps().pvars()) byPvar.computeIfAbsent(v, x -> new ArrayList<>()).add(t);
        for (String v : s.deps().gvars()) byGvar.computeIfAbsent(v, x -> new ArrayList<>()).add(t);
        // Spec 011: in_state('x') desde un paquete apunta a paquete:x si existe.
        for (String st : s.deps().states()) dependents.computeIfAbsent(plugin.rules().resolveName(st, t.rule).toLowerCase(Locale.ROOT), x -> new ArrayList<>()).add(t);
        for (StateDeps.Source src : s.deps().sources()) {
            boolean world = WORLD_SOURCES.contains(src);
            if (!world && !s.perPlayer()) continue;
            for (Class<? extends Event> c : EVENTS.get(src)) {
                Plan p = plans.computeIfAbsent(c, x -> new Plan());
                (world ? p.everyone : p.perPlayer).add(k);
                if (c == PlayerMoveEvent.class) p.blockMove = true;
            }
        }
        for (TriggerType w : s.watch()) plans.computeIfAbsent(w.eventClass(), x -> new Plan()).watch.computeIfAbsent(w, x -> new LinkedHashSet<>()).add(k);
        if (s.perPlayer()) {
            plans.computeIfAbsent(PlayerJoinEvent.class, x -> new Plan()).join = true;
            plans.computeIfAbsent(PlayerQuitEvent.class, x -> new Plan()).quit = true;
        }
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        plans.clear();
        byPvar.clear();
        byGvar.clear();
        dependents.clear();
    }

    /** Oyentes y tareas activos (para /omni stats y la compuerta de recarga). */
    public int listenerCount() {
        return plans.size();
    }

    public int taskCount() {
        return tasks.size();
    }

    // ------------------------------------------------------------------ marcar

    private void onEvent(Class<? extends Event> type, Plan p, Event ev) {
        if (!type.isInstance(ev)) return;
        if (p.blockMove && ev instanceof PlayerMoveEvent m && !blockChanged(m)) return;
        Player who = playerOf(ev);
        if (p.quit && ev instanceof PlayerQuitEvent q) forget(q.getPlayer().getUniqueId());
        if (who != null && !(ev instanceof PlayerQuitEvent)) {
            if (p.join && ev instanceof PlayerJoinEvent) {
                for (Tracked t : tracked.values()) if (t.spec().perPlayer()) mark(t.key(), who.getUniqueId());
            }
            for (String k : p.perPlayer) mark(k, who.getUniqueId());
        }
        for (String k : p.everyone) {
            Tracked t = tracked.get(k);
            if (t != null) markAll(t);
        }
        p.watch.forEach((trigger, keys) -> {
            if (!trigger.accepts(ev)) return;
            Object pl = null;
            try {
                pl = Values.raw(trigger.bind(ev).get("player"));
            } catch (RuntimeException ignored) {
                // sin jugador legible: solo los estados del servidor
            }
            for (String k : keys) {
                Tracked t = tracked.get(k);
                if (t == null) continue;
                if (!t.spec().perPlayer()) mark(k, StateTable.SERVER);
                else if (pl instanceof Player pp) mark(k, pp.getUniqueId());
            }
        });
    }

    private static boolean blockChanged(PlayerMoveEvent m) {
        return m.getFrom().getBlockX() != m.getTo().getBlockX() || m.getFrom().getBlockY() != m.getTo().getBlockY()
                || m.getFrom().getBlockZ() != m.getTo().getBlockZ() || m.getFrom().getWorld() != m.getTo().getWorld();
    }

    private static Player playerOf(Event ev) {
        if (ev instanceof PlayerEvent pe) return pe.getPlayer();
        if (ev instanceof EntityEvent ee && ee.getEntity() instanceof Player p) return p;
        if (ev instanceof InventoryEvent ie && ie.getView().getPlayer() instanceof Player p) return p;
        if (ev instanceof BlockPlaceEvent bp) return bp.getPlayer();
        return null;
    }

    private void markAll(Tracked t) {
        if (!t.spec().perPlayer()) mark(t.key(), StateTable.SERVER);
        else for (Player p : Bukkit.getOnlinePlayers()) mark(t.key(), p.getUniqueId());
    }

    private void mark(String rule, UUID id) {
        dirty.computeIfAbsent(rule, x -> new LinkedHashSet<>()).add(id);
        if (!scheduled) {
            scheduled = true;
            Bukkit.getScheduler().runTask(plugin, this::flush);
        }
    }

    private void forget(UUID id) {
        for (Tracked t : tracked.values()) t.table.forget(id);
        for (Set<UUID> s : dirty.values()) s.remove(id);
    }

    /** Una acción o /omni var cambió una variable (plan §1.3): revisa los estados que la leen. */
    public void varChanged(Scope scope, UUID player, String name) {
        Map<String, List<Tracked>> index = scope == Scope.PLAYER ? byPvar : scope == Scope.GLOBAL ? byGvar : null;
        if (index == null || index.isEmpty() || !index.containsKey(name)) return;
        if (!Bukkit.isPrimaryThread()) {
            // Una regla de chat asíncrono también asigna variables: la marca se hace en el hilo principal.
            Bukkit.getScheduler().runTask(plugin, () -> varChanged(scope, player, name));
            return;
        }
        for (Tracked t : index.get(name)) {
            if (scope == Scope.GLOBAL) markAll(t);
            else if (player != null && t.spec().perPlayer() && Bukkit.getPlayer(player) != null) mark(t.key(), player);
        }
    }

    // ------------------------------------------------------------------ revisar

    private void flush() {
        scheduled = false;
        Map<String, Set<UUID>> work = dirty;
        dirty = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        work.forEach((k, ids) -> {
            Tracked t = tracked.get(k);
            if (t != null) for (UUID id : ids) evaluate(t, id, now, false);
        });
    }

    private ExecContext context(Rule rule, Player p) {
        Map<String, Object> b = new HashMap<>();
        if (p != null) {
            b.put("player", p);
            b.put("world", p.getWorld());
            b.put("location", p.getLocation());
            b.put("item", p.getInventory().getItemInMainHand());
        } else {
            b.put("world", Bukkit.getWorlds().get(0));
        }
        return new ExecContext(plugin, rule, b);
    }

    /** El valor de when ahora (worlds y todas las condiciones). Un error cuenta como falso. */
    private boolean value(Tracked t, ExecContext ctx, java.util.function.Consumer<String> out) {
        Rule r = t.rule;
        if (!r.worlds().isEmpty()) {
            String w = ctx.world() == null ? "" : ctx.world().getName().toLowerCase(Locale.ROOT);
            if (!r.worlds().contains(w)) {
                if (out != null) out.accept("✘ mundo '" + w + "' fuera de " + r.worlds());
                return false;
            }
        }
        for (Condition c : t.spec().when()) {
            boolean ok = c.test(ctx, out, "", out != null, (single, e) -> {
                r.stats().error();
                long ms = System.currentTimeMillis();
                if (ms - t.lastWarn < 60_000) return;
                t.lastWarn = ms;
                plugin.getLogger().warning("Error en «" + single.source() + "» de la regla state '" + r.name() + "' (cuenta como falso; aviso cada minuto): " + e.getMessage());
            });
            if (!ok) return false;
        }
        return true;
    }

    private void evaluate(Tracked t, UUID id, long now, boolean silent) {
        Player p = null;
        if (!id.equals(StateTable.SERVER)) {
            p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) return;
        }
        Rule r = t.rule;
        ExecContext ctx = context(r, p);
        Trace tr = plugin.debugger().start(r.name());
        boolean sampled = plugin.profiler().sample(r.name(), r.trigger().name(), false);
        long t0 = sampled ? System.nanoTime() : 0;
        boolean v = value(t, ctx, tr == null ? null : tr::add);
        if (sampled) plugin.profiler().recordPart(r.name(), r.trigger().name(), false, "when", System.nanoTime() - t0);
        t.checks++;
        StateTable.Change ch = t.table.update(id, v, now, silent || t.spec().initialSilent());
        if (ch.kind() == StateTable.Kind.NONE) return;
        t.changes++;
        String who = p == null ? "el servidor" : p.getName();
        switch (ch.kind()) {
            case ENTER_SILENT -> {
                if (tr != null) {
                    tr.add("✔ " + who + " entra en el estado, en silencio (" + (silent ? "al cargar la regla" : "initial: silent") + ")");
                    plugin.debugger().flush(tr);
                }
            }
            case ENTER -> {
                if (tr != null) tr.add("✔ " + who + " entra en el estado → on_enter");
                if (plugin.rules().runState(r, ctx, tr)) t.table.paired(id);
            }
            case EXIT -> {
                StateTable.Entry e = ch.entry();
                double secs = Math.floor((now - e.since()) / 100.0) / 10.0;
                if (!e.paired()) {
                    if (tr != null) {
                        tr.add("✘ " + who + " sale del estado tras " + Values.formatNumber(secs) + "s; on_exit no corre: su on_enter no corrió");
                        plugin.debugger().flush(tr);
                    }
                } else {
                    if (tr != null) tr.add("✘ " + who + " sale del estado tras " + Values.formatNumber(secs) + "s → on_exit");
                    ctx.locals().put("state_seconds", secs);
                    plugin.rules().runActions(r, t.spec().onExit(), ctx, tr);
                }
            }
            default -> { }
        }
        // H5 CA3: los estados que leen este con in_state se revisan también.
        List<Tracked> deps = dependents.get(t.key());
        if (deps != null) {
            for (Tracked d : deps) {
                if (id.equals(StateTable.SERVER)) markAll(d);
                else if (d.spec().perPlayer()) mark(d.key(), id);
            }
        }
    }

    // ------------------------------------------------------------------ consultas

    /** in_state('regla') y %omni_state_regla% (H5). */
    public boolean isIn(String rule, UUID player) {
        Tracked t = rule == null ? null : tracked.get(rule.toLowerCase(Locale.ROOT));
        if (t == null) return false;
        if (!t.spec().perPlayer()) return t.table.isIn(StateTable.SERVER);
        return player != null && t.table.isIn(player);
    }

    /** /omni state <regla> (H5 CA4). */
    public List<String> describe(Rule rule) {
        List<String> out = new ArrayList<>();
        Tracked t = tracked.get(rule.name().toLowerCase(Locale.ROOT));
        if (t == null) {
            out.add("• la regla está inactiva: no lleva estado");
            return out;
        }
        long now = System.currentTimeMillis();
        Map<UUID, StateTable.Entry> in = t.table.inside();
        if (!t.spec().perPlayer()) {
            StateTable.Entry e = in.get(StateTable.SERVER);
            out.add(e == null ? "✘ estado del servidor inactivo" : "✔ estado del servidor activo desde hace " + Durations.format(now - e.since()));
        } else {
            out.add((in.isEmpty() ? "✘ " : "✔ ") + in.size() + " jugador(es) en el estado:");
            in.forEach((id, e) -> {
                Player p = Bukkit.getPlayer(id);
                out.add("  • " + (p == null ? id.toString() : p.getName()) + " desde hace " + Durations.format(now - e.since())
                        + (e.paired() ? "" : " (sin on_enter)"));
            });
        }
        out.add("revisiones " + t.checks + " · cambios " + t.changes);
        return out;
    }

    /** /omni check de una regla state (H4 CA2): when con valores, el estado guardado y qué correría. */
    public List<String> check(Rule rule, ExecContext ctx) {
        List<String> out = new ArrayList<>();
        Tracked t = tracked.get(rule.name().toLowerCase(Locale.ROOT));
        Player p = ctx.playerOrNull();
        if (t == null) {
            out.add("• la regla está inactiva: no lleva estado");
            return out;
        }
        if (t.spec().perPlayer() && p == null) {
            out.add("• sin jugador: usa /omni check " + rule.name() + " <jugador>");
            return out;
        }
        UUID id = t.spec().perPlayer() ? p.getUniqueId() : StateTable.SERVER;
        boolean v = value(t, ctx, out::add);
        StateTable.Entry e = t.table.entry(id);
        long now = System.currentTimeMillis();
        out.add(e == null ? "• estado guardado: fuera" : "• estado guardado: dentro desde hace " + Durations.format(now - e.since()) + (e.paired() ? "" : " (sin on_enter)"));
        if (v && e == null) {
            for (String line : plugin.rules().check(rule, ctx)) out.add(line.replace("→ correría: actions", "→ correría: on_enter"));
        } else if (!v && e != null) {
            out.add("→ correría: " + (e.paired() ? "on_exit (" + t.spec().onExit().size() + " acciones)" : "nada (su on_enter no corrió)"));
        } else {
            out.add("→ correría: nada (el estado no cambia)");
        }
        return out;
    }

    /** /omni info: de qué depende la regla (H2 CA4). */
    public static List<String> deps(StateSpec s) {
        List<String> out = new ArrayList<>();
        List<String> d = s.deps().describe();
        out.add("Depende de: " + (d.isEmpty() ? "nada que el motor siga" : String.join(", ", d)));
        Set<String> events = new LinkedHashSet<>();
        for (StateDeps.Source src : s.deps().sources()) events.addAll(eventNames(src));
        for (TriggerType w : s.watch()) events.add(w.name());
        if (!events.isEmpty()) out.add("Se revisa con: " + String.join(", ", events));
        if (s.checkEveryTicks() > 0) out.add("Respaldo: cada " + Durations.format(s.checkEveryTicks() * 50));
        if (!s.deps().unknown().isEmpty()) out.add("No deducible (va con check_every): " + String.join(", ", s.deps().unknown()));
        out.add((s.perPlayer() ? "Por jugador" : "Del servidor") + " · al entrar: " + (s.initialSilent() ? "silent" : "enter") + " · on_exit: " + s.onExit().size() + " acciones");
        return out;
    }

    /** Revisiones y cambios por regla (para /omni stats). */
    public Map<String, long[]> counters() {
        Map<String, long[]> out = new LinkedHashMap<>();
        for (Tracked t : tracked.values()) out.put(t.rule.name(), new long[]{t.checks, t.changes, t.table.size()});
        return out;
    }
}
