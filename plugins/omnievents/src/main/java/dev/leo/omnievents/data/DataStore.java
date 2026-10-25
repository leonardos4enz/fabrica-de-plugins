package dev.leo.omnievents.data;

import dev.leo.omnievents.expr.Named;
import dev.leo.omnievents.expr.Scope;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.LongSupplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Variables persistentes, enfriamientos y usos (plan 001 §4). Cada jugador es un archivo; el servidor, global.yml.
 * La escritura es atómica (archivo temporal + reemplazo) y se hace fuera del hilo principal salvo al apagar.
 * Spec 009: las variables pueden caducar (sección expire, formato 2) y la primera vez se respalda el formato 1.
 */
public final class DataStore {
    /** Formato de los archivos que escribe esta versión (spec 009). Sin «format», un archivo es de formato 1. */
    public static final int FORMAT = 2;
    static final String MARK = ".formato-2";

    /** Datos de un jugador o del servidor. Los mapas son concurrentes: el chat asíncrono también los lee. */
    public final class Holder {
        private final Map<String, Object> vars = new ConcurrentHashMap<>();
        private final Map<String, Long> expires = new ConcurrentHashMap<>();
        private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
        private final Map<String, long[]> uses = new ConcurrentHashMap<>();
        private volatile boolean dirty;
        private final File file;
        private final UUID owner;
        private boolean readOnly;

        Holder(File file, UUID owner) {
            this.file = file;
            this.owner = owner;
        }

        /** Mapa crudo, sin mirar la caducidad: para leer usa {@link #get} o {@link #live}. */
        public Map<String, Object> vars() {
            return vars;
        }

        /** Valor de la variable, o null si no existe o ya caducó (aunque el barrido no haya pasado). */
        public Object get(String name) {
            Long at = expires.get(name);
            if (at != null && at <= clock.getAsLong()) return null;
            return vars.get(name);
        }

        /** Asigna; {@code expireAt} en ms de época, 0 = no caduca (y quita una caducidad anterior). */
        public void put(String name, Object value, long expireAt) {
            vars.put(name, value);
            if (expireAt > 0) {
                expires.put(name, expireAt);
                queue.add(new Pending(expireAt, owner, name));
            } else {
                expires.remove(name);
            }
            dirty = true;
        }

        /** Borra la variable y su caducidad; devuelve si existía (viva). */
        public boolean remove(String name) {
            boolean had = get(name) != null;
            vars.remove(name);
            expires.remove(name);
            dirty = true;
            return had;
        }

        /** Hora de caducidad (ms de época) o 0 si no caduca. */
        public long expiresAt(String name) {
            Long at = expires.get(name);
            return at == null ? 0 : at;
        }

        /** Segundos que le quedan; -1 si existe y no caduca; null si no existe (H1 CA4). */
        public Double ttl(String name) {
            if (get(name) == null) return null;
            long at = expiresAt(name);
            return at == 0 ? -1.0 : Math.max(0, at - clock.getAsLong()) / 1000.0;
        }

        /** Copia de las variables vivas. */
        public Map<String, Object> live() {
            Map<String, Object> out = new HashMap<>();
            for (String k : vars.keySet()) {
                Object v = get(k);
                if (v != null) out.put(k, v);
            }
            return out;
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
            return dirty && !readOnly;
        }

        /** Un archivo de un formato más nuevo no se sobrescribe (H4 CA3). */
        public boolean readOnly() {
            return readOnly;
        }
    }

    /** Una caducidad pendiente en la cola (dueño null = el servidor). */
    record Pending(long at, UUID owner, String name) implements Comparable<Pending> {
        public int compareTo(Pending o) {
            return Long.compare(at, o.at);
        }
    }

    /** Una variable que caducó en el barrido. */
    public record Expired(Scope scope, UUID player, String name, Object value) {
    }

    private final File folder;
    private final Logger log;
    private final LongSupplier clock;
    private final PriorityBlockingQueue<Pending> queue = new PriorityBlockingQueue<>();
    private final Holder global;
    private final Map<UUID, Holder> players = new ConcurrentHashMap<>();

    public DataStore(File dataFolder, Logger log) {
        this(dataFolder, log, System::currentTimeMillis);
    }

    public DataStore(File dataFolder, Logger log, LongSupplier clock) {
        this.folder = new File(dataFolder, "data");
        this.log = log;
        this.clock = clock;
        backupFormat1();
        new File(folder, "players").mkdirs();
        this.global = load(new File(folder, "global.yml"), null);
    }

    /**
     * Respaldo único de data/ antes de escribir el formato 2 (H4 CA2). Sin la marca y con datos, copia todo (salvo
     * otros respaldos) a data/respaldo-formato1-&lt;fecha&gt;/; si la copia falla no pone la marca y lo reintenta
     * al siguiente arranque.
     */
    private void backupFormat1() {
        File mark = new File(folder, MARK);
        if (mark.exists()) return;
        File[] playerFiles = new File(folder, "players").listFiles();
        boolean hasData = new File(folder, "global.yml").exists() || playerFiles != null && playerFiles.length > 0;
        try {
            if (hasData) {
                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                Path src = folder.toPath();
                Path dst = src.resolve("respaldo-formato1-" + stamp);
                List<Path> all;
                try (Stream<Path> walk = Files.walk(src)) {
                    all = walk.toList();
                }
                for (Path p : all) {
                    Path rel = src.relativize(p);
                    if (rel.toString().isEmpty() || rel.getName(0).toString().startsWith("respaldo-")) continue;
                    Path to = dst.resolve(rel);
                    if (Files.isDirectory(p)) Files.createDirectories(to);
                    else {
                        Files.createDirectories(to.getParent());
                        Files.copy(p, to, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
                log.info("Datos de una versión anterior respaldados en data/" + dst.getFileName() + " (pasan al formato " + FORMAT + ")");
            }
            Files.createDirectories(folder.toPath());
            Files.writeString(mark.toPath(), "Datos en formato " + FORMAT + " (spec 009). No borrar: evita repetir el respaldo.\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.severe("No se pudo respaldar data/ antes de pasar al formato " + FORMAT + " (" + e.getMessage() + "); se reintenta al siguiente arranque");
        }
    }

    public Holder global() {
        return global;
    }

    /** Datos del jugador; si no estaban cargados (no debería pasar con jugadores en línea) se cargan ahora. */
    public Holder player(UUID id) {
        return players.computeIfAbsent(id, k -> load(playerFile(k), k));
    }

    public boolean isLoaded(UUID id) {
        return players.containsKey(id);
    }

    /** Carga anticipada (desde AsyncPlayerPreLoginEvent, fuera del hilo principal). */
    public void preload(UUID id) {
        players.computeIfAbsent(id, k -> load(playerFile(k), k));
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

    private Holder load(File f, UUID owner) {
        Holder h = new Holder(f, owner);
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
        int format = y.getInt("format", 1);
        if (format > FORMAT) {
            h.readOnly = true;
            log.warning(f.getName() + " es del formato " + format + " (de una versión más nueva de OmniEvents); se lee pero no se sobrescribe");
        }
        long now = clock.getAsLong();
        ConfigurationSection exp = y.getConfigurationSection("expire");
        if (exp != null) for (String k : exp.getKeys(false)) {
            Object at = exp.get(k);
            if (at instanceof Number n) h.expires.put(k, n.longValue());
            else log.warning(f.getName() + ": la caducidad de '" + k + "' no es un número; la variable queda sin caducar");
        }
        ConfigurationSection vars = y.getConfigurationSection("vars");
        if (vars != null) for (String k : vars.getKeys(false)) {
            Object v = vars.get(k);
            if (v == null) continue;
            Long at = h.expires.get(k);
            if (at != null && at <= now) {
                // H1 CA3: caducó con el servidor apagado o el jugador desconectado.
                h.expires.remove(k);
                h.dirty = true;
                continue;
            }
            h.vars.put(k, v instanceof Integer || v instanceof Long ? ((Number) v).doubleValue() : v);
            if (at != null) queue.add(new Pending(at, owner, k));
        }
        h.expires.keySet().retainAll(h.vars.keySet());
        // Regresión (spec 013, F-03): el nombre de una regla de menú lleva puntos (tienda.menu.items.x.click) y YAML lo
        // guarda como secciones anidadas; se leen las claves completas, no solo el primer nivel.
        ConfigurationSection cds = y.getConfigurationSection("cooldowns");
        if (cds != null) for (String k : cds.getKeys(true)) {
            if (cds.isConfigurationSection(k)) continue;
            long until = cds.getLong(k);
            if (until > now) h.cooldowns.put(k, until);
        }
        ConfigurationSection uses = y.getConfigurationSection("uses");
        if (uses != null) for (String k : uses.getKeys(true)) {
            if (!k.endsWith(".count") || uses.isConfigurationSection(k)) continue;
            String rule = k.substring(0, k.length() - ".count".length());
            h.uses.put(rule, new long[]{uses.getLong(k), uses.getLong(rule + ".since")});
        }
        return h;
    }

    /** Copia inmutable para escribir en otro hilo. */
    public record Snapshot(File file, Map<String, Object> vars, Map<String, Long> expires, Map<String, Long> cooldowns,
                           Map<String, long[]> uses) {
    }

    private static Snapshot snapshot(Holder h) {
        h.dirty = false;
        Map<String, long[]> u = new HashMap<>();
        h.uses.forEach((k, v) -> u.put(k, v.clone()));
        return new Snapshot(h.file, new HashMap<>(h.vars), new HashMap<>(h.expires), new HashMap<>(h.cooldowns), u);
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
        long now = clock.getAsLong();
        y.set("format", FORMAT);
        s.vars().forEach((k, v) -> {
            Long at = s.expires().get(k);
            if (at != null && at <= now) return;
            y.set("vars." + k, v);
            if (at != null) y.set("expire." + k, at);
        });
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

    /**
     * Barrido de caducidad (H2, en el hilo principal): saca de la cola lo vencido y borra las variables cuya
     * caducidad vigente es esa. Una entrada vieja (otro set después, o un jugador que ya no está cargado) se descarta.
     */
    public List<Expired> sweep() {
        long now = clock.getAsLong();
        List<Expired> out = new ArrayList<>();
        Pending p;
        while ((p = queue.peek()) != null && p.at() <= now) {
            queue.poll();
            Holder h = p.owner() == null ? global : players.get(p.owner());
            if (h == null) continue;
            Long at = h.expires.get(p.name());
            if (at == null || at != p.at()) continue;
            Object value = h.vars.remove(p.name());
            h.expires.remove(p.name());
            h.dirty = true;
            if (value != null) out.add(new Expired(p.owner() == null ? Scope.GLOBAL : Scope.PLAYER, p.owner(), p.name(), value));
        }
        return out;
    }

    /** Entradas en la cola de caducidad (para /omni stats). */
    public int pendingExpiries() {
        return queue.size();
    }

    public long now() {
        return clock.getAsLong();
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
        return h != null ? h : load(playerFile(id), id);
    }

    /** Escribe ya los datos de un jugador desconectado editado por comando. */
    public void writeOffline(Holder h) {
        if (h.readOnly) return;
        write(snapshot(h));
    }
}
