package dev.leo.worldcraft.command;

import dev.leo.worldcraft.Worldcraft;
import dev.leo.worldcraft.config.Issues;
import dev.leo.worldcraft.config.Messages;
import dev.leo.worldcraft.paper.ToolActions;
import dev.leo.worldcraft.paper.Tools;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.terrain.BrushSettings;
import dev.leo.worldcraft.tool.ToolKind;
import dev.leo.worldcraft.tool.ToolParser;
import dev.leo.worldcraft.tool.ToolSpec;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * /wc brush &lt;pincel de construcción&gt;, /wc tool y /wc superpickaxe (spec 032). /wc brush con un modo de terreno sigue
 * siendo el pincel de la 026 ({@link WorldcraftCommand}).
 */
final class ToolCommands {
    static final List<String> SP_MODES = List.of("single", "area", "recursive", "off");
    private final Worldcraft plugin;

    ToolCommands(Worldcraft plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private Tools tools() {
        return plugin.tools();
    }

    /** Lo escrito tras el tipo: palabras, --opciones (y -h, como WorldEdit) y clave=valor (un estado con [..=..] es una palabra). */
    record Tokens(List<String> words, Set<String> flags, Map<String, String> kv) {
        static Tokens of(String[] a, int from, ToolKind kind) {
            List<String> words = new ArrayList<>();
            Set<String> flags = new HashSet<>();
            Map<String, String> kv = new LinkedHashMap<>();
            for (int i = from; i < a.length; i++) {
                String t = a[i];
                if (t.startsWith("--") && t.length() > 2) flags.add(t.substring(2).toLowerCase(Locale.ROOT));
                else if (t.equals("-h") && kind != null) flags.add(kind == ToolKind.GRAVITY ? "height" : "hollow");
                else if (t.matches("[A-Za-z_]+=.*")) {
                    int eq = t.indexOf('=');
                    kv.put(t.substring(0, eq).toLowerCase(Locale.ROOT), t.substring(eq + 1));
                } else words.add(t);
            }
            return new Tokens(words, flags, kv);
        }
    }

    /**
     * /wc brush …: none/off, un pincel de construcción, o radius=/mask= sobre el de la mano. Devuelve false si es del
     * pincel de terreno (lo sigue WorldcraftCommand).
     */
    boolean brush(Player p, String[] a) {
        if (a.length < 2) return false;
        String first = a[1].toLowerCase(Locale.ROOT);
        if (first.equals("none") || first.equals("off")) {
            none(p);
            return true;
        }
        ToolSpec held = tools().spec(p.getInventory().getItemInMainHand());
        if (ToolKind.parse(first, true) == null) {
            // radius= o mask= sobre el pincel de construcción de la mano (H1 CA5).
            Tokens t = Tokens.of(a, 1, null);
            if (held != null && held.kind().brush && t.words().isEmpty() && t.flags().isEmpty() && !t.kv().isEmpty()) {
                update(p, held, t.kv());
                return true;
            }
            // «spere stone»: un nombre parecido a un pincel (y que no es un modo de terreno ni un bloque) se avisa aquí.
            boolean terrain = BrushSettings.Mode.parse(first) != null || first.contains("=") || first.matches("[\\d.%]+");
            boolean block = plugin.edits().blocks().blocks().contains("minecraft:" + first);
            if (terrain || block || Issues.closest(first, ToolKind.ids(true)) == null) return false;
        }
        bind(p, true, a);
        return true;
    }

    void tool(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        if (a.length < 2) throw new WorldcraftCommand.Usage();
        String first = a[1].toLowerCase(Locale.ROOT);
        if (first.equals("none") || first.equals("off")) {
            none(p);
            return;
        }
        bind(p, false, a);
    }

    private void bind(Player p, boolean brush, String[] a) {
        ToolKind kind = ToolKind.parse(a[1], brush);
        Tokens t = Tokens.of(a, 2, kind);
        try {
            ToolSpec spec = ToolParser.parse(brush, a[1], t.words(), t.flags(), t.kv(), plugin.config().tools().caps());
            // H4 CA3 y H5 CA8: butcher y navwand piden también el permiso de su comando.
            if (spec.kind() == ToolKind.BUTCHER && !p.hasPermission(BuildCommands.PERM_BUTCHER)
                    || spec.kind() == ToolKind.NAVWAND && !p.hasPermission(NavigationCommands.PERM)) {
                p.sendMessage(msg().get("no-permission"));
                return;
            }
            check(spec);
            Tools.Bound b = tools().bind(p, spec);
            String what = brush ? "brush" : "tool";
            switch (b) {
                case WAND -> p.sendMessage(msg().get("tool-not-on-wand", "what", what));
                case GIVEN -> p.sendMessage(msg().get("tool-given", "what", what, "tool", spec.describe()));
                case UPDATED -> p.sendMessage(msg().get("tool-updated", "what", what, "tool", spec.describe()));
                case BOUND -> p.sendMessage(msg().get("tool-bound", "what", what, "tool", spec.describe(),
                        "item", p.getInventory().getItemInMainHand().getType().getKey().getKey()));
            }
        } catch (IllegalArgumentException | ParseException e) {
            p.sendMessage(msg().get("error", "message", e.getMessage()));
        }
    }

    /** Los patrones y la máscara se leen ya (H7 CA2: el error al escribirlo, no al hacer clic). */
    private void check(ToolSpec s) throws ParseException {
        var parser = plugin.config().parser();
        for (String k : List.of("pattern", "left", "right")) if (s.text(k) != null) parser.pattern(s.text(k));
        if (s.mask() != null) parser.mask(s.mask());
    }

    private void update(Player p, ToolSpec held, Map<String, String> kv) {
        try {
            ToolSpec spec = ToolParser.update(held, kv, plugin.config().tools().caps());
            check(spec);
            tools().write(p.getInventory().getItemInMainHand(), spec);
            p.sendMessage(msg().get("tool-updated", "what", "brush", "tool", spec.describe()));
        } catch (IllegalArgumentException | ParseException e) {
            p.sendMessage(msg().get("error", "message", e.getMessage()));
        }
    }

    private void none(Player p) {
        boolean had = tools().unbind(p.getInventory().getItemInMainHand());
        p.sendMessage(msg().get(had ? "tool-removed" : "tool-nothing-to-remove"));
    }

    /** /wc superpickaxe|sp [single|area &lt;radio&gt;|recursive &lt;radio&gt;|off]; sin nada, prende o apaga (H6 CA1). */
    void superpickaxe(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) return;
        ToolActions.SuperPick cur = tools().superpick(p.getUniqueId());
        ToolActions.SuperPick next;
        String mode = a.length < 2 ? null : a[1].toLowerCase(Locale.ROOT);
        if (mode == null) next = cur == null ? new ToolActions.SuperPick("single", 0, true) : new ToolActions.SuperPick(cur.mode(), cur.radius(), !cur.on());
        else if (mode.equals("off")) next = cur == null ? new ToolActions.SuperPick("single", 0, false) : new ToolActions.SuperPick(cur.mode(), cur.radius(), false);
        else if (mode.equals("single")) next = new ToolActions.SuperPick("single", 0, true);
        else if (mode.equals("area") || mode.equals("recursive")) {
            int max = plugin.config().tools().superpickaxeMaxRadius();
            int r = 2;
            if (a.length > 2) {
                try {
                    r = Integer.parseInt(a[2]);
                } catch (NumberFormatException e) {
                    throw new WorldcraftCommand.Usage();
                }
            }
            if (r < 1 || r > max) {
                p.sendMessage(msg().get("radius-too-big", "radius", String.valueOf(r), "max", String.valueOf(max)));
                return;
            }
            next = new ToolActions.SuperPick(mode, r, true);
        } else {
            p.sendMessage(msg().get("error", "message", "'" + a[1] + "' is not a super pickaxe mode" + Issues.hint(mode, SP_MODES)
                    + ": single, area <radius>, recursive <radius> or off"));
            return;
        }
        tools().superpick(p.getUniqueId(), next);
        p.sendMessage(next.on() ? msg().get("superpickaxe-on", "mode", next.mode() + (next.mode().equals("single") ? "" : " " + next.radius()))
                : msg().get("superpickaxe-off"));
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        s.sendMessage(msg().get("players-only"));
        return null;
    }

    /** Las sugerencias tras /wc brush o /wc tool &lt;tipo&gt;: opciones del tipo; los bloques los agrega quien llama. */
    static List<String> optionWords(ToolKind k) {
        List<String> out = new ArrayList<>();
        for (String f : ToolParser.flags(k)) out.add("--" + f);
        if (k.placesBlocks()) out.add("mask=");
        return out;
    }
}
