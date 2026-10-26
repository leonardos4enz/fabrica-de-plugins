package dev.leo.omnievents.action;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Los cohetes de la acción firework no hacen daño (spec 014, H2 CA2): llevan una marca en su PDC y este oyente cancela
 * su daño. Se registra la primera vez que se lanza uno (constitución III.1: sin cohetes, sin oyente).
 */
public final class FireworkGuard implements Listener {
    private final NamespacedKey key;
    private boolean registered;
    private final Plugin plugin;

    public FireworkGuard(Plugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "no_damage");
    }

    /** Marca el cohete como inofensivo y se asegura de que el oyente esté registrado. */
    public void mark(Entity firework) {
        firework.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
    }

    public boolean registered() {
        return registered;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Firework f && f.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) e.setCancelled(true);
    }
}
