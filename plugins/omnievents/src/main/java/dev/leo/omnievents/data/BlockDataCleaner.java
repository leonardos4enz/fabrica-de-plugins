package dev.leo.omnievents.data;

import dev.leo.omnievents.rule.Rule;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.List;

/**
 * Borra las variables de un bloque cuando el bloque deja de estar (spec 009, H3 CA3). A prioridad MONITOR, después de
 * las reglas del evento, que aún pueden leerlas. Solo se registra si alguna regla escribe X.data (constitución III.1).
 */
public final class BlockDataCleaner implements Listener {
    private final Plugin plugin;
    private boolean registered;

    public BlockDataCleaner(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Registra o quita los oyentes según las reglas cargadas. */
    public void update(Collection<Rule> rules) {
        boolean need = rules.stream().anyMatch(Rule::writesObjectData);
        if (need && !registered) Bukkit.getPluginManager().registerEvents(this, plugin);
        if (!need && registered) HandlerList.unregisterAll(this);
        registered = need;
    }

    public void unregister() {
        if (registered) HandlerList.unregisterAll(this);
        registered = false;
    }

    public boolean registered() {
        return registered;
    }

    private static void clear(List<Block> blocks) {
        for (Block b : blocks) ObjectData.clearBlock(b);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        ObjectData.clearBlock(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        ObjectData.clearBlock(e.getBlock());
        clear(e.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        clear(e.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        ObjectData.clearBlock(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        ObjectData.clearBlock(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        ObjectData.clearBlock(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        ObjectData.clearBlock(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        clear(e.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        clear(e.getBlocks());
    }
}
