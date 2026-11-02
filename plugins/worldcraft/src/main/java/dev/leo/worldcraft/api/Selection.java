package dev.leo.worldcraft.api;

import org.bukkit.World;
import org.bukkit.util.BlockVector;

/** A player's selection at one moment: it does not change if the player selects something else later. */
public interface Selection {
    World world();

    /** "cuboid", "poly", "ellipsoid", "sphere", "cyl", "convex" or "magic". */
    String shape();

    /** The smallest corner of the box around the selection. */
    BlockVector min();

    /** The biggest corner of the box around the selection. */
    BlockVector max();

    /** Blocks really inside (not the whole box around it). */
    long volume();

    boolean contains(int x, int y, int z);
}
