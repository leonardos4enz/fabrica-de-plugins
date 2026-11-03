package dev.leo.worldcraft.command;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.api.event.WorldcraftAllowedRegionEvent;
import dev.leo.worldcraft.color.BlockColors;
import dev.leo.worldcraft.config.Issues;
import dev.leo.worldcraft.config.Messages;
import dev.leo.worldcraft.edit.Clipboard;
import dev.leo.worldcraft.edit.Flood;
import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.edit.Snapshot;
import dev.leo.worldcraft.edit.Transforms;
import dev.leo.worldcraft.paper.Edits;
import dev.leo.worldcraft.paper.Nms;
import dev.leo.worldcraft.paper.PaperBlocks;
import dev.leo.worldcraft.paper.Session;
import dev.leo.worldcraft.pattern.Mask;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Region;
import dev.leo.worldcraft.tool.Scroll;
import dev.leo.worldcraft.tool.ToolSpec;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntPredicate;

/**
 * Spec 041: lo que FAWE 2.16.2 le agrega a WorldEdit y Worldcraft no tenía. Lo global (gtexture, gsmask, gtransform), el
 * pincel de la mano (smask, transform, target, targetoffset, scroll, primary, secondary) y sueltos (place, fall,
 * fixblocks, nbtinfo, wea, wer, registry, fixlighting, debugpaste). Lo que cambia bloques termina en una obra del motor.
 */
final class FaweCommands {
    static final List<String> SUBS = List.of("gtexture", "gsmask", "gtransform", "smask", "transform", "target", "targetoffset", "scroll",
            "primary", "secondary", "place", "fall", "fixblocks", "nbtinfo", "wea", "wer", "registry", "fixlighting", "debugpaste");
    /** Los registros de /wc registry (y feature, del puente NMS). */
    static final List<String> REGISTRIES = List.of("block", "item", "biome", "entity", "enchantment", "effect", "sound", "structure",
            "particle", "potion", "game_event", "feature");
    private static final int PER_PAGE = 10;

    static String perm(String sub) {
        return switch (sub) {
            case "wea" -> Edits.BYPASS;
            case "debugpaste" -> WorldcraftCommand.ADMIN;
            default -> WorldcraftCommand.USE;
        };
    }

    static Set<String> flags(String sub) {
        return switch (sub) {
            case "place" -> Set.of("preview", "air", "origin", "select", "only-select", "no-entities", "remove-entities", "biomes");
            case "fall" -> Set.of("preview", "inside");
            default -> Set.of();
        };
    }

    private final Worldcraft plugin;
    private final WorldcraftCommand main;
    private final MoreCommands more;

    FaweCommands(Worldcraft plugin, WorldcraftCommand main, MoreCommands more) {
        this.plugin = plugin;
        this.main = main;
        this.more = more;
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

    void run(CommandSender s, String sub, String[] a) {
        if (sub.equals("place")) {
            main.place(s, a);
            return;
        }
        if (sub.equals("fixblocks")) {
            more.run(s, "update", a);
            return;
        }
        if (sub.equals("debugpaste")) {
            more.debugpaste(s);
            return;
        }
        String rest = a.length < 2 ? "" : String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)).trim();
        try {
            switch (sub) {
                case "gtexture" -> gtexture(s, rest);
                case "gsmask" -> gsmask(s, rest);
                case "gtransform" -> gtransform(s, rest);
                case "smask", "transform", "target", "targetoffset", "scroll" -> held(s, sub, a, rest);
                case "primary", "secondary" -> primary(s, a, sub.equals("primary"));
                case "fall" -> fall(s, a);
                case "nbtinfo" -> nbtinfo(s);
                case "wea" -> wea(s, rest);
                case "wer" -> wer(s);
                case "registry" -> registry(s, a);
                case "fixlighting" -> fixlighting(s);
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

    private static String hint(String c) {
        return c == null ? "" : " (did you mean '" + c + "'?)";
    }

    // ------------------------------------------------------------------ H3 lo global

    /** H3 CA1: /wc gtexture &lt;máscara&gt;|#clipboard|off; sin nada, la de ahora. */
    private void gtexture(CommandSender s, String text) throws ParseException {
        Session ses = edits().session(Edits.owner(s));
        if (text.isEmpty()) {
            s.sendMessage(ses.texture == null ? msg().get("gtexture-none")
                    : msg().get("gtexture-current", "texture", ses.textureText, "count", String.valueOf(ses.texture.size())));
            return;
        }
        if (text.equalsIgnoreCase("off")) {
            ses.texture = null;
            ses.textureText = null;
            s.sendMessage(msg().get("gtexture-off"));
            return;
        }
        BlockColors all = plugin.config().parser().allColors();
        Map<String, Integer> pick = new LinkedHashMap<>();
        if (text.equalsIgnoreCase("#clipboard") || text.equalsIgnoreCase("#copy")) {
            Clipboard clip = ses.clipboard();
            if (clip == null) {
                s.sendMessage(msg().get("no-clipboard"));
                return;
            }
            Set<String> mats = new HashSet<>();
            for (int st : clip.distinct()) mats.add(blocks().table().material(st));
            for (BlockColors.Entry e : all.entries()) if (mats.contains(e.id())) pick.put(e.id(), e.rgb());
        } else {
            Mask m = plugin.config().parser().mask(text);
            for (BlockColors.Entry e : all.entries()) if (m.test(e.id(), Map.of())) pick.put(e.id(), e.rgb());
        }
        if (pick.isEmpty()) {
            s.sendMessage(msg().get("gtexture-empty", "texture", text));
            return;
        }
        ses.texture = new BlockColors(pick);
        ses.textureText = text;
        s.sendMessage(msg().get("gtexture-set", "texture", text, "count", String.valueOf(pick.size())));
    }

    /** H3 CA2: /wc gsmask &lt;máscara&gt;|off. */
    private void gsmask(CommandSender s, String text) throws ParseException {
        Session ses = edits().session(Edits.owner(s));
        if (text.isEmpty()) {
            s.sendMessage(ses.gsmask == null ? msg().get("gsmask-none") : msg().get("gsmask-current", "mask", ses.gsmask.text()));
            return;
        }
        if (text.equalsIgnoreCase("off")) {
            ses.gsmask = null;
            s.sendMessage(msg().get("gsmask-off"));
            return;
        }
        ses.gsmask = stateMask(text);
        s.sendMessage(msg().get("gsmask-set", "mask", ses.gsmask.text()));
    }

    /** Una máscara que mira solo el bloque (la de origen mira lo que se pondría: no tiene vecinos). */
    private Mask stateMask(String text) throws ParseException {
        Mask m = plugin.config().parser().maskNear(text);
        if (m.needsNeighbors()) throw new IllegalArgumentException("'" + text + "' looks at the blocks around: a source mask only looks at the block that would be placed");
        return m;
    }

    /** H3 CA3: /wc gtransform &lt;transformación&gt;|off. */
    private void gtransform(CommandSender s, String text) throws ParseException {
        Session ses = edits().session(Edits.owner(s));
        if (text.isEmpty()) {
            s.sendMessage(ses.gtransform == null ? msg().get("gtransform-none") : msg().get("gtransform-current", "transform", ses.gtransform.text()));
            return;
        }
        if (text.equalsIgnoreCase("off")) {
            ses.gtransform = null;
            s.sendMessage(msg().get("gtransform-off"));
            return;
        }
        ses.gtransform = Transforms.parse(text);
        s.sendMessage(msg().get("gtransform-set", "transform", ses.gtransform.text()));
    }

    // ------------------------------------------------------------------ H3 el pincel de la mano

    /** H3 CA4–CA6: smask, transform, target, targetoffset y scroll sobre el pincel de la mano. */
    private void held(CommandSender s, String sub, String[] a, String rest) throws ParseException {
        Player p = player(s);
        if (p == null) return;
        ItemStack it = p.getInventory().getItemInMainHand();
        ToolSpec spec = plugin.tools().spec(it);
        if (spec == null || !spec.kind().brush) {
            p.sendMessage(msg().get("brush-hold"));
            return;
        }
        ToolSpec.Caps caps = plugin.config().tools().caps();
        boolean off = rest.equalsIgnoreCase("off") || rest.equalsIgnoreCase("none");
        ToolSpec next = switch (sub) {
            case "smask" -> {
                if (rest.isEmpty()) throw new WorldcraftCommand.Usage();
                if (off) yield spec.with("smask", null);
                stateMask(rest);
                yield spec.withText("smask", rest);
            }
            case "transform" -> {
                if (rest.isEmpty()) throw new WorldcraftCommand.Usage();
                yield off ? spec.with("transform", null) : spec.withText("transform", Transforms.parse(rest).text());
            }
            case "target" -> {
                List<String> t = ToolSpec.TARGETS;
                if (rest.isEmpty()) yield spec.with("target", t.get((t.indexOf(spec.target()) + 1) % t.size()));
                String m = rest.toLowerCase(Locale.ROOT);
                if (!t.contains(m)) throw new IllegalArgumentException("'" + rest + "' is not a target mode" + hint(Issues.closest(m, t)) + ": " + String.join(", ", t));
                yield spec.with("target", m);
            }
            case "targetoffset" -> {
                int n;
                try {
                    n = Integer.parseInt(rest);
                } catch (NumberFormatException e) {
                    throw new WorldcraftCommand.Usage();
                }
                if (Math.abs(n) > caps.maxTargetOffset()) {
                    throw new IllegalArgumentException("The offset goes from -" + caps.maxTargetOffset() + " to " + caps.maxTargetOffset() + " (tools.max_target_offset)");
                }
                yield spec.with("toffset", n == 0 ? null : String.valueOf(n));
            }
            default -> {
                // scroll <acción> [valores…]
                if (a.length < 2) throw new WorldcraftCommand.Usage();
                String act = a[1].toLowerCase(Locale.ROOT);
                if (!Scroll.ACTIONS.contains(act)) {
                    throw new IllegalArgumentException("'" + a[1] + "' is not a scroll action" + hint(Issues.closest(act, Scroll.ACTIONS)) + ": " + String.join(", ", Scroll.ACTIONS));
                }
                if (act.equals("none")) yield spec.with("scroll", null).with("scroll_values", null).with("scroll_i", null);
                ToolSpec out = spec.with("scroll", act).with("scroll_i", null);
                if (act.equals("mask") || act.equals("pattern")) {
                    List<String> values = new ArrayList<>();
                    for (int i = 2; i < a.length; i++) {
                        if (act.equals("mask")) plugin.config().parser().mask(a[i]);
                        else plugin.config().parser().pattern(a[i]);
                        values.add(a[i]);
                    }
                    if (values.size() < 2) throw new IllegalArgumentException("Give two or more, each as one word: /wc scroll " + act + " stone dirt glass");
                    out = out.withText("scroll_values", String.join("|", values));
                }
                yield out;
            }
        };
        plugin.tools().write(it, next);
        p.sendMessage(msg().get("tool-updated", "what", "brush", "tool", next.describe()));
    }

    /** H3 CA7: /wc primary &lt;pincel…&gt; y /wc secondary &lt;pincel…&gt; (el clic derecho y el izquierdo del mismo ítem). */
    private void primary(CommandSender s, String[] a, boolean right) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 2) throw new WorldcraftCommand.Usage();
        ToolSpec parsed = main.toolCommands().parseBrush(p, a, 1);
        if (parsed == null) return;
        ItemStack it = p.getInventory().getItemInMainHand();
        ToolSpec held = plugin.tools().spec(it);
        if (right) {
            ToolSpec next = parsed;
            if (held != null && held.text("secondary") != null) next = next.with("secondary", held.text("secondary"));
            plugin.tools().bind(p, next);
            p.sendMessage(msg().get("brush-primary", "tool", parsed.describe()));
            return;
        }
        if (held == null || !held.kind().brush) {
            p.sendMessage(msg().get("brush-hold"));
            return;
        }
        if (parsed.text("secondary") != null) parsed = parsed.with("secondary", null);
        plugin.tools().write(it, held.withText("secondary", parsed.encode()));
        p.sendMessage(msg().get("brush-secondary", "tool", parsed.describe()));
    }

    // ------------------------------------------------------------------ H4 sueltos

    /** H4 CA2: //fall [relleno] [--inside]: lo de la selección cae hasta apoyarse. */
    private void fall(CommandSender s, String[] a) throws ParseException {
        Args args = Args.parse(a, 1, flags("fall"));
        if (args.problem != null) {
            s.sendMessage(msg().get("error", "message", args.problem));
            return;
        }
        Pattern fillP = plugin.config().parser().pattern(args.positional.isEmpty() ? "air" : String.join(" ", args.positional));
        if (fillP.placedStates().size() != 1) throw new IllegalArgumentException("The fill is one block, like air or water");
        int fill = blocks().table().intern(fillP.placedStates().get(0));
        Session ses = edits().session(Edits.owner(s));
        Region region = main.selection(s, ses);
        if (region == null) return;
        World w = Bukkit.getWorld(ses.world());
        Box rb = region.bounds();
        boolean inside = args.flags.contains("inside");
        int lo = inside ? rb.minY() : w.getMinHeight();
        Box read = new Box(rb.minX(), lo, rb.minZ(), rb.maxX(), rb.maxY(), rb.maxZ()).clampY(w.getMinHeight(), w.getMaxHeight() - 1);
        if (read == null) return;
        var lim = edits().limits(s);
        if (read.volume() > lim.maxBlocks()) {
            s.sendMessage(msg().get("limit-blocks", "blocks", Edits.fmt(read.volume()), "max", Edits.fmt(lim.maxBlocks()), "groups", String.join(", ", lim.groups())));
            return;
        }
        Snapshot snap = new Snapshot(read);
        boolean preview = args.flags.contains("preview");
        edits().read(s, w, read, "fall", snap, () -> {
            Set<Integer> airs = new HashSet<>(), fixed = new HashSet<>();
            for (int st : snap.distinct()) {
                if (blocks().isAir(st)) airs.add(st);
                if (blocks().hasTile(st)) fixed.add(st);
            }
            IntPredicate isAir = airs::contains, isFixed = fixed::contains;
            Flood.Bound in = region::contains;
            int yLo = read.minY();
            java.util.concurrent.CompletableFuture.supplyAsync(() -> dev.leo.worldcraft.tool.BrushMath.fall(snap, in, isAir, isFixed, fill, yLo), plugin.compute())
                    .whenComplete((out, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (err != null) {
                            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Calculation failed", err);
                            s.sendMessage(msg().get("compute-error"));
                            return;
                        }
                        if (out.isEmpty()) {
                            s.sendMessage(msg().get("nothing-found", "kind", "fall"));
                            return;
                        }
                        long[] pts = out.keySet().stream().mapToLong(Long::longValue).toArray();
                        var probe = new PointsSource(pts, (x, y, z, c) -> c);
                        edits().request(s, new Edits.Op("fall", region.size() + (inside ? ", inside the selection" : ""), w, probe.bounds(), probe.volume(),
                                () -> new PointsSource(pts, (x, y, z, c) -> out.get(PointsSource.pack(x, y, z))), new HashSet<>(out.values()), null), preview);
                    }));
        });
    }

    /** H4 CA4: //nbtinfo, el NBT del bloque que mira. */
    private void nbtinfo(CommandSender s) {
        Player p = player(s);
        if (p == null) return;
        Nms nms = Nms.get();
        if (!nms.lightAvailable()) {
            p.sendMessage(msg().get("nms-unavailable", "reason", nms.lightProblem()));
            return;
        }
        Block b = p.getTargetBlockExact(plugin.config().tools().range());
        if (b == null) {
            p.sendMessage(msg().get("tool-no-target", "range", String.valueOf(plugin.config().tools().range())));
            return;
        }
        String nbt = nms.blockNbt(b.getWorld(), b.getX(), b.getY(), b.getZ());
        String pos = b.getX() + " " + b.getY() + " " + b.getZ();
        if (nbt == null) {
            p.sendMessage(msg().get("nbtinfo-none", "block", b.getType().getKey().getKey(), "pos", pos));
            return;
        }
        p.sendMessage(msg().get("nbtinfo-header", "block", b.getType().getKey().getKey(), "pos", pos));
        String shown = nbt.length() > 2000 ? nbt.substring(0, 2000) + "… (" + nbt.length() + " characters)" : nbt;
        p.sendMessage(net.kyori.adventure.text.Component.text(shown, net.kyori.adventure.text.format.NamedTextColor.GRAY));
    }

    /** H4 CA5: //wea [on|off] prende o apaga saltarse las protecciones (sin nada, lo cambia). */
    private void wea(CommandSender s, String text) {
        Session ses = edits().session(Edits.owner(s));
        ses.bypass = switch (text.toLowerCase(Locale.ROOT)) {
            case "on" -> true;
            case "off" -> false;
            case "" -> !ses.bypass;
            default -> throw new WorldcraftCommand.Usage();
        };
        s.sendMessage(msg().get(ses.bypass ? "wea-on" : "wea-off"));
        if (ses.bypass) plugin.getLogger().info(s.getName() + " turned on /wc wea (edits go ahead even if a protection plugin stops them)");
    }

    /** H4 CA6: //wer selecciona la zona donde le dejan construir (lo contesta un plugin de protección). */
    private void wer(CommandSender s) {
        Player p = player(s);
        if (p == null) return;
        WorldcraftAllowedRegionEvent ev = new WorldcraftAllowedRegionEvent(p, p.getLocation());
        Bukkit.getPluginManager().callEvent(ev);
        if (!ev.hasRegion()) {
            p.sendMessage(msg().get("wer-none"));
            return;
        }
        int[] lo = ev.getMin(), hi = ev.getMax();
        World w = p.getWorld();
        Box b = new Box(lo[0], Math.max(lo[1], w.getMinHeight()), lo[2], hi[0], Math.min(hi[1], w.getMaxHeight() - 1), hi[2]);
        edits().session(p.getUniqueId()).selectBox(w.getName(), b);
        p.sendMessage(msg().get("wer-selected", "name", ev.getRegionName().isBlank() ? "" : " " + ev.getRegionName(),
                "plugin", ev.getAnsweredBy() == null ? "?" : ev.getAnsweredBy().getName(), "size", b.size(), "blocks", Edits.fmt(b.volume())));
    }

    /** H4 CA7: //registry &lt;registro&gt; [búsqueda] [página]. */
    private void registry(CommandSender s, String[] a) {
        if (a.length < 2) {
            s.sendMessage(msg().get("registry-list", "registries", String.join(", ", REGISTRIES)));
            return;
        }
        String reg = a[1].toLowerCase(Locale.ROOT).replace("minecraft:", "");
        if (reg.equals("blocks") || reg.equals("items") || reg.equals("biomes") || reg.equals("entities")) reg = reg.substring(0, reg.length() - (reg.endsWith("ies") ? 3 : 1)) + (reg.endsWith("ies") ? "y" : "");
        if (reg.equals("entity_type")) reg = "entity";
        if (!REGISTRIES.contains(reg)) {
            s.sendMessage(msg().get("error", "message", "'" + a[1] + "' is not a registry" + hint(Issues.closest(reg, REGISTRIES)) + ": " + String.join(", ", REGISTRIES)));
            return;
        }
        int page = 1;
        String q = "";
        for (int i = 2; i < a.length; i++) {
            if (a[i].matches("\\d{1,4}") && i == a.length - 1) page = Integer.parseInt(a[i]);
            else q = (q + " " + a[i]).trim();
        }
        List<String> all = keys(reg);
        String ql = q.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (String k : all) if (ql.isEmpty() || k.contains(ql)) found.add(k);
        if (found.isEmpty()) {
            s.sendMessage(msg().get("registry-none", "registry", reg, "text", q));
            return;
        }
        int pages = (found.size() + PER_PAGE - 1) / PER_PAGE;
        page = Math.max(1, Math.min(page, pages));
        s.sendMessage(msg().get("registry-header", "registry", reg, "count", String.valueOf(found.size()), "page", String.valueOf(page), "pages", String.valueOf(pages)));
        for (String k : found.subList((page - 1) * PER_PAGE, Math.min(found.size(), page * PER_PAGE))) s.sendMessage(msg().get("registry-line", "id", k.replace("minecraft:", "")));
    }

    @SuppressWarnings({"deprecation", "removal"})
    private List<String> keys(String reg) {
        Set<String> out = new TreeSet<>();
        switch (reg) {
            case "block", "item" -> {
                for (org.bukkit.Material m : org.bukkit.Material.values()) {
                    if (m.isLegacy()) continue;
                    if (reg.equals("block") ? m.isBlock() : m.isItem()) out.add(m.getKey().toString());
                }
            }
            case "feature" -> out.addAll(Nms.get().features());
            default -> {
                Registry<? extends Keyed> r = switch (reg) {
                    case "biome" -> Registry.BIOME;
                    case "entity" -> Registry.ENTITY_TYPE;
                    case "enchantment" -> Registry.ENCHANTMENT;
                    case "effect" -> Registry.EFFECT;
                    case "sound" -> Registry.SOUNDS;
                    case "structure" -> Registry.STRUCTURE;
                    case "particle" -> Registry.PARTICLE_TYPE;
                    case "potion" -> Registry.POTION;
                    default -> Registry.GAME_EVENT;
                };
                for (Keyed k : r) {
                    try {
                        out.add(k.getKey().toString());
                    } catch (RuntimeException ignored) {
                        // una entrada sin clave (entity UNKNOWN) no se lista
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

    /** H5 CA1: //fixlighting recalcula la luz de los chunks de la selección (o del chunk donde está). */
    private void fixlighting(CommandSender s) {
        Nms nms = Nms.get();
        if (!nms.lightAvailable()) {
            s.sendMessage(msg().get("nms-unavailable", "reason", nms.lightProblem()));
            return;
        }
        Session ses = edits().session(Edits.owner(s));
        World w;
        List<long[]> chunks = new ArrayList<>();
        Region region = ses.region();
        if (region != null && ses.world() != null) {
            w = Bukkit.getWorld(ses.world());
            Box b = region.bounds();
            for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++)
                for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) chunks.add(new long[]{cx, cz});
        } else if (s instanceof Player p) {
            w = p.getWorld();
            chunks.add(new long[]{p.getLocation().getBlockX() >> 4, p.getLocation().getBlockZ() >> 4});
        } else {
            s.sendMessage(msg().get("no-selection"));
            return;
        }
        if (w == null) return;
        int max = plugin.config().tools().fixlightingMaxChunks();
        if (chunks.size() > max) {
            s.sendMessage(msg().get("fixlighting-too-many", "chunks", String.valueOf(chunks.size()), "max", String.valueOf(max)));
            return;
        }
        chunks.removeIf(c -> !w.isChunkLoaded((int) c[0], (int) c[1]));
        if (chunks.isEmpty()) {
            s.sendMessage(msg().get("fixlighting-none"));
            return;
        }
        int asked = chunks.size();
        s.sendMessage(msg().get("fixlighting-started", "chunks", String.valueOf(asked)));
        nms.relight(w, chunks, n -> s.sendMessage(msg().get("fixlighting-done", "chunks", String.valueOf(n))));
    }

    // ------------------------------------------------------------------ autocompletado

    List<String> complete(CommandSender s, String sub, String[] a, String last) {
        return switch (sub) {
            case "gtexture" -> a.length == 2 ? merge(WorldcraftCommand.filter(List.of("off", "#clipboard"), last), main.blockSuggestions(last, true)) : main.blockSuggestions(last, true);
            case "gsmask", "smask" -> a.length == 2 ? merge(WorldcraftCommand.filter(List.of("off", "!air"), last), main.blockSuggestions(last, true)) : main.blockSuggestions(last, true);
            case "gtransform", "transform" -> WorldcraftCommand.filter(List.of("off", "#offset[0][1][0]", "#rotate[90]", "#flip[x]", "#spread[2]", "#scale[2]"), last);
            case "target" -> a.length == 2 ? WorldcraftCommand.filter(ToolSpec.TARGETS, last) : List.of();
            case "targetoffset" -> a.length == 2 ? WorldcraftCommand.filter(List.of("1", "2", "-1"), last) : List.of();
            case "scroll" -> a.length == 2 ? WorldcraftCommand.filter(Scroll.ACTIONS, last) : main.blockSuggestions(last, false);
            case "registry" -> a.length == 2 ? WorldcraftCommand.filter(REGISTRIES, last) : List.of();
            case "fall" -> main.blockSuggestions(last, false);
            case "primary", "secondary" -> a.length == 2 ? WorldcraftCommand.filter(dev.leo.worldcraft.tool.ToolKind.ids(true), last) : main.blockSuggestions(last, false);
            default -> List.of();
        };
    }

    private static List<String> merge(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}
