package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.structure.Templates;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.loot.Lootable;
import org.bukkit.structure.Palette;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Las plantillas de estructuras del juego (spec 033 H7, plan §1.5). Paper no da la lista: se leen los nombres de
 * {@code data/<ns>/structure/**.nbt} del jar del servidor y de los paquetes de datos de los mundos (en el hilo de E/S, al
 * habilitar), más las que conoce el {@code StructureManager}. Cargar una la deja en el portapapeles.
 */
public final class Structures {
    /** Un dato de bloque propio: la tabla de botín de un cofre (lo pone {@link PaperWorld#restoreTile}). */
    public static final byte LOOT = 'L';

    private final Worldcraft plugin;
    private final Set<String> names = new ConcurrentSkipListSet<>();

    public Structures(Worldcraft plugin) {
        this.plugin = plugin;
    }

    /** Arma la lista en el hilo de E/S. */
    public void scan() {
        List<File> worlds = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) worlds.add(w.getWorldFolder());
        ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
        plugin.io().execute(() -> {
            try {
                URL u = cl.getResource("data/minecraft/structure/igloo/top.nbt");
                if (u != null && u.openConnection() instanceof JarURLConnection jc) {
                    try (JarFile jar = new JarFile(new File(jc.getJarFileURL().toURI()))) {
                        Enumeration<JarEntry> en = jar.entries();
                        while (en.hasMoreElements()) add(en.nextElement().getName());
                    }
                }
                for (File w : worlds) scanWorld(w.toPath());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not list the game's structures (/wc structure list); loading by name still works", e);
            }
        });
    }

    private void scanWorld(Path world) throws IOException {
        Path packs = world.resolve("datapacks");
        if (Files.isDirectory(packs)) {
            try (Stream<Path> s = Files.list(packs)) {
                for (Path p : s.toList()) {
                    if (Files.isDirectory(p)) walk(p, p);
                    else if (p.toString().endsWith(".zip")) {
                        try (ZipFile z = new ZipFile(p.toFile())) {
                            Enumeration<? extends ZipEntry> en = z.entries();
                            while (en.hasMoreElements()) add(en.nextElement().getName());
                        }
                    }
                }
            }
        }
        // Las guardadas con un bloque de estructura: generated/<ns>/structure/<ruta>.nbt
        Path gen = world.resolve("generated");
        if (Files.isDirectory(gen)) {
            try (Stream<Path> s = Files.walk(gen, 8)) {
                for (Path p : s.filter(f -> f.toString().endsWith(".nbt")).toList()) {
                    Path rel = gen.relativize(p);
                    if (rel.getNameCount() >= 3 && rel.getName(1).toString().equals("structure")) {
                        String path = rel.subpath(2, rel.getNameCount()).toString().replace('\\', '/');
                        put(rel.getName(0) + ":" + path.substring(0, path.length() - 4));
                    }
                }
            }
        }
    }

    private void walk(Path root, Path pack) throws IOException {
        try (Stream<Path> s = Files.walk(pack, 12)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".nbt")).toList()) add(root.relativize(p).toString().replace('\\', '/'));
        }
    }

    /** «data/minecraft/structure/igloo/top.nbt» → «minecraft:igloo/top». */
    private void add(String entry) {
        if (!entry.startsWith("data/") || !entry.endsWith(".nbt")) return;
        String[] parts = entry.split("/", 4);
        if (parts.length < 4 || !parts[2].equals("structure")) return;
        put(parts[1] + ":" + parts[3].substring(0, parts[3].length() - 4));
    }

    private void put(String key) {
        if (Templates.key(key) != null) names.add(key);
    }

    /** Todas las conocidas (las del jar, de los paquetes de datos y las que ya cargó el servidor). */
    public Set<String> all() {
        Set<String> out = new TreeSet<>(names);
        for (NamespacedKey k : Bukkit.getStructureManager().getStructures().keySet()) out.add(k.toString());
        return out;
    }

    /**
     * H7 CA2: la plantilla al portapapeles de quien la pide, con su esquina mínima a sus pies. palette: cuál de sus
     * paletas (una casa de aldea tiene una; un barco hundido, varias).
     */
    public void load(CommandSender s, String name, int palette) {
        Loaded t = template(s, name, palette);
        if (t == null) return;
        var msg = plugin.messages();
        Clipboard clip = t.clip();
        String worldName = s instanceof Player p ? p.getWorld().getName() : Bukkit.getWorlds().get(0).getName();
        plugin.edits().session(Edits.owner(s)).clipboard(clip, worldName);
        s.sendMessage(msg.get("structure-loaded", "name", t.key().replace("minecraft:", ""), "size", clip.width() + "×" + clip.height() + "×" + clip.depth(),
                "blocks", Edits.fmt(t.blocks()), "palette", t.palettes() > 1 ? " (palette " + palette + " of " + t.palettes() + ")" : ""));
        if (t.loot() > 0) s.sendMessage(msg.get("structure-loot", "count", String.valueOf(t.loot())));
        if (t.entities() > 0) s.sendMessage(msg.get("structure-entities", "count", String.valueOf(t.entities())));
    }

    /** Una plantilla leída: su portapapeles y lo que se dice de ella. */
    public record Loaded(String key, Clipboard clip, int blocks, int palettes, int loot, int entities) {
    }

    /** Spec 041: la plantilla como portapapeles nuevo (para /wc tool structureplacer), o null (y se dice por qué). */
    public Loaded template(CommandSender s, String name, int palette) {
        var msg = plugin.messages();
        String key = Templates.key(name);
        if (key == null) {
            s.sendMessage(msg.get("structure-bad-name", "name", name));
            return null;
        }
        Structure st;
        try {
            st = Bukkit.getStructureManager().loadStructure(NamespacedKey.fromString(key), false);
        } catch (RuntimeException e) {
            st = null;
        }
        if (st == null) {
            s.sendMessage(msg.get("structure-unknown", "name", key.replace("minecraft:", ""), "hint", Templates.hint(name, all())));
            return null;
        }
        List<Palette> palettes = st.getPalettes();
        if (palettes.isEmpty()) {
            s.sendMessage(msg.get("structure-empty", "name", key.replace("minecraft:", "")));
            return null;
        }
        if (palette < 0 || palette >= palettes.size()) {
            s.sendMessage(msg.get("structure-palette", "count", String.valueOf(palettes.size())));
            return null;
        }
        BlockVector size = st.getSize();
        int w = size.getBlockX(), h = size.getBlockY(), d = size.getBlockZ();
        PaperBlocks blocks = plugin.edits().blocks();
        int jigsaw = blocks.id(org.bukkit.Material.JIGSAW.createBlockData());
        // Los marcadores de datos (un bloque de estructura en modo data) los usa el juego al generar; quedan como aire.
        int marker = blocks.id(Bukkit.createBlockData("minecraft:structure_block[mode=data]"));
        List<Templates.Block> list = new ArrayList<>();
        int loot = 0;
        for (BlockState bs : palettes.get(palette).getBlocks()) {
            byte[] tile = null;
            if (bs instanceof Lootable l && l.getLootTable() != null) {
                byte[] k = l.getLootTable().getKey().toString().getBytes(StandardCharsets.UTF_8);
                tile = new byte[k.length + 1];
                tile[0] = LOOT;
                System.arraycopy(k, 0, tile, 1, k.length);
                loot++;
            }
            list.add(new Templates.Block(bs.getX(), bs.getY(), bs.getZ(), blocks.id(bs.getBlockData()), tile));
        }
        Clipboard clip = Templates.toClipboard(w, h, d, list, id -> id == jigsaw || id == marker, blocks.air());
        return new Loaded(key, clip, list.size(), palettes.size(), loot, st.getEntityCount());
    }
}
