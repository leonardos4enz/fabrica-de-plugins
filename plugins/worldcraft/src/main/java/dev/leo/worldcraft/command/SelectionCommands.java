package dev.leo.worldcraft.command;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.config.Limits;
import dev.leo.worldcraft.config.Messages;
import dev.leo.worldcraft.edit.Census;
import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.paper.Edits;
import dev.leo.worldcraft.paper.Session;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.region.Box;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spec 029 H1 y H4 (plan §1.3, §1.4): ajustar y medir la selección sin moverse, y la máscara global. Lo que lee el mundo
 * (count, distr, trim) va por tandas con el motor de la 025, con el tope de bloques del grupo.
 */
final class SelectionCommands {
    static final List<String> SUBS = List.of("hpos1", "hpos2", "chunk", "outset", "inset", "trim", "count", "distr",
            "clearclipboard", "clearhistory", "gmask");

    private final Worldcraft plugin;
    private final WorldcraftCommand main;

    SelectionCommands(Worldcraft plugin, WorldcraftCommand main) {
        this.plugin = plugin;
        this.main = main;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private Edits edits() {
        return plugin.edits();
    }

    void run(CommandSender s, String sub, String[] a) {
        try {
            switch (sub) {
                case "hpos1", "hpos2" -> hpos(s, sub.equals("hpos1") ? 1 : 2);
                case "chunk" -> chunk(s, a);
                case "outset", "inset" -> outset(s, a, sub.equals("inset"));
                case "trim", "count" -> census(s, a, sub);
                case "distr" -> distr(s, a);
                case "clearclipboard" -> {
                    edits().session(Edits.owner(s)).clipboard(null, null);
                    s.sendMessage(msg().get("clipboard-cleared"));
                }
                case "clearhistory" -> clearHistory(s);
                case "gmask" -> gmask(s, a);
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

    /** H1 CA1: la esquina en el bloque que se mira, hasta terrain.reach (como jumpto). */
    private void hpos(CommandSender s, int which) {
        Player p = player(s);
        if (p == null) return;
        int reach = plugin.config().terrain().reach();
        RayTraceResult hit = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), reach,
                FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlock() == null) {
            p.sendMessage(msg().get("nav-no-target", "reach", String.valueOf(reach)));
            return;
        }
        var b = hit.getHitBlock();
        main.setCorner(s, p.getUniqueId(), which, p.getWorld(), b.getX(), b.getY(), b.getZ());
    }

    /** H1 CA2: el chunk donde se está (o --chunk-coords x z), de lo más bajo a lo más alto; --expand lo suma. */
    private void chunk(CommandSender s, String[] a) {
        Args args = Args.parse(a, 1, Set.of("expand", "chunk-coords"));
        if (args.problem != null) throw new IllegalArgumentException(args.problem);
        Session ses = edits().session(Edits.owner(s));
        World w;
        int cx, cz;
        if (args.flags.contains("chunk-coords")) {
            if (args.positional.size() != 2) throw new WorldcraftCommand.Usage();
            try {
                cx = Integer.parseInt(args.positional.get(0));
                cz = Integer.parseInt(args.positional.get(1));
            } catch (NumberFormatException e) {
                throw new WorldcraftCommand.Usage();
            }
            w = s instanceof Player p ? p.getWorld() : ses.world() != null ? Bukkit.getWorld(ses.world()) : Bukkit.getWorlds().get(0);
        } else {
            Player p = player(s);
            if (p == null) return;
            if (!args.positional.isEmpty()) throw new WorldcraftCommand.Usage();
            Location l = p.getLocation();
            w = p.getWorld();
            cx = l.getBlockX() >> 4;
            cz = l.getBlockZ() >> 4;
        }
        Box b = Box.chunk(cx, cz, w.getMinHeight(), w.getMaxHeight() - 1);
        Box old = ses.box();
        if (args.flags.contains("expand") && old != null && w.getName().equals(ses.world())) b = old.union(b);
        ses.selectBox(w.getName(), b);
        s.sendMessage(msg().get("sel-changed", "size", b.size(), "blocks", Edits.fmt(b.volume())));
    }

    /** H1 CA3: hacia todos los lados (inset achica); --horizontal o --vertical solo esos ejes. */
    private void outset(CommandSender s, String[] a, boolean inset) {
        Args args = Args.parse(a, 1, Set.of("horizontal", "vertical"));
        if (args.problem != null) throw new IllegalArgumentException(args.problem);
        if (args.positional.size() != 1) throw new WorldcraftCommand.Usage();
        int n;
        try {
            n = Integer.parseInt(args.positional.get(0));
        } catch (NumberFormatException e) {
            throw new WorldcraftCommand.Usage();
        }
        if (n < 0 || n > 100_000) throw new WorldcraftCommand.Usage();
        Session ses = edits().session(Edits.owner(s));
        Box b = ses.box();
        if (b == null) {
            s.sendMessage(msg().get("no-selection"));
            return;
        }
        Box c;
        try {
            c = b.outset(inset ? -n : n, args.flags.contains("horizontal"), args.flags.contains("vertical"));
        } catch (IllegalArgumentException e) {
            s.sendMessage(msg().get("sel-empty"));
            return;
        }
        World w = Bukkit.getWorld(ses.world());
        if (w != null) {
            c = c.clampY(w.getMinHeight(), w.getMaxHeight() - 1);
            if (c == null) {
                s.sendMessage(msg().get("sel-outside"));
                return;
            }
        }
        ses.selectBox(ses.world(), c);
        s.sendMessage(msg().get("sel-changed", "size", c.size(), "blocks", Edits.fmt(c.volume())));
    }

    /** La selección dentro del tope de bloques del grupo (lo que se lee también cuesta), o null y lo dice. */
    private Box readable(CommandSender s, Session ses) {
        Box box = main.selection(s, ses);
        if (box == null) return null;
        Limits.Resolved lim = edits().limits(s);
        if (box.volume() > lim.maxBlocks()) {
            s.sendMessage(msg().get("limit-blocks", "blocks", Edits.fmt(box.volume()), "max", Edits.fmt(lim.maxBlocks()),
                    "groups", String.join(", ", lim.groups())));
            return null;
        }
        return box;
    }

    /** H1 CA4 y CA5: trim [máscara] y count &lt;máscara&gt;: una lectura que cuenta, sin guardar la foto. */
    private void census(CommandSender s, String[] a, String sub) throws ParseException {
        Args args = Args.parse(a, 1, Set.of());
        if (args.problem != null) throw new IllegalArgumentException(args.problem);
        String text = args.join(0);
        if (text.isBlank()) {
            if (sub.equals("count")) throw new WorldcraftCommand.Usage();
            text = "!air"; // trim de fábrica: todo lo que existe (como #existing)
        }
        Mask mask = plugin.config().parser().maskNear(text);
        Session ses = edits().session(Edits.owner(s));
        Box box = readable(s, ses);
        if (box == null) return;
        World w = Bukkit.getWorld(ses.world());
        Mask.Compiled mc = mask.compile(edits().blocks().table());
        long seed = ThreadLocalRandom.current().nextLong();
        String shown = mask.text();
        java.util.function.Consumer<Census> done = c -> {
            if (sub.equals("count")) {
                s.sendMessage(msg().get("count-result", "count", Edits.fmt(c.total()), "mask", shown, "blocks", Edits.fmt(box.volume())));
                return;
            }
            Box t = c.bounds();
            if (t == null) {
                s.sendMessage(msg().get("trim-none", "mask", shown));
                return;
            }
            ses.selectBox(ses.world(), t);
            s.sendMessage(msg().get("sel-changed", "size", t.size(), "blocks", Edits.fmt(t.volume())));
        };
        if (mask.needsNeighbors()) {
            main.readAround(s, w, box, sub, snap -> done.accept(new Census(mc, snap::get, seed, false, edits().blocks().table()).over(snap, box)));
            return;
        }
        Census c = new Census(mc, null, seed, false, edits().blocks().table());
        edits().read(s, w, box, sub, c, () -> done.accept(c));
    }

    /** H1 CA5: los 10 más comunes con su %, por material o por estado (--states); --clipboard sin leer el mundo. */
    private void distr(CommandSender s, String[] a) {
        Args args = Args.parse(a, 1, Set.of("states", "clipboard"));
        if (args.problem != null) throw new IllegalArgumentException(args.problem);
        if (!args.positional.isEmpty()) throw new WorldcraftCommand.Usage();
        boolean byState = args.flags.contains("states");
        Session ses = edits().session(Edits.owner(s));
        Census c = new Census(null, null, 0, byState, edits().blocks().table());
        if (args.flags.contains("clipboard")) {
            Clipboard clip = ses.clipboard();
            if (clip == null) {
                s.sendMessage(msg().get("no-clipboard"));
                return;
            }
            show(s, c.over(clip));
            return;
        }
        Box box = readable(s, ses);
        if (box == null) return;
        edits().read(s, Bukkit.getWorld(ses.world()), box, "distr", c, () -> show(s, c));
    }

    private void show(CommandSender s, Census c) {
        s.sendMessage(msg().get("distr-header", "total", Edits.fmt(c.total())));
        for (Census.Row r : c.top(10)) {
            s.sendMessage(msg().get("distr-line", "name", r.name(), "count", Edits.fmt(r.count()),
                    "percent", String.format(Locale.ROOT, "%.1f", r.percent())));
        }
        int more = c.groups() - 10;
        if (more > 0) s.sendMessage(msg().get("distr-more", "count", String.valueOf(more)));
    }

    /** H1 CA6: borra el historial propio en disco, con /wc confirm. */
    private void clearHistory(CommandSender s) {
        var history = edits().history();
        java.util.UUID owner = Edits.owner(s);
        int n = history.list(owner).size();
        if (n == 0) {
            s.sendMessage(msg().get("history-empty-clear"));
            return;
        }
        edits().ask(s, () -> s.sendMessage(msg().get("history-cleared", "count", String.valueOf(history.clear(owner)))));
        s.sendMessage(msg().get("clearhistory-confirm", "count", String.valueOf(n),
                "seconds", String.valueOf(plugin.config().settings().confirmSeconds())));
    }

    /** H4: /wc gmask &lt;máscara&gt; | off; sin nada dice cuál hay. Solo máscaras sin vecinos (el azar sí). */
    private void gmask(CommandSender s, String[] a) throws ParseException {
        Session ses = edits().session(Edits.owner(s));
        String text = a.length < 2 ? "" : String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)).trim();
        if (text.isEmpty()) {
            s.sendMessage(ses.gmask == null ? msg().get("gmask-none") : msg().get("gmask-current", "mask", ses.gmask.text()));
            return;
        }
        if (text.equalsIgnoreCase("off")) {
            ses.gmask = null;
            s.sendMessage(msg().get("gmask-off"));
            return;
        }
        Mask m = plugin.config().parser().maskNear(text);
        if (m.needsNeighbors()) {
            throw new IllegalArgumentException("'" + text + "' looks at the blocks around: a global mask can't (every edit would read twice); use it in replace or count");
        }
        ses.gmask = m;
        s.sendMessage(msg().get("gmask-set", "mask", m.text()));
    }
}
