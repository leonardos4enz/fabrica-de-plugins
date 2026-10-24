package dev.leo.omnievents.data;

import dev.leo.omnievents.expr.Named;
import dev.leo.omnievents.expr.Values;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Variables persistentes, enfriamientos y usos (plan 001 §4). Cada jugador es un archivo; el servidor, global.yml.
 * La escritura es atómica (archivo temporal + reemplazo) y se hace fuera del hilo principal salvo al apagar.
 */
public final class DataStore {
    /** Datos de un jugador o del servidor. Los mapas son concurrentes: el chat asíncrono también los lee. */
    public static final class Holder {
        private final Map<String, Object> vars = new ConcurrentHashMap<>();
        private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
        private final Map<String, long[]> uses = new ConcurrentHashMap<>();
        private volatile boolean dirty;
        private final File file;

        Holder(File file) {
            this.file = file;
        }

        public Map<String, Object> vars() {
            return vars;
        }

        public Map<String, Long> cooldowns() {
            return cooldowns;
        }

        /** regla → {cuenta, desde (epoch ms)} */
        public Map<String, long[]> uses() {
            return uses;
        }

        public void markDirty() {
            dirty = true;
        }

        public boolean dirty() {
            return dirty;
        }
    }

    private final File folder;
    private final Logger log;
    private final Holder global;
    private final Map<UUID, Holder> players = new ConcurrentHashMap<>();

    public DataStore(File dataFolder, Logger log) {
        this.folder = new File(dataFolder, "data");
        this.log = log;
        new File(folder, "players").mkdirs();
        this.global = load(new File(folder, "global.yml"));
    }

    public Holder global() {
        return global;
    }

    /** Datos del jugador; si no estaban cargados (no debería pasar con jugadores en línea) se cargan ahora. */
    public Holder player(UUID id) {
        return players.computeIfAbsent(id, k -> load(playerFile(k)));
    }

    public boolean isLoaded(UUID id) {
        return players.containsKey(id);
    }

    /** Carga anticipada (desde AsyncPlayerPreLoginEvent, fuera del hilo principal). */
    public void preload(UUID id) {
        players.computeIfAbsent(id, k -> load(playerFile(k)));
    }

    /** Guarda y libera a un jugador que salió. */
    public void unload(UUID id, java.util.concurrent.Executor async) {
        Holder h = players.remove(id);
        if (h != null && h.dirty()) {
            Snapshot s = snapshot(h);
            async.execute(() -> write(s));
        }
    }

    private File playerFile(UUID id) {
        return new File(new File(folder, "players"), id + ".yml");
    }

    private Holder load(File f) {
        Holder h = new Holder(f);
        if (!f.exists()) return h;
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(Files.readString(f.toPath(), StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException e) {
            File broken = new File(f.getParentFile(), f.getName() + ".roto-" + System.currentTimeMillis());
            log.severe("No se pudo leer " + f.getName() + " (" + e.getMessage() + "); se conserva como " + broken.getName() + " y se empieza vacío");
            f.renameTo(broken);
            return h;
        }
        ConfigurationSection vars = y.getConfigurationSection("vars");
        if (vars != null) for (String k : vars.getKeys(false)) {
            Object v = vars.get(k);
            if (v != null) h.vars.put(k, v instanceof Integer || v instanceof Long ? ((Number) v).doubleValue() : v);
        }
        ConfigurationSection cds = y.getConfigurationSection("cooldowns");
        long now = System.currentTimeMillis();
        if (cds != null) for (String k : cds.getKeys(false)) {
            long until = cds.getLong(k);
            if (until > now) h.cooldowns.put(k, until);
        }
        ConfigurationSection uses = y.getConfigurationSection("uses");
        if (uses != null) for (String k : uses.getKeys(false)) {
            h.uses.put(k, new long[]{uses.getLong(k + ".count"), uses.getLong(k + ".since")});
        }
        return h;
    }

    /** Copia inmutable para escribir en otro hilo. */
    public record Snapshot(File file, Map<String, Object> vars, Map<String, Long> cooldowns, Map<String, long[]> uses) {
    }

    private static Snapshot snapshot(Holder h) {
        h.dirty = false;
        Map<String, long[]> u = new HashMap<>();
        h.uses.forEach((k, v) -> u.put(k, v.clone()));
        return new Snapshot(h.file, new HashMap<>(h.vars), new HashMap<>(h.cooldowns), u);
    }

    /** Instantáneas de todo lo que cambió (en el hilo principal); se escriben luego con {@link #write}. */
    public List<Snapshot> dirtySnapshots() {
        List<Snapshot> out = new ArrayList<>();
        if (global.dirty()) out.add(snapshot(global));
        for (Holder h : players.values()) if (h.dirty()) out.add(snapshot(h));
        return out;
    }

    public void write(Snapshot s) {
        YamlConfiguration y = new YamlConfiguration();
        s.vars().forEach((k, v) -> y.set("vars." + k, v));
        long now = System.currentTimeMillis();
        s.cooldowns().forEach((k, v) -> {
            if (v > now) y.set("cooldowns." + k, v);
        });
        s.uses().forEach((k, v) -> {
            y.set("uses." + k + ".count", v[0]);
            y.set("uses." + k + ".since", v[1]);
        });
        try {
            Path target = s.file().toPath();
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, y.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicNotSupported) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.severe("No se pudo guardar " + s.file().getName() + ": " + e.getMessage());
        }
    }

    public void saveAllSync() {
        for (Snapshot s : dirtySnapshots()) write(s);
    }

    public Collection<UUID> loadedPlayers() {
        return players.keySet();
    }

    /** Valor que se puede guardar en YAML y volver a leer igual. */
    public static Object storable(Object v) {
        v = Values.normalize(v);
        if (v instanceof Named n) return n.name();
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean || v instanceof String) return v;
        if (v instanceof Entity e) return e.getUniqueId().toString();
        if (v instanceof Collection<?> c) {
            List<Object> out = new ArrayList<>();
            for (Object o : c) out.add(storable(o));
            return out;
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new HashMap<>();
            m.forEach((k, val) -> out.put(String.valueOf(k), storable(val)));
            return out;
        }
        return Values.str(v);
    }

    /** Carga los datos de un jugador desconectado sin dejarlos en memoria (para /omni var). */
    public Holder peekOffline(UUID id) {
        Holder h = players.get(id);
        return h != null ? h : load(playerFile(id));
    }

    /** Escribe ya los datos de un jugador desconectado editado por comando. */
    public void writeOffline(Holder h) {
        write(snapshot(h));
    }
}
