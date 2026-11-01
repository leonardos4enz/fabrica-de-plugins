package dev.leo.skinstatues.statue;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Los chunks que ocupa una estatua (spec 022 H3 CA6, plan §2.3): se cargan (o generan) sin bloquear el hilo principal
 * con getChunkAtAsync y se sostienen con un ticket del plugin hasta soltarlos. Así poner o quitar bloques lejos de todo
 * jugador nunca carga un chunk dentro del tick, ni se descarga uno a mitad de obra.
 */
final class ChunkHold {
    private final Plugin plugin;
    private final World world;
    private final List<long[]> held = new ArrayList<>();
    private int pending;
    private boolean released;

    private ChunkHold(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    /** Pide los chunks del rectángulo de bloques [minX, maxX] × [minZ, maxZ]. Se llama en el hilo principal. */
    static ChunkHold acquire(Plugin plugin, World world, int minX, int maxX, int minZ, int maxZ) {
        ChunkHold h = new ChunkHold(plugin, world);
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                int x = cx, z = cz;
                h.pending++;
                // Paper completa el futuro en el hilo principal.
                world.getChunkAtAsync(x, z).whenComplete((chunk, error) -> {
                    if (error == null && !h.released) {
                        world.addPluginChunkTicket(x, z, plugin);
                        h.held.add(new long[]{x, z});
                    }
                    h.pending--;
                });
            }
        }
        return h;
    }

    /** Todos los chunks están cargados (o fallaron: entonces getBlockAt los carga como siempre). */
    boolean ready() {
        return pending <= 0;
    }

    int chunks() {
        return held.size();
    }

    /** Quita los tickets; los chunks que lleguen después ya no se sostienen. */
    void release() {
        if (released) return;
        released = true;
        for (long[] c : held) world.removePluginChunkTicket((int) c[0], (int) c[1], plugin);
        held.clear();
    }
}
