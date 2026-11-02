package dev.leo.worldcraft.snapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Lo de regen que no necesita a Paper (plan 036 §1.1): la semilla que se escribe y las carpetas de los mundos temporales.
 * Una carpeta solo se borra si se llama worldcraft_regen_* y tiene la marca que Worldcraft escribe antes de crear el mundo
 * (STRIDE: nunca un mundo del dueño).
 */
public final class TempWorlds {
    public static final String PREFIX = "worldcraft_regen_";
    public static final String MARK = "worldcraft-temporary-world.txt";

    private TempWorlds() {
    }

    /**
     * La semilla: ninguna (null o vacío) = la del mundo; «random» = al azar; un entero = ese; otra palabra, como el juego
     * con level-seed (su hashCode).
     */
    public static long seed(String text, long worldSeed, RandomGenerator random) {
        if (text == null || text.isBlank()) return worldSeed;
        String t = text.trim();
        if (t.equalsIgnoreCase("random")) return random.nextLong();
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return t.hashCode();
        }
    }

    /** ¿Parece una semilla y no un bioma? (un entero o «random») */
    public static boolean looksLikeSeed(String text) {
        return text.equalsIgnoreCase("random") || text.matches("-?\\d{1,19}");
    }

    /** Escribe la marca en una carpeta nueva (antes de crear el mundo en ella). */
    public static void mark(Path dir, String source) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(MARK), "Temporary world of Worldcraft /wc regen (a copy of '" + source
                + "'). It is deleted by itself; you can delete it while the server is off.\n", StandardCharsets.UTF_8);
    }

    /** ¿Es una carpeta de mundo temporal que se puede borrar? */
    public static boolean deletable(Path dir) {
        String n = dir.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.startsWith(PREFIX) && Files.isDirectory(dir) && Files.isRegularFile(dir.resolve(MARK));
    }

    /** Las carpetas temporales que quedaron en la carpeta de mundos (except: las que están cargadas). */
    public static List<Path> leftovers(Path container, java.util.Set<String> except) throws IOException {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(container)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(container, PREFIX + "*")) {
            for (Path p : ds) if (deletable(p) && !except.contains(p.getFileName().toString())) out.add(p);
        }
        return out;
    }

    /** Borra la carpeta entera, si es una temporal (si no, no toca nada y devuelve false). */
    public static boolean delete(Path dir) throws IOException {
        if (!deletable(dir)) return false;
        Path mark = dir.resolve(MARK);
        // La marca se borra al final: si algo falla a la mitad (un archivo abierto), se puede reintentar.
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.equals(mark)) Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                if (e != null) throw e;
                if (!d.equals(dir)) Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
        Files.delete(mark);
        Files.delete(dir);
        return true;
    }
}
