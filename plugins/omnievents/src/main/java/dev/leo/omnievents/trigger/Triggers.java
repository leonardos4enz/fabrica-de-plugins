package dev.leo.omnievents.trigger;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import dev.leo.omnievents.hook.Npcs;
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
import org.bukkit.inventory.view.AnvilView;

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
        integrations();
        menus();
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

    /**
     * Spec 014, H3 CA2: el clic se lleva el resultado de un yunque o una afiladora. En el yunque, fuera de creativo,
     * hacen falta los niveles (si no, Minecraft no deja sacarlo).
     */
    static boolean takesRepairResult(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || e.getRawSlot() != 2) return false;
        org.bukkit.event.inventory.InventoryType type = e.getView().getTopInventory().getType();
        if (type != org.bukkit.event.inventory.InventoryType.ANVIL && type != org.bukkit.event.inventory.InventoryType.GRINDSTONE) return false;
        if (e.getCurrentItem() == null || e.getCurrentItem().getType().isAir()) return false;
        if (e.getView() instanceof AnvilView a && p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            return a.getRepairCost() > 0 && p.getLevel() >= a.getRepairCost();
        }
        return true;
    }

    /** El jugador que causó un daño (directo o con un proyectil). */
    static Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    private void player() {
        String g = "jugador";
        register(TriggerType.event("player_join", g, PlayerJoinEvent.class, PlayerJoinEvent::getPlayer, (e, m) -> m.put("first_join", !e.getPlayer().hasPlayedBefore()), null, false, "first_join").doc("Un jugador entra al servidor (first_join: si es su primera vez)."));
        register(TriggerType.event("player_quit", g, PlayerQuitEvent.class, PlayerQuitEvent::getPlayer, (e, m) -> m.put("reason", e.getReason()), null, false, "reason").doc("Un jugador sale del servidor."));
        register(TriggerType.event("player_pre_login", g, AsyncPlayerPreLoginEvent.class, null, (e, m) -> {
            m.put("name", e.getName());
            m.put("uuid", e.getUniqueId().toString());
            m.put("ip", e.getAddress().getHostAddress());
        }, null, true, "name", "uuid", "ip").doc("Antes de entrar, en un hilo asíncrono: aún no hay jugador, solo name, uuid e ip (deny_join lo rechaza)."));
        register(TriggerType.event("player_respawn", g, PlayerRespawnEvent.class, PlayerRespawnEvent::getPlayer, (e, m) -> {
            m.put("location", e.getRespawnLocation());
            m.put("reason", e.getRespawnReason());
        }, null, false, "reason").doc("Un jugador reaparece tras morir."));
        register(TriggerType.event("player_death", g, PlayerDeathEvent.class, PlayerDeathEvent::getPlayer, (e, m) -> {
            m.put("killer", e.getPlayer().getKiller());
            m.put("cause", e.getPlayer().getLastDamageCause() == null ? null : e.getPlayer().getLastDamageCause().getCause());
        }, null, false, "killer", "cause").doc("Un jugador muere (killer: quien lo mató, si fue otro jugador)."));
        register(TriggerType.event("player_chat", g, AsyncChatEvent.class, AsyncChatEvent::getPlayer, (e, m) -> m.put("message", plain(e.message())), null, true, "message").doc("Un jugador escribe en el chat (asíncrono; message es el texto, sin formato)."));
        register(TriggerType.event("player_command", g, PlayerCommandPreprocessEvent.class, PlayerCommandPreprocessEvent::getPlayer, (e, m) -> {
            Map<String, Object> cmd = command(e.getMessage());
            m.put("cmd", cmd);
            m.put("args", cmd.get("args"));
        }, null, false, "cmd", "args").doc("Un jugador escribe un comando, de cualquier plugin (cmd.name, args)."));
        register(TriggerType.event("player_move", g, PlayerMoveEvent.class, PlayerMoveEvent::getPlayer, (e, m) -> {
            m.put("from", e.getFrom());
            m.put("to", e.getTo());
        }, null, false, "from", "to").doc("Un jugador se mueve (move_mode: block, chunk o any). player.location todavía es el origen (from): el bloque al que llega es to.block y la altura nueva, to.y."));
        register(TriggerType.event("player_teleport", g, PlayerTeleportEvent.class, PlayerTeleportEvent::getPlayer, (e, m) -> {
            m.put("from", e.getFrom());
            m.put("to", e.getTo());
            m.put("cause", e.getCause());
        }, null, false, "from", "to", "cause").doc("Un jugador es teletransportado."));
        register(TriggerType.event("player_world_change", g, PlayerChangedWorldEvent.class, PlayerChangedWorldEvent::getPlayer, (e, m) -> m.put("from_world", e.getFrom()), null, false, "from_world").doc("Un jugador cambia de mundo (from_world: de dónde viene)."));
        register(TriggerType.event("player_sneak", g, PlayerToggleSneakEvent.class, PlayerToggleSneakEvent::getPlayer, (e, m) -> m.put("sneaking", e.isSneaking()), null, false, "sneaking").doc("Un jugador empieza o deja de agacharse (sneaking)."));
        register(TriggerType.event("player_sprint", g, PlayerToggleSprintEvent.class, PlayerToggleSprintEvent::getPlayer, (e, m) -> m.put("sprinting", e.isSprinting()), null, false, "sprinting").doc("Un jugador empieza o deja de correr (sprinting)."));
        register(TriggerType.event("player_jump", g, PlayerJumpEvent.class, PlayerJumpEvent::getPlayer, (e, m) -> {
            m.put("from", e.getFrom());
            m.put("to", e.getTo());
        }, null, false, "from", "to").doc("Un jugador salta."));
        register(TriggerType.event("player_level_change", g, PlayerLevelChangeEvent.class, PlayerLevelChangeEvent::getPlayer, (e, m) -> {
            m.put("old_level", e.getOldLevel());
            m.put("new_level", e.getNewLevel());
        }, null, false, "old_level", "new_level").doc("Cambia el nivel de experiencia de un jugador."));
        register(TriggerType.event("player_gamemode_change", g, PlayerGameModeChangeEvent.class, PlayerGameModeChangeEvent::getPlayer, (e, m) -> m.put("new_mode", e.getNewGameMode()), null, false, "new_mode").doc("Un jugador cambia de modo de juego (new_mode)."));
        register(TriggerType.event("player_toggle_flight", g, PlayerToggleFlightEvent.class, PlayerToggleFlightEvent::getPlayer, (e, m) -> m.put("flying", e.isFlying()), null, false, "flying").doc("Un jugador empieza o deja de volar (flying)."));
        register(TriggerType.event("player_bed_enter", g, PlayerBedEnterEvent.class, PlayerBedEnterEvent::getPlayer, (e, m) -> {
            m.put("block", e.getBed());
            m.put("result", e.getBedEnterResult());
        }, null, false, "block", "result").doc("Un jugador intenta acostarse en una cama."));
        register(TriggerType.event("player_fish", g, PlayerFishEvent.class, PlayerFishEvent::getPlayer, (e, m) -> {
            m.put("target", e.getCaught());
            m.put("state", e.getState());
        }, null, false, "target", "state").doc("Un jugador pesca (state: estado de la caña; target: lo que sacó)."));
        register(TriggerType.event("player_regain_health", g, EntityRegainHealthEvent.class, e -> (Player) e.getEntity(), (e, m) -> {
            m.put("amount", e.getAmount());
            m.put("reason", e.getRegainReason());
        }, e -> e.getEntity() instanceof Player, false, "amount", "reason").doc("Un jugador recupera vida (amount, reason)."));
        register(TriggerType.event("player_food_change", g, FoodLevelChangeEvent.class, e -> (Player) e.getEntity(), (e, m) -> {
            m.put("old_food", ((Player) e.getEntity()).getFoodLevel());
            m.put("new_food", e.getFoodLevel());
        }, e -> e.getEntity() instanceof Player, false, "old_food", "new_food").doc("Cambia el hambre de un jugador (old_food, new_food)."));
        register(TriggerType.event("player_advancement", g, PlayerAdvancementDoneEvent.class, PlayerAdvancementDoneEvent::getPlayer, (e, m) -> m.put("advancement", e.getAdvancement().getKey().toString()), e -> !e.getAdvancement().getKey().getKey().startsWith("recipes/"), false, "advancement").doc("Un jugador consigue un logro (sin las recetas)."));
        register(TriggerType.event("player_swap_hands", g, PlayerSwapHandItemsEvent.class, PlayerSwapHandItemsEvent::getPlayer, (e, m) -> {
            m.put("item", e.getMainHandItem());
            m.put("offhand", e.getOffHandItem());
        }, null, false, "item", "offhand").doc("Un jugador cambia los ítems de mano (tecla F)."));
        register(TriggerType.event("player_armor_change", g, PlayerArmorChangeEvent.class, PlayerArmorChangeEvent::getPlayer, (e, m) -> {
            m.put("item", e.getNewItem());
            m.put("old_item", e.getOldItem());
            m.put("slot", e.getSlotType());
        }, null, false, "item", "old_item", "slot").doc("Un jugador se pone o se quita una pieza de armadura."));
        register(TriggerType.event("player_portal", g, PlayerPortalEvent.class, PlayerPortalEvent::getPlayer, (e, m) -> {
            m.put("from", e.getFrom());
            m.put("to", e.getTo());
            m.put("cause", e.getCause());
        }, null, false, "from", "to", "cause").doc("Un jugador usa un portal."));
        register(TriggerType.event("player_kick", g, PlayerKickEvent.class, PlayerKickEvent::getPlayer, (e, m) -> m.put("reason", plain(e.reason())), null, false, "reason").doc("Un jugador es expulsado del servidor."));
        register(TriggerType.event("player_resource_pack", g, PlayerResourcePackStatusEvent.class, PlayerResourcePackStatusEvent::getPlayer, (e, m) -> m.put("status", e.getStatus()), null, false, "status").doc("Un jugador responde al paquete de recursos (status)."));
        // Spec 015, H5: los eventos de ConditionalEvents que faltaban.
        register(TriggerType.event("player_statistic", g, org.bukkit.event.player.PlayerStatisticIncrementEvent.class, org.bukkit.event.player.PlayerStatisticIncrementEvent::getPlayer, (e, m) -> {
            m.put("statistic", e.getStatistic().name());
            m.put("previous_value", e.getPreviousValue());
            m.put("new_value", e.getNewValue());
            m.put("material", e.getMaterial() == null ? null : e.getMaterial().name());
            m.put("entity_type", e.getEntityType() == null ? null : e.getEntityType().name());
        }, null, false, "statistic", "previous_value", "new_value", "material", "entity_type")
                .doc("Sube una estadística del jugador (statistic: JUMP, MOB_KILLS…; previous_value, new_value; material o entity_type si la estadística los tiene). Algunas, como caminar, suben muy seguido."));
        register(TriggerType.event("player_air_change", g, org.bukkit.event.entity.EntityAirChangeEvent.class, e -> (Player) e.getEntity(), (e, m) -> {
            Player p = (Player) e.getEntity();
            m.put("old_air", p.getRemainingAir());
            m.put("new_air", e.getAmount());
            m.put("max_air", p.getMaximumAir());
        }, e -> e.getEntity() instanceof Player, false, "old_air", "new_air", "max_air")
                .doc("Cambia el aire de un jugador (old_air, new_air, max_air, en ticks: 300 es lleno). Bajo el agua corre en cada tick."));
        register(TriggerType.event("player_tab_complete", g, org.bukkit.event.server.TabCompleteEvent.class, e -> (Player) e.getSender(), (e, m) -> {
            String buffer = e.getBuffer();
            m.put("buffer", buffer);
            m.put("cmd", command(buffer));
            String rest = buffer.startsWith("/") ? buffer.substring(1) : buffer;
            int sp = rest.indexOf(' ');
            m.put("args", sp < 0 ? new ArrayList<Object>() : new ArrayList<Object>(Arrays.asList(rest.substring(sp + 1).split(" ", -1))));
            m.put("last", dev.leo.omnievents.action.TabCompletions.last(buffer));
            m.put("completions", new ArrayList<Object>(e.getCompletions()));
        }, e -> e.getSender() instanceof Player && e.getBuffer().startsWith("/"), false, "buffer", "cmd", "args", "last", "completions")
                .doc("Un jugador pide autocompletar un comando de un plugin (buffer: lo escrito; cmd, args; last: la palabra a medio escribir; completions: las sugerencias). La acción tab_complete las cambia. Los comandos de Minecraft los completa el cliente: no pasan por aquí."));
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
        }, e -> e.getEntity() instanceof Player, false, "cause", "damage", "damager").doc("Un jugador recibe daño (cause; damager si fue una entidad o un jugador, también con proyectil)."));
        register(TriggerType.event("player_attack", g, EntityDamageByEntityEvent.class, e -> attacker(e.getDamager()), (e, m) -> {
            m.put("target", e.getEntity());
            m.put("damage", e.getDamage());
            m.put("projectile", e.getDamager() instanceof Projectile ? e.getDamager() : null);
        }, e -> attacker(e.getDamager()) != null, false, "target", "damage", "projectile", "item").doc("Un jugador golpea a una entidad, directo o con un proyectil (target, damage)."));
        register(TriggerType.event("player_kill", g, EntityDeathEvent.class, e -> e.getEntity().getKiller(), (e, m) -> m.put("target", e.getEntity()),
                e -> e.getEntity().getKiller() != null, false, "target", "item").doc("Un jugador mata a una entidad (target)."));
        register(TriggerType.event("player_shoot", g, EntityShootBowEvent.class, e -> (Player) e.getEntity(), (e, m) -> {
            m.put("item", e.getBow());
            m.put("projectile", e.getProjectile());
            m.put("force", e.getForce());
        }, e -> e.getEntity() instanceof Player, false, "item", "projectile", "force").doc("Un jugador dispara un arco o una ballesta (force)."));
        register(TriggerType.event("projectile_hit", g, ProjectileHitEvent.class, e -> (Player) e.getEntity().getShooter(), (e, m) -> {
            m.put("target", e.getHitEntity());
            m.put("block", e.getHitBlock());
            m.put("projectile", e.getEntity());
        }, e -> e.getEntity().getShooter() instanceof Player, false, "target", "block", "projectile").doc("El proyectil de un jugador pega en una entidad o un bloque."));
    }

    private void blocks() {
        String g = "bloques";
        register(TriggerType.event("block_break", g, BlockBreakEvent.class, BlockBreakEvent::getPlayer, (e, m) -> m.put("block", e.getBlock()), null, false, "block", "item").doc("Un jugador rompe un bloque."));
        register(TriggerType.event("block_place", g, BlockPlaceEvent.class, BlockPlaceEvent::getPlayer, (e, m) -> {
            m.put("block", e.getBlockPlaced());
            m.put("against", e.getBlockAgainst());
            m.put("item", e.getItemInHand());
        }, null, false, "block", "against", "item").doc("Un jugador coloca un bloque (against: el bloque en que se apoya)."));
        register(TriggerType.event("block_interact", g, PlayerInteractEvent.class, PlayerInteractEvent::getPlayer, (e, m) -> {
            m.put("block", e.getClickedBlock());
            m.put("action", clickAction(e));
            m.put("face", e.getBlockFace());
            if (e.getItem() != null) m.put("item", e.getItem());
        }, e -> e.getClickedBlock() != null && mainHand(e.getHand()), false, "block", "action", "face", "item").doc("Un jugador hace clic en un bloque con la mano principal (action: RIGHT_CLICK, SHIFT_LEFT_CLICK…)."));
        register(TriggerType.event("sign_change", g, SignChangeEvent.class, SignChangeEvent::getPlayer, (e, m) -> {
            List<Object> lines = new ArrayList<>();
            for (net.kyori.adventure.text.Component c : e.lines()) lines.add(plain(c));
            m.put("lines", lines);
            m.put("block", e.getBlock());
        }, null, false, "lines", "block").doc("Un jugador escribe un cartel (lines)."));
        register(TriggerType.event("bucket_fill", g, PlayerBucketFillEvent.class, PlayerBucketFillEvent::getPlayer, (e, m) -> {
            m.put("block", e.getBlockClicked());
            m.put("item", e.getItemStack());
        }, null, false, "block", "item").doc("Un jugador llena un cubo."));
        register(TriggerType.event("bucket_empty", g, PlayerBucketEmptyEvent.class, PlayerBucketEmptyEvent::getPlayer, (e, m) -> {
            m.put("block", e.getBlockClicked());
            m.put("bucket", e.getBucket());
        }, null, false, "block", "bucket").doc("Un jugador vacía un cubo."));
    }

    private void items() {
        String g = "ítems";
        register(TriggerType.event("player_interact", g, PlayerInteractEvent.class, PlayerInteractEvent::getPlayer, (e, m) -> {
            m.put("action", clickAction(e));
            m.put("block", e.getClickedBlock());
            if (e.getItem() != null) m.put("item", e.getItem());
        }, e -> mainHand(e.getHand()), false, "action", "block", "item").doc("Un jugador hace clic, con o sin bloque, con la mano principal (action)."));
        register(TriggerType.event("item_interact", g, PlayerInteractEvent.class, PlayerInteractEvent::getPlayer, (e, m) -> {
            m.put("item", e.getItem());
            m.put("action", clickAction(e));
            m.put("block", e.getClickedBlock());
        }, e -> e.getItem() != null && mainHand(e.getHand()) && e.getAction() != Action.PHYSICAL, false, "item", "action", "block").doc("Un jugador hace clic con un ítem en la mano principal (action)."));
        register(TriggerType.event("item_consume", g, PlayerItemConsumeEvent.class, PlayerItemConsumeEvent::getPlayer, (e, m) -> m.put("item", e.getItem()), null, false, "item").doc("Un jugador come o bebe un ítem."));
        register(TriggerType.event("item_craft", g, CraftItemEvent.class, e -> (Player) e.getWhoClicked(), (e, m) -> {
            m.put("item", e.getRecipe().getResult());
            m.put("click", e.getClick());
        }, e -> e.getWhoClicked() instanceof Player, false, "item", "click").doc("Un jugador fabrica un ítem (item: el resultado)."));
        register(TriggerType.event("item_drop", g, PlayerDropItemEvent.class, PlayerDropItemEvent::getPlayer, (e, m) -> {
            m.put("item", e.getItemDrop().getItemStack());
            m.put("entity", e.getItemDrop());
        }, null, false, "item", "entity").doc("Un jugador tira un ítem."));
        register(TriggerType.event("item_pickup", g, EntityPickupItemEvent.class, e -> (Player) e.getEntity(), (e, m) -> {
            m.put("item", e.getItem().getItemStack());
            m.put("entity", e.getItem());
        }, e -> e.getEntity() instanceof Player, false, "item", "entity").doc("Un jugador recoge un ítem del suelo."));
        register(TriggerType.event("item_held", g, PlayerItemHeldEvent.class, PlayerItemHeldEvent::getPlayer, (e, m) -> {
            m.put("item", e.getPlayer().getInventory().getItem(e.getNewSlot()));
            m.put("previous_item", e.getPlayer().getInventory().getItem(e.getPreviousSlot()));
            m.put("slot", e.getNewSlot());
        }, null, false, "item", "previous_item", "slot").doc("Un jugador cambia de ranura en la barra (item: el nuevo, previous_item: el anterior)."));
        register(TriggerType.event("item_enchant", g, EnchantItemEvent.class, EnchantItemEvent::getEnchanter, (e, m) -> {
            m.put("item", e.getItem());
            Map<String, Object> ench = new LinkedHashMap<>();
            e.getEnchantsToAdd().forEach((k, v) -> ench.put(k.getKey().getKey(), v));
            m.put("enchants", ench);
            m.put("cost", e.getExpLevelCost());
        }, null, false, "item", "enchants", "cost").doc("Un jugador encanta un ítem en la mesa (enchants, cost)."));
        // Spec 014, H3: el evento item_repair de ConditionalEvents (yunque y afiladora, al sacar el resultado).
        register(TriggerType.event("item_repair", g, InventoryClickEvent.class, e -> (Player) e.getWhoClicked(), (e, m) -> {
            boolean anvil = e.getView() instanceof AnvilView;
            org.bukkit.inventory.Inventory top = e.getView().getTopInventory();
            m.put("item", e.getCurrentItem());
            m.put("repair_type", anvil ? "ANVIL" : "GRINDSTONE");
            m.put("base_item", top.getItem(0));
            m.put("addition", top.getItem(1));
            String rename = anvil ? ((AnvilView) e.getView()).getRenameText() : null;
            m.put("rename_text", rename == null ? "" : rename);
            m.put("level_cost", anvil ? ((AnvilView) e.getView()).getRepairCost() : 0);
        }, Triggers::takesRepairResult, false, "item", "repair_type", "base_item", "addition", "rename_text", "level_cost")
                .doc("Un jugador saca el resultado de un yunque o una afiladora (repair_type: ANVIL o GRINDSTONE; rename_text, level_cost, base_item, addition)."));
        register(TriggerType.event("item_mend", g, org.bukkit.event.player.PlayerItemMendEvent.class, org.bukkit.event.player.PlayerItemMendEvent::getPlayer, (e, m) -> {
            m.put("item", e.getItem());
            m.put("amount", e.getRepairAmount());
            m.put("orb", e.getExperienceOrb());
        }, null, false, "item", "amount", "orb")
                .doc("Mending repara un ítem del jugador con un orbe de experiencia (spec 015; amount: durabilidad que repara, orb). cancel hace que el orbe dé experiencia en vez de reparar."));
        register(TriggerType.event("item_break", g,PlayerItemBreakEvent.class, PlayerItemBreakEvent::getPlayer, (e, m) -> m.put("item", e.getBrokenItem()), null, false, "item").doc("Se rompe la herramienta o la armadura de un jugador."));
    }

    private void inventory() {
        String g = "inventario";
        register(TriggerType.event("inventory_open", g, InventoryOpenEvent.class, e -> (Player) e.getPlayer(), (e, m) -> {
            m.put("title", plain(e.getView().title()));
            m.put("inventory_type", e.getInventory().getType());
        }, e -> e.getPlayer() instanceof Player, false, "title", "inventory_type").doc("Un jugador abre un inventario (title, inventory_type)."));
        register(TriggerType.event("inventory_close", g, InventoryCloseEvent.class, e -> (Player) e.getPlayer(), (e, m) -> {
            m.put("title", plain(e.getView().title()));
            m.put("inventory_type", e.getInventory().getType());
        }, e -> e.getPlayer() instanceof Player, false, "title", "inventory_type").doc("Un jugador cierra un inventario."));
        register(TriggerType.event("inventory_click", g, InventoryClickEvent.class, e -> (Player) e.getWhoClicked(), (e, m) -> {
            m.put("item", e.getCurrentItem());
            m.put("cursor", e.getCursor());
            m.put("slot", e.getSlot());
            m.put("raw_slot", e.getRawSlot());
            m.put("click", e.getClick());
            m.put("title", plain(e.getView().title()));
            m.put("inventory_type", e.getView().getTopInventory().getType());
        }, e -> e.getWhoClicked() instanceof Player, false, "item", "cursor", "slot", "raw_slot", "click", "title", "inventory_type").doc("Un jugador hace clic en un inventario (slot, raw_slot, click, cursor, title)."));
    }

    private void entities() {
        String g = "entidades";
        register(TriggerType.event("entity_interact", g, PlayerInteractEntityEvent.class, PlayerInteractEntityEvent::getPlayer, (e, m) -> m.put("target", e.getRightClicked()),
                e -> mainHand(e.getHand()), false, "target", "item").doc("Un jugador hace clic derecho en una entidad (target)."));
        register(TriggerType.event("entity_spawn", g, CreatureSpawnEvent.class, null, (e, m) -> {
            m.put("entity", e.getEntity());
            m.put("reason", e.getSpawnReason());
            m.put("world", e.getEntity().getWorld());
            m.put("location", e.getLocation());
        }, null, false, "entity", "reason").doc("Aparece una criatura (entity, reason); sin jugador."));
        register(TriggerType.event("entity_death", g, EntityDeathEvent.class, e -> e.getEntity().getKiller(), (e, m) -> {
            m.put("entity", e.getEntity());
            m.put("killer", e.getEntity().getKiller());
            m.put("world", e.getEntity().getWorld());
            m.put("location", e.getEntity().getLocation());
        }, null, false, "entity", "killer").doc("Muere una entidad (entity; player y killer si la mató un jugador)."));
    }

    private void server() {
        String g = "servidor";
        register(TriggerType.event("console_command", g, ServerCommandEvent.class, null, (e, m) -> {
            Map<String, Object> cmd = command(e.getCommand());
            m.put("cmd", cmd);
            m.put("args", cmd.get("args"));
            m.put("sender", e.getSender());
        }, null, false, "cmd", "args", "sender").doc("La consola ejecuta un comando (cmd, args, sender)."));
        register(TriggerType.special("server_start", TriggerType.Kind.SERVER_START, g).doc("Al arrancar el servidor, cuando OmniEvents termina de cargar."));
        register(TriggerType.special("server_stop", TriggerType.Kind.SERVER_STOP, g).doc("El servidor se apaga."));
        register(TriggerType.special("timer", TriggerType.Kind.TIMER, g, "player", "world", "location").doc("Cada cierto tiempo (every); por defecto una vez por jugador conectado (per_player)."));
        register(TriggerType.special("clock", TriggerType.Kind.CLOCK, g, "player", "world", "location", "time").doc("A una hora del día (at: HH:mm, timezone)."));
        register(TriggerType.special("call", TriggerType.Kind.CALL, g, "player", "event", "world", "location", "sender", "caller").doc("Solo corre cuando otra regla la llama (call) o con /omni call; sus args llegan como var.<nombre>."));
        register(TriggerType.special("command", TriggerType.Kind.COMMAND, g, "player", "sender", "cmd", "args", "world", "location").doc("Registra un comando real (command: name, aliases, permission, tab) y corre al usarlo."));
        register(TriggerType.special("state", TriggerType.Kind.STATE, g, "player", "world", "location").doc("Cuando la expresión de when pasa a ser verdad (on_enter) o deja de serlo (on_exit), por jugador; sin sondeo: se revisa solo con los eventos de los que depende."));
        register(TriggerType.special("var_expire", TriggerType.Kind.VAR_EXPIRE, g, "player", "world", "location", "variable", "value", "scope").doc("Caduca una variable con expire (spec 009): una pvar de un jugador conectado (con player) o una gvar. variable: el nombre; value: lo último que valía; scope: pvar o gvar."));
        register(TriggerType.special("custom", TriggerType.Kind.CUSTOM, g, "player", "event", "world", "location").doc("Cualquier evento de Bukkit o de otro plugin por su clase (event, player_from)."));
    }

    /** Raíces comunes que cualquier contexto ofrece. */
    public static final List<String> COMMON_ROOTS = List.of("server", "rule", "online", "source", "target", "item", "block", "entity");

    static Block blockOf(Object o) {
        return o instanceof Block b ? b : null;
    }
    /**
     * Spec 013: las reglas que compila un menú (apertura, clic y cierre). No escuchan eventos: las corre el menú; una
     * regla suelta con estos disparadores es un error al cargar.
     */
    private void menus() {
        String g = "menús";
        register(TriggerType.special("menu_open", TriggerType.Kind.MENU, g, "player", "world", "location", "menu").doc("Abrir un menú (open_menu): su permission, conditions y cost son el requisito y on_open las acciones. Lo compila el menú; no se escribe a mano."));
        register(TriggerType.special("menu_click", TriggerType.Kind.MENU, g, "player", "world", "location", "menu", "slot", "click", "item").doc("Un clic en un ítem de un menú (click, left_click, right_click, shift_click). click: left, right, shift_left, shift_right, middle, number, drop o bedrock. Lo compila el menú; no se escribe a mano."));
        register(TriggerType.special("menu_close", TriggerType.Kind.MENU, g, "player", "world", "location", "menu").doc("Cerrar un menú (on_close). Lo compila el menú; no se escribe a mano."));
    }

    /** Spec 012: WorldGuard y Citizens. Se registran siempre; sin el plugin, la regla avisa al cargar y nunca corre. */
    private void integrations() {
        String g = "integraciones";
        register(TriggerType.special("region_enter", TriggerType.Kind.REGION, g, "player", "world", "location", "region", "event").doc("El jugador entra a una región de WorldGuard (region.id, region.priority); cancel impide la entrada. Necesita WorldGuard."));
        register(TriggerType.special("region_leave", TriggerType.Kind.REGION, g, "player", "world", "location", "region", "event").doc("El jugador sale de una región de WorldGuard (region.id); cancel lo deja dentro. Necesita WorldGuard."));
        register(TriggerType.event("npc_click", g, PlayerInteractEntityEvent.class, PlayerInteractEntityEvent::getPlayer, (e, m) -> {
            m.put("target", e.getRightClicked());
            m.put("npc", Npcs.info(e.getRightClicked()));
        }, e -> mainHand(e.getHand()) && Npcs.isNpc(e.getRightClicked()), false, "npc", "target", "item").doc("Un jugador hace clic derecho en un NPC de Citizens (npc.id, npc.name, npc.entity). Necesita Citizens."));
        register(TriggerType.event("npc_left_click", g, EntityDamageByEntityEvent.class, e -> (Player) e.getDamager(), (e, m) -> {
            m.put("target", e.getEntity());
            m.put("npc", Npcs.info(e.getEntity()));
        }, e -> e.getDamager() instanceof Player && Npcs.isNpc(e.getEntity()), false, "npc", "target", "item").doc("Un jugador golpea (clic izquierdo) un NPC de Citizens (npc.id, npc.name, npc.entity). Necesita Citizens."));
        // Spec 016: lo que recibe el jugador en el chat, leído de los paquetes con ProtocolLib (hook.ChatPackets).
        register(TriggerType.event("player_receive_chat", g, ReceivedChatEvent.class, ReceivedChatEvent::getPlayer, (e, m) -> {
            m.put("message", e.getMessage());
            m.put("message_legacy", e.getLegacy());
            m.put("json", e.getJson());
            m.put("type", e.getType());
        }, null, true, "message", "message_legacy", "json", "type").doc("Un jugador va a recibir un mensaje del chat o del sistema (asíncrono, en el hilo de red): message sin formato, message_legacy con códigos &, json, type (SYSTEM, DISGUISED o PLAYER). cancel hace que no lo vea; la barra de acción no cuenta. Es texto no confiable. Necesita ProtocolLib."));
    }
}
