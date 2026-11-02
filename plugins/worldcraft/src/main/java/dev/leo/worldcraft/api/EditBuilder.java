package dev.leo.worldcraft.api;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

import java.util.concurrent.CompletableFuture;

/**
 * One edit to ask Worldcraft for. Say where ({@link #in}), optionally for whom ({@link #as}) and which blocks may change
 * ({@link #mask}), then run it with {@link #set}, {@link #replace}, {@link #paste} or {@link #undo}.
 *
 * <p>The future completes on the main thread when the edit ends. A pattern, mask or schematic that does not exist
 * completes it exceptionally with an {@link IllegalArgumentException} (the same message as the commands); limits,
 * a cancelled event or the API turned off give a {@link EditResult.Status#REFUSED} result with the reason.
 * Any thread may run an edit; it starts on the main thread.
 */
public interface EditBuilder {
    /** The edit is the player's: their limits, their history (/wc undo undoes it) and their chat messages. */
    EditBuilder as(Player player);

    /** A box, from one corner to the other (any order). */
    EditBuilder in(World world, BlockVector corner1, BlockVector corner2);

    /** A selection of any shape (from {@link WorldcraftApi#selection}). */
    EditBuilder in(Selection selection);

    /** Only blocks that match this mask change, like "stone, #dirt". Masks that look around (above, exposed) are not supported. */
    EditBuilder mask(String mask);

    /** Fills the place with a pattern, like "stone" or "50% stone, 50% andesite". */
    CompletableFuture<EditResult> set(String pattern);

    /** Replaces what matches the mask with the pattern (also limited by {@link #mask}, if given). */
    CompletableFuture<EditResult> replace(String mask, String pattern);

    /**
     * Pastes a schematic from Worldcraft's schematics folder (or the WorldEdit/FAWE folders the owner added) with its
     * origin at {@code at}, turned {@code rotation} degrees (a multiple of 90). {@link #in} is not used.
     *
     * @param air true to paste the schematic's air too (it clears what is there)
     */
    CompletableFuture<EditResult> paste(String schematic, World world, BlockVector at, int rotation, boolean air);

    /** Undoes the last {@code count} edits of the player ({@link #as}) or of the plugin. */
    CompletableFuture<EditResult> undo(int count);
}
