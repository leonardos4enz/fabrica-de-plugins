package dev.leo.worldcraft.command;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.biome.BiomeStage;
import dev.leo.worldcraft.biome.Cells;
import dev.leo.worldcraft.config.Issues;
import dev.leo.worldcraft.config.Limits;
import dev.leo.worldcraft.config.Messages;
import dev.leo.worldcraft.edit.EmptySource;
import dev.leo.worldcraft.nature.TreeKind;
import dev.leo.worldcraft.paper.Edits;
import dev.leo.worldcraft.paper.Nature;
import dev.leo.worldcraft.paper.PaperBiomes;
import dev.leo.worldcraft.paper.Session;
import dev.leo.worldcraft.paper.Terrain;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Region;
import dev.leo.worldcraft.structure.Templates;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spec 033 (plan §1.2, §1.4, §1.5): biomas (ver, cambiar), árboles, plantas y calabazas, y las estructuras del juego al
 * portapapeles. Todo lo que cambia el mundo es una obra del motor de la 025.
 */
final class NatureCommands {
    static final List<String> SUBS = List.of("biomelist", "biomeinfo", "setbiome", "forest", "forestgen", "flora", "pumpkins", "structure");
    private static final int PAGE = 15;

    private final Worldcraft plugin;
    private final WorldcraftCommand main;

    NatureCommands(Worldcraft plugin, WorldcraftCommand main) {
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
                case "biomelist" -> biomeList(s, a);
                case "biomeinfo" -> biomeInfo(s, a);
                case "setbiome" -> setBiome(s, a);
                case "forest", "flora" -> selectionNature(s, a, sub);
                case "forestgen", "pumpkins" -> aroundNature(s, a, sub);
                case "structure" -> structure(s, a);
                default -> throw new WorldcraftCommand.Usage();
            }
        } catch (IllegalArgumentException e) {
            s.sendMessage(msg().get("error", "message", e.getMessage()));
        }
    }

    /** -t y -p de WorldEdit como --target y --here. */
    private static String[] aliases(String[] a) {
        String[] out = a.clone();
        for (int i = 1; i < out.length; i++) {
            if (out[i].equals("-t")) out[i] = "--target";
            else if (out[i].equals("-p")) out[i] = "--here";
        }
        return out;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        s.sendMessage(msg().get("players-only"));
        return null;
    }

    // ------------------------------------------------------------------ biomas (H1, H2)

    /** H1 CA1: /wc biomelist [página] [filtro]. */
    private void biomeList(CommandSender s, String[] a) {
        int page = 1;
        String filter = "";
        for (int i = 1; i < a.length; i++) {
            if (a[i].matches("\\d+")) page = Integer.parseInt(a[i]);
            else filter = a[i].toLowerCase(Locale.ROOT);
        }
        List<String> all = new ArrayList<>();
        for (String b : PaperBiomes.names()) {
            String shown = b.startsWith("minecraft:") ? b.substring(10) : b;
            if (shown.contains(filter.replace("minecraft:", ""))) all.add(shown);
        }
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        page = Math.max(1, Math.min(page, pages));
        s.sendMessage(msg().get("biomelist-header", "count", String.valueOf(all.size()), "page", String.valueOf(page),
                "pages", String.valueOf(pages)));
        for (String b : all.subList((page - 1) * PAGE, Math.min(all.size(), page * PAGE))) s.sendMessage(msg().get("biomelist-line", "biome", b));
    }

    /** H1 CA2: los biomas de la selección (por celdas, con el % de sus bloques), del bloque que se mira o de los pies. */
    private void biomeInfo(CommandSender s, String[] a) {
        Args args = Args.parse(aliases(a), 1, Set.of("target", "here"));
        if (args.problem != null) throw new IllegalArgumentException(args.problem);
        if (!args.positional.isEmpty()) throw new WorldcraftCommand.Usage();
        if (args.flags.contains("target") || args.flags.contains("here")) {
            Player p = player(s);
            if (p == null) return;
            int x, y, z;
            if (args.flags.contains("target")) {
                RayTraceResult hit = Terrain.look(p, plugin.config().tools().range());
                if (hit == null || hit.getHitBlock() == null) {
                    s.sendMessage(msg().get("tool-no-target", "range", String.valueOf(plugin.config().tools().range())));
                    return;
                }
                x = hit.getHitBlock().getX();
                y = hit.getHitBlock().getY();
                z = hit.getHitBlock().getZ();
            } else {
                Location l = p.getLocation();
                x = l.getBlockX();
                y = l.getBlockY();
                z = l.getBlockZ();
            }
            s.sendMessage(msg().get("biomeinfo-one", "pos", x + " " + y + " " + z, "biome", short_(PaperBiomes.at(p.getWorld(), x, y, z))));
            return;
        }
        Session ses = edits().session(Edits.owner(s));
        Region region = readable(s, ses);
        if (region == null) return;
        World w = Bukkit.getWorld(ses.world());
        Map<String, Long> counts = new TreeMap<>();
        PaperBiomes bw = new PaperBiomes(plugin, w);
        Box box = region.bounds();
        BiomeStage st = BiomeStage.read(bw, Cells.of(region), (x, y, z, biome) -> {
            long n = 0;
            for (int dx = 0; dx < 4; dx++) for (int dy = 0; dy < 4; dy++) for (int dz = 0; dz < 4; dz++) {
                if (box.contains(x + dx, y + dy, z + dz) && region.contains(x + dx, y + dy, z + dz)) n++;
            }
            if (n > 0) counts.merge(biome, n, Long::sum);
        });
        edits().readStage(s, w, new EmptySource(box), "biomeinfo", st, () -> {
            bw.close();
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            s.sendMessage(msg().get("biomeinfo-header", "count", String.valueOf(counts.size()), "blocks", Edits.fmt(total)));
            counts.entrySet().stream().sorted((x, y) -> Long.compare(y.getValue(), x.getValue())).limit(10).forEach(e ->
                    s.sendMessage(msg().get("biomeinfo-line", "biome", short_(e.getKey()),
                            "percent", String.format(Locale.ROOT, "%.1f", 100.0 * e.getValue() / Math.max(1, total)))));
        });
    }

    private static String short_(String biome) {
        return biome.startsWith("minecraft:") ? biome.substring(10) : biome;
    }

    /** El bioma que escribió alguien, o un error con «did you mean». */
    private String biome(String name) {
        String id = plugin.config().parser().biome(name);
        if (id == null) throw new IllegalArgumentException("Unknown biome '" + name + "'" + plugin.config().parser().biomeHint(name)
                + ". See /wc biomelist");
        return id;
    }

    /** H2: /wc setbiome &lt;bioma&gt; [--here]: una obra sin bloques, por celdas de 4×4×4. */
    private void setBiome(CommandSender s, String[] a) {
        Args args = Args.parse(aliases(a), 1, Set.of("here"));
        if (args.problem != null) throw new IllegalArgumentException(args.problem);
        if (args.positional.size() != 1) throw new WorldcraftCommand.Usage();
        String biome = biome(args.positional.get(0));
        Session ses = edits().session(Edits.owner(s));
        Region region;
        World w;
        if (args.flags.contains("here")) {
            Player p = player(s);
            if (p == null) return;
            Location l = p.getLocation();
            region = new Box(l.getBlockX(), l.getBlockY(), l.getBlockZ(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
            w = p.getWorld();
        } else {
            region = main.selection(s, ses);
            if (region == null) return;
            w = Bukkit.getWorld(ses.world());
        }
        long[] cells = Cells.of(region);
        Box bounds = region.bounds();
        // H2 CA3: con la máscara global, la celda cambia si su bloque central pasa.
        Mask gmask = ses.gmask;
        Mask.Compiled gm = gmask == null ? null : edits().compile(gmask, w);
        long seed = ThreadLocalRandom.current().nextLong();
        var blocks = edits().blocks();
        World world = w;
        BiomeStage.Step pick = gm == null ? (i, x, y, z, c) -> biome : (i, x, y, z, c) -> {
            int[] m = Cells.center(x, y, z, bounds);
            int st = blocks.id(world.getBlockData(m[0], m[1], m[2]));
            return gm.test(m[0], m[1], m[2], st, null, seed) ? biome : null;
        };
        s.sendMessage(msg().get("setbiome-cells", "cells", Edits.fmt(cells.length), "biome", short_(biome)));
        Edits.Op op = new Edits.Op("setbiome", short_(biome), w, bounds, region.volume(), () -> new EmptySource(bounds), List.of(), null)
                .with(new Edits.BiomeWork((bw, sink) -> BiomeStage.apply(bw, cells, pick, sink), ""));
        edits().request(s, op, false);
    }

    private Region readable(CommandSender s, Session ses) {
        Region box = main.selection(s, ses);
        if (box == null) return null;
        Limits.Resolved lim = edits().limits(s);
        if (box.volume() > lim.maxBlocks()) {
            s.sendMessage(msg().get("limit-blocks", "blocks", Edits.fmt(box.volume()), "max", Edits.fmt(lim.maxBlocks()),
                    "groups", String.join(", ", lim.groups())));
            return null;
        }
        return box;
    }

    // ------------------------------------------------------------------ árboles, plantas y calabazas (H5, H6)

    /** Un número de densidad en %; con decimales menores que 1, una fracción (0.02 = 2 %, como WorldEdit en pumpkins). */
    static double density(String text) {
        double d;
        try {
            d = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a density: write a % from 0 to 100, like 5");
        }
        if (text.contains(".") && d > 0 && d < 1) d *= 100;
        if (d < 0 || d > 100) throw new IllegalArgumentException("The density is a % from 0 to 100 ('" + text + "')");
        return d;
    }

    private static boolean number(String s) {
        return s.matches("\\d+(\\.\\d+)?");
    }

    /** H5 CA2 y H6 CA1: /wc forest [tipo] [densidad] y /wc flora [densidad] en la selección. */
    private void selectionNature(CommandSender s, String[] a, String sub) {
        Args args = Args.parse(a, 1, Set.of("preview"));
        if (args.problem != null) throw new IllegalArgumentException(args.problem);
        TreeKind kind = TreeKind.OAK;
        Double dens = null;
        for (String w : args.positional) {
            if (number(w) && dens == null) dens = density(w);
            else if (sub.equals("forest") && !number(w)) kind = TreeKind.parse(w);
            else throw new WorldcraftCommand.Usage();
        }
        Session ses = edits().session(Edits.owner(s));
        Region region = main.selection(s, ses);
        if (region == null) return;
        World w = Bukkit.getWorld(ses.world());
        boolean preview = args.flags.contains("preview");
        if (sub.equals("flora")) {
            new Nature(plugin).flora(s, w, region, dens == null ? 10 : dens, preview);
            return;
        }
        double d = dens == null ? 5 : dens;
        new Nature(plugin).forest(s, w, region, kind, d, "forest", kind.id() + " " + fmt(d) + "%", preview, () -> {
        });
    }

    /** H5 CA3 y H6 CA2: /wc forestgen [tamaño] [tipo] [densidad] y /wc pumpkins [tamaño] [densidad] alrededor del jugador. */
    private void aroundNature(CommandSender s, String[] a, String sub) {
        Player p = player(s);
        if (p == null) return;
        Args args = Args.parse(a, 1, Set.of("preview"));
        if (args.problem != null) throw new IllegalArgumentException(args.problem);
        Integer size = null;
        Double dens = null;
        TreeKind kind = TreeKind.OAK;
        for (String w : args.positional) {
            if (w.matches("\\d+") && size == null) size = Integer.parseInt(w);
            else if (number(w) && dens == null) dens = density(w);
            else if (sub.equals("forestgen") && !number(w)) kind = TreeKind.parse(w);
            else throw new WorldcraftCommand.Usage();
        }
        int n = size == null ? 10 : size, max = plugin.config().nature().maxSize();
        if (n < 1 || n > max) throw new IllegalArgumentException("The size " + n + " is out of range (1.." + max + ", nature.max_size)");
        Location l = p.getLocation();
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        World w = p.getWorld();
        Box box = new Box(x - n, y - n, z - n, x + n, y + n, z + n).clampY(w.getMinHeight(), w.getMaxHeight() - 1);
        if (box == null) {
            s.sendMessage(msg().get("sel-outside"));
            return;
        }
        boolean preview = args.flags.contains("preview");
        if (sub.equals("pumpkins")) {
            new Nature(plugin).pumpkins(s, w, box, dens == null ? 2 : dens, preview);
            return;
        }
        double d = dens == null ? 5 : dens;
        new Nature(plugin).forest(s, w, box, kind, d, "forestgen", kind.id() + " " + fmt(d) + "% size " + n, preview, () -> {
        });
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.1f", d);
    }

    // ------------------------------------------------------------------ estructuras (H7)

    /** /wc structure list [filtro] [página] | load &lt;nombre&gt; [--palette N]. */
    private void structure(CommandSender s, String[] a) {
        if (a.length < 2) throw new WorldcraftCommand.Usage();
        String what = a[1].toLowerCase(Locale.ROOT);
        switch (what) {
            case "list" -> {
                int page = 1;
                String filter = "";
                for (int i = 2; i < a.length; i++) {
                    if (a[i].matches("\\d+")) page = Integer.parseInt(a[i]);
                    else filter = a[i];
                }
                List<String> all = Templates.filter(plugin.structures().all(), filter);
                int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
                page = Math.max(1, Math.min(page, pages));
                s.sendMessage(msg().get("structure-list-header", "count", String.valueOf(all.size()), "page", String.valueOf(page),
                        "pages", String.valueOf(pages)));
                for (String n : all.subList((page - 1) * PAGE, Math.min(all.size(), page * PAGE))) s.sendMessage(msg().get("structure-list-line", "name", n));
            }
            case "load" -> {
                Args args = Args.parse(a, 2, Set.of("palette"));
                if (args.problem != null) throw new IllegalArgumentException(args.problem);
                if (args.positional.size() != 1) throw new WorldcraftCommand.Usage();
                int palette = 0;
                if (args.values.containsKey("palette")) {
                    try {
                        palette = Integer.parseInt(args.values.get("palette"));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("'--palette " + args.values.get("palette") + "' needs a whole number");
                    }
                }
                plugin.structures().load(s, args.positional.get(0), palette);
            }
            default -> s.sendMessage(msg().get("error", "message", "Unknown '" + a[1] + "'" + Issues.hint(what, List.of("list", "load"))
                    + ": /wc structure list or /wc structure load <name>"));
        }
    }

    // ------------------------------------------------------------------ autocompletado

    List<String> complete(String sub, String[] a, String last) {
        return switch (sub) {
            case "biomelist" -> List.of();
            case "biomeinfo" -> a.length == 2 ? WorldcraftCommand.filter(List.of("--target", "--here"), last) : List.of();
            case "setbiome" -> a.length == 2 ? WorldcraftCommand.filter(shortBiomes(), last) : WorldcraftCommand.filter(List.of("--here"), last);
            case "forest" -> a.length == 2 ? WorldcraftCommand.filter(TreeKind.ids(), last) : a.length == 3 ? WorldcraftCommand.filter(List.of("5", "10", "20"), last) : List.of();
            case "forestgen" -> a.length == 2 ? WorldcraftCommand.filter(List.of("10", "20"), last) : a.length == 3 ? WorldcraftCommand.filter(TreeKind.ids(), last) : List.of();
            case "flora" -> a.length == 2 ? WorldcraftCommand.filter(List.of("10", "20", "50"), last) : List.of();
            case "pumpkins" -> a.length == 2 ? WorldcraftCommand.filter(List.of("10", "20"), last) : List.of();
            case "structure" -> a.length == 2 ? WorldcraftCommand.filter(List.of("list", "load"), last)
                    : a.length == 3 && a[1].equalsIgnoreCase("load") ? WorldcraftCommand.filter(Templates.filter(plugin.structures().all(), ""), last) : List.of();
            default -> List.of();
        };
    }

    static List<String> shortBiomes() {
        List<String> out = new ArrayList<>();
        for (String b : PaperBiomes.names()) out.add(short_(b));
        return out;
    }

}
