package dev.leo.omnievents.debug;

import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.text.TextFormat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Suscripciones a /omni debug, envío de trazas (H9 CA1) y las últimas trazas para /omni debug export (spec 006, H5). */
public final class Debugger {
    private static final String ALL = "*";
    private static final UUID CONSOLE = new UUID(0, 0);

    private final OmniEvents plugin;
    private final Map<UUID, String> watchers = new ConcurrentHashMap<>();
    private final Deque<Trace> recent = new ArrayDeque<>();

    public Debugger(OmniEvents plugin) {
        this.plugin = plugin;
    }

    private static UUID id(CommandSender s) {
        return s instanceof Player p ? p.getUniqueId() : CONSOLE;
    }

    /** rule = nombre, "all" o null para apagar. */
    public void watch(CommandSender s, String rule) {
        if (rule == null) watchers.remove(id(s));
        else watchers.put(id(s), rule.equalsIgnoreCase("all") ? ALL : rule.toLowerCase());
    }

    public String watching(CommandSender s) {
        return watchers.get(id(s));
    }

    public boolean isWatched(String rule) {
        if (watchers.isEmpty()) return false;
        String r = rule.toLowerCase();
        for (String w : watchers.values()) if (w.equals(ALL) || w.equals(r)) return true;
        return false;
    }

    public Trace start(String rule) {
        return isWatched(rule) ? new Trace(rule) : null;
    }

    /** Guarda la traza para exportarla; se queda con las últimas debug.export-buffer. */
    public void remember(Trace t) {
        int max = plugin.settings().exportBuffer();
        synchronized (recent) {
            recent.addLast(t);
            while (recent.size() > max) recent.removeFirst();
        }
    }

    public List<Trace> recent() {
        synchronized (recent) {
            return new ArrayList<>(recent);
        }
    }

    public void flush(Trace t) {
        // Una simulación no se envía a los que depuran: la recibe quien la pidió (spec 006, H4 CA2).
        if (t == null || t.simulation() || !t.markFlushed()) return;
        remember(t);
        Runnable send = () -> {
            String r = t.rule().toLowerCase();
            for (Map.Entry<UUID, String> e : watchers.entrySet()) {
                if (!e.getValue().equals(ALL) && !e.getValue().equals(r)) continue;
                CommandSender to = e.getKey().equals(CONSOLE) ? Bukkit.getConsoleSender() : Bukkit.getPlayer(e.getKey());
                if (to == null) continue;
                to.sendMessage(TextFormat.parse("<dark_gray>[<gold>debug</gold>]</dark_gray> <yellow>" + escape(t.rule())));
                for (String line : t.lines()) {
                    String color = line.contains("✘") ? "<red>" : line.contains("✔") ? "<green>" : "<gray>";
                    to.sendMessage(TextFormat.parse(color + escape(line)));
                }
                if (to instanceof ConsoleCommandSender) continue;
            }
        };
        if (Bukkit.isPrimaryThread()) send.run();
        else Bukkit.getScheduler().runTask(plugin, send);
    }

    private static String escape(String s) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(s);
    }
}
