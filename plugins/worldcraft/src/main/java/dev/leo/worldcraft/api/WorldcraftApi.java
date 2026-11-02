package dev.leo.worldcraft.api;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.util.BlockVector;

import java.util.Optional;

/**
 * Worldcraft's API for other plugins (spec 037, ADR 0008). Only this package and {@code dev.leo.worldcraft.api.event}
 * are the contract; everything else in Worldcraft may change in any release.
 *
 * <p>Every edit goes through the same engine as the {@code /wc} commands: the limits of the player (or of
 * {@code api.plugin_groups} for a plugin), a few milliseconds per tick, the undo history on disk and the
 * {@link dev.leo.worldcraft.api.event.WorldcraftEditEvent} that a protection plugin can cancel.
 *
 * <pre>{@code
 * WorldcraftApi wc = WorldcraftApi.get();
 * wc.edit(this).in(world, new BlockVector(0, 60, 0), new BlockVector(20, 70, 20)).set("air")
 *   .thenAccept(r -> getLogger().info("Arena reset: " + r.changed() + " blocks"));
 * }</pre>
 */
public interface WorldcraftApi {
    /** The version of this contract. A new method keeps it; a changed or removed one raises it. */
    int API_VERSION = 1;

    /**
     * The API of the running Worldcraft.
     *
     * @throws IllegalStateException if Worldcraft is not installed or not enabled
     */
    static WorldcraftApi get() {
        RegisteredServiceProvider<WorldcraftApi> rsp = Bukkit.getServicesManager().getRegistration(WorldcraftApi.class);
        if (rsp == null) throw new IllegalStateException("Worldcraft is not enabled on this server: add depend: [Worldcraft] to your plugin.yml");
        return rsp.getProvider();
    }

    /** {@link #API_VERSION} of the running Worldcraft. */
    int apiVersion();

    /** Worldcraft's version, like "0.13.0". */
    String pluginVersion();

    /** The player's selection (any shape), or empty if it is not complete. Main thread only. */
    Optional<Selection> selection(Player player);

    /** Selects a box for the player, as if they had used /wc pos1 and /wc pos2 (they see its outline). Main thread only. */
    void select(Player player, World world, BlockVector corner1, BlockVector corner2);

    /** Forgets the player's selection. Main thread only. */
    void clearSelection(Player player);

    /**
     * Starts an edit asked by {@code plugin}. Without {@link EditBuilder#as(Player)} it is the plugin's own edit: it is
     * kept in the history as "#PluginName" and uses the limit groups the server owner gives plugins.
     */
    EditBuilder edit(Plugin plugin);
}
