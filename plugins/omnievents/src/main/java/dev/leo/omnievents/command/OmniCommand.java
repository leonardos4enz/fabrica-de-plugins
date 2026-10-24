package dev.leo.omnievents.command;

import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.action.ExecContext;
import dev.leo.omnievents.data.DataStore;
import dev.leo.omnievents.debug.SelfTest;
import dev.leo.omnievents.debug.Stats;
import dev.leo.omnievents.expr.ExprException;
import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Node;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.importer.ConditionalEventsImporter;
import dev.leo.omnievents.rule.Problems;
import dev.leo.omnievents.rule.Rule;
import dev.leo.omnievents.text.TextFormat;
import dev.leo.omnievents.trigger.TriggerType;
import dev.leo.omnievents.util.Durations;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** /omni (H11). Cada subcomando exige omnievents.command.&lt;sub&gt;. */
public final class OmniCommand implements TabExecutor {
    private static final Map<String, String> SUBS = new LinkedHashMap<>();

    static {
        SUBS.put("help", "Esta ayuda");
        SUBS.put("reload", "Recarga config, mensajes y reglas");
        SUBS.put("validate", "Revisa las reglas sin aplicarlas");
        SUBS.put("list", "Lista las reglas [filtro]");
        SUBS.put("info", "Detalle de una regla");
        SUBS.put("enable", "Activa una regla");
        SUBS.put("disable", "Desactiva una regla");
        SUBS.put("debug", "Traza en vivo: <regla|all|off>");
        SUBS.put("eval", "Evalúa una expresión con tu contexto");
        SUBS.put("call", "Ejecuta una regla call: <regla> [jugador] [k=v…]");
        SUBS.put("var", "Variables: get|set|del|list <jugador|global> [nombre] [valor]");
        SUBS.put("cooldown", "reset <regla> [jugador|global]");
        SUBS.put("limit", "reset <regla> [jugador|global]");
        SUBS.put("stats", "Rendimiento por regla, oyentes y tareas");
        SUBS.put("triggers", "Disparadores disponibles");
        SUBS.put("actions", "Acciones disponibles");
        SUBS.put("item", "save|delete|list <nombre>: ítems para saved:nombre");
        SUBS.put("import", "import conditionalevents");
        SUBS.put("selftest", "Autoprueba del motor en este servidor");
    }

    private final OmniEvents plugin;

    public OmniCommand(OmniEvents plugin) {
        this.plugin = plugin;
    }

    private void send(CommandSender s, String key, Object... args) {
        s.sendMessage(plugin.messages().get(key, args));
    }

    private static void raw(CommandSender s, String mini) {
        s.sendMessage(TextFormat.parse(mini));
    }

    private static String esc(Object o) {
        return MiniMessage.miniMessage().escapeTags(Values.str(o));
    }

    private static boolean allowed(CommandSender s, String sub) {
        return s.hasPermission("omnievents.command." + sub);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!SUBS.containsKey(sub)) {
            send(s, "unknown-subcommand", "sub", sub);
            return true;
        }
        if (!allowed(s, sub)) {
            send(s, "no-permission");
            return true;
        }
        String[] a = Arrays.copyOfRange(args, Math.min(1, args.length), args.length);
        try {
            switch (sub) {
                case "help" -> help(s, label);
                case "reload" -> reload(s);
                case "validate" -> validate(s);
                case "list" -> list(s, a);
                case "info" -> info(s, a);
                case "enable", "disable" -> toggle(s, a, sub.equals("enable"));
                case "debug" -> debug(s, a);
                case "eval" -> eval(s, a);
                case "call" -> call(s, a);
                case "var" -> var(s, a);
                case "cooldown" -> resetUsage(s, a, true);
                case "limit" -> resetUsage(s, a, false);
                case "stats" -> stats(s);
                case "triggers" -> triggers(s);
                case "actions" -> raw(s, "<gold>Acciones:</gold> <gray>" + esc(String.join(", ", plugin.actions().names())));
                case "item" -> item(s, a);
                case "import" -> importCe(s, a);
                case "selftest" -> selftest(s);
                default -> help(s, label);
            }
        } catch (ExprException | IllegalArgumentException e) {
            send(s, "error", "message", e.getMessage());
        }
        return true;
    }

    private void help(CommandSender s, String label) {
        raw(s, "<gold><bold>OmniEvents</bold></gold> <gray>" + esc(plugin.getPluginMeta().getVersion()) + " · eventos condicionales");
        SUBS.forEach((k, v) -> {
            if (allowed(s, k)) raw(s, "<yellow>/" + esc(label) + " " + k + "</yellow> <dark_gray>»</dark_gray> <gray>" + esc(v));
        });
    }

    private void reload(CommandSender s) {
        long t0 = System.nanoTime();
        Problems p = plugin.reload();
        plugin.logProblems(p);
        showProblems(s, p);
        send(s, "reload-done", "rules", plugin.rules().all().size(), "errors", p.errors(), "warnings", p.warnings(),
                "ms", Values.formatNumber(Values.round((System.nanoTime() - t0) / 1e6, 1)));
    }

    private void validate(CommandSender s) {
        Problems p = plugin.validate();
        showProblems(s, p);
        send(s, p.errors() == 0 ? "validate-ok" : "validate-errors", "errors", p.errors(), "warnings", p.warnings());
    }

    private void showProblems(CommandSender s, Problems p) {
        int shown = 0;
        for (Problems.Problem pr : p.all()) {
            if (shown++ >= 30) {
                raw(s, "<gray>… y " + (p.size() - 30) + " más en la consola");
                break;
            }
            raw(s, (pr.error() ? "<red>✘ " : "<yellow>• ") + "<white>" + esc(pr.file()) + "</white>"
                    + (pr.path().isEmpty() ? "" : " <dark_gray>›</dark_gray> <aqua>" + esc(pr.path()) + "</aqua>") + "<gray>: " + esc(pr.message()));
        }
    }

    private void list(CommandSender s, String[] a) {
        String f = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "";
        List<Rule> rs = new ArrayList<>(plugin.rules().all());
        rs.sort((x, y) -> x.name().compareToIgnoreCase(y.name()));
        int n = 0;
        raw(s, "<gold>Reglas</gold> <gray>(" + rs.size() + ")");
        for (Rule r : rs) {
            if (!f.isEmpty() && !r.name().toLowerCase(Locale.ROOT).contains(f) && !r.trigger().name().contains(f) && !r.file().toLowerCase(Locale.ROOT).contains(f)) continue;
            n++;
            raw(s, (r.enabled() ? "<green>● " : "<red>○ ") + "<white><click:run_command:/omni info " + esc(r.name()) + "><hover:show_text:'Ver detalle'>"
                    + esc(r.name()) + "</hover></click></white> <dark_gray>·</dark_gray> <aqua>" + esc(r.trigger().name())
                    + "</aqua> <dark_gray>·</dark_gray> <gray>" + esc(r.file()));
        }
        if (n == 0) send(s, "list-empty");
    }

    private Rule rule(CommandSender s, String[] a, int i) {
        if (a.length <= i) throw new IllegalArgumentException("falta el nombre de la regla");
        Rule r = plugin.rules().get(a[i]);
        if (r == null) {
            String hint = dev.leo.omnievents.util.Suggest.hint(a[i], plugin.rules().names());
            throw new IllegalArgumentException("no existe la regla '" + a[i] + "'" + hint);
        }
        return r;
    }

    private void info(CommandSender s, String[] a) {
        Rule r = rule(s, a, 0);
        Stats st = r.stats();
        raw(s, "<gold>" + esc(r.name()) + "</gold> " + (r.enabled() ? "<green>activa" : "<red>inactiva"));
        raw(s, "<gray>Archivo: <white>" + esc(r.file()) + "</white> · disparador <aqua>" + esc(r.trigger().name()) + "</aqua> · prioridad " + r.priority());
        for (Expression c : r.conditions()) raw(s, "<gray>  si <white>" + esc(c.source()));
        if (r.cooldownMs() > 0) raw(s, "<gray>Enfriamiento: <white>" + Durations.format(r.cooldownMs()) + (r.cooldownGlobal() ? " (global)" : " (por jugador)"));
        if (r.limit() > 0) raw(s, "<gray>Límite: <white>" + r.limit() + " usos " + (r.limitGlobal() ? "globales" : "por jugador") + ", reinicio " + r.limitReset().name().toLowerCase(Locale.ROOT));
        raw(s, "<gray>Acciones: <white>" + r.actionCount() + "</white> · disparos <white>" + st.firedCount() + "</white> · aciertos <white>"
                + st.passedCount() + "</white> · errores <white>" + st.errorCount() + "</white> · promedio <white>"
                + Values.formatNumber(Values.round(st.avgMillis(), 3)) + " ms");
    }

    private void toggle(CommandSender s, String[] a, boolean on) {
        Rule r = rule(s, a, 0);
        r.setEnabled(on);
        // Re-registrar: una regla activada necesita su oyente, una desactivada lo libera (H6 CA5).
        plugin.listeners().register(plugin.rules().all(), new Problems());
        plugin.timers().start();
        plugin.ruleCommands().register();
        send(s, on ? "rule-enabled" : "rule-disabled", "rule", r.name());
        send(s, "toggle-note");
    }

    private void debug(CommandSender s, String[] a) {
        if (a.length == 0) {
            String w = plugin.debugger().watching(s);
            send(s, "debug-status", "rule", w == null ? "off" : w.equals("*") ? "all" : w);
            return;
        }
        String arg = a[0];
        if (arg.equalsIgnoreCase("off")) {
            plugin.debugger().watch(s, null);
            send(s, "debug-off");
            return;
        }
        if (!arg.equalsIgnoreCase("all")) rule(s, a, 0);
        plugin.debugger().watch(s, arg);
        send(s, "debug-on", "rule", arg);
    }

    private ExecContext senderContext(CommandSender s) {
        Map<String, Object> b = new HashMap<>();
        b.put("sender", s);
        if (s instanceof Player p) {
            b.put("player", p);
            b.put("world", p.getWorld());
            b.put("location", p.getLocation());
            b.put("item", p.getInventory().getItemInMainHand());
            var target = p.getTargetEntity(8);
            if (target != null) b.put("target", target);
            var block = p.getTargetBlockExact(8);
            if (block != null) b.put("block", block);
        }
        return new ExecContext(plugin, null, b);
    }

    private void eval(CommandSender s, String[] a) {
        if (a.length == 0) throw new IllegalArgumentException("escribe una expresión, p. ej. /omni eval player.health * 2");
        String src = String.join(" ", a);
        Expression e = Expression.compile(src);
        ExecContext ctx = senderContext(s);
        Object v = e.eval(ctx);
        raw(s, "<gray>" + esc(src) + " <dark_gray>=</dark_gray> <green>" + esc(Node.show(v)) + "</green> <dark_gray>(" + esc(Values.typeName(v)) + ")");
    }

    private void call(CommandSender s, String[] a) {
        Rule r = rule(s, a, 0);
        if (r.trigger().kind() != TriggerType.Kind.CALL) throw new IllegalArgumentException("'" + r.name() + "' no es una regla con trigger: call");
        ExecContext base = senderContext(s);
        int i = 1;
        Player target = null;
        if (a.length > 1 && !a[1].contains("=")) {
            target = Bukkit.getPlayerExact(a[1]);
            if (target == null) throw new IllegalArgumentException("el jugador '" + a[1] + "' no está conectado");
            i = 2;
        }
        Map<String, Object> args = new HashMap<>();
        for (; i < a.length; i++) {
            int eq = a[i].indexOf('=');
            if (eq <= 0) throw new IllegalArgumentException("argumento '" + a[i] + "' sin '=' (usa nombre=expresión)");
            args.put(a[i].substring(0, eq), Expression.compile(a[i].substring(eq + 1)).eval(base));
        }
        Map<String, Object> b = new HashMap<>();
        b.put("sender", s);
        Player p = target != null ? target : s instanceof Player sp ? sp : null;
        if (p != null) {
            b.put("player", p);
            b.put("world", p.getWorld());
            b.put("location", p.getLocation());
        }
        plugin.rules().call(r.name(), new ExecContext(plugin, null, b), args, 0, null);
        send(s, "call-done", "rule", r.name());
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer offline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        if (op == null) throw new IllegalArgumentException("jugador desconocido '" + name + "'");
        return op;
    }

    private void var(CommandSender s, String[] a) {
        if (a.length < 2) throw new IllegalArgumentException("uso: /omni var get|set|del|list <jugador|global> [nombre] [valor]");
        String op = a[0].toLowerCase(Locale.ROOT);
        boolean global = a[1].equalsIgnoreCase("global");
        UUID id = global ? null : offline(a[1]).getUniqueId();
        boolean loaded = global || plugin.data().isLoaded(id);
        DataStore.Holder h = global ? plugin.data().global() : loaded ? plugin.data().player(id) : plugin.data().peekOffline(id);
        String who = global ? "global" : a[1];
        switch (op) {
            case "list" -> {
                raw(s, "<gold>Variables de " + esc(who) + "</gold> <gray>(" + h.vars().size() + ")");
                new TreeMap<>(h.vars()).forEach((k, v) -> raw(s, "<gray>  " + esc(k) + " <dark_gray>=</dark_gray> <white>" + esc(Node.show(v))));
            }
            case "get" -> {
                String n = need(a, 2, "nombre");
                raw(s, "<gray>" + esc(who) + "." + esc(n) + " <dark_gray>=</dark_gray> <white>" + esc(Node.show(h.vars().get(n))));
            }
            case "set" -> {
                String n = need(a, 2, "nombre");
                String src = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
                if (src.isBlank()) throw new IllegalArgumentException("falta el valor (una expresión: 10, 'texto', pvar_of('Leo','x') + 1)");
                Object v = Expression.compile(src).eval(senderContext(s));
                h.vars().put(n, DataStore.storable(v));
                h.markDirty();
                if (!loaded) plugin.data().writeOffline(h);
                plugin.getLogger().info(s.getName() + " asignó " + who + "." + n + " = " + Values.str(v));
                send(s, "var-set", "target", who, "name", n, "value", Node.show(v));
            }
            case "del" -> {
                String n = need(a, 2, "nombre");
                boolean had = h.vars().remove(n) != null;
                h.markDirty();
                if (!loaded) plugin.data().writeOffline(h);
                plugin.getLogger().info(s.getName() + " borró " + who + "." + n);
                send(s, had ? "var-deleted" : "var-missing", "target", who, "name", n);
            }
            default -> throw new IllegalArgumentException("usa get, set, del o list");
        }
    }

    private static String need(String[] a, int i, String what) {
        if (a.length <= i) throw new IllegalArgumentException("falta " + what);
        return a[i];
    }

    private void resetUsage(CommandSender s, String[] a, boolean cooldown) {
        if (a.length < 2 || !a[0].equalsIgnoreCase("reset")) throw new IllegalArgumentException("uso: /omni " + (cooldown ? "cooldown" : "limit") + " reset <regla> [jugador|global]");
        Rule r = rule(s, a, 1);
        UUID id = null;
        if (a.length > 2 && !a[2].equalsIgnoreCase("global")) id = offline(a[2]).getUniqueId();
        else if (a.length <= 2 && s instanceof Player p) id = p.getUniqueId();
        if (cooldown) plugin.rules().resetCooldown(r.name(), id);
        else plugin.rules().resetUses(r.name(), id);
        send(s, "usage-reset", "rule", r.name(), "what", cooldown ? "enfriamiento" : "límite", "target", a.length > 2 ? a[2] : s.getName());
    }

    private void stats(CommandSender s) {
        raw(s, "<gold>Rendimiento</gold> <gray>· " + plugin.listeners().count() + " oyentes · " + plugin.timers().count()
                + " temporizadores · " + plugin.ruleCommands().count() + " comandos · " + plugin.data().loadedPlayers().size() + " jugadores en memoria");
        for (String l : plugin.listeners().describe()) raw(s, "<dark_gray>  " + esc(l));
        List<Rule> rs = new ArrayList<>(plugin.rules().all());
        rs.sort((x, y) -> Double.compare(y.stats().totalMillis(), x.stats().totalMillis()));
        int n = 0;
        for (Rule r : rs) {
            if (n++ >= 15) break;
            Stats st = r.stats();
            if (st.firedCount() == 0) continue;
            raw(s, "<white>" + esc(r.name()) + "</white> <gray>· " + st.firedCount() + " disparos · " + st.passedCount() + " aciertos · "
                    + (st.errorCount() > 0 ? "<red>" + st.errorCount() + " errores</red> · " : "")
                    + "prom <white>" + Values.formatNumber(Values.round(st.avgMillis(), 3)) + " ms</white> · máx " + Values.formatNumber(Values.round(st.maxMillis(), 3)) + " ms");
        }
    }

    private void triggers(CommandSender s) {
        Map<String, List<String>> byGroup = new LinkedHashMap<>();
        for (TriggerType t : plugin.triggers().all()) byGroup.computeIfAbsent(t.group(), k -> new ArrayList<>()).add(t.name());
        raw(s, "<gold>Disparadores</gold> <gray>(" + plugin.triggers().all().size() + ")");
        byGroup.forEach((g, names) -> raw(s, "<yellow>" + esc(g) + ":</yellow> <gray>" + esc(String.join(", ", names))));
    }

    private void item(CommandSender s, String[] a) {
        String op = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        try {
            switch (op) {
                case "list" -> raw(s, "<gold>Ítems guardados:</gold> <gray>" + esc(String.join(", ", plugin.savedItems().names())));
                case "save" -> {
                    if (!(s instanceof Player p)) throw new IllegalArgumentException("solo un jugador puede guardar el ítem de su mano");
                    String n = need(a, 1, "nombre");
                    if (!n.matches("[A-Za-z0-9_\\-]{1,32}")) throw new IllegalArgumentException("nombre inválido (letras, números, _ y -)");
                    ItemStack it = p.getInventory().getItemInMainHand();
                    if (it.getType().isAir()) throw new IllegalArgumentException("no tienes nada en la mano");
                    plugin.savedItems().save(n, it);
                    send(s, "item-saved", "name", n);
                }
                case "delete" -> {
                    String n = need(a, 1, "nombre");
                    send(s, plugin.savedItems().delete(n) ? "item-deleted" : "item-missing", "name", n);
                }
                default -> throw new IllegalArgumentException("usa save, delete o list");
            }
        } catch (IOException e) {
            send(s, "error", "message", "no se pudo guardar items.yml: " + e.getMessage());
        }
    }

    private void importCe(CommandSender s, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("conditionalevents")) throw new IllegalArgumentException("uso: /omni import conditionalevents");
        File src = new File(plugin.getDataFolder().getParentFile(), "ConditionalEvents");
        File out = new File(plugin.getDataFolder(), "rules/importado");
        ConditionalEventsImporter.Report rep = new ConditionalEventsImporter().importFolder(src, out);
        for (String w : rep.notes()) raw(s, "<yellow>• <gray>" + esc(w));
        send(s, "import-done", "events", rep.events(), "review", rep.review(), "folder", "rules/importado");
    }

    private void selftest(CommandSender s) {
        List<SelfTest.Result> res = SelfTest.run();
        long fail = res.stream().filter(r -> !r.ok()).count();
        for (SelfTest.Result r : res) raw(s, (r.ok() ? "<green>✔ " : "<red>✘ ") + esc(r.name()) + " <dark_gray>" + esc(r.detail()));
        Problems p = plugin.validate();
        raw(s, (p.errors() == 0 ? "<green>✔ " : "<red>✘ ") + "validate: " + p.errors() + " errores, " + p.warnings() + " avisos");
        send(s, fail == 0 && p.errors() == 0 ? "selftest-ok" : "selftest-fail", "passed", res.size() - fail, "total", res.size());
    }

    // ------------------------------------------------------------------ autocompletado

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> opts = new ArrayList<>();
        if (args.length == 1) {
            for (String k : SUBS.keySet()) if (allowed(s, k)) opts.add(k);
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (!SUBS.containsKey(sub) || !allowed(s, sub)) return List.of();
            int i = args.length - 1;
            switch (sub) {
                case "info", "enable", "disable" -> {
                    if (i == 1) opts.addAll(plugin.rules().names());
                }
                case "debug" -> {
                    if (i == 1) {
                        opts.add("all");
                        opts.add("off");
                        opts.addAll(plugin.rules().names());
                    }
                }
                case "call" -> {
                    if (i == 1) for (Rule r : plugin.rules().byKind(TriggerType.Kind.CALL)) opts.add(r.name());
                    if (i == 2) Bukkit.getOnlinePlayers().forEach(p -> opts.add(p.getName()));
                }
                case "var" -> {
                    if (i == 1) opts.addAll(List.of("get", "set", "del", "list"));
                    if (i == 2) {
                        opts.add("global");
                        Bukkit.getOnlinePlayers().forEach(p -> opts.add(p.getName()));
                    }
                }
                case "cooldown", "limit" -> {
                    if (i == 1) opts.add("reset");
                    if (i == 2) opts.addAll(plugin.rules().names());
                    if (i == 3) {
                        opts.add("global");
                        Bukkit.getOnlinePlayers().forEach(p -> opts.add(p.getName()));
                    }
                }
                case "item" -> {
                    if (i == 1) opts.addAll(List.of("save", "delete", "list"));
                    if (i == 2 && args[1].equalsIgnoreCase("delete")) opts.addAll(plugin.savedItems().names());
                }
                case "import" -> {
                    if (i == 1) opts.add("conditionalevents");
                }
                default -> { }
            }
        }
        String cur = args[args.length - 1].toLowerCase(Locale.ROOT);
        opts.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(cur));
        return opts;
    }
}
