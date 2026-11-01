package dev.leo.skinstatues.command;

import dev.leo.skinstatues.SkinStatues;
import dev.leo.skinstatues.statue.StatueStore.Statue;
import dev.leo.skinstatues.statue.Statues;
import dev.leo.skinstatues.statue.TickWorker;
import dev.leo.skinstatues.color.BlockPalette;
import dev.leo.skinstatues.config.Issues;
import dev.leo.skinstatues.config.LoadedConfig;
import dev.leo.skinstatues.config.Messages;
import dev.leo.skinstatues.model.Pose;
import dev.leo.skinstatues.skin.MojangClient;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /statue (plan 021 §2). Cada subcomando pide su permiso; el autocompletado solo muestra lo permitido (H9 CA4). */
public final class StatueCommand implements TabExecutor {
    private static final String BUILD = "skinstatues.build", REMOVE = "skinstatues.remove",
            OTHERS = "skinstatues.remove.others", ADMIN = "skinstatues.admin";
    private static final Map<String, String> SUBS = Map.ofEntries(
            Map.entry("build", BUILD), Map.entry("undo", BUILD), Map.entry("cancel", BUILD), Map.entry("list", BUILD),
            Map.entry("info", BUILD), Map.entry("poses", BUILD), Map.entry("palettes", BUILD),
            Map.entry("remove", REMOVE), Map.entry("reload", ADMIN), Map.entry("validate", ADMIN),
            Map.entry("selftest", ADMIN));
    private static final List<String> FLAGS = List.of("--palette", "--no-overlay", "--no-cape");
    private static final int PAGE = 8;

    private final SkinStatues plugin;

    public StatueCommand(SkinStatues plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            s.sendMessage(msg().get("usage"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String perm = SUBS.get(sub);
        if (perm == null) {
            s.sendMessage(msg().get("unknown-subcommand", "sub", args[0], "hint", Issues.hint(sub, visible(s))));
            return true;
        }
        if (!s.hasPermission(perm)) {
            s.sendMessage(msg().get("no-permission"));
            return true;
        }
        switch (sub) {
            case "build" -> build(s, args);
            case "undo" -> undo(s);
            case "cancel" -> cancel(s, args);
            case "remove" -> remove(s, args);
            case "list" -> list(s, args);
            case "info" -> info(s, args);
            case "poses" -> s.sendMessage(msg().get("poses", "list", String.join(", ", plugin.config().poses().keySet()),
                    "default", plugin.config().defaultPose()));
            case "palettes" -> palettes(s);
            case "reload" -> reload(s);
            case "validate" -> validate(s, plugin.config());
            case "selftest" -> selftest(s);
            default -> s.sendMessage(msg().get("usage"));
        }
        return true;
    }

    private void build(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage(msg().get("players-only"));
            return;
        }
        if (args.length < 3) {
            s.sendMessage(msg().get("usage-build"));
            return;
        }
        LoadedConfig cfg = plugin.config();
        String name = args[1];
        if (!MojangClient.NAME.matcher(name).matches()) {
            s.sendMessage(msg().get("invalid-name", "name", name));
            return;
        }
        int height;
        try {
            height = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            height = -1;
        }
        int min = cfg.settings().minHeight(), max = cfg.settings().maxHeight();
        if (height < min || height > max) {
            s.sendMessage(msg().get("invalid-height", "height", args[2], "min", min, "max", max));
            return;
        }
        String pose = cfg.defaultPose(), palette = cfg.defaultPalette();
        boolean overlay = cfg.settings().overlay(), cape = cfg.settings().cape();
        for (int i = 3; i < args.length; i++) {
            String a = args[i].toLowerCase(Locale.ROOT);
            switch (a) {
                case "--no-overlay" -> overlay = false;
                case "--no-cape" -> cape = false;
                case "--palette" -> {
                    if (i + 1 >= args.length) {
                        s.sendMessage(msg().get("usage-build"));
                        return;
                    }
                    palette = args[++i].toLowerCase(Locale.ROOT);
                }
                default -> {
                    if (a.startsWith("--")) {
                        s.sendMessage(msg().get("unknown-flag", "flag", args[i], "hint", Issues.hint(a, FLAGS)));
                        return;
                    }
                    pose = a;
                }
            }
        }
        Pose ps = cfg.poses().get(pose);
        if (ps == null) {
            s.sendMessage(msg().get("unknown-pose", "pose", pose, "hint", Issues.hint(pose, cfg.poses().keySet())));
            return;
        }
        BlockPalette pal = cfg.palettes().get(palette);
        if (pal == null) {
            s.sendMessage(msg().get("unknown-palette", "palette", palette, "hint", Issues.hint(palette, cfg.palettes().keySet())));
            return;
        }
        plugin.statues().build(p, new Statues.Request(name, height, ps, pal, overlay, cape));
    }

    private void undo(CommandSender s) {
        if (!(s instanceof Player p)) {
            s.sendMessage(msg().get("players-only"));
            return;
        }
        Statue last = plugin.statues().store().lastBy(p.getUniqueId());
        if (last == null) {
            s.sendMessage(msg().get("undo-none"));
            return;
        }
        plugin.statues().remove(s, last);
    }

    private void cancel(CommandSender s, String[] args) {
        TickWorker w = plugin.statues().worker();
        if (args.length >= 2) {
            Statue t = statue(s, args[1]);
            if (t == null || !mayTouch(s, t)) return;
            if (!w.cancel(t.id())) s.sendMessage(msg().get("nothing-to-cancel"));
            return;
        }
        UUID who = s instanceof Player p ? p.getUniqueId() : Statues.CONSOLE;
        List<TickWorker.Job> mine = w.of(who);
        if (mine.isEmpty()) {
            s.sendMessage(msg().get("nothing-to-cancel"));
            return;
        }
        w.cancel(mine.get(0).statueId());
    }

    private void remove(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage(msg().get("usage-remove"));
            return;
        }
        Statue t = statue(s, args[1]);
        if (t == null || !mayTouch(s, t)) return;
        plugin.statues().remove(s, t);
    }

    /** Las estatuas de otros solo con skinstatues.remove.others (la consola puede todo). */
    private boolean mayTouch(CommandSender s, Statue t) {
        if (!(s instanceof Player p) || p.getUniqueId().equals(t.owner()) || s.hasPermission(OTHERS)) return true;
        s.sendMessage(msg().get("not-yours", "id", t.id(), "owner", t.ownerName()));
        return false;
    }

    private Statue statue(CommandSender s, String arg) {
        try {
            Statue t = plugin.statues().store().get(Integer.parseInt(arg.replace("#", "")));
            if (t != null) return t;
        } catch (NumberFormatException ignored) {
            // cae al mensaje
        }
        s.sendMessage(msg().get("not-found", "id", arg));
        return null;
    }

    private void list(CommandSender s, String[] args) {
        List<Statue> all = new ArrayList<>(plugin.statues().store().all());
        all.sort(Comparator.comparingInt(Statue::id).reversed());
        if (all.isEmpty()) {
            s.sendMessage(msg().get("list-empty"));
            return;
        }
        int pages = (all.size() + PAGE - 1) / PAGE;
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Math.max(1, Math.min(pages, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
                // página 1
            }
        }
        s.sendMessage(msg().get("list-header", "count", all.size(), "page", page, "pages", pages));
        for (Statue t : all.subList((page - 1) * PAGE, Math.min(all.size(), page * PAGE))) {
            s.sendMessage(msg().get("list-line", "id", t.id(), "name", t.skin(), "height", t.height(), "pose", t.pose(),
                    "world", t.world(), "x", t.x(), "y", t.y(), "z", t.z(), "owner", t.ownerName(),
                    "state", t.state().name().toLowerCase(Locale.ROOT)));
        }
    }

    private void info(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage(msg().get("usage-info"));
            return;
        }
        Statue t = statue(s, args[1]);
        if (t == null) return;
        String when = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(t.created()));
        s.sendMessage(msg().get("info", "id", t.id(), "name", t.skin(), "height", t.height(), "pose", t.pose(),
                "palette", t.palette(), "world", t.world(), "x", t.x(), "y", t.y(), "z", t.z(),
                "facing", facing(t.facing()), "owner", t.ownerName(), "date", when, "blocks", t.blocks(),
                "state", t.state().name().toLowerCase(Locale.ROOT)));
    }

    private static String facing(int yaw) {
        return switch (Math.floorMod(yaw, 360)) {
            case 90 -> "west";
            case 180 -> "north";
            case 270 -> "east";
            default -> "south";
        };
    }

    private void palettes(CommandSender s) {
        List<String> parts = new ArrayList<>();
        plugin.config().palettes().forEach((n, p) -> parts.add(n + " (" + p.entries().size() + ")"));
        s.sendMessage(msg().get("palettes", "list", String.join(", ", parts), "default", plugin.config().defaultPalette()));
    }

    private void reload(CommandSender s) {
        LoadedConfig cfg = plugin.reload();
        s.sendMessage(msg().get("reloaded", "poses", cfg.poses().size(), "palettes", cfg.palettes().size(),
                "errors", cfg.issues().errors(), "warnings", cfg.issues().warnings()));
        if (!cfg.issues().all().isEmpty()) validate(s, cfg);
    }

    private void validate(CommandSender s, LoadedConfig cfg) {
        List<Issues.Issue> all = cfg.issues().all();
        if (all.isEmpty()) {
            s.sendMessage(msg().get("validate-ok", "poses", cfg.poses().size(), "palettes", cfg.palettes().size(),
                    "blocks", cfg.catalog() == null ? 0 : cfg.catalog().colors().size()));
            return;
        }
        s.sendMessage(msg().get("validate-header", "errors", cfg.issues().errors(), "warnings", cfg.issues().warnings()));
        for (Issues.Issue i : all) {
            s.sendMessage(msg().get(i.level() == Issues.Level.ERROR ? "validate-error" : "validate-warning",
                    "issue", i.toString()));
        }
    }

    private void selftest(CommandSender s) {
        BlockPalette pal = plugin.config().palettes().get(plugin.config().defaultPalette());
        if (pal == null) {
            s.sendMessage(msg().get("selftest-fail", "reason", "no palette is loaded"));
            return;
        }
        plugin.statues().selftest(s, pal);
    }

    private List<String> visible(CommandSender s) {
        List<String> out = new ArrayList<>();
        SUBS.forEach((sub, perm) -> {
            if (s.hasPermission(perm)) out.add(sub);
        });
        out.sort(null);
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 1) return starting(visible(s), args[0]);
        String sub = args[0].toLowerCase(Locale.ROOT);
        String perm = SUBS.get(sub);
        if (perm == null || !s.hasPermission(perm)) return List.of();
        LoadedConfig cfg = plugin.config();
        return switch (sub) {
            case "build" -> {
                if (args.length == 2) {
                    List<String> names = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                    names.add("Example");
                    yield starting(names, args[1]);
                }
                if (args.length == 3) yield starting(List.of("16", "32", "64", "128"), args[2]);
                if ("--palette".equalsIgnoreCase(args[args.length - 2])) {
                    yield starting(new ArrayList<>(cfg.palettes().keySet()), args[args.length - 1]);
                }
                List<String> opts = new ArrayList<>(FLAGS);
                if (args.length == 4) opts.addAll(cfg.poses().keySet());
                yield starting(opts, args[args.length - 1]);
            }
            case "remove", "info", "cancel" -> {
                List<String> ids = new ArrayList<>();
                for (Statue t : plugin.statues().store().all()) ids.add(String.valueOf(t.id()));
                yield args.length == 2 ? starting(ids, args[1]) : List.of();
            }
            default -> List.of();
        };
    }

    private static List<String> starting(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }
}
