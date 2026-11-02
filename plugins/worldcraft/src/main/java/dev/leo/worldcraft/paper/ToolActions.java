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
    }

    /** El bloque que mira (con la cara), hasta tools.range; null si no hay (lo dice en la barra de acción). */
    private RayTraceResult target(Player p) {
        int range = plugin.config().tools().range();
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
        RayTraceResult hit = target(p);
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
                case BUTCHER -> {
                    if (!p.hasPermission("worldcraft.butcher")) {
                        p.sendMessage(msg().get("no-permission"));
                        return;
                    }
                    plugin.command().butcherAt(p, w, x, y, z, r, s.butcherFlags());
                }
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
        RayTraceResult hit = target(p);
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
                case LRBUILD -> {
                    String text = s.text(right ? "right" : "left");
                    Pattern pat = plugin.config().parser().pattern(text);
                    boolean remove = pat.placedStates().stream().allMatch(st -> st.endsWith(":air") || st.endsWith("_air"));
                    BlockFace face = hit.getHitBlockFace() == null ? BlockFace.UP : hit.getHitBlockFace();
                    int tx = remove ? x : x + face.getModX(), ty = remove ? y : y + face.getModY(), tz = remove ? z : z + face.getModZ();
                    if (ty < w.getMinHeight() || ty >= w.getMaxHeight()) return;
                    one(p, w, kind, text, tx, ty, tz, pat.compile(blocks().table()), states(text), s);
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
        edits().request(p, new Edits.Op(kind, detail, w, bounds, volume, use, placed, null), false);
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
