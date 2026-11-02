package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.color.BlockColors;
import dev.leo.worldcraft.config.CreateSettings;
import dev.leo.worldcraft.create.ImageBlocks;
import dev.leo.worldcraft.net.ImageLink;
import dev.leo.worldcraft.net.LinkClient;
import dev.leo.worldcraft.net.LinkException;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Spec 035 H4 (plan §1.4): la imagen de la carpeta o de un link, leída, achicada y hecha bloques en un hilo propio (una
 * descarga lenta no frena las fórmulas). La obra la pide el comando, de vuelta en el hilo principal.
 */
public final class Images {
    /** Solo con la línea de comandos de Java (la suite en el juego): deja bajar del HTTPS local, a cualquier puerto. */
    public static final boolean LOCAL_LINKS = Boolean.getBoolean("worldcraft.test.localLinks");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]+\\.(?i:png|jpe?g|gif)");

    /** Una imagen lista: la etiqueta (archivo o url:sitio#huella), su tamaño original y los índices de la tabla. */
    public record Loaded(String label, int sourceWidth, int sourceHeight, int width, int height, int[] blocks) {
    }

    /** Falla con la clave de messages.yml y sus valores. */
    public static final class Problem extends RuntimeException {
        final String key;
        final String[] args;

        Problem(String key, String... args) {
            super(key, null, false, false);
            this.key = key;
            this.args = args;
        }

        public String key() {
            return key;
        }

        /** Pares clave, valor para el mensaje. */
        public Object[] args() {
            return args.clone();
        }
    }

    private final Worldcraft plugin;
    private final ExecutorService thread;
    private final LinkClient client;

    public Images(Worldcraft plugin, ExecutorService thread) {
        this.plugin = plugin;
        this.thread = thread;
        this.client = new LinkClient(LinkClient.realHttp(), LinkClient.realResolver(), LOCAL_LINKS);
        if (LOCAL_LINKS) plugin.getLogger().warning("worldcraft.test.localLinks is on: /wc image may download from local addresses (tests only)");
        try {
            Files.createDirectories(folder()); // así el dueño ve dónde van las imágenes
        } catch (IOException e) {
            plugin.getLogger().warning("Could not create the images folder: " + e.getMessage());
        }
    }

    private CreateSettings settings() {
        return plugin.config().create();
    }

    public Path folder() {
        return plugin.getDataFolder().toPath().resolve(settings().imagesFolder());
    }

    /** Los archivos de imagen de la carpeta, por nombre. */
    public List<String> files() {
        Path dir = folder();
        if (!Files.isDirectory(dir)) return List.of();
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).filter(n -> NAME.matcher(n).matches()).sorted().forEach(out::add);
        } catch (IOException e) {
            return List.of();
        }
        return out;
    }

    /**
     * El archivo de la carpeta con ese nombre (H4 CA3): solo letras, números y . _ - con extensión de imagen, sin rutas,
     * y la ruta resuelta tiene que quedar dentro de la carpeta.
     */
    Path file(String name) {
        if (!NAME.matcher(name).matches() || name.contains("..")) throw new Problem("image-bad-name", "name", name);
        Path dir = folder().toAbsolutePath().normalize();
        Path f = dir.resolve(name).normalize();
        if (!f.startsWith(dir)) throw new Problem("image-bad-name", "name", name);
        if (!Files.isRegularFile(f)) {
            String hint = dev.leo.worldcraft.config.Issues.closest(name, files());
            throw new Problem("image-not-found", "name", name, "folder", plugin.getDataFolder().getName() + "/" + settings().imagesFolder(),
                    "hint", hint == null ? "" : " (did you mean '" + hint + "'?)");
        }
        return f;
    }

    /** Si el texto es un link (y no el nombre de un archivo). */
    public static boolean isLink(String text) {
        return ImageLink.looksLike(text);
    }

    /** La etiqueta de un link sin bajarlo (para el historial); lanza Problem si no es válido. */
    public ImageLink link(String text) {
        try {
            return ImageLink.parse(text, LOCAL_LINKS);
        } catch (LinkException e) {
            throw problem(e);
        }
    }

    /**
     * Lee o baja la imagen, la deja con side bloques en el lado más largo (0 = el suyo, como mucho images.max_size) y elige
     * el bloque de cada píxel; sigue en el hilo principal con then. Un problema se le dice a quien lo pidió.
     */
    public void load(CommandSender s, String source, int side, Consumer<Loaded> then) {
        CreateSettings cfg = settings();
        BlockColors colors = plugin.config().parser().colors();
        int maxBytes = cfg.maxFileKb() * 1024;
        // Lo que toca la configuración y la carpeta se resuelve aquí (hilo principal); lo lento va al hilo de imágenes.
        ImageLink link = isLink(source) ? link(source) : null;
        Path file = link == null ? file(source) : null;
        String label = link == null ? file.getFileName().toString() : link.label();
        List<String> hosts = cfg.allowedHosts();
        int maxSize = cfg.imagesMaxSize();
        CompletableFuture.supplyAsync(() -> {
            byte[] bytes;
            if (link != null) {
                try {
                    bytes = client.fetch(link, hosts, maxBytes);
                } catch (LinkException e) {
                    throw problem(e);
                }
            } else {
                try {
                    if (Files.size(file) > maxBytes) throw new Problem("image-invalid", "name", label, "reason",
                            "the file is larger than " + cfg.maxFileKb() + " KB (images.max_file_kb)");
                    try (InputStream in = Files.newInputStream(file)) {
                        bytes = in.readNBytes(maxBytes + 1);
                    }
                } catch (IOException e) {
                    throw new Problem("image-invalid", "name", label, "reason", "it could not be read");
                }
            }
            ImageBlocks.Pixels px;
            try {
                px = ImageBlocks.read(bytes);
            } catch (IllegalArgumentException e) {
                throw new Problem("image-invalid", "name", label, "reason", e.getMessage());
            }
            int target = side > 0 ? side : Math.min(Math.max(px.width(), px.height()), maxSize);
            ImageBlocks.Pixels scaled = ImageBlocks.scale(px, target);
            return new Loaded(label, px.width(), px.height(), scaled.width(), scaled.height(), ImageBlocks.blocks(scaled, colors));
        }, thread).whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err == null) {
                then.accept(v);
                return;
            }
            Throwable e = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
            if (e instanceof Problem p) {
                s.sendMessage(plugin.messages().get(p.key, p.args()));
            } else {
                plugin.getLogger().log(Level.SEVERE, "Reading an image failed", e);
                s.sendMessage(plugin.messages().get("compute-error"));
            }
        }));
    }

    private static Problem problem(LinkException e) {
        Object[] a = e.args();
        String[] args = new String[a.length];
        for (int i = 0; i < a.length; i++) args[i] = String.valueOf(a[i]);
        return new Problem(e.key(), args);
    }

    /** Para mensajes: «64×32». */
    public static String size(int w, int h) {
        return String.format(Locale.ROOT, "%d×%d", w, h);
    }
}
