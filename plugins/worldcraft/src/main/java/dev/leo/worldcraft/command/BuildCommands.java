package dev.leo.worldcraft.command;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.config.Limits;
import dev.leo.worldcraft.config.Messages;
import dev.leo.worldcraft.edit.ColumnEdit;
import dev.leo.worldcraft.edit.EditSource;
import dev.leo.worldcraft.edit.Flood;
import dev.leo.worldcraft.edit.Liquids;
import dev.leo.worldcraft.edit.MoveSource;
import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.edit.RegionSource;
import dev.leo.worldcraft.edit.SolidSource;
import dev.leo.worldcraft.edit.Snapshot;
import dev.leo.worldcraft.edit.StackSource;
import dev.leo.worldcraft.edit.TopScan;
import dev.leo.worldcraft.paper.Edits;
import dev.leo.worldcraft.paper.PaperBlocks;
import dev.leo.worldcraft.paper.Session;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Direction;
import dev.leo.worldcraft.region.Line;
import dev.leo.worldcraft.region.Region;
import dev.leo.worldcraft.region.Shape;
import dev.leo.worldcraft.region.Solid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Spec 028: formas (H1), operaciones de región (H2) y utilidades alrededor del jugador (H3). Cada comando arma una
 * {@link EditSource} y la pide con {@link Edits#request}: límites, vista previa, historial y tandas de la 025.
 */
final class BuildCommands {
    static final List<String> SHAPES = List.of("sphere", "hsphere", "cyl", "hcyl", "pyramid", "hpyramid", "cone");
    static final List<String> REGION = List.of("stack", "move", "overlay", "naturalize", "smooth", "hollow", "center", "line");
    static final List<String> UTILITIES = List.of("drain", "fixwater", "fixlava", "fill", "removenear", "replacenear",
            "removeabove", "removebelow", "extinguish", "snow", "thaw", "green");

    /** Las opciones de cada comando (para Args y el autocompletado). */
    static Set<String> flags(String sub) {
        if (SHAPES.contains(sub)) {
            Set<String> f = new java.util.HashSet<>(Set.of("preview", "at", "world"));
            if (sub.equals("sphere") || sub.equals("hsphere")) f.add("raised");
            if (sub.startsWith("h")) f.add("thickness");
            return f;
        }
        return switch (sub) {
            case "stack" -> Set.of("preview", "air", "select");
            case "move" -> Set.of("preview", "select");
            case "drain" -> Set.of("preview", "at", "world", "waterlogged");
            default -> UTILITIES.contains(sub) ? Set.of("preview", "at", "world") : Set.of("preview");
        };
    }

    private final Worldcraft plugin;
    private final WorldcraftCommand main;
    private final Map<Integer, Boolean> sturdy = new HashMap<>();

    BuildCommands(Worldcraft plugin, WorldcraftCommand main) {
        this.plugin = plugin;
        this.main = main;
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

    private StateTable table() {
        return blocks().table();
    }

    void run(CommandSender s, String sub, String[] a) {
        Args args = Args.parse(a, 1, flags(sub));
        if (args.problem != null) {
            s.sendMessage(msg().get("error", "message", args.problem));
            return;
        }
        try {
            if (SHAPES.contains(sub)) shape(s, sub, args);
            else switch (sub) {
                case "stack" -> stack(s, args);
                case "move" -> move(s, args);
                case "overlay" -> overlay(s, args);
                case "naturalize" -> naturalize(s, args);
                case "smooth" -> smooth(s, args);
                case "hollow" -> hollow(s, args);
                case "center" -> center(s, args);
                case "line" -> line(s, args);
                case "drain", "fixwater", "fixlava" -> liquid(s, sub, args);
                case "fill" -> fill(s, args);
                case "removenear", "replacenear", "extinguish" -> near(s, sub, args);
                case "removeabove", "removebelow" -> remove(s, sub, args);
                case "snow" -> snow(s, args);
                case "thaw", "green" -> top(s, sub, args);
                default -> throw new WorldcraftCommand.Usage();
            }
        } catch (ParseException | IllegalArgumentException e) {
            s.sendMessage(msg().get("error", "message", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ utilidades de argumentos

    /** Saca del final los números («5», «3,2,4», «-6») y los devuelve en orden; deja el resto (el patrón). */
    static List<int[]> trailingNumbers(List<String> pos, int max) {
        List<int[]> out = new ArrayList<>();
        while (!pos.isEmpty() && out.size() < max && pos.get(pos.size() - 1).matches("-?\\d+(,-?\\d+){0,2}")) {
            String[] parts = pos.remove(pos.size() - 1).split(",");
            int[] v = new int[parts.length];
            for (int i = 0; i < parts.length; i++) v[i] = Integer.parseInt(parts[i]);
            out.add(0, v);
        }
        return out;
    }

    private static int intArg(Args args, int i, int def) {
        if (i >= args.positional.size()) return def;
        try {
            return Integer.parseInt(args.positional.get(i));
        } catch (NumberFormatException e) {
            throw new WorldcraftCommand.Usage();
        }
    }

    /** Un lugar: el mundo y {x, y, z}. */
    private record Place(World world, int x, int y, int z) {
    }

    /** Los pies del jugador, o --at x,y,z (con --world) desde la consola. */
    private Place place(CommandSender s, Args args) {
        Player p = s instanceof Player pl ? pl : null;
        if (args.values.containsKey("at")) {
            String[] c = args.values.get("at").split(",");
            if (c.length != 3) throw new WorldcraftCommand.Usage();
            World w = args.values.containsKey("world") ? Bukkit.getWorld(args.values.get("world"))
                    : p != null ? p.getWorld() : Bukkit.getWorlds().get(0);
            if (w == null) throw new IllegalArgumentException("There is no world called '" + args.values.get("world") + "'");
            Location base = p != null ? p.getLocation() : null;
            try {
                return new Place(w, WorldcraftCommand.coord(c[0], base == null ? 0 : base.getBlockX()),
                        WorldcraftCommand.coord(c[1], base == null ? 0 : base.getBlockY()),
                        WorldcraftCommand.coord(c[2], base == null ? 0 : base.getBlockZ()));
            } catch (NumberFormatException e) {
                throw new WorldcraftCommand.Usage();
            }
        }
        if (p == null) {
            s.sendMessage(msg().get("needs-place"));
            return null;
        }
        Location l = p.getLocation();
        return new Place(p.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    private Direction direction(CommandSender s, String text) {
        if (text != null) {
            Direction d = Direction.parse(text);
            if (d == null) throw new IllegalArgumentException("'" + text + "' is not a direction"
                    + hint(dev.leo.worldcraft.config.Issues.closest(text, Direction.NAMES)) + ": up, down, north, south, east or west");
            return d;
        }
        if (s instanceof Player p) return Direction.facing(p.getLocation().getYaw(), p.getLocation().getPitch());
        throw new WorldcraftCommand.Usage();
    }

    private static String hint(String c) {
        return c == null ? "" : " (did you mean '" + c + "'?)";
    }

    private Pattern pattern(String text) throws ParseException {
        if (text.isBlank()) throw new WorldcraftCommand.Usage();
        return plugin.config().parser().pattern(text);
    }

    private List<Integer> states(Pattern p) {
        List<Integer> out = new ArrayList<>();
        for (String st : p.placedStates()) out.add(table().intern(st));
        return out;
    }

    /** ¿Pasa del tope del grupo? (lo dice). */
    private boolean overLimit(CommandSender s, double blocks) {
        Limits.Resolved lim = edits().limits(s);
        if (blocks <= lim.maxBlocks()) return false;
        s.sendMessage(msg().get("limit-blocks", "blocks", Edits.fmt((long) Math.ceil(blocks)), "max", Edits.fmt(lim.maxBlocks()),
                "groups", String.join(", ", lim.groups())));
        return true;
    }

    private boolean radiusOk(CommandSender s, int r, int max) {
        if (r >= 0 && r <= max) return true;
        s.sendMessage(msg().get("radius-too-big", "radius", String.valueOf(r), "max", String.valueOf(max)));
        return false;
    }

    /** La selección (cualquier forma, spec 030) dentro del mundo, o null (y lo dice). */
    private Region selection(CommandSender s) {
        return main.selection(s, edits().session(Edits.owner(s)));
    }

    /** --select de stack y move (spec 030): una caja se vuelve a seleccionar; otra forma se mueve con sus puntos. */
    private void selectMoved(UUID owner, World w, Box moved, int dx, int dy, int dz) {
        Session ses = edits().session(owner);
        if (ses.selector().boxLike()) ses.selectBox(w.getName(), moved);
        else ses.selector().shift(dx, dy, dz);
    }

    private World selectionWorld(CommandSender s) {
        return Bukkit.getWorld(edits().session(Edits.owner(s)).world());
    }

    /** Calcula en el hilo de cálculo y sigue en el principal. */
    private <T> void compute(CommandSender s, Supplier<T> work, java.util.function.Consumer<T> then) {
        CompletableFuture.supplyAsync(work, plugin.compute()).whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.getLogger().log(Level.SEVERE, "Calculation failed", err);
                s.sendMessage(msg().get("compute-error"));
                return;
            }
            then.accept(v);
        }));
    }

    private Box clampY(World w, Box b) {
        return b.clampY(w.getMinHeight(), w.getMaxHeight() - 1);
    }

    private int air() {
        return blocks().air();
    }

    // ------------------------------------------------------------------ H1 formas

    private void shape(CommandSender s, String sub, Args args) throws ParseException {
        boolean hollow = sub.startsWith("h");
        String kind = hollow ? sub.substring(1) : sub;
        List<String> pos = new ArrayList<>(args.positional);
        List<int[]> nums = trailingNumbers(pos, kind.equals("sphere") || kind.equals("pyramid") ? 1 : 2);
        if (nums.isEmpty()) throw new WorldcraftCommand.Usage();
        Pattern pattern = pattern(String.join(" ", pos));
        int[] radii = nums.get(0);
        int a = radii[0], b = radii.length > 1 ? radii[1] : a, c = radii.length > 2 ? radii[2] : (radii.length > 1 ? radii[1] : a);
        int h = 1;
        switch (kind) {
            case "sphere" -> {
                b = radii.length > 1 ? radii[1] : a;
                c = radii.length > 2 ? radii[2] : a;
            }
            case "cyl" -> {
                c = radii.length > 1 ? radii[1] : a;
                h = nums.size() > 1 ? nums.get(1)[0] : 1;
            }
            case "cone" -> {
                if (nums.size() < 2) throw new WorldcraftCommand.Usage();
                h = nums.get(1)[0];
            }
            default -> {
            }
        }
        int max = plugin.config().settings().shapesMaxRadius();
        for (int r : new int[]{a, b, c, Math.abs(h) / 2}) if (!radiusOk(s, r, max)) return;
        int t = 1;
        if (args.values.containsKey("thickness")) {
            try {
                t = Integer.parseInt(args.values.get("thickness"));
            } catch (NumberFormatException e) {
                throw new WorldcraftCommand.Usage();
            }
            if (t < 1 || t > 32) throw new IllegalArgumentException("The thickness must be 1 to 32");
        }
        if (overLimit(s, Solid.estimate(kind, a, b, c, h) * (hollow ? 0.5 : 1))) return;
        Place at = place(s, args);
        if (at == null) return;
        Solid solid = switch (kind) {
            case "sphere" -> Solid.sphere(at.x, args.flags.contains("raised") ? at.y + b : at.y, at.z, a, b, c);
            case "cyl" -> Solid.cylinder(at.x, at.y, at.z, a, c, h);
            case "pyramid" -> Solid.pyramid(at.x, at.y, at.z, a);
            default -> Solid.cone(at.x, at.y, at.z, a, h);
        };
        if (hollow) solid = solid.hollow(t, kind.equals("cyl"));
        Box bounds = solid.bounds();
        if (bounds == null || clampY(at.world, bounds) == null) {
            s.sendMessage(msg().get("sel-outside"));
            return;
        }
        Pattern.Compiled pc = pattern.compile(table());
        long seed = ThreadLocalRandom.current().nextLong();
        Solid fin = solid;
        String detail = pattern.text() + " " + String.join(",", java.util.Arrays.stream(radii).mapToObj(String::valueOf).toList())
                + (kind.equals("cyl") || kind.equals("cone") ? " h=" + h : "");
        edits().request(s, new Edits.Op(sub, detail, at.world, bounds, solid.volume(), () -> new SolidSource(fin, pc, seed),
                states(pattern), null), args.flags.contains("preview"));
    }

    // ------------------------------------------------------------------ H2 región

    private void stack(CommandSender s, Args args) {
        int n = intArg(args, 0, 1);
        if (n < 1 || n > 1000) throw new IllegalArgumentException("Stack 1 to 1000 copies");
        Direction d = direction(s, args.positional.size() > 1 ? args.positional.get(1) : null);
        Region region = selection(s);
        if (region == null) return;
        Box box = region.bounds();
        World w = selectionWorld(s);
        if (overLimit(s, (double) region.volume() * n)) return;
        Box all = StackSource.copies(box, d, n);
        if (clampY(w, all) == null || !all.equals(clampY(w, all))) {
            s.sendMessage(msg().get("sel-outside"));
            return;
        }
        boolean air = args.flags.contains("air");
        UUID owner = Edits.owner(s);
        // Spec 030 H4 CA4: de una forma solo se lee lo de adentro; lo demás queda -1 en la foto y no se repite.
        Snapshot snap = new Snapshot(box);
        edits().read(s, w, region, "stack", snap, () -> {
            Edits.Op op = new Edits.Op("stack", n + " " + d.id() + (air ? " with air" : ""), w, all, region.volume() * n,
                    () -> new StackSource(snap, d, n, air, blocks()::isAir), snap.distinct(), null);
            Box last = StackSource.last(box, d, n);
            if (args.flags.contains("select")) op = op.then(() -> selectMoved(owner, w, last, last.minX() - box.minX(),
                    last.minY() - box.minY(), last.minZ() - box.minZ()));
            edits().request(s, op, args.flags.contains("preview"));
        });
    }

    private void move(CommandSender s, Args args) throws ParseException {
        List<String> pos = args.positional;
        int n = intArg(args, 0, 1);
        if (n < 1 || n > 10_000) throw new IllegalArgumentException("Move 1 to 10000 blocks");
        int next = 1;
        String dirText = null;
        if (pos.size() > 1 && Direction.parse(pos.get(1)) != null) {
            dirText = pos.get(1);
            next = 2;
        }
        Direction d = direction(s, dirText);
        String fillText = args.join(next);
        Pattern fill = fillText.isBlank() ? Pattern.of("minecraft:air") : pattern(fillText);
        Region region = selection(s);
        if (region == null) return;
        Box box = region.bounds();
        World w = selectionWorld(s);
        if (overLimit(s, 2.0 * region.volume())) return;
        Box dest = box.shift(d, n);
        if (clampY(w, dest) == null || !dest.equals(clampY(w, dest))) {
            s.sendMessage(msg().get("sel-outside"));
            return;
        }
        Pattern.Compiled fc = fill.compile(table());
        long seed = ThreadLocalRandom.current().nextLong();
        UUID owner = Edits.owner(s);
        // Spec 030 H4 CA4: la foto tiene solo lo de la forma (-1 afuera): se mueve la forma y queda atrás solo lo suyo.
        Snapshot snap = new Snapshot(box);
        edits().read(s, w, region, "move", snap, () -> {
            MoveSource probe = new MoveSource(snap, d.dx * n, d.dy * n, d.dz * n, fc, seed);
            Set<Integer> placed = new LinkedHashSet<>(snap.distinct());
            placed.addAll(states(fill));
            Edits.Op op = new Edits.Op("move", n + " " + d.id() + (fillText.isBlank() ? "" : ", leaving " + fill.text()), w,
                    probe.bounds(), probe.volume(), () -> new MoveSource(snap, d.dx * n, d.dy * n, d.dz * n, fc, seed), placed, null);
            if (args.flags.contains("select")) op = op.then(() -> selectMoved(owner, w, dest, d.dx * n, d.dy * n, d.dz * n));
            edits().request(s, op, args.flags.contains("preview"));
        });
    }

    /** Lo que se salta para buscar la cima: aire, cobertura (plantas) salvo líquidos, y troncos y hojas. */
    private IntPredicate open() {
        var m = plugin.terrain().masksForCommands();
        return st -> blocks().isAir(st) || (m.cover().test(st) && !liquid(st));
    }

    private boolean liquid(int st) {
        String mat = table().material(st);
        return mat.equals("minecraft:water") || mat.equals("minecraft:lava");
    }

    private void overlay(CommandSender s, Args args) throws ParseException {
        Pattern p = pattern(args.join(0));
        Region region = selection(s);
        if (region == null) return;
        Box box = region.bounds();
        World w = selectionWorld(s);
        if (overLimit(s, (double) box.width() * box.depth())) return;
        var m = plugin.terrain().masksForCommands();
        IntPredicate open = open();
        IntPredicate skip = st -> open.test(st) || m.buryable().test(st);
        // Spec 030 H4 CA1: solo las columnas de la forma.
        TopScan scan = new TopScan(box, skip.negate(), region instanceof Box ? (x, z) -> true : (x, z) -> region.span(x, z) != null);
        Pattern.Compiled pc = p.compile(table());
        long seed = ThreadLocalRandom.current().nextLong();
        edits().readWith(s, w, scan, "overlay", scan, () -> {
            Box target = clampY(w, box.expand(Direction.UP, 1));
            Supplier<EditSource> src = () -> new ColumnEdit(target, ColumnEdit.aboveTop(scan, 1), ColumnEdit.overlay(open, pc, seed));
            request(s, w, "overlay", p.text(), src, states(p), args);
        });
    }

    /** Pide una obra calculada (volumen y caja de la fuente); avisa si no hay nada que hacer. */
    private void request(CommandSender s, World w, String kind, String detail, Supplier<EditSource> src,
                         java.util.Collection<Integer> placed, Args args) {
        EditSource probe = src.get();
        if (probe.volume() == 0) {
            s.sendMessage(msg().get("nothing-found", "kind", kind));
            return;
        }
        edits().request(s, new Edits.Op(kind, detail, w, probe.bounds(), probe.volume(), src, placed, null),
                args.flags.contains("preview"));
    }

    private void naturalize(CommandSender s, Args args) {
        Region region = selection(s);
        if (region == null) return;
        Box box = region.bounds();
        World w = selectionWorld(s);
        if (overLimit(s, region.volume())) return;
        int grass = state("minecraft:grass_block"), dirt = state("minecraft:dirt"), stone = state("minecraft:stone");
        Set<String> natural = Set.of("minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:stone");
        IntPredicate nat = st -> natural.contains(table().material(st));
        // Spec 030 H4 CA1: cada columna de la forma, de su y más alta a la más baja.
        edits().request(s, new Edits.Op("naturalize", region.size(), w, box, region.volume(),
                () -> new ColumnEdit(box, region::span, ColumnEdit.naturalize(nat, grass, dirt, stone)),
                List.of(grass, dirt, stone), null), args.flags.contains("preview"));
    }

    private int state(String block) {
        return blocks().id(Bukkit.createBlockData(block));
    }

    private void smooth(CommandSender s, Args args) {
        int passes = intArg(args, 0, 1);
        if (passes < 1 || passes > 20) throw new IllegalArgumentException("Smooth 1 to 20 passes");
        Region region = selection(s);
        if (region == null) return;
        if (overLimit(s, region.volume())) return;
        plugin.terrain().smooth(s, selectionWorld(s), region, passes, args.flags.contains("preview"));
    }

    private void hollow(CommandSender s, Args args) throws ParseException {
        int t = intArg(args, 0, 1);
        if (t < 1 || t > 32) throw new IllegalArgumentException("The thickness must be 1 to 32");
        String fillText = args.join(1);
        Pattern fill = fillText.isBlank() ? Pattern.of("minecraft:air") : pattern(fillText);
        Region region = selection(s);
        if (region == null) return;
        Box box = region.bounds();
        World w = selectionWorld(s);
        if (overLimit(s, box.volume())) return;
        Pattern.Compiled fc = fill.compile(table());
        long seed = ThreadLocalRandom.current().nextLong();
        Snapshot snap = new Snapshot(box);
        IntPredicate isAir = blocks()::isAir;
        // Spec 030 H4 CA1: el interior se calcula con la caja que la abarca y se queda lo que está en la forma.
        edits().read(s, w, box, "hollow", snap, () -> compute(s, () -> region instanceof Box ? Flood.interior(snap, isAir, t)
                : java.util.Arrays.stream(Flood.interior(snap, isAir, t))
                .filter(k -> region.contains(PointsSource.unpackX(k), PointsSource.unpackY(k), PointsSource.unpackZ(k))).toArray(), inside ->
                request(s, w, "hollow", "thickness " + t + (fillText.isBlank() ? "" : ", " + fill.text()),
                        () -> new PointsSource(inside, (x, y, z, c) -> fc.apply(x, y, z, c, seed)), states(fill), args)));
    }

    private void center(CommandSender s, Args args) throws ParseException {
        Pattern p = pattern(args.join(0));
        Region r = selection(s);
        if (r == null) return;
        World w = selectionWorld(s);
        Box c = r.bounds().center(); // spec 030 H4 CA6: el centro de la caja que abarca la forma
        Pattern.Compiled pc = p.compile(table());
        long seed = ThreadLocalRandom.current().nextLong();
        edits().request(s, new Edits.Op("center", p.text(), w, c, c.volume(), () -> new RegionSource(c, Shape.FILL, null, pc, seed),
                states(p), null), args.flags.contains("preview"));
    }

    private void line(CommandSender s, Args args) throws ParseException {
        List<String> pos = new ArrayList<>(args.positional);
        List<int[]> nums = trailingNumbers(pos, 1);
        int r = nums.isEmpty() ? 0 : nums.get(0)[0];
        if (r < 0 || r > 16) throw new IllegalArgumentException("The thickness must be 0 to 16");
        Pattern p = pattern(String.join(" ", pos));
        Session ses = edits().session(Edits.owner(s));
        if (!ses.selector().boxLike()) {
            // Spec 030 H4 CA6: una línea va de la esquina 1 a la 2.
            s.sendMessage(msg().get("sel-not-for-shape", "kind", ses.selector().kind().id(), "command", "line"));
            return;
        }
        int[] a = ses.corner1(), b = ses.corner2();
        if (a == null || b == null) {
            s.sendMessage(msg().get("no-selection"));
            return;
        }
        World w = Bukkit.getWorld(ses.world());
        double len = Math.max(Math.abs(a[0] - b[0]), Math.max(Math.abs(a[1] - b[1]), Math.abs(a[2] - b[2]))) + 1;
        if (overLimit(s, len * Math.pow(2 * r + 1, 3))) return;
        long[] pts = Line.points(a[0], a[1], a[2], b[0], b[1], b[2], r, PointsSource::pack);
        Pattern.Compiled pc = p.compile(table());
        long seed = ThreadLocalRandom.current().nextLong();
        int lo = w.getMinHeight(), hi = w.getMaxHeight() - 1;
        request(s, w, "line", p.text() + (r > 0 ? " thickness " + r : ""),
                () -> new PointsSource(pts, (x, y, z, c) -> y < lo || y > hi ? EditSource.KEEP : pc.apply(x, y, z, c, seed)), states(p), args);
    }

    // ------------------------------------------------------------------ H3 utilidades

    /** La caja de un radio alrededor de un lugar, dentro de la altura del mundo; null si el radio no se acepta. */
    private Box around(CommandSender s, Place at, int r) {
        if (!radiusOk(s, r, plugin.config().settings().utilitiesMaxRadius())) return null;
        Box b = clampY(at.world, new Box(at.x - r, at.y - r, at.z - r, at.x + r, at.y + r, at.z + r));
        if (b == null) s.sendMessage(msg().get("sel-outside"));
        return b;
    }

    /**
     * Donde se busca la cima de cada columna (snow, thaw, green): del radio por encima de los pies hasta el fondo del
     * mundo (F-01: con ±radio, volando o en una ladera la cima quedaba fuera). La lectura deja cada columna en su cima.
     */
    static Box surfaceBox(int x, int y, int z, int r, int worldMin, int worldMax) {
        return new Box(x - r, worldMin, z - r, x + r, Math.max(worldMin, y + r), z + r).clampY(worldMin, worldMax);
    }

    private void liquid(CommandSender s, String sub, Args args) {
        if (args.positional.isEmpty()) throw new WorldcraftCommand.Usage();
        int r = intArg(args, 0, 10);
        Place at = place(s, args);
        if (at == null) return;
        Box box = around(s, at, r);
        if (box == null) return;
        boolean waterlogged = args.flags.contains("waterlogged");
        Liquids.Kind kind = Liquids.Kind.valueOf(sub.toUpperCase(java.util.Locale.ROOT));
        // Spec 029 H6: la búsqueda y el cambio viven en Liquids (probados en LiquidsTest).
        Liquids liquids = new Liquids(table(), air(), state("minecraft:water"), state("minecraft:lava"));
        Snapshot snap = new Snapshot(box);
        edits().read(s, at.world, box, sub, snap, () -> compute(s, () -> liquids.find(snap, kind, at.x, at.y, at.z, r, waterlogged), pts -> {
            if (pts.length == 0) {
                s.sendMessage(msg().get("nothing-found", "kind", sub));
                return;
            }
            request(s, at.world, sub, "radius " + r, () -> new PointsSource(pts, liquids.pick(kind)), List.of(liquids.placed(kind)), args);
        }));
    }

    private void fill(CommandSender s, Args args) throws ParseException {
        List<String> pos = new ArrayList<>(args.positional);
        List<int[]> nums = trailingNumbers(pos, 2);
        if (nums.isEmpty()) throw new WorldcraftCommand.Usage();
        int r = nums.get(0)[0];
        int depth = nums.size() > 1 ? nums.get(1)[0] : r;
        if (depth < 1) throw new IllegalArgumentException("The depth must be at least 1");
        Pattern p = pattern(String.join(" ", pos));
        Place at = place(s, args);
        if (at == null) return;
        Box box = around(s, at, r);
        if (box == null) return;
        IntPredicate isAir = blocks()::isAir;
        Pattern.Compiled pc = p.compile(table());
        long seed = ThreadLocalRandom.current().nextLong();
        double e = (r + 0.5) * (r + 0.5);
        Flood.Bound bound = (x, y, z) -> y <= at.y && y > at.y - depth && (double) (x - at.x) * (x - at.x) + (double) (z - at.z) * (z - at.z) <= e;
        Snapshot snap = new Snapshot(box);
        edits().read(s, at.world, box, "fill", snap, () -> compute(s, () -> Flood.connected(snap, at.x, at.y, at.z, bound, isAir), pts -> {
            if (pts.length == 0) {
                s.sendMessage(msg().get("nothing-found", "kind", "fill"));
                return;
            }
            request(s, at.world, "fill", p.text() + " radius " + r, () -> new PointsSource(pts,
                    (x, y, z, c) -> isAir.test(c) ? pc.apply(x, y, z, c, seed) : EditSource.KEEP), states(p), args);
        }));
    }

    private void near(CommandSender s, String sub, Args args) throws ParseException {
        String maskText;
        Pattern p;
        int r;
        switch (sub) {
            case "removenear" -> {
                List<String> pos = new ArrayList<>(args.positional);
                List<int[]> nums = trailingNumbers(pos, 1);
                r = nums.isEmpty() ? 10 : nums.get(0)[0];
                maskText = String.join(" ", pos);
                if (maskText.isBlank()) throw new WorldcraftCommand.Usage();
                p = Pattern.of("minecraft:air");
            }
            case "replacenear" -> {
                r = intArg(args, 0, 10);
                Args rest = new Args();
                rest.positional.addAll(args.positional.subList(1, args.positional.size()));
                String[] mp = rest.maskAndPattern();
                if (mp == null) throw new WorldcraftCommand.Usage();
                maskText = mp[0];
                p = pattern(mp[1]);
            }
            default -> {
                r = intArg(args, 0, 10);
                maskText = "fire, soul_fire";
                p = Pattern.of("minecraft:air");
            }
        }
        Mask mask = plugin.config().parser().maskNear(maskText);
        Place at = place(s, args);
        if (at == null) return;
        Box box = around(s, at, r);
        if (box == null) return;
        if (overLimit(s, box.volume())) return;
        Mask.Compiled mc = mask.compile(table());
        Pattern.Compiled pc = p.compile(table());
        long seed = ThreadLocalRandom.current().nextLong();
        String detail = (sub.equals("replacenear") ? mask.text() + " -> " + p.text() : mask.text()) + " radius " + r;
        boolean preview = args.flags.contains("preview");
        if (mask.needsNeighbors()) {
            // Spec 029 H2 CA2: lo de alrededor se decide con la foto de antes.
            main.readAround(s, at.world, box, sub, snap -> edits().request(s, new Edits.Op(sub, detail, at.world, box, box.volume(),
                    () -> new RegionSource(box, Shape.FILL, mc, pc, seed, snap::get), states(p), null), preview));
            return;
        }
        edits().request(s, new Edits.Op(sub, detail, at.world, box, box.volume(),
                () -> new RegionSource(box, Shape.FILL, mc, pc, seed), states(p), null), preview);
    }

    private void remove(CommandSender s, String sub, Args args) {
        int size = intArg(args, 0, 1);
        Place at = place(s, args);
        if (at == null) return;
        int max = plugin.config().settings().utilitiesMaxRadius();
        if (!radiusOk(s, size - 1, max)) return;
        boolean above = sub.equals("removeabove");
        int lo = at.world.getMinHeight(), hi = at.world.getMaxHeight() - 1;
        int height = intArg(args, 1, above ? hi - at.y + 1 : at.y - lo);
        if (height < 1) throw new IllegalArgumentException("The height must be at least 1");
        int r = size - 1;
        Box box = above ? new Box(at.x - r, at.y, at.z - r, at.x + r, at.y + height - 1, at.z + r)
                : new Box(at.x - r, at.y - height, at.z - r, at.x + r, at.y - 1, at.z + r);
        box = clampY(at.world, box);
        if (box == null) {
            s.sendMessage(msg().get("sel-outside"));
            return;
        }
        if (overLimit(s, box.volume())) return;
        Box fin = box;
        Pattern.Compiled pc = Pattern.of("minecraft:air").compile(table());
        Mask.Compiled notAir = null;
        edits().request(s, new Edits.Op(sub, "size " + size + ", height " + fin.height(), at.world, fin, fin.volume(),
                () -> new RegionSource(fin, Shape.FILL, notAir, pc, 0), List.of(air()), null), args.flags.contains("preview"));
    }

    /** La caja de las columnas del círculo de radio r para buscar su cima (F-01); null si el radio no se acepta. */
    private Box surface(CommandSender s, Place at, int r) {
        if (!radiusOk(s, r, plugin.config().settings().utilitiesMaxRadius())) return null;
        Box b = surfaceBox(at.x, at.y, at.z, r, at.world.getMinHeight(), at.world.getMaxHeight() - 1);
        if (b == null) s.sendMessage(msg().get("sel-outside"));
        return b;
    }

    private static TopScan.Columns circle(Place at, int r) {
        double e = (r + 0.5) * (r + 0.5);
        return (x, z) -> (double) (x - at.x) * (x - at.x) + (double) (z - at.z) * (z - at.z) <= e;
    }

    /** snow (H3 CA4): lee la cima de cada columna del círculo y pone nieve encima (o congela el agua). */
    private void snow(CommandSender s, Args args) {
        int r = intArg(args, 0, 10);
        Place at = place(s, args);
        if (at == null) return;
        Box box = surface(s, at, r);
        if (box == null) return;
        if (overLimit(s, (double) box.width() * box.depth())) return;
        IntPredicate open = open();
        TopScan scan = new TopScan(box, open.negate(), circle(at, r));
        int water = state("minecraft:water"), ice = state("minecraft:ice"), snow = state("minecraft:snow");
        edits().readWith(s, at.world, scan, "snow", scan, () -> {
            Box target = clampY(at.world, box.expand(Direction.UP, 1));
            request(s, at.world, "snow", "radius " + r, () -> new ColumnEdit(target, ColumnEdit.aboveTop(scan, 0),
                    ColumnEdit.snow(scan, st -> st == water, this::sturdy, blocks()::isAir, ice, snow)), List.of(ice, snow), args);
        });
    }

    /** ¿Aguanta una capa de nieve encima? (la cara de arriba entera; necesita el registro del servidor). */
    private boolean sturdy(int st) {
        return sturdy.computeIfAbsent(st, k -> blocks().data(k).isFaceSturdy(BlockFace.UP, BlockSupport.FULL));
    }

    /** thaw y green (H3 CA4): el primer bloque que no es aire de cada columna del círculo (leído antes, F-01). */
    private void top(CommandSender s, String sub, Args args) {
        int r = intArg(args, 0, 10);
        Place at = place(s, args);
        if (at == null) return;
        Box box = surface(s, at, r);
        if (box == null) return;
        if (overLimit(s, (double) box.width() * box.depth())) return;
        TopScan scan = new TopScan(box, st -> !blocks().isAir(st), circle(at, r));
        StateTable t = table();
        java.util.function.IntUnaryOperator change;
        List<Integer> placed;
        if (sub.equals("thaw")) {
            int air = air(), water = state("minecraft:water");
            change = st -> switch (t.material(st)) {
                case "minecraft:snow" -> air;
                case "minecraft:ice" -> water;
                default -> st;
            };
            placed = List.of(air, water);
        } else {
            int grass = state("minecraft:grass_block");
            change = st -> t.material(st).equals("minecraft:dirt") ? grass : st;
            placed = List.of(grass);
        }
        edits().readWith(s, at.world, scan, sub, scan, () -> request(s, at.world, sub, "radius " + r,
                () -> new ColumnEdit(box, (x, z) -> {
                    int top = scan.top(x, z);
                    return top == TopScan.NONE ? null : new int[]{top, top};
                }, ColumnEdit.firstSolid(blocks()::isAir, change)), placed, args));
    }

}
