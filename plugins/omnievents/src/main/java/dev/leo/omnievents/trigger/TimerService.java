package dev.leo.omnievents.trigger;

import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.rule.Rule;
import dev.leo.omnievents.util.Durations;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Disparadores timer (cada intervalo) y clock (a horas del día) (H6 CA2). */
public final class TimerService {
    private final OmniEvents plugin;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<String, String> lastClock = new HashMap<>();

    public TimerService(OmniEvents plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        List<Rule> clocks = new ArrayList<>();
        for (Rule r : plugin.rules().all()) {
            if (!r.enabled()) continue;
            switch (r.trigger().kind()) {
                case TIMER -> {
                    Object every = r.section().get("every", r.section().get("repetitive_time") == null ? null : r.section().get("repetitive_time") + "t");
                    long ticks = Math.max(1, Durations.toTicks(Durations.parseMillis(every)));
                    boolean perPlayer = r.section().getBoolean("per_player", !r.section().getString("trigger", "").equalsIgnoreCase("repetitive_server"));
                    tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(r, perPlayer), ticks, ticks));
                }
                case CLOCK -> clocks.add(r);
                default -> { }
            }
        }
        if (!clocks.isEmpty()) tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> clock(clocks), 20, 20));
    }

    private void tick(Rule r, boolean perPlayer) {
        if (!perPlayer) {
            Map<String, Object> b = new HashMap<>();
            b.put("world", Bukkit.getWorlds().get(0));
            plugin.rules().fire(r, b, null);
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Map<String, Object> b = new HashMap<>();
            b.put("player", p);
            b.put("world", p.getWorld());
            b.put("location", p.getLocation());
            plugin.rules().fire(r, b, null);
        }
    }

    private void clock(List<Rule> clocks) {
        for (Rule r : clocks) {
            String tz = r.section().getString("timezone");
            ZonedDateTime now = ZonedDateTime.now(tz == null ? ZoneId.systemDefault() : ZoneId.of(tz));
            String hhmm = String.format("%02d:%02d", now.getHour(), now.getMinute());
            List<String> at = r.section().isList("at") ? r.section().getStringList("at") : List.of(r.section().getString("at", ""));
            boolean match = false;
            for (String a : at) {
                String[] p = a.split(":");
                if (p.length == 2 && Integer.parseInt(p[0]) == now.getHour() && Integer.parseInt(p[1]) == now.getMinute()) match = true;
            }
            String stamp = now.toLocalDate() + " " + hhmm;
            if (!match || stamp.equals(lastClock.get(r.name()))) continue;
            lastClock.put(r.name(), stamp);
            Map<String, Object> b = new HashMap<>();
            b.put("world", Bukkit.getWorlds().get(0));
            b.put("time", hhmm);
            plugin.rules().fire(r, b, null);
        }
    }

    public void stop() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
    }

    public int count() {
        return tasks.size();
    }
}
