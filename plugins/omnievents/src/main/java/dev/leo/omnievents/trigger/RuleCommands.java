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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Comandos reales declarados por reglas con trigger: command (H6 CA3). */
public final class RuleCommands {
    private static final String PREFIX = "omnievents";

    private final OmniEvents plugin;
    private final List<RuleCommand> registered = new ArrayList<>();

    public RuleCommands(OmniEvents plugin) {
        this.plugin = plugin;
    }

    private final class RuleCommand extends Command {
        private final String ruleName;
        private final List<List<String>> tab;

        RuleCommand(String name, Rule rule, ConfigurationSection c) {
            super(name);
            this.ruleName = rule.name();
            if (c != null) {
                setAliases(c.getStringList("aliases"));
                setDescription(c.getString("description", "Comando de OmniEvents"));
                setUsage(c.getString("usage", "/" + name));
                String perm = c.getString("permission");
                if (perm != null) setPermission(perm);
            }
            List<List<String>> t = new ArrayList<>();
            if (c != null) for (Object o : c.getList("tab", List.of())) {
                List<String> opts = new ArrayList<>();
                if (o instanceof List<?> l) for (Object x : l) opts.add(String.valueOf(x));
                else opts.add(String.valueOf(o));
                t.add(opts);
            }
            this.tab = t;
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            Rule rule = plugin.rules().get(ruleName);
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

    public void register() {
        unregister();
        CommandMap map = Bukkit.getCommandMap();
        for (Rule r : plugin.rules().byKind(TriggerType.Kind.COMMAND)) {
            if (!r.enabled()) continue;
            ConfigurationSection c = r.section().getConfigurationSection("command");
            String name = c == null ? r.section().getString("command") : c.getString("name");
            if (name == null) continue;
            RuleCommand cmd = new RuleCommand(name.toLowerCase(Locale.ROOT), r, c);
            map.register(PREFIX, cmd);
            registered.add(cmd);
        }
        sync();
    }

    public void unregister() {
        if (registered.isEmpty()) return;
        Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
        for (RuleCommand c : registered) c.unregister(Bukkit.getCommandMap());
        removeFrom(known, new ArrayList<>(registered));
        registered.clear();
        sync();
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

    /** Reenvía el árbol de comandos a los jugadores para que vean los nuevos al autocompletar. */
    private void sync() {
        try {
            Bukkit.getServer().getClass().getMethod("syncCommands").invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // sin syncCommands: los jugadores verán los cambios al reconectar
        }
        for (Player p : Bukkit.getOnlinePlayers()) p.updateCommands();
    }

    public int count() {
        return registered.size();
    }
}
