package dev.leo.skinstatues.statue;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** data/statues.yml (H8 CA4, CA5): el índice de estatuas. Lo usa el hilo principal; se escribe de forma atómica. */
public final class StatueStore {
    public enum State { BUILDING, DONE, CANCELLED }

    public record Statue(int id, String skin, int height, String pose, String palette, String world, int x, int y, int z,
                         int facing, UUID owner, String ownerName, long created, int blocks, State state) {
        public Statue withState(State s, int placed) {
            return new Statue(id, skin, height, pose, palette, world, x, y, z, facing, owner, ownerName, created, placed, s);
        }
    }

    private final Path file, journals;
    private final Map<Integer, Statue> statues = new TreeMap<>();
    private int nextId = 1;

    public StatueStore(Path dataDir) {
        this.file = dataDir.resolve("statues.yml");
        this.journals = dataDir.resolve("statues");
    }

    public Path journal(int id) {
        return journals.resolve(id + ".journal");
    }

    /** Lee el índice; una estatua que quedó «construyéndose» tras un apagado se marca como cancelada (sigue deshacible). */
    public int load() throws IOException, InvalidConfigurationException {
        statues.clear();
        if (!Files.exists(file)) return 0;
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        nextId = Math.max(1, y.getInt("next_id", 1));
        ConfigurationSection sec = y.getConfigurationSection("statues");
        int interrupted = 0;
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(k);
                if (s == null) continue;
                State st = State.valueOf(s.getString("state", "DONE"));
                if (st == State.BUILDING) {
                    st = State.CANCELLED;
                    interrupted++;
                }
                Statue t = new Statue(Integer.parseInt(k), s.getString("skin"), s.getInt("height"), s.getString("pose"),
                        s.getString("palette"), s.getString("world"), s.getInt("x"), s.getInt("y"), s.getInt("z"),
                        s.getInt("facing"), UUID.fromString(s.getString("owner", new UUID(0, 0).toString())),
                        s.getString("owner_name"), s.getLong("created"), s.getInt("blocks"), st);
                statues.put(t.id(), t);
                nextId = Math.max(nextId, t.id() + 1);
            }
        }
        return interrupted;
    }

    /** Texto del índice para escribir fuera del hilo principal. */
    public String snapshot() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next_id", nextId);
        for (Statue t : statues.values()) {
            String p = "statues." + t.id() + ".";
            y.set(p + "skin", t.skin());
            y.set(p + "height", t.height());
            y.set(p + "pose", t.pose());
            y.set(p + "palette", t.palette());
            y.set(p + "world", t.world());
            y.set(p + "x", t.x());
            y.set(p + "y", t.y());
            y.set(p + "z", t.z());
            y.set(p + "facing", t.facing());
            y.set(p + "owner", t.owner().toString());
            y.set(p + "owner_name", t.ownerName());
            y.set(p + "created", t.created());
            y.set(p + "blocks", t.blocks());
            y.set(p + "state", t.state().name());
        }
        return y.saveToString();
    }

    /** Temporal + reemplazo (constitución II.6). */
    public void write(String snapshot) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling("statues.yml.tmp");
        Files.writeString(tmp, snapshot, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public int allocateId() {
        return nextId++;
    }

    public void put(Statue s) {
        statues.put(s.id(), s);
    }

    public Statue get(int id) {
        return statues.get(id);
    }

    public void remove(int id) {
        statues.remove(id);
    }

    public List<Statue> all() {
        return new ArrayList<>(statues.values());
    }

    /** La última estatua que hizo ese jugador (para /statue undo). */
    public Statue lastBy(UUID owner) {
        return statues.values().stream().filter(s -> s.owner().equals(owner))
                .max(Comparator.comparingLong(Statue::created).thenComparingInt(Statue::id)).orElse(null);
    }
}
