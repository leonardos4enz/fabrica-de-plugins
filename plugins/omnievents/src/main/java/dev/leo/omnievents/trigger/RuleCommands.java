package dev.leo.omnievents.trigger;

import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.rule.Rule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Comandos reales declarados por reglas con trigger: command (H6 CA3).
 * <p>
 * Registrar o desregistrar cambia el árbol de comandos del servidor, que Paper recorre en otro hilo al mandarlo a los
 * jugadores: dos cambios seguidos chocaban (004 F-01, spec 005). Por eso solo se registra si cambia el conjunto de
 * comandos, y una regla desactivada deja su comando registrado pero oculto ({@link RuleCommand#testPermissionSilent}).
 */
public final class RuleCommands {
    private static final String PREFIX = "omnievents";

    private final OmniEvents plugin;
    private final List<RuleCommand> registered = new ArrayList<>();
    /** Reglas cuyos comandos vio el jugador en el último árbol que se mandó. */
    private Set<String> visible = Set.of();

    public RuleCommands(OmniEvents plugin) {
        this.plugin = plugin;
    }

    /** Lo que define a un comando de regla: si dos listas de Spec son iguales, no hay que registrar nada. */
    record Spec(String ruleName, String name, List<String> aliases, String description, String usage,
                String permission, List<List<String>> tab) {

        /** La Spec de una regla, o null si no declara nombre de comando. */
        static Spec of(String ruleName, ConfigurationSection rule) {
            ConfigurationSection c = rule.getConfigurationSection("command");
            String name = c == null ? rule.getString("command") : c.getString("name");
            if (name == null) return null;
            name = name.toLowerCase(Locale.ROOT);
            List<List<String>> tab = new ArrayList<>();
            if (c != null) for (Object o : c.getList("tab", List.of())) {
                List<String> opts = new ArrayList<>();
                if (o instanceof List<?> l) for (Object x : l) opts.add(String.valueOf(x));
                else opts.add(String.valueOf(o));
                tab.add(List.copyOf(opts));
            }
            return new Spec(ruleName, name,
                    c == null ? List.of() : List.copyOf(c.getStringList("aliases")),
                    c == null ? "Comando de OmniEvents" : c.getString("description", "Comando de OmniEvents"),
                    c == null ? "/" + name : c.getString("usage", "/" + name),
                    c == null ? null : c.getString("permission"),
                    List.copyOf(tab));
        }
    }

    private final class RuleCommand extends Command {
        private final Spec spec;

        RuleCommand(Spec spec) {
            super(spec.name());
            this.spec = spec;
            setAliases(spec.aliases());
            setDescription(spec.description());
            setUsage(spec.usage());
            if (spec.permission() != null) setPermission(spec.permission());
        }

        private boolean ruleEnabled() {
            Rule rule = plugin.rules().get(spec.ruleName());
            return rule != null && rule.enabled();
        }

        /**
         * Paper filtra con esto el árbol que manda a cada jugador y decide si el comando se puede usar: una regla
         * desactivada oculta su comando sin sacarlo del árbol (spec 005, H2).
         */
        @Override
        public boolean testPermissionSilent(@NotNull CommandSender target) {
            return ruleEnabled() && super.testPermissionSilent(target);
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            Rule rule = plugin.rules().get(spec.ruleName());
            if (rule == null || !rule.enabled()) {
                sender.sendMessage(plugin.messages().get("command-disabled"));
                return true;
            }
            if (getPermission() != null && !sender.hasPermission(getPermission())) {
                sender.sendMessage(plugin.messages().get("no-permission"));
                return true;
            }
            Map<String, Object> b = new HashMap<>();
            Map<String, Object> cmd = new HashMap<>();
            List<Object> argList = new ArrayList<>(List.of((Object[]) args));
            cmd.put("name", getName());
            cmd.put("label", label.toLowerCase(Locale.ROOT));
            cmd.put("args", argList);
            cmd.put("full", label + (args.length == 0 ? "" : " " + String.join(" ", args)));
            b.put("cmd", cmd);
            b.put("args", argList);
            b.put("sender", sender);
            if (sender instanceof Player p) {
                b.put("player", p);
                b.put("world", p.getWorld());
                b.put("location", p.getLocation());
            }
            plugin.rules().fire(rule, b, null);
            return true;
        }

        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            List<List<String>> tab = spec.tab();
            if (args.length == 0 || args.length > tab.size()) return List.of();
            String cur = args[args.length - 1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String opt : tab.get(args.length - 1)) {
                if (opt.equals("{players}")) {
                    for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(cur)) out.add(p.getName());
                } else if (opt.toLowerCase(Locale.ROOT).startsWith(cur)) {
                    out.add(opt);
                }
            }
            return out;
        }
    }

    /** Arranque y reload: los comandos de las reglas comando habilitadas. */
    public void register() {
        Set<Spec> wanted = new LinkedHashSet<>();
        for (Rule r : plugin.rules().byKind(TriggerType.Kind.COMMAND)) {
            if (!r.enabled()) continue;
            Spec s = Spec.of(r.name(), r.section());
            if (s != null) wanted.add(s);
        }
        reconcile(wanted);
    }

    /**
     * Tras /omni enable|disable: los ya registrados siguen (ocultos si su regla se desactivó) y se agregan los de
     * reglas comando recién habilitadas. Lo normal es que no cambie nada y solo cambie qué ve el jugador.
     */
    public void refresh() {
        Set<Spec> wanted = new LinkedHashSet<>();
        for (RuleCommand c : registered) {
            Rule r = plugin.rules().get(c.spec.ruleName());
            if (r != null && r.trigger().kind() == TriggerType.Kind.COMMAND && c.spec.equals(Spec.of(r.name(), r.section()))) wanted.add(c.spec);
        }
        for (Rule r : plugin.rules().byKind(TriggerType.Kind.COMMAND)) {
            if (!r.enabled()) continue;
            Spec s = Spec.of(r.name(), r.section());
            if (s != null) wanted.add(s);
        }
        reconcile(wanted);
    }

    private void reconcile(Set<Spec> wanted) {
        Set<Spec> current = new LinkedHashSet<>();
        for (RuleCommand c : registered) current.add(c.spec);
        if (!current.equals(wanted)) {
            removeAll();
            CommandMap map = Bukkit.getCommandMap();
            for (Spec s : wanted) {
                RuleCommand cmd = new RuleCommand(s);
                map.register(PREFIX, cmd);
                registered.add(cmd);
            }
            sync();
            return;
        }
        if (!visibleNow().equals(visible)) sync();
    }

    /** Apagado: quita todos los comandos. */
    public void unregister() {
        if (registered.isEmpty()) return;
        removeAll();
        sync();
    }

    private void removeAll() {
        if (registered.isEmpty()) return;
        Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
        for (RuleCommand c : registered) c.unregister(Bukkit.getCommandMap());
        removeFrom(known, new ArrayList<>(registered));
        registered.clear();
    }

    private Set<String> visibleNow() {
        Set<String> v = new HashSet<>();
        for (RuleCommand c : registered) if (c.ruleEnabled()) v.add(c.spec.ruleName());
        return v;
    }

    /**
     * Quita los comandos del mapa por clave. El mapa de Paper no permite borrar desde su iterador
     * (Regresión, servidor real 001 F-03): se juntan las claves y se borran con remove(clave).
     */
    static void removeFrom(Map<String, Command> known, List<? extends Command> commands) {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Command> e : known.entrySet()) {
            for (Command c : commands) if (e.getValue() == c) keys.add(e.getKey());
        }
        for (String k : keys) known.remove(k);
    }

    /**
     * Reenvía el árbol de comandos, una vez por jugador. syncCommands de Paper ya lo manda a todos: llamar además a
     * updateCommands lo duplicaba (spec 005, H1 CA2).
     */
    private void sync() {
        visible = visibleNow();
        try {
            Bukkit.getServer().getClass().getMethod("syncCommands").invoke(Bukkit.getServer());
            return;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // sin syncCommands: se manda a cada jugador
        }
        for (Player p : Bukkit.getOnlinePlayers()) p.updateCommands();
    }

    public int count() {
        return registered.size();
    }
}
