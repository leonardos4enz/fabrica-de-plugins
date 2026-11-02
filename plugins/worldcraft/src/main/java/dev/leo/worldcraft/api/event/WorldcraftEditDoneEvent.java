package dev.leo.worldcraft.api.event;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/** After a Worldcraft edit ends, or is stopped halfway (/wc cancel, the server stopping). Not fired for previews. */
public class WorldcraftEditDoneEvent extends EditEventBase {
    private static final HandlerList HANDLERS = new HandlerList();
    private final int id;
    private final long changed;
    private final boolean completed;

    public WorldcraftEditDoneEvent(CommandSender actor, UUID actorId, String actorName, Plugin plugin, String kind, String detail,
                                   World world, int[] min, int[] max, long volume, int id, long changed, boolean completed) {
        super(actor, actorId, actorName, plugin, kind, detail, world, min, max, volume);
        this.id = id;
        this.changed = changed;
        this.completed = completed;
    }

    /** Its number in the owner's history (the one undone, for undo and redo). */
    public int getId() {
        return id;
    }

    /** Blocks it changed. */
    public long getChanged() {
        return changed;
    }

    /** False if it was stopped halfway. */
    public boolean isCompleted() {
        return completed;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
