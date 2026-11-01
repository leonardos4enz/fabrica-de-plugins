package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.schem.Nbt;
import dev.leo.worldcraft.schem.TextNbt;
import dev.leo.worldcraft.schem.TileData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Los datos de bloque de un esquema con la API de Paper (spec 027 plan §1.1, §1.4), sin NMS: los ítems pasan por
 * {@code ItemStack.serializeAsBytes}/{@code deserializeBytes} (el juego actualiza los de versiones viejas con su versión
 * de datos) y los carteles por Adventure. Solo en el hilo principal, dentro del presupuesto del planificador.
 */
public final class TileNbt {
    private TileNbt() {
    }

    @SuppressWarnings("deprecation")
    public static int serverDataVersion() {
        return Bukkit.getUnsafe().getDataVersion();
    }

    /** El id de la entidad de bloque de un bloque (los carteles y las cajas de shulker de color comparten uno). */
    public static String entityId(String material) {
        if (material.endsWith("_hanging_sign")) return "minecraft:hanging_sign";
        if (material.endsWith("_sign")) return "minecraft:sign";
        if (material.endsWith("shulker_box")) return "minecraft:shulker_box";
        return material;
    }

    // ------------------------------------------------------------------ poner en el mundo

    /** Pone los datos en el bloque recién puesto; los que no son de contenedor ni de cartel no se ponen (H2 CA3). */
    public static void restore(BlockState st, TileData t) throws IOException {
        switch (t.kind()) {
            case CONTAINER -> {
                if (!(st instanceof Container c)) return;
                ItemStack[] fit = items(t, c.getSnapshotInventory().getSize());
                c.getSnapshotInventory().setContents(fit);
                st.update(true, false);
            }
            case SIGN -> {
                if (!(st instanceof Sign s)) return;
                Lines l = lines(t);
                for (int side = 0; side < 2; side++) {
                    SignSide ss = s.getSide(side == 0 ? Side.FRONT : Side.BACK);
                    for (int i = 0; i < 4; i++) ss.line(i, l.text[side][i]);
                    ss.setGlowingText(l.glow[side]);
                }
                s.setWaxed(l.waxed);
                st.update(true, false);
            }
            default -> {
            }
        }
    }

    static ItemStack[] items(TileData t, int size) throws IOException {
        ItemStack[] out = new ItemStack[size];
        for (Object o : Nbt.list(t.data(), "Items").items()) {
            if (!(o instanceof Map<?, ?>)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> item = new LinkedHashMap<>((Map<String, Object>) o);
            Long slot = Nbt.integer(item, "Slot");
            item.remove("Slot");
            if (slot == null || slot < 0 || slot >= size) continue;
            out[slot.intValue()] = item(item, t.dataVersion());
        }
        return out;
    }

    private static ItemStack item(Map<String, Object> nbt, int dataVersion) {
        Map<String, Object> m = new LinkedHashMap<>(nbt);
        m.put("DataVersion", dataVersion > 0 ? dataVersion : serverDataVersion());
        try {
            return ItemStack.deserializeBytes(Nbt.gzip(Nbt.write("", m)));
        } catch (RuntimeException e) {
            return null; // un ítem que este servidor no conoce: el resto del cofre sigue
        }
    }

    private static Map<String, Object> nbt(ItemStack item, int slot) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>(Nbt.read(Nbt.gunzip(item.serializeAsBytes(), 64L << 20)).tag());
        m.remove("DataVersion");
        m.put("Slot", (byte) slot);
        return m;
    }

    private record Lines(Component[][] text, boolean[] glow, boolean waxed) {
    }

    private static Lines lines(TileData t) {
        Component[][] text = new Component[2][4];
        boolean[] glow = new boolean[2];
        Map<String, Object> d = t.data();
        for (int side = 0; side < 2; side++) {
            Map<String, Object> f = Nbt.compound(d, side == 0 ? "front_text" : "back_text");
            Nbt.TagList msgs = f == null ? null : Nbt.list(f, "messages");
            for (int i = 0; i < 4; i++) {
                Object v = msgs != null && i < msgs.items().size() ? msgs.items().get(i)
                        : side == 0 && f == null ? d.get("Text" + (i + 1)) : null;
                text[side][i] = component(v, t.dataVersion());
            }
            Long g = f != null ? Nbt.integer(f, "has_glowing_text") : side == 0 ? Nbt.integer(d, "GlowingText") : null;
            glow[side] = g != null && g != 0;
        }
        Long w = Nbt.integer(d, "is_waxed");
        return new Lines(text, glow, w != null && w != 0);
    }

    private static Component component(Object v, int dataVersion) {
        if (v == null) return Component.empty();
        try {
            if (v instanceof String s) {
                if (dataVersion >= TextNbt.NBT_TEXT_SINCE) return Component.text(s);
                if (s.isEmpty()) return Component.empty();
                return GsonComponentSerializer.gson().deserialize(s);
            }
            return GsonComponentSerializer.gson().deserializeFromTree(TextNbt.toJson(v, null));
        } catch (RuntimeException e) {
            return Component.text(String.valueOf(v));
        }
    }

    private static Object line(Component c, int dataVersion) {
        if (dataVersion >= TextNbt.NBT_TEXT_SINCE) return TextNbt.toNbt(GsonComponentSerializer.gson().serializeToTree(c));
        return GsonComponentSerializer.gson().serialize(c);
    }

    // ------------------------------------------------------------------ a la forma estándar (para guardar)

    /**
     * Los datos de bloque en la forma estándar con la versión del servidor (plan 027 §1.2): los de la 025 ('C', 'S'), y
     * los 'N' de otra versión que sabemos poner. Los demás 'N' quedan tal cual. null si no hay nada que guardar.
     */
    public static byte[] standard(byte[] tile, String material) throws IOException {
        int dv = serverDataVersion();
        if (tile == null || tile.length == 0) return null;
        if (TileData.is(tile)) {
            TileData t = TileData.decode(tile);
            if (t.dataVersion() == dv || t.kind() == TileData.Kind.OTHER) return tile;
            return switch (t.kind()) {
                case CONTAINER -> container(t.id(), items(t, 256), dv);
                case SIGN -> sign(t.id(), lines(t), dv);
                default -> tile;
            };
        }
        String id = entityId(material);
        if (tile[0] == 'C') {
            byte[] raw = new byte[tile.length - 1];
            System.arraycopy(tile, 1, raw, 0, raw.length);
            return container(id, ItemStack.deserializeItemsFromBytes(raw), dv);
        }
        if (tile[0] == 'S') {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(tile, 1, tile.length - 1));
            Component[][] text = new Component[2][4];
            boolean[] glow = new boolean[2];
            for (int side = 0; side < 2; side++) {
                for (int i = 0; i < 4; i++) text[side][i] = GsonComponentSerializer.gson().deserialize(in.readUTF());
                glow[side] = in.readBoolean();
            }
            return sign(id, new Lines(text, glow, in.readBoolean()), dv);
        }
        return null;
    }

    private static byte[] container(String id, ItemStack[] items, int dv) throws IOException {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && !items[i].isEmpty()) list.add(nbt(items[i], i));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("Items", new Nbt.TagList(Nbt.COMPOUND, list));
        return new TileData(dv, id, data).encode();
    }

    private static byte[] sign(String id, Lines l, int dv) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int side = 0; side < 2; side++) {
            Map<String, Object> f = new LinkedHashMap<>();
            List<Object> msgs = new ArrayList<>();
            for (int i = 0; i < 4; i++) msgs.add(line(l.text[side][i] == null ? Component.empty() : l.text[side][i], dv));
            int type = Nbt.typeOf(msgs.get(0));
            boolean same = msgs.stream().allMatch(o -> Nbt.typeOf(o) == type);
            if (!same) {
                // en NBT, una lista tiene un solo tipo: cada línea como compuesto
                List<Object> fixed = new ArrayList<>();
                for (Object o : msgs) {
                    if (o instanceof Map) fixed.add(o);
                    else {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("text", String.valueOf(o));
                        fixed.add(m);
                    }
                }
                msgs = fixed;
            }
            f.put("messages", new Nbt.TagList(Nbt.typeOf(msgs.get(0)), msgs));
            f.put("color", "black");
            f.put("has_glowing_text", (byte) (l.glow[side] ? 1 : 0));
            data.put(side == 0 ? "front_text" : "back_text", f);
        }
        data.put("is_waxed", (byte) (l.waxed ? 1 : 0));
        return new TileData(dv, id, data).encode();
    }
}
