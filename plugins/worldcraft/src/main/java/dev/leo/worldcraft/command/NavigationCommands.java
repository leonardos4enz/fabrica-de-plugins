package dev.leo.worldcraft.command;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.config.Messages;
import dev.leo.worldcraft.edit.RegionSource;
import dev.leo.worldcraft.paper.Edits;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Direction;
import dev.leo.worldcraft.region.Shape;
import dev.leo.worldcraft.region.Spots;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.List;

/**
 * Spec 028 H4: moverse al construir (plan §1.4). Solo jugadores, con worldcraft.navigate. No cambian el mundo, salvo el
 * vidrio de up y ceil para quien no puede volar (una obra de un bloque, con historial).
 */
final class NavigationCommands {
    static final String PERM = "worldcraft.navigate";
    static final List<String> SUBS = List.of("unstuck", "ascend", "descend", "ceil", "thru", "jumpto", "up");

    private final Worldcraft plugin;

    NavigationCommands(Worldcraft plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private static Spots.World of(World w) {
        return new Spots.World() {
            @Override
            public boolean passable(int x, int y, int z) {
                return w.getBlockAt(x, y, z).isPassable();
            }

            @Override
            public int minY() {
                return w.getMinHeight();
            }

            @Override
            public int maxY() {
                return w.getMaxHeight() - 1;
            }
        };
    }

    private static int number(String[] a, int i, int def) {
        if (a.length <= i) return def;
        try {
            int n = Integer.parseInt(a[i]);
            if (n < 0 || n > 4096) throw new WorldcraftCommand.Usage();
            return n;
        } catch (NumberFormatException e) {
            throw new WorldcraftCommand.Usage();
        }
    }

    void run(CommandSender s, String sub, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(msg().get("players-only"));
            return;
        }
        World w = p.getWorld();
        Location l = p.getLocation();
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        Spots.World sw = of(w);
        switch (sub) {
            case "ascend", "descend" -> {
                int n = Math.max(1, number(a, 1, 1));
                Integer to = sub.equals("ascend") ? Spots.ascend(sw, x, y, z, n) : Spots.descend(sw, x, y, z, n);
                go(p, x, to, z, sub.equals("ascend") ? "nav-no-floor-above" : "nav-no-floor-below");
            }
            case "unstuck" -> go(p, x, Spots.unstuck(sw, x, y, z), z, "nav-no-spot");
            case "ceil" -> {
                Integer to = Spots.ceil(sw, x, y, z, number(a, 1, 0));
                if (to == null) {
                    p.sendMessage(msg().get("nav-no-ceiling"));
                    return;
                }
                hover(p, x, to, z, "ceil");
            }
            case "up" -> {
                if (a.length < 2) throw new WorldcraftCommand.Usage();
                Integer to = Spots.up(sw, x, y, z, number(a, 1, 1));
                if (to == null) {
                    p.sendMessage(msg().get("nav-blocked"));
                    return;
                }
                hover(p, x, to, z, "up");
            }
            case "thru" -> {
                Direction d = Direction.facing(l.getYaw(), l.getPitch());
                int[] to = Spots.thru(sw, x, y, z, d.dx, d.dy, d.dz, 32);
                if (to == null) {
                    p.sendMessage(msg().get("nav-no-wall"));
                    return;
                }
                teleport(p, to[0], to[1], to[2]);
            }
            case "jumpto" -> {
                int reach = plugin.config().terrain().reach();
                RayTraceResult hit = w.rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), reach,
                        FluidCollisionMode.NEVER, true);
                if (hit == null || hit.getHitBlock() == null) {
                    p.sendMessage(msg().get("nav-no-target", "reach", String.valueOf(reach)));
                    return;
                }
                int hx = hit.getHitBlock().getX(), hz = hit.getHitBlock().getZ();
                go(p, hx, Spots.onTop(sw, hx, hit.getHitBlock().getY(), hz), hz, "nav-no-spot");
            }
            default -> throw new WorldcraftCommand.Usage();
        }
    }

    private void go(Player p, int x, Integer y, int z, String failKey) {
        if (y == null) {
            p.sendMessage(msg().get(failKey));
            return;
        }
        teleport(p, x, y, z);
    }

    private void teleport(Player p, int x, int y, int z) {
        Location l = p.getLocation();
        p.teleport(new Location(p.getWorld(), x + 0.5, y, z + 0.5, l.getYaw(), l.getPitch()));
        p.sendMessage(msg().get("nav-moved", "pos", x + " " + y + " " + z));
    }

    /**
     * up y ceil dejan al jugador en el aire con un vidrio bajo los pies como una obra de un bloque (con límites e
     * historial) y lo suben cuando el vidrio está puesto. Spec 040 (pedido del dueño): también si puede volar (en
     * creativo), como /up de WorldEdit: el vidrio sirve de piso.
     */
    private void hover(Player p, int x, int y, int z, String kind) {
        World w = p.getWorld();
        Box glass = new Box(x, y - 1, z, x, y - 1, z);
        if (!w.getBlockAt(x, y - 1, z).isPassable()) {
            teleport(p, x, y, z);
            return;
        }
        var blocks = plugin.edits().blocks();
        int state = blocks.id(org.bukkit.Material.GLASS.createBlockData());
        Pattern.Compiled pc = Pattern.of("minecraft:glass").compile(blocks.table());
        plugin.edits().request(p, new Edits.Op(kind, "glass under your feet", w, glass, 1,
                () -> new RegionSource(glass, Shape.FILL, null, pc, 0), List.of(state), null).then(() -> {
            if (p.isOnline()) teleport(p, x, y, z);
        }), false);
    }
}
