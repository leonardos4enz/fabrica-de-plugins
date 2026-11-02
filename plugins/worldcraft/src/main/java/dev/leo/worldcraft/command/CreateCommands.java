package dev.leo.worldcraft.command;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.config.CreateSettings;
import dev.leo.worldcraft.config.Limits;
import dev.leo.worldcraft.config.Messages;
import dev.leo.worldcraft.create.Caves;
import dev.leo.worldcraft.create.ImageBlocks;
import dev.leo.worldcraft.create.Ores;
import dev.leo.worldcraft.edit.EditSource;
import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.formula.Shapes;
import dev.leo.worldcraft.paper.Edits;
import dev.leo.worldcraft.paper.Images;
import dev.leo.worldcraft.paper.Session;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Region;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spec 035 (plan §1.5): lo de FAWE para crear. caves, ore y ores calculan los puntos en el hilo de cálculo (con el plazo
 * de /wc timeout); image lee y elige los bloques en el hilo de imágenes. Después todo es una obra del motor de la 025.
 */
final class CreateCommands {
    static final List<String> SUBS = List.of("caves", "ore", "ores", "image");
    static final Map<String, String> ALIASES = Map.of("img", "image");
    static final String PERM_LINK = "worldcraft.image.link";

    private final Worldcraft plugin;
    private final WorldcraftCommand main;

    CreateCommands(Worldcraft plugin, WorldcraftCommand main) {
        this.plugin = plugin;
        this.main = main;
    }

    static boolean handles(String sub) {
        return SUBS.contains(sub) || ALIASES.containsKey(sub);
    }

    static String canonical(String sub) {
        return ALIASES.getOrDefault(sub, sub);
    }

    static Set<String> flags(String sub) {
        return switch (canonical(sub)) {
            case "caves" -> Set.of("rooms", "preview");
            case "image" -> Set.of("floor", "wall", "preview");
            default -> Set.of("preview");
        };
    }

    private Messages msg() {
        return plugin.messages();
    }

    private Edits edits() {
        return plugin.edits();
    }

    private CreateSettings cfg() {
        return plugin.config().create();
    }

    void run(CommandSender s, String sub, String[] a) {
        String c = canonical(sub);
        // -d y -b de //ores de FAWE: aquí el deepslate va solo según la roca (no hacen falta).
        String[] in = c.equals("ores") ? Arrays.stream(a).filter(w -> !w.equals("-d") && !w.equals("-b")).toArray(String[]::new) : a;
        Args args = Args.parse(in, 1, flags(c));
        try {
            if (args.problem != null) throw new IllegalArgumentException(args.problem);
            switch (c) {
                case "caves" -> caves(s, args);
                case "ore" -> ore(s, args);
                case "ores" -> ores(s, args);
                default -> image(s, args);
            }
        } catch (ParseException | IllegalArgumentException e) {
            s.sendMessage(msg().get("error", "message", e.getMessage()));
        } catch (Images.Problem p) {
            s.sendMessage(msg().get(p.key(), p.args()));
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

    /** Un número entero entre lo y hi, o el de fábrica si no se escribió. */
    private static int number(List<String> pos, int i, int def, int lo, int hi, String what) {
        if (pos.size() <= i) return def;
        String v = pos.get(i);
        int n;
        try {
            n = Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + v + "' is not a whole number (" + what + ")");
        }
        if (n < lo || n > hi) throw new IllegalArgumentException("The " + what + " goes from " + lo + " to " + hi + " (you wrote " + n + ")");
        return n;
    }

    private int state(String text) throws ParseException {
        return edits().blocks().table().intern(plugin.config().parser().pattern(text).placedStates().get(0));
    }

    private void request(CommandSender s, String kind, String detail, World w, long[] pts, PointsSource.Pick pick,
                         java.util.Collection<Integer> placed, Args args) {
        PointsSource probe = new PointsSource(pts, pick);
        edits().request(s, new Edits.Op(kind, detail, w, probe.bounds(), probe.volume(), () -> new PointsSource(pts, pick), placed, null),
                args.flags.contains("preview"));
    }

    // ------------------------------------------------------------------ H3 caves

    private void caves(CommandSender s, Args args) throws ParseException {
        if (args.positional.size() > 2) throw new WorldcraftCommand.Usage();
        int size = number(args.positional, 0, 3, 1, 10, "size");
        int freq = number(args.positional, 1, 40, 1, 100, "frequency");
        boolean rooms = args.flags.contains("rooms");
        Session ses = edits().session(Edits.owner(s));
        Region region = main.selection(s, ses);
        if (region == null) return;
        World w = Bukkit.getWorld(ses.world());
        if (overLimit(s, region.volume())) return;
        CreateSettings c = cfg();
        Mask.Compiled mask = plugin.config().parser().mask(c.cavesMask()).compile(edits().blocks().table());
        int air = edits().blocks().air(), lava = state("lava");
        int lavaY = c.lavaY();
        long seed = ThreadLocalRandom.current().nextLong();
        Shapes.Work work = plugin.formulas().work(s);
        s.sendMessage(msg().get("caves-calculating", "blocks", Edits.fmt(region.volume())));
        plugin.formulas().compute(s, work, region.volume(), () -> Caves.carve(region, size, freq, rooms, seed, work), pts -> {
            if (pts.length == 0) {
                s.sendMessage(msg().get("caves-nothing"));
                return;
            }
            s.sendMessage(msg().get("caves-planned", "blocks", Edits.fmt(pts.length), "mask", c.cavesMask()));
            PointsSource.Pick pick = (x, y, z, cur) -> mask.test(cur) ? (y <= lavaY ? lava : air) : EditSource.KEEP;
            request(s, "caves", "size " + size + " frequency " + freq + (rooms ? " --rooms" : ""), w, pts, pick,
                    y(region) <= lavaY ? List.of(air, lava) : List.of(air), args);
        }, null, "caves");
    }

    private static int y(Region r) {
        return r.bounds().minY();
    }

    // ------------------------------------------------------------------ H2 ore y ores

    private void ore(CommandSender s, Args args) throws ParseException {
        List<String> p = args.positional;
        if (p.size() < 2 || p.size() > 7) throw new WorldcraftCommand.Usage();
        Mask maskText = plugin.config().parser().mask(p.get(0));
        Pattern pattern = plugin.config().parser().pattern(p.get(1));
        int size = number(p, 2, 8, 1, cfg().maxVein(), "size (ores.max_size)");
        int freq = number(p, 3, 10, 1, 1000, "frequency");
        int rarity = number(p, 4, 100, 0, 100, "rarity");
        Session ses = edits().session(Edits.owner(s));
        Region region = main.selection(s, ses);
        if (region == null) return;
        World w = Bukkit.getWorld(ses.world());
        Box b = region.bounds();
        int minY = number(p, 5, b.minY(), -2048, 2047, "min y"), maxY = number(p, 6, b.maxY(), -2048, 2047, "max y");
        if (minY > maxY) throw new IllegalArgumentException("The min y (" + minY + ") is above the max y (" + maxY + ")");
        if (overLimit(s, region.volume())) return;
        Mask.Compiled mask = maskText.compile(edits().blocks().table());
        Pattern.Compiled pc = pattern.compile(edits().blocks().table());
        List<Integer> placed = new ArrayList<>();
        for (String st : pattern.placedStates()) placed.add(edits().blocks().table().intern(st));
        long seed = ThreadLocalRandom.current().nextLong();
        Ores.Spec spec = new Ores.Spec(size, freq, rarity, minY, maxY);
        Shapes.Work work = plugin.formulas().work(s);
        plugin.formulas().compute(s, work, region.volume(), () -> Ores.place(region, spec, seed, work), r -> {
            if (r.points().length == 0) {
                s.sendMessage(msg().get("ore-nothing", "min", String.valueOf(minY), "max", String.valueOf(maxY), "rarity", String.valueOf(rarity)));
                return;
            }
            s.sendMessage(msg().get("ore-planned", "veins", Edits.fmt(r.veins()), "blocks", Edits.fmt(r.points().length), "mask", maskText.text()));
            PointsSource.Pick pick = (x, y, z, cur) -> mask.test(cur) ? pc.apply(x, y, z, cur, seed) : EditSource.KEEP;
            request(s, "ore", maskText.text() + " " + pattern.text() + " " + size + " " + freq + " " + rarity + " " + minY + " " + maxY,
                    w, r.points(), pick, placed, args);
        }, null, "ores");
    }

    private void ores(CommandSender s, Args args) throws ParseException {
        CreateSettings c = cfg();
        String maskText = args.positional.isEmpty() ? c.oresMask() : args.join(0);
        Mask mask = plugin.config().parser().mask(maskText);
        Session ses = edits().session(Edits.owner(s));
        Region region = main.selection(s, ses);
        if (region == null) return;
        World w = Bukkit.getWorld(ses.world());
        if (overLimit(s, region.volume())) return;
        Box b = region.bounds();
        List<CreateSettings.Ore> kinds = new ArrayList<>();
        for (CreateSettings.Ore o : c.ores()) if (o.minY() <= b.maxY() && o.maxY() >= b.minY()) kinds.add(o);
        if (kinds.isEmpty()) {
            s.sendMessage(msg().get("ores-nothing", "min", String.valueOf(b.minY()), "max", String.valueOf(b.maxY())));
            return;
        }
        var table = edits().blocks().table();
        Mask.Compiled mc = mask.compile(table);
        Mask.Compiled deep = plugin.config().parser().mask(c.deepslateIn()).compile(table);
        int[] block = new int[kinds.size()], deepBlock = new int[kinds.size()];
        Set<Integer> placed = new LinkedHashSet<>();
        for (int i = 0; i < kinds.size(); i++) {
            block[i] = table.intern(kinds.get(i).block());
            deepBlock[i] = table.intern(kinds.get(i).deepslate());
            placed.add(block[i]);
            placed.add(deepBlock[i]);
        }
        long seed = ThreadLocalRandom.current().nextLong();
        Shapes.Work work = plugin.formulas().work(s);
        plugin.formulas().compute(s, work, region.volume(), () -> {
            // Cada mineral con su semilla; un punto que piden dos se queda con el primero de la lista.
            Map<Long, Integer> at = new HashMap<>();
            int veins = 0;
            for (int i = 0; i < kinds.size(); i++) {
                CreateSettings.Ore o = kinds.get(i);
                Ores.Result r = Ores.place(region, new Ores.Spec(o.size(), o.perChunk(), 100, o.minY(), o.maxY()), seed + i * 0x9E3779B97F4A7C15L, work);
                veins += r.veins();
                for (long pt : r.points()) at.putIfAbsent(pt, i);
            }
            return new Object[]{at, veins};
        }, res -> {
            @SuppressWarnings("unchecked") Map<Long, Integer> at = (Map<Long, Integer>) res[0];
            int veins = (int) res[1];
            if (at.isEmpty()) {
                s.sendMessage(msg().get("ores-nothing", "min", String.valueOf(b.minY()), "max", String.valueOf(b.maxY())));
                return;
            }
            List<String> names = new ArrayList<>();
            for (CreateSettings.Ore o : kinds) names.add(o.name());
            s.sendMessage(msg().get("ores-planned", "veins", Edits.fmt(veins), "kinds", String.join(", ", names), "blocks", Edits.fmt(at.size()),
                    "mask", mask.text(), "deepslate", c.deepslateIn()));
            long[] pts = new long[at.size()];
            int n = 0;
            for (long pt : at.keySet()) pts[n++] = pt;
            PointsSource.Pick pick = (x, y, z, cur) -> {
                if (!mc.test(cur)) return EditSource.KEEP;
                Integer i = at.get(PointsSource.pack(x, y, z));
                if (i == null) return EditSource.KEEP;
                return deep.test(cur) ? deepBlock[i] : block[i];
            };
            request(s, "ores", mask.text(), w, pts, pick, placed, args);
        }, null, "ores");
    }

    // ------------------------------------------------------------------ H4 image

    private void image(CommandSender s, Args args) {
        if (args.positional.isEmpty()) throw new WorldcraftCommand.Usage();
        String source = args.positional.get(0);
        Images images = plugin.images();
        String folder = plugin.getDataFolder().getName() + "/" + cfg().imagesFolder();
        if (source.equalsIgnoreCase("list") && args.positional.size() == 1) {
            List<String> files = images.files();
            s.sendMessage(files.isEmpty() ? msg().get("image-list-empty", "folder", folder)
                    : msg().get("image-list", "folder", folder, "names", String.join(", ", files)));
            return;
        }
        if (args.positional.size() > 2) throw new WorldcraftCommand.Usage();
        if (!(s instanceof Player p)) {
            s.sendMessage(msg().get("image-in-game"));
            return;
        }
        if (args.flags.contains("floor") && args.flags.contains("wall")) throw new IllegalArgumentException("Choose one of --floor and --wall");
        int max = cfg().imagesMaxSize();
        int side = 0;
        if (args.positional.size() == 2) {
            String v = args.positional.get(1);
            try {
                side = Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + v + "' is not a whole number (the size, in blocks on the long side)");
            }
            if (side < 1) throw new IllegalArgumentException("The size is at least 1");
            if (side > max) {
                s.sendMessage(msg().get("image-too-big", "size", String.valueOf(side), "max", String.valueOf(max)));
                return;
            }
        }
        if (Images.isLink(source)) {
            if (!cfg().links()) {
                s.sendMessage(msg().get("links-off", "folder", folder));
                return;
            }
            if (!s.hasPermission(PERM_LINK)) {
                s.sendMessage(msg().get("no-permission"));
                return;
            }
        }
        // Dónde y hacia dónde, al pedirla (la descarga puede tardar y el jugador moverse).
        var loc = p.getLocation();
        World w = p.getWorld();
        int fx = loc.getBlockX(), fy = loc.getBlockY(), fz = loc.getBlockZ();
        int[] d = ImageBlocks.facing(loc.getYaw());
        boolean floor = args.flags.contains("floor") || (!args.flags.contains("wall") && loc.getPitch() > 45);
        String label = Images.isLink(source) ? images.link(source).label() : source;
        s.sendMessage(msg().get("image-loading", "name", label));
        images.load(s, source, side, img -> place(p, w, img, fx, fy, fz, d, floor, args));
    }

    private void place(Player p, World w, Images.Loaded img, int fx, int fy, int fz, int[] d, boolean floor, Args args) {
        int width = img.width(), height = img.height();
        ImageBlocks.Placement at = floor ? ImageBlocks.floor(fx, fy, fz, d[0], d[1], width, height)
                : ImageBlocks.wall(fx, fy, fz, d[0], d[1], width, height);
        var parser = plugin.config().parser();
        var colors = parser.colors();
        var table = edits().blocks().table();
        int[] ids = new int[colors.size()];
        Set<Integer> placed = new LinkedHashSet<>();
        for (int i = 0; i < ids.length; i++) {
            String st = parser.colorState(colors.entries().get(i).id());
            ids[i] = st == null ? -1 : table.intern(st);
        }
        int lo = w.getMinHeight(), hi = w.getMaxHeight() - 1;
        int[] blocks = img.blocks();
        long[] pts = new long[blocks.length];
        int n = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int b = blocks[row * width + col];
                if (b < 0 || ids[b] < 0) continue;
                int y = at.y(col, row);
                if (y < lo || y > hi) continue;
                pts[n++] = PointsSource.pack(at.x(col, row), y, at.z(col, row));
                placed.add(ids[b]);
            }
        }
        if (n == 0) {
            p.sendMessage(msg().get("image-invalid", "name", img.label(), "reason", "every pixel is transparent or outside the world"));
            return;
        }
        long[] points = Arrays.copyOf(pts, n);
        String where = floor ? "on the ground ahead of you" : "standing in front of you";
        p.sendMessage(msg().get("image-planned", "name", img.label(), "source", Images.size(img.sourceWidth(), img.sourceHeight()),
                "size", Images.size(width, height), "where", where, "blocks", Edits.fmt(n)));
        PointsSource.Pick pick = (x, y, z, cur) -> {
            int col = at.col(x, y, z), row = at.row(x, y, z);
            if (col < 0 || col >= width || row < 0 || row >= height) return EditSource.KEEP;
            int b = blocks[row * width + col];
            return b < 0 || ids[b] < 0 ? EditSource.KEEP : ids[b];
        };
        request(p, "image", img.label() + " " + Images.size(width, height) + (floor ? " --floor" : " --wall"), w, points, pick, placed, args);
    }

    // ------------------------------------------------------------------ autocompletado

    List<String> complete(String sub, String[] a, String last) {
        String c = canonical(sub);
        long words = Arrays.stream(a).skip(1).filter(x -> !x.startsWith("-")).count();
        return switch (c) {
            case "caves" -> words <= 1 ? WorldcraftCommand.filter(List.of("2", "3", "5", "8"), last)
                    : words == 2 ? WorldcraftCommand.filter(List.of("20", "40", "60", "80"), last) : List.of();
            case "ore" -> words <= 1 ? main.blockSuggestions(last, true) : words == 2 ? main.blockSuggestions(last, false)
                    : words == 3 ? WorldcraftCommand.filter(List.of("4", "8", "12", "17"), last) : List.of();
            case "ores" -> main.blockSuggestions(last, true);
            case "image" -> {
                if (words > 1) yield WorldcraftCommand.filter(List.of("32", "64", "128"), last);
                List<String> out = new ArrayList<>(plugin.images().files());
                out.add("list");
                if (cfg().links()) out.add("https://");
                yield WorldcraftCommand.filter(out, last);
            }
            default -> List.of();
        };
    }
}
