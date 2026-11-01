package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.command.WorldcraftCommand;
import dev.leo.worldcraft.terrain.BrushSettings;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.List;

/**
 * El pincel de terreno (spec 026 H3): un ítem con la marca del plugin y sus ajustes en el PDC. Clic derecho = una pasada
 * donde mira; clic izquierdo = el siguiente modo. Mientras se tiene en la mano, un círculo de partículas del tamaño del
 * pincel donde caería, que ve solo ese jugador.
 */
public final class BrushTool implements Listener {
    private static final int EVERY_TICKS = 4, MAX_PARTICLES = 64;
    private final Worldcraft plugin;
    private final NamespacedKey key;
    private BukkitTask outline;

    public BrushTool(Worldcraft plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "brush");
    }

    public ItemStack create(BrushSettings b) {
        Material m = Material.matchMaterial(plugin.config().terrain().brushItem());
        if (m == null || !m.isItem() || m.isAir()) m = Material.GOLDEN_SHOVEL;
        ItemStack it = new ItemStack(m);
        write(it, b);
        return it;
    }

    /** Guarda los ajustes en el ítem y pone su nombre y descripción. */
    public void write(ItemStack it, BrushSettings b) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text("Terrain brush: " + b.describe(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Right click: use it where you look", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Left click: next mode", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("/wc brush <mode> <radius> <strength> to change it", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, b.encode());
        it.setItemMeta(meta);
    }

    /** Los ajustes del ítem, o null si no es un pincel. */
    public BrushSettings settings(ItemStack it) {
        if (it == null || it.isEmpty() || !it.hasItemMeta()) return null;
        String s = it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return s == null ? null : BrushSettings.decode(s);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        BrushSettings b = settings(e.getItem());
        if (b == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!p.hasPermission(WorldcraftCommand.USE)) return;
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            plugin.terrain().stroke(p, b);
        } else if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            BrushSettings next = b.withMode(b.mode().next());
            write(e.getItem(), next);
            p.sendActionBar(plugin.messages().get("brush-mode", "brush", next.describe()));
        }
    }

    /** En creativo un clic izquierdo rompe el bloque: con el pincel nunca se rompe nada. */
    @EventHandler(priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent e) {
        if (settings(e.getPlayer().getInventory().getItemInMainHand()) != null) e.setCancelled(true);
    }

    public void start() {
        stop();
        if (!plugin.config().terrain().brushShow()) return;
        outline = plugin.getServer().getScheduler().runTaskTimer(plugin, this::draw, EVERY_TICKS, EVERY_TICKS);
    }

    public void stop() {
        if (outline != null) outline.cancel();
        outline = null;
    }

    private static final Particle.DustOptions RING = new Particle.DustOptions(Color.fromRGB(120, 255, 140), 1.0f);

    private void draw() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            BrushSettings b = settings(p.getInventory().getItemInMainHand());
            if (b == null || !p.hasPermission(WorldcraftCommand.USE)) continue;
            RayTraceResult hit = Terrain.look(p, plugin.config().terrain().brushRange());
            if (hit == null || hit.getHitBlock() == null) continue;
            Block c = hit.getHitBlock();
            int r = Math.min(b.radius(), plugin.config().terrain().brushMaxRadius());
            int n = Math.min(MAX_PARTICLES, Math.max(12, r * 8));
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                p.spawnParticle(Particle.DUST, c.getX() + 0.5 + Math.cos(a) * (r + 0.5), c.getY() + 1.1,
                        c.getZ() + 0.5 + Math.sin(a) * (r + 0.5), 1, 0, 0, 0, 0, RING);
            }
        }
    }
}
