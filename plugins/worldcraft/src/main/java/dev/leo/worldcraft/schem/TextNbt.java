package dev.leo.worldcraft.schem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Un texto del juego entre JSON y NBT (plan 027 §1.4). Hasta 1.21.4 una línea de cartel es un texto JSON dentro de un
 * String; desde 1.21.5 (versión de datos 4325) es el mismo texto como NBT: un String es texto literal, un compuesto es un
 * objeto, y los sí/no son bytes.
 */
public final class TextNbt {
    /** La versión de datos de 1.21.5: desde ahí los textos de los carteles van en NBT. */
    public static final int NBT_TEXT_SINCE = 4325;

    private static final Set<String> BOOLEANS = Set.of("bold", "italic", "underlined", "strikethrough", "obfuscated",
            "interpret", "bypass_text_filters");

    private TextNbt() {
    }

    public static Object toNbt(JsonElement e) {
        if (e == null || e.isJsonNull()) return "";
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isBoolean()) return (byte) (p.getAsBoolean() ? 1 : 0);
            if (p.isNumber()) {
                double d = p.getAsDouble();
                return d == Math.rint(d) && Math.abs(d) < Integer.MAX_VALUE ? (Object) (int) d : (Object) d;
            }
            return p.getAsString();
        }
        if (e.isJsonArray()) {
            List<Object> items = new ArrayList<>();
            for (JsonElement x : e.getAsJsonArray()) items.add(toNbt(x));
            // Una lista NBT tiene un solo tipo: si se mezclan, cada elemento va como compuesto {text: …} o como objeto.
            int type = items.isEmpty() ? Nbt.END : Nbt.typeOf(items.get(0));
            boolean mixed = false;
            for (Object o : items) if (Nbt.typeOf(o) != type) mixed = true;
            if (mixed) {
                List<Object> fixed = new ArrayList<>();
                for (Object o : items) {
                    if (o instanceof Map) fixed.add(o);
                    else {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("text", String.valueOf(o));
                        fixed.add(m);
                    }
                }
                return new Nbt.TagList(Nbt.COMPOUND, fixed);
            }
            return new Nbt.TagList(type, items);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) m.put(en.getKey(), toNbt(en.getValue()));
        return m;
    }

    @SuppressWarnings("unchecked")
    public static JsonElement toJson(Object v, String key) {
        if (v instanceof String s) return new JsonPrimitive(s);
        if (v instanceof Byte b && key != null && BOOLEANS.contains(key)) return new JsonPrimitive(b != 0);
        if (v instanceof Number n) return new JsonPrimitive(n);
        if (v instanceof Nbt.TagList l) {
            JsonArray a = new JsonArray();
            for (Object o : l.items()) a.add(toJson(o, null));
            return a;
        }
        if (v instanceof Map) {
            JsonObject o = new JsonObject();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) o.add(e.getKey(), toJson(e.getValue(), e.getKey()));
            return o;
        }
        if (v instanceof int[] a) {
            JsonArray arr = new JsonArray();
            for (int i : a) arr.add(i);
            return arr;
        }
        return new JsonPrimitive(String.valueOf(v));
    }
}
