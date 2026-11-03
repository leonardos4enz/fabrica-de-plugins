package dev.leo.worldcraft.history;

import dev.leo.worldcraft.region.Box;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * El historial de cada jugador en disco (H6, plan 025 §4): {@code history/<uuid>/index.yml} con sus operaciones y un
 * diario por operación. Sobrevive a reinicios y a reload. Lo usa el hilo principal; los índices se escriben de forma
 * atómica en el hilo de E/S (constitución II.6, III.2).
 */
public final class HistoryStore {
    public enum State {
        /** En marcha (si el servidor se apaga así, al volver queda CANCELLED). */
        RUNNING,
        DONE,
        /** Cortada a mitad: lo hecho se puede deshacer. */
        CANCELLED,
        /** Deshecha: se puede rehacer hasta que el jugador haga otra operación. */
        UNDONE
    }

    public record Entry(int id, UUID owner, String ownerName, String kind, String detail, String world, Box bounds,
                        long time, long changed, State state) {
        Entry with(State s, long n) {
            return new Entry(id, owner, ownerName, kind, detail, world, bounds, time, n, s);
        }
    }

    private static final class Player {
        String name = "?";
        int next = 1;
        final TreeMap<Integer, Entry> entries = new TreeMap<>();
    }

    private final Path root;
    private final Executor io;
    private final Map<UUID, Player> players = new HashMap<>();

    /** io: donde se escriben los índices y se borran los diarios (en las pruebas, el mismo hilo). */
    public HistoryStore(Path root, Executor io) {
        this.root = root;
        this.io = io;
    }

    public Path journal(UUID owner, int id) {
        return root.resolve(owner.toString()).resolve(id + ".wcj");
    }

    /** Lee todos los índices; devuelve cuántas operaciones quedaron a mitad por un apagado (pasan a CANCELLED). */
    public int load() throws IOException {
        players.clear();
        if (!Files.isDirectory(root)) return 0;
        int interrupted = 0;
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root, Files::isDirectory)) {
            for (Path dir : dirs) {
                UUID owner;
                try {
                    owner = UUID.fromString(dir.getFileName().toString());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                Path idx = dir.resolve("index.yml");
                if (!Files.exists(idx)) continue;
                YamlConfiguration y = new YamlConfiguration();
                try {
                    y.loadFromString(Files.readString(idx, StandardCharsets.UTF_8));
                } catch (InvalidConfigurationException e) {
                    throw new IOException(idx + ": " + e.getMessage(), e);
                }
                Player p = new Player();
                p.name = y.getString("name", "?");
                p.next = Math.max(1, y.getInt("next", 1));
                ConfigurationSection ops = y.getConfigurationSection("operations");
                if (ops != null) {
                    for (String k : ops.getKeys(false)) {
                        ConfigurationSection s = ops.getConfigurationSection(k);
                        if (s == null) continue;
                        State st = State.valueOf(s.getString("state", "DONE"));
                        if (st == State.RUNNING) {
                            st = State.CANCELLED;
                            interrupted++;
                        }
                        List<Integer> b = s.getIntegerList("bounds");
                        Box box = b.size() == 6 ? new Box(b.get(0), b.get(1), b.get(2), b.get(3), b.get(4), b.get(5)) : null;
                        int id = Integer.parseInt(k);
                        p.entries.put(id, new Entry(id, owner, p.name, s.getString("kind", "?"), s.getString("detail", ""),
                                s.getString("world", "?"), box, s.getLong("time"), s.getLong("changed"), st));
                        p.next = Math.max(p.next, id + 1);
                    }
                }
                players.put(owner, p);
                if (interrupted > 0) save(owner);
            }
        }
        return interrupted;
    }

    /**
     * Una operación nueva: deja de poder rehacerse lo deshecho (como en cualquier editor) y empieza su entrada.
     */
    public Entry begin(UUID owner, String ownerName, String kind, String detail, String world, Box bounds, long now) {
        Player p = players.computeIfAbsent(owner, k -> new Player());
        p.name = ownerName;
        List<Integer> undone = new ArrayList<>();
        for (Entry e : p.entries.values()) if (e.state() == State.UNDONE) undone.add(e.id());
        for (int id : undone) drop(p, owner, id);
        Entry e = new Entry(p.next++, owner, ownerName, kind, detail, world, bounds, now, 0, State.RUNNING);
        p.entries.put(e.id(), e);
        save(owner);
        return e;
    }

    public Entry finish(Entry e, long changed, boolean complete) {
        return put(e.with(complete ? State.DONE : State.CANCELLED, changed));
    }

    public Entry mark(Entry e, State s) {
        return put(e.with(s, e.changed()));
    }

    private Entry put(Entry e) {
        Player p = players.get(e.owner());
        if (p == null || !p.entries.containsKey(e.id())) return e;
        p.entries.put(e.id(), e);
        save(e.owner());
        return e;
    }

    /** La última que se puede deshacer (hecha o cortada), o vacío. */
    public Optional<Entry> lastUndoable(UUID owner) {
        Player p = players.get(owner);
        if (p == null) return Optional.empty();
        return p.entries.descendingMap().values().stream()
                .filter(e -> e.state() == State.DONE || e.state() == State.CANCELLED).findFirst();
    }

    /** La próxima que se puede rehacer: la deshecha más vieja (la última que se deshizo). */
    public Optional<Entry> nextRedo(UUID owner) {
        Player p = players.get(owner);
        if (p == null) return Optional.empty();
        return p.entries.values().stream().filter(e -> e.state() == State.UNDONE).findFirst();
    }

    /** Las operaciones de un jugador, la más nueva primero. */
    public List<Entry> list(UUID owner) {
        Player p = players.get(owner);
        if (p == null) return List.of();
        return new ArrayList<>(p.entries.descendingMap().values());
    }

    /** Spec 041 H4 CA9 (/wc tool inspect): las operaciones de todos, en el orden de cada uno. */
    public List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        for (Player p : players.values()) out.addAll(p.entries.values());
        return out;
    }

    public Optional<UUID> ownerByName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return players.entrySet().stream().filter(e -> e.getValue().name.toLowerCase(Locale.ROOT).equals(n))
                .map(Map.Entry::getKey).findFirst();
    }

    public List<String> names() {
        return players.values().stream().map(p -> p.name).sorted().toList();
    }

    /**
     * Borra lo que pasa del máximo por jugador (las más viejas primero) y lo más viejo que keepDays. Lo que está en
     * marcha no se toca. Devuelve cuántas borró.
     */
    public int prune(long now, int maxPerPlayer, int keepDays) {
        int removed = 0;
        long cutoff = keepDays <= 0 ? Long.MIN_VALUE : now - keepDays * 86_400_000L;
        for (Map.Entry<UUID, Player> pe : players.entrySet()) {
            Player p = pe.getValue();
            List<Entry> old = new ArrayList<>(p.entries.values());
            old.sort(Comparator.comparingInt(Entry::id));
            int keep = old.size();
            List<Integer> drop = new ArrayList<>();
            for (Entry e : old) {
                if (e.state() == State.RUNNING) continue;
                if (keep > maxPerPlayer || e.time() < cutoff) {
                    drop.add(e.id());
                    keep--;
                }
            }
            for (int id : drop) drop(p, pe.getKey(), id);
            if (!drop.isEmpty()) save(pe.getKey());
            removed += drop.size();
        }
        return removed;
    }

    /**
     * Spec 029 (clearhistory): borra todo el historial de un jugador menos lo que está en marcha (su diario se sigue
     * escribiendo). Devuelve cuántas operaciones borró.
     */
    public int clear(UUID owner) {
        Player p = players.get(owner);
        if (p == null) return 0;
        List<Integer> drop = new ArrayList<>();
        for (Entry e : p.entries.values()) if (e.state() != State.RUNNING) drop.add(e.id());
        for (int id : drop) drop(p, owner, id);
        if (!drop.isEmpty()) save(owner);
        return drop.size();
    }

    private void drop(Player p, UUID owner, int id) {
        p.entries.remove(id);
        Path j = journal(owner, id);
        io.execute(() -> {
            try {
                Files.deleteIfExists(j);
            } catch (IOException ignored) {
                // se intenta otra vez en la próxima limpieza: el índice ya no lo nombra
            }
        });
    }

    private void save(UUID owner) {
        Player p = players.get(owner);
        if (p == null) return;
        // F-04: el YAML se arma en el hilo de E/S (en frío costaba milisegundos dentro del tick que termina una obra);
        // aquí solo se copia lo que hay (las entradas son inmutables).
        String name = p.name;
        int next = p.next;
        List<Entry> entries = List.copyOf(p.entries.values());
        Path file = root.resolve(owner.toString()).resolve("index.yml");
        io.execute(() -> write(file, render(name, next, entries)));
    }

    static String render(String name, int next, List<Entry> entries) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", name);
        y.set("next", next);
        for (Entry e : entries) {
            String k = "operations." + e.id();
            y.set(k + ".kind", e.kind());
            y.set(k + ".detail", e.detail());
            y.set(k + ".world", e.world());
            if (e.bounds() != null) {
                Box b = e.bounds();
                y.set(k + ".bounds", List.of(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
            }
            y.set(k + ".time", e.time());
            y.set(k + ".changed", e.changed());
            y.set(k + ".state", e.state().name());
        }
        return y.saveToString();
    }

    static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
