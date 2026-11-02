package dev.leo.worldcraft.api.event;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Before any Worldcraft edit starts: commands, brushes, tools, the API, undo and redo. Fired after Worldcraft's own
 * limits and before the preview or the /wc confirm question. Cancel it and the edit never starts; the one who asked
 * sees "Another plugin stopped this edit" with {@link #setCancelReason your reason}.
 */
public class WorldcraftEditEvent extends EditEventBase implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;
    private String reason = "";

    public WorldcraftEditEvent(CommandSender actor, UUID actorId, String actorName, Plugin plugin, String kind, String detail,
                               World world, int[] min, int[] max, long volume) {
        super(actor, actorId, actorName, plugin, kind, detail, world, min, max, volume);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }

    /** Why it was stopped, shown to the one who asked (and in an API result); empty if none. */
    public String getCancelReason() {
        return reason;
    }

    /** Cancels the edit and says why ("This area is protected"). */
    public void setCancelReason(String reason) {
        this.reason = reason == null ? "" : reason;
        this.cancelled = true;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
