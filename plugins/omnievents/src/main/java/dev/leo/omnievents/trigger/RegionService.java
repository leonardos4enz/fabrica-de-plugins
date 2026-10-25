package dev.leo.omnievents.trigger;

import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.hook.Regions;
import dev.leo.omnievents.rule.Rule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * region_enter y region_leave (spec 012, H3; plan §1.4). Solo escucha si hay reglas de región activas y WorldGuard
 * está instalado (III.1). Guarda por jugador las regiones donde está y dispara al cambiar; si una regla cancela el
 * movimiento, el jugador se queda donde estaba y nada cambia.
 */
public final class RegionService implements Listener {
    private final OmniEvents plugin;
    private final Map<UUID, Map<String, Regions.Region>> inside = new HashMap<>();
    private List<Rule> enter = List.of(), leave = List.of();
    private boolean registered;

    public RegionService(OmniEvents plugin) {
        this.plugin = plugin;
    }

    public void register(Collection<Rule> rules) {
        unregister();
        List<Rule> en = new ArrayList<>(), le = new ArrayList<>();
        for (Rule r : rules) {
            if (!r.enabled() || r.trigger().kind() != TriggerType.Kind.REGION) continue;
            (r.trigger().name().equals("region_enter") ? en : le).add(r);
        }
        enter = List.copyOf(en);
        leave = List.copyOf(le);
        if (enter.isEmpty() && leave.isEmpty() || !plugin.regions().available()) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registered = true;
        // Al recargar no se dispara nada: solo se toma dónde está cada uno.
        for (Player p : Bukkit.getOnlinePlayers()) inside.put(p.getUniqueId(), index(plugin.regions().at(p.getLocation())));
    }

    public void unregister() {
        if (registered) HandlerList.unregisterAll(this);
        registered = false;
        inside.clear();
    }

    /** Oyentes de regiones activos (para /omni stats y la compuerta de recarga). */
    public int count() {
        return registered ? 5 : 0;
    }

    private static Map<String, Regions.Region> index(List<Regions.Region> list) {
        Map<String, Regions.Region> m = new LinkedHashMap<>();
        for (Regions.Region r : list) m.put(r.id(), r);
        return m;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location f = e.getFrom(), t = e.getTo();
        if (f.getBlockX() == t.getBlockX() && f.getBlockY() == t.getBlockY() && f.getBlockZ() == t.getBlockZ() && f.getWorld() == t.getWorld()) return;
        update(e.getPlayer(), t, e);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        update(e.getPlayer(), e.getTo(), e);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        update(e.getPlayer(), e.getPlayer().getLocation(), e);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        update(e.getPlayer(), e.getPlayer().getLocation(), e);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        inside.remove(e.getPlayer().getUniqueId());
    }

    private void update(Player p, Location to, Event e) {
        Map<String, Regions.Region> before = inside.getOrDefault(p.getUniqueId(), Map.of());
        Map<String, Regions.Region> now;
        try {
            now = index(plugin.regions().at(to));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("No se pudieron leer las regiones de WorldGuard: " + ex);
            return;
        }
        for (Regions.Region r : before.values()) if (!now.containsKey(r.id())) fire(leave, p, to, r, e);
        for (Regions.Region r : now.values()) if (!before.containsKey(r.id())) fire(enter, p, to, r, e);
        if (e instanceof Cancellable c && c.isCancelled()) return;
        inside.put(p.getUniqueId(), now);
    }

    private void fire(List<Rule> rules, Player p, Location to, Regions.Region r, Event e) {
        for (Rule rule : rules) {
            Map<String, Object> b = new HashMap<>();
            b.put("player", p);
            b.put("world", to.getWorld());
            b.put("location", to);
            b.put("region", r.asMap());
            b.put("event", e);
            b.put("item", p.getInventory().getItemInMainHand());
            try {
                plugin.rules().fire(rule, b, e);
            } catch (RuntimeException ex) {
                rule.stats().error();
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Error inesperado en la regla '" + rule.name() + "'", ex);
            }
        }
    }
}
