package dev.leo.worldcraft.history;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.leo.worldcraft.entity.EntityCopy;

/**
 * El diario de una operación (H6, plan 025 §4): solo se agrega. Por cada bloque cambiado guarda la posición, el estado
 * que había y el que se puso (como texto, con diccionario) y, si los hay, los datos del bloque de antes y de después
 * (lo que había en un cofre, el texto de un cartel). Una cola cortada (el servidor se cayó a mitad de una escritura) se
 * ignora al leer: lo anterior vale.
 */
public final class Journal implements Closeable {
    private static final int MAGIC = 0x57434A31; // "WCJ1"
    private static final byte DICT = 1, BLOCK = 2, TILE_BEFORE = 3, TILE_AFTER = 4;
    /** Spec 031: una entidad que la obra quitó, una que creó, y un uuid que deshacer o rehacer cambió al recrearla. */
    private static final byte ENTITY_OUT = 5, ENTITY_IN = 6, REMAP = 7;
    /** Spec 033: un nombre de bioma (diccionario propio: no son estados) y una celda de 4×4×4 que cambió de bioma. */
    private static final byte BIOME_DICT = 8, BIOME = 9;
    /** Tope de los datos de un bloque: un cofre lleno de libros escritos cabe de sobra (constitución III.4). */
    public static final int MAX_TILE = 2 * 1024 * 1024;

    /** Spec 031: una entidad quitada (out) o creada por la obra, con su uuid de entonces. */
    public record EntityChange(boolean out, UUID id, EntityCopy copy) {
    }

    /** Un cambio, listo para escribir. */
    public record Change(int x, int y, int z, String before, String after, byte[] tileBefore, byte[] tileAfter) {
    }

    /** Spec 033: una celda (esquina mínima) que pasó de un bioma a otro. */
    public record BiomeChange(int x, int y, int z, String before, String after) {
    }

    private final DataOutputStream out;
    private final Map<String, Integer> dict = new HashMap<>(), biomeDict = new HashMap<>();

    private Journal(DataOutputStream out) {
        this.out = out;
    }

    public static Journal create(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE), 1 << 16));
        out.writeInt(MAGIC);
        out.flush();
        return new Journal(out);
    }

    /**
     * Spec 031: abre un diario que ya existe para agregarle al final (los uuid nuevos al deshacer); si no existe, lo crea.
     */
    public static Journal reopen(Path file) throws IOException {
        if (!Files.exists(file)) return create(file);
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file,
                StandardOpenOption.APPEND, StandardOpenOption.WRITE), 1 << 12));
        return new Journal(out);
    }

    public void appendEntities(List<EntityChange> changes) throws IOException {
        for (EntityChange c : changes) {
            EntityCopy e = c.copy();
            if (e.nbt().length > MAX_TILE) continue;
            out.writeByte(c.out() ? ENTITY_OUT : ENTITY_IN);
            uuid(c.id());
            out.writeDouble(e.x());
            out.writeDouble(e.y());
            out.writeDouble(e.z());
            out.writeFloat(e.yaw());
            out.writeFloat(e.pitch());
            out.writeUTF(e.type());
            out.writeShort(Math.min(e.contains().size(), 255));
            for (int i = 0; i < Math.min(e.contains().size(), 255); i++) out.writeUTF(e.contains().get(i));
            out.writeInt(e.nbt().length);
            out.write(e.nbt());
        }
        out.flush();
    }

    public void appendRemap(UUID old, UUID now) throws IOException {
        out.writeByte(REMAP);
        uuid(old);
        uuid(now);
        out.flush();
    }

    private void uuid(UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    public void append(List<Change> changes) throws IOException {
        for (Change c : changes) {
            int b = ref(c.before()), a = ref(c.after());
            out.writeByte(BLOCK);
            out.writeInt(c.x());
            out.writeInt(c.y());
            out.writeInt(c.z());
            out.writeInt(b);
            out.writeInt(a);
            tile(TILE_BEFORE, c.tileBefore());
            tile(TILE_AFTER, c.tileAfter());
        }
        out.flush();
    }

    public void appendBiomes(List<BiomeChange> changes) throws IOException {
        for (BiomeChange c : changes) {
            int b = biomeRef(c.before()), a = biomeRef(c.after());
            out.writeByte(BIOME);
            out.writeInt(c.x());
            out.writeInt(c.y());
            out.writeInt(c.z());
            out.writeInt(b);
            out.writeInt(a);
        }
        out.flush();
    }

    private int biomeRef(String biome) throws IOException {
        Integer i = biomeDict.get(biome);
        if (i != null) return i;
        int id = biomeDict.size();
        biomeDict.put(biome, id);
        out.writeByte(BIOME_DICT);
        out.writeInt(id);
        out.writeUTF(biome);
        return id;
    }

    private void tile(byte type, byte[] data) throws IOException {
        if (data == null || data.length > MAX_TILE) return;
        out.writeByte(type);
        out.writeInt(data.length);
        out.write(data);
    }

    private int ref(String state) throws IOException {
        Integer i = dict.get(state);
        if (i != null) return i;
        int id = dict.size();
        dict.put(state, id);
        out.writeByte(DICT);
        out.writeInt(id);
        out.writeUTF(state);
        return id;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    /** Lo leído de un diario, en arreglos (un millón de cambios ocupan unos 20 MB). */
    public static final class Data {
        public final List<String> dict = new ArrayList<>();
        public int size;
        public int[] x = new int[256], y = new int[256], z = new int[256], before = new int[256], after = new int[256];
        public final Map<Integer, byte[]> tileBefore = new HashMap<>(), tileAfter = new HashMap<>();
        /** Spec 031: las entidades que la obra quitó y las que creó, con el uuid de entonces. */
        public final List<EntityChange> entitiesOut = new ArrayList<>(), entitiesIn = new ArrayList<>();
        /** Spec 031: uuid de entonces → el de ahora (seguir la cadena con {@link #current}). */
        public final Map<UUID, UUID> remap = new HashMap<>();
        /** Spec 033: las celdas que cambiaron de bioma (esquina mínima) y los índices en biomeDict de antes y después. */
        public final List<String> biomeDict = new ArrayList<>();
        public int biomes;
        public int[] bx = new int[16], by = new int[16], bz = new int[16], bBefore = new int[16], bAfter = new int[16];

        void addBiome(int px, int py, int pz, int b, int a) {
            if (biomes == bx.length) {
                int n = biomes * 2;
                bx = Arrays.copyOf(bx, n);
                by = Arrays.copyOf(by, n);
                bz = Arrays.copyOf(bz, n);
                bBefore = Arrays.copyOf(bBefore, n);
                bAfter = Arrays.copyOf(bAfter, n);
            }
            bx[biomes] = px;
            by[biomes] = py;
            bz[biomes] = pz;
            bBefore[biomes] = b;
            bAfter[biomes] = a;
            biomes++;
        }

        /** El uuid que tiene ahora la entidad que el diario conoce como id (tras deshacer y rehacer). */
        public UUID current(UUID id) {
            UUID u = id;
            for (int i = 0; i < 10_000; i++) {
                UUID n = remap.get(u);
                if (n == null) return u;
                u = n;
            }
            return u;
        }

        void add(int px, int py, int pz, int b, int a) {
            if (size == x.length) {
                int n = size * 2;
                x = Arrays.copyOf(x, n);
                y = Arrays.copyOf(y, n);
                z = Arrays.copyOf(z, n);
                before = Arrays.copyOf(before, n);
                after = Arrays.copyOf(after, n);
            }
            x[size] = px;
            y[size] = py;
            z[size] = pz;
            before[size] = b;
            after[size] = a;
            size++;
        }
    }

    public static Data read(Path file) throws IOException {
        Data d = new Data();
        if (!Files.exists(file)) return d;
        Map<Integer, Integer> dictIndex = new HashMap<>(), biomeIndex = new HashMap<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 16))) {
            if (in.readInt() != MAGIC) throw new IOException("not a Worldcraft history file: " + file.getFileName());
            while (true) {
                int type = in.read();
                if (type < 0) break;
                if (type == DICT) {
                    int id = in.readInt();
                    String s = in.readUTF();
                    dictIndex.put(id, d.dict.size());
                    d.dict.add(s);
                } else if (type == BLOCK) {
                    int x = in.readInt(), y = in.readInt(), z = in.readInt(), b = in.readInt(), a = in.readInt();
                    Integer bi = dictIndex.get(b), ai = dictIndex.get(a);
                    if (bi == null || ai == null) break;
                    d.add(x, y, z, bi, ai);
                } else if (type == TILE_BEFORE || type == TILE_AFTER) {
                    int n = in.readInt();
                    if (n < 0 || n > MAX_TILE || d.size == 0) break;
                    byte[] data = in.readNBytes(n);
                    if (data.length < n) break;
                    (type == TILE_BEFORE ? d.tileBefore : d.tileAfter).put(d.size - 1, data);
                } else if (type == ENTITY_OUT || type == ENTITY_IN) {
                    UUID id = new UUID(in.readLong(), in.readLong());
                    double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
                    float yaw = in.readFloat(), pitch = in.readFloat();
                    String t = in.readUTF();
                    int nc = in.readUnsignedShort();
                    List<String> contains = new ArrayList<>();
                    for (int i = 0; i < nc; i++) contains.add(in.readUTF());
                    int n = in.readInt();
                    if (n < 0 || n > MAX_TILE) break;
                    byte[] nbt = in.readNBytes(n);
                    if (nbt.length < n) break;
                    EntityChange c = new EntityChange(type == ENTITY_OUT, id, new EntityCopy(t, x, y, z, yaw, pitch, nbt, contains));
                    (c.out() ? d.entitiesOut : d.entitiesIn).add(c);
                } else if (type == REMAP) {
                    UUID old = new UUID(in.readLong(), in.readLong()), now = new UUID(in.readLong(), in.readLong());
                    d.remap.put(old, now);
                } else if (type == BIOME_DICT) {
                    int id = in.readInt();
                    String s = in.readUTF();
                    biomeIndex.put(id, d.biomeDict.size());
                    d.biomeDict.add(s);
                } else if (type == BIOME) {
                    int x = in.readInt(), y = in.readInt(), z = in.readInt(), b = in.readInt(), a = in.readInt();
                    Integer bi = biomeIndex.get(b), ai = biomeIndex.get(a);
                    if (bi == null || ai == null) break;
                    d.addBiome(x, y, z, bi, ai);
                } else {
                    break;
                }
            }
        } catch (EOFException truncated) {
            // Cola cortada: lo leído hasta ahí vale.
        }
        return d;
    }
}
