package dev.leo.worldcraft.command;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.config.Issues;
import dev.leo.worldcraft.config.Limits;
import dev.leo.worldcraft.config.Messages;
import dev.leo.worldcraft.config.RestoreSettings;
import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.edit.PasteSource;
import dev.leo.worldcraft.paper.Edits;
import dev.leo.worldcraft.paper.PaperBiomes;
import dev.leo.worldcraft.paper.PaperBlocks;
import dev.leo.worldcraft.paper.Regen;
import dev.leo.worldcraft.paper.Session;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Region;
import dev.leo.worldcraft.schem.SchemBlocks;
import dev.leo.worldcraft.snapshot.Restore;
import dev.leo.worldcraft.snapshot.SnapshotRepo;
import dev.leo.worldcraft.snapshot.TempWorlds;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Spec 036 (plan §1.3): regen genera en el mundo temporal y lo lee al portapapeles; restore lee una copia de seguridad en
 * el hilo de copias. Las dos terminan en un paste en el mismo lugar con el motor de la 025 (vista previa, límites,
 * deshacer).
 */
final class RestoreCommands {
    static final List<String> SUBS = List.of("regen", "snapshot", "restore");
    static final Map<String, String> ALIASES = Map.of("snap", "snapshot");
    static final String PERM_REGEN = "worldcraft.regen", PERM_SNAPSHOT = "worldcraft.snapshot";
    static final List<String> SNAPSHOT_OPS = List.of("list", "use", "sel", "before", "after");
    private static final int PAGE = 10;

    private final Worldcraft plugin;
    private final WorldcraftCommand main;
    /** Las últimas listas de copias por mundo (para autocompletar sin leer el disco en el hilo principal). */
    private final Map<String, List<SnapshotRepo.Entry>> cache = new HashMap<>();
    private final Map<String, Long> cachedAt = new HashMap<>();

    RestoreCommands(Worldcraft plugin, WorldcraftCommand main) {
        this.plugin = plugin;
        this.main = main;
    }

    static boolean handles(String sub) {
        return SUBS.contains(sub) || ALIASES.containsKey(sub);
    }

    static String canonical(String sub) {
        return ALIASES.getOrDefault(sub, sub);
    }

    static String perm(String sub) {
        return canonical(sub).equals("regen") ? PERM_REGEN : PERM_SNAPSHOT;
    }

    static Set<String> flags(String sub) {
        return switch (canonical(sub)) {
            case "regen" -> Set.of("biomes", "random", "preview");
            case "restore" -> Set.of("biomes", "preview");
            default -> Set.of();
        };
    }

    private Messages msg() {
        return plugin.messages();
    }

    private Edits edits() {
        return plugin.edits();
    }

    private RestoreSettings cfg() {
        return plugin.config().restore();
    }

    /** Spec 040 H4 CA5: la zona de /wc tz de quien pregunta, o la del servidor. */
    private ZoneId asking = ZoneId.systemDefault();

    /** En el hilo principal (las lecturas en otro hilo reciben la zona ya tomada). */
    private ZoneId zone() {
        return asking;
    }

    private void zoneFor(CommandSender s) {
        ZoneId z = edits().session(Edits.owner(s)).zone;
        asking = z == null ? ZoneId.systemDefault() : z;
    }

    void run(CommandSender s, String sub, String[] a) {
        zoneFor(s);
        String c = canonical(sub);
        // -b y -r de WorldEdit y FAWE.
        String[] in = new String[a.length];
        for (int i = 0; i < a.length; i++) in[i] = a[i].equals("-b") ? "--biomes" : a[i].equals("-r") && c.equals("regen") ? "--random" : a[i];
        try {
            switch (c) {
                case "regen" -> regen(s, in);
                case "restore" -> restore(s, in);
                default -> snapshot(s, in);
            }
        } catch (IllegalArgumentException e) {
            s.sendMessage(msg().get("error", "message", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ ayudas

    private boolean overLimit(CommandSender s, long blocks) {
        Limits.Resolved lim = edits().limits(s);
        if (blocks <= lim.maxBlocks()) return false;
        s.sendMessage(msg().get("limit-blocks", "blocks", Edits.fmt(blocks), "max", Edits.fmt(lim.maxBlocks()),
                "groups", String.join(", ", lim.groups())));
        return true;
    }

    /** El portapapeles pegado en su lugar con el aire (y los biomas si se piden), como una obra del motor. */
    private void apply(CommandSender s, String kind, String detail, World w, Region region, Clipboard clip, boolean biomes, boolean preview) {
        Box box = region.bounds();
        PaperBlocks blocks = edits().blocks();
        Edits.Op op = new Edits.Op(kind, detail, w, box, region.volume(),
                () -> new PasteSource(clip, box.minX(), box.minY(), box.minZ(), true, blocks::isAir, blocks), clip.distinct(), null);
        if (biomes && clip.hasBiomes()) {
            long[] cells = dev.leo.worldcraft.biome.Cells.of(region);
            op = op.with(new Edits.BiomeWork((bw, sink) -> dev.leo.worldcraft.biome.BiomeStage.apply(bw, cells, (i, x, y, z, c) -> {
                int[] m = dev.leo.worldcraft.biome.Cells.center(x, y, z, box);
                return clip.biomeFor(m[0], m[1], m[2], box.minX(), box.minY(), box.minZ());
            }, sink), msg().text("note-biomes")));
        }
        edits().request(s, op, preview);
    }

    /** El mundo de quien escribe: el del jugador; la consola, el de su selección (o el primero). */
    private World world(CommandSender s) {
        if (s instanceof Player p) return p.getWorld();
        String w = edits().session(Edits.owner(s)).world();
        World out = w == null ? null : Bukkit.getWorld(w);
        return out != null ? out : Bukkit.getWorlds().get(0);
    }

    // ------------------------------------------------------------------ H1 regen

    private void regen(CommandSender s, String[] a) {
        Args args = Args.parse(a, 1, flags("regen"));
        if (args.problem != null) throw new IllegalArgumentException(args.problem);
        List<String> p = args.positional;
        if (p.size() > 2) throw new WorldcraftCommand.Usage();
        String seedText = args.flags.contains("random") ? "random" : null, biomeText = null;
        if (p.size() == 2) {
            seedText = p.get(0);
            biomeText = p.get(1);
        } else if (p.size() == 1) {
            // Una palabra sola: un bioma si lo es (regen desert), si no una semilla (regen 123, regen hello).
            if (!TempWorlds.looksLikeSeed(p.get(0)) && plugin.config().parser().biome(p.get(0)) != null) biomeText = p.get(0);
            else seedText = p.get(0);
        }
        org.bukkit.block.Biome biome = null;
        String biomeId = null;
        if (biomeText != null) {
            biomeId = plugin.config().parser().biome(biomeText);
            if (biomeId == null) throw new IllegalArgumentException("Unknown biome '" + biomeText + "'"
                    + plugin.config().parser().biomeHint(biomeText) + ". See /wc biomelist");
            biome = PaperBiomes.biome(biomeId);
            if (biome == null) throw new IllegalArgumentException("The biome '" + biomeText + "' can't be used to generate");
        }
        Session ses = edits().session(Edits.owner(s));
        Region region = main.selection(s, ses);
        if (region == null) return;
        World w = Bukkit.getWorld(ses.world());
        if (overLimit(s, region.volume())) return;
        long chunks = Restore.chunkCount(region.bounds());
        if (chunks > cfg().regenMaxChunks()) {
            s.sendMessage(msg().get("regen-too-many-chunks", "chunks", Edits.fmt(chunks), "max", Edits.fmt(cfg().regenMaxChunks())));
            return;
        }
        Regen regen = plugin.regen();
        if (regen.busy() != null) {
            s.sendMessage(msg().get("regen-running", "who", regen.busy()));
            return;
        }
        long seed = TempWorlds.seed(seedText, w.getSeed(), ThreadLocalRandom.current());
        World temp;
        try {
            temp = regen.acquire(w, seed, biome, s.getName());
        } catch (Regen.Problem e) {
            s.sendMessage(msg().get(e.getMessage().startsWith("another") ? "regen-running" : "regen-failed", "who", regen.busy() == null ? "" : regen.busy(),
                    "reason", e.getMessage()));
            return;
        }
        boolean biomes = args.flags.contains("biomes") || biome != null;
        boolean preview = args.flags.contains("preview");
        String shortBiome = biomeId == null ? "" : biomeId.replace("minecraft:", "");
        String detail = "seed " + seed + (biome == null ? "" : " biome " + shortBiome) + (biomes ? " --biomes" : "");
        s.sendMessage(msg().get("regen-start", "chunks", Edits.fmt(chunks), "world", w.getName(), "seed", String.valueOf(seed),
                "biome", biome == null ? "" : ", biome " + shortBiome));
        edits().readClip(s, temp, region, "regen", biomes, clip -> {
            regen.release();
            apply(s, "regen", detail, w, region, clip, biomes, preview);
        }, regen::release);
    }

    // ------------------------------------------------------------------ H2 snapshot

    /** Lista las copias del mundo fuera del hilo principal y sigue en el principal (o dice el error). */
    private void listed(CommandSender s, World w, Consumer<List<SnapshotRepo.Entry>> then) {
        Path folder = cfg().folder(Path.of("").toAbsolutePath());
        if (folder == null) {
            s.sendMessage(msg().get("snapshots-off"));
            return;
        }
        String world = w.getName();
        ZoneId zone = zone();
        CompletableFuture.supplyAsync(() -> {
            try {
                return SnapshotRepo.list(folder, world, zone);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, plugin.snapshotThread()).whenComplete((list, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                s.sendMessage(msg().get("snapshot-folder-error", "folder", folder.toString(), "reason", String.valueOf(c.getMessage())));
                return;
            }
            cache.put(world, list);
            cachedAt.put(world, System.currentTimeMillis());
            if (list.isEmpty()) {
                s.sendMessage(msg().get("snapshot-none", "world", world, "folder", folder.toString()));
                return;
            }
            then.accept(list);
        }));
    }

    private void snapshot(CommandSender s, String[] a) {
        String op = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        if (!SNAPSHOT_OPS.contains(op)) {
            String c = Issues.closest(op, SNAPSHOT_OPS);
            throw new IllegalArgumentException("Unknown '" + a[1] + "'" + (c == null ? "" : " (did you mean '" + c + "'?)")
                    + ": list, use, sel, before or after");
        }
        World w = world(s);
        Session ses = edits().session(Edits.owner(s));
        String rest = a.length > 2 ? String.join(" ", java.util.Arrays.asList(a).subList(2, a.length)) : "";
        if (!op.equals("list") && rest.isEmpty()) throw new WorldcraftCommand.Usage();
        long date = op.equals("before") || op.equals("after") ? SnapshotRepo.parseDate(rest, zone()) : 0;
        int page;
        try {
            page = op.equals("list") && !rest.isEmpty() ? Integer.parseInt(rest.trim()) : 1;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + rest + "' is not a page number");
        }
        listed(s, w, list -> {
            switch (op) {
                case "list" -> {
                    int pages = (list.size() + PAGE - 1) / PAGE, pg = Math.max(1, Math.min(page, pages));
                    s.sendMessage(msg().get("snapshot-list-header", "world", w.getName(), "count", String.valueOf(list.size()),
                            "page", String.valueOf(pg), "pages", String.valueOf(pages)));
                    for (int i = (pg - 1) * PAGE; i < Math.min(list.size(), pg * PAGE); i++) {
                        SnapshotRepo.Entry e = list.get(i);
                        s.sendMessage(msg().get(e.name().equals(ses.snapshot) ? "snapshot-list-line-chosen" : "snapshot-list-line",
                                "n", String.valueOf(i + 1), "name", e.name(), "date", e.date(zone())));
                    }
                }
                case "use" -> {
                    SnapshotRepo.Entry e = rest.equalsIgnoreCase("latest") ? list.get(0) : find(list, rest);
                    if (e == null) {
                        unknown(s, list, rest, w);
                        return;
                    }
                    choose(s, ses, e);
                }
                case "sel" -> {
                    int n;
                    try {
                        n = Integer.parseInt(rest.trim());
                    } catch (NumberFormatException ex) {
                        s.sendMessage(msg().get("error", "message", "'" + rest + "' is not a number from /wc snapshot list"));
                        return;
                    }
                    if (n < 1 || n > list.size()) {
                        s.sendMessage(msg().get("snapshot-bad-number", "n", String.valueOf(n), "count", String.valueOf(list.size())));
                        return;
                    }
                    choose(s, ses, list.get(n - 1));
                }
                default -> {
                    SnapshotRepo.Entry e = op.equals("before") ? SnapshotRepo.before(list, date) : SnapshotRepo.after(list, date);
                    if (e == null) {
                        s.sendMessage(msg().get(op.equals("before") ? "snapshot-none-before" : "snapshot-none-after", "world", w.getName(), "date", rest));
                        return;
                    }
                    choose(s, ses, e);
                }
            }
        });
    }

    private void choose(CommandSender s, Session ses, SnapshotRepo.Entry e) {
        ses.snapshot = e.name();
        s.sendMessage(msg().get("snapshot-chosen", "name", e.name(), "date", e.date(zone())));
    }

    private static SnapshotRepo.Entry find(List<SnapshotRepo.Entry> list, String name) {
        for (SnapshotRepo.Entry e : list) if (e.name().equals(name)) return e;
        for (SnapshotRepo.Entry e : list) if (e.name().equalsIgnoreCase(name)) return e;
        return null;
    }

    private void unknown(CommandSender s, List<SnapshotRepo.Entry> list, String name, World w) {
        String c = Issues.closest(name, list.stream().map(SnapshotRepo.Entry::name).toList());
        s.sendMessage(msg().get("snapshot-unknown", "input", name, "world", w.getName(), "hint", c == null ? "" : " (did you mean '" + c + "'?)"));
    }

    // ------------------------------------------------------------------ H3 restore

    private void restore(CommandSender s, String[] a) {
        Args args = Args.parse(a, 1, flags("restore"));
        if (args.problem != null) throw new IllegalArgumentException(args.problem);
        if (args.positional.size() > 1) throw new WorldcraftCommand.Usage();
        Session ses = edits().session(Edits.owner(s));
        Region region = main.selection(s, ses);
        if (region == null) return;
        World w = Bukkit.getWorld(ses.world());
        if (overLimit(s, region.volume())) return;
        long chunks = Restore.chunkCount(region.bounds());
        if (chunks > cfg().snapshotsMaxChunks()) {
            s.sendMessage(msg().get("restore-too-many-chunks", "chunks", Edits.fmt(chunks), "max", Edits.fmt(cfg().snapshotsMaxChunks())));
            return;
        }
        boolean biomes = args.flags.contains("biomes"), preview = args.flags.contains("preview");
        String asked = args.positional.isEmpty() ? null : args.positional.get(0);
        listed(s, w, list -> {
            SnapshotRepo.Entry e;
            if (asked != null) {
                e = find(list, asked);
                if (e == null) {
                    unknown(s, list, asked, w);
                    return;
                }
            } else if (ses.snapshot != null) {
                e = find(list, ses.snapshot);
                if (e == null) {
                    s.sendMessage(msg().get("snapshot-gone", "name", ses.snapshot, "world", w.getName()));
                    return;
                }
            } else {
                e = list.get(0);
                s.sendMessage(msg().get("snapshot-newest", "name", e.name(), "date", e.date(zone())));
            }
            read(s, w, region, e, biomes, preview);
        });
    }

    private void read(CommandSender s, World w, Region region, SnapshotRepo.Entry e, boolean biomes, boolean preview) {
        long chunks = Restore.chunkCount(region.bounds());
        s.sendMessage(msg().get("restore-reading", "chunks", Edits.fmt(chunks), "name", e.name()));
        String world = w.getName();
        CompletableFuture.supplyAsync(() -> {
            try (SnapshotRepo.Chunks src = SnapshotRepo.open(e, world)) {
                return Restore.read(region, src, biomes);
            } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        }, plugin.snapshotThread()).whenComplete((raw, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                s.sendMessage(msg().get("restore-failed", "name", e.name(), "reason", String.valueOf(c.getMessage())));
                return;
            }
            resolved(s, w, region, e, raw, biomes, preview);
        }));
    }

    /** Hilo principal: la paleta de la copia a estados de este servidor (las traducciones de la 027). */
    private void resolved(CommandSender s, World w, Region region, SnapshotRepo.Entry e, Restore.Raw raw, boolean biomes, boolean preview) {
        if (raw.skipped() > 0) s.sendMessage(msg().get("restore-skipped", "count", Edits.fmt(raw.skipped()), "why", why(raw)));
        PaperBlocks blocks = edits().blocks();
        SchemBlocks.Result res = SchemBlocks.resolve(raw.palette, raw.counts, blocks, blocks.table());
        int[] map = res.map().clone();
        for (int i = 0; i < map.length; i++) {
            String st = raw.palette[i];
            int b = st.indexOf('[');
            String mat = (b < 0 ? st : st.substring(0, b)).toLowerCase(Locale.ROOT);
            if (res.unknownByName().containsKey(mat)) map[i] = -1;
        }
        long unknown = Restore.unknownBlocks(raw, map);
        if (unknown > 0) {
            List<String> names = new ArrayList<>();
            for (String n : res.unknownByName().keySet()) if (names.size() < 5) names.add(n.replace("minecraft:", ""));
            s.sendMessage(msg().get("restore-unknown-blocks", "count", Edits.fmt(unknown), "names", String.join(", ", names)));
        }
        Set<String> knownBiomes = new HashSet<>(blocks.biomes());
        java.util.function.IntPredicate isAir = blocks::isAir;
        CompletableFuture.supplyAsync(() -> Restore.toClipboard(raw, map, isAir, knownBiomes::contains), plugin.snapshotThread())
                .whenComplete((clip, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (err != null) {
                        s.sendMessage(msg().get("restore-failed", "name", e.name(), "reason", String.valueOf(err.getMessage())));
                        return;
                    }
                    if (clip.distinct().isEmpty()) {
                        s.sendMessage(msg().get("restore-nothing"));
                        return;
                    }
                    apply(s, "restore", e.name() + (biomes ? " --biomes" : ""), w, region, clip, biomes, preview);
                }));
    }

    private String why(Restore.Raw raw) {
        List<String> parts = new ArrayList<>();
        if (raw.missing > 0) parts.add(msg().text("restore-why-missing", "count", Edits.fmt(raw.missing)));
        if (raw.unfinished > 0) parts.add(msg().text("restore-why-unfinished", "count", Edits.fmt(raw.unfinished)));
        if (raw.old > 0) parts.add(msg().text("restore-why-old", "count", Edits.fmt(raw.old)));
        if (raw.broken > 0) parts.add(msg().text("restore-why-broken", "count", Edits.fmt(raw.broken), "reason", raw.problem));
        if (raw.unsupported > 0) parts.add(msg().text("restore-why-unsupported", "count", Edits.fmt(raw.unsupported), "what", raw.problem));
        return String.join(", ", parts);
    }

    // ------------------------------------------------------------------ autocompletado

    List<String> complete(CommandSender s, String sub, String[] a, String last) {
        zoneFor(s);
        String c = canonical(sub);
        long words = java.util.Arrays.stream(a).skip(1).filter(x -> !x.startsWith("-")).count();
        return switch (c) {
            case "regen" -> {
                List<String> out = new ArrayList<>();
                if (words <= 1) out.add("random");
                if (words <= 2) for (String b : edits().blocks().biomes()) out.add(b.replace("minecraft:", ""));
                yield WorldcraftCommand.filter(out, last);
            }
            case "restore" -> words <= 1 ? WorldcraftCommand.filter(names(s), last) : List.of();
            default -> {
                if (a.length == 2) yield WorldcraftCommand.filter(SNAPSHOT_OPS, last);
                String op = a[1].toLowerCase(Locale.ROOT);
                if (a.length != 3) yield List.of();
                yield switch (op) {
                    case "use" -> {
                        List<String> out = new ArrayList<>(names(s));
                        out.add("latest");
                        yield WorldcraftCommand.filter(out, last);
                    }
                    case "sel" -> {
                        List<String> out = new ArrayList<>();
                        for (int i = 1; i <= Math.min(9, names(s).size()); i++) out.add(String.valueOf(i));
                        yield WorldcraftCommand.filter(out, last);
                    }
                    case "before", "after" -> WorldcraftCommand.filter(List.of(java.time.LocalDate.now(zone()).toString()), last);
                    default -> List.of();
                };
            }
        };
    }

    /** Los nombres de la última lista de ese mundo; si es vieja (o no hay), se pide otra para la próxima vez. */
    private List<String> names(CommandSender s) {
        World w = world(s);
        Long at = cachedAt.get(w.getName());
        Path folder = cfg().folder(Path.of("").toAbsolutePath());
        if (folder != null && (at == null || System.currentTimeMillis() - at > 30_000)) {
            cachedAt.put(w.getName(), System.currentTimeMillis());
            String world = w.getName();
            ZoneId zone = zone();
            CompletableFuture.supplyAsync(() -> {
                try {
                    return SnapshotRepo.list(folder, world, zone);
                } catch (java.io.IOException e) {
                    return List.<SnapshotRepo.Entry>of();
                }
            }, plugin.snapshotThread()).thenAccept(list -> Bukkit.getScheduler().runTask(plugin, () -> cache.put(world, list)));
        }
        return cache.getOrDefault(w.getName(), List.of()).stream().map(SnapshotRepo.Entry::name).toList();
    }
}
