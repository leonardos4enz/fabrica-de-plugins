package dev.leo.worldcraft.command;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.config.Issues;
import dev.leo.worldcraft.config.Messages;
import dev.leo.worldcraft.edit.EditSource;
import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.edit.Scheduler;
import dev.leo.worldcraft.edit.SnowMath;
import dev.leo.worldcraft.edit.Snapshot;
import dev.leo.worldcraft.paper.Edits;
import dev.leo.worldcraft.paper.Nature;
import dev.leo.worldcraft.paper.Nms;
import dev.leo.worldcraft.paper.PaperBlocks;
import dev.leo.worldcraft.paper.Session;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Curve;
import dev.leo.worldcraft.region.Region;
import dev.leo.worldcraft.tool.ToolSpec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;
import java.util.logging.Level;

/**
 * Spec 040: los comandos de WorldEdit 7.3.19 que le faltaban a Worldcraft. Construir (curve, snowsmooth, feature,
 * update; fillr está con fill en {@link BuildCommands}), dónde se pone y la varita (placement, toggleplace,
 * toggleeditwand), el pincel de la mano (range, tracemask) e información (chunkinfo, listchunks, searchitem, version,
 * report, trace, tz). Lo que cambia bloques termina en una obra del motor.
 */
final class MoreCommands {
    static final List<String> SUBS = List.of("curve", "snowsmooth", "feature", "update", "placement", "toggleplace", "toggleeditwand",
            "range", "tracemask", "chunkinfo", "listchunks", "searchitem", "version", "report", "trace", "tz");
    static final List<String> PLACEMENTS = List.of("player", "pos1", "pos2", "center");
    private static final int PER_PAGE = 10;

    static String perm(String sub) {
        return sub.equals("report") ? WorldcraftCommand.ADMIN : WorldcraftCommand.USE;
    }

    static Set<String> flags(String sub) {
        return switch (sub) {
            case "curve" -> Set.of("preview", "hollow");
            case "snowsmooth" -> Set.of("preview", "mask");
            case "feature" -> Set.of("preview", "at", "world");
            case "update" -> Set.of("preview");
            case "searchitem" -> Set.of("blocks", "items");
            default -> Set.of();
        };
    }

    private final Worldcraft plugin;
    private final WorldcraftCommand main;
    private final BuildCommands build;

    MoreCommands(Worldcraft plugin, WorldcraftCommand main, BuildCommands build) {
        this.plugin = plugin;
        this.main = main;
        this.build = build;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private Edits edits() {
        return plugin.edits();
    }

    private PaperBlocks blocks() {
        return edits().blocks();
    }

    void run(CommandSender s, String sub, String[] a) {
        Args args = Args.parse(a, 1, flags(sub));
        if (args.problem != null) {
            s.sendMessage(msg().get("error", "message", args.problem));
            return;
        }
        try {
            switch (sub) {
                case "curve" -> curve(s, args);
                case "snowsmooth" -> snowsmooth(s, args);
                case "feature" -> feature(s, args);
                case "update" -> update(s, args);
                case "placement" -> placement(s, args);
                case "toggleplace" -> toggleplace(s);
                case "toggleeditwand" -> toggleeditwand(s);
                case "range" -> range(s, args);
                case "tracemask" -> tracemask(s, args);
                case "chunkinfo" -> chunkinfo(s);
                case "listchunks" -> listchunks(s, args);
                case "searchitem" -> searchitem(s, args);
                case "version" -> version(s);
                case "report" -> report(s, false);
                case "trace" -> trace(s, args);
                case "tz" -> tz(s, args);
                default -> throw new WorldcraftCommand.Usage();
            }
        } catch (ParseException | IllegalArgumentException e) {
            s.sendMessage(msg().get("error", "message", e.getMessage()));
        }
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        s.sendMessage(msg().get("players-only"));
        return null;
    }

    private Region selection(CommandSender s) {
        return main.selection(s, edits().session(Edits.owner(s)));
    }

    private World selectionWorld(CommandSender s) {
        return Bukkit.getWorld(edits().session(Edits.owner(s)).world());
    }

    private boolean overLimit(CommandSender s, double n) {
        var lim = edits().limits(s);
        if (n <= lim.maxBlocks()) return false;
        s.sendMessage(msg().get("limit-blocks", "blocks", Edits.fmt((long) Math.ceil(n)), "max", Edits.fmt(lim.maxBlocks()),
                "groups", String.join(", ", lim.groups())));
        return true;
    }

    private <T> void compute(CommandSender s, java.util.function.Supplier<T> work, java.util.function.Consumer<T> then) {
        CompletableFuture.supplyAsync(work, plugin.compute()).whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.getLogger().log(Level.SEVERE, "Calculation failed", err);
                s.sendMessage(msg().get("compute-error"));
                return;
            }
            then.accept(v);
        }));
    }

    private static IntPredicate ids(Snapshot snap, IntPredicate test) {
        Set<Integer> yes = new HashSet<>();
        for (int st : snap.distinct()) if (test.test(st)) yes.add(st);
        return yes::contains;
    }

    /** La obra de unos puntos ya calculados. */
    private void points(CommandSender s, World w, String kind, String detail, Map<Long, Integer> put, boolean preview) {
        if (put.isEmpty()) {
            s.sendMessage(msg().get("nothing-found", "kind", kind));
            return;
        }
        long[] pts = put.keySet().stream().mapToLong(Long::longValue).toArray();
        var probe = new PointsSource(pts, (x, y, z, c) -> put.get(PointsSource.pack(x, y, z)));
        edits().request(s, new Edits.Op(kind, detail, w, probe.bounds(), probe.volume(),
                () -> new PointsSource(pts, (x, y, z, c) -> put.get(PointsSource.pack(x, y, z))), new HashSet<>(put.values()), null), preview);
    }

    // ------------------------------------------------------------------ H1 construir

    /** H1 CA1: //curve por los puntos de la selección convexa o de polígono. */
    private void curve(CommandSender s, Args args) throws ParseException {
        List<String> pos = new ArrayList<>(args.positional);
        int r = 0;
        if (!pos.isEmpty() && pos.get(pos.size() - 1).matches("\\d+")) r = Integer.parseInt(pos.remove(pos.size() - 1));
        if (r > 16) throw new IllegalArgumentException("The thickness must be 0 to 16");
        if (pos.isEmpty()) throw new WorldcraftCommand.Usage();
        Pattern p = plugin.config().parser().pattern(String.join(" ", pos));
        Session ses = edits().session(Edits.owner(s));
        List<int[]> nodes = ses.selector().points();
        if (nodes.isEmpty()) {
            s.sendMessage(msg().get("curve-needs-points", "kind", ses.selector().kind().id()));
            return;
        }
        World w = Bukkit.getWorld(ses.world());
        if (w == null) {
            s.sendMessage(msg().get("no-selection"));
            return;
        }
        double len = 0;
        for (int i = 1; i < nodes.size(); i++) {
            int[] u = nodes.get(i - 1), v = nodes.get(i);
            len += Math.sqrt(Math.pow(u[0] - v[0], 2) + Math.pow(u[1] - v[1], 2) + Math.pow(u[2] - v[2], 2));
        }
        if (overLimit(s, (len * 1.6 + 1) * Math.pow(2 * r + 1, 3))) return;
        long[] pts = Curve.points(nodes, r, args.flags.contains("hollow"), PointsSource::pack);
        Pattern.Compiled pc = p.compile(blocks().table());
        long seed = ThreadLocalRandom.current().nextLong();
        int lo = w.getMinHeight(), hi = w.getMaxHeight() - 1;
        List<Integer> placed = new ArrayList<>();
        for (String st : p.placedStates()) placed.add(blocks().table().intern(st));
        var probe = new PointsSource(pts, (x, y, z, c) -> c);
        edits().request(s, new Edits.Op("curve", p.text() + " through " + nodes.size() + " points" + (r > 0 ? ", thickness " + r : ""), w,
                probe.bounds(), probe.volume(), () -> new PointsSource(pts, (x, y, z, c) -> y < lo || y > hi ? EditSource.KEEP : pc.apply(x, y, z, c, seed)),
                placed, null), args.flags.contains("preview"));
    }

    /** H1 CA2: //snowsmooth sobre la selección (leída con una capa más arriba). */
    private void snowsmooth(CommandSender s, Args args) throws ParseException {
        int passes = 1;
        if (!args.positional.isEmpty()) {
            try {
                passes = Integer.parseInt(args.positional.get(0));
            } catch (NumberFormatException e) {
                throw new WorldcraftCommand.Usage();
            }
        }
        if (passes < 1 || passes > 10) throw new IllegalArgumentException("Smooth 1 to 10 passes");
        Mask.Compiled ground = args.values.containsKey("mask") ? plugin.config().parser().mask(args.values.get("mask")).compile(blocks().table()) : null;
        Region region = selection(s);
        if (region == null) return;
        World w = selectionWorld(s);
        Box rb = region.bounds();
        Box read = new Box(rb.minX(), rb.minY(), rb.minZ(), rb.maxX(), rb.maxY() + 1, rb.maxZ()).clampY(w.getMinHeight(), w.getMaxHeight() - 1);
        if (read == null || overLimit(s, read.volume())) return;
        Snapshot snap = new Snapshot(read);
        int n = passes;
        edits().read(s, w, read, "snowsmooth", snap, () -> {
            SnowMath.Rules rules = plugin.tools().actions().snowRules(snap, ground);
            compute(s, () -> SnowMath.smooth(snap, (x, z) -> region.span(x, z) != null, n, rules),
                    out -> points(s, w, "snowsmooth", n + (n == 1 ? " pass" : " passes"), out, args.flags.contains("preview")));
        });
    }

    /** H1 CA4: //feature donde se pone una forma (los pies o lo que diga placement). */
    private void feature(CommandSender s, Args args) {
        Nms nms = Nms.get();
        if (!nms.available()) {
            s.sendMessage(msg().get("nms-unavailable", "reason", nms.problem()));
            return;
        }
        if (args.positional.size() != 1) throw new WorldcraftCommand.Usage();
        String id = args.positional.get(0).toLowerCase(Locale.ROOT);
        if (!nms.hasFeature(id)) {
            List<String> short_ = new ArrayList<>();
            for (String f : nms.features()) short_.add(f.replace("minecraft:", ""));
            s.sendMessage(msg().get("feature-unknown", "input", id, "hint", hint(Issues.closest(id.replace("minecraft:", ""), short_))));
            return;
        }
        BuildCommands.Place at = build.place(s, args);
        if (at == null) return;
        new Nature(plugin).feature(s, at.world(), at.x(), at.y(), at.z(), id, args.flags.contains("preview"), () -> {
        });
    }

    private static String hint(String c) {
        return c == null ? "" : " (did you mean '" + c + "'?)";
    }

    /** H1 CA5: //update, la forma de cada bloque de la selección según sus vecinos, por tandas. */
    private void update(CommandSender s, Args args) {
        Nms nms = Nms.get();
        if (!nms.available()) {
            s.sendMessage(msg().get("nms-unavailable", "reason", nms.problem()));
            return;
        }
        Region region = selection(s);
        if (region == null) return;
        World w = selectionWorld(s);
        Box box = region.bounds().clampY(w.getMinHeight(), w.getMaxHeight() - 1);
        if (box == null || overLimit(s, region.volume())) return;
        Snapshot snap = new Snapshot(box);
        boolean preview = args.flags.contains("preview");
        edits().read(s, w, box, "update", snap, () -> {
            // Solo los estados con propiedades pueden cambiar de forma (vallas, muros, escaleras, redstone…).
            IntPredicate maybe = ids(snap, st -> !blocks().isAir(st) && !blocks().table().properties(st).isEmpty());
            UUID owner = Edits.owner(s);
            edits().submitTask(id -> new UpdateJob(id, owner, plugin, w, region, snap, maybe, nms, out -> Bukkit.getScheduler().runTask(plugin,
                    () -> points(s, w, "update", region.size(), out, preview))));
        });
    }

    /** Recorre la caja por tandas con el reloj (lección 025 F-01) y junta los bloques cuya forma cambia. */
    private final class UpdateJob implements Scheduler.Job {
        private final int id;
        private final UUID owner;
        private final Plugin plugin;
        private final World world;
        private final Region region;
        private final Snapshot snap;
        private final IntPredicate maybe;
        private final Nms nms;
        private final java.util.function.Consumer<Map<Long, Integer>> done;
        private final Map<Long, Integer> out = new LinkedHashMap<>();
        private int x, y, z;
        private boolean finished, cancelled;

        UpdateJob(int id, UUID owner, Plugin plugin, World world, Region region, Snapshot snap, IntPredicate maybe, Nms nms,
                  java.util.function.Consumer<Map<Long, Integer>> done) {
            this.id = id;
            this.owner = owner;
            this.plugin = plugin;
            this.world = world;
            this.region = region;
            this.snap = snap;
            this.maybe = maybe;
            this.nms = nms;
            this.done = done;
            Box b = snap.box;
            x = b.minX();
            y = b.minY();
            z = b.minZ();
        }

        @Override
        public boolean step(long deadlineNanos, int maxBlocks) {
            if (finished || cancelled) return true;
            Box b = snap.box;
            PaperBlocks blocks = blocks();
            while (y <= b.maxY()) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    world.getChunkAtAsync(x >> 4, z >> 4);
                    return false;
                }
                int st = snap.get(x, y, z);
                if (st >= 0 && maybe.test(st) && region.contains(x, y, z)) {
                    BlockData now = world.getBlockAt(x, y, z).getBlockData();
                    BlockData shaped = nms.shape(world, x, y, z, now);
                    if (!shaped.equals(now)) out.put(PointsSource.pack(x, y, z), blocks.id(shaped));
                }
                if (++x > b.maxX()) {
                    x = b.minX();
                    if (++z > b.maxZ()) {
                        z = b.minZ();
                        y++;
                    }
                }
                if (System.nanoTime() >= deadlineNanos) return false;
            }
            finished = true;
            done.accept(out);
            return true;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public UUID owner() {
            return owner;
        }

        @Override
        public String world() {
            return world.getName();
        }

        @Override
        public Box bounds() {
            return null;
        }
    }

    // ------------------------------------------------------------------ H3 dónde se pone, la varita, el pincel

    /** H3 CA1: /wc placement [player|pos1|pos2|center] [x,y,z]; sin nada, dice el de ahora. */
    private void placement(CommandSender s, Args args) {
        Session ses = edits().session(Edits.owner(s));
        if (args.positional.isEmpty()) {
            s.sendMessage(msg().get("placement-now", "placement", ses.placement, "offset", offset(ses.placementOffset)));
            return;
        }
        String what = args.positional.get(0).toLowerCase(Locale.ROOT);
        if (what.equals("here") || what.equals("feet")) what = "player";
        if (!PLACEMENTS.contains(what)) {
            s.sendMessage(msg().get("error", "message", "'" + args.positional.get(0) + "' is not a placement"
                    + hint(Issues.closest(what, PLACEMENTS)) + ": player, pos1, pos2 or center"));
            return;
        }
        int[] off = {0, 0, 0};
        if (args.positional.size() > 1) {
            String[] c = String.join("", args.positional.subList(1, args.positional.size())).split(",");
            if (c.length != 3) throw new WorldcraftCommand.Usage();
            try {
                for (int i = 0; i < 3; i++) off[i] = Integer.parseInt(c[i].trim());
            } catch (NumberFormatException e) {
                throw new WorldcraftCommand.Usage();
            }
        }
        ses.placement = what;
        ses.placementOffset = off;
        s.sendMessage(msg().get("placement-set", "placement", what, "offset", offset(off)));
    }

    private static String offset(int[] o) {
        return (o[0] | o[1] | o[2]) == 0 ? "" : " + " + o[0] + "," + o[1] + "," + o[2];
    }

    /** H3 CA1: /wc toggleplace, los pies ↔ la esquina 1 (como WorldEdit). */
    private void toggleplace(CommandSender s) {
        Session ses = edits().session(Edits.owner(s));
        ses.placement = ses.placement.equals("player") ? "pos1" : "player";
        s.sendMessage(msg().get("placement-set", "placement", ses.placement, "offset", offset(ses.placementOffset)));
    }

    /** H3 CA2: /wc toggleeditwand. */
    private void toggleeditwand(CommandSender s) {
        Player p = player(s);
        if (p == null) return;
        Session ses = edits().session(p.getUniqueId());
        ses.wandOff = !ses.wandOff;
        s.sendMessage(msg().get(ses.wandOff ? "wand-off" : "wand-on"));
    }

    /** El pincel o herramienta de construcción de la mano, o null (y lo dice). */
    private ToolSpec held(Player p) {
        ToolSpec spec = plugin.tools().spec(p.getInventory().getItemInMainHand());
        if (spec != null) return spec;
        p.sendMessage(msg().get(plugin.brush().settings(p.getInventory().getItemInMainHand()) != null ? "range-terrain" : "no-brush-in-hand"));
        return null;
    }

    /** H3 CA3: /wc range &lt;bloques&gt; para el pincel de la mano. */
    private void range(CommandSender s, Args args) {
        Player p = player(s);
        if (p == null) return;
        if (args.positional.size() != 1) throw new WorldcraftCommand.Usage();
        int max = plugin.config().tools().range(), n;
        try {
            n = Integer.parseInt(args.positional.get(0));
        } catch (NumberFormatException e) {
            throw new WorldcraftCommand.Usage();
        }
        if (n < 1 || n > max) throw new IllegalArgumentException("The range must be 1 to " + max + " (tools.range)");
        ToolSpec spec = held(p);
        if (spec == null) return;
        ToolSpec next = spec.with("reach", String.valueOf(n));
        plugin.tools().write(p.getInventory().getItemInMainHand(), next);
        p.sendMessage(msg().get("tool-updated", "what", next.kind().brush ? "brush" : "tool", "tool", next.describe()));
    }

    /** H3 CA4: /wc tracemask &lt;máscara&gt;|off para el pincel de la mano. */
    private void tracemask(CommandSender s, Args args) throws ParseException {
        Player p = player(s);
        if (p == null) return;
        ToolSpec spec = held(p);
        if (spec == null) return;
        String text = args.join(0).trim();
        ToolSpec next;
        if (text.isEmpty() || text.equalsIgnoreCase("off") || text.equals("*")) {
            next = spec.with("trace", null);
        } else {
            plugin.config().parser().mask(text);
            next = spec.with("trace", ToolSpec.encodeText(text));
        }
        plugin.tools().write(p.getInventory().getItemInMainHand(), next);
        p.sendMessage(msg().get("tool-updated", "what", next.kind().brush ? "brush" : "tool", "tool", next.describe()));
    }

    // ------------------------------------------------------------------ H4 información

    /** H4 CA1: /wc chunkinfo. */
    private void chunkinfo(CommandSender s) {
        Player p = player(s);
        if (p == null) return;
        Location l = p.getLocation();
        int[] c = Info.chunk(l.getBlockX(), l.getBlockY(), l.getBlockZ());
        s.sendMessage(msg().get("chunk-info", "chunk", c[0] + ", " + c[1], "region", Info.region(c[0], c[1]),
                "inside", c[2] + " " + c[3] + " " + c[4], "world", p.getWorld().getName()));
    }

    /** H4 CA2: /wc listchunks [página]. */
    private void listchunks(CommandSender s, Args args) {
        Region r = selection(s);
        if (r == null) return;
        int page = 1;
        if (!args.positional.isEmpty()) {
            try {
                page = Integer.parseInt(args.positional.get(0));
            } catch (NumberFormatException e) {
                throw new WorldcraftCommand.Usage();
            }
        }
        List<int[]> all = Info.chunks(r.bounds());
        int pages = Info.pages(all.size(), PER_PAGE);
        page = Math.max(1, Math.min(page, pages));
        s.sendMessage(msg().get("chunks-header", "count", String.valueOf(all.size()), "page", String.valueOf(page), "pages", String.valueOf(pages)));
        for (int[] c : Info.page(all, page, PER_PAGE)) {
            s.sendMessage(msg().get("chunks-line", "chunk", c[0] + ", " + c[1], "region", Info.region(c[0], c[1])));
        }
        if (page < pages) s.sendMessage(msg().get("chunks-more", "next", String.valueOf(page + 1)));
    }

    /** H4 CA3: /wc searchitem &lt;texto&gt; [página] [--blocks|--items]. */
    private void searchitem(CommandSender s, Args args) {
        List<String> words = new ArrayList<>(args.positional);
        int page = 1;
        if (words.size() > 1 && words.get(words.size() - 1).matches("\\d{1,4}")) page = Integer.parseInt(words.remove(words.size() - 1));
        if (words.isEmpty()) throw new WorldcraftCommand.Usage();
        boolean onlyBlocks = args.flags.contains("blocks"), onlyItems = args.flags.contains("items");
        List<String> keys = new ArrayList<>();
        Map<String, String> kind = new HashMap<>();
        for (Material m : Material.values()) {
            if (m.isLegacy()) continue;
            boolean block = m.isBlock(), item = m.isItem();
            if (onlyBlocks && !block || onlyItems && !item) continue;
            String k = m.getKey().toString();
            keys.add(k);
            kind.put(k, block && item ? "block and item" : block ? "block" : "item");
        }
        String q = String.join(" ", words);
        List<String> found = Info.search(keys, q);
        if (found.isEmpty()) {
            s.sendMessage(msg().get("search-none", "text", q));
            return;
        }
        int pages = Info.pages(found.size(), PER_PAGE);
        page = Math.max(1, Math.min(page, pages));
        s.sendMessage(msg().get("search-header", "count", String.valueOf(found.size()), "text", q, "page", String.valueOf(page), "pages", String.valueOf(pages)));
        for (String k : Info.page(found, page, PER_PAGE)) s.sendMessage(msg().get("search-line", "id", k.replace("minecraft:", ""), "kind", kind.get(k)));
    }

    /** Worldcraft, Paper, Java y el puente de los features (H4 CA5). */
    private String[] versions() {
        Nms nms = Nms.get();
        return new String[]{plugin.getPluginMeta().getVersion(), Bukkit.getName() + " " + Bukkit.getVersion(),
                System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")",
                nms.available() ? "available (" + nms.features().size() + " features)" : "not available: " + nms.problem()};
    }

    private void version(CommandSender s) {
        String[] v = versions();
        s.sendMessage(msg().get("version", "version", v[0], "server", v[1], "java", v[2], "features", v[3]));
    }

    /** H4 CA5: /wc report escribe un archivo en reports/ (nunca lo sube a ningún lado). */
    /** Spec 041 H4 CA11: /wc debugpaste, el informe con el final del log y los .yml (tampoco se sube). */
    void debugpaste(CommandSender s) {
        report(s, true);
    }

    private void report(CommandSender s, boolean debug) {
        String[] v = versions();
        StringBuilder sb = new StringBuilder();
        sb.append("Worldcraft report, ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");
        sb.append("Worldcraft: ").append(v[0]).append("\nServer: ").append(v[1]).append("\nJava: ").append(v[2])
                .append("\nOS: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
                .append("\nGame features (//feature, //update): ").append(v[3])
                .append("\nLight and NBT (//fixlighting, //nbtinfo): ").append(Nms.get().lightAvailable() ? "available" : "not available: " + Nms.get().lightProblem())
                .append("\n\nWorlds:\n");
        for (World w : Bukkit.getWorlds()) {
            sb.append("  ").append(w.getName()).append(" (").append(w.getEnvironment()).append(", ").append(w.getMinHeight()).append("..")
                    .append(w.getMaxHeight() - 1).append(", ").append(w.getLoadedChunks().length).append(" chunks loaded)\n");
        }
        sb.append("\nPlugins:\n");
        for (Plugin pl : Bukkit.getPluginManager().getPlugins()) {
            sb.append("  ").append(pl.getName()).append(' ').append(pl.getPluginMeta().getVersion()).append(pl.isEnabled() ? "" : " (disabled)").append('\n');
        }
        var lim = edits().limits(s);
        sb.append("\nLimits of who asked: groups ").append(String.join(", ", lim.groups())).append(", max blocks ").append(lim.maxBlocks())
                .append(", max jobs ").append(lim.maxJobs()).append('\n');
        sb.append("\nRunning edits: ").append(edits().jobs().size()).append('\n');
        for (var j : edits().jobs()) sb.append("  #").append(j.id()).append(' ').append(j.label()).append(" in ").append(j.world()).append('\n');
        var issues = plugin.config().issues();
        sb.append("\nConfiguration: ").append(issues.errors()).append(" errors, ").append(issues.warnings()).append(" warnings\n");
        for (var i : issues.all()) sb.append("  ").append(i).append('\n');
        Path folder = plugin.getDataFolder().toPath();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT));
        Path file = folder.resolve("reports").resolve((debug ? "debugpaste-" : "report-") + stamp + ".txt");
        Path logs = plugin.getServer().getWorldContainer().toPath().resolve("logs").resolve("latest.log");
        CompletableFuture.runAsync(() -> {
            try {
                Path cfg = folder.resolve("config.yml");
                sb.append("\nconfig.yml:\n").append(Files.exists(cfg) ? Files.readString(cfg, StandardCharsets.UTF_8) : "(none: all defaults)\n");
                if (debug) {
                    for (String y : List.of("messages.yml", "item-uses.yml", "slash-commands.yml")) {
                        Path f = folder.resolve(y);
                        if (Files.exists(f)) sb.append('\n').append(y).append(":\n").append(Files.readString(f, StandardCharsets.UTF_8));
                    }
                    if (Files.exists(logs)) {
                        List<String> lines = Files.readAllLines(logs, StandardCharsets.UTF_8);
                        sb.append("\nlogs/latest.log (last ").append(Math.min(200, lines.size())).append(" lines):\n");
                        for (String l : lines.subList(Math.max(0, lines.size() - 200), lines.size())) sb.append(l).append('\n');
                    }
                }
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
                Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, plugin.io()).whenComplete((ok, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Could not write the report", err);
                s.sendMessage(msg().get("report-failed"));
                return;
            }
            s.sendMessage(msg().get("report-written", "file", "plugins/" + plugin.getName() + "/reports/" + file.getFileName()));
        }));
    }

    /** H4 CA5: /wc trace [on|off]. */
    private void trace(CommandSender s, Args args) {
        Session ses = edits().session(Edits.owner(s));
        String v = args.positional.isEmpty() ? "" : args.positional.get(0).toLowerCase(Locale.ROOT);
        ses.trace = switch (v) {
            case "on" -> true;
            case "off" -> false;
            case "" -> !ses.trace;
            default -> throw new WorldcraftCommand.Usage();
        };
        s.sendMessage(msg().get(ses.trace ? "trace-on" : "trace-off"));
    }

    /** H4 CA5: /wc tz [zona]; sin nada, dice la de ahora. */
    private void tz(CommandSender s, Args args) {
        Session ses = edits().session(Edits.owner(s));
        if (args.positional.isEmpty()) {
            ZoneId z = ses.zone == null ? ZoneId.systemDefault() : ses.zone;
            s.sendMessage(msg().get("tz-now", "zone", z.getId(), "time", LocalDateTime.now(z).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
            return;
        }
        String text = args.positional.get(0);
        ZoneId z;
        try {
            z = ZoneId.of(text.equalsIgnoreCase("server") ? ZoneId.systemDefault().getId() : text);
        } catch (java.time.DateTimeException e) {
            s.sendMessage(msg().get("tz-unknown", "input", text, "hint", hint(Issues.closest(text, ZoneId.getAvailableZoneIds()))));
            return;
        }
        ses.zone = text.equalsIgnoreCase("server") ? null : z;
        s.sendMessage(msg().get("tz-set", "zone", z.getId(), "time", LocalDateTime.now(z).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
    }

    // ------------------------------------------------------------------ autocompletado

    List<String> complete(CommandSender s, String sub, String[] a, String last) {
        return switch (sub) {
            case "curve" -> a.length > 2 && !last.isEmpty() && Character.isDigit(last.charAt(0)) ? WorldcraftCommand.filter(List.of("1", "2", "3"), last)
                    : main.blockSuggestions(last, false);
            case "snowsmooth" -> a.length == 2 ? WorldcraftCommand.filter(List.of("1", "2", "3", "5"), last) : List.of();
            case "feature" -> a.length == 2 ? featureSuggestions(last) : List.of();
            case "placement" -> a.length == 2 ? WorldcraftCommand.filter(PLACEMENTS, last) : a.length == 3 ? WorldcraftCommand.filter(List.of("0,1,0"), last) : List.of();
            case "range" -> a.length == 2 ? WorldcraftCommand.filter(List.of("10", "50", "100", "200"), last) : List.of();
            case "tracemask" -> a.length == 2 ? merge(WorldcraftCommand.filter(List.of("off"), last), main.blockSuggestions(last, true)) : main.blockSuggestions(last, true);
            case "trace" -> a.length == 2 ? WorldcraftCommand.filter(List.of("on", "off"), last) : List.of();
            case "tz" -> a.length == 2 ? zoneSuggestions(last) : List.of();
            case "searchitem" -> List.of();
            case "listchunks" -> a.length == 2 ? WorldcraftCommand.filter(List.of("1", "2"), last) : List.of();
            default -> List.of();
        };
    }

    List<String> featureSuggestions(String last) {
        List<String> out = new ArrayList<>();
        String q = last.toLowerCase(Locale.ROOT);
        for (String f : Nms.get().features()) {
            String n = f.startsWith("minecraft:") ? f.substring(10) : f;
            if ((n.startsWith(q) || f.startsWith(q)) && out.size() < 80) out.add(n);
        }
        return out;
    }

    private static List<String> zoneSuggestions(String last) {
        List<String> out = new ArrayList<>(List.of("server", "UTC"));
        for (String z : new java.util.TreeSet<>(ZoneId.getAvailableZoneIds())) if (z.contains("/") && out.size() < 60) out.add(z);
        List<String> f = new ArrayList<>();
        for (String z : out) if (z.toLowerCase(Locale.ROOT).startsWith(last.toLowerCase(Locale.ROOT))) f.add(z);
        if (f.isEmpty() && !last.isEmpty()) {
            for (String z : new java.util.TreeSet<>(ZoneId.getAvailableZoneIds())) if (z.toLowerCase(Locale.ROOT).startsWith(last.toLowerCase(Locale.ROOT)) && f.size() < 60) f.add(z);
        }
        return f;
    }

    private static List<String> merge(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}
