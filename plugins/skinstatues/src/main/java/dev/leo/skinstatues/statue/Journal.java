package dev.leo.skinstatues.statue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Diario de una estatua (plan 021 §4): solo se agrega. Por cada bloque puesto guarda la posición relativa, lo que había
 * y lo que se puso (BlockData como texto, con diccionario). Una cola truncada (el servidor se cayó a mitad de una
 * escritura) se ignora al leer.
 */
public final class Journal implements Closeable {
    public record Entry(int dx, int dy, int dz, String before, String placed) {
    }

    private static final int MAGIC = 0x53534A31; // "SSJ1"
    private static final byte DICT = 1, BLOCK = 2;

    private final DataOutputStream out;
    private final Map<String, Integer> dict = new HashMap<>();

    private Journal(DataOutputStream out) {
        this.out = out;
    }

    public static Journal create(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)));
        out.writeInt(MAGIC);
        out.flush();
        return new Journal(out);
    }

    public void append(List<Entry> entries) throws IOException {
        for (Entry e : entries) {
            int b = ref(e.before()), p = ref(e.placed());
            out.writeByte(BLOCK);
            out.writeInt(e.dx());
            out.writeInt(e.dy());
            out.writeInt(e.dz());
            out.writeInt(b);
            out.writeInt(p);
        }
        out.flush();
    }

    private int ref(String data) throws IOException {
        Integer i = dict.get(data);
        if (i != null) return i;
        int id = dict.size();
        dict.put(data, id);
        out.writeByte(DICT);
        out.writeInt(id);
        out.writeUTF(data);
        return id;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    public static List<Entry> read(Path file) throws IOException {
        List<Entry> out = new ArrayList<>();
        if (!Files.exists(file)) return out;
        try (InputStream raw = Files.newInputStream(file);
             DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) {
            if (in.readInt() != MAGIC) throw new IOException("not a SkinStatues journal: " + file.getFileName());
            Map<Integer, String> dict = new HashMap<>();
            while (true) {
                int type = in.read();
                if (type < 0) break;
                if (type == DICT) {
                    int id = in.readInt();
                    dict.put(id, in.readUTF());
                } else if (type == BLOCK) {
                    int x = in.readInt(), y = in.readInt(), z = in.readInt(), b = in.readInt(), p = in.readInt();
                    String before = dict.get(b), placed = dict.get(p);
                    if (before == null || placed == null) break;
                    out.add(new Entry(x, y, z, before, placed));
                } else {
                    break;
                }
            }
        } catch (EOFException truncated) {
            // Cola cortada: lo leído hasta ahí vale.
        }
        return out;
    }
}
