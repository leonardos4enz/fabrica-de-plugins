package dev.leo.omnievents.command;

import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.action.ExecContext;
import dev.leo.omnievents.data.DataStore;
import dev.leo.omnievents.data.ObjectData;
import dev.leo.omnievents.debug.SelfTest;
import dev.leo.omnievents.debug.Stats;
import dev.leo.omnievents.expr.ExprException;
import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Node;
import dev.leo.omnievents.expr.Scope;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.importer.ConditionalEventsImporter;
import dev.leo.omnievents.rule.Condition;
import dev.leo.omnievents.rule.Pack;
import dev.leo.omnievents.rule.Problems;
import dev.leo.omnievents.rule.Rule;
import dev.leo.omnievents.text.TextFormat;
import dev.leo.omnievents.trigger.StateService;
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
        SUBS.put("debug", "Traza en vivo: <regla|all|off|export>");
        SUBS.put("check", "Revisa una regla ahora, sin efectos: <regla> [jugador]");
        SUBS.put("test", "Corre las pruebas (tests:) de las reglas, sin efectos: [regla]");
        SUBS.put("pack", "Paquetes de reglas: list|info|enable|disable <paquete>");
        SUBS.put("run", "Dispara una regla: <regla> <jugador> [--simular]");
        SUBS.put("state", "Quién está en el estado de una regla state: <regla>");
        SUBS.put("eval", "Evalúa una expresión con tu contexto");
        SUBS.put("call", "Ejecuta una regla call: <regla> [jugador] [k=v…]");
        SUBS.put("var", "Variables: get|set|del|list <jugador|global> [nombre] [valor] [--expire 10m]");
        SUBS.put("data", "Variables de un objeto: [bloque|entidad|mano]");
        SUBS.put("cooldown", "reset <regla> [jugador|global]");
        SUBS.put("limit", "reset <regla> [jugador|global]");
        SUBS.put("stats", "Rendimiento por regla, oyentes y tareas");
        SUBS.put("cpu", "Perfilador por regla, condición y acción: [on|off|reset]");
        SUBS.put("triggers", "Disparadores disponibles");
        SUBS.put("actions", "Acciones disponibles");
        SUBS.put("item", "save|delete|list <nombre>: ítems para saved:nombre");
        SUBS.put("import", "import conditionalevents");
        SUBS.put("selftest", "Autoprueba del motor en este servidor");
        SUBS.put("schema", "Escribe el esquema JSON y la referencia para el editor");
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
                case "check" -> check(s, a);
                case "test" -> test(s, a);
                case "pack" -> pack(s, a);
                case "run" -> runRule(s, a);
                case "state" -> state(s, a);
                case "cpu" -> cpu(s, a);
                case "eval" -> eval(s, a);
                case "call" -> call(s, a);
                case "var" -> var(s, a);
                case "data" -> data(s, a);
                case "cooldown" -> resetUsage(s, a, true);
                case "limit" -> resetUsage(s, a, false);
                case "stats" -> stats(s);
                case "triggers" -> triggers(s);
                case "actions" -> raw(s, "<gold>Acciones:</gold> <gray>" + esc(String.join(", ", plugin.actions().names())));
                case "item" -> item(s, a);
                case "import" -> importCe(s, a);
                case "selftest" -> selftest(s);
                case "schema" -> schema(s);
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
        for (Condition c : r.conditions()) {
            raw(s, "<gray>  si <white>" + esc(c.source()) + (c.deny().isEmpty() ? "" : "</white> <dark_gray>·</dark_gray> deny: " + c.deny().size() + " acciones"));
        }
        if (r.state() != null) for (String line : StateService.deps(r.state())) raw(s, "<gray>" + esc(line));
        if (r.budgetNanos() > 0) raw(s, "<gray>Presupuesto: <white>" + Values.formatNumber(r.budgetNanos() / 1e6) + " ms");
        if (r.cooldownMs() > 0) raw(s, "<gray>Enfriamiento: <white>" + Durations.format(r.cooldownMs()) + (r.cooldownGlobal() ? " (global)" : " (por jugador)"));
        if (r.limit() > 0) raw(s, "<gray>Límite: <white>" + r.limit() + " usos " + (r.limitGlobal() ? "globales" : "por jugador") + ", reinicio " + r.limitReset().name().toLowerCase(Locale.ROOT));
        raw(s, "<gray>Acciones: <white>" + r.actionCount() + "</white> · disparos <white>" + st.firedCount() + "</white> · aciertos <white>"
                + st.passedCount() + "</white> · errores <white>" + st.errorCount() + "</white> · promedio <white>"
                + Values.formatNumber(Values.round(st.avgMillis(), 3)) + " ms");
    }

    private void toggle(CommandSender s, String[] a, boolean on) {
        Rule r = rule(s, a, 0);
        r.setEnabled(on);
        reRegister();
        send(s, on ? "rule-enabled" : "rule-disabled", "rule", r.name());
        send(s, "toggle-note");
    }

    /** Re-registrar: una regla activada necesita su oyente, una desactivada lo libera (H6 CA5). */
    private void reRegister() {
        plugin.listeners().register(plugin.rules().all(), new Problems());
        plugin.timers().start();
        plugin.states().register(plugin.rules().all());
        plugin.regionService().register(plugin.rules().all());
        // Solo registra si cambió el conjunto de comandos; si no, como mucho reenvía el árbol una vez (spec 005).
        plugin.ruleCommands().refresh();
    }

    // ------------------------------------------------------------------ paquetes (spec 011, H4)

    private void pack(CommandSender s, String[] a) {
        String what = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        if (what.equals("list")) {
            packList(s);
            return;
        }
        if (!List.of("info", "enable", "disable").contains(what)) {
            throw new IllegalArgumentException("uso: /omni pack list|info|enable|disable <paquete>" + dev.leo.omnievents.util.Suggest.hint(what, List.of("list", "info", "enable", "disable")));
        }
        if (a.length < 2) throw new IllegalArgumentException("falta el nombre del paquete");
        Pack p = plugin.rules().pack(a[1]);
        if (p == null) {
            List<String> names = plugin.rules().packs().stream().map(Pack::name).toList();
            throw new IllegalArgumentException("no existe el paquete '" + a[1] + "'" + dev.leo.omnievents.util.Suggest.hint(a[1], names));
        }
        if (what.equals("info")) {
            packInfo(s, p);
            return;
        }
        if (!p.loaded()) throw new IllegalArgumentException("el paquete " + p.name() + " no carga: " + p.error());
        boolean on = what.equals("enable");
        for (String n : p.rules()) {
            Rule r = plugin.rules().get(n);
            if (r != null) r.setEnabled(on);
        }
        reRegister();
        send(s, on ? "pack-enabled" : "pack-disabled", "pack", p.name(), "rules", p.rules().size());
        send(s, "pack-note");
    }

    private void packList(CommandSender s) {
        List<Pack> ps = new ArrayList<>(plugin.rules().packs());
        if (ps.isEmpty()) {
            send(s, "pack-none");
            return;
        }
        ps.sort((x, y) -> x.name().compareToIgnoreCase(y.name()));
        raw(s, "<gold>Paquetes</gold> <gray>(" + ps.size() + ")");
        for (Pack p : ps) {
            String head = "<white><click:run_command:/omni pack info " + esc(p.name()) + "><hover:show_text:'Ver detalle'>" + esc(p.name())
                    + "</hover></click></white> <gray>" + esc(p.version());
            if (!p.loaded()) {
                raw(s, "<red>✘ " + head + " <dark_gray>·</dark_gray> <red>no carga: " + esc(p.error()));
                continue;
            }
            long active = p.rules().stream().map(plugin.rules()::get).filter(r -> r != null && r.enabled()).count();
            raw(s, (active > 0 || p.rules().isEmpty() ? "<green>● " : "<red>○ ") + head + " <dark_gray>·</dark_gray> <gray>" + p.rules().size() + " reglas"
                    + (active < p.rules().size() ? " (" + active + " activas)" : "") + " <dark_gray>·</dark_gray> " + p.templates().size() + " plantillas");
        }
    }

    private void packInfo(CommandSender s, Pack p) {
        raw(s, "<gold>" + esc(p.name()) + "</gold> <gray>" + esc(p.version()) + (p.loaded() ? "" : " <red>· no carga: " + esc(p.error())));
        if (p.description() != null) raw(s, "<gray>" + esc(p.description()));
        raw(s, "<gray>Carpeta: <white>rules/" + esc(p.folder()) + "</white>" + (p.author() == null ? "" : " · autor <white>" + esc(p.author()))
                + (p.enabled() ? "" : " · <red>enabled: false"));
        String saved = plugin.packVersions().get(p.name());
        raw(s, "<gray>Versión guardada: <white>" + (saved == null ? "ninguna (aún no corrió)" : esc(saved)));
        if (!p.params().isEmpty()) {
            List<String> ps = new ArrayList<>();
            p.params().forEach((k, v) -> ps.add(k + " = " + Values.str(v)));
            raw(s, "<gray>Parámetros: <white>" + esc(String.join(", ", ps)));
        }
        if (p.requiresOmni() != null || !p.requiresPacks().isEmpty()) {
            List<String> rq = new ArrayList<>();
            if (p.requiresOmni() != null) rq.add("omnievents " + p.requiresOmni());
            p.requiresPacks().forEach((k, v) -> rq.add(k + " " + v));
            raw(s, "<gray>Requiere: <white>" + esc(String.join(", ", rq)));
        }
        if (!p.migrations().isEmpty()) {
            raw(s, "<gray>Migraciones: <white>" + esc(String.join(", ", p.migrations().stream().map(m -> m.version() + " (" + m.rule().actionCount() + " acciones)").toList())));
        }
        if (!p.rules().isEmpty()) raw(s, "<gray>Reglas: <white>" + esc(String.join(", ", p.rules())));
        if (!p.templates().isEmpty()) raw(s, "<gray>Plantillas: <white>" + esc(String.join(", ", p.templates())));
    }

    private void debug(CommandSender s, String[] a) {
        if (a.length == 0) {
            String w = plugin.debugger().watching(s);
            send(s, "debug-status", "rule", w == null ? "off" : w.equals("*") ? "all" : w);
            return;
        }
        String arg = a[0];
        if (arg.equalsIgnoreCase("export")) {
            export(s);
            return;
        }
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
        return playerContext(s, s instanceof Player p ? p : null);
    }

    /** Contexto de /omni eval, check y run: el jugador, su mundo, lugar, ítem en la mano y lo que mira. */
    private ExecContext playerContext(CommandSender s, Player p) {
        Map<String, Object> b = new HashMap<>();
        b.put("sender", s);
        if (p != null) {
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
                Map<String, Object> live = new TreeMap<>(h.live());
                raw(s, "<gold>Variables de " + esc(who) + "</gold> <gray>(" + live.size() + ")");
                live.forEach((k, v) -> raw(s, "<gray>  " + esc(k) + " <dark_gray>=</dark_gray> <white>" + esc(Node.show(v)) + expiresIn(h.expiresAt(k))));
            }
            case "get" -> {
                String n = need(a, 2, "nombre");
                raw(s, "<gray>" + esc(who) + "." + esc(n) + " <dark_gray>=</dark_gray> <white>" + esc(Node.show(h.get(n))) + expiresIn(h.get(n) == null ? 0 : h.expiresAt(n)));
            }
            case "set" -> {
                String n = need(a, 2, "nombre");
                List<String> rest = new ArrayList<>(Arrays.asList(a).subList(Math.min(3, a.length), a.length));
                long expireAt = 0;
                int flag = rest.indexOf("--expire");
                if (flag >= 0) {
                    // Spec 009, H1 CA5: /omni var set <quién> <nombre> <valor…> --expire 10m
                    if (flag + 1 >= rest.size()) throw new IllegalArgumentException("--expire necesita una duración (10m, 1h)");
                    long ms = Durations.parseMillis(rest.get(flag + 1));
                    if (ms <= 0) throw new IllegalArgumentException("--expire debe ser una duración positiva");
                    expireAt = plugin.data().now() + ms;
                    rest.subList(flag, flag + 2).clear();
                }
                String src = String.join(" ", rest);
                if (src.isBlank()) throw new IllegalArgumentException("falta el valor (una expresión: 10, 'texto', pvar_of('Leo','x') + 1)");
                Object v = Expression.compile(src).eval(senderContext(s));
                h.put(n, DataStore.storable(v), expireAt);
                plugin.states().varChanged(global ? Scope.GLOBAL : Scope.PLAYER, id, n);
                if (!loaded) plugin.data().writeOffline(h);
                plugin.getLogger().info(s.getName() + " asignó " + who + "." + n + " = " + Values.str(v));
                send(s, "var-set", "target", who, "name", n, "value", Node.show(v));
            }
            case "del" -> {
                String n = need(a, 2, "nombre");
                boolean had = h.remove(n);
                plugin.states().varChanged(global ? Scope.GLOBAL : Scope.PLAYER, id, n);
                if (!loaded) plugin.data().writeOffline(h);
                plugin.getLogger().info(s.getName() + " borró " + who + "." + n);
                send(s, had ? "var-deleted" : "var-missing", "target", who, "name", n);
            }
            default -> throw new IllegalArgumentException("usa get, set, del o list");
        }
    }

    /** « (caduca en 4m 12s)» o nada (spec 009). */
    private String expiresIn(long at) {
        if (at == 0) return "";
        long s = Math.max(0, (at - plugin.data().now()) / 1000);
        String left = s >= 3600 ? s / 3600 + "h " + s % 3600 / 60 + "m" : s >= 60 ? s / 60 + "m " + s % 60 + "s" : s + "s";
        return " <dark_gray>(caduca en " + left + ")";
    }

    /** /omni data [bloque|entidad|mano]: variables del objeto (spec 009, H3 CA6). */
    private void data(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) throw new IllegalArgumentException("solo un jugador: mira el bloque o la entidad, o toma el ítem");
        String what = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        Object target;
        String label;
        switch (what) {
            case "bloque", "block" -> {
                target = p.getTargetBlockExact(6);
                label = "el bloque que miras";
            }
            case "entidad", "entity" -> {
                target = p.getTargetEntity(6);
                label = "la entidad que miras";
            }
            case "mano", "hand", "item" -> {
                target = p.getInventory().getItemInMainHand();
                label = "el ítem en tu mano";
            }
            case "" -> {
                target = p.getTargetBlockExact(6);
                label = "el bloque que miras";
                if (target == null) {
                    target = p.getInventory().getItemInMainHand();
                    label = "el ítem en tu mano";
                }
            }
            default -> throw new IllegalArgumentException("usa bloque, entidad o mano");
        }
        if (target == null || target instanceof ItemStack st && st.getType().isAir()) throw new IllegalArgumentException("no hay " + label.replaceFirst("^el |^la ", "") + " (a 6 bloques como máximo)");
        Map<String, ObjectData.Entry> entries = plugin.objectData().entries(target);
        raw(s, "<gold>Variables de " + esc(label) + "</gold> <gray>(" + entries.size() + ")");
        entries.forEach((k, e) -> raw(s, "<gray>  " + esc(k) + " <dark_gray>=</dark_gray> <white>" + esc(Node.show(e.value())) + expiresIn(e.expireAt())));
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
                + " temporizadores · " + plugin.states().listenerCount() + " oyentes y " + plugin.states().taskCount() + " tareas de estado · " + plugin.ruleCommands().count() + " comandos · " + plugin.regionService().count() + " oyentes de regiones · " + plugin.data().loadedPlayers().size() + " jugadores en memoria");
        raw(s, "<dark_gray>  integraciones: " + esc(plugin.integrationsLine()));
        for (String l : plugin.listeners().describe()) raw(s, "<dark_gray>  " + esc(l));
        raw(s, "<dark_gray>  variables: " + plugin.data().pendingExpiries() + " caducidades en cola · limpieza de datos de bloques " + (plugin.blockCleaner().registered() ? "activa" : "apagada"));
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
        plugin.states().counters().forEach((name, c) -> raw(s, "<white>" + esc(name) + "</white> <gray>· estado · " + c[0] + " revisiones · "
                + c[1] + " cambios · " + c[2] + " dentro"));
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

    // ------------------------------------------------------------------ herramientas de autor (spec 006)

    private Player onlinePlayer(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p;
        List<String> online = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(x -> online.add(x.getName()));
        throw new IllegalArgumentException("el jugador '" + name + "' no está conectado" + dev.leo.omnievents.util.Suggest.hint(name, online));
    }

    /** Una línea de traza con el color de /omni debug. */
    private static void traceLine(CommandSender s, String line) {
        String color = line.contains("✘") ? "<red>" : line.contains("✔") ? "<green>" : line.startsWith("→") ? "<yellow>" : "<gray>";
        raw(s, color + esc(line));
    }

    /** /omni check (H3): evalúa todo con sus valores, sin ejecutar acciones. */
    private void check(CommandSender s, String[] a) {
        Rule r = rule(s, a, 0);
        Player p = a.length > 1 ? onlinePlayer(a[1]) : s instanceof Player sp ? sp : null;
        raw(s, "<gold>Revisión de <white>" + esc(r.name()) + "</white></gold> <gray>· " + esc(r.trigger().name())
                + (p == null ? " · sin jugador" : " · para " + esc(p.getName())) + (r.enabled() ? "" : " · <red>regla inactiva</red>")
                + " <dark_gray>(no se ejecuta nada)");
        List<String> lines = r.state() != null ? plugin.states().check(r, playerContext(s, p)) : plugin.rules().check(r, playerContext(s, p));
        for (String line : lines) traceLine(s, line);
    }

    /** /omni test [regla] (spec 010, H3): corre los casos de tests sin efectos y muestra los que fallan. */
    private void test(CommandSender s, String[] a) {
        dev.leo.omnievents.rule.RuleTester tester = plugin.tester();
        if (a.length > 0) {
            Rule r = rule(s, a, 0);
            if (r.tests().isEmpty()) {
                send(s, "test-rule-none", "rule", r.name());
                return;
            }
            List<dev.leo.omnievents.rule.RuleTester.Result> res = tester.run(r);
            showTests(s, r, res);
            testSummary(s, new dev.leo.omnievents.rule.RuleTester.Summary(res, 0));
            return;
        }
        dev.leo.omnievents.rule.RuleTester.Summary sum = tester.runAll(plugin.rules().all());
        if (sum.results().isEmpty()) {
            send(s, "test-none");
            return;
        }
        Map<Rule, List<dev.leo.omnievents.rule.RuleTester.Result>> byRule = new java.util.LinkedHashMap<>();
        for (var r : sum.results()) byRule.computeIfAbsent(r.rule(), k -> new ArrayList<>()).add(r);
        byRule.forEach((r, res) -> showTests(s, r, res));
        testSummary(s, sum);
    }

    private static void showTests(CommandSender s, Rule rule, List<dev.leo.omnievents.rule.RuleTester.Result> res) {
        long ok = res.stream().filter(dev.leo.omnievents.rule.RuleTester.Result::ok).count();
        raw(s, (ok == res.size() ? "<green>✔ " : "<red>✘ ") + "<white>" + esc(rule.name()) + "</white> <gray>" + ok + "/" + res.size());
        for (var r : res) {
            if (r.ok()) continue;
            raw(s, "<red>  ✘ «" + esc(r.test().name()) + "»");
            for (String f : r.failures()) raw(s, "<red>    • " + esc(f));
            raw(s, "<dark_gray>    traza:");
            for (String line : r.trace()) traceLine(s, "    " + line);
        }
    }

    private void testSummary(CommandSender s, dev.leo.omnievents.rule.RuleTester.Summary sum) {
        String untested = sum.untested() == 0 ? "" : " · " + sum.untested() + (sum.untested() == 1 ? " regla" : " reglas") + " sin pruebas";
        if (sum.failed() == 0) send(s, "test-summary-ok", "total", sum.results().size(), "untested", untested);
        else send(s, "test-summary-fail", "total", sum.results().size(), "failed", sum.failed(), "passed", sum.passed(), "untested", untested);
    }

    /** /omni state <regla> (spec 008, H5 CA4). */
    private void state(CommandSender s, String[] a) {
        Rule r = rule(s, a, 0);
        if (r.state() == null) throw new IllegalArgumentException("'" + r.name() + "' no es una regla con trigger: state");
        raw(s, "<gold>Estado de <white>" + esc(r.name()) + "</white></gold>");
        for (String line : plugin.states().describe(r)) traceLine(s, line);
    }

    /** /omni run (H4): dispara la regla para un jugador, o la simula con --simular. */
    private void runRule(CommandSender s, String[] a) {
        List<String> args = new ArrayList<>(Arrays.asList(a));
        boolean simulate = args.removeIf(x -> x.equalsIgnoreCase("--simular"));
        if (args.size() < 2) throw new IllegalArgumentException("uso: /omni run <regla> <jugador> [--simular]");
        Rule r = rule(s, args.toArray(new String[0]), 0);
        Player p = onlinePlayer(args.get(1));
        ExecContext ctx = playerContext(s, p);
        if (simulate) {
            for (String line : plugin.rules().simulate(r, ctx).lines()) traceLine(s, line);
            return;
        }
        if (!r.enabled()) throw new IllegalArgumentException("la regla '" + r.name() + "' está desactivada (/omni enable " + r.name() + ")");
        plugin.getLogger().info(s.getName() + " ejecutó la regla " + r.name() + " para " + p.getName() + " (/omni run)");
        Map<String, Object> b = new HashMap<>(ctx.flatBindings());
        b.remove("sender");
        boolean passed = plugin.rules().run(r, new ExecContext(plugin, r, b), 0, null);
        send(s, passed ? "run-done" : "run-not-passed", "rule", r.name(), "player", p.getName());
    }

    /** /omni cpu (H6, H7 CA3). */
    private void cpu(CommandSender s, String[] a) {
        var prof = plugin.profiler();
        String op = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (op) {
            case "on" -> {
                prof.start();
                send(s, "cpu-on", "n", prof.sampleEvery());
                return;
            }
            case "off" -> {
                prof.stop();
                send(s, "cpu-off");
                return;
            }
            case "reset" -> {
                prof.reset();
                send(s, "cpu-reset");
                return;
            }
            case "" -> { }
            default -> throw new IllegalArgumentException("usa /omni cpu [on|off|reset]");
        }
        if (!prof.enabled() && prof.rules().isEmpty()) {
            send(s, "cpu-disabled");
        } else {
            showProfile(s, prof);
        }
        List<Rule> budgeted = new ArrayList<>();
        for (Rule r : plugin.rules().all()) if (r.budgetNanos() > 0) budgeted.add(r);
        if (budgeted.isEmpty()) return;
        budgeted.sort((x, y) -> x.name().compareToIgnoreCase(y.name()));
        raw(s, "<gold>Presupuestos</gold> <gray>(promedio reciente, se mide siempre)");
        for (Rule r : budgeted) {
            double budget = r.budgetNanos() / 1e6, recent = r.budget().recentMillis();
            String state = r.budget().warmingUp() ? "<gray>calentando (" + r.budget().fires() + " disparos)" : recent > budget ? "<red>✘ lo pasa" : "<green>✔ dentro";
            raw(s, "<white>" + esc(r.name()) + "</white> <gray>· " + Values.formatNumber(Values.round(recent, 3)) + " ms de "
                    + Values.formatNumber(budget) + " ms · " + state);
        }
    }

    private void showProfile(CommandSender s, dev.leo.omnievents.debug.Profiler prof) {
        long elapsed = prof.elapsedNanos();
        long mainNanos = 0;
        Map<String, Long> byTrigger = new TreeMap<>();
        for (var e : prof.rules().values()) {
            if (!e.async()) mainNanos += e.total().nanos();
            byTrigger.merge(e.trigger(), e.total().nanos(), Long::sum);
        }
        raw(s, "<gold>Perfilador</gold> <gray>· " + (prof.enabled() ? "<green>encendido</green>" : "<red>apagado</red>") + " · "
                + Durations.format(elapsed / 1_000_000) + " midiendo · 1 de cada " + prof.sampleEvery() + " disparos · OmniEvents ≈ <white>"
                + Values.formatNumber(Values.round(prof.mainThreadPercent(mainNanos), 3)) + " %</white> del hilo principal");
        List<Map.Entry<String, dev.leo.omnievents.debug.Profiler.RuleProfile>> rs = new ArrayList<>(prof.rules().entrySet());
        rs.sort((x, y) -> Long.compare(y.getValue().total().nanos(), x.getValue().total().nanos()));
        if (rs.isEmpty()) raw(s, "<gray>  Aún no hay disparos medidos.");
        int n = 0;
        for (var e : rs) {
            if (n++ >= 10) break;
            var rp = e.getValue();
            raw(s, "<white>" + esc(e.getKey()) + "</white> <gray>· " + esc(rp.trigger()) + " · " + rp.total().count() + " medidos · prom <white>"
                    + Values.formatNumber(Values.round(rp.total().avgMicros(), 1)) + " µs</white> · "
                    + (rp.async() ? "asíncrona" : Values.formatNumber(Values.round(prof.mainThreadPercent(rp.total().nanos()), 3)) + " %"));
            int k = 0;
            for (var part : rp.parts()) {
                if (k++ >= 3) break;
                raw(s, "<dark_gray>    " + esc(part.getKey()) + " · prom " + Values.formatNumber(Values.round(part.getValue().avgMicros(), 1))
                        + " µs · " + part.getValue().count() + " veces");
            }
        }
        if (byTrigger.size() > 1) {
            List<Map.Entry<String, Long>> ts = new ArrayList<>(byTrigger.entrySet());
            ts.sort((x, y) -> Long.compare(y.getValue(), x.getValue()));
            StringBuilder sb = new StringBuilder();
            for (var t : ts) sb.append(sb.isEmpty() ? "" : ", ").append(t.getKey()).append(" ").append(Values.formatNumber(Values.round(t.getValue() / 1e3 * prof.sampleEvery(), 0))).append(" µs");
            raw(s, "<gray>Por disparador (estimado): <white>" + esc(sb.toString()));
        }
    }

    private volatile boolean exporting;
    private volatile boolean writingSchema;

    /**
     * /omni schema (spec 007, H3 CA2): arma los textos en el hilo principal (lee el registro vivo, con lo que agregaron
     * otros plugins) y los escribe en un hilo asíncrono, de forma atómica. Solo dos nombres fijos en la carpeta del plugin.
     */
    private void schema(CommandSender s) {
        if (writingSchema) {
            send(s, "schema-busy");
            return;
        }
        Map<String, String> files = dev.leo.omnievents.schema.SchemaFiles.render(plugin.triggers(), plugin.actions());
        java.nio.file.Path folder = plugin.getDataFolder().toPath();
        writingSchema = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                dev.leo.omnievents.schema.SchemaFiles.write(folder, files);
                String base = "plugins/" + plugin.getDataFolder().getName() + "/";
                Bukkit.getScheduler().runTask(plugin, () -> send(s, "schema-done",
                        "schema", base + dev.leo.omnievents.schema.SchemaFiles.SCHEMA, "reference", base + dev.leo.omnievents.schema.SchemaFiles.REFERENCE));
            } catch (IOException e) {
                Bukkit.getScheduler().runTask(plugin, () -> send(s, "error", "message", "no se pudo escribir el esquema: " + e.getMessage()));
            } finally {
                writingSchema = false;
            }
        });
    }

    /** /omni debug export (H5): arma el texto en el hilo principal y lo escribe en otro hilo, sin red. */
    private void export(CommandSender s) {
        List<dev.leo.omnievents.debug.Trace> traces = plugin.debugger().recent();
        if (traces.isEmpty()) {
            send(s, "export-empty");
            return;
        }
        if (exporting) {
            send(s, "export-busy");
            return;
        }
        List<String> header = List.of(
                "OmniEvents " + plugin.getPluginMeta().getVersion() + " · " + Bukkit.getName() + " " + Bukkit.getVersion()
                        + " · Java " + System.getProperty("java.version"),
                "Fecha: " + java.time.LocalDateTime.now().withNano(0),
                "Reglas: " + plugin.rules().all().size() + " · oyentes " + plugin.listeners().count() + " · temporizadores "
                        + plugin.timers().count() + " · comandos " + plugin.ruleCommands().count());
        List<String> problems = new ArrayList<>();
        for (Problems.Problem pr : plugin.lastProblems().all()) {
            problems.add((pr.error() ? "ERROR " : "aviso ") + pr.file() + (pr.path().isEmpty() ? "" : " › " + pr.path()) + ": " + pr.message());
        }
        Map<String, String> yaml = new TreeMap<>();
        for (var t : traces) {
            Rule r = plugin.rules().get(t.rule());
            if (r == null || yaml.containsKey(r.name())) continue;
            org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
            y.set(r.name(), r.section());
            yaml.put(r.name(), "# " + r.file() + "\n" + y.saveToString());
        }
        String text = dev.leo.omnievents.debug.TraceExport.render(header, problems, traces, yaml);
        java.nio.file.Path folder = plugin.getDataFolder().toPath().resolve("trazas");
        exporting = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.nio.file.Path out = dev.leo.omnievents.debug.TraceExport.write(folder, text, java.time.LocalDateTime.now());
                String rel = plugin.getDataFolder().getParentFile().toPath().relativize(out).toString().replace('\\', '/');
                Bukkit.getScheduler().runTask(plugin, () -> send(s, "export-done", "file", "plugins/" + rel, "traces", traces.size()));
            } catch (IOException e) {
                Bukkit.getScheduler().runTask(plugin, () -> send(s, "error", "message", "no se pudo guardar la traza: " + e.getMessage()));
            } finally {
                exporting = false;
            }
        });
    }

    private void selftest(CommandSender s) {
        List<SelfTest.Result> res = new ArrayList<>(SelfTest.run());
        res.addAll(SelfTest.objectData(plugin.objectData()));
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
                case "test" -> {
                    if (i == 1) for (Rule r : plugin.rules().all()) if (!r.tests().isEmpty()) opts.add(r.name());
                }
                case "check", "run" -> {
                    if (i == 1) opts.addAll(plugin.rules().names());
                    if (i == 2) Bukkit.getOnlinePlayers().forEach(p -> opts.add(p.getName()));
                    if (i == 3 && sub.equals("run")) opts.add("--simular");
                }
                case "state" -> {
                    if (i == 1) for (Rule r : plugin.rules().byKind(TriggerType.Kind.STATE)) opts.add(r.name());
                }
                case "cpu" -> {
                    if (i == 1) opts.addAll(List.of("on", "off", "reset"));
                }
                case "pack" -> {
                    if (i == 1) opts.addAll(List.of("list", "info", "enable", "disable"));
                    if (i == 2 && !args[1].equalsIgnoreCase("list")) for (Pack p : plugin.rules().packs()) opts.add(p.name());
                }
                case "debug" -> {
                    if (i == 1) {
                        opts.add("all");
                        opts.add("off");
                        opts.add("export");
                        opts.addAll(plugin.rules().names());
                    }
                }
                case "call" -> {
                    if (i == 1) for (Rule r : plugin.rules().byKind(TriggerType.Kind.CALL)) opts.add(r.name());
                    if (i == 2) Bukkit.getOnlinePlayers().forEach(p -> opts.add(p.getName()));
                }
                case "data" -> {
                    if (i == 1) opts.addAll(List.of("bloque", "entidad", "mano"));
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
