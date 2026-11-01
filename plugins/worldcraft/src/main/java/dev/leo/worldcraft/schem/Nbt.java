package dev.leo.worldcraft.schem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * El formato NBT del juego (plan 027 §1), escrito desde su descripción pública: cada etiqueta es un tipo de Java. Un
 * compuesto es un {@code Map<String, Object>} ordenado; una lista, {@link TagList}; los números, Byte, Short, Integer,
 * Long, Float y Double; los arreglos, byte[], int[] y long[]; el texto, String. El lector no confía en el archivo: la
 * profundidad tiene tope y cada largo se compara con lo que queda antes de reservar memoria (H5 CA2).
 */
public final class Nbt {
    public static final int END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, BYTE_ARRAY = 7,
            STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;
    public static final int MAX_DEPTH = 512;

    private Nbt() {
    }

    /** Una lista NBT: todos sus elementos del mismo tipo. */
    public record TagList(int type, List<Object> items) {
    }

    /** La raíz: su nombre (casi siempre vacío) y el compuesto. */
    public record Root(String name, Map<String, Object> tag) {
    }

    /** Un archivo que no es NBT válido; el mensaje, en inglés, va al jugador. */
    public static final class NbtException extends IOException {
        public NbtException(String message) {
            super(message);
        }
    }

    // ------------------------------------------------------------------ leer

    public static Root read(byte[] data) throws NbtException {
        Reader r = new Reader(data);
        try {
            int type = r.in.readUnsignedByte();
            if (type != COMPOUND) throw new NbtException("not an NBT file (it does not start with a compound)");
            String name = r.in.readUTF();
            @SuppressWarnings("unchecked")
            Map<String, Object> tag = (Map<String, Object>) r.payload(COMPOUND, 0);
            return new Root(name, tag);
        } catch (EOFException e) {
            throw new NbtException("the file is cut short");
        } catch (NbtException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new NbtException("damaged NBT (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static final class Reader {
        final ByteArrayInputStream bytes;
        final DataInputStream in;

        Reader(byte[] data) {
            bytes = new ByteArrayInputStream(data);
            in = new DataInputStream(bytes);
        }

        /** Un largo leído del archivo, que no puede pasar de lo que queda (cada elemento ocupa al menos size bytes). */
        int length(int size) throws IOException {
            int n = in.readInt();
            if (n < 0) throw new NbtException("damaged NBT (negative length)");
            if ((long) n * size > bytes.available()) throw new NbtException("the file is cut short");
            return n;
        }

        Object payload(int type, int depth) throws IOException {
            if (depth > MAX_DEPTH) throw new NbtException("NBT nested too deep (more than " + MAX_DEPTH + " levels)");
            switch (type) {
                case BYTE:
                    return in.readByte();
                case SHORT:
                    return in.readShort();
                case INT:
                    return in.readInt();
                case LONG:
                    return in.readLong();
                case FLOAT:
                    return in.readFloat();
                case DOUBLE:
                    return in.readDouble();
                case BYTE_ARRAY: {
                    byte[] a = new byte[length(1)];
                    in.readFully(a);
                    return a;
                }
                case STRING:
                    return in.readUTF();
                case LIST: {
                    int t = in.readUnsignedByte();
                    if (t > LONG_ARRAY) throw new NbtException("damaged NBT (unknown tag type " + t + ")");
                    int n = length(t == END ? 0 : 1);
                    if (t == END && n > 0) throw new NbtException("damaged NBT (list of nothing)");
                    List<Object> items = new ArrayList<>(Math.min(n, 1 << 16));
                    for (int i = 0; i < n; i++) items.add(payload(t, depth + 1));
                    return new TagList(t, items);
                }
                case COMPOUND: {
                    Map<String, Object> m = new LinkedHashMap<>();
                    while (true) {
                        int t = in.readUnsignedByte();
                        if (t == END) return m;
                        if (t > LONG_ARRAY) throw new NbtException("damaged NBT (unknown tag type " + t + ")");
                        String name = in.readUTF();
                        m.put(name, payload(t, depth + 1));
                    }
                }
                case INT_ARRAY: {
                    int[] a = new int[length(4)];
                    for (int i = 0; i < a.length; i++) a[i] = in.readInt();
                    return a;
                }
                case LONG_ARRAY: {
                    long[] a = new long[length(8)];
                    for (int i = 0; i < a.length; i++) a[i] = in.readLong();
                    return a;
                }
                default:
                    throw new NbtException("damaged NBT (unknown tag type " + type + ")");
            }
        }
    }

    // ------------------------------------------------------------------ escribir

    public static byte[] write(String name, Map<String, Object> tag) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            out.writeByte(COMPOUND);
            out.writeUTF(name);
            payload(out, tag, 0);
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot write NBT: " + e.getMessage(), e);
        }
        return bytes.toByteArray();
    }

    /** El tipo NBT de un valor de Java. */
    public static int typeOf(Object v) {
        if (v instanceof Byte) return BYTE;
        if (v instanceof Short) return SHORT;
        if (v instanceof Integer) return INT;
        if (v instanceof Long) return LONG;
        if (v instanceof Float) return FLOAT;
        if (v instanceof Double) return DOUBLE;
        if (v instanceof byte[]) return BYTE_ARRAY;
        if (v instanceof String) return STRING;
        if (v instanceof TagList) return LIST;
        if (v instanceof Map) return COMPOUND;
        if (v instanceof int[]) return INT_ARRAY;
        if (v instanceof long[]) return LONG_ARRAY;
        throw new IllegalArgumentException("not an NBT value: " + (v == null ? "null" : v.getClass().getName()));
    }

    @SuppressWarnings("unchecked")
    private static void payload(DataOutputStream out, Object v, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("nested too deep");
        switch (typeOf(v)) {
            case BYTE -> out.writeByte((Byte) v);
            case SHORT -> out.writeShort((Short) v);
            case INT -> out.writeInt((Integer) v);
            case LONG -> out.writeLong((Long) v);
            case FLOAT -> out.writeFloat((Float) v);
            case DOUBLE -> out.writeDouble((Double) v);
            case BYTE_ARRAY -> {
                byte[] a = (byte[]) v;
                out.writeInt(a.length);
                out.write(a);
            }
            case STRING -> out.writeUTF((String) v);
            case LIST -> {
                TagList l = (TagList) v;
                out.writeByte(l.items().isEmpty() ? END : l.type());
                out.writeInt(l.items().size());
                for (Object o : l.items()) {
                    if (typeOf(o) != l.type()) throw new IOException("a list mixes tag types");
                    payload(out, o, depth + 1);
                }
            }
            case COMPOUND -> {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                    out.writeByte(typeOf(e.getValue()));
                    out.writeUTF(e.getKey());
                    payload(out, e.getValue(), depth + 1);
                }
                out.writeByte(END);
            }
            case INT_ARRAY -> {
                int[] a = (int[]) v;
                out.writeInt(a.length);
                for (int i : a) out.writeInt(i);
            }
            case LONG_ARRAY -> {
                long[] a = (long[]) v;
                out.writeInt(a.length);
                for (long l : a) out.writeLong(l);
            }
            default -> throw new IOException("unknown tag");
        }
    }

    // ------------------------------------------------------------------ GZip

    /**
     * Descomprime GZip sin pasar de max bytes (contra las bombas de compresión, H5 CA2). Un archivo sin comprimir (que
     * empieza por el compuesto) se devuelve tal cual.
     */
    public static byte[] gunzip(byte[] data, long max) throws NbtException {
        if (data.length >= 2 && (data[0] & 0xff) == 0x1f && (data[1] & 0xff) == 0x8b) {
            try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data), 8192)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(data.length * 4, 1 << 20));
                byte[] buf = new byte[8192];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > max) throw new NbtException("the file is too big once uncompressed (more than " + (max >> 20) + " MB)");
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            } catch (NbtException e) {
                throw e;
            } catch (IOException e) {
                throw new NbtException("the file is not valid GZip (" + e.getMessage() + ")");
            }
        }
        if (data.length > max) throw new NbtException("the file is too big");
        return data;
    }

    public static byte[] gzip(byte[] data) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(64, data.length / 4));
        try (GZIPOutputStream out = new GZIPOutputStream(bytes, 8192)) {
            out.write(data);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    // ------------------------------------------------------------------ ayudas para leer compuestos

    @SuppressWarnings("unchecked")
    public static Map<String, Object> compound(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    public static TagList list(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof TagList l ? l : null;
    }

    /** Un número entero de cualquier ancho; null si no está o no es un número entero. */
    public static Long integer(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Byte || v instanceof Short || v instanceof Integer || v instanceof Long) return ((Number) v).longValue();
        return null;
    }

    public static String string(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof String s ? s : null;
    }
}
