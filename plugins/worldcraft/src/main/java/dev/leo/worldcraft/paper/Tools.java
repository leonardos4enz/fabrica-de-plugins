package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.command.WorldcraftCommand;
import dev.leo.worldcraft.tool.ToolKind;
import dev.leo.worldcraft.tool.ToolSpec;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pinceles de construcción y herramientas de un clic (spec 032 H1, H6): la marca en el ítem de la mano (solo ese ítem:
 * otro igual sigue siendo normal), los clics, el círculo de partículas y el superpico. Lo que hace cada clic está en
 * {@link ToolActions}. Hilo principal.
 */
public final class Tools implements Listener {
    public static final String SUPERPICKAXE = "worldcraft.superpickaxe";
    private static final int EVERY_TICKS = 4, MAX_PARTICLES = 64;
    private final Worldcraft plugin;
    private final NamespacedKey key, added;
    private final ToolActions actions;
    private final Map<UUID, ToolActions.SuperPick> superpicks = new HashMap<>();
    private BukkitTask outline;

    /** Lo que el plugin le puso al ítem y le quita con none (H1 CA2). */
    private static final String NAME = "name", LORE = "lore";

    public Tools(Worldcraft plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "tool");
        this.added = new NamespacedKey(plugin, "tool_added");
        this.actions = new ToolActions(plugin);
    }

    public ToolActions actions() {
        return actions;
    }

    /** El pincel o herramienta del ítem, o null. */
    public ToolSpec spec(ItemStack it) {
        if (it == null || it.isEmpty() || !it.hasItemMeta()) return null;
        String s = it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return ToolSpec.decode(s, plugin.config().tools().caps());
    }

    /** Qué pasó al poner un pincel o herramienta. */
    public enum Bound { GIVEN, UPDATED, BOUND, WAND }

    /**
     * H1 CA1: el ítem de la mano se vuelve el pincel (uno ya marcado se vuelve a ajustar; uno de terreno deja de serlo);
     * con la mano vacía, uno nuevo. La varita no.
     */
    public Bound bind(Player p, ToolSpec s) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (plugin.wand().isWand(held)) return Bound.WAND;
        if (held.isEmpty()) {
            String id = s.kind() == ToolKind.NAVWAND ? plugin.config().tools().navwandItem() : plugin.config().tools().item();
            Material m = Material.matchMaterial(id);
            if (m == null || !m.isItem() || m.isAir()) m = s.kind() == ToolKind.NAVWAND ? Material.COMPASS : Material.BLAZE_ROD;
            ItemStack it = new ItemStack(m);
            write(it, s);
            p.getInventory().setItemInMainHand(it);
            return Bound.GIVEN;
        }
        boolean was = spec(held) != null;
        if (plugin.brush().settings(held) != null) {
            plugin.brush().clear(held);
            was = true;
        }
        write(held, s);
        return was ? Bound.UPDATED : Bound.BOUND;
    }

    /** Guarda el pincel en el ítem; el nombre y la descripción solo si no tenía unos propios (o si eran nuestros). */
    public void write(ItemStack it, ToolSpec s) {
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String mine = pdc.getOrDefault(added, PersistentDataType.STRING, "");
        boolean ourName = mine.contains(NAME) || !meta.hasDisplayName(), ourLore = mine.contains(LORE) || !meta.hasLore();
        String what = s.kind().brush ? "Brush" : "Tool";
        if (ourName) meta.displayName(Component.text(what + ": " + s.describe(), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        if (ourLore) meta.lore(lore(s).stream().map(l -> (Component) Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        pdc.set(key, PersistentDataType.STRING, s.encode());
        pdc.set(added, PersistentDataType.STRING, (ourName ? NAME : "") + "," + (ourLore ? LORE : ""));
        it.setItemMeta(meta);
    }

    private static List<String> lore(ToolSpec s) {
        String right = switch (s.kind()) {
            case INFO -> "Click: what is this block";
            case FARWAND -> "Left click: corner 1 · right click: corner 2";
            case NAVWAND -> "Left click: jump to · right click: through walls";
            case REPL -> "Right click: place · left click: pick a block";
            case CYCLER -> "Right click: next value · left click: next property";
            case LRBUILD -> "Left click: " + s.text("left") + " · right click: " + s.text("right");
            case TREE -> "Right click: a tree on the block you look at";
            default -> "Right click: use it where you look";
        };
        return List.of(right, "/wc " + (s.kind().brush ? "brush" : "tool") + " none: back to a normal item");
    }

    /** H1 CA2: le quita la marca (también la del pincel de terreno) y lo que el plugin le agregó. ¿Tenía algo? */
    public boolean unbind(ItemStack it) {
        if (it == null || it.isEmpty() || !it.hasItemMeta()) return false;
        boolean any = false;
        if (plugin.brush().settings(it) != null) {
            plugin.brush().clear(it);
            any = true;
        }
        ItemMeta meta = it.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(key)) {
            String mine = pdc.getOrDefault(added, PersistentDataType.STRING, "");
            if (mine.contains(NAME)) meta.displayName(null);
            if (mine.contains(LORE)) meta.lore(null);
            pdc.remove(key);
            pdc.remove(added);
            it.setItemMeta(meta);
            any = true;
        }
        return any;
    }

    // ------------------------------------------------------------------ superpico (H6)

    public ToolActions.SuperPick superpick(UUID id) {
        return superpicks.get(id);
    }

    public void superpick(UUID id, ToolActions.SuperPick sp) {
        superpicks.put(id, sp);
    }

    private static boolean pickaxe(ItemStack it) {
        return it != null && it.getType().name().endsWith("_PICKAXE");
    }

    private boolean superpickActive(Player p, ItemStack it) {
        ToolActions.SuperPick sp = superpicks.get(p.getUniqueId());
        return sp != null && sp.on() && pickaxe(it) && spec(it) == null;
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() == Action.PHYSICAL) return;
        Player p = e.getPlayer();
        ItemStack it = e.getItem();
        ToolSpec s = spec(it);
        boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (s == null) {
            // H6: clic izquierdo en un bloque con un pico, con el superpico prendido.
            if (e.getAction() == Action.LEFT_CLICK_BLOCK && superpickActive(p, it) && e.getClickedBlock() != null) {
                e.setCancelled(true);
                if (!p.hasPermission(SUPERPICKAXE)) return;
                actions.superpickaxe(p, e.getClickedBlock(), superpicks.get(p.getUniqueId()));
            }
            return;
        }
        e.setCancelled(true);
        // H7 CA1: los permisos y los límites son los de quien hace clic, en cada clic.
        if (!p.hasPermission(WorldcraftCommand.USE)) return;
        if (s.kind().brush) {
            if (right) actions.brush(p, s);
        } else {
            actions.tool(p, it, s, right);
        }
    }

    /** H1 CA6: con un pincel o herramienta (o el superpico) nunca se rompe un bloque a mano. */
    @EventHandler(priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent e) {
        ItemStack it = e.getPlayer().getInventory().getItemInMainHand();
        if (spec(it) != null || superpickActive(e.getPlayer(), it)) e.setCancelled(true);
    }

    /** H1 CA6: un montón de bloques marcado no se pone. */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getHand() == EquipmentSlot.HAND && spec(e.getItemInHand()) != null) e.setCancelled(true);
    }

    /** H6 CA3: el superpico se apaga al desconectarse. */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        superpicks.remove(e.getPlayer().getUniqueId());
        actions.forget(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ círculo (H1 CA4)

    public void start() {
        stop();
        if (!plugin.config().tools().show()) return;
        outline = plugin.getServer().getScheduler().runTaskTimer(plugin, this::draw, EVERY_TICKS, EVERY_TICKS);
    }

    public void stop() {
        if (outline != null) outline.cancel();
        outline = null;
    }

    private static final Particle.DustOptions RING = new Particle.DustOptions(Color.fromRGB(110, 190, 255), 1.0f);

    private void draw() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            ToolSpec s = spec(p.getInventory().getItemInMainHand());
            if (s == null || !s.kind().hasRadius() || !p.hasPermission(WorldcraftCommand.USE)) continue;
            RayTraceResult hit = Terrain.look(p, plugin.config().tools().range());
            if (hit == null || hit.getHitBlock() == null) continue;
            Block c = hit.getHitBlock();
            int r = s.radius();
            int n = Math.min(MAX_PARTICLES, Math.max(12, r * 8));
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                p.spawnParticle(Particle.DUST, c.getX() + 0.5 + Math.cos(a) * (r + 0.5), c.getY() + 1.1,
                        c.getZ() + 0.5 + Math.sin(a) * (r + 0.5), 1, 0, 0, 0, 0, RING);
            }
        }
    }
}
