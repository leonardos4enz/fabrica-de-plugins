package dev.leo.wcexample;

import dev.leo.worldcraft.api.EditResult;
import dev.leo.worldcraft.api.Selection;
import dev.leo.worldcraft.api.WorldcraftApi;
import dev.leo.worldcraft.api.event.WorldcraftAllowedRegionEvent;
import dev.leo.worldcraft.api.event.WorldcraftEditDoneEvent;
import dev.leo.worldcraft.api.event.WorldcraftEditEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * An example of Worldcraft's API (spec 037). /wcx asks Worldcraft for edits, as the player or as this plugin; the
 * listeners stop any Worldcraft edit inside the protected areas of config.yml and write finished edits to the console.
 */
public final class WorldcraftApiExample extends JavaPlugin implements Listener {
    private record Area(String name, String world, BlockVector from, BlockVector to) {
    }

    private WorldcraftApi wc;
    private List<Area> areas = List.of(), buildAreas = List.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        wc = WorldcraftApi.get(); // depend: [Worldcraft] in plugin.yml: it is enabled before us
        load();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Using Worldcraft " + wc.pluginVersion() + " (API " + wc.apiVersion() + ")");
    }

    private void load() {
        reloadConfig();
        areas = read("protected");
        buildAreas = read("build_areas");
    }

    private List<Area> read(String key) {
        List<Area> out = new ArrayList<>();
        ConfigurationSection sec = getConfig().getConfigurationSection(key);
        if (sec != null) {
            for (String name : sec.getKeys(false)) {
                ConfigurationSection a = sec.getConfigurationSection(name);
                List<Integer> f = a == null ? List.of() : a.getIntegerList("from"), t = a == null ? List.of() : a.getIntegerList("to");
                if (a == null || f.size() != 3 || t.size() != 3) {
                    getLogger().warning(key + "." + name + " needs world, from: [x, y, z] and to: [x, y, z]: skipped");
                    continue;
                }
                out.add(new Area(name, a.getString("world", "world"), new BlockVector(f.get(0), f.get(1), f.get(2)),
                        new BlockVector(t.get(0), t.get(1), t.get(2))));
            }
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------ events

    /** Stops any Worldcraft edit that touches a protected area. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEdit(WorldcraftEditEvent ev) {
        for (Area a : areas) {
            World w = Bukkit.getWorld(a.world());
            if (ev.overlaps(w, a.from(), a.to())) {
                ev.setCancelReason("This area is protected by WorldcraftApiExample (" + a.name() + ")");
                return;
            }
        }
    }

    /** Since Worldcraft 0.16.0: //wer selects the build area the player stands in. */
    @EventHandler
    public void onAllowedRegion(WorldcraftAllowedRegionEvent ev) {
        var l = ev.getLocation();
        for (Area a : buildAreas) {
            if (!a.world().equals(l.getWorld().getName())) continue;
            int[] lo = {Math.min(a.from().getBlockX(), a.to().getBlockX()), Math.min(a.from().getBlockY(), a.to().getBlockY()), Math.min(a.from().getBlockZ(), a.to().getBlockZ())};
            int[] hi = {Math.max(a.from().getBlockX(), a.to().getBlockX()), Math.max(a.from().getBlockY(), a.to().getBlockY()), Math.max(a.from().getBlockZ(), a.to().getBlockZ())};
            if (l.getBlockX() >= lo[0] && l.getBlockX() <= hi[0] && l.getBlockY() >= lo[1] && l.getBlockY() <= hi[1] && l.getBlockZ() >= lo[2] && l.getBlockZ() <= hi[2]) {
                ev.setRegion(this, lo, hi, a.name());
                return;
            }
        }
    }

    @EventHandler
    public void onDone(WorldcraftEditDoneEvent ev) {
        if (!getConfig().getBoolean("log_edits", true)) return;
        getLogger().info("Edit #" + ev.getId() + " by " + ev.getActorName() + ": " + ev.getKind() + " " + ev.getDetail()
                + ", " + ev.getChanged() + " blocks changed" + (ev.isCompleted() ? "" : " (stopped)")
                + (ev.getPlugin() == null ? "" : ", asked by " + ev.getPlugin().getName()));
    }

    // ------------------------------------------------------------------ /wcx

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        if (a.length == 0) return false;
        try {
            switch (a[0].toLowerCase()) {
                case "selection" -> {
                    Player p = player(s);
                    wc.selection(p).ifPresentOrElse(sel -> s.sendMessage("API selection: " + describe(sel)),
                            () -> s.sendMessage("API selection: none"));
                }
                case "select" -> {
                    Player p = player(s);
                    int[] n = ints(a, 1, 6);
                    wc.select(p, p.getWorld(), new BlockVector(n[0], n[1], n[2]), new BlockVector(n[3], n[4], n[5]));
                    s.sendMessage("API selected " + String.join(" ", Arrays.copyOfRange(a, 1, 7)));
                }
                case "fill" -> {
                    Player p = player(s);
                    if (a.length < 2) return false;
                    Selection sel = wc.selection(p).orElse(null);
                    if (sel == null) {
                        s.sendMessage("Select something first");
                        return true;
                    }
                    reply(s, wc.edit(this).as(p).in(sel).set(join(a, 1)));
                }
                case "box" -> {
                    if (a.length < 9) return false;
                    World w = world(a[1]);
                    int[] n = ints(a, 2, 6);
                    reply(s, wc.edit(this).in(w, new BlockVector(n[0], n[1], n[2]), new BlockVector(n[3], n[4], n[5])).set(join(a, 8)));
                }
                case "paste" -> {
                    if (a.length < 5) return false;
                    World w = s instanceof Player p ? p.getWorld() : Bukkit.getWorlds().get(0);
                    int[] n = ints(a, 2, 3);
                    int turn = a.length > 5 ? Integer.parseInt(a[5]) : 0;
                    reply(s, wc.edit(this).paste(a[1], w, new BlockVector(n[0], n[1], n[2]), turn, false));
                }
                case "undo" -> reply(s, wc.edit(this).undo(1));
                case "reload" -> {
                    load();
                    s.sendMessage("WorldcraftApiExample reloaded: " + areas.size() + " protected area(s), " + buildAreas.size() + " build area(s)");
                }
                default -> {
                    return false;
                }
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            s.sendMessage("Error: " + e.getMessage());
        }
        return true;
    }

    /** "Edit #12: 27 blocks", "Refused: …" or the error of a bad pattern. */
    private void reply(CommandSender s, CompletableFuture<EditResult> f) {
        f.whenComplete((r, t) -> {
            if (t != null) {
                Throwable e = t.getCause() != null && t instanceof java.util.concurrent.CompletionException ? t.getCause() : t;
                s.sendMessage("API error: " + e.getMessage());
            } else if (r.status() == EditResult.Status.REFUSED) {
                s.sendMessage("API refused " + r.kind() + ": " + r.reason());
            } else {
                s.sendMessage("API " + r.kind() + " #" + r.id() + " " + r.status().name().toLowerCase() + ": " + r.changed() + " blocks");
            }
        });
    }

    private static String describe(Selection sel) {
        BlockVector a = sel.min(), b = sel.max();
        return sel.shape() + " in " + sel.world().getName() + ", " + a.getBlockX() + " " + a.getBlockY() + " " + a.getBlockZ()
                + " to " + b.getBlockX() + " " + b.getBlockY() + " " + b.getBlockZ() + ", " + sel.volume() + " blocks";
    }

    private static Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        throw new IllegalArgumentException("Only players can do that");
    }

    private static World world(String name) {
        World w = Bukkit.getWorld(name);
        if (w == null) throw new IllegalArgumentException("No world " + name);
        return w;
    }

    private static int[] ints(String[] a, int from, int count) {
        if (a.length < from + count) throw new IllegalArgumentException("Needs " + count + " numbers");
        int[] out = new int[count];
        try {
            for (int i = 0; i < count; i++) out[i] = Integer.parseInt(a[from + i]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a whole number: " + e.getMessage());
        }
        return out;
    }

    private static String join(String[] a, int from) {
        return String.join(" ", Arrays.copyOfRange(a, from, a.length));
    }
}
