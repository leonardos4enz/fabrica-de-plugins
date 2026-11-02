package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.biome.BiomeWorld;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.region.ChunkOrder;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Los biomas de un mundo de Paper para el motor (plan 033 §1.1): {@code getBiome}/{@code setBiome} por celda, los chunks
 * pedidos sin bloquear y sostenidos con un ticket del plugin mientras la etapa los usa, y {@code refreshChunk} para que
 * los jugadores vean el bioma nuevo. Hilo principal.
 */
public final class PaperBiomes implements BiomeWorld {
    private static final Map<String, Biome> BY_NAME = new HashMap<>();
    private static List<String> names;

    private final Plugin plugin;
    private final World world;
    private final Set<Long> held = new HashSet<>(), loading = new HashSet<>();
    private boolean closed;

    public PaperBiomes(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    /** El bioma del servidor con ese id completo, o null. */
    @SuppressWarnings("deprecation") // Registry.BIOME: la forma de la API 1.21.4 sin pasar por RegistryAccess
    public static Biome biome(String name) {
        return BY_NAME.computeIfAbsent(name, k -> {
            NamespacedKey key = NamespacedKey.fromString(k);
            return key == null ? null : Registry.BIOME.get(key);
        });
    }

    /** Los ids de todos los biomas del servidor (también los de paquetes de datos), ordenados. */
    @SuppressWarnings("deprecation")
    public static synchronized List<String> names() {
        if (names == null) {
            List<String> out = new ArrayList<>();
            for (Biome b : Registry.BIOME) out.add(b.getKey().toString());
            out.sort(null);
            names = List.copyOf(out);
        }
        return names;
    }

    /** El nombre del bioma de una posición. */
    public static String at(World w, int x, int y, int z) {
        return w.getBiome(x, y, z).getKey().toString();
    }

    /** Spec 033 H3: con qué lee el bioma una máscara (en el hilo principal, donde se evalúan). */
    public static Mask.BiomeLookup lookup(World w) {
        return (x, y, z) -> at(w, x, y, z);
    }

    @Override
    public int minY() {
        return world.getMinHeight();
    }

    @Override
    public int maxY() {
        return world.getMaxHeight() - 1;
    }

    @Override
    public String get(int x, int y, int z) {
        return at(world, x, y, z);
    }

    @Override
    public void set(int x, int y, int z, String name) {
        Biome b = biome(name);
        if (b != null) world.setBiome(x, y, z, b);
    }

    @Override
    public boolean ready(int cx, int cz) {
        long key = ChunkOrder.pack(cx, cz);
        if (held.contains(key)) return true;
        if (world.isChunkLoaded(cx, cz)) {
            hold(cx, cz, key);
            return true;
        }
        if (!closed && loading.add(key)) {
            world.getChunkAtAsync(cx, cz).whenComplete((c, err) -> {
                loading.remove(key);
                if (err == null && !closed) hold(cx, cz, key);
            });
        }
        return false;
    }

    private void hold(int cx, int cz, long key) {
        if (held.add(key)) world.addPluginChunkTicket(cx, cz, plugin);
    }

    @Override
    public void refresh(int cx, int cz) {
        world.refreshChunk(cx, cz);
    }

    @Override
    public void close() {
        closed = true;
        for (long k : held) world.removePluginChunkTicket(ChunkOrder.chunkX(k), ChunkOrder.chunkZ(k), plugin);
        held.clear();
    }
}
