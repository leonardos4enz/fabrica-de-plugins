package dev.leo.worldcraft.command;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.biome.Cells;
import dev.leo.worldcraft.config.Limits;
import dev.leo.worldcraft.config.Messages;
import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.formula.Formula;
import dev.leo.worldcraft.formula.Shapes;
import dev.leo.worldcraft.formula.Space;
import dev.leo.worldcraft.paper.Edits;
import dev.leo.worldcraft.paper.Session;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Region;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spec 034 (plan §1.3): las fórmulas. generate, deform y generatebiome calculan en el hilo de fórmulas
 * ({@link dev.leo.worldcraft.paper.Formulas}) y después piden una obra al motor de la 025; calc y timeout solo hablan.
 */
final class FormulaCommands {
    static final List<String> SUBS = List.of("generate", "generatebiome", "deform", "calc", "timeout");
    /** Los alias de WorldEdit (sin los de una letra). */
    static final Map<String, String> ALIASES = Map.of("gen", "generate", "genbiome", "generatebiome", "calculate", "calc", "eval", "calc");
    private static final List<String> XYZ = List.of("x", "y", "z");

    private final Worldcraft plugin;
    private final WorldcraftCommand main;
    private final NatureCommands nature;

    FormulaCommands(Worldcraft plugin, WorldcraftCommand main, NatureCommands nature) {
        this.plugin = plugin;
        this.main = main;
        this.nature = nature;
    }

    static boolean handles(String sub) {
        return SUBS.contains(sub) || ALIASES.containsKey(sub);
    }

    static String canonical(String sub) {
        return ALIASES.getOrDefault(sub, sub);
    }

    /** Las opciones de cada uno (para Args y el autocompletado). */
    static Set<String> flags(String sub) {
        return switch (canonical(sub)) {
            case "generate" -> Set.of("hollow", "raw", "offset", "center", "preview");
            case "generatebiome" -> Set.of("hollow", "raw", "offset", "center");
            case "deform" -> Set.of("raw", "offset", "center", "preview");
            default -> Set.of();
        };
    }

    /** -h, -r, -o y -c de WorldEdit como --hollow, --raw, --offset y --center. */
    private static String[] aliases(String[] a) {
        String[] out = a.clone();
        for (int i = 1; i < out.length; i++) {
            switch (out[i]) {
                case "-h" -> out[i] = "--hollow";
                case "-r" -> out[i] = "--raw";
                case "-o" -> out[i] = "--offset";
                case "-c" -> out[i] = "--center";
                default -> {
                }
            }
        }
        return out;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private Edits edits() {
        return plugin.edits();
    }

    void run(CommandSender s, String sub, String[] a) {
        String c = canonical(sub);
        try {
            switch (c) {
                case "calc" -> calc(s, a);
                case "timeout" -> timeout(s, a);
                default -> {
                    Args args = Args.parse(aliases(a), 1, flags(c));
                    if (args.problem != null) throw new IllegalArgumentException(args.problem);
                    switch (c) {
                        case "generate" -> generate(s, args);
                        case "generatebiome" -> generateBiome(s, args);
                        default -> deform(s, args);
                    }
                }
            }
        } catch (ParseException | IllegalArgumentException e) {
            s.sendMessage(msg().get("error", "message", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ ayudas

    /** --raw, --offset o --center (uno solo); --offset desde los pies de quien lo pide. */
    private Space space(CommandSender s, Args args, Region region) {
        List<Space.Mode> picked = new ArrayList<>();
        for (Space.Mode m : List.of(Space.Mode.RAW, Space.Mode.OFFSET, Space.Mode.CENTER)) if (args.flags.contains(m.flag())) picked.add(m);
        if (picked.size() > 1) throw new IllegalArgumentException("Choose one of --raw, --offset and --center");
        Space.Mode mode = picked.isEmpty() ? Space.Mode.NORMAL : picked.get(0);
        int[] from = {0, 0, 0};
        if (mode == Space.Mode.OFFSET) {
            if (!(s instanceof Player p)) throw new IllegalArgumentException("--offset counts from where you stand: use it in the game");
            from = new int[]{p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ()};
        }
        return Space.of(mode, region.bounds(), from);
    }

    /** ¿Lo que se evalúa pasa del tope del grupo? (lo dice) */
    private boolean overLimit(CommandSender s, long blocks) {
        Limits.Resolved lim = edits().limits(s);
        if (blocks <= lim.maxBlocks()) return false;
        s.sendMessage(msg().get("limit-blocks", "blocks", Edits.fmt(blocks), "max", Edits.fmt(lim.maxBlocks()),
                "groups", String.join(", ", lim.groups())));
        return true;
    }

    private Formula formula(String text, List<String> inputs) throws ParseException {
        if (text.isBlank()) throw new WorldcraftCommand.Usage();
        return plugin.config().parser().formula(text, inputs);
    }

    private static String mode(Args args) {
        StringBuilder sb = new StringBuilder();
        for (String f : List.of("hollow", "raw", "offset", "center")) if (args.flags.contains(f)) sb.append(" --").append(f);
        return sb.toString();
    }

    // ------------------------------------------------------------------ H2 generate

    private void generate(CommandSender s, Args args) throws ParseException {
        if (args.positional.size() < 2) throw new WorldcraftCommand.Usage();
        Pattern pattern = plugin.config().parser().pattern(args.positional.get(0));
        Formula f = formula(args.join(1), XYZ);
        Session ses = edits().session(Edits.owner(s));
        Region region = main.selection(s, ses);
        if (region == null) return;
        World w = Bukkit.getWorld(ses.world());
        if (overLimit(s, region.volume())) return;
        Space sp = space(s, args, region);
        boolean hollow = args.flags.contains("hollow");
        Pattern.Compiled pc = pattern.compile(edits().blocks().table());
        List<Integer> placed = new ArrayList<>();
        for (String st : pattern.placedStates()) placed.add(edits().blocks().table().intern(st));
        long seed = ThreadLocalRandom.current().nextLong();
        Shapes.Work work = plugin.formulas().work(s);
        s.sendMessage(msg().get("formula-calculating", "blocks", Edits.fmt(region.volume())));
        plugin.formulas().compute(s, work, region.volume(), () -> Shapes.generate(region, f, sp, hollow, seed, work), pts -> {
            if (pts.length == 0) {
                s.sendMessage(msg().get("formula-nothing"));
                return;
            }
            PointsSource.Pick pick = (x, y, z, c) -> pc.apply(x, y, z, c, seed);
            PointsSource probe = new PointsSource(pts, pick);
            edits().request(s, new Edits.Op("generate", pattern.text() + " " + f.text() + mode(args), w, probe.bounds(), probe.volume(),
                    () -> new PointsSource(pts, pick), placed, null), args.flags.contains("preview"));
        }, null);
    }

    // ------------------------------------------------------------------ H3 deform

    private void deform(CommandSender s, Args args) throws ParseException {
        Formula f = formula(args.join(0), XYZ);
        Session ses = edits().session(Edits.owner(s));
        Region region = main.selection(s, ses);
        if (region == null) return;
        World w = Bukkit.getWorld(ses.world());
        if (overLimit(s, region.volume())) return;
        Space sp = space(s, args, region);
        s.sendMessage(msg().get("formula-calculating", "blocks", Edits.fmt(region.volume())));
        plugin.formulas().deform(s, w, region, f, sp, "deform", prep -> edits().request(s, new Edits.Op("deform", f.text() + mode(args), w,
                prep.bounds(), prep.volume(), prep.source(), prep.placed(), null), args.flags.contains("preview")), null);
    }

    // ------------------------------------------------------------------ H4 generatebiome

    private void generateBiome(CommandSender s, Args args) throws ParseException {
        if (args.positional.size() < 2) throw new WorldcraftCommand.Usage();
        String biome = nature.biome(args.positional.get(0));
        Formula f = formula(args.join(1), XYZ);
        Session ses = edits().session(Edits.owner(s));
        Region region = main.selection(s, ses);
        if (region == null) return;
        World w = Bukkit.getWorld(ses.world());
        if (overLimit(s, region.volume())) return;
        Space sp = space(s, args, region);
        long[] cells = Cells.of(region);
        long seed = ThreadLocalRandom.current().nextLong();
        Shapes.Work work = plugin.formulas().work(s);
        boolean hollow = args.flags.contains("hollow");
        plugin.formulas().compute(s, work, cells.length, () -> Shapes.cells(cells, f, sp, hollow, seed, work), picked -> {
            if (picked.length == 0) {
                s.sendMessage(msg().get("formula-nothing"));
                return;
            }
            s.sendMessage(msg().get("generatebiome-cells", "cells", Edits.fmt(picked.length), "total", Edits.fmt(cells.length),
                    "biome", NatureCommands.short_(biome)));
            edits().request(s, nature.biomeOp(s, w, region, picked, biome, "generatebiome",
                    NatureCommands.short_(biome) + " " + f.text() + mode(args)), false);
        }, null);
    }

    // ------------------------------------------------------------------ H6 calc y timeout

    private void calc(CommandSender s, String[] a) throws ParseException {
        Formula f = formula(String.join(" ", Arrays.asList(a).subList(1, a.length)), List.of());
        Shapes.Work work = plugin.formulas().work(s);
        long seed = ThreadLocalRandom.current().nextLong();
        plugin.formulas().compute(s, work, 1, () -> f.run(seed).deadline(work.deadline).eval(),
                v -> s.sendMessage(msg().get("calc-result", "formula", f.text(), "value", number(v))), null);
    }

    /** Un entero sin decimales; si no, 10 cifras (como mucho). */
    static String number(double v) {
        if (Double.isNaN(v)) return "NaN";
        if (Double.isInfinite(v)) return v > 0 ? "Infinity" : "-Infinity";
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        BigDecimal b = new BigDecimal(v).round(new MathContext(10)).stripTrailingZeros();
        double abs = Math.abs(v);
        return abs >= 1e-6 && abs < 1e15 ? b.toPlainString() : b.toString();
    }

    private void timeout(CommandSender s, String[] a) {
        var fs = plugin.config().formulas();
        Session ses = edits().session(Edits.owner(s));
        if (a.length < 2) {
            s.sendMessage(msg().get("timeout-current", "ms", String.valueOf(plugin.formulas().timeoutMs(s)),
                    "default", String.valueOf(fs.timeoutMs()), "max", String.valueOf(fs.maxTimeoutMs())));
            return;
        }
        if (a.length > 2) throw new WorldcraftCommand.Usage();
        String v = a[1].toLowerCase(Locale.ROOT);
        if (v.equals("default")) {
            ses.formulaTimeout = -1;
        } else {
            int ms;
            try {
                ms = Integer.parseInt(v.endsWith("ms") ? v.substring(0, v.length() - 2) : v);
            } catch (NumberFormatException e) {
                throw new WorldcraftCommand.Usage();
            }
            if (ms < 1 || ms > fs.maxTimeoutMs()) {
                throw new IllegalArgumentException("The timeout must be 1 to " + fs.maxTimeoutMs() + " ms (formulas.max_timeout_ms)");
            }
            ses.formulaTimeout = ms;
        }
        s.sendMessage(msg().get("timeout-set", "ms", String.valueOf(plugin.formulas().timeoutMs(s))));
    }

    // ------------------------------------------------------------------ autocompletado

    List<String> complete(String sub, String[] a, String last) {
        String c = canonical(sub);
        long words = Arrays.stream(a).skip(1).filter(w -> !w.startsWith("-")).count();
        return switch (c) {
            case "generate" -> words <= 1 ? main.blockSuggestions(last, false) : List.of();
            case "generatebiome" -> words <= 1 ? WorldcraftCommand.filter(NatureCommands.shortBiomes(), last) : List.of();
            case "timeout" -> a.length == 2 ? WorldcraftCommand.filter(List.of("default", "1000", "5000"), last) : List.of();
            default -> List.of();
        };
    }
}
