package dev.leo.omnievents.data;

import dev.leo.omnievents.action.ActionException;
import dev.leo.omnievents.expr.Named;
import dev.leo.omnievents.expr.Values;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * Variables de bloques, entidades e ítems (spec 009, H3) en el PersistentDataContainer de Paper. En cada objeto hay
 * dos contenedores: {@code omnievents:v} (valores) y {@code omnievents:e} (caducidades, ms de época). Los de un
 * bloque van dentro del PDC de su chunk, bajo {@code omnievents:b.<x>.<y>.<z>}. Una variable vencida vale nulo al
 * leerse y se borra en la siguiente escritura al mismo objeto (no se puede recorrer cada chunk e ítem).
 */
public final class ObjectData {
    public static final String NS = "omnievents";
    private static final NamespacedKey VALUES = new NamespacedKey(NS, "v");
    private static final NamespacedKey EXPIRES = new NamespacedKey(NS, "e");

    /** Una variable con su caducidad (0 = no caduca). */
    public record Entry(Object value, long expireAt) {
    }

    private final LongSupplier clock;
    private final IntSupplier maxText;
    private final IntSupplier maxVars;

    public ObjectData(LongSupplier clock, IntSupplier maxText, IntSupplier maxVars) {
        this.clock = clock;
        this.maxText = maxText;
        this.maxVars = maxVars;
    }

    /** ¿Se le pueden guardar variables? (bloque, entidad o ítem; el aire y el ítem vacío no). */
    public static boolean supports(Object o) {
        return o instanceof Block || o instanceof Entity || o instanceof ItemStack;
    }

    // ------------------------------------------------------------------ nombres → claves de Bukkit

    /**
     * Las claves de Bukkit solo admiten [a-z0-9._-/]: el nombre se codifica de forma reversible. [a-z0-9_] van tal
     * cual; una mayúscula es «.» + minúscula; cualquier otro carácter (ñ, á) es «-» + hexadecimal + «-».
     */
    public static String encode(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        name.codePoints().forEach(c -> {
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_') sb.append((char) c);
            else if (c >= 'A' && c <= 'Z') sb.append('.').append((char) Character.toLowerCase(c));
            else sb.append('-').append(Integer.toHexString(c)).append('-');
        });
        return sb.toString();
    }

    public static String decode(String key) {
        StringBuilder sb = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '.' && i + 1 < key.length()) sb.append(Character.toUpperCase(key.charAt(++i)));
            else if (c == '-') {
                int end = key.indexOf('-', i + 1);
                if (end < 0) return key;
                sb.appendCodePoint(Integer.parseInt(key.substring(i + 1, end), 16));
                i = end;
            } else sb.append(c);
        }
        return sb.toString();
    }

    /** Valor que se puede guardar en el PDC: número, texto o booleano (lo demás, como texto). */
    public static Object storable(Object v, int maxText) {
        v = Values.normalize(v);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b;
        String s = v instanceof Named n ? n.name() : v instanceof String str ? str : Values.str(v);
        if (s.length() > maxText) {
            throw new ActionException("el texto mide " + s.length() + " caracteres y el máximo para un objeto es " + maxText + " (object-data.max-text-length)");
        }
        return s;
    }

    // ------------------------------------------------------------------ lectura

    public Object get(Object target, String name) {
        Entry e = entries(target).get(name);
        return e == null ? null : e.value();
    }

    /** Segundos que le quedan; -1 si no caduca; null si no existe. */
    public Double ttl(Object target, String name) {
        Entry e = entries(target).get(name);
        if (e == null) return null;
        return e.expireAt() == 0 ? -1.0 : Math.max(0, e.expireAt() - clock.getAsLong()) / 1000.0;
    }

    /** Variables vivas del objeto, por nombre. */
    public Map<String, Entry> entries(Object target) {
        Map<String, Entry> out = new TreeMap<>();
        PersistentDataContainer root = readRoot(target);
        if (root == null) return out;
        PersistentDataContainer v = root.get(VALUES, PersistentDataType.TAG_CONTAINER);
        if (v == null) return out;
        PersistentDataContainer e = root.get(EXPIRES, PersistentDataType.TAG_CONTAINER);
        long now = clock.getAsLong();
        for (NamespacedKey k : v.getKeys()) {
            if (!NS.equals(k.getNamespace())) continue;
            long at = e != null && e.has(k, PersistentDataType.LONG) ? e.get(k, PersistentDataType.LONG) : 0;
            if (at != 0 && at <= now) continue;
            Object value = read(v, k);
            if (value != null) out.put(decode(k.getKey()), new Entry(value, at));
        }
        return out;
    }

    private static Object read(PersistentDataContainer c, NamespacedKey k) {
        if (c.has(k, PersistentDataType.DOUBLE)) return c.get(k, PersistentDataType.DOUBLE);
        if (c.has(k, PersistentDataType.STRING)) return c.get(k, PersistentDataType.STRING);
        if (c.has(k, PersistentDataType.BOOLEAN)) return c.get(k, PersistentDataType.BOOLEAN);
        return null;
    }

    private static PersistentDataContainer readRoot(Object target) {
        target = Values.raw(target);
        if (target instanceof Entity en) return en.getPersistentDataContainer();
        if (target instanceof Block b) return b.getChunk().getPersistentDataContainer().get(blockKey(b), PersistentDataType.TAG_CONTAINER);
        if (target instanceof ItemStack st) {
            if (st.getType().isAir() || st.getAmount() <= 0) return null;
            ItemMeta m = st.getItemMeta();
            return m == null ? null : m.getPersistentDataContainer();
        }
        return null;
    }

    static NamespacedKey blockKey(Block b) {
        return new NamespacedKey(NS, "b." + b.getX() + "." + b.getY() + "." + b.getZ());
    }

    // ------------------------------------------------------------------ escritura

    /** Asigna (value null = borra); expireAt en ms de época, 0 = no caduca. */
    public void set(Object target, String name, Object value, long expireAt) {
        Object raw = Values.raw(target);
        Object stored = value == null ? null : storable(value, maxText.getAsInt());
        if (raw instanceof Entity en) {
            edit(en.getPersistentDataContainer(), name, stored, expireAt);
        } else if (raw instanceof Block b) {
            if (b.getType().isAir()) throw new ActionException("no se pueden guardar variables en un bloque de aire");
            PersistentDataContainer chunk = b.getChunk().getPersistentDataContainer();
            NamespacedKey key = blockKey(b);
            PersistentDataContainer root = chunk.get(key, PersistentDataType.TAG_CONTAINER);
            if (root == null) root = chunk.getAdapterContext().newPersistentDataContainer();
            edit(root, name, stored, expireAt);
            if (root.isEmpty()) chunk.remove(key);
            else chunk.set(key, PersistentDataType.TAG_CONTAINER, root);
        } else if (raw instanceof ItemStack st) {
            if (st.getType().isAir() || st.getAmount() <= 0) throw new ActionException("no se pueden guardar variables en un ítem vacío");
            st.editMeta(m -> edit(m.getPersistentDataContainer(), name, stored, expireAt));
        } else {
            throw new ActionException("solo se guardan variables en un bloque, una entidad o un ítem, y esto es " + Values.typeName(raw));
        }
    }

    /** Cambia una variable dentro del contenedor del objeto y de paso borra las vencidas. */
    private void edit(PersistentDataContainer root, String name, Object stored, long expireAt) {
        PersistentDataContainer v = root.get(VALUES, PersistentDataType.TAG_CONTAINER);
        PersistentDataContainer e = root.get(EXPIRES, PersistentDataType.TAG_CONTAINER);
        if (v == null) v = root.getAdapterContext().newPersistentDataContainer();
        if (e == null) e = root.getAdapterContext().newPersistentDataContainer();
        long now = clock.getAsLong();
        for (NamespacedKey k : e.getKeys()) {
            Long at = e.get(k, PersistentDataType.LONG);
            if (at != null && at <= now) {
                v.remove(k);
                e.remove(k);
            }
        }
        NamespacedKey key = new NamespacedKey(NS, encode(name));
        if (stored == null) {
            v.remove(key);
            e.remove(key);
        } else {
            if (!v.has(key) && v.getKeys().size() >= maxVars.getAsInt()) {
                throw new ActionException("el objeto ya tiene " + v.getKeys().size() + " variables, el máximo (object-data.max-vars)");
            }
            v.remove(key);
            if (stored instanceof Double d) v.set(key, PersistentDataType.DOUBLE, d);
            else if (stored instanceof Boolean b) v.set(key, PersistentDataType.BOOLEAN, b);
            else v.set(key, PersistentDataType.STRING, (String) stored);
            if (expireAt > 0) e.set(key, PersistentDataType.LONG, expireAt);
            else e.remove(key);
        }
        if (v.isEmpty()) root.remove(VALUES);
        else root.set(VALUES, PersistentDataType.TAG_CONTAINER, v);
        if (e.isEmpty()) root.remove(EXPIRES);
        else root.set(EXPIRES, PersistentDataType.TAG_CONTAINER, e);
    }

    /** Borra todas las variables de un bloque (al romperse, H3 CA3). Barato si no tiene. */
    public static void clearBlock(Block b) {
        PersistentDataContainer chunk = b.getChunk().getPersistentDataContainer();
        if (chunk.isEmpty()) return;
        NamespacedKey key = blockKey(b);
        if (chunk.has(key)) chunk.remove(key);
    }
}
