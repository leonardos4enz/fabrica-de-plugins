package dev.leo.blockwright.paper;

import dev.leo.blockwright.edit.EditJob;
import io.papermc.paper.math.Position;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La vista previa (H4 CA2): cada tanda de cambios se manda solo a ese jugador como bloques falsos (el mundo no cambia
 * y los demás no ven nada). Recuerda las posiciones para devolverle los bloques reales al confirmar, cancelar o vencer.
 */
public final class PreviewSink implements EditJob.Sink {
    private final Player player;
    private final World world;
    private final PaperBlocks blocks;
    private final Map<Position, BlockData> batch = new HashMap<>();
    private final List<long[]> shown = new ArrayList<>();
    private boolean reverted;

    public PreviewSink(Player player, World world, PaperBlocks blocks) {
        this.player = player;
        this.world = world;
        this.blocks = blocks;
    }

    @Override
    public void changed(int x, int y, int z, int before, int after, byte[] tb, byte[] ta) {
        batch.put(Position.block(x, y, z), blocks.data(after));
        shown.add(new long[]{x, y, z});
    }

    @Override
    public void flush() {
        if (batch.isEmpty() || reverted) return;
        if (player.isOnline() && player.getWorld().equals(world)) player.sendMultiBlockChange(new HashMap<>(batch));
        batch.clear();
    }

    public int shown() {
        return shown.size();
    }

    /** Le devuelve al jugador los bloques reales donde vio bloques falsos. */
    public void revert() {
        if (reverted) return;
        reverted = true;
        batch.clear();
        if (!player.isOnline() || !player.getWorld().equals(world)) return;
        Map<Position, BlockData> real = new HashMap<>();
        for (long[] p : shown) {
            int x = (int) p[0], y = (int) p[1], z = (int) p[2];
            real.put(Position.block(x, y, z), world.getBlockData(x, y, z));
            if (real.size() >= 4096) {
                player.sendMultiBlockChange(real);
                real = new HashMap<>();
            }
        }
        if (!real.isEmpty()) player.sendMultiBlockChange(real);
        shown.clear();
    }
}
