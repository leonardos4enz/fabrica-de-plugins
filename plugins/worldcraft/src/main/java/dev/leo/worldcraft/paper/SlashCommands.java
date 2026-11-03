package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.command.SlashTable;
import dev.leo.worldcraft.command.WorldcraftCommand;
import dev.leo.worldcraft.config.SlashSettings;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Spec 039: los nombres de WorldEdit (//set, /brush, //…) como comandos de Worldcraft (plan 039 §1.3). Se registran en
 * el primer tick (ya cargó WorldEdit) y en /wc reload solo si cambió lo que se quiere; el árbol se manda una vez.
 */
public final class SlashCommands implements Listener {
    /** Los plugins a los que Worldcraft les quita los nombres (con worldedit: take). */
    static final Set<String> WORLDEDIT = Set.of("WorldEdit", "FastAsyncWorldEdit");

    private final Worldcraft plugin;
    /** Etiqueta → el nuestro registrado. */
    private final Map<String, Slash> registered = new LinkedHashMap<>();
    /** Etiqueta → el de WorldEdit que se sacó (para devolverlo). */
    private final Map<String, Command> taken = new LinkedHashMap<>();
    /** Lo que se pidió la última vez (para no registrar de nuevo si no cambió: lección 004 F-01). */
    private Object wanted;

    public SlashCommands(Worldcraft plugin) {
        this.plugin = plugin;
    }

    /** Un //x: corre /wc con los argumentos traducidos. */
    final class Slash extends Command implements PluginIdentifiableCommand {
        final SlashTable.Entry entry;

        Slash(SlashTable.Entry entry, String permission) {
            super(entry.label(), "Worldcraft: /wc " + String.join(" ", entry.to()), "/" + entry.label(), List.of());
            this.entry = entry;
            setPermission(permission);
        }

        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            PluginCommand wc = plugin.getCommand("worldcraft");
            return plugin.command().onCommand(sender, wc != null ? wc : this, "wc", SlashTable.translate(entry, args));
        }

        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            PluginCommand wc = plugin.getCommand("worldcraft");
            List<String> out = plugin.command().onTabComplete(sender, wc != null ? wc : this, "wc", SlashTable.complete(entry, args));
            return out == null ? List.of() : out;
        }

        @Override
        public @NotNull Plugin getPlugin() {
            return plugin;
        }
    }

    /** Registra lo que piden los ajustes (nada si están apagados) y manda el árbol una vez si algo cambió. */
    public void apply(SlashSettings settings) {
        Object want = List.of(settings.enabled(), settings.takeFromWorldEdit(), settings.skip(), Set.copyOf(worldEditPlugins()));
        if (want.equals(wanted)) return;
        wanted = want;
        boolean had = !registered.isEmpty();
        removeAll();
        if (!settings.enabled()) {
            if (had) sync();
            plugin.getLogger().info("WorldEdit-style commands (//set...) are off (slash_commands.enabled): only /wc");
            return;
        }
        CommandMap map = Bukkit.getCommandMap();
        Map<String, Command> known = map.getKnownCommands();
        int fromWorldEdit = 0;
        List<String> leftToWorldEdit = new ArrayList<>();
        Map<String, List<String>> others = new TreeMap<>();
        for (SlashTable.Entry e : SlashTable.builtin().entries().values()) {
            String label = e.label();
            if (settings.skip().contains(label)) continue;
            String perm = WorldcraftCommand.permission(e.sub());
            if (perm == null) continue;
            Command existing = known.get(label);
            if (existing != null) {
                String owner = owner(existing, label, known);
                if (owner != null && WORLDEDIT.contains(owner)) {
                    if (!settings.takeFromWorldEdit()) {
                        leftToWorldEdit.add("/" + label);
                        continue;
                    }
                    known.remove(label);
                    taken.put(label, existing);
                    fromWorldEdit++;
                } else {
                    others.computeIfAbsent(owner == null ? "the server" : owner, k -> new ArrayList<>()).add("/" + label);
                    continue;
                }
            }
            Slash cmd = new Slash(e, perm);
            map.register(label, plugin.getName().toLowerCase(java.util.Locale.ROOT), cmd);
            if (known.get(label) == cmd) registered.put(label, cmd);
            else cmd.unregister(map);
        }
        sync();
        StringBuilder line = new StringBuilder(registered.size() + " WorldEdit-style commands (//set, //copy, /brush...) run Worldcraft");
        if (fromWorldEdit > 0) line.append("; ").append(fromWorldEdit).append(" taken from WorldEdit (its own stay as /worldedit:/set...)");
        if (!leftToWorldEdit.isEmpty()) line.append("; ").append(leftToWorldEdit.size()).append(" left to WorldEdit (slash_commands.worldedit: leave)");
        plugin.getLogger().info(line.toString());
        for (var o : others.entrySet()) {
            plugin.getLogger().info("Kept " + String.join(", ", o.getValue()) + ": " + (o.getValue().size() == 1 ? "it belongs" : "they belong")
                    + " to " + o.getKey() + "; use /wc instead");
        }
    }

    /** Quita los nuestros y devuelve a WorldEdit lo suyo (al apagar o en /wc reload). */
    public void unregisterAll() {
        wanted = null;
        if (registered.isEmpty() && taken.isEmpty()) return;
        removeAll();
        sync();
    }

    private void removeAll() {
        CommandMap map = Bukkit.getCommandMap();
        Map<String, Command> known = map.getKnownCommands();
        // El mapa de Paper no deja borrar desde su iterador (001 F-03): se juntan las claves y se borran con remove.
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Command> e : known.entrySet()) if (e.getValue() instanceof Slash) keys.add(e.getKey());
        for (String k : keys) known.remove(k);
        for (Slash s : registered.values()) s.unregister(map);
        registered.clear();
        for (Map.Entry<String, Command> e : taken.entrySet()) if (!known.containsKey(e.getKey())) known.put(e.getKey(), e.getValue());
        taken.clear();
    }

    /** El plugin dueño de un comando: el suyo propio o el de una clave «plugin:etiqueta» con el mismo objeto. */
    static String owner(Command c, String label, Map<String, Command> known) {
        if (c instanceof PluginIdentifiableCommand p) return p.getPlugin().getName();
        for (Map.Entry<String, Command> e : known.entrySet()) {
            String k = e.getKey();
            if (e.getValue() == c && k.endsWith(":" + label)) {
                String ns = k.substring(0, k.length() - label.length() - 1);
                for (Plugin p : Bukkit.getPluginManager().getPlugins()) if (p.getName().equalsIgnoreCase(ns)) return p.getName();
                return ns.equals("minecraft") || ns.equals("bukkit") || ns.equals("paper") ? null : ns;
            }
        }
        return null;
    }

    private static List<String> worldEditPlugins() {
        List<String> out = new ArrayList<>();
        for (String n : WORLDEDIT) if (Bukkit.getPluginManager().isPluginEnabled(n)) out.add(n);
        return out;
    }

    /** El árbol de comandos, una vez por jugador: syncCommands de Paper ya lo manda a todos (lección 004/005). */
    private static void sync() {
        try {
            Bukkit.getServer().getClass().getMethod("syncCommands").invoke(Bukkit.getServer());
            return;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // sin syncCommands: se manda a cada jugador
        }
        for (Player p : Bukkit.getOnlinePlayers()) p.updateCommands();
    }

    /** Selftest: quién tiene //set ahora (null si es de Worldcraft). */
    public String setOwner() {
        Command c = Bukkit.getCommandMap().getKnownCommands().get("/set");
        if (c instanceof Slash) return null;
        if (c == null) return "nobody";
        String o = owner(c, "/set", Bukkit.getCommandMap().getKnownCommands());
        return o == null ? "the server" : o;
    }

    public int count() {
        return registered.size();
    }

    /**
     * WorldEdit quita del árbol del cliente lo que cree suyo y el jugador no puede usar (/set sin worldedit.region.set):
     * se vuelven a poner los nuestros que el jugador sí puede usar (H2 CA3). Paper solo borra lo que falta en la colección.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent e) {
        if (registered.isEmpty()) return;
        try {
            for (Slash s : registered.values()) {
                if (!e.getCommands().contains(s.getLabel()) && s.testPermissionSilent(e.getPlayer())) e.getCommands().add(s.getLabel());
            }
        } catch (UnsupportedOperationException ignored) {
            // una colección que no deja agregar: queda como la dejó WorldEdit
        }
    }
}
