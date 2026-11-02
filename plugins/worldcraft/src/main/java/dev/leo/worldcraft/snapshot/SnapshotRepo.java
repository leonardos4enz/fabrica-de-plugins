package dev.leo.worldcraft.snapshot;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Las copias de seguridad de snapshots.folder (plan 036 §1.2, H2): cada una es una carpeta o un .zip con las carpetas de
 * los mundos adentro. Java puro; lee el disco, así que corre fuera del hilo principal.
 */
public final class SnapshotRepo {
    private static final Pattern DATE = Pattern.compile(
            "(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[ _T-](\\d{1,2})[-:._h](\\d{1,2})(?:[-:._](\\d{1,2}))?)?");
    private static final DateTimeFormatter SHOW = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    /**
     * Una copia que tiene el mundo: su nombre (el de la carpeta o el .zip), la fecha (millis) y, en un .zip, dónde empieza
     * la carpeta del mundo («world/» o «backup/world/»).
     */
    public record Entry(String name, Path path, boolean zip, String zipPrefix, long time, boolean dateFromName) {
        public String date(ZoneId zone) {
            return SHOW.format(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(time), zone));
        }
    }

    private SnapshotRepo() {
    }

    /** Las copias de la carpeta que tienen ese mundo, de la más nueva a la más vieja. */
    public static List<Entry> list(Path folder, String world, ZoneId zone) throws IOException {
        List<Entry> out = new ArrayList<>();
        if (!Files.isDirectory(folder)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder)) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (!safeName(name)) continue;
                Long fromName = dateFromName(name, zone);
                if (Files.isDirectory(p)) {
                    if (!Files.isDirectory(p.resolve(world).resolve("region"))) continue;
                    long t = fromName != null ? fromName : Files.getLastModifiedTime(p).toMillis();
                    out.add(new Entry(name, p, false, null, t, fromName != null));
                } else if (name.toLowerCase(Locale.ROOT).endsWith(".zip") && Files.isRegularFile(p)) {
                    String prefix;
                    try {
                        prefix = zipPrefix(p, world);
                    } catch (IOException e) {
                        continue; // un .zip roto no es una copia
                    }
                    if (prefix == null) continue;
                    long t = fromName != null ? fromName : Files.getLastModifiedTime(p).toMillis();
                    out.add(new Entry(name, p, true, prefix, t, fromName != null));
                }
            }
        }
        out.sort(Comparator.comparingLong(Entry::time).reversed().thenComparing(Entry::name));
        return out;
    }

    /** Un nombre que se puede usar (los de la carpeta; nunca uno con rutas). */
    public static boolean safeName(String name) {
        return !name.isEmpty() && !name.startsWith(".") && !name.contains("/") && !name.contains("\\") && !name.contains("..")
                && name.length() <= 128;
    }

    /** Dónde empieza el mundo dentro del .zip («world/» o «algo/world/»), o null si no lo tiene. */
    static String zipPrefix(Path zip, String world) throws IOException {
        try (ZipFile z = new ZipFile(zip.toFile())) {
            String best = null;
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName().replace('\\', '/');
                int i = n.indexOf(world + "/region/");
                if (i < 0 || !n.endsWith(".mca")) continue;
                String prefix = n.substring(0, i + world.length() + 1);
                // Solo el mundo en la raíz o con una carpeta de más, y nunca «..».
                String before = n.substring(0, i);
                if (before.contains("..") || before.chars().filter(c -> c == '/').count() > 1 || !(before.isEmpty() || before.endsWith("/"))) continue;
                if (best == null || prefix.length() < best.length()) best = prefix;
            }
            return best;
        }
    }

    /** La fecha que dice el nombre («2026-11-02», «2026-11-02-14-30», «2026-11-02_14-30-00»…), o null. */
    public static Long dateFromName(String name, ZoneId zone) {
        Matcher m = DATE.matcher(name);
        if (!m.find()) return null;
        try {
            int h = m.group(4) == null ? 0 : Integer.parseInt(m.group(4));
            int mi = m.group(5) == null ? 0 : Integer.parseInt(m.group(5));
            int s = m.group(6) == null ? 0 : Integer.parseInt(m.group(6));
            LocalDateTime t = LocalDateTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), h, mi, s);
            return t.atZone(zone).toInstant().toEpochMilli();
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    /** La fecha que escribe el jugador: «2026-11-02», «2026-11-02 14:30» o «2026-11-02T14:30». */
    public static long parseDate(String text, ZoneId zone) {
        String t = text.trim().replace('T', ' ');
        try {
            if (t.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                String[] p = t.split("-");
                return LocalDate.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])).atStartOfDay(zone).toInstant().toEpochMilli();
            }
            Matcher m = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2}) +(\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?").matcher(t);
            if (m.matches()) {
                LocalDateTime d = LocalDateTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                        Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), m.group(6) == null ? 0 : Integer.parseInt(m.group(6)));
                return d.atZone(zone).toInstant().toEpochMilli();
            }
        } catch (DateTimeException | NumberFormatException e) {
            // abajo
        }
        throw new IllegalArgumentException("'" + text + "' is not a date: write it like 2026-11-02 or 2026-11-02 14:30");
    }

    /** La más nueva antes de t (list va de la más nueva a la más vieja), o null. */
    public static Entry before(List<Entry> list, long t) {
        for (Entry e : list) if (e.time() < t) return e;
        return null;
    }

    /** La más vieja después de t, o null. */
    public static Entry after(List<Entry> list, long t) {
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).time() > t) return list.get(i);
        return null;
    }

    // ------------------------------------------------------------------ leer los chunks de una copia

    /** Los chunks de un mundo de una copia. */
    public interface Chunks extends Closeable {
        RegionFile.Chunk chunk(int cx, int cz) throws IOException;
    }

    /** Abre el mundo de la copia (una carpeta o un .zip) para leer chunks. */
    public static Chunks open(Entry e, String world) throws IOException {
        if (!e.zip()) return new FolderChunks(e.path().resolve(world).resolve("region"));
        return new ZipChunks(new ZipFile(e.path().toFile()), e.zipPrefix() + "region/");
    }

    private static final class FolderChunks implements Chunks {
        private final Path dir;
        private final Map<Long, RegionFile.Bytes> open = new HashMap<>();

        FolderChunks(Path dir) {
            this.dir = dir;
        }

        @Override
        public RegionFile.Chunk chunk(int cx, int cz) throws IOException {
            long key = ((long) (cx >> 5) << 32) ^ ((cz >> 5) & 0xffffffffL);
            RegionFile.Bytes b = open.get(key);
            if (b == null && !open.containsKey(key)) {
                Path f = dir.resolve("r." + (cx >> 5) + "." + (cz >> 5) + ".mca");
                b = Files.isRegularFile(f) ? file(f) : null;
                open.put(key, b);
            }
            if (b == null) return RegionFile.Chunk.missing();
            return RegionFile.chunk(b, (x, z, max) -> {
                Path f = dir.resolve("c." + x + "." + z + ".mcc");
                if (!Files.isRegularFile(f)) return null;
                try (InputStream in = Files.newInputStream(f)) {
                    return RegionFile.readAll(in, max, "the .mcc file");
                }
            }, cx, cz);
        }

        @Override
        public void close() throws IOException {
            for (RegionFile.Bytes b : open.values()) if (b != null) b.close();
            open.clear();
        }

        private static RegionFile.Bytes file(Path f) throws IOException {
            RandomAccessFile raf = new RandomAccessFile(f.toFile(), "r");
            return new RegionFile.Bytes() {
                @Override
                public long length() throws IOException {
                    return raf.length();
                }

                @Override
                public void read(long pos, byte[] into, int len) throws IOException {
                    raf.seek(pos);
                    raf.readFully(into, 0, len);
                }

                @Override
                public void close() throws IOException {
                    raf.close();
                }
            };
        }
    }

    private static final class ZipChunks implements Chunks {
        private final ZipFile zip;
        private final String dir;
        private final Map<Long, RegionFile.Bytes> open = new HashMap<>();

        ZipChunks(ZipFile zip, String dir) {
            this.zip = zip;
            this.dir = dir;
        }

        private byte[] entry(String name, long max) throws IOException {
            ZipEntry e = zip.getEntry(dir + name);
            if (e == null) return null;
            try (InputStream in = zip.getInputStream(e)) {
                return RegionFile.readAll(in, max, dir + name);
            }
        }

        @Override
        public RegionFile.Chunk chunk(int cx, int cz) throws IOException {
            long key = ((long) (cx >> 5) << 32) ^ ((cz >> 5) & 0xffffffffL);
            RegionFile.Bytes b = open.get(key);
            if (b == null && !open.containsKey(key)) {
                open.clear(); // una sola región del .zip en memoria (Restore las recorre de a una)
                byte[] data = entry("r." + (cx >> 5) + "." + (cz >> 5) + ".mca", RegionFile.MAX_REGION);
                b = data == null ? null : RegionFile.of(data);
                open.put(key, b);
            }
            if (b == null) return RegionFile.Chunk.missing();
            return RegionFile.chunk(b, (x, z, max) -> entry("c." + x + "." + z + ".mcc", max), cx, cz);
        }

        @Override
        public void close() throws IOException {
            open.clear();
            zip.close();
        }
    }
}
