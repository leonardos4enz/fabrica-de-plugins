package dev.leo.omnievents.trigger;

import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.rule.Problems;
import dev.leo.omnievents.rule.Rule;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registra un oyente por (clase de evento, prioridad) que usen las reglas habilitadas, y nada más (H6 CA5,
 * constitución III.1). Al recargar se desregistra todo de una vez.
 */
public final class ListenerManager implements Listener {
    private record Key(Class<? extends Event> type, EventPriority priority) {
    }

    private final OmniEvents plugin;
    private final Map<Key, List<Rule>> groups = new LinkedHashMap<>();

    public ListenerManager(OmniEvents plugin) {
        this.plugin = plugin;
    }

    public void register(Collection<Rule> rules, Problems problems) {
        unregister();
        for (Rule r : rules) {
            if (!r.enabled() || r.trigger().eventClass() == null) continue;
            groups.computeIfAbsent(new Key(r.trigger().eventClass(), r.priority()), k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<Key, List<Rule>> e : groups.entrySet()) {
            Key k = e.getKey();
            List<Rule> list = List.copyOf(e.getValue());
            try {
                Bukkit.getPluginManager().registerEvent(k.type(), this, k.priority(), (l, ev) -> dispatch(k.type(), list, ev), plugin, false);
            } catch (RuntimeException ex) {
                problems.file("(registro)").error(list.get(0).name(), "no se pudo escuchar " + k.type().getSimpleName() + ": " + ex.getMessage());
            }
        }
    }

    private void dispatch(Class<? extends Event> type, List<Rule> rules, Event ev) {
        if (!type.isInstance(ev)) return;
        Map<TriggerType, Map<String, Object>> cache = new IdentityHashMap<>();
        for (Rule r : rules) {
            TriggerType t = r.trigger();
            if (!t.accepts(ev)) continue;
            Map<String, Object> b;
            try {
                b = cache.computeIfAbsent(t, x -> x.bind(ev));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("No se pudo leer el evento " + ev.getEventName() + " para " + t.name() + ": " + ex);
                continue;
            }
            try {
                plugin.rules().fire(r, b, ev);
            } catch (RuntimeException ex) {
                r.stats().error();
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Error inesperado en la regla '" + r.name() + "'", ex);
            }
        }
    }

    public void unregister() {
        HandlerList.unregisterAll(this);
        groups.clear();
    }

    /** true si alguna regla habilitada escucha esa clase de evento (spec 016: los paquetes de chat solo entonces). */
    public boolean listens(Class<? extends Event> type) {
        for (Key k : groups.keySet()) if (k.type() == type) return true;
        return false;
    }

    /** Oyentes activos (para /omni stats y la compuerta de recarga). */
    public int count() {
        return groups.size();
    }

    public List<String> describe() {
        List<String> out = new ArrayList<>();
        groups.forEach((k, v) -> out.add(k.type().getSimpleName() + " @" + k.priority() + " → " + v.size() + " regla(s)"));
        return out;
    }
}
