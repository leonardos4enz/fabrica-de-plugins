package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.command.WorldcraftCommand;
import dev.leo.worldcraft.region.Box;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * La varita (H1 CA1, CA2) y el contorno de la selección (H1 CA3). Solo es varita el ítem que da /wc wand (lleva una
 * marca): un hacha cualquiera sigue cortando madera. El contorno son partículas que ve solo ese jugador, mientras tiene
 * la varita en la mano.
 */
public final class Wand implements Listener {
    private final Worldcraft plugin;
    private final NamespacedKey key;
    private BukkitTask outline;

    public Wand(Worldcraft plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "wand");
    }

    public ItemStack create() {
        Material m = Material.matchMaterial(plugin.config().settings().wandItem());
        if (m == null || !m.isItem() || m.isAir()) m = Material.GOLDEN_AXE;
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text("Worldcraft wand", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Left click: corner 1", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right click: corner 2", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public boolean isWand(ItemStack it) {
        if (it == null || it.isEmpty() || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !isWand(e.getItem())) return;
        Action a = e.getAction();
        if (a != Action.LEFT_CLICK_BLOCK && a != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission(WorldcraftCommand.USE)) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        plugin.command().setCorner(p, p.getUniqueId(), a == Action.LEFT_CLICK_BLOCK ? 1 : 2, b.getWorld(), b.getX(), b.getY(), b.getZ());
    }

    /** En creativo un clic rompe el bloque antes de que importe lo demás: con la varita nunca se rompe nada. */
    @EventHandler(priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent e) {
        if (isWand(e.getPlayer().getInventory().getItemInMainHand())) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.edits().quit(e.getPlayer());
    }

    /** Empieza (o reinicia con los valores de config.yml) el dibujo del contorno. */
    public void startOutline() {
        if (outline != null) outline.cancel();
        outline = null;
        var s = plugin.config().settings();
        if (!s.showSelection()) return;
        outline = plugin.getServer().getScheduler().runTaskTimer(plugin, this::draw, s.showEveryTicks(), s.showEveryTicks());
    }

    public void stop() {
        if (outline != null) outline.cancel();
        outline = null;
    }

    private static final Particle.DustOptions EDGE = new Particle.DustOptions(Color.fromRGB(255, 170, 0), 0.9f);
    private static final Particle.DustOptions CORNER = new Particle.DustOptions(Color.fromRGB(80, 200, 255), 1.4f);

    private void draw() {
        int max = plugin.config().settings().maxParticles();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!isWand(p.getInventory().getItemInMainHand())) continue;
            Session ses = plugin.edits().session(p.getUniqueId());
            if (!ses.show() || ses.world() == null || !ses.world().equals(p.getWorld().getName())) continue;
            Box b = ses.box();
            if (b == null) {
                int[] c = ses.corner1() != null ? ses.corner1() : ses.corner2();
                if (c != null) cube(p, c[0], c[1], c[2], c[0] + 1, c[1] + 1, c[2] + 1, CORNER, 48);
                continue;
            }
            cube(p, b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1, EDGE, max);
        }
    }

    /** Las 12 aristas de la caja, con el espaciado que haga falta para no pasar de max partículas. */
    private static void cube(Player p, double x0, double y0, double z0, double x1, double y1, double z1, Particle.DustOptions dust, int max) {
        double total = 4 * ((x1 - x0) + (y1 - y0) + (z1 - z0));
        double step = Math.max(0.5, total / max);
        double[][] edges = {
                {x0, y0, z0, x1, y0, z0}, {x0, y1, z0, x1, y1, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z1, x1, y1, z1},
                {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
                {x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}};
        for (double[] e : edges) {
            double len = Math.abs(e[3] - e[0]) + Math.abs(e[4] - e[1]) + Math.abs(e[5] - e[2]);
            int n = Math.max(1, (int) Math.ceil(len / step));
            for (int i = 0; i <= n; i++) {
                double t = i / (double) n;
                p.spawnParticle(Particle.DUST, e[0] + (e[3] - e[0]) * t, e[1] + (e[4] - e[1]) * t, e[2] + (e[5] - e[2]) * t,
                        1, 0, 0, 0, 0, dust);
            }
        }
    }
}
