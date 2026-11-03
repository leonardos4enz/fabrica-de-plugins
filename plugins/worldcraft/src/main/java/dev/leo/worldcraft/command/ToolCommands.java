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
                // Spec 034: en deform, -r y -o son de WorldEdit, y «x=x*2» es parte de la fórmula (solo radius= y mask= son opciones).
                else if (kind == ToolKind.DEFORM && (t.equals("-r") || t.equals("-o"))) flags.add(t.equals("-r") ? "raw" : "offset");
                else if (kind == ToolKind.DEFORM && t.matches("[A-Za-z_]+=.*") && !t.matches("(?i)(radius|mask)=.*")) words.add(t);
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
        // Spec 041 H2 CA7: guardar, listar y cargar pinceles.
        switch (first) {
            case "savebrush", "save" -> {
                saveBrush(p, a);
                return true;
            }
            case "listbrush", "list" -> {
                listBrush(p, a);
                return true;
            }
            case "loadbrush", "load" -> {
                loadBrush(p, a);
                return true;
            }
            default -> {
            }
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
            // Spec 040 H2 CA6: «paint … item <ítem>» es el pincel apply (sin item, paint sigue siendo del terreno).
            if (first.equals("paint") && java.util.Arrays.stream(a).anyMatch(w -> w.equalsIgnoreCase("item"))) {
                bind(p, true, a);
                return true;
            }
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
        // Spec 040: el ítem, el bioma, el feature y la imagen existen.
        switch (s.kind()) {
            case APPLY -> {
                if (plugin.config().itemUses().rules(s.text("item")) == null) {
                    List<String> items = plugin.config().itemUses().items();
                    throw new IllegalArgumentException("Worldcraft does not know what '" + s.text("item") + "' does" + Issues.hint(s.text("item"), items)
                            + ". It knows: " + String.join(", ", items) + " (add more in item-uses.yml)");
                }
            }
            case BIOME -> {
                List<String> names = new ArrayList<>();
                for (String b : plugin.edits().blocks().biomes()) names.add(b.replace("minecraft:", ""));
                if (!names.contains(s.text("biome"))) {
                    throw new IllegalArgumentException("There is no biome '" + s.text("biome") + "'" + Issues.hint(s.text("biome"), names) + "; see /wc biomelist");
                }
            }
            // Spec 041
            case STENCIL, IMAGE, HEIGHT -> {
                String img = s.text("image");
                if (img != null && !plugin.images().files().contains(img)) {
                    throw new IllegalArgumentException("There is no image '" + img + "' in plugins/Worldcraft/images/" + Issues.hint(img, plugin.images().files()));
                }
            }
            case POPULATE -> parser.mask(s.text("on"));
            case FEATURE, FEATUREPLACER -> {
                var nms = dev.leo.worldcraft.paper.Nms.get();
                if (!nms.available()) throw new IllegalArgumentException("The game's features are not available on this server version: " + nms.problem());
                if (!nms.hasFeature(s.text("feature"))) {
                    List<String> names = new ArrayList<>();
                    for (String f : nms.features()) names.add(f.replace("minecraft:", ""));
                    throw new IllegalArgumentException("There is no feature '" + s.text("feature") + "'" + Issues.hint(s.text("feature"), names));
                }
            }
            case HEIGHTMAP -> {
                if (!plugin.images().files().contains(s.text("image"))) {
                    throw new IllegalArgumentException("There is no image '" + s.text("image") + "' in plugins/Worldcraft/images/"
                            + Issues.hint(s.text("image"), plugin.images().files()));
                }
            }
            default -> {
            }
        }
    }

    /**
     * Spec 041 H3 CA7: un pincel escrito como tras /wc brush (a[from] es el tipo), ya revisado; null si no sirve (y se
     * dice por qué).
     */
    ToolSpec parseBrush(Player p, String[] a, int from) {
        if (a.length <= from) {
            p.sendMessage(msg().get("error", "message", "Write a brush, like 'sphere stone 3'"));
            return null;
        }
        ToolKind kind = ToolKind.parse(a[from], true);
        Tokens t = Tokens.of(a, from + 1, kind);
        try {
            ToolSpec spec = ToolParser.parse(true, a[from], t.words(), t.flags(), t.kv(), plugin.config().tools().caps());
            if (spec.kind() == ToolKind.BUTCHER && !p.hasPermission(BuildCommands.PERM_BUTCHER)) {
                p.sendMessage(msg().get("no-permission"));
                return null;
            }
            check(spec);
            return spec;
        } catch (IllegalArgumentException | ParseException e) {
            p.sendMessage(msg().get("error", "message", e.getMessage()));
            return null;
        }
    }

    private java.nio.file.Path brushes() {
        return plugin.getDataFolder().toPath().resolve("brushes");
    }

    /** /wc brush savebrush &lt;nombre&gt; [--global]: el pincel de la mano a un archivo de texto. */
    private void saveBrush(Player p, String[] a) {
        boolean global = java.util.Arrays.asList(a).contains("--global") || java.util.Arrays.asList(a).contains("-g");
        List<String> words = new ArrayList<>();
        for (int i = 2; i < a.length; i++) if (!a[i].startsWith("-")) words.add(a[i]);
        ToolSpec held = tools().spec(p.getInventory().getItemInMainHand());
        if (held == null || !held.kind().brush) {
            p.sendMessage(msg().get("brush-hold"));
            return;
        }
        if (words.size() != 1) throw new WorldcraftCommand.Usage();
        if (global && !p.hasPermission(WorldcraftCommand.ADMIN)) {
            p.sendMessage(msg().get("no-permission"));
            return;
        }
        java.nio.file.Path file;
        try {
            file = dev.leo.worldcraft.tool.Brushes.file(brushes(), global ? null : p.getUniqueId().toString(), words.get(0));
        } catch (IllegalArgumentException e) {
            p.sendMessage(msg().get("error", "message", e.getMessage()));
            return;
        }
        String text = held.encode();
        String name = words.get(0);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Files.writeString(file, text + "\n", java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, plugin.io()).whenComplete((v, err) -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                p.sendMessage(err == null ? msg().get("brush-saved", "name", name, "where", global ? "for everyone" : "for you")
                        : msg().get("error", "message", "Could not save the brush: " + err.getMessage()))));
    }

    /** Los nombres guardados: los del jugador y los de todos. */
    private List<String[]> savedBrushes(Player p) {
        List<String[]> out = new ArrayList<>();
        for (java.nio.file.Path dir : List.of(brushes().resolve(p.getUniqueId().toString()), brushes())) {
            if (!java.nio.file.Files.isDirectory(dir)) continue;
            try (var st = java.nio.file.Files.list(dir)) {
                st.filter(f -> f.getFileName().toString().endsWith(".txt")).sorted().forEach(f -> {
                    String n = f.getFileName().toString();
                    out.add(new String[]{n.substring(0, n.length() - 4), dir.equals(brushes()) ? "everyone" : "you"});
                });
            } catch (java.io.IOException ignored) {
                // una carpeta que no se lee no se lista
            }
        }
        return out;
    }

    /** /wc brush listbrush [página]. */
    private void listBrush(Player p, String[] a) {
        int page = 1;
        if (a.length > 2) {
            try {
                page = Math.max(1, Integer.parseInt(a[2]));
            } catch (NumberFormatException e) {
                throw new WorldcraftCommand.Usage();
            }
        }
        List<String[]> all = savedBrushes(p);
        if (all.isEmpty()) {
            p.sendMessage(msg().get("brush-list-empty"));
            return;
        }
        int pages = (all.size() + 9) / 10;
        page = Math.min(page, pages);
        p.sendMessage(msg().get("brush-list-header", "count", String.valueOf(all.size()), "page", String.valueOf(page), "pages", String.valueOf(pages)));
        for (String[] b : all.subList((page - 1) * 10, Math.min(all.size(), page * 10))) {
            p.sendMessage(msg().get("brush-list-line", "name", b[0], "who", b[1]));
        }
    }

    /** /wc brush loadbrush &lt;nombre&gt;: el guardado (primero el propio) a la mano, revisado como si se escribiera. */
    private void loadBrush(Player p, String[] a) {
        if (a.length != 3) throw new WorldcraftCommand.Usage();
        java.nio.file.Path file;
        try {
            file = dev.leo.worldcraft.tool.Brushes.file(brushes(), p.getUniqueId().toString(), a[2]);
            if (!java.nio.file.Files.isRegularFile(file)) file = dev.leo.worldcraft.tool.Brushes.file(brushes(), null, a[2]);
        } catch (IllegalArgumentException e) {
            p.sendMessage(msg().get("error", "message", e.getMessage()));
            return;
        }
        ToolSpec spec;
        try {
            spec = java.nio.file.Files.isRegularFile(file) ? ToolSpec.decode(java.nio.file.Files.readString(file).trim(), plugin.config().tools().caps()) : null;
        } catch (java.io.IOException e) {
            spec = null;
        }
        if (spec == null || !spec.kind().brush) {
            List<String> names = new ArrayList<>();
            for (String[] b : savedBrushes(p)) names.add(b[0]);
            p.sendMessage(msg().get("brush-not-saved", "name", a[2], "hint", hint(Issues.closest(a[2], names))));
            return;
        }
        try {
            if (spec.kind() == ToolKind.BUTCHER && !p.hasPermission(BuildCommands.PERM_BUTCHER)) {
                p.sendMessage(msg().get("no-permission"));
                return;
            }
            check(spec);
        } catch (IllegalArgumentException | ParseException e) {
            p.sendMessage(msg().get("error", "message", e.getMessage()));
            return;
        }
        Tools.Bound b = tools().bind(p, spec);
        if (b == Tools.Bound.WAND) p.sendMessage(msg().get("tool-not-on-wand", "what", "brush"));
        else p.sendMessage(msg().get("brush-loaded", "name", a[2], "tool", spec.describe()));
    }

    private static String hint(String c) {
        return c == null ? "" : " (did you mean '" + c + "'?)";
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
