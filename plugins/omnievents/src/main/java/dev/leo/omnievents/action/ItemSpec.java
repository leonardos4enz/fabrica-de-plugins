package dev.leo.omnievents.action;

import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.text.Template;
import dev.leo.omnievents.text.TextFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Ítem declarado: MATERIAL;cantidad;nombre;lore1|lore2, mapa con propiedades, o saved:nombre. */
public final class ItemSpec {
    private Template material;
    private Template amount;
    private Template name;
    private final List<Template> lore = new ArrayList<>();
    private Template model;
    private final Map<String, Template> enchants = new LinkedHashMap<>();
    private boolean glow;
    private boolean unbreakable;
    private boolean hideFlags;
    private String saved;
    private OmniEvents plugin;

    /** Un ítem fuera de una acción (spec 013: los ítems de un menú), con el mismo formato que give_item. */
    public static ItemSpec parse(Object value, String path, OmniEvents plugin) {
        return parse(new Args("item", value, Map.of(), path, null), plugin);
    }

    /** El material (null si es un ítem guardado). */
    public Template material() {
        return material;
    }

    /** El nombre (null si no tiene). */
    public Template name() {
        return name;
    }

    /** Las plantillas del ítem (material, cantidad, nombre, lore, modelo, encantamientos), para validar sus raíces. */
    public List<Template> templates() {
        List<Template> out = new ArrayList<>();
        for (Template t : new Template[]{material, amount, name, model}) if (t != null) out.add(t);
        out.addAll(lore);
        out.addAll(enchants.values());
        return out;
    }

    static ItemSpec parse(Args a, OmniEvents plugin) {
        ItemSpec s = new ItemSpec();
        s.plugin = plugin;
        if (a.isMap()) {
            Map<String, Object> m = a.map();
            Object mat = m.get("material");
            if (mat == null) mat = m.get("id");
            if (mat == null && m.get("saved") == null) throw new IllegalArgumentException("el ítem necesita 'material'");
            if (m.get("saved") != null) s.saved = Values.str(m.get("saved"));
            else s.material = Template.compile(Values.str(mat));
            s.amount = m.get("amount") == null ? null : Template.compile(Values.str(m.get("amount")));
            s.name = m.get("name") == null ? null : Template.compile(Values.str(m.get("name")));
            Object lore = m.get("lore");
            if (lore instanceof List<?> l) for (Object o : l) s.lore.add(Template.compile(Values.str(o)));
            else if (lore != null) for (String line : Values.str(lore).split("\\|")) s.lore.add(Template.compile(line));
            s.model = m.get("model") == null ? null : Template.compile(Values.str(m.get("model")));
            Object ench = m.get("enchants");
            if (ench instanceof Map<?, ?> em) for (Map.Entry<?, ?> e : em.entrySet()) s.enchants.put(String.valueOf(e.getKey()), Template.compile(Values.str(e.getValue())));
            else if (ench instanceof List<?> el) for (Object o : el) addEnchant(s, Values.str(o));
            s.glow = Values.truthy(m.get("glow"));
            s.unbreakable = Values.truthy(m.get("unbreakable"));
            s.hideFlags = Values.truthy(m.get("hide_flags"));
            return s;
        }
        String text = a.text();
        if (text.toLowerCase(Locale.ROOT).startsWith("saved:")) {
            s.saved = text.substring(6).trim();
            return s;
        }
        List<Template> p = a.parts();
        if (p.isEmpty() || p.get(0).source().isEmpty()) throw new IllegalArgumentException("el ítem necesita un material");
        s.material = p.get(0);
        if (p.size() > 1 && !p.get(1).source().isEmpty()) s.amount = p.get(1);
        if (p.size() > 2 && !p.get(2).source().isEmpty()) s.name = p.get(2);
        if (p.size() > 3) for (String line : p.get(3).source().split("\\|")) s.lore.add(Template.compile(line));
        return s;
    }

    private static void addEnchant(ItemSpec s, String entry) {
        String[] kv = entry.split("[:\\-]");
        s.enchants.put(kv[0].trim(), Template.compile(kv.length > 1 ? kv[1].trim() : "1"));
    }

    public ItemStack build(ExecContext ctx) {
        if (saved != null) {
            ItemStack it = plugin.savedItems().get(saved);
            if (it == null) throw new ActionException("no hay un ítem guardado con el nombre '" + saved + "' (/omni item save " + saved + ")");
            ItemStack copy = it.clone();
            if (amount != null) copy.setAmount((int) Values.num(amount.render(ctx, Template.Mode.RAW)));
            return copy;
        }
        ItemStack it = new ItemStack(Actions.material(material.render(ctx, Template.Mode.RAW)));
        if (amount != null) it.setAmount((int) Math.max(1, Math.min(it.getMaxStackSize() * 36, Values.num(amount.render(ctx, Template.Mode.RAW)))));
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        if (name != null) meta.displayName(noItalic(name, ctx));
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (Template t : lore) lines.add(noItalic(t, ctx));
            meta.lore(lines);
        }
        if (model != null) setModel(meta, (int) Values.num(model.render(ctx, Template.Mode.RAW)));
        for (Map.Entry<String, Template> e : enchants.entrySet()) {
            Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(e.getKey().toLowerCase(Locale.ROOT)));
            if (ench == null) throw new ActionException("encantamiento desconocido '" + e.getKey() + "'");
            meta.addEnchant(ench, (int) Values.num(e.getValue().render(ctx, Template.Mode.RAW)), true);
        }
        if (glow) meta.setEnchantmentGlintOverride(true);
        if (unbreakable) meta.setUnbreakable(true);
        if (hideFlags) meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    @SuppressWarnings("deprecation")
    private static void setModel(ItemMeta meta, int model) {
        meta.setCustomModelData(model);
    }

    private static Component noItalic(Template t, ExecContext ctx) {
        return t.component(ctx).decorationIfAbsent(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }
}
