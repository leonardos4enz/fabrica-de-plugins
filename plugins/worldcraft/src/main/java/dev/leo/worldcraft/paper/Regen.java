package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.snapshot.TempWorlds;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * El mundo temporal de /wc regen (plan 036 §1.1): una copia del generador de un mundo (con su semilla u otra, y quizá un
 * solo bioma) donde los chunks se generan fuera del hilo del servidor al pedirlos con getChunkAtAsync. Una regen a la vez;
 * el mundo queda cargado regen.keep_seconds por si la siguiente usa el mismo, y después se descarga y se borra. Solo se
 * borra una carpeta con la marca de TempWorlds. Hilo principal.
 */
public final class Regen {
    /** No se pudo crear el mundo temporal; el mensaje va al jugador. */
    public static final class Problem extends Exception {
        public Problem(String message) {
            super(message);
        }
    }

    private final Worldcraft plugin;
    private World temp;
    private String key;
    private String busyWho;
    private BukkitTask unload;
    private int counter;
    /** Cuánto tardó en crearse el último mundo temporal (para el log y el selftest). */
    private long lastCreateMs = -1;

    public Regen(Worldcraft plugin) {
        this.plugin = plugin;
    }

    /** Quién está usando el mundo temporal, o null si nadie. */
    public String busy() {
        return busyWho;
    }

    public long lastCreateMs() {
        return lastCreateMs;
    }

    /** Al arrancar: las carpetas que quedaron de un apagón (con la marca), en el hilo de E/S. */
    public void cleanLeftovers() {
        Path container = Bukkit.getWorldContainer().toPath();
        Set<String> loaded = new HashSet<>();
        for (World w : Bukkit.getWorlds()) loaded.add(w.getName());
        plugin.io().execute(() -> {
            try {
                for (Path p : TempWorlds.leftovers(container, loaded)) {
                    if (TempWorlds.delete(p)) plugin.getLogger().info("Deleted the temporary world " + p.getFileName() + " left by a regen");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not delete an old temporary world: " + e.getMessage());
            }
        });
    }

    /**
     * El mundo temporal para generar como source con esa semilla (y ese bioma, o null): el que ya está si es igual, o uno
     * nuevo. Lo marca como en uso por who hasta {@link #release()}.
     */
    public World acquire(World source, long seed, Biome biome, String who) throws Problem {
        if (busyWho != null) throw new Problem("another regen is running (" + busyWho + ")");
        String k = source.getUID() + "/" + seed + "/" + (biome == null ? "" : biome.getKey());
        if (unload != null) {
            unload.cancel();
            unload = null;
        }
        if (temp != null && !k.equals(key)) drop();
        if (temp == null) {
            temp = create(source, seed, biome);
            key = k;
        }
        busyWho = who;
        return temp;
    }

    /** Se terminó de leer: el mundo queda keep_seconds y después se borra. */
    public void release() {
        busyWho = null;
        if (temp == null) return;
        // Los chunks que quedaron en memoria (los tickets ya se soltaron) no hacen falta.
        int keep = plugin.config().restore().regenKeepSeconds();
        if (keep <= 0) {
            drop();
            return;
        }
        unload = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            unload = null;
            if (busyWho == null) drop();
        }, keep * 20L);
    }

    /** Al apagar: se descarga y se borra ya. */
    public void shutdown() {
        if (unload != null) unload.cancel();
        unload = null;
        busyWho = null;
        if (temp == null) return;
        World w = temp;
        temp = null;
        key = null;
        Path dir = w.getWorldFolder().toPath();
        try {
            Bukkit.unloadWorld(w, false);
            TempWorlds.delete(dir);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Could not delete the temporary world " + dir.getFileName() + " (it is deleted at the next start): " + e);
        }
    }

    private World create(World source, long seed, Biome biome) throws Problem {
        Path container = Bukkit.getWorldContainer().toPath();
        String name;
        do {
            name = TempWorlds.PREFIX + (++counter);
        } while (Files.exists(container.resolve(name)) || Bukkit.getWorld(name) != null);
        try {
            // La marca va antes del mundo: si el servidor se cae al crearlo, la carpeta igual se reconoce y se borra.
            TempWorlds.mark(container.resolve(name), source.getName());
        } catch (IOException e) {
            throw new Problem("could not write in the server folder (" + e.getMessage() + ")");
        }
        WorldCreator wc = new WorldCreator(name).copy(source).seed(seed).keepSpawnLoaded(TriState.FALSE);
        if (biome != null) wc.biomeProvider(new SingleBiome(biome));
        long t0 = System.nanoTime();
        World w;
        try {
            w = Bukkit.createWorld(wc);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not create the temporary world " + name, e);
            w = null;
        }
        lastCreateMs = (System.nanoTime() - t0) / 1_000_000;
        if (w == null) {
            deleteLater(container.resolve(name));
            throw new Problem("the server did not create the temporary world (see the log)");
        }
        w.setAutoSave(false);
        quiet(w);
        plugin.getLogger().info("Temporary world " + name + " for /wc regen of " + source.getName() + " made in " + lastCreateMs + " ms");
        return w;
    }

    @SuppressWarnings("deprecation") // los nombres de 1.21.4 (en 26.x cambian; si no existen, se ignoran)
    private static void quiet(World w) {
        try {
            w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            w.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
            w.setGameRule(GameRule.DO_FIRE_TICK, false);
        } catch (RuntimeException e) {
            // un servidor sin alguna de estas reglas: el mundo temporal no se guarda ni se juega
        }
    }

    /** El selftest: se descarga y se borra ya (si nadie lo usa). */
    public void discard() {
        if (busyWho != null) return;
        if (unload != null) unload.cancel();
        unload = null;
        drop();
    }

    private void drop() {
        if (temp == null) return;
        World w = temp;
        temp = null;
        key = null;
        Path dir = w.getWorldFolder().toPath();
        if (!Bukkit.unloadWorld(w, false)) {
            plugin.getLogger().warning("Could not unload the temporary world " + w.getName() + ": it is deleted at the next start");
            return;
        }
        deleteLater(dir);
    }

    private void deleteLater(Path dir) {
        deleteLater(dir, 1);
    }

    /** Paper cierra los archivos de región del mundo descargado un momento después: se reintenta (Windows no borra un archivo abierto). */
    private void deleteLater(Path dir, int attempt) {
        plugin.io().execute(() -> {
            try {
                TempWorlds.delete(dir);
            } catch (IOException e) {
                if (attempt < 4 && plugin.isEnabled()) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> deleteLater(dir, attempt + 1), 40L * attempt);
                } else {
                    plugin.getLogger().warning("Could not delete the temporary world " + dir.getFileName() + " (it is deleted at the next start): " + e.getMessage());
                }
            }
        });
    }

    /** Un solo bioma para todo el mundo temporal (regen con un bioma). */
    private static final class SingleBiome extends BiomeProvider {
        private final Biome biome;

        SingleBiome(Biome biome) {
            this.biome = biome;
        }

        @Override
        public Biome getBiome(WorldInfo info, int x, int y, int z) {
            return biome;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo info) {
            return List.of(biome);
        }
    }
}
