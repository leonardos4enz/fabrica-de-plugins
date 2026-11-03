package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.edit.EditSource;
import dev.leo.worldcraft.edit.Flood;
import dev.leo.worldcraft.edit.MaskedSource;
import dev.leo.worldcraft.edit.PasteSource;
import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.edit.RegionSource;
import dev.leo.worldcraft.edit.Snapshot;
import dev.leo.worldcraft.edit.SolidSource;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Shape;
import dev.leo.worldcraft.region.Solid;
import dev.leo.worldcraft.tool.BrushMath;
import dev.leo.worldcraft.tool.Cycler;
import dev.leo.worldcraft.tool.ToolKind;
import dev.leo.worldcraft.tool.ToolSpec;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Lo que hace cada clic de un pincel o una herramienta (plan 032 §1.2). Todo termina en una obra del motor
 * ({@link Edits#request}): límites del grupo de quien hace clic, máscara global, historial y deshacer. Lo que mira el
 * mundo lee una foto por tandas y calcula en el hilo de cálculo. Hilo principal.
 */
public final class ToolActions {
    /** El superpico de un jugador (H6): modo, radio y si está prendido. */
    public record SuperPick(String mode, int radius, boolean on) {
    }

    private final Worldcraft plugin;
    /** Quienes esperan una lectura o un cálculo: sus clics se ignoran (H1 CA3). */
    private final Set<UUID> busy = new HashSet<>();
    /** Lo que acepta el servidor como estado (cycler): createBlockData tira una excepción cara, se recuerda. */
    private final Map<String, Boolean> valid = new HashMap<>();

    public ToolActions(Worldcraft plugin) {
        this.plugin = plugin;
    }

    private Edits edits() {
        return plugin.edits();
    }

    private PaperBlocks blocks() {
        return edits().blocks();
    }

    private dev.leo.worldcraft.config.Messages msg() {
        return plugin.messages();
    }

    /** ¿Puede hacer clic ahora? (sin lecturas ni obras en marcha) */
    public boolean ready(Player p) {
        return !busy.contains(p.getUniqueId()) && edits().running(p.getUniqueId()) == 0;
    }

    public void forget(UUID id) {
        busy.remove(id);
        nodes.remove(id);
    }

    /**
     * El bloque que mira (con la cara), hasta tools.range; null si no hay (lo dice en la barra de acción). Spec 040: con
     * /wc range, hasta ese alcance; con /wc tracemask, el primer bloque que cumple la máscara.
     */
    private RayTraceResult target(Player p, ToolSpec s) {
        // Spec 041 H3 CA5: forward apunta a reach bloques hacia donde mira, aunque sea aire.
        if (s != null && s.target().equals("forward")) {
            int n = s.text("reach") != null ? s.number("reach") : 10;
            org.bukkit.util.Vector at = p.getEyeLocation().toVector().add(p.getEyeLocation().getDirection().multiply(n));
            Block b = p.getWorld().getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ());
            return offset(p, s, new RayTraceResult(b.getLocation().toVector(), b, BlockFace.UP));
        }
        RayTraceResult hit = rawTarget(p, s);
        if (hit == null || s == null) return hit;
        Block b = hit.getHitBlock();
        BlockFace face = hit.getHitBlockFace() == null ? BlockFace.UP : hit.getHitBlockFace();
        switch (s.target()) {
            case "height" -> {
                int y = Math.max(b.getWorld().getMinHeight(), Math.min(b.getWorld().getMaxHeight() - 1, p.getLocation().getBlockY()));
                b = b.getWorld().getBlockAt(b.getX(), y, b.getZ());
            }
            case "face" -> b = b.getRelative(face);
            default -> {
            }
        }
        return offset(p, s, new RayTraceResult(b.getLocation().toVector(), b, face));
    }

    /** Spec 041 H3 CA5: /wc targetoffset corre el blanco hacia afuera de la cara tocada (negativo, hacia adentro). */
    private static RayTraceResult offset(Player p, ToolSpec s, RayTraceResult hit) {
        int n = s.number("toffset");
        if (n == 0) return hit;
        BlockFace f = hit.getHitBlockFace() == null ? BlockFace.UP : hit.getHitBlockFace();
        Block b = hit.getHitBlock();
        int y = Math.max(b.getWorld().getMinHeight(), Math.min(b.getWorld().getMaxHeight() - 1, b.getY() + f.getModY() * n));
        Block o = b.getWorld().getBlockAt(b.getX() + f.getModX() * n, y, b.getZ() + f.getModZ() * n);
        return new RayTraceResult(o.getLocation().toVector(), o, f);
    }

    private RayTraceResult rawTarget(Player p, ToolSpec s) {
        int max = plugin.config().tools().range();
        int range = s != null && s.text("reach") != null ? Math.min(max, s.number("reach")) : max;
        String trace = s == null ? null : s.trace();
        if (trace != null) {
            Mask.Compiled m;
            try {
                m = plugin.config().parser().mask(trace).compile(blocks().table());
            } catch (ParseException e) {
                p.sendMessage(msg().get("error", "message", e.getMessage()));
                return null;
            }
            org.bukkit.util.BlockIterator it = new org.bukkit.util.BlockIterator(p, range);
            Block prev = null;
            while (it.hasNext()) {
                Block b = it.next();
                if (m.test(blocks().id(b.getBlockData()))) {
                    BlockFace face = prev == null ? BlockFace.UP : b.getFace(prev);
                    return new RayTraceResult(b.getLocation().toVector(), b, face == null ? BlockFace.UP : face);
                }
                prev = b;
            }
            p.sendActionBar(msg().get("tool-no-target", "range", String.valueOf(range)));
            return null;
        }
        RayTraceResult hit = Terrain.look(p, range);
        if (hit == null || hit.getHitBlock() == null) {
            p.sendActionBar(msg().get("tool-no-target", "range", String.valueOf(range)));
            return null;
        }
        return hit;
    }

    // ------------------------------------------------------------------ pinceles (H2–H4)

    /** Un clic derecho con un pincel. */
    public void brush(Player p, ToolSpec s) {
        if (!ready(p)) return;
        RayTraceResult hit = target(p, s);
        if (hit == null) return;
        Block b = hit.getHitBlock();
        World w = b.getWorld();
        int x = b.getX(), y = b.getY(), z = b.getZ(), r = s.radius();
        String kind = "brush " + s.kind().id();
        try {
            switch (s.kind()) {
                case SPHERE, CYL, SET -> shape(p, s, w, x, y, z, kind);
                case CLIPBOARD -> clipboard(p, s, w, x, y, z, kind);
                case SPLATTER, SCATTER -> {
                    Pattern.Compiled pc = pattern(s.pattern());
                    Box box = clamp(w, new Box(x - r, y - r, z - r, x + r, y + r + 1, z + r));
                    if (box == null) return;
                    long seed = ThreadLocalRandom.current().nextLong();
                    boolean overlay = s.flag("overlay");
                    read(p, w, box, kind, snap -> {
                        IntPredicate air = ids(snap, blocks()::isAir), liquid = ids(snap, this::liquid);
                        IntPredicate ground = st -> !air.test(st) && !liquid.test(st);
                        return () -> s.kind() == ToolKind.SPLATTER
                                ? BrushMath.splatter(snap, x, y, z, r, s.number("decay"), ground, air, seed)
                                : BrushMath.scatter(snap, x, y, z, r, s.number("points"), s.number("distance"), overlay, ground, air, seed);
                    }, pts -> points(p, s, w, kind, pts, (px, py, pz, c) -> overlay && !blocks().isAir(c) ? EditSource.KEEP
                            : pc.apply(px, py, pz, c, seed), states(s.pattern())));
                }
                case ROCK -> {
                    Pattern.Compiled pc = pattern(s.pattern());
                    long seed = ThreadLocalRandom.current().nextLong();
                    busy.add(p.getUniqueId());
                    compute(p, () -> BrushMath.rock(x, y, z, r, s.number("sphericity"), s.number("frequency"), s.number("amplitude"), seed),
                            pts -> points(p, s, w, kind, pts, (px, py, pz, c) -> pc.apply(px, py, pz, c, seed), states(s.pattern())));
                }
                case GRAVITY -> {
                    int lo = Math.max(w.getMinHeight(), y - r), hi = s.flag("full") ? w.getMaxHeight() - 1 : Math.min(w.getMaxHeight() - 1, y + r);
                    Box box = new Box(x - r, lo, z - r, x + r, hi, z + r);
                    int air = blocks().air();
                    read(p, w, box, kind, snap -> {
                        // Los bloques con datos (cofres, carteles) no se mueven: el registro se mira aquí, en el hilo principal.
                        IntPredicate isAir = ids(snap, blocks()::isAir), fixed = ids(snap, blocks()::hasTile);
                        return () -> BrushMath.gravity(snap, x, z, r, lo, hi, isAir, fixed, air);
                    }, changes -> {
                        long[] pts = changes.keySet().stream().mapToLong(Long::longValue).toArray();
                        points(p, s, w, kind, pts, (px, py, pz, c) -> changes.get(PointsSource.pack(px, py, pz)), new HashSet<>(changes.values()));
                    });
                }
                case EXTINGUISH -> {
                    Solid solid = Solid.sphere(x, y, z, r, r, r);
                    Mask.Compiled fire = plugin.config().parser().mask("fire, soul_fire").compile(blocks().table());
                    Pattern.Compiled air = Pattern.of("minecraft:air").compile(blocks().table());
                    if (clamp(w, solid.bounds()) == null) return;
                    edits().request(p, new Edits.Op(kind, "radius " + r, w, solid.bounds(), solid.volume(),
                            () -> new MaskedSource(new SolidSource(solid, air, 0), fire, 0), List.of(blocks().air()), null), false);
                }
                case FOREST -> {
                    // Spec 033 H5 CA5: un bosque en el círculo donde mira (la superficie entre y - radio e y + radio).
                    dev.leo.worldcraft.nature.TreeKind tk = dev.leo.worldcraft.nature.TreeKind.parse(s.text("type") == null ? "oak" : s.text("type"));
                    int lo = Math.max(w.getMinHeight(), y - r), hi = Math.min(w.getMaxHeight() - 2, y + r);
                    var region = new dev.leo.worldcraft.region.Cylinder(x, z, r, r, lo, hi);
                    UUID id = p.getUniqueId();
                    busy.add(id);
                    new Nature(plugin).forest(p, w, region, tk, s.number("density"), kind, s.describe(), false, () -> busy.remove(id));
                }
                case DEFORM -> {
                    // Spec 034 H7: la deformación de /wc deform en la esfera donde mira (de −1 a 1 en la esfera).
                    var sphere = new dev.leo.worldcraft.region.Ellipsoid(x, y, z, r, r, r, true);
                    var region = sphere.clampY(w.getMinHeight(), w.getMaxHeight() - 1);
                    if (region == null) return;
                    var f = plugin.config().parser().formula(s.formula(), List.of("x", "y", "z"));
                    var mode = s.flag("raw") ? dev.leo.worldcraft.formula.Space.Mode.RAW
                            : s.flag("offset") ? dev.leo.worldcraft.formula.Space.Mode.OFFSET : dev.leo.worldcraft.formula.Space.Mode.NORMAL;
                    var sp = dev.leo.worldcraft.formula.Space.of(mode, sphere.bounds(), new int[]{x, y, z});
                    UUID id = p.getUniqueId();
                    busy.add(id);
                    plugin.formulas().deform(p, w, region, f, sp, kind, prep -> {
                        busy.remove(id);
                        request(p, w, kind, s.describe(), prep.source(), prep.bounds(), prep.volume(), prep.placed(), s);
                    }, () -> busy.remove(id));
                }
                // Spec 040 H2: los pinceles de WorldEdit que faltaban.
                case SNOW, SNOWSMOOTH -> {
                    Box box = clamp(w, new Box(x - r, y - r, z - r, x + r, y + r + 1, z + r));
                    if (box == null) return;
                    boolean stack = s.flag("stack");
                    int passes = s.number("iterations");
                    double e = (r + 0.5) * (r + 0.5);
                    read(p, w, box, kind, snap -> {
                        dev.leo.worldcraft.edit.SnowMath.Rules rules = snowRules(snap, null);
                        return () -> s.kind() == ToolKind.SNOW ? dev.leo.worldcraft.edit.SnowMath.fall(snap, x, y, z, r, stack, rules)
                                : dev.leo.worldcraft.edit.SnowMath.smooth(snap, (cx, cz) -> (double) (cx - x) * (cx - x) + (double) (cz - z) * (cz - z) <= e, passes, rules);
                    }, changes -> changes(p, s, w, kind, changes));
                }
                case BIOME -> {
                    var sphere = new dev.leo.worldcraft.region.Ellipsoid(x, y, z, r, r, r, true).clampY(w.getMinHeight(), w.getMaxHeight() - 1);
                    if (sphere == null) return;
                    plugin.command().biomeAt(p, w, sphere, s.text("biome"), s.describe());
                }
                case MORPH, DILATE -> {
                    Box box = clamp(w, new Box(x - r - 1, y - r - 1, z - r - 1, x + r + 1, y + r + 1, z + r + 1));
                    if (box == null) return;
                    int air = blocks().air();
                    read(p, w, box, kind, snap -> {
                        // Lo que tiene datos (cofres, carteles) no se toca: cuenta como sólido pero no se borra ni se copia.
                        IntPredicate isAir = ids(snap, blocks()::isAir), tile = ids(snap, blocks()::hasTile);
                        return () -> {
                            var out = BrushMath.morph(snap, x, y, z, r, s.number("erode_faces"), s.number("erode"), s.number("dilate_faces"),
                                    s.number("dilate"), isAir, air);
                            out.entrySet().removeIf(en -> tile.test(en.getValue()) || tile.test(snap.get(PointsSource.unpackX(en.getKey()),
                                    PointsSource.unpackY(en.getKey()), PointsSource.unpackZ(en.getKey()))));
                            return out;
                        };
                    }, changes -> changes(p, s, w, kind, changes));
                }
                case HEIGHTMAP -> heightmap(p, s, w, x, y, z, kind);
                case APPLY -> apply(p, s, w, x, y, z, kind);
                case FEATURE -> {
                    Nms nms = Nms.get();
                    if (!nms.available()) {
                        p.sendMessage(msg().get("nms-unavailable", "reason", nms.problem()));
                        return;
                    }
                    int lo = Math.max(w.getMinHeight(), y - r), hi = Math.min(w.getMaxHeight() - 2, y + r);
                    var region = new dev.leo.worldcraft.region.Cylinder(x, z, r, r, lo, hi);
                    UUID id = p.getUniqueId();
                    busy.add(id);
                    new Nature(plugin).featureField(p, w, region, s.text("feature"), s.number("density"), kind, () -> busy.remove(id));
                }
                case BUTCHER -> {
                    if (!p.hasPermission("worldcraft.butcher")) {
                        p.sendMessage(msg().get("no-permission"));
                        return;
                    }
                    plugin.command().butcherAt(p, w, x, y, z, r, s.butcherFlags());
                }
                case LINE, SPLINE, SURFACESPLINE, SWEEP, CATENARY, CIRCLE, PULL, RECURSIVE, SHATTER, STENCIL, IMAGE, HEIGHT, POPULATE,
                     COPYPASTE -> fawe(p, s, w, x, y, z, kind);
                default -> {
                }
            }
        } catch (ParseException | IllegalArgumentException e) {
            p.sendMessage(msg().get("error", "message", e.getMessage()));
        }
    }

    private void shape(Player p, ToolSpec s, World w, int x, int y, int z, String kind) throws ParseException {
        int r = s.radius();
        Pattern.Compiled pc = pattern(s.pattern());
        long seed = ThreadLocalRandom.current().nextLong();
        String shape = s.kind() == ToolKind.SET ? s.text("shape") : s.kind() == ToolKind.CYL ? "cyl" : "sphere";
        boolean hollow = s.flag("hollow");
        Supplier<EditSource> src;
        Box bounds;
        long volume;
        if ("cube".equals(shape)) {
            Box box = new Box(x - r, y - r, z - r, x + r, y + r, z + r);
            src = () -> new RegionSource(box, Shape.FILL, null, pc, seed);
            bounds = box;
            volume = box.volume();
        } else {
            Solid solid = shape.equals("cyl")
                    ? Solid.cylinder(x, s.kind() == ToolKind.SET ? y - r : y, z, r, r, s.kind() == ToolKind.SET ? 2 * r + 1 : s.number("height"))
                    : Solid.sphere(x, y, z, r, r, r);
            if (hollow) solid = solid.hollow(1, shape.equals("cyl"));
            Solid fin = solid;
            src = () -> new SolidSource(fin, pc, seed);
            bounds = solid.bounds();
            volume = solid.volume();
        }
        if (bounds == null || clamp(w, bounds) == null) return;
        request(p, w, kind, s.describe(), src, bounds, volume, states(s.pattern()), s);
    }

    /** H2 CA4: el portapapeles actual, centrado en el bloque que mira (o con --origin, donde estaba quien copió). */
    private void clipboard(Player p, ToolSpec s, World w, int x, int y, int z, String kind) {
        Clipboard clip = edits().session(p.getUniqueId()).clipboard();
        if (clip == null) {
            p.sendMessage(msg().get("no-clipboard"));
            return;
        }
        int ox = x, oy = y, oz = z;
        if (!s.flag("origin")) {
            Box at0 = clip.target(0, 0, 0);
            ox -= Math.floorDiv(at0.minX() + at0.maxX(), 2);
            oy -= Math.floorDiv(at0.minY() + at0.maxY(), 2);
            oz -= Math.floorDiv(at0.minZ() + at0.maxZ(), 2);
        }
        Box target = clip.target(ox, oy, oz);
        if (clamp(w, target) == null) return;
        boolean air = s.flag("air");
        PaperBlocks blocks = blocks();
        int fx = ox, fy = oy, fz = oz;
        request(p, w, kind, clip.size() + (air ? " with air" : ""), () -> new PasteSource(clip, fx, fy, fz, air, blocks::isAir, blocks),
                target, clip.volume(), clip.distinct(), s);
    }

    // ------------------------------------------------------------------ herramientas (H5)

    /** Un clic con una herramienta; right = clic derecho. */
    public void tool(Player p, org.bukkit.inventory.ItemStack item, ToolSpec s, boolean right) {
        if (s.kind() == ToolKind.NAVWAND) {
            if (!p.hasPermission("worldcraft.navigate")) {
                p.sendMessage(msg().get("no-permission"));
                return;
            }
            plugin.command().navigate(p, right ? "thru" : "jumpto");
            return;
        }
        if (!ready(p)) return;
        RayTraceResult hit = target(p, s);
        if (hit == null) return;
        Block b = hit.getHitBlock();
        World w = b.getWorld();
        int x = b.getX(), y = b.getY(), z = b.getZ();
        String kind = "tool " + s.kind().id();
        try {
            switch (s.kind()) {
                case REPL -> {
                    if (right) {
                        Pattern.Compiled pc = pattern(s.pattern());
                        one(p, w, kind, s.pattern(), x, y, z, pc, states(s.pattern()), s);
                    } else {
                        String st = blocks().table().state(blocks().id(b.getBlockData()));
                        plugin.tools().write(item, s.with("pattern", st));
                        p.sendActionBar(msg().get("repl-picked", "block", st.replace("minecraft:", "")));
                    }
                }
                case FLOODFILL -> {
                    int n = s.number("range");
                    Pattern.Compiled pc = pattern(s.pattern());
                    Box box = clamp(w, new Box(x - n, y - n, z - n, x + n, y + n, z + n));
                    if (box == null) return;
                    long seed = ThreadLocalRandom.current().nextLong();
                    double e = (n + 0.5) * (n + 0.5);
                    Flood.Bound bound = (px, py, pz) -> (double) (px - x) * (px - x) + (double) (py - y) * (py - y) + (double) (pz - z) * (pz - z) <= e;
                    read(p, w, box, kind, snap -> {
                                IntPredicate same = sameMaterial(snap, x, y, z, null);
                                return () -> Flood.connected(snap, x, y, z, bound, same);
                            },
                            pts -> points(p, s, w, kind, pts, (px, py, pz, c) -> pc.apply(px, py, pz, c, seed), states(s.pattern())));
                }
                case INFO -> p.sendMessage(msg().get("tool-info", "pos", x + " " + y + " " + z,
                        "block", b.getBlockData().getAsString(), "biome", b.getBiome().getKey().toString(),
                        "light", b.getLightFromBlocks() + " block, " + b.getLightFromSky() + " sky"));
                case CYCLER -> cycler(p, item, s, b, right);
                case DELTREE -> {
                    int n = plugin.config().tools().deltreeRange();
                    Box box = clamp(w, new Box(x - n, y - n, z - n, x + n, y + n, z + n));
                    if (box == null) return;
                    Mask.Compiled tree = plugin.config().parser().mask(plugin.config().tools().treeBlocks()).compile(blocks().table());
                    var cover = plugin.terrain().masksForCommands().cover();
                    read(p, w, box, kind, snap -> {
                        IntPredicate isTree = ids(snap, tree::test), free = ids(snap, st -> blocks().isAir(st) || cover.test(st));
                        return () -> BrushMath.deltree(snap, x, y, z, isTree, free);
                    }, t -> {
                        switch (t.status()) {
                            case NOT_TREE -> p.sendActionBar(msg().get("deltree-not-tree"));
                            case GROUNDED -> p.sendMessage(msg().get("deltree-grounded"));
                            case TOO_BIG -> p.sendMessage(msg().get("deltree-too-big", "range", String.valueOf(n)));
                            case OK -> points(p, s, w, kind, t.points(), (px, py, pz, c) -> blocks().air(), List.of(blocks().air()));
                        }
                    });
                }
                case FARWAND -> plugin.command().setCorner(p, p.getUniqueId(), right ? 2 : 1, w, x, y, z);
                case STACKER -> {
                    // Spec 040 H3 CA5: el bloque se repite hacia la cara tocada (clic derecho) o hacia el otro lado (izquierdo).
                    BlockFace face = hit.getHitBlockFace() == null ? BlockFace.UP : hit.getHitBlockFace();
                    if (!right) face = face.getOppositeFace();
                    int n = Math.min(s.number("range"), plugin.config().tools().maxRange());
                    Mask.Compiled into = plugin.config().parser().mask(s.mask() == null ? "air" : s.mask()).compile(blocks().table());
                    int lo = w.getMinHeight(), hi = w.getMaxHeight() - 1;
                    long[] pts = dev.leo.worldcraft.tool.Stacker.points(x, y, z, face.getModX(), face.getModY(), face.getModZ(), n, into::test,
                            (px, py, pz) -> py < lo || py > hi ? -1 : blocks().id(w.getBlockData(px, py, pz)));
                    int state = blocks().id(b.getBlockData());
                    points(p, s.with("mask", null), w, kind, pts, (px, py, pz, c) -> state, List.of(state), s.describe());
                }
                case TREE -> {
                    // Spec 033 H5 CA4: un árbol sobre el bloque que mira (si encima hay lugar).
                    if (!right) return;
                    if (y + 1 >= w.getMaxHeight()) return;
                    org.bukkit.block.Block above = w.getBlockAt(x, y + 1, z);
                    if (!above.isEmpty() && !above.isReplaceable()) {
                        p.sendActionBar(msg().get("tree-no-room"));
                        return;
                    }
                    dev.leo.worldcraft.nature.TreeKind tk = dev.leo.worldcraft.nature.TreeKind.parse(s.text("type") == null ? "oak" : s.text("type"));
                    UUID id = p.getUniqueId();
                    busy.add(id);
                    new Nature(plugin).tree(p, w, x, y + 1, z, tk, kind, () -> busy.remove(id));
                }
                case LRBUILD -> {
                    String text = s.text(right ? "right" : "left");
                    Pattern pat = plugin.config().parser().pattern(text);
                    boolean remove = pat.placedStates().stream().allMatch(st -> st.endsWith(":air") || st.endsWith("_air"));
                    BlockFace face = hit.getHitBlockFace() == null ? BlockFace.UP : hit.getHitBlockFace();
                    int tx = remove ? x : x + face.getModX(), ty = remove ? y : y + face.getModY(), tz = remove ? z : z + face.getModZ();
                    if (ty < w.getMinHeight() || ty >= w.getMaxHeight()) return;
                    one(p, w, kind, text, tx, ty, tz, pat.compile(blocks().table()), states(text), s);
                }
                // Spec 041 H4 CA9, CA10
                case INSPECT -> inspect(p, w, x, y, z);
                case FEATUREPLACER -> {
                    if (!right) return;
                    Nms nms = Nms.get();
                    if (!nms.available()) {
                        p.sendMessage(msg().get("nms-unavailable", "reason", nms.problem()));
                        return;
                    }
                    UUID id = p.getUniqueId();
                    busy.add(id);
                    new Nature(plugin).feature(p, w, x, y + 1, z, s.text("feature"), false, () -> busy.remove(id));
                }
                case STRUCTUREPLACER -> {
                    if (!right) return;
                    Structures.Loaded t = plugin.structures().template(p, s.text("structure"), 0);
                    if (t == null) return;
                    Clipboard clip = t.clip();
                    Box target = clip.target(x, y + 1, z);
                    if (clamp(w, target) == null) return;
                    PaperBlocks blocks = blocks();
                    request(p, w, kind, s.text("structure"), () -> new PasteSource(clip, x, y + 1, z, false, blocks::isAir, blocks),
                            target, clip.volume(), clip.distinct(), s);
                }
                default -> {
                }
            }
        } catch (ParseException | IllegalArgumentException e) {
            p.sendMessage(msg().get("error", "message", e.getMessage()));
        }
    }

    /** H5 CA4: clic derecho, el valor siguiente (agachado, el anterior); clic izquierdo, la propiedad siguiente. */
    private void cycler(Player p, org.bukkit.inventory.ItemStack item, ToolSpec s, Block b, boolean right) {
        String state = b.getBlockData().getAsString();
        List<String> props = Cycler.properties(state);
        if (props.isEmpty()) {
            p.sendActionBar(msg().get("cycler-nothing", "block", state.replace("minecraft:", "")));
            return;
        }
        String prop = props.contains(s.text("property")) ? s.text("property") : props.get(0);
        if (!right) {
            String next = Cycler.nextProperty(state, prop);
            plugin.tools().write(item, s.with("property", next));
            p.sendActionBar(msg().get("cycler-property", "property", next, "value", dev.leo.worldcraft.block.StateTable.parseProperties(state).get(next)));
            return;
        }
        String changed = Cycler.cycle(state, prop, p.isSneaking(), this::validState);
        if (changed == null) {
            p.sendActionBar(msg().get("cycler-nothing", "block", state.replace("minecraft:", "")));
            return;
        }
        int id = blocks().id(Bukkit.createBlockData(changed));
        Pattern.Compiled pc = Pattern.of(blocks().table().state(id)).compile(blocks().table());
        one(p, b.getWorld(), "tool cycler", prop + "=" + dev.leo.worldcraft.block.StateTable.parseProperties(changed).get(prop),
                b.getX(), b.getY(), b.getZ(), pc, List.of(id), s);
    }

    private boolean validState(String state) {
        return valid.computeIfAbsent(state, k -> {
            try {
                Bukkit.createBlockData(k);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        });
    }

    // ------------------------------------------------------------------ superpico (H6)

    /** Un clic izquierdo con un pico en un bloque, con el superpico prendido. */
    public void superpickaxe(Player p, Block b, SuperPick sp) {
        if (!ready(p)) return;
        World w = b.getWorld();
        int x = b.getX(), y = b.getY(), z = b.getZ();
        Mask.Compiled never;
        try {
            never = plugin.config().parser().mask(plugin.config().tools().neverBreak()).compile(blocks().table());
        } catch (ParseException e) {
            p.sendMessage(msg().get("error", "message", e.getMessage()));
            return;
        }
        int state = blocks().id(b.getBlockData());
        if (blocks().isAir(state)) return;
        if (never.test(state)) {
            p.sendActionBar(msg().get("superpickaxe-never", "block", blocks().table().material(state).replace("minecraft:", "")));
            return;
        }
        String kind = "superpickaxe";
        Pattern.Compiled air = Pattern.of("minecraft:air").compile(blocks().table());
        if (sp.mode().equals("single")) {
            one(p, w, kind, "single", x, y, z, air, List.of(blocks().air()), null);
            return;
        }
        int r = sp.radius();
        Box box = clamp(w, new Box(x - r, y - r, z - r, x + r, y + r, z + r));
        if (box == null) return;
        read(p, w, box, kind, snap -> {
            IntPredicate same = sameMaterial(snap, x, y, z, never);
            double e = (r + 0.5) * (r + 0.5);
            Flood.Bound ball = (px, py, pz) -> (double) (px - x) * (px - x) + (double) (py - y) * (py - y) + (double) (pz - z) * (pz - z) <= e;
            return () -> sp.mode().equals("area") ? BrushMath.cube(snap, x, y, z, r, same) : Flood.connected(snap, x, y, z, ball, same);
        }, pts -> points(p, null, w, kind, pts, (px, py, pz, c) -> blocks().air(), List.of(blocks().air()), sp.mode() + " " + r));
    }

    // ------------------------------------------------------------------ spec 040

    private final Map<Integer, Boolean> sturdy = new HashMap<>();

    /** ¿Aguanta una capa de nieve encima? (necesita el registro del servidor). */
    private boolean sturdy(int st) {
        return sturdy.computeIfAbsent(st, k -> blocks().data(k).isFaceSturdy(BlockFace.UP, org.bukkit.block.BlockSupport.FULL));
    }

    /**
     * Las reglas de la nieve con los estados de la foto, armadas en el hilo principal (lección 032); ground (o null) es lo
     * que cuenta como suelo además de aguantar la nieve (--mask de //snowsmooth).
     */
    public dev.leo.worldcraft.edit.SnowMath.Rules snowRules(Snapshot snap, Mask.Compiled ground) {
        PaperBlocks b = blocks();
        int[] snow = new int[9];
        for (int i = 1; i <= 8; i++) snow[i] = b.id(Bukkit.createBlockData("minecraft:snow[layers=" + i + "]"));
        Map<Integer, Integer> layers = new HashMap<>();
        for (int i = 1; i <= 8; i++) layers.put(snow[i], i);
        IntPredicate air = ids(snap, b::isAir), holds = ids(snap, st -> !b.isAir(st) && !layers.containsKey(st) && sturdy(st)
                && (ground == null || ground.test(st)));
        int water = b.id(Bukkit.createBlockData("minecraft:water")), ice = b.id(org.bukkit.Material.ICE.createBlockData());
        return new dev.leo.worldcraft.edit.SnowMath.Rules(air, st -> layers.getOrDefault(st, 0), holds, snow, b.air(), st -> st == water, ice);
    }

    /** La obra de unos cambios ya calculados (posición → estado). */
    private void changes(Player p, ToolSpec s, World w, String kind, Map<Long, Integer> changes) {
        long[] pts = changes.keySet().stream().mapToLong(Long::longValue).toArray();
        points(p, s, w, kind, pts, (px, py, pz, c) -> changes.get(PointsSource.pack(px, py, pz)), new HashSet<>(changes.values()));
    }

    /** H2 CA5: la imagen gris (leída en el hilo de cálculo) estirada sobre el círculo donde mira. */
    private void heightmap(Player p, ToolSpec s, World w, int x, int y, int z, String kind) {
        int r = s.radius(), h = s.number("height");
        java.nio.file.Path file;
        try {
            file = plugin.images().file(s.text("image"));
        } catch (RuntimeException e) {
            p.sendMessage(msg().get("error", "message", "There is no image '" + s.text("image") + "' in the images folder"));
            return;
        }
        int lo = Math.max(w.getMinHeight(), y - h - r), hi = Math.min(w.getMaxHeight() - 1, y + h + r);
        Box box = new Box(x - r, lo, z - r, x + r, hi, z + r);
        boolean erase = s.flag("erase"), flatten = s.flag("flatten");
        int air = blocks().air(), side = 2 * r + 1;
        read(p, w, box, kind, snap -> {
            IntPredicate isAir = ids(snap, st -> blocks().isAir(st) || isCover(st));
            return () -> BrushMath.heightmap(snap, x, y, z, r, gray(file, side), h, erase, flatten, isAir, air);
        }, changes -> changes(p, s, w, kind, changes));
    }

    /** El pasto corto y las flores no son la cima (como en el pincel de terreno). */
    private boolean isCover(int st) {
        return plugin.terrain().masksForCommands().cover().test(st);
    }

    /** La imagen en gris (0..1) de side × side, leída del disco en el hilo de cálculo. */
    private static double[][] gray(java.nio.file.Path file, int side) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file.toFile());
            if (img == null) throw new IllegalArgumentException("not an image: " + file.getFileName());
            double[][] out = new double[side][side];
            for (int j = 0; j < side; j++) {
                for (int i = 0; i < side; i++) {
                    int px = Math.min(img.getWidth() - 1, i * img.getWidth() / side), py = Math.min(img.getHeight() - 1, j * img.getHeight() / side);
                    int argb = img.getRGB(px, py);
                    double a = ((argb >>> 24) & 255) / 255.0;
                    double l = (0.299 * ((argb >> 16) & 255) + 0.587 * ((argb >> 8) & 255) + 0.114 * (argb & 255)) / 255.0;
                    out[j][i] = l * a;
                }
            }
            return out;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** H2 CA6: usar el ítem en cada bloque de la forma (o en density % al azar), con las reglas de item-uses.yml. */
    private void apply(Player p, ToolSpec s, World w, int x, int y, int z, String kind) {
        var rules = plugin.config().itemUses().rules(s.text("item"));
        if (rules == null) {
            p.sendMessage(msg().get("error", "message", "Worldcraft does not know what '" + s.text("item") + "' does (item-uses.yml)"));
            return;
        }
        int r = s.radius(), density = s.number("density");
        String shape = s.text("shape") == null ? "sphere" : s.text("shape");
        Box box = clamp(w, new Box(x - r, y - r, z - r, x + r, y + r + 1, z + r));
        if (box == null) return;
        Solid solid = shape.equals("cube") ? null : shape.equals("cyl") ? Solid.cylinder(x, y - r, z, r, r, 2 * r + 1) : Solid.sphere(x, y, z, r, r, r);
        long seed = ThreadLocalRandom.current().nextLong();
        List<Mask.Compiled> masks = new ArrayList<>();
        List<Pattern.Compiled> pats = new ArrayList<>();
        for (var rule : rules) {
            masks.add(rule.mask().compile(blocks().table()));
            pats.add(rule.pattern().compile(blocks().table()));
        }
        read(p, w, box, kind, snap -> {
            IntPredicate isAir = ids(snap, blocks()::isAir);
            // Qué regla cumple cada estado de la foto (la tabla se mira aquí, en el hilo principal).
            Map<Integer, Integer> which = new HashMap<>();
            for (int st : snap.distinct()) {
                for (int i = 0; i < rules.size(); i++) {
                    if (masks.get(i).test(st)) {
                        which.put(st, i);
                        break;
                    }
                }
            }
            return () -> {
                Map<Long, Integer> out = new java.util.LinkedHashMap<>();
                for (int py = y - r; py <= y + r; py++) {
                    for (int pz = z - r; pz <= z + r; pz++) {
                        for (int px = x - r; px <= x + r; px++) {
                            if (solid != null && !solid.contains(px, py, pz)) continue;
                            int st = snap.get(px, py, pz);
                            Integer i = st < 0 ? null : which.get(st);
                            if (i == null) continue;
                            if (density < 100 && BrushMath.hash01(px, py, pz, seed) * 100 >= density) continue;
                            if (rules.get(i).above()) {
                                int up = snap.get(px, py + 1, pz);
                                if (up < 0 || !isAir.test(up)) continue;
                                int t = pats.get(i).apply(px, py + 1, pz, up, seed);
                                if (t >= 0 && t != up) out.put(PointsSource.pack(px, py + 1, pz), t);
                            } else {
                                int t = pats.get(i).apply(px, py, pz, st, seed);
                                if (t >= 0 && t != st) out.put(PointsSource.pack(px, py, pz), t);
                            }
                        }
                    }
                }
                return out;
            };
        }, changes -> changes(p, s, w, kind, changes));
    }

    // ------------------------------------------------------------------ spec 041: los pinceles de FAWE

    /** Los puntos marcados con el pincel de líneas de la mano (por jugador; se olvidan al cambiar de pincel). */
    private record Nodes(String kind, List<int[]> list) {
    }

    private final Map<UUID, Nodes> nodes = new HashMap<>();

    private List<int[]> nodes(Player p, ToolSpec s) {
        Nodes n = nodes.get(p.getUniqueId());
        if (n == null || !n.kind().equals(s.kind().id())) {
            n = new Nodes(s.kind().id(), new ArrayList<>());
            nodes.put(p.getUniqueId(), n);
        }
        return n.list();
    }

    /** ¿Pasa del tope de bloques de su grupo? (antes de calcular algo enorme) */
    private boolean overLimit(Player p, double blocks) {
        var lim = edits().limits(p);
        if (blocks <= lim.maxBlocks()) return false;
        p.sendMessage(msg().get("limit-blocks", "blocks", Edits.fmt((long) Math.ceil(blocks)), "max", Edits.fmt(lim.maxBlocks()),
                "groups", String.join(", ", lim.groups())));
        return true;
    }

    private static double dist(int[] a, int[] b) {
        return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
    }

    private void fawe(Player p, ToolSpec s, World w, int x, int y, int z, String kind) throws ParseException {
        int r = s.radius();
        int[] here = {x, y, z};
        long seed = ThreadLocalRandom.current().nextLong();
        switch (s.kind()) {
            case LINE, CATENARY -> {
                // H1 CA1, CA4: el primer clic marca, el segundo traza.
                Pattern.Compiled pc = pattern(s.pattern());
                List<int[]> n = nodes(p, s);
                if (n.isEmpty()) {
                    n.add(here);
                    p.sendActionBar(msg().get("line-point", "pos", x + " " + y + " " + z));
                    return;
                }
                int[] a = n.get(0);
                double len = dist(a, here) * (s.kind() == ToolKind.CATENARY ? s.decimal("length") : 1);
                if (overLimit(p, (len * 1.6 + 1) * Math.pow(2 * r + 1, 3))) return;
                n.clear();
                if (s.flag("select")) n.add(here);
                long[] pts;
                if (s.kind() == ToolKind.LINE) {
                    pts = dev.leo.worldcraft.tool.LineMath.line(a, here, r, s.flag("shell"), s.flag("flat"));
                } else {
                    double[] down = {0, -1, 0};
                    if (s.flag("facing")) {
                        org.bukkit.util.Vector d = p.getEyeLocation().getDirection().normalize();
                        down = new double[]{d.getX(), d.getY(), d.getZ()};
                    }
                    pts = dev.leo.worldcraft.tool.LineMath.catenary(a, here, s.decimal("length"), down, r, s.flag("shell"));
                }
                int lo = w.getMinHeight(), hi = w.getMaxHeight() - 1;
                points(p, s, w, kind, pts, (px, py, pz, c) -> py < lo || py > hi ? EditSource.KEEP : pc.apply(px, py, pz, c, seed), states(s.pattern()));
            }
            case SPLINE, SURFACESPLINE, SWEEP -> {
                // H1 CA2, CA3: cada clic agrega un punto; el mismo bloque dos veces cierra.
                List<int[]> n = nodes(p, s);
                boolean close = !n.isEmpty() && java.util.Arrays.equals(n.get(n.size() - 1), here);
                if (!close) {
                    if (n.size() >= plugin.config().tools().maxSplinePoints()) {
                        p.sendActionBar(msg().get("spline-full", "max", String.valueOf(plugin.config().tools().maxSplinePoints())));
                        return;
                    }
                    n.add(here);
                    p.sendActionBar(msg().get("spline-point", "count", String.valueOf(n.size())));
                    return;
                }
                if (n.size() < 2) {
                    p.sendActionBar(msg().get("spline-few"));
                    return;
                }
                List<int[]> path = new ArrayList<>(n);
                n.clear();
                double len = 0;
                for (int i = 1; i < path.size(); i++) len += dist(path.get(i - 1), path.get(i));
                if (s.kind() == ToolKind.SWEEP) {
                    sweep(p, s, w, kind, path);
                    return;
                }
                if (overLimit(p, (len * 1.6 + 1) * Math.pow(2 * r + 1, 3))) return;
                Pattern.Compiled pc = pattern(s.pattern());
                int lo = w.getMinHeight(), hi = w.getMaxHeight() - 1;
                long[] pts;
                if (s.kind() == ToolKind.SPLINE) {
                    pts = dev.leo.worldcraft.region.Curve.points(path, r, false, PointsSource::pack);
                } else {
                    // La curva en el plano y cada punto en la cima de su columna (hasta 2 × largo columnas: barato).
                    long[] flat = dev.leo.worldcraft.region.Curve.points(path, 0, false, PointsSource::pack);
                    java.util.Set<Long> out = new java.util.LinkedHashSet<>();
                    java.util.Set<Long> seen = new HashSet<>();
                    double er = (r + 0.5) * (r + 0.5);
                    for (long q : flat) {
                        int qx = PointsSource.unpackX(q), qz = PointsSource.unpackZ(q);
                        if (!seen.add(PointsSource.pack(qx, 0, qz))) continue;
                        int top = w.getHighestBlockYAt(qx, qz, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
                        for (int dx = -r; dx <= r; dx++)
                            for (int dy = -r; dy <= r; dy++)
                                for (int dz = -r; dz <= r; dz++)
                                    if (r == 0 || dx * dx + dy * dy + dz * dz <= er) out.add(PointsSource.pack(qx + dx, top + dy, qz + dz));
                    }
                    pts = out.stream().mapToLong(Long::longValue).toArray();
                }
                points(p, s, w, kind, pts, (px, py, pz, c) -> py < lo || py > hi ? EditSource.KEEP : pc.apply(px, py, pz, c, seed), states(s.pattern()));
            }
            case CIRCLE -> {
                Pattern.Compiled pc = pattern(s.pattern());
                org.bukkit.util.Vector d = p.getEyeLocation().getDirection();
                long[] pts = dev.leo.worldcraft.tool.LineMath.circle(x, y, z, d.getX(), d.getY(), d.getZ(), r, s.flag("fill"));
                int lo = w.getMinHeight(), hi = w.getMaxHeight() - 1;
                points(p, s, w, kind, pts, (px, py, pz, c) -> py < lo || py > hi ? EditSource.KEEP : pc.apply(px, py, pz, c, seed), states(s.pattern()));
            }
            case PULL -> {
                // El eje que va del blanco al jugador.
                org.bukkit.util.Vector v = p.getEyeLocation().toVector().subtract(new org.bukkit.util.Vector(x + 0.5, y + 0.5, z + 0.5));
                int[] dir = new int[3];
                double ax = Math.abs(v.getX()), ay = Math.abs(v.getY()), az = Math.abs(v.getZ());
                if (ax >= ay && ax >= az) dir[0] = v.getX() >= 0 ? 1 : -1;
                else if (ay >= az) dir[1] = v.getY() >= 0 ? 1 : -1;
                else dir[2] = v.getZ() >= 0 ? 1 : -1;
                Box box = clamp(w, new Box(x - r - 1, y - r - 1, z - r - 1, x + r + 1, y + r + 1, z + r + 1));
                if (box == null) return;
                int passes = s.number("iterations");
                read(p, w, box, kind, snap -> {
                    IntPredicate isAir = ids(snap, st -> blocks().isAir(st) || isCover(st)), tile = ids(snap, blocks()::hasTile);
                    return () -> {
                        var out = dev.leo.worldcraft.tool.BrushMath.pull(snap, x, y, z, r, dir, passes, isAir);
                        out.values().removeIf(tile::test);
                        return out;
                    };
                }, changes -> changes(p, s, w, kind, changes));
            }
            case RECURSIVE -> {
                Pattern.Compiled pc = pattern(s.pattern());
                Box box = clamp(w, new Box(x - r, y - r, z - r, x + r, y + r, z + r));
                if (box == null) return;
                Mask.Compiled m = s.mask() == null ? null : plugin.config().parser().mask(s.mask()).compile(blocks().table());
                double e = (r + 0.5) * (r + 0.5);
                Flood.Bound ball = (px, py, pz) -> (double) (px - x) * (px - x) + (double) (py - y) * (py - y) + (double) (pz - z) * (pz - z) <= e;
                read(p, w, box, kind, snap -> {
                    IntPredicate along = m == null ? sameMaterial(snap, x, y, z, null) : ids(snap, m::test);
                    return () -> Flood.connected(snap, x, y, z, ball, along);
                }, pts -> points(p, s, w, kind, pts, (px, py, pz, c) -> pc.apply(px, py, pz, c, seed), states(s.pattern())));
            }
            case SHATTER -> {
                Pattern.Compiled pc = pattern(s.pattern());
                Box box = clamp(w, new Box(x - r - 1, y - r - 1, z - r - 1, x + r + 1, y + r + 1, z + r + 1));
                if (box == null) return;
                int count = s.number("count");
                read(p, w, box, kind, snap -> {
                    IntPredicate isAir = ids(snap, st -> blocks().isAir(st) || liquid(st));
                    return () -> dev.leo.worldcraft.tool.BrushMath.shatter(snap, x, y, z, r, count, isAir, seed);
                }, pts -> points(p, s, w, kind, pts, (px, py, pz, c) -> pc.apply(px, py, pz, c, seed), states(s.pattern())));
            }
            case STENCIL, HEIGHT -> {
                java.nio.file.Path file = imageFile(p, s);
                if (file == null && s.text("image") != null) return;
                int side = 2 * r + 1;
                int quarter = s.flag("random-rotate") ? ThreadLocalRandom.current().nextInt(4) : 0;
                int h = s.kind() == ToolKind.HEIGHT ? Math.abs(s.number("scale")) * Math.max(1, r) : r;
                int lo = Math.max(w.getMinHeight(), y - h - r), hi = Math.min(w.getMaxHeight() - 1, y + h + r);
                Box box = new Box(x - r, lo, z - r, x + r, hi, z + r);
                int air = blocks().air();
                if (s.kind() == ToolKind.STENCIL) {
                    Pattern.Compiled pc = pattern(s.pattern());
                    boolean white = s.flag("white");
                    read(p, w, box, kind, snap -> {
                        IntPredicate isAir = ids(snap, st -> blocks().isAir(st) || isCover(st));
                        return () -> dev.leo.worldcraft.tool.BrushMath.stencil(snap, x, z, r,
                                dev.leo.worldcraft.tool.BrushMath.rotate(file == null ? dev.leo.worldcraft.tool.BrushMath.cone(side) : gray(file, side), quarter), white, isAir);
                    }, pts -> points(p, s, w, kind, pts, (px, py, pz, c) -> pc.apply(px, py, pz, c, seed), states(s.pattern())));
                } else {
                    boolean erase = s.number("scale") < 0;
                    read(p, w, box, kind, snap -> {
                        IntPredicate isAir = ids(snap, st -> blocks().isAir(st) || isCover(st));
                        return () -> dev.leo.worldcraft.tool.BrushMath.heightmap(snap, x, y, z, r,
                                dev.leo.worldcraft.tool.BrushMath.rotate(file == null ? dev.leo.worldcraft.tool.BrushMath.cone(side) : gray(file, side), quarter),
                                h, erase, false, isAir, air);
                    }, changes -> changes(p, s, w, kind, changes));
                }
            }
            case IMAGE -> image(p, s, w, x, y, z, kind);
            case POPULATE -> populate(p, s, w, x, y, z, kind);
            case COPYPASTE -> {
                // H2 CA6, clic derecho: el portapapeles con la base sobre el bloque que mira.
                Clipboard clip = edits().session(p.getUniqueId()).clipboard();
                if (clip == null) {
                    p.sendMessage(msg().get("no-clipboard"));
                    return;
                }
                int turn = s.flag("random-rotate") ? ThreadLocalRandom.current().nextInt(4) * 90
                        : s.flag("auto-rotate") ? Math.floorMod(Math.round(p.getLocation().getYaw() / 90f), 4) * 90 : 0;
                Clipboard use = turn == 0 ? clip : clip.withTransform(clip.transform().then(dev.leo.worldcraft.edit.Transform.IDENTITY.rotate(turn)));
                Box target = use.target(x, y + 1, z);
                if (clamp(w, target) == null) return;
                PaperBlocks blocks = blocks();
                request(p, w, kind, use.size() + (turn == 0 ? "" : ", turned " + turn), () -> new PasteSource(use, x, y + 1, z, false, blocks::isAir, blocks),
                        target, use.volume(), use.distinct(), s);
            }
            default -> {
            }
        }
    }

    /** La imagen del pincel en images/, o null si no lleva (un cono); si no está, lo dice y da null. */
    private java.nio.file.Path imageFile(Player p, ToolSpec s) {
        String name = s.text("image");
        if (name == null) return null;
        try {
            return plugin.images().file(name);
        } catch (RuntimeException e) {
            p.sendMessage(msg().get("error", "message", "There is no image '" + name + "' in the images folder"));
            return null;
        }
    }

    /** H2 CA6, clic izquierdo de copypaste: lo conectado al bloque que mira (lo que no es aire) al portapapeles. */
    public void copy(Player p, ToolSpec s) {
        if (!ready(p)) return;
        RayTraceResult hit = target(p, s);
        if (hit == null) return;
        Block b = hit.getHitBlock();
        World w = b.getWorld();
        int x = b.getX(), y = b.getY(), z = b.getZ(), r = s.radius();
        Box box = clamp(w, new Box(x - r, y - r, z - r, x + r, y + r, z + r));
        if (box == null) return;
        double e = (r + 0.5) * (r + 0.5);
        Flood.Bound ball = (px, py, pz) -> (double) (px - x) * (px - x) + (double) (py - y) * (py - y) + (double) (pz - z) * (pz - z) <= e;
        read(p, w, box, "brush copypaste", snap -> {
            IntPredicate solid = ids(snap, st -> !blocks().isAir(st));
            return () -> {
                long[] pts = Flood.connected(snap, x, y, z, ball, solid);
                if (pts.length == 0) return null;
                var probe = new PointsSource(pts, (px, py, pz, c) -> c);
                Box cb = probe.bounds();
                // La base: el bloque que miró queda en el origen; se pega con la base sobre el bloque que se mire.
                Clipboard clip = new Clipboard(cb, x, y, z);
                int[] q = new int[3];
                while (probe.next(q)) clip.put(cb, q[0], q[1], q[2], snap.get(q[0], q[1], q[2]), snap.tile(q[0], q[1], q[2]));
                return clip;
            };
        }, clip -> {
            if (clip == null) {
                p.sendActionBar(msg().get("tool-nothing", "kind", "brush copypaste"));
                return;
            }
            edits().session(p.getUniqueId()).clipboard(clip, w.getName());
            p.sendActionBar(msg().get("copypaste-copied", "blocks", Edits.fmt(clip.distinct().isEmpty() ? 0 : countSet(clip)), "size", clip.size()));
        });
    }

    private static long countSet(Clipboard c) {
        long n = 0;
        for (int st : c.rawStates()) if (st >= 0) n++;
        return n;
    }

    /** H1 CA3: el portapapeles a lo largo de la curva, girado según hacia dónde va. */
    private void sweep(Player p, ToolSpec s, World w, String kind, List<int[]> path) {
        Clipboard clip = edits().session(p.getUniqueId()).clipboard();
        if (clip == null) {
            p.sendMessage(msg().get("no-clipboard"));
            return;
        }
        List<dev.leo.worldcraft.tool.LineMath.Placement> at = dev.leo.worldcraft.tool.LineMath.sweep(path, Math.max(1, clip.width()), s.number("copies"));
        if (overLimit(p, (double) clip.volume() * at.size())) return;
        PaperBlocks blocks = blocks();
        List<java.util.function.Supplier<EditSource>> parts = new ArrayList<>();
        for (var pl : at) {
            int turn = pl.dx() == 1 ? 0 : pl.dz() == 1 ? 90 : pl.dx() == -1 ? 180 : 270;
            Clipboard use = turn == 0 ? clip : clip.withTransform(clip.transform().then(dev.leo.worldcraft.edit.Transform.IDENTITY.rotate(turn)));
            Box at0 = use.target(0, 0, 0);
            int ox = pl.x() - Math.floorDiv(at0.minX() + at0.maxX(), 2), oy = pl.y() - at0.minY(), oz = pl.z() - Math.floorDiv(at0.minZ() + at0.maxZ(), 2);
            parts.add(() -> new PasteSource(use, ox, oy, oz, false, blocks::isAir, blocks));
        }
        multi(p, s, w, kind, at.size() + " copies of " + clip.size(), parts, clip.distinct());
    }

    /** Una obra con varias fuentes (un solo deshacer). */
    private void multi(Player p, ToolSpec s, World w, String kind, String detail, List<java.util.function.Supplier<EditSource>> parts,
                       Collection<Integer> placed) {
        if (parts.isEmpty()) {
            p.sendActionBar(msg().get("tool-nothing", "kind", kind));
            return;
        }
        Supplier<EditSource> src = () -> {
            List<EditSource> list = new ArrayList<>();
            for (var sp : parts) list.add(sp.get());
            return new dev.leo.worldcraft.edit.MultiSource(list);
        };
        EditSource probe = src.get();
        if (clamp(w, probe.bounds()) == null) return;
        request(p, w, kind, detail, src, probe.bounds(), probe.volume(), placed, s);
    }

    /** H2 CA3: los colores de la imagen sobre las cimas del círculo. */
    private void image(Player p, ToolSpec s, World w, int x, int y, int z, String kind) {
        java.nio.file.Path file = imageFile(p, s);
        if (file == null) return;
        int r = s.radius(), side = 2 * r + 1;
        // La tabla con la textura del jugador (/wc gtexture), y el estado de cada bloque de la tabla, en el hilo principal.
        dev.leo.worldcraft.color.BlockColors table = plugin.config().parser().colors();
        Map<String, Integer> stateOf = new HashMap<>();
        for (String id : table.ids()) {
            String st = plugin.config().parser().colorState(id);
            if (st != null) stateOf.put(id, blocks().table().intern(st));
        }
        boolean alpha = s.flag("alpha"), fade = s.flag("fade");
        long seed = ThreadLocalRandom.current().nextLong();
        Box box = clamp(w, new Box(x - r, y - r, z - r, x + r, y + r, z + r));
        if (box == null) return;
        read(p, w, box, kind, snap -> {
            IntPredicate isAir = ids(snap, st -> blocks().isAir(st) || isCover(st));
            return () -> {
                int[][] px = argb(file, side);
                Map<Long, Integer> out = new java.util.LinkedHashMap<>();
                double e = (r + 0.5) * (r + 0.5);
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (dx * dx + dz * dz > e) continue;
                        int c = px[dz + r][dx + r];
                        if (alpha && (c >>> 24) < 128) continue;
                        if (fade && dev.leo.worldcraft.tool.BrushMath.hash01(x + dx, 0, z + dz, seed) > 1 - Math.sqrt(dx * dx + dz * dz) / (r + 1.0)) continue;
                        int top = dev.leo.worldcraft.tool.BrushMath.surfaceY(snap, x + dx, z + dz, isAir);
                        if (top == Integer.MIN_VALUE) continue;
                        Integer st = stateOf.get(table.nearestId(c | 0xFF000000));
                        if (st != null) out.put(PointsSource.pack(x + dx, top, z + dz), st);
                    }
                }
                return out;
            };
        }, changes -> changes(p, s, w, kind, changes));
    }

    /** La imagen en ARGB de side × side, leída del disco en el hilo de cálculo. */
    private static int[][] argb(java.nio.file.Path file, int side) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file.toFile());
            if (img == null) throw new IllegalArgumentException("not an image: " + file.getFileName());
            int[][] out = new int[side][side];
            for (int j = 0; j < side; j++)
                for (int i = 0; i < side; i++)
                    out[j][i] = img.getRGB(Math.min(img.getWidth() - 1, i * img.getWidth() / side), Math.min(img.getHeight() - 1, j * img.getHeight() / side));
            return out;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** H2 CA5: esquemas (uno, o al azar de una carpeta) en lugares al azar del círculo sobre lo que cumple la máscara. */
    private void populate(Player p, ToolSpec s, World w, int x, int y, int z, String kind) throws ParseException {
        String name = s.text("schem");
        Mask.Compiled on = plugin.config().parser().mask(s.text("on")).compile(blocks().table());
        List<String> names = new ArrayList<>();
        String prefix = name.endsWith("/") ? name : name + "/";
        for (String n : plugin.schematics().names(false)) if (n.startsWith(prefix)) names.add(n);
        if (names.isEmpty()) names.add(name);
        if (names.size() > 20) names = names.subList(0, 20);
        UUID id = p.getUniqueId();
        busy.add(id);
        List<java.util.concurrent.CompletableFuture<Clipboard>> loads = new ArrayList<>();
        for (String n : names) loads.add(plugin.schematics().loadClipboard(n));
        java.util.concurrent.CompletableFuture.allOf(loads.toArray(new java.util.concurrent.CompletableFuture[0])).whenComplete((v, err) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    busy.remove(id);
                    List<Clipboard> clips = new ArrayList<>();
                    for (var f : loads) if (!f.isCompletedExceptionally()) clips.add(f.join());
                    if (clips.isEmpty()) {
                        String why = err == null ? "" : err.getCause() == null ? err.getMessage() : err.getCause().getMessage();
                        p.sendMessage(msg().get("error", "message", why == null || why.isBlank() ? "No schematic '" + name + "'" : why));
                        return;
                    }
                    placeSchematics(p, s, w, x, y, z, kind, clips, on);
                }));
    }

    private void placeSchematics(Player p, ToolSpec s, World w, int x, int y, int z, String kind, List<Clipboard> clips, Mask.Compiled on) {
        int r = s.radius(), density = s.number("density");
        Box box = clamp(w, new Box(x - r, y - r, z - r, x + r, y + r + 1, z + r));
        if (box == null) return;
        long seed = ThreadLocalRandom.current().nextLong();
        boolean rotate = s.flag("rotate");
        read(p, w, box, kind, snap -> {
            IntPredicate isAir = ids(snap, st -> blocks().isAir(st) || isCover(st)), ground = ids(snap, on::test);
            return () -> {
                // Las cimas que cumplen la máscara, en un orden al azar.
                List<long[]> spots = new ArrayList<>();
                double e = (r + 0.5) * (r + 0.5);
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (dx * dx + dz * dz > e) continue;
                        int top = dev.leo.worldcraft.tool.BrushMath.surfaceY(snap, x + dx, z + dz, isAir);
                        if (top == Integer.MIN_VALUE || !ground.test(snap.get(x + dx, top, z + dz))) continue;
                        spots.add(new long[]{x + dx, top, z + dz, (long) (dev.leo.worldcraft.tool.BrushMath.hash01(x + dx, top, z + dz, seed) * Long.MAX_VALUE)});
                    }
                }
                spots.sort(java.util.Comparator.comparingLong(a -> a[3]));
                return spots;
            };
        }, spots -> {
            double foot = 0;
            for (Clipboard c : clips) foot += Math.max(1, c.width()) * Math.max(1, c.depth());
            foot /= clips.size();
            int want = (int) Math.max(1, Math.round(Math.PI * (r + 0.5) * (r + 0.5) * density / 100.0 / foot));
            double gap = Math.sqrt(foot) / 2 + 1;
            PaperBlocks blocks = blocks();
            List<java.util.function.Supplier<EditSource>> parts = new ArrayList<>();
            List<long[]> chosen = new ArrayList<>();
            java.util.Set<Integer> placed = new HashSet<>();
            java.util.Random rnd = new java.util.Random(seed);
            for (long[] sp : spots) {
                if (chosen.size() >= want) break;
                boolean far = true;
                for (long[] c : chosen) if (Math.hypot(c[0] - sp[0], c[2] - sp[2]) < gap) far = false;
                if (!far) continue;
                chosen.add(sp);
                Clipboard clip = clips.get(rnd.nextInt(clips.size()));
                int turn = rotate ? rnd.nextInt(4) * 90 : 0;
                Clipboard use = turn == 0 ? clip : clip.withTransform(clip.transform().then(dev.leo.worldcraft.edit.Transform.IDENTITY.rotate(turn)));
                Box at0 = use.target(0, 0, 0);
                int ox = (int) sp[0] - Math.floorDiv(at0.minX() + at0.maxX(), 2), oy = (int) sp[1] + 1 - at0.minY(), oz = (int) sp[2] - Math.floorDiv(at0.minZ() + at0.maxZ(), 2);
                parts.add(() -> new PasteSource(use, ox, oy, oz, false, blocks::isAir, blocks));
                placed.addAll(use.distinct());
            }
            multi(p, s, w, kind, chosen.size() + " schematics", parts, placed);
        });
    }

    /** H4 CA9: quién cambió este bloque con Worldcraft (del historial en disco de todos), los 10 más nuevos. */
    private void inspect(Player p, World w, int x, int y, int z) {
        if (!p.hasPermission(dev.leo.worldcraft.command.WorldcraftCommand.MODERATE)) {
            p.sendMessage(msg().get("no-permission"));
            return;
        }
        var store = edits().history();
        List<dev.leo.worldcraft.history.HistoryStore.Entry> cand = new ArrayList<>();
        for (var e : store.all()) {
            if (e.world().equals(w.getName()) && e.bounds() != null && e.bounds().contains(x, y, z)) cand.add(e);
        }
        cand.sort(java.util.Comparator.comparingLong(dev.leo.worldcraft.history.HistoryStore.Entry::time).reversed());
        if (cand.size() > 200) cand = cand.subList(0, 200);
        UUID id = p.getUniqueId();
        busy.add(id);
        List<dev.leo.worldcraft.history.HistoryStore.Entry> look = cand;
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            List<String[]> out = new ArrayList<>();
            for (var e : look) {
                if (out.size() >= 10) break;
                try {
                    var d = dev.leo.worldcraft.history.Journal.read(store.journal(e.owner(), e.id()));
                    for (int i = d.size - 1; i >= 0; i--) {
                        if (d.x[i] == x && d.y[i] == y && d.z[i] == z) {
                            out.add(new String[]{e.ownerName(), d.dict.get(d.before[i]), d.dict.get(d.after[i]), String.valueOf(e.time()), e.kind(),
                                    e.state().name()});
                            break;
                        }
                    }
                } catch (java.io.IOException ignored) {
                    // un diario que falta (borrado por la limpieza) no cuenta
                }
            }
            return out;
        }, plugin.io()).whenComplete((rows, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            busy.remove(id);
            if (err != null || rows.isEmpty()) {
                p.sendMessage(msg().get("inspect-none", "pos", x + " " + y + " " + z));
                return;
            }
            p.sendMessage(msg().get("inspect-header", "pos", x + " " + y + " " + z));
            long now = System.currentTimeMillis();
            for (String[] row : rows) {
                p.sendMessage(msg().get("inspect-line", "player", row[0], "before", row[1].replace("minecraft:", ""), "after", row[2].replace("minecraft:", ""),
                        "ago", ago(now - Long.parseLong(row[3])), "kind", row[4], "state", row[5].equals("UNDONE") ? " (undone)" : ""));
            }
        }));
    }

    private static String ago(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s < 60) return s + "s";
        if (s < 3600) return s / 60 + "m";
        if (s < 86400) return s / 3600 + "h";
        return s / 86400 + "d";
    }

    // ------------------------------------------------------------------ utilidades

    private Pattern.Compiled pattern(String text) throws ParseException {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("This brush has no blocks: set it again with /wc brush");
        return plugin.config().parser().pattern(text).compile(blocks().table());
    }

    private List<Integer> states(String text) {
        List<Integer> out = new ArrayList<>();
        try {
            for (String st : plugin.config().parser().pattern(text).placedStates()) out.add(blocks().table().intern(st));
        } catch (ParseException e) {
            // ya se dijo al leer el patrón
        }
        return out;
    }

    private boolean liquid(int st) {
        String m = blocks().table().material(st);
        return m.equals("minecraft:water") || m.equals("minecraft:lava");
    }

    /**
     * Lo que cumple test entre los estados de la foto, como un conjunto (el registro y la tabla se miran aquí, en el hilo
     * principal; el cálculo en el otro hilo solo pregunta al conjunto).
     */
    private static IntPredicate ids(Snapshot snap, IntPredicate test) {
        Set<Integer> yes = new HashSet<>();
        for (int s : snap.distinct()) if (test.test(s)) yes.add(s);
        return yes::contains;
    }

    /** Los estados de la foto con el mismo material que el de (x, y, z), sin los de never (o null). */
    private IntPredicate sameMaterial(Snapshot snap, int x, int y, int z, Mask.Compiled never) {
        int s0 = snap.get(x, y, z);
        if (s0 < 0) return s -> false;
        String mat = blocks().table().material(s0);
        return ids(snap, s -> blocks().table().material(s).equals(mat) && (never == null || !never.test(s)));
    }

    private Box clamp(World w, Box b) {
        Box c = b.clampY(w.getMinHeight(), w.getMaxHeight() - 1);
        return c;
    }

    /** Una obra de un bloque. */
    private void one(Player p, World w, String kind, String detail, int x, int y, int z, Pattern.Compiled pc,
                     Collection<Integer> placed, ToolSpec s) {
        Box box = new Box(x, y, z, x, y, z);
        long seed = ThreadLocalRandom.current().nextLong();
        request(p, w, kind, detail, () -> new RegionSource(box, Shape.FILL, null, pc, seed), box, 1, placed, s);
    }

    private void points(Player p, ToolSpec s, World w, String kind, long[] pts, PointsSource.Pick pick, Collection<Integer> placed) {
        points(p, s, w, kind, pts, pick, placed, s.describe());
    }

    private void points(Player p, ToolSpec s, World w, String kind, long[] pts, PointsSource.Pick pick, Collection<Integer> placed, String detail) {
        if (pts.length == 0) {
            p.sendActionBar(msg().get("tool-nothing", "kind", kind));
            return;
        }
        request(p, w, kind, detail, () -> new PointsSource(pts, pick), placed, s);
    }

    private void request(Player p, World w, String kind, String detail, Supplier<EditSource> src, Collection<Integer> placed, ToolSpec s) {
        EditSource probe = src.get();
        request(p, w, kind, detail, src, probe.bounds(), probe.volume(), placed, s);
    }

    /** La obra, con la máscara del pincel (mask=) si tiene (H1 CA5). */
    private void request(Player p, World w, String kind, String detail, Supplier<EditSource> src, Box bounds, long volume,
                         Collection<Integer> placed, ToolSpec s) {
        Supplier<EditSource> use = src;
        if (s != null && s.mask() != null) {
            Mask.Compiled mc;
            try {
                mc = plugin.config().parser().mask(s.mask()).compile(blocks().table());
            } catch (ParseException e) {
                p.sendMessage(msg().get("error", "message", e.getMessage()));
                return;
            }
            long seed = ThreadLocalRandom.current().nextLong();
            use = () -> new MaskedSource(src.get(), mc, seed);
        }
        Edits.Op op = new Edits.Op(kind, detail, w, bounds, volume, use, placed, null);
        // Spec 041 H3 CA4: la máscara de origen y la transformación del pincel (/wc smask, /wc transform).
        if (s != null) {
            try {
                String sm = s.decodedText("smask"), tr = s.decodedText("transform");
                if (sm != null) op = edits().sourceMasked(op, plugin.config().parser().mask(sm).compile(blocks().table()), "smask " + sm);
                if (tr != null) op = edits().transformed(op, dev.leo.worldcraft.edit.Transforms.parse(tr));
            } catch (ParseException e) {
                p.sendMessage(msg().get("error", "message", e.getMessage()));
                return;
            }
        }
        edits().request(p, op, false);
    }

    /**
     * Lee la caja por tandas y sigue: prepare corre en el hilo principal con la foto (lo que pregunta al registro) y da el
     * cálculo, que corre en el hilo de cálculo; then, otra vez en el principal. Mientras, los clics se ignoran.
     */
    private <T> void read(Player p, World w, Box box, String label, java.util.function.Function<Snapshot, Supplier<T>> prepare,
                          java.util.function.Consumer<T> then) {
        UUID id = p.getUniqueId();
        busy.add(id);
        Snapshot snap = new Snapshot(box);
        edits().readOrCancel(p, w, box, label, snap, () -> {
            Supplier<T> work;
            try {
                work = prepare.apply(snap);
            } catch (RuntimeException e) {
                busy.remove(id);
                throw e;
            }
            compute(p, work, then);
        }, () -> busy.remove(id));
    }

    private <T> void compute(Player p, Supplier<T> work, java.util.function.Consumer<T> then) {
        UUID id = p.getUniqueId();
        CompletableFuture.supplyAsync(work, plugin.compute()).whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            busy.remove(id);
            if (err != null) {
                plugin.getLogger().log(Level.SEVERE, "Calculation failed", err);
                p.sendMessage(msg().get("compute-error"));
                return;
            }
            then.accept(v);
        }));
    }
}
