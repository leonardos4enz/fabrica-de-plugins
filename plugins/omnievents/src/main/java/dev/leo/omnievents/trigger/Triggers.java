package dev.leo.omnievents.trigger;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Catálogo de disparadores (plan 001 §2.6). */
public final class Triggers {
    private final Map<String, TriggerType> types = new LinkedHashMap<>();

    public Triggers() {
        player();
        combat();
        blocks();
        items();
        inventory();
        entities();
        server();
    }

    public void register(TriggerType t) {
        types.put(t.name(), t);
    }

    public TriggerType get(String name) {
        return types.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<TriggerType> all() {
        return types.values();
    }

    public List<String> names() {
        return new ArrayList<>(types.keySet());
    }

    private static String plain(net.kyori.adventure.text.Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** cmd = {name, label, args, full} a partir de "/spawn a b" o "spawn a b". */
    public static Map<String, Object> command(String raw) {
        String s = raw.startsWith("/") ? raw.substring(1) : raw;
        String[] parts = s.trim().split("\\s+");
        Map<String, Object> cmd = new HashMap<>();
        String label = parts.length == 0 ? "" : parts[0].toLowerCase(Locale.ROOT);
        cmd.put("label", label);
        cmd.put("name", label.contains(":") ? label.substring(label.indexOf(':') + 1) : label);
        List<Object> args = new ArrayList<>(Arrays.asList(parts).subList(Math.min(1, parts.length), parts.length));
        cmd.put("args", args);
        cmd.put("full", s);
        return cmd;
    }

    private static boolean mainHand(EquipmentSlot hand) {
        return hand == null || hand == EquipmentSlot.HAND;
    }

    private static String clickAction(PlayerInteractEvent e) {
        Action a = e.getAction();
        String prefix = e.getPlayer().isSneaking() && a != Action.PHYSICAL ? "SHIFT_" : "";
        return switch (a) {
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> prefix + "LEFT_CLICK";
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> prefix + "RIGHT_CLICK";
            default -> "PHYSICAL";
        };
    }

    /** El jugador que causó un daño (directo o con un proyectil). */
    static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    private void player() {
        String g = "jugador";
        register(TriggerType.event("player_join", g, PlayerJoinEvent.class, PlayerJoinEvent::getPlayer, (e, m) -> m.put("first_join", !e.getPlayer().hasPlayedBefore()), null, false, "first_join"));
        register(TriggerType.event("player_quit", g, PlayerQuitEvent.class, PlayerQuitEvent::getPlayer, (e, m) -> m.put("reason", e.getReason()), null, false, "reason"));
        register(TriggerType.event("player_pre_login", g, AsyncPlayerPreLoginEvent.class, null, (e, m) -> {
            m.put("name", e.getName());
            m.put("uuid", e.getUniqueId().toString());
            m.put("ip", e.getAddress().getHostAddress());
        }, null, true, "name", "uuid", "ip"));
        register(TriggerType.event("player_respawn", g, PlayerRespawnEvent.class, PlayerRespawnEvent::getPlayer, (e, m) -> {
            m.put("location", e.getRespawnLocation());
            m.put("reason", e.getRespawnReason());
        }, null, false, "reason"));
        register(TriggerType.event("player_death", g, PlayerDeathEvent.class, PlayerDeathEvent::getPlayer, (e, m) -> {
            m.put("killer", e.getPlayer().getKiller());
            m.put("cause", e.getPlayer().getLastDamageCause() == null ? null : e.getPlayer().getLastDamageCause().getCause());
        }, null, false, "killer", "cause"));
        register(TriggerType.event("player_chat", g, AsyncChatEvent.class, AsyncChatEvent::getPlayer, (e, m) -> m.put("message", plain(e.message())), null, true, "message"));
        register(TriggerType.event("player_command", g, PlayerCommandPreprocessEvent.class, PlayerCommandPreprocessEvent::getPlayer, (e, m) -> {
            Map<String, Object> cmd = command(e.getMessage());
            m.put("cmd", cmd);
            m.put("args", cmd.get("args"));
        }, null, false, "cmd", "args"));
        register(TriggerType.event("player_move", g, PlayerMoveEvent.class, PlayerMoveEvent::getPlayer, (e, m) -> {
            m.put("from", e.getFrom());
            m.put("to", e.getTo());
        }, null, false, "from", "to"));
        register(TriggerType.event("player_teleport", g, PlayerTeleportEvent.class, PlayerTeleportEvent::getPlayer, (e, m) -> {
            m.put("from", e.getFrom());
            m.put("to", e.getTo());
            m.put("cause", e.getCause());
        }, null, false, "from", "to", "cause"));
        register(TriggerType.event("player_world_change", g, PlayerChangedWorldEvent.class, PlayerChangedWorldEvent::getPlayer, (e, m) -> m.put("from_world", e.getFrom()), null, false, "from_world"));
        register(TriggerType.event("player_sneak", g, PlayerToggleSneakEvent.class, PlayerToggleSneakEvent::getPlayer, (e, m) -> m.put("sneaking", e.isSneaking()), null, false, "sneaking"));
        register(TriggerType.event("player_sprint", g, PlayerToggleSprintEvent.class, PlayerToggleSprintEvent::getPlayer, (e, m) -> m.put("sprinting", e.isSprinting()), null, false, "sprinting"));
        register(TriggerType.event("player_jump", g, PlayerJumpEvent.class, PlayerJumpEvent::getPlayer, (e, m) -> {
            m.put("from", e.getFrom());
            m.put("to", e.getTo());
        }, null, false, "from", "to"));
        register(TriggerType.event("player_level_change", g, PlayerLevelChangeEvent.class, PlayerLevelChangeEvent::getPlayer, (e, m) -> {
            m.put("old_level", e.getOldLevel());
            m.put("new_level", e.getNewLevel());
        }, null, false, "old_level", "new_level"));
        register(TriggerType.event("player_gamemode_change", g, PlayerGameModeChangeEvent.class, PlayerGameModeChangeEvent::getPlayer, (e, m) -> m.put("new_mode", e.getNewGameMode()), null, false, "new_mode"));
        register(TriggerType.event("player_toggle_flight", g, PlayerToggleFlightEvent.class, PlayerToggleFlightEvent::getPlayer, (e, m) -> m.put("flying", e.isFlying()), null, false, "flying"));
        register(TriggerType.event("player_bed_enter", g, PlayerBedEnterEvent.class, PlayerBedEnterEvent::getPlayer, (e, m) -> {
            m.put("block", e.getBed());
            m.put("result", e.getBedEnterResult());
        }, null, false, "block", "result"));
        register(TriggerType.event("player_fish", g, PlayerFishEvent.class, PlayerFishEvent::getPlayer, (e, m) -> {
            m.put("target", e.getCaught());
            m.put("state", e.getState());
        }, null, false, "target", "state"));
        register(TriggerType.event("player_regain_health", g, EntityRegainHealthEvent.class, e -> (Player) e.getEntity(), (e, m) -> {
            m.put("amount", e.getAmount());
            m.put("reason", e.getRegainReason());
        }, e -> e.getEntity() instanceof Player, false, "amount", "reason"));
        register(TriggerType.event("player_food_change", g, FoodLevelChangeEvent.class, e -> (Player) e.getEntity(), (e, m) -> {
            m.put("old_food", ((Player) e.getEntity()).getFoodLevel());
            m.put("new_food", e.getFoodLevel());
        }, e -> e.getEntity() instanceof Player, false, "old_food", "new_food"));
        register(TriggerType.event("player_advancement", g, PlayerAdvancementDoneEvent.class, PlayerAdvancementDoneEvent::getPlayer, (e, m) -> m.put("advancement", e.getAdvancement().getKey().toString()), e -> !e.getAdvancement().getKey().getKey().startsWith("recipes/"), false, "advancement"));
        register(TriggerType.event("player_swap_hands", g, PlayerSwapHandItemsEvent.class, PlayerSwapHandItemsEvent::getPlayer, (e, m) -> {
            m.put("item", e.getMainHandItem());
            m.put("offhand", e.getOffHandItem());
        }, null, false, "item", "offhand"));
        register(TriggerType.event("player_armor_change", g, PlayerArmorChangeEvent.class, PlayerArmorChangeEvent::getPlayer, (e, m) -> {
            m.put("item", e.getNewItem());
            m.put("old_item", e.getOldItem());
            m.put("slot", e.getSlotType());
        }, null, false, "item", "old_item", "slot"));
        register(TriggerType.event("player_portal", g, PlayerPortalEvent.class, PlayerPortalEvent::getPlayer, (e, m) -> {
            m.put("from", e.getFrom());
            m.put("to", e.getTo());
            m.put("cause", e.getCause());
        }, null, false, "from", "to", "cause"));
        register(TriggerType.event("player_kick", g, PlayerKickEvent.class, PlayerKickEvent::getPlayer, (e, m) -> m.put("reason", plain(e.reason())), null, false, "reason"));
        register(TriggerType.event("player_resource_pack", g, PlayerResourcePackStatusEvent.class, PlayerResourcePackStatusEvent::getPlayer, (e, m) -> m.put("status", e.getStatus()), null, false, "status"));
    }

    private void combat() {
        String g = "combate";
        register(TriggerType.event("player_damage", g, EntityDamageEvent.class, e -> (Player) e.getEntity(), (e, m) -> {
            m.put("cause", e.getCause());
            m.put("damage", e.getDamage());
            if (e instanceof EntityDamageByEntityEvent b) {
                Player a = attacker(b.getDamager());
                m.put("damager", a != null ? a : b.getDamager());
            }
        }, e -> e.getEntity() instanceof Player, false, "cause", "damage", "damager"));
        register(TriggerType.event("player_attack", g, EntityDamageByEntityEvent.class, e -> attacker(e.getDamager()), (e, m) -> {
            m.put("target", e.getEntity());
            m.put("damage", e.getDamage());
            m.put("projectile", e.getDamager() instanceof Projectile ? e.getDamager() : null);
        }, e -> attacker(e.getDamager()) != null, false, "target", "damage", "projectile", "item"));
        register(TriggerType.event("player_kill", g, EntityDeathEvent.class, e -> e.getEntity().getKiller(), (e, m) -> m.put("target", e.getEntity()),
                e -> e.getEntity().getKiller() != null, false, "target", "item"));
        register(TriggerType.event("player_shoot", g, EntityShootBowEvent.class, e -> (Player) e.getEntity(), (e, m) -> {
            m.put("item", e.getBow());
            m.put("projectile", e.getProjectile());
            m.put("force", e.getForce());
        }, e -> e.getEntity() instanceof Player, false, "item", "projectile", "force"));
        register(TriggerType.event("projectile_hit", g, ProjectileHitEvent.class, e -> (Player) e.getEntity().getShooter(), (e, m) -> {
            m.put("target", e.getHitEntity());
            m.put("block", e.getHitBlock());
            m.put("projectile", e.getEntity());
        }, e -> e.getEntity().getShooter() instanceof Player, false, "target", "block", "projectile"));
    }

    private void blocks() {
        String g = "bloques";
        register(TriggerType.event("block_break", g, BlockBreakEvent.class, BlockBreakEvent::getPlayer, (e, m) -> m.put("block", e.getBlock()), null, false, "block", "item"));
        register(TriggerType.event("block_place", g, BlockPlaceEvent.class, BlockPlaceEvent::getPlayer, (e, m) -> {
            m.put("block", e.getBlockPlaced());
            m.put("against", e.getBlockAgainst());
            m.put("item", e.getItemInHand());
        }, null, false, "block", "against", "item"));
        register(TriggerType.event("block_interact", g, PlayerInteractEvent.class, PlayerInteractEvent::getPlayer, (e, m) -> {
            m.put("block", e.getClickedBlock());
            m.put("action", clickAction(e));
            m.put("face", e.getBlockFace());
            if (e.getItem() != null) m.put("item", e.getItem());
        }, e -> e.getClickedBlock() != null && mainHand(e.getHand()), false, "block", "action", "face", "item"));
        register(TriggerType.event("sign_change", g, SignChangeEvent.class, SignChangeEvent::getPlayer, (e, m) -> {
            List<Object> lines = new ArrayList<>();
            for (net.kyori.adventure.text.Component c : e.lines()) lines.add(plain(c));
            m.put("lines", lines);
            m.put("block", e.getBlock());
        }, null, false, "lines", "block"));
        register(TriggerType.event("bucket_fill", g, PlayerBucketFillEvent.class, PlayerBucketFillEvent::getPlayer, (e, m) -> {
            m.put("block", e.getBlockClicked());
            m.put("item", e.getItemStack());
        }, null, false, "block", "item"));
        register(TriggerType.event("bucket_empty", g, PlayerBucketEmptyEvent.class, PlayerBucketEmptyEvent::getPlayer, (e, m) -> {
            m.put("block", e.getBlockClicked());
            m.put("bucket", e.getBucket());
        }, null, false, "block", "bucket"));
    }

    private void items() {
        String g = "ítems";
        register(TriggerType.event("player_interact", g, PlayerInteractEvent.class, PlayerInteractEvent::getPlayer, (e, m) -> {
            m.put("action", clickAction(e));
            m.put("block", e.getClickedBlock());
            if (e.getItem() != null) m.put("item", e.getItem());
        }, e -> mainHand(e.getHand()), false, "action", "block", "item"));
        register(TriggerType.event("item_interact", g, PlayerInteractEvent.class, PlayerInteractEvent::getPlayer, (e, m) -> {
            m.put("item", e.getItem());
            m.put("action", clickAction(e));
            m.put("block", e.getClickedBlock());
        }, e -> e.getItem() != null && mainHand(e.getHand()) && e.getAction() != Action.PHYSICAL, false, "item", "action", "block"));
        register(TriggerType.event("item_consume", g, PlayerItemConsumeEvent.class, PlayerItemConsumeEvent::getPlayer, (e, m) -> m.put("item", e.getItem()), null, false, "item"));
        register(TriggerType.event("item_craft", g, CraftItemEvent.class, e -> (Player) e.getWhoClicked(), (e, m) -> {
            m.put("item", e.getRecipe().getResult());
            m.put("click", e.getClick());
        }, e -> e.getWhoClicked() instanceof Player, false, "item", "click"));
        register(TriggerType.event("item_drop", g, PlayerDropItemEvent.class, PlayerDropItemEvent::getPlayer, (e, m) -> {
            m.put("item", e.getItemDrop().getItemStack());
            m.put("entity", e.getItemDrop());
        }, null, false, "item", "entity"));
        register(TriggerType.event("item_pickup", g, EntityPickupItemEvent.class, e -> (Player) e.getEntity(), (e, m) -> {
            m.put("item", e.getItem().getItemStack());
            m.put("entity", e.getItem());
        }, e -> e.getEntity() instanceof Player, false, "item", "entity"));
        register(TriggerType.event("item_held", g, PlayerItemHeldEvent.class, PlayerItemHeldEvent::getPlayer, (e, m) -> {
            m.put("item", e.getPlayer().getInventory().getItem(e.getNewSlot()));
            m.put("previous_item", e.getPlayer().getInventory().getItem(e.getPreviousSlot()));
            m.put("slot", e.getNewSlot());
        }, null, false, "item", "previous_item", "slot"));
        register(TriggerType.event("item_enchant", g, EnchantItemEvent.class, EnchantItemEvent::getEnchanter, (e, m) -> {
            m.put("item", e.getItem());
            Map<String, Object> ench = new LinkedHashMap<>();
            e.getEnchantsToAdd().forEach((k, v) -> ench.put(k.getKey().getKey(), v));
            m.put("enchants", ench);
            m.put("cost", e.getExpLevelCost());
        }, null, false, "item", "enchants", "cost"));
        register(TriggerType.event("item_break", g, PlayerItemBreakEvent.class, PlayerItemBreakEvent::getPlayer, (e, m) -> m.put("item", e.getBrokenItem()), null, false, "item"));
    }

    private void inventory() {
        String g = "inventario";
        register(TriggerType.event("inventory_open", g, InventoryOpenEvent.class, e -> (Player) e.getPlayer(), (e, m) -> {
            m.put("title", plain(e.getView().title()));
            m.put("inventory_type", e.getInventory().getType());
        }, e -> e.getPlayer() instanceof Player, false, "title", "inventory_type"));
        register(TriggerType.event("inventory_close", g, InventoryCloseEvent.class, e -> (Player) e.getPlayer(), (e, m) -> {
            m.put("title", plain(e.getView().title()));
            m.put("inventory_type", e.getInventory().getType());
        }, e -> e.getPlayer() instanceof Player, false, "title", "inventory_type"));
        register(TriggerType.event("inventory_click", g, InventoryClickEvent.class, e -> (Player) e.getWhoClicked(), (e, m) -> {
            m.put("item", e.getCurrentItem());
            m.put("cursor", e.getCursor());
            m.put("slot", e.getSlot());
            m.put("raw_slot", e.getRawSlot());
            m.put("click", e.getClick());
            m.put("title", plain(e.getView().title()));
            m.put("inventory_type", e.getView().getTopInventory().getType());
        }, e -> e.getWhoClicked() instanceof Player, false, "item", "cursor", "slot", "raw_slot", "click", "title", "inventory_type"));
    }

    private void entities() {
        String g = "entidades";
        register(TriggerType.event("entity_interact", g, PlayerInteractEntityEvent.class, PlayerInteractEntityEvent::getPlayer, (e, m) -> m.put("target", e.getRightClicked()),
                e -> mainHand(e.getHand()), false, "target", "item"));
        register(TriggerType.event("entity_spawn", g, CreatureSpawnEvent.class, null, (e, m) -> {
            m.put("entity", e.getEntity());
            m.put("reason", e.getSpawnReason());
            m.put("world", e.getEntity().getWorld());
            m.put("location", e.getLocation());
        }, null, false, "entity", "reason"));
        register(TriggerType.event("entity_death", g, EntityDeathEvent.class, e -> e.getEntity().getKiller(), (e, m) -> {
            m.put("entity", e.getEntity());
            m.put("killer", e.getEntity().getKiller());
            m.put("world", e.getEntity().getWorld());
            m.put("location", e.getEntity().getLocation());
        }, null, false, "entity", "killer"));
    }

    private void server() {
        String g = "servidor";
        register(TriggerType.event("console_command", g, ServerCommandEvent.class, null, (e, m) -> {
            Map<String, Object> cmd = command(e.getCommand());
            m.put("cmd", cmd);
            m.put("args", cmd.get("args"));
            m.put("sender", e.getSender());
        }, null, false, "cmd", "args", "sender"));
        register(TriggerType.special("server_start", TriggerType.Kind.SERVER_START, g));
        register(TriggerType.special("server_stop", TriggerType.Kind.SERVER_STOP, g));
        register(TriggerType.special("timer", TriggerType.Kind.TIMER, g, "player", "world", "location"));
        register(TriggerType.special("clock", TriggerType.Kind.CLOCK, g, "player", "world", "location", "time"));
        register(TriggerType.special("call", TriggerType.Kind.CALL, g, "player", "event", "world", "location", "sender", "caller"));
        register(TriggerType.special("command", TriggerType.Kind.COMMAND, g, "player", "sender", "cmd", "args", "world", "location"));
        register(TriggerType.special("custom", TriggerType.Kind.CUSTOM, g, "player", "event", "world", "location"));
    }

    /** Raíces comunes que cualquier contexto ofrece. */
    public static final List<String> COMMON_ROOTS = List.of("server", "rule", "online", "source", "target", "item", "block", "entity");

    static Block blockOf(Object o) {
        return o instanceof Block b ? b : null;
    }
}
