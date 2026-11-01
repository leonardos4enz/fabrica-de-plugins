package dev.leo.skinstatues.skin;

import org.bukkit.configuration.file.YamlConfiguration;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.Locale;

/**
 * De dónde sale la skin (H2): primero skins/&lt;name&gt;.png del dueño; si no hay y la red está activada, la caché o
 * Mojang. Bloqueante: corre en un hilo asíncrono.
 */
public final class SkinRepository {
    public record Result(Skin skin, String source) {
    }

    private static final java.util.regex.Pattern FILE = java.util.regex.Pattern.compile("[A-Za-z0-9_.-]{1,64}\\.png");

    private final Path skins, cache;
    private final MojangClient client;
    private final Clock clock;

    public SkinRepository(Path skins, Path cache, MojangClient client, Clock clock) {
        this.skins = skins;
        this.cache = cache;
        this.client = client;
        this.clock = clock;
    }

    /**
     * @param network    network.mojang de config.yml
     * @param cacheHours cuánto vale lo bajado antes de volver a preguntar
     */
    public Result load(String name, boolean network, int cacheHours) throws SkinException {
        if (!MojangClient.NAME.matcher(name).matches()) throw new SkinException("invalid-name", "name", name);
        Path local = find(skins, name + ".png");
        if (local != null) return fromFile(local, name);
        if (!network) throw new SkinException("skin-not-found", "name", name, "folder", "plugins/SkinStatues/skins/");

        String key = name.toLowerCase(Locale.ROOT);
        Path png = cache.resolve(key + ".png"), meta = cache.resolve(key + ".yml"), capePng = cache.resolve(key + "-cape.png");
        YamlConfiguration cached = readMeta(meta);
        boolean fresh = cached != null && Files.exists(png)
                && clock.millis() - cached.getLong("time") < cacheHours * 3_600_000L;
        if (fresh) return fromCache(png, capePng, cached, name);
        try {
            MojangClient.Profile p = client.fetch(name);
            Skin skin = Skin.of(SkinPng.read(p.skin(), SkinPng.Kind.SKIN, name + " (Mojang)"), p.slim(),
                    p.cape() == null ? null : SkinPng.read(p.cape(), SkinPng.Kind.CAPE, name + " cape (Mojang)"));
            save(png, p.skin());
            if (p.cape() != null) save(capePng, p.cape());
            else Files.deleteIfExists(capePng);
            YamlConfiguration m = new YamlConfiguration();
            m.set("name", p.name());
            m.set("uuid", p.uuid());
            m.set("model", p.slim() ? "slim" : "classic");
            m.set("cape", p.cape() != null);
            m.set("time", clock.millis());
            save(meta, m.saveToString().getBytes(StandardCharsets.UTF_8));
            return new Result(skin, "Mojang");
        } catch (SkinException e) {
            // Sin respuesta de Mojang, lo que ya se bajó una vez sigue sirviendo.
            if (cached != null && Files.exists(png) && e.key().equals("mojang-error")) return fromCache(png, capePng, cached, name);
            throw e;
        } catch (IOException e) {
            throw new SkinException("mojang-error", "name", name, "reason", "could not write the cache");
        }
    }

    private Result fromCache(Path png, Path capePng, YamlConfiguration meta, String name) throws SkinException {
        BufferedImage cape = meta.getBoolean("cape") && Files.exists(capePng)
                ? SkinPng.read(bytes(capePng), SkinPng.Kind.CAPE, capePng.getFileName().toString()) : null;
        return new Result(Skin.of(SkinPng.read(bytes(png), SkinPng.Kind.SKIN, name + " (cache)"),
                "slim".equals(meta.getString("model")), cape), "cache");
    }

    private Result fromFile(Path file, String name) throws SkinException {
        boolean slim = false;
        BufferedImage cape = null;
        Path opts = find(skins, file.getFileName().toString().replaceAll("(?i)\\.png$", "") + ".yml");
        if (opts != null) {
            YamlConfiguration y = new YamlConfiguration();
            try {
                y.load(new StringReader(new String(bytes(opts), StandardCharsets.UTF_8)));
            } catch (Exception e) {
                throw new SkinException("image-invalid", "file", opts.getFileName().toString(), "reason", "invalid YAML");
            }
            slim = "slim".equalsIgnoreCase(y.getString("model", "classic"));
            String capeName = y.getString("cape");
            if (capeName != null) {
                if (!FILE.matcher(capeName).matches()) {
                    throw new SkinException("image-invalid", "file", opts.getFileName().toString(),
                            "reason", "'cape' must be a .png file name inside skins/");
                }
                Path c = find(skins, capeName);
                if (c == null) throw new SkinException("cape-not-found", "file", capeName);
                cape = SkinPng.read(bytes(c), SkinPng.Kind.CAPE, capeName);
            }
        }
        BufferedImage img = SkinPng.read(bytes(file), SkinPng.Kind.SKIN, file.getFileName().toString());
        return new Result(Skin.of(img, slim, cape), "skins/" + file.getFileName());
    }

    /** Busca un archivo por nombre sin distinguir mayúsculas, solo dentro de la carpeta (sin rutas). */
    static Path find(Path dir, String fileName) {
        if (!Files.isDirectory(dir)) return null;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (p.getFileName().toString().equalsIgnoreCase(fileName) && Files.isRegularFile(p)) return p;
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static byte[] bytes(Path p) throws SkinException {
        try {
            if (Files.size(p) > SkinPng.MAX_BYTES) {
                throw new SkinException("image-invalid", "file", p.getFileName().toString(), "reason", "larger than 1 MB");
            }
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new SkinException("image-invalid", "file", p.getFileName().toString(), "reason", "unreadable file");
        }
    }

    private static YamlConfiguration readMeta(Path meta) {
        if (!Files.exists(meta)) return null;
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.load(meta.toFile());
            return y;
        } catch (Exception e) {
            return null;
        }
    }

    /** Escritura atómica: temporal y reemplazo (constitución II.6). */
    static void save(Path target, byte[] data) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, data);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
