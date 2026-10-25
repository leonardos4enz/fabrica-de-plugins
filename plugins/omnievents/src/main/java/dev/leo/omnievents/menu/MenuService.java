package dev.leo.omnievents.menu;

import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.action.ExecContext;
import dev.leo.omnievents.hook.Forms;
import dev.leo.omnievents.rule.Rule;
import dev.leo.omnievents.rule.RuleLoader;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Menús abiertos (spec 013, plan §1.3–§1.4): abre, dibuja y atiende los clics en Java, y manda los formularios de
 * Bedrock. Todo en el hilo principal; los oyentes de inventario solo con menús cargados (constitución III.1).
 */
public final class MenuService implements Listener {
    private final OmniEvents plugin;
    private volatile Map<String, Menu> menus = Map.of();
    private boolean registered;
    /** Dentro de un InventoryClickEvent no se puede abrir ni cerrar un inventario: se pasa al tick siguiente. */
    private boolean inClick;

    /** El formulario de Bedrock vigente de un jugador: solo su respuesta vale (H3 CA4). */
    record FormSession(Menu menu, Map<String, Object> args, long id, List<Menu.Item> buttons) {
    }

    private final Map<UUID, FormSession> forms = new HashMap<>();
    private long formSeq;
    /** Jugadores a los que un clic les abrió o cerró un menú (no se les vuelve a mandar el formulario). */
    private final Set<UUID> touched = new HashSet<>();

    public MenuService(OmniEvents plugin) {
        this.plugin = plugin;
    }

    /** Recarga (H4 CA2): cierra los menús abiertos sin on_close, descarta los formularios y registra si hay menús. */
    public void set(Map<String, Menu> loaded) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof MenuHolder h) {
                h.silent = true;
                p.closeInventory();
            }
        }
        forms.clear();
        menus = Map.copyOf(loaded);
        if (!menus.isEmpty() && !registered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registered = true;
        } else if (menus.isEmpty() && registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    public void unregister() {
        if (registered) HandlerList.unregisterAll(this);
        registered = false;
    }

    public Collection<Menu> all() {
        return menus.values();
    }

    /** Un menú por nombre, primero en el paquete de quien lo pide (spec 011). */
    public Menu get(String name, String pack) {
        return RuleLoader.resolveMenu(name, pack, menus);
    }

    /** Oyentes registrados (para /omni stats): clic dos veces, arrastre, cierre y salida. */
    public int listenerCount() {
        return registered ? 5 : 0;
    }

    /** Menús abiertos ahora: inventarios y formularios de Bedrock vigentes. */
    public int openCount() {
        int n = forms.size();
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof MenuHolder) n++;
        return n;
    }

    // ------------------------------------------------------------------ abrir y cerrar

    /** open_menu y /omni menu open (H1 CA2): requisito, on_open y el inventario o el formulario de Bedrock. */
    public void open(Player p, Menu menu, Map<String, Object> args) {
        if (inClick || !Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> open(p, menu, args));
            return;
        }
        if (!p.isOnline()) return;
        touched.add(p.getUniqueId());
        if (!plugin.rules().run(menu.open(), context(menu.open(), p, menu, args, Map.of()), 0, null)) return;
        Forms f = plugin.forms();
        if (menu.bedrock() && f.isBedrock(p)) {
            sendForm(p, menu, args);
            return;
        }
        MenuHolder h = new MenuHolder(menu, p.getUniqueId(), args);
        ExecContext ctx = context(menu.open(), p, menu, args, Map.of());
        Inventory inv;
        try {
            inv = Bukkit.createInventory(h, menu.size(), menu.title().component(ctx));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Error en el título del menú '" + menu.name() + "': " + e.getMessage());
            inv = Bukkit.createInventory(h, menu.size());
        }
        h.inventory = inv;
        draw(h, p);
        p.openInventory(inv);
    }

    /** close_menu y /omni menu close: cierra el menú de OmniEvents que tenga abierto (otro inventario no). */
    public void close(Player p) {
        if (inClick || !Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> close(p));
            return;
        }
        touched.add(p.getUniqueId());
        if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof MenuHolder) p.closeInventory();
        FormSession s = forms.remove(p.getUniqueId());
        if (s != null) runClose(p, s.menu(), s.args());
    }

    private ExecContext context(Rule rule, Player p, Menu menu, Map<String, Object> args, Map<String, Object> extra) {
        Map<String, Object> b = new HashMap<>();
        b.put("player", p);
        b.put("world", p.getWorld());
        b.put("location", p.getLocation());
        b.put("menu", menu.name());
        b.putAll(extra);
        ExecContext ctx = new ExecContext(plugin, rule, b);
        ctx.locals().putAll(args);
        return ctx;
    }

    /** Arma el inventario (H1 CA1, CA4): el primer ítem visible de cada casilla y fill en las vacías. */
    private void draw(MenuHolder h, Player p) {
        Menu menu = h.menu;
        ExecContext ctx = context(menu.open(), p, menu, h.args, Map.of());
        Map<Integer, Menu.Item> layout = menu.layout(ctx, (it, e) -> warn(menu, it, "view", e));
        ItemStack[] contents = new ItemStack[menu.size()];
        Map<Integer, Menu.Item> shown = new LinkedHashMap<>();
        Map<Menu.Item, ItemStack> built = new HashMap<>();
        for (Map.Entry<Integer, Menu.Item> e : layout.entrySet()) {
            Menu.Item it = e.getValue();
            ItemStack stack = built.computeIfAbsent(it, x -> {
                try {
                    return x.spec().build(ctx);
                } catch (RuntimeException ex) {
                    warn(menu, x, "item", ex);
                    return null;
                }
            });
            if (stack == null) continue;
            contents[e.getKey()] = stack.clone();
            shown.put(e.getKey(), it);
        }
        if (menu.fill() != null) {
            ItemStack filler = null;
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] != null) continue;
                if (filler == null) filler = menu.fill().build(ctx);
                contents[i] = filler.clone();
            }
        }
        h.shown = shown;
        h.inventory.setContents(contents);
    }

    private void warn(Menu menu, Menu.Item it, String part, RuntimeException e) {
        plugin.getLogger().warning("Error en el menú '" + menu.name() + "', ítem " + it.key() + " (" + part + "): " + e.getMessage());
    }

    private void runClose(Player p, Menu menu, Map<String, Object> args) {
        plugin.rules().run(menu.close(), context(menu.close(), p, menu, args, Map.of()), 0, null);
    }

    // ------------------------------------------------------------------ Java: clics, arrastre y cierre

    /** H1 CA5: con el menú arriba, ningún clic mueve ítems; el del menú corre la regla de su clic (H2). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof MenuHolder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        String type = Menu.clickType(e.getClick().name());
        Menu.Item it = h.shown.get(e.getRawSlot());
        if (type == null || it == null) return;
        Rule rule = it.clickFor(type);
        if (rule == null) return;
        Map<String, Object> extra = new HashMap<>();
        extra.put("slot", (double) e.getRawSlot());
        extra.put("click", type);
        extra.put("item", e.getCurrentItem());
        boolean ran;
        inClick = true;
        try {
            ran = plugin.rules().run(rule, context(rule, p, h.menu, h.args, extra), 0, null);
        } finally {
            inClick = false;
        }
        boolean close = ran && it.closes(type);
        // Al tick siguiente: si sigue abierto (un open_menu del clic ya abrió otro), se cierra o se vuelve a dibujar.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || p.getOpenInventory().getTopInventory().getHolder(false) != h) return;
            if (close) p.closeInventory();
            else draw(h, p);
        });
    }

    /** Otro plugin no puede descancelar el clic de un menú (STRIDE: manipulación). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClickMonitor(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof MenuHolder) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof MenuHolder) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof MenuHolder h) || h.silent) return;
        if (e.getPlayer() instanceof Player p) runClose(p, h.menu, h.args);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        forms.remove(e.getPlayer().getUniqueId());
        touched.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ Bedrock (H3)

    private void sendForm(Player p, Menu menu, Map<String, Object> args) {
        ExecContext ctx = context(menu.open(), p, menu, args, Map.of());
        List<Menu.Item> buttons = Menu.buttons(menu.layout(ctx, (it, e) -> warn(menu, it, "view", e)));
        List<Forms.Button> out = new ArrayList<>();
        for (Menu.Item it : buttons) {
            String text;
            try {
                text = Menu.buttonText(it, ctx);
            } catch (RuntimeException e) {
                warn(menu, it, "bedrock.button", e);
                text = it.key();
            }
            out.add(new Forms.Button(text, it.image()));
        }
        String title = plain(menu, ctx, menu.title());
        String content = menu.bedrockContent() == null ? "" : plain(menu, ctx, menu.bedrockContent());
        long id = ++formSeq;
        UUID uuid = p.getUniqueId();
        forms.put(uuid, new FormSession(menu, args, id, List.copyOf(buttons)));
        // Las respuestas llegan en el hilo de red de Floodgate: se pasan al principal antes de mirar nada (III.3).
        boolean sent = plugin.forms().send(p, title, content, out,
                i -> Bukkit.getScheduler().runTask(plugin, () -> onButton(uuid, id, i)),
                () -> Bukkit.getScheduler().runTask(plugin, () -> onFormClosed(uuid, id)));
        if (!sent) {
            forms.remove(uuid);
            plugin.getLogger().warning("No se pudo mandar el formulario del menú '" + menu.name() + "' a " + p.getName() + " (Floodgate)");
        }
    }

    private String plain(Menu menu, ExecContext ctx, dev.leo.omnievents.text.Template t) {
        try {
            return PlainTextComponentSerializer.plainText().serialize(t.component(ctx));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Error en el menú '" + menu.name() + "': " + e.getMessage());
            return menu.name();
        }
    }

    /** H3 CA2, CA4: solo vale la respuesta del formulario vigente, con un botón dentro de rango. */
    void onButton(UUID uuid, long id, int index) {
        FormSession s = forms.get(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (!validResponse(s, id, index) || p == null) return;
        forms.remove(uuid);
        Menu.Item it = s.buttons().get(index);
        Rule rule = it.clickFor("bedrock");
        Map<String, Object> extra = new HashMap<>();
        extra.put("slot", (double) it.slots().get(0));
        extra.put("click", "bedrock");
        ExecContext ctx = context(rule, p, s.menu(), s.args(), extra);
        try {
            extra.put("item", it.spec().build(ctx));
        } catch (RuntimeException ignored) {
            // el ítem es solo informativo en un clic de Bedrock
        }
        touched.remove(uuid);
        boolean ran = plugin.rules().run(rule, context(rule, p, s.menu(), s.args(), extra), 0, null);
        if (touched.remove(uuid) || !p.isOnline()) return;
        if (ran && it.closes("bedrock")) runClose(p, s.menu(), s.args());
        else sendForm(p, s.menu(), s.args());
    }

    static boolean validResponse(FormSession s, long id, int index) {
        return s != null && s.id() == id && index >= 0 && index < s.buttons().size();
    }

    void onFormClosed(UUID uuid, long id) {
        FormSession s = forms.get(uuid);
        if (s == null || s.id() != id) return;
        forms.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) runClose(p, s.menu(), s.args());
    }
}
