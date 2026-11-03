package dev.leo.worldcraft.tool;

import java.nio.file.Path;

/**
 * Spec 041 H2 CA7: dónde se guarda un pincel con /wc brush savebrush: {@code brushes/<uuid>/<nombre>.txt}, o
 * {@code brushes/<nombre>.txt} con --global. Un nombre que pueda salir de la carpeta se rechaza. Java puro.
 */
public final class Brushes {
    private Brushes() {
    }

    public static final String NAME = "[A-Za-z0-9_-]{1,32}";

    /** El archivo del pincel; owner null = los de todos. */
    public static Path file(Path root, String owner, String name) {
        if (name == null || !name.matches(NAME)) {
            throw new IllegalArgumentException("A brush name is 1 to 32 letters, numbers, - or _ (got '" + name + "')");
        }
        Path dir = owner == null ? root : root.resolve(owner);
        Path f = dir.resolve(name + ".txt").normalize();
        if (!f.startsWith(root.normalize())) throw new IllegalArgumentException("That name leaves the brushes folder");
        return f;
    }
}
