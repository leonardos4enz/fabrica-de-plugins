package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.config.Issues;
import dev.leo.worldcraft.config.Settings;
import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.edit.TaskJob;
import dev.leo.worldcraft.edit.Transform;
import dev.leo.worldcraft.schem.Nbt;
import dev.leo.worldcraft.schem.SchemBlocks;
import dev.leo.worldcraft.schem.SchemConvert;
import dev.leo.worldcraft.schem.SchemFiles;
import dev.leo.worldcraft.schem.SchemFormat;
import dev.leo.worldcraft.schem.Schematic;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/**
 * /wc schem save|load|list|delete (spec 027). El disco, la compresión y el NBT van en el hilo de esquemas; en el hilo
 * principal solo la paleta, los giros de cada estado distinto y, por pasos dentro del planificador, los datos de bloque
 * con la API de Paper (plan 027 §1.1, §1.2, §3). Una operación de esquemas a la vez por jugador (H5 CA6).
 */
public final class Schematics {
    public static final String PERM = "worldcraft.schematic", PERM_DELETE = "worldcraft.schematic.delete";
    /** Spec 029 H5 CA2 (decisión del dueño): guardar e importar escriben en el disco, con su propio permiso. */
    public static final String PERM_SAVE = "worldcraft.schematic.save";
    private static final int PAGE = 10;

    private final Worldcraft plugin;
    private final ExecutorService thread;
    private final Executor main;
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private volatile List<SchemFiles.Entry> names = List.of();
    private volatile long namesAt;

    public Schematics(Worldcraft plugin, ExecutorService thread) {
        this.plugin = plugin;
        this.thread = thread;
        this.main = r -> {
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, r);
        };
    }

    private Settings.Schematics settings() {
        return plugin.config().settings().schematics();
    }

    /** Las carpetas según la configuración de ahora (las de solo lectura, relativas a la carpeta del servidor). */
    public SchemFiles files() {
        Path server = plugin.getDataFolder().getAbsoluteFile().toPath().getParent().getParent();
        List<SchemFiles.Extra> extra = new ArrayList<>();
        for (String f : settings().extraFolders()) {
            Path p = server.resolve(f).normalize();
            String label = p.getFileName() != null && p.getFileName().toString().equalsIgnoreCase("schematics") && p.getParent() != null
                    ? p.getParent().getFileName().toString() : String.valueOf(p.getFileName());
            extra.add(new SchemFiles.Extra(p, label));
        }
        return new SchemFiles(plugin.getDataFolder().toPath().resolve("schematics"), extra);
    }

    /** Al arrancar: la carpeta propia y la papelera vieja (H4 CA2). */
    public void start() {
        SchemFiles files = files();
        int days = settings().trashDays();
        thread.execute(() -> {
            try {
                Files.createDirectories(files.own());
                int n = files.purgeTrash(System.currentTimeMillis(), days);
                if (n > 0) plugin.getLogger().info("Emptied " + n + " old schematic(s) from schematics/.trash");
                names = files.list();
                namesAt = System.currentTimeMillis();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not prepare the schematics folder: " + e.getMessage());
            }
        });
    }

    private dev.leo.worldcraft.config.Messages msg() {
        return plugin.messages();
    }

    private boolean begin(CommandSender s, UUID owner) {
        if (busy.add(owner)) return true;
        s.sendMessage(msg().get("schem-busy"));
        return false;
    }

    private void fail(CommandSender s, String name, Throwable t) {
        s.sendMessage(failure(name, t));
    }

    private net.kyori.adventure.text.Component failure(String name, Throwable t) {
        Throwable e = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
        if (e instanceof FileAlreadyExistsException) return msg().get("schem-exists", "name", name);
        if (e instanceof NotFound) return msg().get("schem-not-found", "name", name, "hint", hint(name));
        if (e instanceof SchemFiles.SchemFileException || e instanceof Nbt.NbtException) {
            return msg().get("schem-bad", "name", name, "reason", e.getMessage());
        }
        if (e instanceof IOException) return msg().get("schem-io", "name", name, "reason", String.valueOf(e.getMessage()));
        plugin.getLogger().log(Level.WARNING, "Could not handle the schematic '" + name + "'", e);
        return msg().get("schem-error", "name", name);
    }

    private String hint(String name) {
        List<String> all = new ArrayList<>();
        for (SchemFiles.Entry e : names) all.add(e.name());
        String c = Issues.closest(name, all);
        return c == null ? "" : " (did you mean '" + c + "'?)";
    }

    private static final class NotFound extends IOException {
        NotFound() {
            super("not found");
        }
    }

    // ------------------------------------------------------------------ cargar

    private record Read(SchemFiles.Entry entry, Schematic schem, long[] counts) {
    }

    public void load(CommandSender s, String name) {
        String clean;
        try {
            clean = SchemFiles.clean(name);
        } catch (SchemFiles.SchemFileException e) {
            s.sendMessage(msg().get("error", "message", e.getMessage()));
            return;
        }
        UUID owner = Edits.owner(s);
        if (!begin(s, owner)) return;
        SchemFiles files = files();
        s.sendMessage(msg().get("schem-loading", "name", clean));
        read(clean).whenCompleteAsync((o, t) -> {
            busy.remove(owner);
            if (t != null) {
                fail(s, clean, t);
                return;
            }
            Read r = (Read) o[0];
            SchemBlocks.Result res = (SchemBlocks.Result) o[1];
            SchemConvert.Loaded l = (SchemConvert.Loaded) o[2];
            Session ses = plugin.edits().session(owner);
            ses.clipboard(l.clipboard(), s instanceof Player p ? p.getWorld().getName() : null);
            // Spec 041: un esquema solo deja de lado los de loadall; schem move mueve este archivo.
            ses.clipboards = List.of();
            ses.loadedFile = r.entry.folder() == null ? r.entry.path() : null;
            Schematic sc = r.schem;
            s.sendMessage(msg().get("schem-loaded", "name", clean, "folder", r.entry.folder() == null ? "" : " [" + r.entry.folder() + "]",
                    "version", String.valueOf(sc.version()), "size", sc.width() + "×" + sc.height() + "×" + sc.length(), "blocks", Edits.fmt(sc.volume())));
            notes(s, sc, res, l);
            refreshNames(files);
        }, main);
    }

    /** Spec 041 H4 CA8: los máximos esquemas de un loadall. */
    public static final int MAX_LOADALL = 50;

    /**
     * Spec 041 H4 CA8: /wc schem loadall: cada nombre, o cada esquema de una carpeta («trees/»), a la lista de la sesión;
     * paste pega uno al azar. El primero queda también como el portapapeles.
     */
    public void loadAll(CommandSender s, List<String> inputs) {
        List<String> names = new ArrayList<>();
        List<String> known = names(false);
        for (String in : inputs) {
            String prefix = in.endsWith("/") ? in : in + "/";
            List<String> inFolder = new ArrayList<>();
            for (String n : known) if (n.startsWith(prefix)) inFolder.add(n);
            if (inFolder.isEmpty()) names.add(in);
            else names.addAll(inFolder);
        }
        if (names.size() > MAX_LOADALL) {
            s.sendMessage(msg().get("schem-loadall-too-many", "count", String.valueOf(names.size()), "max", String.valueOf(MAX_LOADALL)));
            return;
        }
        UUID owner = Edits.owner(s);
        if (!begin(s, owner)) return;
        s.sendMessage(msg().get("schem-loading", "name", names.size() + " schematics"));
        List<CompletableFuture<Clipboard>> loads = new ArrayList<>();
        for (String n : names) loads.add(loadClipboard(n));
        CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenCompleteAsync((v, t) -> {
            busy.remove(owner);
            List<Clipboard> clips = new ArrayList<>();
            List<String> failed = new ArrayList<>();
            for (int i = 0; i < loads.size(); i++) {
                if (loads.get(i).isCompletedExceptionally()) failed.add(names.get(i));
                else clips.add(loads.get(i).join());
            }
            if (clips.isEmpty()) {
                fail(s, String.join(", ", failed), t == null ? new IllegalArgumentException("nothing loaded") : t.getCause() == null ? t : t.getCause());
                return;
            }
            Session ses = plugin.edits().session(owner);
            ses.clipboard(clips.get(0), s instanceof Player p ? p.getWorld().getName() : null);
            ses.clipboards = List.copyOf(clips);
            ses.loadedFile = null;
            s.sendMessage(msg().get("schem-loadall-done", "count", String.valueOf(clips.size()),
                    "failed", failed.isEmpty() ? "" : " (could not load: " + String.join(", ", failed) + ")"));
        }, main);
    }

    /** Spec 041 H4 CA8: /wc schem move &lt;carpeta&gt;: el último esquema cargado (de la carpeta propia) a otra carpeta. */
    public void move(CommandSender s, String folder) {
        Session ses = plugin.edits().session(Edits.owner(s));
        Path from = ses.loadedFile;
        if (from == null) {
            s.sendMessage(msg().get("schem-move-nothing"));
            return;
        }
        if (!folder.matches("[A-Za-z0-9_-]{1,32}(/[A-Za-z0-9_-]{1,32}){0,4}/?")) {
            s.sendMessage(msg().get("error", "message", "A folder is letters, numbers, - and _, with / between folders (like trees/oak)"));
            return;
        }
        Path own = files().own().normalize();
        Path to = own.resolve(folder.replaceAll("/$", "")).resolve(from.getFileName()).normalize();
        if (!to.startsWith(own)) {
            s.sendMessage(msg().get("error", "message", "That folder leaves the schematics folder"));
            return;
        }
        thread.execute(() -> {
            String result;
            try {
                if (Files.exists(to)) throw new java.nio.file.FileAlreadyExistsException(own.relativize(to).toString());
                Files.createDirectories(to.getParent());
                Files.move(from, to);
                result = null;
            } catch (java.nio.file.FileAlreadyExistsException e) {
                result = "exists";
            } catch (IOException e) {
                result = e.getMessage();
            }
            String r = result;
            main.execute(() -> {
                String a = own.relativize(from.normalize()).toString().replace('\\', '/'), b = own.relativize(to).toString().replace('\\', '/');
                if (r == null) {
                    ses.loadedFile = to;
                    s.sendMessage(msg().get("schem-moved", "from", a, "to", b));
                } else if (r.equals("exists")) {
                    s.sendMessage(msg().get("schem-move-exists", "to", b));
                } else {
                    s.sendMessage(msg().get("error", "message", "Could not move " + a + ": " + r));
                }
                refreshNames(files());
            });
        });
    }

    /**
     * Spec 037: un esquema leído a un portapapeles nuevo (no el de nadie), para la API. Un nombre que no sirve, que no
     * está o un archivo roto falla con IllegalArgumentException y el mismo texto que /wc schem load.
     */
    public CompletableFuture<Clipboard> loadClipboard(String name) {
        String clean;
        try {
            clean = SchemFiles.clean(name);
        } catch (SchemFiles.SchemFileException e) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(e.getMessage()));
        }
        CompletableFuture<Clipboard> out = new CompletableFuture<>();
        read(clean).whenCompleteAsync((o, t) -> {
            if (t != null) out.completeExceptionally(new IllegalArgumentException(
                    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(failure(clean, t))));
            else out.complete(((SchemConvert.Loaded) o[2]).clipboard());
        }, main);
        return out;
    }

    /** El disco y el NBT en el hilo de esquemas, la paleta en el principal: {Read, SchemBlocks.Result, SchemConvert.Loaded}. */
    private CompletableFuture<Object[]> read(String clean) {
        SchemFiles files = files();
        Settings.Schematics st = settings();
        long maxBytes = st.maxFileKb() * 1024L;
        int dv = TileNbt.serverDataVersion();
        return CompletableFuture.supplyAsync(() -> {
            try {
                SchemFiles.Entry e = files.find(clean, true);
                if (e == null) throw new NotFound();
                if (e.bytes() > maxBytes) {
                    throw new SchemFiles.SchemFileException("the file is " + e.bytes() / 1024 + " KB; the limit is " + st.maxFileKb() + " KB (schematics.max_file_kb)");
                }
                Schematic sc = SchemFormat.read(Files.readAllBytes(e.path()), st.maxBlocks());
                return new Read(e, sc, SchemBlocks.counts(sc));
            } catch (IOException ex) {
                throw new CompletionException(ex);
            }
        }, thread).thenApplyAsync(r -> {
            // Hilo principal: la paleta (pocas entradas) con el registro y la tabla de estados.
            PaperBlocks blocks = plugin.edits().blocks();
            SchemBlocks.Result res = SchemBlocks.resolve(r.schem.palette(), r.counts, blocks, blocks.table());
            Set<Integer> air = new HashSet<>();
            for (int id : res.map()) if (blocks.isAir(id)) air.add(id);
            // Spec 033: los biomas del servidor, leídos aquí (el registro, en el hilo principal).
            return new Object[]{r, res, Set.copyOf(air), Set.copyOf(PaperBiomes.names())};
        }, main).thenApplyAsync(o -> {
            Read r = (Read) o[0];
            @SuppressWarnings("unchecked")
            Set<Integer> air = (Set<Integer>) o[2];
            @SuppressWarnings("unchecked")
            Set<String> biomes = (Set<String>) o[3];
            SchemConvert.Loaded l = SchemConvert.toClipboard(r.schem, ((SchemBlocks.Result) o[1]).map(), air::contains, dv, biomes::contains);
            return new Object[]{r, o[1], l};
        }, thread);
    }

    private void notes(CommandSender s, Schematic sc, SchemBlocks.Result res, SchemConvert.Loaded l) {
        if (l.placeable() > 0) s.sendMessage(msg().get("schem-note-tiles", "count", String.valueOf(l.placeable())));
        if (!l.kept().isEmpty()) {
            int n = l.kept().values().stream().mapToInt(Integer::intValue).sum();
            s.sendMessage(msg().get("schem-note-kept", "count", String.valueOf(n), "kinds", top(l.kept(), 4)));
        }
        if (res.unknown() > 0) {
            Map<String, Long> named = new java.util.LinkedHashMap<>();
            res.unknownByName().forEach((k, v) -> named.put(k.replace("minecraft:", ""), v));
            s.sendMessage(msg().get("schem-note-unknown", "count", Edits.fmt(res.unknown()), "kinds", top(named, 5)));
        }
        if (res.adjusted() > 0) s.sendMessage(msg().get("schem-note-adjusted", "count", String.valueOf(res.adjusted())));
        if (res.renamed() > 0) s.sendMessage(msg().get("schem-note-renamed", "count", String.valueOf(res.renamed())));
        // Spec 031 H6 CA2: las entidades vienen con el esquema (se pegan con los bloques; --no-entities las deja).
        if (!sc.entityList().isEmpty()) s.sendMessage(msg().get("schem-note-entities", "count", String.valueOf(sc.entityList().size())));
        if (sc.entities() > sc.entityList().size()) {
            s.sendMessage(msg().get("schem-note-entities-bad", "count", String.valueOf(sc.entities() - sc.entityList().size())));
        }
        // Spec 033 H4 CA3: los biomas vienen con el esquema (se pegan con paste --biomes).
        if (l.biomes() > 0) s.sendMessage(msg().get("schem-note-biomes", "count", String.valueOf(l.biomes())));
        if (!l.unknownBiomes().isEmpty()) s.sendMessage(msg().get("schem-note-biomes-unknown", "count", String.valueOf(l.unknownBiomes().size()),
                "kinds", String.join(", ", l.unknownBiomes().stream().limit(5).toList())));
        if (sc.badBlockEntities() > 0) s.sendMessage(msg().get("schem-note-bad", "count", String.valueOf(sc.badBlockEntities())));
        if (sc.dataVersion() > TileNbt.serverDataVersion()) {
            s.sendMessage(msg().get("schem-note-newer", "version", String.valueOf(sc.dataVersion()), "server", String.valueOf(TileNbt.serverDataVersion())));
        }
        s.sendMessage(msg().get("schem-loaded-next"));
    }

    private static String top(Map<String, ? extends Number> m, int max) {
        List<String> out = new ArrayList<>();
        m.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue().longValue(), a.getValue().longValue())).limit(max)
                .forEach(e -> out.add(e.getKey() + " ×" + Edits.fmt(e.getValue().longValue())));
        if (m.size() > max) out.add("…");
        return String.join(", ", out);
    }

    // ------------------------------------------------------------------ guardar

    public void save(CommandSender s, String name, boolean overwrite) {
        String clean;
        try {
            clean = SchemFiles.clean(name);
        } catch (SchemFiles.SchemFileException e) {
            s.sendMessage(msg().get("error", "message", e.getMessage()));
            return;
        }
        if (overwrite && !s.hasPermission(PERM_DELETE)) {
            s.sendMessage(msg().get("schem-overwrite-permission"));
            return;
        }
        UUID owner = Edits.owner(s);
        Clipboard clip = plugin.edits().session(owner).clipboard();
        if (clip == null) {
            s.sendMessage(msg().get("no-clipboard"));
            return;
        }
        if (!begin(s, owner)) return;
        PaperBlocks blocks = plugin.edits().blocks();
        Transform transform = clip.transform();
        // El giro de cada estado distinto y su texto para la paleta (hilo principal, memorizado por estado).
        Map<Integer, Integer> turned = new HashMap<>();
        Map<Integer, String> stateNames = new HashMap<>();
        for (int st : clip.distinct()) {
            int t = st;
            for (Transform.Step step : transform.steps()) {
                t = switch (step) {
                    case ROTATE_90 -> blocks.rotate90(t);
                    case FLIP_X -> blocks.flip(t, 'x');
                    case FLIP_Y -> blocks.flip(t, 'y');
                    case FLIP_Z -> blocks.flip(t, 'z');
                };
            }
            turned.put(st, t);
            stateNames.put(t, blocks.table().state(t));
        }
        // Los datos de bloque a la forma estándar, por pasos dentro del presupuesto por tick.
        List<Map.Entry<Integer, byte[]>> tiles = new ArrayList<>(clip.rawTiles().entrySet());
        int[] raw = clip.rawStates();
        String[] materials = new String[tiles.size()];
        for (int i = 0; i < tiles.size(); i++) {
            int st = raw[tiles.get(i).getKey()];
            materials[i] = st < 0 ? "minecraft:air" : blocks.table().material(st);
        }
        Map<Integer, byte[]> std = new ConcurrentHashMap<>();
        int[] failed = new int[1];
        int dv = TileNbt.serverDataVersion();
        String author = s.getName();
        String version = plugin.getPluginMeta().getVersion();
        String[] biomeNames = clip.hasBiomes() ? clip.biomeNames().toArray(new String[0]) : null;
        int[] biomeData = clip.hasBiomes() ? clip.rawBiomes().clone() : null;
        SchemFiles files = files();
        s.sendMessage(msg().get("schem-saving", "name", clean));
        Runnable write = () -> CompletableFuture.supplyAsync(() -> {
            try {
                Clipboard c = Clipboard.of(clip.width(), clip.height(), clip.depth(), clip.offset()[0], clip.offset()[1],
                        clip.offset()[2], raw, std);
                c.entities(clip.entities());
                // Spec 033 F-01: la copia que se guarda también lleva los biomas (antes se perdían aquí).
                if (biomeNames != null) c.biomes(biomeNames, biomeData);
                c.transform(transform);
                Clipboard baked = c.baked(st -> turned.getOrDefault(st, st));
                Schematic sc = SchemConvert.fromClipboard(baked, stateNames, "minecraft:air", dv, clean, author, System.currentTimeMillis());
                byte[] bytes = SchemFormat.write(sc, version);
                files.write(clean, bytes, overwrite);
                return new long[]{sc.width(), sc.height(), sc.length(), bytes.length};
            } catch (IOException e) {
                throw new CompletionException(e);
            } catch (IllegalArgumentException e) {
                throw new CompletionException(new SchemFiles.SchemFileException(e.getMessage()));
            }
        }, thread).whenCompleteAsync((r, t) -> {
            busy.remove(owner);
            if (t != null) {
                fail(s, clean, t);
                return;
            }
            s.sendMessage(msg().get("schem-saved", "name", clean, "size", r[0] + "×" + r[1] + "×" + r[2],
                    "blocks", Edits.fmt(r[0] * r[1] * r[2]), "kb", String.valueOf(Math.max(1, (r[3] + 1023) / 1024))));
            if (failed[0] > 0) s.sendMessage(msg().get("schem-note-tiles-failed", "count", String.valueOf(failed[0])));
            refreshNames(files);
        }, main);
        if (tiles.isEmpty()) {
            write.run();
            return;
        }
        plugin.edits().submitTask(id -> new TaskJob(id, owner, tiles.size(), i -> {
            try {
                byte[] b = TileNbt.standard(tiles.get(i).getValue(), materials[i]);
                if (b != null) std.put(tiles.get(i).getKey(), b);
            } catch (IOException | RuntimeException e) {
                failed[0]++;
            }
        }, System::nanoTime, write, () -> {
            busy.remove(owner);
            s.sendMessage(msg().get("schem-save-cancelled", "name", clean));
        }));
    }

    // ------------------------------------------------------------------ importar (spec 029 H5)

    /** Copia los esquemas de WorldEdit y FAWE a la carpeta propia, en el hilo de disco, sin borrar los originales. */
    public void importAll(CommandSender s, boolean overwrite) {
        UUID owner = Edits.owner(s);
        if (!begin(s, owner)) return;
        SchemFiles files = files();
        long max = settings().maxFileKb() * 1024L;
        CompletableFuture.supplyAsync(() -> {
            try {
                return files.importAll(overwrite, max);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, thread).whenCompleteAsync((r, t) -> {
            busy.remove(owner);
            if (t != null) {
                fail(s, "import", t);
                return;
            }
            if (r.copied() + r.skipped() + r.tooBig() == 0) {
                s.sendMessage(msg().get("schem-import-none"));
                return;
            }
            s.sendMessage(msg().get("schem-imported", "copied", String.valueOf(r.copied()), "skipped", String.valueOf(r.skipped()),
                    "big", String.valueOf(r.tooBig()), "max", String.valueOf(settings().maxFileKb())));
            refreshNames(files);
        }, main);
    }

    // ------------------------------------------------------------------ listar y borrar

    public void list(CommandSender s, int page, String filter) {
        SchemFiles files = files();
        UUID owner = Edits.owner(s);
        if (!begin(s, owner)) return;
        CompletableFuture.supplyAsync(() -> {
            try {
                return files.list();
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, thread).whenCompleteAsync((all, t) -> {
            busy.remove(owner);
            if (t != null) {
                fail(s, "", t);
                return;
            }
            names = all;
            namesAt = System.currentTimeMillis();
            List<SchemFiles.Entry> shown = new ArrayList<>();
            String f = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
            for (SchemFiles.Entry e : all) if (e.name().toLowerCase(Locale.ROOT).contains(f)) shown.add(e);
            if (shown.isEmpty()) {
                s.sendMessage(msg().get(all.isEmpty() ? "schem-list-empty" : "schem-list-none", "filter", filter == null ? "" : filter));
                return;
            }
            int pages = (shown.size() + PAGE - 1) / PAGE;
            int p = Math.max(1, Math.min(page, pages));
            s.sendMessage(msg().get("schem-list-header", "count", String.valueOf(shown.size()), "page", String.valueOf(p), "pages", String.valueOf(pages)));
            SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            for (SchemFiles.Entry e : shown.subList((p - 1) * PAGE, Math.min(shown.size(), p * PAGE))) {
                s.sendMessage(msg().get("schem-list-line", "name", e.name(), "folder", e.folder() == null ? "" : "[" + e.folder() + "] ",
                        "kb", String.valueOf(Math.max(1, (e.bytes() + 1023) / 1024)), "date", date.format(new Date(e.modified()))));
            }
            if (p < pages) s.sendMessage(msg().get("schem-list-more", "next", String.valueOf(p + 1)));
        }, main);
    }

    public void delete(CommandSender s, String name) {
        String clean;
        try {
            clean = SchemFiles.clean(name);
        } catch (SchemFiles.SchemFileException e) {
            s.sendMessage(msg().get("error", "message", e.getMessage()));
            return;
        }
        UUID owner = Edits.owner(s);
        if (!begin(s, owner)) return;
        SchemFiles files = files();
        int days = settings().trashDays();
        CompletableFuture.supplyAsync(() -> {
            try {
                if (files.trash(clean, System.currentTimeMillis())) return "deleted";
                SchemFiles.Entry other = files.find(clean, true);
                if (other != null) return other.folder();
                throw new NotFound();
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, thread).whenCompleteAsync((r, t) -> {
            busy.remove(owner);
            if (t != null) {
                fail(s, clean, t);
                return;
            }
            if (r.equals("deleted")) s.sendMessage(msg().get("schem-deleted", "name", clean, "days", String.valueOf(days)));
            else s.sendMessage(msg().get("schem-read-only", "name", clean, "folder", r));
            refreshNames(files);
        }, main);
    }

    // ------------------------------------------------------------------ autocompletado

    /** Los nombres conocidos (sin leer el disco en el hilo principal: se refrescan en el de esquemas). */
    public List<String> names(boolean ownOnly) {
        if (System.currentTimeMillis() - namesAt > 10_000) refreshNames(files());
        List<String> out = new ArrayList<>();
        for (SchemFiles.Entry e : names) if (!ownOnly || e.folder() == null) out.add(e.name());
        return out;
    }

    private void refreshNames(SchemFiles files) {
        namesAt = System.currentTimeMillis();
        thread.execute(() -> {
            try {
                names = files.list();
            } catch (IOException ignored) {
                // se intenta de nuevo en el próximo autocompletado
            }
        });
    }
}
