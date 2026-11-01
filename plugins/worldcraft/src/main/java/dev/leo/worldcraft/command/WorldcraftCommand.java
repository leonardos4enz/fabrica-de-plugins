package dev.leo.worldcraft.command;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.config.Issues;
import dev.leo.worldcraft.config.Limits;
import dev.leo.worldcraft.config.Messages;
import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.edit.EditJob;
import dev.leo.worldcraft.edit.PasteSource;
import dev.leo.worldcraft.edit.RegionSource;
import dev.leo.worldcraft.history.HistoryStore;
import dev.leo.worldcraft.paper.Edits;
import dev.leo.worldcraft.paper.PaperBlocks;
import dev.leo.worldcraft.paper.SelfTest;
import dev.leo.worldcraft.paper.Session;
import dev.leo.worldcraft.paper.Wand;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Direction;
import dev.leo.worldcraft.region.Shape;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** /worldcraft (alias /wc): los subcomandos, su ayuda con ejemplos (H8 CA5) y el autocompletado por permiso (H8 CA4). */
public final class WorldcraftCommand implements TabExecutor {
    public static final String USE = "worldcraft.use", MODERATE = "worldcraft.moderate", ADMIN = "worldcraft.admin";

    /** Subcomando → permiso, en el orden de la ayuda. */
    private static final Map<String, String> SUBS = new LinkedHashMap<>();

    static {
        for (String s : List.of("help", "wand", "pos1", "pos2", "sel", "expand", "contract", "shift", "set", "replace",
                "walls", "faces", "copy", "cut", "paste", "rotate", "flip", "undo", "redo", "history", "confirm", "cancel",
                "jobs", "limits")) {
            SUBS.put(s, USE);
        }
        for (String s : List.of("reload", "validate", "selftest")) SUBS.put(s, ADMIN);
    }

    private static final Set<String> EDIT_FLAGS = Set.of("preview");

    private final Worldcraft plugin;

    public WorldcraftCommand(Worldcraft plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private Edits edits() {
        return plugin.edits();
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        String perm = SUBS.get(sub);
        if (perm == null) {
            s.sendMessage(msg().get("unknown-command", "input", a[0], "hint",
                    hintText(Issues.closest(sub, visible(s)))));
            return true;
        }
        if (!s.hasPermission(perm)) {
            s.sendMessage(msg().get("no-permission"));
            return true;
        }
        try {
            switch (sub) {
                case "help" -> help(s, a);
                case "wand" -> wand(s);
                case "pos1", "pos2" -> pos(s, a, sub.equals("pos1") ? 1 : 2);
                case "sel" -> sel(s, a);
                case "expand", "contract", "shift" -> resize(s, a, sub);
                case "set" -> region(s, a, Shape.FILL, false);
                case "walls" -> region(s, a, Shape.WALLS, false);
                case "faces" -> region(s, a, Shape.FACES, false);
                case "replace" -> region(s, a, Shape.FILL, true);
                case "copy", "cut" -> copy(s, a, sub.equals("cut"));
                case "paste" -> paste(s, a);
                case "rotate" -> rotate(s, a);
                case "flip" -> flip(s, a);
                case "undo", "redo" -> undo(s, a, sub.equals("redo"));
                case "history" -> history(s, a);
                case "confirm" -> edits().confirm(s);
                case "cancel" -> edits().cancel(s);
                case "jobs" -> jobs(s, a);
                case "limits" -> limits(s);
                case "reload" -> reload(s);
                case "validate" -> validate(s);
                case "selftest" -> new SelfTest(plugin).run(s);
                default -> help(s, a);
            }
        } catch (Usage u) {
            s.sendMessage(msg().get("usage", "usage", msg().raw("help." + sub + ".usage")));
        }
        return true;
    }

    /** El subcomando se usó mal: se muestra su forma. */
    private static final class Usage extends RuntimeException {
        Usage() {
            super(null, null, false, false);
        }
    }

    private static String hintText(String closest) {
        return closest == null ? "" : " (did you mean '" + closest + "'?)";
    }

    private List<String> visible(CommandSender s) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : SUBS.entrySet()) if (s.hasPermission(e.getValue())) out.add(e.getKey());
        return out;
    }

    // ------------------------------------------------------------------ ayuda

    private void help(CommandSender s, String[] a) {
        if (a.length >= 2) {
            String sub = a[1].toLowerCase(Locale.ROOT);
            if (!SUBS.containsKey(sub)) {
                s.sendMessage(msg().get("unknown-command", "input", a[1], "hint", hintText(Issues.closest(sub, SUBS.keySet()))));
                return;
            }
            s.sendMessage(msg().get("help-detail", "usage", msg().raw("help." + sub + ".usage"),
                    "text", msg().raw("help." + sub + ".text"), "example", msg().raw("help." + sub + ".example")));
            return;
        }
        s.sendMessage(msg().get("help-header"));
        for (String sub : visible(s)) {
            s.sendMessage(msg().get("help-line", "usage", msg().raw("help." + sub + ".usage"), "text", msg().raw("help." + sub + ".text")));
        }
        s.sendMessage(msg().get("help-footer"));
    }

    // ------------------------------------------------------------------ selección

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        s.sendMessage(msg().get("players-only"));
        return null;
    }

    private void wand(CommandSender s) {
        Player p = player(s);
        if (p == null) return;
        p.getInventory().addItem(plugin.wand().create());
        p.sendMessage(msg().get("wand-given"));
    }

    private void pos(CommandSender s, String[] a, int which) {
        Args args = Args.parse(a, 1, Set.of("world"));
        if (args.problem != null) {
            s.sendMessage(msg().get("error", "message", args.problem));
            return;
        }
        World world;
        int x, y, z;
        if (args.positional.isEmpty()) {
            Player p = player(s);
            if (p == null) return;
            Location l = p.getLocation();
            world = p.getWorld();
            x = l.getBlockX();
            y = l.getBlockY();
            z = l.getBlockZ();
        } else {
            if (args.positional.size() != 3) throw new Usage();
            Location base = s instanceof Player p ? p.getLocation() : null;
            world = args.values.containsKey("world") ? Bukkit.getWorld(args.values.get("world"))
                    : base != null ? base.getWorld() : Bukkit.getWorlds().get(0);
            if (world == null) {
                s.sendMessage(msg().get("unknown-world", "world", args.values.get("world")));
                return;
            }
            try {
                x = coord(args.positional.get(0), base == null ? 0 : base.getBlockX());
                y = coord(args.positional.get(1), base == null ? 0 : base.getBlockY());
                z = coord(args.positional.get(2), base == null ? 0 : base.getBlockZ());
            } catch (NumberFormatException e) {
                throw new Usage();
            }
        }
        setCorner(s, Edits.owner(s), which, world, x, y, z);
    }

    static int coord(String v, int base) {
        if (v.startsWith("~")) return base + (v.length() == 1 ? 0 : Integer.parseInt(v.substring(1)));
        return Integer.parseInt(v);
    }

    /** Pone una esquina y dice el tamaño (también la usa la varita, H1 CA2 y CA3). */
    public void setCorner(CommandSender s, UUID owner, int which, World world, int x, int y, int z) {
        Session ses = edits().session(owner);
        ses.corner(which, world.getName(), x, y, z);
        Box b = ses.box();
        String c = x + " " + y + " " + z;
        if (b == null) s.sendMessage(msg().get("corner-set", "which", String.valueOf(which), "pos", c));
        else s.sendMessage(msg().get("corner-set-size", "which", String.valueOf(which), "pos", c, "size", b.size(),
                "blocks", Edits.fmt(b.volume())));
    }

    private void sel(CommandSender s, String[] a) {
        Session ses = edits().session(Edits.owner(s));
        String what = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (what) {
            case "clear" -> {
                ses.clearSelection();
                s.sendMessage(msg().get("sel-cleared"));
            }
            case "show", "hide" -> {
                ses.show(what.equals("show"));
                s.sendMessage(msg().get(what.equals("show") ? "sel-shown" : "sel-hidden"));
            }
            case "" -> {
                Box b = ses.box();
                if (b == null) {
                    s.sendMessage(msg().get("no-selection"));
                    return;
                }
                s.sendMessage(msg().get("sel-info", "size", b.size(), "blocks", Edits.fmt(b.volume()), "world", ses.world(),
                        "from", b.minX() + " " + b.minY() + " " + b.minZ(), "to", b.maxX() + " " + b.maxY() + " " + b.maxZ()));
            }
            default -> throw new Usage();
        }
    }

    private void resize(CommandSender s, String[] a, String sub) {
        Session ses = edits().session(Edits.owner(s));
        Box b = ses.box();
        if (b == null) {
            s.sendMessage(msg().get("no-selection"));
            return;
        }
        World world = Bukkit.getWorld(ses.world());
        if (a.length >= 2 && sub.equals("expand") && a[1].equalsIgnoreCase("vert")) {
            if (world == null) return;
            b = b.withY(world.getMinHeight(), world.getMaxHeight() - 1);
        } else {
            if (a.length < 2) throw new Usage();
            int n;
            try {
                n = Integer.parseInt(a[1]);
            } catch (NumberFormatException e) {
                throw new Usage();
            }
            if (n < 0 || n > 100_000) throw new Usage();
            Direction d;
            if (a.length >= 3) {
                d = Direction.parse(a[2]);
                if (d == null) {
                    s.sendMessage(msg().get("unknown-direction", "input", a[2], "hint", hintText(Issues.closest(a[2], Direction.NAMES))));
                    return;
                }
            } else if (s instanceof Player p) {
                d = Direction.facing(p.getLocation().getYaw(), p.getLocation().getPitch());
            } else {
                throw new Usage();
            }
            try {
                b = switch (sub) {
                    case "expand" -> b.expand(d, n);
                    case "contract" -> b.contract(d, n);
                    default -> b.shift(d, n);
                };
            } catch (IllegalArgumentException e) {
                s.sendMessage(msg().get("sel-empty"));
                return;
            }
            if (world != null) {
                Box c = b.clampY(world.getMinHeight(), world.getMaxHeight() - 1);
                if (c == null) {
                    s.sendMessage(msg().get("sel-outside"));
                    return;
                }
                b = c;
            }
        }
        ses.selectBox(ses.world(), b);
        s.sendMessage(msg().get("sel-changed", "size", b.size(), "blocks", Edits.fmt(b.volume())));
    }

    // ------------------------------------------------------------------ operaciones

    /** La selección dentro del mundo, o null (y lo dice). */
    private Box selection(CommandSender s, Session ses) {
        Box b = ses.box();
        if (b == null) {
            s.sendMessage(msg().get("no-selection"));
            return null;
        }
        World w = Bukkit.getWorld(ses.world());
        if (w == null) {
            s.sendMessage(msg().get("unknown-world", "world", ses.world()));
            return null;
        }
        Box c = b.clampY(w.getMinHeight(), w.getMaxHeight() - 1);
        if (c == null) s.sendMessage(msg().get("sel-outside"));
        return c;
    }

    private void region(CommandSender s, String[] a, Shape shape, boolean replace) {
        Args args = Args.parse(a, 1, EDIT_FLAGS);
        if (args.problem != null) {
            s.sendMessage(msg().get("error", "message", args.problem));
            return;
        }
        String maskText = null, patternText;
        if (replace) {
            String[] mp = args.maskAndPattern();
            if (mp == null) throw new Usage();
            maskText = mp[0];
            patternText = mp[1];
        } else {
            patternText = args.join(0);
            if (patternText.isBlank()) throw new Usage();
        }
        Session ses = edits().session(Edits.owner(s));
        Box box = selection(s, ses);
        if (box == null) return;
        World world = Bukkit.getWorld(ses.world());
        Pattern pattern;
        Mask mask;
        try {
            pattern = plugin.config().parser().pattern(patternText);
            mask = maskText == null ? null : plugin.config().parser().mask(maskText);
        } catch (ParseException e) {
            s.sendMessage(msg().get("error", "message", e.getMessage()));
            return;
        }
        PaperBlocks blocks = edits().blocks();
        Pattern.Compiled pc = pattern.compile(blocks.table());
        Mask.Compiled mc = mask == null ? null : mask.compile(blocks.table());
        long seed = ThreadLocalRandom.current().nextLong();
        List<Integer> placed = new ArrayList<>();
        for (String st : pattern.states()) placed.add(blocks.table().intern(st));
        String kind = replace ? "replace" : shape == Shape.FILL ? "set" : shape == Shape.WALLS ? "walls" : "faces";
        String detail = replace ? mask.text() + " -> " + pattern.text() : pattern.text();
        edits().request(s, new Edits.Op(kind, detail, world, box, box.count(shape),
                () -> new RegionSource(box, shape, mc, pc, seed), placed, null), args.flags.contains("preview"));
    }

    private void copy(CommandSender s, String[] a, boolean cut) {
        Args args = Args.parse(a, 1, cut ? EDIT_FLAGS : Set.of());
        if (args.problem != null) {
            s.sendMessage(msg().get("error", "message", args.problem));
            return;
        }
        Session ses = edits().session(Edits.owner(s));
        Box box = selection(s, ses);
        if (box == null) return;
        World world = Bukkit.getWorld(ses.world());
        Limits.Resolved lim = edits().limits(s);
        if (box.volume() > lim.maxBlocks()) {
            s.sendMessage(msg().get("limit-blocks", "blocks", Edits.fmt(box.volume()), "max", Edits.fmt(lim.maxBlocks()),
                    "groups", String.join(", ", lim.groups())));
            return;
        }
        int[] o = origin(s, box);
        if (!cut) {
            edits().copy(s, world, box, o[0], o[1], o[2], () -> s.sendMessage(msg().get("copied",
                    "size", box.size(), "blocks", Edits.fmt(box.volume()))));
            return;
        }
        PaperBlocks blocks = edits().blocks();
        Pattern.Compiled air = Pattern.of("minecraft:air").compile(blocks.table());
        edits().request(s, new Edits.Op("cut", "", world, box, box.volume(),
                () -> new RegionSource(box, Shape.FILL, null, air, 0), List.of(), () -> {
            Clipboard clip = new Clipboard(box, o[0], o[1], o[2]);
            ses.clipboard(clip, world.getName());
            return (x, y, z, st, tile) -> clip.put(box, x, y, z, st, tile);
        }), args.flags.contains("preview"));
    }

    /** Dónde está quien copia o pega: el jugador; la consola usa la esquina 1. */
    private int[] origin(CommandSender s, Box box) {
        if (s instanceof Player p) {
            Location l = p.getLocation();
            return new int[]{l.getBlockX(), l.getBlockY(), l.getBlockZ()};
        }
        int[] p1 = edits().session(Edits.owner(s)).corner1();
        return p1 != null ? p1 : new int[]{box.minX(), box.minY(), box.minZ()};
    }

    private void paste(CommandSender s, String[] a) {
        Args args = Args.parse(a, 1, Set.of("preview", "air"));
        if (args.problem != null) {
            s.sendMessage(msg().get("error", "message", args.problem));
            return;
        }
        Session ses = edits().session(Edits.owner(s));
        Clipboard clip = ses.clipboard();
        if (clip == null) {
            s.sendMessage(msg().get("no-clipboard"));
            return;
        }
        World world;
        int[] o;
        if (s instanceof Player p) {
            world = p.getWorld();
            o = origin(s, null);
        } else {
            if (ses.corner1() == null || ses.world() == null) {
                s.sendMessage(msg().get("no-selection"));
                return;
            }
            world = Bukkit.getWorld(ses.world());
            o = ses.corner1();
        }
        Box target = clip.target(o[0], o[1], o[2]);
        if (target.clampY(world.getMinHeight(), world.getMaxHeight() - 1) == null) {
            s.sendMessage(msg().get("sel-outside"));
            return;
        }
        PaperBlocks blocks = edits().blocks();
        boolean air = args.flags.contains("air");
        edits().request(s, new Edits.Op("paste", clip.size() + (air ? " with air" : ""), world, target, clip.volume(),
                () -> new PasteSource(clip, o[0], o[1], o[2], air, blocks::isAir, blocks), clip.distinct(), null),
                args.flags.contains("preview"));
    }

    private void rotate(CommandSender s, String[] a) {
        Clipboard clip = edits().session(Edits.owner(s)).clipboard();
        if (clip == null) {
            s.sendMessage(msg().get("no-clipboard"));
            return;
        }
        int deg;
        try {
            deg = a.length >= 2 ? Integer.parseInt(a[1]) : 90;
        } catch (NumberFormatException e) {
            throw new Usage();
        }
        if (deg % 90 != 0) {
            s.sendMessage(msg().get("rotate-90"));
            return;
        }
        clip.transform(clip.transform().rotate(deg));
        s.sendMessage(msg().get("rotated", "degrees", String.valueOf(deg), "size", clip.size()));
    }

    private void flip(CommandSender s, String[] a) {
        Clipboard clip = edits().session(Edits.owner(s)).clipboard();
        if (clip == null) {
            s.sendMessage(msg().get("no-clipboard"));
            return;
        }
        Direction d;
        if (a.length >= 2) {
            d = Direction.parse(a[1]);
            if (d == null) {
                s.sendMessage(msg().get("unknown-direction", "input", a[1], "hint", hintText(Issues.closest(a[1], Direction.NAMES))));
                return;
            }
        } else if (s instanceof Player p) {
            d = Direction.facing(p.getLocation().getYaw(), p.getLocation().getPitch());
        } else {
            throw new Usage();
        }
        char axis = d.dx != 0 ? 'x' : d.dy != 0 ? 'y' : 'z';
        clip.transform(clip.transform().flip(axis));
        s.sendMessage(msg().get("flipped", "direction", d.id()));
    }

    // ------------------------------------------------------------------ historial

    private void undo(CommandSender s, String[] a, boolean redo) {
        Args args = Args.parse(a, 1, Set.of("player"));
        if (args.problem != null) {
            s.sendMessage(msg().get("error", "message", args.problem));
            return;
        }
        int n = 1;
        if (!args.positional.isEmpty()) {
            try {
                n = Integer.parseInt(args.positional.get(0));
            } catch (NumberFormatException e) {
                throw new Usage();
            }
            if (n < 1 || n > 50) throw new Usage();
        }
        UUID target = Edits.owner(s);
        String name = s.getName();
        if (args.values.containsKey("player")) {
            String who = args.values.get("player");
            if (!who.equalsIgnoreCase(s.getName())) {
                if (!s.hasPermission(MODERATE)) {
                    s.sendMessage(msg().get("no-permission"));
                    return;
                }
                var found = edits().history().ownerByName(who);
                if (found.isEmpty()) {
                    s.sendMessage(msg().get("no-history", "player", who, "hint",
                            hintText(Issues.closest(who, edits().history().names()))));
                    return;
                }
                target = found.get();
                name = who;
            }
        }
        edits().undo(s, target, name, n, redo);
    }

    private void history(CommandSender s, String[] a) {
        UUID target = Edits.owner(s);
        String name = s.getName();
        if (a.length >= 2) {
            if (!a[1].equalsIgnoreCase(s.getName())) {
                if (!s.hasPermission(MODERATE)) {
                    s.sendMessage(msg().get("no-permission"));
                    return;
                }
                var found = edits().history().ownerByName(a[1]);
                if (found.isEmpty()) {
                    s.sendMessage(msg().get("no-history", "player", a[1], "hint",
                            hintText(Issues.closest(a[1], edits().history().names()))));
                    return;
                }
                target = found.get();
                name = a[1];
            }
        }
        List<HistoryStore.Entry> list = edits().history().list(target);
        if (list.isEmpty()) {
            s.sendMessage(msg().get("history-empty", "player", name));
            return;
        }
        s.sendMessage(msg().get("history-header", "player", name, "count", String.valueOf(list.size())));
        SimpleDateFormat f = new SimpleDateFormat("MMM d HH:mm", Locale.ROOT);
        for (HistoryStore.Entry e : list.subList(0, Math.min(10, list.size()))) {
            s.sendMessage(msg().get("history-line", "id", String.valueOf(e.id()), "kind", e.kind(), "detail", e.detail(),
                    "changed", Edits.fmt(e.changed()), "world", e.world(), "when", f.format(new Date(e.time())),
                    "state", e.state().name().toLowerCase(Locale.ROOT)));
        }
    }

    // ------------------------------------------------------------------ estado y administración

    private void jobs(CommandSender s, String[] a) {
        boolean all = s.hasPermission(MODERATE);
        if (a.length >= 3 && a[1].equalsIgnoreCase("cancel")) {
            if (!all) {
                s.sendMessage(msg().get("no-permission"));
                return;
            }
            try {
                int id = Integer.parseInt(a[2].replace("#", ""));
                s.sendMessage(msg().get(edits().cancelJob(id) ? "job-cancelled" : "no-such-job", "id", String.valueOf(id)));
            } catch (NumberFormatException e) {
                throw new Usage();
            }
            return;
        }
        List<EditJob> list = new ArrayList<>();
        for (EditJob j : edits().jobs()) if (all || j.owner().equals(Edits.owner(s))) list.add(j);
        var sch = edits().scheduler();
        s.sendMessage(msg().get("jobs-header", "count", String.valueOf(list.size()),
                "last", String.format(Locale.ROOT, "%.1f", sch.lastTickNanos() / 1e6),
                "max", String.format(Locale.ROOT, "%.1f", sch.maxTickNanos() / 1e6),
                "budget", String.valueOf(plugin.config().settings().maxMsPerTick())));
        EditJob last = edits().lastFinished();
        if (last != null) {
            s.sendMessage(msg().get("jobs-last", "kind", last.label(), "changed", Edits.fmt(last.changed()),
                    "longest", String.format(Locale.ROOT, "%.1f", last.maxStepNanos() / 1e6),
                    "budget", String.valueOf(plugin.config().settings().maxMsPerTick())));
        }
        for (EditJob j : list) {
            Player p = Bukkit.getPlayer(j.owner());
            String owner = p != null ? p.getName() : j.owner().equals(Edits.CONSOLE) ? "Console" : j.owner().toString().substring(0, 8);
            s.sendMessage(msg().get("jobs-line", "id", String.valueOf(j.id()), "kind", j.label(), "owner", owner,
                    "percent", String.valueOf((int) (j.progress() * 100)), "state", j.state().name().toLowerCase(Locale.ROOT)));
        }
    }

    private void limits(CommandSender s) {
        Limits.Resolved l = edits().limits(s);
        s.sendMessage(msg().get("limits", "groups", String.join(", ", l.groups()), "max", Edits.fmt(l.maxBlocks()),
                "jobs", String.valueOf(l.maxJobs()), "worlds", String.join(", ", l.worlds()),
                // Un bloque está prohibido solo si lo prohíben todos los grupos: con uno que no prohíbe nada, nada.
                "blocked", l.blocked().isEmpty() || l.blocked().contains(null) ? "none"
                        : l.blocked().size() == 1 ? l.blocked().get(0).text()
                        : "what all of these block: " + l.blocked().stream().map(Mask::text).reduce((x, y) -> x + " | " + y).orElse("")));
    }

    private void reload(CommandSender s) {
        var c = plugin.reload();
        s.sendMessage(msg().get("reloaded", "errors", String.valueOf(c.issues().errors()), "warnings", String.valueOf(c.issues().warnings())));
    }

    private void validate(CommandSender s) {
        var issues = plugin.config().issues().all();
        if (issues.isEmpty()) {
            s.sendMessage(msg().get("validate-ok"));
            return;
        }
        for (Issues.Issue i : issues) s.sendMessage(msg().get(i.level() == Issues.Level.ERROR ? "validate-error" : "validate-warning",
                "issue", i.toString()));
    }

    // ------------------------------------------------------------------ autocompletado

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length <= 1) return filter(visible(s), a.length == 0 ? "" : a[0]);
        String sub = a[0].toLowerCase(Locale.ROOT);
        String perm = SUBS.get(sub);
        if (perm == null || !s.hasPermission(perm)) return List.of();
        String last = a[a.length - 1];
        if (last.startsWith("--")) {
            Set<String> opts = switch (sub) {
                case "set", "replace", "walls", "faces", "cut" -> Set.of("--preview");
                case "paste" -> Set.of("--preview", "--air");
                case "undo", "redo" -> Set.of("--player");
                case "pos1", "pos2" -> Set.of("--world");
                default -> Set.of();
            };
            return filter(new ArrayList<>(opts), last);
        }
        return switch (sub) {
            case "help" -> a.length == 2 ? filter(visible(s), last) : List.of();
            case "sel" -> a.length == 2 ? filter(List.of("clear", "show", "hide"), last) : List.of();
            case "expand", "contract", "shift" -> a.length == 2 ? filter(sub.equals("expand") ? List.of("1", "5", "10", "vert") : List.of("1", "5", "10"), last)
                    : a.length == 3 ? filter(Direction.NAMES, last) : List.of();
            case "flip" -> a.length == 2 ? filter(Direction.NAMES, last) : List.of();
            case "rotate" -> a.length == 2 ? filter(List.of("90", "180", "270", "-90"), last) : List.of();
            case "set", "walls", "faces" -> blockSuggestions(last, false);
            case "replace" -> blockSuggestions(last, !hasWith(a));
            case "history" -> a.length == 2 && s.hasPermission(MODERATE) ? filter(edits().history().names(), last) : List.of();
            case "jobs" -> a.length == 2 && s.hasPermission(MODERATE) ? filter(List.of("cancel"), last) : List.of();
            default -> List.of();
        };
    }

    private static boolean hasWith(String[] a) {
        for (int i = 1; i < a.length - 1; i++) if (a[i].equalsIgnoreCase("with")) return true;
        return false;
    }

    /** Bloques para la última parte de una lista («70% stone, gra» → grass_block…), presets y, en máscaras, etiquetas. */
    private List<String> blockSuggestions(String last, boolean mask) {
        int cut = Math.max(last.lastIndexOf(','), last.lastIndexOf('%'));
        String head = last.substring(0, cut + 1), part = last.substring(cut + 1);
        String neg = mask && part.startsWith("!") ? "!" : "";
        String q = part.substring(neg.length()).toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (q.startsWith("$")) {
            for (String p : (mask ? plugin.config().maskPresets() : plugin.config().patternPresets()).keySet()) {
                if (("$" + p).startsWith(q)) out.add(head + neg + "$" + p);
            }
            return out;
        }
        if (mask && q.startsWith("#")) {
            for (String t : edits().blocks().tags()) {
                String n = "#" + t.replace("minecraft:", "");
                if (n.startsWith(q) && out.size() < 60) out.add(head + neg + n);
            }
            return out;
        }
        Set<String> seen = new HashSet<>();
        for (String b : edits().blocks().blocks()) {
            String n = b.replace("minecraft:", "");
            if (n.startsWith(q) && seen.add(n) && out.size() < 60) out.add(head + neg + n);
        }
        if (q.isEmpty()) {
            for (String p : (mask ? plugin.config().maskPresets() : plugin.config().patternPresets()).keySet()) out.add(head + "$" + p);
        }
        return out;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }
}
