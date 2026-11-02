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
import dev.leo.worldcraft.entity.EntityStage;
import dev.leo.worldcraft.paper.PaperBlocks;
import dev.leo.worldcraft.paper.Schematics;
import dev.leo.worldcraft.paper.SelfTest;
import dev.leo.worldcraft.paper.Session;
import dev.leo.worldcraft.paper.Wand;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Direction;
import dev.leo.worldcraft.region.Region;
import dev.leo.worldcraft.region.Selector;
import dev.leo.worldcraft.region.Shape;
import dev.leo.worldcraft.terrain.BrushSettings;
import dev.leo.worldcraft.terrain.Options;
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
                "jobs", "limits", "mountain", "island", "floating_island", "reroll", "brush", "tool", "styles")) {
            SUBS.put(s, USE);
        }
        // Spec 027: esquemas, con su propio permiso (un ayudante puede pegar sin poder llenar el disco).
        SUBS.put("schem", Schematics.PERM);
        SUBS.put("schematic", Schematics.PERM);
        // Spec 028: formas, región y utilidades (como cualquier edición) y navegación (permiso propio).
        for (String s : BuildCommands.SHAPES) SUBS.put(s, USE);
        for (String s : BuildCommands.REGION) SUBS.put(s, USE);
        for (String s : BuildCommands.UTILITIES) SUBS.put(s, USE);
        for (String s : NavigationCommands.SUBS) SUBS.put(s, NavigationCommands.PERM);
        // Spec 031: quitar entidades, cada uno con su permiso (como worldedit.butcher y worldedit.remove).
        SUBS.put("butcher", BuildCommands.PERM_BUTCHER);
        SUBS.put("remove", BuildCommands.PERM_REMOVE);
        // Spec 029: selección, información y máscara global (como cualquier edición).
        for (String s : SelectionCommands.SUBS) SUBS.put(s, USE);
        // Spec 033: biomas, árboles, plantas y estructuras del juego (como cualquier edición).
        for (String s : NatureCommands.SUBS) SUBS.put(s, USE);
        // Spec 034: fórmulas (como cualquier edición), con los alias de WorldEdit.
        for (String s : FormulaCommands.SUBS) SUBS.put(s, USE);
        for (String s : FormulaCommands.ALIASES.keySet()) SUBS.put(s, USE);
        // Spec 035: lo de FAWE para crear (como cualquier edición; el link de image pide además worldcraft.image.link).
        for (String s : CreateCommands.SUBS) SUBS.put(s, USE);
        for (String s : CreateCommands.ALIASES.keySet()) SUBS.put(s, USE);
        // Spec 032: el superpico, con su propio permiso (los pinceles y herramientas van con worldcraft.use).
        SUBS.put("superpickaxe", dev.leo.worldcraft.paper.Tools.SUPERPICKAXE);
        SUBS.put("sp", dev.leo.worldcraft.paper.Tools.SUPERPICKAXE);
        for (String s : List.of("reload", "validate", "selftest")) SUBS.put(s, ADMIN);
    }

    private static final Set<String> EDIT_FLAGS = Set.of("preview");
    /** Lo que se escribe después de /wc sel (spec 030: las formas, con los nombres de WorldEdit y FAWE). */
    static final List<String> SEL_WORDS = List.of("clear", "show", "hide", "cuboid", "extend", "poly", "ellipsoid", "sphere",
            "cyl", "convex", "magic", "box", "fuzzy", "hull", "polyhedral");

    private final Worldcraft plugin;
    private final BuildCommands build;
    private final NavigationCommands navigation;
    private final SelectionCommands selection;
    private final ToolCommands tool;
    private final NatureCommands nature;
    private final FormulaCommands formulas;
    private final CreateCommands create;

    public WorldcraftCommand(Worldcraft plugin) {
        this.plugin = plugin;
        this.build = new BuildCommands(plugin, this);
        this.navigation = new NavigationCommands(plugin);
        this.selection = new SelectionCommands(plugin, this);
        this.tool = new ToolCommands(plugin);
        this.nature = new NatureCommands(plugin, this);
        this.formulas = new FormulaCommands(plugin, this, nature);
        this.create = new CreateCommands(plugin, this);
    }

    /** Spec 032 H4 CA3: el pincel butcher (la obra de /wc butcher en otro punto). */
    public void butcherAt(Player p, World w, int x, int y, int z, int r, Set<String> flags) {
        build.butcherAt(p, w, x, y, z, r, flags);
    }

    /** Spec 032 H5 CA8: la brújula (jumpto con el clic izquierdo, thru con el derecho). */
    public void navigate(Player p, String sub) {
        navigation.run(p, sub, new String[]{sub});
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
                case "mountain" -> generate(s, a, Options.Tool.MOUNTAIN);
                case "island", "floating_island" -> generate(s, a, Options.Tool.ISLAND);
                case "reroll" -> plugin.terrain().reroll(s);
                case "brush" -> brush(s, a);
                case "tool" -> tool.tool(s, a);
                case "superpickaxe", "sp" -> tool.superpickaxe(s, a);
                case "styles" -> styles(s);
                case "schem", "schematic" -> schem(s, a);
                default -> {
                    if (NavigationCommands.SUBS.contains(sub)) navigation.run(s, sub, a);
                    else if (SelectionCommands.SUBS.contains(sub)) selection.run(s, sub, a);
                    else if (NatureCommands.SUBS.contains(sub)) nature.run(s, sub, a);
                    else if (FormulaCommands.handles(sub)) formulas.run(s, sub, a);
                    else if (CreateCommands.handles(sub)) create.run(s, sub, a);
                    else if (BuildCommands.SHAPES.contains(sub) || BuildCommands.REGION.contains(sub)
                            || BuildCommands.UTILITIES.contains(sub) || BuildCommands.ENTITIES.contains(sub)) build.run(s, sub, a);
                    else help(s, a);
                }
            }
        } catch (Usage u) {
            s.sendMessage(msg().get("usage", "usage", msg().raw("help." + helpKey(sub) + ".usage")));
        }
        return true;
    }

    /** El subcomando se usó mal: se muestra su forma. */
    static final class Usage extends RuntimeException {
        Usage() {
            super(null, null, false, false);
        }
    }

    /** floating_island usa la ayuda de island. */
    private static String helpKey(String sub) {
        if (FormulaCommands.ALIASES.containsKey(sub)) return FormulaCommands.canonical(sub);
        if (CreateCommands.ALIASES.containsKey(sub)) return CreateCommands.canonical(sub);
        return sub.equals("floating_island") ? "island" : sub.equals("schematic") ? "schem" : sub.equals("sp") ? "superpickaxe" : sub;
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
            String hk = helpKey(sub);
            s.sendMessage(msg().get("help-detail", "usage", msg().raw("help." + hk + ".usage"),
                    "text", msg().raw("help." + hk + ".text"), "example", msg().raw("help." + hk + ".example")));
            return;
        }
        s.sendMessage(msg().get("help-header"));
        for (String sub : visible(s)) {
            if (sub.equals("floating_island") || sub.equals("schematic") || sub.equals("sp") || FormulaCommands.ALIASES.containsKey(sub) || CreateCommands.ALIASES.containsKey(sub)) continue;
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

    /**
     * Pone una esquina y dice el tamaño (también la usa la varita, H1 CA2 y CA3). Spec 030: en otras formas, el punto o
     * el centro (which 1 = clic izquierdo, 2 = derecho, como WorldEdit); la mágica lee el mundo antes.
     */
    public void setCorner(CommandSender s, UUID owner, int which, World world, int x, int y, int z) {
        Session ses = edits().session(owner);
        String c = x + " " + y + " " + z;
        Selector.Kind kind = ses.selector().kind();
        if (kind == Selector.Kind.MAGIC) {
            ses.enter(world.getName());
            selection.magic(s, ses, which, world, x, y, z);
            return;
        }
        String what;
        try {
            what = ses.click(which, world.getName(), x, y, z, plugin.config().settings().maxPoints());
        } catch (IllegalArgumentException e) {
            s.sendMessage(msg().get("error", "message", e.getMessage()));
            return;
        }
        Region r = ses.region();
        if (kind == Selector.Kind.CUBOID) {
            if (r == null) s.sendMessage(msg().get("corner-set", "which", String.valueOf(which), "pos", c));
            else s.sendMessage(msg().get("corner-set-size", "which", String.valueOf(which), "pos", c, "size", r.size(),
                    "blocks", Edits.fmt(r.volume())));
            return;
        }
        s.sendMessage(msg().get("sel-point", "what", what, "pos", c, "kind", kind.id(), "size", r.size(),
                "blocks", Edits.fmt(r.volume())));
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
                Region r = ses.region();
                if (r == null) {
                    Selector.Kind k = ses.selector().kind();
                    s.sendMessage(k == Selector.Kind.CUBOID ? msg().get("no-selection") : msg().get("sel-kind-empty", "kind", k.id()));
                    return;
                }
                Box b = r.bounds();
                String from = b.minX() + " " + b.minY() + " " + b.minZ(), to = b.maxX() + " " + b.maxY() + " " + b.maxZ();
                if (ses.selector().kind() == Selector.Kind.CUBOID) {
                    s.sendMessage(msg().get("sel-info", "size", b.size(), "blocks", Edits.fmt(b.volume()), "world", ses.world(),
                            "from", from, "to", to));
                    return;
                }
                // Spec 030 H1 CA2: la forma, sus puntos y los bloques de verdad (no los de la caja).
                s.sendMessage(msg().get("sel-info-shape", "kind", ses.selector().kind().id(), "detail", ses.selector().describe(),
                        "blocks", Edits.fmt(r.volume()), "world", ses.world(), "size", b.size(), "from", from, "to", to));
            }
            default -> {
                // Spec 030 H1 CA1: /wc sel cuboid|extend|poly|ellipsoid|sphere|cyl|convex|magic
                Selector.Kind k = Selector.NAMES.get(what);
                if (k == null) {
                    s.sendMessage(msg().get("unknown-selection", "input", a[1], "hint", hintText(Issues.closest(what, SEL_WORDS))));
                    return;
                }
                ses.selector().kind(k);
                s.sendMessage(msg().get(switch (k) {
                    case CUBOID -> "sel-kind-cuboid";
                    case EXTEND -> "sel-kind-extend";
                    case POLY -> "sel-kind-poly";
                    case ELLIPSOID -> "sel-kind-ellipsoid";
                    case SPHERE -> "sel-kind-sphere";
                    case CYL -> "sel-kind-cyl";
                    case CONVEX -> "sel-kind-convex";
                    case MAGIC -> "sel-kind-magic";
                }));
            }
        }
    }

    private void resize(CommandSender s, String[] a, String sub) {
        Session ses = edits().session(Edits.owner(s));
        Region r = ses.region();
        if (r == null) {
            s.sendMessage(msg().get("no-selection"));
            return;
        }
        World world = Bukkit.getWorld(ses.world());
        if (!ses.selector().boxLike()) {
            shapeResize(s, a, sub, ses, world);
            return;
        }
        Box b = r.bounds();
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

    /**
     * Spec 030 H4 CA5: shift mueve cualquier forma; expand/contract up o down y expand vert cambian el alto de poly y cyl.
     * Lo demás se dice (una esfera no se agranda hacia un lado).
     */
    private void shapeResize(CommandSender s, String[] a, String sub, Session ses, World world) {
        Selector sel = ses.selector();
        try {
            if (a.length >= 2 && sub.equals("expand") && a[1].equalsIgnoreCase("vert")) {
                if (world == null) return;
                sel.heightTo(world.getMinHeight(), world.getMaxHeight() - 1);
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
                if (sub.equals("shift")) {
                    sel.shift(d.dx * n, d.dy * n, d.dz * n);
                } else if (d.dy == 0) {
                    throw new IllegalStateException(sel.kind().id());
                } else {
                    int k = sub.equals("expand") ? n : -n;
                    // contract up achica desde arriba; contract down, desde abajo
                    sel.height(d.dy < 0 ? k : 0, d.dy > 0 ? k : 0);
                }
            }
        } catch (IllegalStateException e) {
            s.sendMessage(msg().get("sel-not-for-shape", "kind", sel.kind().id(), "command", sub));
            return;
        } catch (IllegalArgumentException e) {
            s.sendMessage(msg().get("sel-empty"));
            return;
        }
        Region r = ses.region();
        if (world != null && r.clampY(world.getMinHeight(), world.getMaxHeight() - 1) == null) {
            s.sendMessage(msg().get("sel-outside"));
            return;
        }
        s.sendMessage(msg().get("sel-changed", "size", r.size(), "blocks", Edits.fmt(r.volume())));
    }

    // ------------------------------------------------------------------ operaciones

    /** La selección (cualquier forma, spec 030) dentro del mundo, o null (y lo dice). */
    Region selection(CommandSender s, Session ses) {
        Region b = ses.region();
        if (b == null) {
            s.sendMessage(msg().get("no-selection"));
            return null;
        }
        World w = Bukkit.getWorld(ses.world());
        if (w == null) {
            s.sendMessage(msg().get("unknown-world", "world", ses.world()));
            return null;
        }
        Region c = b.clampY(w.getMinHeight(), w.getMaxHeight() - 1);
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
        Region box = selection(s, ses);
        if (box == null) return;
        World world = Bukkit.getWorld(ses.world());
        Pattern pattern;
        Mask mask;
        try {
            pattern = plugin.config().parser().pattern(patternText);
            mask = maskText == null ? null : plugin.config().parser().maskNear(maskText);
        } catch (ParseException e) {
            s.sendMessage(msg().get("error", "message", e.getMessage()));
            return;
        }
        PaperBlocks blocks = edits().blocks();
        Pattern.Compiled pc = pattern.compile(blocks.table());
        Mask.Compiled mc = mask == null ? null : edits().compile(mask, world);
        long seed = ThreadLocalRandom.current().nextLong();
        List<Integer> placed = new ArrayList<>();
        for (String st : pattern.placedStates()) placed.add(blocks.table().intern(st));
        String kind = replace ? "replace" : shape == Shape.FILL ? "set" : shape == Shape.WALLS ? "walls" : "faces";
        String detail = replace ? mask.text() + " -> " + pattern.text() : pattern.text();
        boolean preview = args.flags.contains("preview");
        if (mask != null && mask.needsNeighbors()) {
            // Spec 029 H2 CA2: «above», «exposed»… se deciden con la foto de antes (la caja + 1), no con el mundo a medio hacer.
            readAround(s, world, box.bounds(), kind, snap -> edits().request(s, new Edits.Op(kind, detail, world, box.bounds(), box.count(shape),
                    () -> new RegionSource(box, shape, mc, pc, seed, snap::get), placed, null), preview));
            return;
        }
        edits().request(s, new Edits.Op(kind, detail, world, box.bounds(), box.count(shape),
                () -> new RegionSource(box, shape, mc, pc, seed), placed, null), preview);
    }

    /**
     * Spec 029: lee la caja con un borde de un bloque (dentro del alto del mundo) y da la foto para las máscaras que miran
     * alrededor. El tope de bloques del grupo se revisa antes de leer.
     */
    void readAround(CommandSender s, World world, Box box, String label, java.util.function.Consumer<dev.leo.worldcraft.edit.Snapshot> then) {
        Box around = box.grow(1).clampY(world.getMinHeight(), world.getMaxHeight() - 1);
        Limits.Resolved lim = edits().limits(s);
        if (around.volume() > lim.maxBlocks() || around.volume() > Integer.MAX_VALUE - 8) {
            s.sendMessage(msg().get("limit-blocks", "blocks", Edits.fmt(around.volume()), "max", Edits.fmt(lim.maxBlocks()),
                    "groups", String.join(", ", lim.groups())));
            return;
        }
        dev.leo.worldcraft.edit.Snapshot snap = new dev.leo.worldcraft.edit.Snapshot(around);
        edits().read(s, world, around, label, snap, () -> then.accept(snap));
    }

    private void copy(CommandSender s, String[] a, boolean cut) {
        Args args = Args.parse(a, 1, cut ? Set.of("preview", "mask", "entities", "biomes") : Set.of("mask", "entities", "biomes"));
        if (args.problem != null) {
            s.sendMessage(msg().get("error", "message", args.problem));
            return;
        }
        // Spec 029 H1 CA7: «--mask» toma todo lo demás (la máscara puede tener espacios: «stone above grass_block»).
        Mask mask = null;
        if (args.flags.contains("mask")) {
            if (args.join(0).isBlank()) throw new Usage();
            try {
                mask = plugin.config().parser().maskNear(args.join(0));
            } catch (ParseException e) {
                s.sendMessage(msg().get("error", "message", e.getMessage()));
                return;
            }
        } else if (!args.positional.isEmpty()) {
            throw new Usage();
        }
        Session ses = edits().session(Edits.owner(s));
        Region region = selection(s, ses);
        if (region == null) return;
        // Spec 030 H4 CA3: de una forma se copia solo lo de adentro (lo demás queda «sin leer» y no se pega).
        Box box = region.bounds();
        World world = Bukkit.getWorld(ses.world());
        Limits.Resolved lim = edits().limits(s);
        if (region.volume() > lim.maxBlocks() || box.volume() > Integer.MAX_VALUE - 8) {
            s.sendMessage(msg().get("limit-blocks", "blocks", Edits.fmt(region.volume()), "max", Edits.fmt(lim.maxBlocks()),
                    "groups", String.join(", ", lim.groups())));
            return;
        }
        int[] o = origin(s, box);
        // Spec 031 H1 CA1: entidades solo con --entities (como //copy -e de WorldEdit; decisión del dueño).
        boolean entities = args.flags.contains("entities");
        // Spec 033 H4 CA1: biomas solo con --biomes (como -b de WorldEdit; decisión del dueño).
        boolean biomes = args.flags.contains("biomes");
        if (mask == null && !cut) {
            edits().copy(s, world, region, o[0], o[1], o[2], entities, biomes, clip -> {
                s.sendMessage(msg().get("copied", "size", box.size(), "blocks", Edits.fmt(region.volume())));
                if (entities) s.sendMessage(msg().get("copied-entities", "count", Edits.fmt(clip.entities().size())));
                if (biomes) s.sendMessage(msg().get("copied-biomes", "count", Edits.fmt(clip.biomeNames().size())));
            });
            return;
        }
        PaperBlocks blocks = edits().blocks();
        Mask.Compiled mc = mask == null ? null : edits().compile(mask, world);
        long seed = ThreadLocalRandom.current().nextLong();
        int air = blocks.air();
        String detail = mask == null ? "" : "mask " + mask.text();
        java.util.function.Consumer<Mask.Neighbors> go = near -> {
            // Lo que no pasa la máscara queda como aire en el portapapeles (como -m de WorldEdit).
            if (!cut) {
                Clipboard clip = new Clipboard(box, o[0], o[1], o[2]);
                var scan = entities ? edits().scan(s, world, region) : null;
                edits().read(s, world, region, "copy", (x, y, z, st, tile) -> {
                    boolean in = mc.test(x, y, z, st, near, seed);
                    clip.put(box, x, y, z, in ? st : air, in ? tile : null);
                }, scan, () -> {
                    if (scan != null) clip.entities(Edits.relative(scan.result(), o[0], o[1], o[2]));
                    Runnable fin = () -> {
                        ses.clipboard(clip, world.getName());
                        s.sendMessage(msg().get("copied", "size", box.size(), "blocks", Edits.fmt(region.volume())));
                        if (scan != null) s.sendMessage(msg().get("copied-entities", "count", Edits.fmt(clip.entities().size())));
                        if (biomes) s.sendMessage(msg().get("copied-biomes", "count", Edits.fmt(clip.biomeNames().size())));
                    };
                    if (!biomes) {
                        fin.run();
                        return;
                    }
                    // Spec 033: los biomas, en otra lectura por celdas (copy --mask --biomes).
                    dev.leo.worldcraft.paper.PaperBiomes bw = new dev.leo.worldcraft.paper.PaperBiomes(plugin, world);
                    edits().readStage(s, world, new dev.leo.worldcraft.edit.EmptySource(box), "copy", Edits.readBiomes(bw, region, clip), () -> {
                        bw.close();
                        fin.run();
                    });
                });
                return;
            }
            Pattern.Compiled airP = Pattern.of("minecraft:air").compile(blocks.table());
            // cut --mask solo quita lo que pasa la máscara.
            Clipboard[] made = new Clipboard[1];
            Edits.Op op = new Edits.Op("cut", detail + (entities ? (detail.isEmpty() ? "" : ", ") + "with entities" : ""), world, box, region.volume(),
                    () -> new RegionSource(region, Shape.FILL, mc, airP, seed, near), List.of(), () -> {
                Clipboard clip = new Clipboard(box, o[0], o[1], o[2]);
                made[0] = clip;
                ses.clipboard(clip, world.getName());
                return (x, y, z, st, tile) -> {
                    boolean in = mc == null || mc.test(x, y, z, st, near, seed);
                    clip.put(box, x, y, z, in ? st : air, in ? tile : null);
                };
            });
            // Spec 031 H1 CA2: cut --entities las quita antes que los bloques (un marco sin pared se cae) y las deja en el portapapeles.
            // Spec 033 H4 CA1: cut --biomes los guarda en el portapapeles; el lugar conserva su bioma.
            if (biomes) op = op.with(new Edits.BiomeWork((bw, sink) -> Edits.readBiomes(bw, region, made[0]), ""));
            if (entities) op = op.with(new Edits.EntityWork(
                    (w, sink, max, b) -> new EntityStage.Scan(w, region, e -> true, true, max, sink),
                    (w, sink, max, b) -> EntityStage.action(() -> made[0].entities(
                            Edits.relative(((EntityStage.Scan) b).result(), o[0], o[1], o[2]))),
                    0, Set.of(), msg().text("note-entities-cut")));
            edits().request(s, op, args.flags.contains("preview"));
        };
        if (mask != null && mask.needsNeighbors()) readAround(s, world, box, cut ? "cut" : "copy", snap -> go.accept(snap::get));
        else go.accept(null);
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
        Args args = Args.parse(a, 1, Set.of("preview", "air", "origin", "select", "only-select", "no-entities", "remove-entities", "biomes"));
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
        if (args.flags.contains("origin")) {
            // Spec 029 H1 CA7: donde se copió (un esquema no lo sabe).
            o = clip.source();
            world = ses.clipboardWorld() == null ? null : Bukkit.getWorld(ses.clipboardWorld());
            if (o == null || world == null) {
                s.sendMessage(msg().get("paste-no-origin"));
                return;
            }
        } else if (s instanceof Player p) {
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
        if (args.flags.contains("only-select")) {
            // Como -n de WorldEdit: solo selecciona dónde quedaría, sin pegar.
            ses.selectBox(world.getName(), target);
            s.sendMessage(msg().get("sel-changed", "size", target.size(), "blocks", Edits.fmt(target.volume())));
            return;
        }
        PaperBlocks blocks = edits().blocks();
        boolean air = args.flags.contains("air");
        Edits.Op op = new Edits.Op("paste", clip.size() + (air ? " with air" : ""), world, target, clip.volume(),
                () -> new PasteSource(clip, o[0], o[1], o[2], air, blocks::isAir, blocks), clip.distinct(), null);
        if (args.flags.contains("select")) op = op.then(() -> ses.selectBox(world.getName(), target));
        // Spec 031 H1 CA3–CA4: las entidades del portapapeles van después de los bloques; --remove-entities quita antes las de ahí.
        List<dev.leo.worldcraft.entity.EntityCopy> ents = args.flags.contains("no-entities") ? List.of() : clip.entitiesAt(o[0], o[1], o[2]);
        boolean clear = args.flags.contains("remove-entities");
        if (!ents.isEmpty() || clear) {
            List<EntityStage.Item> items = ents.stream().map(e -> new EntityStage.Item(null, e)).toList();
            String note = (ents.isEmpty() ? "" : msg().text("note-entities-add", "count", Edits.fmt(ents.size())))
                    + (clear ? (ents.isEmpty() ? "" : " ") + msg().text("note-entities-clear") : "");
            op = op.with(new Edits.EntityWork(
                    clear ? (w, sink, max, b) -> new EntityStage.Scan(w, target, e -> true, true, max, sink) : null,
                    ents.isEmpty() ? null : (w, sink, max, b) -> new EntityStage.Spawn(w, () -> items, sink),
                    ents.size(), ents.isEmpty() ? Set.of() : clip.entityTypes(), note));
        }
        // Spec 033 H4 CA2: los biomas del portapapeles (girados con él), cada celda por el bloque de su centro.
        if (args.flags.contains("biomes")) {
            if (!clip.hasBiomes()) {
                s.sendMessage(msg().get("paste-no-biomes"));
                return;
            }
            long[] cells = dev.leo.worldcraft.biome.Cells.of(target);
            op = op.with(new Edits.BiomeWork((bw, sink) -> dev.leo.worldcraft.biome.BiomeStage.apply(bw, cells, (i, x, y, z, c) -> {
                int[] m = dev.leo.worldcraft.biome.Cells.center(x, y, z, target);
                return clip.biomeFor(m[0], m[1], m[2], o[0], o[1], o[2]);
            }, sink), msg().text("note-biomes")));
        }
        edits().request(s, op, args.flags.contains("preview"));
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
        // Spec 031 H7.
        s.sendMessage(msg().get("limits-entities", "max", Edits.fmt(l.maxEntities()), "blocked", l.blockedEntities().isEmpty() ? "none"
                : String.join(", ", l.blockedEntities().stream().map(t -> t.replace("minecraft:", "")).sorted().toList())));
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

    // ------------------------------------------------------------------ terreno (spec 026)

    /** Separa «clave=valor» de las palabras sueltas; las claves en minúsculas. */
    public static Map<String, String> keyValues(List<String> positional, List<String> words) {
        Map<String, String> kv = new LinkedHashMap<>();
        for (String p : positional) {
            int eq = p.indexOf('=');
            if (eq > 0) kv.put(p.substring(0, eq).toLowerCase(Locale.ROOT), p.substring(eq + 1));
            else words.add(p);
        }
        return kv;
    }

    private void generate(CommandSender s, String[] a, Options.Tool tool) {
        Args args = Args.parse(a, 1, Set.of("now"));
        if (args.problem != null) {
            s.sendMessage(msg().get("error", "message", args.problem));
            return;
        }
        List<String> words = new ArrayList<>();
        Map<String, String> kv = keyValues(args.positional, words);
        var cfg = plugin.config().terrain();
        try {
            Options.Resolved o = Options.resolve(tool, words, kv, cfg, cfg.maxRadius(), ThreadLocalRandom.current().nextLong(1, 1L << 40));
            if (tool == Options.Tool.MOUNTAIN) plugin.terrain().mountain(s, o, args.flags.contains("now"));
            else plugin.terrain().island(s, o, args.flags.contains("now"));
        } catch (IllegalArgumentException e) {
            s.sendMessage(msg().get("error", "message", e.getMessage()));
        }
    }

    private void brush(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        // Spec 032: los pinceles de construcción (y none, radius=, mask= sobre uno de ellos).
        if (this.tool.brush(p, a)) return;
        var tool = plugin.brush();
        var held = p.getInventory().getItemInMainHand();
        // Un pincel de construcción en la mano pasa a ser de terreno (el mismo ítem).
        boolean convert = plugin.tools().spec(held) != null;
        if (convert) plugin.tools().unbind(held);
        BrushSettings current = convert ? BrushSettings.DEFAULT : tool.settings(held);
        BrushSettings base = current != null ? current : BrushSettings.DEFAULT;
        List<String> words = new ArrayList<>();
        Map<String, String> kv = keyValues(List.of(a).subList(1, a.length), words);
        try {
            BrushSettings b = brushSettings(base, words, kv, plugin.config().terrain().brushMaxRadius(),
                    plugin.config().terrain().styles().keySet());
            if (b.pattern() != null) plugin.config().parser().pattern(b.pattern());
            if (b.mask() != null) plugin.config().parser().mask(b.mask());
            if (current != null) {
                tool.write(held, b);
                if (convert) {
                    s.sendMessage(msg().get("brush-given", "brush", b.describe()));
                    return;
                }
                s.sendMessage(msg().get("brush-updated", "brush", b.describe()));
            } else {
                p.getInventory().addItem(tool.create(b));
                s.sendMessage(msg().get("brush-given", "brush", b.describe()));
            }
        } catch (IllegalArgumentException | ParseException e) {
            s.sendMessage(msg().get("error", "message", e.getMessage()));
        }
    }

    private static final List<String> BRUSH_KEYS = List.of("mode", "radius", "strength", "style", "mask", "pattern");

    /**
     * Lo escrito tras /wc brush sobre unos ajustes: «[modo] [radio] [fuerza] [patrón…]» y mode=, radius=, strength=,
     * style=, mask=, pattern= (H3 CA1, H4 CA4). La fuerza va de 0.1 a 1 o en porcentaje (50%). Unos bloques sueltos
     * pasan el pincel a pintar.
     */
    public static BrushSettings brushSettings(BrushSettings base, List<String> words, Map<String, String> kv, int maxRadius,
                                       java.util.Collection<String> styles) {
        for (String k : kv.keySet()) {
            if (!BRUSH_KEYS.contains(k)) throw new IllegalArgumentException("Unknown option '" + k + "='" + Issues.hint(k, BRUSH_KEYS));
        }
        BrushSettings.Mode mode = base.mode();
        int radius = base.radius();
        double strength = base.strength();
        String style = base.style(), pattern = base.pattern(), mask = base.mask();
        List<String> rest = new ArrayList<>(words);
        int i = 0;
        boolean modeGiven = false;
        String m = kv.containsKey("mode") ? kv.get("mode") : !rest.isEmpty() && !isNumber(rest.get(0)) && BrushSettings.Mode.parse(rest.get(0)) != null ? rest.get(i++) : null;
        if (m == null && kv.isEmpty() && rest.size() == 1 && !isNumber(rest.get(0)) && !rest.get(0).contains("_") && Issues.closest(rest.get(0), BrushSettings.Mode.ids()) != null) {
            m = rest.get(i++); // «smoth»: se avisa, no se toma como un bloque
        }
        if (m != null) {
            mode = BrushSettings.Mode.parse(m);
            if (mode == null) {
                throw new IllegalArgumentException("Unknown brush mode '" + m + "'" + Issues.hint(m, BrushSettings.Mode.ids())
                        + ". Modes: " + String.join(", ", BrushSettings.Mode.ids()));
            }
            modeGiven = true;
        }
        String r = kv.containsKey("radius") ? kv.get("radius") : i < rest.size() && rest.get(i).matches("\\d+") ? rest.get(i++) : null;
        if (r != null) {
            try {
                radius = Integer.parseInt(r);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'radius=" + r + "' needs a whole number");
            }
            if (radius < 1 || radius > maxRadius) throw new IllegalArgumentException("radius " + radius + " is out of range (1.." + maxRadius + ")");
        }
        // «70% grass_block, 30% podzol»: un porcentaje seguido de un bloque es el peso de un patrón, no la fuerza.
        boolean weight = i + 1 < rest.size() && rest.get(i).endsWith("%") && !isNumber(rest.get(i + 1));
        String st = kv.containsKey("strength") ? kv.get("strength") : i < rest.size() && isNumber(rest.get(i)) && !weight ? rest.get(i++) : null;
        if (st != null) {
            try {
                strength = st.endsWith("%") ? Double.parseDouble(st.substring(0, st.length() - 1)) / 100 : Double.parseDouble(st);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'strength=" + st + "' needs a number from 0.1 to 1, or a percentage");
            }
            if (strength < 0.1 || strength > 1) throw new IllegalArgumentException("strength " + st + " is out of range (0.1..1, or 10%..100%)");
        }
        List<String> blocks = new ArrayList<>(rest.subList(i, rest.size()));
        if (kv.containsKey("pattern")) blocks.add(0, kv.get("pattern"));
        if (!blocks.isEmpty()) {
            if (!modeGiven) mode = BrushSettings.Mode.PAINT;
            if (mode != BrushSettings.Mode.PAINT) {
                throw new IllegalArgumentException("Only the paint mode takes blocks: /wc brush paint " + String.join(" ", blocks));
            }
            pattern = String.join(" ", blocks);
            style = null;
        }
        if (kv.containsKey("style")) {
            String sn = kv.get("style").toLowerCase(Locale.ROOT);
            if (!styles.contains(sn)) throw new IllegalArgumentException("Unknown style '" + sn + "'" + Issues.hint(sn, styles) + "; see /wc styles");
            style = sn;
            pattern = null;
        }
        if (kv.containsKey("mask")) mask = kv.get("mask").isBlank() || kv.get("mask").equals("*") ? null : kv.get("mask");
        return new BrushSettings(mode, radius, strength, style, pattern, mask);
    }

    private static boolean isNumber(String s) {
        return s.matches("\\d+(\\.\\d+)?%?") || s.matches("\\.\\d+");
    }

    /** /wc schem list|load|save|delete (spec 027) e import (spec 029 H5). */
    private void schem(CommandSender s, String[] a) {
        String op = a.length < 2 ? "" : a[1].toLowerCase(Locale.ROOT);
        Args args = Args.parse(a, 2, op.equals("save") || op.equals("import") ? Set.of("overwrite") : Set.of());
        if (args.problem != null) {
            s.sendMessage(msg().get("error", "message", args.problem));
            return;
        }
        Schematics sch = plugin.schematics();
        switch (op) {
            case "list" -> {
                int page = 1;
                String filter = null;
                for (String p : args.positional) {
                    if (p.matches("\\d{1,6}")) page = Integer.parseInt(p);
                    else filter = p;
                }
                sch.list(s, page, filter);
            }
            case "import" -> {
                // Spec 029 H5: copia los de WorldEdit y FAWE a la carpeta propia; escribe en el disco como save.
                if (!args.positional.isEmpty()) {
                    s.sendMessage(msg().get("usage", "usage", msg().raw("help.schem.usage")));
                    return;
                }
                boolean overwrite = args.flags.contains("overwrite");
                if (!s.hasPermission(Schematics.PERM_SAVE) || overwrite && !s.hasPermission(Schematics.PERM_DELETE)) {
                    s.sendMessage(msg().get("no-permission"));
                    return;
                }
                sch.importAll(s, overwrite);
            }
            case "load", "save", "delete" -> {
                if (args.positional.size() != 1) {
                    s.sendMessage(msg().get("usage", "usage", msg().raw("help.schem.usage")));
                    return;
                }
                String name = args.positional.get(0);
                if (op.equals("load")) sch.load(s, name);
                else if (op.equals("save") && !s.hasPermission(Schematics.PERM_SAVE)) s.sendMessage(msg().get("no-permission"));
                else if (op.equals("save")) sch.save(s, name, args.flags.contains("overwrite"));
                else if (!s.hasPermission(Schematics.PERM_DELETE)) s.sendMessage(msg().get("no-permission"));
                else sch.delete(s, name);
            }
            default -> s.sendMessage(msg().get(op.isEmpty() ? "usage" : "schem-unknown", "usage", msg().raw("help.schem.usage"),
                    "input", op, "hint", hintText(Issues.closest(op, List.of("list", "load", "save", "delete", "import")))));
        }
    }

    private void styles(CommandSender s) {
        var styles = plugin.config().terrain().styles();
        s.sendMessage(msg().get("styles-header", "count", String.valueOf(styles.size())));
        for (var st : styles.values()) s.sendMessage(msg().get("styles-line", "name", st.name(), "description", st.description()));
    }

    // ------------------------------------------------------------------ autocompletado

    /** Tamaños, estilos y claves («peaks=»); tras «style=», los estilos (H6 CA3). */
    private List<String> terrainSuggestions(Options.Tool tool, String last) {
        var styles = plugin.config().terrain().styles().keySet();
        List<String> out = new ArrayList<>();
        if (last.toLowerCase(Locale.ROOT).startsWith("style=")) {
            for (String st : styles) out.add("style=" + st);
            return filter(out, last);
        }
        out.addAll(Options.SIZES);
        out.addAll(styles);
        for (String k : Options.names(tool)) out.add(k + "=");
        return filter(out, last);
    }

    /** Spec 032: tras /wc brush o /wc tool &lt;tipo&gt;: sus opciones, números y bloques. */
    private List<String> toolSuggestions(String[] a, String last, boolean brush) {
        if (a.length == 2) {
            List<String> out = new ArrayList<>(dev.leo.worldcraft.tool.ToolKind.ids(brush));
            out.add("none");
            if (brush) out.addAll(BrushSettings.Mode.ids());
            return filter(out, last);
        }
        var kind = dev.leo.worldcraft.tool.ToolKind.parse(a[1], brush);
        if (kind == null) return List.of();
        if (last.toLowerCase(Locale.ROOT).startsWith("mask=")) {
            List<String> out = new ArrayList<>();
            for (String b : blockSuggestions(last.substring(5), true)) out.add(last.substring(0, 5) + b);
            return out;
        }
        if (last.startsWith("-")) return filter(ToolCommands.optionWords(kind), last);
        if (kind == dev.leo.worldcraft.tool.ToolKind.SET && a.length == 3) return filter(dev.leo.worldcraft.tool.ToolParser.SET_SHAPES, last);
        boolean number = !last.isEmpty() && Character.isDigit(last.charAt(0));
        boolean blocks = switch (kind) {
            case SPHERE, CYL, SET, SPLATTER, SCATTER, ROCK, REPL, FLOODFILL, LRBUILD -> true;
            default -> false;
        };
        List<String> out = new ArrayList<>();
        if (number || !blocks) out.addAll(filter(RADII, last));
        if (blocks && !number) out.addAll(blockSuggestions(last, false));
        if (!number) out.addAll(filter(ToolCommands.optionWords(kind), last));
        return out;
    }

    private List<String> brushSuggestions(String[] a, String last) {
        if (a.length == 2 || dev.leo.worldcraft.tool.ToolKind.parse(a[1], true) != null) return toolSuggestions(a, last, true);
        String l = last.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (l.startsWith("style=")) {
            for (String st : plugin.config().terrain().styles().keySet()) out.add("style=" + st);
            return filter(out, last);
        }
        if (l.startsWith("mask=")) {
            for (String b : blockSuggestions(last.substring(5), true)) out.add(last.substring(0, 5) + b);
            return out;
        }
        if (a.length == 2) return filter(BrushSettings.Mode.ids(), last);
        if (a.length == 3) return filter(List.of("3", "5", "8", "12"), last);
        if (a.length == 4) return filter(List.of("0.3", "0.5", "1"), last);
        out.addAll(List.of("style=", "mask="));
        if (a[1].equalsIgnoreCase("paint")) out.addAll(blockSuggestions(last, false));
        return filter(out, last);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length <= 1) return filter(visible(s), a.length == 0 ? "" : a[0]);
        String sub = a[0].toLowerCase(Locale.ROOT);
        String perm = SUBS.get(sub);
        if (perm == null || !s.hasPermission(perm)) return List.of();
        String last = a[a.length - 1];
        if (last.startsWith("--")) {
            Set<String> opts = switch (sub) {
                case "set", "replace", "walls", "faces" -> Set.of("--preview");
                case "cut" -> Set.of("--preview", "--mask", "--entities", "--biomes");
                case "copy" -> Set.of("--mask", "--entities", "--biomes");
                case "paste" -> Set.of("--preview", "--air", "--origin", "--select", "--only-select", "--no-entities", "--remove-entities", "--biomes");
                case "chunk" -> Set.of("--expand", "--chunk-coords");
                case "outset", "inset" -> Set.of("--horizontal", "--vertical");
                case "distr" -> Set.of("--states", "--clipboard");
                case "undo", "redo" -> Set.of("--player");
                case "pos1", "pos2" -> Set.of("--world");
                case "mountain", "island", "floating_island" -> Set.of("--now");
                case "biomeinfo" -> Set.of("--target", "--here");
                case "setbiome" -> Set.of("--here");
                case "forest", "forestgen", "flora", "pumpkins" -> Set.of("--preview");
                case "structure" -> Set.of("--palette");
                case "caves", "ore", "ores", "image", "img" -> {
                    Set<String> f = new HashSet<>();
                    for (String o : CreateCommands.flags(sub)) f.add("--" + o);
                    yield f;
                }
                case "generate", "gen", "generatebiome", "genbiome", "deform" -> {
                    Set<String> f = new HashSet<>();
                    for (String o : FormulaCommands.flags(sub)) f.add("--" + o);
                    yield f;
                }
                case "brush", "tool" -> {
                    var k = a.length > 2 ? dev.leo.worldcraft.tool.ToolKind.parse(a[1], sub.equals("brush")) : null;
                    Set<String> f = new HashSet<>();
                    if (k != null) for (String o : dev.leo.worldcraft.tool.ToolParser.flags(k)) f.add("--" + o);
                    yield f;
                }
                case "schem", "schematic" -> a.length > 1 && (a[1].equalsIgnoreCase("save") || a[1].equalsIgnoreCase("import")) && s.hasPermission(Schematics.PERM_DELETE)
                        ? Set.of("--overwrite") : Set.of();
                default -> {
                    Set<String> f = new HashSet<>();
                    if (!NavigationCommands.SUBS.contains(sub) && !SelectionCommands.SUBS.contains(sub) && !NatureCommands.SUBS.contains(sub) && !FormulaCommands.handles(sub) && !CreateCommands.handles(sub)) for (String o : BuildCommands.flags(sub)) f.add("--" + o);
                    yield f;
                }
            };
            return filter(new ArrayList<>(opts), last);
        }
        return switch (sub) {
            case "help" -> a.length == 2 ? filter(visible(s), last) : List.of();
            case "sel" -> a.length == 2 ? filter(SEL_WORDS, last) : List.of();
            case "expand", "contract", "shift" -> a.length == 2 ? filter(sub.equals("expand") ? List.of("1", "5", "10", "vert") : List.of("1", "5", "10"), last)
                    : a.length == 3 ? filter(Direction.NAMES, last) : List.of();
            case "flip" -> a.length == 2 ? filter(Direction.NAMES, last) : List.of();
            case "rotate" -> a.length == 2 ? filter(List.of("90", "180", "270", "-90"), last) : List.of();
            case "set", "walls", "faces" -> blockSuggestions(last, false);
            case "replace" -> blockSuggestions(last, !hasWith(a));
            case "history" -> a.length == 2 && s.hasPermission(MODERATE) ? filter(edits().history().names(), last) : List.of();
            case "jobs" -> a.length == 2 && s.hasPermission(MODERATE) ? filter(List.of("cancel"), last) : List.of();
            case "mountain" -> terrainSuggestions(Options.Tool.MOUNTAIN, last);
            case "island", "floating_island" -> terrainSuggestions(Options.Tool.ISLAND, last);
            case "brush" -> brushSuggestions(a, last);
            case "tool" -> toolSuggestions(a, last, false);
            case "superpickaxe", "sp" -> a.length == 2 ? filter(ToolCommands.SP_MODES, last)
                    : a.length == 3 && !a[1].equalsIgnoreCase("single") && !a[1].equalsIgnoreCase("off") ? filter(List.of("1", "2", "3", "5"), last) : List.of();
            case "schem", "schematic" -> schemSuggestions(s, a, last);
            // Spec 029
            case "count", "trim" -> blockSuggestions(last, true);
            case "copy", "cut" -> hasFlag(a, "--mask") ? blockSuggestions(last, true) : List.of();
            case "gmask" -> a.length == 2 ? merge(filter(List.of("off"), last), blockSuggestions(last, true)) : blockSuggestions(last, true);
            case "outset", "inset" -> a.length == 2 ? filter(List.of("1", "5", "10"), last) : List.of();
            case "biomelist", "biomeinfo", "setbiome", "forest", "forestgen", "flora", "pumpkins", "structure" -> nature.complete(sub, a, last);
            case "generate", "gen", "generatebiome", "genbiome", "timeout" -> formulas.complete(sub, a, last);
            case "caves", "ore", "ores", "image", "img" -> create.complete(sub, a, last);
            default -> buildSuggestions(sub, a, last);
        };
    }

    private static final List<String> RADII = List.of("3", "5", "10", "20");

    /** Spec 028: números, direcciones, bloques y máscaras donde va cada uno. */
    private List<String> buildSuggestions(String sub, String[] a, String last) {
        boolean number = last.isEmpty() || Character.isDigit(last.charAt(0)) || last.startsWith("-");
        if (BuildCommands.SHAPES.contains(sub)) return a.length > 2 && number ? filter(RADII, last) : blockSuggestions(last, false);
        return switch (sub) {
            case "stack" -> a.length == 2 ? filter(List.of("1", "2", "5", "10"), last) : a.length == 3 ? filter(Direction.NAMES, last) : List.of();
            case "move" -> a.length == 2 ? filter(List.of("1", "5", "10"), last) : a.length == 3 ? filter(Direction.NAMES, last) : blockSuggestions(last, false);
            case "overlay", "center", "line" -> blockSuggestions(last, false);
            case "hollow" -> a.length == 2 ? filter(List.of("1", "2", "3"), last) : blockSuggestions(last, false);
            case "smooth" -> a.length == 2 ? filter(List.of("1", "2", "3", "5"), last) : List.of();
            case "fill" -> a.length > 2 && number ? filter(RADII, last) : blockSuggestions(last, false);
            case "removenear" -> a.length > 2 && number ? filter(RADII, last) : blockSuggestions(last, true);
            case "replacenear" -> a.length == 2 ? filter(RADII, last) : blockSuggestions(last, !hasWith(a));
            case "removeabove", "removebelow" -> a.length <= 3 ? filter(List.of("1", "3", "5"), last) : List.of();
            case "drain", "fixwater", "fixlava", "extinguish", "snow", "thaw", "green", "butcher" -> a.length == 2 ? filter(RADII, last) : List.of();
            case "remove" -> a.length == 2 ? filter(dev.leo.worldcraft.entity.RemoveType.words(edits().blocks().entityTypes()), last)
                    : a.length == 3 ? filter(RADII, last) : List.of();
            case "ascend", "descend", "up", "ceil" -> a.length == 2 ? filter(List.of("1", "2", "5", "10"), last) : List.of();
            default -> List.of();
        };
    }

    private static boolean hasFlag(String[] a, String flag) {
        for (int i = 1; i < a.length - 1; i++) if (a[i].equalsIgnoreCase(flag)) return true;
        return false;
    }

    private static List<String> merge(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private List<String> schemSuggestions(CommandSender s, String[] a, String last) {
        if (a.length == 2) {
            List<String> ops = new ArrayList<>(List.of("list", "load"));
            // Spec 029 H5: guardar e importar escriben en el disco: permiso aparte.
            if (s.hasPermission(Schematics.PERM_SAVE)) ops.addAll(List.of("save", "import"));
            if (s.hasPermission(Schematics.PERM_DELETE)) ops.add("delete");
            return filter(ops, last);
        }
        if (a.length != 3) return List.of();
        return switch (a[1].toLowerCase(Locale.ROOT)) {
            case "load" -> filter(plugin.schematics().names(false), last);
            case "delete" -> s.hasPermission(Schematics.PERM_DELETE) ? filter(plugin.schematics().names(true), last) : List.of();
            case "save" -> s.hasPermission(Schematics.PERM_SAVE) ? filter(plugin.schematics().names(true), last) : List.of();
            default -> List.of();
        };
    }

    private static boolean hasWith(String[] a) {
        for (int i = 1; i < a.length - 1; i++) if (a[i].equalsIgnoreCase("with")) return true;
        return false;
    }

    /** Bloques para la última parte de una lista («70% stone, gra» → grass_block…), presets y, en máscaras, etiquetas. */
    List<String> blockSuggestions(String last, boolean mask) {
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
        // Spec 035: los patrones por color.
        if (!mask && q.startsWith("#")) {
            for (String w : dev.leo.worldcraft.pattern.Parser.COLOR_WORDS) {
                String n = "#" + w + (w.equals("darken") || w.equals("lighten") ? "" : "[");
                if (n.startsWith(q)) out.add(head + n);
            }
            return out;
        }
        Set<String> seen = new HashSet<>();
        if (mask && !q.isEmpty()) {
            for (String w : List.of("exposed", "surface", "solid", "above", "below")) if (w.startsWith(q)) out.add(head + neg + w);
        }
        for (String b : edits().blocks().blocks()) {
            String n = b.replace("minecraft:", "");
            if (n.startsWith(q) && seen.add(n) && out.size() < 60) out.add(head + neg + n);
        }
        if (q.isEmpty()) {
            for (String p : (mask ? plugin.config().maskPresets() : plugin.config().patternPresets()).keySet()) out.add(head + "$" + p);
        }
        return out;
    }

    static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }
}
