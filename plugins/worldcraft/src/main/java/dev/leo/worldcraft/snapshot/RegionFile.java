package dev.leo.worldcraft.snapshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Un archivo de región del juego (.mca, plan 036 §1.2), escrito desde su descripción pública: una cabecera de 8 KiB con
 * la posición (3 bytes, en sectores de 4 KiB) y la cantidad de sectores (1 byte) de cada uno de los 32×32 chunks; en el
 * sector, el largo (4 bytes), la compresión (1 byte) y los datos. No confía en el archivo: cada posición se compara con el
 * largo del archivo y lo descomprimido tiene tope (H3 CA4).
 */
public final class RegionFile {
    public static final int SECTOR = 4096;
    /** Lo más que puede ocupar un chunk descomprimido (una bomba de compresión se corta). */
    public static final long MAX_CHUNK = 32L << 20;
    /** Lo más que se lee de una región dentro de un .zip (va entera a memoria). */
    public static final long MAX_REGION = 64L << 20;

    /** Lo que se encontró en el lugar de un chunk. */
    public enum Kind { OK, MISSING, UNSUPPORTED, BROKEN }

    /** Un chunk: el NBT sin comprimir (si OK) o por qué no está. */
    public record Chunk(Kind kind, byte[] nbt, String problem) {
        static Chunk missing() {
            return new Chunk(Kind.MISSING, null, "");
        }

        static Chunk broken(String why) {
            return new Chunk(Kind.BROKEN, null, why);
        }
    }

    /** Los bytes de una región: un archivo de una carpeta o una entrada de un .zip ya leída. */
    public interface Bytes extends Closeable {
        long length() throws IOException;

        void read(long pos, byte[] into, int len) throws IOException;
    }

    /** El archivo .mcc de un chunk demasiado grande para la región (al lado de ella), o null si no está. */
    public interface External {
        byte[] load(int cx, int cz, long max) throws IOException;
    }

    private RegionFile() {
    }

    /** Lee un chunk (coordenadas de chunk del mundo) de su región. */
    public static Chunk chunk(Bytes region, External external, int cx, int cz) throws IOException {
        long len = region.length();
        if (len < 2L * SECTOR) return len == 0 ? Chunk.missing() : Chunk.broken("the region file is cut short");
        byte[] head = new byte[4];
        region.read(4L * ((cx & 31) + (cz & 31) * 32), head, 4);
        int entry = ((head[0] & 0xff) << 24) | ((head[1] & 0xff) << 16) | ((head[2] & 0xff) << 8) | (head[3] & 0xff);
        if (entry == 0) return Chunk.missing();
        long offset = (entry >>> 8) & 0xffffffL, sectors = entry & 0xff;
        if (offset < 2 || sectors == 0) return Chunk.broken("the chunk is outside the region file");
        long pos = offset * SECTOR;
        if (pos + 5 > len) return Chunk.broken("the chunk is outside the region file");
        byte[] h = new byte[5];
        region.read(pos, h, 5);
        int size = ((h[0] & 0xff) << 24) | ((h[1] & 0xff) << 16) | ((h[2] & 0xff) << 8) | (h[3] & 0xff);
        int type = h[4] & 0xff;
        byte[] data;
        if ((type & 128) != 0) {
            data = external == null ? null : external.load(cx, cz, MAX_CHUNK);
            if (data == null) return Chunk.broken("the chunk is in a .mcc file that is not there");
            type &= 127;
        } else {
            if (size < 1 || 4L + size > sectors * SECTOR || pos + 4 + size > len) return Chunk.broken("the chunk length is wrong");
            data = new byte[size - 1];
            region.read(pos + 5, data, data.length);
        }
        return switch (type) {
            case 1 -> inflate(new GZIPInputStream(new ByteArrayInputStream(data)), "GZip");
            case 2 -> inflate(new InflaterInputStream(new ByteArrayInputStream(data)), "zlib");
            case 3 -> data.length > MAX_CHUNK ? Chunk.broken("the chunk is too big") : new Chunk(Kind.OK, data, "");
            case 4 -> new Chunk(Kind.UNSUPPORTED, null, "LZ4");
            default -> new Chunk(Kind.UNSUPPORTED, null, "compression " + type);
        };
    }

    private static Chunk inflate(InputStream in, String what) {
        try (in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_CHUNK) return Chunk.broken("the chunk is too big once uncompressed (more than " + (MAX_CHUNK >> 20) + " MB)");
                out.write(buf, 0, n);
            }
            return new Chunk(Kind.OK, out.toByteArray(), "");
        } catch (IOException e) {
            return Chunk.broken("the chunk is not valid " + what);
        }
    }

    /** Lee una entrada (o un archivo) entero hasta max bytes, cortando aunque diga otro tamaño. */
    public static byte[] readAll(InputStream in, long max, String what) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
        byte[] buf = new byte[1 << 16];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > max) throw new IOException(what + " is too big (more than " + (max >> 20) + " MB)");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** Una región ya en memoria (la de un .zip). */
    public static Bytes of(byte[] data) {
        return new Bytes() {
            @Override
            public long length() {
                return data.length;
            }

            @Override
            public void read(long pos, byte[] into, int len) throws IOException {
                if (pos < 0 || pos + len > data.length) throw new IOException("the region file is cut short");
                System.arraycopy(data, (int) pos, into, 0, len);
            }

            @Override
            public void close() {
            }
        };
    }
}
