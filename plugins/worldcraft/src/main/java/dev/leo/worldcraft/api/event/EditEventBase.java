package dev.leo.worldcraft.api.event;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BlockVector;

import java.util.UUID;

/** What both Worldcraft events say about an edit. Fired on the main thread. */
public abstract class EditEventBase extends Event {
    private final CommandSender actor;
    private final UUID actorId;
    private final String actorName;
    private final Plugin plugin;
    private final String kind, detail;
    private final World world;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final long volume;

    protected EditEventBase(CommandSender actor, UUID actorId, String actorName, Plugin plugin, String kind, String detail,
                            World world, int[] min, int[] max, long volume) {
        this.actor = actor;
        this.actorId = actorId;
        this.actorName = actorName;
        this.plugin = plugin;
        this.kind = kind;
        this.detail = detail;
        this.world = world;
        this.minX = min[0];
        this.minY = min[1];
        this.minZ = min[2];
        this.maxX = max[0];
        this.maxY = max[1];
        this.maxZ = max[2];
        this.volume = volume;
    }

    /** The player or console that asked; null for a plugin's own edit (see {@link #getPlugin()}). */
    public CommandSender getActor() {
        return actor;
    }

    /** The owner of the edit in the history: a player's UUID, or a fixed one for the console and for each plugin. */
    public UUID getActorId() {
        return actorId;
    }

    /** "Ana", "CONSOLE" or "#PluginName". */
    public String getActorName() {
        return actorName;
    }

    /** The plugin that asked through the API, or null for commands, brushes and tools. */
    public Plugin getPlugin() {
        return plugin;
    }

    /** "set", "replace", "paste", "undo", "redo", "sphere", "brush"… (the command's name). */
    public String getKind() {
        return kind;
    }

    /** The pattern, mask or other details, as /wc history shows them. */
    public String getDetail() {
        return detail;
    }

    public World getWorld() {
        return world;
    }

    /** The smallest corner of the box the edit may change. */
    public BlockVector getMinimum() {
        return new BlockVector(minX, minY, minZ);
    }

    /** The biggest corner of the box the edit may change. */
    public BlockVector getMaximum() {
        return new BlockVector(maxX, maxY, maxZ);
    }

    /** Blocks the edit visits (0 for an edit of only entities or biomes). */
    public long getVolume() {
        return volume;
    }

    /** True if the box the edit may change touches the box from {@code a} to {@code b} (any order) in that world. */
    public boolean overlaps(World world, BlockVector a, BlockVector b) {
        if (world == null || !world.equals(this.world)) return false;
        return overlaps(minX, minY, minZ, maxX, maxY, maxZ, a.getBlockX(), a.getBlockY(), a.getBlockZ(), b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    static boolean overlaps(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int ax, int ay, int az, int bx, int by, int bz) {
        return Math.max(minX, Math.min(ax, bx)) <= Math.min(maxX, Math.max(ax, bx))
                && Math.max(minY, Math.min(ay, by)) <= Math.min(maxY, Math.max(ay, by))
                && Math.max(minZ, Math.min(az, bz)) <= Math.min(maxZ, Math.max(az, bz));
    }
}
