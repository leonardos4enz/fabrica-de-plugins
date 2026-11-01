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

/**
 * El diario de una operación (H6, plan 025 §4): solo se agrega. Por cada bloque cambiado guarda la posición, el estado
 * que había y el que se puso (como texto, con diccionario) y, si los hay, los datos del bloque de antes y de después
 * (lo que había en un cofre, el texto de un cartel). Una cola cortada (el servidor se cayó a mitad de una escritura) se
 * ignora al leer: lo anterior vale.
 */
public final class Journal implements Closeable {
    private static final int MAGIC = 0x57434A31; // "WCJ1"
    private static final byte DICT = 1, BLOCK = 2, TILE_BEFORE = 3, TILE_AFTER = 4;
    /** Tope de los datos de un bloque: un cofre lleno de libros escritos cabe de sobra (constitución III.4). */
    public static final int MAX_TILE = 2 * 1024 * 1024;

    /** Un cambio, listo para escribir. */
    public record Change(int x, int y, int z, String before, String after, byte[] tileBefore, byte[] tileAfter) {
    }

    private final DataOutputStream out;
    private final Map<String, Integer> dict = new HashMap<>();

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
        Map<Integer, Integer> dictIndex = new HashMap<>();
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
