package dev.leo.worldcraft.schem;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Las carpetas de esquemas (spec 027 H3, H4, H5 CA1): la propia, donde se escribe, y las de solo lectura (WorldEdit,
 * FAWE). Un nombre nunca sale de su carpeta: lista blanca de caracteres, sin «..» ni rutas absolutas, y la ruta real
 * (con los enlaces resueltos) tiene que seguir adentro. Solo E/S: corre en el hilo de esquemas, nunca en el principal.
 */
public final class SchemFiles {
    public static final String EXT = ".schem";
    public static final int MAX_LIST = 5000;
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_+\\-][A-Za-z0-9_.+\\-]{0,63}");
    private static final int MAX_SEGMENTS = 4;

    /** Un esquema encontrado: su nombre, dónde está y de qué carpeta (null = la propia; si no, la etiqueta). */
    public record Entry(String name, Path path, String folder, long bytes, long modified) {
    }

    /** Una carpeta de solo lectura con su etiqueta para la lista («WorldEdit»). */
    public record Extra(Path folder, String label) {
    }

    /** Un nombre o un archivo que no sirve; el mensaje en inglés va al jugador. */
    public static final class SchemFileException extends IOException {
        public SchemFileException(String message) {
            super(message);
        }
    }

    private final Path own;
    private final List<Extra> extra;

    public SchemFiles(Path own, List<Extra> extra) {
        this.own = own;
        this.extra = List.copyOf(extra);
    }

    public Path own() {
        return own;
    }

    /** El nombre limpio («castles/tower») o el motivo por el que no sirve. Sin E/S: también en el hilo principal. */
    public static String clean(String name) throws SchemFileException {
        String n = name.trim();
        if (n.toLowerCase(Locale.ROOT).endsWith(EXT)) n = n.substring(0, n.length() - EXT.length());
        if (n.isEmpty()) throw new SchemFileException("Give the schematic a name");
        if (n.contains("\\") || n.contains(":") || n.startsWith("/") || n.endsWith("/")) {
            throw new SchemFileException("'" + name + "' is not a valid name: use letters, numbers, _ and -, and / for folders");
        }
        String[] parts = n.split("/", -1);
        if (parts.length > MAX_SEGMENTS) throw new SchemFileException("'" + name + "' has too many folders (at most " + (MAX_SEGMENTS - 1) + ")");
        for (String p : parts) {
            if (!SEGMENT.matcher(p).matches() || p.equals("..") || p.contains("..")) {
                throw new SchemFileException("'" + name + "' is not a valid name: use letters, numbers, _ and -, and / for folders");
            }
        }
        return n;
    }

    /** El archivo de ese nombre dentro de folder, comprobado contra los enlaces que salen; null si no existe. */
    private static Path inside(Path folder, String clean) throws IOException {
        Path f = folder.resolve(clean + EXT).normalize();
        if (!f.startsWith(folder.normalize())) throw new SchemFileException("That name points outside the schematics folder");
        if (!Files.exists(f)) return null;
        Path realFolder = folder.toRealPath();
        if (!f.toRealPath().startsWith(realFolder)) throw new SchemFileException("That schematic is a link that points outside its folder");
        if (!Files.isRegularFile(f)) return null;
        return f;
    }

    /** Busca primero en la carpeta propia y después en las de solo lectura (H3 CA1); null si no está. */
    public Entry find(String name, boolean extraToo) throws IOException {
        String n = clean(name);
        if (Files.isDirectory(own)) {
            Path f = inside(own, n);
            if (f != null) return entry(n, f, null);
        }
        if (extraToo) {
            for (Extra e : extra) {
                if (!Files.isDirectory(e.folder())) continue;
                Path f = inside(e.folder(), n);
                if (f != null) return entry(n, f, e.label());
            }
        }
        return null;
    }

    private static Entry entry(String n, Path f, String label) throws IOException {
        return new Entry(n, f, label, Files.size(f), Files.getLastModifiedTime(f).toMillis());
    }

    /** Todos, la propia primero; un nombre de la propia tapa al de otra carpeta. Ordenados por nombre. */
    public List<Entry> list() throws IOException {
        List<Entry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        walk(own, null, "", 0, out, seen);
        for (Extra e : extra) walk(e.folder(), e.label(), "", 0, out, seen);
        out.sort(Comparator.comparing((Entry e) -> e.name().toLowerCase(Locale.ROOT)).thenComparing(e -> e.folder() == null ? "" : e.folder()));
        return out;
    }

    private static void walk(Path dir, String label, String prefix, int depth, List<Entry> out, Set<String> seen) throws IOException {
        if (depth >= MAX_SEGMENTS || !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) || out.size() >= MAX_LIST) return;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (out.size() >= MAX_LIST) return;
                String f = p.getFileName().toString();
                if (f.startsWith(".") || Files.isSymbolicLink(p)) continue;
                if (Files.isDirectory(p)) {
                    if (SEGMENT.matcher(f).matches()) walk(p, label, prefix + f + "/", depth + 1, out, seen);
                } else if (f.toLowerCase(Locale.ROOT).endsWith(EXT)) {
                    String n = prefix + f.substring(0, f.length() - EXT.length());
                    if (!SEGMENT.matcher(f.substring(0, f.length() - EXT.length())).matches()) continue;
                    if (!seen.add(n.toLowerCase(Locale.ROOT))) continue;
                    out.add(entry(n, p, label));
                }
            }
        }
    }

    /**
     * Escribe en la carpeta propia de forma atómica (temporal + mover, II.6). Un nombre que existe no se pisa sin
     * overwrite (H1 CA4).
     */
    public Path write(String name, byte[] bytes, boolean overwrite) throws IOException {
        String n = clean(name);
        Path f = own.resolve(n + EXT).normalize();
        if (!f.startsWith(own.normalize())) throw new SchemFileException("That name points outside the schematics folder");
        Files.createDirectories(f.getParent());
        if (!f.getParent().toRealPath().startsWith(own.toRealPath())) throw new SchemFileException("That folder is a link that points outside the schematics folder");
        if (Files.exists(f, LinkOption.NOFOLLOW_LINKS)) {
            if (!overwrite) throw new FileAlreadyExistsException(n);
            if (Files.isSymbolicLink(f) || !Files.isRegularFile(f)) throw new SchemFileException("'" + n + "' is not a file that can be replaced");
        }
        Path tmp = Files.createTempFile(f.getParent(), ".tmp-", EXT);
        try {
            Files.write(tmp, bytes);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
        return f;
    }

    /** Lo que hizo /wc schem import (spec 029 H5): copiados, los que ya estaban y los que pasan del tope. */
    public record Imported(int copied, int skipped, int tooBig) {
    }

    /**
     * Spec 029 H5 CA1: copia los .schem de las carpetas de solo lectura (WorldEdit, FAWE) a la propia, con su subcarpeta.
     * Los originales no se tocan; uno que ya existe en la propia no se pisa sin overwrite; uno de más de maxBytes se
     * salta (el mismo tope que load). Mismo recorrido que list: sin enlaces, nombres seguros, hasta MAX_LIST.
     */
    public Imported importAll(boolean overwrite, long maxBytes) throws IOException {
        int copied = 0, skipped = 0, tooBig = 0;
        Set<String> done = new HashSet<>();
        for (Extra e : extra) {
            List<Entry> found = new ArrayList<>();
            walk(e.folder(), e.label(), "", 0, found, new HashSet<>());
            for (Entry en : found) {
                if (!done.add(en.name().toLowerCase(Locale.ROOT))) continue; // el mismo nombre en WorldEdit y FAWE: el primero
                if (en.bytes() > maxBytes) {
                    tooBig++;
                    continue;
                }
                if (!overwrite && Files.isDirectory(own) && inside(own, en.name()) != null) {
                    skipped++;
                    continue;
                }
                write(en.name(), Files.readAllBytes(en.path()), overwrite);
                copied++;
            }
        }
        return new Imported(copied, skipped, tooBig);
    }

    /** Lo pasa a .trash/ (H4 CA2): solo de la carpeta propia. false si no existe. */
    public boolean trash(String name, long now) throws IOException {
        String n = clean(name);
        if (!Files.isDirectory(own)) return false;
        Path f = inside(own, n);
        if (f == null) return false;
        Path trash = own.resolve(".trash");
        Files.createDirectories(trash);
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date(now));
        Path to = trash.resolve(n.replace('/', '_') + "-" + stamp + EXT);
        for (int i = 2; Files.exists(to); i++) to = trash.resolve(n.replace('/', '_') + "-" + stamp + "-" + i + EXT);
        Files.move(f, to, StandardCopyOption.ATOMIC_MOVE);
        // La papelera cuenta desde que se borró, no desde que se guardó.
        Files.setLastModifiedTime(to, java.nio.file.attribute.FileTime.fromMillis(now));
        return true;
    }

    /** Vacía de la papelera lo de más de days días (al arrancar). Devuelve cuántos borró. */
    public int purgeTrash(long now, int days) throws IOException {
        Path trash = own.resolve(".trash");
        if (!Files.isDirectory(trash, LinkOption.NOFOLLOW_LINKS)) return 0;
        long limit = now - days * 86_400_000L;
        int n = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(trash)) {
            for (Path p : ds) {
                if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && Files.getLastModifiedTime(p).toMillis() <= limit) {
                    Files.delete(p);
                    n++;
                }
            }
        }
        return n;
    }
}
