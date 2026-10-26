package dev.leo.omnievents.hook;

import dev.leo.omnievents.expr.EvalContext;
import dev.leo.omnievents.expr.Values;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

/**
 * %placeholders%: unos pocos incorporados (compatibles con ConditionalEvents) y luego PlaceholderAPI si está.
 * Un placeholder que nadie resuelve se devuelve tal cual.
 */
public final class Placeholders {
    private volatile BiFunction<OfflinePlayer, String, String> papi;

    public void hook(BiFunction<OfflinePlayer, String, String> papi) {
        this.papi = papi;
    }

    public boolean hasPapi() {
        return papi != null;
    }

    public Object resolve(String raw, OfflinePlayer player, EvalContext ctx) {
        String inner = raw.substring(1, raw.length() - 1);
        String k = inner.toLowerCase(Locale.ROOT);
        if (k.startsWith("random_")) {
            String[] p = k.split("_");
            if (p.length == 3 && Values.isNumeric(p[1]) && Values.isNumeric(p[2])) {
                long a = (long) Values.num(p[1]), b = (long) Values.num(p[2]);
                return dev.leo.omnievents.expr.Functions.lastRandom((double) ThreadLocalRandom.current().nextLong(Math.min(a, b), Math.max(a, b) + 1));
            }
        }
        if (player instanceof Player pl) {
            switch (k) {
                case "player", "player_name" -> {
                    return pl.getName();
                }
                case "player_world" -> {
                    return pl.getWorld().getName();
                }
                case "player_x" -> {
                    return (double) pl.getLocation().getBlockX();
                }
                case "player_y" -> {
                    return (double) pl.getLocation().getBlockY();
                }
                case "player_z" -> {
                    return (double) pl.getLocation().getBlockZ();
                }
                case "player_uuid" -> {
                    return pl.getUniqueId().toString();
                }
                default -> {
                    // seguir con PlaceholderAPI
                }
            }
        }
        BiFunction<OfflinePlayer, String, String> p = papi;
        if (p != null) {
            String out = p.apply(player, raw);
            if (out != null) return out;
        }
        return raw;
    }
}
