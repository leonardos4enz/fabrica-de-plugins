package dev.leo.worldcraft.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

/**
 * Fired by {@code /wc wer} (FAWE's {@code //wer}): a player asks to select the area where they may build. A protection
 * plugin answers with {@link #setRegion} (the first answer wins); if nobody answers, Worldcraft says so. Main thread.
 * Since 0.16.0.
 */
public class WorldcraftAllowedRegionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Location location;
    private int[] min, max;
    private Plugin answeredBy;
    private String name = "";

    public WorldcraftAllowedRegionEvent(Player player, Location location) {
        this.player = player;
        this.location = location.clone();
    }

    public Player getPlayer() {
        return player;
    }

    /** Where the player stands. */
    public Location getLocation() {
        return location.clone();
    }

    /** Answers with a box (both corners included) and a name to show ("plot 12"); ignored if another plugin answered. */
    public void setRegion(Plugin from, int[] min, int[] max, String name) {
        if (answeredBy != null) return;
        this.answeredBy = from;
        this.min = new int[]{Math.min(min[0], max[0]), Math.min(min[1], max[1]), Math.min(min[2], max[2])};
        this.max = new int[]{Math.max(min[0], max[0]), Math.max(min[1], max[1]), Math.max(min[2], max[2])};
        this.name = name == null ? "" : name;
    }

    public boolean hasRegion() {
        return answeredBy != null;
    }

    public int[] getMin() {
        return min == null ? null : min.clone();
    }

    public int[] getMax() {
        return max == null ? null : max.clone();
    }

    public Plugin getAnsweredBy() {
        return answeredBy;
    }

    public String getRegionName() {
        return name;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
