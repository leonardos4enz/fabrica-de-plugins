package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.config.Limits;
import dev.leo.worldcraft.edit.EditSource;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.terrain.Brush;
import dev.leo.worldcraft.terrain.BrushSettings;
import dev.leo.worldcraft.terrain.ColumnScan;
import dev.leo.worldcraft.terrain.HeightField;
import dev.leo.worldcraft.terrain.HeightSource;
import dev.leo.worldcraft.terrain.IslandShape;
import dev.leo.worldcraft.terrain.IslandSource;
import dev.leo.worldcraft.terrain.Masks;
import dev.leo.worldcraft.terrain.MountainShape;
import dev.leo.worldcraft.terrain.Options;
import dev.leo.worldcraft.terrain.Style;
import dev.leo.worldcraft.terrain.TerrainSettings;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * El terreno en el servidor (spec 026, plan §1.1): dónde va (la mira, los pies o at=), leer el suelo por tandas, calcular
 * en el hilo de terreno y pedir la obra al motor de la 025 con su vista previa; reroll y las pasadas del pincel.
 */
public final class Terrain {
    private final Worldcraft plugin;
    private final ExecutorService compute;

    public Terrain(Worldcraft plugin, ExecutorService compute) {
        this.plugin = plugin;
        this.compute = compute;
    }

    private TerrainSettings cfg() {
        return plugin.config().terrain();
    }

    private Edits edits() {
        return plugin.edits();
    }

    private StateTable table() {
        return edits().blocks().table();
    }

    /** Spec 028: las mismas máscaras para overlay y snow. */
    public Masks masksForCommands() {
        return masks();
    }

    Masks masks() {
        PaperBlocks b = edits().blocks();
        return new Masks(cfg().ground().compile(b.table()), cfg().cover().compile(b.table()), cfg().buryable().compile(b.table()), b::isAir);
    }

    /** Un lugar en un mundo. */
    public record Place(World world, int x, int y, int z) {
    }

    /**
     * «x,y,z» con ~ relativo a base (o a 0 sin base); null y el motivo en el mensaje si está mal.
     */
    public static int[] parseAt(String text, int[] base) {
        String[] p = text.split(",");
        if (p.length != 3) throw new IllegalArgumentException("'at=" + text + "' needs x,y,z, like at=100,64,-20 or at=~,~,~10");
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            String v = p[i].trim();
            try {
                if (v.startsWith("~")) out[i] = (base == null ? 0 : base[i]) + (v.length() == 1 ? 0 : Integer.parseInt(v.substring(1)));
                else out[i] = Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'at=" + text + "' needs whole numbers or ~, like at=100,64,-20 or at=~,~,~10");
            }
        }
        return out;
    }

    /** El mundo pedido (world=) o el de quien manda; null y lo dice si no existe. */
    private World world(CommandSender s, Options.Resolved o) {
        if (o.world() != null) {
            World w = Bukkit.getWorld(o.world());
            if (w == null) s.sendMessage(plugin.messages().get("unknown-world", "world", o.world()));
            return w;
        }
        return s instanceof Player p ? p.getWorld() : Bukkit.getWorlds().get(0);
    }

    private static int[] feet(CommandSender s) {
        if (!(s instanceof Player p)) return null;
        Location l = p.getLocation();
        return new int[]{l.getBlockX(), l.getBlockY(), l.getBlockZ()};
    }

    /** Lo que mira el jugador (sin plantas ni agua), hasta range bloques. */
    static RayTraceResult look(Player p, int range) {
        return p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), range,
                FluidCollisionMode.NEVER, true);
    }

    /** Rechazo antes de leer el suelo (H5 CA3): la estimación ya pasa del doble del tope. */
    private boolean tooBig(CommandSender s, World w, long estimate) {
        Limits.Resolved lim = edits().limits(s);
        var msg = plugin.messages();
        if (!lim.world(w.getName())) {
            s.sendMessage(msg.get("limit-world", "world", w.getName()));
            return true;
        }
        if (estimate > 2L * lim.maxBlocks()) {
            s.sendMessage(msg.get("terrain-too-big", "blocks", Edits.fmt(estimate), "max", Edits.fmt(lim.maxBlocks()),
                    "groups", String.join(", ", lim.groups())));
            return true;
        }
        return false;
    }

    private boolean busy(CommandSender s, Session ses) {
        if (!ses.terrainBusy) return false;
        s.sendMessage(plugin.messages().get("terrain-busy"));
        return true;
    }

    // ------------------------------------------------------------------ montaña

    public void mountain(CommandSender s, Options.Resolved o, boolean now) {
        Session ses = edits().session(Edits.owner(s));
        if (busy(s, ses)) return;
        World w = world(s, o);
        if (w == null) return;
        int r = o.integer("radius"), h = o.integer("height");
        int cx, cy, cz;
        if (o.at() != null) {
            int[] a = parseAt(o.at(), feet(s));
            cx = a[0];
            cy = a[1];
            cz = a[2];
        } else if (s instanceof Player p) {
            RayTraceResult hit = look(p, cfg().reach());
            if (hit != null && hit.getHitBlock() != null && hit.getHitBlock().getWorld().equals(w)) {
                cx = hit.getHitBlock().getX();
                cy = hit.getHitBlock().getY();
                cz = hit.getHitBlock().getZ();
            } else {
                int[] f = feet(s);
                cx = f[0];
                cy = f[1] - 1;
                cz = f[2];
            }
        } else {
            s.sendMessage(plugin.messages().get("terrain-needs-at", "kind", "mountain"));
            return;
        }
        long estimate = (long) (Math.PI * r * r * h / 3 * 0.8);
        if (tooBig(s, w, estimate)) return;
        int top = Math.min(w.getMaxHeight() - 1, cy + h + 2), bottom = Math.max(w.getMinHeight(), cy - 48);
        if (top < bottom) top = bottom;
        Box rect = new Box(cx - r, 0, cz - r, cx + r, 0, cz + r);
        Masks m = masks();
        ColumnScan scan = new ColumnScan(rect, top, bottom, m.ground(), m.cover(), m.buryable(), m.air());
        ses.terrainBusy = true;
        s.sendMessage(plugin.messages().get("terrain-reading", "kind", "mountain"));
        int fx = cx, fy = cy, fz = cz;
        edits().scan(s, w, scan, () -> {
            HeightField f = scan.field();
            ses.terrain = seed -> computeMountain(s, ses, w, f, fx, fy, fz, o, seed, now);
            ses.terrain.accept(o.seed());
        }, () -> ses.terrainBusy = false);
    }

    private void computeMountain(CommandSender s, Session ses, World w, HeightField f, int cx, int cy, int cz,
                                 Options.Resolved o, long seed, boolean now) {
        MountainShape shape = new MountainShape(o.integer("radius"), o.integer("height"), o.integer("peaks"),
                o.number("roughness"), o.number("ridges"), o.number("sharpness"), o.number("crater"), seed);
        int maxY = w.getMaxHeight() - 1;
        run(s, ses, () -> shape.plan(f, cx, cz, cy, maxY), plan -> {
            Masks m = masks();
            Style style = o.style();
            int height = o.integer("height");
            int air = edits().blocks().air();
            Supplier<EditSource> full = () -> new HeightSource(f, plan.newTop(), HeightSource.Mode.GENERATE,
                    style.painter(table(), seed), plan.slope(), plan.base(), height, m, 1, air, false);
            Supplier<EditSource> skin = () -> new HeightSource(f, plan.newTop(), HeightSource.Mode.GENERATE,
                    style.painter(table(), seed), plan.slope(), plan.base(), height, m, 1, air, true);
            String note = "seed=" + seed + " · style " + style.name() + " · radius " + o.integer("radius") + " · height " + height;
            request(s, w, "mountain", note, full, skin, style, now);
        });
    }

    // ------------------------------------------------------------------ isla

    public void island(CommandSender s, Options.Resolved o, boolean now) {
        Session ses = edits().session(Edits.owner(s));
        if (busy(s, ses)) return;
        World w = world(s, o);
        if (w == null) return;
        int r = o.integer("radius");
        int cx, cy, cz;
        if (o.at() != null) {
            int[] a = parseAt(o.at(), feet(s));
            cx = a[0];
            cy = a[1];
            cz = a[2];
        } else if (s instanceof Player p) {
            // Delante, a una distancia que no toca al jugador. Volando, la cima a la altura de sus pies; en el suelo,
            // lo bastante alta para que la isla (con sus estalactitas) flote sobre él: si no, quedaría enterrada.
            Location l = p.getLocation();
            double yaw = Math.toRadians(l.getYaw());
            double dist = r * 1.3 + 6;
            cx = (int) Math.floor(l.getX() - Math.sin(yaw) * dist);
            cz = (int) Math.floor(l.getZ() + Math.cos(yaw) * dist);
            cy = p.isFlying() ? l.getBlockY() - 1 : l.getBlockY() + (int) Math.ceil(o.number("depth") * r * 1.7) + 6;
            cy = Math.min(cy, w.getMaxHeight() - 12);
        } else {
            s.sendMessage(plugin.messages().get("terrain-needs-at", "kind", "island"));
            return;
        }
        long estimate = (long) (Math.PI * r * r * (2 + o.number("depth") * r / 3));
        if (tooBig(s, w, estimate)) return;
        int ix = cx, iy = cy, iz = cz;
        ses.terrain = seed -> computeIsland(s, ses, w, ix, iy, iz, o, seed, now);
        ses.terrain.accept(o.seed());
    }

    private void computeIsland(CommandSender s, Session ses, World w, int cx, int cy, int cz, Options.Resolved o, long seed, boolean now) {
        IslandShape shape = new IslandShape(o.integer("radius"), o.number("depth"), o.number("relief"), o.number("spikes"),
                o.number("roots"), o.integer("trees"), seed);
        int min = w.getMinHeight(), max = w.getMaxHeight() - 1;
        run(s, ses, () -> shape.plan(cx, cy, cz, min, max), plan -> {
            Masks m = masks();
            Style style = o.style();
            Supplier<EditSource> full = () -> new IslandSource(plan, style.painter(table(), seed), m, false);
            Supplier<EditSource> skin = () -> new IslandSource(plan, style.painter(table(), seed), m, true);
            String note = "seed=" + seed + " · style " + style.name() + " · radius " + o.integer("radius");
            request(s, w, "island", note, full, skin, style, now);
        });
    }

    // ------------------------------------------------------------------ comunes

    /** Calcula en el hilo de terreno y sigue en el principal (H5 CA2). */
    private <T> void run(CommandSender s, Session ses, Supplier<T> work, java.util.function.Consumer<T> then) {
        ses.terrainBusy = true;
        CompletableFuture.supplyAsync(work, compute).whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            ses.terrainBusy = false;
            if (err != null) {
                plugin.getLogger().log(Level.SEVERE, "Terrain calculation failed", err);
                s.sendMessage(plugin.messages().get("terrain-error"));
                return;
            }
            then.accept(v);
        }));
    }

    private void request(CommandSender s, World w, String kind, String note, Supplier<EditSource> full,
                         Supplier<EditSource> skin, Style style, boolean now) {
        EditSource probe = full.get();
        if (probe.volume() == 0) {
            s.sendMessage(plugin.messages().get("terrain-nothing", "kind", kind));
            return;
        }
        Set<Integer> placed = new LinkedHashSet<>();
        for (String st : style.states()) placed.add(table().intern(st));
        boolean preview = cfg().previewFirst() && !now && s instanceof Player;
        edits().request(s, new Edits.Op(kind, note, w, probe.bounds(), probe.volume(), full, placed, null, skin, note, true), preview);
    }

    /** /wc reroll: la misma receta con otra semilla, sin volver a leer el suelo (H1 CA4). */
    public void reroll(CommandSender s) {
        UUID owner = Edits.owner(s);
        Session ses = edits().session(owner);
        if (busy(s, ses)) return;
        if (ses.terrain == null) {
            s.sendMessage(plugin.messages().get("nothing-to-reroll"));
            return;
        }
        edits().dropPending(owner);
        ses.terrain.accept(ThreadLocalRandom.current().nextLong(1, 1L << 40));
    }

    // ------------------------------------------------------------------ pincel

    /** Una pasada del pincel donde mira el jugador (H3 CA2, CA5). Los clics mientras hay una en marcha se ignoran. */
    public void stroke(Player p, BrushSettings b) {
        UUID owner = p.getUniqueId();
        Session ses = edits().session(owner);
        if (ses.terrainBusy || edits().running(owner) > 0) return;
        RayTraceResult hit = look(p, cfg().brushRange());
        if (hit == null || hit.getHitBlock() == null) return;
        World w = p.getWorld();
        int r = Math.min(b.radius(), cfg().brushMaxRadius());
        int cx = hit.getHitBlock().getX(), cy = hit.getHitBlock().getY(), cz = hit.getHitBlock().getZ();
        int margin = Brush.margin(b.mode(), r) + r;
        int reach = r * 2 + 8;
        Box rect = new Box(cx - margin, 0, cz - margin, cx + margin, 0, cz + margin);
        int top = Math.min(w.getMaxHeight() - 1, cy + reach), bottom = Math.max(w.getMinHeight(), cy - reach);
        Masks m = masks();
        Mask.Compiled only;
        Pattern paint = null;
        Style style = null;
        try {
            only = b.mask() == null ? null : plugin.config().parser().mask(b.mask()).compile(table());
            if (b.mode() == BrushSettings.Mode.PAINT) {
                if (b.pattern() != null) paint = plugin.config().parser().pattern(b.pattern());
                else {
                    String name = b.style() != null ? b.style() : String.valueOf(cfg().defaults().getOrDefault("mountain", java.util.Map.of()).getOrDefault("style", ""));
                    style = cfg().styles().getOrDefault(name, cfg().styles().values().iterator().next());
                }
            }
        } catch (ParseException e) {
            p.sendMessage(plugin.messages().get("error", "message", e.getMessage()));
            return;
        }
        Style paintStyle = paint != null ? Style.of(paint) : style;
        ColumnScan scan = new ColumnScan(rect, top, bottom, m.ground(), m.cover(), m.buryable(), m.air());
        ses.terrainBusy = true;
        long seed = ThreadLocalRandom.current().nextLong();
        BrushSettings use = new BrushSettings(b.mode(), r, b.strength(), b.style(), b.pattern(), b.mask());
        edits().scan(p, w, scan, () -> {
            HeightField f = scan.field();
            run(p, ses, () -> Brush.apply(use, f, cx, cz, cy, only == null ? null : only::test, seed), res -> {
                boolean painting = use.mode() == BrushSettings.Mode.PAINT;
                int air = edits().blocks().air();
                Style.Painter painter = painting ? paintStyle.painter(table(), seed) : null;
                float[] slope = HeightField.slopes(groundOf(f), f.width, f.depth);
                Supplier<EditSource> src = () -> new HeightSource(f, res.newTop(),
                        painting ? HeightSource.Mode.PAINT : HeightSource.Mode.PRESERVE, painter, slope, res.base(),
                        res.span(), m, painting ? paintStyle.paintDepth() : 1, air, false);
                EditSource probe = src.get();
                if (probe.volume() == 0) return;
                Set<Integer> placed = new LinkedHashSet<>();
                if (painting) for (String st : paintStyle.states()) placed.add(table().intern(st));
                else for (int i = 0; i < f.size(); i++) if (res.newTop()[i] != HeightField.NONE) {
                    placed.add(f.surface(i));
                    placed.add(f.under(i));
                }
                edits().request(p, new Edits.Op("brush " + use.mode().id(), use.describe(), w, probe.bounds(),
                        probe.volume(), src, placed, null), false);
            });
        }, () -> ses.terrainBusy = false);
    }

    /**
     * /wc smooth sobre la selección (spec 028 H2 CA5): el suavizado del pincel en todo el rectángulo, solo sobre el
     * suelo natural (no derrite árboles ni casas). Lee un borde de 2 alrededor para que el borde empalme.
     */
    public void smooth(CommandSender s, World w, dev.leo.worldcraft.region.Region region, int passes, boolean preview) {
        Session ses = edits().session(Edits.owner(s));
        if (busy(s, ses)) return;
        Box box = region.bounds();
        Box rect = new Box(box.minX() - 2, 0, box.minZ() - 2, box.maxX() + 2, 0, box.maxZ() + 2);
        Masks m = masks();
        ColumnScan scan = new ColumnScan(rect, box.maxY(), box.minY(), m.ground(), m.cover(), m.buryable(), m.air());
        ses.terrainBusy = true;
        edits().scan(s, w, scan, () -> {
            HeightField f = scan.field();
            run(s, ses, () -> {
                int[] top = Brush.smoothArea(f, box, passes);
                // Spec 030 H4 CA1: solo cambian las columnas de la forma (el borde se lee igual, para que empalme).
                if (!(region instanceof Box)) {
                    for (int i = 0; i < top.length; i++) if (region.span(f.x(i), f.z(i)) == null) top[i] = HeightField.NONE;
                }
                return top;
            }, newTop -> {
                int air = edits().blocks().air();
                float[] slope = HeightField.slopes(groundOf(f), f.width, f.depth);
                Supplier<EditSource> src = () -> new HeightSource(f, newTop, HeightSource.Mode.PRESERVE, null, slope,
                        box.minY(), Math.max(1, box.height()), m, 1, air, false);
                EditSource probe = src.get();
                if (probe.volume() == 0) {
                    s.sendMessage(plugin.messages().get("smooth-nothing"));
                    return;
                }
                Set<Integer> placed = new LinkedHashSet<>();
                for (int i = 0; i < f.size(); i++) if (newTop[i] != HeightField.NONE) {
                    placed.add(f.surface(i));
                    placed.add(f.under(i));
                }
                edits().request(s, new Edits.Op("smooth", passes + (passes == 1 ? " pass" : " passes"), w, probe.bounds(),
                        probe.volume(), src, placed, null), preview);
            });
        }, () -> ses.terrainBusy = false);
    }

    private static int[] groundOf(HeightField f) {
        int[] g = new int[f.size()];
        for (int i = 0; i < g.length; i++) g[i] = f.ground(i);
        return g;
    }

    /** Los estilos, para /wc styles y las sugerencias. */
    public List<String> styleNames() {
        return new ArrayList<>(cfg().styles().keySet());
    }

}
