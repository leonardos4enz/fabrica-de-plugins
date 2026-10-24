package dev.leo.omnievents.expr;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Alias legibles para los tipos de Bukkit más usados (H3 CA1–CA2). El resto sale por reflexión de lectura. */
public final class BukkitProperties {
    private BukkitProperties() {
    }

    public static void register(Properties p) {
        p.alias(CommandSender.class, "name", CommandSender::getName);

        p.alias(Entity.class, "type", Entity::getType);
        p.alias(Entity.class, "uuid", e -> e.getUniqueId().toString());
        p.alias(Entity.class, "world", Entity::getWorld);
        p.alias(Entity.class, "location", Entity::getLocation);
        p.alias(Entity.class, "x", e -> e.getLocation().getX());
        p.alias(Entity.class, "y", e -> e.getLocation().getY());
        p.alias(Entity.class, "z", e -> e.getLocation().getZ());
        p.alias(Entity.class, "block_x", e -> (double) e.getLocation().getBlockX());
        p.alias(Entity.class, "block_y", e -> (double) e.getLocation().getBlockY());
        p.alias(Entity.class, "block_z", e -> (double) e.getLocation().getBlockZ());
        p.alias(Entity.class, "custom_name", e -> e.customName());
        p.alias(Entity.class, "is_player", e -> e instanceof Player);
        p.alias(LivingEntity.class, "max_health", e -> maxHealth(e));
        p.alias(LivingEntity.class, "alive", e -> !e.isDead());

        p.alias(Player.class, "displayname", Player::displayName);
        p.alias(Player.class, "food", Player::getFoodLevel);
        p.alias(Player.class, "item", e -> e.getInventory().getItemInMainHand());
        p.alias(Player.class, "offhand", e -> e.getInventory().getItemInOffHand());
        p.alias(Player.class, "helmet", e -> e.getInventory().getHelmet());
        p.alias(Player.class, "chestplate", e -> e.getInventory().getChestplate());
        p.alias(Player.class, "leggings", e -> e.getInventory().getLeggings());
        p.alias(Player.class, "boots", e -> e.getInventory().getBoots());
        p.alias(Player.class, "op", Player::isOp);
        p.alias(Player.class, "ip", e -> e.getAddress() == null ? null : e.getAddress().getAddress().getHostAddress());
        p.alias(Player.class, "first_join", e -> !e.hasPlayedBefore());
        p.alias(Player.class, "online", Player::isOnline);
        p.alias(Player.class, "ping", Player::getPing);
        p.alias(Player.class, "locale", e -> e.locale().toString());
        p.alias(Player.class, "empty_slots", e -> {
            int n = 0;
            for (ItemStack it : e.getInventory().getStorageContents()) if (it == null || it.getType().isAir()) n++;
            return (double) n;
        });

        p.alias(Location.class, "world", Location::getWorld);
        p.alias(Location.class, "block", Location::getBlock);
        p.alias(Location.class, "block_x", l -> (double) l.getBlockX());
        p.alias(Location.class, "block_y", l -> (double) l.getBlockY());
        p.alias(Location.class, "block_z", l -> (double) l.getBlockZ());
        p.alias(Location.class, "biome", l -> l.getBlock().getBiome());

        p.alias(Block.class, "type", Block::getType);
        p.alias(Block.class, "world", Block::getWorld);
        p.alias(Block.class, "location", Block::getLocation);
        p.alias(Block.class, "x", b -> (double) b.getX());
        p.alias(Block.class, "y", b -> (double) b.getY());
        p.alias(Block.class, "z", b -> (double) b.getZ());
        p.alias(Block.class, "light", b -> (double) b.getLightLevel());
        p.alias(Block.class, "data", b -> b.getBlockData().getAsString());
        p.alias(Block.class, "powered", Block::isBlockPowered);

        p.alias(ItemStack.class, "type", ItemStack::getType);
        p.alias(ItemStack.class, "amount", i -> (double) i.getAmount());
        p.alias(ItemStack.class, "air", i -> i.getType().isAir());
        p.alias(ItemStack.class, "is_air", i -> i.getType().isAir());
        p.alias(ItemStack.class, "name", i -> {
            ItemMeta m = i.getItemMeta();
            return m != null && m.hasDisplayName() ? m.displayName() : "";
        });
        p.alias(ItemStack.class, "has_name", i -> i.getItemMeta() != null && i.getItemMeta().hasDisplayName());
        p.alias(ItemStack.class, "lore", i -> {
            List<Object> out = new ArrayList<>();
            ItemMeta m = i.getItemMeta();
            if (m != null && m.lore() != null) for (var c : m.lore()) out.add(Values.normalize(c));
            return out;
        });
        p.alias(ItemStack.class, "model", BukkitProperties::model);
        p.alias(ItemStack.class, "enchants", i -> {
            Map<String, Object> out = new LinkedHashMap<>();
            i.getEnchantments().forEach((e, lvl) -> out.put(e.getKey().getKey(), (double) lvl));
            return out;
        });
        p.alias(ItemStack.class, "durability", i -> i.getItemMeta() instanceof Damageable d ? (double) (i.getType().getMaxDurability() - d.getDamage()) : 0.0);
        p.alias(ItemStack.class, "max_durability", i -> (double) i.getType().getMaxDurability());
        p.alias(ItemStack.class, "unbreakable", i -> i.getItemMeta() != null && i.getItemMeta().isUnbreakable());

        p.alias(World.class, "players", w -> (double) w.getPlayers().size());
        p.alias(World.class, "day", w -> w.getTime() < 12300 || w.getTime() > 23850);
        p.alias(World.class, "storm", World::hasStorm);
        p.alias(World.class, "thunder", World::isThundering);
    }

    private static double maxHealth(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20 : a.getValue();
    }

    @SuppressWarnings("deprecation")
    private static Object model(ItemStack i) {
        ItemMeta m = i.getItemMeta();
        return m != null && m.hasCustomModelData() ? (double) m.getCustomModelData() : 0.0;
    }
}
